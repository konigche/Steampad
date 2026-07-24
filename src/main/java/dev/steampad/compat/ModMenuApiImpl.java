package dev.steampad.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.steampad.screen.ControllerSelectScreen;

/**
 * Registers SteamPad's config screen with Mod Menu when it is present.
 * This class is only loaded by Fabric when modmenu mod is active — safe to reference
 * compile-only Mod Menu classes here without causing NoClassDefFoundError at runtime.
 */
public final class ModMenuApiImpl implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ControllerSelectScreen::new;
    }
}
