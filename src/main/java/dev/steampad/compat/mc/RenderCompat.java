package dev.steampad.compat.mc;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Version shim for GUI texture drawing and the background blur, both reshaped by the Blaze3D rewrite.
 * Signatures verified with {@code javap} against the real remapped jars:
 *
 * <ul>
 *   <li><b>Textures.</b> From 1.21.6 every draw takes an explicit {@code RenderPipeline} as its first
 *       argument ({@code RenderLayer::getGuiTextured} was removed), and gained a trailing tint colour.
 *       <br>1.21.1 — {@code blit(ResourceLocation, x, y, w, h, u, v, regionW, regionH, texW, texH)}
 *       <br>1.21.10 — {@code blit(RenderPipeline, ResourceLocation, x, y, u, v, w, h, regionW, regionH, texW, texH[, argb])}
 *       <br>Note the argument <em>order</em> differs too, not just the extras: the old form puts the
 *       draw size before the uv pair, the new one after. Easy to get silently wrong by hand.</li>
 *   <li><b>Tint.</b> The new form takes an ARGB int. The old one has no colour parameter at all —
 *       tinting was done by setting the shader colour around the draw, which is what this does.</li>
 *   <li><b>Blur.</b> {@code blurBeforeThisStratum()} belongs to the queued GUI render-state system and
 *       has no pre-rewrite equivalent — see {@link #blurBackground}.</li>
 * </ul>
 */
public final class RenderCompat {

    private RenderCompat() {}

    /**
     * Draws a whole texture scaled into a {@code drawW × drawH} box. {@code texW}/{@code texH} are the
     * texture's own dimensions.
     */
    public static void blitTexture(GuiGraphics ctx, ResourceLocation texture, int x, int y,
                                   int drawW, int drawH, int texW, int texH) {
        //? if >=1.21.6 {
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                texture, x, y, 0f, 0f, drawW, drawH, texW, texH, texW, texH);
        //?} else {
        /*ctx.blit(texture, x, y, drawW, drawH, 0f, 0f, texW, texH, texW, texH);*/
        //?}
    }

    /**
     * Same as {@link #blitTexture} but multiplied by an ARGB tint. On versions without a colour
     * parameter the shader colour is set around the draw and restored afterwards — leaving it set
     * would tint everything drawn later in the frame.
     */
    public static void blitTextureTinted(GuiGraphics ctx, ResourceLocation texture, int x, int y,
                                         int drawW, int drawH, int texW, int texH, int argb) {
        //? if >=1.21.6 {
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                texture, x, y, 0f, 0f, drawW, drawH, texW, texH, texW, texH, argb);
        //?} else {
        /*float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(r, g, b, a);
        ctx.blit(texture, x, y, drawW, drawH, 0f, 0f, texW, texH, texW, texH);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        *///?}
    }

    /** Draws a GUI-atlas sprite into a {@code w × h} box. */
    public static void blitSprite(GuiGraphics ctx, ResourceLocation sprite,
                                  int x, int y, int w, int h) {
        //? if >=1.21.6 {
        ctx.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                sprite, x, y, w, h);
        //?} else {
        /*ctx.blitSprite(sprite, x, y, w, h);*/
        //?}
    }

    /**
     * Blurs whatever has been drawn so far this frame, so an overlay reads as focused against a soft
     * background — the same post-process pass vanilla's own pause/inventory screens use, on BOTH
     * version families.
     *
     * <ul>
     *   <li><b>&ge; 1.21.6</b> — one queued command ({@code blurBeforeThisStratum}) resolved when the
     *       GUI render-state list is flushed.</li>
     *   <li><b>&lt; 1.21.6</b> — the pre-rewrite path, run immediately: flush the GUI batch so
     *       everything drawn so far is actually in the framebuffer, run
     *       {@code GameRenderer.processBlurEffect}, then rebind the main render target so subsequent
     *       drawing lands there. That is vanilla's OWN sequence, verbatim from
     *       {@code Screen.renderBlurredBackground}'s bytecode (blur, then
     *       {@code getMainRenderTarget().bindWrite(false)}) — the only addition is the flush, which is
     *       what makes it safe to run from a HUD overlay mid-frame instead of from screen rendering.
     *       </li>
     * </ul>
     *
     * <p>This previously returned {@code false} on the older family, on the assumption that reaching
     * for the pre-rewrite path outside screen rendering would fight the frame's ordering (D140) — so
     * the radial wheels fell back to a flat dim there. That assumption was wrong: the pass is a plain
     * full-screen post-process and, with the batch flushed first, composes correctly from the HUD
     * overlay too. Kept as a boolean return anyway, because there is still one case where no blur
     * happens: vanilla's own {@code processBlurEffect} silently does nothing when the player has set
     * "Menu background blurriness" to 0 (verified in its bytecode — it early-outs below 1). Reporting
     * that back lets the caller dim instead, so the wheel never ends up with no separation at all.
     *
     * @return whether a real blur was applied, so callers can compensate when it was not
     */
    public static boolean blurBackground(GuiGraphics ctx) {
        //? if >=1.21.6 {
        ctx.blurBeforeThisStratum();
        return true;
        //?} else {
        /*net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        // Vanilla's blur honours this setting and no-ops at 0; mirror that instead of blurring
        // anyway, so a player who deliberately turned menu blur off doesn't get it here regardless.
        if (mc.options.menuBackgroundBlurriness().get() < 1) return false;
        ctx.flush();
        mc.gameRenderer.processBlurEffect(FrameTimeCompat.partialTick(mc));
        mc.getMainRenderTarget().bindWrite(false);
        return true;
        *///?}
    }
}
