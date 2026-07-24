package dev.steampad.emote;

/**
 * Render-thread pass-through that carries a "pose the NEXT queued GUI entity draw as a frozen frame
 * of this emote" tag from a screen to {@code PlayerEntityRendererMixin} — see
 * {@link EmotePreviewState} for the full mechanism. Usage (render thread only, no nesting):
 *
 * <pre>{@code
 * EmotePreviewTagger.begin(data, EmoteAnimator.representativeTick(data));
 * InventoryScreen.drawEntity(...);   // updateRenderState runs synchronously here and picks the tag up
 * EmotePreviewTagger.end();
 * }</pre>
 *
 * While no tag is active (the overwhelmingly common case — all world rendering, all untagged GUI
 * draws) the mixin writes null into the state, which also clears any stale tag on a REUSED state
 * object.
 */
public final class EmotePreviewTagger {

    private static EmoteData pendingData;
    private static float pendingTick;

    private EmotePreviewTagger() {}

    public static void begin(EmoteData data, float tick) {
        pendingData = data;
        pendingTick = tick;
    }

    public static void end() {
        pendingData = null;
    }

    public static EmoteData pendingData() {
        return pendingData;
    }

    public static float pendingTick() {
        return pendingTick;
    }
}
