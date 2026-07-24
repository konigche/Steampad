package dev.steampad.input;

/**
 * Tracks which physical device most recently produced input — the controller or the mouse/keyboard —
 * so the two can coexist without fighting (the core of the "ghost cursor / can't press A" bug).
 *
 * <p>Rules:
 * <ul>
 *   <li>Any controller input (stick nudge, button) marks the device GAMEPAD.</li>
 *   <li>A real physical-mouse <em>movement</em> (non-trivial delta) marks the device MOUSE.</li>
 *   <li>While GAMEPAD is active <b>inside a screen</b>, physical mouse motion is suppressed (so a
 *       resting/ghosting mouse can't steal hover or block the A press) until the user actually moves
 *       it, which flips the device back to MOUSE.</li>
 * </ul>
 * In gameplay (no screen) the mouse is never suppressed — it stays usable as an aiming device, gated
 * only by the on-screen-glyph logic (mixed input).
 */
public final class InputRouter {

    public enum Device { GAMEPAD, MOUSE }

    private static volatile Device active = Device.GAMEPAD;
    private static volatile long lastMouseMoveMs = 0L;
    private static volatile long lastGamepadMs = 0L;

    /** While the controller has been active within this window, ignore phantom mouse events. Many
     *  handheld controllers (Steam Deck / HHD) also emit trackpad/gyro mouse motion, which would
     *  otherwise flip the active device every frame → the cursor flicker the user saw. */
    private static final long GAMEPAD_HOLD_MS = 400L;

    private InputRouter() {}

    public static Device active() { return active; }
    public static boolean isGamepad() { return active == Device.GAMEPAD; }
    public static boolean isMouse() { return active == Device.MOUSE; }

    /** Called whenever the controller produces input (the dispatcher each tick it sees activity). */
    public static void markGamepad() {
        active = Device.GAMEPAD;
        lastGamepadMs = System.currentTimeMillis();
    }

    /** Called on a genuine physical-mouse movement. Ignored while the controller is actively in use. */
    public static void markMouse() {
        if (System.currentTimeMillis() - lastGamepadMs < GAMEPAD_HOLD_MS) return;
        active = Device.MOUSE;
        lastMouseMoveMs = System.currentTimeMillis();
    }

    /**
     * Unconditional switch to MOUSE — for input that can only come from a deliberate human action
     * (a large physical sweep, a physical button click). The {@link #GAMEPAD_HOLD_MS} window exists
     * to filter trackpad/gyro phantom NOISE; it must never be able to override an unambiguous human
     * mouse action, or a pad that keeps re-marking itself (stick drift, trigger noise) locks the
     * mouse out of menus permanently.
     */
    public static void markMouseForce() {
        active = Device.MOUSE;
        lastMouseMoveMs = System.currentTimeMillis();
    }

    /** True if the mouse moved within the last {@code ms} milliseconds (for transient glyph hiding). */
    public static boolean mouseMovedRecently(long ms) {
        return System.currentTimeMillis() - lastMouseMoveMs < ms;
    }

    // ---- Debug-dump getters (D098) --------------------------------------------------------------
    public static long msSinceLastMouse() {
        return lastMouseMoveMs == 0L ? -1 : System.currentTimeMillis() - lastMouseMoveMs;
    }
    public static long msSinceLastGamepad() {
        return lastGamepadMs == 0L ? -1 : System.currentTimeMillis() - lastGamepadMs;
    }
}
