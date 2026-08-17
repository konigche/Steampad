package dev.steampad.input;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * Soft snap-to-widget magnetism for the virtual cursor in ordinary (non-container) screens — the
 * console feel where the cursor settles onto the nearest button instead of drifting between them.
 * Mirrors {@link SlotSnap} but for widgets. The dispatcher only calls {@link #apply} on ticks the
 * stick is settled/released (not while actively steering, so the magnet never fights live movement)
 * and only while the user hasn't turned it off — see the call site in
 * {@code GamepadInputDispatcher} for the exact gate.
 */
public final class WidgetSnap {

    private static final double SNAP_RADIUS = 26.0;   // scaled px
    private static final double PULL = 0.30;          // fraction pulled toward the widget per tick

    /** A snappable point: a widget's or mod-owned target's center. */
    private record Point(double cx, double cy) {}

    private WidgetSnap() {}

    public static void apply(Screen screen) {
        try {
            Point p = nearest(screen);
            if (p == null) return;
            double cx = VirtualMouseController.getX(), cy = VirtualMouseController.getY();
            VirtualMouseController.setPosition(cx + (p.cx() - cx) * PULL, cy + (p.cy() - cy) * PULL);
        } catch (Throwable ignored) {
            // Never let a snap hiccup affect input.
        }
    }

    /** Center of the widget/target nearest the cursor within the snap radius, or null. Also considers
     *  things a mod owns but never added to {@code children()} (see {@link ExternalWidgetScanner}) —
     *  REI's search bar, Traveler's Backpack's custom buttons, and similar. */
    private static Point nearest(Screen screen) {
        double cx = VirtualMouseController.getX(), cy = VirtualMouseController.getY();
        Point best = null;
        double bestSq = SNAP_RADIUS * SNAP_RADIUS;
        for (GuiEventListener e : screen.children()) {
            if (!(e instanceof AbstractWidget w) || !w.visible || !w.active) continue;
            double dx = (w.getX() + w.getWidth() / 2.0) - cx;
            double dy = (w.getY() + w.getHeight() / 2.0) - cy;
            double d = dx * dx + dy * dy;
            if (d < bestSq) { bestSq = d; best = new Point(w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0); }
        }
        for (var t : ExternalWidgetScanner.discover(screen)) {
            double dx = t.centerX() - cx, dy = t.centerY() - cy;
            double d = dx * dx + dy * dy;
            if (d < bestSq) { bestSq = d; best = new Point(t.centerX(), t.centerY()); }
        }
        return best;
    }
}
