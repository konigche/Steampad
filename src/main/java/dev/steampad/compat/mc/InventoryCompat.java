package dev.steampad.compat.mc;

import net.minecraft.world.entity.player.Inventory;

/**
 * Version shim for the selected-hotbar-slot accessors, which flipped visibility in 1.21.2. Verified
 * against the real remapped jars:
 *
 * <ul>
 *   <li>&le; 1.21.1 — {@code public int selected} field; no getter/setter pair.</li>
 *   <li>&ge; 1.21.2 — the field went private and gained
 *       {@code getSelectedSlot()} / {@code setSelectedSlot(int)}.</li>
 * </ul>
 *
 * <p>Note there is deliberately <em>no</em> shim for the hotbar size here: {@code SELECTION_SIZE} did
 * flip the other way (private in 1.21.1, public in 1.21.10), but
 * {@code Inventory.getSelectionSize()} is public in <em>both</em>, so call sites use that directly
 * instead of paying for a conditional — or, worse, an access widener that would only be needed on one
 * version.
 */
public final class InventoryCompat {

    private InventoryCompat() {}

    /** The currently selected hotbar slot index. */
    public static int getSelectedSlot(Inventory inventory) {
        //? if >=1.21.2 {
        return inventory.getSelectedSlot();
        //?} else {
        /*return inventory.selected;*/
        //?}
    }

    /**
     * The player's non-equipment stacks: hotbar (0–8) plus main storage (9–35), in slot order.
     *
     * <p>1.21.1 exposes the backing lists directly as {@code public final} fields split three ways
     * ({@code items}, {@code armor}, {@code offhand}); {@code items} is exactly the non-equipment
     * compartment, so no filtering is needed. From 1.21.2 the field went private and
     * {@code getNonEquipmentItems()} returns the same list.
     */
    public static java.util.List<net.minecraft.world.item.ItemStack> nonEquipmentItems(Inventory inventory) {
        //? if >=1.21.2 {
        return inventory.getNonEquipmentItems();
        //?} else {
        /*return inventory.items;*/
        //?}
    }

    /** Selects a hotbar slot by index. Caller is responsible for keeping it in range. */
    public static void setSelectedSlot(Inventory inventory, int slot) {
        //? if >=1.21.2 {
        inventory.setSelectedSlot(slot);
        //?} else {
        /*inventory.selected = slot;*/
        //?}
    }
}
