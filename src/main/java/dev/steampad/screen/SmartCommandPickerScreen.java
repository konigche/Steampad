package dev.steampad.screen;

import dev.steampad.radial.SmartCommand;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Picker for a radial SMART_COMMAND slot: the built-in commands from {@link SmartCommand}, each with
 * its one-line description underneath. Deliberately a plain list with no search box — unlike the
 * keybind and item pickers this is a short, curated set, so filtering would only add a step.
 */
public class SmartCommandPickerScreen extends SteamPadBaseScreen {

    private static final int ROW_H = 34;

    private record Desc(int y, int x, Component text) {}

    private final Screen parent;
    private final Consumer<String> onSelect;
    private final java.util.List<Desc> descriptions = new java.util.ArrayList<>();

    public SmartCommandPickerScreen(Screen parent, Consumer<String> onSelect) {
        super(Component.translatable("steampad.radial.pick_smartcmd"));
        this.parent = parent;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();
        resetScroll();
        descriptions.clear();   // init() also runs on every resize/rebuild

        int w = Math.min(300, this.width - 40);
        int x = (this.width - w) / 2;
        int y = contentTop();
        for (SmartCommand cmd : SmartCommand.values()) {
            Button row = Button.builder(cmd.label(), b -> { onSelect.accept(cmd.id()); onClose(); })
                    .bounds(x, y, w, 20)
                    .tooltip(Tooltip.create(cmd.description()))
                    .build();
            addScroll(row, y);
            descriptions.add(new Desc(y + 21, x, cmd.description()));
            y += ROW_H;
        }
        finishScroll(y);

        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        // The description under each row — always visible, so the difference between the entries is
        // readable with a gamepad too (a tooltip alone needs a hover or a focus).
        for (Desc d : descriptions) {
            if (!isInViewport(d.y(), 10)) continue;
            ctx.drawString(font, d.text(), d.x() + 2, d.y() - scrollY(), 0xFFAAAAAA, false);
        }
        renderScrollbar(ctx, (this.width + Math.min(300, this.width - 40)) / 2 + 4);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
