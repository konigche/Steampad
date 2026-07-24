package dev.steampad.input.sdl;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import dev.steampad.input.GamepadSnapshot;
import dev.steampad.input.GlfwControllerProvider;
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
            sdl = Native.load("SDL3", Sdl3Native.class);
            // Keep the gamepad alive when the MC window is NOT focused — this is the Controlify
            // behaviour the user wants: clicking on the desktop (or another instance) must NOT freeze
            // or glitch the controller. Must be set BEFORE init. Harmless if the symbol is absent.
            try { sdl.SDL_SetHint("SDL_JOYSTICK_ALLOW_BACKGROUND_EVENTS", "1"); } catch (Throwable ignored) {}
            // Prefer SDL's HIDAPI drivers: they are what expose the back paddles / M1-M2 of pads
            // like the 8BitDo Ultimate 2 (the evdev mapping doesn't), and drive rumble via hidraw.
            // Unknown hints are ignored by older SDL builds — safe to set unconditionally.
            try {
                sdl.SDL_SetHint("SDL_JOYSTICK_HIDAPI", "1");
                sdl.SDL_SetHint("SDL_JOYSTICK_HIDAPI_8BITDO", "1");
            } catch (Throwable ignored) {}
            if (sdl.SDL_Init(Sdl3Native.SDL_INIT_GAMEPAD) == 0) {
                LogUtil.warn("[SteamPad] SDL3 present but SDL_Init failed: {}", safeError());
                sdl = null;
                return false;
            }
            available = true;
            int v = 0;
            try { v = sdl.SDL_GetVersion(); } catch (Throwable ignored) {}
            sdlVersionRaw = v;
            LogUtil.info("[SteamPad] SDL3 gamepad backend initialized (SDL {}.{}.{}).",
                    v / 1000000, (v / 1000) % 1000, v % 1000);
            return true;
        } catch (Throwable t) {
            // libSDL3 not installed, or symbol mismatch — fall back to GLFW silently-ish.
            LogUtil.info("[SteamPad] SDL3 not available ({}). Using GLFW fallback instead.",
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
                            handleFor(id), name, GlfwControllerProvider.guessType(name)));
                }
            } finally {
                sdl.SDL_free(ids);
            }
        } catch (Throwable t) {
            LogUtil.warn("[SteamPad] SDL3 poll failed, disabling SDL3 backend: {}", t.getMessage());
            available = false;
        }
        return result;
    }

    private static Pointer openGamepad(int instanceId) {
        Pointer gp = openGamepads.get(instanceId);
        if (gp != null) return gp;
        try {
            gp = sdl.SDL_OpenGamepad(instanceId);
            if (gp != null) {
                openGamepads.put(instanceId, gp);
                boolean anyExtra = hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_MISC1)
                        || hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE1)
                        || hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_LEFT_PADDLE1);
                boolean rumble = hasRumble(gp);
                openPadInfo.put(instanceId, String.format(java.util.Locale.ROOT,
                        "id=%d '%s' MISC1=%b P1=%b P2=%b P3=%b P4=%b MISC2=%b rumble=%b",
                        instanceId, safeName(gp),
                        hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_MISC1),
                        hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE1),
                        hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_LEFT_PADDLE1),
                        hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE2),
                        hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_LEFT_PADDLE2),
                        hasBtn(gp, Sdl3Native.SDL_GAMEPAD_BUTTON_MISC2),
                        rumble));
                LogUtil.info("[SteamPad] SDL3 gamepad opened (id={}): '{}'. Extra buttons exposed by "
                        + "its mapping: MISC1={} P1={} P2={} P3={} P4={} MISC2={} | rumble={}",
                        instanceId, safeName(gp),
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
                if (!anyExtra && !rumble) {
                    LogUtil.warn("[SteamPad] This pad was opened WITHOUT SDL's HIDAPI driver — back "
                            + "paddles/M1 and rumble need it. Fix on the host system:");
                    LogUtil.warn("[SteamPad]   1) flatpak override --user --device=all org.prismlauncher.PrismLauncher");
                    LogUtil.warn("[SteamPad]   2) udev rule for hidraw access (vendor 2dc8 = 8BitDo), "
                            + "then reload udev and replug the dongle. See TODO_BLOCKERS B031.");
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

    private static String safeName(Pointer gp) {
        try { return sdl.SDL_GetGamepadName(gp); } catch (Throwable t) { return null; }
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

    /** Best-effort rumble. Logs the reason once when the device/driver rejects it (silent F6 bug). */
    public static void rumble(long handle, float low, float high, int durationMs) {
        if (!available || sdl == null) return;
        Pointer gp = openGamepad(instanceId(handle));
        if (gp == null) return;
        try {
            byte ok = sdl.SDL_RumbleGamepad(gp,
                    (short) (clamp01(low) * 0xFFFF),
                    (short) (clamp01(high) * 0xFFFF),
                    durationMs);
            if (ok == 0 && rumbleFailLogs++ < 3) {
                LogUtil.warn("[SteamPad] SDL_RumbleGamepad rejected (hasRumble={}): {} — under Flatpak "
                        + "this usually means the sandbox blocks force-feedback on the input device.",
                        hasRumble(gp), safeError());
            }
        } catch (Throwable t) {
            if (rumbleFailLogs++ < 3) LogUtil.warn("[SteamPad] SDL_RumbleGamepad threw: {}", t.toString());
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
