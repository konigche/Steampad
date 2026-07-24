package dev.steampad.input;

/**
 * Backend-agnostic snapshot of a gamepad's physical state for one tick.
 *
 * <p>Both the GLFW and SDL3 fallback backends normalize their device reads into this shape so a
 * single dispatcher ({@link GamepadInputDispatcher}) can drive the game regardless of source. The
 * button and axis indices intentionally mirror GLFW's standard gamepad mapping so a GLFW read is a
 * 1:1 copy and SDL3 maps onto the same well-known layout.
 */
public final class GamepadSnapshot {

    // Button indices (same order/meaning as GLFW_GAMEPAD_BUTTON_*).
    public static final int A = 0, B = 1, X = 2, Y = 3,
            LEFT_BUMPER = 4, RIGHT_BUMPER = 5,
            BACK = 6, START = 7, GUIDE = 8,
            LEFT_THUMB = 9, RIGHT_THUMB = 10,
            DPAD_UP = 11, DPAD_RIGHT = 12, DPAD_DOWN = 13, DPAD_LEFT = 14;
    // Extra buttons beyond GLFW's standard set — the back paddles / misc buttons that controllers like
    // the 8BitDo Ultimate 2 / Pro 3 expose. Only SDL3 reads these (GLFW has no concept of them, so they
    // stay false on the GLFW path). MISC1 = the single extra button; PADDLE1..4 = back paddles.
    public static final int MISC1 = 15,
            PADDLE1 = 16, PADDLE2 = 17, PADDLE3 = 18, PADDLE4 = 19,
            MISC2 = 20, MISC3 = 21, MISC4 = 22;
    public static final int BUTTON_COUNT = 23;
    /** Number of standard (GLFW) buttons — the GLFW path only fills these. */
    public static final int STANDARD_BUTTON_COUNT = 15;

    // Axis indices (same order/meaning as GLFW_GAMEPAD_AXIS_*). Triggers rest at -1, pressed at +1.
    public static final int AXIS_LEFT_X = 0, AXIS_LEFT_Y = 1,
            AXIS_RIGHT_X = 2, AXIS_RIGHT_Y = 3,
            AXIS_LEFT_TRIGGER = 4, AXIS_RIGHT_TRIGGER = 5;
    public static final int AXIS_COUNT = 6;

    public final boolean[] buttons;
    public final float[] axes;

    public GamepadSnapshot() {
        this.buttons = new boolean[BUTTON_COUNT];
        this.axes = new float[AXIS_COUNT];
        // Triggers idle low.
        this.axes[AXIS_LEFT_TRIGGER] = -1f;
        this.axes[AXIS_RIGHT_TRIGGER] = -1f;
    }

    public boolean button(int i) {
        return i >= 0 && i < buttons.length && buttons[i];
    }

    public float axis(int i) {
        return (i >= 0 && i < axes.length) ? axes[i] : 0f;
    }
}
