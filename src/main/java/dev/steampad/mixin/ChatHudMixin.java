package dev.steampad.mixin;

import dev.steampad.client.keyboard.VirtualKeyboard;
import dev.steampad.compat.mc.GuiPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pushes the chat HISTORY up above the open virtual keyboard. The history is drawn by ChatHud from
 * InGameHud (bottom-anchored ~40 px above the screen edge), independent of ChatScreen, so the
 * ChatScreenMixin translation alone would leave the most recent messages buried under the keyboard.
 * Only shifts while the current screen is the chat with the keyboard open â€” the ordinary in-game
 * message feed is untouched.
 */
@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {

    @Unique private boolean steampad$pushed = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void steampad$pushAboveKeyboard(GuiGraphics ctx, int currentTick, int mouseX, int mouseY,
                                            boolean focused, CallbackInfo ci) {
        var screen = Minecraft.getInstance().screen;
        if (!(screen instanceof ChatScreen chat)) return;
        // ChatScreen.render() already translated this draw — pushing again here is what sent the
        // history far above the input line. See VirtualKeyboard.isPushInProgress for the javap
        // evidence that this is in fact the normal path, not the exception.
        if (VirtualKeyboard.isPushInProgress()) return;
        int shift = VirtualKeyboard.textEntryPushUp(chat);
        if (shift > 0) {
            GuiPose.push(ctx);
            GuiPose.translate(ctx, 0f, -shift);
            steampad$pushed = true;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void steampad$popAboveKeyboard(GuiGraphics ctx, int currentTick, int mouseX, int mouseY,
                                           boolean focused, CallbackInfo ci) {
        if (steampad$pushed) {
            GuiPose.pop(ctx);
            steampad$pushed = false;
        }
    }
}
