package dev.steampad.radial;

public enum RadialActionType {
    CHAT_COMMAND,
    KEYBIND,
    SUBMENU,
    SCREEN_SHORTCUT,
    MALILIB_KEYBIND,
    /** Plays a player emote from the emote library (FASE 63); actionValue = emote id. */
    EMOTE,
    NONE
}
