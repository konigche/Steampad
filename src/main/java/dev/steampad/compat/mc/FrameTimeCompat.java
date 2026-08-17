package dev.steampad.compat.mc;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;

/**
 * Version shim for the frame-timing accessor on {@link Minecraft}, renamed between versions. The
 * {@link DeltaTracker} type it returns is identical on both (verified with {@code javap}: same
 * {@code getGameTimeDeltaPartialTick(boolean)} signature), so only the getter name needs gating:
 *
 * <ul>
 *   <li>1.21.1 — {@code Minecraft.getTimer()} (backing field {@code timer})</li>
 *   <li>1.21.10 — {@code Minecraft.getDeltaTracker()} (backing field {@code deltaTracker})</li>
 * </ul>
 *
 * <p>Needed wherever render code has to reconstruct the partial tick it wasn't handed as a parameter
 * — e.g. a screen's chrome helper that must call vanilla's own {@code renderBackground} (whose blur
 * and panorama both take the partial tick) without changing every screen's method signature.
 */
public final class FrameTimeCompat {

    private FrameTimeCompat() {}

    /**
     * Partial tick for rendering, i.e. what a {@code render(…, float delta)} parameter carries.
     *
     * <p>Uses the "not paused" reading (the same one the game passes to screen rendering), so a
     * paused game reports a frozen tick rather than one that keeps advancing.
     */
    public static float partialTick(Minecraft mc) {
        if (mc == null) return 0f;
        //? if >=1.21.2 {
        DeltaTracker tracker = mc.getDeltaTracker();
        //?} else {
        /*DeltaTracker tracker = mc.getTimer();*/
        //?}
        return tracker == null ? 0f : tracker.getGameTimeDeltaPartialTick(false);
    }
}
