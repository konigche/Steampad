package dev.steampad.service;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.GlobalConfig;
import dev.steampad.input.GamepadSnapshot;
import dev.steampad.input.GlfwControllerProvider;
import dev.steampad.input.GlfwSnapshotSource;
import dev.steampad.input.sdl.Sdl3GamepadProvider;
import dev.steampad.steam.SteamControllerHandleRef;
import dev.steampad.steam.SteamHapticsService;
import dev.steampad.steam.SteamInputManager;

import java.util.List;

/**
 * Single source of truth for "which controllers are connected" and "what is their state", across
 * all backends. Resolution order (revised, D031-followup): SDL3/GLFW ALWAYS drive the main
 * controller list and gameplay dispatch, Steam Input never becomes primary here:
 * <ol>
 *   <li><b>SDL3</b> (via JNA) when available and enabled — broad device DB + rumble.</li>
 *   <li><b>GLFW</b> joystick/gamepad detection — the always-available baseline.</li>
 *   <li><b>Steam Input</b> (ISteamController) when neither fallback sees a physical device — which,
 *       with the IGA manifest deployed, is the normal state rather than a rare one.</li>
 * </ol>
 *
 * <p><b>Hybrid auto-switch (v0.77.0, D117) — supersedes the old "Steam Input is never primary"
 * rule.</b> D032 demoted Steam Input because a deployed manifest bound <em>nothing</em>: Steam only
 * forwards actions the user wired up by hand in its configurator, so promoting it made gameplay go
 * silent unless every single action was remapped there. The manifest now ships real default bindings
 * generated from the mod's own button defaults ({@link dev.steampad.steam.SteamDefaultConfigGenerator}),
 * which removes that objection entirely.
 *
 * <p>The switch is automatic and one-directional by circumstance, not by preference: SDL3/GLFW are
 * still tried first and still win whenever they can see a pad. Steam Input takes over only when they
 * cannot — and with the manifest deployed they cannot, because Steam claims the controller and stops
 * emitting the virtual gamepad they read (B076). With the manifest off, nothing about the previous
 * behaviour changes.
 *
 * <p>Independently of all of the above, {@code SteamSlotDispatcher} keeps reading Steam Input
 * directly for the generic slot actions (paddles → keybinds, D030), whichever source this class
 * reports as active.
 *
 * <p>Every backend returns {@link SteamControllerHandleRef}, and handles are tagged by source, so a
 * single dispatcher can read state via {@link #readSnapshot}.
 */
public final class ControllerManager {

    public enum Source { STEAM_INPUT, SDL3, GLFW_FALLBACK, NONE }

    private ControllerManager() {}

    // Short-lived cache so a single tick/frame doesn't re-enumerate the native backends repeatedly
    // (the select screen called poll() several times per frame). Refreshed every CACHE_MS.
    private static final long CACHE_MS = 80L;
    private static List<SteamControllerHandleRef> cachedList = List.of();
    private static Source cachedSource = Source.NONE;
    private static long cacheStamp = 0L;

    /**
     * Virtual INPUT-INJECTION devices that must never be listed or auto-activated as gamepads.
     * Streaming stacks (Sunshine/Moonlight) and gamescope expose uinput devices — "Mouse passthrough
     * (absolute)", "Touch passthrough", "Pen passthrough", "extest fake device" — that SDL3/GLFW
     * enumerate as joysticks. When the real pad disconnected, auto-activation grabbed one of these
     * (a MOUSE-position "joystick") and the virtual cursor went haywire; they also flooded the
     * selector. "Steam Virtual Gamepad" is deliberately NOT filtered — that one is a real, usable
     * pad (it's how Steam Input re-exposes a claimed physical controller).
     */
    private static boolean isFakeInputDevice(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(java.util.Locale.ROOT);
        // i2c-HID naming ("NVTK0603:00 0603:F200" — the Ally's Novatek TOUCHSCREEN exposed as a
        // joystick): ACPI id + ":NN " instance. Real gamepads are never named like this. This was
        // the device auto-activation grabbed on 8BitDo disconnect — touch coords as axes = the
        // runaway virtual cursor.
        if (n.matches("^[a-z0-9]{4,8}:\\d{2}\\s.*")) return true;
        return n.contains("passthrough") || n.contains("extest") || n.contains("fake device");
    }

    private static List<SteamControllerHandleRef> dropFakes(List<SteamControllerHandleRef> in) {
        List<SteamControllerHandleRef> out = new java.util.ArrayList<>(in.size());
        for (SteamControllerHandleRef r : in) {
            if (!isFakeInputDevice(r.displayName)) out.add(r);
        }
        return out;
    }

    // Diagnostic only (feedback: "la detección de mando de la ROG solo ocurre cuando conecto el
    // 8bitdo, no se si es el mod o es Bazzite"): GlfwControllerProvider.poll() uses
    // glfwJoystickPresent, which does NOT require the mapping DB to recognize the device — so if raw
    // GLFW (BEFORE dropFakes/merge) reports 0 joysticks with only the Ally's built-in pad connected,
    // this class is filtering nothing; the OS/Steam session itself isn't exposing a device node for
    // it yet (consistent with the established finding that Steam claims a controller in exclusive HID
    // mode until its own controller subsystem "wakes up" — see steampad-controller-detection memory).
    // Logs only when the raw counts actually change, so this can't spam a healthy session.
    private static int lastRawSdlCount = -1, lastRawGlfwCount = -1;

    /** Debug-dump getters (D098): raw per-backend counts from the most recent poll. */
    public static int lastRawSdl3Count() { return lastRawSdlCount; }
    public static int lastRawGlfwCount() { return lastRawGlfwCount; }

    /** Decodes the backend tag baked into a handle's high bits, for the debug dump — this exact
     *  decode is what identified the last real bug (the 8BitDo silently living on GLFW instead of
     *  SDL3, D097); putting it IN the dump means the next report carries it automatically. */
    public static String backendTagOf(long handle) {
        if (Sdl3GamepadProvider.isSdl3Handle(handle)) return "SDL3";
        if (GlfwControllerProvider.isGlfwHandle(handle)) return "GLFW";
        return "STEAM";
    }

    private static void refreshCache() {
        long now = System.currentTimeMillis();
        if (now - cacheStamp < CACHE_MS && cacheStamp != 0L) return;
        cacheStamp = now;
        try {
            refreshCacheInner();
        } finally {
            // Resolve persistent identities for whatever ended up in the list, ALWAYS and in one
            // place, so nothing downstream can read a per-controller config before the handle it is
            // keyed by has an identity. Cheap no-op while the connected set is unchanged.
            ControllerIdentityService.refresh(cachedList);
        }
    }

    private static void refreshCacheInner() {
        // SDL3/GLFW first, always — see the class doc for why Steam Input is deliberately NOT
        // promoted to primary here even when its ActionSets are valid.
        GlobalConfig g = ConfigManager.getGlobal();
        List<SteamControllerHandleRef> rawSdl = (g.useSdl3Fallback && Sdl3GamepadProvider.isAvailable())
                ? Sdl3GamepadProvider.poll() : List.of();
        List<SteamControllerHandleRef> rawGlfw = g.useGlfwFallback
                ? GlfwControllerProvider.poll() : List.of();
        if (rawSdl.size() != lastRawSdlCount || rawGlfw.size() != lastRawGlfwCount) {
            lastRawSdlCount = rawSdl.size();
            lastRawGlfwCount = rawGlfw.size();
            dev.steampad.util.LogUtil.debug(
                    "[SteamPad] Raw controller poll changed: SDL3={} GLFW={} (before name-filtering/merge)",
                    rawSdl.size(), rawGlfw.size());
        }
        List<SteamControllerHandleRef> sdl = dropFakes(rawSdl);
        List<SteamControllerHandleRef> glfw = dropFakes(rawGlfw);
        if (!sdl.isEmpty()) {
            // MERGE, not cascade: SDL3 can miss a pad GLFW does see (real case: the ROG Ally's
            // built-in pad only shows via GLFW while an 8BitDo is on SDL3 — the all-or-nothing
            // cascade made it invisible until the 8BitDo disconnected). Append only the GLFW devices
            // that are NOT the same physical pad SDL3 already reported.
            cachedList = mergeBackends(sdl, glfw);
            cachedSource = Source.SDL3;
            return;
        }
        if (!glfw.isEmpty()) { cachedList = glfw; cachedSource = Source.GLFW_FALLBACK; return; }

        // ---- Steam Input takeover (hybrid auto-switch, v0.77.0 / D117) ---------------------------
        // Both fallbacks are blind. With the IGA manifest deployed that is the EXPECTED state, not a
        // failure: Steam then treats the title as a Steam-Input-API game, claims the pad, and stops
        // emitting the virtual gamepad SDL3/GLFW consume (B076). Steam Input is the only thing that
        // can still see the controller, so it takes over gameplay rather than merely adding paddles.
        //
        // This is what deadlocked before and produced the reported "connects but never turns green":
        // manifest on -> fallbacks blind -> this branch -> but it demanded areActionSetsValid(), which
        // is false until Steam has actually loaded the manifest (needs a Steam restart). Everything in
        // between reported NONE, i.e. no controller at all, even though Steam could see it perfectly.
        //
        // Promoting Steam Input to primary is only safe because the manifest now ships real default
        // bindings (SteamDefaultConfigGenerator) — the reason D032 rejected it was that an unbound
        // manifest made gameplay silent, which is no longer the case.
        if (SteamInputManager.isAvailable()) {
            List<SteamControllerHandleRef> steam = dropFakes(SteamInputManager.getConnectedControllers());
            if (!steam.isEmpty()) {
                cachedList = steam;
                cachedSource = Source.STEAM_INPUT;
                // Report the degraded case loudly instead of silently showing a pad that can't act:
                // the device is real and selectable, but until Steam loads the manifest none of its
                // actions resolve. Says exactly what to do rather than leaving a dead controller.
                if (!SteamInputManager.areActionSetsValid() && !warnedActionSetsMissing) {
                    warnedActionSetsMissing = true;
                    dev.steampad.util.LogUtil.warn(
                            "[SteamPad] Steam Input sees {} controller(s) but the action sets are NOT "
                            + "loaded yet, so no button will respond. Steam picks the manifest up only "
                            + "at startup: RESTART STEAM once. If it still fails afterwards, the "
                            + "manifest never reached Steam's controller_config folder — check the "
                            + "deploy lines above in this log.", steam.size());
                } else if (SteamInputManager.areActionSetsValid() && warnedActionSetsMissing) {
                    warnedActionSetsMissing = false;
                    dev.steampad.util.LogUtil.info("[SteamPad] Steam Input action sets are now valid — "
                            + "controllers are fully live on the Steam Input path.");
                }
                return;
            }
        }
        cachedList = List.of();
        cachedSource = Source.NONE;
    }

    /**
     * SDL3's list plus only the GLFW devices that aren't the SAME physical pad SDL3 already reported.
     *
     * <p>Dedup key is the USB VID/PID, not the display name. Names cannot do this job: one physical
     * pad routinely reports completely unrelated names to the two backends (real case on Windows:
     * SDL3 "8Bitdo Ultimate 2 Wireless Controller" vs GLFW "Eje 6 botón 24 controlador para juegos con
     * pulsador superior"), which is exactly how it ended up listed twice.
     *
     * <p>Matching is ONE-TO-ONE rather than "drop every GLFW pad whose VID/PID appears in SDL3": two
     * identical pads of the same model share a VID/PID, and a set-based check would silently hide the
     * second one. Each SDL3 pad absorbs at most one GLFW pad.
     *
     * <p>A pad whose VID/PID is unknown on either side (0 — e.g. GLFW's synthetic XInput GUIDs, which
     * encode no real IDs) can't be matched this way and falls back to the previous name comparison,
     * which is still right whenever both backends do agree on the name.
     */
    static List<SteamControllerHandleRef> mergeBackends(List<SteamControllerHandleRef> sdl,
                                                        List<SteamControllerHandleRef> glfw) {
        List<SteamControllerHandleRef> merged = new java.util.ArrayList<>(sdl);
        // Remaining SDL3 pads available to absorb a GLFW duplicate, keyed "vid:pid".
        java.util.Map<String, Integer> unmatched = new java.util.HashMap<>();
        java.util.Set<String> sdlNames = new java.util.HashSet<>();
        for (SteamControllerHandleRef r : sdl) {
            if (r.vendorId != 0 && r.productId != 0) {
                unmatched.merge(r.vendorId + ":" + r.productId, 1, Integer::sum);
            }
            if (r.displayName != null) sdlNames.add(r.displayName.toLowerCase(java.util.Locale.ROOT));
        }
        for (SteamControllerHandleRef r : glfw) {
            String key = r.vendorId + ":" + r.productId;
            Integer left = (r.vendorId != 0 && r.productId != 0) ? unmatched.get(key) : null;
            if (left != null && left > 0) {
                unmatched.put(key, left - 1);   // same physical pad, already listed via SDL3
                continue;
            }
            if (r.displayName != null
                    && !sdlNames.add(r.displayName.toLowerCase(java.util.Locale.ROOT))) {
                continue;                        // name fallback, for pads with no usable VID/PID
            }
            merged.add(r);
        }
        return merged;
    }

    /** One-shot latch so the "sets not loaded" warning can't spam a polling loop. */
    private static boolean warnedActionSetsMissing = false;

    /** True when controllers are currently coming from Steam Input but its action sets have not been
     *  loaded yet — the device is visible but no action will resolve. Surfaced in the debug dump and
     *  the controller UI so this state is never mistaken for a working controller. */
    public static boolean isSteamInputDegraded() {
        return cachedSource == Source.STEAM_INPUT && !SteamInputManager.areActionSetsValid();
    }

    /** Connected controllers from the highest-priority live backend (cached briefly). */
    public static List<SteamControllerHandleRef> getConnectedControllers() {
        refreshCache();
        return cachedList;
    }

    /** True when controllers are being sourced from a fallback (Steam Input is down). */
    public static boolean isUsingFallback() {
        return !SteamInputManager.isAvailable();
    }

    /** Which backend is currently providing controllers (for diagnostics/UI labels; cached briefly). */
    public static Source activeSource() {
        refreshCache();
        return cachedSource;
    }

    /**
     * Reads the normalized physical state of a fallback (SDL3/GLFW) controller into {@code out}.
     * Returns false for Steam handles (those use the Steam ActionSet pipeline, not snapshots).
     */
    public static boolean readSnapshot(long handle, GamepadSnapshot out) {
        if (Sdl3GamepadProvider.isSdl3Handle(handle)) {
            return Sdl3GamepadProvider.readSnapshot(handle, out);
        }
        if (GlfwControllerProvider.isGlfwHandle(handle)) {
            return GlfwSnapshotSource.read(GlfwControllerProvider.joystickId(handle), out);
        }
        return false;
    }

    /** True if this handle is a fallback (SDL3 or GLFW) controller driven by the snapshot dispatcher. */
    public static boolean isFallbackHandle(long handle) {
        return Sdl3GamepadProvider.isSdl3Handle(handle) || GlfwControllerProvider.isGlfwHandle(handle);
    }

    /**
     * Best-effort rumble routed to whatever backend owns the handle:
     * SDL3 → native rumble, Steam → ISteamController vibration, GLFW → no-op (GLFW has no rumble API).
     * {@code intensity} in [0,1]; {@code durationMs} used by SDL3.
     */
    public static boolean rumble(long handle, float intensity, int durationMs) {
        return rumble(handle, intensity, intensity, durationMs);
    }

    /**
     * Asymmetric rumble: separate low-frequency (the big/heavy motor — booms, thuds, rumble) and
     * high-frequency (the small/sharp motor — buzzes, taps, zaps) intensities, both in [0,1]. This is
     * the only "texture" control our hardware actually offers (SDL3 dual-motor rumble / Steamworks4j
     * triggerVibration's two channels — no true HD haptics, see B003), but leaning on the two motors
     * differently already reads as "heavy" vs "sharp" to the player at no extra cost.
     *
     * @return whether the call actually reached (and, for SDL3, was ACCEPTED by) the backend — false
     *         for handle==0, "Allow Vibration" off, GLFW (no rumble API at all), or SDL3 explicitly
     *         rejecting the request. Diagnostic use (see {@code HapticsController}) — never gates
     *         gameplay logic, only what gets logged.
     */
    public static boolean rumble(long handle, float lowFreq, float highFreq, int durationMs) {
        if (handle == 0L) return false;
        // Respect the per-controller "Allow Vibration" option (it existed but was never enforced).
        var cfg = ConfigManager.getControllerConfig(handle);
        if (cfg != null && !cfg.allowVibration) return false;
        if (Sdl3GamepadProvider.isSdl3Handle(handle)) {
            return Sdl3GamepadProvider.rumble(handle, lowFreq, highFreq, durationMs);
        } else if (GlfwControllerProvider.isGlfwHandle(handle)) {
            return false;   // GLFW exposes no rumble; nothing to do.
        } else {
            SteamHapticsService.triggerVibration(handle, (int) (lowFreq * 0xFFFF), (int) (highFreq * 0xFFFF));
            return true;   // Steamworks4j swallows its own failures; no per-call confirmation available.
        }
    }
}
