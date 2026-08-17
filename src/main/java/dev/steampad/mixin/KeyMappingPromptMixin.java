package dev.steampad.mixin;

import dev.steampad.input.KeyPromptResolver;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes in-world "press X to …" prompts show the GAMEPAD button instead of the keyboard key while a
 * controller is driving — e.g. a mod's "Open with Right Click" becomes "Open with LT".
 *
 * <p>{@link KeyMapping#getTranslatedKeyMessage()} is the standard API a mod calls to render the key
 * an action is bound to, so hooking this one method covers every mod that builds its prompt properly,
 * with zero per-mod integration. A mod that hardcodes the key letter into its own translation string
 * is out of reach — this never edits arbitrary text, it only answers the question the mod asked.
 *
 * <p><b>Scoped to in-world, no screen open</b> (see {@link KeyPromptResolver#promptsShouldUsePad}).
 * That is deliberate and load-bearing: vanilla's own Controls screen renders every binding through
 * this exact method, so substituting there would leave the player unable to see or rebind their real
 * keyboard keys. Interaction prompts render with no screen open, which is exactly the case we want.
 *
 * <p>Returns unchanged whenever the pad has no button for that action, so a prompt never claims a
 * button that would do nothing.
 *
 * <p>Target verified with {@code javap} on both mapped jars: {@code getTranslatedKeyMessage()} with
 * descriptor {@code ()Lnet/minecraft/network/chat/Component;}, a single overload on each — hence the
 * bare selector and no version conditional.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingPromptMixin {

    @Inject(method = "getTranslatedKeyMessage", at = @At("RETURN"), cancellable = true)
    private void steampad$showPadButtonInPrompts(CallbackInfoReturnable<Component> cir) {
        if (!KeyPromptResolver.promptsShouldUsePad()) return;
        String button = KeyPromptResolver.buttonFor((KeyMapping) (Object) this);
        if (button == null) return;
        cir.setReturnValue(Component.literal(button));
    }
}
