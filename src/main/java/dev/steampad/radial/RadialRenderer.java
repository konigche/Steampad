package dev.steampad.radial;

import dev.steampad.compat.mc.GuiPose;
import dev.steampad.config.ConfigManager;
import dev.steampad.config.PixelTheme;
import dev.steampad.config.RadialConfig;
import dev.steampad.config.RadialSkin;
import dev.steampad.radial.icon.RadialIconResolver;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the radial menu: donut backdrop, round chips, icons, and slot labels.
 *
 * Layout: slot 0 at top (270Â°), going clockwise. Colors come from the per-controller
 * {@link PixelTheme} preset (Vanilla neutral grays by default â€” matches the vanilla pause/
 * inventory panel tones â€” through Emerald, Redstone, etc., same set as the virtual keyboard). A
 * "jelly" blob eases from the center toward the highlighted chip, stretching while it travels
 * (E11) â€” kept theme-neutral (white-based) by design, it's a decorative accent, not a surface.
 */
public final class RadialRenderer {

    private RadialRenderer() {}

    /**
     * Scale for the wheel's control-hint glyphs (ghost-wheel LB/RB switch glyph, the RS/A/LT/RB hint
     * row) â€” the HUD glyph-scale slider (feedback: "agrega un slider para poder cambiar la escala de
     * los glifos en gameplay, debe afectar a todo, inventario, rueda etc."). Deliberately does NOT
     * touch chipRadius/slot icon sizing â€” that's a separate, already-configurable wheel geometry
     * setting, not a "button glyph". Clamped defensively â€” the slider itself limits to [0.5, 2.0].
     */
    private static float glyphScale() {
        float s = ConfigManager.getGlobal().ingameButtonGuideScale;
        return s < 0.5f ? 0.5f : (s > 2.0f ? 2.0f : s);
    }

    /** Default chip radius â€” used only as the fallback when a config predates {@code chipRadius}. */
    private static final int CHIP_R = 18;

    /** Chip radius for this config, clamped to something that still renders sanely. Public so
     *  {@code ItemRadialController} can compute the bloom ring's geometry identically to
     *  {@link #render} â€” see {@link #outerRing} and {@link #bloomChildStepForGeometry}. */
    public static int chipRadius(RadialConfig cfg) {
        float r = cfg.chipRadius > 0 ? cfg.chipRadius : CHIP_R;
        return Math.round(Math.max(10f, Math.min(30f, r)));
    }

    /** The outer ring's radius for a wheel of {@code count} slots â€” same derivation {@link #render}
     *  uses inline, extracted so {@code ItemRadialController} can compute the SAME bloom-ring
     *  geometry {@link #drawBloom} draws with, keeping stick selection in sync (see the class doc on
     *  a real navigation bug this avoids repeating). */
    public static int outerRing(RadialConfig cfg, int count) {
        int chipR = chipRadius(cfg);
        int ring = Math.max(54, (int) cfg.outerRadius);
        // Scale the ring a little with slot count so 12 chips don't overlap.
        if (count > 8) ring += (count - 8) * 4;
        // Bigger-than-default chips also need more ring so neighbours keep their separation.
        if (chipR > CHIP_R) ring += (chipR - CHIP_R) * 2;
        return ring;
    }

    /** Default icon scale â€” used only as the fallback when a config predates {@code iconScale}
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

    // The SEGMENTED skin's "this slice is selected" fill â€” fixed across every PixelTheme, on purpose,
    // same precedent as the E11 jelly blob below ("kept theme-neutral (white-based) by design, it's a
    // decorative accent, not a surface"): a color theme should still tint backgrounds/borders/text,
    // but "this is the highlighted slot" needs to read the same regardless of which theme is active â€”
    // a theme whose own chipSel already leans green (EMERALD) shouldn't look identical to one that
    // doesn't (NETHER). Alpha matches every theme's own chipSel (0xF0).
    private static final int SEGMENT_SELECTED_FILL = 0xF04CAF50;

    /**
     * {@link #palette}, with {@link #SEGMENT_SELECTED_FILL} substituted for {@code chipSel} when
     * {@code skin} is {@link RadialSkin#SEGMENTED} â€” the ONE place that substitution happens, so it
     * reaches both the segmented ring itself AND the Item Radial's circular bloom satellites for free
     * (both draw through this same {@link Palette}, see {@link #drawChip}/{@link #drawBloom}) without
     * either of those methods needing to know skins exist. {@link RadialSkin#CLASSIC} is untouched.
     */
    private static Palette paletteFor(RadialConfig cfg, RadialSkin skin) {
        Palette base = palette(cfg.theme == null ? PixelTheme.VANILLA : cfg.theme);
        if (skin != RadialSkin.SEGMENTED) return base;
        return new Palette(base.chipBg(), SEGMENT_SELECTED_FILL, base.backdrop(), base.edgeSoft(),
                base.accent(), base.hintText());
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

    // Bloom ring (see the Bloom record): eases open/closed with real delta-time, same exponential-
    // decay-toward-target shape as the carousel above. 0 = fully collapsed (not drawn), 1 = fully
    // open. A single shared progress value drives every child's staggered entrance/exit â€” see
    // childSpawnProgress â€” so there's no per-child timer to manage.
    private static float bloomAnim = 0f;
    private static int bloomAnimSource = -1;
    private static long bloomLastNanos = 0L;
    private static final double BLOOM_TAU = 0.09;

    // SEGMENTED skin's center "compass needle" â€” eases toward the selected slice's angle, same
    // exponential-decay shape as the blob/carousel/bloom above, just over an angle (shortest-path,
    // via atan2(sin,cos) so it never spins the "wrong way round" through the wraparound seam).
    private static double segPointerAngle;
    private static boolean segPointerLive = false;
    private static long segPointerLastNanos = 0L;
    private static final double SEG_POINTER_TAU = 0.09;

    /** Reset the jelly blob (CLASSIC) / direction pointer (SEGMENTED) to a neutral state â€” call when
     *  the wheel opens, so a fresh open never eases in from wherever the LAST wheel session left off. */
    public static void resetBlob() {
        blobLive = false;
        segPointerLive = false;
    }

    /** Kick the carousel animation when the wheel page changes (E10). */
    public static void onPageSwitched(int dir) {
        carouselOffset = dir >= 0 ? 110 : -110;
        carouselLastNanos = 0L;
        resetBlob();
    }

    /** Per-slot hook invoked from INSIDE the existing chip-drawing loop, at the slot's own already-
     *  computed screen position/radius â€” lets a caller (the emote wheel) substitute a live 3D
     *  thumbnail for the flat icon on a per-slot basis, using the SAME geometry the icon itself would
     *  have used (no separate/divergent position math, no risk of drifting out of sync with the
     *  jelly-blob/carousel animation state this class already owns). Callers that don't need this
     *  (the regular radial menu) simply never pass one â€” see the overload below. */
    @FunctionalInterface
    public interface SlotThumbnailRenderer {
        /** @return true if this hook drew something for the slot (skips the normal icon draw),
         *          false to let the chip's normal icon render as usual. */
        boolean render(GuiGraphics ctx, int slotIndex, RadialSlot slot, int sx, int sy, int chipRadius, boolean selected);
    }

    /**
     * Optional satellite ring of "child" slots blooming from ONE outer slot (feedback: "el cÃ­rculo
     * de selecciÃ³n... se vaya rellenando... se abra... y se ponga alrededor de este [rueda],
     * permaneciendo la rueda"). Generic on purpose â€” any wheel controller can request one later, not
     * just the Item Radial's category wheel that introduced it; {@code null} (every existing caller)
     * means no bloom.
     *
     * @param sourceIndex   which outer slot the ring blooms from. That chip is drawn exactly like a
     *                      normal "selected" chip (bigger, accent border â€” pass it as the outer
     *                      {@code selectedSlot} too) plus a dwell-fill progress arc; every OTHER
     *                      outer chip dims.
     * @param fillProgress  0..1, how full the dwell-fill arc around the source chip is. Purely a
     *                      readout â€” this renderer doesn't decide what reaching 1.0 means, the
     *                      caller does (e.g. auto-opening the bloom).
     * @param children      the child slots to lay out in the inner ring. Empty/{@code null} keeps
     *                      the ring closed (still eases out smoothly if it was previously open).
     * @param selectedChild which child is focused, or -1.
     */
    public record Bloom(int sourceIndex, float fillProgress, List<RadialSlot> children, int selectedChild) {}

    /**
     * @param handle the controller whose {@link RadialConfig} styles this render â€” callers with a
     *               specific controller in mind (the live overlay, the editor preview) must pass it
     *               explicitly rather than relying on the globally-active controller, which can
     *               diverge for a tick (e.g. mid-editing another pad, or the active controller
     *               disconnecting while the wheel is still closing).
     * @param wheelCount how many wheels the CALLER's own wheel system has configured, and
     * @param page       which one is showing â€” passed explicitly rather than read from
     *                   {@code RadialMenuController} directly so this renderer stays reusable by any
     *                   independent wheel system (the dedicated emote wheel has its own
     *                   {@code EmoteWheelController} with its own count/page, see FASE 63 decoupling).
     */
    public static void render(GuiGraphics ctx, int centerX, int centerY,
                              List<RadialSlot> slots, int selectedSlot, long handle,
                              int wheelCount, int page) {
        render(ctx, centerX, centerY, slots, selectedSlot, handle, wheelCount, page, null, true, true, null);
    }

    /** Same as the 8-arg overload, with an optional per-slot thumbnail hook (see
     *  {@link SlotThumbnailRenderer}) â€” {@code null} reproduces the 8-arg overload exactly. */
    public static void render(GuiGraphics ctx, int centerX, int centerY,
                              List<RadialSlot> slots, int selectedSlot, long handle,
                              int wheelCount, int page, SlotThumbnailRenderer thumbnailRenderer) {
        render(ctx, centerX, centerY, slots, selectedSlot, handle, wheelCount, page, thumbnailRenderer, true, true, null);
    }

    /** Same as the 9-arg overload, with an explicit {@code allowBackgroundBlur} (see the full
     *  overload's doc) â€” {@code editable} defaults to {@code true} (MenÃº Radial/Emote semantics). */
    public static void render(GuiGraphics ctx, int centerX, int centerY,
                              List<RadialSlot> slots, int selectedSlot, long handle,
                              int wheelCount, int page, SlotThumbnailRenderer thumbnailRenderer,
                              boolean allowBackgroundBlur) {
        render(ctx, centerX, centerY, slots, selectedSlot, handle, wheelCount, page, thumbnailRenderer,
                allowBackgroundBlur, true, null);
    }

    /** Same as the 10-arg overload, with an explicit {@code editable} â€” {@code bloom} defaults to
     *  {@code null} (no satellite ring). */
    public static void render(GuiGraphics ctx, int centerX, int centerY,
                              List<RadialSlot> slots, int selectedSlot, long handle,
                              int wheelCount, int page, SlotThumbnailRenderer thumbnailRenderer,
                              boolean allowBackgroundBlur, boolean editable) {
        render(ctx, centerX, centerY, slots, selectedSlot, handle, wheelCount, page, thumbnailRenderer,
                allowBackgroundBlur, editable, null);
    }

    /**
     * Full overload.
     * @param allowBackgroundBlur must be {@code false} when the caller is a {@code Screen} preview
     *        (e.g. {@code RadialEditorScreen}/{@code RadialStyleScreen}): MC's own render pipeline
     *        already blurs every SteamPad screen's background before {@code render()} runs (see
     *        {@code SteamPadBaseScreen}'s note on this), so a second {@code applyBlur()} call here
     *        crashes with "Can only blur once per frame". The live gameplay overlays (HUD, not a
     *        Screen) never get that native blur, so they pass {@code true} to get it from here.
     * @param editable whether the "LT edit" control hint is shown â€” {@code false} for wheels whose
     *        slots are computed dynamically at runtime (the Item Radial's LB/RB hotbar/inventory
     *        wheels) and have no per-slot editor screen to jump into (feedback: "Elimina Editar
     *        casilla en las ruedas de inventario y hotbar... estos no deberÃ­an de estar"); {@code
     *        true} for user-configured wheels (MenÃº Radial, Emotes) where LT really does open an
     *        editor for the focused slot.
     * @param bloom optional satellite ring â€” see {@link Bloom}. {@code null} for every wheel that
     *        doesn't have this concept (MenÃº Radial, Emotes, as of this writing).
     */
    public static void render(GuiGraphics ctx, int centerX, int centerY,
                              List<RadialSlot> slots, int selectedSlot, long handle,
                              int wheelCount, int page, SlotThumbnailRenderer thumbnailRenderer,
                              boolean allowBackgroundBlur, boolean editable, Bloom bloom) {
        RadialConfig cfg = getConfig(handle);
        RadialSkin skin = cfg.skin == null ? RadialSkin.CLASSIC : cfg.skin;
        Palette pal = paletteFor(cfg, skin);
        Font tr = Minecraft.getInstance().font;

        // Background blur (feedback: "agrega un blur... super optimizado, que se pueda desactivar") â€”
        // the SAME post-process pass vanilla's own pause/inventory-like screens use
        // (Screen#applyBlur -> DrawContext#applyBlur -> GuiRenderState#applyBlur, confirmed via
        // javap): a single queued GPU command flushed once per frame, not a custom shader â€” as
        // optimized as it gets. Applies to all 3 wheels for free since they share this renderer.
        // Real blur on BOTH version families now (see RenderCompat.blurBackground — the older path
        // runs vanilla's own pre-rewrite blur immediately instead of the flat dim it used to fall back
        // to). It still reports false in one case: the player set vanilla's "Menu background
        // blurriness" to 0, where vanilla's blur no-ops too — then we darken, so the wheel always has
        // SOME separation from the world. Alpha chosen to read like the blurred pass does.
        if (allowBackgroundBlur && cfg.backgroundBlur
                && !dev.steampad.compat.mc.RenderCompat.blurBackground(ctx)) {
            ctx.fill(0, 0, ctx.guiWidth(), ctx.guiHeight(), 0x66000000);
        }

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
        int ring = outerRing(cfg, count);

        if (skin == RadialSkin.SEGMENTED) {
            // No donut backdrop / jelly blob for this skin â€” see renderSegmentedHub's doc.
            renderSegmentedHub(ctx, pal, cfg, cx, centerY, count, selectedSlot, ring, chipR);
        } else {
            // Soft donut backdrop behind the chips, themed per the controller's PixelTheme.
            if (cfg.showBackground) {
                dev.steampad.client.ui.Draw.fillRing(ctx, cx, centerY, ring + chipR + 8, ring - chipR - 10, pal.backdrop());
                dev.steampad.client.ui.Draw.outlineCircle(ctx, cx, centerY, ring + chipR + 8, 1, pal.edgeSoft());
            }

            // E11 â€” jelly blob easing toward the selected chip (drawn under the chips).
            drawJellyBlob(ctx, cx, centerY, ring, count, selectedSlot);
        }

        // Bloom ease-toward-target (see the field doc). A different SOURCE than last frame snaps
        // collapsed first instead of crossfading between two different source positions â€” a category
        // switch mid-animation should never look like items fly across the wheel from the old spot.
        boolean wantBloomOpen = bloom != null && bloom.children() != null && !bloom.children().isEmpty();
        if (wantBloomOpen && bloom.sourceIndex() != bloomAnimSource) {
            bloomAnim = 0f;
        }
        if (wantBloomOpen) bloomAnimSource = bloom.sourceIndex();
        double bloomDt = bloomLastNanos == 0L ? 0.0 : Math.min(0.1, (now - bloomLastNanos) / 1.0e9);
        bloomLastNanos = now;
        float bloomTarget = wantBloomOpen ? 1f : 0f;
        if (bloomDt > 0 && bloomAnim != bloomTarget) {
            float rate = (float) (1.0 - Math.exp(-bloomDt / BLOOM_TAU));
            bloomAnim += (bloomTarget - bloomAnim) * rate;
            if (Math.abs(bloomAnim - bloomTarget) < 0.003f) bloomAnim = bloomTarget;
        }

        float iconSc = iconScale(cfg);
        // SEGMENTED-only geometry, hoisted out of the per-slot loop below (doesn't depend on i).
        int segOuterR = ring + chipR;
        int segInnerR = segInnerRadius(cfg, ring, chipR);
        double segSlice = 2 * Math.PI / count;
        double segGapRad = Math.min(segSlice * 0.4, cfg.segmentGap > 0 ? cfg.segmentGap / segOuterR : 0.05);
        double segHalfAngle = Math.max(0.02, segSlice / 2.0 - segGapRad / 2.0);

        for (int i = 0; i < count; i++) {
            double angle = -Math.PI / 2 + i * (2 * Math.PI / count);
            boolean selected = (i == selectedSlot);
            // Every OTHER outer chip dims while a bloom is open, drawing attention to the source and
            // its satellite ring (feedback: "las que no estÃ¡n en foco se atenÃºan"). Icons themselves
            // aren't dimmed (no cheap alpha hook into item rendering) â€” the chip surface carries it.
            float alphaMul = (bloom != null && i != bloom.sourceIndex()) ? 0.35f : 1f;
            // The dwelling category's OWN selection ring fills as the dwell-timer progresses instead
            // of a second ring growing alongside it â€” see the drawChip overload's doc.
            float selectionSweep = (bloom != null && i == bloom.sourceIndex()) ? bloom.fillProgress() : 1f;
            if (skin == RadialSkin.SEGMENTED) {
                drawSegmentSlice(ctx, tr, pal, cx, centerY, segOuterR, segInnerR, segHalfAngle, angle,
                        slots.get(i), selected, thumbnailRenderer, i, iconSc, alphaMul, selectionSweep);
            } else {
                int sx = cx + (int) (Math.cos(angle) * ring);
                int sy = centerY + (int) (Math.sin(angle) * ring);
                drawChip(ctx, pal, sx, sy, chipR, slots.get(i), selected, thumbnailRenderer, i, iconSc,
                        alphaMul, selectionSweep);
            }
        }

        // Satellite ring â€” items of the source category, OUTSIDE the outer wheel (see drawBloom).
        // Drawn after the outer ring so it layers on top.
        if (bloom != null) {
            double sourceAngle = -Math.PI / 2 + bloom.sourceIndex() * (2 * Math.PI / count);
            drawBloom(ctx, pal, cx, centerY, ring, chipR, sourceAngle, bloom);
        }

        // Label in the center: the focused CHILD's name once the bloom ring has something to show
        // (feedback: keep "focused item's name shows" once you're actually choosing among items, not
        // stuck on the category's own name) â€” otherwise the normal selected OUTER slot's name.
        if (bloomAnim > 0.5f && bloom != null && bloom.children() != null
                && bloom.selectedChild() >= 0 && bloom.selectedChild() < bloom.children().size()) {
            RadialSlot child = bloom.children().get(bloom.selectedChild());
            if (!child.displayName.isEmpty()) {
                ctx.drawCenteredString(tr, net.minecraft.network.chat.Component.literal(child.displayName),
                        cx, centerY - 4, pal.accent());
            }
        } else if (selectedSlot >= 0 && selectedSlot < count) {
            RadialSlot slot = slots.get(selectedSlot);
            if (!slot.displayName.isEmpty()) {
                ctx.drawCenteredString(tr, net.minecraft.network.chat.Component.literal(slot.displayName),
                        cx, centerY - 4, pal.accent());
            }
        }

        // E10 â€” multiple wheels: ghost silhouettes of the PREVIOUS wheel (left, LB) and the NEXT
        // wheel (right, RB), each drawn with its page's REAL slot count so the player can tell the
        // wheels apart at a glance, plus an N-dot page indicator under the center. Ghosts follow the
        // carousel offset (cx) so they slide along with the wheel during a page switch.
        int hintsY = centerY + ring + chipR + 14;
        if (wheelCount > 1) {
            Font trHints = Minecraft.getInstance().font;
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
        drawControlHints(ctx, tr, cx, hintsY, wheelCount > 1, pal.hintText(), editable);
    }

    /** One chip: background + accent/outline ring, then its icon (or empty-slot dot) â€” shared by the
     *  outer ring and the bloom's inner ring so both stay visually identical. {@code alphaMul} dims
     *  the chip surface (not the icon â€” no cheap alpha hook into item rendering) for de-emphasized
     *  outer chips while a bloom is open. */
    private static void drawChip(GuiGraphics ctx, Palette pal, int sx, int sy, int chipR, RadialSlot slot,
                                 boolean selected, SlotThumbnailRenderer thumbnailRenderer, int index,
                                 float iconSc, float alphaMul) {
        drawChip(ctx, pal, sx, sy, chipR, slot, selected, thumbnailRenderer, index, iconSc, alphaMul, 1f);
    }

    /**
     * Same as the other overload, with an explicit {@code selectionSweep} â€” ONE ring instead of two
     * (feedback round 2 on the bloom: "el cÃ­rculo de selecciÃ³n son dos, el que hace foco y el que
     * hace timing, el mismo debe servir para ambos"). {@code 1f} draws the normal full selection
     * ring; anything below that draws it as a partial ARC instead (same radius/thickness/color), so
     * a category dwelling toward its bloom just fills its OWN existing selection ring rather than
     * growing a second one alongside it.
     */
    private static void drawChip(GuiGraphics ctx, Palette pal, int sx, int sy, int chipR, RadialSlot slot,
                                 boolean selected, SlotThumbnailRenderer thumbnailRenderer, int index,
                                 float iconSc, float alphaMul, float selectionSweep) {
        dev.steampad.client.ui.Draw.fillCircle(ctx, sx, sy, chipR,
                scaleAlpha(selected ? pal.chipSel() : pal.chipBg(), alphaMul));
        if (selected) {
            int ringColor = scaleAlpha(pal.accent(), alphaMul);
            if (selectionSweep < 0.999f) {
                dev.steampad.client.ui.Draw.outlineArc(ctx, sx, sy, chipR + 2, 2,
                        -Math.PI / 2, Math.max(0, selectionSweep) * 2 * Math.PI, ringColor);
            } else {
                dev.steampad.client.ui.Draw.outlineCircle(ctx, sx, sy, chipR + 2, 2, ringColor);
            }
        } else {
            dev.steampad.client.ui.Draw.outlineCircle(ctx, sx, sy, chipR, 1, scaleAlpha(pal.edgeSoft(), alphaMul));
        }

        if (!slot.isEmpty()) {
            boolean handled = thumbnailRenderer != null
                    && thumbnailRenderer.render(ctx, index, slot, sx, sy, chipR, selected);
            if (handled) {
                // thumbnail hook drew something in place of the flat icon â€” nothing more to do.
            } else if (iconSc == 1.0f) {
                // Icon providers always draw at a fixed 16px footprint â€” scale around the chip's own
                // center so a bigger icon grows in place instead of drifting off-center (feedback:
                // "haz mÃ¡s grandes los iconos de menu radial... pon un slider para controlar el
                // tamaÃ±o"). Fast path at 1.0 to skip the matrix push/pop entirely.
                RadialIconResolver.render(ctx, slot, sx - 8, sy - 8);
            } else {
                GuiPose.push(ctx);
                GuiPose.translate(ctx, sx, sy);
                GuiPose.scale(ctx, iconSc, iconSc);
                GuiPose.translate(ctx, -sx, -sy);
                RadialIconResolver.render(ctx, slot, sx - 8, sy - 8);
                GuiPose.pop(ctx);
            }
        } else {
            dev.steampad.client.ui.Draw.fillCircle(ctx, sx, sy, 2, scaleAlpha(0x66FFFFFF, alphaMul));
        }
    }

    /** Multiplies just the alpha channel of an 0xAARRGGBB color, clamped. {@code mul >= 1} is a
     *  no-op (the common case â€” most chips never dim). */
    private static int scaleAlpha(int argb, float mul) {
        if (mul >= 1f) return argb;
        int a = (argb >>> 24) & 0xFF;
        int newA = Math.max(0, Math.min(255, Math.round(a * mul)));
        return (newA << 24) | (argb & 0x00FFFFFF);
    }

    // ============================================================================================
    // SEGMENTED skin â€” trapezoid slices forming a full ring around a center hub. Everything else in
    // this class (bloom, ghost wheels, page dots, control hints, center label) is shared as-is: this
    // section only replaces HOW the outer ring of slots is drawn, via the skin branch in render().
    // ============================================================================================

    /** Icon footprint multiplier for this skin â€” its slices have far more room than CLASSIC's small
     *  round chips, and the reference mock's icons read noticeably bigger. Applied on top of the
     *  user's own icon-size slider, same as every other size in this skin reuses the existing config. */
    private static final float SEGMENT_ICON_MULT = 1.7f;

    /** Halo border thickness (px) around a selected/dwelling slice. */
    private static final int SEG_BORDER_T = 2;

    /**
     * Center-hub radius â€” derived from {@code ring}/{@code chipR} (the SAME basis {@link #outerRing}/
     * {@link #chipRadius} already give CLASSIC) so the hub scales sensibly with the existing "Wheel
     * radius"/"Slot size" sliders, then clamped against {@code cfg.innerRadius} so a slice band always
     * has room for an icon no matter how that field is tuned. ONE formula, called everywhere the hub's
     * radius matters (the hub circle, the pointer, every slice), so they can never disagree about
     * where the ring actually starts â€” the exact class of bug {@link #bloomChildStepForGeometry}'s doc
     * warns about, just for this skin's own geometry instead of the bloom ring's.
     */
    private static int segInnerRadius(RadialConfig cfg, int ring, int chipR) {
        int outerR = ring + chipR;
        int minBand = Math.max(18, chipR * 2);
        float configured = cfg.innerRadius > 0 ? cfg.innerRadius : 30f;
        return (int) Math.max(outerR * 0.35, Math.min(outerR - minBand, configured));
    }

    /**
     * The hub: a filled circle â€” no donut backdrop behind the slices, unlike CLASSIC (the reference
     * mock shows the game world straight through the gaps between slices, so there's nothing to draw
     * there) â€” plus the direction pointer, this skin's replacement for the jelly blob (there's no
     * single "chip position" to glide to anymore, just a slice direction).
     */
    private static void renderSegmentedHub(GuiGraphics ctx, Palette pal, RadialConfig cfg, int cx, int centerY,
                                           int count, int selectedSlot, int ring, int chipR) {
        int innerR = segInnerRadius(cfg, ring, chipR);
        dev.steampad.client.ui.Draw.fillCircle(ctx, cx, centerY, innerR, pal.backdrop());
        dev.steampad.client.ui.Draw.outlineCircle(ctx, cx, centerY, innerR, 1, pal.edgeSoft());
        drawSegmentPointer(ctx, pal, cx, centerY, count, selectedSlot, innerR);
    }

    /** Short arrow eased toward the selected slice's angle (shortest-path, via atan2(sin,cos) so it
     *  never spins the long way round through the wraparound seam) â€” poking out from the hub's edge
     *  toward whichever slice is currently highlighted, like a compass needle. */
    private static void drawSegmentPointer(GuiGraphics ctx, Palette pal, int cx, int centerY, int count,
                                           int selectedSlot, int innerR) {
        if (selectedSlot < 0 || selectedSlot >= count) { segPointerLive = false; return; }
        double target = -Math.PI / 2 + selectedSlot * (2 * Math.PI / count);
        long now = System.nanoTime();
        if (!segPointerLive) {
            segPointerAngle = target;
            segPointerLive = true;
            segPointerLastNanos = now;
        } else {
            double dt = segPointerLastNanos == 0L ? 0.0 : Math.min(0.1, (now - segPointerLastNanos) / 1.0e9);
            segPointerLastNanos = now;
            double delta = Math.atan2(Math.sin(target - segPointerAngle), Math.cos(target - segPointerAngle));
            segPointerAngle += delta * (1.0 - Math.exp(-dt / SEG_POINTER_TAU));
        }
        int half = Math.max(3, innerR / 7);
        int tipR = innerR + Math.max(8, innerR / 4);
        dev.steampad.client.ui.Draw.fillPointer(ctx, cx, centerY, segPointerAngle, innerR - 3, tipR, half,
                pal.accent());
    }

    /**
     * One trapezoid slice: background/selection fill, then its icon (or empty-slot dot) â€” the
     * SEGMENTED counterpart of {@link #drawChip}, called from the same per-slot loop in
     * {@link #render}.
     */
    private static void drawSegmentSlice(GuiGraphics ctx, Font tr, Palette pal, int cx, int centerY,
                                         int outerR, int innerR, double halfAngle, double angle, RadialSlot slot,
                                         boolean selected, SlotThumbnailRenderer thumbnailRenderer, int index,
                                         float iconSc, float alphaMul, float selectionSweep) {
        if (selected) {
            // Halo UNDERNEATH, slightly bigger radially AND angularly, so a uniform border peeks out
            // on all 4 sides of the (smaller, on-top) fill sector below â€” the same "bigger shape
            // behind, smaller shape in front" trick drawChip's own selection ring uses, generalized to
            // a 4-sided slice. A <1.0 sweep is the dwell-fill "timing" readout (see the Bloom record):
            // instead of a small circular arc next to a chip (CLASSIC), it grows the halo's OWN
            // angular coverage starting from the slice's leading edge â€” the border visibly draws
            // itself around the whole slice as the dwell timer fills, rather than a separate indicator
            // floating next to it ("rodea toda la figura").
            double haloHalf = halfAngle + Math.max(0.01, SEG_BORDER_T * 1.4 / outerR);
            double sweep = Math.max(0f, Math.min(1f, selectionSweep)) * haloHalf * 2;
            dev.steampad.client.ui.Draw.fillDonutSlice(ctx, cx, centerY, outerR + SEG_BORDER_T,
                    Math.max(1, innerR - SEG_BORDER_T), angle - haloHalf, sweep, scaleAlpha(pal.accent(), alphaMul));
            dev.steampad.client.ui.Draw.fillDonutSlice(ctx, cx, centerY, outerR, innerR,
                    angle - halfAngle, halfAngle * 2, scaleAlpha(pal.chipSel(), alphaMul));
        } else {
            // Faint 1px edge, same "bigger shape behind" trick at a much smaller offset â€” parity with
            // drawChip, which outlines every unselected chip too, not just the selected one.
            double edgeHalf = halfAngle + Math.max(0.005, 0.7 / outerR);
            dev.steampad.client.ui.Draw.fillDonutSlice(ctx, cx, centerY, outerR + 1, Math.max(1, innerR - 1),
                    angle - edgeHalf, edgeHalf * 2, scaleAlpha(pal.edgeSoft(), alphaMul));
            dev.steampad.client.ui.Draw.fillDonutSlice(ctx, cx, centerY, outerR, innerR,
                    angle - halfAngle, halfAngle * 2, scaleAlpha(pal.chipBg(), alphaMul));
        }

        int midR = (innerR + outerR) / 2;
        int sx = cx + (int) Math.round(Math.cos(angle) * midR);
        int sy = centerY + (int) Math.round(Math.sin(angle) * midR);

        if (slot.isEmpty()) {
            dev.steampad.client.ui.Draw.fillCircle(ctx, sx, sy, 2, scaleAlpha(0x66FFFFFF, alphaMul));
            return;
        }

        float segIconSc = iconSc * SEGMENT_ICON_MULT;
        int iconHalf = Math.max(10, (outerR - innerR) / 2 - 2);
        boolean handled = thumbnailRenderer != null
                && thumbnailRenderer.render(ctx, index, slot, sx, sy, iconHalf, selected);
        if (handled) return;

        if (segIconSc == 1.0f) {
            RadialIconResolver.render(ctx, slot, sx - 8, sy - 8);
        } else {
            GuiPose.push(ctx);
            GuiPose.translate(ctx, sx, sy);
            GuiPose.scale(ctx, segIconSc, segIconSc);
            GuiPose.translate(ctx, -sx, -sy);
            RadialIconResolver.render(ctx, slot, sx - 8, sy - 8);
            GuiPose.pop(ctx);
        }
        if (slot.count > 1) drawCountBadge(ctx, tr, sx, sy, segIconSc, slot.count);
    }

    /** Vanilla-style stack count badge (white digits, dark shadow) at the icon's bottom-right â€” same
     *  convention as a real inventory slot's stack size text. Icons render at a fixed 16px footprint
     *  before scaling (see {@link #drawChip}'s own note on this), so the badge anchors off that scaled
     *  footprint to stay glued to the icon's corner as the icon-size slider moves. */
    private static void drawCountBadge(GuiGraphics ctx, Font tr, int sx, int sy, float iconSc, int count) {
        String text = String.valueOf(count);
        int footprint = Math.round(16 * iconSc);
        int tw = tr.width(text);
        ctx.drawString(tr, net.minecraft.network.chat.Component.literal(text),
                sx + footprint / 2 - tw + 1, sy + footprint / 2 - 9, 0xFFFFFFFF, true);
    }

    /**
     * The Item Radial's satellite "bloom" ring â€” a category's items arranged OUTSIDE the outer wheel
     * (round-3 correction after a screenshot: "es por fuera totalmente del cÃ­rculo... sigue estando
     * dentro" â€” the ring must clear the whole backdrop entirely, not just sit closer to its rim
     * while still nested inside it). Every child uses the SAME evenly-spaced angular math the outer
     * ring already uses (see the fan-vs-full-circle note below), just at a LARGER radius, reusing
     * {@link #drawChip}.
     *
     * <p>Each child animates in from the SOURCE chip's own position out to its final ring slot
     * (feedback: "los objetos deben tener una animaciÃ³n como que nacen" â€” they visibly burst out of
     * the category you opened), with a short stagger across the ring so they cascade in rather than
     * popping together â€” driven entirely by the single shared {@link #bloomAnim} progress, so
     * collapsing (B) automatically un-staggers them in reverse for free.
     *
     * <p><b>Fan out from the source, not a full circle</b> (round-2 correction after a screenshot:
     * "no deben estar uniformemente distribuidos... a partir de la madre de donde salen se empiezan
     * a distribuir, no deben rodear todo el cÃ­rculo a menos que tengan suficientes elementos"). The
     * angular spacing between children is fixed to a comfortable, non-overlapping value; they're
     * placed CENTERED on the source category's own angle and only wrap the FULL circle once there
     * are enough of them that comfortable spacing needs the whole ring â€” never before. Chip radius
     * only shrinks below the comfortable default in that many-items case, just enough to keep every
     * chip from overlapping ("que no se van 'montando'"); the focused child stays a touch bigger and
     * sits a little further out than its packed siblings ("un poco mÃ¡s grande y un poco mÃ¡s libre").
     */
    private static void drawBloom(GuiGraphics ctx, Palette pal, int cx, int centerY,
                                  int outerRing, int outerChipR, double sourceAngle, Bloom bloom) {
        List<RadialSlot> children = bloom.children();
        if (children == null || children.isEmpty() || bloomAnim <= 0.005f) return;
        int count = children.size();

        int desiredChipR = Math.max(9, outerChipR - 3);
        // OUTSIDE the outer wheel entirely (round-2 correction after a screenshot: "es por fuera
        // totalmente del cÃ­rculo... sigue estando dentro" â€” the previous fix only moved it closer to
        // the rim, still nested INSIDE the backdrop). The satellite ring's near edge sits just past
        // the outer ring's own chip edge, with a small gap so nothing touches.
        int satelliteRing = outerRing + outerChipR + desiredChipR + 12;

        double gapRad = 6.0 / satelliteRing;   // ~6px breathing room between adjacent chip edges
        double desiredStep = (2.0 * desiredChipR) / satelliteRing + gapRad;
        // Same derivation as bloomChildStepForGeometry (called here so this and
        // ItemRadialController's stick-selection math can never silently diverge again â€” see that
        // method's doc for the real navigation bug this guards against).
        double step = bloomChildStepForGeometry(outerRing, outerChipR, count);
        // Spacing only got capped when there are enough items to need the whole ring â€” shrink the
        // chip to match exactly that spacing so nothing overlaps even then.
        int packedChipR = step < desiredStep
                ? Math.max(7, (int) ((step - gapRad) * satelliteRing / 2.0))
                : desiredChipR;

        int sourceX = cx + (int) Math.round(Math.cos(sourceAngle) * outerRing);
        int sourceY = centerY + (int) Math.round(Math.sin(sourceAngle) * outerRing);

        for (int i = 0; i < count; i++) {
            float childT = childSpawnProgress(bloomAnim, i, count);
            if (childT <= 0.001f) continue;
            boolean focused = (i == bloom.selectedChild());

            double angle = bloomChildAngle(sourceAngle, i, count, step);
            int finalR = focused ? Math.min(outerChipR, packedChipR + 4) : packedChipR;
            double finalRadius = satelliteRing + (focused ? 6 : 0);
            int finalX = cx + (int) Math.round(Math.cos(angle) * finalRadius);
            int finalY = centerY + (int) Math.round(Math.sin(angle) * finalRadius);

            double posT = easeOutCubic(childT);
            int sx = (int) Math.round(sourceX + (finalX - sourceX) * posT);
            int sy = (int) Math.round(sourceY + (finalY - sourceY) * posT);
            // A touch of overshoot on the SCALE only (never on position â€” overshooting past a slot
            // could briefly overlap a neighbour that already settled) for the "pop" feel.
            int r = Math.max(2, (int) Math.round(finalR * Math.max(0.0, easeOutBack(childT))));

            drawChip(ctx, pal, sx, sy, r, children.get(i), focused, null, i, 1.0f, 1f);
        }
    }

    /**
     * Desired angular spacing (radians) between adjacent bloom children for a ring of {@code count},
     * derived from the SAME live geometry {@link #drawBloom} sizes its chips with (
     * {@code outerRing}/{@code outerChipR}, in turn from the controller's own {@link RadialConfig}),
     * capped so the whole ring never exceeds a full circle. Public and side-effect-free specifically
     * so {@code ItemRadialController} can compute the EXACT same values {@link #drawBloom} draws
     * with â€” earlier this used a fixed constant instead (simpler for the controller to call, no
     * config lookup needed) but that changed the actual spacing/sizing versus what geometry-derived
     * math produced, which the user had already confirmed looked right ("la distribuciÃ³n de la
     * versiÃ³n anterior estaba perfecta... vuelve a como estaba"). Geometry-derived it is â€” the
     * controller pays for it with one extra {@code RadialConfig} lookup, not a big deal.
     */
    public static double bloomChildStepForGeometry(int outerRing, int outerChipR, int count) {
        int desiredChipR = Math.max(9, outerChipR - 3);
        int satelliteRing = outerRing + outerChipR + desiredChipR + 12;
        double gapRad = 6.0 / satelliteRing;
        double desiredStep = (2.0 * desiredChipR) / satelliteRing + gapRad;
        double maxStep = 2 * Math.PI / Math.max(1, count);
        return Math.min(desiredStep, maxStep);
    }

    /** The angle (same convention as the chip-layout loop: 0 = 3 o'clock, {@code -PI/2} = 12
     *  o'clock, increasing = clockwise) of bloom child {@code index} of {@code count}, fanned
     *  CENTERED on {@code sourceAngle} at the given {@code step} (see
     *  {@link #bloomChildStepForGeometry}) â€” index {@code (count-1)/2} sits exactly at the source;
     *  when {@code step} is capped to a full circle this is mathematically a full circle too, just
     *  rotated so the source sits in the middle of the sequence instead of at 12 o'clock. THE SAME
     *  formula (called with the SAME {@code step}) both {@link #drawBloom} and {@code
     *  ItemRadialController#childSlotForAnalog} use â€” they must never diverge (see the "apunto
     *  arriba, selecciona arriba-izquierda" bug this pairing exists to prevent). */
    public static double bloomChildAngle(double sourceAngle, int index, int count, double step) {
        if (count <= 1) return sourceAngle;
        return sourceAngle + (index - (count - 1) / 2.0) * step;
    }

    /** 0..1 entrance progress for child {@code index} of {@code count}, given the ring's overall
     *  {@code ringT} (0..1, {@link #bloomAnim}). A short clockwise stagger so children cascade in
     *  instead of popping together, without a separate timer per child â€” the SAME formula run with a
     *  falling {@code ringT} un-staggers them in reverse for the collapse, for free. */
    static float childSpawnProgress(float ringT, int index, int count) {
        if (count <= 1) return clamp01(ringT);
        float span = Math.min(0.6f, count * 0.05f);   // total stagger budget across the whole ring
        float delay = span * index / (count - 1);
        float local = (ringT - delay) / Math.max(0.05f, 1f - delay);
        return clamp01(local);
    }

    private static float clamp01(float v) { return v < 0f ? 0f : Math.min(v, 1f); }

    private static double easeOutCubic(double t) {
        double p = 1 - t;
        return 1 - p * p * p;
    }

    /** Standard "back" overshoot ease (easings.net) â€” briefly exceeds 1.0 before settling, the "pop"
     *  feel for a spawning chip's scale. */
    private static double easeOutBack(double t) {
        double c1 = 1.70158, c3 = c1 + 1;
        double t1 = t - 1;
        return 1 + c3 * t1 * t1 * t1 + c1 * t1 * t1;
    }

    /**
     * Ghost silhouette of a neighbouring wheel: a faint ring with that page's REAL chip count laid
     * out exactly like the live wheel, a dim center dot, and the LB/RB switch glyph underneath. Kept
     * to a handful of tiny fills â€” negligible cost, only drawn while the radial is open.
     */
    private static void drawGhostWheel(GuiGraphics ctx, Font tr, int gx, int gy,
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

    /** Glyph + label hint row centered under the wheel: RS select Â· A use Â· [LT edit] Â· [RB wheel]
     *  â€” the edit hint is omitted entirely when {@code editable} is false (dynamic wheels with no
     *  per-slot editor, e.g. the Item Radial's LB/RB wheels). */
    private static void drawControlHints(GuiGraphics ctx, Font tr, int cx, int y, boolean multiWheel,
                                         int hintTextColor, boolean editable) {
        List<String[]> hintList = new java.util.ArrayList<>();
        hintList.add(new String[]{"RS_UP", "steampad.hud.radial_select"});
        hintList.add(new String[]{"A", "steampad.hud.radial_use"});
        if (editable) hintList.add(new String[]{"LT", "steampad.hud.radial_edit"});
        if (multiWheel) hintList.add(new String[]{"RB", "steampad.hud.radial_page"});
        String[][] hints = hintList.toArray(new String[0][]);
        int iconSz = Math.round(10 * glyphScale()), gap = 10;
        int total = 0;
        for (String[] h : hints) {
            total += dev.steampad.client.ui.ButtonIcon.width(tr, iconSz, h[0]) + 3
                    + tr.width(net.minecraft.network.chat.Component.translatable(h[1]).getString()) + gap;
        }
        total -= gap;
        int x = cx - total / 2;
        for (String[] h : hints) {
            dev.steampad.client.ui.ButtonIcon.draw(ctx, tr, x, y, iconSz, h[0]);
            x += dev.steampad.client.ui.ButtonIcon.width(tr, iconSz, h[0]) + 3;
            net.minecraft.network.chat.Component label = net.minecraft.network.chat.Component.translatable(h[1]);
            ctx.drawString(tr, label, x, y + 1, hintTextColor, true);
            x += tr.width(label.getString()) + gap;
        }
    }

    /**
     * The E11 "gelatina": a soft blob that glides from its current spot toward the selected chip,
     * drawn as a tapered chain of circles from the wheel center â€” it visibly stretches while
     * traveling and settles under the selection. No selection â†’ it rests at the center.
     */
    private static void drawJellyBlob(GuiGraphics ctx, int centerX, int centerY,
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

        if (selectedSlot < 0) return;   // resting at center with nothing selected â€” stay invisible

        // PIXEL-ART jelly (BG3-inspired, chunky): a tapered chain of squares quantized to a 2 px
        // grid â€” it stretches toward the target while traveling and settles as a crisp pixel blob.
        int steps = 6;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = quant2((int) Math.round(centerX + (blobX - centerX) * t));
            int y = quant2((int) Math.round(centerY + (blobY - centerY) * t));
            int half = 1 + (int) (t * 3);          // 2 px at the tail â†’ 8 px square at the head
            int alpha = 0x30 + (int) (t * 0x55);   // fades in toward the head
            ctx.fill(x - half, y - half, x + half, y + half, (alpha << 24) | 0xFFFFFF);
        }
        // Pixel-diamond head: a plus of 2 px squares â€” reads as a sprite, not a smooth ball.
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
