package dev.steampad.itemradial;

import dev.steampad.itemradial.backpack.BackpackRegistry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Builds the live, dynamic content of both item-radial wheels from the player's actual inventory â€”
 *  nothing here is user-configured (unlike {@link dev.steampad.radial.RadialConfig}'s wheels). */
public final class InventoryScanner {

    /** One hotbar slot's contents for the Hotbar Radial (LB) â€” direct, unaggregated, 1:1 with the
     *  real hotbar so it mirrors what the player already sees at the bottom of the screen. */
    public record HotbarEntry(int slotIndex, ItemStack stack) {}

    /** Same item type merged across every stack found (hotbar/main/offhand/backpacks), so the
     *  Inventory Radial (RB) shows "Apple x12" once instead of three near-identical chips. */
    public record AggregatedItem(ItemStack representative, int totalCount, int topLevelSlot) {
        /** True if at least one copy sits at a real {@link Inventory} slot (equip-able via the
         *  vanilla swap-to-hotbar helper); false if every copy is backpack-only â€” shown for
         *  awareness, not selectable (moving items out of a third-party mod's own container isn't
         *  attempted here â€” see {@code dev.steampad.itemradial.backpack} package docs). */
        public boolean grabbable() { return topLevelSlot >= 0; }
    }

    private InventoryScanner() {}

    public static List<HotbarEntry> hotbarItems(Player player) {
        List<HotbarEntry> out = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) out.add(new HotbarEntry(i, stack));
        }
        return out;
    }

    /** All 5 {@link ItemCategory} keys are always present, empty list or not â€” the Inventory Radial
     *  keeps categories in fixed clock positions and dims empty ones rather than reflowing (AAA
     *  spatial-memory principle, see {@link ItemCategory}). */
    public static Map<ItemCategory, List<AggregatedItem>> categorized(Player player) {
        return categorizeStacks(InventoryStacks.indexedHotbarMainAndOffhand(player), BackpackRegistry.scanAll(player));
    }

    /** Pure aggregation/classification logic, extracted for testability â€” no {@link Player}
     *  needed, just the two stack sources {@link #categorized} would otherwise gather itself. */
    static Map<ItemCategory, List<AggregatedItem>> categorizeStacks(
            List<InventoryStacks.IndexedStack> topLevel, List<ItemStack> backpackStacks) {
        Map<Item, int[]> countByItem = new LinkedHashMap<>();
        Map<Item, ItemStack> representativeByItem = new LinkedHashMap<>();
        Map<Item, Integer> topLevelSlotByItem = new LinkedHashMap<>();

        for (InventoryStacks.IndexedStack s : topLevel) {
            accumulate(s.stack(), s.slotIndex(), countByItem, representativeByItem, topLevelSlotByItem);
        }
        for (ItemStack backpackStack : backpackStacks) {
            accumulate(backpackStack, -1, countByItem, representativeByItem, topLevelSlotByItem);
        }

        Map<ItemCategory, List<AggregatedItem>> result = new EnumMap<>(ItemCategory.class);
        for (ItemCategory cat : ItemCategory.values()) result.put(cat, new ArrayList<>());

        for (Map.Entry<Item, int[]> e : countByItem.entrySet()) {
            Item item = e.getKey();
            ItemStack rep = representativeByItem.get(item);
            int topLevelSlot = topLevelSlotByItem.getOrDefault(item, -1);
            ItemCategory cat = ItemClassifier.classify(rep);
            result.get(cat).add(new AggregatedItem(rep, e.getValue()[0], topLevelSlot));
        }
        return result;
    }

    private static void accumulate(ItemStack stack, int slotIndex,
                                    Map<Item, int[]> countByItem, Map<Item, ItemStack> representativeByItem,
                                    Map<Item, Integer> topLevelSlotByItem) {
        if (stack.isEmpty()) return;
        Item item = stack.getItem();
        countByItem.computeIfAbsent(item, k -> new int[1])[0] += stack.getCount();
        representativeByItem.putIfAbsent(item, stack);
        if (slotIndex >= 0) topLevelSlotByItem.putIfAbsent(item, slotIndex);
    }
}
