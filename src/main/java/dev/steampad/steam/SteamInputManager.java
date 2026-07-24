package dev.steampad.steam;

import com.codedisaster.steamworks.SteamController;
import com.codedisaster.steamworks.SteamControllerAnalogActionHandle;
import com.codedisaster.steamworks.SteamControllerAnalogActionData;
import com.codedisaster.steamworks.SteamControllerDigitalActionHandle;
import com.codedisaster.steamworks.SteamControllerDigitalActionData;
import com.codedisaster.steamworks.SteamControllerHandle;
import com.codedisaster.steamworks.SteamControllerMotionData;
import com.codedisaster.steamworks.SteamNativeHandle;
import dev.steampad.input.ControllerState;
import dev.steampad.util.LogUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Core wrapper around ISteamController (Steamworks4j 1.9.0).
 *
 * Responsibilities:
 * - Initialize/shutdown ISteamController
 * - Enumerate connected controllers each frame
 * - Build ControllerState snapshots
 *
 * Note: ISteamController is the predecessor to ISteamInput; both expose the same
 * ActionSet/ActionHandle model used by SteamPad. ISteamInput support requires
 * Steamworks4j ≥ 1.10 (not yet released at time of writing — see DECISIONS.md D002).
 */
public final class SteamInputManager {

    private static SteamController steamController;
    private static boolean available = false;

    private static final List<SteamControllerHandleRef> connectedControllers = new CopyOnWriteArrayList<>();
    private static final Map<Long, ControllerState> latestStates = new HashMap<>();

    // Pre-allocated handle buffer — avoids GC churn per frame
    private static final SteamControllerHandle[] handleBuffer = new SteamControllerHandle[SteamController.STEAM_CONTROLLER_MAX_COUNT];

    // Re-usable output objects for getDigitalActionData / getAnalogActionData / getMotionData
    private static final SteamControllerDigitalActionData digitalDataBuffer = new SteamControllerDigitalActionData();
    private static final SteamControllerAnalogActionData  analogDataBuffer  = new SteamControllerAnalogActionData();
    private static final SteamControllerMotionData        motionBuffer      = new SteamControllerMotionData();

    static {
        for (int i = 0; i < handleBuffer.length; i++) {
            handleBuffer[i] = new SteamControllerHandle(0L);
        }
    }

    private SteamInputManager() {}

    public static boolean isAvailable() {
        return available;
    }

    /**
     * True when Steam Input is available AND the VDF config was imported (both ActionSets valid).
     * When false, Steam can enumerate controllers but they may include ghost/virtual devices from
     * Steam's internal state. ControllerManager should fall through to SDL3 in this case.
     */
    public static boolean areActionSetsValid() {
        return available && SteamActionRegistry.areActionSetsValid();
    }

    public static boolean init() {
        try {
            steamController = new SteamController();
            if (!steamController.init()) {
                LogUtil.warn("ISteamController.init() returned false.");
                steamController = null;
                return false;
            }
            SteamActionRegistry.registerAll(steamController);
            available = true;
            LogUtil.info("Steam Controller (ISteamController) initialized.");
            return true;
        } catch (Throwable t) {
            LogUtil.error("Steam Controller init exception: {}", t.getMessage(), t);
            steamController = null;
            return false;
        }
    }

    public static void runFrame() {
        if (steamController == null) return;
        steamController.runFrame();
        refreshControllers();
        updateStates();
    }

    public static void shutdown() {
        if (steamController != null) {
            steamController.shutdown();
            steamController = null;
            available = false;
        }
    }

    private static void refreshControllers() {
        int count = steamController.getConnectedControllers(handleBuffer);
        List<SteamControllerHandleRef> updated = new ArrayList<SteamControllerHandleRef>(count);
        for (int i = 0; i < count; i++) {
            long rawHandle = SteamNativeHandle.getNativeHandle(handleBuffer[i]);
            if (rawHandle == 0L) continue;
            String name = resolveControllerName(handleBuffer[i]);
            SteamControllerHandleRef.ControllerType type = resolveControllerType(handleBuffer[i]);
            updated.add(new SteamControllerHandleRef(rawHandle, name, type));
        }
        if (!updated.equals(connectedControllers)) {
            connectedControllers.clear();
            connectedControllers.addAll(updated);
            LogUtil.debug("Controller list updated: {} controllers", count);
        }
    }

    private static String resolveControllerName(SteamControllerHandle handle) {
        try {
            SteamController.InputType type = steamController.getInputTypeForHandle(handle);
            return switch (type) {
                case SteamController    -> "Steam Controller";
                case XBox360Controller  -> "Xbox 360 Controller";
                case XBoxOneController  -> "Xbox One Controller";
                case GenericGamepad     -> "Generic Gamepad";
                case PS3Controller      -> "DualShock 3";
                case PS4Controller      -> "DualShock 4";
                case PS5Controller      -> "DualSense";
                case SwitchProController -> "Switch Pro Controller";
                case SwitchJoyConPair   -> "Joy-Con (Pair)";
                case SwitchJoyConSingle -> "Joy-Con";
                default                 -> "Controller (" + type.name() + ")";
            };
        } catch (Throwable t) {
            return "Unknown Controller";
        }
    }

    private static SteamControllerHandleRef.ControllerType resolveControllerType(SteamControllerHandle handle) {
        try {
            SteamController.InputType type = steamController.getInputTypeForHandle(handle);
            return switch (type) {
                case XBox360Controller, XBoxOneController -> SteamControllerHandleRef.ControllerType.XBOX;
                case PS3Controller, PS4Controller, PS5Controller -> SteamControllerHandleRef.ControllerType.PLAYSTATION;
                case SteamController  -> SteamControllerHandleRef.ControllerType.STEAM_CONTROLLER;
                case SwitchProController, SwitchJoyConPair, SwitchJoyConSingle -> SteamControllerHandleRef.ControllerType.SWITCH;
                default               -> SteamControllerHandleRef.ControllerType.GENERIC;
            };
        } catch (Throwable t) {
            return SteamControllerHandleRef.ControllerType.GENERIC;
        }
    }

    private static void updateStates() {
        if (!SteamActionRegistry.isRegistered()) return;
        for (SteamControllerHandleRef ref : connectedControllers) {
            ControllerState state = buildState(ref);
            latestStates.put(ref.handle, state);
        }
        // Remove stale entries for disconnected controllers
        Set<Long> connectedHandles = new HashSet<Long>();
        for (SteamControllerHandleRef r : connectedControllers) {
            connectedHandles.add(r.handle);
        }
        latestStates.keySet().retainAll(connectedHandles);
    }

    private static ControllerState buildState(SteamControllerHandleRef ref) {
        long h = ref.handle;
        boolean[] digital   = readDigitalActions(h);
        float[] leftStick   = readAnalog(h, SteamActionRegistry.analogLeftStick);
        float[] rightStick  = readAnalog(h, SteamActionRegistry.analogRightStick);
        float[] gyro        = readGyro(h);
        float[] vmouse      = readAnalog(h, SteamActionRegistry.analogVMouse);
        return new ControllerState(ref, digital, leftStick, rightStick, gyro, vmouse, -1f);
    }

    private static boolean[] readDigitalActions(long rawHandle) {
        SteamControllerDigitalActionHandle[] handles = ControllerState.DigitalAction.getHandles();
        boolean[] result = new boolean[handles.length];
        SteamControllerHandle ctrlHandle = new SteamControllerHandle(rawHandle);
        for (int i = 0; i < handles.length; i++) {
            if (handles[i] == null) continue;
            try {
                steamController.getDigitalActionData(ctrlHandle, handles[i], digitalDataBuffer);
                result[i] = digitalDataBuffer.getState() && digitalDataBuffer.getActive();
            } catch (Throwable t) {
                // action may not be registered in VDF — skip silently
            }
        }
        return result;
    }

    private static float[] readAnalog(long rawHandle, SteamControllerAnalogActionHandle actionHandle) {
        if (actionHandle == null) return new float[]{0f, 0f};
        try {
            steamController.getAnalogActionData(new SteamControllerHandle(rawHandle), actionHandle, analogDataBuffer);
            return analogDataBuffer.getActive()
                    ? new float[]{analogDataBuffer.getX(), analogDataBuffer.getY()}
                    : new float[]{0f, 0f};
        } catch (Throwable t) {
            return new float[]{0f, 0f};
        }
    }

    private static float[] readGyro(long rawHandle) {
        try {
            steamController.getMotionData(new SteamControllerHandle(rawHandle), motionBuffer);
            // RotVelX = pitch (tilt forward/back), RotVelZ = yaw (turn left/right)
            return new float[]{motionBuffer.getRotVelX(), motionBuffer.getRotVelZ()};
        } catch (Throwable t) {
            return new float[]{0f, 0f};
        }
    }

    public static List<SteamControllerHandleRef> getConnectedControllers() {
        return Collections.unmodifiableList(connectedControllers);
    }

    public static Optional<ControllerState> getState(long handle) {
        return Optional.ofNullable(latestStates.get(handle));
    }

    public static SteamController getRawController() {
        return steamController;
    }
}
