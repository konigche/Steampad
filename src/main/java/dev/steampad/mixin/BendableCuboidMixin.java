package dev.steampad.mixin;

import dev.steampad.emote.bend.BendableCuboid;
import dev.steampad.emote.bend.CuboidBender;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thin render hook for Emotecraft's {@code bend}/{@code bendDir} channels (CLAUDE.md restriction 4:
 * hooks only — every actual computation lives in {@link CuboidBender}). Swaps this cuboid's
 * {@code sides} to a freshly-bent quad array right before vanilla's own {@code renderCuboid} reads it,
 * so the entire rest of vanilla's rendering (matrix/light/overlay handling) runs completely untouched
 * — the only thing this changes is WHICH geometry gets rendered, never HOW.
 *
 * <p>Signature verified against the mapped 1.21.10 jar via {@code javap} (Mojmap names — this doc
 * used to cite the pre-migration Yarn names, {@code ModelPart.Cuboid}/{@code sides}/{@code Quad[]},
 * left stale after D138's Yarn→Mojmap migration even though the code below was already correct):
 * {@code ModelPart.Cube.compile(PoseStack.Pose, VertexConsumer, int, int, int)} (see the real
 * {@code @Inject} selector below), {@code public final ModelPart.Polygon[] polygons}.
 */
@Mixin(ModelPart.Cube.class)
public abstract class BendableCuboidMixin implements BendableCuboid {

    // Same field name on every supported version, but its visibility flipped: private final in
    // 1.21.1, public final from 1.21.2. Declared to match per version so Mixin's shadow-visibility
    // validation has nothing to complain about — javac would accept either, so this is about mixin
    // apply time, not compile time.
    @Mutable
    @Shadow
    @org.spongepowered.asm.mixin.Final
    //? if >=1.21.2 {
    public ModelPart.Polygon[] polygons;
    //?} else {
    /*private ModelPart.Polygon[] polygons;*/
    //?}

    private ModelPart.Polygon[] steampad$originalSides;
    private CuboidBender.Precomputed steampad$precomputed;
    private Direction steampad$precomputedDirection;
    private boolean steampad$bendActive = false;
    private float steampad$bendAxis = 0f;
    private float steampad$bendValue = 0f;

    @Override
    public void steampad$setBend(Direction direction, float bendAxis, float bendValue) {
        if (steampad$originalSides == null) steampad$originalSides = this.polygons;
        if (steampad$precomputed == null || steampad$precomputedDirection != direction) {
            steampad$precomputed = CuboidBender.precompute(steampad$originalSides, direction);
            steampad$precomputedDirection = direction;
        }
        steampad$bendActive = true;
        steampad$bendAxis = bendAxis;
        steampad$bendValue = bendValue;
    }

    @Override
    public void steampad$clearBend() {
        steampad$bendActive = false;
    }

    @Inject(method = "compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("HEAD"))
    private void steampad$swapBentGeometry(com.mojang.blaze3d.vertex.PoseStack.Pose entry,
                                           com.mojang.blaze3d.vertex.VertexConsumer vertexConsumer,
                                           int light, int overlay, int color, CallbackInfo ci) {
        if (steampad$originalSides == null) steampad$originalSides = this.polygons;
        // A no-op bend (value ~0, e.g. easing in/out at the edges of the emote's active window) still
        // renders through CuboidBender rather than early-returning to the untouched original array —
        // deliberately simple: correctness over a micro-optimization here, since only a handful of
        // cuboids on the LOCAL bendable parts ever have an active bend at once (never every player).
        this.polygons = steampad$bendActive && steampad$precomputed != null
                ? CuboidBender.apply(steampad$precomputed, steampad$bendAxis, steampad$bendValue)
                : steampad$originalSides;
    }
}
