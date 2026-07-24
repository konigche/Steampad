package dev.steampad.input;

/**
 * All logical actions that can be bound to controller inputs.
 * These are the game-side actions, separate from the Steam Input action handles.
 */
public enum InputAction {

    // Gameplay
    WALK_FORWARD("gameplay.walk_forward", Category.GAMEPLAY),
    WALK_BACKWARD("gameplay.walk_backward", Category.GAMEPLAY),
    STRAFE_LEFT("gameplay.strafe_left", Category.GAMEPLAY),
    STRAFE_RIGHT("gameplay.strafe_right", Category.GAMEPLAY),
    LOOK_UP("gameplay.look_up", Category.GAMEPLAY),
    LOOK_DOWN("gameplay.look_down", Category.GAMEPLAY),
    LOOK_LEFT("gameplay.look_left", Category.GAMEPLAY),
    LOOK_RIGHT("gameplay.look_right", Category.GAMEPLAY),
    JUMP("gameplay.jump", Category.GAMEPLAY),
    SNEAK("gameplay.sneak", Category.GAMEPLAY),
    ATTACK("gameplay.attack", Category.GAMEPLAY),
    USE("gameplay.use", Category.GAMEPLAY),
    SPRINT("gameplay.sprint", Category.GAMEPLAY),
    PAUSE("gameplay.pause", Category.GAMEPLAY),
    INVENTORY("gameplay.inventory", Category.GAMEPLAY),
    CHANGE_PERSPECTIVE("gameplay.change_perspective", Category.GAMEPLAY),
    SWAP_HANDS("gameplay.swap_hands", Category.GAMEPLAY),
    OPEN_CHAT("gameplay.open_chat", Category.GAMEPLAY),
    DROP_ITEM("gameplay.drop_item", Category.GAMEPLAY),
    DROP_STACK("gameplay.drop_stack", Category.GAMEPLAY),
    PICK_BLOCK("gameplay.pick_block", Category.GAMEPLAY),
    PICK_BLOCK_NBT("gameplay.pick_block_nbt", Category.GAMEPLAY),
    TAKE_SCREENSHOT("gameplay.take_screenshot", Category.GAMEPLAY),
    TOGGLE_HUD("gameplay.toggle_hud", Category.GAMEPLAY),
    SHOW_PLAYER_LIST("gameplay.show_player_list", Category.GAMEPLAY),
    GAME_MODE_SWITCHER("gameplay.game_mode_switcher", Category.GAMEPLAY),

    // Hotbar
    NEXT_HOTBAR("hotbar.next", Category.HOTBAR),
    PREV_HOTBAR("hotbar.prev", Category.HOTBAR),
    LOAD_CREATIVE_HOTBAR("hotbar.load_creative", Category.HOTBAR),
    SAVE_CREATIVE_HOTBAR("hotbar.save_creative", Category.HOTBAR),
    HOTBAR_SLOT_RADIAL("hotbar.slot_radial", Category.HOTBAR),

    // GUI
    GUI_PRESS("gui.press", Category.GUI),
    GUI_BACK("gui.back", Category.GUI),
    GUI_NEXT_TAB("gui.next_tab", Category.GUI),
    GUI_PREV_TAB("gui.prev_tab", Category.GUI),
    GUI_ABSTRACT_1("gui.abstract_1", Category.GUI),
    GUI_ABSTRACT_2("gui.abstract_2", Category.GUI),
    GUI_ABSTRACT_3("gui.abstract_3", Category.GUI),
    GUI_NAV_UP("gui.nav_up", Category.GUI),
    GUI_NAV_DOWN("gui.nav_down", Category.GUI),
    GUI_NAV_LEFT("gui.nav_left", Category.GUI),
    GUI_NAV_RIGHT("gui.nav_right", Category.GUI),
    CYCLE_OPTION_FORWARD("gui.cycle_forward", Category.GUI),
    CYCLE_OPTION_BACKWARD("gui.cycle_backward", Category.GUI),

    // Virtual Mouse
    VMOUSE_UP("vmouse.up", Category.VIRTUAL_MOUSE),
    VMOUSE_DOWN("vmouse.down", Category.VIRTUAL_MOUSE),
    VMOUSE_LEFT("vmouse.left", Category.VIRTUAL_MOUSE),
    VMOUSE_RIGHT("vmouse.right", Category.VIRTUAL_MOUSE),
    VMOUSE_LEFT_CLICK("vmouse.left_click", Category.VIRTUAL_MOUSE),
    VMOUSE_RIGHT_CLICK("vmouse.right_click", Category.VIRTUAL_MOUSE),
    VMOUSE_SHIFT_CLICK("vmouse.shift_click", Category.VIRTUAL_MOUSE),
    VMOUSE_SNAP_UP("vmouse.snap_up", Category.VIRTUAL_MOUSE),
    VMOUSE_SNAP_DOWN("vmouse.snap_down", Category.VIRTUAL_MOUSE),
    VMOUSE_SNAP_LEFT("vmouse.snap_left", Category.VIRTUAL_MOUSE),
    VMOUSE_SNAP_RIGHT("vmouse.snap_right", Category.VIRTUAL_MOUSE),
    VMOUSE_SCROLL_UP("vmouse.scroll_up", Category.VIRTUAL_MOUSE),
    VMOUSE_SCROLL_DOWN("vmouse.scroll_down", Category.VIRTUAL_MOUSE),
    KEY_ESCAPE("vmouse.key_escape", Category.VIRTUAL_MOUSE),
    KEY_SHIFT("vmouse.key_shift", Category.VIRTUAL_MOUSE),
    PAGE_NEXT("vmouse.page_next", Category.VIRTUAL_MOUSE),
    PAGE_PREV("vmouse.page_prev", Category.VIRTUAL_MOUSE),
    PAGE_UP("vmouse.page_up", Category.VIRTUAL_MOUSE),
    PAGE_DOWN("vmouse.page_down", Category.VIRTUAL_MOUSE),
    TOGGLE_VMOUSE("vmouse.toggle", Category.VIRTUAL_MOUSE),

    // Radial Menu
    OPEN_RADIAL("radial.open", Category.RADIAL),
    RADIAL_NAV_UP("radial.nav_up", Category.RADIAL),
    RADIAL_NAV_DOWN("radial.nav_down", Category.RADIAL),
    RADIAL_NAV_LEFT("radial.nav_left", Category.RADIAL),
    RADIAL_NAV_RIGHT("radial.nav_right", Category.RADIAL),

    // Gyro
    GYRO_ACTIVATION("gyro.activation", Category.GYRO),

    // Debug
    DEBUG_RADIAL("debug.radial_menu", Category.DEBUG),
    TOGGLE_DEBUG_MENU("debug.toggle_menu", Category.DEBUG),
    DEBUG_FPS("debug.fps", Category.DEBUG),
    DEBUG_PROFILER("debug.profiler", Category.DEBUG),
    DEBUG_NET_GRAPH("debug.net_graph", Category.DEBUG),
    DEBUG_CHARTS("debug.charts", Category.DEBUG);

    public final String id;
    public final Category category;

    InputAction(String id, Category category) {
        this.id = id;
        this.category = category;
    }

    public enum Category {
        GAMEPLAY, HOTBAR, GUI, VIRTUAL_MOUSE, RADIAL, GYRO, DEBUG, CHORDS
    }
}
