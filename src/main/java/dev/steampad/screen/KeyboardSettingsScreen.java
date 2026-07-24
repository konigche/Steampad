package dev.steampad.screen;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.GlobalConfig;
import dev.steampad.config.PixelTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/**
 * Settings for the controller-driven virtual keyboard. Two-column layout (list + description) like the
 * rest of the settings screens.
 */
public class KeyboardSettingsScreen extends ColumnSettingsScreen {

    private static final int PREVIEW_H = 26;

    private final Screen parent;
    private GlobalConfig cfg;
    /** Base Y (before scroll) of the reserved theme-preview strip, drawn below the theme row. */
    private int previewBaseY;

    public KeyboardSettingsScreen(Screen parent) {
        super(Text.translatable("steampad.keyboard.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.cfg = ConfigManager.getGlobal();
        beginLayout();

        section("steampad.keyboard.settings.title");
        toggle("steampad.keyboard.enabled", cfg.virtualKeyboardEnabled,
                v -> { cfg.virtualKeyboardEnabled = v; save(); });
        toggle("steampad.keyboard.autoshow", cfg.virtualKeyboardAutoShow,
                v -> { cfg.virtualKeyboardAutoShow = v; save(); });
        toggle("steampad.keyboard.sounds", cfg.virtualKeyboardSounds,
                v -> { cfg.virtualKeyboardSounds = v; save(); });
        // Both sliders center their DEFAULT: height 20–40% (default 30% = mid), speed 0.5–1.5×
        // (default 1.0× = mid) — so there is always equal room to go up or down from stock.
        slider("steampad.keyboard.height", cfg.virtualKeyboardHeightPct * 100f, 20f, 40f, "%.0f%%",
                v -> { cfg.virtualKeyboardHeightPct = v / 100f; save(); });
        slider("steampad.keyboard.stick_speed", cfg.virtualKeyboardStickSpeed, 0.5f, 1.5f, "%.2fx",
                v -> { cfg.virtualKeyboardStickSpeed = v; save(); });
        cycling("steampad.keyboard.stick_mode", GlobalConfig.KeyboardStickMode.values(),
                cfg.virtualKeyboardStickMode == null
                        ? GlobalConfig.KeyboardStickMode.VELOCITY : cfg.virtualKeyboardStickMode,
                v -> { cfg.virtualKeyboardStickMode = v; save(); });
        // Steam-style dual-pointer typing: both sticks, one orb each, LB/RB press (see D078).
        toggle("steampad.keyboard.dual_stick", cfg.virtualKeyboardDualStick,
                v -> { cfg.virtualKeyboardDualStick = v; save(); });
        cycling("steampad.keyboard.theme", PixelTheme.values(),
                cfg.virtualKeyboardTheme == null ? PixelTheme.VANILLA : cfg.virtualKeyboardTheme,
                v -> { cfg.virtualKeyboardTheme = v; save(); });
        // Reserve space right under the theme row for a live color-swatch preview (render() draws it).
        previewBaseY = rowTop();
        advance(PREVIEW_H + 6);

        finishLayout();

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, b -> close())
                .dimensions(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());
    }

    private void save() { ConfigManager.saveGlobal(); }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        renderColumns(ctx, mouseX, mouseY);

        int y = previewBaseY - scrollY();
        if (y + PREVIEW_H >= contentTop() - 2 && y <= contentBottom() + 2) {
            PixelTheme t = cfg.virtualKeyboardTheme == null ? PixelTheme.VANILLA : cfg.virtualKeyboardTheme;
            dev.steampad.client.ui.VirtualKeyboardRenderer.renderThemePreview(
                    ctx, textRenderer, listX(), y, listW(), PREVIEW_H, t);
        }
    }

    @Override
    public void close() { client.setScreen(parent); }
}
