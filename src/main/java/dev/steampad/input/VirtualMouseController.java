package dev.steampad.input;

import dev.steampad.util.MathUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.MouseInput;

/**
 * Controller-driven virtual cursor, modeled on Controlify's behaviour.
 *
 * <p>Three user-selectable modes (cycled with Select):
 * <ul>
 *   <li><b>OFF</b> — cursor never shows; navigate purely with the D-pad (focus).</li>
 *   <li><b>ON</b> — cursor always shows; the controller owns the pointer.</li>
 *   <li><b>AUTO</b> — cursor appears when you nudge the left stick and hides when you use the D-pad,
 *       so it coexists with focus navigation.</li>
 * </ul>
 *
 * <p>While the cursor is shown, the physical mouse is suppressed (see MouseMixin + {@link #INJECTING})
 * so the two don't fight: the cursor moves the OS pointer through {@code onCursorPos} with a guard
 * flag, and physical movements are ignored. When the cursor is hidden, the physical mouse works
 * normally — full coexistence with mouse + keyboard.
 *
 * <p>Performance: the OS pointer is only repositioned when the virtual position actually changes by
 * a meaningful amount (see {@link #MOVE_EPSILON}). {@code onCursorPos} re-runs MC's full hover/move
 * handling, so calling it every tick (even while the stick was still) was the main source of the
 * inventory/menu lag the user reported. Sensitivity is applied exactly once, here.
 */
public final class VirtualMouseController {

    public enum Mode { OFF, ON, AUTO }

    /** True while WE are moving the OS cursor (so MouseMixin lets it through; physical moves don't). */
    public static volatile boolean INJECTING = false;

    /**
     * Pixels-per-SECOND at full stick deflection when sensitivity == 1.0 (integrated per frame).
     * Tuned to 0.4× the previous feel (was 850) per user request: sensitivity 1.0 is now noticeably
     * slower/steadier, and 1.0 remains the default config value.
     */
    private static final double BASE_SPEED_PER_SEC = 340.0;
    /** Don't bother moving the OS pointer for sub-pixel changes (avoids onCursorPos churn). */
    private static final double MOVE_EPSILON = 0.4;

    private static Mode mode = Mode.AUTO;
    private static boolean shown = false;        // is the cursor currently visible/active?
    private static double virtualX = 0.0;
    private static double virtualY = 0.0;
    private static double lastSentX = -1e9;
    private static double lastSentY = -1e9;
    /** Wall-clock throttle for {@code onCursorPos} dispatch, independent of {@link #MOVE_EPSILON}
     *  (see {@link #syncOsCursor()}'s doc for why the epsilon alone doesn't bound this). */
    private static long lastCursorPosNanos = 0L;
    private static float sensitivity = 1.0f;
    /** True if the cursor was nudged by the stick this tick (gates snap so a resting cursor is quiet). */
    private static boolean movedThisTick = false;

    // Per-frame smooth motion: the dispatcher sets a TARGET velocity each tick (20 Hz); frameUpdate()
    // eases the actual velocity toward it and integrates every rendered frame using real delta-time, so
    // the cursor glides naturally instead of stepping at 20 Hz (Controlify-style).
    private static double velX = 0.0, velY = 0.0;             // current px/sec
    private static double targetVelX = 0.0, targetVelY = 0.0; // px/sec requested by the stick
    private static long lastFrameNanos = 0L;
    /** Velocity smoothing time-constant (s). Small = responsive; just enough to remove stepping. */
    private static final double VEL_TAU = 0.035;

    /** Per-screen-class memory of the chosen mode (item: "remember per context"). AUTO is the default. */
    private static final java.util.Map<String, Mode> contextModes = new java.util.HashMap<>();
    private static String currentContext = "";
    /** When the mode last changed (ms) — the on-screen badge only shows briefly after a change. */
    private static long lastModeChangeMs = 0L;
    private static final long BADGE_MS = 2500L;

    private VirtualMouseController() {}

    // — mode —
    public static Mode getMode() { return mode; }
    public static void setMode(Mode m) {
        mode = m;
        rememberContext();
        markModeChanged();
        refreshShown();
    }
    public static Mode cycleMode() {
        mode = switch (mode) { case OFF -> Mode.ON; case ON -> Mode.AUTO; case AUTO -> Mode.OFF; };
        rememberContext();
        markModeChanged();
        refreshShown();
        return mode;
    }

    private static void rememberContext() {
        if (!currentContext.isEmpty()) contextModes.put(currentContext, mode);
    }
    private static void markModeChanged() { lastModeChangeMs = System.currentTimeMillis(); }

    /** True while the mode badge should be visible (shortly after a change). */
    public static boolean badgeVisible() {
        return System.currentTimeMillis() - lastModeChangeMs < BADGE_MS;
    }
    /** 0..1 fade for the badge (fades out over the last 500ms). */
    public static float badgeAlpha() {
        long age = System.currentTimeMillis() - lastModeChangeMs;
        if (age >= BADGE_MS) return 0f;
        long fade = BADGE_MS - age;
        return fade >= 500 ? 1f : fade / 500f;
    }

    /** Whether the cursor is currently shown (and thus owns the pointer / suppresses physical mouse). */
    public static boolean isShown() { return shown; }

    /** True if the stick moved the cursor this tick (reset by callers after reading). */
    public static boolean consumeMoved() {
        boolean m = movedThisTick;
        movedThisTick = false;
        return m;
    }

    /** In AUTO, the left stick reveals the cursor (waking it where the physical pointer was). */
    public static void onStickUsed() {
        if (mode == Mode.AUTO && !shown) { shown = true; syncFromOsMouse(); clearScreenFocus(); }
    }

    /**
     * The physical mouse took over (a real movement, S1). In AUTO, hide the virtual cursor so the OS
     * pointer reappears and the user selects with the real mouse — until the stick wakes it again. In
     * ON the user forced the cursor on, so we don't relinquish; in OFF it's already hidden.
     */
    public static void onPhysicalMouseTookOver() {
        if (mode == Mode.AUTO) shown = false;
    }

    /** Snap the virtual position to the current OS pointer (seamless hand-off mouse → stick). */
    private static void syncFromOsMouse() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null || mc.mouse == null) return;
        double scale = mc.getWindow().getScaleFactor();
        if (scale <= 0) scale = 1;
        virtualX = MathUtil.clamp(mc.mouse.getX() / scale, 0, mc.currentScreen.width);
        virtualY = MathUtil.clamp(mc.mouse.getY() / scale, 0, mc.currentScreen.height);
        lastSentX = virtualX;
        lastSentY = virtualY;
    }

    /** In AUTO, using the D-pad hides the cursor and hands navigation to focus. */
    public static void onDpadUsed() {
        if (mode == Mode.AUTO) { shown = false; }
    }

    private static void refreshShown() {
        boolean wasShown = shown;
        shown = (mode == Mode.ON);   // ON = always; OFF = never; AUTO resolves at runtime
        if (shown && !wasShown) clearScreenFocus();   // no stale focus highlight under the cursor
    }

    /**
     * Called when a screen opens. Restores the mode remembered for this screen's class (default AUTO),
     * so enabling/disabling the cursor in one window doesn't change it in others (item: per-context).
     */
    public static void onScreenOpened(net.minecraft.client.gui.screen.Screen screen, boolean container) {
        currentContext = screen == null ? "" : screen.getClass().getName();
        mode = contextModes.getOrDefault(currentContext, Mode.AUTO);
        switch (mode) {
            case ON -> shown = true;
            case OFF -> shown = false;
            case AUTO -> shown = container;   // containers (inventory) start in cursor mode
        }
        velX = 0.0; velY = 0.0; targetVelX = 0.0; targetVelY = 0.0;   // don't carry velocity across screens
        lastFrameNanos = 0L;           // reset the frame clock so dt starts fresh (no jump)
        centerOnScreen();
    }

    public static void setSensitivity(float s) { sensitivity = s; }
    public static double getX() { return virtualX; }
    public static double getY() { return virtualY; }

    // Kept for compatibility with existing callers.
    public static boolean isActive() { return shown; }
    public static void setActive(boolean value) { shown = value; }
    public static void toggle() { cycleMode(); }

    /**
     * Sets the cursor velocity from the raw stick (each component in [-1,1]); applied per frame by
     * {@link #frameUpdate()}. Called once per tick by the dispatcher.
     */
    public static void setStick(float rawDx, float rawDy) {
        targetVelX = rawDx * sensitivity * BASE_SPEED_PER_SEC;
        targetVelY = rawDy * sensitivity * BASE_SPEED_PER_SEC;
    }

    /**
     * Eases velocity toward the stick target and integrates with real frame delta-time (smooth,
     * frame-rate independent). Call once per rendered frame while a screen is open. Returns true if
     * the cursor actually moved.
     */
    public static boolean frameUpdate() {
        long now = System.nanoTime();
        long prev = lastFrameNanos;
        lastFrameNanos = now;
        if (prev == 0L) return false;
        double dt = (now - prev) / 1.0e9;
        if (dt <= 0) return false;
        if (dt > 0.1) dt = 0.1;   // clamp after a stall/pause so the cursor doesn't jump

        // Exponential easing of velocity toward the target (natural ramp up/down).
        double a = 1.0 - Math.exp(-dt / VEL_TAU);
        velX += (targetVelX - velX) * a;
        velY += (targetVelY - velY) * a;
        if (Math.abs(velX) < 1.0 && targetVelX == 0.0) velX = 0.0;
        if (Math.abs(velY) < 1.0 && targetVelY == 0.0) velY = 0.0;

        if (!shown || (velX == 0.0 && velY == 0.0)) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null) return false;
        virtualX = MathUtil.clamp(virtualX + velX * dt, 0, mc.currentScreen.width);
        virtualY = MathUtil.clamp(virtualY + velY * dt, 0, mc.currentScreen.height);
        movedThisTick = true;
        syncOsCursor();
        return true;
    }

    /** Legacy per-tick move (kept for compatibility); prefer {@link #setStick}+{@link #frameUpdate}. */
    public static void update(float rawDx, float rawDy) {
        setStick(rawDx, rawDy);
    }

    /** True if the stick is currently deflecting the cursor (used to gate settle-snap). */
    public static boolean isMovingByStick() { return targetVelX != 0.0 || targetVelY != 0.0; }

    public static void setPosition(double x, double y) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null) return;
        virtualX = MathUtil.clamp(x, 0, mc.currentScreen.width);
        virtualY = MathUtil.clamp(y, 0, mc.currentScreen.height);
        syncOsCursor();
    }

    public static void centerOnScreen() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null) return;
        virtualX = mc.currentScreen.width / 2.0;
        virtualY = mc.currentScreen.height / 2.0;
        syncOsCursor();
    }

    /** Dispatch {@code onCursorPos} at most this often (B078: the "enormous lag while moving the
     *  virtual mouse/scrolling with the stick, but D-pad/buttons are fine" report). {@link
     *  #MOVE_EPSILON} alone does NOT bound the call rate: at BASE_SPEED_PER_SEC=340px/s any real
     *  deflection moves well over 0.4px every single rendered frame, so while actively moving,
     *  onCursorPos — which re-runs MC's FULL hover/tooltip pass over the screen — fired completely
     *  uncapped at the render frame rate (100-200+ Hz on a light scene, and each call gets more
     *  expensive with a big modded widget tree like REI's). ~90 Hz is far more than hover feedback
     *  needs to feel instant, and the drawn dot never waits on this: {@link VirtualCursorRenderer}
     *  reads {@link #virtualX}/{@link #virtualY} directly every frame regardless, so throttling only
     *  the vanilla hover dispatch is invisible to the eye but bounds its worst-case cost hard. */
    private static final long CURSOR_POS_MIN_INTERVAL_NANOS = 11_000_000L;   // ~90 Hz

    /** Move the real cursor only when it actually shifted AND enough time passed since the last
     *  dispatch (cheap when the stick is still; rate-capped while actively moving). */
    private static void syncOsCursor() {
        if (Math.abs(virtualX - lastSentX) < MOVE_EPSILON && Math.abs(virtualY - lastSentY) < MOVE_EPSILON) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastCursorPosNanos < CURSOR_POS_MIN_INTERVAL_NANOS) return;
        lastCursorPosNanos = now;
        lastSentX = virtualX;
        lastSentY = virtualY;
        moveOsCursor(virtualX, virtualY);
    }

    /** Move the real cursor to (x,y) in scaled coords, flagged so the mixin allows this move. */
    private static void moveOsCursor(double x, double y) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.mouse == null) return;
        MouseEventStats.recordInjected();
        double scale = mc.getWindow().getScaleFactor();
        INJECTING = true;
        try {
            mc.mouse.onCursorPos(mc.getWindow().getHandle(), x * scale, y * scale);
        } finally {
            INJECTING = false;
        }
    }

    // ---- Debug-dump state getters (D098 — the dump must show the live arbitration state) --------
    public static double debugVelX() { return velX; }
    public static double debugVelY() { return velY; }
    public static float debugSensitivity() { return sensitivity; }

    /** Drops any focused widget so a cursor click doesn't leave a second highlighted element. */
    private static void clearScreenFocus() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.currentScreen != null) {
            mc.currentScreen.setFocused(null);
        }
    }

    public static void simulateLeftClick() {
        clickAt(0);
    }

    public static void simulateRightClick() {
        clickAt(1);
    }

    /**
     * Full press+release at the cursor, routed through the REAL {@code Mouse.onMouseButton} (widened
     * via the access widener) instead of calling {@code Screen.mouseClicked}/{@code mouseReleased}
     * directly. Mods built on Architectury API (Roughly Enough Items and others) intercept clicks via
     * mixins on the CALL SITE of {@code Screen.mouseClicked} <em>inside</em> {@code Mouse.onMouseButton}
     * — a direct call to the Screen method from elsewhere in the codebase never reaches that call site,
     * so those mods never saw synthetic clicks at all (feedback: "no me deja interactuar en lo
     * absoluto, ni clic de mouse virtual ni nada... no es integrarlo, es interactuar con él"). Every
     * other screen (vanilla and every other mod) still gets the exact same press+release it always did
     * — this only adds a path those mods can also observe.
     *
     * <p>{@code onMouseButton} re-reads {@code MinecraftClient.currentScreen} itself on each call, so
     * it's inherently safe even if the press closes/replaces the screen (e.g. "Save and Quit") — no
     * manual "only release if still current" workaround needed anymore (that was working around the
     * direct-call path, not a general requirement).
     */
    private static void clickAt(int button) {
        fireMouseButton(button, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        fireMouseButton(button, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
    }

    /** Fires a real mouse-button event via {@code Mouse.onMouseButton}, guarded so {@code MouseMixin}
     *  doesn't mistake it for the physical mouse taking over (same {@link #INJECTING} pattern already
     *  used by {@link #moveOsCursor} and {@code ActionExecutor.pressMouseButton}). */
    private static void fireMouseButton(int button, int action) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null || mc.mouse == null) return;
        INJECTING = true;
        try {
            mc.mouse.onMouseButton(mc.getWindow().getHandle(), new MouseInput(button, 0), action);
        } finally {
            INJECTING = false;
        }
    }

    // ---- Hold-based click (for drag support) ------------------------------------------
    // When the cursor is visible, buttons send press on down and release on up, allowing
    // the user to drag items across inventory slots by holding the button.

    /** Send only the press event (call releaseLeft when the button is released). */
    public static void pressLeftDown() {
        fireMouseButton(0, org.lwjgl.glfw.GLFW.GLFW_PRESS);
    }

    /** Release the left button (pair with pressLeftDown). */
    public static void releaseLeft() {
        fireMouseButton(0, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
    }

    /** Send only the press event for the right button. */
    public static void pressRightDown() {
        fireMouseButton(1, org.lwjgl.glfw.GLFW.GLFW_PRESS);
    }

    /** Release the right button (pair with pressRightDown). */
    public static void releaseRight() {
        fireMouseButton(1, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
    }

    public static void simulateScroll(double amount) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null) return;
        mc.currentScreen.mouseScrolled(virtualX, virtualY, 0, amount);
    }
}
