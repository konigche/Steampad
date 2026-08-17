package dev.steampad.screen;

import dev.steampad.haptics.HapticsController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * On-demand preview of every named haptic effect ({@link HapticsController#TEST_PRESETS}), without
 * needing to reproduce its real trigger condition in-game (take damage, walk on slime, get near a
 * boss...). Each button fires the same tier/category/intensity/duration/freqBalance the real effect
 * uses — what you feel here is what you'd feel in-game with the current vibration sliders, just
 * reachable instantly, so tuning a value no longer requires round-tripping through a whole play
 * session to feel the result.
 */
public class HapticsTestScreen extends ColumnSettingsScreen {

    private final Screen parent;

    public HapticsTestScreen(Screen parent) {
        super(Component.translatable("steampad.haptics.test.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        beginLayout();

        section("steampad.haptics.test.section.player");
        addSection(HapticsController.Category.PLAYER);
        section("steampad.haptics.test.section.world");
        addSection(HapticsController.Category.WORLD);
        section("steampad.haptics.test.section.interaction");
        addSection(HapticsController.Category.INTERACTION);
        section("steampad.haptics.test.section.gui");
        addSection(HapticsController.Category.GUI);

        finishLayout();

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, btn -> onClose())
                .bounds(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());
    }

    private void addSection(HapticsController.Category cat) {
        for (var p : HapticsController.TEST_PRESETS) {
            if (p.category() == cat) button(p.key(), () -> HapticsController.testFire(p));
        }
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        renderColumns(ctx, mouseX, mouseY);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
