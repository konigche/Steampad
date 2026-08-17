package dev.steampad.radial.icon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Renders an item icon in a radial slot. */
public final class ItemIconProvider {

    private ItemIconProvider() {}

    public static void render(GuiGraphics ctx, String itemId, int x, int y) {
        if (itemId == null || itemId.isEmpty()) return;
        try {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null) return;
            // Registry lookup by id was renamed get -> getValue in 1.21.2.
            //? if >=1.21.2 {
            var item = BuiltInRegistries.ITEM.getValue(id);
            //?} else {
            /*var item = BuiltInRegistries.ITEM.get(id);*/
            //?}
            if (item == null) return;
            ctx.renderFakeItem(new ItemStack(item), x, y);
        } catch (Exception ignored) {}
    }

    /**
     * The item's own display name, or null when the id doesn't resolve — which is the normal case for a
     * layer left behind by a mod that has since been removed, and the caller falls back to the raw id
     * so that layer stays visible (and deletable) instead of vanishing.
     */
    public static net.minecraft.network.chat.Component displayName(String itemId) {
        if (itemId == null || itemId.isEmpty()) return null;
        try {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null) return null;
            //? if >=1.21.2 {
            var item = BuiltInRegistries.ITEM.getValue(id);
            //?} else {
            /*var item = BuiltInRegistries.ITEM.get(id);*/
            //?}
            return item == null ? null : new ItemStack(item).getHoverName();
        } catch (Exception ignored) {
            return null;
        }
    }
}
