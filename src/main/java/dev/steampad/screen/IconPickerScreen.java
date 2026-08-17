package dev.steampad.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Searchable grid of every Minecraft item, used to pick a radial slot icon. Returns the item id
 * (e.g. {@code minecraft:diamond}) to the caller through a consumer. Navigable with the controller
 * cursor (A clicks a cell) and with the mouse.
 */
public class IconPickerScreen extends SteamPadBaseScreen {

    private static final int CELL = 18;

    private final Screen parent;
    private final Consumer<String> onSelect;

    private EditBox search;
    private final List<Item> all = new ArrayList<>();
    private final List<Item> filtered = new ArrayList<>();
    private int gridX, gridY, cols, rowsVisible, scroll;

    public IconPickerScreen(Screen parent, Consumer<String> onSelect) {
        super(Component.translatable("steampad.radial.pick_icon"));
        this.parent = parent;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();
        if (all.isEmpty()) BuiltInRegistries.ITEM.forEach(all::add);

        int w = Math.min(360, this.width - 40);
        int x = (this.width - w) / 2;
        search = new EditBox(this.font, x, HEADER_H + 8, w, 18,
                Component.translatable("steampad.radial.search"));
        search.setResponder(t -> { applyFilter(); scroll = 0; });
        addRenderableWidget(search);

        gridX = x;
        gridY = HEADER_H + 34;
        cols = Math.max(1, w / CELL);
        rowsVisible = Math.max(1, (contentBottom() - gridY) / CELL);

        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());

        applyFilter();
    }

    private void applyFilter() {
        filtered.clear();
        String q = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
        for (Item it : all) {
            if (q.isEmpty()) { filtered.add(it); continue; }
            String id = BuiltInRegistries.ITEM.getKey(it).toString();
            String name = new ItemStack(it).getHoverName().getString().toLowerCase(Locale.ROOT);
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

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (steampad$pickCellAt(click.x(), click.y())) return true;
        return super.mouseClicked(click, doubled);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (steampad$pickCellAt(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }
    *///?}

    /** Selects the icon under (x, y) and closes, if any. */
    private boolean steampad$pickCellAt(double x, double y) {
        int idx = cellAt(x, y);
        if (idx >= 0 && idx < filtered.size()) {
            onSelect.accept(BuiltInRegistries.ITEM.getKey(filtered.get(idx)).toString());
            onClose();
            return true;
        }
        return false;
    }

    private int cellAt(double mx, double my) {
        if (mx < gridX || my < gridY) return -1;
        int col = (int) ((mx - gridX) / CELL);
        int row = (int) ((my - gridY) / CELL);
        if (col < 0 || col >= cols || row < 0 || row >= rowsVisible) return -1;
        return (scroll + row) * cols + col;
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);

        int hovered = cellAt(mouseX, mouseY);
        for (int row = 0; row < rowsVisible; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = (scroll + row) * cols + col;
                if (idx >= filtered.size()) break;
                int cx = gridX + col * CELL, cy = gridY + row * CELL;
                if (idx == hovered) ctx.fill(cx, cy, cx + CELL, cy + CELL, 0x55FFFFFF);
                ctx.renderItem(new ItemStack(filtered.get(idx)), cx + 1, cy + 1);
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
    public void onClose() { minecraft.setScreen(parent); }
}
