package dev.steampad.client.ui;

import net.minecraft.client.gui.DrawContext;

/**
 * Small vector-ish drawing primitives built on {@link DrawContext#fill} (axis-aligned rects only),
 * so SteamPad can render clean glyphs, brand marks and the radial menu without PNG assets. All shapes
 * are filled by horizontal scanlines — cheap at the small sizes used here.
 */
public final class Draw {

    private Draw() {}

    /** Filled disc centered at (cx,cy) with radius r. */
    public static void fillCircle(DrawContext ctx, int cx, int cy, int r, int color) {
        if (r <= 0) return;
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** Annulus (filled ring) between rInner and rOuter. */
    public static void fillRing(DrawContext ctx, int cx, int cy, int rOuter, int rInner, int color) {
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
    public static void outlineCircle(DrawContext ctx, int cx, int cy, int r, int thickness, int color) {
        fillRing(ctx, cx, cy, r, Math.max(0, r - thickness), color);
    }

    /** Rounded-rect-ish fill (square with clipped corners) — good enough for chips/badges. */
    public static void fillRoundRect(DrawContext ctx, int x1, int y1, int x2, int y2, int radius, int color) {
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

    private static void fillCorner(DrawContext ctx, int cx, int cy, int r, int color, boolean left, boolean top) {
        for (int dy = 0; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            int yy = top ? cy - dy : cy + dy;
            if (left) ctx.fill(cx - dx, yy, cx + 1, yy + 1, color);
            else      ctx.fill(cx, yy, cx + dx + 1, yy + 1, color);
        }
    }

    /** Upward-pointing filled triangle inside the box (used for PlayStation glyph). */
    public static void fillTriangleUp(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            double t = (double) (dy + r) / (2 * r);   // 0 at top, 1 at bottom
            int half = (int) (t * r);
            ctx.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    /** A '×' cross of two thick diagonals (PlayStation cross). */
    public static void drawCross(DrawContext ctx, int cx, int cy, int r, int thickness, int color) {
        for (int d = -r; d <= r; d++) {
            ctx.fill(cx + d - thickness / 2, cy + d, cx + d + thickness / 2 + 1, cy + d + 1, color);
            ctx.fill(cx + d - thickness / 2, cy - d, cx + d + thickness / 2 + 1, cy - d + 1, color);
        }
    }

    /** Square outline (PlayStation square). */
    public static void drawSquareOutline(DrawContext ctx, int cx, int cy, int r, int thickness, int color) {
        ctx.fill(cx - r, cy - r, cx + r, cy - r + thickness, color);
        ctx.fill(cx - r, cy + r - thickness, cx + r, cy + r, color);
        ctx.fill(cx - r, cy - r, cx - r + thickness, cy + r, color);
        ctx.fill(cx + r - thickness, cy - r, cx + r, cy + r, color);
    }

    /** Diagonal/horizontal/vertical thin line approximation via small rects. */
    public static void line(DrawContext ctx, int x1, int y1, int x2, int y2, int thickness, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) { ctx.fill(x1, y1, x1 + thickness, y1 + thickness, color); return; }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            ctx.fill(x, y, x + thickness, y + thickness, color);
        }
    }
}
