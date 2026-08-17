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

    /**
     * <b>Axial</b> (single-axis) deadzone, sign-preserving and linearly rescaled above the threshold —
     * the exact same shape as {@link #process}'s circular one, applied to one axis instead of to the
     * vector's length. Used for vehicle steering (see
     * {@code GamepadInputDispatcher.shapeMountedSteering}).
     *
     * <p><b>Why a circular deadzone cannot do this job.</b> {@link #process} zeroes the stick near its
     * CENTRE and, above that, preserves the vector's ANGLE exactly while rescaling only its length. So
     * there is no dead band around the forward AXIS: a thumb resting a few degrees off-axis while
     * pushing fully forward hands through its whole lateral component, at any magnitude. A keyboard
     * cannot produce that state at all — its lateral impulse is only ever exactly -1, 0 or +1, which is
     * why holding W drives dead straight — but an analog stick essentially never reads exactly zero,
     * and a vehicle that turns proportionally to that value integrates any residue into a permanent
     * curve ("nunca va solo adelante"). Attenuating is not enough; inside the band it has to be
     * EXACTLY zero.
     *
     * <p>Above the band the response stays linear and continuous (0 at the edge of the band, full 1.0
     * at full deflection), so steering authority is untouched — the stick keeps its analog character,
     * it just gains a real "straight ahead" the way the keyboard has one.
     *
     * <p>{@code deadzone <= 0} returns {@code v} unchanged (feature off).
     */
    public static float applyAxialDeadzone(float v, float deadzone) {
        if (deadzone <= 0f) return v;
        float shaped = MathUtil.applyCircularDeadzone(Math.abs(v), deadzone);
        return v < 0f ? -shaped : shaped;
    }
}
