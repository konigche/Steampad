package dev.steampad.steam;

import dev.steampad.config.ConfigManager;
import dev.steampad.platform.EnvironmentReport;
import dev.steampad.platform.LinuxRuntimeInspector;
import dev.steampad.platform.NativeEnv;
import dev.steampad.platform.SteamLaunchDetector;
import dev.steampad.service.ControllerManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Deep, self-contained report on <b>why Steam Input is or is not working right now</b>.
 *
 * <p>Steam Input fails silently by design: {@code SteamAPI.init()} returns a bare {@code false},
 * unresolved action handles are just the number 0, and an unbound action is indistinguishable from a
 * missing one. Every past investigation of this subsystem stalled on the same thing — the mod could
 * see that it had failed but never <em>where</em>. This report makes each link in the chain visible in
 * one paste: the attach policy, the AppID actually used, every init attempt and its outcome, the Steam
 * client files the SDK depends on, what is really sitting in Steam's {@code controller_config} folder,
 * every action handle Steam resolved (or didn't), and finally a live probe of which actions Steam has
 * actually bound to a physical input.
 *
 * <p>Read-only and fully defensive: every section is wrapped so a failure anywhere degrades to one
 * line of text instead of breaking the dump.
 */
public final class SteamInputDiagnostics {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private SteamInputDiagnostics() {}

    public static String generate() {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("-- Steam Input (detailed) --\n");
        section(sb, "Verdict", SteamInputDiagnostics::verdict);
        section(sb, "Attach policy", SteamInputDiagnostics::attachPolicy);
        section(sb, "AppID resolution", SteamInputDiagnostics::appIdChain);
        section(sb, "Attach attempt trail", SteamInputDiagnostics::trail);
        section(sb, "Native libraries", SteamInputDiagnostics::natives);
        section(sb, "Steam client layout", SteamInputDiagnostics::steamClientLayout);
        section(sb, "Deployed files", SteamInputDiagnostics::deployedFiles);
        section(sb, "Action sets", SteamInputDiagnostics::actionSets);
        section(sb, "Action handles", SteamInputDiagnostics::actionHandles);
        section(sb, "Live binding probe", SteamInputDiagnostics::liveProbe);
        return sb.toString();
    }

    /** Runs one section, converting any failure into a visible line instead of losing the whole dump. */
    private static void section(StringBuilder sb, String title, java.util.function.Supplier<String> body) {
        sb.append('\n').append("  [").append(title).append("]\n");
        String text;
        try {
            text = body.get();
        } catch (Throwable t) {
            text = "(section failed: " + t + ")";
        }
        for (String line : text.split("\n", -1)) {
            if (!line.isEmpty()) sb.append("    ").append(line).append('\n');
        }
    }

    // ---------------------------------------------------------------- sections

    /** A plain-language read of the current state plus the single most useful next action. */
    private static String verdict() {
        if (SteamBootstrap.isAttachSkippedByPolicy()) {
            return "NOT ATTACHED BY CHOICE — 'Conectar a Steam' is set to a mode that does not attach in\n"
                 + "this environment. SDL3/GLFW drive everything; Steam Input slots are inactive.\n"
                 + "This is the safe default and NOT an error.";
        }
        if (!SteamNativeLoader.isLoaded()) {
            return "BLOCKED — Steam native libraries never loaded, so no Steam call is possible at all.\n"
                 + "See the 'Native libraries' section below.";
        }
        if (!SteamBootstrap.isSteamAvailable()) {
            String pending = SteamBootstrap.isAttachPending()
                    ? "Automatic retries are still running (attempt " + SteamBootstrap.attachRetryCount()
                      + "), so this may still turn green on its own within ~60s of launch."
                    : "Automatic retries are exhausted for this session.";
            return "NOT ATTACHED — SteamAPI.init() has not succeeded.\n" + pending
                 + "\nThe 'Attach attempt trail' section below lists every attempt and why it failed.";
        }
        if (!SteamBootstrap.isInputAvailable()) {
            return "PARTIAL — Steam API is attached but ISteamController failed to initialize.";
        }
        if (!SteamActionRegistry.areActionSetsValid()) {
            return "PARTIAL — attached and Steam Input is up, but the action sets are still handle 0.\n"
                 + "Steam has not loaded our manifest for this AppID. RESTART STEAM once; if it stays 0\n"
                 + "afterwards, check that the manifest really landed in the folder shown below.";
        }
        return "ATTACHED — Steam API + Steam Input are live and the action sets resolved.\n"
             + "If buttons still do nothing, read the 'Live binding probe' section: that distinguishes\n"
             + "'Steam has no bindings' from 'the mod is not reading them'.";
    }

    private static String attachPolicy() {
        EnvironmentReport env = SteamBootstrap.getEnvironmentReport();
        var cfg = ConfigManager.getGlobal();
        StringBuilder sb = new StringBuilder();
        sb.append("steamAttachMode      : ").append(cfg.steamAttachMode).append('\n');
        sb.append("deployIgaManifest    : ").append(cfg.deployIgaManifest)
          .append(cfg.deployIgaManifest ? "  (EXPERIMENTAL — on)" : "  (off — manifests auto-removed)").append('\n');
        sb.append("autoWriteAppIdFile   : ").append(cfg.autoWriteAppIdFile).append('\n');
        sb.append("autoWriteCtrlConfig  : ").append(cfg.autoWriteControllerConfig).append('\n');
        sb.append("gamescope/Game Mode  : ").append(env != null && env.isGamescope).append('\n');
        sb.append("skipped by policy    : ").append(SteamBootstrap.isAttachSkippedByPolicy()).append('\n');
        sb.append("retries used         : ").append(SteamBootstrap.attachRetryCount())
          .append(SteamBootstrap.isAttachPending() ? "  (more pending)" : "").append('\n');
        return sb.toString();
    }

    private static String appIdChain() {
        StringBuilder sb = new StringBuilder();
        sb.append("env (raw)            : ").append(SteamLaunchDetector.rawDiagnostics()).append('\n');
        sb.append("detected launch id   : ").append(SteamLaunchDetector.detectAppId()).append('\n');
        long effective = SteamBootstrap.resolveEffectiveAppId();
        sb.append("effective (this app) : ").append(effective)
          .append(SteamLaunchDetector.isShortcutPseudoAppId(effective)
                  ? "  (non-Steam shortcut pseudo-AppID)" : "  (real/configured AppID)").append('\n');
        sb.append("1st init candidate   : ").append(SteamBootstrap.resolveSdkAppId()).append('\n');
        sb.append("2nd init candidate   : ")
          .append(SteamLaunchDetector.isShortcutPseudoAppId(effective)
                  ? SteamBootstrap.SANDBOX_APP_ID + " (Spacewar, with the env cleared)"
                  : "n/a — no fallback for a real AppID").append('\n');
        long attached = SteamBootstrap.getInitializedAppId();
        sb.append("ATTACHED AS          : ").append(attached == 0L ? "not attached" : attached);
        if (attached != 0L && attached != effective) {
            sb.append("\n  !! MISMATCH: Steam files this session's controller layout under ").append(effective)
              .append("\n     but the mod attached as ").append(attached)
              .append(" — bindings made in Steam's UI may never reach the mod.");
        }
        sb.append('\n');
        sb.append("env editing support  : ").append(NativeEnv.isSupported()
                ? "yes (libc)" : "no — the 2nd attempt cannot run on this platform").append('\n');
        for (Path dir : appIdFileDirs()) {
            sb.append("steam_appid.txt      : ").append(describeAppIdFile(dir.resolve("steam_appid.txt"))).append('\n');
        }
        return sb.toString();
    }

    private static List<Path> appIdFileDirs() {
        List<Path> dirs = new ArrayList<>();
        String wd = System.getProperty("user.dir");
        if (wd != null && !wd.isBlank()) dirs.add(Path.of(wd));
        try {
            Path game = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
            if (!dirs.contains(game)) dirs.add(game);
        } catch (Throwable ignored) {}
        return dirs;
    }

    private static String describeAppIdFile(Path file) {
        try {
            if (!Files.exists(file)) return "MISSING at " + file;
            return "'" + Files.readString(file).trim() + "' at " + file;
        } catch (Throwable t) {
            return "unreadable at " + file + " (" + t.getMessage() + ")";
        }
    }

    private static String trail() {
        List<String> trail = SteamBootstrap.attachTrail();
        if (trail.isEmpty()) return "(nothing recorded — init() has not run this session)";
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (String line : trail) sb.append(i++).append(". ").append(line).append('\n');
        return sb.toString();
    }

    private static String natives() {
        StringBuilder sb = new StringBuilder();
        sb.append("loaded               : ").append(SteamNativeLoader.isLoaded()).append('\n');
        String custom = ConfigManager.getGlobal().customNativesPath;
        sb.append("customNativesPath    : ").append(custom.isEmpty() ? "(empty — default temp dir)" : custom).append('\n');
        if (!custom.isEmpty()) {
            sb.append("  note               : this is an EXTRACTION TARGET for the mod's own bundled\n");
            sb.append("                       natives, not a place to load Steam's from. Pointing it at a\n");
            sb.append("                       Steam install folder works but is not recommended; leaving it\n");
            sb.append("                       empty is the clean setup.\n");
        }
        sb.append("extract path prop    : ")
          .append(System.getProperty("com.codedisaster.steamworks.SharedLibraryExtractPath", "(unset)")).append('\n');
        return sb.toString();
    }

    /**
     * The Steam client files {@code SteamAPI_Init} depends on. On Linux the SDK reaches a running Steam
     * through {@code ~/.steam}: a missing or stale entry here is a classic silent cause of init failing
     * while the Steam process is demonstrably alive.
     */
    private static String steamClientLayout() {
        StringBuilder sb = new StringBuilder();
        sb.append("Steam process alive  : ").append(SteamBootstrap.isSteamProcessAlive()).append('\n');
        if (!LinuxRuntimeInspector.isLinux()) {
            sb.append("(Linux-only section — skipped on this OS)\n");
            return sb.toString();
        }
        Path home = Path.of(System.getProperty("user.home", ""));
        Path dotSteam = home.resolve(".steam");
        sb.append(fileLine("~/.steam", dotSteam));
        sb.append(fileLine("~/.steam/steam.pid", dotSteam.resolve("steam.pid")));
        try {
            Path pid = dotSteam.resolve("steam.pid");
            if (Files.exists(pid)) {
                String raw = Files.readString(pid).trim();
                boolean alive = ProcessHandle.of(Long.parseLong(raw)).map(ProcessHandle::isAlive).orElse(false);
                sb.append("  pid contents       : ").append(raw).append(alive ? " (ALIVE)" : " (NOT running)").append('\n');
            }
        } catch (Throwable t) {
            sb.append("  pid contents       : unreadable (").append(t.getMessage()).append(")\n");
        }
        sb.append(fileLine("~/.steam/steam.pipe", dotSteam.resolve("steam.pipe")));
        sb.append(fileLine("~/.steam/registry.vdf", dotSteam.resolve("registry.vdf")));
        sb.append(fileLine("~/.steam/sdk32/steamclient.so", dotSteam.resolve("sdk32").resolve("steamclient.so")));
        sb.append(fileLine("~/.steam/sdk64/steamclient.so", dotSteam.resolve("sdk64").resolve("steamclient.so")));
        sb.append(fileLine("~/.steam/steam (link)", dotSteam.resolve("steam")));
        sb.append(fileLine("~/.steam/root (link)", dotSteam.resolve("root")));
        return sb.toString();
    }

    private static String deployedFiles() {
        StringBuilder sb = new StringBuilder();
        sb.append("last deploy          : ").append(SteamControllerConfigDeployer.lastDeploySummary()).append('\n');
        Path dir;
        try {
            dir = SteamControllerConfigDeployer.locateControllerConfigDir();
        } catch (Throwable t) {
            return sb.append("controller_config    : lookup failed (").append(t.getMessage()).append(")\n").toString();
        }
        if (dir == null) {
            return sb.append("controller_config    : NOT FOUND — nothing could have been deployed\n").toString();
        }
        sb.append("controller_config    : ").append(dir).append('\n');
        appendMatching(sb, dir, "game_actions_*.vdf");
        appendMatching(sb, dir, "steampad_controller_*.vdf");
        return sb.toString();
    }

    private static void appendMatching(StringBuilder sb, Path dir, String glob) {
        boolean any = false;
        try (var stream = Files.newDirectoryStream(dir, glob)) {
            for (Path f : stream) {
                any = true;
                sb.append("  ").append(f.getFileName()).append("  ").append(sizeAndTime(f));
                if (f.getFileName().toString().startsWith("game_actions_")) {
                    sb.append("  revision=").append(revisionOf(f));
                }
                sb.append('\n');
            }
        } catch (Throwable t) {
            sb.append("  (listing ").append(glob).append(" failed: ").append(t.getMessage()).append(")\n");
        }
        if (!any) sb.append("  (no ").append(glob).append(" present)\n");
    }

    private static String revisionOf(Path manifest) {
        try {
            return SteamControllerConfigDeployer.manifestMajorRevision(Files.readAllBytes(manifest));
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String actionSets() {
        if (!SteamActionRegistry.isRegistered()) return "(not registered — Steam Input never initialized)";
        StringBuilder sb = new StringBuilder();
        for (var e : SteamActionRegistry.actionSetsByName().entrySet()) {
            long raw = SteamActionRegistry.rawValueOf(e.getValue());
            sb.append(String.format("%-22s: %s%n", e.getKey(), raw == 0L ? "0  (NOT RESOLVED)" : String.valueOf(raw)));
        }
        return sb.toString();
    }

    private static String actionHandles() {
        if (!SteamActionRegistry.isRegistered()) return "(not registered — Steam Input never initialized)";
        List<String> zero = new ArrayList<>();
        int total = 0, ok = 0;
        for (var e : SteamActionRegistry.digitalActionsByName().entrySet()) {
            total++;
            if (SteamActionRegistry.rawValueOf(e.getValue()) == 0L) zero.add(e.getKey()); else ok++;
        }
        for (var e : SteamActionRegistry.analogActionsByName().entrySet()) {
            total++;
            if (SteamActionRegistry.rawValueOf(e.getValue()) == 0L) zero.add(e.getKey()); else ok++;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("resolved             : ").append(ok).append('/').append(total).append('\n');
        if (zero.isEmpty()) {
            sb.append("all action handles resolved\n");
        } else {
            sb.append("handle 0 (Steam does not know these — they can never fire):\n");
            sb.append("  ").append(String.join(", ", zero)).append('\n');
        }
        return sb.toString();
    }

    private static String liveProbe() {
        if (!SteamBootstrap.isInputAvailable()) return "(Steam Input not available — nothing to probe)";
        var controllers = SteamInputManager.getConnectedControllers();
        if (controllers.isEmpty()) return "Steam Input reports 0 controllers";
        StringBuilder sb = new StringBuilder();
        sb.append("ControllerManager source: ").append(ControllerManager.activeSource())
          .append(ControllerManager.isSteamInputDegraded() ? "  (DEGRADED — sets not loaded)" : "").append('\n');
        // Which set the mod would have active right now. Every action set is probed below regardless,
        // but this says which one gameplay is actually running under at the moment of the dump.
        try {
            sb.append("Context right now       : ")
              .append(dev.steampad.input.SteamSlotDispatcher.currentContext(
                      net.minecraft.client.Minecraft.getInstance()))
              .append("  (a screen being open forces MENU/INVENTORY)\n");
        } catch (Throwable ignored) {}
        for (var ref : controllers) {
            sb.append(ref.displayName).append(" [").append(ref.handle).append("]\n");
            sb.append("    ").append(SteamInputManager.describeBoundActions(ref.handle)).append('\n');
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- helpers

    private static String fileLine(String label, Path p) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-22s: ", label));
        try {
            if (!Files.exists(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                sb.append("MISSING");
            } else if (Files.isSymbolicLink(p)) {
                Path target = Files.readSymbolicLink(p);
                sb.append("link -> ").append(target)
                  .append(Files.exists(p) ? "" : "  (BROKEN — target does not exist)");
            } else if (Files.isDirectory(p)) {
                sb.append("dir");
            } else {
                sb.append(sizeAndTime(p));
            }
        } catch (Throwable t) {
            sb.append("unreadable (").append(t.getMessage()).append(')');
        }
        return sb.append('\n').toString();
    }

    private static String sizeAndTime(Path p) {
        try {
            long size = Files.size(p);
            Instant when = Files.getLastModifiedTime(p).toInstant();
            return size + " bytes, " + STAMP.format(when);
        } catch (Throwable t) {
            return "(stat failed)";
        }
    }

    /** Small convenience used by callers that want the report as bytes (e.g. writing it to a file). */
    public static byte[] generateBytes() {
        return generate().getBytes(StandardCharsets.UTF_8);
    }
}
