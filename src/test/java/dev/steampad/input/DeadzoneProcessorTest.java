package dev.steampad.input;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeadzoneProcessorTest {

    private static final float EPSILON = 0.001f;

    @Test
    void testDeadzoneMapsZeroWithinThreshold() {
        float[] result = DeadzoneProcessor.process(0.05f, 0.05f, 0.1f);
        assertEquals(0f, result[0], EPSILON, "X should be 0 within deadzone");
        assertEquals(0f, result[1], EPSILON, "Y should be 0 within deadzone");
    }

    @Test
    void testFullDeflectionPassesThrough() {
        float[] result = DeadzoneProcessor.process(1.0f, 0.0f, 0.1f);
        assertEquals(1.0f, result[0], EPSILON, "Full X deflection should pass through");
        assertEquals(0.0f, result[1], EPSILON, "Y should remain 0");
    }

    @Test
    void testCircularDeadzoneIsCircular() {
        // magnitude = sqrt(0.07^2 + 0.07^2) ≈ 0.099 < 0.1 deadzone
        float[] result = DeadzoneProcessor.process(0.07f, 0.07f, 0.1f);
        assertEquals(0f, result[0], EPSILON, "X should be 0 within circular deadzone");
        assertEquals(0f, result[1], EPSILON, "Y should be 0 within circular deadzone");
    }

    @Test
    void testOutsideDeadzoneHasValue() {
        // magnitude = sqrt(0.08^2 + 0.08^2) ≈ 0.113 > 0.1 deadzone
        float[] result = DeadzoneProcessor.process(0.08f, 0.08f, 0.1f);
        assertTrue(result[0] > 0f, "X should be positive outside deadzone");
        assertTrue(result[1] > 0f, "Y should be positive outside deadzone");
    }

    @Test
    void testNegativeAxis() {
        float[] result = DeadzoneProcessor.process(-0.5f, 0.0f, 0.1f);
        assertTrue(result[0] < 0f, "Negative X should remain negative after deadzone");
        assertEquals(0f, result[1], EPSILON, "Y should remain 0");
    }

    @Test
    void testScalingIsNormalized() {
        float[] result = DeadzoneProcessor.process(1.0f, 0.0f, 0.1f);
        assertTrue(result[0] <= 1.0f, "Result should not exceed 1.0");
        assertTrue(result[0] >= -1.0f, "Result should not be below -1.0");
    }

    @Test
    void testZeroInputIsZeroOutput() {
        float[] result = DeadzoneProcessor.process(0f, 0f, 0.15f);
        assertEquals(0f, result[0], EPSILON);
        assertEquals(0f, result[1], EPSILON);
    }

    @Test
    void testThresholdReturnsBinary() {
        assertEquals(0f, DeadzoneProcessor.applyThreshold(0.3f, 0.5f));
        assertEquals(1f, DeadzoneProcessor.applyThreshold(0.6f, 0.5f));
        assertEquals(1f, DeadzoneProcessor.applyThreshold(0.5f, 0.5f));
    }

    // ---- Axial deadzone (mounted lateral shaping) ------------------------------------------------

    @Test
    void axialDeadzoneMakesOffAxisPushEXACTLYZero() {
        // THE bug, straight from the user's debug dump while mounted: movement input (-0.01, 1.00).
        // A vehicle that turns proportionally to the lateral impulse integrates ANY residue into a
        // permanent curve, so "very small" is not good enough — it has to be exactly 0f, the same
        // value a keyboard produces when only W is held.
        assertEquals(0f, DeadzoneProcessor.applyAxialDeadzone(0.01f, 0.25f), 0f);
        assertEquals(0f, DeadzoneProcessor.applyAxialDeadzone(-0.01f, 0.25f), 0f);
        assertEquals(0f, DeadzoneProcessor.applyAxialDeadzone(0.158f, 0.25f), 0f);
    }

    @Test
    void axialDeadzoneKeepsFullSteeringLock() {
        // Stability must not cost steering authority: full deflection still means full turn.
        assertEquals(1.0f, DeadzoneProcessor.applyAxialDeadzone(1.0f, 0.25f), EPSILON);
        assertEquals(-1.0f, DeadzoneProcessor.applyAxialDeadzone(-1.0f, 0.25f), EPSILON);
    }

    @Test
    void axialDeadzoneStaysContinuousAtTheEdge() {
        // Just past the band the output must start from ~0, not jump — otherwise steering would snap
        // on rather than ease in, which is exactly what a stick should not do.
        float justInside = DeadzoneProcessor.applyAxialDeadzone(0.249f, 0.25f);
        float justOutside = DeadzoneProcessor.applyAxialDeadzone(0.251f, 0.25f);
        assertEquals(0f, justInside, 0f);
        assertTrue(justOutside < 0.01f, "should ease in from zero, was " + justOutside);
        assertTrue(justOutside > 0f, "should be moving once past the band");
    }

    @Test
    void axialDeadzonePreservesSign() {
        assertTrue(DeadzoneProcessor.applyAxialDeadzone(-0.6f, 0.25f) < 0f, "left must stay left");
        assertTrue(DeadzoneProcessor.applyAxialDeadzone(0.6f, 0.25f) > 0f, "right must stay right");
    }

    @Test
    void axialDeadzoneIsMonotonic() {
        float prev = -1f;
        for (int i = 0; i <= 20; i++) {
            float shaped = DeadzoneProcessor.applyAxialDeadzone(i / 20f, 0.25f);
            assertTrue(shaped >= prev, "must never decrease as input grows (at " + (i / 20f) + ")");
            prev = shaped;
        }
    }

    @Test
    void axialDeadzoneOfZeroIsOff() {
        assertEquals(0.37f, DeadzoneProcessor.applyAxialDeadzone(0.37f, 0f), EPSILON);
        assertEquals(-0.37f, DeadzoneProcessor.applyAxialDeadzone(-0.37f, 0f), EPSILON);
    }

    @Test
    void axialDeadzoneStillAllowsTurningWhileDrivingForward() {
        // The user's own keyboard analogy: W alone goes straight, W+D turns. A deliberate lateral push
        // past the band must survive, or the fix for "goes straight" would break "can still turn".
        assertTrue(DeadzoneProcessor.applyAxialDeadzone(0.5f, 0.25f) > 0.3f,
                "a deliberate half push must still steer meaningfully");
    }
}
