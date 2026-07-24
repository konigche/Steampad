package dev.steampad.util;

public final class TimeUtil {

    private TimeUtil() {}

    public static long nowMs() {
        return System.currentTimeMillis();
    }

    public static boolean hasElapsed(long startMs, long durationMs) {
        return nowMs() - startMs >= durationMs;
    }

    public static long ticksToMs(int ticks) {
        return ticks * 50L;
    }

    public static int msToTicks(long ms) {
        return (int) (ms / 50L);
    }
}
