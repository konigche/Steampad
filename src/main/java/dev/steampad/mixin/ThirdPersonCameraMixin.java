package dev.steampad.mixin;

import dev.steampad.emote.EmoteAnimator;
import dev.steampad.input.JuiceController;
import dev.steampad.input.ThirdPersonCameraController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thin hook for three camera effects that all work by nudging/replacing the ALREADY vanilla-computed
 * camera position (CLAUDE.md restriction 4: hooks only, zero logic here — the math lives in
 * {@link ThirdPersonCameraController} and {@link EmoteAnimator}):
 * <ol>
 *   <li>the free-look camera override (full replacement of position/rotation),</li>
 *   <li>the emote first-person↔third-person transition (eases instead of jump-cutting), and</li>
 *   <li>the over-the-shoulder camera offset.</li>
 * </ol>
 *
 * <p>Signature verified against the mapped 1.21.10 jar via javap:
 * {@code public void update(BlockView, Entity, boolean, boolean, float)}. ALL THREE hooks fire at
 * TAIL — AFTER vanilla has already run its own full method body to completion — so every one of
 * them only ever adjusts an already-fully-computed camera; none replaces or bypasses vanilla's own
 * logic (see {@link ThirdPersonCameraController}'s class doc for why that matters here specifically).
 *
 * <p><b>Free-look was a HEAD-cancel until v0.56.0 (D093), fixed after real-hardware testing showed
 * free-look camera looking "like first person, orbiting an invisible player":</b> {@code update()}'s
 * very first instructions (verified via javap) are {@code this.ready = true}, then
 * {@code this.thirdPerson = <the boolean parameter>} — the exact field {@link Camera#isThirdPerson()}
 * reads, which vanilla's own entity-render code checks to decide whether to draw the camera's own
 * focused entity (skipped in first person — you can't see inside your own head). Cancelling at HEAD
 * means that assignment never runs, so {@code thirdPerson} stays frozen at whatever it last was
 * BEFORE free-look engaged — for anyone with free-look already enabled at world-load (config
 * persists it) that's the field's Java default, {@code false}, forever: vanilla treats every frame
 * as first person, hides the player model, and every OTHER book-keeping field {@code update()} sets
 * (submersion-relevant {@code area}, the frustum {@code horizontalPlane}/{@code verticalPlane}/
 * {@code diagonalPlane}, etc.) freezes right along with it. Letting the full method run at TAIL
 * instead costs one extra vanilla third-person raycast per frame (cheap — see
 * {@code ThirdPersonCameraController}'s own perf-diagnostic note) in exchange for zero risk of ever
 * missing a book-keeping field vanilla itself depends on.
 */
@Mixin(Camera.class)
public abstract class ThirdPersonCameraMixin {

    @Shadow protected abstract void setPos(Vec3d pos);
    @Shadow public abstract Vec3d getPos();
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    /**
     * TAIL: only takes over when free-look is on (see
     * {@link ThirdPersonCameraController#isFreeLookEnabled}) — vanilla has already run its complete,
     * unmodified {@code update()} this frame (correctly setting {@code thirdPerson}/{@code ready}/
     * frustum planes/etc. from whatever it was actually called with), and THEN this overwrites the
     * position/rotation with the free camera's own math, exactly like the plain offset hook below
     * overwrites position only. {@code setRotation} itself recomputes the rotation quaternion and all
     * three frustum planes from the yaw/pitch given to it (verified via javap), so this fully replaces
     * vanilla's rotation state too — nothing about the free camera's own pose is left stale.
     */
    @Inject(method = "update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
            at = @At("TAIL"))
    private void steampad$applyFreeLook(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                        boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!ThirdPersonCameraController.isFreeLookEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || focusedEntity != mc.player || inverseView) return;

        Camera self = (Camera) (Object) this;
        var pose = ThirdPersonCameraController.computeFreePose(mc, self);
        if (pose == null) return;   // e.g. first person — let vanilla's own result stand

        this.setPos(pose.pos());
        this.setRotation(pose.yaw(), pose.pitch());
    }

    @Inject(method = "update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
            at = @At("TAIL"))
    private void steampad$applyOffset(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                      boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (ThirdPersonCameraController.isFreeLookEnabled()) return;   // handled at HEAD instead

        Camera self = (Camera) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();

        Vec3d pos = this.getPos();
        Vec3d transitionPos = EmoteAnimator.computeCameraOverride(mc, pos);
        if (transitionPos != null) {
            this.setPos(transitionPos);
            pos = transitionPos;
        }

        Vec3d offset = ThirdPersonCameraController.computeOffset(mc, self, focusedEntity);
        if (offset.x != 0 || offset.y != 0 || offset.z != 0) {
            this.setPos(pos.add(offset));
        }
    }

    /**
     * Unconditional — runs regardless of perspective or free-look state, on top of whatever position
     * the hooks above already settled on. {@link JuiceController#cameraOffset()} is {@code Vec3d.ZERO}
     * whenever nothing is shaking, so this is a single cheap check the rest of the time.
     */
    @Inject(method = "update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
            at = @At("TAIL"))
    private void steampad$applyScreenShake(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                           boolean inverseView, float tickDelta, CallbackInfo ci) {
        Vec3d shake = JuiceController.cameraOffset();
        if (shake.x != 0 || shake.y != 0 || shake.z != 0) {
            this.setPos(this.getPos().add(shake));
        }
    }
}
