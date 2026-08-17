package dev.steampad.mixin;

import dev.steampad.client.keyboard.VirtualKeyboard;
import dev.steampad.compat.mc.GuiPose;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pushes the book/quill editor's whole screen (the page, the turn buttons) up above the virtual
 * keyboard — same gap and same fix as {@link SignEditScreenMixin}, see its doc.
 */
@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin {

    @Unique private boolean steampad$pushed = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void steampad$pushAboveKeyboard(GuiGraphics ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int shift = VirtualKeyboard.textEntryPushUp((BookEditScreen) (Object) this);
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
