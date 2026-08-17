package dev.steampad.compat;

import dev.steampad.input.ExternalWidgetScanner;
import dev.steampad.util.LogUtil;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * Soft (reflection-only, zero hard dependency) bridge to Roughly Enough Items' overlay, so its
 * search box, buttons, tabs and catalog entries become D-pad/snap targets like any other widget.
 *
 * <p>Why this exists: REI's overlay widgets are NOT owned by the container screen at all — they live
 * in REI's own runtime singleton ({@code me.shedaniel.rei.api.client.REIRuntime}), so
 * {@link ExternalWidgetScanner}'s screen-field scan can never reach them, no matter how permissive
 * its duck-typing gets (the actual reason three earlier attempts, D063/D068/D071, made clicks work
 * but never snap/D-pad — B075). This walks REI's own PUBLIC API instead, verified against the
 * 1.21.9 branch of its real source:
 * <ul>
 *   <li>{@code REIRuntime.getInstance()} → {@code isOverlayVisible()} → {@code getOverlay()}
 *       ({@code Optional<ScreenOverlay>});</li>
 *   <li>the overlay and every REI widget implement vanilla's {@link ContainerEventHandler}, so its tree is
 *       walkable with plain {@code children()};</li>
 *   <li>REI widgets expose {@code getBounds()} returning {@code me.shedaniel.math.Rectangle} with
 *       public int {@code x/y/width/height} — absolute screen coordinates.</li>
 * </ul>
 *
 * <p>Fail-safe by construction: the first lookup failure (REI absent, API reshaped) disables the
 * bridge for the session; every per-call failure just yields fewer targets. Never throws.
 */
public final class ReiCompat {

    /** null = not probed yet; FALSE = REI absent/incompatible (bridge disabled); TRUE = live. */
    private static Boolean available = null;

    private static Method mGetInstance;       // REIRuntime.getInstance()
    private static Method mIsOverlayVisible;  // runtime.isOverlayVisible()
    private static Method mGetOverlay;        // runtime.getOverlay() → Optional<ScreenOverlay>

    /** Per-class resolved getBounds() (or null when the class has none). */
    private static final Map<Class<?>, Method> BOUNDS_METHOD_CACHE = new IdentityHashMap<>();
    /** Per-class resolved Rectangle {x,y,width,height} fields. */
    private static final Map<Class<?>, Field[]> RECT_FIELD_CACHE = new IdentityHashMap<>();

    private static final int MAX_TARGETS = 400;
    private static final int MAX_DEPTH = 5;

    private ReiCompat() {}

    /** REI overlay widgets as snap/nav targets, or an empty list (REI absent, overlay hidden, any
     *  failure). Called from {@link ExternalWidgetScanner#discover} — already memoized there. */
    public static List<ExternalWidgetScanner.Target> discover(Screen screen) {
        if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) return List.of();
        if (Boolean.FALSE.equals(available)) return List.of();
        try {
            if (available == null && !probe()) return List.of();

            Object runtime = mGetInstance.invoke(null);
            if (runtime == null || !Boolean.TRUE.equals(mIsOverlayVisible.invoke(runtime))) return List.of();
            Object overlayOpt = mGetOverlay.invoke(runtime);
            if (!(overlayOpt instanceof java.util.Optional<?> opt) || opt.isEmpty()) return List.of();

            List<ExternalWidgetScanner.Target> out = new ArrayList<>();
            java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            collect(opt.get(), out, seen, 0, screen.width, screen.height);
            return out;
        } catch (Throwable t) {
            // Anything unexpected past the probe: disable for the session, never disturb input.
            available = Boolean.FALSE;
            LogUtil.warn("[SteamPad] REI overlay bridge disabled after error: {}", t.toString());
            return List.of();
        }
    }

    /** One-time API lookup. Any miss → bridge off for the session (REI absent or API changed). */
    private static boolean probe() {
        try {
            Class<?> cRuntime = Class.forName("me.shedaniel.rei.api.client.REIRuntime");
            mGetInstance = cRuntime.getMethod("getInstance");
            mIsOverlayVisible = cRuntime.getMethod("isOverlayVisible");
            mGetOverlay = cRuntime.getMethod("getOverlay");
            available = Boolean.TRUE;
            LogUtil.info("[SteamPad] REI detected — overlay widgets wired into D-pad/snap navigation.");
            return true;
        } catch (Throwable t) {
            available = Boolean.FALSE;
            return false;
        }
    }

    /** Depth-first walk of REI's widget tree: every widget with sane bounds becomes a target, and
     *  every ParentElement is descended into (entry grids yield their individual entries this way). */
    private static void collect(Object node, List<ExternalWidgetScanner.Target> out,
                                java.util.Set<Object> seen, int depth, int sw, int sh) {
        if (node == null || depth > MAX_DEPTH || out.size() >= MAX_TARGETS || !seen.add(node)) return;

        double[] r = boundsOf(node);
        boolean isLeafLike = !(node instanceof ContainerEventHandler);
        if (r != null && r[2] > 0 && r[3] > 0 && r[2] <= sw && r[3] <= sh
                && r[0] > -r[2] && r[1] > -r[3] && r[0] < sw && r[1] < sh) {
            // Containers (the overlay itself, the entry grid) are walked, not targeted — snapping to
            // a giant panel's center is noise; their CHILDREN are the real targets.
            if (isLeafLike || (r[2] < 40 && r[3] < 40)) {
                out.add(new ExternalWidgetScanner.Target(r[0], r[1], r[2], r[3], null));
            }
        }
        if (node instanceof ContainerEventHandler pe) {
            List<? extends GuiEventListener> children;
            try {
                children = pe.children();
            } catch (Throwable t) {
                return;
            }
            for (GuiEventListener child : children) collect(child, out, seen, depth + 1, sw, sh);
        }
    }

    /** {x, y, w, h} via the node's own getBounds() (REI's WidgetWithBounds), or null. */
    private static double[] boundsOf(Object node) {
        Class<?> cls = node.getClass();
        Method m = BOUNDS_METHOD_CACHE.computeIfAbsent(cls, c -> {
            try {
                Method mm = c.getMethod("getBounds");
                mm.setAccessible(true);
                return mm;
            } catch (Throwable t) {
                return null;
            }
        });
        if (m == null) return null;
        try {
            Object rect = m.invoke(node);
            if (rect == null) return null;
            Field[] f = rectFieldsOf(rect.getClass());
            if (f == null) return null;
            return new double[]{((Number) f[0].get(rect)).doubleValue(),
                    ((Number) f[1].get(rect)).doubleValue(),
                    ((Number) f[2].get(rect)).doubleValue(),
                    ((Number) f[3].get(rect)).doubleValue()};
        } catch (Throwable t) {
            return null;
        }
    }

    private static Field[] rectFieldsOf(Class<?> cls) {
        if (RECT_FIELD_CACHE.containsKey(cls)) return RECT_FIELD_CACHE.get(cls);
        Field[] result = null;
        try {
            Field fx = cls.getField("x"), fy = cls.getField("y");
            Field fw = cls.getField("width"), fh = cls.getField("height");
            result = new Field[]{fx, fy, fw, fh};
        } catch (Throwable ignored) {}
        RECT_FIELD_CACHE.put(cls, result);
        return result;
    }
}
