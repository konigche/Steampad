package dev.steampad.util;

public final class MathUtil {

    private MathUtil() {}

    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp(t, 0f, 1f);
    }

    /** Circular (Euclidean) deadzone. Returns magnitude clamped [0,1] after deadzone. */
    public static float applyCircularDeadzone(float magnitude, float deadzone) {
        if (magnitude <= deadzone) return 0f;
        if (deadzone >= 1f) return 0f;
        return clamp((magnitude - deadzone) / (1f - deadzone), 0f, 1f);
    }

    public static float magnitude(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }

    public static float[] normalizeDeadzone(float x, float y, float deadzone) {
        float mag = magnitude(x, y);
        float scaled = applyCircularDeadzone(mag, deadzone);
        if (mag == 0f) return new float[]{0f, 0f};
        float nx = x / mag * scaled;
        float ny = y / mag * scaled;
        return new float[]{nx, ny};
    }
}
