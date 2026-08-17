package dev.steampad.itemradial.backpack;

import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Extension point for "extra storage a player is carrying" — a shulker box, a bundle, a dedicated
 * backpack mod's item, anything that holds its own {@link ItemStack}s. There is no universal way to
 * detect an arbitrary mod's storage format (each mod invents its own), so this is deliberately a
 * registry of concrete adapters (see {@link BackpackRegistry}) rather than a promise of magic
 * detection — a new mod's backpack gets its own provider once its storage format is known.
 */
public interface BackpackProvider {

    /** Non-empty {@link ItemStack}s found inside any recognized carried storage. Never null. */
    List<ItemStack> scan(Player player);
}
