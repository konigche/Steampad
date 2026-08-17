package dev.steampad.input;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link ZoomController#stabilize} in isolation — the bug it exists to catch (reported:
 * "cuando se mueve se detiene muy de golpe") was never in this math, it was in a caller taking an
 * early-return path that stopped feeding it input. This pins the math itself: fed zero input, a
 * non-empty carry buffer must keep draining, not freeze.
 */
class ZoomControllerStabilizerTest {

    @AfterEach
    void reset() {
        ZoomController.resetStabilizer();   // tests must not leak carry into each other
    }

    @Test
    void aSuddenStopStillDrainsGraduallyInsteadOfFreezing() {
        // One big tick of input, as if the player panned hard then let go of the stick instantly.
        double[] first = ZoomController.stabilize(30.0, 0.0, 1.0 / 60.0);
        assertTrue(first[0] > 0.0 && first[0] < 30.0, "first tick releases only part of a sudden input");

        // Every following tick has ZERO new input (stick at rest) — the old code path never even
        // reached stabilize() here, so this exact call sequence is what the bug skipped entirely.
        double[] second = ZoomController.stabilize(0.0, 0.0, 1.0 / 60.0);
        assertTrue(second[0] > 0.0, "leftover motion must keep draining on zero-input ticks, not freeze");
        assertTrue(second[0] < first[0], "the drained amount decays tick over tick");
    }

    @Test
    void eventuallyFullyDrainsToExactZero() {
        // The decay is geometric (~93% of the remainder kept per tick at this halflife/tick-rate), so
        // it takes a real double all the way down through normal range into subnormals before a
        // multiply finally underflows to bit-exact 0.0 — roughly 9800 ticks for this starting
        // magnitude, confirmed empirically. 50,000 leaves ample margin without masking a real stall.
        ZoomController.stabilize(30.0, 10.0, 1.0 / 60.0);
        double[] last = {1, 1};
        for (int i = 0; i < 50_000 && (last[0] != 0.0 || last[1] != 0.0); i++) {
            last = ZoomController.stabilize(0.0, 0.0, 1.0 / 60.0);
        }
        assertEquals(0.0, last[0], "buffer must reach exactly zero, not just asymptotically small");
        assertEquals(0.0, last[1]);
    }

    @Test
    void aPanEndsExactlyWhereRequestedNothingIsLost() {
        double totalYawRequested = 0.0;
        double totalYawReleased = 0.0;
        for (int i = 0; i < 30; i++) {
            double yawThisTick = 5.0;   // steady steering for 30 ticks
            totalYawRequested += yawThisTick;
            totalYawReleased += ZoomController.stabilize(yawThisTick, 0.0, 1.0 / 60.0)[0];
        }
        // Drain whatever the buffer still holds after steering stops.
        for (int i = 0; i < 10_000; i++) {
            double[] r = ZoomController.stabilize(0.0, 0.0, 1.0 / 60.0);
            totalYawReleased += r[0];
            if (r[0] == 0.0) break;
        }
        assertEquals(totalYawRequested, totalYawReleased, 1e-6,
                "smoothing must redistribute input over time, never lose or invent any of it");
    }

    @Test
    void resetDropsCarryInsteadOfReplayingItIntoTheNextZoom() {
        ZoomController.stabilize(50.0, 50.0, 1.0 / 60.0);   // leaves a large carry mid-drain
        ZoomController.resetStabilizer();
        double[] afterReset = ZoomController.stabilize(0.0, 0.0, 1.0 / 60.0);
        assertEquals(0.0, afterReset[0], "old carry must not leak into a later cinematic session");
        assertEquals(0.0, afterReset[1]);
    }
}
