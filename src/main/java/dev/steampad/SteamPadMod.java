package dev.steampad;

import dev.steampad.util.LogUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * SteamPad mod server/common entry point.
 * Minimal: most initialization is client-side only.
 */
public class SteamPadMod implements ModInitializer {

    public static final String MOD_ID = "steampad";

    @Override
    public void onInitialize() {
        // Read the version from the Fabric metadata (the JAR manifest's Implementation-Version is not
        // set, which is why this logged "null" before).
        String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        LogUtil.info("SteamPad mod loaded. Version: " + version);

        // Emote multiplayer sync (FASE 63): payload types + the server-side relay. Common init so a
        // dedicated server with SteamPad relays emotes between clients; pure no-op otherwise.
        dev.steampad.emote.EmoteNetwork.registerCommon();
    }
}
