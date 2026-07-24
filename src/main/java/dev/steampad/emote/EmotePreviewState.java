package dev.steampad.emote;

/**
 * Duck interface mixed onto {@code PlayerEntityRenderState} (see {@code PlayerEntityRenderStateMixin})
 * — carries an optional "pose this exact queued draw as a FROZEN frame of THIS emote" tag.
 *
 * <p>This is what makes per-cell static thumbnails possible at all in 1.21.10: GUI entity draws are
 * DEFERRED ({@code DrawContext.addEntity} queues, the flush happens once at the end of the screen
 * pass — D092), so every queued draw of the same player used to read the same single entry of
 * {@link EmoteAnimator}'s per-entity-id playback map at flush time and show one shared pose. But
 * each queued draw DOES get its own fresh render state, filled synchronously at queue time
 * ({@code PlayerEntityRenderer.updateRenderState}) — so a tag written onto the state at queue time
 * survives to the flush, where {@code PlayerEntityModel.setAngles} receives that exact state and
 * can pose that exact draw independently. Set via {@link EmotePreviewTagger} around a
 * {@code InventoryScreen.drawEntity} call; null tag = normal live-playback path.
 */
public interface EmotePreviewState {

    void steampad$setPinnedPreview(EmoteData data, float tick);

    EmoteData steampad$pinnedPreviewData();

    float steampad$pinnedPreviewTick();
}
