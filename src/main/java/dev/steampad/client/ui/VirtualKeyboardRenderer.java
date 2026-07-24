package dev.steampad.client.ui;

import dev.steampad.client.keyboard.KeyboardLayout;
import dev.steampad.client.keyboard.VirtualKeyboard;
import dev.steampad.config.ConfigManager;
import dev.steampad.config.PixelTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Draws the controller virtual keyboard as a vanilla-Minecraft pixel-art panel: sharp 1px black
 * outlines, chunky top-light / bottom-dark bevels (the classic MC button relief), shadowed key
 * labels, and inventory-style white corner brackets on the selected key. Colors come from the
 * user-selected {@link PixelTheme} preset (Vanilla stone-gray, Oak, Emerald, …).
 *
 * The preview strip was removed: the real text field (above the keyboard panel) shows the cursor and
 * live content — the preview was redundant and didn't display the caret position.
 */
public final class VirtualKeyboardRenderer {

    /** One theme's colors. Bevels/outline are alpha overlays so they read correctly on any base. */
    private record Palette(int panelBg, int panelEdge, int accent,
                           int keyBg, int specialBg, int selBg, int selEdge,
                           int keyText, int hintText) {}

    // MC-material palettes. All colors carry a full alpha byte (1.21.10 renders 0-alpha text invisible).
    private static Palette palette(PixelTheme t) {
        return switch (t) {
            case OAK      -> new Palette(0xE63A2C17, 0xFF241A0D, 0xFFD9B380,
                    0xFF8F6B3C, 0xFF6E5227, 0xFFB8945F, 0xFFF8E4B0, 0xFFFFF4DE, 0xFFC9AE84);
            case STONE    -> new Palette(0xE62A2D2F, 0xFF1B1D1F, 0xFFAAB4BC,
                    0xFF5E6265, 0xFF474B4E, 0xFF7B8288, 0xFFD8DEE4, 0xFFE8ECEF, 0xFF9AA4AC);
            case EMERALD  -> new Palette(0xE60F2418, 0xFF081710, 0xFF4FD07F,
                    0xFF1E4D33, 0xFF16382A, 0xFF2FA05E, 0xFFA8F0C4, 0xFFE8FFEF, 0xFF8CC8A2);
            case REDSTONE -> new Palette(0xE6260D0B, 0xFF170705, 0xFFFF5544,
                    0xFF57201C, 0xFF3E1714, 0xFFA53229, 0xFFFFB199, 0xFFFFEAE4, 0xFFC89288);
            case LAPIS    -> new Palette(0xE60E1C33, 0xFF081121, 0xFF5590E8,
                    0xFF1F3D6E, 0xFF172C4F, 0xFF3568C0, 0xFFA9CBFF, 0xFFE8F0FF, 0xFF8FAACE);
            case AMETHYST -> new Palette(0xE6221530, 0xFF150C1E, 0xFFB98AF0,
                    0xFF4E3468, 0xFF3A264E, 0xFF8A5FC0, 0xFFE0C8FF, 0xFFF4EAFF, 0xFFB49ACC);
            case NETHER   -> new Palette(0xE6201113, 0xFF120809, 0xFFE08050,
                    0xFF4A2A2E, 0xFF351D20, 0xFF7E3B44, 0xFFFFC49A, 0xFFFFEFE2, 0xFFBC9484);
            // VANILLA: the stone-gray MC button look.
            default       -> new Palette(0xE6262626, 0xFF161616, 0xFFFFFFFF,
                    0xFF6F6F6F, 0xFF565656, 0xFF8A8A8A, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFB8B8B8);
        };
    }

    // Pixel-art relief overlays (alpha over the theme base — consistent across every palette).
    private static final int BEVEL_LIGHT = 0x59FFFFFF;   // top/left highlight
    private static final int BEVEL_DARK  = 0x66000000;   // bottom/right shade (chunky, 2px bottom)
    private static final int OUTLINE     = 0xFF000000;   // crisp 1px black outline (MC-button style)

    // Cached palette (themes change rarely; avoids a switch per key per frame).
    private static Palette cached = palette(PixelTheme.VANILLA);
    private static PixelTheme cachedTheme = PixelTheme.VANILLA;

    private static Palette theme() {
        PixelTheme t = ConfigManager.getGlobal().virtualKeyboardTheme;
        if (t == null) t = PixelTheme.VANILLA;   // old configs / corrupt value
        if (t != cachedTheme) { cachedTheme = t; cached = palette(t); }
        return cached;
    }

    // Footer hint entries: [icon, label] or [icon, label, chordIcon] for a two-button combo.
    // Icon is a ButtonIcon glyph id or a Unicode char.
    private static final String[][] FOOTER_HINTS = {
        { "A",  "steampad.keyboard.hint.confirm" },
        { "X",  "steampad.keyboard.hint.backspace" },
        { "Y",  "steampad.keyboard.hint.space" },
        { "RT", "steampad.keyboard.hint.enter" },
        { "LT", "steampad.keyboard.hint.shift" },
        { "B",  "steampad.keyboard.hint.close" },
        // DUP+RT moves the panel top/bottom (feedback: "no olvides poner el glifo ya abierto el
        // teclado de como se mueve"). RT's own "enter" still fires on a plain press — only pressing
        // it WHILE DUP is held triggers the reposition instead (see handleKeyboardInput).
        { "RT", "steampad.keyboard.hint.move", "DUP" },
    };

    // Dual-pointer mode swaps in LB/RB press hints first (the headline interaction of that mode).
    private static final String[][] FOOTER_HINTS_DUAL = {
        { "LB", "steampad.keyboard.hint.press_left" },
        { "RB", "steampad.keyboard.hint.press_right" },
        { "A",  "steampad.keyboard.hint.confirm" },
        { "X",  "steampad.keyboard.hint.backspace" },
        { "Y",  "steampad.keyboard.hint.space" },
        { "RT", "steampad.keyboard.hint.enter" },
        { "LT", "steampad.keyboard.hint.shift" },
        { "B",  "steampad.keyboard.hint.close" },
        { "RT", "steampad.keyboard.hint.move", "DUP" },
    };

    // Orb colors: left = warm white, right = the accent blue — semi-transparent so the key label
    // stays readable underneath (the Steam keyboard's two translucent "bolitas").
    private static final int ORB_L      = 0x7DFFFFFF;
    private static final int ORB_L_CORE = 0xE6FFFFFF;
    private static final int ORB_R      = 0x7D57B0FF;
    private static final int ORB_R_CORE = 0xE68FD0FF;

    private VirtualKeyboardRenderer() {}

    /**
     * Main render entry. Called from the after-screen-render hook each frame.
     * Renders either the full keyboard panel (active) or a small "press A" badge (eligible only).
     */
    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Screen screen = mc.currentScreen;
        if (screen == null) return;

        if (VirtualKeyboard.isActive()) {
            long tKb = dev.steampad.util.TickProfiler.begin();
            renderKeyboard(ctx, mc, screen);
            dev.steampad.util.TickProfiler.end(dev.steampad.util.TickProfiler.KEYBOARD_DRAW, tKb);
        } else if (VirtualKeyboard.isEligible()) {
            renderEligibleBadge(ctx, mc, screen);
        }
    }

    // ---- Full keyboard panel -------------------------------------------------------

    private static void renderKeyboard(DrawContext ctx, MinecraftClient mc, Screen screen) {
        TextRenderer tr = mc.textRenderer;
        Palette P = theme();
        int sw = screen.width;
        boolean isChatScreen = screen instanceof net.minecraft.client.gui.screen.ChatScreen;

        // Shared geometry (same rects the stick's nearest-key logic uses — always in sync).
        List<List<KeyboardLayout.Key>> grid = VirtualKeyboard.grid();
        dev.steampad.client.keyboard.KeyboardGeometry.Panel p = dev.steampad.client.keyboard.KeyboardGeometry.layout(
                sw, screen.height, isChatScreen, VirtualKeyboard.isPositionTop(), grid);

        // Panel: flat fill + pixel-art top border (1px outline + 1px accent — no gradients).
        ctx.fill(0, p.panelTop(), sw, p.panelBot(), P.panelBg());
        ctx.fill(0, p.panelTop(), sw, p.panelTop() + 1, OUTLINE);
        ctx.fill(0, p.panelTop() + 1, sw, p.panelTop() + 2, P.accent());

        // Footer hint row at the bottom of the panel (shortcut glyphs).
        renderFooterHints(ctx, tr, sw, p.footerY(), P);

        boolean shift = VirtualKeyboard.isShift();
        boolean dual = VirtualKeyboard.isDualStick();
        VirtualKeyboard.ensurePointersForRender(p);
        // Selection is hidden until its source is actually active (feedback: "debe estar limpio hasta
        // que uno de los sticks se mueva, y después de un instante... desaparece toda selección") —
        // D-pad is exempt (persistent highlight, it's the only way to see where you are with it).
        boolean leftVisible = VirtualKeyboard.leftSelectionVisible();
        boolean rightVisible = dual && VirtualKeyboard.rightSelectionVisible();
        for (dev.steampad.client.keyboard.KeyboardGeometry.KeyRect kr : p.keys()) {
            KeyboardLayout.Key k = grid.get(kr.row()).get(kr.col());
            boolean selected = leftVisible && kr.row() == VirtualKeyboard.selRow() && kr.col() == VirtualKeyboard.selCol();
            boolean selectedR = rightVisible && kr.row() == VirtualKeyboard.selRow2() && kr.col() == VirtualKeyboard.selCol2();
            boolean sunken = VirtualKeyboard.isKeySunken(kr.row(), kr.col());
            drawKey(ctx, tr, kr.x(), kr.y(), kr.w(), kr.h(), k, selected || selectedR, shift, sunken, P);
            // Inventory-style selection: corner brackets around each pointer's key. The right
            // pointer's brackets use the accent color so the two selections stay tellable apart.
            if (selected) drawCornerBrackets(ctx, kr.x() - 2, kr.y() - 2, kr.w() + 4, kr.h() + 4, P.selEdge());
            if (selectedR && !selected) {
                drawCornerBrackets(ctx, kr.x() - 2, kr.y() - 2, kr.w() + 4, kr.h() + 4, ORB_R_CORE);
            }
        }

        // The two floating orbs — dual mode only, drawn ABOVE the keys, semi-transparent, exactly
        // where each stick's free-floating point currently is. Classic mode stays visually untouched.
        // Same visibility gate as the brackets above — no orb until its stick moves, and it fades
        // with the rest of the selection after the idle timeout.
        if (dual) {
            if (leftVisible) {
                drawOrb(ctx, (int) Math.round(VirtualKeyboard.pointerLX()),
                        (int) Math.round(VirtualKeyboard.pointerLY()), ORB_L, ORB_L_CORE);
            }
            if (rightVisible && VirtualKeyboard.pointerRValid()) {
                drawOrb(ctx, (int) Math.round(VirtualKeyboard.pointerRX()),
                        (int) Math.round(VirtualKeyboard.pointerRY()), ORB_R, ORB_R_CORE);
            }
        }
    }

    /** A small translucent disc with a brighter core — the Steam-keyboard "bolita". Drawn as
     *  non-overlapping horizontal strips so the translucency stays even (no double-blended bands). */
    private static void drawOrb(DrawContext ctx, int cx, int cy, int halo, int core) {
        ctx.fill(cx - 2, cy - 5, cx + 3, cy - 4, halo);
        ctx.fill(cx - 4, cy - 4, cx + 5, cy - 2, halo);
        ctx.fill(cx - 5, cy - 2, cx + 6, cy + 3, halo);
        ctx.fill(cx - 4, cy + 3, cx + 5, cy + 5, halo);
        ctx.fill(cx - 2, cy + 5, cx + 3, cy + 6, halo);
        ctx.fill(cx - 2, cy - 2, cx + 3, cy + 3, core);
    }

    /** Four corner brackets around the selected key (same look as the inventory slot selection). */
    private static void drawCornerBrackets(DrawContext ctx, int x, int y, int w, int h, int c) {
        int len = 5, t = 2;
        ctx.fill(x, y, x + len, y + t, c);
        ctx.fill(x, y, x + t, y + len, c);
        ctx.fill(x + w - len, y, x + w, y + t, c);
        ctx.fill(x + w - t, y, x + w, y + len, c);
        ctx.fill(x, y + h - t, x + len, y + h, c);
        ctx.fill(x, y + h - len, x + t, y + h, c);
        ctx.fill(x + w - len, y + h - t, x + w, y + h, c);
        ctx.fill(x + w - t, y + h - len, x + w, y + h, c);
    }

    /**
     * Renders the bottom hint row: brand-specific button glyphs + short labels.
     *
     * <p>Dual-stick mode adds 2 extra entries (LB/RB press hints) on top of the classic set, and the
     * old code just stopped drawing once it ran off the panel's right edge — silently dropping
     * whichever hint came last in the array (the DUP+RT "move keyboard" hint, feedback: "ya no
     * aparece... creo que como tenemos muchos se salió de pantalla"). Measures the row FIRST at the
     * normal gap; if it doesn't fit, shrinks only the gap between entries (never the icons/text
     * themselves) so every hint always gets drawn.
     */
    private static void renderFooterHints(DrawContext ctx, TextRenderer tr, int sw, int y, Palette P) {
        ctx.fill(0, y - 1, sw, y, P.panelEdge());
        int iconSz = 12;
        String[][] hints = VirtualKeyboard.isDualStick() ? FOOTER_HINTS_DUAL : FOOTER_HINTS;
        int available = sw - 16;

        int coreWidth = 0;   // icons + "+"-chords + labels, WITHOUT the inter-entry gap
        for (String[] hint : hints) {
            coreWidth += hintCoreWidth(tr, hint, iconSz);
        }
        int minGap = 4, baseGap = 10;
        int gapBudget = available - coreWidth;
        int gap = hints.length > 1 ? Math.max(minGap, Math.min(baseGap, gapBudget / (hints.length - 1))) : baseGap;

        int x = 8;
        for (String[] hint : hints) {
            String id = hint[0];
            String label = Text.translatable(hint[1]).getString();
            // A 3rd entry is a chord modifier drawn before the main glyph as "[chord]+[main]".
            if (hint.length > 2 && !hint[2].isEmpty()) {
                String chordId = hint[2];
                ButtonIcon.draw(ctx, tr, x, y + 1, iconSz, chordId);
                x += ButtonIcon.width(tr, iconSz, chordId) + 1;
                ctx.drawText(tr, Text.literal("+"), x, y + 3, P.hintText(), false);
                x += tr.getWidth("+") + 2;
            }
            // Draw the button glyph (brand-specific PNG if available, else text fallback).
            ButtonIcon.draw(ctx, tr, x, y + 1, iconSz, id);
            int glyphW = ButtonIcon.width(tr, iconSz, id);
            x += glyphW + 2;
            int lw = tr.getWidth(label);
            ctx.drawText(tr, Text.literal(label), x, y + 3, P.hintText(), false);
            x += lw + gap;
        }
    }

    /** Width one hint entry occupies excluding the trailing inter-entry gap. */
    private static int hintCoreWidth(TextRenderer tr, String[] hint, int iconSz) {
        int w = 0;
        if (hint.length > 2 && !hint[2].isEmpty()) {
            w += ButtonIcon.width(tr, iconSz, hint[2]) + 1 + tr.getWidth("+") + 2;
        }
        w += ButtonIcon.width(tr, iconSz, hint[0]) + 2;
        w += tr.getWidth(Text.translatable(hint[1]).getString());
        return w;
    }

    // ---- "Press A" badge (eligible but not active) --------------------------------

    /** Small translucent badge near the bottom-right: "[A] Keyboard". Shows how to open. */
    private static void renderEligibleBadge(DrawContext ctx, MinecraftClient mc, Screen screen) {
        TextRenderer tr = mc.textRenderer;
        Palette P = theme();
        String label = Text.translatable("steampad.keyboard.badge").getString();
        int iconSz = 10;
        int iconW = ButtonIcon.width(tr, iconSz, "A");
        int textW = tr.getWidth(label);
        int totalW = iconW + 3 + textW + 8;
        int bx = screen.width - totalW - 6;
        int by = screen.height - 14;
        ctx.fill(bx - 4, by - 2, bx + totalW, by + 12, P.panelBg());
        ctx.fill(bx - 4, by - 2, bx + totalW, by - 1, P.accent());
        ButtonIcon.draw(ctx, tr, bx, by + 1, iconSz, "A");
        ctx.drawText(tr, Text.literal(label), bx + iconW + 3, by + 2, P.hintText(), false);
    }

    // ---- Theme preview (used by the theme picker in Ajustes) ----------------------

    /**
     * Draws a compact 3-key strip in {@code theme}'s actual palette — the same {@link #palette} and
     * {@link #drawKey} the real keyboard uses, so what you see here is exactly what you get, no
     * separate "preview-only" color table to drift out of sync.
     */
    public static void renderThemePreview(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h,
                                          PixelTheme theme) {
        Palette P = palette(theme);
        ctx.fill(x, y, x + w, y + h, P.panelBg());
        ctx.fill(x, y, x + w, y + 1, OUTLINE);
        ctx.fill(x, y + 1, x + w, y + 2, P.accent());

        int pad = 4, gap = 3;
        int keyW = (w - pad * 2 - gap * 2) / 3;
        int keyH = h - pad * 2;
        int ky = y + pad;
        KeyboardLayout.Key[] sample = {
            new KeyboardLayout.Key(KeyboardLayout.KeyType.CHAR, "A", 'A', 1),
            new KeyboardLayout.Key(KeyboardLayout.KeyType.CHAR, "S", 'S', 1),
            new KeyboardLayout.Key(KeyboardLayout.KeyType.SPACE, "␣", 0, 1),
        };
        for (int i = 0; i < 3; i++) {
            int kx = x + pad + i * (keyW + gap);
            drawKey(ctx, tr, kx, ky, keyW, keyH, sample[i], i == 1, false, P);
        }
    }

    // ---- Key drawing --------------------------------------------------------------

    /** Back-compat overload (theme preview) — never sunken. */
    private static void drawKey(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h,
                                KeyboardLayout.Key k, boolean selected, boolean shift, Palette P) {
        drawKey(ctx, tr, x, y, w, h, k, selected, shift, false, P);
    }

    /**
     * One key, MC-button style: 1px black outline, flat themed face, 1px light bevel on top/left,
     * chunky 2px dark bevel on the bottom (the classic vanilla button relief), shadowed label.
     * {@code sunken} renders the just-pressed state: label nudged 1px down + a dark overlay on the
     * face — the same press treatment the button glyphs use (feedback: "al presionar una letra del
     * teclado que se hunda, el mismo efecto que con los botones").
     */
    private static void drawKey(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h,
                                KeyboardLayout.Key k, boolean selected, boolean shift, boolean sunken,
                                Palette P) {
        boolean special = k.type() != KeyboardLayout.KeyType.CHAR;
        int bg = selected ? P.selBg() : (special ? P.specialBg() : P.keyBg());

        ctx.fill(x, y, x + w, y + h, selected ? P.selEdge() : OUTLINE);   // outline
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);                 // face
        if (!sunken) {
            ctx.fill(x + 1, y + 1, x + w - 1, y + 2, BEVEL_LIGHT);        // top highlight
            ctx.fill(x + 1, y + 2, x + 2, y + h - 2, BEVEL_LIGHT);        // left highlight
            ctx.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, BEVEL_DARK); // bottom shade (2px, chunky)
            ctx.fill(x + w - 2, y + 2, x + w - 1, y + h - 3, BEVEL_DARK); // right shade
        } else {
            // Pressed: bevels inverted (dark on top = the face reads as pushed in) + dark overlay.
            ctx.fill(x + 1, y + 1, x + w - 1, y + 3, BEVEL_DARK);
            ctx.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, BEVEL_LIGHT);
            ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x40000000);
        }

        // Label. Shift up-cases letter keys for clarity; Shift key lights up when active.
        String lbl = k.label();
        if (k.type() == KeyboardLayout.KeyType.CHAR && shift && lbl.length() == 1 && Character.isLetter(lbl.charAt(0))) {
            lbl = lbl.toUpperCase();
        }
        int color = P.keyText();
        if (k.type() == KeyboardLayout.KeyType.SHIFT && shift) color = P.selEdge();
        int tw = tr.getWidth(lbl);
        // Shadowed text = the vanilla MC button look (readable on every theme).
        ctx.drawText(tr, Text.literal(lbl), x + (w - tw) / 2, y + (h - 8) / 2 + (sunken ? 1 : 0), color, true);
    }

}
