package dev.steampad.client.ui;

import dev.steampad.emote.EmoteData;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * A small fixed preview panel showing name/author/duration for whatever emote is currently
 * highlighted — one shared look reused by the emote library browser, the wheel-slot editor, and the
 * in-game emote wheel overlay itself (feedback: "previos en la rueda de emotes... puede ser fijo por
 * optimización... puedes dividir y poner el previo del lado derecho"). Drawing ONE static text panel
 * instead of animating every row's own icon is exactly the "fijo por optimización" the user asked
 * for — a single cheap fill+text call regardless of how many emotes are in the list.
 *
 * <p>Callers that also want an ANIMATED character preview (the user's follow-up ask, not a plain
 * silhouette but the player's own model actually posed by the emote) reserve {@code entityInset} px
 * at the top of this same panel and draw it themselves via
 * {@code InventoryScreen.drawEntity(...)} driven by {@link dev.steampad.emote.EmoteAnimator#playFor}
 * on a dedicated, non-networked preview playback — see {@code EmoteLibraryScreen},
 * {@code EmoteWheelScreen}, and {@code EmoteWheelOverlay} for the three call sites.
 */
public final class EmotePreviewPanel {

    private EmotePreviewPanel() {}

    public static void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, EmoteData data) {
        render(ctx, tr, x, y, w, h, data, 0, false);
    }

    /**
     * @param entityInset extra vertical space reserved at the top of the panel for a caller-drawn
     *                    animated character preview (see {@code EmoteLibraryScreen}) — 0 for the
     *                    plain text-only panel.
     */
    public static void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, EmoteData data,
                              int entityInset) {
        render(ctx, tr, x, y, w, h, data, entityInset, false);
    }

    /**
     * @param noWorldHint true when there's a real emote to preview but no local player entity exists
     *                    to animate it with (this screen was opened outside a loaded world) — shows a
     *                    small explanatory line instead of silently leaving the space blank, which
     *                    otherwise reads as "the preview is broken" (feedback: "en menú ajustes parece
     *                    no funcionar" — a world-less screen genuinely has no player to pose).
     */
    public static void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, EmoteData data,
                              int entityInset, boolean noWorldHint) {
        ctx.fill(x, y, x + w, y + h, 0xCC161616);
        ctx.fill(x, y, x + w, y + 1, 0xFF565656);
        ctx.fill(x, y, x + 1, y + h, 0xFF565656);
        ctx.fill(x + w - 1, y, x + w, y + h, 0xFF565656);
        ctx.fill(x, y + h - 1, x + w, y + h, 0xFF565656);

        int cx = x + w / 2;
        if (data == null) {
            ctx.drawCenteredTextWithShadow(tr, Text.translatable("steampad.emote.preview.empty"),
                    cx, y + entityInset + (h - entityInset) / 2 - 4, 0xFF888888);
            return;
        }

        int ty = y + entityInset + 8;
        ctx.drawCenteredTextWithShadow(tr, Text.literal(data.name), cx, ty, 0xFFFFFFFF);
        ty += 13;
        if (data.author != null && !data.author.isBlank()) {
            ctx.drawCenteredTextWithShadow(tr, Text.literal("— " + data.author), cx, ty, 0xFFAAAAAA);
            ty += 12;
        }
        ty += 4;
        float seconds = data.totalTicks() / 20f;
        String meta = data.loop
                ? Text.translatable("steampad.emote.preview.loop").getString()
                : Text.translatable("steampad.emote.preview.duration", String.format("%.1f", seconds)).getString();
        ctx.drawCenteredTextWithShadow(tr, Text.literal(meta), cx, ty, 0xFF8CC8A2);
        if (noWorldHint) {
            ty += 14;
            ctx.drawCenteredTextWithShadow(tr, Text.translatable("steampad.emote.preview.no_world"),
                    cx, ty, 0xFF777777);
        }
    }
}
