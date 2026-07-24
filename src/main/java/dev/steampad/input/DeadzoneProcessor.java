package dev.steampad.input;

import dev.steampad.util.MathUtil;

/**
 * Applies circular deadzone and linear rescaling to analog stick values.
 */
public final class DeadzoneProcessor {

    private DeadzoneProcessor() {}

    /**
     * Applies circular deadzone to (x, y) and rescales output to full [−1, 1] range.
     *
     * @param x        raw X axis value in [−1, 1]
     * @param y        raw Y axis value in [−1, 1]
     * @param deadzone deadzone radius in [0, 1)
     * @return processed [x, y] pair
     */
    public static float[] process(float x, float y, float deadzone) {
        return MathUtil.normalizeDeadzone(x, y, deadzone);
    }

    /**
     * Processes a 2-element float array in-place equivalent.
     */
    public static float[] process(float[] xy, float deadzone) {
        return process(xy[0], xy[1], deadzone);
    }

    /**
     * Applies a simple threshold to a trigger or button analog value.
     * Returns 1.0 if value >= threshold, else 0.0.
     */
    public static float applyThreshold(float value, float threshold) {
        return value >= threshold ? 1.0f : 0.0f;
    }

    /**
     * Scales a sensitivity multiplier onto a processed axis value.
     */
    public static float applySensitivity(float value, float sensitivity) {
        return MathUtil.clamp(value * sensitivity, -1f, 1f);
    }
}
