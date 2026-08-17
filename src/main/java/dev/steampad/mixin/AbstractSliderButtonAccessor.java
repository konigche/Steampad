package dev.steampad.mixin;

import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the normalized 0..1 {@code value} field and the {@code updateMessage}/{@code applyValue}
 * hooks that every slider — vanilla's own AND every mod's, including SteamPad's own {@code
 * SteamSlider} — inherits from {@code AbstractSliderButton} but never itself needs to touch directly.
 *
 * <p>Needed because the right-stick fine-adjust in {@code GamepadInputDispatcher} only ever worked on
 * {@code SteamSlider} — its own subclass, where {@code value}/{@code updateMessage}/{@code applyValue}
 * are reachable by ordinary Java access rules — and did nothing for a VANILLA slider (music volume,
 * FOV, any other options-screen slider) or a third-party mod's, because those are different concrete
 * classes this mod has no compile-time relationship with (reported: "los sliders vanilla no se mueven
 * con el stick derecho como los sliders del mod"). Targeting {@code AbstractSliderButton} itself,
 * which every slider extends, reaches all of them uniformly through the same three members.
 *
 * <p>{@code updateMessage}/{@code applyValue} are {@code protected abstract} on this exact class —
 * Mixin's {@code @Invoker} binds to the real virtual call, so invoking it on any concrete slider
 * instance correctly runs THAT slider's own override (the one that actually commits the value, e.g.
 * vanilla's writes it into {@code Options}), the same as if the slider's own code had called it.
 *
 * <p>{@code value}/{@code updateMessage}/{@code applyValue} are declared on {@code AbstractSliderButton}
 * itself with identical names/signatures on every version this project targets — this class has no
 * per-version conditional because there is nothing here that changed (confirmed with {@code javap} on
 * both mapped jars; the METHODS that did change signature on this class, {@code onClick}/
 * {@code keyPressed}/{@code onDrag}/{@code onRelease}/{@code setValueFromMouse}, are not used here).
 */
@Mixin(AbstractSliderButton.class)
public interface AbstractSliderButtonAccessor {

    @Accessor("value")
    double steampad$getValue();

    @Accessor("value")
    void steampad$setValue(double value);

    @Invoker("updateMessage")
    void steampad$updateMessage();

    @Invoker("applyValue")
    void steampad$applyValue();
}
