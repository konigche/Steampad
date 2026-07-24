package dev.steampad.client.ui;

import dev.steampad.steam.SteamControllerHandleRef.ControllerType;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Draws controller button glyphs that match the controller family — Xbox-style colored ABXY discs,
 * PlayStation shape glyphs (×/○/□/△), Switch-style lettered discs, and labeled chips for shoulders
 * and triggers. All vector-drawn (no PNG assets) via {@link Draw}, so they scale and theme cleanly.
 */
public final class ControllerGlyphs {

    // Xbox face-button colors.
    private static final int XB_A = 0xFF6CC04A; // green
    private static final int XB_B = 0xFFE03B3B; // red
    private static final int XB_X = 0xFF2D7FE0; // blue
    private static final int XB_Y = 0xFFEFC03A; // yellow

    private static final int PS_TINT = 0xFFCBD6F2;
    private static final int CHIP_BG = 0xCC121A24;
    private static final int CHIP_BORDER = 0xFF4FA3FF;

    private ControllerGlyphs() {}

    // See ButtonIcon's identical constants for why this is a texture color multiplier (shape-accurate,
    // respects PNG alpha) and not a rectangle overlay — the rectangle version leaked a black square
    // around any non-square glyph (feedback: "aparece una sombra negra... fuera del glifo del botón").
    private static final int TINT_NORMAL = 0xFFFFFFFF;
    private static final int TINT_PRESSED = 0xFF999999;

    /**
     * Draws the glyph for {@code button} of the given {@code type} into a box of the given size at
     * top-left (x,y). Returns the width consumed (square for face buttons, wider for labeled chips).
     *
     * <p>Press feedback (feedback: "¿le puedes dar un efecto de presión cuando se presiona un botón?"):
     * while the button is physically held, brand PNG icons darken via the texture color multiplier and
     * the glyph nudges down 1px — this is the class the always-on gameplay HUD hints
     * ({@code GameplayHudOverlay}) actually use. Vector fallback icons only get the nudge. The Y-nudge
     * never changes the returned width, so a press can never shift the rest of the HUD row.
     */
    public static int draw(DrawContext ctx, TextRenderer tr, int x, int y, int size, ControllerType type, String button) {
        boolean pressed = dev.steampad.input.GamepadInputDispatcher.isPhysicallyHeld(button);
        int dy = pressed ? y + 1 : y;
        return drawGlyph(ctx, tr, x, dy, size, type, button, pressed);
    }

    private static int drawGlyph(DrawContext ctx, TextRenderer tr, int x, int y, int size, ControllerType type, String button, boolean pressed) {
        // Prefer the connected controller's brand PNG; fall back to the vector glyphs below.
        Identifier tex = ButtonTextureManager.resolveButton(button);
        if (tex != null) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, size, size,
                    ButtonTextureManager.TEX, ButtonTextureManager.TEX,
                    ButtonTextureManager.TEX, ButtonTextureManager.TEX,
                    pressed ? TINT_PRESSED : TINT_NORMAL);
            return size;
        }
        switch (button) {
            case "A", "B", "X", "Y" -> { drawFace(ctx, tr, x, y, size, type, button); return size; }
            default -> { return drawChip(ctx, tr, x, y, size, button); }
        }
    }

    /** Width the glyph for {@code button} will consume at the given size (without drawing). */
    public static int width(TextRenderer tr, int size, String button) {
        if (ButtonTextureManager.resolveButton(button) != null) return size;   // brand PNGs are square
        return switch (button) {
            case "A", "B", "X", "Y" -> size;
            default -> Math.max(size + 6, tr.getWidth(button) + 8);
        };
    }

    private static void drawFace(DrawContext ctx, TextRenderer tr, int x, int y, int size, ControllerType type, String b) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        int r = size / 2 - 1;

        if (type == ControllerType.PLAYSTATION) {
            Draw.fillCircle(ctx, cx, cy, r, 0xFF26314A);
            Draw.outlineCircle(ctx, cx, cy, r, 1, 0xFF4A5878);
            int s = r - 3;
            switch (b) {
                case "A" -> Draw.drawCross(ctx, cx, cy, s, 2, PS_TINT);                 // south = cross
                case "B" -> Draw.outlineCircle(ctx, cx, cy, s, 2, PS_TINT);             // east = circle
                case "X" -> Draw.drawSquareOutline(ctx, cx, cy, s, 2, PS_TINT);         // west = square
                case "Y" -> Draw.fillTriangleUp(ctx, cx, cy, s, PS_TINT);              // north = triangle
            }
            return;
        }

        int color;
        boolean nintendo = type == ControllerType.SWITCH;
        if (nintendo) {
            color = 0xFF2B2B2B;
        } else {
            color = switch (b) {
                case "A" -> XB_A;
                case "B" -> XB_B;
                case "X" -> XB_X;
                default  -> XB_Y;
            };
        }
        Draw.fillCircle(ctx, cx, cy, r, color);
        Draw.outlineCircle(ctx, cx, cy, r, 1, 0x55000000);
        int tw = tr.getWidth(b);
        ctx.drawText(tr, Text.literal(b), cx - tw / 2, cy - 4, 0xFFFFFFFF, false);
    }

    private static int drawChip(DrawContext ctx, TextRenderer tr, int x, int y, int size, String label) {
        int w = Math.max(size + 6, tr.getWidth(label) + 8);
        Draw.fillRoundRect(ctx, x, y, x + w, y + size, 3, CHIP_BG);
        // accent border
        ctx.fill(x, y, x + w, y + 1, CHIP_BORDER);
        ctx.fill(x, y + size - 1, x + w, y + size, CHIP_BORDER);
        ctx.fill(x, y, x + 1, y + size, CHIP_BORDER);
        ctx.fill(x + w - 1, y, x + w, y + size, CHIP_BORDER);
        int tw = tr.getWidth(label);
        ctx.drawText(tr, Text.literal(label), x + (w - tw) / 2, y + (size - 8) / 2, 0xFFFFFFFF, false);
        return w;
    }
}
