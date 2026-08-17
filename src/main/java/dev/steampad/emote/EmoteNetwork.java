package dev.steampad.emote;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * COMMON side of emote multiplayer sync (safe to classload on a dedicated server — no client
 * classes referenced; the client half lives in {@link EmoteNetworkClient}).
 *
 * <p>Protocol (SteamPad's own, v1): when a player starts/stops an emote, the client sends
 * {@link StartC2S} to the server; a server with SteamPad installed relays it as {@link StartS2C}
 * to every player currently tracking (seeing) the sender. Receivers look the emote up in their OWN
 * library by id — the animation data itself is never transmitted (v1 keeps the wire format tiny and
 * removes the remote-data validation attack surface; both sides ship the same bundled pack, so the
 * 12 built-ins always sync — B077 records "full-data sync + Emotecraft protocol interop" as the
 * known follow-up).
 *
 * <p>On servers WITHOUT SteamPad nothing is sent at all ({@code canSend} gate client-side) and
 * nothing breaks — emotes simply stay visible only to the local player, mirroring how this mod
 * ecosystem behaves without a server component.
 */
public final class EmoteNetwork {

    /** Client → server: "I started (or stopped) this emote". */
    public record StartC2S(String emoteId, boolean stop) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StartC2S> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("steampad", "emote_c2s"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StartC2S> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, StartC2S::emoteId,
                ByteBufCodecs.BOOL, StartC2S::stop,
                StartC2S::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** Server → clients tracking the sender: "entity X started (or stopped) this emote". */
    public record StartS2C(int entityId, String emoteId, boolean stop) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StartS2C> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("steampad", "emote_s2c"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StartS2C> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, StartS2C::entityId,
                ByteBufCodecs.STRING_UTF8, StartS2C::emoteId,
                ByteBufCodecs.BOOL, StartS2C::stop,
                StartS2C::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** Longest emote id accepted from the wire (defensive bound; ids are filename stems). */
    private static final int MAX_ID_LENGTH = 128;

    private EmoteNetwork() {}

    /** Registers payload types + the server relay. Called once from SteamPadMod (common init). */
    public static void registerCommon() {
        PayloadTypeRegistry.playC2S().register(StartC2S.ID, StartC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(StartS2C.ID, StartS2C.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(StartC2S.ID, (payload, context) -> {
            String id = payload.emoteId();
            if (id == null || id.isEmpty() || id.length() > MAX_ID_LENGTH) return;   // hostile/garbage
            ServerPlayer sender = context.player();
            StartS2C out = new StartS2C(sender.getId(), id, payload.stop());
            // tracking(entity) = every player that currently sees the sender (sender excluded).
            for (ServerPlayer viewer : PlayerLookup.tracking(sender)) {
                ServerPlayNetworking.send(viewer, out);
            }
        });
    }
}
