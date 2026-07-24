package dev.steampad.mixin;

import dev.steampad.config.ConfigManager;
import dev.steampad.input.ThirdPersonCameraController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redirects the crosshair/interaction raycast to the free camera's own sightline instead of the
 * player's real eye/facing — this is what makes "shoot/attack/mine like first-person" true in third
 * person (README: "place the crosshair above the enemy as if in first-person"): whatever the
 * crosshair is over is what actually gets targeted. Only ever touches the LOCAL player's own raycast
 * — every other entity, and the player's own raycast whenever free-look/crosshair are off, falls
 * through to vanilla's {@code Entity#raycast} completely untouched.
 *
 * <p>Design credited to Leawind's <a href="https://github.com/Leawind/Third-Person">Third-Person</a>
 * mod (MIT license), whose {@code EntityMixin}/{@code CameraAgent#pick} do the equivalent redirect —
 * reimplemented here against SteamPad's own free-camera hit-test rather than ported line for line.
 */
@Mixin(Entity.class)
public abstract class ThirdPersonPickMixin {

    @Inject(method = "raycast(DFZ)Lnet/minecraft/util/hit/HitResult;", at = @At("HEAD"), cancellable = true)
    private void steampad$redirectToCrosshair(double maxDistance, float tickDelta, boolean includeFluids,
                                              CallbackInfoReturnable<HitResult> cir) {
        if (!ThirdPersonCameraController.isFreeLookEnabled()) return;
        if (!ConfigManager.getGlobal().thirdPersonCrosshairEnabled) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || (Entity) (Object) this != mc.player) return;
        if (mc.options.getPerspective().isFirstPerson()) return;

        HitResult hit = ThirdPersonCameraController.currentHitResult();
        if (hit != null) cir.setReturnValue(hit);
    }
}
