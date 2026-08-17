package dev.steampad.mixin;

import dev.steampad.client.ui.GamepadOptionsButton;
import dev.steampad.screen.ControllerSelectScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects a 20×20 gamepad icon button into the vanilla Options screen,
 * positioned immediately to the right of the "Controls" button.
 * Falls back to bottom-left corner if Controls button cannot be found.
 *
 * This is the primary, always-visible entry point for SteamPad — no keybind required.
 *
 * <p>The button widget itself ({@link GamepadOptionsButton}) is deliberately a top-level class in
 * {@code client.ui}, not nested here — see its class doc for why nesting it inside a {@code @Mixin}
 * class crashed the game.
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {

    protected OptionsScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init()V", at = @At("RETURN"))
    private void steampad$addSettingsButton(CallbackInfo ci) {
        int bx = 4;
        int by = this.height - 24;

        // Find the Controls button by its translation key so positioning is locale-independent.
        for (GuiEventListener element : this.children()) {
            if (element instanceof Button btn
                    && btn.getMessage().getContents() instanceof TranslatableContents tc
                    && "options.controls".equals(tc.getKey())) {
                bx = btn.getX() + btn.getWidth() + 2;
                by = btn.getY();
                break;
            }
        }

        this.addRenderableWidget(new GamepadOptionsButton(bx, by,
            btn -> this.minecraft.setScreen(new ControllerSelectScreen(this))));
    }
}
