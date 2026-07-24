package dev.steampad.compat;

import dev.steampad.util.LogUtil;

/**
 * Fallback controller detection using SDL3 via JNA.
 * Only activated when Steam Input is unavailable (Steam not running, or
 * GAMESCOPE_WAYLAND_DISPLAY present but ISteamInput returning 0 controllers).
 *
 * Current status: STUB — SDL3/JNA binding is not yet implemented.
 * See TODO_BLOCKERS.md B001 for rationale and plan.
 */
public final class SDLFallbackProvider {

    private static boolean enabled = false;

    private SDLFallbackProvider() {}

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Attempt to initialize SDL3 as fallback.
     * Returns true if SDL3 is available and at least one controller is found.
     */
    public static boolean tryInit() {
        LogUtil.info("SDL3 fallback requested. Current status: not implemented.");
        LogUtil.info("To add SDL3 fallback: add net.java.dev.jna:jna and SDL3 bindings, then implement this class.");
        // Future implementation:
        // 1. Load libSDL3.so via JNA
        // 2. Call SDL_Init(SDL_INIT_GAMEPAD)
        // 3. Enumerate gamepads via SDL_GetGamepads()
        // 4. Expose them as SteamControllerHandleRef with handle = SDL joystick ID
        return false;
    }

    public static void shutdown() {
        if (!enabled) return;
        // SDL_Quit() here
        enabled = false;
    }

    public static int getControllerCount() {
        return 0;
    }
}
