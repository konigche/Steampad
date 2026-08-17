package dev.steampad.compat.mc;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Version shim for the GUI transform stack, which changed type outright in the Blaze3D rewrite —
 * not just signature. Verified against the real remapped jars:
 *
 * <ul>
 *   <li>&le; 1.21.5 — {@code GuiGraphics.pose()} returns a 3D {@code PoseStack}:
 *       {@code pushPose()}/{@code popPose()}, and translate/scale take x, y <em>and z</em>.</li>
 *   <li>&ge; 1.21.8 — it returns a 2D {@code org.joml.Matrix3x2fStack}:
 *       {@code pushMatrix()}/{@code popMatrix()}, and translate/scale take x, y only.</li>
 * </ul>
 *
 * <p>The boundary is 1.21.6 (the Blaze3D rewrite); 1.21.6 and 1.21.7 were not available locally to
 * probe directly, so if a variant for either is ever added, confirm the type before trusting it.
 *
 * <p>This exists so the ~13 call sites across the radial renderer, the chat mixins and the emote /
 * controller screens stay readable: one conditional per operation here instead of one at every call
 * site. The z the old API needs is a no-op for 2D GUI work (0 for translate, 1 for scale), so both
 * branches mean the same thing.
 */
public final class GuiPose {

    private GuiPose() {}

    /** Saves the current GUI transform. Pair with {@link #pop}. */
    public static void push(GuiGraphics ctx) {
        //? if >=1.21.6 {
        ctx.pose().pushMatrix();
        //?} else {
        /*ctx.pose().pushPose();
        *///?}
    }

    /** Restores the transform saved by the matching {@link #push}. */
    public static void pop(GuiGraphics ctx) {
        //? if >=1.21.6 {
        ctx.pose().popMatrix();
        //?} else {
        /*ctx.pose().popPose();
        *///?}
    }

    /** Translates the GUI transform. */
    public static void translate(GuiGraphics ctx, float x, float y) {
        //? if >=1.21.6 {
        ctx.pose().translate(x, y);
        //?} else {
        /*ctx.pose().translate(x, y, 0.0f);
        *///?}
    }

    /** Scales the GUI transform about the current origin. */
    public static void scale(GuiGraphics ctx, float sx, float sy) {
        //? if >=1.21.6 {
        ctx.pose().scale(sx, sy);
        //?} else {
        /*ctx.pose().scale(sx, sy, 1.0f);
        *///?}
    }

    /**
     * Opens a drawing layer guaranteed to land ON TOP of item stacks the screen already drew. Pair
     * with {@link #popOverlay}.
     *
     * <p><b>Why this is needed at all.</b> On the 2D (post-Blaze3D-rewrite) versions, GUI drawing is
     * collected into an ordered render-state list, so "drawn later" simply means "on top" and this is
     * a plain {@link #push}. On the older 3D versions it is not: {@code GuiGraphics} batches geometry
     * into a shared buffer that is flushed in render-TYPE order rather than call order, and item
     * stacks are additionally drawn at <b>z = 150</b> (confirmed in {@code GuiGraphics.renderItem}'s
     * bytecode: {@code translate(x + 8, y + 8, 150)}) with depth testing on, while ordinary GUI fills
     * sit at z = 0. An overlay drawn "after" the screen therefore still renders UNDERNEATH every item
     * — the reported bug where inventory icons floated over the virtual keyboard and cursor.
     *
     * <p>So on those versions this flushes what the screen queued (putting the items into the depth
     * buffer, and fixing translucent blend order) and then raises z above them.
     *
     * @param z height above the base GUI plane. Item stacks are at 150 and vanilla tooltips at 400,
     *          so ~200–350 sits above items while leaving tooltips on top; above 400 covers those too.
     *          Ignored entirely on the 2D versions, which have no z.
     */
    public static void pushOverlay(GuiGraphics ctx, float z) {
        //? if >=1.21.6 {
        ctx.pose().pushMatrix();
        //?} else {
        /*ctx.flush();
        ctx.pose().pushPose();
        ctx.pose().translate(0.0f, 0.0f, z);
        *///?}
    }

    /**
     * Closes a {@link #pushOverlay} layer.
     *
     * <p>No flush is needed here: on the 3D versions the transform is baked into each vertex as it is
     * built, so geometry already queued keeps the raised z after the pose is popped.
     */
    public static void popOverlay(GuiGraphics ctx) {
        //? if >=1.21.6 {
        ctx.pose().popMatrix();
        //?} else {
        /*ctx.pose().popPose();
        *///?}
    }
}
