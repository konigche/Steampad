package dev.steampad.screen;

import dev.steampad.input.GamepadBinds;
import dev.steampad.input.SteamSlotDispatcher;
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
 * Searchable list of every assignable Steam Input slot target: SteamPad's own internal actions
 * (Menú Radial, Zoom, and everything else in {@link GamepadBinds.Bind} — feedback: "esas ranuras de
 * Steaminput no puedo asignar menu radial ni zoom, debemos poder asignar cualquier cosa") FIRST, then
 * every vanilla/mod keybind ({@link KeybindPickerScreen}'s list) below. Selecting a SteamPad action
 * returns {@link SteamSlotDispatcher#encodeBind}'s form; selecting a keybind returns its bare id —
 * both handled by the caller exactly as before (a plain string in the slot's config map). Section
 * dividers are plain disabled buttons (not real rows) so they scroll/position with everything else
 * for free, no custom header-drawing math needed.
 */
public class SteamSlotTargetPickerScreen extends SteamPadBaseScreen {

    private static final int ROW_H = 22;

    private final Screen parent;
    private final Consumer<String> onSelect;
    private TextFieldWidget search;

    public SteamSlotTargetPickerScreen(Screen parent, Consumer<String> onSelect) {
        super(Text.translatable("steampad.slot.pick_target"));
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
        this.clearChildren();
        resetScroll();

        int w = Math.min(360, this.width - 40);
        int x = (this.width - w) / 2;
        addDrawableChild(search);
        addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> close())
                .dimensions(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());

        String q = search.getText().toLowerCase(Locale.ROOT).trim();
        int y = contentTop();

        List<GamepadBinds.Bind> binds = new ArrayList<>();
        for (GamepadBinds.Bind b : GamepadBinds.Bind.values()) {
            String name = Text.translatable(b.labelKey).getString().toLowerCase(Locale.ROOT);
            if (q.isEmpty() || name.contains(q)) binds.add(b);
        }
        if (!binds.isEmpty()) {
            y = addHeader(Text.translatable("steampad.slot.section.steampad"), x, y, w);
            for (GamepadBinds.Bind b : binds) {
                String target = SteamSlotDispatcher.encodeBind(b);
                ButtonWidget row = ButtonWidget.builder(Text.translatable(b.labelKey),
                        btn -> { onSelect.accept(target); close(); }).dimensions(x, y, w, 20).build();
                addScroll(row, y);
                y += ROW_H;
            }
        }

        List<KeyBinding> matches = new ArrayList<>();
        for (KeyBinding kb : client.options.allKeys) {
            String name = Text.translatable(kb.getId()).getString().toLowerCase(Locale.ROOT);
            if (q.isEmpty() || name.contains(q) || kb.getId().toLowerCase(Locale.ROOT).contains(q)) {
                matches.add(kb);
            }
        }
        matches.sort((a, b) -> Text.translatable(a.getId()).getString()
                .compareToIgnoreCase(Text.translatable(b.getId()).getString()));
        if (!matches.isEmpty()) {
            y = addHeader(Text.translatable("steampad.slot.section.keybinds"), x, y, w);
            for (KeyBinding kb : matches) {
                String id = kb.getId();
                Text label = Text.translatable(id).copy()
                        .append("  [").append(kb.getBoundKeyLocalizedText()).append("]");
                ButtonWidget row = ButtonWidget.builder(label, b -> { onSelect.accept(id); close(); })
                        .dimensions(x, y, w, 20).build();
                addScroll(row, y);
                y += ROW_H;
            }
        }

        finishScroll(y);
        this.setFocused(search);
        search.setFocused(true);
    }

    /** A non-interactive section divider — a disabled button, so it scrolls with the real rows without
     *  any custom header-position math. */
    private int addHeader(Text label, int x, int y, int w) {
        ButtonWidget header = ButtonWidget.builder(label, b -> {}).dimensions(x, y, w, 16).build();
        header.active = false;
        addScroll(header, y);
        return y + 18;
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
