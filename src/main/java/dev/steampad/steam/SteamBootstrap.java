package dev.steampad.steam;

import com.codedisaster.steamworks.SteamAPI;
import dev.steampad.platform.EnvironmentReport;
import dev.steampad.platform.NativeEnv;
import dev.steampad.platform.SteamLaunchDetector;
import dev.steampad.util.LogUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Manages the lifecycle of the Steam API: init, callbacks loop, and shutdown.
 *
 * Must be initialized on the game thread. runCallbacks() is called every client tick
 * via Fabric's ClientTickEvents to process pending Steam API events.
 */
public final class SteamBootstrap {

    /** Spacewar — Valve's public sandbox AppID, usable by any process without owning a game. */
    public static final long SANDBOX_APP_ID = 480L;

    private static boolean steamAvailable = false;
    private static boolean inputAvailable = false;
    private static boolean attachSkippedByPolicy = false;
    private static EnvironmentReport envReport;

    /** The AppID {@link SteamAPI#init()} actually succeeded with, or 0 while not attached. */
    private static long initializedAppId = 0L;
    /** True once the attach policy approved attaching AND the natives are up — gates the retry loop. */
    private static boolean attachDesired = false;
    /** VDF deploy is a per-session side effect; the retry loop must not repeat it every few seconds. */
    private static boolean deployedThisSession = false;
    /** One-shot so the "native check lied, trusting the process scan" note isn't logged on every retry. */
    private static boolean notedProcessScanOverride = false;

    /** Ordered, human-readable trail of every attach decision/attempt — surfaced in the debug dump. */
    private static final List<String> attachTrail = new ArrayList<>();
    private static final int TRAIL_MAX = 40;

    private SteamBootstrap() {}

    public static boolean isSteamAvailable() {
        return steamAvailable;
    }

    public static boolean isInputAvailable() {
        return inputAvailable;
    }

    /** True when init() deliberately did not attach to Steam (steamAttachMode policy, D033). */
    public static boolean isAttachSkippedByPolicy() {
        return attachSkippedByPolicy;
    }

    public static EnvironmentReport getEnvironmentReport() {
        return envReport;
    }

    /** The AppID the current Steam session attached with (0 = not attached). */
    public static long getInitializedAppId() {
        return initializedAppId;
    }

    /** True while the attach policy wants a connection that hasn't succeeded yet (retries pending). */
    public static boolean isAttachPending() {
        return attachDesired && !steamAvailable && attachRetryCount < ATTACH_RETRY_MAX;
    }

    /** How many automatic re-attach attempts have run so far this session. */
    public static int attachRetryCount() {
        return attachRetryCount;
    }

    /** Chronological record of the attach decisions and attempts, for the debug dump. */
    public static List<String> attachTrail() {
        return Collections.unmodifiableList(new ArrayList<>(attachTrail));
    }

    private static void note(String message) {
        if (attachTrail.size() >= TRAIL_MAX) attachTrail.remove(0);
        attachTrail.add(message);
    }

    /**
     * Initializes Steam API and Steam Input. Call once during mod client init.
     * Returns true if at least Steam API initialized successfully.
     *
     * <p>Safe to call again (the "Retry Steam Init" button and the attach-mode switch both do).
     */
    public static boolean init() {
        // Guard: if already initialized, skip re-init (makes retry from UI safe).
        if (steamAvailable) {
            LogUtil.info("[SteamPad] SteamBootstrap.init() called but already initialized — skipping.");
            return true;
        }

        envReport = EnvironmentReport.generate();

        // Attach policy (D033/B038, widened B040): attaching to Steam with our AppID makes Steam treat
        // this process as a running game and SEIZE every Steam-managed controller (applying that
        // game's layout) — raw SDL3 input goes silent for those pads. Validated in hardware: on
        // desktop the 8BitDo vanished from SDL3 and the built-in pad went mute the instant
        // SteamAPI.init() succeeded. On desktop with NO Steam-launch signal, SDL3/HIDAPI already
        // delivers everything raw (back paddles included), so attaching there is pure loss — AUTO
        // stays off in that case, same as always.
        //
        // AUTO now also attaches when SteamLaunchDetector found a real Steam launch AppID for this
        // session (SteamAppId/SteamGameId env vars) — not just gamescope. Reasoning: a genuine Steam
        // launch (the user pressed Play on MC's Steam library entry, on desktop OR in Game Mode) is a
        // MORE deliberate, MORE authoritative "the user wants Steam Input for this session" signal
        // than gamescope alone — every real Steam Input game decides the same way (it doesn't check
        // "am I in gamescope", it checks "was I started by Steam"). Gamescope stays as a second,
        // independent trigger for the case where MC is embedded in a custom Game Mode session without
        // going through Steam's own process-launch path (the sway-script setup this project has
        // tested against before), where Steam still owns every pad regardless of our attach.
        attachSkippedByPolicy = false;
        var attachMode = dev.steampad.config.ConfigManager.getGlobal().steamAttachMode;
        boolean launchedFromSteam = envReport.steamLaunchAppId > 0;
        boolean shouldAttach = switch (attachMode) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> envReport.isGamescope || launchedFromSteam;
        };
        note("policy: mode=" + attachMode + " gamescope=" + envReport.isGamescope
                + " launchedFromSteam=" + launchedFromSteam + " -> attach=" + shouldAttach);
        if (!shouldAttach) {
            attachSkippedByPolicy = true;
            attachDesired = false;
            LogUtil.info("[SteamPad] Not attaching to Steam (steamAttachMode={}, gamescope={}, "
                    + "launchedFromSteam={}): raw SDL3/GLFW input keeps full control of the pads "
                    + "(paddles P1..P4 included on desktop). Steam Input slots are inactive in this "
                    + "session.", attachMode, envReport.isGamescope, launchedFromSteam);
            envReport = envReport.withSteamAvailable(false);
            return false;
        }
        LogUtil.info("[SteamPad] Attaching to Steam (steamAttachMode={}, gamescope={}, "
                + "launchedFromSteam={}, detectedAppId={}).",
                attachMode, envReport.isGamescope, launchedFromSteam, envReport.steamLaunchAppId);

        if (!SteamNativeLoader.isLoaded()) {
            LogUtil.warn("Steam natives not loaded — Steam API unavailable.");
            note("aborted: Steam natives not loaded");
            return false;
        }

        attachDesired = true;
        attachRetryCount = 0;   // a manual retry restarts the automatic budget

        // Drop the action manifest + default bindings into Steam's controller_config once per session.
        // Deliberately BEFORE the attach attempt and independent of its outcome: Steam reads that
        // folder on its own schedule, so the files being on disk is useful even if we never connect.
        deployManifestOnce();

        return attemptAttach();
    }

    /**
     * One attach attempt cycle. Split out from {@link #init()} so the periodic retry can re-run just
     * this part without repeating the policy evaluation or the VDF deploy.
     *
     * <p><b>Two attempts, in order (v0.81.0).</b> Which AppID the Steamworks SDK initializes as decides
     * which app's Steam Input configuration the mod can read — and the SDK resolves that from the
     * {@code SteamAppId} environment variable FIRST, only falling back to {@code steam_appid.txt} when
     * the variable is absent. So:
     * <ol>
     *   <li><b>The session's own AppID.</b> When Steam launched this process it stamped its AppID into
     *       the environment; that is the app Steam files this session's controller layout, action sets
     *       and user bindings under. Attaching as anything else means reading a different app's
     *       actions than the one the user is configuring — connected, but permanently mismatched.
     *       This is why the previous unconditional rewrite to 480 for shortcut launches was wrong.</li>
     *   <li><b>Spacewar (480), with the environment cleared.</b> A non-Steam shortcut's pseudo-AppID is
     *       not a real Steamworks app, so attempt 1 may be rejected outright. Clearing the variables
     *       (see {@link NativeEnv}) makes the SDK fall through to {@code steam_appid.txt}, which we
     *       write as 480 — Valve's public sandbox ID, which any process may use.</li>
     * </ol>
     * Both outcomes are recorded in {@link #attachTrail()}, so a single hardware run answers which
     * path this environment actually accepts instead of leaving it to be guessed at again.
     */
    private static boolean attemptAttach() {
        if (steamAvailable) return true;

        // Pre-flight: On Linux, SteamAPI.init() can return true even when Steam is NOT running because
        // it finds steamclient.so and "initializes" locally. But the first SteamAPI.runCallbacks() then
        // tries to write to the IPC pipe (which doesn't exist) → fatal assert in pipes.cpp → process
        // killed. This abort() is NOT catchable from Java. Guard before init() is the only real fix.
        if (!isSteamReachable()) {
            LogUtil.info("[SteamPad] Steam is not running — skipping Steam API init. Fallback backend "
                    + "will handle controllers.");
            note("preflight: Steam not reachable");
            envReport = envReport.withSteamAvailable(false);
            return false;
        }

        long primary = resolveSdkAppId();
        boolean primaryIsShortcut = SteamLaunchDetector.isShortcutPseudoAppId(primary);

        if (tryInitAs(primary, primaryIsShortcut ? "shortcut/session AppID" : "session AppID")) {
            return finishAttach(primary);
        }

        // Attempt 2 only makes sense when attempt 1 used a shortcut pseudo-AppID (a real store AppID
        // that Steam rejects will not start working just because we renamed ourselves), and only when
        // we are allowed to write the file the SDK would then fall back to.
        if (primaryIsShortcut && dev.steampad.config.ConfigManager.getGlobal().autoWriteAppIdFile) {
            String savedAppId = System.getenv("SteamAppId");
            String savedGameId = System.getenv("SteamGameId");
            boolean cleared = NativeEnv.unset("SteamAppId");
            cleared &= NativeEnv.unset("SteamGameId");
            if (!cleared) {
                note("attempt 2 skipped: could not clear SteamAppId/SteamGameId "
                        + (NativeEnv.isSupported() ? "(libc call failed)" : "(unsupported platform)"));
                return false;
            }
            note("cleared SteamAppId/SteamGameId from the native environment for attempt 2");
            if (tryInitAs(SANDBOX_APP_ID, "sandbox AppID")) {
                LogUtil.warn("[SteamPad] Attached as Spacewar ({}) instead of this session's AppID ({}). "
                        + "Steam Input will work, but Steam files your controller layout under the "
                        + "SHORTCUT's id — if bound actions never reach the mod, that mismatch is why.",
                        SANDBOX_APP_ID, primary);
                return finishAttach(SANDBOX_APP_ID);
            }
            // Both failed — put the environment back exactly as Steam handed it to us, so nothing else
            // in the process (or a later retry) sees a half-modified environment.
            NativeEnv.set("SteamAppId", savedAppId);
            NativeEnv.set("SteamGameId", savedGameId);
            note("attempt 2 failed as well — restored the original environment");
        }
        return false;
    }

    /** Writes the AppID file for {@code appId}, then calls SteamAPI.init() and records the outcome. */
    private static boolean tryInitAs(long appId, String label) {
        writeAppIdFile(appId);
        try {
            if (SteamAPI.init()) {
                note("attempt [" + label + " = " + appId + "] -> OK");
                return true;
            }
            note("attempt [" + label + " = " + appId + "] -> SteamAPI.init() returned false");
        } catch (Throwable t) {
            note("attempt [" + label + " = " + appId + "] -> threw " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
            LogUtil.error("Steam API init threw exception: {}", t.getMessage(), t);
        }
        return false;
    }

    /** Marks the session attached and brings Steam Input up on top of it. */
    private static boolean finishAttach(long appId) {
        steamAvailable = true;
        initializedAppId = appId;
        envReport = envReport.withSteamAvailable(true);
        LogUtil.info("[SteamPad] Steam API initialized as AppID {} (Steamworks4j 1.9.0 / ISteamController).",
                appId);

        inputAvailable = SteamInputManager.init();
        if (!inputAvailable) {
            LogUtil.warn("Steam Input failed to initialize. Controller features will be limited.");
            note("Steam Input (ISteamController) init FAILED");
        } else {
            note("Steam Input (ISteamController) init OK; action sets valid="
                    + SteamActionRegistry.areActionSetsValid());
        }
        return true;
    }

    /**
     * "Is Steam running" with the documented false-negative worked around.
     *
     * <p>{@link SteamAPI#isSteamRunning()} is implemented in the small bundled Steamworks4j 1.9.0
     * (~2018) native shim, which on Linux can fail to recognize a modern Steam client's IPC layout
     * (e.g. Bazzite/SteamOS-style installs) even though Steam is demonstrably running. A false negative
     * here just means "degrade to fallback needlessly" (no crash risk), so it is safe to override with
     * an independent, pure-Java, cross-platform process-list check.
     */
    private static boolean isSteamReachable() {
        boolean nativeSaysRunning = false;
        try {
            nativeSaysRunning = SteamAPI.isSteamRunning();
        } catch (Throwable t) {
            LogUtil.warn("[SteamPad] Could not check SteamAPI.isSteamRunning(): {} — falling back to "
                    + "process scan.", t.getMessage());
        }
        if (nativeSaysRunning) return true;

        boolean processSaysRunning = isSteamProcessAlive();
        if (processSaysRunning && !notedProcessScanOverride) {
            notedProcessScanOverride = true;
            LogUtil.info("[SteamPad] SteamAPI.isSteamRunning() said no, but a live Steam process was found "
                    + "(cross-platform process scan) — proceeding with Steam API init.");
            note("preflight: native isSteamRunning()=false, process scan found Steam -> proceeding");
        }
        return processSaysRunning;
    }

    /**
     * The AppID this session belongs to: Steam's own launch signal (env vars, see
     * {@link dev.steampad.platform.SteamLaunchDetector}) when present — meaning this process was
     * genuinely started from a Steam library entry — otherwise the configured/default 480 (Spacewar).
     *
     * <p>Long, not int: shortcut pseudo-AppIDs always exceed {@code Integer.MAX_VALUE} — the previous
     * int version overflowed negative for every non-Steam shortcut and silently fell back to 480
     * everywhere (the real reason "launched from Steam" was never detected on the user's Ally).
     */
    public static long resolveEffectiveAppId() {
        long detected = envReport != null ? envReport.steamLaunchAppId : SteamLaunchDetector.detectAppId();
        if (detected > 0) return detected;
        int configured = dev.steampad.config.ConfigManager.getGlobal().steamAppId;
        return configured > 0 ? configured : SANDBOX_APP_ID;
    }

    /**
     * The AppID the FIRST {@link SteamAPI#init()} attempt should use — the session's own, shortcut
     * pseudo-AppIDs included.
     *
     * <p><b>Changed in v0.81.0.</b> This used to rewrite every shortcut pseudo-AppID to 480 on the
     * theory that only real store apps can attach. That made the mod attach as one app while Steam
     * filed the session's controller configuration under a different one, so even a successful attach
     * could not read the bindings the user had made. The session's AppID is now tried first and 480 is
     * the documented fallback (see {@link #attemptAttach()}), which also matches the observation that
     * the one time every diagnostic line went green was with the manifest deployed for the SHORTCUT's
     * AppID — not for 480.
     */
    public static long resolveSdkAppId() {
        return resolveEffectiveAppId();
    }

    /** Both directories the Steamworks SDK may look in for {@code steam_appid.txt}. */
    private static List<Path> appIdFileTargets() {
        LinkedHashSet<Path> targets = new LinkedHashSet<>();
        // 1) Process working directory — what the Steamworks SDK actually reads.
        String wd = System.getProperty("user.dir");
        if (wd != null && !wd.isBlank()) targets.add(Path.of(wd));
        // 2) Game directory (Fabric) — usually the same, but covers launchers that differ.
        try {
            targets.add(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir());
        } catch (Throwable ignored) {}
        return new ArrayList<>(targets);
    }

    /**
     * Writes {@code steam_appid.txt} containing {@code appId} where the Steamworks SDK looks for it.
     *
     * <p>Unlike the previous version this REWRITES a file whose contents differ from what this attempt
     * needs. The old code skipped any existing file outright, so a value written by an older build (or
     * by a different attempt) was never corrected — the file silently pinned the AppID for good.
     */
    private static void writeAppIdFile(long appId) {
        if (!dev.steampad.config.ConfigManager.getGlobal().autoWriteAppIdFile) {
            note("steam_appid.txt not written (autoWriteAppIdFile disabled)");
            return;
        }
        String content = Long.toString(appId);
        for (Path dir : appIdFileTargets()) {
            try {
                Path file = dir.resolve("steam_appid.txt");
                if (Files.exists(file)) {
                    String current = Files.readString(file).trim();
                    if (current.equals(content)) continue;   // already what this attempt needs
                    LogUtil.info("[SteamPad] Rewriting steam_appid.txt at {}: '{}' -> '{}'",
                            file, current, content);
                }
                Files.writeString(file, content);
            } catch (Throwable t) {
                LogUtil.warn("[SteamPad] Could not write steam_appid.txt to {}: {}", dir, t.getMessage());
                note("steam_appid.txt write FAILED at " + dir + ": " + t.getMessage());
            }
        }
    }

    /**
     * Drops the Steam Input action manifest + generated default bindings into Steam's own
     * controller_config folder, at most once per session. Best-effort; never throws.
     */
    private static void deployManifestOnce() {
        if (deployedThisSession) return;
        deployedThisSession = true;
        SteamControllerConfigDeployer.deploy(resolveEffectiveAppId());
    }

    /**
     * Deploys the manifest right now, bypassing the once-per-session guard. Used by the Steam Input
     * settings screen so switching the experimental toggle ON takes effect immediately instead of
     * silently waiting for the next game start (the exact trap the toggle's first users fell into —
     * turning it OFF cleaned up instantly while turning it ON appeared to do nothing).
     */
    public static void deployManifestNow() {
        deployedThisSession = true;
        SteamControllerConfigDeployer.deploy(resolveEffectiveAppId());
    }

    /**
     * Cross-platform, pure-Java fallback for "is Steam running": scans the OS process list (via
     * {@link ProcessHandle}, no native code, works on Linux/Windows/macOS alike) for a process whose
     * executable name looks like the Steam client ("steam" on Linux/macOS, "steam.exe" on Windows).
     *
     * <p>Exists because the bundled Steamworks4j 1.9.0 native {@code isSteamRunning()} can false-negative
     * against modern Steam client installs (observed on Bazzite/SteamOS-style layouts where the client
     * lives under {@code ~/.local/share/Steam} with a newer IPC/pipe scheme than that ~2018-era shim
     * expects). Best-effort: any failure to enumerate processes (sandboxing, permissions) is swallowed
     * and treated as "unknown" (false), never crashes.
     */
    public static boolean isSteamProcessAlive() {
        try {
            return ProcessHandle.allProcesses().anyMatch(p -> {
                String cmd = p.info().command().orElse("");
                if (cmd.isEmpty()) return false;
                String name = Path.of(cmd).getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                // Exact-ish match on the client binary name — avoids matching unrelated processes that
                // merely contain "steam" as a substring (e.g. "steamwebhelper" children, mod tools).
                return name.equals("steam") || name.equals("steam.exe");
            });
        } catch (Throwable t) {
            return false;
        }
    }

    // Retry window for ActionSet handle registration: observed intermittently on Linux — the VDF is
    // correctly imported and visible in Steam's own controller-config UI, but ISteamController's
    // getActionSetHandle() can return 0 right after init() even so, only becoming valid a few seconds
    // later once Steam's Input subsystem finishes internally binding this connection to the app's VDF.
    // Waiting BEFORE launching does not help (confirmed) — only retrying the handle lookup after init
    // does. Re-registering is cheap (just re-reads handles) and safe to repeat.
    private static final int ACTIONSET_RETRY_TICKS = 20;   // ~1s between attempts (20 ticks/s)
    private static final int ACTIONSET_RETRY_MAX = 10;      // give up after ~10s
    private static int actionSetRetryCount = 0;

    // Retry window for the ATTACH itself. Steam picks a freshly-deployed manifest up on its own
    // schedule (observed in hardware: the action-set layers appeared in Steam's UI mid-session, well
    // after our one and only init() attempt had already failed at startup). Without this the first
    // failure was permanent for the whole session, so that window could never be caught.
    private static final int ATTACH_RETRY_TICKS = 100;   // ~5s between attempts
    private static final int ATTACH_RETRY_MAX = 12;      // ~60s of trying, then stop
    private static int attachRetryCount = 0;
    private static int attachRetryTick = 0;

    /**
     * Re-attempts {@link SteamActionRegistry#registerAll} every ~1s for ~10s after init if the
     * ActionSet handles came back invalid (0) despite the VDF being present. See the retry-window
     * comment above. No-op once valid or once the retry budget is exhausted.
     */
    private static void retryActionSetRegistrationIfNeeded() {
        if (SteamActionRegistry.areActionSetsValid()) { actionSetRetryCount = 0; return; }
        if (actionSetRetryCount >= ACTIONSET_RETRY_MAX) return;
        if (callbackTick % ACTIONSET_RETRY_TICKS != 0) return;
        var controller = SteamInputManager.getRawController();
        if (controller == null) return;
        actionSetRetryCount++;
        SteamActionRegistry.registerAll(controller);
        if (SteamActionRegistry.areActionSetsValid()) {
            LogUtil.info("[SteamPad] ActionSet handles became valid on retry #{} (~{}s after init).",
                    actionSetRetryCount, actionSetRetryCount * (ACTIONSET_RETRY_TICKS / 20));
            note("action sets became valid on retry #" + actionSetRetryCount);
        } else if (actionSetRetryCount == ACTIONSET_RETRY_MAX) {
            LogUtil.warn("[SteamPad] ActionSet handles still invalid after {} retries (~10s) — "
                    + "giving up; VDF present but Steam Input never bound it to this session. "
                    + "Controllers will use raw button fallback mode.", ACTIONSET_RETRY_MAX);
            note("action sets STILL invalid after " + ACTIONSET_RETRY_MAX + " retries");
        }
    }

    /**
     * Periodically re-runs the attach while the policy wants one and it hasn't succeeded. Cheap: a
     * counter compare on every tick, real work only once every {@link #ATTACH_RETRY_TICKS} ticks and
     * only while {@link #attachDesired} is set (so a NEVER/desktop session never pays anything).
     */
    private static void retryAttachIfNeeded() {
        if (steamAvailable || !attachDesired) return;
        if (attachRetryCount >= ATTACH_RETRY_MAX) return;
        if (++attachRetryTick % ATTACH_RETRY_TICKS != 0) return;

        attachRetryCount++;
        if (attemptAttach()) {
            LogUtil.info("[SteamPad] Steam attach succeeded on retry #{} (~{}s after startup) — Steam "
                    + "had not accepted the connection yet at launch.",
                    attachRetryCount, attachRetryCount * (ATTACH_RETRY_TICKS / 20));
            note("attach succeeded on retry #" + attachRetryCount);
        } else if (attachRetryCount >= ATTACH_RETRY_MAX) {
            LogUtil.warn("[SteamPad] Steam attach still failing after {} attempts (~{}s) — giving up for "
                    + "this session. SDL3/GLFW keep driving controllers normally. See the Steam Input "
                    + "section of the debug dump for the full attempt trail.",
                    ATTACH_RETRY_MAX, ATTACH_RETRY_MAX * (ATTACH_RETRY_TICKS / 20));
            note("gave up after " + ATTACH_RETRY_MAX + " attach attempts");
        }
    }

    private static int callbackTick = 0;

    /**
     * Processes pending Steam API callbacks. Must be called every game tick on the game thread.
     *
     * <p>Checks every 200 ticks (10 s) whether Steam is still running. If Steam exits while MC is
     * running the IPC pipe breaks and the next runCallbacks() would trigger a fatal native assert
     * (abort), killing the process. Degrading to fallback before that happens is the only safe path.
     */
    public static void runCallbacks() {
        if (!steamAvailable) {
            retryAttachIfNeeded();
            return;
        }
        // Periodic isSteamRunning() check so that if Steam exits mid-session we degrade gracefully
        // instead of crashing on the next IPC write.
        if (++callbackTick % 200 == 0) {
            try {
                // Same false-negative caveat as init(): the bundled native isSteamRunning() can lie.
                // Only treat Steam as gone if BOTH the native check and our own process scan agree.
                if (!SteamAPI.isSteamRunning() && !isSteamProcessAlive()) {
                    LogUtil.warn("[SteamPad] Steam exited — disabling Steam Input, switching to fallback backend.");
                    steamAvailable = false;
                    inputAvailable = false;
                    initializedAppId = 0L;
                    note("Steam exited mid-session — detached");
                    return;
                }
            } catch (Throwable ignored) {}
        }
        try {
            SteamAPI.runCallbacks();
            if (inputAvailable) {
                SteamInputManager.runFrame();
                retryActionSetRegistrationIfNeeded();
            }
        } catch (Throwable t) {
            LogUtil.error("Exception during Steam callback processing: {}", t.getMessage(), t);
        }
    }

    /**
     * Shuts down Steam API. Call on game exit.
     */
    public static void shutdown() {
        if (!steamAvailable) return;
        try {
            SteamInputManager.shutdown();
            SteamAPI.shutdown();
            steamAvailable = false;
            inputAvailable = false;
            initializedAppId = 0L;
            attachDesired = false;
            note("shutdown() — detached by request");
            LogUtil.info("Steam API shut down.");
        } catch (Throwable t) {
            LogUtil.error("Exception during Steam shutdown: {}", t.getMessage(), t);
        }
    }
}
