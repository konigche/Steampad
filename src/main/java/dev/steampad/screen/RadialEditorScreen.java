package dev.steampad.screen;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.RadialConfig;
import dev.steampad.radial.RadialActionType;
import dev.steampad.radial.RadialRenderer;
import dev.steampad.radial.RadialSlot;
import dev.steampad.service.UiSoundService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Visual editor for the radial menu (up to 12 slots). Pick the slot count, pick a slot, then set its
 * type, value (with a context-aware field/picker that changes per type — keybind picker, command text,
 * etc.), a name, an icon (from the full Minecraft item list), and the trigger. Live preview on the
 * right. Reworked to match the reference radial mod's per-slot editor.
 */
public class RadialEditorScreen extends SteamPadBaseScreen {

    private final Screen parent;
    private final long handle;
    private RadialConfig cfg;
    private int selected;
    /** Which wheel is being edited (0-based page, E10). */
    private int page;

    private record Label(int y, Text text) {}
    private final List<Label> labels = new ArrayList<>();

    public RadialEditorScreen(Screen parent, long handle) { this(parent, handle, 0); }

    public RadialEditorScreen(Screen parent, long handle, int initialSlot) {
        super(Text.translatable("steampad.screen.radial_editor.title"));
        this.parent = parent;
        this.handle = handle;
        this.selected = Math.max(0, initialSlot);
        // Start on the wheel currently shown in-game (opening the editor from the wheel with LT).
        this.page = dev.steampad.radial.RadialMenuController.getPage();
    }

    @Override
    protected void init() {
        super.init();
        labels.clear();
        this.cfg = ConfigManager.getRadialConfig(handle);
        cfg.normalize();
        if (page >= cfg.wheelCount()) page = cfg.wheelCount() - 1;
        if (selected >= cfg.slotCountFor(page)) selected = cfg.slotCountFor(page) - 1;

        int colX = 14, colW = 200;
        int y = HEADER_H + 8;

        // Row 1 — WHEEL management only (E10), clearly labeled: which wheel is being edited
        // ("Rueda 1/3"), ◀/▶ to switch, ＋ to add one, ✕ to remove this one. Slot count lives in
        // its own labeled row below — mixing both in one row read as "add a wheel? add a slot?".
        int wheels = cfg.wheelCount();
        labels.add(new Label(y - 1, Text.translatable("steampad.radial.wheel")
                .copy().append(Text.literal("  " + (page + 1) + "/" + wheels))));
        y += 10;
        ButtonWidget prevW = ButtonWidget.builder(Text.literal("◀"), b -> switchWheel(-1))
                .dimensions(colX, y, 44, 18)
                .tooltip(Tooltip.of(Text.translatable("steampad.radial.wheel.desc"))).build();
        prevW.active = wheels > 1;
        addDrawableChild(prevW);
        ButtonWidget nextW = ButtonWidget.builder(Text.literal("▶"), b -> switchWheel(+1))
                .dimensions(colX + 48, y, 44, 18)
                .tooltip(Tooltip.of(Text.translatable("steampad.radial.wheel.desc"))).build();
        nextW.active = wheels > 1;
        addDrawableChild(nextW);
        ButtonWidget addW = ButtonWidget.builder(Text.translatable("steampad.radial.wheel.add_short"), b -> addWheel())
                .dimensions(colX + colW - 104, y, 50, 18)
                .tooltip(Tooltip.of(Text.translatable("steampad.radial.wheel.add"))).build();
        addW.active = wheels < RadialConfig.MAX_WHEELS;
        addDrawableChild(addW);
        ButtonWidget delW = ButtonWidget.builder(Text.translatable("steampad.radial.wheel.remove_short"), b -> openDeleteWheelPicker())
                .dimensions(colX + colW - 50, y, 50, 18)
                .tooltip(Tooltip.of(Text.translatable("steampad.radial.wheel.remove"))).build();
        delW.active = wheels > 1;
        addDrawableChild(delW);
        y += 22;

        // Row 2 — SLOT COUNT of this wheel, its own labeled row ("Espacios: N", − / +).
        labels.add(new Label(y - 1, Text.translatable("steampad.radial.slot_count", cfg.slotCountFor(page))));
        y += 10;
        addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> changeSlotCount(-1))
                .dimensions(colX, y, 44, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> changeSlotCount(+1))
                .dimensions(colX + 48, y, 44, 18).build());
        y += 22;

        // Slot picker (rows of 6).
        for (int i = 0; i < cfg.slotCountFor(page); i++) {
            final int idx = i;
            ButtonWidget b = ButtonWidget.builder(Text.literal(String.valueOf(i + 1)), btn -> {
                UiSoundService.playNavigate();
                selected = idx;
                clearAndInit();
            }).dimensions(colX + (i % 6) * 33, y + (i / 6) * 20, 30, 18).build();
            if (i == selected) b.active = false;
            addDrawableChild(b);
        }
        y += ((cfg.slotCountFor(page) + 5) / 6) * 20 + 4;

        RadialConfig.SlotConfig slot = cfg.slotsFor(page).get(selected);
        RadialActionType type = parseType(slot.type);

        // Type.
        labels.add(new Label(y - 1, Text.translatable("steampad.radial.type")));
        y += 10;
        addDrawableChild(CyclingButtonWidget.<RadialActionType>builder(
                v -> Text.translatable("steampad.radial.type." + v.name().toLowerCase()))
                .values(RadialActionType.values())
                .initially(type)
                // omitKeyText → the control shows ONLY the type name ("Comando"), not "Tipo: Comando"
                // (the "Tipo" label is already drawn above). Per-type tooltip describes what it does,
                // shown on hover (mouse) and on focus (D-pad).
                .omitKeyText()
                .tooltip(v -> Tooltip.of(Text.translatable("steampad.radial.type." + v.name().toLowerCase() + ".desc")))
                .build(colX, y, colW, 18, Text.translatable("steampad.radial.type"),
                        (btn, v) -> { slot.type = v.name(); slot.action = ""; save(); clearAndInit(); }));
        y += 22;

        // Name.
        labels.add(new Label(y - 1, Text.translatable("steampad.radial.label")));
        y += 10;
        TextFieldWidget name = new TextFieldWidget(this.textRenderer, colX, y, colW, 18,
                Text.translatable("steampad.radial.label"));
        name.setMaxLength(48);
        name.setText(slot.displayName);
        name.setChangedListener(t -> { slot.displayName = t; save(); });
        addDrawableChild(name);
        y += 22;

        // Value — context-aware per type.
        y = addValueWidget(colX, colW, y, slot, type);

        // Icon picker (opens the full Minecraft item list).
        labels.add(new Label(y - 1, Text.translatable("steampad.radial.icon")));
        y += 10;
        String iconLabel = slot.iconValue.isBlank()
                ? Text.translatable("steampad.radial.icon_pick").getString()
                : slot.iconValue;
        addDrawableChild(ButtonWidget.builder(Text.literal(iconLabel), btn ->
                client.setScreen(new IconPickerScreen(this, id -> {
                    slot.iconValue = id;
                    slot.iconType = "ITEM";
                    save();
                }))).dimensions(colX, y, colW - 44, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("✕"), btn -> {
            slot.iconValue = ""; slot.iconType = "NONE"; save(); clearAndInit();
        }).dimensions(colX + colW - 40, y, 40, 18).build());
        y += 22;

        // Trigger.
        labels.add(new Label(y - 1, Text.translatable("steampad.radial.trigger")));
        y += 10;
        addDrawableChild(CyclingButtonWidget.<Boolean>builder(
                v -> Text.translatable(v ? "steampad.radial.trigger.on_release" : "steampad.radial.trigger.on_click"))
                .values(Boolean.TRUE, Boolean.FALSE)
                .initially("ON_RELEASE".equals(slot.trigger))
                .build(colX, y, colW, 18, Text.translatable("steampad.radial.trigger"),
                        (btn, v) -> { slot.trigger = v ? "ON_RELEASE" : "ON_CLICK"; save(); }));

        // Footer: Appearance (theme/size/backdrop live in their own screen) + Done.
        addDrawableChild(ButtonWidget.builder(Text.translatable("steampad.radial.style"), btn -> {
            UiSoundService.playNavigate();
            client.setScreen(new RadialStyleScreen(this, handle));
        }).dimensions(this.width / 2 - 115, this.height - FOOTER_H + 7, 110, 20).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, btn -> {
            UiSoundService.playSelect();
            ConfigManager.saveRadialConfig(handle);
            close();
        }).dimensions(this.width / 2 + 5, this.height - FOOTER_H + 7, 110, 20).build());
    }

    /** Adds the value editor appropriate to the slot's type (the picker/field changes with the type). */
    private int addValueWidget(int colX, int colW, int y, RadialConfig.SlotConfig slot, RadialActionType type) {
        switch (type) {
            case KEYBIND, MALILIB_KEYBIND -> {
                labels.add(new Label(y - 1, Text.translatable("steampad.radial.value.keybind")));
                y += 10;
                String cur = slot.action.isBlank()
                        ? Text.translatable("steampad.radial.value.pick").getString() : slot.action;
                addDrawableChild(ButtonWidget.builder(Text.literal(cur), btn ->
                        client.setScreen(new KeybindPickerScreen(this, id -> {
                            slot.action = id; save();
                        }))).dimensions(colX, y, colW, 18).build());
                y += 22;
            }
            case CHAT_COMMAND -> {
                labels.add(new Label(y - 1, Text.translatable("steampad.radial.value.command")));
                y += 10;
                y = addTextValue(colX, colW, y, slot);
            }
            case SCREEN_SHORTCUT -> {
                labels.add(new Label(y - 1, Text.translatable("steampad.radial.value.screen")));
                y += 10;
                y = addTextValue(colX, colW, y, slot);
            }
            case SUBMENU -> {
                labels.add(new Label(y - 1, Text.translatable("steampad.radial.value.submenu")));
                y += 10;
                y = addTextValue(colX, colW, y, slot);
            }
            default -> { /* NONE — no value field */ }
        }
        return y;
    }

    private int addTextValue(int colX, int colW, int y, RadialConfig.SlotConfig slot) {
        TextFieldWidget value = new TextFieldWidget(this.textRenderer, colX, y, colW, 18,
                Text.translatable("steampad.radial.action"));
        value.setMaxLength(256);
        value.setText(slot.action);
        value.setChangedListener(t -> { slot.action = t; save(); });
        addDrawableChild(value);
        return y + 22;
    }

    private void changeSlotCount(int delta) {
        int n = cfg.slotCountFor(page) + delta;
        if (n < 2 || n > RadialConfig.MAX_SLOTS) return;
        UiSoundService.playNavigate();
        cfg.setSlotCountFor(page, n);
        if (selected >= n) selected = n - 1;
        save();
        clearAndInit();
    }

    private void switchWheel(int dir) {
        int n = cfg.wheelCount();
        if (n <= 1) return;
        UiSoundService.playNavigate();
        page = Math.floorMod(page + dir, n);
        selected = 0;
        clearAndInit();
    }

    private void addWheel() {
        int idx = cfg.addWheel();
        if (idx < 0) return;
        UiSoundService.playSelect();
        page = idx;
        selected = 0;
        save();
        clearAndInit();
    }

    /**
     * Opens the wheel-delete picker (feedback: "poder seleccionar que rueda quieres eliminar... si
     * esta con atajos preguntar") instead of deleting whatever wheel this editor happens to be
     * showing right now — the old behavior silently deleted a wheel WITH shortcuts while an empty one
     * sat untouched elsewhere in the list (the reported bug).
     */
    private void openDeleteWheelPicker() {
        client.setScreen(new RadialWheelDeleteScreen(this, handle));
    }

    /** Called by {@link RadialWheelDeleteScreen} after it deletes a wheel — refresh our state. */
    void onWheelDeleted() {
        page = Math.min(page, cfg.wheelCount() - 1);
        selected = 0;
        clearAndInit();
    }

    private RadialActionType parseType(String s) {
        try { return RadialActionType.valueOf(s); } catch (Exception e) { return RadialActionType.NONE; }
    }

    private List<RadialSlot> previewSlots() {
        List<RadialSlot> out = new ArrayList<>();
        for (int i = 0; i < cfg.slotCountFor(page); i++) {
            RadialConfig.SlotConfig sc = cfg.slotsFor(page).get(i);
            out.add(new RadialSlot(parseType(sc.type), sc.action, sc.iconType, sc.iconValue,
                    sc.displayName, "ON_RELEASE".equals(sc.trigger)));
        }
        return out;
    }

    private void save() { ConfigManager.saveRadialConfig(handle); }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderChrome(context);
        // Live preview on the right (centered in the right third).
        RadialRenderer.render(context, this.width * 3 / 4, this.height / 2, previewSlots(), selected, handle,
                cfg.wheelCount(), page);
        // Field labels.
        for (Label l : labels) {
            context.drawText(textRenderer, l.text(), 14, l.y(), ACCENT, true);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() { client.setScreen(parent); }
}
