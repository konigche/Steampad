package dev.steampad.input;

import dev.steampad.util.MathUtil;
import net.minecraft.client.Minecraft;

/**
 * Converts gyroscope data from Steam Input into camera movement.
 *
 * Supports Relative mode (gyro data is delta rotation per frame)
 * and Absolute mode (gyro data is absolute orientation).
 *
 * Yaw modes:
 * - YAW_ONLY: use only the yaw axis for horizontal look
 * - ROLL_ONLY: use roll axis for horizontal look
 * - BOTH: combine yaw and roll
 */
public final class GyroHandler {

    public enum GyroMode { RELATIVE, ABSOLUTE }
    public enum YawMode { YAW_ONLY, ROLL_ONLY, BOTH }
    public enum RequireButton { OFF, HOLD, INVERT, TOGGLE }

    private static GyroMode mode = GyroMode.RELATIVE;
    private static YawMode yawMode = YawMode.YAW_ONLY;
    private static RequireButton requireButton = RequireButton.OFF;
    private static boolean flickStickEnabled = false;
    private static float sensitivity = 1.0f;
    private static boolean invertX = false;
    private static boolean invertY = false;

    private static boolean gyroToggleState = false; // for TOGGLE mode
    private static boolean gyroButtonHeldLastTick = false;

    private GyroHandler() {}

    public static void configure(GyroMode m, YawMode y, RequireButton r,
                                  float sens, boolean invX, boolean invY, boolean flick) {
        mode = m;
        yawMode = y;
        requireButton = r;
        sensitivity = sens;
        invertX = invX;
        invertY = invY;
        flickStickEnabled = flick;
    }

    /**
     * Process gyro data for this tick.
     *
     * @param gyroX       gyro X axis (pitch)
     * @param gyroY       gyro Y axis (yaw or roll depending on mode)
     * @param buttonHeld  whether the gyro activation button is held
     */
    public static void process(float gyroX, float gyroY, boolean buttonHeld) {
        boolean shouldApply = shouldApplyGyro(buttonHeld);
        if (!shouldApply) return;

        float deltaYaw = 0f;
        float deltaPitch = gyroX;

        deltaYaw = switch (yawMode) {
            case YAW_ONLY -> gyroY;
            case ROLL_ONLY -> gyroX; // simplified: roll maps to yaw
            case BOTH -> gyroY + gyroX * 0.3f;
        };

        if (invertX) deltaYaw = -deltaYaw;
        if (invertY) deltaPitch = -deltaPitch;

        deltaYaw *= sensitivity;
        deltaPitch *= sensitivity;

        applyToCamera(deltaYaw, deltaPitch);
        gyroButtonHeldLastTick = buttonHeld;
    }

    private static boolean shouldApplyGyro(boolean buttonHeld) {
        return switch (requireButton) {
            case OFF -> true;
            case HOLD -> buttonHeld;
            case INVERT -> !buttonHeld;
            case TOGGLE -> {
                if (buttonHeld && !gyroButtonHeldLastTick) {
                    gyroToggleState = !gyroToggleState;
                }
                yield gyroToggleState;
            }
        };
    }

    private static void applyToCamera(float deltaYaw, float deltaPitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.turn(deltaYaw, deltaPitch);
    }

    public static void setToggleState(boolean state) {
        gyroToggleState = state;
    }
}
