package dev.steampad.steam;

import com.codedisaster.steamworks.SteamAPI;
import com.codedisaster.steamworks.SteamException;
import dev.steampad.platform.EnvironmentReport;
import dev.steampad.platform.SteamLaunchDetector;
import dev.steampad.util.LogUtil;

/**
 * Manages the lifecycle of the Steam API: init, callbacks loop, and shutdown.
 *
 * Must be initialized on the game thread. runCallbacks() is called every client tick
 * via Fabric's ClientTickEvents to process pending Steam API events.
 */
public final class SteamBootstrap {

    private static boolean steamAvailable = false;
    private static boolean inputAvailable = false;
    private static boolean attachSkippedByPolicy = false;
    private static EnvironmentReport envReport;

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

    /**
     * Initializes Steam API and Steam Input. Call once during mod client init.
     * Returns true if at least Steam API initialized successfully.
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
        if (!shouldAttach) {
            attachSkippedByPolicy = true;
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
            return false;
        }

        // Ensure the Steamworks SDK can find an AppID before init (the missing piece on Prism/Flatpak:
        // the file was checked but never written). With Steam running and IPC reachable, this lets
        // Steam Input initialize even when MC was not launched from Steam.
        ensureAppIdFile();

        // Pre-flight: On Linux, SteamAPI.init() can return true even when Steam is NOT running because
        // it finds steamclient.so and "initializes" locally. But the first SteamAPI.runCallbacks() then
        // tries to write to the IPC pipe (which doesn't exist) → fatal assert in pipes.cpp → process
        // killed. This abort() is NOT catchable from Java. Guard before init() is the only real fix.
        //
        // SteamAPI.isSteamRunning() itself can give a FALSE NEGATIVE: it's implemented in the small
        // bundled Steamworks4j 1.9.0 (~2018) native shim, which on Linux can fail to recognize a
        // modern Steam client's IPC layout (e.g. Bazzite/SteamOS-style installs) even though Steam is
        // demonstrably running. A false negative here just means "degrade to fallback needlessly" (no
        // crash risk), so it's safe to override it with an independent, pure-Java, cross-platform
        // process-list check: if isSteamRunning() says no but a real "steam"/"steam.exe" process is
        // alive, trust that instead and proceed — the crash this guard protects against only happens
        // when Steam is ACTUALLY not running, which our own check rules out.
        boolean nativeSaysRunning = false;
        try {
            nativeSaysRunning = SteamAPI.isSteamRunning();
        } catch (Throwable t) {
            LogUtil.warn("[SteamPad] Could not check SteamAPI.isSteamRunning(): {} — falling back to process scan.", t.getMessage());
        }
        if (!nativeSaysRunning) {
            boolean processSaysRunning = isSteamProcessAlive();
            if (!processSaysRunning) {
                LogUtil.info("[SteamPad] Steam is not running — skipping Steam API init. Fallback backend will handle controllers.");
                envReport = envReport.withSteamAvailable(false);
                return false;
            }
            LogUtil.info("[SteamPad] SteamAPI.isSteamRunning() said no, but a live Steam process was found "
                    + "(cross-platform process scan) — proceeding with Steam API init.");
        }

        try {
            // SteamAPI.init() returns true only if Steam is running and the app is valid.
            // In dev, use steam_appid.txt with AppID 480 (Spacewar).
            if (!SteamAPI.init()) {
                String wd = System.getProperty("user.dir", "?");
                LogUtil.warn("SteamAPI.init() returned false. Steam may not be running or AppID is missing.");
                LogUtil.warn("[SteamPad] Working directory (where steam_appid.txt should be): {}", wd);
                LogUtil.warn("Hint: Place 'steam_appid.txt' with AppID 480 in the run directory for development.");
                LogUtil.warn("Hint: Also try 'steamappid.txt' (no underscore) — both variants are checked.");
                envReport = envReport.withSteamAvailable(false);
                return false;
            }

            steamAvailable = true;
            envReport = envReport.withSteamAvailable(true);
            LogUtil.info("Steam API initialized (Steamworks4j 1.9.0 / ISteamController).");

            // Init Steam Input
            inputAvailable = SteamInputManager.init();
            if (!inputAvailable) {
                LogUtil.warn("Steam Input failed to initialize. Controller features will be limited.");
            }

            return true;
        } catch (SteamException e) {
            LogUtil.error("Steam API init threw exception: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * The AppID actually used this session: Steam's own launch signal (env vars, see
     * {@link dev.steampad.platform.SteamLaunchDetector}) when present — meaning this process was
     * genuinely started from a Steam library entry, so using ITS real (pseudo-)AppID instead of the
     * generic 480 sandbox is what lets Steam associate our Steam Input action manifest with the actual
     * running session — otherwise the configured/default 480 (Spacewar), exactly as before.
     *
     * <p>Long, not int: shortcut pseudo-AppIDs always exceed {@code Integer.MAX_VALUE} — the previous
     * int version overflowed negative for every non-Steam shortcut and silently fell back to 480
     * everywhere (the real reason "launched from Steam" was never detected on the user's Ally).
     */
    public static long resolveEffectiveAppId() {
        long detected = envReport != null ? envReport.steamLaunchAppId : SteamLaunchDetector.detectAppId();
        if (detected > 0) return detected;
        int configured = dev.steampad.config.ConfigManager.getGlobal().steamAppId;
        return configured > 0 ? configured : 480;
    }

    /**
     * The AppID {@code steam_appid.txt} (and thus {@code SteamAPI.init()}) should use: shortcut
     * pseudo-AppIDs are NOT real store apps — the Steamworks SDK cannot validate against them, so a
     * shortcut session still inits as 480 (Spacewar), exactly the combination tools like GlosSI use.
     * The pseudo-ID still matters for the VDF deploy (see {@link SteamControllerConfigDeployer}):
     * that's the folder name Steam actually checks for the SHORTCUT's controller config.
     */
    public static long resolveSdkAppId() {
        long effective = resolveEffectiveAppId();
        return SteamLaunchDetector.isShortcutPseudoAppId(effective) ? 480 : effective;
    }

    /**
     * Writes {@code steam_appid.txt} (containing the effective AppID — see
     * {@link #resolveEffectiveAppId()}) where the Steamworks SDK looks for it — the process working
     * directory and the game directory — unless it already exists or the user disabled it. This is the
     * concrete fix for "no appID found": the SDK then has an AppID to hand to a running Steam client.
     * Also drops our Steam Input action manifest (game_actions_&lt;appid&gt;.vdf) directly into Steam's
     * own controller_config folder for that SAME AppID, so there is no manual "import the VDF in Steam"
     * step left when we correctly detected a real Steam launch (B033's pain point). Best-effort; never
     * throws.
     */
    private static void ensureAppIdFile() {
        long appId = resolveEffectiveAppId();
        // steam_appid.txt must hold a REAL AppID (SDK-valid) — a shortcut pseudo-ID fails init.
        long sdkAppId = resolveSdkAppId();
        try {
            if (!dev.steampad.config.ConfigManager.getGlobal().autoWriteAppIdFile) {
                LogUtil.info("[SteamPad] autoWriteAppIdFile disabled — not writing steam_appid.txt.");
            } else {
                String content = Long.toString(sdkAppId);
                java.util.LinkedHashSet<java.nio.file.Path> targets = new java.util.LinkedHashSet<>();
                // 1) Process working directory — what the Steamworks SDK actually reads.
                String wd = System.getProperty("user.dir");
                if (wd != null && !wd.isBlank()) targets.add(java.nio.file.Path.of(wd));
                // 2) Game directory (Fabric) — usually the same, but covers launchers that differ.
                try {
                    targets.add(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir());
                } catch (Throwable ignored) {}

                for (java.nio.file.Path dir : targets) {
                    try {
                        java.nio.file.Path file = dir.resolve("steam_appid.txt");
                        if (java.nio.file.Files.exists(file)) {
                            LogUtil.info("[SteamPad] steam_appid.txt already present at {}", file);
                            continue;
                        }
                        java.nio.file.Files.writeString(file, content);
                        LogUtil.info("[SteamPad] Wrote steam_appid.txt (SDK AppID {}, session AppID {}) to {}",
                                sdkAppId, appId, file);
                    } catch (Throwable t) {
                        LogUtil.warn("[SteamPad] Could not write steam_appid.txt to {}: {}", dir, t.getMessage());
                    }
                }
            }
        } catch (Throwable t) {
            LogUtil.warn("[SteamPad] ensureAppIdFile failed: {}", t.getMessage());
        }

        SteamControllerConfigDeployer.deploy(appId);
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
    private static boolean isSteamProcessAlive() {
        try {
            return ProcessHandle.allProcesses().anyMatch(p -> {
                String cmd = p.info().command().orElse("");
                if (cmd.isEmpty()) return false;
                String name = java.nio.file.Path.of(cmd).getFileName().toString().toLowerCase(java.util.Locale.ROOT);
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
        } else if (actionSetRetryCount == ACTIONSET_RETRY_MAX) {
            LogUtil.warn("[SteamPad] ActionSet handles still invalid after {} retries (~10s) — "
                    + "giving up; VDF present but Steam Input never bound it to this session. "
                    + "Controllers will use raw button fallback mode.", ACTIONSET_RETRY_MAX);
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
        if (!steamAvailable) return;
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
            LogUtil.info("Steam API shut down.");
        } catch (Throwable t) {
            LogUtil.error("Exception during Steam shutdown: {}", t.getMessage(), t);
        }
    }
}
