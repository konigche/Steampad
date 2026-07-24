package dev.steampad.mixin;

import dev.steampad.emote.EmoteData;
import dev.steampad.emote.EmotePreviewState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adds the {@link EmotePreviewState} duck fields to the player render state — pure data carrier,
 * zero logic (CLAUDE.md restriction 4). See the duck interface's doc for why this exists (per-cell
 * static emote thumbnails through 1.21.10's deferred GUI entity rendering, D099).
 */
@Mixin(PlayerEntityRenderState.class)
public abstract class PlayerEntityRenderStateMixin implements EmotePreviewState {

    @Unique private EmoteData steampad$previewData;
    @Unique private float steampad$previewTick;

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
}
