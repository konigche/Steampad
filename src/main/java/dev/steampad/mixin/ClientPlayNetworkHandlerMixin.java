package dev.steampad.mixin;

import dev.steampad.haptics.HapticsController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Haptics hooks: server packets that carry information no client-side poll can reconstruct on its
 * own — an explosion's true center/radius, and the {@code DamageSource} (type + attacker) behind an
 * entity taking damage. Signatures verified against the mapped 1.21.10 jar via javap:
 * {@code onExplosion(ExplosionS2CPacket)}, {@code onEntityDamage(EntityDamageS2CPacket)}. Full
 * descriptors used deliberately (this project has a history of mixin crashes from descriptor
 * mismatches — see D018).
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onExplosion(Lnet/minecraft/network/packet/s2c/play/ExplosionS2CPacket;)V", at = @At("TAIL"))
    private void steampad$onExplosion(ExplosionS2CPacket packet, CallbackInfo ci) {
        HapticsController.onExplosion(packet.center(), packet.radius(), packet.playerKnockback().isPresent());
    }

    @Inject(method = "onEntityDamage(Lnet/minecraft/network/packet/s2c/play/EntityDamageS2CPacket;)V", at = @At("TAIL"))
    private void steampad$onEntityDamage(EntityDamageS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (packet.entityId() == mc.player.getId()) {
            HapticsController.onPlayerDamaged(packet.createDamageSource(mc.world));
            return;
        }
        // This same packet fires for ANY nearby entity taking damage (how vanilla shows hurt
        // animations on mobs/other players) — used to detect the player dealing damage to a boss,
        // melee OR ranged (arrows), so the mystery proximity ping can stop (feedback: "no se detiene
        // la vibracion de los jefes al darle el primer golpe... esto incluye flechas, lo mejor es al
        // hacerle daño"). See HapticsController.onEntityDamaged.
        net.minecraft.entity.Entity target = mc.world.getEntityById(packet.entityId());
        if (target != null) {
            HapticsController.onEntityDamaged(target, packet.createDamageSource(mc.world));
        }
    }
}
