package dev.steampad.mixin;

import dev.steampad.haptics.HapticsController;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Haptics hook: the player's melee attack just landed on {@code target}. Signature verified against
 * the mapped 1.21.10 jar via javap: {@code attackEntity(PlayerEntity, Entity)}.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;)V",
            at = @At("HEAD"))
    private void steampad$onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        HapticsController.onMeleeHit(player, target);
    }
}
