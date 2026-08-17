package dev.steampad.client.window;

import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import dev.steampad.config.ConfigManager;
import dev.steampad.mixin.WindowAccessor;
import dev.steampad.util.LogUtil;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * "Splitscreen" window-tiling feature (see {@code GlobalConfig.windowArrangeEnabled}): repositions and
 * resizes the OS window on demand — Windowed → Left/Right/Top/Bottom halves → 4 quadrant corners →
 * Fullscreen → back to Windowed — for setting up local multiplayer across multiple Minecraft instances
 * on one screen. This is <b>not</b> in-game splitscreen (see CLAUDE.md Restriction 2); it never touches
 * rendering, camera, or player/session state — purely GLFW window geometry, same idea as
 * <a href="https://github.com/pcal43/splitscreen">pcal43/splitscreen</a> (MIT), reimplemented here.
 *
 * <p>Triggered by holding Select (BACK) and pressing RB — see
 * {@code GamepadInputDispatcher.tickWindowArrangeChord()} for the chord handling. All logic here is a
 * plain, mixin-free class; {@link WindowAccessor} is the only mixin touchpoint, and it is a pure field
 * accessor (no injected logic), per the project's "thin mixin hooks" rule.
 */
public final class WindowArrangeController {

    private static WindowArrangeMode current = WindowArrangeMode.WINDOWED;
    /** Bounds to restore when cycling back to WINDOWED — captured the moment tiling first starts. */
    private static WindowBounds savedWindowed;

    // Baseline captured the moment the feature transitions disabled -> enabled (at startup if it was
    // left on, or when the user flips the toggle live) — "how the window looked before splitscreen
    // touched it", restored verbatim when the feature is turned back off (D-057: persistence feedback).
    private static WindowBounds baselineWindowed;
    private static boolean baselineFullscreen;
    private static boolean hasBaseline = false;
    private static boolean startupApplied = false;

    private WindowArrangeController() {}

    public static WindowArrangeMode currentMode() {
        return current;
    }

    /**
     * Called once from the first client tick (the window is fully constructed and GLFW-safe by then —
     * same timing rule the rest of SteamPad's startup already follows). If the feature was left enabled
     * from a previous session, re-applies the last layout automatically.
     */
    public static void onFirstTick(Minecraft mc) {
        if (startupApplied) return;
        startupApplied = true;
        if (!ConfigManager.getGlobal().windowArrangeEnabled) return;
        activate(mc.getWindow());
    }

    /**
     * Called when the user flips the "Enable Splitscreen" toggle in Global Settings. Turning ON
     * captures a fresh baseline and re-applies the last remembered layout; turning OFF restores the
     * window to that baseline (fullscreen or windowed, whichever it was) and forgets it — the layout
     * itself stays saved in {@code GlobalConfig.windowArrangeMode} for the next time it's re-enabled.
     */
    public static void setEnabled(boolean enabled) {
        Window window = Minecraft.getInstance().getWindow();
        if (enabled) activate(window);
        else deactivate(window);
    }

    private static void activate(Window window) {
        baselineFullscreen = window.isFullscreen();
        baselineWindowed = new WindowBounds(window.getX(), window.getY(), window.getScreenWidth(), window.getScreenHeight());
        hasBaseline = true;
        WindowArrangeMode saved = ConfigManager.getGlobal().windowArrangeMode;
        current = saved != null ? saved : WindowArrangeMode.WINDOWED;
        savedWindowed = baselineFullscreen ? null : baselineWindowed;   // seed the WINDOWED-mode restore point
        apply(window);
    }

    private static void deactivate(Window window) {
        if (!hasBaseline) return;
        if (baselineFullscreen) {
            if (!window.isFullscreen()) window.toggleFullScreen();
        } else {
            if (window.isFullscreen()) window.toggleFullScreen();
            reposition(window, baselineWindowed, true);
        }
        hasBaseline = false;
        LogUtil.info("[SteamPad] Splitscreen disabled — window restored to its pre-splitscreen state.");
    }

    /**
     * Advances to the next layout in the cycle and applies it immediately. No-op if the feature is
     * disabled (defense in depth — the dispatcher already gates the chord on the same toggle).
     */
    public static void cycle() {
        if (!ConfigManager.getGlobal().windowArrangeEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        if (current == WindowArrangeMode.WINDOWED) {
            // Leaving windowed for the first time this cycle — remember the size/position to return to.
            savedWindowed = new WindowBounds(window.getX(), window.getY(), window.getScreenWidth(), window.getScreenHeight());
        }
        WindowArrangeMode[] modes = WindowArrangeMode.values();
        current = modes[(current.ordinal() + 1) % modes.length];
        apply(window);
        // Persisted immediately (not just on world exit) so a crash/force-quit while tiled still
        // remembers the layout next launch — the whole point of this being "persistent" per feedback.
        ConfigManager.getGlobal().windowArrangeMode = current;
        ConfigManager.saveGlobal();
        LogUtil.info("[SteamPad] Splitscreen window layout -> " + current);
    }

    private static void apply(Window window) {
        if (current.isFullscreen()) {
            if (!window.isFullscreen()) window.toggleFullScreen();
            return;
        }
        // Exiting fullscreen goes through vanilla's own toggle so its internal state (it tracks a
        // second currentFullscreen flag and does GL/viewport work) stays consistent; only the
        // windowed/tiled bounds below are ever written directly.
        if (window.isFullscreen()) window.toggleFullScreen();

        WindowBounds screen = screenBounds(window);
        WindowBounds saved = savedWindowed != null
                ? savedWindowed
                : new WindowBounds(window.getX(), window.getY(), window.getScreenWidth(), window.getScreenHeight());
        int gap = Math.max(0, ConfigManager.getGlobal().windowArrangeGap);
        WindowBounds b = current.bounds(screen, saved, gap);
        reposition(window, b, current == WindowArrangeMode.WINDOWED);
    }

    /**
     * Writes the windowed/tiled bounds (both the mixin-exposed live fields and the actual GLFW window)
     * and sets the border. Decoration is applied BEFORE the move/resize, not after: on at least some
     * window managers, changing GLFW_DECORATED only reflows the window on its NEXT geometry change —
     * decorating-then-moving in the same call left a stale titlebar-sized gap on the first
     * WINDOWED-to-tiled transition (reported by the user only on the very first cycle, never again
     * once already undecorated — consistent with this being a decoration/reflow ordering issue rather
     * than a monitor-bounds problem).
     */
    private static void reposition(Window window, WindowBounds b, boolean decorated) {
        long handle = dev.steampad.compat.mc.WindowCompat.handle(window);
        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, decorated ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);

        // Window is a final vanilla class — javac can't see the mixin-injected interface statically,
        // so the cast has to go through Object (standard Fabric/Mixin accessor idiom).
        WindowAccessor acc = (WindowAccessor) (Object) window;
        acc.steampad$setWindowedX(b.x());
        acc.steampad$setWindowedY(b.y());
        acc.steampad$setWindowedWidth(b.width());
        acc.steampad$setWindowedHeight(b.height());
        acc.steampad$setX(b.x());
        acc.steampad$setY(b.y());
        acc.steampad$setWidth(b.width());
        acc.steampad$setHeight(b.height());

        GLFW.glfwSetWindowMonitor(handle, 0L, b.x(), b.y(), Math.max(1, b.width()), Math.max(1, b.height()),
                GLFW.GLFW_DONT_CARE);
        GLFW.glfwShowWindow(handle);
        GLFW.glfwFocusWindow(handle);
    }

    /** The work-area of the monitor the window currently sits on, falling back to the window's own
     *  bounds if no monitor is tracked yet (never divides by zero / places the window off-screen). */
    private static WindowBounds screenBounds(Window window) {
        Monitor monitor = window.findBestMonitor();
        if (monitor == null) return new WindowBounds(window.getX(), window.getY(), window.getScreenWidth(), window.getScreenHeight());
        VideoMode mode = monitor.getCurrentMode();
        if (mode == null) return new WindowBounds(window.getX(), window.getY(), window.getScreenWidth(), window.getScreenHeight());
        return new WindowBounds(monitor.getX(), monitor.getY(), mode.getWidth(), mode.getHeight());
    }
}
