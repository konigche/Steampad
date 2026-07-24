package dev.steampad.util;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Featherweight always-on profiler for SteamPad's own tick/frame entry points (B076: "todo el
 * gamepad quedó lentísimo" reported in hardware while every static audit of the tick path came back
 * clean — the next hardware log must SHOW where the time goes instead of us guessing).
 *
 * <p>Usage: {@code long t = TickProfiler.begin(); ...work...; TickProfiler.end(SLOT_DISPATCH, t);}
 * Cost per sample is two {@code System.nanoTime()} calls and an atomic add — nanoseconds; no
 * allocation, no I/O on the hot path.
 *
 * <p>Every ~5 s it evaluates the window: if SteamPad's combined tick+frame work stayed under
 * {@link #QUIET_BUDGET_NANOS_PER_SEC} it logs NOTHING (a healthy session adds zero log noise).
 * Over budget — or if any single sample spiked past {@link #SPIKE_NANOS} — it logs ONE line with
 * the per-section breakdown (total ms, max single ms, calls), which pinpoints the hot section
 * directly from the user's ordinary latest.log.
 */
public final class TickProfiler {

    // Sections — keep in sync with NAMES.
    public static final int SLOT_DISPATCH = 0;   // SteamSlotDispatcher.tick
    public static final int PAD_TICK      = 1;   // GamepadInputDispatcher.tick
    public static final int HAPTICS       = 2;   // HapticsController.tick
    public static final int CAMERA_FRAME  = 3;   // CameraController.frame
    public static final int WIDGET_SCAN   = 4;   // ExternalWidgetScanner.discover (uncached walks)
    public static final int KEYBOARD_DRAW = 5;   // VirtualKeyboardRenderer.render
    public static final int CLIENT_TICK   = 6;   // whole SteamPad END_CLIENT_TICK body
    public static final int CURSOR_RENDER = 7;   // VirtualCursorRenderer.render (frameUpdate + draw)
    private static final int SECTIONS = 8;

    private static final String[] NAMES = {
            "slots", "padTick", "haptics", "cameraFrame", "widgetScan", "kbDraw", "clientTick", "cursorRender"};

    /** Over ~2 ms of SteamPad work per second, the breakdown is worth a log line. */
    private static final long QUIET_BUDGET_NANOS_PER_SEC = 2_000_000L;
    /** Any single sample this long is a hitch worth reporting on its own. */
    private static final long SPIKE_NANOS = 25_000_000L;
    private static final long WINDOW_NANOS = 5_000_000_000L;

    private static final AtomicLongArray totalNanos = new AtomicLongArray(SECTIONS);
    private static final AtomicLongArray maxNanos = new AtomicLongArray(SECTIONS);
    private static final AtomicLongArray calls = new AtomicLongArray(SECTIONS);
    private static volatile long windowStartNanos = 0L;
    private static volatile boolean spiked = false;

    private TickProfiler() {}

    public static long begin() {
        return System.nanoTime();
    }

    public static void end(int section, long beganAt) {
        long dt = System.nanoTime() - beganAt;
        totalNanos.addAndGet(section, dt);
        calls.incrementAndGet(section);
        if (dt > maxNanos.get(section)) maxNanos.set(section, dt);   // benign race: it's diagnostics
        if (dt > SPIKE_NANOS) spiked = true;
    }

    /** The most recent completed window's breakdown, ALWAYS stored (even when under budget and
     *  nothing was logged) — so the debug dump can show real timing without needing a laggy session
     *  to have tripped the log threshold first (D098). */
    private static volatile String lastWindowSummary = "no window completed yet";

    public static String lastWindowSummary() { return lastWindowSummary; }

    /** Call once per client tick (cheap). Evaluates + resets the 5 s window. */
    public static void tickWindow() {
        long now = System.nanoTime();
        if (windowStartNanos == 0L) { windowStartNanos = now; return; }
        long elapsed = now - windowStartNanos;
        if (elapsed < WINDOW_NANOS) return;

        long total = 0;
        for (int i = 0; i < SECTIONS; i++) total += totalNanos.get(i);
        StringBuilder sb = new StringBuilder("(")
                .append(elapsed / 1_000_000_000L).append("s window, total ")
                .append(total / 1_000_000L).append("ms):");
        for (int i = 0; i < SECTIONS; i++) {
            if (calls.get(i) == 0) continue;
            sb.append(' ').append(NAMES[i]).append('=')
              .append(totalNanos.get(i) / 1_000_000L).append("ms(max ")
              .append(maxNanos.get(i) / 1_000_000L).append("ms/x").append(calls.get(i)).append(')');
        }
        lastWindowSummary = sb.toString();
        long budget = QUIET_BUDGET_NANOS_PER_SEC * (elapsed / 1_000_000_000L);
        if (spiked || total > budget) {
            LogUtil.warn("[SteamPad] Tick profile " + lastWindowSummary);
        }
        for (int i = 0; i < SECTIONS; i++) {
            totalNanos.set(i, 0);
            maxNanos.set(i, 0);
            calls.set(i, 0);
        }
        spiked = false;
        windowStartNanos = now;
    }
}
