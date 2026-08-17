package dev.steampad.mixin;

import dev.steampad.input.ZoomController;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the first-person hands/held items while the cinematic letterbox bars are closed, so the shot
 * is actually clean (reported: "cuando activo la barra cinemática del zoom, esta no guarda la UI ni
 * los objetos de la mano, debe guardarlo todo"). The vanilla HUD half of that is handled separately
 * via {@code Options.hideGui} — see {@link ZoomController}.
 *
 * <p>Cancelling at HEAD simply skips vanilla's whole hand-rendering pass for that frame; nothing else
 * in the method has side effects the rest of the renderer depends on, so there is no state left
 * half-updated by not running it.
 *
 * <p>The target's third parameter changed type in the render-pipeline rework — {@code BufferSource}
 * before, {@code SubmitNodeCollector} after — so the handler is gated. The exact boundary was probed
 * against real jars: 1.21.1/1.21.3/1.21.4/1.21.5/<b>1.21.8</b> all still take {@code BufferSource},
 * 1.21.10 takes {@code SubmitNodeCollector}. 1.21.9 itself was not available locally, so if a variant
 * for it is ever added, confirm which side it falls on rather than trusting this boundary.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    //? if >=1.21.9 {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void steampad$hideHandsDuringCinematicBars(
            float partialTick,
            com.mojang.blaze3d.vertex.PoseStack pose,
            net.minecraft.client.renderer.SubmitNodeCollector collector,
            net.minecraft.client.player.LocalPlayer player,
            int light,
            CallbackInfo ci) {
        if (ZoomController.shouldHideHands()) ci.cancel();
    }
    //?} else {
    /*@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void steampad$hideHandsDuringCinematicBars(
            float partialTick,
            com.mojang.blaze3d.vertex.PoseStack pose,
            net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers,
            net.minecraft.client.player.LocalPlayer player,
            int light,
            CallbackInfo ci) {
        if (ZoomController.shouldHideHands()) ci.cancel();
    }
    *///?}
}
