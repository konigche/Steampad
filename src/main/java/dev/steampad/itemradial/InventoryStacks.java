package dev.steampad.itemradial;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Shared helper: every {@link ItemStack} the player is directly carrying (hotbar + main storage +
 *  offhand) — the base set both {@link dev.steampad.itemradial.backpack.BackpackProvider}s and
 *  {@link InventoryScanner} scan for carried storage / classify. Deliberately excludes armor —
 *  not something a quick-select wheel needs to offer. */
public final class InventoryStacks {

    /** A stack at a real {@link Inventory} slot index — same addressable space
     *  {@link Inventory#pickSlot(int)} expects (0-35 = hotbar+main,
     *  {@link Inventory#SLOT_OFFHAND} = offhand), unlike backpack-nested stacks which have no
     *  such index. */
    public record IndexedStack(int slotIndex, ItemStack stack) {}

    private InventoryStacks() {}

    public static List<ItemStack> hotbarMainAndOffhand(Player player) {
        List<ItemStack> all = new ArrayList<>();
        for (IndexedStack s : indexedHotbarMainAndOffhand(player)) all.add(s.stack());
        return all;
    }

    /** Same set as {@link #hotbarMainAndOffhand}, but keeping each stack's real inventory slot index —
     *  needed to equip a top-level item via the vanilla swap-to-hotbar helper. */
    public static List<IndexedStack> indexedHotbarMainAndOffhand(Player player) {
        Inventory inv = player.getInventory();
        List<IndexedStack> all = new ArrayList<>();
        // hotbar (0-8) + main storage (9-35)
        List<ItemStack> main = dev.steampad.compat.mc.InventoryCompat.nonEquipmentItems(inv);
        for (int i = 0; i < main.size(); i++) {
            ItemStack stack = main.get(i);
            if (!stack.isEmpty()) all.add(new IndexedStack(i, stack));
        }
        ItemStack offhand = inv.getItem(Inventory.SLOT_OFFHAND);
        if (!offhand.isEmpty()) all.add(new IndexedStack(Inventory.SLOT_OFFHAND, offhand));
        return all;
    }
}
