package dev.steampad.mixin;

// Two implementations, one per era. The hook point is the same idea on both — the RETURN of the
// renderer's own setupRotations, after vanilla has placed and rotated the entity and before
// LivingEntityRenderer.render's scale(-1,-1,1) flip — but the class, the method signature and where the
// preview tag comes from all differ. The transform math itself is identical, so the D110 fix ("se
// sienta en el aire") behaves the same on both.
//? if >=1.21.2 {
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.steampad.emote.EmoteAnimator;
import dev.steampad.emote.EmoteData;
import dev.steampad.emote.EmotePreviewState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thin render hook applying an emote's WHOLE-MODEL ("body") transform (CLAUDE.md restriction 4:
 * hooks only — all sampling lives in {@link EmoteAnimator}). Direct port of the reference's own
 * {@code PlayerRendererMixin.applyBodyTransforms} (KosmX/minecraftPlayerAnimator, <b>MIT</b>, ported
 * with attribution — same standing as this project's sampler port, D099), adapted from its Mojang
 * 1.21.1 mappings to Yarn 1.21.10.
 *
 * <p><b>Why this exists (D110):</b> the {@code body} channel of an emote is NOT a bone — the
 * reference consumes it here, in the renderer, as a transform on the matrix stack for the ENTIRE
 * player. It is the only mechanism that can physically lower a character onto the floor to sit, or
 * tip the whole body horizontal to crawl. SteamPad previously merged {@code body} into the torso
 * BONE, so that data came out as a torso rotation while the character stayed standing at full
 * height — the long-reported "se sienta en el aire".
 *
 * <p>Injected at {@code RETURN} of {@code setupTransforms} (the reference uses the same hook point,
 * named {@code setupRotations} in Mojang mappings) — after vanilla has placed and rotated the entity
 * in the world, and crucially BEFORE {@code LivingEntityRenderer.render}'s own {@code scale(-1,-1,1)}
 * flip and model-unit descent, so translations here are in BLOCK units, exactly as authored.
 * Signature verified with {@code javap} against the mapped 1.21.10 jar:
 * {@code setupTransforms(PlayerEntityRenderState, MatrixStack, float, float)}.
 */
@Mixin(AvatarRenderer.class)
public abstract class PlayerBodyTransformMixin {

    /** Rotation pivot height, in blocks — the reference's own constant: rotate about the body centre
     *  rather than the feet, then undo the shift (translate +0.7 → rotate → translate −0.7). */
    private static final double STEAMPAD$PIVOT_Y = 0.7;

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("RETURN"))
    private void steampad$applyBodyTransform(AvatarRenderState state, PoseStack matrices,
                                             float bodyYaw, float scale, CallbackInfo ci) {
        // A GUI thumbnail carries its own frozen frame (duck-tagged at queue time, D099) so the cell
        // shows the same whole-model pose the emote really has; everything else reads live playback.
        EmoteData pinned = ((EmotePreviewState) state).steampad$pinnedPreviewData();
        EmoteAnimator.BodyTransform body = pinned != null
                ? EmoteAnimator.computeStaticBodyTransform(pinned,
                        ((EmotePreviewState) state).steampad$pinnedPreviewTick())
                : EmoteAnimator.computeBodyTransform(state.id,
                        ((EmotePreviewState) state).steampad$isGuiPreviewPass());
        if (body == null) return;   // nothing playing, or this emote never animates the body channel

        matrices.scale(body.scaleX(), body.scaleY(), body.scaleZ());
        matrices.translate(body.x(), body.y() + STEAMPAD$PIVOT_Y, body.z());
        // Same axis order as the reference: roll (Z), then yaw (Y), then pitch (X).
        matrices.mulPose(Axis.ZP.rotation(body.roll()));
        matrices.mulPose(Axis.YP.rotation(body.yaw()));
        matrices.mulPose(Axis.XP.rotation(body.pitch()));
        matrices.translate(0.0, -STEAMPAD$PIVOT_Y, 0.0);
    }
}
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.steampad.emote.EmoteAnimator;
import dev.steampad.emote.EmoteData;
import dev.steampad.emote.EmotePreviewTagger;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Pre-1.21.2 counterpart of the whole-model ("body") transform hook — the channel that physically
 * lowers a character onto the floor to sit or tips the whole body to crawl (D110). Identical matrix
 * work to the render-state version; only the plumbing differs.
 *
 * <p>Target is {@code PlayerRenderer}, which on these versions declares its own
 * {@code setupRotations(AbstractClientPlayer, PoseStack, float, float, float, float)} override
 * (confirmed with {@code javap} — it is not merely inherited from {@code LivingEntityRenderer}, so
 * injecting here reaches players only and never every other living entity). Injected at RETURN, the
 * same point the reference uses, which keeps translations in BLOCK units as authored.
 *
 * <p>The frozen-frame preview comes straight from {@link EmotePreviewTagger} rather than from a tag
 * carried on a render state: GUI entity rendering is immediate here, so the tag standing at draw time
 * is the right one. Live playback is keyed by {@code entity.getId()}.
 ^/
@Mixin(PlayerRenderer.class)
public abstract class PlayerBodyTransformMixin {

    /^* Rotation pivot height, in blocks — the reference's own constant: rotate about the body centre
     *  rather than the feet, then undo the shift (translate +0.7 → rotate → translate −0.7). ^/
    private static final double STEAMPAD$PIVOT_Y = 0.7;

    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("RETURN"))
    private void steampad$applyBodyTransform(AbstractClientPlayer entity, PoseStack matrices,
                                             float ageInTicks, float bodyYaw, float partialTicks,
                                             float scale, CallbackInfo ci) {
        EmoteData pinned = EmotePreviewTagger.pendingData();
        EmoteAnimator.BodyTransform body = pinned != null
                ? EmoteAnimator.computeStaticBodyTransform(pinned, EmotePreviewTagger.pendingTick())
                : EmoteAnimator.computeBodyTransform(entity.getId(), EmotePreviewTagger.isGuiPass());
        if (body == null) return;   // nothing playing, or this emote never animates the body channel

        matrices.scale(body.scaleX(), body.scaleY(), body.scaleZ());
        matrices.translate(body.x(), body.y() + STEAMPAD$PIVOT_Y, body.z());
        // Same axis order as the reference: roll (Z), then yaw (Y), then pitch (X).
        matrices.mulPose(Axis.ZP.rotation(body.roll()));
        matrices.mulPose(Axis.YP.rotation(body.yaw()));
        matrices.mulPose(Axis.XP.rotation(body.pitch()));
        matrices.translate(0.0, -STEAMPAD$PIVOT_Y, 0.0);
    }
}
*///?}
