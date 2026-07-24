package dev.steampad.service;

import dev.steampad.steam.SteamControllerHandleRef;
import dev.steampad.util.LogUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Cross-instance controller claiming so several Minecraft instances on the same machine each grab a
 * <em>distinct</em> physical controller and never react to another instance's pad — the core of
 * "jugar varias instancias sin que se perjudiquen".
 *
 * <p>Why this is needed: each instance enumerates the same physical controllers and, with no explicit
 * choice, auto-activates the first one — i.e. the <em>same</em> pad for every instance. Then any
 * button on that pad (e.g. the menu/start button) fires in all instances at once. There is no vanilla
 * window-focus pause path in 1.21.10, so this logical claim — not focus — is what isolates instances.
 *
 * <p>Coordination is a tiny lock file per controller in a shared directory ({@code <tmp>/steampad-claims}),
 * holding this instance's random token + a heartbeat timestamp. A claim is "live" only while its
 * timestamp is fresh (refreshed every tick), so a crashed instance's claim expires on its own and the
 * controller frees up. Identity is a random per-process token (not the PID), which stays correct even
 * under Flatpak PID namespaces.
 *
 * <p><b>Fail-open by design:</b> if the directory is not writable or anything throws, every method
 * degrades to "nothing is claimed" and the caller behaves exactly as before — single-instance play is
 * never affected.
 */
public final class ControllerClaimService {

    /** A claim is considered live while its heartbeat is newer than this. */
    private static final long TTL_MS = 6_000L;

    private static volatile boolean enabled = false;
    private static Path claimsDir;
    private static String myToken;

    /** The controller this instance currently holds a claim for (0 = none). */
    private static volatile long claimedHandle = 0L;
    private static volatile String claimedKey = null;

    private ControllerClaimService() {}

    /** Idempotent. Sets up the shared dir + this instance's token; disables itself (fail-open) on error. */
    public static synchronized void init() {
        if (enabled || myToken != null) return;
        try {
            myToken = UUID.randomUUID().toString();
            claimsDir = Path.of(System.getProperty("java.io.tmpdir", "."), "steampad-claims");
            Files.createDirectories(claimsDir);
            enabled = true;
            LogUtil.info("[SteamPad] Controller claim coordination active: {}", claimsDir);
        } catch (Throwable t) {
            enabled = false;
            LogUtil.warn("[SteamPad] Controller claim coordination disabled (fail-open): {}", t.getMessage());
        }
    }

    /**
     * True if another <em>live</em> instance currently holds this controller. Always false when
     * disabled, when the handle is ours, or on any error (fail-open).
     */
    public static boolean isClaimedByOther(long handle, List<SteamControllerHandleRef> connected) {
        if (!enabled || handle == 0L || handle == claimedHandle) return false;
        String key = keyFor(handle, connected);
        if (key == null) return false;
        try {
            Path f = claimsDir.resolve(key + ".claim");
            if (!Files.exists(f)) return false;
            String[] lines = Files.readString(f).split("\n");
            if (lines.length < 2) return false;
            String token = lines[0].trim();
            long stamp = Long.parseLong(lines[1].trim());
            if (token.equals(myToken)) return false;                 // somehow ours
            return (System.currentTimeMillis() - stamp) < TTL_MS;     // fresh = held by a live instance
        } catch (Throwable t) {
            return false;   // fail-open
        }
    }

    /**
     * Ensure this instance holds the claim for {@code handle}, releasing any previous one, and refresh
     * the heartbeat. Call every tick with the current active handle. No-op when disabled.
     */
    public static void ensureClaim(long handle, List<SteamControllerHandleRef> connected) {
        if (!enabled || handle == 0L) return;
        try {
            if (handle != claimedHandle) {
                release();
                claimedKey = keyFor(handle, connected);
                claimedHandle = handle;
            }
            if (claimedKey != null) {
                Path f = claimsDir.resolve(claimedKey + ".claim");
                Files.writeString(f, myToken + "\n" + System.currentTimeMillis() + "\n" + claimedKey);
            }
        } catch (Throwable t) {
            // fail-open: drop the claim silently, isolation just won't be enforced this tick
        }
    }

    /** Drop this instance's claim file (on disconnect / shutdown / switching controllers). */
    public static void release() {
        if (!enabled) { claimedHandle = 0L; claimedKey = null; return; }
        try {
            if (claimedKey != null) Files.deleteIfExists(claimsDir.resolve(claimedKey + ".claim"));
        } catch (Throwable ignored) {
        } finally {
            claimedHandle = 0L;
            claimedKey = null;
        }
    }

    /**
     * Stable cross-process key for a controller: sanitized display name plus an ordinal among
     * identically-named devices (so two identical pads still get distinct keys). Returns null if the
     * handle isn't in the connected list.
     */
    private static String keyFor(long handle, List<SteamControllerHandleRef> connected) {
        if (connected == null) return null;
        SteamControllerHandleRef ref = null;
        int ordinal = 0;
        for (SteamControllerHandleRef r : connected) {
            if (r.handle == handle) { ref = r; break; }
        }
        if (ref == null) return null;
        for (SteamControllerHandleRef r : connected) {
            if (r.handle == handle) break;
            if (sanitize(r.displayName).equals(sanitize(ref.displayName))) ordinal++;
        }
        return sanitize(ref.displayName) + "_" + ordinal;
    }

    private static String sanitize(String s) {
        if (s == null || s.isEmpty()) return "unknown";
        return s.replaceAll("[^A-Za-z0-9]", "_");
    }
}
