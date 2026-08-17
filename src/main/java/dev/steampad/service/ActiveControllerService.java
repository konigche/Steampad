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
        // Also by identity: the handle alone is meaningless in the NEXT process — see
        // GlobalConfig.lastActiveControllerId. Only overwrite when the identity is actually known, so a
        // call made before the first poll can't blank a good value.
        String key = ControllerIdentityService.keyFor(handle);
        if (!key.isEmpty()) ConfigManager.getGlobal().lastActiveControllerId = key;
        ConfigManager.saveGlobal();
        LogUtil.info("Active controller set to handle={} (identity '{}')", handle, key);
    }

    public static void clearActive() {
        activeHandle = 0L;
        ConfigManager.getGlobal().lastActiveControllerHandle = 0L;
        ConfigManager.getGlobal().lastActiveControllerId = "";
        ConfigManager.saveGlobal();
        LogUtil.info("Active controller cleared.");
    }

    /**
     * Marks a controller as the user's "Default", by persistent identity.
     *
     * <p>Identity, not display name: the name a pad reports changes with the backend that enumerated
     * it and with the compatibility mode it booted in, so a saved name frequently failed to match on
     * the next launch — which is exactly the reported "el PRO 3 no lo recuerda como predeterminado
     * muchas veces". Two pads of the same model also share a name, so a name could never point at one
     * of them specifically. Pass handle 0 to clear the preference.
     */
    public static void setPreferred(long handle) {
        String key = ControllerIdentityService.keyFor(handle);
        ConfigManager.getGlobal().preferredControllerId = key;
        ConfigManager.getGlobal().preferredControllerName = "";   // superseded; never written again
        ConfigManager.saveGlobal();
        LogUtil.info("Preferred (default) controller set to identity '{}'.", key);
    }

    /** True when this handle is the controller the user marked as "Default". */
    public static boolean isPreferred(long handle) {
        String pref = ConfigManager.getGlobal().preferredControllerId;
        return pref != null && !pref.isEmpty() && pref.equals(ControllerIdentityService.keyFor(handle));
    }

    /**
     * Restores the active controller at startup: the user's "Default" controller by persistent
     * identity, else the last-active handle if it happens to still be valid. Only ever sets a
     * controller that is actually present.
     */
    public static void restoreFromConfig() {
        var connected = ControllerManager.getConnectedControllers();
        migratePreferredName(connected);

        // 1) Preferred ("Default") controller by identity — what "guardar predeterminado" sets.
        String preferred = ConfigManager.getGlobal().preferredControllerId;
        if (preferred != null && !preferred.isEmpty()) {
            long h = ControllerIdentityService.handleFor(preferred);
            if (h != 0L) {
                activeHandle = h;
                ConfigManager.getGlobal().lastActiveControllerHandle = h;
                LogUtil.info("Restored preferred (default) controller '{}' (identity {}).",
                        ControllerIdentityService.nameFor(preferred), preferred);
                return;
            }
            LogUtil.info("Preferred controller (identity {}) not connected at startup.", preferred);
        }

        // 2) Otherwise the last-active controller BY IDENTITY. This must be tried before the raw
        //    handle: the handle is a per-process number and the pad now carrying last session's number
        //    is usually a different device (see GlobalConfig.lastActiveControllerId).
        String lastId = ConfigManager.getGlobal().lastActiveControllerId;
        if (lastId != null && !lastId.isEmpty()) {
            long h = ControllerIdentityService.handleFor(lastId);
            if (h != 0L) {
                activeHandle = h;
                ConfigManager.getGlobal().lastActiveControllerHandle = h;
                LogUtil.info("Restored last active controller '{}' (identity {}).",
                        ControllerIdentityService.nameFor(lastId), lastId);
                return;
            }
            LogUtil.info("Last active controller (identity {}) not connected at startup.", lastId);
            return;   // known device, simply absent — do NOT fall through to the stale handle below.
        }

        // 3) Legacy fallback: the raw handle, for configs written before identities were recorded.
        //    Still guarded on the device being present, exactly as before.
        long saved = ConfigManager.getGlobal().lastActiveControllerHandle;
        if (saved != 0L && connected.stream().anyMatch(r -> r.handle == saved)) {
            activeHandle = saved;
            LogUtil.info("Restored active controller from config (legacy handle): handle={}", saved);
        } else if (saved != 0L) {
            LogUtil.info("Saved controller handle={} not connected, skipping restore.", saved);
        }
    }

    /**
     * One-time upgrade of a pre-existing "Default" saved as a display name. Runs only while the new
     * identity field is empty and only converts when that name is actually connected right now — the
     * name is all we have to go on, so there is nothing to resolve it against otherwise. Failing to
     * convert costs the user one click on "Predeterminado", never a wrong controller.
     */
    private static void migratePreferredName(java.util.List<SteamControllerHandleRef> connected) {
        var g = ConfigManager.getGlobal();
        String id = g.preferredControllerId;
        String legacyName = g.preferredControllerName;
        if ((id != null && !id.isEmpty()) || legacyName == null || legacyName.isEmpty()) return;
        for (SteamControllerHandleRef r : connected) {
            if (!legacyName.equals(r.displayName)) continue;
            String key = ControllerIdentityService.keyFor(r.handle);
            if (key.isEmpty()) continue;
            g.preferredControllerId = key;
            g.preferredControllerName = "";
            ConfigManager.saveGlobal();
            LogUtil.info("Migrated preferred controller '{}' from name to identity '{}'.", legacyName, key);
            return;
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
