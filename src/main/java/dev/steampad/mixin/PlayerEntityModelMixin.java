package dev.steampad.mixin;

// Two implementations, one per era. From 1.21.2 setupAnim receives an AvatarRenderState carrying both
// the entity id and the duck-typed preview tag; before that it receives the ENTITY itself and there is
// no state object to tag, so the preview is read straight off EmotePreviewTagger. That works because
// pre-1.21.2 GUI entity rendering is IMMEDIATE rather than queued-and-flushed — the tag set just before
// the draw is still the right one during it, which is exactly what the deferred path could not assume
// (D099). Same EmoteAnimator calls in both, so the hardware-validated pose behaviour is shared.
//? if >=1.21.2 {
import dev.steampad.emote.EmoteAnimator;
import dev.steampad.emote.EmoteData;
import dev.steampad.emote.EmotePreviewState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thin render hook for the emote engine (CLAUDE.md restriction 4: hooks only, zero logic here).
 * Two injections, mirroring the real reference's own two-step split (D109 — verified in
 * {@code PlayerModelMixin.java}, KosmX/minecraftPlayerAnimator, MIT):
 * <ul>
 *   <li><b>HEAD:</b> {@link EmoteAnimator#resetToBakedRest} — unconditionally resets origin/rotation
 *       on every bendable part to its baked pivot, BEFORE vanilla's own per-frame pose logic runs.
 *       Since the biped model instance is SHARED across every player using the same arm-width
 *       variant, this is the only thing that stops a value ONE entity's emote wrote (on a field
 *       vanilla itself never touches, e.g. arm yaw/roll) from silently surviving on every OTHER
 *       entity's render forever (D108's post-mortem — a narrower TAIL-only version of this same
 *       idea missed fields vanilla only CONDITIONALLY manages, like sneaking's {@code body.pitch}).</li>
 *   <li><b>TAIL:</b> after vanilla finishes posing the model for this frame (on top of the clean
 *       reset above), the emote pose overrides exactly the channels it animates — everything else
 *       keeps whatever vanilla just computed. Two paths:
 *       <ul>
 *         <li>Render state carries a frozen-frame preview tag ({@link EmotePreviewState}, set at
 *             queue time — GUI thumbnails) → {@link EmoteAnimator#applyStatic} poses THIS draw
 *             independently, letting many differently-posed cells coexist in one deferred flush
 *             (D099).</li>
 *         <li>Otherwise, {@link EmoteAnimator#apply} looks up the live playback by
 *             {@code PlayerEntityRenderState.id} (the entity id — verified via javap on the mapped
 *             1.21.10 jar). No emote running for that id → one Map.get and out.</li>
 *       </ul>
 *   </li>
 * </ul>
 */
@Mixin(PlayerModel.class)
public abstract class PlayerEntityModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("HEAD"))
    private void steampad$resetPoseBeforeVanilla(AvatarRenderState state, CallbackInfo ci) {
        EmoteAnimator.resetToBakedRest((HumanoidModel<?>) (Object) this);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("TAIL"))
    private void steampad$applyEmote(AvatarRenderState state, CallbackInfo ci) {
        EmoteData pinned = ((EmotePreviewState) state).steampad$pinnedPreviewData();
        if (pinned != null) {
            EmoteAnimator.applyStatic((HumanoidModel<?>) (Object) this, pinned,
                    ((EmotePreviewState) state).steampad$pinnedPreviewTick());
        } else {
            EmoteAnimator.apply((HumanoidModel<?>) (Object) this, state.id,
                    ((EmotePreviewState) state).steampad$isGuiPreviewPass());
        }
        // Re-syncs hat/jacket/sleeves/pants to their (possibly just-overridden) base bone — see the
        // method's own doc for why vanilla's own per-frame sync isn't enough once TAIL overrides the
        // base bones (D112 correction). Raw PlayerModel on purpose: the class is generic before the
        // entity-render-state rework and non-generic after, so the raw type is the one spelling that
        // compiles on both — see syncOverlayLayers' doc.
        EmoteAnimator.syncOverlayLayers((PlayerModel) (Object) this);
    }
}
//?} else {
/*import dev.steampad.emote.EmoteAnimator;
import dev.steampad.emote.EmoteData;
import dev.steampad.emote.EmotePreviewTagger;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Pre-1.21.2 counterpart of the render-state hook: same two-step HEAD-reset / TAIL-apply split and the
 * same EmoteAnimator entry points, but sourced differently.
 *
 * <p>{@code setupAnim} here takes the entity (erased to {@code LivingEntity} by the generic
 * {@code PlayerModel<T>}), so the live playback key comes from {@code entity.getId()} directly instead
 * of a render state's {@code id}. And since GUI entity rendering on these versions draws immediately
 * rather than queueing for a later flush, the frozen-frame preview can simply be read from
 * {@link EmotePreviewTagger} during the draw — no per-instance tag to carry, which is why no duck
 * interface is needed and {@code PlayerEntityRenderStateMixin} has no counterpart here.
 ^/
@Mixin(PlayerModel.class)
public abstract class PlayerEntityModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void steampad$resetPoseBeforeVanilla(LivingEntity entity, float limbSwing,
                                                 float limbSwingAmount, float ageInTicks,
                                                 float netHeadYaw, float headPitch, CallbackInfo ci) {
        EmoteAnimator.resetToBakedRest((HumanoidModel<?>) (Object) this);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void steampad$applyEmote(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                     float ageInTicks, float netHeadYaw, float headPitch,
                                     CallbackInfo ci) {
        EmoteData pinned = EmotePreviewTagger.pendingData();
        if (pinned != null) {
            EmoteAnimator.applyStatic((HumanoidModel<?>) (Object) this, pinned,
                    EmotePreviewTagger.pendingTick());
        } else {
            EmoteAnimator.apply((HumanoidModel<?>) (Object) this, entity.getId(),
                    EmotePreviewTagger.isGuiPass());
        }
        EmoteAnimator.syncOverlayLayers((PlayerModel) (Object) this);
    }
}
*///?}
