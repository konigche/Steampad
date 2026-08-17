package dev.steampad.screen;

import dev.steampad.client.ui.SteamSlider;
import dev.steampad.client.ui.SteamToggle;
import dev.steampad.service.UiSoundService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Shared two-column settings layout matching the Buttons screen: a scrollable option list on the left
 * (~3/4) and a description panel on the right (~1/4) that shows the focused/hovered option's name and
 * description. Subclasses call {@link #beginLayout()}, then {@code section/toggle/slider/cycling/button}
 * to build rows, then {@link #finishLayout()}; and call {@link #renderColumns} from {@code render()}.
 *
 * <p>Toggles use {@link SteamToggle} (visible on/off switch) and sliders use {@link SteamSlider}
 * (right-stick fine adjust + scroll-safe), unifying look and behaviour across every settings screen.
 */
public abstract class ColumnSettingsScreen extends SteamPadBaseScreen {

    protected static final int ROW_H = 24;
    private static final int SECTION_GAP = 6;
    private static final int HEADER_RESERVE = 17;

    protected int listX, listW, panelX, panelW;
    private int cursorY;

    protected record Row(AbstractWidget w, String labelKey, String descKey) {}
    private final List<Row> rows = new ArrayList<>();
    private final List<int[]> headerPos = new ArrayList<>();
    private final List<String> headerKeys = new ArrayList<>();
    private Row selected;

    protected ColumnSettingsScreen(Component title) { super(title); }

    /** Column geometry + reset. Call after super.init(). */
    protected void beginLayout() {
        resetScroll();
        rows.clear();
        headerPos.clear();
        headerKeys.clear();
        panelW = Math.max(120, Math.min(200, this.width / 4));
        panelX = this.width - panelW - 8;
        listX = 8;
        listW = panelX - 12 - listX;
        cursorY = contentTop();
    }

    /** Left column geometry for subclass extras (e.g. a tab row). */
    protected int listX() { return listX; }
    protected int listW() { return listW; }
    protected int rowTop() { return cursorY; }
    protected void advance(int dy) { cursorY += dy; }

    protected void section(String key) {
        cursorY += SECTION_GAP;
        headerPos.add(new int[]{cursorY});
        headerKeys.add(key);
        cursorY += HEADER_RESERVE;
    }

    protected void toggle(String key, boolean initial, Consumer<Boolean> onChange) {
        SteamToggle t = new SteamToggle(listX, cursorY, listW, 20, Component.translatable(key), initial, v -> {
            UiSoundService.playNavigate();
            onChange.accept(v);
        });
        addScroll(t, cursorY);
        rows.add(new Row(t, key, key + ".desc"));
        cursorY += ROW_H;
    }

    protected void slider(String key, float initial, float min, float max, String fmt, Consumer<Float> onChange) {
        SteamSlider s = new SteamSlider(listX, cursorY, listW, 20, Component.translatable(key), initial, min, max, fmt, v -> {
            UiSoundService.playNavigate();
            onChange.accept(v);
        });
        addScroll(s, cursorY);
        rows.add(new Row(s, key, key + ".desc"));
        cursorY += ROW_H;
    }

    protected <T extends Enum<T>> void cycling(String key, T[] values, T initial, Consumer<T> onChange) {
        // valueToText returns ONLY the value ("Normal") — CyclingButtonWidget.build(..., optionName, ...)
        // already prepends "optionName: " itself (vanilla's ScreenTexts.composeGenericOptionText, via
        // options.generic_value = "%s: %s") whenever optionTextOmitted is false (the default we use
        // here). Returning "key: value" from valueToText on top of that produced "key: key: value" on
        // EVERY cycling control in the mod (feedback: "Detalle de glifos en juego: Detalle de glifos en
        // juego: Normal") — confirmed by disassembling CyclingButtonWidget.composeText/
        // composeGenericOptionText with javap -c before touching this.
        AbstractWidget w = CycleButton.<T>builder(v -> localizedEnum(key, v))
                .withValues(values)
                .withInitialValue(initial)
                .create(listX, cursorY, listW, 20, Component.translatable(key),
                        (btn, v) -> { UiSoundService.playNavigate(); onChange.accept(v); });
        addScroll(w, cursorY);
        rows.add(new Row(w, key, key + ".desc"));
        cursorY += ROW_H;
    }

    /** Enum value label: tries "<key>.<value>" then falls back to the raw name. */
    private static Component localizedEnum(String key, Enum<?> v) {
        String tk = key + "." + v.name().toLowerCase();
        String s = Component.translatable(tk).getString();
        return s.equals(tk) ? Component.literal(v.name()) : Component.literal(s);
    }

    protected void button(String key, Runnable action) {
        AbstractWidget w = Button.builder(Component.translatable(key),
                        btn -> { UiSoundService.playSelect(); action.run(); })
                .bounds(listX, cursorY, listW, 20).build();
        addScroll(w, cursorY);
        rows.add(new Row(w, key, key + ".desc"));
        cursorY += ROW_H;
    }

    protected void finishLayout() { finishScroll(cursorY); }

    /** The focused slider (for right-stick fine adjust), or null. */
    public SteamSlider focusedSlider() {
        return this.getFocused() instanceof SteamSlider s ? s : null;
    }

    /** Draws section headers, the scrollbar, and the right description panel. Call from render(). */
    protected void renderColumns(GuiGraphics ctx, int mouseX, int mouseY) {
        int top = contentTop(), bottom = contentBottom();
        for (int i = 0; i < headerKeys.size(); i++) {
            int baseY = headerPos.get(i)[0];
            int y = baseY - scrollY();
            if (y >= top - 2 && y + 12 <= bottom + 2) {
                drawSectionHeader(ctx, listX, y, listW, Component.translatable(headerKeys.get(i)));
            }
        }
        renderScrollbar(ctx, listX + listW + 4);
        updateSelection(mouseX, mouseY);
        renderPanel(ctx, top);
    }

    private void updateSelection(int mouseX, int mouseY) {
        Row sel = null;
        for (Row r : rows) if (r.w() == this.getFocused()) { sel = r; break; }
        if (sel == null) {
            for (Row r : rows) {
                AbstractWidget w = r.w();
                if (w.visible && mouseX >= w.getX() && mouseX <= w.getX() + w.getWidth()
                        && mouseY >= w.getY() && mouseY <= w.getY() + w.getHeight()) { sel = r; break; }
            }
        }
        if (sel != null) selected = sel;
    }

    private void renderPanel(GuiGraphics ctx, int top) {
        int x = panelX, w = panelW;
        ctx.fill(x, top, x + w, this.height - FOOTER_H - 4, PANEL_BG);
        ctx.fill(x, top, x + w, top + 1, ACCENT_DIM);
        int y = top + 6;
        if (selected == null) {
            ctx.drawString(font, Component.translatable("steampad.bind.panel.none"), x + 6, y, TEXT_MUTED, false);
            return;
        }
        for (FormattedCharSequence l : font.split(Component.translatable(selected.labelKey()), w - 12)) {
            ctx.drawString(font, l, x + 6, y, TEXT_PRIMARY, false);
            y += 10;
        }
        y += 4;
        String descStr = Component.translatable(selected.descKey()).getString();
        if (!descStr.equals(selected.descKey())) {   // skip if the .desc key is missing (no raw key shown)
            for (FormattedCharSequence l : font.split(Component.literal(descStr), w - 12)) {
                ctx.drawString(font, l, x + 6, y, TEXT_MUTED, false);
                y += 10;
            }
        }
    }
}
