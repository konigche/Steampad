package dev.steampad.itemradial.backpack;

import dev.steampad.itemradial.InventoryStacks;
import dev.steampad.util.LogUtil;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Reads Traveler's Backpack's carried contents WITHOUT opening its screen — confirmed feasible by
 * cloning the real {@code Tiviacz1337/Travelers-Backpack} source (1.21.10-fabric branch) rather than
 * guessing: {@code TravelersBackpackItem.hasCustomData} reads its inventory straight off two custom
 * {@code DataComponentType}s on the carried {@link ItemStack} itself — {@code backpack_container}
 * (main storage) and {@code tools_container} (its dedicated tool slots) — registered under the
 * {@code travelersbackpack} namespace, each holding a {@code BackpackContainerContents} with a public
 * {@code getItems()} returning the stored stacks.
 *
 * <p>No compile-time dependency needed: the component TYPE is looked up generically through the
 * shared vanilla registry by its stable id (works across mods/loaders — registries are keyed by id at
 * runtime regardless of what mappings either side compiled against), and the one method call whose
 * return type we don't have on our classpath ({@code BackpackContainerContents.getItems()}) is
 * invoked reflectively on the already-resolved instance — no {@code Class.forName} on the mod's own
 * package needed at all. If the mod isn't installed, both component ids simply don't exist in the
 * registry and every scan is a silent no-op (mirrors {@link dev.steampad.compat.MalilibCompat}'s
 * soft-dependency pattern used elsewhere in this project).
 */
public final class TravelersBackpackProvider implements BackpackProvider {

    private static final ResourceLocation STORAGE_ID = ResourceLocation.fromNamespaceAndPath("travelersbackpack", "backpack_container");
    private static final ResourceLocation TOOLS_ID = ResourceLocation.fromNamespaceAndPath("travelersbackpack", "tools_container");

    @Override
    public List<ItemStack> scan(Player player) {
        // Registry lookup by id was renamed get -> getValue in 1.21.2 (get now returns a Holder).
        //? if >=1.21.2 {
        DataComponentType<?> storage = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(STORAGE_ID);
        DataComponentType<?> tools = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(TOOLS_ID);
        //?} else {
        /*DataComponentType<?> storage = BuiltInRegistries.DATA_COMPONENT_TYPE.get(STORAGE_ID);
        DataComponentType<?> tools = BuiltInRegistries.DATA_COMPONENT_TYPE.get(TOOLS_ID);
        *///?}
        if (storage == null && tools == null) return List.of();   // mod not installed — no-op

        List<ItemStack> found = new ArrayList<>();
        for (ItemStack carrier : InventoryStacks.hotbarMainAndOffhand(player)) {
            if (storage != null) collectFrom(carrier.get(storage), found);
            if (tools != null) collectFrom(carrier.get(tools), found);
        }
        return found;
    }

    /** {@code contents} is a {@code BackpackContainerContents} instance (unknown type to us) or null. */
    private static void collectFrom(Object contents, List<ItemStack> out) {
        if (contents == null) return;
        try {
            Method getItems = contents.getClass().getMethod("getItems");
            Object items = getItems.invoke(contents);
            if (!(items instanceof Iterable<?> iterable)) return;
            for (Object o : iterable) {
                if (o instanceof ItemStack stack && !stack.isEmpty()) out.add(stack);
            }
        } catch (ReflectiveOperationException e) {
            LogUtil.debug("Traveler's Backpack getItems() reflection failed — API may have changed: {}", e.toString());
        }
    }
}
