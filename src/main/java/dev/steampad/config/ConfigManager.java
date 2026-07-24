package dev.steampad.config;

import dev.steampad.util.JsonUtil;
import dev.steampad.util.LogUtil;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central manager for all SteamPad configuration.
 * Loads and saves JSON files under .minecraft/config/steampad/
 * Auto-saves on every modification (write-through model).
 */
public final class ConfigManager {

    private static final Path CONFIG_ROOT =
            FabricLoader.getInstance().getConfigDir().resolve("steampad");
    private static final Path GLOBAL_PATH = CONFIG_ROOT.resolve("global.json");
    private static final Path CONTROLLERS_DIR = CONFIG_ROOT.resolve("controllers");

    private static GlobalConfig global;
    private static final Map<Long, ControllerConfig> controllerConfigs = new HashMap<>();
    private static final Map<Long, BindingConfig> bindingConfigs = new HashMap<>();
    private static final Map<Long, RadialConfig> radialConfigs = new HashMap<>();

    private ConfigManager() {}

    public static void loadAll() {
        global = JsonUtil.loadFromFile(GLOBAL_PATH, GlobalConfig.class, GlobalConfig.defaults());
        LogUtil.info("Global config loaded. loadNatives={}", global.loadNatives);
    }

    public static GlobalConfig getGlobal() {
        // Lazy-load the REAL file, never hand back defaults: an early caller (before the explicit
        // loadAll() in client init) used to silently get a defaults instance — SteamBootstrap.init()
        // read steamAttachMode=NEVER at startup no matter what the user had saved (D077). Loading here
        // is safe: it only reads a JSON file, and CONFIG_ROOT is already resolved at class-init.
        if (global == null) loadAll();
        return global;
    }

    public static void saveGlobal() {
        JsonUtil.saveToFile(GLOBAL_PATH, global);
        LogUtil.debug("Global config saved.");
    }

    public static ControllerConfig getControllerConfig(long handle) {
        return controllerConfigs.computeIfAbsent(handle, h -> {
            Path path = controllerPath(h);
            return JsonUtil.loadFromFile(path, ControllerConfig.class, ControllerConfig.defaults());
        });
    }

    public static void saveControllerConfig(long handle) {
        Path path = controllerPath(handle);
        JsonUtil.saveToFile(path, controllerConfigs.getOrDefault(handle, ControllerConfig.defaults()));
    }

    public static BindingConfig getBindingConfig(long handle) {
        return bindingConfigs.computeIfAbsent(handle, h -> {
            Path path = bindingPath(h);
            return JsonUtil.loadFromFile(path, BindingConfig.class, BindingConfig.defaults());
        });
    }

    public static void saveBindingConfig(long handle) {
        Path path = bindingPath(handle);
        JsonUtil.saveToFile(path, bindingConfigs.getOrDefault(handle, BindingConfig.defaults()));
    }

    public static RadialConfig getRadialConfig(long handle) {
        return radialConfigs.computeIfAbsent(handle, h -> {
            Path path = radialPath(h);
            return JsonUtil.loadFromFile(path, RadialConfig.class, RadialConfig.defaults());
        });
    }

    public static void saveRadialConfig(long handle) {
        Path path = radialPath(handle);
        JsonUtil.saveToFile(path, radialConfigs.getOrDefault(handle, RadialConfig.defaults()));
    }

    /** Persists all loaded configs to disk. */
    public static void saveAll() {
        saveGlobal();
        controllerConfigs.keySet().forEach(ConfigManager::saveControllerConfig);
        bindingConfigs.keySet().forEach(ConfigManager::saveBindingConfig);
        radialConfigs.keySet().forEach(ConfigManager::saveRadialConfig);
    }

    private static Path controllerPath(long handle) {
        return CONTROLLERS_DIR.resolve("controller_" + handle + ".json");
    }

    private static Path bindingPath(long handle) {
        return CONTROLLERS_DIR.resolve("bindings_" + handle + ".json");
    }

    private static Path radialPath(long handle) {
        return CONTROLLERS_DIR.resolve("radial_" + handle + ".json");
    }

    // ---- Reconnect config migration ------------------------------------------------------------
    //
    // Bug (feedback: "cuando desconecto el gamepad... se desconfiguran los botones... tengo que
    // reiniciar el juego"): every per-controller file above is keyed by the numeric HANDLE, but
    // SDL3/GLFW hand out a NEW handle every time the SAME physical controller reconnects — confirmed
    // on real hardware, consecutive session logs showed the identical 8BitDo pad go from handle
    // ...386369 to ...386370 after a disconnect/reconnect. getControllerConfig/getBindingConfig/
    // getRadialConfig had no way to know the new handle belonged to a pad they'd already configured,
    // so a mid-session reconnect silently created blank defaults under the new handle — the user's
    // real bindings were still on disk, just orphaned under the old handle's filename. A full game
    // restart "fixed" it only by coincidence (SDL3 sometimes re-issues the earlier handle on a fresh
    // enumeration). Fixed properly by tracking the last handle seen for each controller NAME (the one
    // stable identifier across reconnects — the same one ActiveControllerService already trusts for
    // restoring the "Default" controller) and copying that handle's saved files forward the moment a
    // newly-connected handle with no config of its own shows up under a known name.

    private static final Path NAME_INDEX_PATH = CONTROLLERS_DIR.resolve("name_index.json");
    private static NameIndexFile nameIndex;

    private static final class NameIndexFile {
        Map<String, Long> lastHandleByName = new HashMap<>();
    }

    private static NameIndexFile nameIndex() {
        if (nameIndex == null) {
            nameIndex = JsonUtil.loadFromFile(NAME_INDEX_PATH, NameIndexFile.class, new NameIndexFile());
            if (nameIndex.lastHandleByName == null) nameIndex.lastHandleByName = new HashMap<>();
        }
        return nameIndex;
    }

    /** Call once per tick for every currently-detected controller (cheap no-op once a name's handle
     *  is already up to date). Copies the previous handle's saved config files forward under the new
     *  handle the first time a reconnect is detected for that name — never overwrites an existing
     *  file at the destination, so it can never clobber a config the new handle already has. */
    public static void migrateControllerConfigByName(long handle, String name) {
        if (name == null || name.isEmpty()) return;
        NameIndexFile idx = nameIndex();
        Long oldHandle = idx.lastHandleByName.get(name);
        if (oldHandle != null && oldHandle != handle) {
            boolean migratedAny = copyIfExists(controllerPath(oldHandle), controllerPath(handle));
            migratedAny |= copyIfExists(bindingPath(oldHandle), bindingPath(handle));
            migratedAny |= copyIfExists(radialPath(oldHandle), radialPath(handle));
            if (migratedAny) {
                LogUtil.info("[SteamPad] Reconnect detected for '{}' — migrated saved config from "
                        + "handle={} to handle={}.", name, oldHandle, handle);
            }
        }
        if (oldHandle == null || oldHandle != handle) {
            idx.lastHandleByName.put(name, handle);
            JsonUtil.saveToFile(NAME_INDEX_PATH, idx);
        }
    }

    // ---- Named config profiles -----------------------------------------------------------------
    //
    // A "profile" bundles the SAME three per-controller files (controller/bindings/radial) under a
    // player-chosen name instead of a handle, so a player can save e.g. "Exploración" (low sensitivity,
    // no aim assist) and "Combate" (aim assist strong, tighter camera) and switch between them with one
    // button instead of re-tuning every slider by hand each time.

    private static final Path PROFILES_DIR = CONFIG_ROOT.resolve("profiles");

    /** Profile names currently saved, alphabetical. Empty list if none yet or the directory is unreadable. */
    public static List<String> listProfiles() {
        if (!Files.isDirectory(PROFILES_DIR)) return List.of();
        try (var stream = Files.list(PROFILES_DIR)) {
            return stream.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException e) {
            LogUtil.warn("[SteamPad] Could not list profiles: {}", e.getMessage());
            return List.of();
        }
    }

    /** Saves {@code handle}'s CURRENT controller/bindings/radial config as a named profile —
     *  overwrites an existing profile of the same name. Flushes the in-memory config to disk first so
     *  the profile always reflects what's actually active, not a stale on-disk copy. */
    public static boolean saveProfile(String name, long handle) {
        String safe = sanitizeProfileName(name);
        if (safe.isEmpty()) return false;
        saveControllerConfig(handle);
        saveBindingConfig(handle);
        saveRadialConfig(handle);
        Path dir = PROFILES_DIR.resolve(safe);
        try {
            Files.createDirectories(dir);
            Files.copy(controllerPath(handle), dir.resolve("controller.json"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(bindingPath(handle), dir.resolve("bindings.json"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(radialPath(handle), dir.resolve("radial.json"), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            LogUtil.warn("[SteamPad] Could not save profile '{}': {}", safe, e.getMessage());
            return false;
        }
    }

    /** Loads a named profile onto {@code handle} — OVERWRITES its current controller/bindings/radial
     *  config on disk and drops the in-memory copies so the very next read picks up the profile's
     *  values. Returns false (no-op) if the profile doesn't exist. */
    public static boolean loadProfile(String name, long handle) {
        String safe = sanitizeProfileName(name);
        Path dir = PROFILES_DIR.resolve(safe);
        if (!Files.isDirectory(dir)) return false;
        try {
            Files.copy(dir.resolve("controller.json"), controllerPath(handle), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(dir.resolve("bindings.json"), bindingPath(handle), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(dir.resolve("radial.json"), radialPath(handle), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LogUtil.warn("[SteamPad] Could not load profile '{}': {}", safe, e.getMessage());
            return false;
        }
        controllerConfigs.remove(handle);
        bindingConfigs.remove(handle);
        radialConfigs.remove(handle);
        return true;
    }

    /** Deletes a saved profile's directory entirely. Never touches any controller's ACTIVE config —
     *  only the saved snapshot under {@code profiles/}. */
    public static boolean deleteProfile(String name) {
        String safe = sanitizeProfileName(name);
        Path dir = PROFILES_DIR.resolve(safe);
        if (!Files.isDirectory(dir)) return false;
        try {
            for (String f : new String[]{"controller.json", "bindings.json", "radial.json"}) {
                Files.deleteIfExists(dir.resolve(f));
            }
            Files.deleteIfExists(dir);
            return true;
        } catch (IOException e) {
            LogUtil.warn("[SteamPad] Could not delete profile '{}': {}", safe, e.getMessage());
            return false;
        }
    }

    /** Strips path-unsafe characters — profile names become directory names directly. */
    private static String sanitizeProfileName(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("[^a-zA-Z0-9 _\\-]", "_");
    }

    private static boolean copyIfExists(Path from, Path to) {
        try {
            if (Files.exists(from) && !Files.exists(to)) {
                Files.createDirectories(to.getParent());
                Files.copy(from, to);
                return true;
            }
        } catch (IOException e) {
            LogUtil.warn("[SteamPad] Could not migrate config file {} -> {}: {}", from, to, e.getMessage());
        }
        return false;
    }
}
