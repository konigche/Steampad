package dev.steampad.service;

import dev.steampad.steam.SteamControllerHandleRef;
import dev.steampad.util.JsonUtil;
import dev.steampad.util.LogUtil;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persistent registry mapping every physical controller ever seen to a stable key, plus the
 * volatile-handle → key lookup everything else uses.
 *
 * <p>{@link ControllerIdentity} decides <em>which</em> key a device gets; this class remembers the
 * decisions across sessions ({@code config/steampad/controllers/identity.json}) and re-resolves the
 * currently-connected devices whenever the connected set changes. Resolution is deliberately done for
 * the WHOLE connected list at once, never per device on demand: two identical pads can only be told
 * apart relative to each other, so a per-device call would have no way to avoid handing both the same
 * key.
 *
 * <p>Fail-open like the rest of the controller stack: any I/O problem degrades to an in-memory-only
 * registry, so a read-only config directory costs persistence across restarts and nothing else.
 */
public final class ControllerIdentityService {

    private static final Path CONTROLLERS_DIR =
            FabricLoader.getInstance().getConfigDir().resolve("steampad").resolve("controllers");
    private static final Path IDENTITY_PATH = CONTROLLERS_DIR.resolve("identity.json");

    private ControllerIdentityService() {}

    /** On-disk shape. One entry per physical controller we have ever resolved. */
    private static final class RegistryFile {
        Map<String, Entry> devices = new LinkedHashMap<>();
    }

    /** Serialized signals + the last name we saw, purely so the file is readable by a human. */
    private static final class Entry {
        int vendorId;
        int productId;
        int productVersion;
        String serial = "";
        String name = "";
        /** Handles this key was known by BEFORE identity keys existed — see the legacy migration. */
        long legacyHandle;
    }

    private static RegistryFile registry;
    /** Live handle → stable key for whatever is connected right now. */
    private static final Map<Long, String> byHandle = new HashMap<>();
    /** Stable key → the handle currently carrying it (0 when that device isn't connected). */
    private static final Map<String, Long> handleByKey = new HashMap<>();
    /** Signature of the last connected set we resolved, so a steady session does no work. */
    private static String lastResolvedSignature = "";

    private static RegistryFile registry() {
        if (registry == null) {
            registry = JsonUtil.loadFromFile(IDENTITY_PATH, RegistryFile.class, new RegistryFile());
            if (registry.devices == null) registry.devices = new LinkedHashMap<>();
        }
        return registry;
    }

    private static ControllerIdentity.Signals signalsOf(Entry e) {
        return new ControllerIdentity.Signals(e.vendorId, e.productId, e.productVersion, e.serial, e.name);
    }

    private static ControllerIdentity.Signals signalsOf(SteamControllerHandleRef r) {
        return new ControllerIdentity.Signals(r.vendorId, r.productId, r.productVersion, r.serial, r.displayName);
    }

    /**
     * Re-resolves the stable key of every connected controller. Cheap no-op while the connected set
     * is unchanged; called from {@link ControllerManager}'s poll so no caller has to remember to.
     */
    public static synchronized void refresh(List<SteamControllerHandleRef> connected) {
        if (connected == null) return;
        String signature = signatureOf(connected);
        if (signature.equals(lastResolvedSignature)) return;
        lastResolvedSignature = signature;

        RegistryFile reg = registry();
        Map<String, ControllerIdentity.Signals> known = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> e : reg.devices.entrySet()) known.put(e.getKey(), signalsOf(e.getValue()));

        // Deterministic order: two identical pads must resolve the same way for a given connected set,
        // whatever order the backend happened to enumerate them in this time.
        List<SteamControllerHandleRef> ordered = new ArrayList<>(connected);
        ordered.sort((a, b) -> Long.compare(a.handle, b.handle));

        Set<String> taken = new HashSet<>();
        Map<Long, String> resolved = new HashMap<>();
        boolean dirty = false;
        for (SteamControllerHandleRef r : ordered) {
            ControllerIdentity.Signals s = signalsOf(r);
            String key = ControllerIdentity.resolve(s, known, taken);
            taken.add(key);
            resolved.put(r.handle, key);

            Entry entry = reg.devices.get(key);
            if (entry == null) {
                entry = new Entry();
                reg.devices.put(key, entry);
                dirty = true;
                LogUtil.info("[SteamPad] New controller identity '{}' for '{}' (vid=0x{} pid=0x{}{}).",
                        key, r.displayName, Integer.toHexString(r.vendorId), Integer.toHexString(r.productId),
                        s.hasSerial() ? ", serial known" : "");
            } else if (byHandle.get(r.handle) == null || !key.equals(byHandle.get(r.handle))) {
                LogUtil.info("[SteamPad] Controller '{}' resolved to identity '{}' (handle={}).",
                        r.displayName, key, r.handle);
            }
            // Upgrade the stored signals: learning the serial (or a corrected name) makes the NEXT
            // resolution exact instead of "same model, take the free one".
            if (entry.vendorId != r.vendorId || entry.productId != r.productId
                    || entry.productVersion != r.productVersion) {
                entry.vendorId = r.vendorId;
                entry.productId = r.productId;
                entry.productVersion = r.productVersion;
                dirty = true;
            }
            if (s.hasSerial() && !s.serial().equals(entry.serial)) { entry.serial = s.serial(); dirty = true; }
            if (!r.displayName.isEmpty() && !r.displayName.equals(entry.name)) {
                entry.name = r.displayName;
                dirty = true;
            }
            known.put(key, signalsOf(entry));
        }

        byHandle.clear();
        byHandle.putAll(resolved);
        handleByKey.clear();
        for (Map.Entry<Long, String> e : resolved.entrySet()) handleByKey.put(e.getValue(), e.getKey());

        if (dirty) save();
    }

    /** Cheap fingerprint of "which devices are connected", so refresh() only works when it changed. */
    private static String signatureOf(List<SteamControllerHandleRef> connected) {
        long sum = 0;
        for (SteamControllerHandleRef r : connected) sum = sum * 31 + r.handle;
        return connected.size() + ":" + sum;
    }

    private static void save() {
        JsonUtil.saveToFile(IDENTITY_PATH, registry());
    }

    /**
     * The stable key for a live handle, or {@code ""} when the handle isn't one of the currently
     * connected controllers (callers fall back to handle-named storage, exactly as before).
     */
    public static String keyFor(long handle) {
        if (handle == 0L) return "";
        String k = byHandle.get(handle);
        return k == null ? "" : k;
    }

    /** The live handle currently carrying a stable key, or 0 when that controller isn't connected. */
    public static long handleFor(String key) {
        if (key == null || key.isEmpty()) return 0L;
        Long h = handleByKey.get(key);
        return h == null ? 0L : h;
    }

    /** Last display name recorded for a key — for UI/log lines about a controller that is unplugged. */
    public static String nameFor(String key) {
        if (key == null || key.isEmpty()) return "";
        Entry e = registry().devices.get(key);
        return e == null || e.name == null ? "" : e.name;
    }

    /** Every key ever registered, for diagnostics. */
    public static Map<String, String> allKnown() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> e : registry().devices.entrySet()) {
            out.put(e.getKey(), e.getValue().name == null ? "" : e.getValue().name);
        }
        return out;
    }

    // ---- Legacy handle-keyed storage --------------------------------------------------------
    //
    // Everything a user configured before identity keys existed lives in files named after the
    // volatile handle. The first time a controller resolves to a stable key we copy those files
    // forward (see ConfigManager.adoptLegacyFiles) — the handle to look under is what this remembers.

    /** Records the handle a key's legacy files were last written under. */
    public static synchronized void rememberLegacyHandle(String key, long handle) {
        if (key == null || key.isEmpty() || handle == 0L) return;
        Entry e = registry().devices.get(key);
        if (e == null || e.legacyHandle == handle) return;
        e.legacyHandle = handle;
        save();
    }

    /** The handle a key's legacy files were written under, or 0 if none was recorded. */
    public static long legacyHandle(String key) {
        if (key == null || key.isEmpty()) return 0L;
        Entry e = registry().devices.get(key);
        return e == null ? 0L : e.legacyHandle;
    }

    /** Test/diagnostic reset — drops the in-memory view without touching the file. */
    static synchronized void resetForTests() {
        registry = null;
        byHandle.clear();
        handleByKey.clear();
        lastResolvedSignature = "";
    }
}
