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

    /**
     * How this process is contained, which decides what advice about device access is even applicable.
     *
     * <p>This exists because the mod used to print "run `flatpak override --device=all
     * org.prismlauncher.PrismLauncher`" unconditionally whenever a pad came up without HIDAPI — advice
     * that is simply wrong, and actively misleading, on a NATIVE PrismLauncher (a real user report:
     * their install lives in {@code ~/.local/share/PrismLauncher}, which is the native path; the Flatpak
     * one would be {@code ~/.var/app/org.prismlauncher.PrismLauncher}). Detection markers are the
     * documented ones: {@code /.flatpak-info} for Flatpak and {@code /run/pressure-vessel} for Steam's
     * Linux Runtime container — the same two SDL itself probes for.
     */
    public enum Container { NATIVE, FLATPAK, PRESSURE_VESSEL, OTHER_CONTAINER }

    public static Container container() {
        try {
            if (java.nio.file.Files.exists(java.nio.file.Path.of("/.flatpak-info"))) return Container.FLATPAK;
            if (java.nio.file.Files.exists(java.nio.file.Path.of("/run/pressure-vessel"))) return Container.PRESSURE_VESSEL;
        } catch (Throwable ignored) {
            // Unreadable root — fall through to the env check, then NATIVE.
        }
        String c = System.getenv("container");
        if (c != null && !c.isBlank()) {
            return "flatpak".equalsIgnoreCase(c) ? Container.FLATPAK : Container.OTHER_CONTAINER;
        }
        return Container.NATIVE;
    }

    /** Human-readable container name for logs/diagnostics. */
    public static String containerName() {
        switch (container()) {
            case FLATPAK: return "Flatpak";
            case PRESSURE_VESSEL: return "Steam Linux Runtime (pressure-vessel)";
            case OTHER_CONTAINER: return "container (" + System.getenv("container") + ")";
            default: return "native";
        }
    }

    public static String buildReport() {
        return String.format("Linux=%b | Wayland=%b | X11=%b | XDG_SESSION=%s | DISPLAY=%s | WAYLAND_DISPLAY=%s | runtime=%s",
                isLinux(), isWayland(), isX11(),
                getXdgSessionType(), getDisplay(), getWaylandDisplay(), containerName());
    }
}
