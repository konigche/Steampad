package dev.steampad.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Searchable list of every keybind (vanilla + mods) for picking a radial KEYBIND slot. Each row shows
 * the keybind's name and its current keyboard key; selecting one returns its id to the caller.
 */
public class KeybindPickerScreen extends SteamPadBaseScreen {

    private static final int ROW_H = 22;

    private final Screen parent;
    private final Consumer<String> onSelect;
    private TextFieldWidget search;

    public KeybindPickerScreen(Screen parent, Consumer<String> onSelect) {
        super(Text.translatable("steampad.radial.pick_keybind"));
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
        search = new TextFieldWidget(this.textRenderer, x, HEADER_H + 8, w, 18,
                Text.translatable("steampad.radial.search"));
        search.setChangedListener(t -> rebuild());
        addDrawableChild(search);

        addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> close())
                .dimensions(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());

        rebuild();
    }

    private void rebuild() {
        // Rebuild the whole widget set from scratch (search + cancel + filtered rows).
        this.clearChildren();
        resetScroll();

        int w = Math.min(360, this.width - 40);
        int x = (this.width - w) / 2;
        addDrawableChild(search);
        addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> close())
                .dimensions(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());

        String q = search.getText().toLowerCase(Locale.ROOT).trim();
        List<KeyBinding> matches = new ArrayList<>();
        for (KeyBinding kb : client.options.allKeys) {
            String name = Text.translatable(kb.getId()).getString().toLowerCase(Locale.ROOT);
            if (q.isEmpty() || name.contains(q) || kb.getId().toLowerCase(Locale.ROOT).contains(q)) {
                matches.add(kb);
            }
        }
        matches.sort((a, b) -> Text.translatable(a.getId()).getString()
                .compareToIgnoreCase(Text.translatable(b.getId()).getString()));

        int y = contentTop();
        for (KeyBinding kb : matches) {
            String id = kb.getId();
            Text label = Text.translatable(id).copy()
                    .append("  [").append(kb.getBoundKeyLocalizedText()).append("]");
            ButtonWidget row = ButtonWidget.builder(label, b -> { onSelect.accept(id); close(); })
                    .dimensions(x, y, w, 20).build();
            addScroll(row, y);
            y += ROW_H;
        }
        finishScroll(y);

        // Keep typing in the search field after each rebuild.
        this.setFocused(search);
        search.setFocused(true);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        renderScrollbar(ctx, (this.width + Math.min(360, this.width - 40)) / 2 + 4);
    }

    @Override
    public void close() { client.setScreen(parent); }
}
