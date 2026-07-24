package dev.steampad.steam;

import dev.steampad.config.ConfigManager;
import dev.steampad.platform.LinuxRuntimeInspector;
import dev.steampad.util.LogUtil;

import java.io.IOException;
import java.io.InputStream;
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
 * manual import instructions) is copied verbatim under each target filename.
 */
public final class SteamControllerConfigDeployer {

    private static final String RESOURCE_PATH = "/assets/steampad/steam_input/game_actions_template.vdf";

    /** Content fingerprints that identify a game_actions_*.vdf as OURS (both VDF generations used
     *  these set/action names) — cleanup only ever deletes files containing one of them, so another
     *  project's manifest under the same AppID can never be touched. */
    private static final String[] OUR_FINGERPRINTS = {"SteamPad_", "steampad_slot_1"};

    private SteamControllerConfigDeployer() {}

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
                LogUtil.info("[SteamPad] deployIgaManifest disabled (default) — not deploying "
                        + "game_actions_*.vdf. Steam Slots work via the F13-F22 path instead.");
                return;
            }
            if (!ConfigManager.getGlobal().autoWriteControllerConfig) {
                LogUtil.info("[SteamPad] autoWriteControllerConfig disabled — not deploying game_actions_*.vdf.");
                return;
            }
            Path configDir = findControllerConfigDir();
            if (configDir == null) {
                LogUtil.info("[SteamPad] Could not locate Steam's controller_config folder — skipping VDF "
                        + "auto-deploy (manual import still works, see steampad_steam_input/README).");
                return;
            }
            byte[] content = readTemplate();
            if (content == null) return;

            writeOne(configDir, 480, content);
            if (effectiveAppId > 0 && effectiveAppId != 480) {
                writeOne(configDir, effectiveAppId, content);
            }
        } catch (Throwable t) {
            LogUtil.warn("[SteamPad] SteamControllerConfigDeployer.deploy failed: {}", t.getMessage());
        }
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
