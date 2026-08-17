package dev.steampad.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Small vector-ish drawing primitives built on {@link GuiGraphics#fill} (axis-aligned rects only),
 * so SteamPad can render clean glyphs, brand marks and the radial menu without PNG assets. All shapes
 * are filled by horizontal scanlines — cheap at the small sizes used here.
 */
public final class Draw {

    private Draw() {}

    /** Filled disc centered at (cx,cy) with radius r. */
    public static void fillCircle(GuiGraphics ctx, int cx, int cy, int r, int color) {
        if (r <= 0) return;
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** Annulus (filled ring) between rInner and rOuter. */
    public static void fillRing(GuiGraphics ctx, int cx, int cy, int rOuter, int rInner, int color) {
        if (rOuter <= 0 || rInner >= rOuter) return;
        for (int dy = -rOuter; dy <= rOuter; dy++) {
            double yy = (double) dy * dy;
            double outer = Math.sqrt((double) rOuter * rOuter - yy);
            double inner = rInner * rInner - yy;
            if (inner <= 0) {
                ctx.fill(cx - (int) outer, cy + dy, cx + (int) outer + 1, cy + dy + 1, color);
            } else {
                int in = (int) Math.sqrt(inner);
                int out = (int) outer;
                ctx.fill(cx - out, cy + dy, cx - in, cy + dy + 1, color);
                ctx.fill(cx + in + 1, cy + dy, cx + out + 1, cy + dy + 1, color);
            }
        }
    }

    /** 1px-ish circle outline of given thickness. */
    public static void outlineCircle(GuiGraphics ctx, int cx, int cy, int r, int thickness, int color) {
        fillRing(ctx, cx, cy, r, Math.max(0, r - thickness), color);
    }

    /**
     * Partial ring outline (arc) — the dwell-fill progress indicator on a category chip in the Item
     * Radial (feedback: "el círculo de selección... se vaya rellenando"). Approximated as a chain of
     * small filled dots along the arc every few degrees rather than a true scanline stroke — at the
     * small chip radii this renderer works with, it reads as a solid arc for a fraction of the cost.
     *
     * @param startAngle radians, SAME convention as the chip-layout loop in {@code RadialRenderer}:
     *                   0 = 3 o'clock, {@code -PI/2} = 12 o'clock, increasing = clockwise.
     * @param sweepAngle radians to sweep clockwise from {@code startAngle}; &le;0 draws nothing.
     */
    public static void outlineArc(GuiGraphics ctx, int cx, int cy, int r, int thickness,
                                  double startAngle, double sweepAngle, int color) {
        if (r <= 0 || sweepAngle <= 0) return;
        int dotR = Math.max(1, thickness / 2);
        double stepRad = Math.toRadians(4);
        int steps = Math.max(1, (int) Math.ceil(sweepAngle / stepRad));
        for (int i = 0; i <= steps; i++) {
            double t = Math.min(1.0, (double) i / steps);
            double angle = startAngle + sweepAngle * t;
            int px = cx + (int) Math.round(Math.cos(angle) * r);
            int py = cy + (int) Math.round(Math.sin(angle) * r);
            fillCircle(ctx, px, py, dotR, color);
        }
    }

    /**
     * Filled donut slice (annular sector) between rInner/rOuter, sweeping clockwise from
     * {@code startAngle} — same angle convention as the chip layout in {@code RadialRenderer} (0 = 3
     * o'clock, {@code -PI/2} = 12 o'clock). The SEGMENTED radial skin's building block: one call per
     * trapezoid slice (plus an oversized/narrower-gap call underneath for the selection halo).
     *
     * <p>Same left/right-band scanline trick as {@link #fillRing} (skip straight to the annulus band
     * via {@code sqrt} instead of testing every pixel in the bounding box), refined further to only
     * fill the contiguous run of columns whose angle actually falls in range — a slice's bounding box
     * is mostly empty space once {@code sweepAngle} is a small fraction of the full circle.
     */
    public static void fillDonutSlice(GuiGraphics ctx, int cx, int cy, int rOuter, int rInner,
                                      double startAngle, double sweepAngle, int color) {
        if (rOuter <= 0 || rInner >= rOuter || sweepAngle <= 0) return;
        double endAngle = startAngle + Math.min(sweepAngle, 2 * Math.PI);
        for (int dy = -rOuter; dy <= rOuter; dy++) {
            double yy = (double) dy * dy;
            double outer2 = (double) rOuter * rOuter - yy;
            if (outer2 < 0) continue;
            int outerX = (int) Math.sqrt(outer2);
            double inner2 = (double) rInner * rInner - yy;
            if (inner2 > 0) {
                int innerX = (int) Math.sqrt(inner2);
                fillSliceBand(ctx, cx, cy, dy, -outerX, -innerX - 1, startAngle, endAngle, color);
                fillSliceBand(ctx, cx, cy, dy, innerX + 1, outerX, startAngle, endAngle, color);
            } else {
                fillSliceBand(ctx, cx, cy, dy, -outerX, outerX, startAngle, endAngle, color);
            }
        }
    }

    /** One scanline row of {@link #fillDonutSlice}: walks {@code [xFrom,xTo]} and flushes contiguous
     *  in-range runs as single fills (usually one run per row, since a slice's angular range is
     *  convex for any {@code sweepAngle < 2π}). */
    private static void fillSliceBand(GuiGraphics ctx, int cx, int cy, int dy, int xFrom, int xTo,
                                      double startAngle, double endAngle, int color) {
        if (xFrom > xTo) return;
        int runStart = Integer.MIN_VALUE;
        for (int dx = xFrom; dx <= xTo; dx++) {
            boolean in = dx != 0 || dy != 0 ? angleInRange(dx, dy, startAngle, endAngle) : false;
            if (in) {
                if (runStart == Integer.MIN_VALUE) runStart = dx;
            } else if (runStart != Integer.MIN_VALUE) {
                ctx.fill(cx + runStart, cy + dy, cx + dx, cy + dy + 1, color);
                runStart = Integer.MIN_VALUE;
            }
        }
        if (runStart != Integer.MIN_VALUE) ctx.fill(cx + runStart, cy + dy, cx + xTo + 1, cy + dy + 1, color);
    }

    /** True if (dx,dy)'s angle falls within [startAngle,endAngle), normalized so the range can cross
     *  the atan2 -π/π seam without special-casing it at the call site. */
    private static boolean angleInRange(int dx, int dy, double startAngle, double endAngle) {
        double a = Math.atan2(dy, dx);
        double rel = a - startAngle;
        rel -= Math.floor(rel / (2 * Math.PI)) * (2 * Math.PI);   // normalize to [0, 2π)
        return rel <= (endAngle - startAngle);
    }

    /**
     * Filled triangle for 3 arbitrary vertices — standard scanline polygon fill (per row, intersect
     * each of the 3 edges with the scanline and fill between the min/max hit). General-purpose (not
     * radial-specific) so future skins can reuse it for other pointer/glyph shapes.
     */
    public static void fillTriangle(GuiGraphics ctx, int x1, int y1, int x2, int y2, int x3, int y3, int color) {
        int minY = Math.min(y1, Math.min(y2, y3)), maxY = Math.max(y1, Math.max(y2, y3));
        for (int y = minY; y <= maxY; y++) {
            double xa = edgeX(x1, y1, x2, y2, y);
            double xb = edgeX(x2, y2, x3, y3, y);
            double xc = edgeX(x3, y3, x1, y1, y);
            double lo = Double.NaN, hi = Double.NaN;
            for (double x : new double[]{xa, xb, xc}) {
                if (Double.isNaN(x)) continue;
                lo = Double.isNaN(lo) ? x : Math.min(lo, x);
                hi = Double.isNaN(hi) ? x : Math.max(hi, x);
            }
            if (!Double.isNaN(lo)) {
                ctx.fill((int) Math.floor(lo), y, (int) Math.ceil(hi) + 1, y + 1, color);
            }
        }
    }

    /** X where the segment (x1,y1)-(x2,y2) crosses horizontal line {@code y}, or NaN if {@code y} is
     *  outside the segment's Y range (including horizontal segments, which never contribute a single
     *  crossing — the OTHER two edges of the triangle still bound the row correctly). */
    private static double edgeX(int x1, int y1, int x2, int y2, int y) {
        if (y1 == y2) return Double.NaN;
        if (y < Math.min(y1, y2) || y > Math.max(y1, y2)) return Double.NaN;
        return x1 + (double) (x2 - x1) * (y - y1) / (y2 - y1);
    }

    /**
     * Small filled arrow pointing along {@code angle} (same convention as {@link #fillDonutSlice}) —
     * tip at radius {@code tipR} from (cx,cy), base at radius {@code baseR} spanning {@code halfWidth}
     * either side. The SEGMENTED radial skin's center "compass needle", eased toward whichever slice
     * is currently selected.
     */
    public static void fillPointer(GuiGraphics ctx, int cx, int cy, double angle,
                                   int baseR, int tipR, int halfWidth, int color) {
        double tx = cx + Math.cos(angle) * tipR, ty = cy + Math.sin(angle) * tipR;
        double perp = angle + Math.PI / 2.0;
        double bx = cx + Math.cos(angle) * baseR, by = cy + Math.sin(angle) * baseR;
        double b1x = bx + Math.cos(perp) * halfWidth, b1y = by + Math.sin(perp) * halfWidth;
        double b2x = bx - Math.cos(perp) * halfWidth, b2y = by - Math.sin(perp) * halfWidth;
        fillTriangle(ctx, (int) Math.round(tx), (int) Math.round(ty),
                (int) Math.round(b1x), (int) Math.round(b1y),
                (int) Math.round(b2x), (int) Math.round(b2y), color);
    }

    /** Rounded-rect-ish fill (square with clipped corners) — good enough for chips/badges. */
    public static void fillRoundRect(GuiGraphics ctx, int x1, int y1, int x2, int y2, int radius, int color) {
        int w = x2 - x1, h = y2 - y1;
        radius = Math.min(radius, Math.min(w, h) / 2);
        ctx.fill(x1 + radius, y1, x2 - radius, y2, color);
        ctx.fill(x1, y1 + radius, x1 + radius, y2 - radius, color);
        ctx.fill(x2 - radius, y1 + radius, x2, y2 - radius, color);
        // Rounded corners as quarter discs.
        fillCorner(ctx, x1 + radius, y1 + radius, radius, color, true, true);
        fillCorner(ctx, x2 - radius - 1, y1 + radius, radius, color, false, true);
        fillCorner(ctx, x1 + radius, y2 - radius - 1, radius, color, true, false);
        fillCorner(ctx, x2 - radius - 1, y2 - radius - 1, radius, color, false, false);
    }

    private static void fillCorner(GuiGraphics ctx, int cx, int cy, int r, int color, boolean left, boolean top) {
        for (int dy = 0; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            int yy = top ? cy - dy : cy + dy;
            if (left) ctx.fill(cx - dx, yy, cx + 1, yy + 1, color);
            else      ctx.fill(cx, yy, cx + dx + 1, yy + 1, color);
        }
    }

    /** Upward-pointing filled triangle inside the box (used for PlayStation glyph). */
    public static void fillTriangleUp(GuiGraphics ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            double t = (double) (dy + r) / (2 * r);   // 0 at top, 1 at bottom
            int half = (int) (t * r);
            ctx.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    /** A '×' cross of two thick diagonals (PlayStation cross). */
    public static void drawCross(GuiGraphics ctx, int cx, int cy, int r, int thickness, int color) {
        for (int d = -r; d <= r; d++) {
            ctx.fill(cx + d - thickness / 2, cy + d, cx + d + thickness / 2 + 1, cy + d + 1, color);
            ctx.fill(cx + d - thickness / 2, cy - d, cx + d + thickness / 2 + 1, cy - d + 1, color);
        }
    }

    /** Square outline (PlayStation square). */
    public static void drawSquareOutline(GuiGraphics ctx, int cx, int cy, int r, int thickness, int color) {
        ctx.fill(cx - r, cy - r, cx + r, cy - r + thickness, color);
        ctx.fill(cx - r, cy + r - thickness, cx + r, cy + r, color);
        ctx.fill(cx - r, cy - r, cx - r + thickness, cy + r, color);
        ctx.fill(cx + r - thickness, cy - r, cx + r, cy + r, color);
    }

    /** Diagonal/horizontal/vertical thin line approximation via small rects. */
    public static void line(GuiGraphics ctx, int x1, int y1, int x2, int y2, int thickness, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) { ctx.fill(x1, y1, x1 + thickness, y1 + thickness, color); return; }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            ctx.fill(x, y, x + thickness, y + thickness, color);
        }
    }
}
