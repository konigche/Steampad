package dev.steampad.mixin;

import dev.steampad.client.ui.GamepadOptionsButton;
import dev.steampad.screen.ControllerSelectScreen;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
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
        super(Text.empty());
    }

    @Inject(method = "init()V", at = @At("RETURN"))
    private void steampad$addSettingsButton(CallbackInfo ci) {
        int bx = 4;
        int by = this.height - 24;

        // Find the Controls button by its translation key so positioning is locale-independent.
        for (Element element : this.children()) {
            if (element instanceof ButtonWidget btn
                    && btn.getMessage().getContent() instanceof TranslatableTextContent tc
                    && "options.controls".equals(tc.getKey())) {
                bx = btn.getX() + btn.getWidth() + 2;
                by = btn.getY();
                break;
            }
        }

        this.addDrawableChild(new GamepadOptionsButton(bx, by,
            btn -> this.client.setScreen(new ControllerSelectScreen(this))));
    }
}
