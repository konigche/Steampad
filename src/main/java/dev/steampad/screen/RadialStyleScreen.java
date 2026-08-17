package dev.steampad.screen;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.PixelTheme;
import dev.steampad.config.RadialConfig;
import dev.steampad.config.RadialSkin;
import dev.steampad.radial.RadialActionType;
import dev.steampad.radial.RadialRenderer;
import dev.steampad.radial.RadialSlot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Appearance settings for the radial wheel (per controller): color theme, wheel radius, slot-chip
 * size, and the dark backdrop toggle — split out of the slot editor so visual tuning has room of its
 * own instead of crowding the (already dense) per-slot fields. Live wheel preview on the right, same
 * spot as the editor's, so every slider tick is visible immediately.
 */
public class RadialStyleScreen extends SteamPadBaseScreen {

    private final Screen parent;
    private final long handle;
    private RadialConfig cfg;

    private record Label(int y, Component text) {}
    private final List<Label> labels = new ArrayList<>();

    public RadialStyleScreen(Screen parent, long handle) {
        super(Component.translatable("steampad.radial.style.title"));
        this.parent = parent;
        this.handle = handle;
    }

    @Override
    protected void init() {
        super.init();
        this.cfg = ConfigManager.getRadialConfig(handle);
        cfg.normalize();
        rebuild();
    }

    /** Rebuilds the widget list from scratch — called from {@link #init()} and again whenever the
     *  skin cycling button changes (the SEGMENTED-only sliders need to appear/disappear immediately,
     *  same {@code clearChildren()}-then-relayout pattern as {@code ProfilesScreen}/
     *  {@code EmoteWheelScreen}). */
    private void rebuild() {
        this.clearWidgets();
        labels.clear();

        int colX = 14, colW = 200;
        int y = HEADER_H + 8;

        // Wheel radius.
        labels.add(new Label(y - 1, Component.translatable("steampad.radial.radius")));
        y += 10;
        addRenderableWidget(new dev.steampad.client.ui.SteamSlider(colX, y, colW, 18,
                Component.translatable("steampad.radial.radius"), cfg.outerRadius, 54f, 130f, "%.0f px",
                v -> { cfg.outerRadius = v; save(); }));
        y += 24;

        // Slot-chip size.
        labels.add(new Label(y - 1, Component.translatable("steampad.radial.chip_size")));
        y += 10;
        addRenderableWidget(new dev.steampad.client.ui.SteamSlider(colX, y, colW, 18,
                Component.translatable("steampad.radial.chip_size"), cfg.chipRadius, 12f, 26f, "%.0f px",
                v -> { cfg.chipRadius = v; save(); }));
        y += 24;

        // SEGMENTED-only shape sliders (feedback: reuse the RadialConfig fields that were already
        // reserved — and unused — for exactly this: innerRadius/segmentGap). Hidden for CLASSIC,
        // which derives its own ring geometry entirely from the two sliders above.
        boolean segmented = cfg.skin == RadialSkin.SEGMENTED;
        if (segmented) {
            labels.add(new Label(y - 1, Component.translatable("steampad.radial.inner_radius")));
            y += 10;
            addRenderableWidget(new dev.steampad.client.ui.SteamSlider(colX, y, colW, 18,
                    Component.translatable("steampad.radial.inner_radius"), cfg.innerRadius, 18f, 70f, "%.0f px",
                    v -> { cfg.innerRadius = v; save(); }));
            y += 24;

            labels.add(new Label(y - 1, Component.translatable("steampad.radial.segment_gap")));
            y += 10;
            addRenderableWidget(new dev.steampad.client.ui.SteamSlider(colX, y, colW, 18,
                    Component.translatable("steampad.radial.segment_gap"), cfg.segmentGap, 0f, 12f, "%.0f px",
                    v -> { cfg.segmentGap = v; save(); }));
            y += 24;
        }

        // Icon size (independent of chip size — feedback: "pon un slider para controlar el tamaño
        // de los iconos"). Stored as a raw multiplier, shown as a percentage (same pattern as the
        // HUD glyph-scale slider).
        labels.add(new Label(y - 1, Component.translatable("steampad.radial.icon_size")));
        y += 10;
        addRenderableWidget(new dev.steampad.client.ui.SteamSlider(colX, y, colW, 18,
                Component.translatable("steampad.radial.icon_size"), cfg.iconScale * 100f, 70f, 220f, "%.0f%%",
                v -> { cfg.iconScale = v / 100f; save(); }));
        y += 24;

        // Dark backdrop on/off.
        labels.add(new Label(y - 1, Component.translatable("steampad.radial.background")));
        y += 10;
        addRenderableWidget(new dev.steampad.client.ui.SteamToggle(colX, y, colW, 18,
                Component.translatable("steampad.radial.background"), cfg.showBackground,
                v -> { cfg.showBackground = v; save(); }));
        y += 24;

        // Background blur on/off (feedback: "agrega un blur... que se pueda desactivar") — same
        // vanilla post-process pass the pause menu uses, applies to all 3 wheels via RadialRenderer.
        labels.add(new Label(y - 1, Component.translatable("steampad.radial.background_blur")));
        y += 10;
        addRenderableWidget(new dev.steampad.client.ui.SteamToggle(colX, y, colW, 18,
                Component.translatable("steampad.radial.background_blur"), cfg.backgroundBlur,
                v -> { cfg.backgroundBlur = v; save(); }));
        y += 24;

        // Color theme — LAST, as requested (visual identity comes after shape).
        labels.add(new Label(y - 1, Component.translatable("steampad.radial.theme")));
        y += 10;
        addRenderableWidget(CycleButton.<PixelTheme>builder(
                        v -> Component.translatable("steampad.keyboard.theme." + v.name().toLowerCase()))
                .withValues(PixelTheme.values())
                .withInitialValue(cfg.theme == null ? PixelTheme.VANILLA : cfg.theme)
                .displayOnlyValue()
                .create(colX, y, colW, 18, Component.translatable("steampad.radial.theme"),
                        (btn, v) -> { cfg.theme = v; save(); }));

        // Skin — the wheel's overall LAYOUT (round chips vs. trapezoid slices), independent of the
        // color theme above. Placed above the live preview on the right (not in the settings column)
        // since it's the one control that changes which OTHER sliders on this screen are relevant —
        // picking it first, before scanning the rest of the list, reads more naturally than burying
        // it at the bottom of a column it also reshapes.
        addRenderableWidget(CycleButton.<RadialSkin>builder(
                        v -> Component.translatable("steampad.radial.skin." + v.name().toLowerCase()))
                .withValues(RadialSkin.values())
                .withInitialValue(cfg.skin == null ? RadialSkin.CLASSIC : cfg.skin)
                .displayOnlyValue()
                .create(this.width * 3 / 4 - 100, HEADER_H + 8, 200, 18, Component.translatable("steampad.radial.skin"),
                        (btn, v) -> { cfg.skin = v; save(); rebuild(); }));

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());
    }

    private void save() { ConfigManager.saveRadialConfig(handle); }

    /** Wheel-0 slots for the live preview (same mapping the editor uses). */
    private List<RadialSlot> previewSlots() {
        List<RadialSlot> out = new ArrayList<>();
        for (int i = 0; i < cfg.slotCountFor(0); i++) {
            RadialConfig.SlotConfig sc = cfg.slotsFor(0).get(i);
            out.add(new RadialSlot(parseType(sc.type), sc.action, sc.iconType, sc.iconValue,
                    sc.displayName, "ON_RELEASE".equals(sc.trigger)));
        }
        return out;
    }

    private RadialActionType parseType(String s) {
        try { return RadialActionType.valueOf(s); } catch (Exception e) { return RadialActionType.NONE; }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderChrome(context);
        RadialRenderer.render(context, this.width * 3 / 4, this.height / 2, previewSlots(), 0, handle, 1, 0, null, false);
        for (Label l : labels) {
            context.drawString(font, l.text(), 14, l.y(), ACCENT, true);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
