package dev.steampad.mixin;

import dev.steampad.client.keyboard.VirtualKeyboard;
import dev.steampad.compat.mc.GuiPose;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pushes the WHOLE chat screen (input line, background strip, command suggestions) up above the
 * open virtual keyboard, Controlify-style. A translation around render() is the only correct hook:
 * the command-suggestion window anchors at {@code owner.height - 12} (hardcoded, verified in the
 * 1.21.10 bytecode), NOT at the text field, so repositioning the field alone leaves the suggestions
 * buried under the keyboard â€” the exact "commands are covered" complaint. The keyboard itself draws
 * later (ScreenEvents.afterRender), outside this push/pop, so it stays at the bottom.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Unique private boolean steampad$pushed = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void steampad$pushAboveKeyboard(GuiGraphics ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int shift = VirtualKeyboard.textEntryPushUp((ChatScreen) (Object) this);
        if (shift > 0) {
            GuiPose.push(ctx);
            GuiPose.translate(ctx, 0f, -shift);
            steampad$pushed = true;
            // Claim the frame so ChatHudMixin doesn't translate the history a SECOND time when
            // render() below calls ChatComponent.render() — see VirtualKeyboard.isPushInProgress.
            VirtualKeyboard.setPushInProgress(true);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void steampad$popAboveKeyboard(GuiGraphics ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (steampad$pushed) {
            GuiPose.pop(ctx);
            steampad$pushed = false;
            VirtualKeyboard.setPushInProgress(false);
        }
    }
}
