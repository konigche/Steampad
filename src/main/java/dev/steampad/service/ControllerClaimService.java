package dev.steampad.service;

import dev.steampad.steam.SteamControllerHandleRef;
import dev.steampad.util.LogUtil;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

    /**
     * How often the heartbeat actually touches the disk while nothing has changed.
     *
     * <p><b>This is a performance fix, and it was the mod's largest single cost.</b> {@link #ensureClaim}
     * is called every client tick (20 Hz) and used to run the full {@link #tryClaim} dance each time:
     * a {@code CREATE_NEW} write that <em>throws</em> {@code FileAlreadyExistsException} on every tick
     * after the first, then {@code exists} + {@code readString} + an overwriting {@code writeString} —
     * four filesystem syscalls and one constructed exception, 20 times a second, synchronously on the
     * render thread, in a Windows temp directory an antivirus scans. It is the only per-tick disk I/O in
     * the whole mod, and the user's own profile line showed SteamPad's client tick costing ~26× the sum
     * of everything nested inside it that WAS instrumented.
     *
     * <p>1 s keeps a 6× margin under {@link #TTL_MS}, so a claim can still never expire under a live
     * instance — the protocol's own safety property is untouched, only the write rate changes. Losing a
     * race to another instance is now noticed within a second instead of within a tick, which is still
     * six times faster than the TTL the whole design already depends on.
     *
     * <p>Only the unchanged case is throttled: acquiring a claim, switching pads and releasing all still
     * hit the disk immediately, so nothing about who-gets-which-controller is decided any later.
     */
    private static final long HEARTBEAT_MS = 1_000L;

    /** When the heartbeat last actually wrote, and what it concluded — see {@link #HEARTBEAT_MS}. */
    private static volatile long lastHeartbeatMs = 0L;
    private static volatile boolean lastHeartbeatOwned = true;

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
     * disabled or on any error (fail-open).
     *
     * <p>This deliberately re-reads the claim file even for the handle this instance believes it owns.
     * It used to short-circuit on {@code handle == claimedHandle}, which made the check unable to ever
     * report the one case it exists for: two instances that pick the same free pad on the same tick
     * both pass the check, both write the file, and from then on <em>each</em> one answers "not claimed
     * by anyone else" forever because the handle matches its own local state — so both keep reading the
     * same physical pad. That race is why the symptom was intermittent ("a veces funciona, a veces no")
     * rather than deterministic. Ownership now always comes from the file, which is the only thing both
     * processes can actually see.
     */
    public static boolean isClaimedByOther(long handle, List<SteamControllerHandleRef> connected) {
        if (!enabled || handle == 0L) return false;
        String key = keyFor(handle, connected);
        if (key == null) return false;
        String holder = liveHolderOf(key);
        return holder != null && !holder.equals(myToken);
    }

    /**
     * Take (or keep) the claim for {@code handle}, refreshing the heartbeat.
     *
     * @return true when this instance owns the pad afterwards; false when another live instance holds
     *         it. Returns true when claiming is disabled or errored — fail-open, exactly as before, so
     *         single-instance play is never gated on the claim directory being writable.
     */
    public static boolean tryClaim(long handle, List<SteamControllerHandleRef> connected) {
        if (!enabled || handle == 0L) return true;
        String key = keyFor(handle, connected);
        if (key == null) return true;
        try {
            Path f = claimsDir.resolve(key + ".claim");
            // Atomic acquire: CREATE_NEW fails if the file exists, so two instances racing for a free
            // pad cannot both succeed — exactly one creates it and the other takes the branch below.
            try {
                Files.writeString(f, stampedClaim(key), StandardOpenOption.CREATE_NEW);
                return own(handle, key);
            } catch (FileAlreadyExistsException alreadyClaimed) {
                String holder = liveHolderOf(key);
                if (holder != null && !holder.equals(myToken)) return false;   // held by a live instance
                // Ours already, or a dead instance's claim that has aged out — take it and heartbeat.
                Files.writeString(f, stampedClaim(key));
                return own(handle, key);
            }
        } catch (Throwable t) {
            return true;   // fail-open
        }
    }

    /**
     * Ensure this instance holds the claim for {@code handle}, releasing any previous one, and refresh
     * the heartbeat. Call every tick with the current active handle.
     *
     * @return whether this instance still owns the pad (see {@link #tryClaim}).
     */
    public static boolean ensureClaim(long handle, List<SteamControllerHandleRef> connected) {
        if (!enabled || handle == 0L) return true;
        if (handle == claimedHandle) {
            // Same pad, nothing changed: heartbeat at HEARTBEAT_MS instead of every tick. The cached
            // verdict is returned in between so the caller sees a stable answer rather than one that
            // depends on which tick it happened to ask on.
            long now = System.currentTimeMillis();
            if (now - lastHeartbeatMs < HEARTBEAT_MS) return lastHeartbeatOwned;
            lastHeartbeatMs = now;
            lastHeartbeatOwned = tryClaim(handle, connected);
            return lastHeartbeatOwned;
        }

        // Switching pads: acquire the NEW one before dropping the old one. Releasing first would open a
        // window in which a refused switch (the target is busy) has already given away the controller
        // the player is actually using, letting another instance take it.
        String previousKey = claimedKey;
        if (!tryClaim(handle, connected)) return false;   // stay exactly where we were
        if (previousKey != null && !previousKey.equals(claimedKey)) releaseKey(previousKey);
        return true;
    }

    private static boolean own(long handle, String key) {
        claimedHandle = handle;
        claimedKey = key;
        // The write that just happened IS this period's heartbeat — whichever path performed it
        // (acquire, pad switch, or the throttled refresh itself), so an immediate second write on the
        // next tick is never needed. See HEARTBEAT_MS.
        lastHeartbeatMs = System.currentTimeMillis();
        lastHeartbeatOwned = true;
        return true;
    }

    private static String stampedClaim(String key) {
        return myToken + "\n" + System.currentTimeMillis() + "\n" + key;
    }

    /**
     * The token of the instance currently holding {@code key}, or null when the claim is absent,
     * malformed, or stale (its owner died without releasing — the heartbeat is how that is detected).
     */
    private static String liveHolderOf(String key) {
        try {
            Path f = claimsDir.resolve(key + ".claim");
            if (!Files.exists(f)) return null;
            return holderOf(Files.readString(f), System.currentTimeMillis(), TTL_MS);
        } catch (Throwable t) {
            return null;   // fail-open
        }
    }

    /**
     * The claim-file rule itself, as pure logic so it can be tested without a Minecraft runtime:
     * a claim counts only while its heartbeat is younger than {@code ttlMs}.
     *
     * <p>Package-private and separated out deliberately — this predicate is where the cross-instance
     * bug lived, and the property that matters ("a fresh claim written by someone else means the pad is
     * theirs, whatever this process believes it owns") is invisible in an integration test and obvious
     * in a unit test. Returns null for absent/malformed/expired, i.e. "nobody holds this".
     */
    static String holderOf(String fileContent, long nowMs, long ttlMs) {
        if (fileContent == null) return null;
        String[] lines = fileContent.split("\n");
        if (lines.length < 2) return null;
        try {
            long stamp = Long.parseLong(lines[1].trim());
            if ((nowMs - stamp) >= ttlMs) return null;   // expired: owner died without releasing
            String token = lines[0].trim();
            return token.isEmpty() ? null : token;
        } catch (NumberFormatException malformed) {
            return null;
        }
    }

    /** The exact predicate {@link #isClaimedByOther} applies, as pure logic. */
    static boolean heldByOther(String fileContent, String myToken, long nowMs, long ttlMs) {
        String holder = holderOf(fileContent, nowMs, ttlMs);
        return holder != null && !holder.equals(myToken);
    }

    /** TTL used by the live service, exposed so the test asserts against the real value. */
    static long ttlMs() { return TTL_MS; }

    /** Drop this instance's claim (on disconnect / shutdown / no controller active). */
    public static void release() {
        // Nothing to give back and nothing to reset — return before touching the disk. release() is
        // called every tick while no controller is active (see SteamPadClient's tick body), and
        // releaseKey() below reads the claim file before deleting it, so without this the "no pad
        // connected" case paid the same per-tick I/O the heartbeat throttle just removed.
        if (claimedHandle == 0L && claimedKey == null) return;
        lastHeartbeatMs = 0L;   // a future claim must write immediately, not wait out the throttle
        lastHeartbeatOwned = true;
        if (!enabled) { claimedHandle = 0L; claimedKey = null; return; }
        try {
            releaseKey(claimedKey);
        } finally {
            claimedHandle = 0L;
            claimedKey = null;
        }
    }

    /**
     * Deletes one claim file, but only while it still carries OUR token: after losing a race the file
     * belongs to the other instance, and deleting it there would hand the pad straight back and start
     * the two instances trading it back and forth.
     */
    private static void releaseKey(String key) {
        if (!enabled || key == null) return;
        try {
            String holder = liveHolderOf(key);
            if (holder == null || holder.equals(myToken)) {
                Files.deleteIfExists(claimsDir.resolve(key + ".claim"));
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Cross-process key for a controller: its persistent identity
     * ({@link ControllerIdentityService}), which is the same string in every instance on this machine
     * and the same string after a reconnect.
     *
     * <p>It used to be "sanitized display name + ordinal among identically-named devices", and both
     * halves were unstable in exactly the situation this service exists for. The NAME differs between
     * backends for one physical pad (SDL3 and GLFW report unrelated strings for the same 8BitDo), so
     * two instances that happened to enumerate through different backends built different keys for
     * one pad and each believed it was free. The ORDINAL was computed from position in the connected
     * list, which is enumeration order — it changes when a pad is unplugged and replugged, so after a
     * reconnect an instance could silently start claiming the OTHER pad, or two instances could land
     * on the same key. That is the "en pantalla dividida... se confunden los controles" report.
     *
     * <p>Returns null if the handle isn't in the connected list or has no resolved identity yet, and
     * every caller treats null as "not claimed" — fail-open, exactly as before.
     */
    private static String keyFor(long handle, List<SteamControllerHandleRef> connected) {
        if (connected == null) return null;
        boolean present = false;
        for (SteamControllerHandleRef r : connected) {
            if (r.handle == handle) { present = true; break; }
        }
        if (!present) return null;
        String id = ControllerIdentityService.keyFor(handle);
        return id.isEmpty() ? null : id;
    }
}
