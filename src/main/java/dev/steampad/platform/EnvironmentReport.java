package dev.steampad.platform;

import dev.steampad.util.LogUtil;

public final class EnvironmentReport {

    public final boolean isLinux;
    public final boolean isWindows;
    public final boolean isMac;
    public final boolean isGamescope;
    public final boolean isSteamDeck;
    public final boolean isSteamAvailable;
    public final String os;
    public final String gamescopeDisplay;
    public final String steamDeckModel;
    /** AppID Steam launched this process as (SteamLaunchDetector), 0 = not detected. Long, not int:
     *  non-Steam shortcut pseudo-AppIDs always exceed Integer.MAX_VALUE (see SteamLaunchDetector). */
    public final long steamLaunchAppId;

    private EnvironmentReport(Builder b) {
        this.isLinux = b.isLinux;
        this.isWindows = b.isWindows;
        this.isMac = b.isMac;
        this.isGamescope = b.isGamescope;
        this.isSteamDeck = b.isSteamDeck;
        this.isSteamAvailable = b.isSteamAvailable;
        this.os = b.os;
        this.gamescopeDisplay = b.gamescopeDisplay;
        this.steamDeckModel = b.steamDeckModel;
        this.steamLaunchAppId = b.steamLaunchAppId;
    }

    public static EnvironmentReport generate() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        boolean isLinux = osName.contains("linux") || osName.contains("nix") || osName.contains("nux");
        boolean isWindows = osName.contains("windows");
        boolean isMac = osName.contains("mac") || osName.contains("darwin");

        String gamescopeDisplay = System.getenv("GAMESCOPE_WAYLAND_DISPLAY");
        boolean isGamescope = gamescopeDisplay != null && !gamescopeDisplay.isEmpty();

        String steamDeckModel = System.getenv("SteamDeck");
        if (steamDeckModel == null) steamDeckModel = System.getenv("STEAM_DECK");
        boolean isSteamDeck = "1".equals(steamDeckModel) || SteamDeckDetector.detect();

        long launchAppId = SteamLaunchDetector.detectAppId();

        // isSteamAvailable is set after Steam API init — default false
        EnvironmentReport report = new Builder()
                .linux(isLinux).windows(isWindows).mac(isMac)
                .gamescope(isGamescope, gamescopeDisplay)
                .steamDeck(isSteamDeck, steamDeckModel)
                .os(osName)
                .steamLaunchAppId(launchAppId)
                .build();

        LogUtil.info("Environment: OS={}, Linux={}, Gamescope={}, SteamDeck={}",
                osName, isLinux, isGamescope, isSteamDeck);
        LogUtil.info("[SteamPad] Steam launch AppID detection: detected={} ({})",
                launchAppId, SteamLaunchDetector.rawDiagnostics());

        return report;
    }

    public String toDebugString() {
        return String.format(
            "OS: %s\nLinux: %b | Windows: %b | Mac: %b\nGamescope: %b (display=%s)\n" +
            "SteamDeck: %b (model=%s)\nSteam Launch AppID: %d (0 = not detected, 480 fallback used)\n" +
            "Steam Available: %b",
            os, isLinux, isWindows, isMac, isGamescope, gamescopeDisplay,
            isSteamDeck, steamDeckModel, steamLaunchAppId, isSteamAvailable
        );
    }

    public EnvironmentReport withSteamAvailable(boolean available) {
        return new Builder()
                .linux(isLinux).windows(isWindows).mac(isMac)
                .gamescope(isGamescope, gamescopeDisplay)
                .steamDeck(isSteamDeck, steamDeckModel)
                .os(os)
                .steamLaunchAppId(steamLaunchAppId)
                .steam(available)
                .build();
    }

    static class Builder {
        boolean isLinux, isWindows, isMac, isGamescope, isSteamDeck, isSteamAvailable;
        String os = "", gamescopeDisplay = "", steamDeckModel = "";
        long steamLaunchAppId = 0;

        Builder linux(boolean v)   { isLinux = v; return this; }
        Builder windows(boolean v) { isWindows = v; return this; }
        Builder mac(boolean v)     { isMac = v; return this; }
        Builder gamescope(boolean v, String display) { isGamescope = v; gamescopeDisplay = display != null ? display : ""; return this; }
        Builder steamDeck(boolean v, String model)   { isSteamDeck = v; steamDeckModel = model != null ? model : ""; return this; }
        Builder os(String v)       { os = v; return this; }
        Builder steamLaunchAppId(long v) { steamLaunchAppId = v; return this; }
        Builder steam(boolean v)   { isSteamAvailable = v; return this; }
        EnvironmentReport build()  { return new EnvironmentReport(this); }
    }
}
