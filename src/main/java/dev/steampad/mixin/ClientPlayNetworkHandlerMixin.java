package dev.steampad.mixin;

import dev.steampad.haptics.HapticsController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
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
@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = "handleExplosion(Lnet/minecraft/network/protocol/game/ClientboundExplodePacket;)V", at = @At("TAIL"))
    private void steampad$onExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
        // 1.21.2 turned this packet into a record and reshaped it: loose x/y/z + power + knockbackX/Y/Z
        // became center() (a Vec3), radius() and an Optional playerKnockback(). On the old shape a
        // non-zero knockback vector is the equivalent of "present".
        //? if >=1.21.2 {
        HapticsController.onExplosion(packet.center(), packet.radius(), packet.playerKnockback().isPresent());
        //?} else {
        /*HapticsController.onExplosion(
                new net.minecraft.world.phys.Vec3(packet.getX(), packet.getY(), packet.getZ()),
                packet.getPower(),
                packet.getKnockbackX() != 0f || packet.getKnockbackY() != 0f
                        || packet.getKnockbackZ() != 0f);
        *///?}
    }

    @Inject(method = "handleDamageEvent(Lnet/minecraft/network/protocol/game/ClientboundDamageEventPacket;)V", at = @At("TAIL"))
    private void steampad$onEntityDamage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (packet.entityId() == mc.player.getId()) {
            HapticsController.onPlayerDamaged(packet.getSource(mc.level));
            return;
        }
        // This same packet fires for ANY nearby entity taking damage (how vanilla shows hurt
        // animations on mobs/other players) — used to detect the player dealing damage to a boss,
        // melee OR ranged (arrows), so the mystery proximity ping can stop (feedback: "no se detiene
        // la vibracion de los jefes al darle el primer golpe... esto incluye flechas, lo mejor es al
        // hacerle daño"). See HapticsController.onEntityDamaged.
        net.minecraft.world.entity.Entity target = mc.level.getEntity(packet.entityId());
        if (target != null) {
            HapticsController.onEntityDamaged(target, packet.getSource(mc.level));
        }
    }
}
