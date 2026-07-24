package dev.steampad.mixin;

import dev.steampad.config.ConfigManager;
import dev.steampad.input.ThirdPersonCameraController;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.option.Perspective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Forces the crosshair to render in third person while free-look + crosshair redirect are both on —
 * a single boolean flip on the same first-person guard vanilla's {@code renderCrosshair} already
 * checks at its own top, so every OTHER first-person-only behavior in the HUD is untouched. Design
 * credited to Leawind's Third-Person mod (MIT), whose own {@code GuiMixin} does the equivalent flip.
 */
@Mixin(InGameHud.class)
public abstract class ThirdPersonCrosshairMixin {

    @Redirect(method = "renderCrosshair",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z"))
    private boolean steampad$forceThirdPersonCrosshair(Perspective perspective) {
        if (perspective.isFirstPerson()) return true;
        return ThirdPersonCameraController.isFreeLookEnabled()
                && ConfigManager.getGlobal().thirdPersonCrosshairEnabled;
    }
}
