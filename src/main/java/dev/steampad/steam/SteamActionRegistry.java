package dev.steampad.steam;

import com.codedisaster.steamworks.SteamController;
import com.codedisaster.steamworks.SteamControllerActionSetHandle;
import com.codedisaster.steamworks.SteamControllerAnalogActionHandle;
import com.codedisaster.steamworks.SteamControllerDigitalActionHandle;
import com.codedisaster.steamworks.SteamControllerHandle;
import com.codedisaster.steamworks.SteamNativeHandle;
import dev.steampad.util.LogUtil;

/**
 * Registers Steam Input ActionSets and ActionHandles via ISteamController.
 *
 * ActionSets must be defined in the Steam Input VDF configuration file.
 * If the VDF is not imported by the user, action handles will be null (unregistered).
 *
 * ActionSets — one per in-mod slot layer, so what Steam's configurator shows matches what the mod
 * does (D077; feedback: "cuando entro a Set de acciones no corresponde con los que tenemos en el
 * juego... utiliza un criterio lógico"):
 * - SteamPad_Gameplay  : no screen open, on foot
 * - SteamPad_Menu      : a non-inventory screen is open (settings, pause, ...)
 * - SteamPad_Inventory : a container/inventory screen is open
 * - SteamPad_Mounted   : riding an entity (horse, boat, minecart, ...)
 */
public final class SteamActionRegistry {

    // ActionSet handles — one per context/layer (see class doc)
    public static SteamControllerActionSetHandle actionSetGameplay;
    public static SteamControllerActionSetHandle actionSetMenu;
    public static SteamControllerActionSetHandle actionSetInventory;
    public static SteamControllerActionSetHandle actionSetMounted;

    // Digital action handles — InGame
    public static SteamControllerDigitalActionHandle actionWalkForward;
    public static SteamControllerDigitalActionHandle actionWalkBackward;
    public static SteamControllerDigitalActionHandle actionStrafeLeft;
    public static SteamControllerDigitalActionHandle actionStrafeRight;
    public static SteamControllerDigitalActionHandle actionJump;
    public static SteamControllerDigitalActionHandle actionSneak;
    public static SteamControllerDigitalActionHandle actionAttack;
    public static SteamControllerDigitalActionHandle actionUse;
    public static SteamControllerDigitalActionHandle actionSprint;
    public static SteamControllerDigitalActionHandle actionPause;
    public static SteamControllerDigitalActionHandle actionInventory;
    public static SteamControllerDigitalActionHandle actionSwapHands;
    public static SteamControllerDigitalActionHandle actionOpenChat;
    public static SteamControllerDigitalActionHandle actionDropItem;
    public static SteamControllerDigitalActionHandle actionDropStack;
    public static SteamControllerDigitalActionHandle actionPickBlock;
    public static SteamControllerDigitalActionHandle actionChangePerspective;
    public static SteamControllerDigitalActionHandle actionNextHotbar;
    public static SteamControllerDigitalActionHandle actionPrevHotbar;
    public static SteamControllerDigitalActionHandle actionOpenRadial;
    public static SteamControllerDigitalActionHandle actionToggleDebug;
    public static SteamControllerDigitalActionHandle actionGyroButton;

    /**
     * Generic user-assignable slots (steampad_slot_1..N). The user maps a physical input (e.g. the
     * back paddles that Steam Input claims exclusively in Game Mode) to a slot in Steam's controller
     * configurator; in the mod's Buttons screen each slot is assigned to any vanilla/mod keybind.
     */
    public static final int SLOT_COUNT = 10;
    public static final SteamControllerDigitalActionHandle[] actionSlots =
            new SteamControllerDigitalActionHandle[SLOT_COUNT];

    // Digital action handles — GUI
    public static SteamControllerDigitalActionHandle actionGuiPress;
    public static SteamControllerDigitalActionHandle actionGuiBack;
    public static SteamControllerDigitalActionHandle actionGuiNavUp;
    public static SteamControllerDigitalActionHandle actionGuiNavDown;
    public static SteamControllerDigitalActionHandle actionGuiNavLeft;
    public static SteamControllerDigitalActionHandle actionGuiNavRight;
    public static SteamControllerDigitalActionHandle actionGuiNextTab;
    public static SteamControllerDigitalActionHandle actionGuiPrevTab;
    public static SteamControllerDigitalActionHandle actionCycleForward;
    public static SteamControllerDigitalActionHandle actionCycleBackward;
    public static SteamControllerDigitalActionHandle actionToggleVMouse;
    public static SteamControllerDigitalActionHandle actionRadialNavUp;
    public static SteamControllerDigitalActionHandle actionRadialNavDown;
    public static SteamControllerDigitalActionHandle actionRadialNavLeft;
    public static SteamControllerDigitalActionHandle actionRadialNavRight;

    // Analog action handles (gyro is read via getMotionData, not an analog action)
    public static SteamControllerAnalogActionHandle analogLeftStick;
    public static SteamControllerAnalogActionHandle analogRightStick;
    public static SteamControllerAnalogActionHandle analogVMouse;

    private static boolean registered = false;
    private static SteamController steamController;

    private SteamActionRegistry() {}

    public static boolean isRegistered() {
        return registered;
    }

    public static void registerAll(SteamController controller) {
        steamController = controller;

        actionSetGameplay  = controller.getActionSetHandle("SteamPad_Gameplay");
        actionSetMenu      = controller.getActionSetHandle("SteamPad_Menu");
        actionSetInventory = controller.getActionSetHandle("SteamPad_Inventory");
        actionSetMounted   = controller.getActionSetHandle("SteamPad_Mounted");

        boolean vdfPresent = isValidHandle(actionSetGameplay) && isValidHandle(actionSetMenu);
        if (!vdfPresent) {
            LogUtil.warn("Steam Input ActionSet handles are 0. VDF config may not be imported in Steam.");
            LogUtil.warn("Controllers will use raw button fallback mode.");
        }

        // Register digital actions — InGame
        actionWalkForward    = controller.getDigitalActionHandle("steampad_walk_forward");
        actionWalkBackward   = controller.getDigitalActionHandle("steampad_walk_backward");
        actionStrafeLeft     = controller.getDigitalActionHandle("steampad_strafe_left");
        actionStrafeRight    = controller.getDigitalActionHandle("steampad_strafe_right");
        actionJump           = controller.getDigitalActionHandle("steampad_jump");
        actionSneak          = controller.getDigitalActionHandle("steampad_sneak");
        actionAttack         = controller.getDigitalActionHandle("steampad_attack");
        actionUse            = controller.getDigitalActionHandle("steampad_use");
        actionSprint         = controller.getDigitalActionHandle("steampad_sprint");
        actionPause          = controller.getDigitalActionHandle("steampad_pause");
        actionInventory      = controller.getDigitalActionHandle("steampad_inventory");
        actionSwapHands      = controller.getDigitalActionHandle("steampad_swap_hands");
        actionOpenChat       = controller.getDigitalActionHandle("steampad_open_chat");
        actionDropItem       = controller.getDigitalActionHandle("steampad_drop_item");
        actionDropStack      = controller.getDigitalActionHandle("steampad_drop_stack");
        actionPickBlock      = controller.getDigitalActionHandle("steampad_pick_block");
        actionChangePerspective = controller.getDigitalActionHandle("steampad_change_perspective");
        actionNextHotbar     = controller.getDigitalActionHandle("steampad_next_hotbar");
        actionPrevHotbar     = controller.getDigitalActionHandle("steampad_prev_hotbar");
        actionOpenRadial     = controller.getDigitalActionHandle("steampad_open_radial");
        actionToggleDebug    = controller.getDigitalActionHandle("steampad_toggle_debug");
        actionGyroButton     = controller.getDigitalActionHandle("steampad_gyro_button");
        for (int i = 0; i < SLOT_COUNT; i++) {
            actionSlots[i] = controller.getDigitalActionHandle("steampad_slot_" + (i + 1));
        }

        // Register digital actions — GUI
        actionGuiPress       = controller.getDigitalActionHandle("steampad_gui_press");
        actionGuiBack        = controller.getDigitalActionHandle("steampad_gui_back");
        actionGuiNavUp       = controller.getDigitalActionHandle("steampad_gui_nav_up");
        actionGuiNavDown     = controller.getDigitalActionHandle("steampad_gui_nav_down");
        actionGuiNavLeft     = controller.getDigitalActionHandle("steampad_gui_nav_left");
        actionGuiNavRight    = controller.getDigitalActionHandle("steampad_gui_nav_right");
        actionGuiNextTab     = controller.getDigitalActionHandle("steampad_gui_next_tab");
        actionGuiPrevTab     = controller.getDigitalActionHandle("steampad_gui_prev_tab");
        actionCycleForward   = controller.getDigitalActionHandle("steampad_cycle_forward");
        actionCycleBackward  = controller.getDigitalActionHandle("steampad_cycle_backward");
        actionToggleVMouse   = controller.getDigitalActionHandle("steampad_toggle_vmouse");
        actionRadialNavUp    = controller.getDigitalActionHandle("steampad_radial_nav_up");
        actionRadialNavDown  = controller.getDigitalActionHandle("steampad_radial_nav_down");
        actionRadialNavLeft  = controller.getDigitalActionHandle("steampad_radial_nav_left");
        actionRadialNavRight = controller.getDigitalActionHandle("steampad_radial_nav_right");

        // Register analog actions
        analogLeftStick  = controller.getAnalogActionHandle("steampad_left_stick");
        analogRightStick = controller.getAnalogActionHandle("steampad_right_stick");
        analogVMouse     = controller.getAnalogActionHandle("steampad_vmouse");

        registered = true;
        LogUtil.info("Steam Input action handles registered. ActionSets valid: Gameplay={}, Menu={}, "
                        + "Inventory={}, Mounted={}",
                isValidHandle(actionSetGameplay), isValidHandle(actionSetMenu),
                isValidHandle(actionSetInventory), isValidHandle(actionSetMounted));
    }

    /** Activates the ActionSet matching an in-mod slot layer/context. Inventory and Mounted fall back
     *  to Menu/Gameplay respectively when their own handle is invalid (an older deployed VDF that only
     *  had two sets keeps working instead of silently activating nothing). */
    public static void activateSetFor(dev.steampad.input.SteamSlotDispatcher.Context ctx, long controllerHandle) {
        SteamControllerActionSetHandle set = switch (ctx) {
            case GAMEPLAY -> actionSetGameplay;
            case MENU -> actionSetMenu;
            case INVENTORY -> isValidHandle(actionSetInventory) ? actionSetInventory : actionSetMenu;
            case MOUNTED -> isValidHandle(actionSetMounted) ? actionSetMounted : actionSetGameplay;
        };
        if (steamController != null && isValidHandle(set)) {
            steamController.activateActionSet(new SteamControllerHandle(controllerHandle), set);
        }
    }

    /** Returns true when a handle was successfully resolved (native handle != 0). */
    public static boolean isValidHandle(SteamNativeHandle handle) {
        return handle != null && SteamNativeHandle.getNativeHandle(handle) != 0L;
    }

    /** True when the two core ActionSet handles (Gameplay + Menu) are valid (VDF deployed/imported). */
    public static boolean areActionSetsValid() {
        return isValidHandle(actionSetGameplay) && isValidHandle(actionSetMenu);
    }
}
