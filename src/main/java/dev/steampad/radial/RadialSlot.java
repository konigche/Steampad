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

    public RadialSlot(RadialActionType type, String actionValue,
                      String iconType, String iconValue,
                      String displayName, boolean triggerOnRelease) {
        this.type = type;
        this.actionValue = actionValue != null ? actionValue : "";
        this.iconType = iconType != null ? iconType : "NONE";
        this.iconValue = iconValue != null ? iconValue : "";
        this.displayName = displayName != null ? displayName : "";
        this.triggerOnRelease = triggerOnRelease;
    }

    public static RadialSlot empty() {
        return new RadialSlot(RadialActionType.NONE, "", "NONE", "", "", true);
    }

    public boolean isEmpty() {
        return type == RadialActionType.NONE || actionValue.isEmpty();
    }
}
