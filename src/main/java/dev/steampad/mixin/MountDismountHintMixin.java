package dev.steampad.mixin;

import dev.steampad.input.KeyPromptResolver;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla's "Press SHIFT to Dismount" action-bar hint (reported: "sale un cartel que dice baja con
 * mayuscula, cambialo por el boton del gamepad"), verified via javap on both mapped jars:
 * {@code ClientPacketListener.handleSetEntityPassengersPacket} builds it as
 * {@code Component.translatable("mount.onboard", mc.options.keyShift.getTranslatedKeyMessage())} the
 * moment the local player boards ANY vehicle (horse, boat, minecart...) — a single, hardcoded call to
 * the raw KEYBOARD key's own translated name, with no gamepad awareness at all. Identical bytecode
 * (same method descriptor, same single {@code getTranslatedKeyMessage()} call) on 1.21.1 and 1.21.10,
 * so no version conditional is needed.
 *
 * <p>{@link KeyMappingPromptMixin} already does the equivalent substitution generically for any mod
 * that renders its own "press X to..." prompt through this same standard API — this redirects the ONE
 * extra call site that builds a {@link Component} directly inside a vanilla packet handler rather than
 * through a mod's own prompt-rendering path, using the exact same {@link KeyPromptResolver} so the
 * answer ("which button is Sneak on right now") is computed identically everywhere in the mod.
 */
@Mixin(ClientPacketListener.class)
public abstract class MountDismountHintMixin {

    @Redirect(method = "handleSetEntityPassengersPacket(Lnet/minecraft/network/protocol/game/ClientboundSetPassengersPacket;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/KeyMapping;getTranslatedKeyMessage()Lnet/minecraft/network/chat/Component;"))
    private Component steampad$dismountHintButton(KeyMapping keyMapping) {
        if (KeyPromptResolver.promptsShouldUsePad()) {
            String button = KeyPromptResolver.buttonFor(keyMapping);
            if (button != null) return Component.literal(button);
        }
        return keyMapping.getTranslatedKeyMessage();
    }
}
