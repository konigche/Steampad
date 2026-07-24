package dev.steampad.radial;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.PixelTheme;
import dev.steampad.config.RadialConfig;
import dev.steampad.radial.icon.RadialIconResolver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Draws the radial menu: donut backdrop, round chips, icons, and slot labels.
 *
 * Layout: slot 0 at top (270°), going clockwise. Colors come from the per-controller
 * {@link PixelTheme} preset (Vanilla neutral grays by default — matches the vanilla pause/
 * inventory panel tones — through Emerald, Redstone, etc., same set as the virtual keyboard). A
 * "jelly" blob eases from the center toward the highlighted chip, stretching while it travels
 * (E11) — kept theme-neutral (white-based) by design, it's a decorative accent, not a surface.
 */
public final class RadialRenderer {

    private RadialRenderer() {}

    /**
     * Scale for the wheel's control-hint glyphs (ghost-wheel LB/RB switch glyph, the RS/A/LT/RB hint
     * row) — the HUD glyph-scale slider (feedback: "agrega un slider para poder cambiar la escala de
     * los glifos en gameplay, debe afectar a todo, inventario, rueda etc."). Deliberately does NOT
     * touch chipRadius/slot icon sizing — that's a separate, already-configurable wheel geometry
     * setting, not a "button glyph". Clamped defensively — the slider itself limits to [0.5, 2.0].
     */
    private static float glyphScale() {
        float s = ConfigManager.getGlobal().ingameButtonGuideScale;
        return s < 0.5f ? 0.5f : (s > 2.0f ? 2.0f : s);
    }

    /** Default chip radius — used only as the fallback when a config predates {@code chipRadius}. */
    private static final int CHIP_R = 18;

    /** Chip radius for this config, clamped to something that still renders sanely. */
    private static int chipRadius(RadialConfig cfg) {
        float r = cfg.chipRadius > 0 ? cfg.chipRadius : CHIP_R;
        return Math.round(Math.max(10f, Math.min(30f, r)));
    }

    /** Default icon scale — used only as the fallback when a config predates {@code iconScale}
     *  (same defensive pattern as {@link #chipRadius}: Gson leaves it 0 for old saved JSON). */
    private static final float ICON_SCALE_DEFAULT = 1.4f;

    /** Icon scale for this config, clamped to a sane range. */
    private static float iconScale(RadialConfig cfg) {
        float s = cfg.iconScale > 0 ? cfg.iconScale : ICON_SCALE_DEFAULT;
        return Math.max(0.7f, Math.min(2.2f, s));
    }

    /** One theme's colors for the wheel's main surfaces. */
    private record Palette(int chipBg, int chipSel, int backdrop, int edgeSoft, int accent, int hintText) {}

    // MC-material palettes, echoing the same families as the keyboard theme. All colors carry a full
    // alpha byte (1.21.10 renders 0-alpha text invisible).
    private static Palette palette(PixelTheme t) {
        return switch (t) {
            case OAK      -> new Palette(0xE63A2C17, 0xF0553F22, 0xB0201509, 0x40D9B380, 0xFFF8E4B0, 0xCCE8D2A8);
            case STONE    -> new Palette(0xE63A3D40, 0xF04A4E52, 0xB01A1C1E, 0x40AAB4BC, 0xFFE8ECEF, 0xCCD0D6DC);
            case EMERALD  -> new Palette(0xE614301F, 0xF01E4D33, 0xB00A1810, 0x404FD07F, 0xFFB8F0CC, 0xCCA8D8B8);
            case REDSTONE -> new Palette(0xE6371210, 0xF0572019, 0xB0200A08, 0x40FF5544, 0xFFFFC4B0, 0xCCE0A898);
            case LAPIS    -> new Palette(0xE6122040, 0xF01F3D6E, 0xB00A1226, 0x405590E8, 0xFFB8D4FF, 0xCCA0C0EC);
            case AMETHYST -> new Palette(0xE6241733, 0xF0402A55, 0xB0140D1E, 0x40B98AF0, 0xFFE4CCFF, 0xCCC4A8DC);
            case NETHER   -> new Palette(0xE624120F, 0xF0432521, 0xB0170A08, 0x40E08050, 0xFFFFCCA0, 0xCCD0A088);
            // VANILLA: the neutral-gray look this wheel always had (B16).
            default       -> new Palette(0xE6212121, 0xF0333333, 0xB0101010, 0x40FFFFFF, 0xFFFFFFFF, 0xCCE6ECF2);
        };
    }

    // E11 jelly blob: eases from the center toward the selected chip with real delta-time.
    private static double blobX, blobY;
    private static boolean blobLive = false;
    private static long blobLastNanos = 0L;
    /** Easing time-constant (s): small = snappy, still visibly fluid. */
    private static final double BLOB_TAU = 0.055;

    // E10 carousel: switching wheels kicks the active wheel in from the side and eases it to center.
    private static double carouselOffset = 0.0;
    private static long carouselLastNanos = 0L;
    private static final double CAROUSEL_TAU = 0.07;

    /** Reset the jelly blob to the wheel center (call when the wheel opens). */
    public static void resetBlob() {
        blobLive = false;
    }

    /** Kick the carousel animation when the wheel page changes (E10). */
    public static void onPageSwitched(int dir) {
        carouselOffset = dir >= 0 ? 110 : -110;
        carouselLastNanos = 0L;
        resetBlob();
    }

    /** Per-slot hook invoked from INSIDE the existing chip-drawing loop, at the slot's own already-
     *  computed screen position/radius — lets a caller (the emote wheel) substitute a live 3D
     *  thumbnail for the flat icon on a per-slot basis, using the SAME geometry the icon itself would
     *  have used (no separate/divergent position math, no risk of drifting out of sync with the
     *  jelly-blob/carousel animation state this class already owns). Callers that don't need this
     *  (the regular radial menu) simply never pass one — see the overload below. */
    @FunctionalInterface
    public interface SlotThumbnailRenderer {
        /** @return true if this hook drew something for the slot (skips the normal icon draw),
         *          false to let the chip's normal icon render as usual. */
        boolean render(DrawContext ctx, int slotIndex, RadialSlot slot, int sx, int sy, int chipRadius, boolean selected);
    }

    /**
     * @param handle the controller whose {@link RadialConfig} styles this render — callers with a
     *               specific controller in mind (the live overlay, the editor preview) must pass it
     *               explicitly rather than relying on the globally-active controller, which can
     *               diverge for a tick (e.g. mid-editing another pad, or the active controller
     *               disconnecting while the wheel is still closing).
     * @param wheelCount how many wheels the CALLER's own wheel system has configured, and
     * @param page       which one is showing — passed explicitly rather than read from
     *                   {@code RadialMenuController} directly so this renderer stays reusable by any
     *                   independent wheel system (the dedicated emote wheel has its own
     *                   {@code EmoteWheelController} with its own count/page, see FASE 63 decoupling).
     */
    public static void render(DrawContext ctx, int centerX, int centerY,
                              List<RadialSlot> slots, int selectedSlot, long handle,
                              int wheelCount, int page) {
        render(ctx, centerX, centerY, slots, selectedSlot, handle, wheelCount, page, null);
    }

    /** Same as the 8-arg overload, with an optional per-slot thumbnail hook (see
     *  {@link SlotThumbnailRenderer}) — {@code null} reproduces the 8-arg overload exactly. */
    public static void render(DrawContext ctx, int centerX, int centerY,
                              List<RadialSlot> slots, int selectedSlot, long handle,
                              int wheelCount, int page, SlotThumbnailRenderer thumbnailRenderer) {
        RadialConfig cfg = getConfig(handle);
        Palette pal = palette(cfg.theme == null ? PixelTheme.VANILLA : cfg.theme);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // E10 carousel ease: after a page switch the wheel slides in from the side.
        long now = System.nanoTime();
        if (carouselOffset != 0.0) {
            double dt = carouselLastNanos == 0L ? 0.0 : Math.min(0.1, (now - carouselLastNanos) / 1.0e9);
            carouselOffset *= Math.exp(-dt / CAROUSEL_TAU);
            if (Math.abs(carouselOffset) < 0.8) carouselOffset = 0.0;
        }
        carouselLastNanos = now;
        int cx = centerX + (int) Math.round(carouselOffset);

        int count = Math.max(1, slots.size());
        int chipR = chipRadius(cfg);
        int ring = Math.max(54, (int) cfg.outerRadius);
        // Scale the ring a little with slot count so 12 chips don't overlap.
        if (count > 8) ring += (count - 8) * 4;
        // Bigger-than-default chips also need more ring so neighbours keep their separation.
        if (chipR > CHIP_R) ring += (chipR - CHIP_R) * 2;

        // Soft donut backdrop behind the chips, themed per the controller's PixelTheme.
        if (cfg.showBackground) {
            dev.steampad.client.ui.Draw.fillRing(ctx, cx, centerY, ring + chipR + 8, ring - chipR - 10, pal.backdrop());
            dev.steampad.client.ui.Draw.outlineCircle(ctx, cx, centerY, ring + chipR + 8, 1, pal.edgeSoft());
        }

        // E11 — jelly blob easing toward the selected chip (drawn under the chips).
        drawJellyBlob(ctx, cx, centerY, ring, count, selectedSlot);

        float iconSc = iconScale(cfg);
        for (int i = 0; i < count; i++) {
            double angle = -Math.PI / 2 + i * (2 * Math.PI / count);
            int sx = cx + (int) (Math.cos(angle) * ring);
            int sy = centerY + (int) (Math.sin(angle) * ring);
            boolean selected = (i == selectedSlot);

            // Chip background + (for selected) accent ring, both themed.
            dev.steampad.client.ui.Draw.fillCircle(ctx, sx, sy, chipR, selected ? pal.chipSel() : pal.chipBg());
            if (selected) {
                dev.steampad.client.ui.Draw.outlineCircle(ctx, sx, sy, chipR + 2, 2, pal.accent());
            } else {
                dev.steampad.client.ui.Draw.outlineCircle(ctx, sx, sy, chipR, 1, pal.edgeSoft());
            }

            RadialSlot slot = slots.get(i);
            if (!slot.isEmpty()) {
                boolean handled = thumbnailRenderer != null
                        && thumbnailRenderer.render(ctx, i, slot, sx, sy, chipR, selected);
                if (handled) {
                    // thumbnail hook drew something in place of the flat icon — nothing more to do.
                } else if (iconSc == 1.0f) {
                    // Icon providers always draw at a fixed 16px footprint — scale around the chip's
                    // own center so a bigger icon grows in place instead of drifting off-center
                    // (feedback: "haz más grandes los iconos de menu radial... pon un slider para
                    // controlar el tamaño"). Fast path at 1.0 to skip the matrix push/pop entirely (not
                    // reachable with the current default of 1.4, but kept for a user who dials the
                    // slider back down).
                    RadialIconResolver.render(ctx, slot, sx - 8, sy - 8);
                } else {
                    ctx.getMatrices().pushMatrix();
                    ctx.getMatrices().translate(sx, sy);
                    ctx.getMatrices().scale(iconSc, iconSc);
                    ctx.getMatrices().translate(-sx, -sy);
                    RadialIconResolver.render(ctx, slot, sx - 8, sy - 8);
                    ctx.getMatrices().popMatrix();
                }
            } else {
                dev.steampad.client.ui.Draw.fillCircle(ctx, sx, sy, 2, 0x66FFFFFF);
            }
            // Label for the selected slot, in the center.
            if (selected && !slot.displayName.isEmpty()) {
                ctx.drawCenteredTextWithShadow(tr, net.minecraft.text.Text.literal(slot.displayName),
                        cx, centerY - 4, pal.accent());
            }
        }

        // E10 — multiple wheels: ghost silhouettes of the PREVIOUS wheel (left, LB) and the NEXT
        // wheel (right, RB), each drawn with its page's REAL slot count so the player can tell the
        // wheels apart at a glance, plus an N-dot page indicator under the center. Ghosts follow the
        // carousel offset (cx) so they slide along with the wheel during a page switch.
        int hintsY = centerY + ring + chipR + 14;
        if (wheelCount > 1) {
            TextRenderer trHints = MinecraftClient.getInstance().textRenderer;
            int prevPage = Math.floorMod(page - 1, wheelCount);
            int nextPage = Math.floorMod(page + 1, wheelCount);
            int ghostDist = ring + chipR + 34;
            drawGhostWheel(ctx, trHints, cx - ghostDist, centerY, cfg.slotCountFor(prevPage), "LB");
            drawGhostWheel(ctx, trHints, cx + ghostDist, centerY, cfg.slotCountFor(nextPage), "RB");

            int dotsW = wheelCount * 10 - 4;
            int dx0 = cx - dotsW / 2 + 3;
            for (int i = 0; i < wheelCount; i++) {
                dev.steampad.client.ui.Draw.fillCircle(ctx, dx0 + i * 10, hintsY, 2,
                        i == page ? pal.accent() : 0x50FFFFFF);
            }
            hintsY += 9;
        }

        // Control glyphs ON the overlay so the wheel explains itself (select/use/edit/switch).
        drawControlHints(ctx, tr, cx, hintsY, wheelCount > 1, pal.hintText());
    }

    /**
     * Ghost silhouette of a neighbouring wheel: a faint ring with that page's REAL chip count laid
     * out exactly like the live wheel, a dim center dot, and the LB/RB switch glyph underneath. Kept
     * to a handful of tiny fills — negligible cost, only drawn while the radial is open.
     */
    private static void drawGhostWheel(DrawContext ctx, TextRenderer tr, int gx, int gy,
                                       int slotCount, String glyphId) {
        int ghostRing = 16, chipRing = 11;
        dev.steampad.client.ui.Draw.outlineCircle(ctx, gx, gy, ghostRing, 1, 0x26FFFFFF);
        int n = Math.max(1, slotCount);
        for (int i = 0; i < n; i++) {
            double a = -Math.PI / 2 + i * (2 * Math.PI / n);
            dev.steampad.client.ui.Draw.fillCircle(ctx,
                    gx + (int) Math.round(Math.cos(a) * chipRing),
                    gy + (int) Math.round(Math.sin(a) * chipRing), 2, 0x40FFFFFF);
        }
        dev.steampad.client.ui.Draw.fillCircle(ctx, gx, gy, 1, 0x30FFFFFF);
        int glyphSz = Math.round(12 * glyphScale());
        int gw = dev.steampad.client.ui.ButtonIcon.width(tr, glyphSz, glyphId);
        dev.steampad.client.ui.ButtonIcon.draw(ctx, tr, gx - gw / 2, gy + ghostRing + 5, glyphSz, glyphId);
    }

    /** Glyph + label hint row centered under the wheel: RS select · A use · LT edit · RB wheel. */
    private static void drawControlHints(DrawContext ctx, TextRenderer tr, int cx, int y, boolean multiWheel,
                                         int hintTextColor) {
        String[][] hints = multiWheel
                ? new String[][]{{"RS_UP", "steampad.hud.radial_select"}, {"A", "steampad.hud.radial_use"},
                                 {"LT", "steampad.hud.radial_edit"}, {"RB", "steampad.hud.radial_page"}}
                : new String[][]{{"RS_UP", "steampad.hud.radial_select"}, {"A", "steampad.hud.radial_use"},
                                 {"LT", "steampad.hud.radial_edit"}};
        int iconSz = Math.round(10 * glyphScale()), gap = 10;
        int total = 0;
        for (String[] h : hints) {
            total += dev.steampad.client.ui.ButtonIcon.width(tr, iconSz, h[0]) + 3
                    + tr.getWidth(net.minecraft.text.Text.translatable(h[1]).getString()) + gap;
        }
        total -= gap;
        int x = cx - total / 2;
        for (String[] h : hints) {
            dev.steampad.client.ui.ButtonIcon.draw(ctx, tr, x, y, iconSz, h[0]);
            x += dev.steampad.client.ui.ButtonIcon.width(tr, iconSz, h[0]) + 3;
            net.minecraft.text.Text label = net.minecraft.text.Text.translatable(h[1]);
            ctx.drawText(tr, label, x, y + 1, hintTextColor, true);
            x += tr.getWidth(label.getString()) + gap;
        }
    }

    /**
     * The E11 "gelatina": a soft blob that glides from its current spot toward the selected chip,
     * drawn as a tapered chain of circles from the wheel center — it visibly stretches while
     * traveling and settles under the selection. No selection → it rests at the center.
     */
    private static void drawJellyBlob(DrawContext ctx, int centerX, int centerY,
                                      int ring, int count, int selectedSlot) {
        double targetX = centerX, targetY = centerY;
        if (selectedSlot >= 0 && selectedSlot < count) {
            double angle = -Math.PI / 2 + selectedSlot * (2 * Math.PI / count);
            targetX = centerX + Math.cos(angle) * ring;
            targetY = centerY + Math.sin(angle) * ring;
        }

        long now = System.nanoTime();
        if (!blobLive) {
            blobLive = true;
            blobX = centerX;
            blobY = centerY;
            blobLastNanos = now;
        }
        double dt = Math.min(0.1, (now - blobLastNanos) / 1.0e9);
        blobLastNanos = now;
        double a = 1.0 - Math.exp(-dt / BLOB_TAU);
        blobX += (targetX - blobX) * a;
        blobY += (targetY - blobY) * a;

        if (selectedSlot < 0) return;   // resting at center with nothing selected — stay invisible

        // PIXEL-ART jelly (BG3-inspired, chunky): a tapered chain of squares quantized to a 2 px
        // grid — it stretches toward the target while traveling and settles as a crisp pixel blob.
        int steps = 6;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = quant2((int) Math.round(centerX + (blobX - centerX) * t));
            int y = quant2((int) Math.round(centerY + (blobY - centerY) * t));
            int half = 1 + (int) (t * 3);          // 2 px at the tail → 8 px square at the head
            int alpha = 0x30 + (int) (t * 0x55);   // fades in toward the head
            ctx.fill(x - half, y - half, x + half, y + half, (alpha << 24) | 0xFFFFFF);
        }
        // Pixel-diamond head: a plus of 2 px squares — reads as a sprite, not a smooth ball.
        int hx = quant2((int) Math.round(blobX)), hy = quant2((int) Math.round(blobY));
        ctx.fill(hx - 2, hy - 6, hx + 2, hy - 4, 0xCCFFFFFF);
        ctx.fill(hx - 2, hy + 4, hx + 2, hy + 6, 0xCCFFFFFF);
        ctx.fill(hx - 6, hy - 2, hx - 4, hy + 2, 0xCCFFFFFF);
        ctx.fill(hx + 4, hy - 2, hx + 6, hy + 2, 0xCCFFFFFF);
    }

    /** Snap a coordinate to the 2 px pixel-art grid. */
    private static int quant2(int v) {
        return (v >> 1) << 1;
    }

    private static RadialConfig getConfig(long handle) {
        if (handle == 0L) return RadialConfig.defaults();
        return ConfigManager.getRadialConfig(handle);
    }
}
