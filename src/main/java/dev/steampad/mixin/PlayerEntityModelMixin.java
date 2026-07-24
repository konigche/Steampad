package dev.steampad.mixin;

import dev.steampad.emote.EmoteAnimator;
import dev.steampad.emote.EmoteData;
import dev.steampad.emote.EmotePreviewState;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thin render hook for the emote engine (CLAUDE.md restriction 4: hooks only, zero logic here).
 * After vanilla finishes posing the player model for a frame, the emote pose overrides exactly the
 * channels the emote animates — everything else keeps the vanilla pose. Two paths:
 * <ul>
 *   <li>Render state carries a frozen-frame preview tag ({@link EmotePreviewState}, set at queue
 *       time — GUI thumbnails) → {@link EmoteAnimator#applyStatic} poses THIS draw independently,
 *       which is what lets many differently-posed cells coexist in one deferred flush (D099).</li>
 *   <li>Otherwise, {@link EmoteAnimator#apply} looks up the live playback by
 *       {@code PlayerEntityRenderState.id} (the entity id — verified via javap on the mapped
 *       1.21.10 jar). No emote running for that id → one Map.get and out.</li>
 * </ul>
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V",
            at = @At("TAIL"))
    private void steampad$applyEmote(PlayerEntityRenderState state, CallbackInfo ci) {
        EmoteData pinned = ((EmotePreviewState) state).steampad$pinnedPreviewData();
        if (pinned != null) {
            EmoteAnimator.applyStatic((BipedEntityModel<?>) (Object) this, pinned,
                    ((EmotePreviewState) state).steampad$pinnedPreviewTick());
        } else {
            EmoteAnimator.apply((BipedEntityModel<?>) (Object) this, state.id);
        }
    }
}
