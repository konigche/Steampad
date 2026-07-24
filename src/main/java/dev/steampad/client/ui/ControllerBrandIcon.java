package dev.steampad.client.ui;

import dev.steampad.steam.SteamControllerHandleRef.ControllerType;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

/**
 * Stylized brand/category marks for the controller-select list, drawn from primitives (no PNG
 * assets) so they stay crisp and theme with the UI. These are simplified, original marks that evoke
 * each brand (Steam Deck silhouette, 8BitDo retro grid, Xbox sphere-X, PlayStation shapes, Switch
 * Joy-Cons), chosen to be recognizable without reproducing trademarked logos pixel-for-pixel.
 */
public final class ControllerBrandIcon {

    private ControllerBrandIcon() {}

    private enum Brand { STEAM_DECK, EIGHTBITDO, XBOX, PLAYSTATION, SWITCH, STEAM, GENERIC }

    /** Draws a brand mark filling a {@code size} box at top-left (x,y). */
    public static void draw(DrawContext ctx, int x, int y, int size, ControllerType type, String name) {
        // Prefer the per-brand silhouette PNG (controller.png); fall back to the vector marks below.
        net.minecraft.util.Identifier tex = ButtonTextureManager.resolveSilhouette(type, name);
        if (tex != null) {
            ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f,
                    size, size, ButtonTextureManager.TEX, ButtonTextureManager.TEX,
                    ButtonTextureManager.TEX, ButtonTextureManager.TEX);
            return;
        }
        Brand brand = resolve(type, name);
        int cx = x + size / 2;
        int cy = y + size / 2;
        int r = size / 2 - 1;

        switch (brand) {
            case STEAM_DECK   -> steamDeck(ctx, x, y, size, cx, cy, r);
            case EIGHTBITDO   -> eightBitDo(ctx, x, y, size);
            case XBOX         -> xbox(ctx, cx, cy, r);
            case PLAYSTATION  -> playstation(ctx, x, y, size, cx, cy);
            case SWITCH       -> nswitch(ctx, x, y, size, cx, cy, r);
            case STEAM        -> steam(ctx, cx, cy, r);
            case GENERIC      -> generic(ctx, x, y, size, cx, cy);
        }
    }

    /**
     * Human-readable manufacturer/brand for the controller sub-line (e.g. "8BitDo", "Sony"), derived
     * from the device name and category. Replaces the raw enum name ("GENERIC") that used to show.
     * Falls back to "Generic" only when nothing recognizable is found.
     */
    public static String manufacturer(ControllerType type, String name) {
        return switch (resolve(type, name)) {
            case EIGHTBITDO  -> "8BitDo";
            case STEAM_DECK  -> "Valve";
            case STEAM       -> "Valve";
            case PLAYSTATION -> "Sony";
            case XBOX        -> "Microsoft";
            case SWITCH      -> "Nintendo";
            case GENERIC     -> "Generic";
        };
    }

    private static Brand resolve(ControllerType type, String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.contains("8bitdo") || n.contains("8bit")) return Brand.EIGHTBITDO;
        if (n.contains("steam deck") || type == ControllerType.STEAM_DECK) return Brand.STEAM_DECK;
        if (n.contains("dualsense") || n.contains("dualshock") || n.contains("sony")
                || n.contains("playstation") || type == ControllerType.PLAYSTATION) return Brand.PLAYSTATION;
        if (n.contains("xbox") || type == ControllerType.XBOX) return Brand.XBOX;
        if (n.contains("switch") || n.contains("joy-con") || n.contains("joycon")
                || n.contains("nintendo") || type == ControllerType.SWITCH) return Brand.SWITCH;
        if (n.contains("steam") || type == ControllerType.STEAM_CONTROLLER) return Brand.STEAM;
        return Brand.GENERIC;
    }

    // — marks —

    private static void steamDeck(DrawContext ctx, int x, int y, int size, int cx, int cy, int r) {
        // Handheld silhouette: wide body + two sticks.
        int bodyL = x + 1, bodyR = x + size - 1, bodyT = cy - size / 5, bodyB = cy + size / 5;
        Draw.fillRoundRect(ctx, bodyL, bodyT, bodyR, bodyB, 4, 0xFF2B2B2E);
        Draw.fillCircle(ctx, x + size / 4, cy, Math.max(2, r / 4), 0xFF4FA3FF);
        Draw.fillCircle(ctx, x + size * 3 / 4, cy, Math.max(2, r / 4), 0xFF4FA3FF);
        Draw.outlineCircle(ctx, cx, cy, r, 1, 0xFF454549);
    }

    private static void eightBitDo(DrawContext ctx, int x, int y, int size) {
        // Retro 3x3 grid: mix of squares and a circle (the 8BitDo button motif) on a cream tile.
        Draw.fillRoundRect(ctx, x, y, x + size, y + size, 3, 0xFFEFE7D2);
        int pad = 2, cell = (size - pad * 2) / 3, c0 = x + pad, r0 = y + pad, dark = 0xFF20242B;
        // squares
        ctx.fill(c0, r0, c0 + cell - 1, r0 + cell - 1, dark);
        ctx.fill(c0 + cell, r0 + cell, c0 + 2 * cell - 1, r0 + 2 * cell - 1, dark);
        ctx.fill(c0 + 2 * cell, r0, c0 + 3 * cell - 1, r0 + cell - 1, dark);
        ctx.fill(c0, r0 + 2 * cell, c0 + cell - 1, r0 + 3 * cell - 1, dark);
        // a circle button
        Draw.fillCircle(ctx, c0 + 2 * cell + cell / 2, r0 + 2 * cell + cell / 2, Math.max(1, cell / 2 - 1), 0xFFC0392B);
    }

    private static void xbox(DrawContext ctx, int cx, int cy, int r) {
        Draw.fillCircle(ctx, cx, cy, r, 0xFF107C10);          // Xbox green
        Draw.fillCircle(ctx, cx, cy, r - 2, 0xFF0E6B0E);
        int s = r - 3;
        Draw.line(ctx, cx - s, cy - s, cx + s, cy + s, 2, 0xFFFFFFFF);
        Draw.line(ctx, cx + s, cy - s, cx - s, cy + s, 2, 0xFFFFFFFF);
    }

    private static void playstation(DrawContext ctx, int x, int y, int size, int cx, int cy) {
        Draw.fillRoundRect(ctx, x, y, x + size, y + size, 3, 0xFF1C3A8F);
        int q = size / 4, tint = 0xFFFFFFFF;
        Draw.fillTriangleUp(ctx, cx, y + q, q - 2, tint);                 // triangle (top)
        Draw.outlineCircle(ctx, x + q, cy + q - 1, q - 2, 2, tint);       // circle (left-ish)
        Draw.drawCross(ctx, x + size - q, cy + q - 1, q - 3, 2, tint);    // cross (right-ish)
        Draw.drawSquareOutline(ctx, cx, y + size - q, q - 3, 2, tint);    // square (bottom)
    }

    private static void nswitch(DrawContext ctx, int x, int y, int size, int cx, int cy, int r) {
        // Two Joy-Cons flanking a dark screen.
        int jw = size / 4;
        Draw.fillRoundRect(ctx, x, y, x + jw, y + size, 3, 0xFFE60012);          // left red
        Draw.fillRoundRect(ctx, x + size - jw, y, x + size, y + size, 3, 0xFF00A0E9); // right blue
        ctx.fill(x + jw, y + 2, x + size - jw, y + size - 2, 0xFF1A1A1A);        // screen
    }

    private static void steam(DrawContext ctx, int cx, int cy, int r) {
        Draw.fillCircle(ctx, cx, cy, r, 0xFF1B2838);
        Draw.outlineCircle(ctx, cx, cy, r, 1, 0xFF66C0F4);
        Draw.fillCircle(ctx, cx + r / 3, cy - r / 3, Math.max(2, r / 3), 0xFF66C0F4);
        Draw.fillCircle(ctx, cx - r / 4, cy + r / 4, Math.max(1, r / 5), 0xFF66C0F4);
        Draw.line(ctx, cx - r / 4, cy + r / 4, cx + r / 3, cy - r / 3, 1, 0xFF66C0F4);
    }

    private static void generic(DrawContext ctx, int x, int y, int size, int cx, int cy) {
        // Stylized gamepad: rounded body, two grips, a D-pad on the left and face buttons on the right.
        int bodyT = cy - size / 5, bodyB = cy + size / 5;
        Draw.fillRoundRect(ctx, x + 1, bodyT - 1, x + size - 1, bodyB + 1, 5, 0xFF3A3F47);
        // grips
        Draw.fillCircle(ctx, x + size / 5, bodyB, Math.max(2, size / 7), 0xFF3A3F47);
        Draw.fillCircle(ctx, x + size * 4 / 5, bodyB, Math.max(2, size / 7), 0xFF3A3F47);
        // left D-pad (cross)
        int dcx = x + size / 4, w = Math.max(1, size / 14), arm = Math.max(2, size / 8);
        ctx.fill(dcx - w, cy - arm, dcx + w, cy + arm, 0xFFB9C2CE);
        ctx.fill(dcx - arm, cy - w, dcx + arm, cy + w, 0xFFB9C2CE);
        // right face buttons (4 dots)
        int fcx = x + size * 3 / 4, off = Math.max(2, size / 8), r = Math.max(1, size / 12);
        Draw.fillCircle(ctx, fcx, cy - off, r, 0xFF8A93A0);
        Draw.fillCircle(ctx, fcx, cy + off, r, 0xFF8A93A0);
        Draw.fillCircle(ctx, fcx - off, cy, r, 0xFF8A93A0);
        Draw.fillCircle(ctx, fcx + off, cy, r, 0xFF8A93A0);
    }
}
