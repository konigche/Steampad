package dev.steampad.input;

import dev.steampad.config.ConfigManager;
import dev.steampad.service.ActiveControllerService;
import dev.steampad.service.ControllerManager;
import dev.steampad.util.LogUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;

/**
 * Gatekeeper for the pause menu while playing with a controller.
 *
 * <p>In MC 1.21.10 the vanilla focus→pause path lives in {@code GameRenderer.render}: when the
 * window is unfocused for over 500 ms and {@code pauseOnLostFocus} is on, it calls
 * {@code openGameMenu} — so clicking OUTSIDE the game window during gameplay kicked the player
 * into the pause menu with no way back on the gamepad (verified at bytecode level and confirmed
 * fixed on hardware). While a controller is playing, a pause reaching an UNFOCUSED Minecraft is
 * unwanted by definition — unless SteamPad itself initiated it (playing out of focus is
 * supported). This class encodes exactly that rule — the same idea as Controlify's
 * {@code pauseIfInactive} mixin — plus the Controlify-style out-of-focus cursor grab.
 */
public final class PauseGate {

    /** True only while a SteamPad code path is opening the pause menu on purpose. */
    private static boolean steamPadInitiated = false;
    /** The first suppression logs the origin stack so the real culprit can be identified. */
    private static boolean originLogged = false;
    /** Vanilla retries the focus-pause EVERY FRAME while unfocused — rate-limit the log. */
    private static long lastSuppressLogMs = 0L;

    private PauseGate() {}

    /** Every SteamPad path that opens the pause menu goes through here — never suppressed. */
    public static void openPauseMenu(MinecraftClient mc) {
        steamPadInitiated = true;
        try {
            mc.setScreen(new GameMenuScreen(true));
        } finally {
            steamPadInitiated = false;
        }
    }

    /**
     * True if this {@code setScreen(GameMenuScreen)} must be cancelled: gameplay is running, the
     * window is NOT focused, a fallback controller is driving the game, and the open was NOT
     * initiated by SteamPad.
     */
    public static boolean shouldSuppress(Screen screen) {
        if (!(screen instanceof GameMenuScreen)) return false;
        if (steamPadInitiated) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return false;
        // A screen is already open → this open CANNOT be the spurious focus-pause: vanilla's
        // openGameMenu starts with "if (currentScreen != null) return;" (verified in the 1.21.10
        // bytecode), so from a child screen the only way a GameMenuScreen arrives here is a
        // legitimate parent-restore (a settings screen's close() returning to the pause menu).
        // Suppressing those jammed the whole B-close chain while the window was unfocused: the
        // hop back to the pause menu got cancelled, currentScreen stayed on the child, and the
        // player could never reach gameplay again without refocusing with the physical mouse.
        if (mc.currentScreen != null) return false;
        if (mc.isWindowFocused()) return false;
        long handle = ActiveControllerService.getActiveHandle();
        if (handle == 0L || !ControllerManager.isFallbackHandle(handle)) return false;
        if (!originLogged) {
            originLogged = true;
            LogUtil.warn("Suppressed a spurious pause-menu open while the window is unfocused. Origin:",
                    new Throwable("pause-menu origin trace"));
        } else {
            long now = System.currentTimeMillis();
            if (now - lastSuppressLogMs > 10_000L) {   // once per 10 s, not once per frame
                lastSuppressLogMs = now;
                LogUtil.info("Suppressing focus-pause while unfocused (controller active).");
            }
        }
        return true;
    }

    /**
     * Controlify-style out-of-focus grab: lets {@code Mouse.lockCursor} proceed while the window is
     * unfocused, so closing a menu with the gamepad in the background re-locks the cursor instead of
     * leaving it permanently free. Gated on the "Out of Focus Input" global option.
     */
    public static boolean allowLockCursorUnfocused() {
        if (!ConfigManager.getGlobal().outOfFocusInput) return false;
        long handle = ActiveControllerService.getActiveHandle();
        return handle != 0L && ControllerManager.isFallbackHandle(handle);
    }
}
