package dev.steampad.screen;

import dev.steampad.input.GamepadBinds;
import dev.steampad.input.SteamSlotDispatcher;
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
    private EditBox search;

    public SteamSlotTargetPickerScreen(Screen parent, Consumer<String> onSelect) {
        super(Component.translatable("steampad.slot.pick_target"));
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
        this.clearWidgets();
        resetScroll();

        int w = Math.min(360, this.width - 40);
        int x = (this.width - w) / 2;
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());

        String q = search.getValue().toLowerCase(Locale.ROOT).trim();
        int y = contentTop();

        List<GamepadBinds.Bind> binds = new ArrayList<>();
        for (GamepadBinds.Bind b : GamepadBinds.Bind.values()) {
            String name = Component.translatable(b.labelKey).getString().toLowerCase(Locale.ROOT);
            if (q.isEmpty() || name.contains(q)) binds.add(b);
        }
        if (!binds.isEmpty()) {
            y = addHeader(Component.translatable("steampad.slot.section.steampad"), x, y, w);
            for (GamepadBinds.Bind b : binds) {
                String target = SteamSlotDispatcher.encodeBind(b);
                Button row = Button.builder(Component.translatable(b.labelKey),
                        btn -> { onSelect.accept(target); onClose(); }).bounds(x, y, w, 20).build();
                addScroll(row, y);
                y += ROW_H;
            }
        }

        List<KeyMapping> matches = new ArrayList<>();
        for (KeyMapping kb : minecraft.options.keyMappings) {
            String name = Component.translatable(kb.getName()).getString().toLowerCase(Locale.ROOT);
            if (q.isEmpty() || name.contains(q) || kb.getName().toLowerCase(Locale.ROOT).contains(q)) {
                matches.add(kb);
            }
        }
        matches.sort((a, b) -> Component.translatable(a.getName()).getString()
                .compareToIgnoreCase(Component.translatable(b.getName()).getString()));
        if (!matches.isEmpty()) {
            y = addHeader(Component.translatable("steampad.slot.section.keybinds"), x, y, w);
            for (KeyMapping kb : matches) {
                String id = kb.getName();
                Component label = Component.translatable(id).copy()
                        .append("  [").append(kb.getTranslatedKeyMessage()).append("]");
                Button row = Button.builder(label, b -> { onSelect.accept(id); onClose(); })
                        .bounds(x, y, w, 20).build();
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
    private int addHeader(Component label, int x, int y, int w) {
        Button header = Button.builder(label, b -> {}).bounds(x, y, w, 16).build();
        header.active = false;
        addScroll(header, y);
        return y + 18;
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
