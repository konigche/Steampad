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
     * Tries to fire {@code keybindId} through MaLiLib's OWN hotkey registry.
     *
     * @return true if MaLiLib handled it; <b>false means the caller must fall back</b> — see below.
     *
     * <p><b>This always returns false today, and that is the honest answer.</b> MaLiLib hotkeys are
     * not {@code KeyMapping}s: masa's mods register them in MaLiLib's own {@code IKeybind} manager,
     * which never appears in {@code Minecraft.options.keyMappings}. So there is nothing here to
     * reach without calling MaLiLib's API reflectively, and this project does not write an
     * integration against an API it cannot verify against a real jar. That is why the radial's
     * "MaLiLib" action type did nothing at all before: the value it stored came from the ordinary
     * keybind picker (vanilla {@code KeyMapping}s) and was then handed to a method that only logged.
     *
     * <p>Until the real integration exists, the caller runs the value as a normal keybind instead of
     * silently doing nothing — which is exactly what the user observed as "it only works if a key is
     * assigned on the keyboard".
     */
    public static boolean triggerKeybind(String keybindId) {
        if (!AVAILABLE) {
            LogUtil.debug("MaLiLib keybind '{}' requested but MaLiLib is not installed — "
                    + "running it as an ordinary keybind instead.", keybindId);
            return false;
        }
        LogUtil.debug("MaLiLib is installed, but SteamPad has no verified binding to its hotkey "
                + "registry yet — running '{}' as an ordinary keybind instead.", keybindId);
        return false;
    }
}
