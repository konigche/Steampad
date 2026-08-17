package dev.steampad.emote;

/**
 * Render-thread pass-through that carries a "this queued GUI entity draw is a preview" tag from a
 * screen to {@code PlayerEntityRendererMixin} — see {@link EmotePreviewState} for the full mechanism.
 * Two flavors, both "render thread only, no nesting":
 *
 * <pre>{@code
 * EmotePreviewTagger.begin(data, EmoteAnimator.representativeTick(data));   // FROZEN frame
 * InventoryScreen.drawEntity(...);   // updateRenderState runs synchronously here and picks the tag up
 * EmotePreviewTagger.end();
 *
 * EmotePreviewTagger.beginLive();   // LIVE (still reads EmoteAnimator's playback map for its pose)
 * InventoryScreen.drawEntity(...);
 * EmotePreviewTagger.end();
 * }</pre>
 *
 * {@code beginLive()} exists so a GUI-only preview (see {@code EmoteAnimator.Playback#guiPreviewOnly},
 * D111) can still be told apart, at pose time, from that SAME entity's normal world render — both
 * read the shared live playback map, but only the GUI-tagged one may act on a preview-only playback.
 *
 * <p>While no tag is active (the overwhelmingly common case — all world rendering, all untagged GUI
 * draws) the mixin writes null/false into the state, which also clears any stale tag on a REUSED
 * state object.
 */
public final class EmotePreviewTagger {

    private static EmoteData pendingData;
    private static float pendingTick;
    private static boolean guiPass;

    private EmotePreviewTagger() {}

    /** Pins the NEXT queued GUI entity draw to a FROZEN, non-advancing frame of {@code data}. */
    public static void begin(EmoteData data, float tick) {
        pendingData = data;
        pendingTick = tick;
        guiPass = true;
    }

    /** Marks the NEXT queued GUI entity draw as a GUI preview pass WITHOUT pinning a frame — it still
     *  reads the LIVE playback map for its pose (the wheel's selected chip / library's focused cell),
     *  but is tagged so {@code EmoteAnimator} knows this draw — not the entity's normal world render —
     *  may act on a GUI-preview-only playback (D111). */
    public static void beginLive() {
        pendingData = null;
        guiPass = true;
    }

    public static void end() {
        pendingData = null;
        guiPass = false;
    }

    public static EmoteData pendingData() {
        return pendingData;
    }

    public static float pendingTick() {
        return pendingTick;
    }

    public static boolean isGuiPass() {
        return guiPass;
    }
}
