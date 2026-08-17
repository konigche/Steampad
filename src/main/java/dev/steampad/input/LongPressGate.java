package dev.steampad.input;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.ControllerConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Long-press ("mantener"): a button keeps its normal job on a short tap and fires a SECOND action when
 * held past the threshold.
 *
 * <p><b>The request.</b> "Que al lado de chords se pueda asignar el botón por mantener más tiempo" —
 * one button, two actions, chosen by how long you hold it.
 *
 * <h2>Why this is not a new mechanism</h2>
 * {@link GamepadInputDispatcher#bPressed} is already the single choke point every edge-triggered bind
 * passes through, and it <em>already</em> defers a tap to its release twice, for exactly this reason:
 * once for the Splitscreen chord on BACK, once for the generic chord-modifier gate. This gate is the
 * third client of that same pattern, so it composes with the other two instead of racing them: a button
 * that is both a chord modifier and a long-press host only fires its tap when BOTH gates report a clean
 * release — the same AND that {@code tickPauseLongPress} already uses.
 *
 * <h2>Where a long press is refused, and why</h2>
 * Every one of these is a gesture that already owns the hold on that button. {@link #blockedReason}
 * is the single source of truth: the editor greys the option out and says which one, and the dispatcher
 * re-checks it live, so enabling the item wheel later can never leave a stale long press fighting it.
 * <ul>
 *   <li><b>Held-type binds</b> ({@code Bind.held}) — ATTACK, USE, JUMP, SNEAK, SPRINT, RADIAL, ZOOM,
 *       EMOTE_WHEEL, PLAYER_LIST, THIRD_PERSON_ADJUST. Deferring these to release would break mining,
 *       eating, bow charging and every hold-to-open wheel. The enum already knows which they are.</li>
 *   <li><b>The PAUSE button</b> — its long press already shows every bind ({@code PAUSE_LONG_TICKS}).</li>
 *   <li><b>LB/RB while the item wheel is on</b> — their hold already opens it.</li>
 *   <li><b>Any chord modifier</b> — holding it is how a chord is completed; a long press there would
 *       fire on every chord you use.</li>
 *   <li><b>BACK while window arranging is on</b> — Select+RB already owns that hold.</li>
 *   <li><b>An entry that has its own chord</b> — the modifier hold would fight the action hold, which
 *       is the same reason a chorded PAUSE is excluded from its own long press.</li>
 * </ul>
 *
 * <h2>What cancels a pending hold</h2>
 * A hold in progress is abandoned — firing NEITHER action — when what it was for changes underneath it:
 * the binding stops being relevant here, or a screen opens or closes. Finishing the long action after
 * that would be a surprise; finishing the SHORT one would be worse, because the player was clearly
 * reaching for the long one. Suppressed buttons (every button held when a screen or wheel closes, see
 * {@code armHeldSuppression}) never accumulate at all.
 */
public final class LongPressGate {

    private LongPressGate() {}

    /** Ticks a button must be held for the long action to fire. Same 500 ms as the PAUSE long press —
     *  one long-press feel across the whole mod is worth more than a per-feature tuning knob. */
    public static final int LONG_TICKS = 10;

    /** The button currently accumulating a hold, or null. Only ever one: you cannot long-press two
     *  buttons into two different actions without one of them being a chord, which is excluded. */
    private static String holdingButton;
    private static int heldTicks;
    private static String holdKeybind;
    /** Set when the long action fired: the release must not also fire the short one. */
    private static boolean consumed;
    /** Buttons whose deferred TAP became due this tick (read by bPressed, cleared next tick). */
    private static final java.util.Map<String, Boolean> tapReleasedThisTick = new java.util.HashMap<>();

    /** Forgets any pending hold. Called on controller loss and on every screen transition. */
    public static void reset() {
        holdingButton = null;
        holdKeybind = null;
        heldTicks = 0;
        consumed = false;
        tapReleasedThisTick.clear();
    }

    /**
     * True while {@code buttonId} hosts a live long-press binding — the signal {@code bPressed} uses to
     * defer that button's ordinary bind to its release. False the instant the layer stops applying
     * (you put the weapon away), with no state to unwind.
     */
    public static boolean claims(String buttonId) {
        return buttonId != null && buttonId.equals(holdingButtonCandidate);
    }

    /** True on the tick a short tap completed on this button, i.e. released before the threshold. */
    public static boolean tapReleased(String buttonId) {
        return tapReleasedThisTick.getOrDefault(buttonId, false);
    }

    /** The button that currently hosts a long-press binding, recomputed once per tick by {@link #tick}. */
    private static String holdingButtonCandidate;

    /**
     * Per-tick drive. Runs before the bind dispatch so {@link #claims} and {@link #tapReleased} already
     * describe this tick when {@code bPressed} asks.
     *
     * @param relevantBinds the button → keybind map that is actually live here (already filtered by
     *                      {@link ModKeybindContext}), so a hold can never accumulate for a binding
     *                      that would not fire in this situation
     */
    public static void tick(Minecraft mc, ControllerConfig cfg, GamepadSnapshot cur,
                            java.util.Map<String, String> relevantBinds,
                            java.util.function.Predicate<String> suppressed,
                            java.util.function.Predicate<String> heldRaw) {
        tapReleasedThisTick.clear();

        // Which button, if any, hosts a live long-press binding right now.
        holdingButtonCandidate = null;
        String candidateKeybind = null;
        for (var e : relevantBinds.entrySet()) {
            String btn = e.getKey();
            String kb = e.getValue();
            if (btn == null || btn.isEmpty() || kb == null || kb.isBlank()) continue;
            if (!isHold(cfg, btn)) continue;
            if (blockedReason(cfg, btn, kb) != null) continue;   // live re-check, see class doc
            holdingButtonCandidate = btn;
            candidateKeybind = kb;
            break;
        }

        if (holdingButtonCandidate == null) { cancel(); return; }

        boolean down = heldRaw.test(holdingButtonCandidate) && !suppressed.test(holdingButtonCandidate);

        // The gesture is void if what it was for changed underneath it.
        if (holdingButton != null
                && (!holdingButton.equals(holdingButtonCandidate)
                    || !candidateKeybind.equals(holdKeybind))) {
            cancel();
        }

        if (down) {
            if (holdingButton == null) {
                holdingButton = holdingButtonCandidate;
                holdKeybind = candidateKeybind;
                heldTicks = 0;
                consumed = false;
            }
            heldTicks++;
            // Exactly AT the threshold, never past it: `>=` would re-fire every tick for the rest of
            // the hold. Same edge PAUSE's long press uses.
            if (heldTicks == LONG_TICKS && !consumed) {
                fire(mc, holdKeybind);
                consumed = true;
            }
            return;
        }

        // Released.
        if (holdingButton != null) {
            if (!consumed && heldTicks > 0 && heldTicks < LONG_TICKS) {
                tapReleasedThisTick.put(holdingButton, true);   // short tap: let the normal bind fire now
            }
            holdingButton = null;
            holdKeybind = null;
                heldTicks = 0;
            consumed = false;
        }
    }

    /** Drops a pending hold without firing anything (layer changed, or nothing hosts one any more). */
    private static void cancel() {
        holdingButton = null;
        holdKeybind = null;
        heldTicks = 0;
        consumed = false;
    }

    /**
     * Fires the long action as a PULSE, not a hold.
     *
     * <p>Deliberate: the button is by definition still held when this fires, so its release means "the
     * gesture ended", not "the action ended". {@link KeyTap#hold} would keep the mod's key down for as
     * long as the player kept holding — a stuck key, or a re-trigger, on the very action ("recargar")
     * the feature exists for. {@link KeyTap#press} holds for a couple of ticks, which satisfies both
     * {@code isDown()} and {@code consumeClick()} consumers.
     *
     * <p>No sound or rumble of its own: the action IS the confirmation (the reload starts, the backpack
     * sorts), and a UI chirp on every use would be noise in gameplay.
     */
    private static void fire(Minecraft mc, String keybindTranslation) {
        for (KeyMapping kb : mc.options.keyMappings) {
            if (kb.getName().equals(keybindTranslation)) {
                KeyTap.press(kb);
                return;
            }
        }
    }

    /** True if this button's action fires on a LONG PRESS instead of on the press edge. */
    public static boolean isHold(ControllerConfig cfg, String buttonId) {
        return buttonId != null && cfg.holdButtons.contains(buttonId);
    }

    /** Sets or clears long-press mode for a button. */
    public static void setHold(ControllerConfig cfg, String buttonId, boolean hold) {
        cfg.holdButtons.remove(buttonId);
        if (hold) cfg.holdButtons.add(buttonId);
    }

    /**
     * Why this button cannot host a long press, as a translation key — or null if it can.
     * See the class doc for the reasoning behind each. Used by the editor (to grey the option out and
     * say which gesture owns the hold) and by {@link #tick} (to re-check live).
     */
    public static String blockedReason(ControllerConfig cfg, String buttonId, String keybindId) {
        if (buttonId == null || buttonId.isEmpty()) return "steampad.hold.blocked.unbound";

        // A bind whose semantics are "held" cannot have its edge deferred — mining, eating, bow
        // charging and every hold-to-open wheel are exactly that. The enum already knows which.
        for (GamepadBinds.Bind b : GamepadBinds.Bind.values()) {
            if (!b.held) continue;
            if (buttonId.equals(GamepadBinds.button(cfg, b))) return "steampad.hold.blocked.held_bind";
        }
        if (buttonId.equals(GamepadBinds.button(cfg, GamepadBinds.Bind.PAUSE))) {
            return "steampad.hold.blocked.pause";
        }
        if (cfg.hotbarRadialMode != ControllerConfig.HotbarRadialMode.OFF
                && ("LB".equals(buttonId) || "RB".equals(buttonId))) {
            return "steampad.hold.blocked.item_wheel";
        }
        // Button test FIRST, option second: this runs every tick, and there is no reason to touch the
        // global config for the 20-odd buttons that are not BACK.
        if ("BACK".equals(buttonId) && ConfigManager.getGlobal().windowArrangeEnabled) {
            return "steampad.hold.blocked.window_arrange";
        }
        for (GamepadBinds.Bind b : GamepadBinds.Bind.values()) {
            if (buttonId.equals(GamepadBinds.chord(cfg, b))) return "steampad.hold.blocked.chord_mod";
        }
        for (String mod : cfg.extraChords.values()) {
            if (buttonId.equals(mod)) return "steampad.hold.blocked.chord_mod";
        }
        if (keybindId != null && !cfg.extraChords.getOrDefault(keybindId, "").isEmpty()) {
            return "steampad.hold.blocked.own_chord";
        }
        return null;
    }
}
