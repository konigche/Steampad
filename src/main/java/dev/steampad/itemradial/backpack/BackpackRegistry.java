package dev.steampad.itemradial.backpack;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Active {@link BackpackProvider}s. Add a new mod's adapter here once its storage format is known —
 *  nothing else in the item-radial system needs to change. */
public final class BackpackRegistry {

    private static final List<BackpackProvider> PROVIDERS = List.of(
            new VanillaContainerBackpackProvider(),
            new TravelersBackpackProvider()
    );

    private BackpackRegistry() {}

    /** Every non-empty {@link ItemStack} found across all registered providers. */
    public static List<ItemStack> scanAll(Player player) {
        List<ItemStack> all = new ArrayList<>();
        for (BackpackProvider provider : PROVIDERS) {
            all.addAll(provider.scan(player));
        }
        return all;
    }
}
