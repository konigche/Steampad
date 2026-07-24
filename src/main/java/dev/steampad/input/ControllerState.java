package dev.steampad.input;

import com.codedisaster.steamworks.SteamControllerDigitalActionHandle;
import dev.steampad.steam.SteamActionRegistry;
import dev.steampad.steam.SteamControllerHandleRef;

/**
 * Immutable snapshot of a controller's state at a given tick.
 *
 * Digital actions are indexed by DigitalAction.ordinal().
 * Analog values are raw [-1.0, 1.0] before deadzone processing.
 */
public final class ControllerState {

    public final SteamControllerHandleRef ref;
    public final boolean[] digital;    // indexed by DigitalAction.ordinal()
    public final float[] leftStick;    // [x, y]
    public final float[] rightStick;   // [x, y]
    public final float[] gyro;         // [pitch, yaw] from rotation velocity
    public final float[] vmouse;       // [x, y] analog for virtual mouse
    public final float battery;        // 0.0–1.0, or -1 if unknown

    public ControllerState(SteamControllerHandleRef ref, boolean[] digital,
                           float[] leftStick, float[] rightStick,
                           float[] gyro, float[] vmouse, float battery) {
        this.ref = ref;
        this.digital = digital;
        this.leftStick = leftStick;
        this.rightStick = rightStick;
        this.gyro = gyro;
        this.vmouse = vmouse;
        this.battery = battery;
    }

    public boolean isPressed(DigitalAction action) {
        int idx = action.ordinal();
        return idx < digital.length && digital[idx];
    }

    public long getHandle() {
        return ref.handle;
    }

    /**
     * All digital actions, in the order they are stored in the digital[] array.
     * Must stay in sync with SteamActionRegistry handle assignments.
     */
    public enum DigitalAction {
        WALK_FORWARD,
        WALK_BACKWARD,
        STRAFE_LEFT,
        STRAFE_RIGHT,
        JUMP,
        SNEAK,
        ATTACK,
        USE,
        SPRINT,
        PAUSE,
        INVENTORY,
        SWAP_HANDS,
        OPEN_CHAT,
        DROP_ITEM,
        DROP_STACK,
        PICK_BLOCK,
        CHANGE_PERSPECTIVE,
        NEXT_HOTBAR,
        PREV_HOTBAR,
        OPEN_RADIAL,
        TOGGLE_DEBUG,
        GYRO_BUTTON,
        GUI_PRESS,
        GUI_BACK,
        GUI_NAV_UP,
        GUI_NAV_DOWN,
        GUI_NAV_LEFT,
        GUI_NAV_RIGHT,
        GUI_NEXT_TAB,
        GUI_PREV_TAB,
        CYCLE_FORWARD,
        CYCLE_BACKWARD,
        TOGGLE_VMOUSE,
        RADIAL_NAV_UP,
        RADIAL_NAV_DOWN,
        RADIAL_NAV_LEFT,
        RADIAL_NAV_RIGHT,
        // Generic user-assignable Steam Input slots (paddles etc.) — must stay LAST and contiguous;
        // SteamSlotDispatcher indexes them as SLOT_1.ordinal() + i.
        SLOT_1,
        SLOT_2,
        SLOT_3,
        SLOT_4,
        SLOT_5,
        SLOT_6,
        SLOT_7,
        SLOT_8,
        SLOT_9,
        SLOT_10;

        /** Returns Steam action handles in the same order as this enum. */
        public static SteamControllerDigitalActionHandle[] getHandles() {
            SteamControllerDigitalActionHandle[] fixed = new SteamControllerDigitalActionHandle[]{
                SteamActionRegistry.actionWalkForward,
                SteamActionRegistry.actionWalkBackward,
                SteamActionRegistry.actionStrafeLeft,
                SteamActionRegistry.actionStrafeRight,
                SteamActionRegistry.actionJump,
                SteamActionRegistry.actionSneak,
                SteamActionRegistry.actionAttack,
                SteamActionRegistry.actionUse,
                SteamActionRegistry.actionSprint,
                SteamActionRegistry.actionPause,
                SteamActionRegistry.actionInventory,
                SteamActionRegistry.actionSwapHands,
                SteamActionRegistry.actionOpenChat,
                SteamActionRegistry.actionDropItem,
                SteamActionRegistry.actionDropStack,
                SteamActionRegistry.actionPickBlock,
                SteamActionRegistry.actionChangePerspective,
                SteamActionRegistry.actionNextHotbar,
                SteamActionRegistry.actionPrevHotbar,
                SteamActionRegistry.actionOpenRadial,
                SteamActionRegistry.actionToggleDebug,
                SteamActionRegistry.actionGyroButton,
                SteamActionRegistry.actionGuiPress,
                SteamActionRegistry.actionGuiBack,
                SteamActionRegistry.actionGuiNavUp,
                SteamActionRegistry.actionGuiNavDown,
                SteamActionRegistry.actionGuiNavLeft,
                SteamActionRegistry.actionGuiNavRight,
                SteamActionRegistry.actionGuiNextTab,
                SteamActionRegistry.actionGuiPrevTab,
                SteamActionRegistry.actionCycleForward,
                SteamActionRegistry.actionCycleBackward,
                SteamActionRegistry.actionToggleVMouse,
                SteamActionRegistry.actionRadialNavUp,
                SteamActionRegistry.actionRadialNavDown,
                SteamActionRegistry.actionRadialNavLeft,
                SteamActionRegistry.actionRadialNavRight
            };
            // Append the generic slot handles (SLOT_1..SLOT_N are the last enum constants).
            SteamControllerDigitalActionHandle[] all =
                    java.util.Arrays.copyOf(fixed, fixed.length + SteamActionRegistry.SLOT_COUNT);
            System.arraycopy(SteamActionRegistry.actionSlots, 0, all, fixed.length,
                    SteamActionRegistry.SLOT_COUNT);
            return all;
        }
    }
}
