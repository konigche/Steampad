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

    byte SDL_GetGamepadButton(Pointer gamepad, int button);
    short SDL_GetGamepadAxis(Pointer gamepad, int axis);

    /** True if the gamepad's mapping exposes this button (diagnoses missing paddles/misc). */
    byte SDL_GamepadHasButton(Pointer gamepad, int button);

    /** True if the gamepad supports rumble (diagnoses silent vibration). */
    byte SDL_GamepadHasRumble(Pointer gamepad);

    /** Runtime SDL version as a single int (e.g. 3002010 = 3.2.10). */
    int SDL_GetVersion();

    /** Rumble (optional). low/high freq 0..65535, durationMs. Returns bool (byte). */
    byte SDL_RumbleGamepad(Pointer gamepad, short lowFreq, short highFreq, int durationMs);

    /** Registers one SDL-format mapping line (same syntax as gamecontrollerdb.txt / GLFW's own DB).
     *  Returns 1 = added, 0 = updated an existing entry, -1 = error (SDL_GetError() has the reason). */
    int SDL_AddGamepadMapping(String mapping);
}
