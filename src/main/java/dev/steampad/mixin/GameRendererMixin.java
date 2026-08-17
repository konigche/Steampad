package dev.steampad.mixin;

import dev.steampad.input.JuiceController;
import dev.steampad.input.ZoomController;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FOV hook for the controller zoom ({@link ZoomController}) and the impact "FOV kick"
 * ({@link JuiceController}) — the two multiply together, so a hit landing while zoomed still punches
 * in relative to whatever FOV the zoom already set.
 *
 * <p>Signature verified against the mapped 1.21.10 jar via javap:
 * {@code private float getFov(Camera, float, boolean)} — the third parameter ({@code changingFov})
 * is true for the world projection and false for the hand renderer; the multiplier is applied only
 * to the world so the held item doesn't distort while zoomed (same as dedicated zoom mods).
 *
 * <p>Cost while idle: {@code fovFactor()} short-circuits to a single compare and the multiply is
 * skipped entirely — this mixin adds effectively nothing when the zoom is off.
 *
 * <p>(Historical note, D018: a previous renderWorld @Inject here used a pre-1.21 descriptor and
 * crashed startup; the radial overlay renders via HudRenderCallback instead. This class stayed as an
 * empty placeholder until the zoom hook landed.)
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    // The PARAMETERS are identical on every supported version but the RETURN TYPE is not: getFov
    // returns double on 1.21.1 and float from 1.21.9. That changes the descriptor in the @Inject
    // selector, the CallbackInfoReturnable type parameter and the getter — and a wrong descriptor here
    // is a hard startup crash, not a compile error (this exact mismatch crashed 1.21.1 the first time).
    //? if >=1.21.9 {
    @Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)F", at = @At("RETURN"), cancellable = true)
    private void steampad$applyZoom(Camera camera, float tickProgress, boolean changingFov,
                                    CallbackInfoReturnable<Float> cir) {
        if (!changingFov) return;   // hand renderer — leave untouched
        float factor = steampad$fovFactor();
        if (factor < 0.9995f) {
            cir.setReturnValue(cir.getReturnValueF() * factor);
        }
    }
    //?} else {
    /*@Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)D", at = @At("RETURN"), cancellable = true)
    private void steampad$applyZoom(Camera camera, float tickProgress, boolean changingFov,
                                    CallbackInfoReturnable<Double> cir) {
        if (!changingFov) return;   // hand renderer — leave untouched
        float factor = steampad$fovFactor();
        if (factor < 0.9995f) {
            cir.setReturnValue(cir.getReturnValueD() * factor);
        }
    }
    *///?}

    /** Zoom and impact-kick multiplied together, so a hit landing while zoomed still punches in
     *  relative to whatever FOV the zoom already set. Shared by both version branches. */
    @org.spongepowered.asm.mixin.Unique
    private static float steampad$fovFactor() {
        return ZoomController.fovFactor() * JuiceController.fovMultiplier();
    }
}
