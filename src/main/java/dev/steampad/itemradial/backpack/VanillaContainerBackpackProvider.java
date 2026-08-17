package dev.steampad.itemradial.backpack;

import dev.steampad.itemradial.InventoryStacks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Reads {@link DataComponents#CONTAINER} off any carried stack — shulker boxes, bundles, and any
 * mod backpack that reuses this vanilla component instead of inventing its own storage format. Zero
 * reflection, zero soft-dependency: this is plain vanilla API, so it's always registered.
 */
public final class VanillaContainerBackpackProvider implements BackpackProvider {

    @Override
    public List<ItemStack> scan(Player player) {
        List<ItemStack> found = new ArrayList<>();
        for (ItemStack carrier : InventoryStacks.hotbarMainAndOffhand(player)) {
            ItemContainerContents container = carrier.get(DataComponents.CONTAINER);
            if (container == null) continue;
            container.nonEmptyStream().forEach(found::add);
        }
        return found;
    }
}
