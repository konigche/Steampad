package dev.steampad.mixin;

import dev.steampad.emote.EmoteAnimator;
import dev.steampad.input.JuiceController;
import dev.steampad.input.ThirdPersonCameraController;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
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
 * {@code this.thirdPerson = <the boolean parameter>} — the exact field {@link Camera#isDetached()}
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

    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow public abstract Vec3 getPosition();
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
     *
     * <p><b>{@code inverseView} (front-view / mirror mode) still runs through this hook (D123).</b>
     * Third iteration of this decision: D120 bailed on {@code inverseView} → vanilla's hard-locked
     * front camera, reported as "bloqueada/laggy"; D121 removed the bail AND made the pose identical
     * to back-view → mirror mode "desapareció" (a free orbit doesn't inherently face you). Now the
     * pose still comes from the free pipeline (same smoothing/offset/wall-clamp — the no-lag half),
     * but {@code computeFreePoseInner}'s own mirror block drives yaw/pitch toward the player's front
     * instead of the stick (the "siempre viendo de frente" half) — so this hook stays a dumb
     * apply-the-pose site with zero perspective-specific logic of its own.
     */
    @Inject(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At("TAIL"))
    private void steampad$applyFreeLook(BlockGetter area, Entity focusedEntity, boolean thirdPerson,
                                        boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!ThirdPersonCameraController.isFreeLookEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || focusedEntity != mc.player) return;

        Camera self = (Camera) (Object) this;
        // tickDelta is vanilla's own render-interpolation factor for THIS frame — forwarded so the
        // free camera can read its tick-rate follow state interpolated instead of stair-stepped.
        // Passing it is what makes walking smooth; see ThirdPersonCameraController.clientTick.
        var pose = ThirdPersonCameraController.computeFreePose(mc, self, tickDelta);
        if (pose == null) return;   // e.g. first person — let vanilla's own result stand

        // D123: the enter/exit "camera leaves/re-enters the body" ease applies on top of the FREE
        // pose too. It used to live only in the plain-offset hook below — which bails whenever
        // free-look is on, and a REAL emote FORCES free-look on (D100), so the ease built FOR emotes
        // had been unreachable during emotes. See PerspectiveTransition's class doc.
        Vec3 eased = dev.steampad.input.PerspectiveTransition.blend(mc, pose.pos(), tickDelta);
        this.setPosition(eased != null ? eased : pose.pos());
        this.setRotation(pose.yaw(), pose.pitch());
    }

    @Inject(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At("TAIL"))
    private void steampad$applyOffset(BlockGetter area, Entity focusedEntity, boolean thirdPerson,
                                      boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (ThirdPersonCameraController.isFreeLookEnabled()) return;   // handled at HEAD instead

        Camera self = (Camera) (Object) this;
        Minecraft mc = Minecraft.getInstance();

        Vec3 pos = this.getPosition();
        Vec3 transitionPos = dev.steampad.input.PerspectiveTransition.blend(mc, pos, tickDelta);
        if (transitionPos != null) {
            this.setPosition(transitionPos);
            pos = transitionPos;
        }

        Vec3 offset = ThirdPersonCameraController.computeOffset(mc, self, focusedEntity);
        if (offset.x != 0 || offset.y != 0 || offset.z != 0) {
            this.setPosition(pos.add(offset));
        }
    }

    /**
     * Unconditional — runs regardless of perspective or free-look state, on top of whatever position
     * the hooks above already settled on. {@link JuiceController#cameraOffset()} is {@code Vec3d.ZERO}
     * whenever nothing is shaking, so this is a single cheap check the rest of the time.
     */
    @Inject(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At("TAIL"))
    private void steampad$applyScreenShake(BlockGetter area, Entity focusedEntity, boolean thirdPerson,
                                           boolean inverseView, float tickDelta, CallbackInfo ci) {
        Vec3 shake = JuiceController.cameraOffset();
        if (shake.x != 0 || shake.y != 0 || shake.z != 0) {
            this.setPosition(this.getPosition().add(shake));
        }
    }
}
