package dev.steampad.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Monochrome, console-style controller button icons for the configuration screen — black shapes with
 * white symbols, designed to match SteamPad's dark UI (per the user's request). Each real control is
 * reinterpreted as a small icon: face buttons are lettered discs, sticks are rings with an L/R letter,
 * the D-pad is a cross with the active arm lit, bumpers/triggers are labeled pills, and stick-direction
 * pseudo-buttons (LS_UP, RS_LEFT, …) are a stick with a directional arrow. All vector-drawn via
 * {@link Draw}, so they scale without PNG assets.
 *
 * <p>Recognized ids: A B X Y · LB RB · LT RT · L3 R3 · DUP DDOWN DLEFT DRIGHT · START BACK ·
 * LS_UP/DOWN/LEFT/RIGHT · RS_UP/DOWN/LEFT/RIGHT · "" (unbound).
 */
public final class ButtonIcon {

    private static final int FILL    = 0xFF15191F;   // near-black body
    private static final int EDGE    = 0xFFE9EEF4;   // white edge/symbol
    private static final int EMPTY   = 0xFF565E68;   // muted outline for "unbound"

    private ButtonIcon() {}

    /** Short readable label for a button id (for text prompts), e.g. "DRIGHT" → "D-Pad →". */
    public static String label(String id) {
        if (id == null || id.isEmpty()) return "—";
        return switch (id) {
            case "A", "B", "X", "Y", "LB", "RB", "LT", "RT" -> id;
            case "L3" -> "L3";
            case "R3" -> "R3";
            case "START" -> "Start";
            case "BACK" -> "Select";
            case "DUP" -> "D-Pad ↑";
            case "DDOWN" -> "D-Pad ↓";
            case "DLEFT" -> "D-Pad ←";
            case "DRIGHT" -> "D-Pad →";
            case "P1", "P2", "P3", "P4" -> "Paddle " + id.substring(1);
            case "M1", "M2", "M3", "M4" -> "Extra " + id.substring(1);
            default -> id;
        };
    }

    /** Width consumed by the icon for {@code id} at the given size. */
    public static int width(TextRenderer tr, int size, String id) {
        if (id == null || id.isEmpty()) return size;
        // Brand PNGs are square, so they consume exactly {@code size}; only the vector fallback widens.
        if (ButtonTextureManager.resolveButton(id) != null) return size;
        return switch (id) {
            case "LB", "RB", "LT", "RT", "START", "BACK" -> Math.max(size + 8, tr.getWidth(id) + 10);
            default -> size;
        };
    }

    // Press-feedback tints — passed as the drawTexture color multiplier, which only darkens pixels the
    // texture actually draws (respects the PNG's own alpha) instead of a flat rectangle overlay. The
    // rectangle version (first cut) painted over the icon's whole square bounding box regardless of the
    // art's real silhouette, showing as a black square around any non-square glyph (feedback: "aparece
    // una sombra negra... fuera del glifo del botón"). White = no change; the pressed tint is a uniform
    // ~60% brightness multiply, alpha untouched.
    private static final int TINT_NORMAL = 0xFFFFFFFF;
    private static final int TINT_PRESSED = 0xFF999999;

    /**
     * Draws the icon for {@code id} into a box of {@code size} at (x,y); returns the width consumed.
     *
     * <p>Press feedback (feedback: "¿le puedes dar un efecto de presión cuando se presiona un botón?"):
     * while the physical button is actually held ({@link dev.steampad.input.GamepadInputDispatcher#isPhysicallyHeld}),
     * brand PNG icons darken via the texture color multiplier (shape-accurate, respects alpha) and the
     * glyph nudges down 1px; vector fallback icons only get the nudge (their exact silhouette isn't
     * known ahead of the draw, so a safe rectangle tint isn't possible without the same leak risk).
     * The Y-nudge never changes the returned width, so pressing a button can never shift layout.
     */
    public static int draw(DrawContext ctx, TextRenderer tr, int x, int y, int size, String id) {
        if (id == null || id.isEmpty()) { drawEmpty(ctx, x, y, size); return size; }
        boolean pressed = dev.steampad.input.GamepadInputDispatcher.isPhysicallyHeld(id);
        int dy = pressed ? y + 1 : y;
        return drawGlyph(ctx, tr, x, dy, size, id, pressed);
    }

    /** The actual icon drawing (brand PNG or vector fallback). */
    private static int drawGlyph(DrawContext ctx, TextRenderer tr, int x, int y, int size, String id, boolean pressed) {
        // Prefer the connected controller's brand PNG; fall back to the vector glyphs below.
        Identifier tex = ButtonTextureManager.resolveButton(id);
        if (tex != null) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, size, size,
                    ButtonTextureManager.TEX, ButtonTextureManager.TEX,
                    ButtonTextureManager.TEX, ButtonTextureManager.TEX,
                    pressed ? TINT_PRESSED : TINT_NORMAL);
            return size;
        }
        switch (id) {
            case "A", "B", "X", "Y" -> { drawDisc(ctx, tr, x, y, size, id); return size; }
            case "L3" -> { drawStick(ctx, tr, x, y, size, "L", 0, 0); return size; }
            case "R3" -> { drawStick(ctx, tr, x, y, size, "R", 0, 0); return size; }
            case "DUP" -> { drawDpad(ctx, x, y, size, 0, -1); return size; }
            case "DDOWN" -> { drawDpad(ctx, x, y, size, 0, 1); return size; }
            case "DLEFT" -> { drawDpad(ctx, x, y, size, -1, 0); return size; }
            case "DRIGHT" -> { drawDpad(ctx, x, y, size, 1, 0); return size; }
            case "LS_UP" -> { drawStick(ctx, tr, x, y, size, "L", 0, -1); return size; }
            case "LS_DOWN" -> { drawStick(ctx, tr, x, y, size, "L", 0, 1); return size; }
            case "LS_LEFT" -> { drawStick(ctx, tr, x, y, size, "L", -1, 0); return size; }
            case "LS_RIGHT" -> { drawStick(ctx, tr, x, y, size, "L", 1, 0); return size; }
            case "RS_UP" -> { drawStick(ctx, tr, x, y, size, "R", 0, -1); return size; }
            case "RS_DOWN" -> { drawStick(ctx, tr, x, y, size, "R", 0, 1); return size; }
            case "RS_LEFT" -> { drawStick(ctx, tr, x, y, size, "R", -1, 0); return size; }
            case "RS_RIGHT" -> { drawStick(ctx, tr, x, y, size, "R", 1, 0); return size; }
            case "LT", "RT" -> { return drawTrigger(ctx, tr, x, y, size, id); }
            case "LB", "RB", "START", "BACK" -> { return drawPill(ctx, tr, x, y, size, id); }
            default -> { return drawPill(ctx, tr, x, y, size, id); }
        }
    }

    private static void drawEmpty(DrawContext ctx, int x, int y, int size) {
        // Hollow rounded square = "no button assigned".
        Draw.fillRoundRect(ctx, x, y, x + size, y + size, 3, 0x33000000);
        int t = 1;
        ctx.fill(x, y, x + size, y + t, EMPTY);
        ctx.fill(x, y + size - t, x + size, y + size, EMPTY);
        ctx.fill(x, y, x + t, y + size, EMPTY);
        ctx.fill(x + size - t, y, x + size, y + size, EMPTY);
    }

    private static void drawDisc(DrawContext ctx, TextRenderer tr, int x, int y, int size, String letter) {
        int cx = x + size / 2, cy = y + size / 2, r = size / 2 - 1;
        Draw.fillCircle(ctx, cx, cy, r, FILL);
        Draw.outlineCircle(ctx, cx, cy, r, 1, EDGE);
        int tw = tr.getWidth(letter);
        ctx.drawText(tr, Text.literal(letter), cx - tw / 2, cy - 3, EDGE, false);
    }

    /** Stick = outer ring + inner disc with L/R letter; an arrow when a direction is given. */
    private static void drawStick(DrawContext ctx, TextRenderer tr, int x, int y, int size, String lr, int dx, int dy) {
        int cx = x + size / 2, cy = y + size / 2, r = size / 2 - 1;
        Draw.fillCircle(ctx, cx, cy, r, FILL);
        Draw.outlineCircle(ctx, cx, cy, r, 1, EDGE);
        Draw.outlineCircle(ctx, cx, cy, r - 3, 1, 0x66E9EEF4);   // inner hub ring
        if (dx == 0 && dy == 0) {
            int tw = tr.getWidth(lr);
            ctx.drawText(tr, Text.literal(lr), cx - tw / 2, cy - 3, EDGE, false);
        } else {
            arrow(ctx, cx, cy, Math.max(3, r - 2), dx, dy);
            // tiny L/R hint in the corner
            ctx.drawText(tr, Text.literal(lr), x + 1, y + size - 8, 0xAAE9EEF4, false);
        }
    }

    /** D-pad cross with the active arm lit white. */
    private static void drawDpad(DrawContext ctx, int x, int y, int size, int dx, int dy) {
        int cx = x + size / 2, cy = y + size / 2;
        int arm = size / 2 - 1, w = Math.max(2, size / 5);
        // base cross (dark)
        ctx.fill(cx - w, cy - arm, cx + w, cy + arm, FILL);
        ctx.fill(cx - arm, cy - w, cx + arm, cy + w, FILL);
        // lit arm
        if (dy < 0) ctx.fill(cx - w, cy - arm, cx + w, cy, EDGE);
        if (dy > 0) ctx.fill(cx - w, cy, cx + w, cy + arm, EDGE);
        if (dx < 0) ctx.fill(cx - arm, cy - w, cx, cy + w, EDGE);
        if (dx > 0) ctx.fill(cx, cy - w, cx + arm, cy + w, EDGE);
    }

    private static int drawTrigger(DrawContext ctx, TextRenderer tr, int x, int y, int size, String label) {
        int w = Math.max(size + 8, tr.getWidth(label) + 10);
        // trigger = pill with a rounded top (suggesting a lever)
        Draw.fillRoundRect(ctx, x, y + 2, x + w, y + size, 3, FILL);
        Draw.fillRoundRect(ctx, x + 3, y, x + w - 3, y + 5, 2, FILL);
        outline(ctx, x, y + 2, x + w, y + size, EDGE);
        int tw = tr.getWidth(label);
        ctx.drawText(tr, Text.literal(label), x + (w - tw) / 2, y + (size - 8) / 2 + 1, EDGE, false);
        return w;
    }

    private static int drawPill(DrawContext ctx, TextRenderer tr, int x, int y, int size, String label) {
        int w = Math.max(size + 8, tr.getWidth(label) + 10);
        Draw.fillRoundRect(ctx, x, y, x + w, y + size, size / 2, FILL);
        int tw = tr.getWidth(label);
        ctx.drawText(tr, Text.literal(label), x + (w - tw) / 2, y + (size - 8) / 2, EDGE, false);
        // subtle top highlight
        ctx.fill(x + 4, y + 1, x + w - 4, y + 2, 0x55E9EEF4);
        return w;
    }

    private static void outline(DrawContext ctx, int x1, int y1, int x2, int y2, int c) {
        ctx.fill(x1, y1, x2, y1 + 1, c);
        ctx.fill(x1, y2 - 1, x2, y2, c);
        ctx.fill(x1, y1, x1 + 1, y2, c);
        ctx.fill(x2 - 1, y1, x2, y2, c);
    }

    /** A filled directional triangle centered at (cx,cy). */
    private static void arrow(DrawContext ctx, int cx, int cy, int r, int dx, int dy) {
        for (int i = 0; i <= r; i++) {
            int half = (r - i);
            if (dy < 0)      ctx.fill(cx - half, cy - r + i, cx + half + 1, cy - r + i + 1, EDGE);
            else if (dy > 0) ctx.fill(cx - half, cy + r - i, cx + half + 1, cy + r - i + 1, EDGE);
            else if (dx < 0) ctx.fill(cx - r + i, cy - half, cx - r + i + 1, cy + half + 1, EDGE);
            else             ctx.fill(cx + r - i, cy - half, cx + r - i + 1, cy + half + 1, EDGE);
        }
    }
}
