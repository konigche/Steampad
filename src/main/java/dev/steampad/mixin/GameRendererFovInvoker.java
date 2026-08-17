package dev.steampad.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes vanilla's own private {@code GameRenderer#getFov} so the free camera can project its
 * shoulder offset through the <b>real</b> field of view actually being rendered this frame — not the
 * raw FOV slider value.
 *
 * <p>Needed because the offset is a screen-space ratio (see
 * {@link dev.steampad.input.ThirdPersonCameraController}): converting "keep the player 30% of the
 * way to the left edge of the screen" into a world-space camera nudge requires the actual vertical
 * FOV, which vanilla adjusts at runtime for sprinting, for the speed effect, and while a bow is
 * drawn. Reading {@code mc.options.getFov()} instead would make the player drift across the screen
 * every time any of those kick in.
 *
 * <p>Signature verified against the mapped Yarn 1.21.10 jar via javap:
 * {@code private float getFov(Camera, float, boolean)}. This is a pure accessor — it adds no logic
 * and changes no behaviour on its own (CLAUDE.md restriction 4). Mirrors the reference mod's own
 * {@code GameRendererInvoker}.
 */
@Mixin(GameRenderer.class)
public interface GameRendererFovInvoker {

    // Return type differs by version: double on 1.21.1, float from 1.21.9. The single caller already
    // returns double, so widening is harmless — but the @Invoker signature must match the target
    // exactly or Mixin refuses to bind it at startup.
    //? if >=1.21.9 {
    @Invoker("getFov")
    float steampad$getFov(Camera camera, float tickDelta, boolean changingFov);
    //?} else {
    /*@Invoker("getFov")
    double steampad$getFov(Camera camera, float tickDelta, boolean changingFov);
    *///?}
}
