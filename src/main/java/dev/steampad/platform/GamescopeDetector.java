package dev.steampad.platform;

public final class GamescopeDetector {

    private GamescopeDetector() {}

    public static boolean detect() {
        String display = System.getenv("GAMESCOPE_WAYLAND_DISPLAY");
        if (display != null && !display.isEmpty()) return true;

        String gamemodeStr = System.getenv("STEAM_GAME_ID");
        String inGameScope = System.getenv("GAMESCOPE_EMBEDDED");
        return inGameScope != null || gamemodeStr != null;
    }

    public static String getDisplay() {
        String d = System.getenv("GAMESCOPE_WAYLAND_DISPLAY");
        return d != null ? d : "";
    }
}
