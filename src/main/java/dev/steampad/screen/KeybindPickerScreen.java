package dev.steampad.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Searchable list of every keybind (vanilla + mods) for picking a radial KEYBIND slot. Each row shows
 * the keybind's name and its current keyboard key; selecting one returns its id to the caller.
 */
public class KeybindPickerScreen extends SteamPadBaseScreen {

    private static final int ROW_H = 22;

    private final Screen parent;
    private final Consumer<String> onSelect;
    private EditBox search;

    public KeybindPickerScreen(Screen parent, Consumer<String> onSelect) {
        super(Component.translatable("steampad.radial.pick_keybind"));
        this.parent = parent;
        this.onSelect = onSelect;
    }

    @Override protected int contentTop() { return HEADER_H + 34; }

    @Override
    protected void init() {
        super.init();
        resetScroll();

        int w = Math.min(360, this.width - 40);
        int x = (this.width - w) / 2;
        search = new EditBox(this.font, x, HEADER_H + 8, w, 18,
                Component.translatable("steampad.radial.search"));
        search.setResponder(t -> rebuild());
        addRenderableWidget(search);

        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());

        rebuild();
    }

    private void rebuild() {
        // Rebuild the whole widget set from scratch (search + cancel + filtered rows).
        this.clearWidgets();
        resetScroll();

        int w = Math.min(360, this.width - 40);
        int x = (this.width - w) / 2;
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());

        String q = search.getValue().toLowerCase(Locale.ROOT).trim();
        List<KeyMapping> matches = new ArrayList<>();
        for (KeyMapping kb : minecraft.options.keyMappings) {
            String name = Component.translatable(kb.getName()).getString().toLowerCase(Locale.ROOT);
            if (q.isEmpty() || name.contains(q) || kb.getName().toLowerCase(Locale.ROOT).contains(q)) {
                matches.add(kb);
            }
        }
        matches.sort((a, b) -> Component.translatable(a.getName()).getString()
                .compareToIgnoreCase(Component.translatable(b.getName()).getString()));

        int y = contentTop();
        for (KeyMapping kb : matches) {
            String id = kb.getName();
            Component label = Component.translatable(id).copy()
                    .append("  [").append(kb.getTranslatedKeyMessage()).append("]");
            Button row = Button.builder(label, b -> { onSelect.accept(id); onClose(); })
                    .bounds(x, y, w, 20).build();
            addScroll(row, y);
            y += ROW_H;
        }
        finishScroll(y);

        // Keep typing in the search field after each rebuild.
        this.setFocused(search);
        search.setFocused(true);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        renderScrollbar(ctx, (this.width + Math.min(360, this.width - 40)) / 2 + 4);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
