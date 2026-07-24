package dev.steampad.service;

import dev.steampad.input.ControllerState;

/**
 * Enforces that only the active controller's state is dispatched as game actions.
 * All other controllers are filtered out at this layer.
 */
public final class ControllerIsolationService {

    private ControllerIsolationService() {}

    /**
     * Returns true only if the given controller state belongs to the currently
     * active controller for this Minecraft instance.
     */
    public static boolean isActive(ControllerState state) {
        long active = ActiveControllerService.getActiveHandle();
        if (active == 0L) return false;
        return state.getHandle() == active;
    }

    /**
     * Returns true if the given handle is the active one.
     */
    public static boolean isActive(long handle) {
        return handle != 0L && handle == ActiveControllerService.getActiveHandle();
    }
}
