package dev.steampad.input;

import dev.steampad.config.BindingConfig;
import dev.steampad.config.ConfigManager;
import dev.steampad.config.ControllerConfig;
import dev.steampad.radial.RadialMenuController;
import dev.steampad.service.ActiveControllerService;
import dev.steampad.service.BatteryMonitorService;
import dev.steampad.service.ControllerIsolationService;
import dev.steampad.service.ControllerManager;
import dev.steampad.steam.SteamInputManager;
import dev.steampad.util.LogUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.util.List;

/**
 * Central dispatcher: reads the active controller's state each tick,
 * resolves bindings (including chords), and executes game actions.
 *
 * Called from the Fabric ClientTickEvents.END_CLIENT_TICK event.
 */
public final class InputBindingManager {

    private static final ChordResolver chordResolver = new ChordResolver();
    private static List<InputBinding> activeBindings = List.of();
    private static long lastLoadedHandle = -1L;

    private InputBindingManager() {}

    /**
     * Main tick method. Called once per client tick.
     */
    public static void tick() {
        long activeHandle = ActiveControllerService.getActiveHandle();
        if (activeHandle == 0L) {
            ControllerInputState.clear();
            SteamSlotDispatcher.releaseAll();
            return;
        }

        // Steam Input slots (paddles etc.) are dispatched on BOTH paths: even when SDL3/GLFW serves
        // the active controller, Steam Input may still own the paddles (Game-Mode hybrid, B032).
        long tSlots = dev.steampad.util.TickProfiler.begin();
        SteamSlotDispatcher.tick();
        dev.steampad.util.TickProfiler.end(dev.steampad.util.TickProfiler.SLOT_DISPATCH, tSlots);

        // Fallback controllers (GLFW or SDL3) drive the game directly (vanilla key bindings + look),
        // bypassing the Steam ActionSet pipeline which is unavailable in this environment.
        if (ControllerManager.isFallbackHandle(activeHandle)) {
            long tPad = dev.steampad.util.TickProfiler.begin();
            GamepadInputDispatcher.tick(activeHandle);
            dev.steampad.util.TickProfiler.end(dev.steampad.util.TickProfiler.PAD_TICK, tPad);
            return;
        }

        // Reload bindings if controller changed
        if (lastLoadedHandle != activeHandle) {
            reloadBindings(activeHandle);
        }

        // Get current state for active controller
        var stateOpt = SteamInputManager.getState(activeHandle);
        if (stateOpt.isEmpty()) return;

        var state = stateOpt.get();

        // Isolation check (should always pass here, but double-check)
        if (!ControllerIsolationService.isActive(state)) return;

        // Battery monitor
        BatteryMonitorService.tick();

        // Determine context
        MinecraftClient mc = MinecraftClient.getInstance();
        Screen currentScreen = mc.currentScreen;
        boolean hasScreen = currentScreen != null;
        boolean isInGame = !hasScreen && mc.player != null;

        // ActionSet activation lives in SteamSlotDispatcher.tick() (runs on BOTH paths) — the call
        // that used to sit here was AFTER the fallback early-return above, so it never executed in
        // the Game-Mode hybrid setup, leaving every Steam action permanently inert (B074 → B075).

        // Build dispatch context
        InputDispatchContext ctx = new InputDispatchContext(
            hasScreen, isInGame,
            RadialMenuController.isOpen() || dev.steampad.emote.EmoteWheelController.isOpen(),
            VirtualMouseController.isActive(),
            currentScreen != null ? currentScreen.getClass().getSimpleName() : "null"
        );

        // Hold and move state: must be set/cleared every tick regardless of bindings.
        if (ctx.isInGame) {
            holdKey(mc.options.attackKey, state.isPressed(ControllerState.DigitalAction.ATTACK));
            holdKey(mc.options.useKey,    state.isPressed(ControllerState.DigitalAction.USE));
        } else {
            // Release held keys and movement when a screen is open or player is absent.
            holdKey(mc.options.attackKey, false);
            holdKey(mc.options.useKey,    false);
            ControllerInputState.clear();
        }

        // Process gyro
        processGyro(state, activeHandle);

        // Process analog (camera + movement + vmouse)
        processAnalog(state, ctx, activeHandle);

        // Resolve and dispatch digital bindings
        List<InputAction> actionsToFire = chordResolver.resolve(state, activeBindings);
        for (InputAction action : actionsToFire) {
            dispatch(action, ctx, activeHandle);
        }
    }

    private static void holdKey(KeyBinding key, boolean down) {
        if (key == null) return;
        KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey()), down);
    }

    private static void reloadBindings(long handle) {
        BindingConfig cfg = ConfigManager.getBindingConfig(handle);
        activeBindings = cfg.toInputBindings();
        chordResolver.reset();
        lastLoadedHandle = handle;
        LogUtil.debug("Bindings reloaded for controller {}: {} bindings", handle, activeBindings.size());
    }

    private static void processAnalog(ControllerState state, InputDispatchContext ctx, long handle) {
        ControllerConfig cfg = ConfigManager.getControllerConfig(handle);

        float[] left = DeadzoneProcessor.process(state.leftStick, cfg.leftStickDeadzone);
        float[] right = DeadzoneProcessor.process(state.rightStick, cfg.rightStickDeadzone);

        MinecraftClient mc = MinecraftClient.getInstance();

        if (ctx.isInGame && mc.player != null) {
            // Left stick → analog movement. Steam Input reports Y+ = up (game space), so no Y inversion.
            ControllerInputState.set(
                -left[0], left[1],
                state.isPressed(ControllerState.DigitalAction.JUMP),
                state.isPressed(ControllerState.DigitalAction.SNEAK),
                state.isPressed(ControllerState.DigitalAction.SPRINT));

            // Right stick → camera look.
            float yaw = right[0] * cfg.horizontalSensitivity * dev.steampad.config.ControllerConfig.SENSITIVITY_REBASE * 5f;
            float pitch = right[1] * cfg.verticalSensitivity * dev.steampad.config.ControllerConfig.SENSITIVITY_REBASE * 5f;
            if (cfg.invertLookY) pitch = -pitch;
            if (yaw != 0f || pitch != 0f) {
                mc.player.changeLookDirection(yaw, pitch);
            }
        }

        if (ctx.isVMouseActive && ctx.hasScreenOpen) {
            float[] vm = DeadzoneProcessor.process(state.vmouse, 0.1f);
            VirtualMouseController.update(vm[0] * cfg.virtualMouseSensitivity,
                                          -vm[1] * cfg.virtualMouseSensitivity);
        }

        if (RadialMenuController.isOpen()) {
            // Radial navigation via right stick or D-pad
            RadialMenuController.updateAnalog(right[0], right[1]);
        } else if (dev.steampad.emote.EmoteWheelController.isOpen()) {
            dev.steampad.emote.EmoteWheelController.updateAnalog(right[0], right[1]);
        }
    }

    private static void processGyro(ControllerState state, long handle) {
        if (state.gyro == null || state.gyro.length < 2) return;
        ControllerConfig cfg = ConfigManager.getControllerConfig(handle);
        if (!cfg.gyroEnabled) return;   // gyro is opt-in (off by default)
        boolean gyroButtonHeld = state.isPressed(ControllerState.DigitalAction.GYRO_BUTTON);
        // Apply the user's gyro settings — GyroHandler.configure previously had NO caller, so the
        // whole gyro section of the settings screen (mode/yaw/invert/sensitivity/flick) was inert.
        GyroHandler.configure(
                GyroHandler.GyroMode.valueOf(cfg.gyroBehaviour.name()),
                GyroHandler.YawMode.valueOf(cfg.yawMode.name()),
                GyroHandler.RequireButton.valueOf(cfg.gyroRequireButton.name()),
                cfg.gyroSensitivity, cfg.gyroInvertX, cfg.gyroInvertY, cfg.flickStick);
        GyroHandler.process(state.gyro[0], state.gyro[1], gyroButtonHeld);
    }

    private static void dispatch(InputAction action, InputDispatchContext ctx, long handle) {
        ActionExecutor.execute(action, ctx, handle);
    }
}
