package dev.steampad.radial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises {@link RadialRenderer#childSpawnProgress} — the pure stagger math behind the Item
 *  Radial's bloom ring "nacen" entrance (feedback round 4: children should cascade in rather than
 *  pop together, and the SAME shared progress un-staggers them in reverse for the collapse). Values
 *  cross-checked against a standalone reference script before writing assertions (float
 *  multiplication by 0.05 isn't exact — see the class's own {@code span} comment). */
class RadialRendererLogicTest {

    @Test
    void singleChildAlwaysTracksRingProgressDirectly() {
        assertEquals(0f, RadialRenderer.childSpawnProgress(0f, 0, 1));
        assertEquals(0.5f, RadialRenderer.childSpawnProgress(0.5f, 0, 1));
        assertEquals(1f, RadialRenderer.childSpawnProgress(1f, 0, 1));
    }

    @Test
    void firstChildOfAnyRingTracksRingProgressDirectly() {
        // index 0's delay is always exactly 0 regardless of count, so it never lags.
        assertEquals(0.4f, RadialRenderer.childSpawnProgress(0.4f, 0, 5));
        assertEquals(0.73f, RadialRenderer.childSpawnProgress(0.73f, 0, 5));
    }

    @Test
    void fullyOpenRingSpawnsEveryChildRegardlessOfCount() {
        for (int i = 0; i < 5; i++) {
            assertEquals(1f, RadialRenderer.childSpawnProgress(1f, i, 5),
                    "child " + i + " of 5 should be fully spawned once the ring is fully open");
        }
        assertEquals(1f, RadialRenderer.childSpawnProgress(1f, 11, 12));
    }

    @Test
    void fullyClosedRingSpawnsNoChildRegardlessOfCount() {
        for (int i = 0; i < 5; i++) {
            assertEquals(0f, RadialRenderer.childSpawnProgress(0f, i, 5),
                    "child " + i + " of 5 should not have spawned yet at ring progress 0");
        }
    }

    @Test
    void laterIndicesLagBehindEarlierOnesMidBloom() {
        // The cascade: at the same ring progress, an earlier index is always at least as far along
        // as a later one — this is what makes the ring visibly "sweep" open instead of popping.
        float first = RadialRenderer.childSpawnProgress(0.3f, 0, 6);
        float last = RadialRenderer.childSpawnProgress(0.3f, 5, 6);
        assertEquals(0.3f, first);
        assertEquals(0f, last);
        assertTrue(first > last, "an earlier child must not lag behind a later one");
    }
}
