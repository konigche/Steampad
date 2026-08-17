package dev.steampad.mixin;

import dev.steampad.config.ConfigManager;
import dev.steampad.input.ThirdPersonCameraController;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
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

    @Inject(method = "pick(DFZ)Lnet/minecraft/world/phys/HitResult;", at = @At("HEAD"), cancellable = true)
    private void steampad$redirectToCrosshair(double maxDistance, float tickDelta, boolean includeFluids,
                                              CallbackInfoReturnable<HitResult> cir) {
        if (!ThirdPersonCameraController.isFreeLookEnabled()) return;
        if (!ConfigManager.getGlobal().thirdPersonCrosshairEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (Entity) (Object) this != mc.player) return;
        if (mc.options.getCameraType().isFirstPerson()) return;
        // Mirror mode (D123): the free camera there faces the PLAYER, so its sightline points at
        // yourself — redirecting interaction to it would mean punching toward your own camera.
        // Vanilla's player-facing raycast is the correct semantics in that view (the body is
        // stick-driven there, exactly like vanilla front view). isMirrorMode, not raw isFrontView:
        // a real emote's free orbit wins even in front view.
        if (ThirdPersonCameraController.isMirrorMode(mc)) return;
        // Mounted (D171): interacting while riding should use the REAL raycast (vanilla semantics),
        // not the chase camera's sightline — isMounted() is now also one of the reasons
        // isFreeLookEnabled() can be true, so it has to be excluded here explicitly too.
        if (ThirdPersonCameraController.isMounted(mc)) return;

        HitResult hit = ThirdPersonCameraController.currentHitResult();
        if (hit != null) cir.setReturnValue(hit);
    }
}
