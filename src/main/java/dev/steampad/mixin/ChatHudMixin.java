package dev.steampad.mixin;

import dev.steampad.client.keyboard.VirtualKeyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pushes the chat HISTORY up above the open virtual keyboard. The history is drawn by ChatHud from
 * InGameHud (bottom-anchored ~40 px above the screen edge), independent of ChatScreen, so the
 * ChatScreenMixin translation alone would leave the most recent messages buried under the keyboard.
 * Only shifts while the current screen is the chat with the keyboard open — the ordinary in-game
 * message feed is untouched.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Unique private boolean steampad$pushed = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void steampad$pushAboveKeyboard(DrawContext ctx, int currentTick, int mouseX, int mouseY,
                                            boolean focused, CallbackInfo ci) {
        var screen = MinecraftClient.getInstance().currentScreen;
        if (!(screen instanceof ChatScreen chat)) return;
        int shift = VirtualKeyboard.chatPushUp(chat);
        if (shift > 0) {
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(0f, -shift);
            steampad$pushed = true;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void steampad$popAboveKeyboard(DrawContext ctx, int currentTick, int mouseX, int mouseY,
                                           boolean focused, CallbackInfo ci) {
        if (steampad$pushed) {
            ctx.getMatrices().popMatrix();
            steampad$pushed = false;
        }
    }
}
