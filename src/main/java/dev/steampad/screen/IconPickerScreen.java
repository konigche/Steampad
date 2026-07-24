package dev.steampad.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Searchable grid of every Minecraft item, used to pick a radial slot icon. Returns the item id
 * (e.g. {@code minecraft:diamond}) to the caller through a consumer. Navigable with the controller
 * cursor (A clicks a cell) and with the mouse.
 */
public class IconPickerScreen extends SteamPadBaseScreen {

    private static final int CELL = 18;

    private final Screen parent;
    private final Consumer<String> onSelect;

    private TextFieldWidget search;
    private final List<Item> all = new ArrayList<>();
    private final List<Item> filtered = new ArrayList<>();
    private int gridX, gridY, cols, rowsVisible, scroll;

    public IconPickerScreen(Screen parent, Consumer<String> onSelect) {
        super(Text.translatable("steampad.radial.pick_icon"));
        this.parent = parent;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();
        if (all.isEmpty()) Registries.ITEM.forEach(all::add);

        int w = Math.min(360, this.width - 40);
        int x = (this.width - w) / 2;
        search = new TextFieldWidget(this.textRenderer, x, HEADER_H + 8, w, 18,
                Text.translatable("steampad.radial.search"));
        search.setChangedListener(t -> { applyFilter(); scroll = 0; });
        addDrawableChild(search);

        gridX = x;
        gridY = HEADER_H + 34;
        cols = Math.max(1, w / CELL);
        rowsVisible = Math.max(1, (contentBottom() - gridY) / CELL);

        addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> close())
                .dimensions(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());

        applyFilter();
    }

    private void applyFilter() {
        filtered.clear();
        String q = search == null ? "" : search.getText().toLowerCase(Locale.ROOT).trim();
        for (Item it : all) {
            if (q.isEmpty()) { filtered.add(it); continue; }
            String id = Registries.ITEM.getId(it).toString();
            String name = new ItemStack(it).getName().getString().toLowerCase(Locale.ROOT);
            if (id.contains(q) || name.contains(q)) filtered.add(it);
        }
    }

    private int maxScroll() {
        int rows = (filtered.size() + cols - 1) / cols;
        return Math.max(0, rows - rowsVisible);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        scroll = clampInt(scroll - (int) Math.signum(vAmount), 0, maxScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        int idx = cellAt(click.x(), click.y());
        if (idx >= 0 && idx < filtered.size()) {
            onSelect.accept(Registries.ITEM.getId(filtered.get(idx)).toString());
            close();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private int cellAt(double mx, double my) {
        if (mx < gridX || my < gridY) return -1;
        int col = (int) ((mx - gridX) / CELL);
        int row = (int) ((my - gridY) / CELL);
        if (col < 0 || col >= cols || row < 0 || row >= rowsVisible) return -1;
        return (scroll + row) * cols + col;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);

        int hovered = cellAt(mouseX, mouseY);
        for (int row = 0; row < rowsVisible; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = (scroll + row) * cols + col;
                if (idx >= filtered.size()) break;
                int cx = gridX + col * CELL, cy = gridY + row * CELL;
                if (idx == hovered) ctx.fill(cx, cy, cx + CELL, cy + CELL, 0x55FFFFFF);
                ctx.drawItem(new ItemStack(filtered.get(idx)), cx + 1, cy + 1);
            }
        }
        // Simple scroll indicator.
        int ms = maxScroll();
        if (ms > 0) {
            int barX = gridX + cols * CELL + 4;
            int top = gridY, bottom = gridY + rowsVisible * CELL;
            ctx.fill(barX, top, barX + 3, bottom, 0x33FFFFFF);
            int h = bottom - top;
            int thumb = Math.max(16, h / (ms + 1));
            int ty = top + (h - thumb) * scroll / ms;
            ctx.fill(barX, ty, barX + 3, ty + thumb, 0xFFFFFFFF);
        }
    }

    @Override
    public void close() { client.setScreen(parent); }
}
