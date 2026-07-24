package dev.steampad.steam;

import com.codedisaster.steamworks.SteamController;
import com.codedisaster.steamworks.SteamControllerHandle;
import dev.steampad.util.LogUtil;

/**
 * Resolves Steam Input button glyphs for UI rendering via ISteamController.
 */
public final class SteamGlyphService {

    private SteamGlyphService() {}

    /**
     * Returns a localized glyph string for the first origin bound to the given digital action.
     * Returns null if Steam Input is unavailable, the action has no bindings, or glyph resolution fails.
     */
    public static String getGlyphPath(long controllerHandle,
                                      com.codedisaster.steamworks.SteamControllerDigitalActionHandle digitalActionHandle) {
        if (!SteamBootstrap.isInputAvailable()) return null;
        if (digitalActionHandle == null) return null;
        if (!SteamActionRegistry.isValidHandle(SteamActionRegistry.actionSetGameplay)) return null;

        SteamController raw = SteamInputManager.getRawController();
        if (raw == null) return null;

        try {
            SteamController.ActionOrigin[] origins =
                    new SteamController.ActionOrigin[SteamController.STEAM_CONTROLLER_MAX_ORIGINS];
            int count = raw.getDigitalActionOrigins(
                    new SteamControllerHandle(controllerHandle),
                    SteamActionRegistry.actionSetGameplay,
                    digitalActionHandle,
                    origins);

            if (count == 0 || origins[0] == null) return null;

            // getGlyphForActionOrigin returns a path string (e.g. "xbox_button_a")
            return raw.getGlyphForActionOrigin(origins[0]);
        } catch (Throwable t) {
            LogUtil.debug("Glyph lookup failed: {}", t.getMessage());
            return null;
        }
    }

    /**
     * Returns a human-readable origin string (e.g. "A Button") for the given action.
     */
    public static String getOriginString(long controllerHandle,
                                         com.codedisaster.steamworks.SteamControllerDigitalActionHandle digitalActionHandle) {
        if (!SteamBootstrap.isInputAvailable()) return null;
        if (digitalActionHandle == null) return null;
        if (!SteamActionRegistry.isValidHandle(SteamActionRegistry.actionSetGameplay)) return null;

        SteamController raw = SteamInputManager.getRawController();
        if (raw == null) return null;

        try {
            SteamController.ActionOrigin[] origins =
                    new SteamController.ActionOrigin[SteamController.STEAM_CONTROLLER_MAX_ORIGINS];
            int count = raw.getDigitalActionOrigins(
                    new SteamControllerHandle(controllerHandle),
                    SteamActionRegistry.actionSetGameplay,
                    digitalActionHandle,
                    origins);

            if (count == 0 || origins[0] == null) return null;
            return raw.getStringForActionOrigin(origins[0]);
        } catch (Throwable t) {
            LogUtil.debug("Origin string lookup failed: {}", t.getMessage());
            return null;
        }
    }
}
