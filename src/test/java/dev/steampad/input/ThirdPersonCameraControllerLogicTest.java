package dev.steampad.input;

import dev.steampad.config.GlobalConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the pure decision logic behind the "Mejor Tercera Persona" fixes
 * ({@code distanceTargetFor}/{@code offsetTargetXFor}, D120/D122) without touching any
 * Minecraft/Camera state — these two methods were extracted from {@code computeFreePoseInner}
 * specifically so this class of bug (a real one, caught mid-session before it shipped — see below)
 * has a regression test instead of relying on hardware validation to ever exercise every
 * side×state combination.
 */
class ThirdPersonCameraControllerLogicTest {

    private static GlobalConfig config(GlobalConfig.ThirdPersonCameraSide side, float offset,
                                        float aimingOffset, float freeDistance) {
        GlobalConfig g = new GlobalConfig();
        g.thirdPersonCameraSide = side;
        g.thirdPersonCameraOffset = offset;
        g.thirdPersonAimingOffset = aimingOffset;
        g.thirdPersonFreeDistance = freeDistance;
        return g;
    }

    // ---- distance ----------------------------------------------------------------------------

    @Test
    void normalStateUsesTheConfiguredFreeDistanceUnchanged() {
        GlobalConfig g = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 0.30f, 0.3f, 2.7f);
        assertEquals(2.7, ThirdPersonCameraController.distanceTargetFor(g, false, false, 0.0), 1e-5);
    }

    @Test
    void emotingWithNoManualZoomMatchesTheConfirmedGoodD111Baseline() {
        // Point 3 hardware feedback: "quedo perfecto" — this exact baseline (offset=0) must be
        // bit-for-bit identical to what shipped in v0.78.0, unaffected by the new manual-zoom feature.
        // 2.7 * 0.68 = 1.836, below the 1.9 floor — must clamp, not just scale.
        GlobalConfig g = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 0.30f, 0.3f, 2.7f);
        assertEquals(1.9, ThirdPersonCameraController.distanceTargetFor(g, true, false, 0.0), 1e-5);

        // A large configured distance must clamp at the top of the D111 range too.
        GlobalConfig far = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 0.30f, 0.3f, 6.0f);
        assertEquals(3.3, ThirdPersonCameraController.distanceTargetFor(far, true, false, 0.0), 1e-5);
    }

    @Test
    void emotingWithManualZoomAddsOnTopOfTheD111BaselineThenClampsWide() {
        // point 3/D122: the live D-pad nudge is added AFTER the D111 clamp, then the SUM is bounded
        // by the wider absolute range — not just re-scaling the base.
        GlobalConfig g = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 0.30f, 0.3f, 2.7f);
        double baseline = ThirdPersonCameraController.distanceTargetFor(g, true, false, 0.0);

        assertEquals(baseline + 0.5, ThirdPersonCameraController.distanceTargetFor(g, true, false, 0.5), 1e-5,
                "a modest zoom-out nudge must add linearly on top of the D111 baseline");
        assertEquals(baseline - 0.5, ThirdPersonCameraController.distanceTargetFor(g, true, false, -0.5), 1e-5,
                "a modest zoom-in nudge must subtract linearly from the D111 baseline");
    }

    @Test
    void emotingWithExtremeManualZoomClampsToTheAbsoluteRangeInsteadOfClippingOrFlyingOff() {
        GlobalConfig g = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 0.30f, 0.3f, 2.7f);
        // A huge positive offset must not push the camera absurdly far away.
        assertTrue(ThirdPersonCameraController.distanceTargetFor(g, true, false, 100.0) <= 5.5 + 1e-9);
        // A huge negative offset must not let the camera clip through/past the character.
        assertTrue(ThirdPersonCameraController.distanceTargetFor(g, true, false, -100.0) >= 0.8 - 1e-9);
    }

    @Test
    void aimingPullsInCloseToShoulderWithAFloor() {
        // D122: tightened from 0.6/1.3 to 0.4/0.9 — feedback: "mas como al hombro del personaje,
        // tipo shooter tercera persona".
        GlobalConfig g = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 0.30f, 0.3f, 2.7f);
        assertEquals(2.7 * 0.4, ThirdPersonCameraController.distanceTargetFor(g, false, true, 0.0), 1e-5);

        // A very short configured distance must not let the aiming pull-in clip into the character.
        GlobalConfig close = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 0.30f, 0.3f, 1.0f);
        assertEquals(0.9, ThirdPersonCameraController.distanceTargetFor(close, false, true, 0.0), 1e-5);
    }

    @Test
    void emoteZoomOffsetIsIgnoredWhileOnlyAiming() {
        // The manual emote-zoom parameter must have zero effect outside of emoting — it's a
        // point-3-specific nudge, not a general aiming-distance override.
        GlobalConfig g = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 0.30f, 0.3f, 2.7f);
        double withoutOffset = ThirdPersonCameraController.distanceTargetFor(g, false, true, 0.0);
        double withOffset = ThirdPersonCameraController.distanceTargetFor(g, false, true, 1.0);
        assertEquals(withoutOffset, withOffset, 1e-5,
                "emoteZoomOffset must not affect the aiming (non-emoting) branch");
    }

    @Test
    void emotingTakesPriorityOverAimingIfSomehowBothAreTrue() {
        GlobalConfig g = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 0.30f, 0.3f, 2.7f);
        double emotingOnly = ThirdPersonCameraController.distanceTargetFor(g, true, false, 0.0);
        double both = ThirdPersonCameraController.distanceTargetFor(g, true, true, 0.0);
        assertEquals(emotingOnly, both, 1e-5, "emoting=true must win regardless of the aiming flag");
    }

    // ---- mounted distance (D178) --------------------------------------------------------------

    @Test
    void mountedUsesItsOwnPullBackDistanceNotTheOnFootOne() {
        // "solo aleja un poco mas la camara cuando esta montado": a horse or boat fills far more of
        // the frame than a player, so the mounted view has its own distance rather than sharing the
        // on-foot one.
        GlobalConfig g = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 0.30f, 0.3f, 2.7f);
        g.thirdPersonMountedCameraDistance = 4.0f;
        assertEquals(4.0, ThirdPersonCameraController.distanceTargetFor(g, false, false, 0.0, true), 1e-5);
        // On foot the very same config must be untouched by the new field.
        assertEquals(2.7, ThirdPersonCameraController.distanceTargetFor(g, false, false, 0.0, false), 1e-5);
    }

    @Test
    void theFourArgOverloadStillMeansOnFoot() {
        // Every pre-existing call site and test uses the 4-arg form; it must keep meaning "not
        // mounted" exactly, or the mounted pull-back would leak into ordinary walking around.
        GlobalConfig g = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 0.30f, 0.3f, 2.7f);
        g.thirdPersonMountedCameraDistance = 9.0f;
        assertEquals(ThirdPersonCameraController.distanceTargetFor(g, false, false, 0.0, false),
                ThirdPersonCameraController.distanceTargetFor(g, false, false, 0.0), 1e-5);
    }

    @Test
    void aimingWhileMountedStillPullsInClose() {
        // Deliberate precedence: charging a bow pulls the camera in whether you're on foot or riding,
        // so aiming must win over the mounted pull-back (which is only the resting driving framing).
        GlobalConfig g = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 0.30f, 0.3f, 2.7f);
        g.thirdPersonMountedCameraDistance = 4.0f;
        assertEquals(2.7 * 0.4,
                ThirdPersonCameraController.distanceTargetFor(g, false, true, 0.0, true), 1e-5);
    }

    @Test
    void emotingWhileMountedStillOwnsTheFraming() {
        GlobalConfig g = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 0.30f, 0.3f, 2.7f);
        g.thirdPersonMountedCameraDistance = 4.0f;
        assertEquals(ThirdPersonCameraController.distanceTargetFor(g, true, false, 0.0, false),
                ThirdPersonCameraController.distanceTargetFor(g, true, false, 0.0, true), 1e-5,
                "a real emote frames itself the same way on foot or mounted");
    }

    // ---- lateral offset ------------------------------------------------------------------------

    @Test
    void emotingAlwaysCentersRegardlessOfConfiguredSide() {
        for (GlobalConfig.ThirdPersonCameraSide side : GlobalConfig.ThirdPersonCameraSide.values()) {
            GlobalConfig g = config(side, 0.9f, 0.5f, 2.7f);
            assertEquals(0.0, ThirdPersonCameraController.offsetTargetXFor(g, true, false, 0.2), 1e-5,
                    "emoting must centre even with a live adjustOffsetX and side=" + side);
        }
    }

    @Test
    void aimingWithCenterSideStaysCentered() {
        // The regression this test exists for: an early draft treated "side != LEFT" as "go right",
        // which pushed the camera off-centre the instant you aimed even with CENTER selected — the
        // exact opposite of "more centered while aiming". Caught here, before hardware, not by it.
        GlobalConfig g = config(GlobalConfig.ThirdPersonCameraSide.CENTER, 0.9f, 0.8f, 2.7f);
        assertEquals(0.0, ThirdPersonCameraController.offsetTargetXFor(g, false, true, 0.0), 1e-5);
    }

    @Test
    void aimingWithRightSideBlendsTowardThePositiveAimingOffset() {
        GlobalConfig g = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 0.9f, 0.5f, 2.7f);
        double result = ThirdPersonCameraController.offsetTargetXFor(g, false, true, 0.0);
        assertTrue(result > 0, "RIGHT side while aiming must stay on the positive (right) side, got " + result);
    }

    @Test
    void aimingWithLeftSideBlendsTowardTheNegativeAimingOffset() {
        GlobalConfig g = config(GlobalConfig.ThirdPersonCameraSide.LEFT, 0.9f, 0.5f, 2.7f);
        double result = ThirdPersonCameraController.offsetTargetXFor(g, false, true, 0.0);
        assertTrue(result < 0, "LEFT side while aiming must stay on the negative (left) side, got " + result);
    }

    @Test
    void aimingOffsetMagnitudeComesFromTheAimingSettingNotTheNormalOneWhenLargerThanTheFloor() {
        // Same side, deliberately different normal-vs-aiming magnitudes, both ABOVE the D122 floor —
        // the aiming target must track thirdPersonAimingOffset, not thirdPersonCameraOffset.
        GlobalConfig smallAim = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 1.5f, 0.8f, 2.7f);
        GlobalConfig bigAim = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 1.5f, 1.5f, 2.7f);
        double small = ThirdPersonCameraController.offsetTargetXFor(smallAim, false, true, 0.0);
        double big = ThirdPersonCameraController.offsetTargetXFor(bigAim, false, true, 0.0);
        assertTrue(big > small, "a larger thirdPersonAimingOffset must produce a larger aiming offset");
    }

    @Test
    void aimingOffsetHasAMinimumEvenWithAConservativeConfiguredValue() {
        // D122: feedback "mas como al hombro del personaje" — a user with the DEFAULT (30%,
        // ratio ≈0.10) or lower thirdPersonAimingOffset would otherwise get a barely-there shift.
        // Both a very low setting and the default itself must floor to the same minimum.
        GlobalConfig veryLow = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 1.5f, 0.02f, 2.7f);
        GlobalConfig zero = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 1.5f, 0.0f, 2.7f);
        double low = ThirdPersonCameraController.offsetTargetXFor(veryLow, false, true, 0.0);
        double none = ThirdPersonCameraController.offsetTargetXFor(zero, false, true, 0.0);
        assertEquals(low, none, 1e-5, "both must hit the same floor regardless of how low the config is");
        assertTrue(low >= 0.20, "the floored aiming offset must still read as a decisive shoulder shift, got " + low);
    }

    @Test
    void aLargerConfiguredAimingOffsetStillWinsOverTheFloor() {
        GlobalConfig g = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 1.5f, 1.5f, 2.7f);
        double result = ThirdPersonCameraController.offsetTargetXFor(g, false, true, 0.0);
        assertTrue(result > 0.22, "a deliberately large configured value must not be clamped down to the floor");
    }

    @Test
    void normalStateUsesTheConfiguredSideOffsetPlusLiveAdjustment() {
        GlobalConfig g = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 0.30f, 0.3f, 2.7f);
        double withoutAdjust = ThirdPersonCameraController.offsetTargetXFor(g, false, false, 0.0);
        double withAdjust = ThirdPersonCameraController.offsetTargetXFor(g, false, false, 0.05);
        assertEquals(withoutAdjust + 0.05, withAdjust, 1e-5,
                "the live THIRD_PERSON_ADJUST nudge must add linearly on top of the configured offset");
    }

    @Test
    void normalStateWithCenterSideIgnoresTheConfiguredOffsetMagnitude() {
        GlobalConfig g = config(GlobalConfig.ThirdPersonCameraSide.CENTER, 1.5f, 0.5f, 2.7f);
        assertEquals(0.0, ThirdPersonCameraController.offsetTargetXFor(g, false, false, 0.0), 1e-5);
    }

    // ---- manual emote-zoom state (point 3, D122) ----------------------------------------------

    @Test
    void adjustEmoteZoomStepsTowardCloserOnDpadUp() {
        ThirdPersonCameraController.resetEmoteZoom();   // known starting state, independent of test order
        try {
            assertEquals(0.0, ThirdPersonCameraController.currentEmoteZoomOffset(), 1e-9);
            ThirdPersonCameraController.adjustEmoteZoom(+1);   // D-pad up: pull closer
            assertTrue(ThirdPersonCameraController.currentEmoteZoomOffset() < 0.0,
                    "D-pad up (dir=+1) must move the offset NEGATIVE (closer), matching adjustDistance's "
                            + "own sign convention (subtracted from the distance target)");
        } finally {
            ThirdPersonCameraController.resetEmoteZoom();
        }
    }

    @Test
    void adjustEmoteZoomStepsTowardFartherOnDpadDown() {
        ThirdPersonCameraController.resetEmoteZoom();
        try {
            ThirdPersonCameraController.adjustEmoteZoom(-1);   // D-pad down: push back
            assertTrue(ThirdPersonCameraController.currentEmoteZoomOffset() > 0.0,
                    "D-pad down (dir=-1) must move the offset POSITIVE (farther)");
        } finally {
            ThirdPersonCameraController.resetEmoteZoom();
        }
    }

    @Test
    void adjustEmoteZoomClampsInsteadOfGrowingUnbounded() {
        ThirdPersonCameraController.resetEmoteZoom();
        try {
            for (int i = 0; i < 200; i++) ThirdPersonCameraController.adjustEmoteZoom(+1);
            double clamped = ThirdPersonCameraController.currentEmoteZoomOffset();
            assertTrue(clamped >= -1.1 - 1e-9 && clamped <= 2.2 + 1e-9,
                    "repeated presses must clamp, not accumulate an unbounded internal value "
                            + "(would create a dead zone working back the other way), got " + clamped);
        } finally {
            ThirdPersonCameraController.resetEmoteZoom();
        }
    }

    @Test
    void resetEmoteZoomAlwaysReturnsToExactlyZero() {
        try {
            ThirdPersonCameraController.adjustEmoteZoom(+1);
            ThirdPersonCameraController.adjustEmoteZoom(+1);
            ThirdPersonCameraController.adjustEmoteZoom(-1);
            ThirdPersonCameraController.resetEmoteZoom();
            assertEquals(0.0, ThirdPersonCameraController.currentEmoteZoomOffset(), 1e-9,
                    "point 3: \"siempre debe de regresar a su posicion\" — reset must be exact, not "
                            + "just \"close to\" zero, so the automatic D111 baseline is bit-for-bit "
                            + "restored the instant an emote stops or is interrupted");
        } finally {
            ThirdPersonCameraController.resetEmoteZoom();
        }
    }

    @Test
    void manualZoomOffsetCombinesCorrectlyWithTheAutomaticBaselineEndToEnd() {
        // End-to-end sanity check tying adjustEmoteZoom's real stateful effect back into
        // distanceTargetFor's pure computation, the same composition the real per-frame call site uses.
        ThirdPersonCameraController.resetEmoteZoom();
        try {
            GlobalConfig g = config(GlobalConfig.ThirdPersonCameraSide.RIGHT, 0.30f, 0.3f, 2.7f);
            double baseline = ThirdPersonCameraController.distanceTargetFor(g, true, false, 0.0);

            ThirdPersonCameraController.adjustEmoteZoom(+1);
            double afterZoomIn = ThirdPersonCameraController.distanceTargetFor(
                    g, true, false, ThirdPersonCameraController.currentEmoteZoomOffset());
            assertTrue(afterZoomIn < baseline, "D-pad up must pull the camera CLOSER during an emote");

            ThirdPersonCameraController.resetEmoteZoom();
            double afterReset = ThirdPersonCameraController.distanceTargetFor(
                    g, true, false, ThirdPersonCameraController.currentEmoteZoomOffset());
            assertEquals(baseline, afterReset, 1e-5,
                    "after reset, the combined distance must be EXACTLY the automatic baseline again");
        } finally {
            ThirdPersonCameraController.resetEmoteZoom();
        }
    }

    // ---- easeAngleToward (D123, mirror mode) — angle-wrap is exactly the sign/branch class of bug
    // these tests exist for (the D120 CENTER bug was caught the same way, pre-compile) ------------

    @Test
    void angleEaseMovesTowardTheTargetProportionally() {
        // Plain case, no wrap involved: from 0° toward 100° at factor 0.5 → 50°.
        assertEquals(50.0, ThirdPersonCameraController.easeAngleToward(0.0, 100.0, 0.5), 1e-9);
    }

    @Test
    void angleEaseCrossesTheWrapBoundaryTheShortWayAround() {
        // From +170° toward −170° the short way is +20° THROUGH ±180, not −340° back through 0.
        // Half of that 20° step lands at +180 == −180 (wrapDegrees canonicalizes to −180).
        double stepped = ThirdPersonCameraController.easeAngleToward(170.0, -170.0, 0.5);
        assertEquals(-180.0, stepped, 1e-9,
                "easing +170→−170 must pass through ±180 (short way), never swing back through 0");
    }

    @Test
    void angleEaseConvergesExactlyOnTheTargetAtFullFactor() {
        // factor 1.0 = arrive this very step, wherever the target sits on the circle.
        assertEquals(-170.0, ThirdPersonCameraController.easeAngleToward(170.0, -170.0, 1.0), 1e-9);
        assertEquals(45.0, ThirdPersonCameraController.easeAngleToward(-45.0, 45.0, 1.0), 1e-9);
    }

    @Test
    void angleEaseIsAlreadyAtTargetStaysPut() {
        assertEquals(90.0, ThirdPersonCameraController.easeAngleToward(90.0, 90.0, 0.35), 1e-9);
    }

    // ---- bodyRelativeMove: walk camera-relative while the body stays aimed at the crosshair -------
    // The world direction vanilla will actually move in, given (side, fwd) and a body yaw, using
    // vanilla's own convention — the same one applyCameraRelativeMovement uses to build its world
    // vector. Recomputing it here (instead of asserting raw components) is what proves the round trip.
    private static double[] worldDir(double yawDeg, float side, float fwd) {
        double y = Math.toRadians(yawDeg);
        return new double[]{side * Math.cos(y) - fwd * Math.sin(y), side * Math.sin(y) + fwd * Math.cos(y)};
    }

    @Test
    void bodyRelativeMoveWithBodyAlreadyFacingTheCameraIsAPlainForwardPush() {
        // Body already aligned with the camera: "push forward" must stay a pure forward push.
        float[] r = ThirdPersonCameraController.bodyRelativeMove(40.0, 40.0, 0f, 1f);
        assertEquals(0.0, r[0], 1e-5);
        assertEquals(1.0, r[1], 1e-5);
    }

    @Test
    void bodyRelativeMoveProducesTheSameWorldDirectionTheCameraAsked() {
        // The whole point: whatever the body's yaw, feeding the returned pair to vanilla must move the
        // player in the direction the CAMERA-relative push meant. Body 90° away from the camera is the
        // case that was broken (arrow flew toward the walk direction instead of the crosshair).
        double camYaw = 30.0, bodyYaw = 120.0;
        float side = 0f, fwd = 1f;
        double[] want = worldDir(camYaw, side, fwd);
        float[] r = ThirdPersonCameraController.bodyRelativeMove(camYaw, bodyYaw, side, fwd);
        double[] got = worldDir(bodyYaw, r[0], r[1]);
        assertEquals(want[0], got[0], 1e-5);
        assertEquals(want[1], got[1], 1e-5);
    }

    @Test
    void bodyRelativeMoveRoundTripsForStrafeAndDiagonalPushesToo() {
        double camYaw = -75.0, bodyYaw = 160.0;
        for (float[] push : new float[][]{{1f, 0f}, {-1f, 0f}, {0f, -1f}, {0.7f, 0.7f}, {-0.5f, 0.9f}}) {
            double[] want = worldDir(camYaw, push[0], push[1]);
            // Production clamps to unit length only when the push exceeds it; mirror that exactly.
            double len = Math.hypot(want[0], want[1]);
            if (len > 1.0) { want[0] /= len; want[1] /= len; }
            float[] r = ThirdPersonCameraController.bodyRelativeMove(camYaw, bodyYaw, push[0], push[1]);
            double[] got = worldDir(bodyYaw, r[0], r[1]);
            assertEquals(want[0], got[0], 1e-5, "worldX for push " + push[0] + "," + push[1]);
            assertEquals(want[1], got[1], 1e-5, "worldZ for push " + push[0] + "," + push[1]);
        }
    }

    @Test
    void bodyRelativeMoveNeverExceedsFullSpeed() {
        // A diagonal push must not sneak past magnitude 1 (that would be a speed hack).
        float[] r = ThirdPersonCameraController.bodyRelativeMove(0.0, 45.0, 1f, 1f);
        assertTrue(Math.hypot(r[0], r[1]) <= 1.0 + 1e-6, "magnitude was " + Math.hypot(r[0], r[1]));
    }

    @Test
    void bodyRelativeMoveIgnoresAnIdleStick() {
        float[] r = ThirdPersonCameraController.bodyRelativeMove(12.0, 200.0, 0f, 0f);
        assertEquals(0f, r[0], 1e-9);
        assertEquals(0f, r[1], 1e-9);
    }
}
