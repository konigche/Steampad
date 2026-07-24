package dev.steampad.radial.icon;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Renders an item icon in a radial slot. */
public final class ItemIconProvider {

    private ItemIconProvider() {}

    public static void render(DrawContext ctx, String itemId, int x, int y) {
        if (itemId == null || itemId.isEmpty()) return;
        try {
            Identifier id = Identifier.tryParse(itemId);
            if (id == null) return;
            var item = Registries.ITEM.get(id);
            if (item == null) return;
            ctx.drawItemWithoutEntity(new ItemStack(item), x, y);
        } catch (Exception ignored) {}
    }
}
