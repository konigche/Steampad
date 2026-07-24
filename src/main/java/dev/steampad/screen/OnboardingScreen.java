package dev.steampad.screen;

import dev.steampad.config.ConfigManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Shown ONCE EVER, the first time any controller becomes active (see {@code SteamPadClient}) —
 * SteamPad's own feature list has grown large enough (radial menu, emote wheel, zoom, third-person
 * free camera, haptics...) that most of it lives buried in Ajustes Globales until a player happens to
 * stumble on it. This is a short, skippable pointer to the highlights, each with a button straight to
 * its own settings screen — not a tutorial, just "here's what exists and where to turn it on".
 *
 * <p>{@code parent} may be null (this is opened directly over gameplay, not navigated to from another
 * screen) — {@link #close()} accounts for that.
 */
public class OnboardingScreen extends ColumnSettingsScreen {

    private final Screen parent;
    private final long handle;

    public OnboardingScreen(Screen parent, long handle) {
        super(Text.translatable("steampad.screen.onboarding.title"));
        this.parent = parent;
        this.handle = handle;
    }

    @Override
    protected void init() {
        super.init();
        beginLayout();

        section("steampad.onboarding.section.highlights");
        button("steampad.onboarding.radial", () -> client.setScreen(new RadialEditorScreen(this, handle)));
        button("steampad.onboarding.emotes", () -> client.setScreen(new GlobalSettingsScreen(this)));
        button("steampad.onboarding.zoom", () -> client.setScreen(new ControllerAdvancedSettingsScreen(this, handle)));
        button("steampad.onboarding.third_person", () -> client.setScreen(new GlobalSettingsScreen(this)));
        button("steampad.onboarding.haptics", () -> client.setScreen(new HapticsTestScreen(this)));
        button("steampad.onboarding.buttons", () -> client.setScreen(new BindingsScreen(this, handle)));

        finishLayout();

        addDrawableChild(ButtonWidget.builder(Text.translatable("steampad.onboarding.dismiss"), btn -> close())
                .dimensions(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        renderColumns(ctx, mouseX, mouseY);
    }

    @Override
    public void close() {
        ConfigManager.getGlobal().hasSeenOnboarding = true;
        ConfigManager.saveGlobal();
        if (client != null) client.setScreen(parent);
    }
}
