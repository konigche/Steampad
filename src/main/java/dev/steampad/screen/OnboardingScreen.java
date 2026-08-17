package dev.steampad.screen;

import dev.steampad.config.ConfigManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shown ONCE EVER, the first time any controller becomes active (see {@code SteamPadClient}) —
 * SteamPad's own feature list has grown large enough (radial menu, emote wheel, zoom, third-person
 * free camera, haptics...) that most of it lives buried in Ajustes Globales until a player happens to
 * stumble on it. This is a short, skippable pointer to the highlights, each with a button straight to
 * its own settings screen — not a tutorial, just "here's what exists and where to turn it on".
 *
 * <p>{@code parent} may be null (this is opened directly over gameplay, not navigated to from another
 * screen) — {@link #onClose()} accounts for that.
 */
public class OnboardingScreen extends ColumnSettingsScreen {

    private final Screen parent;
    private final long handle;

    public OnboardingScreen(Screen parent, long handle) {
        super(Component.translatable("steampad.screen.onboarding.title"));
        this.parent = parent;
        this.handle = handle;
    }

    @Override
    protected void init() {
        super.init();
        beginLayout();

        section("steampad.onboarding.section.highlights");
        button("steampad.onboarding.radial", () -> minecraft.setScreen(new RadialEditorScreen(this, handle)));
        button("steampad.onboarding.emotes", () -> minecraft.setScreen(new GlobalSettingsScreen(this)));
        button("steampad.onboarding.zoom", () -> minecraft.setScreen(new ControllerAdvancedSettingsScreen(this, handle)));
        button("steampad.onboarding.third_person", () -> minecraft.setScreen(new GlobalSettingsScreen(this)));
        button("steampad.onboarding.haptics", () -> minecraft.setScreen(new HapticsTestScreen(this)));
        button("steampad.onboarding.buttons", () -> minecraft.setScreen(new BindingsScreen(this, handle)));

        finishLayout();

        addRenderableWidget(Button.builder(Component.translatable("steampad.onboarding.dismiss"), btn -> onClose())
                .bounds(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        renderColumns(ctx, mouseX, mouseY);
    }

    @Override
    public void onClose() {
        ConfigManager.getGlobal().hasSeenOnboarding = true;
        ConfigManager.saveGlobal();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
