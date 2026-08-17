package dev.steampad.steam;

import dev.steampad.config.ConfigManager;
import dev.steampad.platform.LinuxRuntimeInspector;
import dev.steampad.util.LogUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Drops SteamPad's Steam Input action manifest ({@code game_actions_<appid>.vdf}) directly into
 * Steam's {@code controller_config} folder — the location Valve documents for developer-supplied
 * in-game-actions (IGA) files, picked up automatically with no "import" step in Steam's UI. This is
 * the concrete fix for B033's documented pain point ("requires re-importing the VDF in Steam").
 *
 * <p>Always (re)writes the AppID 480 (Spacewar) copy — the long-standing dev/manual-setup target, kept
 * working exactly as documented — AND, when {@link dev.steampad.platform.SteamLaunchDetector} found a
 * real Steam launch AppID for this session, ALSO writes that AppID's own copy, so a genuine "launched
 * from Steam" session gets a correctly-matched manifest with zero manual steps (see B040).
 *
 * <p>The manifest content itself has no AppID inside it — only the filename ties it to a specific game
 * — so a single bundled resource ({@code assets/steampad/steam_input/game_actions_template.vdf}, kept
 * byte-identical to the loose {@code steampad_steam_input/game_actions_480.vdf} in the repo used for
 * manual import instructions) is rewritten once (see {@link #buildManifestContent}, D117) and that
 * SAME rewritten content is written verbatim under each target filename — the rewrite only
 * substitutes the default-config paths, which don't depend on AppID at all (one shared
 * {@code controller_config} folder serves every AppID's manifest).
 */
public final class SteamControllerConfigDeployer {

    private static final String RESOURCE_PATH = "/assets/steampad/steam_input/game_actions_template.vdf";

    /** Content fingerprints that identify a game_actions_*.vdf as OURS (both VDF generations used
     *  these set/action names) — cleanup only ever deletes files containing one of them, so another
     *  project's manifest under the same AppID can never be touched. */
    private static final String[] OUR_FINGERPRINTS = {"SteamPad_", "steampad_slot_1"};

    /** What the last {@link #deploy} call actually did, verbatim, for the debug dump. */
    private static volatile String lastDeploySummary = "not run yet this session";

    private SteamControllerConfigDeployer() {}

    /** Outcome of the most recent deploy attempt — surfaced in the Steam Input debug section. */
    public static String lastDeploySummary() {
        return lastDeploySummary;
    }

    /** Steam's {@code controller_config} folder if it already exists, else null. Never creates it —
     *  safe for diagnostics to call at any time. */
    public static Path locateControllerConfigDir() {
        return findControllerConfigDir(false);
    }

    /** Best-effort; never throws. No-op if disabled or Steam's install directory can't be located.
     *  Long AppID: shortcut pseudo-AppIDs exceed Integer.MAX_VALUE (see SteamLaunchDetector) — and
     *  the pseudo-ID IS the correct filename here (it's what Steam uses in controller_config for a
     *  shortcut), unlike steam_appid.txt which needs a real SDK-valid AppID. */
    public static void deploy(long effectiveAppId) {
        try {
            // OPT-IN since v0.44.0 (B076): once a game_actions_<appid>.vdf exists for a title, Steam
            // treats it as a native Steam Input API game — in Game Mode it claims the physical pad
            // and STOPS emitting the virtual gamepad that feeds SDL3/GLFW. That silently killed ALL
            // controller detection for the user's shortcut (v0.43.0 hardware session: SDL3 up, GLFW
            // up, 0 devices — both backends code-audited clean; the OS simply exposed no pad). The
            // hybrid architecture (D032: SDL3 drives gameplay, Steam Input only adds paddles) is
            // fundamentally incompatible with an ALWAYS-deployed IGA manifest, so the manifest is
            // now experimental and off by default; the F13-F22 slot path (D034) needs none of this.
            if (!ConfigManager.getGlobal().deployIgaManifest) {
                lastDeploySummary = "skipped — deployIgaManifest is OFF";
                LogUtil.info("[SteamPad] deployIgaManifest disabled (default) — not deploying "
                        + "game_actions_*.vdf. Steam Slots work via the F13-F22 path instead.");
                return;
            }
            if (!ConfigManager.getGlobal().autoWriteControllerConfig) {
                lastDeploySummary = "skipped — autoWriteControllerConfig is OFF";
                LogUtil.info("[SteamPad] autoWriteControllerConfig disabled — not deploying game_actions_*.vdf.");
                return;
            }
            Path configDir = findControllerConfigDir();
            if (configDir == null) {
                lastDeploySummary = "FAILED — Steam's controller_config folder could not be located";
                LogUtil.info("[SteamPad] Could not locate Steam's controller_config folder — skipping VDF "
                        + "auto-deploy (manual import still works, see steampad_steam_input/README).");
                return;
            }
            // The manifest alone binds NOTHING — Steam answers a manifest with no configuration by
            // generating an empty template, which is why every button read as unassigned and the
            // sticks were dead (D117). These are the actual default bindings the manifest's
            // "configurations" section points at, one per controller family. Deployed first so the
            // files the manifest is about to reference already exist on disk.
            deployDefaultConfigs(configDir);

            byte[] content = buildManifestContent(configDir);
            if (content == null) {
                lastDeploySummary = "FAILED — bundled manifest resource could not be read";
                return;
            }

            writeOne(configDir, 480, content);
            if (effectiveAppId > 0 && effectiveAppId != 480) {
                writeOne(configDir, effectiveAppId, content);
            }
            lastDeploySummary = "deployed to " + configDir + " for AppIDs 480"
                    + (effectiveAppId > 0 && effectiveAppId != 480 ? " + " + effectiveAppId : "")
                    + " (manifest revision " + manifestMajorRevision(content) + ")";
        } catch (Throwable t) {
            lastDeploySummary = "FAILED — " + t;
            LogUtil.warn("[SteamPad] SteamControllerConfigDeployer.deploy failed: {}", t.getMessage());
        }
    }

    /** Reads the {@code major_revision} out of manifest bytes; "?" when absent/unparseable. Steam only
     *  rebuilds a title's layout when this number changes, so it is worth showing in diagnostics. */
    static String manifestMajorRevision(byte[] manifest) {
        try {
            var m = java.util.regex.Pattern
                    .compile("\"major_revision\"\\s*\"(\\d+)\"")
                    .matcher(new String(manifest, StandardCharsets.UTF_8));
            return m.find() ? m.group(1) : "?";
        } catch (Throwable t) {
            return "?";
        }
    }

    /**
     * Writes one default {@code controller_mappings} configuration per controller family, under the
     * filenames the manifest's {@code configurations} section references. Generated (not a checked-in
     * resource) so the Steam layout can never drift from the mod's own button defaults — see
     * {@link SteamDefaultConfigGenerator}.
     */
    private static void deployDefaultConfigs(Path dir) {
        for (String type : SteamDefaultConfigGenerator.CONTROLLER_TYPES) {
            String name = SteamDefaultConfigGenerator.fileNameFor(type);
            try {
                byte[] body = SteamDefaultConfigGenerator.generate(type)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                Path file = dir.resolve(name);
                Path tmp = dir.resolve(name + ".tmp");
                Files.write(tmp, body);
                try {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
                LogUtil.info("[SteamPad] Deployed default Steam Input bindings for {} to {}", type, file);
            } catch (Throwable t) {
                LogUtil.warn("[SteamPad] Could not deploy default config {}: {}", name, t.getMessage());
            }
        }
    }

    /**
     * Builds the manifest content with the bundled template's relative {@code "path"} placeholders
     * ({@code "steampad_controller_generic.vdf"}, etc.) rewritten to ABSOLUTE paths pointing at the
     * files {@link #deployDefaultConfigs} just wrote.
     *
     * <p><b>Why (v0.77.0, D117 follow-up).</b> Valve documents the {@code configurations} block's
     * {@code path} as relative to the game's own install directory — a concept a non-Steam shortcut
     * (this mod's actual deployment target) doesn't have. Rather than guess what a relative path
     * resolves against with no install directory to be relative TO, this sidesteps the question
     * entirely: an absolute path has nothing left to resolve. The location it points at is not a
     * guess either — it's exactly where {@link #deployDefaultConfigs} already writes, using the same
     * {@code controller_config} directory this class has reliably located since the manifest itself
     * was first deployed (proven working, B076).
     *
     * <p>Forward slashes throughout, including on Windows: VDF is a plain KeyValues text format with
     * no native path type, and a literal backslash inside a quoted string is this format's escape
     * character — writing a raw Windows path would corrupt the file the moment a component contained
     * a backslash escape sequence by coincidence. Every OS's file APIs, including Win32, accept
     * forward slashes in paths, so this side-steps escaping altogether rather than hand-rolling a
     * VDF-string escaper for one call site.
     */
    private static byte[] buildManifestContent(Path configDir) {
        byte[] raw = readTemplate();
        if (raw == null) return null;
        return substitutePaths(new String(raw, StandardCharsets.UTF_8), configDir)
                .getBytes(StandardCharsets.UTF_8);
    }

    /** Pure string transform, split out from {@link #buildManifestContent} so it's unit-testable
     *  without a real Steam install or filesystem writes — see {@code SteamInputManifestTest}. */
    static String substitutePaths(String template, Path configDir) {
        String result = template;
        for (String type : SteamDefaultConfigGenerator.CONTROLLER_TYPES) {
            String relName = SteamDefaultConfigGenerator.fileNameFor(type);
            String absPath = configDir.resolve(relName).toAbsolutePath().normalize()
                    .toString().replace('\\', '/');
            result = result.replace("\"" + relName + "\"", "\"" + absPath + "\"");
        }
        return result;
    }

    private static void writeOne(Path dir, long appId, byte[] content) {
        try {
            Path file = dir.resolve("game_actions_" + appId + ".vdf");
            Path tmp = dir.resolve("game_actions_" + appId + ".vdf.tmp");
            Files.write(tmp, content);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            LogUtil.info("[SteamPad] Deployed Steam Input action manifest for AppID {} to {}", appId, file);
        } catch (Throwable t) {
            LogUtil.warn("[SteamPad] Could not deploy game_actions_{}.vdf to {}: {}", appId, dir, t.getMessage());
        }
    }

    /**
     * Deletes every {@code game_actions_*.vdf} in Steam's controller_config that SteamPad itself
     * deployed (identified by content fingerprint — see {@link #OUR_FINGERPRINTS}; files from other
     * projects are never touched). Called at every startup while {@code deployIgaManifest} is off,
     * so simply updating the mod REVERTS the Game-Mode virtual-gamepad suppression (B076): after the
     * next Steam restart the shortcut is a plain non-IGA title again and Steam resumes emitting the
     * virtual gamepad that SDL3/GLFW consume. Idempotent, best-effort, never throws; cost is one
     * directory listing at startup (zero when the folder doesn't exist).
     */
    public static void cleanupOurManifests() {
        try {
            Path configDir = findExistingControllerConfigDir();
            if (configDir == null) return;
            int removed = 0;
            try (var stream = Files.newDirectoryStream(configDir, "game_actions_*.vdf")) {
                for (Path file : stream) {
                    if (!isOurs(file)) continue;
                    try {
                        Files.delete(file);
                        removed++;
                        LogUtil.info("[SteamPad] Removed SteamPad IGA manifest: {} (deployIgaManifest is off).", file);
                    } catch (Throwable t) {
                        LogUtil.warn("[SteamPad] Could not remove {}: {}", file, t.getMessage());
                    }
                }
            }
            // The generated default configs go with it — leaving them behind would strand bindings
            // for a manifest that no longer exists. Named by our own prefix, so nothing else is hit.
            try (var stream = Files.newDirectoryStream(configDir, "steampad_controller_*.vdf")) {
                for (Path file : stream) {
                    try {
                        Files.delete(file);
                        removed++;
                        LogUtil.info("[SteamPad] Removed SteamPad default config: {}", file);
                    } catch (Throwable t) {
                        LogUtil.warn("[SteamPad] Could not remove {}: {}", file, t.getMessage());
                    }
                }
            }
            if (removed > 0) {
                LogUtil.warn("[SteamPad] {} SteamPad manifest(s) removed from Steam's controller_config. "
                        + "RESTART STEAM once so it stops treating the shortcut as a Steam-Input-API game "
                        + "and re-creates the virtual gamepad; if the layout still shows an empty action "
                        + "template, pick a normal 'Gamepad' template in the controller settings.", removed);
            }
        } catch (Throwable t) {
            LogUtil.warn("[SteamPad] IGA manifest cleanup failed (harmless): {}", t.getMessage());
        }
    }

    /** True when the file's content carries a SteamPad fingerprint. Unreadable → false (never delete). */
    private static boolean isOurs(Path file) {
        try {
            String content = Files.readString(file);
            for (String fp : OUR_FINGERPRINTS) {
                if (content.contains(fp)) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Like {@link #findControllerConfigDir()} but NEVER creates the folder — cleanup must not
     *  side-effect a Steam install that has no controller_config at all. */
    private static Path findExistingControllerConfigDir() {
        return findControllerConfigDir(false);
    }

    private static byte[] readTemplate() {
        try (InputStream in = SteamControllerConfigDeployer.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                LogUtil.warn("[SteamPad] Bundled Steam Input manifest resource not found at {}", RESOURCE_PATH);
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            LogUtil.warn("[SteamPad] Could not read bundled Steam Input manifest: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Best-effort, cross-platform guess at Steam's {@code controller_config} folder. Steam creates
     * that subfolder lazily (only once a game has ever shipped one), so a directory that merely looks
     * like a real Steam install (has {@code steamapps/}, {@code steam.sh}, etc.) is also accepted and
     * the subfolder created ourselves.
     */
    private static Path findControllerConfigDir() {
        return findControllerConfigDir(true);
    }

    private static Path findControllerConfigDir(boolean createIfMissing) {
        String home = System.getProperty("user.home", "");
        List<Path> candidates = new ArrayList<>();

        // Proton/compat tooling sets this to Steam's own install path when present — the most direct
        // signal available, sidesteps distro-specific layout guessing entirely when we have it.
        String compatInstall = System.getenv("STEAM_COMPAT_CLIENT_INSTALL_PATH");
        if (compatInstall != null && !compatInstall.isBlank()) candidates.add(Path.of(compatInstall));

        if (LinuxRuntimeInspector.isLinux()) {
            candidates.add(Path.of(home, ".local", "share", "Steam"));
            candidates.add(Path.of(home, ".steam", "steam"));
            candidates.add(Path.of(home, ".steam", "root"));
            // Flatpak Steam (still relevant for users who haven't migrated to native — session 19).
            candidates.add(Path.of(home, ".var", "app", "com.valvesoftware.Steam", ".local", "share", "Steam"));
        } else if (isWindows()) {
            candidates.add(Path.of("C:\\Program Files (x86)\\Steam"));
            candidates.add(Path.of("C:\\Program Files\\Steam"));
        } else if (isMac()) {
            candidates.add(Path.of(home, "Library", "Application Support", "Steam"));
        }

        for (Path base : candidates) {
            Path cfg = base.resolve("controller_config");
            if (Files.isDirectory(cfg)) return cfg;
            if (createIfMissing && Files.isDirectory(base) && looksLikeSteamInstall(base)) {
                try { Files.createDirectories(cfg); return cfg; } catch (IOException ignored) {}
            }
        }
        return null;
    }

    private static boolean looksLikeSteamInstall(Path base) {
        return Files.exists(base.resolve("steamapps")) || Files.exists(base.resolve("steam.sh"))
                || Files.exists(base.resolve("steam.exe")) || Files.exists(base.resolve("Steam.AppImage"));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }
}
