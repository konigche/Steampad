package dev.steampad.screen;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.PixelTheme;
import dev.steampad.config.RadialConfig;
import dev.steampad.radial.RadialActionType;
import dev.steampad.radial.RadialRenderer;
import dev.steampad.radial.RadialSlot;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

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

    private record Label(int y, Text text) {}
    private final List<Label> labels = new ArrayList<>();

    public RadialStyleScreen(Screen parent, long handle) {
        super(Text.translatable("steampad.radial.style.title"));
        this.parent = parent;
        this.handle = handle;
    }

    @Override
    protected void init() {
        super.init();
        labels.clear();
        this.cfg = ConfigManager.getRadialConfig(handle);
        cfg.normalize();

        int colX = 14, colW = 200;
        int y = HEADER_H + 8;

        // Wheel radius.
        labels.add(new Label(y - 1, Text.translatable("steampad.radial.radius")));
        y += 10;
        addDrawableChild(new dev.steampad.client.ui.SteamSlider(colX, y, colW, 18,
                Text.translatable("steampad.radial.radius"), cfg.outerRadius, 54f, 130f, "%.0f px",
                v -> { cfg.outerRadius = v; save(); }));
        y += 24;

        // Slot-chip size.
        labels.add(new Label(y - 1, Text.translatable("steampad.radial.chip_size")));
        y += 10;
        addDrawableChild(new dev.steampad.client.ui.SteamSlider(colX, y, colW, 18,
                Text.translatable("steampad.radial.chip_size"), cfg.chipRadius, 12f, 26f, "%.0f px",
                v -> { cfg.chipRadius = v; save(); }));
        y += 24;

        // Icon size (independent of chip size — feedback: "pon un slider para controlar el tamaño
        // de los iconos"). Stored as a raw multiplier, shown as a percentage (same pattern as the
        // HUD glyph-scale slider).
        labels.add(new Label(y - 1, Text.translatable("steampad.radial.icon_size")));
        y += 10;
        addDrawableChild(new dev.steampad.client.ui.SteamSlider(colX, y, colW, 18,
                Text.translatable("steampad.radial.icon_size"), cfg.iconScale * 100f, 70f, 220f, "%.0f%%",
                v -> { cfg.iconScale = v / 100f; save(); }));
        y += 24;

        // Dark backdrop on/off.
        labels.add(new Label(y - 1, Text.translatable("steampad.radial.background")));
        y += 10;
        addDrawableChild(new dev.steampad.client.ui.SteamToggle(colX, y, colW, 18,
                Text.translatable("steampad.radial.background"), cfg.showBackground,
                v -> { cfg.showBackground = v; save(); }));
        y += 24;

        // Color theme — LAST, as requested (visual identity comes after shape).
        labels.add(new Label(y - 1, Text.translatable("steampad.radial.theme")));
        y += 10;
        addDrawableChild(CyclingButtonWidget.<PixelTheme>builder(
                        v -> Text.translatable("steampad.keyboard.theme." + v.name().toLowerCase()))
                .values(PixelTheme.values())
                .initially(cfg.theme == null ? PixelTheme.VANILLA : cfg.theme)
                .omitKeyText()
                .build(colX, y, colW, 18, Text.translatable("steampad.radial.theme"),
                        (btn, v) -> { cfg.theme = v; save(); }));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, b -> close())
                .dimensions(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderChrome(context);
        RadialRenderer.render(context, this.width * 3 / 4, this.height / 2, previewSlots(), 0, handle, 1, 0);
        for (Label l : labels) {
            context.drawText(textRenderer, l.text(), 14, l.y(), ACCENT, true);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() { client.setScreen(parent); }
}
