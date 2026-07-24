package dev.steampad.compat;

import dev.steampad.util.LogUtil;

/**
 * Optional integration with MaLiLib for advanced keybind support in the radial menu.
 * Only active if MaLiLib is present in the classpath.
 */
public final class MalilibCompat {

    private static final boolean AVAILABLE;

    static {
        boolean found = false;
        try {
            Class.forName("fi.dy.masa.malilib.MaLiLib");
            found = true;
            LogUtil.info("MaLiLib detected — advanced keybind support enabled in radial.");
        } catch (ClassNotFoundException e) {
            LogUtil.debug("MaLiLib not found — radial will use standard keybinds only.");
        }
        AVAILABLE = found;
    }

    private MalilibCompat() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Triggers a MaLiLib keybind by its ID string.
     * No-op if MaLiLib is not available.
     */
    public static void triggerKeybind(String keybindId) {
        if (!AVAILABLE) {
            LogUtil.debug("MaLiLib keybind '{}' requested but MaLiLib not installed.", keybindId);
            return;
        }
        // Actual MaLiLib integration would call its keybind manager here.
        // Left as a documented extension point to implement when MaLiLib's API is confirmed.
        LogUtil.debug("MaLiLib keybind trigger: {} (integration stub)", keybindId);
    }
}
