package dev.steampad.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels the vanilla hotbar render while any of the three wheels (Menú Radial, Rueda de Emotes,
 * Rueda de Objetos) is open — the wheels already show the same items as big icons, so the flat
 * vanilla bar underneath is redundant clutter. Health/hunger/XP (separate private methods,
 * {@code renderHealthBar}/{@code renderFood}/etc. — confirmed by {@code javap} before writing this)
 * are untouched — only the hotbar itself hides.
 */
@Mixin(Gui.class)
public abstract class InGameHudMixin {

    @Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true)
    private void steampad$hideDuringWheel(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (dev.steampad.radial.RadialMenuController.isOpen()
                || dev.steampad.emote.EmoteWheelController.isOpen()
                || dev.steampad.itemradial.ItemRadialController.isOpen()) {
            ci.cancel();
        }
    }
}
