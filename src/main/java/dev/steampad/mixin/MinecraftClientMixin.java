package dev.steampad.mixin;

import dev.steampad.input.sdl.Sdl3GamepadProvider;
import dev.steampad.steam.SteamBootstrap;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thin hooks: clean backend shutdown at exit, and the spurious-pause guard.
 * Actual logic lives in the respective services — this mixin only delegates.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "close", at = @At("HEAD"))
    private void steampad$onClose(CallbackInfo ci) {
        dev.steampad.service.ControllerClaimService.release();
        SteamBootstrap.shutdown();
        Sdl3GamepadProvider.shutdown();
    }

    // Vanilla pauses the game 500 ms after the window loses focus (GameRenderer.render →
    // openGameMenu when pauseOnLostFocus). With a gamepad playing that kick-to-menu is unwanted —
    // see PauseGate. SteamPad's own pause opens go through PauseGate.openPauseMenu, never blocked.
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void steampad$guardSpuriousPause(net.minecraft.client.gui.screens.Screen screen, CallbackInfo ci) {
        if (dev.steampad.input.PauseGate.shouldSuppress(screen)) ci.cancel();
    }
}
