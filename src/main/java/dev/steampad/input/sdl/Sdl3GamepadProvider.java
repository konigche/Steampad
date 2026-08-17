package dev.steampad.input.sdl;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import dev.steampad.input.GamepadSnapshot;
import dev.steampad.input.GlfwControllerProvider;
import dev.steampad.platform.LinuxRuntimeInspector;
import dev.steampad.steam.SteamControllerHandleRef;
import dev.steampad.util.LogUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SDL3 gamepad backend (best-effort, via JNA). Sits between Steam Input and GLFW in the fallback
 * order: it brings SDL's broad device database and rumble to setups where Steam Input is down.
 *
 * <p>Entirely fail-safe: if libSDL3 is missing or any native call misbehaves, the backend disables
 * itself and the mod falls back to GLFW. Nothing here is on a hot path that can crash the game.
 */
public final class Sdl3GamepadProvider {

    /** ASCII "SDL3" tag in the high bytes so an SDL3 handle is distinguishable from GLFW/Steam. */
    public static final long SDL3_HANDLE_BASE = 0x53444C3300000000L;

    private static Sdl3Native sdl;
    private static boolean available = false;
    private static boolean initTried = false;

    // instanceId -> open SDL_Gamepad*
    private static final Map<Integer, Pointer> openGamepads = new HashMap<>();

    // SDL_UpdateGamepads (the event pump + hotplug/HIDAPI update) used to run on EVERY poll() and
    // EVERY readSnapshot() call — and readSnapshot alone runs per rendered FRAME (CameraController)
    // plus per tick (dispatcher) plus per frame on some screens, i.e. 3+ pumps per frame at 120 fps.
    // SDL's own internal update work (HIDAPI device polling, hotplug scans — cost that changed under
    // us with Bazzite's SDL 3.2.30 → 3.4.0 bump) multiplies by that. One pump per ~4 ms is more than
    // fresh for input (250 Hz) and bounds the native cost no matter how many callers read state
    // (SDL_GetGamepadButton/Axis just read SDL's cached state — only the pump does real work).
    private static final long PUMP_MIN_INTERVAL_NANOS = 4_000_000L;
    private static long lastPumpNanos = 0L;

    private static void pumpThrottled() {
        long now = System.nanoTime();
        if (now - lastPumpNanos < PUMP_MIN_INTERVAL_NANOS) return;
        lastPumpNanos = now;
        sdl.SDL_UpdateGamepads();
    }
    // Handles for which extra-button state has already been logged (avoid log spam).
    private static final java.util.Set<Long> extraBtnLoggedHandles = new java.util.HashSet<>();

    // ---- Debug-dump state (D098): per-open-pad capability line + version + mapping stats ---------
    private static final Map<Integer, String> openPadInfo = new HashMap<>();
    private static volatile int sdlVersionRaw = 0;
    private static volatile String mappingStats = "not loaded";

    /** SDL runtime version as "3.x.y", or "unavailable". */
    public static String sdlVersionString() {
        if (!available || sdlVersionRaw == 0) return "unavailable";
        return (sdlVersionRaw / 1000000) + "." + ((sdlVersionRaw / 1000) % 1000) + "." + (sdlVersionRaw % 1000);
    }

    /** "N added, M updated, K failed" from the last loadMappings() call. */
    public static String mappingStats() { return mappingStats; }

    /** One line per currently-open SDL pad: name + capability flags captured at open time. */
    public static java.util.List<String> describeOpenPads() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (var e : openPadInfo.entrySet()) {
            if (openGamepads.containsKey(e.getKey())) out.add(e.getValue());
        }
        return out;
    }

    private Sdl3GamepadProvider() {}

    public static boolean isAvailable() {
        return available;
    }

    public static boolean isSdl3Handle(long handle) {
        return (handle & 0xFFFFFFFF00000000L) == SDL3_HANDLE_BASE;
    }

    public static int instanceId(long handle) {
        return isSdl3Handle(handle) ? (int) (handle & 0xFFFFFFFFL) : -1;
    }

    private static long handleFor(int instanceId) {
        return SDL3_HANDLE_BASE | (instanceId & 0xFFFFFFFFL);
    }

    /** Loads libSDL3 and initializes the gamepad subsystem. Safe to call once; no-op on failure. */
    public static boolean init() {
        if (initTried) return available;
        initTried = true;
        try {
            // Multi-location search rather than a bare Native.load("SDL3") — see Sdl3Library for why
            // the OS default path alone silently loses SDL3 (and with it paddles, rumble and SDL's
            // device database) on Windows, where nothing puts an SDL3.dll on PATH.
            sdl = Sdl3Library.load(dev.steampad.config.ConfigManager.getGlobal().sdl3LibraryPath);
            if (sdl == null) {
                LogUtil.warn("[SteamPad] libSDL3 not found — falling back to GLFW, which cannot do "
                        + "rumble or back paddles and knows far fewer controllers.");
                for (String attempt : Sdl3Library.attempts()) {
                    LogUtil.warn("[SteamPad]   tried: {}", attempt);
                }
                LogUtil.warn("[SteamPad] How to fix: install Steam (its own SDL3 is used automatically), "
                        + "or install libSDL3 from your package manager, or drop the SDL3 library next to "
                        + "your .minecraft folder, or set 'sdl3LibraryPath' in config/steampad/global.json "
                        + "to its absolute path.");
                return false;
            }
            // Keep the gamepad alive when the MC window is NOT focused — this is the Controlify
            // behaviour the user wants: clicking on the desktop (or another instance) must NOT freeze
            // or glitch the controller. Must be set BEFORE init. Harmless if the symbol is absent.
            try { sdl.SDL_SetHint("SDL_JOYSTICK_ALLOW_BACKGROUND_EVENTS", "1"); } catch (Throwable ignored) {}
            // Prefer SDL's HIDAPI drivers: they are what expose paddles/extra buttons and drive rumble
            // via the device's real HID report instead of a generic/evdev fallback. The general
            // SDL_JOYSTICK_HIDAPI hint enables the family, but this project already has one falsifying
            // data point proving that alone isn't enough to guarantee a SPECIFIC brand's HIDAPI driver
            // is on (8BitDo needed its own hint set explicitly) — so every brand this mod cares about
            // gets its hint set explicitly too, rather than trusting the general hint to cascade to all
            // of them. All of these are safe no-ops on SDL builds that don't recognize a given hint name.
            try {
                sdl.SDL_SetHint("SDL_JOYSTICK_HIDAPI", "1");
                sdl.SDL_SetHint("SDL_JOYSTICK_HIDAPI_8BITDO", "1");
                sdl.SDL_SetHint("SDL_JOYSTICK_HIDAPI_XBOX", "1");
                sdl.SDL_SetHint("SDL_JOYSTICK_HIDAPI_XBOX_360", "1");
                sdl.SDL_SetHint("SDL_JOYSTICK_HIDAPI_PS4", "1");
                sdl.SDL_SetHint("SDL_JOYSTICK_HIDAPI_PS5", "1");
                sdl.SDL_SetHint("SDL_JOYSTICK_HIDAPI_SWITCH", "1");
                sdl.SDL_SetHint("SDL_JOYSTICK_HIDAPI_STEAM", "1");
                sdl.SDL_SetHint("SDL_JOYSTICK_HIDAPI_STEAMDECK", "1");
            } catch (Throwable ignored) {}
            // On Windows, SDL3 can route Xbox-compatible pads (incl. 3rd-party ones in their XInput
            // mode, e.g. an 8BitDo switched via Power+B) through either classic XInput or the newer
            // Windows.Gaming.Input backend. WGI has known compatibility gaps with XInput-clone
            // devices (misdetection, rumble not reaching the pad) that raw XInput doesn't have —
            // forcing XInput off WGI is SDL's own documented fix for exactly that symptom pattern.
            // No-op on non-Windows builds of libSDL3.
            try { sdl.SDL_SetHint("SDL_JOYSTICK_WGI", "0"); } catch (Throwable ignored) {}
            if (sdl.SDL_Init(Sdl3Native.SDL_INIT_GAMEPAD) == 0) {
                LogUtil.warn("[SteamPad] SDL3 present but SDL_Init failed: {}", safeError());
                sdl = null;
                return false;
            }
            available = true;
            int v = 0;
            try { v = sdl.SDL_GetVersion(); } catch (Throwable ignored) {}
            sdlVersionRaw = v;
            LogUtil.info("[SteamPad] SDL3 gamepad backend initialized (SDL {}.{}.{}) from {}.",
                    v / 1000000, (v / 1000) % 1000, v % 1000, Sdl3Library.loadedFrom());
            return true;
        } catch (Throwable t) {
            // Symbol mismatch or an SDL that loaded but misbehaved — fall back to GLFW.
            LogUtil.info("[SteamPad] SDL3 not usable ({}). Using GLFW fallback instead.",
                    t.getClass().getSimpleName());
            sdl = null;
            available = false;
            return false;
        }
    }

    private static String safeError() {
        try { return sdl != null ? sdl.SDL_GetError() : "?"; } catch (Throwable t) { return "?"; }
    }

    /**
     * Registers the SAME SDL-format mapping DB {@link dev.steampad.input.GamepadMappings} already
     * pushed into GLFW ({@code glfwUpdateGamepadMappings}) — no-op if SDL3 isn't available.
     *
     * <p>Root cause fixed here (feedback: "el LAG parece ser con los joysticks... pasa con el 8bitdo
     * pero con el fisico de la ROG no pasa"): confirmed by inspecting a real hardware log that the
     * 8BitDo's handle carried the GLFW tag, not the SDL3 tag, the entire session, while the ROG Ally's
     * own pad was a proper SDL3 handle throughout — meaning the 8BitDo was falling all the way back to
     * {@code GlfwSnapshotSource}'s joystick-polling path instead of SDL3's own (HIDAPI-capable,
     * purpose-built, actively-optimized) gamepad path. Root cause: {@link dev.steampad.input.GamepadMappings}
     * was calling ONLY {@code glfwUpdateGamepadMappings} — it never taught libSDL3 the SAME 8BitDo
     * mapping lines this project already curates specifically because "SDL_GetGamepads() only
     * enumerates devices SDL's OWN mapping DB recognizes as a gamepad" — without a matching SDL3
     * mapping, an 8BitDo pad SDL doesn't already know about from its own bundled DB is invisible to
     * {@code SDL_GetGamepads()} and never gets picked up here at all, forcing the GLFW fallback merge
     * in {@code ControllerManager} to supply it instead. Parses the same multi-line SDL mapping-DB
     * content (one mapping string per line, {@code #}-comments and blank lines skipped) GLFW already
     * received, via {@code SDL_AddGamepadMapping} per line — SDL3's own equivalent of
     * {@code glfwUpdateGamepadMappings}, just one line at a time (SDL3 has no bulk "from string" call,
     * only from-file/from-IO).
     */
    public static void loadMappings(String content) {
        if (!available || sdl == null || content == null || content.isEmpty()) return;
        int added = 0, updated = 0, failed = 0;
        for (String line : content.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            try {
                int r = sdl.SDL_AddGamepadMapping(trimmed);
                if (r > 0) added++;
                else if (r == 0) updated++;
                else failed++;
            } catch (Throwable t) {
                failed++;
            }
        }
        mappingStats = added + " added, " + updated + " updated, " + failed + " failed";
        LogUtil.info("[SteamPad] SDL3 gamepad mappings loaded: {} added, {} updated, {} failed.",
                added, updated, failed);
    }

    /** Enumerates connected gamepads as controller refs. Empty if SDL3 is unavailable. */
    public static List<SteamControllerHandleRef> poll() {
        List<SteamControllerHandleRef> result = new ArrayList<>();
        if (!available || sdl == null) return result;
        try {
            pumpThrottled();
            IntByReference count = new IntByReference(0);
            Pointer ids = sdl.SDL_GetGamepads(count);
            if (ids == null) return result;
            try {
                int n = count.getValue();
                int[] arr = n > 0 ? ids.getIntArray(0, n) : new int[0];
                for (int id : arr) {
                    Pointer gp = openGamepad(id);
                    String name = gp != null ? safeName(gp) : null;
                    if (name == null || name.isBlank()) name = "Gamepad " + id;
                    result.add(new SteamControllerHandleRef(
                            handleFor(id), name, resolveType(gp, name), vendorId(gp), productId(gp),
                            productVersion(gp), serial(gp)));
                }
                closeDisconnected(arr);
            } finally {
                sdl.SDL_free(ids);
            }
        } catch (Throwable t) {
            LogUtil.warn("[SteamPad] SDL3 poll failed, disabling SDL3 backend: {}", t.getMessage());
            available = false;
        }
        return result;
    }

    /**
     * Closes and forgets every gamepad SDL no longer enumerates.
     *
     * <p>{@link #openGamepad} caches an {@code SDL_Gamepad*} per instance id and, before this, nothing
     * ever removed one: a pad that disconnected stayed in the map forever, still counted as "open",
     * still polled by every {@code SDL_UpdateGamepads}, and still listed in the debug dump's open-pad
     * table as if it were live. Every unplug/replug cycle in a session added another. SDL's contract is
     * that the object stays alive until {@code SDL_CloseGamepad}, so this is a straight leak of a
     * native handle plus per-pump work that grows without bound — and it means a reconnecting pad was
     * never really re-opened from scratch, only shadowed by a second entry. Pruning here (right after
     * the authoritative enumeration, which is the only place that knows what disappeared) also
     * guarantees the next connection genuinely re-opens the device and re-reads its capabilities.
     */
    private static void closeDisconnected(int[] presentIds) {
        if (openGamepads.isEmpty()) return;
        java.util.Set<Integer> present = new java.util.HashSet<>(presentIds.length * 2);
        for (int id : presentIds) present.add(id);
        var it = openGamepads.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (present.contains(e.getKey())) continue;
            try { sdl.SDL_CloseGamepad(e.getValue()); } catch (Throwable ignored) { }
            openPadInfo.remove(e.getKey());
            extraBtnLoggedHandles.remove(handleFor(e.getKey()));
            it.remove();
            LogUtil.info("[SteamPad] SDL3 gamepad closed (id={}) — no longer connected.", e.getKey());
        }
    }

    /**
     * The instance id an open gamepad currently reports, or -1 when SDL can't tell us (old runtime,
     * missing symbol, throw). -1 means "no opinion" and is deliberately NOT treated as a mismatch.
     */
    private static int idOf(Pointer gp) {
        try {
            int id = sdl.SDL_GetGamepadID(gp);
            return id <= 0 ? -1 : id;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Instance ids whose stale-cache correction has already been logged, so this can't spam. */
    private static final java.util.Set<Integer> staleLogged = new java.util.HashSet<>();

    private static Pointer openGamepad(int instanceId) {
        Pointer gp = openGamepads.get(instanceId);
        if (gp != null) {
            // Verify the cached object still IS this instance id before trusting anything read from it.
            //
            // Why this can't be assumed: SDL3 instance ids are documented as never reused and
            // monotonically increasing, yet a real two-instance Game Mode log shows that right after
            // "SDL3 gamepad closed (id=2)" this provider reported id=1 as '8BitDo Ultimate 2 Wireless'
            // and id=3 as 'Steam Controller (HHD)' — i.e. names, VID/PID and serial attributed to the
            // WRONG pad. Everything downstream is keyed off exactly those fields: the persistent
            // identity, the per-pad config file name, and the cross-instance claim key. One bad poll
            // therefore rewrites which pad owns which configuration AND lets two instances compute the
            // same claim key for different physical pads — the reported "se pelean los controles" and
            // "los botones quedan locos" after a pad is switched off and on again.
            //
            // Re-opening is cheap (SDL refcounts an already-open gamepad) and only happens on an actual
            // mismatch. When SDL gives no answer (-1) the cached pointer is used exactly as before, so
            // a runtime without this symbol keeps the previous behaviour.
            int actual = idOf(gp);
            if (actual != -1 && actual != instanceId) {
                if (staleLogged.add(instanceId)) {
                    LogUtil.warn("[SteamPad] SDL3 gamepad cached under id={} now reports id={} — dropping "
                            + "the stale handle and re-opening so its name/VID/PID/serial belong to the "
                            + "right pad. (This is what mixes up per-pad configs between instances.)",
                            instanceId, actual);
                }
                try { sdl.SDL_CloseGamepad(gp); } catch (Throwable ignored) { }
                openGamepads.remove(instanceId);
                openPadInfo.remove(instanceId);
                gp = null;
            } else {
                return gp;
            }
        }
        try {
            gp = sdl.SDL_OpenGamepad(instanceId);
            if (gp != null) {
                openGamepads.put(instanceId, gp);
                boolean anyExtra = hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_MISC1)
                        || hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE1)
                        || hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_LEFT_PADDLE1);
                boolean rumble = hasRumble(gp);
                String nm = safeName(gp);
                int sdlType;
                try { sdlType = sdl.SDL_GetGamepadType(gp); } catch (Throwable ignored) { sdlType = Sdl3Native.SDL_GAMEPAD_TYPE_UNKNOWN; }
                SteamControllerHandleRef.ControllerType resolved = resolveType(gp, nm);
                int vid = vendorId(gp), pid = productId(gp);
                String conn = connectionState(gp);
                openPadInfo.put(instanceId, String.format(java.util.Locale.ROOT,
                        "id=%d '%s' sdlType=%d resolved=%s vid=0x%04X pid=0x%04X conn=%s MISC1=%b P1=%b P2=%b P3=%b P4=%b MISC2=%b rumble=%b",
                        instanceId, nm, sdlType, resolved, vid, pid, conn,
                        hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_MISC1),
                        hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE1),
                        hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_LEFT_PADDLE1),
                        hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE2),
                        hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_LEFT_PADDLE2),
                        hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_MISC2),
                        rumble));
                LogUtil.info("[SteamPad] SDL3 gamepad opened (id={}): '{}' sdlType={} resolved={} vid=0x{} pid=0x{} "
                        + "conn={}. Extra buttons exposed by its mapping: MISC1={} P1={} P2={} P3={} P4={} MISC2={} | rumble={}",
                        instanceId, nm, sdlType, resolved, Integer.toHexString(vid), Integer.toHexString(pid), conn,
                        hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_MISC1),
                        hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE1),
                        hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_LEFT_PADDLE1),
                        hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE2),
                        hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_LEFT_PADDLE2),
                        hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_MISC2),
                        rumble);
                // No paddles AND no rumble = SDL fell back to the generic evdev path instead of its
                // HIDAPI driver — under Flatpak that means /dev/hidraw* is blocked/unreadable.
                // Print the fix so the log is self-explanatory (verified on Bazzite + Prism Flatpak).
                // This diagnosis (evdev/hidraw/udev/Flatpak) is Linux-only vocabulary — printing it on
                // Windows would be actively wrong, so it's gated; Windows gets its own likely causes.
                if (!anyExtra && !rumble) {
                    if (LinuxRuntimeInspector.isLinux()) {
                        // Advice has to match the runtime this actually IS. Printing the Flatpak
                        // override line on a native install (the real case in a user report) sends
                        // people to a command that does nothing and hides the real cause, which on
                        // native is plain /dev/hidraw* permissions.
                        LogUtil.warn("[SteamPad] This pad was opened WITHOUT SDL's HIDAPI driver — back "
                                + "paddles/M1 and rumble need it. Detected runtime: {}. Fix on the host:",
                                LinuxRuntimeInspector.containerName());
                        if (LinuxRuntimeInspector.container() == LinuxRuntimeInspector.Container.FLATPAK) {
                            LogUtil.warn("[SteamPad]   1) flatpak override --user --device=all org.prismlauncher.PrismLauncher");
                        }
                        LogUtil.warn("[SteamPad]   {}) udev rule granting hidraw access to your user "
                                + "(vendor 2dc8 = 8BitDo), then `sudo udevadm control --reload-rules && "
                                + "sudo udevadm trigger` and replug the pad. See TODO_BLOCKERS B031.",
                                LinuxRuntimeInspector.container() == LinuxRuntimeInspector.Container.FLATPAK ? 2 : 1);
                    } else {
                        LogUtil.warn("[SteamPad] This pad reports no paddles/M1 and no rumble support. Likely "
                                + "causes on this platform: the Steam client running in the background with "
                                + "\"Xbox/Generic Configuration Support\" on (claims the HID device even with "
                                + "this mod's Steam Input off — close Steam or disable that in Steam's own "
                                + "Controller settings), or a wireless mode/driver that doesn't expose the "
                                + "extended HID report (try the pad's other pairing mode, or wired/USB).");
                    }
                }
            }
        } catch (Throwable t) {
            return null;
        }
        return gp;
    }

    private static boolean hasBtn(Pointer gp, int button) {
        try { return sdl.SDL_GamepadHasButton(gp, button) != 0; } catch (Throwable t) { return false; }
    }

    private static boolean hasRumble(Pointer gp) {
        try { return sdl.SDL_GamepadHasRumble(gp) != 0; } catch (Throwable t) { return false; }
    }

    /** Uint16 vendor ID as an unsigned int (0 = unknown to SDL), e.g. 0x2DC8 for 8BitDo. */
    private static int vendorId(Pointer gp) {
        if (gp == null) return 0;
        try { return Short.toUnsignedInt(sdl.SDL_GetGamepadVendor(gp)); } catch (Throwable t) { return 0; }
    }

    private static int productId(Pointer gp) {
        if (gp == null) return 0;
        try { return Short.toUnsignedInt(sdl.SDL_GetGamepadProduct(gp)); } catch (Throwable t) { return 0; }
    }

    private static int productVersion(Pointer gp) {
        if (gp == null) return 0;
        try { return Short.toUnsignedInt(sdl.SDL_GetGamepadProductVersion(gp)); } catch (Throwable t) { return 0; }
    }

    /** Per-unit serial, or "" — see {@link Sdl3Native#SDL_GetGamepadSerial}. Never lets a missing
     *  symbol on an old libSDL3 escape: identity resolution treats "" as "no extra signal". */
    private static String serial(Pointer gp) {
        if (gp == null) return "";
        try {
            String s = sdl.SDL_GetGamepadSerial(gp);
            return s == null ? "" : s.trim();
        } catch (Throwable t) {
            return "";
        }
    }

    /** "wired" / "wireless" / "unknown" — the actual link type SDL detected, not a guess. */
    private static String connectionState(Pointer gp) {
        if (gp == null) return "unknown";
        int s;
        try { s = sdl.SDL_GetGamepadConnectionState(gp); } catch (Throwable t) { return "unknown"; }
        if (s == Sdl3Native.SDL_JOYSTICK_CONNECTION_WIRED) return "wired";
        if (s == Sdl3Native.SDL_JOYSTICK_CONNECTION_WIRELESS) return "wireless";
        return "unknown";
    }

    private static String safeName(Pointer gp) {
        try { return sdl.SDL_GetGamepadName(gp); } catch (Throwable t) { return null; }
    }

    /**
     * Controller family for icon/theme selection, preferring SDL's own VID/PID-backed classification
     * over name-string guessing.
     *
     * <p>Root cause this fixes: a pad in an Xbox-compatible mode (e.g. an 8BitDo Ultimate 2 switched
     * to XInput mode via its 2.4GHz dongle) can report a raw name string that contains neither
     * "xbox" nor the brand — {@link GlfwControllerProvider#guessType} then falls through to GENERIC
     * even though the OS itself (and SDL's own device database) knows exactly what it is. SDL has no
     * dedicated "third-party brand" type, only the console family it's emulating — which is exactly
     * what matters functionally (button layout / prompt style) and matches what Windows itself
     * reports for the same pad. UNKNOWN (SDL genuinely doesn't know) falls back to the name
     * heuristic, which is also the only signal available for Steam Deck / Steam Controller (SDL has
     * no console-family type for those since they aren't emulating anyone else).
     *
     * <p>{@code STANDARD} maps to {@code XBOX}, but only AFTER the name has had its say. SDL's
     * "standard" IS the Xbox-style face layout (south=A, east=B, west=X, north=Y), so those are the
     * correct glyphs to draw for it — and it's what the reported 8BitDo Ultimate 2 in its
     * XInput/Power+B mode actually reports (verified in a real debug dump: {@code sdlType=1}).
     * Letting the name go first keeps a Steam Deck or a Switch-mode pad that SDL merely calls
     * "standard" from being mislabelled as Xbox.
     */
    private static SteamControllerHandleRef.ControllerType resolveType(Pointer gp, String name) {
        if (gp != null) {
            int t;
            try { t = sdl.SDL_GetGamepadType(gp); } catch (Throwable ignored) { t = Sdl3Native.SDL_GAMEPAD_TYPE_UNKNOWN; }
            if (t == Sdl3Native.SDL_GAMEPAD_TYPE_XBOX360 || t == Sdl3Native.SDL_GAMEPAD_TYPE_XBOXONE) {
                return SteamControllerHandleRef.ControllerType.XBOX;
            }
            if (t == Sdl3Native.SDL_GAMEPAD_TYPE_PS3 || t == Sdl3Native.SDL_GAMEPAD_TYPE_PS4
                    || t == Sdl3Native.SDL_GAMEPAD_TYPE_PS5) {
                return SteamControllerHandleRef.ControllerType.PLAYSTATION;
            }
            if (t == Sdl3Native.SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_PRO
                    || t == Sdl3Native.SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_JOYCON_LEFT
                    || t == Sdl3Native.SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_JOYCON_RIGHT
                    || t == Sdl3Native.SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_JOYCON_PAIR) {
                return SteamControllerHandleRef.ControllerType.SWITCH;
            }
            SteamControllerHandleRef.ControllerType byName = GlfwControllerProvider.guessType(name);
            if (byName != SteamControllerHandleRef.ControllerType.GENERIC) return byName;
            if (t == Sdl3Native.SDL_GAMEPAD_TYPE_STANDARD) {
                return SteamControllerHandleRef.ControllerType.XBOX;
            }
            return byName;
        }
        return GlfwControllerProvider.guessType(name);
    }

    /** Fills {@code out} with the current state of the SDL3 gamepad behind {@code handle}. */
    public static boolean readSnapshot(long handle, GamepadSnapshot out) {
        if (!available || sdl == null) return false;
        int id = instanceId(handle);
        Pointer gp = openGamepad(id);
        if (gp == null) return false;
        try {
            pumpThrottled();
            out.buttons[GamepadSnapshot.A]            = btn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_SOUTH);
            out.buttons[GamepadSnapshot.B]            = btn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_EAST);
            out.buttons[GamepadSnapshot.X]            = btn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_WEST);
            out.buttons[GamepadSnapshot.Y]            = btn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_NORTH);
            out.buttons[GamepadSnapshot.LEFT_BUMPER]  = btn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_LEFT_SHOULDER);
            out.buttons[GamepadSnapshot.RIGHT_BUMPER] = btn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER);
            out.buttons[GamepadSnapshot.BACK]         = btn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_BACK);
            out.buttons[GamepadSnapshot.START]        = btn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_START);
            out.buttons[GamepadSnapshot.GUIDE]        = btn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_GUIDE);
            out.buttons[GamepadSnapshot.LEFT_THUMB]   = btn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_LEFT_STICK);
            out.buttons[GamepadSnapshot.RIGHT_THUMB]  = btn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_RIGHT_STICK);
            out.buttons[GamepadSnapshot.DPAD_UP]      = btn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_DPAD_UP);
            out.buttons[GamepadSnapshot.DPAD_RIGHT]   = btn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_DPAD_RIGHT);
            out.buttons[GamepadSnapshot.DPAD_DOWN]    = btn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_DPAD_DOWN);
            out.buttons[GamepadSnapshot.DPAD_LEFT]    = btn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_DPAD_LEFT);

            // Extra buttons (back paddles / misc) — these are the 8BitDo Ultimate 2 / Pro 3 back buttons.
            // SDL only reports them when its mapping for the device/mode exposes them; otherwise they stay
            // false (harmless). guarded so an out-of-range button on older SDL never breaks the read.
            out.buttons[GamepadSnapshot.MISC1]   = btnSafe(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_MISC1);
            out.buttons[GamepadSnapshot.PADDLE1] = btnSafe(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE1);
            out.buttons[GamepadSnapshot.PADDLE2] = btnSafe(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_LEFT_PADDLE1);
            out.buttons[GamepadSnapshot.PADDLE3] = btnSafe(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE2);
            out.buttons[GamepadSnapshot.PADDLE4] = btnSafe(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_LEFT_PADDLE2);
            out.buttons[GamepadSnapshot.MISC2]   = btnSafe(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_MISC2);
            out.buttons[GamepadSnapshot.MISC3]   = btnSafe(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_MISC3);
            out.buttons[GamepadSnapshot.MISC4]   = btnSafe(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_MISC4);

            out.axes[GamepadSnapshot.AXIS_LEFT_X]  = stick(gp, Sdl3Native.SDL_GAMEPAD_AXIS_LEFTX);
            out.axes[GamepadSnapshot.AXIS_LEFT_Y]  = stick(gp, Sdl3Native.SDL_GAMEPAD_AXIS_LEFTY);
            out.axes[GamepadSnapshot.AXIS_RIGHT_X] = stick(gp, Sdl3Native.SDL_GAMEPAD_AXIS_RIGHTX);
            out.axes[GamepadSnapshot.AXIS_RIGHT_Y] = stick(gp, Sdl3Native.SDL_GAMEPAD_AXIS_RIGHTY);
            out.axes[GamepadSnapshot.AXIS_LEFT_TRIGGER]  = trigger(gp, Sdl3Native.SDL_GAMEPAD_AXIS_LEFT_TRIGGER);
            out.axes[GamepadSnapshot.AXIS_RIGHT_TRIGGER] = trigger(gp, Sdl3Native.SDL_GAMEPAD_AXIS_RIGHT_TRIGGER);

            // Diagnostic: log first time any extra button is pressed (8BitDo paddle detection).
            long h = handleFor(id);
            if (!extraBtnLoggedHandles.contains(h)) {
                boolean anyExtra = out.buttons[GamepadSnapshot.MISC1]   || out.buttons[GamepadSnapshot.PADDLE1] ||
                                   out.buttons[GamepadSnapshot.PADDLE2]  || out.buttons[GamepadSnapshot.PADDLE3] ||
                                   out.buttons[GamepadSnapshot.PADDLE4];
                if (anyExtra) {
                    extraBtnLoggedHandles.add(h);
                    LogUtil.info("[SteamPad] SDL3 extra buttons ACTIVE for gamepad {}: MISC1={} P1={} P2={} P3={} P4={}",
                        id, out.buttons[GamepadSnapshot.MISC1], out.buttons[GamepadSnapshot.PADDLE1],
                        out.buttons[GamepadSnapshot.PADDLE2], out.buttons[GamepadSnapshot.PADDLE3],
                        out.buttons[GamepadSnapshot.PADDLE4]);
                }
            }

            return true;
        } catch (Throwable t) {
            LogUtil.warn("[SteamPad] SDL3 read failed: {}", t.getMessage());
            return false;
        }
    }

    /** Best-effort rumble. Logs the reason once when the device/driver rejects it (silent F6 bug).
     *  Returns whether the native call actually reported success — {@code false} covers "no backend",
     *  "gamepad not open", a thrown exception, AND {@code SDL_RumbleGamepad} itself returning 0
     *  (rejected by the driver) — the caller (see {@code HapticsController}) surfaces this in its own
     *  diagnostics so a REPEATED rejection (as opposed to "never even attempted") is visible without
     *  depending on {@link #rumbleFailLogs}' own 3-line cap, which a single unrelated earlier failure
     *  could otherwise exhaust before the effect actually worth diagnosing ever fires. */
    public static boolean rumble(long handle, float low, float high, int durationMs) {
        if (!available || sdl == null) return false;
        Pointer gp = openGamepad(instanceId(handle));
        if (gp == null) return false;
        try {
            byte ok = sdl.SDL_RumbleGamepad(gp,
                    (short) (clamp01(low) * 0xFFFF),
                    (short) (clamp01(high) * 0xFFFF),
                    durationMs);
            if (ok == 0) {
                if (rumbleFailLogs++ < 3) {
                    // Report the REAL detected link (connectionState()) instead of guessing — "wired" vs
                    // "wireless" here is SDL's own read of the device, not an assumption about which
                    // pairing mode a proprietary 2.4GHz dongle uses. Do NOT assume a 2.4GHz dongle is
                    // more wire-like than Bluetooth: both are wireless links and either can legitimately
                    // not expose rumble in its HID report depending on the pad's firmware.
                    LogUtil.warn("[SteamPad] SDL_RumbleGamepad rejected (hasRumble={}, link={}): {} — a few "
                            + "known causes, cheapest to check first: (1) Steam client running in the "
                            + "background with its own \"Xbox/Generic Configuration Support\" still on can "
                            + "claim the HID device exclusively even with this mod's own Steam Input feature "
                            + "off — close Steam or disable that in Steam's own Controller settings and "
                            + "retry; (2) under Flatpak the sandbox can block force-feedback on the input "
                            + "device; (3) some pads genuinely don't expose rumble over ANY wireless link "
                            + "(Bluetooth or a 2.4GHz dongle alike) in some firmwares — a wired/USB "
                            + "connection is the only one that reliably rules this out.",
                            hasRumble(gp), connectionState(gp), safeError());
                }
                return false;
            }
            return true;
        } catch (Throwable t) {
            if (rumbleFailLogs++ < 3) LogUtil.warn("[SteamPad] SDL_RumbleGamepad threw: {}", t.toString());
            return false;
        }
    }

    /** Cap for rumble-failure log lines (avoid spamming once per pulse). */
    private static int rumbleFailLogs = 0;

    private static boolean btn(Pointer gp, int sdlButton) {
        return sdl.SDL_GetGamepadButton(gp, sdlButton) != 0;
    }

    /** Like {@link #btn} but never throws for higher button indices (older SDL builds). */
    private static boolean btnSafe(Pointer gp, int sdlButton) {
        try { return sdl.SDL_GetGamepadButton(gp, sdlButton) != 0; }
        catch (Throwable t) { return false; }
    }

    private static float stick(Pointer gp, int sdlAxis) {
        return Math.max(-1f, Math.min(1f, sdl.SDL_GetGamepadAxis(gp, sdlAxis) / 32767f));
    }

    private static float trigger(Pointer gp, int sdlAxis) {
        // SDL triggers report 0..32767; normalize to the GLFW-style -1..1 convention.
        float v = sdl.SDL_GetGamepadAxis(gp, sdlAxis) / 32767f;
        return Math.max(-1f, Math.min(1f, v * 2f - 1f));
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static void shutdown() {
        if (sdl == null) return;
        try {
            for (Pointer gp : openGamepads.values()) {
                try { sdl.SDL_CloseGamepad(gp); } catch (Throwable ignored) { }
            }
            openGamepads.clear();
            sdl.SDL_Quit();
        } catch (Throwable ignored) {
        } finally {
            available = false;
            sdl = null;
        }
    }
}
