package dev.steampad.mixin;

import dev.steampad.config.ConfigManager;
import dev.steampad.emote.EmoteAnimator;
import dev.steampad.input.ThirdPersonCameraController;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Forces the crosshair to render in third person while free-look + crosshair redirect are both on —
 * a single boolean flip on the same first-person guard vanilla's {@code renderCrosshair} already
 * checks at its own top, so every OTHER first-person-only behavior in the HUD is untouched. Design
 * credited to Leawind's Third-Person mod (MIT), whose own {@code GuiMixin} does the equivalent flip.
 *
 * <p><b>Excludes a real emote (point 2/D120).</b> {@link ThirdPersonCameraController#isFreeLookEnabled}
 * is also {@code true} while dancing — the free camera orbits so the player can see the animation from
 * any angle (D100) — but that free orbit has nothing to aim, and a crosshair floating over a dance
 * reads as broken UI, not a feature. Checked here directly rather than folding the exclusion into
 * {@code isFreeLookEnabled()} itself: that method is the single source every free-look call site reads
 * (camera pose, pick redirect, entity-target redirect, camera-relative movement), and the crosshair is
 * the one consumer that needs a different answer during an emote specifically — narrowing it there
 * instead keeps every other call site's behavior (the camera still orbits, "mira funcional" still
 * targets whatever's under it) exactly as designed.
 */
@Mixin(Gui.class)
public abstract class ThirdPersonCrosshairMixin {

    @Redirect(method = "renderCrosshair",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
    private boolean steampad$forceThirdPersonCrosshair(CameraType perspective) {
        if (perspective.isFirstPerson()) return true;
        if (EmoteAnimator.isLocalRealEmotePlaying()) return false;
        // Mirror mode (D123): no forced crosshair — the camera faces the player, there is nothing
        // sensible for a crosshair to aim at (vanilla's own front view doesn't draw one either).
        if (perspective.isMirrored()) return false;
        // Mounted (D171): the chase camera's sightline isn't a real aiming reticle — it's just
        // wherever the mount happens to be facing, not something the player deliberately pointed.
        // isMounted() is now also one of the reasons isFreeLookEnabled() can be true, so this has to
        // be excluded explicitly here too, same as the pick/entity-target redirects below.
        if (ThirdPersonCameraController.isMounted(net.minecraft.client.Minecraft.getInstance())) return false;
        return ThirdPersonCameraController.isFreeLookEnabled()
                && ConfigManager.getGlobal().thirdPersonCrosshairEnabled;
    }
}
