package dev.steampad.radial;

/**
 * Data for a single slot in the radial menu.
 */
public final class RadialSlot {

    public final RadialActionType type;
    public final String actionValue;    // command text, keybind id, screen id, etc.
    public final String iconType;       // "ITEM", "EFFECT", "CHARACTER", "NONE"
    public final String iconValue;      // item id, effect id, character
    public final String displayName;    // label shown in the radial, empty = none
    public final boolean triggerOnRelease; // true = ON_RELEASE, false = ON_CLICK
    /** Real item stack size, or 0 for anything that isn't backed by a live {@code ItemStack} (every
     *  editor-configured slot — Menú Radial, Emotes — and category chips, which aggregate several
     *  stacks). Only the SEGMENTED skin reads this, to draw a vanilla-style count badge on the icon
     *  (feedback: reference mock shows a persistent per-slot count, unlike the CLASSIC skin's
     *  existing "Name x8" text which only shows for the currently-selected slot). */
    public final int count;

    public RadialSlot(RadialActionType type, String actionValue,
                      String iconType, String iconValue,
                      String displayName, boolean triggerOnRelease) {
        this(type, actionValue, iconType, iconValue, displayName, triggerOnRelease, 0);
    }

    public RadialSlot(RadialActionType type, String actionValue,
                      String iconType, String iconValue,
                      String displayName, boolean triggerOnRelease, int count) {
        this.type = type;
        this.actionValue = actionValue != null ? actionValue : "";
        this.iconType = iconType != null ? iconType : "NONE";
        this.iconValue = iconValue != null ? iconValue : "";
        this.displayName = displayName != null ? displayName : "";
        this.triggerOnRelease = triggerOnRelease;
        this.count = count;
    }

    public static RadialSlot empty() {
        return new RadialSlot(RadialActionType.NONE, "", "NONE", "", "", true);
    }

    public boolean isEmpty() {
        return type == RadialActionType.NONE || actionValue.isEmpty();
    }
}
