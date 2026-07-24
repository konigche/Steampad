package dev.steampad.input;

/**
 * Shared analog movement intent written by {@link GamepadInputDispatcher} (the single reader of the
 * controller) and applied to the player's movement by the KeyboardInput mixin. Keeping it here lets
 * the dispatcher own all controller reads while the mixin only consumes a normalized result.
 *
 * <p>{@code active} is true only while the dispatcher is actively driving gameplay; it is cleared
 * when a screen opens, the radial is up, the player is gone, or the controller disconnects — so the
 * mixin never keeps walking the player in those states.
 */
public final class ControllerInputState {

    private static volatile boolean active = false;
    private static volatile float forward = 0f;    // +forward, -backward
    private static volatile float sideways = 0f;    // +left, -right (vanilla movementVector convention)
    private static volatile boolean jump = false;
    private static volatile boolean sneak = false;
    private static volatile boolean sprint = false;

    private ControllerInputState() {}

    public static void set(float sidewaysValue, float forwardValue, boolean jumpValue,
                           boolean sneakValue, boolean sprintValue) {
        sideways = clamp(sidewaysValue);
        forward = clamp(forwardValue);
        jump = jumpValue;
        sneak = sneakValue;
        sprint = sprintValue;
        active = true;
    }

    public static void clear() {
        active = false;
        forward = 0f;
        sideways = 0f;
        jump = false;
        sneak = false;
        sprint = false;
    }

    public static boolean isActive() { return active; }
    public static float forward() { return forward; }
    public static float sideways() { return sideways; }
    public static boolean jump() { return jump; }
    public static boolean sneak() { return sneak; }
    public static boolean sprint() { return sprint; }

    private static float clamp(float v) {
        return v < -1f ? -1f : (v > 1f ? 1f : v);
    }
}
