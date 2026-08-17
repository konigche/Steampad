package dev.steampad.mixin;

// Entire class is gated to >=1.21.2: entity render states did not exist before that, so there is no
// object to hang the emote preview tag on and the mixin target itself is absent. On 1.21.1 this file
// compiles to nothing and the class is not registered in any mixin config — see
// steampad-renderstate.mixins.json and its conditional registration in build.gradle.kts.
// The 1.21.1 emote path is a different design entirely (GUI entity rendering is immediate there, not
// deferred, so no per-cell tagging is needed at all) and lands in S4.
//? if >=1.21.2 {
import dev.steampad.emote.EmoteData;
import dev.steampad.emote.EmotePreviewState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adds the {@link EmotePreviewState} duck fields to the player render state — pure data carrier,
 * zero logic (CLAUDE.md restriction 4). See the duck interface's doc for why this exists (per-cell
 * static emote thumbnails through 1.21.10's deferred GUI entity rendering, D099).
 */
@Mixin(AvatarRenderState.class)
public abstract class PlayerEntityRenderStateMixin implements EmotePreviewState {

    @Unique private EmoteData steampad$previewData;
    @Unique private float steampad$previewTick;
    @Unique private boolean steampad$guiPass;

    @Override
    public void steampad$setPinnedPreview(EmoteData data, float tick) {
        this.steampad$previewData = data;
        this.steampad$previewTick = tick;
    }

    @Override
    public EmoteData steampad$pinnedPreviewData() {
        return this.steampad$previewData;
    }

    @Override
    public float steampad$pinnedPreviewTick() {
        return this.steampad$previewTick;
    }

    @Override
    public void steampad$setGuiPreviewPass(boolean guiPass) {
        this.steampad$guiPass = guiPass;
    }

    @Override
    public boolean steampad$isGuiPreviewPass() {
        return this.steampad$guiPass;
    }
}
//?}
