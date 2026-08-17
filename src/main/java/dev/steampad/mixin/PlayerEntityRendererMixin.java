package dev.steampad.mixin;

// Gated to >=1.21.2 — hooks extractRenderState, which only exists once entity render states do. On
// 1.21.1 GUI entity rendering is immediate rather than deferred, so no per-cell tagging is needed at
// all and the 1.21.1 emote path is a different design; see S4 in PORT_MULTIVERSION.md.
//? if >=1.21.2 {
import dev.steampad.emote.EmotePreviewState;
import dev.steampad.emote.EmotePreviewTagger;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
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
@Mixin(AvatarRenderer.class)
public abstract class PlayerEntityRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void steampad$tagPreviewState(Avatar entity, AvatarRenderState state,
                                          float tickDelta, CallbackInfo ci) {
        ((EmotePreviewState) state).steampad$setPinnedPreview(
                EmotePreviewTagger.pendingData(), EmotePreviewTagger.pendingTick());
        ((EmotePreviewState) state).steampad$setGuiPreviewPass(EmotePreviewTagger.isGuiPass());
    }
}
//?}
