package dev.steampad.itemradial;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;

/**
 * Classifies an {@link ItemStack} into a fixed {@link ItemCategory} for the Inventory Radial.
 *
 * <p>Verified via {@code javap} against the mapped 1.21.10 jar before writing this (project
 * discipline — a prior session already hit "SwordItem no longer a class" as a real gotcha): there is
 * no {@code SwordItem}/{@code ToolItem} class anymore. Melee tools/weapons are data-driven —
 * {@link DataComponents#TOOL} is present on pickaxes, axes, shovels, hoes AND swords alike (the
 * component that also grants the "correct tool" bonus vs. cobwebs) — so checking for that component
 * covers all of them without depending on a class hierarchy that no longer exists.
 */
public final class ItemClassifier {

    private ItemClassifier() {}

    public static ItemCategory classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemCategory.MISC;

        if (stack.has(DataComponents.FOOD)) return ItemCategory.FOOD;
        if (stack.getItem() instanceof BlockItem) return ItemCategory.BLOCKS;
        if (stack.has(DataComponents.TOOL)
                || stack.getItem() instanceof ProjectileWeaponItem
                || stack.getItem() instanceof TridentItem) return ItemCategory.TOOLS_WEAPONS;
        if (stack.getItem() instanceof PotionItem
                || stack.has(DataComponents.POTION_CONTENTS)) return ItemCategory.POTIONS;
        return ItemCategory.MISC;
    }
}
