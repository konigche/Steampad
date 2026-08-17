package dev.steampad.emote.bend;

import net.minecraft.core.Direction;

/**
 * Duck interface mixed onto {@code ModelPart.Cuboid} (see {@code BendableCuboidMixin}) — lets
 * non-mixin code (here, {@code EmoteAnimator}) drive the per-vertex "bend" deformation Emotecraft's
 * {@code bend}/{@code bendDir} channels describe, without either side needing to know about Mixin.
 *
 * <p>Same pattern as {@link dev.steampad.emote.EmotePreviewState} — a plain interface implemented by
 * a mixin, cast to from ordinary code.
 */
public interface BendableCuboid {

    /** Sets (or updates) this cuboid's current bend state — {@code direction} picks which pair of
     *  opposite faces is the fixed "base" end vs. the folding "other" end (matching the real
     *  reference's own per-part registration: torso = DOWN, arms/legs = UP), {@code bendAxis} is the
     *  bend's own in-plane direction (radians, from the emote's "bendDir"/"axis" channel) and
     *  {@code bendValue} is how far it folds (radians, from "bend"). Safe to call every frame with a
     *  changing value — the underlying geometry only needs to be built once per cuboid. */
    void steampad$setBend(Direction direction, float bendAxis, float bendValue);

    /** Reverts to vanilla's own unbent geometry. MUST be called for every bendable cuboid on every
     *  frame it should NOT be bending (not just when an emote ends) — Minecraft renders every player
     *  with a SHARED model instance (see {@code EmoteAnimator#restOrigin}'s own doc for the exact same
     *  lesson learned the hard way), so a bend left active by one entity's render pass would otherwise
     *  leak into the next entity/frame that reuses the same model without an emote at all. */
    void steampad$clearBend();
}
