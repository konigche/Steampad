package dev.steampad.input.sdl;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

/**
 * Minimal JNA binding to the SDL3 gamepad subsystem (libSDL3).
 *
 * <p>Only the handful of entry points SteamPad needs are declared. Everything is best-effort: if
 * libSDL3 is not installed, {@code Native.load} throws and the caller disables the SDL3 backend and
 * falls back to GLFW. SDL3 (not SDL2) semantics: {@code SDL_Init} returns {@code bool} (mapped as
 * byte, non-zero = success), axes are Sint16, buttons are bool.
 */
public interface Sdl3Native extends Library {

    int SDL_INIT_GAMEPAD = 0x00002000;

    // SDL3 SDL_GamepadButton enum (note: differs from GLFW ordering).
    int SDL_GAMEPAD_BUTTON_SOUTH = 0;          // A
    int SDL_GAMEPAD_BUTTON_EAST = 1;           // B
    int SDL_GAMEPAD_BUTTON_WEST = 2;           // X
    int SDL_GAMEPAD_BUTTON_NORTH = 3;          // Y
    int SDL_GAMEPAD_BUTTON_BACK = 4;
    int SDL_GAMEPAD_BUTTON_GUIDE = 5;
    int SDL_GAMEPAD_BUTTON_START = 6;
    int SDL_GAMEPAD_BUTTON_LEFT_STICK = 7;
    int SDL_GAMEPAD_BUTTON_RIGHT_STICK = 8;
    int SDL_GAMEPAD_BUTTON_LEFT_SHOULDER = 9;
    int SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER = 10;
    int SDL_GAMEPAD_BUTTON_DPAD_UP = 11;
    int SDL_GAMEPAD_BUTTON_DPAD_DOWN = 12;
    int SDL_GAMEPAD_BUTTON_DPAD_LEFT = 13;
    int SDL_GAMEPAD_BUTTON_DPAD_RIGHT = 14;
    // Extra buttons (back paddles / misc) — present on pads like the 8BitDo Ultimate 2 / Pro 3.
    int SDL_GAMEPAD_BUTTON_MISC1 = 15;
    int SDL_GAMEPAD_BUTTON_RIGHT_PADDLE1 = 16;
    int SDL_GAMEPAD_BUTTON_LEFT_PADDLE1 = 17;
    int SDL_GAMEPAD_BUTTON_RIGHT_PADDLE2 = 18;
    int SDL_GAMEPAD_BUTTON_LEFT_PADDLE2 = 19;
    int SDL_GAMEPAD_BUTTON_TOUCHPAD = 20;
    int SDL_GAMEPAD_BUTTON_MISC2 = 21;
    int SDL_GAMEPAD_BUTTON_MISC3 = 22;
    int SDL_GAMEPAD_BUTTON_MISC4 = 23;

    // SDL3 SDL_GamepadAxis enum.
    int SDL_GAMEPAD_AXIS_LEFTX = 0;
    int SDL_GAMEPAD_AXIS_LEFTY = 1;
    int SDL_GAMEPAD_AXIS_RIGHTX = 2;
    int SDL_GAMEPAD_AXIS_RIGHTY = 3;
    int SDL_GAMEPAD_AXIS_LEFT_TRIGGER = 4;
    int SDL_GAMEPAD_AXIS_RIGHT_TRIGGER = 5;

    // SDL3 SDL_GamepadType enum — SDL's OWN device classification (VID/PID-backed, from its bundled
    // gamecontrollerdb), independent of whatever string SDL_GetGamepadName returns. A pad in an
    // Xbox-compatible mode (e.g. an 8BitDo switched to XInput mode) reports XBOX360/XBOXONE here even
    // when its name string doesn't obviously say "xbox" or the brand.
    int SDL_GAMEPAD_TYPE_UNKNOWN = 0;
    int SDL_GAMEPAD_TYPE_STANDARD = 1;
    int SDL_GAMEPAD_TYPE_XBOX360 = 2;
    int SDL_GAMEPAD_TYPE_XBOXONE = 3;
    int SDL_GAMEPAD_TYPE_PS3 = 4;
    int SDL_GAMEPAD_TYPE_PS4 = 5;
    int SDL_GAMEPAD_TYPE_PS5 = 6;
    int SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_PRO = 7;
    int SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_JOYCON_LEFT = 8;
    int SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_JOYCON_RIGHT = 9;
    int SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_JOYCON_PAIR = 10;

    // SDL3 SDL_JoystickConnectionState enum — real link type, replacing name-string/comment guessing.
    int SDL_JOYSTICK_CONNECTION_INVALID = -1;
    int SDL_JOYSTICK_CONNECTION_UNKNOWN = 0;
    int SDL_JOYSTICK_CONNECTION_WIRED = 1;
    int SDL_JOYSTICK_CONNECTION_WIRELESS = 2;

    byte SDL_Init(int flags);
    /** Set an SDL hint (must be set BEFORE init for joystick hints). Returns bool. */
    byte SDL_SetHint(String name, String value);
    void SDL_Quit();
    String SDL_GetError();
    void SDL_free(Pointer mem);

    void SDL_UpdateGamepads();

    /** Returns a malloc'd array of SDL_JoystickID (Uint32); caller SDL_free's it. count out-param. */
    Pointer SDL_GetGamepads(IntByReference count);

    Pointer SDL_OpenGamepad(int instanceId);
    void SDL_CloseGamepad(Pointer gamepad);
    String SDL_GetGamepadName(Pointer gamepad);

    /** The instance id an open gamepad currently IS, or 0 on failure. Since SDL 3.2.0. Used to catch a
     *  cached {@code SDL_Gamepad*} that no longer corresponds to the id it is filed under. */
    int SDL_GetGamepadID(Pointer gamepad);

    byte SDL_GetGamepadButton(Pointer gamepad, int button);
    short SDL_GetGamepadAxis(Pointer gamepad, int axis);

    /** True if the gamepad's mapping exposes this button (diagnoses missing paddles/misc). */
    byte SDL_GamepadHasButton(Pointer gamepad, int button);

    /** True if the gamepad supports rumble (diagnoses silent vibration). */
    byte SDL_GamepadHasRumble(Pointer gamepad);

    /** SDL's own device classification (see SDL_GAMEPAD_TYPE_* above). */
    int SDL_GetGamepadType(Pointer gamepad);

    /** USB vendor/product ID (Uint16, 0 if unknown to SDL) — brand identity that survives a pad
     *  switching compatibility modes, unlike its reported name. */
    short SDL_GetGamepadVendor(Pointer gamepad);
    short SDL_GetGamepadProduct(Pointer gamepad);

    /** USB bcdDevice / product version (Uint16, 0 if unknown). Recorded with VID/PID as extra device
     *  detail for the persistent controller identity. */
    short SDL_GetGamepadProductVersion(Pointer gamepad);

    /**
     * Per-unit serial number, or NULL when SDL has none — the only thing that can tell two pads of the
     * SAME model apart (VID/PID are identical for both). SDL only knows it when the device was opened
     * through its HIDAPI driver, so callers must treat null/blank as "unavailable this session", never
     * as a difference between devices. Declared after the other entry points on purpose: a libSDL3 old
     * enough to lack the symbol only fails when this is actually called, and every call site catches.
     */
    String SDL_GetGamepadSerial(Pointer gamepad);

    /** Wired vs wireless vs unknown (see SDL_JOYSTICK_CONNECTION_* above) — the actual link type,
     *  instead of guessing it from a name string or a pairing-mode assumption. */
    int SDL_GetGamepadConnectionState(Pointer gamepad);

    /** Runtime SDL version as a single int (e.g. 3002010 = 3.2.10). */
    int SDL_GetVersion();

    /** Rumble (optional). low/high freq 0..65535, durationMs. Returns bool (byte). */
    byte SDL_RumbleGamepad(Pointer gamepad, short lowFreq, short highFreq, int durationMs);

    /** Registers one SDL-format mapping line (same syntax as gamecontrollerdb.txt / GLFW's own DB).
     *  Returns 1 = added, 0 = updated an existing entry, -1 = error (SDL_GetError() has the reason). */
    int SDL_AddGamepadMapping(String mapping);
}
