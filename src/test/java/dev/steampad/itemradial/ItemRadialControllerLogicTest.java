package dev.steampad.itemradial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercises {@link ItemRadialController#slotForAnalog} — the pure angle-to-slot math behind the
 *  Item Radial's sticky selection (see class doc: holding LB/RB and letting the stick recenter must
 *  NOT lose focus, since the plan explicitly requires pressing A while the stick rests at center). */
class ItemRadialControllerLogicTest {

    @Test
    void belowThresholdMagnitudeReturnsSticky() {
        assertEquals(-1, ItemRadialController.slotForAnalog(0.1f, 0.1f, 5));
    }

    @Test
    void centeredStickReturnsSticky() {
        assertEquals(-1, ItemRadialController.slotForAnalog(0f, 0f, 5));
    }

    @Test
    void straightUpIsSlotZero() {
        assertEquals(0, ItemRadialController.slotForAnalog(0f, -1f, 5));
    }

    @Test
    void straightDownIsOppositeSlotForFourCategories() {
        // 4 slots at 0/90/180/270° — straight down (180°) lands cleanly on index 2, opposite the top.
        assertEquals(2, ItemRadialController.slotForAnalog(0f, 1f, 4));
    }

    @Test
    void straightRightIsClockwiseFromTop() {
        // Clockwise convention: right of center is a quarter turn clockwise from slot 0.
        int quarterTurn = ItemRadialController.slotForAnalog(1f, 0f, 8);
        assertEquals(2, quarterTurn);
    }

    @Test
    void wrapsAroundJustBelowZeroDegrees() {
        // Mostly-left, slightly-down: the raw atan2+90° angle goes negative before the +360°
        // correction — must wrap to the LAST slot (index 3 of 4, ~270°), not jump to slot 0.
        int slot = ItemRadialController.slotForAnalog(-1f, -0.1f, 4);
        assertEquals(3, slot);
    }

    @Test
    void singleSlotAlwaysResolvesToZero() {
        assertEquals(0, ItemRadialController.slotForAnalog(1f, 1f, 1));
    }

    // ---- childSlotForAnalog — the bloom ring's stick selection (feedback: "apunto arriba y
    // selecciona arriba-izquierda... entorpece la navegación"). Unlike slotForAnalog, children fan
    // out from a source angle instead of evenly covering a full circle, so selection is nearest-angle
    // matching against RadialRenderer.bloomChildAngle rather than sector division — and MUST use the
    // raw (unshifted) atan2(y,x), not slotForAnalog's "+PI/2" convention, or every pick silently
    // rotates 90° (caught before shipping by cross-checking against a reference script). `step` is
    // whatever RadialRenderer.bloomChildStepForGeometry produced for that frame in production — the
    // test just picks a representative 30° so the values below stay simple, since these tests are
    // about the nearest-angle ALGORITHM, not any particular geometry-derived number. ----

    private static final double TOP = -Math.PI / 2;   // source category drawn at 12 o'clock
    private static final double STEP = Math.toRadians(30);

    @Test
    void belowThresholdReturnsSticky() {
        assertEquals(-1, ItemRadialController.childSlotForAnalog(0.1f, 0.1f, TOP, 5, STEP));
    }

    @Test
    void singleChildAlwaysResolvesToZero() {
        assertEquals(0, ItemRadialController.childSlotForAnalog(1f, 1f, TOP, 1, STEP));
    }

    @Test
    void pointingStraightAtTheSourceSelectsTheMiddleChild() {
        // 5 children fanned around a source at 12 o'clock: the middle one, index (5-1)/2=2, sits
        // exactly at the source's own angle — pointing the stick straight up must select it.
        assertEquals(2, ItemRadialController.childSlotForAnalog(0f, -1f, TOP, 5, STEP));
    }

    @Test
    void pointingExactlyAtAChildSelectsThatChild() {
        // Reference values cross-checked against a standalone Node script before writing this test —
        // exactly the class of angle-convention bug this fix addresses.
        double idx0Angle = Math.toRadians(-150.0);   // bloomChildAngle(TOP, 0, 5, STEP)
        float x0 = (float) Math.cos(idx0Angle), y0 = (float) Math.sin(idx0Angle);
        assertEquals(0, ItemRadialController.childSlotForAnalog(x0, y0, TOP, 5, STEP));

        double idx4Angle = Math.toRadians(-30.0);    // bloomChildAngle(TOP, 4, 5, STEP)
        float x4 = (float) Math.cos(idx4Angle), y4 = (float) Math.sin(idx4Angle);
        assertEquals(4, ItemRadialController.childSlotForAnalog(x4, y4, TOP, 5, STEP));
    }

    // ---- Release "whiplash" regression. A self-centring stick springs PAST centre when let go and
    // reads a real deflection the other way for about a tenth of a second; the wheels' old pick
    // threshold (0.35) sat INSIDE that band, so the rebound re-pointed the selection at a different
    // sector right before the button-release executed it. The threshold is now the measured rebound
    // ceiling (StickSettleGate.REENGAGE_MAG). ----

    @Test
    void aSpringBackSizedSampleDoesNotPickASector() {
        // 0.40 is a plausible rebound: above the old 0.35 threshold, below the measured ceiling.
        float x = 0f, y = -0.40f;   // straight "up", i.e. it WOULD have selected slot 0
        assertEquals(-1, ItemRadialController.slotForAnalog(x, y, 8));
        assertEquals(-1, ItemRadialController.childSlotForAnalog(x, y, TOP, 5, STEP));
    }

    @Test
    void aDeliberatePushStillPicksItsSector() {
        assertEquals(0, ItemRadialController.slotForAnalog(0f, -0.9f, 8));
    }
}
