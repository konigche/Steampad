package dev.steampad.input;

import dev.steampad.mixin.HandledScreenAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/**
 * Soft "snap-to-target" magnetism for the virtual cursor inside container screens — the Bedrock-style
 * feel where the cursor gently settles onto the nearest target instead of drifting freely.
 *
 * <p>Targets are the handler's SLOTS plus every active, visible {@code ClickableWidget} the screen
 * added — which is what makes MOD buttons (backpack openers, recipe-book toggle, sort buttons…)
 * snappable and D-pad-navigable exactly like inventory cells, instead of being mouse-only islands.
 *
 * <p>Each tick (while the cursor is within the snap radius of a target) the cursor is nudged a
 * fraction of the way toward its center. The pull is gentle enough that the player can still move
 * freely, but strong enough to "click into" each cell/button.
 */
public final class SlotSnap {

    private static final double WIDGET_SNAP_RADIUS = 22.0;   // scaled px — clickable widgets/buttons
    // Reverted (feedback: "regresemos como lo teniamos... se siente raro el inventario, como que no
    // se tiene control" — shrinking this to 8px to fix Traveler's Backpack made EVERY slot in EVERY
    // container feel loose, since the cursor had to arrive almost dead-center before any magnetism
    // kicked in at all, not just near a mod button). Back to the same generous radius slots always
    // had. The narrow-gap-vs-mod-button problem this was originally trying to solve is fixed instead
    // in the SCORING below (WIDGET_PRIORITY), which favors a button over a neighboring slot without
    // starving ordinary slot-to-slot magnetism of its own radius to do it.
    private static final double SLOT_SNAP_RADIUS = WIDGET_SNAP_RADIUS;
    // A button/widget only has to be reasonably close (not dead-center) to out-score a slot that's
    // ALSO reasonably close — normalized distance (fraction of the way to each target's OWN radius)
    // times this factor, so a mod button (Traveler's Backpack) sitting in the gap between two slots
    // wins the tie instead of losing to whichever slot center happens to be a few raw pixels nearer.
    // Still capped by each target's own radius (see apply()'s eligibility check) — this only affects
    // WHICH eligible target wins, never how far a button can reach.
    private static final double WIDGET_PRIORITY = 0.6;
    private static final double PULL = 0.35;          // fraction pulled toward the target per tick

    /** A snappable point: a slot center or a clickable widget center, with its own pull radius. */
    private record Target(double cx, double cy, double radius, boolean isWidget) {}

    private SlotSnap() {}

    /** All snap/nav targets of the screen: enabled slots + active visible clickable widgets. */
    private static java.util.List<Target> targets(AbstractContainerScreen<?> screen) {
        java.util.List<Target> out = new java.util.ArrayList<>();
        HandledScreenAccessor acc = (HandledScreenAccessor) screen;
        int ox = acc.steampad$getX(), oy = acc.steampad$getY();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.isActive()) out.add(new Target(ox + slot.x + 8.0, oy + slot.y + 8.0, SLOT_SNAP_RADIUS, false));
        }
        for (var el : screen.children()) {
            if (el instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && w.active && w.visible && w.getWidth() > 0 && w.getHeight() > 0) {
                out.add(new Target(w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0, WIDGET_SNAP_RADIUS, true));
            }
        }
        // Mod-owned buttons that never registered in children() — e.g. Traveler's Backpack's
        // BackpackScreen keeps its own `List<IButton> buttons`, and IButton has nothing to do with
        // vanilla's ClickableWidget at all (verified against its real 1.21.10-fabric source, D071) —
        // without these, a button sitting between two slots always loses the snap to the slots on
        // either side since it was never even a candidate. See ExternalWidgetScanner.
        for (var t : ExternalWidgetScanner.discover(screen)) {
            out.add(new Target(t.centerX(), t.centerY(), WIDGET_SNAP_RADIUS, true));
        }
        return out;
    }

    public static void apply(AbstractContainerScreen<?> screen) {
        try {
            double cx = VirtualMouseController.getX(), cy = VirtualMouseController.getY();
            Target best = null;
            double bestScore = Double.MAX_VALUE;
            for (Target t : targets(screen)) {
                double dx = t.cx() - cx, dy = t.cy() - cy;
                double d = dx * dx + dy * dy;
                double rSq = t.radius() * t.radius();
                if (d > rSq) continue;   // outside this target's own reach — not a candidate at all
                // Normalized closeness (0 = dead center, 1 = at the target's own radius edge), not raw
                // distance — so two DIFFERENTLY-radiused targets compete fairly on how "arrived" the
                // cursor is at each, rather than always favoring whichever center happens to sit a few
                // raw pixels nearer. Widgets get a further head start (see WIDGET_PRIORITY) so a mod
                // button in the narrow gap between two slots wins the tie instead of losing to them.
                double score = (d / rSq) * (t.isWidget() ? WIDGET_PRIORITY : 1.0);
                if (score < bestScore) { bestScore = score; best = t; }
            }
            if (best == null) return;
            VirtualMouseController.setPosition(cx + (best.cx() - cx) * PULL, cy + (best.cy() - cy) * PULL);
        } catch (Throwable ignored) {
            // Never let a snap hiccup affect input.
        }
    }

    /**
     * D-pad navigation inside a container: moves the cursor from the target it is on to the nearest
     * target in the (dx,dy) direction — slots AND buttons (vanilla or modded) alike. Returns true if
     * it moved. This is the "inventory acts in auto mode when you touch the D-pad" behaviour.
     */
    public static boolean moveToNeighbor(AbstractContainerScreen<?> screen, int dx, int dy) {
        try {
            double curX = VirtualMouseController.getX(), curY = VirtualMouseController.getY();
            java.util.List<Target> all = targets(screen);
            // Anchor on the target the cursor currently sits on (if any) for stable grid hops. D-pad
            // hopping is discrete (not the continuous pull `apply()` does), so this keeps the original
            // generous radius unchanged — not the reported issue, and already confirmed working.
            Target from = null;
            double fromSq = WIDGET_SNAP_RADIUS * WIDGET_SNAP_RADIUS;
            for (Target t : all) {
                double ddx = t.cx() - curX, ddy = t.cy() - curY;
                double d = ddx * ddx + ddy * ddy;
                if (d < fromSq) { fromSq = d; from = t; }
            }
            if (from != null) { curX = from.cx(); curY = from.cy(); }
            Target best = null;
            double bestScore = Double.MAX_VALUE;
            for (Target t : all) {
                if (t == from) continue;
                double ddx = t.cx() - curX, ddy = t.cy() - curY;
                double along = ddx * dx + ddy * dy;
                if (along <= 1.0) continue;
                double perp = Math.abs(ddx * dy - ddy * dx);
                double score = along + perp * 3.0;   // strongly prefer the same row/column
                if (score < bestScore) { bestScore = score; best = t; }
            }
            if (best == null) return false;
            VirtualMouseController.setPosition(best.cx(), best.cy());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Nearest slot to the cursor with no radius limit (anchor for grid navigation), or null. */
    public static Slot nearestSlotUnbounded(AbstractContainerScreen<?> screen) {
        try {
            HandledScreenAccessor acc = (HandledScreenAccessor) screen;
            int ox = acc.steampad$getX(), oy = acc.steampad$getY();
            double cx = VirtualMouseController.getX(), cy = VirtualMouseController.getY();
            Slot best = null;
            double bestSq = Double.MAX_VALUE;
            for (Slot slot : screen.getMenu().slots) {
                if (!slot.isActive()) continue;
                double dx = (ox + slot.x + 8.0) - cx;
                double dy = (oy + slot.y + 8.0) - cy;
                double d = dx * dx + dy * dy;
                if (d < bestSq) { bestSq = d; best = slot; }
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    /** The slot nearest the cursor within the (tight, "basically arrived") slot snap radius, or null
     *  — kept in sync with {@link #apply}'s own slot radius so the highlighted cell always matches
     *  what the magnetism would actually settle on (never highlights a slot the cursor hasn't really
     *  reached yet, e.g. while it's sitting on a mod button between two slots). */
    public static Slot nearestSlot(AbstractContainerScreen<?> screen) {
        try {
            HandledScreenAccessor acc = (HandledScreenAccessor) screen;
            int ox = acc.steampad$getX(), oy = acc.steampad$getY();
            double cx = VirtualMouseController.getX(), cy = VirtualMouseController.getY();
            Slot best = null;
            double bestSq = SLOT_SNAP_RADIUS * SLOT_SNAP_RADIUS;
            for (Slot slot : screen.getMenu().slots) {
                if (!slot.isActive()) continue;
                double dx = (ox + slot.x + 8.0) - cx;
                double dy = (oy + slot.y + 8.0) - cy;
                double d = dx * dx + dy * dy;
                if (d < bestSq) { bestSq = d; best = slot; }
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Screen-space top-left of a slot's 16x16 cell, as {x, y}, or null. */
    public static int[] slotRect(AbstractContainerScreen<?> screen, Slot slot) {
        try {
            HandledScreenAccessor acc = (HandledScreenAccessor) screen;
            return new int[]{acc.steampad$getX() + slot.x, acc.steampad$getY() + slot.y};
        } catch (Throwable t) {
            return null;
        }
    }
}
