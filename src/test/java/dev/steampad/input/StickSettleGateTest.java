package dev.steampad.input;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The release behaviour of the virtual keyboard's stick pointer.
 *
 * <p>The scenario that matters is the reported one: push the stick, let go, and have the spring carry
 * it past centre into a real deflection the other way. Before the gate, that deflection moved the
 * pointer and the selection slid one key backwards.
 */
class StickSettleGateTest {

    private StickSettleGate gate;

    @BeforeEach
    void setUp() {
        gate = new StickSettleGate();
    }

    @Test
    @DisplayName("Nothing moves until the stick is actually pushed")
    void startsSettled() {
        assertTrue(gate.isSettling());
        assertFalse(gate.accept(0.0, 1000));
    }

    @Test
    @DisplayName("A normal push drives the pointer")
    void pushDrives() {
        assertTrue(gate.accept(0.8, 1000));
        assertTrue(gate.accept(0.6, 1010));
        assertFalse(gate.isSettling());
    }

    @Test
    @DisplayName("Spring-back after release does NOT move the pointer — the reported bug")
    void springBackIsIgnored() {
        assertTrue(gate.accept(0.9, 1000));       // pushed right
        assertFalse(gate.accept(0.10, 1040));     // let go, crossing centre
        // The spring carries it the other way: a genuine 0.30 deflection, for ~100 ms.
        assertFalse(gate.accept(0.22, 1060), "overshoot must not drive the pointer");
        assertFalse(gate.accept(0.30, 1080), "overshoot must not drive the pointer");
        assertFalse(gate.accept(0.19, 1110), "overshoot must not drive the pointer");
        assertFalse(gate.accept(0.05, 1150));
    }

    @Test
    @DisplayName("The selection is committed exactly once, on the release itself")
    void justSettledFiresOncePerRelease() {
        gate.accept(0.9, 1000);
        assertFalse(gate.consumeJustSettled(), "not settled while pushing");
        gate.accept(0.05, 1040);
        assertTrue(gate.consumeJustSettled(), "the release must commit the selection");
        gate.accept(0.05, 1050);
        assertFalse(gate.consumeJustSettled(), "and only once");
    }

    @Test
    @DisplayName("A deliberate flick right after a release is honoured immediately, not delayed")
    void hardPushBreaksTheLockout() {
        gate.accept(0.9, 1000);
        gate.accept(0.05, 1040);                       // release → lockout until 1220
        assertTrue(gate.accept(0.6, 1060), "a real flick must not have to wait out the lockout");
    }

    @Test
    @DisplayName("A gentle push is honoured once the spring has had time to settle")
    void gentlePushWorksAfterLockout() {
        gate.accept(0.9, 1000);
        gate.accept(0.05, 1040);                       // release → lockout until 1220
        assertFalse(gate.accept(0.25, 1100), "still inside the lockout");
        assertTrue(gate.accept(0.25, 1250), "a slow, deliberate nudge must still work");
    }

    @Test
    @DisplayName("Repeated pushes each get their own release commit")
    void repeatedPushes() {
        for (int i = 0; i < 3; i++) {
            long t = 1000 + i * 1000L;
            assertTrue(gate.accept(0.9, t));
            assertFalse(gate.accept(0.02, t + 40));
            assertTrue(gate.consumeJustSettled(), "release " + i + " must commit");
        }
    }

    @Test
    @DisplayName("reset() puts it back to the initial settled state")
    void resetSettles() {
        gate.accept(0.9, 1000);
        assertFalse(gate.isSettling());
        gate.reset();
        assertTrue(gate.isSettling());
        assertFalse(gate.consumeJustSettled());
    }
}
