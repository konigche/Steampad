package dev.steampad.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/**
 * Drives vanilla's own sprint keybind so a controller can sprint on Minecraft versions that have no
 * sprint flag in the player's movement input.
 *
 * <p><b>Why this exists.</b> From 1.21.2 the player's input is a record with a {@code sprint()} bit,
 * which {@code KeyboardInputMixin} simply ORs the controller's request into — that is the channel
 * vanilla reads, so nothing else is needed. On 1.21.1 the movement object has no sprint field at all
 * ({@code up}, {@code down}, {@code left}, {@code right}, {@code jumping}, {@code shiftKeyDown} and
 * nothing more): vanilla decides sprinting by polling {@code Options.keySprint}. Emulating that
 * keybind is therefore not a workaround, it is the native channel for that version — the same
 * approach Controlify takes (its sprint bind is declared with {@code keyEmulation(options.keySprint)}).
 *
 * <p><b>Why the keybind and not {@code LocalPlayer.setSprinting}.</b> Forcing the sprint <em>state</em>
 * fights vanilla's own logic, which re-evaluates it every tick against hunger, collisions, being in
 * water and so on — the state would flicker back off. Going through the keybind means every vanilla
 * rule, plus the FOV change, the sprint-jump behaviour and the player's own "toggle sprint" option,
 * all keep working exactly as they do for a keyboard.
 *
 * <p><b>Edge-triggered on purpose.</b> {@code KeyTap.hold}/{@code release} write the global pressed
 * state of the key, so releasing it every tick would cancel a sprint the player is holding
 * <em>physically</em> on the keyboard. This only touches the key when the controller's request
 * actually changes, so a physical hold is left alone unless the controller itself releases.
 *
 * <p>Sprint mode (hold vs toggle) is already resolved upstream in {@code GamepadInputDispatcher}
 * before it reaches {@code ControllerInputState}, so this class deliberately knows nothing about it.
 */
public final class SprintKeyEmulator {

    private static boolean held = false;
    private static InputConstants.Key heldKey = null;

    /**
     * Diagnostics for the sprint pipeline — INFO level (see {@link dev.steampad.util.LogUtil#debug},
     * DEBUG is dropped by Minecraft's shipped log4j config), logged ONLY on a state change, never per
     * tick. Three rounds were spent on sprint without a single line of evidence in any log about what
     * this class was actually doing; every one of those rounds ended in a plausible-but-unproven story.
     * These few lines make the next report answer it outright: did the pad ask, did we press, and did
     * the key survive until vanilla looked at it.
     */
    private static boolean lastLoggedWant = false;
    private static int repairCount = 0;

    private SprintKeyEmulator() {}

    /**
     * Applies the controller's current sprint request. Call every tick, including on paths that bail
     * out early (menu open, no player) with {@code false} — otherwise a held sprint leaks and the
     * player sprints forever. {@code ControllerInputState.clear()} already zeroes the request when a
     * menu opens, so the natural flow releases on its own.
     */
    public static void apply(Minecraft mc, boolean wantSprint) {
        if (mc == null || mc.options == null || mc.options.keySprint == null) return;
        net.minecraft.client.KeyMapping kb = mc.options.keySprint;
        if (wantSprint != lastLoggedWant) {
            lastLoggedWant = wantSprint;
            dev.steampad.util.LogUtil.debug(
                    "[sprint] pad request {} -> {} | keyDown(before)={} boundTo='{}' repairsSinceLast={}",
                    wantSprint ? "OFF" : "ON", wantSprint ? "ON" : "OFF",
                    kb.isDown(), kb.saveString(), repairCount);
            repairCount = 0;
        }
        if (wantSprint) {
            if (!held) {
                heldKey = KeyTap.hold(kb);
                held = true;
                dev.steampad.util.LogUtil.debug("[sprint] pressed vanilla sprint key; keyDown(after)={}",
                        kb.isDown());
            } else if (!kb.isDown()) {
                if (repairCount == 0) {
                    // Only the FIRST repair of a run is logged: if something clears the key every tick
                    // this would otherwise spam 20 lines/second. The count is reported on the next
                    // state change above, which is the number that actually diagnoses it — a handful
                    // means the odd screen open, thousands means something is fighting us every tick.
                    dev.steampad.util.LogUtil.debug(
                            "[sprint] vanilla sprint key was cleared by something else while still "
                                    + "requested — repairing (further repairs in this run are counted, not logged)");
                }
                repairCount++;
                // Repair (feedback: sprint on L3 in TOGGLE mode died silently and only came back by
                // opening and closing the radial wheel). Proven with javap on the real 1.21.1 jar:
                // Minecraft.setScreen(screen) calls KeyMapping.releaseAll() whenever the incoming
                // screen is non-null, and releaseAll() zeroes isDown on EVERY mapping in the game —
                // including this one, which we are deliberately holding for the pad. Nothing tells us
                // it happened.
                //
                // Purely edge-triggered, this class could never recover from that: in TOGGLE mode
                // wantSprint stays true across the whole screen visit, so the old `wantSprint == held`
                // early-return fired forever and the key was never pressed again — sprint was dead
                // until the player toggled the bind off and on. Worse, in single player the game is
                // PAUSED while a screen is open, so KeyboardInput.tick (and with it this method) never
                // runs to notice; the bail-out with `false` that the doc above describes only covers
                // the multiplayer case where ticking continues.
                //
                // ⚠️ HONEST STATUS: this repair is necessary and correct, but it is NOT yet proven to
                // be the whole story. The user re-reproduced the radial-wheel "workaround" WITH this
                // repair already shipped, which rules out the tidy explanation written here first time
                // round (the wheel is an overlay, not a Screen, so it keeps ticking and forces sprint
                // false while open — walking this state machine through true→false→true and re-pressing
                // the key). If that were the entire mechanism, the repair above would have made the
                // workaround unnecessary, and it did not. That is exactly why the logging in this
                // method exists: three rounds produced three plausible stories and zero evidence.
                // Do not write a fourth story — read the [sprint] lines in the next log first.
                //
                // Guarded on !isDown() rather than re-asserting every tick because keySprint is a
                // ToggleKeyMapping: with the game's own "Toggle Sprint" option on, its setDown(true)
                // FLIPS instead of sets (javap-confirmed), so an unconditional re-assert would strobe
                // the key 20×/second. Checking first is correct under both semantics.
                KeyTap.reassert(kb, heldKey);
            }
        } else if (held) {
            // Release stays strictly edge-triggered — see the class doc: writing `false` every tick
            // would cancel a sprint the player is holding PHYSICALLY on the keyboard. (Known, untouched
            // vanilla interaction: if "Toggle Sprint" is on, ToggleKeyMapping.setDown(false) is a no-op
            // by design, so this release relies on the flip already having happened. Left alone rather
            // than "fixed" speculatively — probing isDown() here to force it off would cancel exactly
            // the physical hold this edge-triggering exists to protect.)
            KeyTap.release(kb, heldKey);
            heldKey = null;
            held = false;
        }
    }

    /** Force-releases the key regardless of state — for disconnect/teardown. */
    public static void reset(Minecraft mc) {
        if (!held) return;
        if (mc != null && mc.options != null) KeyTap.release(mc.options.keySprint, heldKey);
        heldKey = null;
        held = false;
    }
}
