package dev.steampad.screen;

/**
 * Implemented by SteamPad screens that have tabs, so the controller can switch tabs with LB/RB
 * (console-style) from {@code GamepadInputDispatcher} without knowing the concrete screen.
 */
public interface TabbedScreen {
    void steampad$nextTab();
    void steampad$prevTab();
}
