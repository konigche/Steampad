package dev.steampad.itemradial;

/**
 * Fixed clock-position categories for the Inventory Radial (RB). Order here IS the clock order
 * (index 0 = 12 o'clock, clockwise) — deliberately never reordered by what the player is carrying,
 * so an empty category renders dimmed in its own slot instead of the rest reflowing to fill the gap
 * (AAA spatial-memory principle: the hand should learn "up = food" once and keep it forever).
 */
public enum ItemCategory {
    FOOD,
    BLOCKS,
    TOOLS_WEAPONS,
    POTIONS,
    MISC
}
