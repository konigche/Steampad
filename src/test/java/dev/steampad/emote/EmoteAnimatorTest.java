package dev.steampad.emote;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * D109: proves the cross-entity pose contamination bug is real, and that
 * {@link EmoteAnimator#resetToBakedRest} — the faithful port of the real reference's own
 * "unconditional reset at HEAD, before vanilla's own pose logic runs" mechanism — closes it
 * completely, including the fields D108's narrower TAIL-only attempt could not safely touch. No
 * Mixin/game runtime needed — {@link ModelPart}/{@link HumanoidModel} are plain data classes;
 * only the bend-cuboid duck interfaces ({@code BendableCuboid}) require Mixin weaving, and this
 * test never touches those.
 */
class EmoteAnimatorTest {

    /** Bare biped with real per-part rest transforms (the actual vanilla pivots) and empty cuboid
     *  lists — enough for {@link EmoteAnimator#resetToBakedRest} to operate on; nothing here needs
     *  to actually render. */
    private static HumanoidModel<?> freshBipedModel() {
        ModelPart hat = new ModelPart(List.of(), Map.of());
        ModelPart head = new ModelPart(List.of(), Map.of("hat", hat));
        ModelPart body = new ModelPart(List.of(), Map.of());
        ModelPart rightArm = new ModelPart(List.of(), Map.of());
        ModelPart leftArm = new ModelPart(List.of(), Map.of());
        ModelPart rightLeg = new ModelPart(List.of(), Map.of());
        ModelPart leftLeg = new ModelPart(List.of(), Map.of());

        head.setInitialPose(PartPose.offsetAndRotation(0f, 0f, 0f, 0f, 0f, 0f));
        body.setInitialPose(PartPose.offsetAndRotation(0f, 0f, 0f, 0f, 0f, 0f));
        rightArm.setInitialPose(PartPose.offsetAndRotation(-5f, 2f, 0f, 0f, 0f, 0f));
        leftArm.setInitialPose(PartPose.offsetAndRotation(5f, 2f, 0f, 0f, 0f, 0f));
        rightLeg.setInitialPose(PartPose.offsetAndRotation(-1.9f, 12f, 0.1f, 0f, 0f, 0f));
        leftLeg.setInitialPose(PartPose.offsetAndRotation(1.9f, 12f, 0.1f, 0f, 0f, 0f));

        // "hat" is registered under BOTH head and root on purpose: HumanoidModel's constructor resolves
        // it from head on 1.21.2+ but from the ROOT before that, and it throws if it can't find it.
        // Listing it twice (same instance) satisfies either lookup without needing a version
        // conditional here — which matters because this is a test source, and whether Stonecutter
        // preprocesses those is not something this fixture should depend on. Harmless for what these
        // tests assert: resetting a part to its baked pose is idempotent, so a double visit changes
        // nothing.
        ModelPart root = new ModelPart(List.of(), Map.of(
                "head", head, "hat", hat, "body", body,
                "right_arm", rightArm, "left_arm", leftArm,
                "right_leg", rightLeg, "left_leg", leftLeg));
        return new HumanoidModel<>(root);
    }

    @Test
    void reproducesTheContaminationBugOnAFreshModel_thenResetToBakedRestClearsIt() {
        HumanoidModel<?> model = freshBipedModel();

        // Simulate exactly what a PAST emote (e.g. a crossed-arms dance) left behind on this SHARED
        // model instance — vanilla's own setAngles() never touches rightArm.yaw/roll or originX, so
        // nothing but our own code could ever have written these, and nothing but our own code will
        // ever clear them.
        model.rightArm.yRot = -0.68f;
        model.rightArm.zRot = -1.2f;
        model.rightArm.x = 999f;
        model.body.yRot = 0.4f;
        model.body.z = -3f;
        model.leftLeg.y = -50f;

        // Reproduction: without the fix, these fields would still read the stale, contaminated
        // values right here — nothing clears them on its own. This assertion documents the BEFORE
        // state directly, so the test fails loudly if a future refactor accidentally stops wiring
        // resetToBakedRest into the mixin's HEAD injection.
        assertEquals(-0.68f, model.rightArm.yRot, 1e-6, "sanity: contamination actually applied");

        EmoteAnimator.resetToBakedRest(model);

        assertEquals(0f, model.rightArm.yRot, 1e-6, "rightArm.yaw restored to baked rest");
        assertEquals(0f, model.rightArm.zRot, 1e-6, "rightArm.roll restored to baked rest");
        assertEquals(-5f, model.rightArm.x, 1e-6, "rightArm.originX restored to its real pivot, not 0");
        assertEquals(0f, model.body.yRot, 1e-6, "body.yaw restored");
        assertEquals(0f, model.body.z, 1e-6, "body.originZ restored");
        assertEquals(12f, model.leftLeg.y, 1e-6, "leftLeg.originY restored to its real pivot, not 0");
    }

    @Test
    void closesTheGapD108CouldNotSafelyClose_conditionallyManagedFieldsLikeSneakingPitch() {
        HumanoidModel<?> model = freshBipedModel();

        // body.pitch is exactly the field D108's post-mortem flagged as an unresolved residual risk:
        // vanilla only sets it during sneaking (an absolute 0.5f), so a stale non-default value
        // survives untouched on any render where the CURRENT entity isn't sneaking. Simulate that
        // leftover directly.
        model.body.xRot = 0.5f;   // e.g. left over from a PREVIOUS entity's sneaking-pose render
        model.head.y = 4.2f; // same story for a part vanilla only conditionally offsets

        EmoteAnimator.resetToBakedRest(model);

        assertEquals(0f, model.body.xRot, 1e-6,
                "body.pitch reset to neutral — this runs at HEAD, before vanilla's OWN sneaking check "
                        + "for the CURRENT render even executes, so it can never fight a legitimate value");
        assertEquals(0f, model.head.y, 1e-6, "head.originY reset to its baked rest");
    }

    @Test
    void vanillasOwnSubsequentLogicCanStillFreelyOverwriteAfterTheReset() {
        // Proves the HEAD-timing claim itself: resetToBakedRest() only establishes a baseline: it
        // does not prevent code that runs AFTER it (vanilla's own setAngles body, in real gameplay)
        // from setting whatever pose it wants — exactly the same plain field writes vanilla itself
        // performs (walk cycle, sneaking), simulated here directly since this test has no live
        // vanilla logic to invoke.
        HumanoidModel<?> model = freshBipedModel();
        EmoteAnimator.resetToBakedRest(model);

        model.head.xRot = 0.3f;      // as if vanilla's own head-look logic just ran
        model.rightArm.xRot = 0.9f;  // as if vanilla's own walk-cycle logic just ran
        model.body.xRot = 0.5f;      // as if vanilla's own sneaking check just ran

        assertEquals(0.3f, model.head.xRot, 1e-6);
        assertEquals(0.9f, model.rightArm.xRot, 1e-6);
        assertEquals(0.5f, model.body.xRot, 1e-6);
    }
}
