package dev.steampad.mixin;

import dev.steampad.input.VirtualMouseController;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thin hook: center the virtual cursor when a screen opens. The cursor is drawn via Fabric's
 * after-render screen event (so it sits above held items/tooltips) — see SteamPadClient.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "init(Lnet/minecraft/client/Minecraft;II)V", at = @At("RETURN"))
    private void steampad$onScreenInit(CallbackInfo ci) {
        VirtualMouseController.centerOnScreen();
        // MC resets the OS pointer to NORMAL on (re)init; force the cursor renderer to re-assert it.
        dev.steampad.client.ui.VirtualCursorRenderer.invalidateOsCursorState();
    }
}
