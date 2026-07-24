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
}
