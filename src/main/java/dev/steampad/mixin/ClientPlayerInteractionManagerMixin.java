package dev.steampad.mixin;

import dev.steampad.haptics.HapticsController;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Haptics hook: the player's melee attack just landed on {@code target}. Signature verified against
 * the mapped 1.21.10 jar via javap: {@code attackEntity(PlayerEntity, Entity)}.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"))
    private void steampad$onAttackEntity(Player player, Entity target, CallbackInfo ci) {
        HapticsController.onMeleeHit(player, target);
    }
}
