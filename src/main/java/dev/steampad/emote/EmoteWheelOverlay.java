package dev.steampad.emote;

import dev.steampad.radial.RadialActionType;
import dev.steampad.radial.RadialRenderer;
import dev.steampad.radial.RadialSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * Renders the dedicated emote wheel overlay — the sibling of {@link dev.steampad.radial.RadialMenuOverlay}
 * for the fully independent {@link EmoteWheelController} (FASE 63 decoupling). Reuses
 * {@link RadialRenderer} as-is for the donut/blob/carousel/hint chrome, plus a per-slot thumbnail
 * hook (FASE 73/77):
 * <ul>
 *   <li><b>Every</b> non-empty emote chip shows the local player frozen at a representative frame
 *       of THAT chip's dance — a still photo per chip. Possible since D099's duck-tagged render
 *       states: each queued GUI entity draw carries its own frozen-frame tag, so 1.21.10's deferred
 *       flush (D092 — the reason the first attempt showed every chip animating the same pose) no
 *       longer collapses them into one shared pose.</li>
 *   <li>The SELECTED chip is drawn BIGGER and plays its dance live in real time (untagged draw →
 *       the normal playback-map path), freezing back to a still the instant selection moves.</li>
 * </ul>
 *
 * <p><b>Preview session (FASE 77):</b> while the wheel is open, whatever REAL playback the player
 * had running (e.g. a looping dance) is snapshotted and parked, so previews always work — before
 * this, an active looping emote made {@code conflictsWithRealEmote} permanently true and the wheel
 * previewed nothing (the user's exact report, with club_penguin_dance looping in their log). On
 * close the snapshot is restored — unless the user confirmed a NEW emote from the wheel, which
 * rightfully replaces it (generation-token check, preserving the D093 fix).
 */
public final class EmoteWheelOverlay {

    /** Selected chip: entity render box/scale as a fraction of the chip radius — deliberately
     *  larger than the chip itself ("Agrega un tamaño más grande para el que está en foco"; raised
     *  raised again in v0.60.0/D100 ("hazlo aun más grande para que destaque más"), once more in D101
     *  by another explicit +30% ("haz un 30% más grande cuando esta en foco en la rueda"), and once
     *  more this round (same request, "amplíalo un poco más"). */
    private static final float SELECTED_BOX_PER_CHIP_RADIUS = 2.45f;    // 1.885 * 1.30
    private static final float SELECTED_SCALE_PER_CHIP_RADIUS = 1.86f;   // 1.43 * 1.30
    /** Non-selected chips: the frozen-frame thumbnails stay inside their chip (deliberately kept
     *  under 1.0 — unlike the selected chip above, these must NOT spill past the chip's own circle;
     *  raised a bit, D111, "haz un poquito más grande la imagen de previo fija... sin que se salga"). */
    private static final float ENTITY_SCALE_PER_CHIP_RADIUS = 0.65f;    // 0.55 * ~1.18
    private static final float ENTITY_BOX_PER_CHIP_RADIUS = 0.85f;      // 0.72 * ~1.18

    private static String animatedPreviewId;
    /** Generation token of the playback WE started (see {@code EmoteAnimator#currentGeneration}) —
     *  the single source of truth for "is a preview WE own currently active". Replaces an earlier
     *  plain boolean that couldn't tell our preview apart from a real emote that took over the same
     *  entity id in the meantime (see {@link #stopPreview()}). */
    private static long previewGeneration = -1L;
    /** Parked REAL playback (see class doc) + whether a park/restore session is open. */
    private static Object realPlaybackSnapshot;
    private static boolean sessionActive;

    private static boolean ownsCurrentPreview(Minecraft mc) {
        return mc.player != null && previewGeneration != -1L
                && EmoteAnimator.currentGeneration(mc.player.getId()) == previewGeneration;
    }

    private EmoteWheelOverlay() {}

    public static void render(GuiGraphics context, float tickDelta) {
        if (!EmoteWheelController.isOpen()) {
            stopPreview();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int selectedIdx = EmoteWheelController.getSelectedSlot();
        var slots = EmoteWheelController.getSlots();
        long handle = EmoteWheelController.getActiveHandle();
        int wheelCount = EmoteWheelController.wheelCount();
        int page = EmoteWheelController.getPage();

        if (mc.player == null) {
            // No body to pose (shouldn't normally happen — the wheel only opens in a loaded world —
            // but fail safe): fall back to the plain flat-icon wheel, same as the regular radial menu.
            RadialRenderer.render(context, screenW / 2, screenH / 2, slots, selectedIdx, handle, wheelCount, page);
            return;
        }

        // Park any real playback for the duration of the wheel session so previews always work.
        if (!sessionActive) {
            sessionActive = true;
            realPlaybackSnapshot = EmoteAnimator.snapshotPlayback(mc.player.getId());
            if (realPlaybackSnapshot != null) EmoteAnimator.restorePlayback(mc.player.getId(), null);
        }

        String selectedEmoteId = null;
        if (selectedIdx >= 0 && selectedIdx < slots.size()) {
            RadialSlot sel = slots.get(selectedIdx);
            if (!sel.isEmpty()) selectedEmoteId = sel.actionValue;
        }
        EmoteData selectedData = selectedEmoteId != null ? EmoteLibrary.byId(selectedEmoteId) : null;
        updateAnimatedPreview(mc, selectedEmoteId, selectedData);

        boolean conflictsWithRealEmote = EmoteAnimator.isLocalPlaying() && !ownsCurrentPreview(mc);

        RadialRenderer.render(context, screenW / 2, screenH / 2, slots, selectedIdx, handle, wheelCount, page,
                (ctx, i, slot, sx, sy, chipR, isSelected) -> {
                    if (slot.type != RadialActionType.EMOTE || slot.isEmpty()) return false;
                    EmoteData data = EmoteLibrary.byId(slot.actionValue);
                    if (data == null) return false;

                    boolean live = isSelected && !conflictsWithRealEmote
                            && slot.actionValue.equals(animatedPreviewId);
                    float boxFrac = isSelected ? SELECTED_BOX_PER_CHIP_RADIUS : ENTITY_BOX_PER_CHIP_RADIUS;
                    float scaleFrac = isSelected ? SELECTED_SCALE_PER_CHIP_RADIUS : ENTITY_SCALE_PER_CHIP_RADIUS;
                    int half = Math.max(6, Math.round(chipR * boxFrac));
                    int scale = Math.max(4, Math.round(chipR * scaleFrac));

                    if (live) {
                        // Tagged as a GUI preview pass (D111, no pinned frame) → still poses from the
                        // live playback map at flush (real animation), but the tag is what lets this
                        // exact draw — never the player's normal WORLD render — act on a
                        // GUI-preview-only playback (see EmoteAnimator.playForGuiPreview).
                        EmotePreviewTagger.beginLive();
                        try {
                            InventoryScreen.renderEntityInInventoryFollowsMouse(ctx, sx - half, sy - half, sx + half, sy + half,
                                    scale, 0f, 0f, tickDelta, mc.player);
                        } finally {
                            EmotePreviewTagger.end();
                        }
                    } else {
                        // Duck-tagged draw → this exact queued draw renders frozen at a
                        // representative frame of THIS chip's dance, independent of every other chip.
                        EmotePreviewTagger.begin(data, EmoteAnimator.representativeTick(data));
                        try {
                            InventoryScreen.renderEntityInInventoryFollowsMouse(ctx, sx - half, sy - half, sx + half, sy + half,
                                    scale, 0f, 0f, tickDelta, mc.player);
                        } finally {
                            EmotePreviewTagger.end();
                        }
                    }
                    return true;
                });
    }

    /** Starts/switches the animated preview to match the SELECTED slot — and RESTARTS it when a
     *  non-loop preview finished and was pruned (the prune left {@code animatedPreviewId} set with
     *  no playback behind it, so the chip silently froze forever — FASE 77 fix). Never fights a
     *  real emote something else started (generation mismatch → back off). */
    private static void updateAnimatedPreview(Minecraft mc, String emoteId, EmoteData data) {
        if (mc.player == null || emoteId == null) return;
        if (animatedPreviewId != null && previewGeneration != -1L
                && EmoteAnimator.currentGeneration(mc.player.getId()) == -1L) {
            animatedPreviewId = null;   // our non-loop preview ended and was pruned — restart below
            previewGeneration = -1L;
        }
        boolean conflictsWithRealEmote = EmoteAnimator.isLocalPlaying() && !ownsCurrentPreview(mc);
        if (conflictsWithRealEmote) return;
        if (!emoteId.equals(animatedPreviewId)) {
            if (data == null) return;
            EmoteAnimator.playForGuiPreview(mc.player.getId(), data);
            animatedPreviewId = emoteId;
            previewGeneration = EmoteAnimator.currentGeneration(mc.player.getId());
        }
    }

    /** Ends the preview session the moment the wheel closes — restores whatever REAL playback was
     *  parked at open (so a looping dance resumes exactly as it was), or eases our preview out if
     *  nothing was parked.
     *
     * <p>Preserved fix (D093, "hace la transición de cámara pero enseguida regresa"): confirming a
     * wheel selection calls {@code EmoteAnimator.playLocal} for the SAME entity id and closes the
     * wheel in the same action. If our generation token is NOT the active one, something else (that
     * real emote) now owns the slot — leave it completely alone and drop the parked snapshot (the
     * user's new choice replaces the old playback). */
    private static void stopPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (sessionActive && mc.player != null) {
            int id = mc.player.getId();
            long current = EmoteAnimator.currentGeneration(id);
            boolean somethingElseOwns = current != -1L && previewGeneration != current;
            if (!somethingElseOwns) {
                if (realPlaybackSnapshot != null) {
                    EmoteAnimator.restorePlayback(id, realPlaybackSnapshot);
                } else if (ownsCurrentPreview(mc)) {
                    EmoteAnimator.requestStop(id);
                }
            }
        }
        sessionActive = false;
        realPlaybackSnapshot = null;
        animatedPreviewId = null;
        previewGeneration = -1L;
    }
}
