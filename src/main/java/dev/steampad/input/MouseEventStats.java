package dev.steampad.input;

import dev.steampad.util.LogUtil;

/**
 * Always-on counters for every path a cursor-position event can take through {@code MouseMixin} —
 * built for the persistent "el mouse virtual laggea con el 8BitDo" report (D098), where two blind
 * fix rounds failed because nothing MEASURED what the input stream actually contained.
 *
 * <p>The specific question these counters answer: while the user steers the virtual cursor with the
 * stick, is EXTERNAL (non-injected) mouse motion arriving at the same time? On this user's setup
 * (Steam running on desktop, "Steam Virtual Gamepad" present alongside the raw pad), the prime
 * suspect is Steam Input's desktop layout emulating a mouse from the SAME physical pad SteamPad is
 * already reading raw — the classic double-input failure Controlify documents too. Each such event
 * used to force-hide the virtual cursor ({@code onPhysicalMouseTookOver}) and the next tick's
 * {@code onStickUsed} re-showed it SNAPPED to wherever the external motion had dragged the OS
 * pointer — a hide/teleport/fight cycle several times a second that reads exactly as "lag terrible".
 *
 * <p>Counters are per-window (~5 s, rolled on read/record) plus session totals, all render-thread
 * only (no atomics needed — every caller is the render thread). {@code recordSuppressedDuringStick}
 * also emits a throttled log line so a plain {@code latest.log} carries the evidence.
 */
public final class MouseEventStats {

    private static final long WINDOW_NANOS = 5_000_000_000L;
    private static final long SUPPRESS_LOG_INTERVAL_MS = 4000L;

    // Current window.
    private static long windowStartNanos = 0L;
    private static int injected, externalMoves, suppressedDuringStick, takeovers, ghostCancelled;
    private static double externalPx, suppressedPx;

    // Last COMPLETED window (what the debug dump shows — stable numbers, not a half-filled window).
    private static String lastWindowSummary = "no window completed yet";

    // Session totals (survive window rolls — a dump taken during a quiet moment still shows history).
    private static long totalInjected, totalExternalMoves, totalSuppressed, totalTakeovers;

    private static long lastSuppressLogMs = 0L;

    private MouseEventStats() {}

    private static void rollWindowIfDue() {
        long now = System.nanoTime();
        if (windowStartNanos == 0L) { windowStartNanos = now; return; }
        if (now - windowStartNanos < WINDOW_NANOS) return;
        long secs = (now - windowStartNanos) / 1_000_000_000L;
        lastWindowSummary = String.format(java.util.Locale.ROOT,
                "%ds window: injected=%d externalMoves=%d(~%.0fpx) suppressedDuringStick=%d(~%.0fpx) "
                        + "takeovers=%d ghostCancelled=%d",
                secs, injected, externalMoves, externalPx, suppressedDuringStick, suppressedPx,
                takeovers, ghostCancelled);
        injected = externalMoves = suppressedDuringStick = takeovers = ghostCancelled = 0;
        externalPx = suppressedPx = 0;
        windowStartNanos = now;
    }

    /** A cursor move WE injected (VirtualMouseController.moveOsCursor). */
    public static void recordInjected() {
        rollWindowIfDue();
        injected++;
        totalInjected++;
    }

    /** A real (non-injected) cursor move that passed the jitter filter. */
    public static void recordExternalMove(double dx, double dy) {
        rollWindowIfDue();
        externalMoves++;
        totalExternalMoves++;
        externalPx += Math.hypot(dx, dy);
    }

    /** External motion swallowed because the user was actively steering the cursor with the stick —
     *  THE signal that something (Steam Input desktop emulation, a phantom device) is double-feeding
     *  mouse motion from the same pad. Logs throttled so latest.log carries the evidence. */
    public static void recordSuppressedDuringStick(double dx, double dy) {
        rollWindowIfDue();
        suppressedDuringStick++;
        totalSuppressed++;
        suppressedPx += Math.hypot(dx, dy);
        long now = System.currentTimeMillis();
        if (now - lastSuppressLogMs > SUPPRESS_LOG_INTERVAL_MS) {
            lastSuppressLogMs = now;
            LogUtil.warn("[SteamPad] [mouse-arb] EXTERNAL mouse motion is arriving WHILE the stick "
                    + "steers the virtual cursor (suppressed {} events, ~{}px so far this window). "
                    + "This is double input: something else is translating this same controller into "
                    + "mouse movement — on this setup, most likely Steam Input's DESKTOP LAYOUT for "
                    + "this pad. Fix at the source: Steam -> Settings -> Controller -> disable Steam "
                    + "Input for this controller (or set its Desktop Layout to gamepad-only), or close "
                    + "Steam. SteamPad is suppressing the duplicates so the cursor stays usable.",
                    suppressedDuringStick, (long) suppressedPx);
        }
    }

    /** The mouse genuinely took over (virtual cursor stepped aside). */
    public static void recordTakeover() {
        rollWindowIfDue();
        takeovers++;
        totalTakeovers++;
    }

    /** A tiny/ghost move cancelled by the existing resting-mouse filter. */
    public static void recordGhostCancelled() {
        rollWindowIfDue();
        ghostCancelled++;
    }

    /** Multi-line block for the debug dump. */
    public static String dumpSummary() {
        rollWindowIfDue();
        return "Last window: " + lastWindowSummary + "\n"
                + String.format(java.util.Locale.ROOT,
                "Current window (partial): injected=%d externalMoves=%d(~%.0fpx) suppressedDuringStick=%d(~%.0fpx) takeovers=%d ghostCancelled=%d%n",
                injected, externalMoves, externalPx, suppressedDuringStick, suppressedPx, takeovers, ghostCancelled)
                + String.format(java.util.Locale.ROOT,
                "Session totals: injected=%d externalMoves=%d suppressedDuringStick=%d takeovers=%d",
                totalInjected, totalExternalMoves, totalSuppressed, totalTakeovers)
                + (totalSuppressed > 0
                    ? "\n*** DOUBLE INPUT DETECTED: external mouse motion arrived while the stick was "
                        + "steering the cursor (" + totalSuppressed + " events this session). Most likely "
                        + "Steam Input's desktop layout emulating a mouse from the same pad — disable "
                        + "Steam Input for this controller in Steam's settings. ***"
                    : "");
    }
}
