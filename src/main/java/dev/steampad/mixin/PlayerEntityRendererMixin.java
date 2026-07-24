package dev.steampad.mixin;

import dev.steampad.emote.EmotePreviewState;
import dev.steampad.emote.EmotePreviewTagger;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Copies {@link EmotePreviewTagger}'s pending tag (usually null) into every freshly-updated player
 * render state — thin hook, one assignment (CLAUDE.md restriction 4). {@code updateRenderState}
 * runs synchronously at QUEUE time for GUI entity draws (verified: descriptor
 * {@code (Lnet/minecraft/entity/PlayerLikeEntity;…PlayerEntityRenderState;F)V} via javap on the
 * project's own Yarn-mapped 1.21.10 jar), which is exactly what lets each queued thumbnail carry
 * its own frozen-frame tag to the deferred flush — see {@link EmotePreviewState}. Writing null
 * unconditionally also scrubs stale tags off reused state objects.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL"))
    private void steampad$tagPreviewState(PlayerLikeEntity entity, PlayerEntityRenderState state,
                                          float tickDelta, CallbackInfo ci) {
        ((EmotePreviewState) state).steampad$setPinnedPreview(
                EmotePreviewTagger.pendingData(), EmotePreviewTagger.pendingTick());
    }
}
