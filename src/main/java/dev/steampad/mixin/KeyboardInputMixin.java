package dev.steampad.mixin;

import dev.steampad.input.ControllerInputState;
import dev.steampad.service.ControllerManager;
import dev.steampad.service.ActiveControllerService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MERGES analog controller input into the player's movement after vanilla computes it from keys,
 * giving console-style movement: a gentle stick push walks slowly, a full push runs. We set both the
 * analog {@code movementVector} (drives actual speed) and the boolean {@code playerInput} record
 * (drives sprint eligibility, animations, networking).
 *
 * <p>Merge, not overwrite (mixed-input fix): the old version REPLACED vanilla's values every tick,
 * so an idle stick wrote (0,0) over WASD and false over Space/Shift — the keyboard was dead in
 * gameplay whenever a pad was connected. Now the stick only takes the vector while actually pushed,
 * and the booleans OR with vanilla's, so keyboard+mouse and the pad coexist frame by frame like any
 * native PC port with controller support.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("TAIL"))
    private void steampad$applyAnalogMovement(CallbackInfo ci) {
        if (!ControllerInputState.isActive()) return;
        long handle = ActiveControllerService.getActiveHandle();
        if (handle == 0L || !ControllerManager.isFallbackHandle(handle)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null || mc.player == null) return;

        float fwd = ControllerInputState.forward();
        float side = ControllerInputState.sideways();
        boolean stickMoving = Math.abs(fwd) > 0.02f || Math.abs(side) > 0.02f;

        // effFwd/effSide are what actually reaches vanilla's movement THIS tick — the raw stick
        // values, unless camera-relative remapping swaps them for its own (0, magnitude) "walk toward
        // the body's new facing" pair. The PlayerInput booleans below must be derived from these SAME
        // effective values, not the raw stick ones (see the bug note just below).
        float effFwd = fwd, effSide = side;

        // The stick owns the movement vector only while pushed; otherwise vanilla's keyboard vector
        // stands untouched (this is what lets WASD work with a pad connected).
        if (stickMoving) {
            // Camera-relative movement (opt-in, off by default — see ThirdPersonCameraController's own
            // doc): reinterprets (side, fwd) relative to the free camera instead of the body, and turns
            // the body to match, in the SAME tick this movement vector is consumed. With the config
            // flag off, isCameraRelativeMovementActive() is a single boolean check and this is
            // byte-for-byte the original line below.
            if (dev.steampad.input.ThirdPersonCameraController.isCameraRelativeMovementActive()) {
                float[] relative = dev.steampad.input.ThirdPersonCameraController
                        .applyCameraRelativeMovement(mc.player, side, fwd);
                effSide = relative[0];
                effFwd = relative[1];
            }
            this.movementVector = new Vec2f(effSide, effFwd);
        }

        // Bug fixed here (feedback: "movimiento relativo a cámara... revisa el codigo original"):
        // these booleans used to always read the RAW pre-remap fwd/side, even when camera-relative
        // mode had just swapped the actual movement vector for a completely different (0, magnitude)
        // pair. The body DID turn and walk the correct world direction (movementVector drives the
        // real physics), but sprint eligibility and leg/arm animation — which read these booleans, not
        // movementVector — kept judging by the OLD, pre-turn stick direction: push "forward" relative
        // to a sideways-facing camera and the body would turn and correctly walk that way, but play a
        // strafe animation and refuse to sprint, since kb.forward() never went true. Reading the same
        // effFwd/effSide the movement vector itself just used keeps both in sync.
        PlayerInput kb = this.playerInput;
        this.playerInput = new PlayerInput(
                kb.forward()  || (stickMoving && effFwd > 0.05f),
                kb.backward() || (stickMoving && effFwd < -0.05f),
                kb.left()     || (stickMoving && effSide > 0.05f),
                kb.right()    || (stickMoving && effSide < -0.05f),
                kb.jump()   || ControllerInputState.jump(),
                kb.sneak()  || ControllerInputState.sneak(),
                kb.sprint() || ControllerInputState.sprint());
    }
}
