package dev.steampad.input;

import dev.steampad.config.ControllerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The rules that decide WHERE a long press is allowed.
 *
 * <p>Every one of them exists because some other gesture already owns the hold on that button, and the
 * whole point of {@link LongPressGate#blockedReason} is that the editor and the dispatcher ask the same
 * question — so these tests are what stops the two from drifting apart. The timing half of the gate
 * (fires once, suppresses the tap, cancels on a layer change) needs a live snapshot and is covered in
 * hardware; what is pinned here is the part that is pure decision.
 */
class LongPressGateRulesTest {

    private static final String RELOAD = "key.tacz.reload";

    private ControllerConfig cfg;

    @BeforeEach
    void setUp() {
        cfg = new ControllerConfig();
        cfg.buttonBindings.clear();
        cfg.chordBindings.clear();
        cfg.extraBinds.clear();
        cfg.extraChords.clear();
        cfg.holdButtons.clear();
        cfg.hotbarRadialMode = ControllerConfig.HotbarRadialMode.OFF;
    }

    @Test
    @DisplayName("the reported case works: X is swap-hands (a tap bind), so it can host a long press")
    void swapHandsButtonAcceptsALongPress() {
        assertEquals("X", GamepadBinds.Bind.SWAP_HANDS.defaultButton);
        assertFalse(GamepadBinds.Bind.SWAP_HANDS.held, "swap hands is a tap, which is what makes this safe");
        assertNull(LongPressGate.blockedReason(cfg, "X", RELOAD));
    }

    @Test
    @DisplayName("no button carrying a HELD bind may host one — mining, eating and bow charging poll it")
    void heldBindsAreRefused() {
        for (GamepadBinds.Bind b : GamepadBinds.Bind.values()) {
            if (!b.held) continue;
            String btn = GamepadBinds.button(cfg, b);
            if (btn.isEmpty()) continue;   // unbound by default: nothing to protect
            assertNotNull(LongPressGate.blockedReason(cfg, btn, RELOAD),
                    b + " is a held-type bind on " + btn + " — deferring its edge would break it");
        }
    }

    @Test
    @DisplayName("ATTACK's button is refused by name, because this is the one that would ruin mining")
    void attackIsRefused() {
        String attack = GamepadBinds.button(cfg, GamepadBinds.Bind.ATTACK);
        assertEquals("steampad.hold.blocked.held_bind",
                LongPressGate.blockedReason(cfg, attack, RELOAD));
    }

    @Test
    @DisplayName("the PAUSE button is refused: its own long press already shows every binding")
    void pauseIsRefused() {
        String pause = GamepadBinds.button(cfg, GamepadBinds.Bind.PAUSE);
        assertEquals("steampad.hold.blocked.pause", LongPressGate.blockedReason(cfg, pause, RELOAD));
    }

    @Test
    @DisplayName("LB/RB are refused only while the item wheel owns their hold")
    void itemWheelRefusalIsConditional() {
        cfg.hotbarRadialMode = ControllerConfig.HotbarRadialMode.OFF;
        assertNull(LongPressGate.blockedReason(cfg, "LB", RELOAD), "wheel off: the hold is free");

        cfg.hotbarRadialMode = ControllerConfig.HotbarRadialMode.HOLD_TO_OPEN;
        assertEquals("steampad.hold.blocked.item_wheel",
                LongPressGate.blockedReason(cfg, "LB", RELOAD));
        assertEquals("steampad.hold.blocked.item_wheel",
                LongPressGate.blockedReason(cfg, "RB", RELOAD));
    }

    @Test
    @DisplayName("a chord modifier is refused: holding it is how every chord on it is completed")
    void chordModifierIsRefused() {
        cfg.chordBindings.put(GamepadBinds.Bind.CHAT.name(), "DLEFT");
        assertEquals("steampad.hold.blocked.chord_mod",
                LongPressGate.blockedReason(cfg, "DLEFT", RELOAD));
    }

    @Test
    @DisplayName("a held bind outranks the chord reason — the more fundamental refusal is shown")
    void heldBindReasonWinsOverChord() {
        // DRIGHT is RADIAL's default button (a held bind) AND made a chord modifier here. Both refuse
        // it; the message names the one the player would hit first.
        cfg.chordBindings.put(GamepadBinds.Bind.CHAT.name(), "DRIGHT");
        assertEquals("steampad.hold.blocked.held_bind",
                LongPressGate.blockedReason(cfg, "DRIGHT", RELOAD));
    }

    @Test
    @DisplayName("a MOD chord's modifier is refused too, not just a Bind's")
    void extraChordModifierIsRefused() {
        cfg.extraChords.put("key.some.mod", "M1");
        assertEquals("steampad.hold.blocked.chord_mod",
                LongPressGate.blockedReason(cfg, "M1", RELOAD));
    }

    @Test
    @DisplayName("an entry that already has its own chord is refused — the two holds would fight")
    void ownChordIsRefused() {
        cfg.extraBinds.put("M2", RELOAD);
        cfg.extraChords.put(RELOAD, "LB");
        assertEquals("steampad.hold.blocked.own_chord",
                LongPressGate.blockedReason(cfg, "M2", RELOAD));
    }

    @Test
    @DisplayName("an unassigned button is refused, with its own reason rather than a silent no")
    void unboundIsRefused() {
        assertEquals("steampad.hold.blocked.unbound", LongPressGate.blockedReason(cfg, "", RELOAD));
    }

    @Test
    @DisplayName("checking a reason never writes to the config — the dispatcher asks every tick")
    void blockedReasonDoesNotMutateConfig() {
        LongPressGate.blockedReason(cfg, "M3", RELOAD);
        LongPressGate.blockedReason(cfg, "M3", RELOAD);
        assertTrue(cfg.extraChords.isEmpty());
        assertTrue(cfg.extraBinds.isEmpty());
    }

    @Test
    @DisplayName("the threshold matches the mod's existing long press, so the feel is one feel")
    void thresholdMatchesPause() {
        assertEquals(10, LongPressGate.LONG_TICKS, "500 ms, same as the PAUSE long press");
    }
}
