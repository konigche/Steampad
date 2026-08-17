package dev.steampad.radial;

public enum RadialActionType {
    CHAT_COMMAND,
    /**
     * A built-in command from {@link SmartCommand} — one whose text has to be worked out when it
     * fires (teleport to where you died) or that is worth a client-side check first (teleport to the
     * farthest player). {@code actionValue} holds the {@link SmartCommand#id()}, exactly the way
     * {@link #CHAT_COMMAND} holds its literal text, so it is configured, stored and triggered through
     * the same slot machinery.
     */
    SMART_COMMAND,
    KEYBIND,
    SUBMENU,
    SCREEN_SHORTCUT,
    MALILIB_KEYBIND,
    /** Plays a player emote from the emote library (FASE 63); actionValue = emote id. */
    EMOTE,
    /** Internal-only: a live inventory item shown by {@code dev.steampad.itemradial}'s dynamic
     *  wheels (never user-configured, never offered in {@code RadialEditorScreen}'s type picker —
     *  filtered out there explicitly). Exists so a populated dynamic slot reports {@code isEmpty()
     *  == false} (unlike {@code NONE}, which always does) so its icon actually renders. */
    ITEM,
    NONE
}
