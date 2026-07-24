package dev.steampad.platform;

public final class LinuxRuntimeInspector {

    private LinuxRuntimeInspector() {}

    public static boolean isLinux() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("linux") || os.contains("nix") || os.contains("nux");
    }

    public static String getXdgSessionType() {
        return System.getenv().getOrDefault("XDG_SESSION_TYPE", "unknown");
    }

    public static String getWaylandDisplay() {
        return System.getenv().getOrDefault("WAYLAND_DISPLAY", "");
    }

    public static String getDisplay() {
        return System.getenv().getOrDefault("DISPLAY", "");
    }

    public static boolean isWayland() {
        return !getWaylandDisplay().isEmpty() || "wayland".equalsIgnoreCase(getXdgSessionType());
    }

    public static boolean isX11() {
        return !getDisplay().isEmpty() && !isWayland();
    }

    public static String buildReport() {
        return String.format("Linux=%b | Wayland=%b | X11=%b | XDG_SESSION=%s | DISPLAY=%s | WAYLAND_DISPLAY=%s",
                isLinux(), isWayland(), isX11(),
                getXdgSessionType(), getDisplay(), getWaylandDisplay());
    }
}
