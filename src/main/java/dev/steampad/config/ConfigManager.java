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
    // Keyed by the PERSISTENT identity key, not by the handle — see storageKey(). A reconnect hands
    // the same physical pad a brand-new handle but the same key, so its already-loaded config is
    // simply still there: no copying, no cache eviction, no window where a default could be written.
    private static final Map<String, ControllerConfig> controllerConfigs = new HashMap<>();
    private static final Map<String, BindingConfig> bindingConfigs = new HashMap<>();
    private static final Map<String, RadialConfig> radialConfigs = new HashMap<>();

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
        String key = storageKey(handle);
        return controllerConfigs.computeIfAbsent(key, k ->
                JsonUtil.loadFromFile(controllerPath(k), ControllerConfig.class, ControllerConfig.defaults()));
    }

    public static void saveControllerConfig(long handle) {
        // getControllerConfig(), never getOrDefault(key, defaults()): see the note on storageKey().
        JsonUtil.saveToFile(controllerPath(storageKey(handle)), getControllerConfig(handle));
    }

    public static BindingConfig getBindingConfig(long handle) {
        String key = storageKey(handle);
        return bindingConfigs.computeIfAbsent(key, k ->
                JsonUtil.loadFromFile(bindingPath(k), BindingConfig.class, BindingConfig.defaults()));
    }

    public static void saveBindingConfig(long handle) {
        JsonUtil.saveToFile(bindingPath(storageKey(handle)), getBindingConfig(handle));
    }

    public static RadialConfig getRadialConfig(long handle) {
        String key = storageKey(handle);
        return radialConfigs.computeIfAbsent(key, k ->
                JsonUtil.loadFromFile(radialPath(k), RadialConfig.class, RadialConfig.defaults()));
    }

    public static void saveRadialConfig(long handle) {
        JsonUtil.saveToFile(radialPath(storageKey(handle)), getRadialConfig(handle));
    }

    /** Persists all loaded configs to disk. */
    public static void saveAll() {
        saveGlobal();
        controllerConfigs.forEach((k, v) -> JsonUtil.saveToFile(controllerPath(k), v));
        bindingConfigs.forEach((k, v) -> JsonUtil.saveToFile(bindingPath(k), v));
        radialConfigs.forEach((k, v) -> JsonUtil.saveToFile(radialPath(k), v));
    }

    // ---- Storage key ----------------------------------------------------------------------------
    //
    // Every per-controller file used to be named after the numeric HANDLE. That is the single root
    // cause behind "cuando se conectan recuerdan otras configuraciones antiguas" and "cambiaba mis
    // botones": SDL mints a new SDL_JoystickID per connection and restarts the counter each process,
    // and GLFW hands out the first free slot — so the same pad is a different number every time, and
    // the number it lands on may be the one the OTHER pad's file was written under. It also explains
    // the user's own discriminating observation that launching with the pads ALREADY connected worked
    // fine: enumerating at startup reproduces last session's id order, so the handles line up by
    // coincidence. Files are now named after the persistent identity key (VID/PID, plus the serial
    // when SDL can read one) — see dev.steampad.service.ControllerIdentity.

    /** Identity key for a handle, or a handle-derived fallback while identity is unknown. */
    private static String storageKey(long handle) {
        String key = dev.steampad.service.ControllerIdentityService.keyFor(handle);
        if (key.isEmpty()) {
            // Not (yet) a connected controller — e.g. a config read during startup before the first
            // poll, or a Steam Input handle. Keep the historical handle-named file so nothing that
            // used to work stops working; the identity path takes over as soon as a poll resolves it.
            return "h" + handle;
        }
        adoptLegacyFiles(key, handle);
        promoteHandleKeyedEntries("h" + handle, key);
        return key;
    }

    /**
     * Moves any cache entry parked under the pre-identity {@code h<handle>} key onto the identity key.
     *
     * <p>This closes a silent config-loss window that only opened when a pad's identity resolved
     * BETWEEN a read and its matching write — which is the normal state in Steam Game Mode, where Steam
     * claims and releases pads continuously so {@code keyFor()} flips between "" and the real key many
     * times per session (on a plain desktop launch identity resolves once, before the UI, so both sides
     * always agreed and the bug stayed invisible). The getters cache under the key that was current at
     * READ time, and the setters used to look the object up under the key current at WRITE time and,
     * on a miss, write {@code defaults()} — so a user's freshly edited bindings were not merely "not
     * saved", the real {@code bindings_<identity>.json} was overwritten with defaults. That is the
     * reported "en modo Game Mode no guarda la configuración de botones".
     *
     * <p>{@code putIfAbsent}, never {@code put}: when the identity key already holds an object it was
     * loaded from the pad's real file, whereas the handle-keyed one can be a defaults instance created
     * by an early caller (e.g. the "Allow Vibration" lookup in {@code ControllerManager.rumble}). The
     * real config always wins.
     */
    private static void promoteHandleKeyedEntries(String from, String to) {
        if (from.equals(to)) return;
        promote(controllerConfigs, from, to);
        promote(bindingConfigs, from, to);
        promote(radialConfigs, from, to);
    }

    private static <T> void promote(Map<String, T> cache, String from, String to) {
        T pending = cache.remove(from);
        if (pending != null) cache.putIfAbsent(to, pending);
    }

    private static Path controllerPath(String key) { return CONTROLLERS_DIR.resolve("controller_" + key + ".json"); }
    private static Path bindingPath(String key)    { return CONTROLLERS_DIR.resolve("bindings_" + key + ".json"); }
    private static Path radialPath(String key)     { return CONTROLLERS_DIR.resolve("radial_" + key + ".json"); }

    private static Path legacyControllerPath(long h) { return CONTROLLERS_DIR.resolve("controller_" + h + ".json"); }
    private static Path legacyBindingPath(long h)    { return CONTROLLERS_DIR.resolve("bindings_" + h + ".json"); }
    private static Path legacyRadialPath(long h)     { return CONTROLLERS_DIR.resolve("radial_" + h + ".json"); }

    /** Identity keys whose legacy adoption has already been attempted this session. */
    private static final java.util.Set<String> legacyAdopted = new java.util.HashSet<>();

    /**
     * One-time upgrade path: copies a controller's pre-identity, handle-named files onto its new
     * identity-named files the first time that identity is used.
     *
     * <p>Two places are searched for the old files, best first: the handle recorded in
     * {@code name_index.json} for this controller's display name (what the old reconnect migration
     * tracked, so it points at the most recently written set), then the handle the pad happens to
     * carry right now. Never overwrites a file that already exists at the destination, so this can
     * only ever ADD the user's old settings, never clobber newer ones.
     */
    private static void adoptLegacyFiles(String key, long handle) {
        if (!legacyAdopted.add(key)) return;
        if (Files.exists(controllerPath(key))) return;   // already migrated in an earlier session

        String name = dev.steampad.service.ControllerIdentityService.nameFor(key);
        long fromName = name.isEmpty() ? 0L : nameIndex().lastHandleByName.getOrDefault(name, 0L);
        long recorded = dev.steampad.service.ControllerIdentityService.legacyHandle(key);

        for (long old : new long[]{recorded, fromName, handle}) {
            if (old == 0L) continue;
            boolean any = copyIfExists(legacyControllerPath(old), controllerPath(key));
            any |= copyIfExists(legacyBindingPath(old), bindingPath(key));
            any |= copyIfExists(legacyRadialPath(old), radialPath(key));
            if (any) {
                dev.steampad.service.ControllerIdentityService.rememberLegacyHandle(key, old);
                LogUtil.info("[SteamPad] Adopted pre-identity config for '{}' (handle={}) into identity "
                        + "'{}' — bindings/radial/settings preserved across the upgrade.", name, old, key);
                return;
            }
        }
    }

    // ---- Legacy name index (READ ONLY) ---------------------------------------------------------
    //
    // Written by the pre-identity reconnect migration, which tracked "last handle seen for each
    // controller NAME" and copied files forward on every reconnect. That approach is gone: a name is
    // not an identifier (two pads of one model share it; one pad reports different names on different
    // backends/modes), so the migration could copy one pad's bindings on top of the other's, and with
    // two identically-named pads it ping-ponged the index — and a disk write — on EVERY tick. The file
    // is still READ, once, purely to find where a pre-upgrade user's real settings live so
    // adoptLegacyFiles can bring them across. Nothing writes it any more.

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
        String key = storageKey(handle);
        Path dir = PROFILES_DIR.resolve(safe);
        try {
            Files.createDirectories(dir);
            Files.copy(controllerPath(key), dir.resolve("controller.json"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(bindingPath(key), dir.resolve("bindings.json"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(radialPath(key), dir.resolve("radial.json"), StandardCopyOption.REPLACE_EXISTING);
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
        String key = storageKey(handle);
        try {
            Files.copy(dir.resolve("controller.json"), controllerPath(key), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(dir.resolve("bindings.json"), bindingPath(key), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(dir.resolve("radial.json"), radialPath(key), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LogUtil.warn("[SteamPad] Could not load profile '{}': {}", safe, e.getMessage());
            return false;
        }
        controllerConfigs.remove(key);
        bindingConfigs.remove(key);
        radialConfigs.remove(key);
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
