package dev.steampad.mixin;

import dev.steampad.client.keyboard.VirtualKeyboard;
import dev.steampad.compat.mc.GuiPose;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pushes a sign editor's whole screen (the 3D sign preview, the text lines) up above the virtual
 * keyboard, same mechanism as {@link ChatScreenMixin}. Sign editing is one of
 * {@link VirtualKeyboard#isTextEntryScreen} — the keyboard auto-opens there exactly like chat — but
 * before this mixin nothing repositioned the sign's own UI to make room for it, so the bottom-anchored
 * keyboard panel overlapped the sign directly.
 *
 * <p>Targets {@code AbstractSignEditScreen} rather than the concrete {@code SignEditScreen} so both
 * standing-sign and hanging-sign editing (its only two subclasses) get the fix from one mixin.
 */
@Mixin(AbstractSignEditScreen.class)
public abstract class SignEditScreenMixin {

    @Unique private boolean steampad$pushed = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void steampad$pushAboveKeyboard(GuiGraphics ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int shift = VirtualKeyboard.textEntryPushUp((AbstractSignEditScreen) (Object) this);
        if (shift > 0) {
            GuiPose.push(ctx);
            GuiPose.translate(ctx, 0f, -shift);
            steampad$pushed = true;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void steampad$popAboveKeyboard(GuiGraphics ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (steampad$pushed) {
            GuiPose.pop(ctx);
            steampad$pushed = false;
        }
    }
}
