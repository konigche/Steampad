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
 *   <li><b>Steam Input</b> (ISteamController) only as a last resort, when neither fallback sees any
 *       physical device — extremely rare.</li>
 * </ol>
 *
 * <p><b>Why Steam Input isn't primary despite CLAUDE.md's original "Steam Input principal" rule:</b>
 * Once a valid VDF/ActionSet made Steam Input eligible to be primary, gameplay went silent —
 * Steam Input only forwards actions the user explicitly bound in Steam's own controller-layout UI,
 * so unless EVERY action (movement, camera, menus, all of BOTONES) is remapped there too, nothing
 * responds. That defeats the "just works" default the fallback backends already provide. Steam
 * Input is instead used in PARALLEL, only for the generic slot actions (paddles → keybinds, see
 * {@code SteamSlotDispatcher}, D030) — {@code SteamSlotDispatcher} reads Steam Input's state
 * directly via {@code SteamInputManager}, independent of which source this class reports as active.
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
            // cascade made it invisible until the 8BitDo disconnected). Append GLFW devices whose
            // name SDL3 didn't already report (same physical pad is visible to both backends).
            List<SteamControllerHandleRef> merged = new java.util.ArrayList<>(sdl);
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (SteamControllerHandleRef r : sdl) seen.add(r.displayName.toLowerCase(java.util.Locale.ROOT));
            for (SteamControllerHandleRef r : glfw) {
                if (seen.add(r.displayName.toLowerCase(java.util.Locale.ROOT))) merged.add(r);
            }
            cachedList = merged;
            cachedSource = Source.SDL3;
            return;
        }
        if (!glfw.isEmpty()) { cachedList = glfw; cachedSource = Source.GLFW_FALLBACK; return; }
        // Last resort: neither fallback sees any physical device. Only used when ActionSets are
        // valid, to avoid ghost/virtual devices misleading the selector.
        if (SteamInputManager.isAvailable() && SteamInputManager.areActionSetsValid()) {
            cachedList = SteamInputManager.getConnectedControllers();
            cachedSource = Source.STEAM_INPUT;
            return;
        }
        cachedList = List.of();
        cachedSource = Source.NONE;
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
    public static void rumble(long handle, float intensity, int durationMs) {
        rumble(handle, intensity, intensity, durationMs);
    }

    /**
     * Asymmetric rumble: separate low-frequency (the big/heavy motor — booms, thuds, rumble) and
     * high-frequency (the small/sharp motor — buzzes, taps, zaps) intensities, both in [0,1]. This is
     * the only "texture" control our hardware actually offers (SDL3 dual-motor rumble / Steamworks4j
     * triggerVibration's two channels — no true HD haptics, see B003), but leaning on the two motors
     * differently already reads as "heavy" vs "sharp" to the player at no extra cost.
     */
    public static void rumble(long handle, float lowFreq, float highFreq, int durationMs) {
        if (handle == 0L) return;
        // Respect the per-controller "Allow Vibration" option (it existed but was never enforced).
        var cfg = ConfigManager.getControllerConfig(handle);
        if (cfg != null && !cfg.allowVibration) return;
        if (Sdl3GamepadProvider.isSdl3Handle(handle)) {
            Sdl3GamepadProvider.rumble(handle, lowFreq, highFreq, durationMs);
        } else if (GlfwControllerProvider.isGlfwHandle(handle)) {
            // GLFW exposes no rumble; nothing to do.
        } else {
            SteamHapticsService.triggerVibration(handle, (int) (lowFreq * 0xFFFF), (int) (highFreq * 0xFFFF));
        }
    }
}
