package dev.steampad.emote;

import dev.steampad.util.LogUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * CLIENT side of emote multiplayer sync (see {@link EmoteNetwork} for the protocol). Sending is
 * gated on {@code canSend} — on a server without SteamPad the channel was never negotiated, so
 * nothing is transmitted and nothing errors (emotes stay local-only there, documented behavior).
 */
public final class EmoteNetworkClient {

    private EmoteNetworkClient() {}

    /** Registers the S2C receiver + disconnect cleanup. Called once from SteamPadClient. */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(EmoteNetwork.StartS2C.ID, (payload, context) -> {
            Minecraft mc = context.client();
            if (mc.level == null) return;
            if (payload.stop()) {
                EmoteAnimator.requestStop(payload.entityId());
                return;
            }
            Entity e = mc.level.getEntity(payload.entityId());
            if (e == null) return;   // not visible/loaded here — nothing to animate
            EmoteData data = EmoteLibrary.byId(payload.emoteId());
            if (data == null) {
                // The other player has an emote file we don't — v1 id-based sync limit (B077).
                LogUtil.debug("Remote emote '{}' not in local library — ignored.", payload.emoteId());
                return;
            }
            EmoteAnimator.playFor(payload.entityId(), data);
        });

        // World/server change: never leave stale playbacks pointing at dead entity ids.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> EmoteAnimator.clearAll());
    }

    /** Announces a local emote start (no-op on servers without SteamPad). */
    public static void sendStart(String emoteId) {
        if (ClientPlayNetworking.canSend(EmoteNetwork.StartC2S.ID)) {
            ClientPlayNetworking.send(new EmoteNetwork.StartC2S(emoteId, false));
        }
    }

    /** Announces a local emote stop (no-op on servers without SteamPad). */
    public static void sendStop() {
        if (ClientPlayNetworking.canSend(EmoteNetwork.StartC2S.ID)) {
            ClientPlayNetworking.send(new EmoteNetwork.StartC2S("stop", true));
        }
    }
}
