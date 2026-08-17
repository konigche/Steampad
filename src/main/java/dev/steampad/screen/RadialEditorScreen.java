package dev.steampad.screen;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.RadialConfig;
import dev.steampad.radial.RadialActionType;
import dev.steampad.radial.RadialRenderer;
import dev.steampad.radial.RadialSlot;
import dev.steampad.service.UiSoundService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Visual editor for the radial menu (up to 12 slots). Pick the slot count, pick a slot, then set its
 * type, value (with a context-aware field/picker that changes per type — keybind picker, command text,
 * etc.), a name, an icon (from the full Minecraft item list), and the trigger. Live preview on the
 * right. Reworked to match the reference radial mod's per-slot editor.
 */
public class RadialEditorScreen extends SteamPadBaseScreen {

    // Fixed pixel geometry of the left (editor) column — NOT a fraction of screen width, so the
    // scrollbar (anchored off these, see listRightX()) stays right next to the settings instead of
    // drifting into the preview wheel on wider screens (bug: scrollbar rendered at this.width*3/4,
    // which only matched the column's right edge by coincidence on narrow/default window sizes).
    private static final int COL_X = 14;
    private static final int COL_W = 200;

    private final Screen parent;
    private final long handle;
    private RadialConfig cfg;
    private int selected;
    /** Which wheel is being edited (0-based page, E10). */
    private int page;

    private record Label(int y, Component text) {}
    private final List<Label> labels = new ArrayList<>();

    public RadialEditorScreen(Screen parent, long handle) { this(parent, handle, 0); }

    public RadialEditorScreen(Screen parent, long handle, int initialSlot) {
        super(Component.translatable("steampad.screen.radial_editor.title"));
        this.parent = parent;
        this.handle = handle;
        this.selected = Math.max(0, initialSlot);
        // Start on the wheel currently shown in-game (opening the editor from the wheel with LT).
        this.page = dev.steampad.radial.RadialMenuController.getPage();
    }

    @Override
    protected void init() {
        super.init();
        resetScroll();
        labels.clear();
        this.cfg = ConfigManager.getRadialConfig(handle);
        cfg.normalize();
        if (page >= cfg.wheelCount()) page = cfg.wheelCount() - 1;
        if (selected >= cfg.slotCountFor(page)) selected = cfg.slotCountFor(page) - 1;

        int colX = COL_X, colW = COL_W;
        int y = contentTop();

        // Row 1 — WHEEL management only (E10), clearly labeled: which wheel is being edited
        // ("Rueda 1/3"), ◀/▶ to switch, ＋ to add one, ✕ to remove this one. Slot count lives in
        // its own labeled row below — mixing both in one row read as "add a wheel? add a slot?".
        int wheels = cfg.wheelCount();
        labels.add(new Label(y - 1, Component.translatable("steampad.radial.wheel")
                .copy().append(Component.literal("  " + (page + 1) + "/" + wheels))));
        y += 10;
        Button prevW = Button.builder(Component.literal("◀"), b -> switchWheel(-1))
                .bounds(colX, y, 44, 18)
                .tooltip(Tooltip.create(Component.translatable("steampad.radial.wheel.desc"))).build();
        prevW.active = wheels > 1;
        addScroll(prevW, y);
        Button nextW = Button.builder(Component.literal("▶"), b -> switchWheel(+1))
                .bounds(colX + 48, y, 44, 18)
                .tooltip(Tooltip.create(Component.translatable("steampad.radial.wheel.desc"))).build();
        nextW.active = wheels > 1;
        addScroll(nextW, y);
        // Right = add (+), left of the pair = remove (−) — D111: "derecha es más y quitar es izquierda".
        Button delW = Button.builder(Component.translatable("steampad.radial.wheel.remove_short"), b -> openDeleteWheelPicker())
                .bounds(colX + colW - 104, y, 50, 18)
                .tooltip(Tooltip.create(Component.translatable("steampad.radial.wheel.remove"))).build();
        delW.active = wheels > 1;
        addScroll(delW, y);
        Button addW = Button.builder(Component.translatable("steampad.radial.wheel.add_short"), b -> addWheel())
                .bounds(colX + colW - 50, y, 50, 18)
                .tooltip(Tooltip.create(Component.translatable("steampad.radial.wheel.add"))).build();
        addW.active = wheels < RadialConfig.MAX_WHEELS;
        addScroll(addW, y);
        y += 22;

        // Row 2 — SLOT COUNT of this wheel, its own labeled row ("Espacios: N", − / +).
        labels.add(new Label(y - 1, Component.translatable("steampad.radial.slot_count", cfg.slotCountFor(page))));
        y += 10;
        addScroll(Button.builder(Component.literal("−"), b -> changeSlotCount(-1))
                .bounds(colX, y, 44, 18).build(), y);
        addScroll(Button.builder(Component.literal("+"), b -> changeSlotCount(+1))
                .bounds(colX + 48, y, 44, 18).build(), y);
        y += 22;

        // Slot picker (rows of 6).
        for (int i = 0; i < cfg.slotCountFor(page); i++) {
            final int idx = i;
            int rowY = y + (i / 6) * 20;
            Button b = Button.builder(Component.literal(String.valueOf(i + 1)), btn -> {
                UiSoundService.playNavigate();
                selected = idx;
                rebuildWidgets();
            }).bounds(colX + (i % 6) * 33, rowY, 30, 18).build();
            if (i == selected) b.active = false;
            addScroll(b, rowY);
        }
        y += ((cfg.slotCountFor(page) + 5) / 6) * 20 + 4;

        RadialConfig.SlotConfig slot = cfg.slotsFor(page).get(selected);
        RadialActionType type = parseType(slot.type);

        // Type.
        labels.add(new Label(y - 1, Component.translatable("steampad.radial.type")));
        y += 10;
        addScroll(CycleButton.<RadialActionType>builder(
                v -> Component.translatable("steampad.radial.type." + v.name().toLowerCase()))
                // ITEM is internal-only (dev.steampad.itemradial's dynamic wheels) — never a real
                // user-configurable slot type, so it's excluded from this picker entirely.
                // EMOTE belongs to the DEDICATED Emote Wheel only (EmoteWheelScreen.assignAndPersist,
                // which opens the Emote Library picker to fill slot.action with a real emote id).
                // This generic editor has no such picker wired into addValueWidget below, so picking
                // EMOTE here left slot.action permanently empty — a dead end with no way to ever
                // choose WHICH emote (reported: "no debería ser elegible en esta rueda"). Excluded the
                // same way ITEM already is; a slot some other path already set to EMOTE (there isn't
                // one today, since only EmoteWheelScreen ever writes it, and that screen has its own
                // separate config list — see RadialConfig.emoteWheels vs .wheels) would still render
                // and function correctly, it just can't be CHOSEN from this dropdown.
                .withValues(java.util.Arrays.stream(RadialActionType.values())
                        .filter(t -> t != RadialActionType.ITEM && t != RadialActionType.EMOTE)
                        .toArray(RadialActionType[]::new))
                .withInitialValue(type)
                // omitKeyText → the control shows ONLY the type name ("Comando"), not "Tipo: Comando"
                // (the "Tipo" label is already drawn above). Per-type tooltip describes what it does,
                // shown on hover (mouse) and on focus (D-pad).
                .displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.translatable("steampad.radial.type." + v.name().toLowerCase() + ".desc")))
                .create(colX, y, colW, 18, Component.translatable("steampad.radial.type"),
                        (btn, v) -> { slot.type = v.name(); slot.action = ""; save(); rebuildWidgets(); }), y);
        y += 22;

        // Name.
        labels.add(new Label(y - 1, Component.translatable("steampad.radial.label")));
        y += 10;
        EditBox name = new EditBox(this.font, colX, y, colW, 18,
                Component.translatable("steampad.radial.label"));
        name.setMaxLength(48);
        name.setValue(slot.displayName);
        name.setResponder(t -> { slot.displayName = t; save(); });
        addScroll(name, y);
        y += 22;

        // Value — context-aware per type.
        y = addValueWidget(colX, colW, y, slot, type);

        // Icon picker (opens the full Minecraft item list).
        labels.add(new Label(y - 1, Component.translatable("steampad.radial.icon")));
        y += 10;
        String iconLabel = slot.iconValue.isBlank()
                ? Component.translatable("steampad.radial.icon_pick").getString()
                : slot.iconValue;
        addScroll(Button.builder(Component.literal(iconLabel), btn ->
                minecraft.setScreen(new IconPickerScreen(this, id -> {
                    slot.iconValue = id;
                    slot.iconType = "ITEM";
                    save();
                }))).bounds(colX, y, colW - 44, 18).build(), y);
        addScroll(Button.builder(Component.literal("✕"), btn -> {
            slot.iconValue = ""; slot.iconType = "NONE"; save(); rebuildWidgets();
        }).bounds(colX + colW - 40, y, 40, 18).build(), y);
        y += 22;

        // Trigger.
        labels.add(new Label(y - 1, Component.translatable("steampad.radial.trigger")));
        y += 10;
        addScroll(CycleButton.<Boolean>builder(
                v -> Component.translatable(v ? "steampad.radial.trigger.on_release" : "steampad.radial.trigger.on_click"))
                .withValues(Boolean.TRUE, Boolean.FALSE)
                .withInitialValue("ON_RELEASE".equals(slot.trigger))
                .create(colX, y, colW, 18, Component.translatable("steampad.radial.trigger"),
                        (btn, v) -> { slot.trigger = v ? "ON_RELEASE" : "ON_CLICK"; save(); }), y);
        y += 22;
        finishScroll(y);

        // Footer: Appearance (theme/size/backdrop live in their own screen) + Done — fixed, never
        // scrolls (feedback: "no tienes puesto una barra de scroll... en pantallas pequeñas no puedo
        // bajar para ver todas las opciones" — everything above now scrolls as one column).
        addRenderableWidget(Button.builder(Component.translatable("steampad.radial.style"), btn -> {
            UiSoundService.playNavigate();
            minecraft.setScreen(new RadialStyleScreen(this, handle));
        }).bounds(this.width / 2 - 115, this.height - FOOTER_H + 7, 110, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> {
            UiSoundService.playSelect();
            ConfigManager.saveRadialConfig(handle);
            onClose();
        }).bounds(this.width / 2 + 5, this.height - FOOTER_H + 7, 110, 20).build());
    }

    /** Adds the value editor appropriate to the slot's type (the picker/field changes with the type). */
    private int addValueWidget(int colX, int colW, int y, RadialConfig.SlotConfig slot, RadialActionType type) {
        switch (type) {
            case KEYBIND, MALILIB_KEYBIND -> {
                labels.add(new Label(y - 1, Component.translatable("steampad.radial.value.keybind")));
                y += 10;
                String cur = slot.action.isBlank()
                        ? Component.translatable("steampad.radial.value.pick").getString() : slot.action;
                addScroll(Button.builder(Component.literal(cur), btn ->
                        minecraft.setScreen(new KeybindPickerScreen(this, id -> {
                            slot.action = id; save();
                        }))).bounds(colX, y, colW, 18).build(), y);
                y += 22;
            }
            case CHAT_COMMAND -> {
                labels.add(new Label(y - 1, Component.translatable("steampad.radial.value.command")));
                y += 10;
                y = addTextValue(colX, colW, y, slot);
            }
            case SMART_COMMAND -> {
                // Same shape as KEYBIND: a picker, not a free-text field — the value is one of a fixed
                // set of ids (see SmartCommand), so typing it by hand could only ever be a typo.
                labels.add(new Label(y - 1, Component.translatable("steampad.radial.value.smartcmd")));
                y += 10;
                dev.steampad.radial.SmartCommand cur = dev.steampad.radial.SmartCommand.byId(slot.action);
                Component label = cur != null ? cur.label()
                        : Component.translatable("steampad.radial.value.pick");
                addScroll(Button.builder(label, btn ->
                        minecraft.setScreen(new SmartCommandPickerScreen(this, id -> {
                            slot.action = id;
                            // Give a fresh slot a sensible name straight away, the way the wheel shows
                            // it — otherwise a slot picked here draws blank until the user types one.
                            var picked = dev.steampad.radial.SmartCommand.byId(id);
                            if (slot.displayName.isBlank() && picked != null) {
                                slot.displayName = picked.label().getString();
                            }
                            save();
                        }))).bounds(colX, y, colW, 18).build(), y);
                y += 22;
            }
            case SCREEN_SHORTCUT -> {
                labels.add(new Label(y - 1, Component.translatable("steampad.radial.value.screen")));
                y += 10;
                y = addTextValue(colX, colW, y, slot);
            }
            case SUBMENU -> {
                labels.add(new Label(y - 1, Component.translatable("steampad.radial.value.submenu")));
                y += 10;
                y = addTextValue(colX, colW, y, slot);
            }
            default -> { /* NONE — no value field */ }
        }
        return y;
    }

    private int addTextValue(int colX, int colW, int y, RadialConfig.SlotConfig slot) {
        EditBox value = new EditBox(this.font, colX, y, colW, 18,
                Component.translatable("steampad.radial.action"));
        value.setMaxLength(256);
        value.setValue(slot.action);
        value.setResponder(t -> { slot.action = t; save(); });
        addScroll(value, y);
        return y + 22;
    }

    private void changeSlotCount(int delta) {
        int n = cfg.slotCountFor(page) + delta;
        if (n < 2 || n > RadialConfig.MAX_SLOTS) return;
        UiSoundService.playNavigate();
        cfg.setSlotCountFor(page, n);
        if (selected >= n) selected = n - 1;
        save();
        rebuildWidgets();
    }

    private void switchWheel(int dir) {
        int n = cfg.wheelCount();
        if (n <= 1) return;
        UiSoundService.playNavigate();
        page = Math.floorMod(page + dir, n);
        selected = 0;
        rebuildWidgets();
    }

    private void addWheel() {
        int idx = cfg.addWheel();
        if (idx < 0) return;
        UiSoundService.playSelect();
        page = idx;
        selected = 0;
        save();
        rebuildWidgets();
    }

    /**
     * Opens the wheel-delete picker (feedback: "poder seleccionar que rueda quieres eliminar... si
     * esta con atajos preguntar") instead of deleting whatever wheel this editor happens to be
     * showing right now — the old behavior silently deleted a wheel WITH shortcuts while an empty one
     * sat untouched elsewhere in the list (the reported bug).
     */
    private void openDeleteWheelPicker() {
        minecraft.setScreen(new RadialWheelDeleteScreen(this, handle));
    }

    /** Called by {@link RadialWheelDeleteScreen} after it deletes a wheel — refresh our state. */
    void onWheelDeleted() {
        page = Math.min(page, cfg.wheelCount() - 1);
        selected = 0;
        rebuildWidgets();
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
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderChrome(context);
        // Live preview on the right (centered in the right third).
        RadialRenderer.render(context, this.width * 3 / 4, this.height / 2, previewSlots(), selected, handle,
                cfg.wheelCount(), page, null, false);
        // Field labels — scroll with their widgets, culled the same way addScroll's widgets are
        // (feedback: "no tienes puesto una barra de scroll... en pantallas pequeñas no puedo bajar").
        for (Label l : labels) {
            if (!isInViewport(l.y(), 10)) continue;
            context.drawString(font, l.text(), 14, l.y() - scrollY(), ACCENT, true);
        }
        renderScrollbar(context, listRightX());
        super.render(context, mouseX, mouseY, delta);
    }

    /** Right edge of the left (editor) column, plus a small margin — the column has a FIXED pixel
     *  width regardless of screen size, so this must anchor off {@link #COL_X}/{@link #COL_W}, not
     *  a fraction of {@code this.width} (bug: a width-fraction placed the scrollbar on top of the
     *  preview wheel on screens wider than the column was designed for). */
    private int listRightX() { return COL_X + COL_W + 10; }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
