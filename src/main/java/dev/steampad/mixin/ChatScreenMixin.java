package dev.steampad.mixin;

import dev.steampad.client.keyboard.VirtualKeyboard;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
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
 * buried under the keyboard — the exact "commands are covered" complaint. The keyboard itself draws
 * later (ScreenEvents.afterRender), outside this push/pop, so it stays at the bottom.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Unique private boolean steampad$pushed = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void steampad$pushAboveKeyboard(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int shift = VirtualKeyboard.chatPushUp((ChatScreen) (Object) this);
        if (shift > 0) {
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(0f, -shift);
            steampad$pushed = true;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void steampad$popAboveKeyboard(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (steampad$pushed) {
            ctx.getMatrices().popMatrix();
            steampad$pushed = false;
        }
    }
}
