package dev.steampad.service;

import dev.steampad.config.ConfigManager;
import dev.steampad.steam.SteamControllerHandleRef;
import dev.steampad.util.LogUtil;

import java.util.Optional;

/**
 * Singleton that tracks the active controller for this Minecraft instance.
 * Only one controller can be active at a time; all others are logically suppressed.
 */
public final class ActiveControllerService {

    private static volatile long activeHandle = 0L;

    private ActiveControllerService() {}

    public static long getActiveHandle() {
        return activeHandle;
    }

    public static boolean hasActiveController() {
        return activeHandle != 0L;
    }

    /**
     * Sets the active controller and persists the choice.
     */
    public static void setActive(long handle) {
        activeHandle = handle;
        ConfigManager.getGlobal().lastActiveControllerHandle = handle;
        ConfigManager.saveGlobal();
        LogUtil.info("Active controller set to handle={}", handle);
    }

    public static void clearActive() {
        activeHandle = 0L;
        ConfigManager.getGlobal().lastActiveControllerHandle = 0L;
        ConfigManager.saveGlobal();
        LogUtil.info("Active controller cleared.");
    }

    /**
     * Restores the active controller at startup. Prefers the user's "Default" controller BY NAME
     * (stable across reconnects/sessions, unlike the synthetic handle), then falls back to the last
     * active handle if it's still connected. Only sets a controller that is actually present.
     */
    public static void restoreFromConfig() {
        var connected = ControllerManager.getConnectedControllers();

        // 1) Preferred ("Default") controller by name — this is what "guardar predeterminado" sets.
        String preferred = ConfigManager.getGlobal().preferredControllerName;
        if (preferred != null && !preferred.isEmpty()) {
            var byName = connected.stream().filter(r -> preferred.equals(r.displayName)).findFirst();
            if (byName.isPresent()) {
                activeHandle = byName.get().handle;
                ConfigManager.getGlobal().lastActiveControllerHandle = activeHandle;
                LogUtil.info("Restored preferred (default) controller by name: {}", preferred);
                return;
            }
            LogUtil.info("Preferred controller '{}' not connected at startup.", preferred);
        }

        // 2) Otherwise the last-active handle, if it's still connected.
        long saved = ConfigManager.getGlobal().lastActiveControllerHandle;
        if (saved != 0L && connected.stream().anyMatch(r -> r.handle == saved)) {
            activeHandle = saved;
            LogUtil.info("Restored active controller from config: handle={}", saved);
        } else if (saved != 0L) {
            LogUtil.info("Saved controller handle={} not connected, skipping restore.", saved);
        }
    }

    public static Optional<SteamControllerHandleRef> getActiveRef() {
        if (activeHandle == 0L) return Optional.empty();
        return ControllerManager.getConnectedControllers()
                .stream()
                .filter(r -> r.handle == activeHandle)
                .findFirst();
    }
}
