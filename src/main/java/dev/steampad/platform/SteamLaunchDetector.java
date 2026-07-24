package dev.steampad.platform;

/**
 * Detects the real AppID Steam assigned to THIS session, from the environment variables Steam sets
 * on the child process when it launches a game (confirmed by Proton's own reliance on the same
 * variables for per-title compat settings — Valve does not document this env-var contract directly,
 * but third-party tooling built against real Steam behavior does).
 *
 * <p>Two shapes are checked, in priority order:
 * <ul>
 *   <li>{@code SteamAppId} — the plain AppID, set for real Steam-owned titles (and by some launcher
 *       wrappers directly).</li>
 *   <li>{@code SteamGameId} — set for "non-Steam game" shortcuts (which is how a Fabric/Prism install
 *       normally gets into Steam's library and Big Picture/Game Mode). Its value is a 64-bit id built
 *       as {@code ((crc32(exe+name) | 0x80000000) << 32) | 0x02000000} — the upper 32 bits ARE the
 *       pseudo-AppID Steam uses for that shortcut everywhere else (including the folder name for its
 *       Steam Input controller config), so an unsigned right-shift by 32 recovers it.</li>
 * </ul>
 *
 * <p>Used to replace the previously-hardcoded AppID 480 (Spacewar) fallback: when this process was
 * genuinely launched from a Steam library entry, using ITS real (pseudo-)AppID instead of a generic
 * sandbox id is what lets Steam associate our Steam Input action manifest with the actual running
 * session — this is the missing piece behind why Steam Input never fully "just works" like a native
 * Steam Input title (B040). 480 stays as the fallback when neither variable is present (dev builds,
 * or MC launched outside of any Steam library entry — e.g. plain Prism/Flatpak).
 */
public final class SteamLaunchDetector {

    private SteamLaunchDetector() {}

    /**
     * Best-known AppID Steam launched this process as, or 0 if neither signal is present/parseable.
     *
     * <p>Returns {@code long}, NOT {@code int} — non-Steam shortcut pseudo-AppIDs always have the
     * high bit set ({@code crc32 | 0x80000000}), so they ALWAYS exceed {@code Integer.MAX_VALUE}.
     * The original int-based version overflowed to a negative number for every shortcut launch
     * (confirmed in a real Game Mode log: {@code SteamAppId=4221053437 → detected=-73913859}), which
     * made every downstream {@code > 0} check fail — including {@code launchedFromSteam}, so a
     * genuine Steam launch was never recognized as one. That single overflow silently disabled the
     * whole launched-from-Steam path for the project's primary use case.
     */
    public static long detectAppId() {
        Long direct = parseUnsignedLong(System.getenv("SteamAppId"));
        if (direct != null && direct > 0 && direct <= 0xFFFFFFFFL) return direct;

        Long gameId = parseUnsignedLong(System.getenv("SteamGameId"));
        if (gameId != null && gameId != 0L) {
            long appIdPart = gameId >>> 32;
            if (appIdPart != 0L && appIdPart <= 0xFFFFFFFFL) return appIdPart;
        }
        return 0;
    }

    /** True when {@code appId} is a non-Steam-shortcut pseudo-AppID (crc32 | 0x80000000 — high bit
     *  set). These are what Steam uses in controller_config folder names for shortcuts, but they are
     *  NOT real store apps — SteamAPI.init() cannot validate against them (only real AppIDs like 480
     *  work there). Callers use this to decide which ID goes where. */
    public static boolean isShortcutPseudoAppId(long appId) {
        return appId >= 0x80000000L && appId <= 0xFFFFFFFFL;
    }

    /**
     * Raw values of every candidate variable, for the log — including ones not currently used
     * ({@code STEAM_GAME_ID}, the all-caps variant this project's own {@link GamescopeDetector}
     * already checks as a gamescope signal) so a future session has real data instead of a guess if
     * {@link #detectAppId()} comes back 0 on hardware where Steam Input still doesn't work right.
     */
    public static String rawDiagnostics() {
        return "SteamAppId=" + System.getenv("SteamAppId")
                + " SteamGameId=" + System.getenv("SteamGameId")
                + " STEAM_GAME_ID=" + System.getenv("STEAM_GAME_ID")
                + " SteamOverlayGameId=" + System.getenv("SteamOverlayGameId");
    }

    private static Long parseUnsignedLong(String s) {
        try { return (s == null || s.isBlank()) ? null : Long.parseUnsignedLong(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
