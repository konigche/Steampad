package dev.steampad.mixin;

import dev.steampad.input.ControllerInputState;
import dev.steampad.input.SprintKeyEmulator;
import dev.steampad.service.ControllerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import dev.steampad.service.ActiveControllerService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.2 {
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
//?} else {
/*import net.minecraft.client.player.Input;
*///?}

/**
 * MERGES analog controller input into the player's movement after vanilla computes it from keys,
 * giving console-style movement: a gentle stick push walks slowly, a full push runs. We set both the
 * analog movement vector (drives actual speed) and the boolean input (drives sprint eligibility,
 * animations, networking).
 *
 * <p>Merge, not overwrite (mixed-input fix): the old version REPLACED vanilla's values every tick,
 * so an idle stick wrote (0,0) over WASD and false over Space/Shift — the keyboard was dead in
 * gameplay whenever a pad was connected. Now the stick only takes the vector while actually pushed,
 * and the booleans OR with vanilla's, so keyboard+mouse and the pad coexist frame by frame like any
 * native PC port with controller support.
 *
 * <p><b>Four separate version boundaries live in this one class</b> — the movement input was reshaped
 * in stages, so a single conditional would be wrong. Boundaries taken from Controlify, which ships all
 * of them across 1.21.1–1.21.11:
 * <ul>
 *   <li><b>1.21.2</b> — base class {@code Input} became {@code ClientInput}, and the loose booleans
 *       became a {@code world.entity.player.Input} record in a {@code keyPresses} field.</li>
 *   <li><b>1.21.4</b> — {@code tick(boolean slowDown, float movementMultiplier)} became {@code tick()}.</li>
 *   <li><b>1.21.5</b> — the {@code forwardImpulse}/{@code leftImpulse} floats became a {@code Vec2
 *       moveVector}. (Vanilla also started normalising that vector; we inject at TAIL and overwrite it,
 *       so analog gradation survives — normalising would make every push full speed.)</li>
 *   <li>Sprint has no field at all before 1.21.2 — see {@link SprintKeyEmulator}.</li>
 * </ul>
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends /*? if >=1.21.2 {*/ ClientInput /*?} else {*/ /*Input *//*?}*/ {

    //? if >=1.21.4 {
    @Inject(method = "tick", at = @At("TAIL"))
    private void steampad$applyAnalogMovement(CallbackInfo ci) {
        steampad$merge(false, 1.0f);
    }
    //?} else {
    /*@Inject(method = "tick", at = @At("TAIL"))
    private void steampad$applyAnalogMovement(boolean slowDown, float movementMultiplier, CallbackInfo ci) {
        steampad$merge(slowDown, movementMultiplier);
    }
    *///?}

    /**
     * The real merge. {@code slowDown}/{@code movementMultiplier} are only meaningful before 1.21.4,
     * where vanilla applies them to its own impulses inside {@code tick} — since we overwrite those
     * impulses at TAIL, we have to apply the same scaling or sneaking/aiming with the stick would move
     * at full speed. From 1.21.4 vanilla handles that elsewhere and the parameters are inert.
     */
    private void steampad$merge(boolean slowDown, float movementMultiplier) {
        Minecraft mc = Minecraft.getInstance();

        if (!ControllerInputState.isActive()) {
            SprintKeyEmulator.apply(mc, false);
            return;
        }
        long handle = ActiveControllerService.getActiveHandle();
        if (handle == 0L || !ControllerManager.isFallbackHandle(handle)) {
            SprintKeyEmulator.apply(mc, false);
            return;
        }
        if (mc.screen != null || mc.player == null) {
            // Release an emulated sprint on the way out, or it leaks and the player sprints forever.
            SprintKeyEmulator.apply(mc, false);
            return;
        }

        // ---- Mixed vs exclusive input (ControllerConfig.mixedInput) ---------------------------------
        // The flag existed since the initial commit but was never wired to the input path at all — its
        // only consumer was the HUD glyph row, so "exclusive" behaved exactly like "mixed". This is
        // where it becomes real, and this method is the natural place: vanilla has already written its
        // own key state into the fields below by the time we run (TAIL), so the "is the keyboard being
        // used" signal is free here and needs no extra mixin of its own.
        //
        //   mixedInput = true  → pad and keyboard/mouse coexist, merged (the behaviour below, unchanged).
        //   mixedInput = false → whichever device was used last owns movement outright, switching
        //                        instantly: any pad activity marks GAMEPAD (GamepadInputDispatcher's
        //                        hasActivity, which deliberately ignores stick drift and idle-trigger
        //                        noise), any movement key marks the keyboard/mouse side.
        if (steampad$keyboardIsMoving()) dev.steampad.input.InputRouter.markKeyboard();
        if (!dev.steampad.config.ConfigManager.getControllerConfig(handle).mixedInput
                && dev.steampad.input.InputRouter.isMouse()) {
            // Keyboard/mouse owns this tick: leave every vanilla value untouched. Same sprint release
            // as the other early exits — skipping it would leave an emulated sprint key held forever.
            SprintKeyEmulator.apply(mc, false);
            return;
        }

        float fwd = ControllerInputState.forward();
        float side = ControllerInputState.sideways();
        boolean stickMoving = Math.abs(fwd) > 0.02f || Math.abs(side) > 0.02f;
        boolean wantSprint = ControllerInputState.sprint();

        // effFwd/effSide are what actually reaches vanilla's movement THIS tick — the raw stick
        // values, unless camera-relative remapping swaps them for its own (0, magnitude) "walk toward
        // the body's new facing" pair. The boolean input below must be derived from these SAME
        // effective values, not the raw stick ones (see the bug note further down).
        float effFwd = fwd, effSide = side;

        // The stick owns the movement vector only while pushed; otherwise vanilla's keyboard vector
        // stands untouched (this is what lets WASD work with a pad connected).
        if (stickMoving) {
            // Camera-relative movement (opt-in, off by default — see ThirdPersonCameraController's own
            // doc): reinterprets (side, fwd) relative to the free camera instead of the body, and turns
            // the body to match, in the SAME tick this movement vector is consumed. With the config
            // flag off, isCameraRelativeMovementActive() is a single boolean check and this is
            // byte-for-byte the original behaviour.
            if (dev.steampad.input.ThirdPersonCameraController.isCameraRelativeMovementActive()) {
                float[] relative = dev.steampad.input.ThirdPersonCameraController
                        .applyCameraRelativeMovement(mc.player, side, fwd);
                effSide = relative[0];
                effFwd = relative[1];
            }
            //? if >=1.21.5 {
            this.moveVector = new Vec2(effSide, effFwd);
            //?} else {
            /*// Bug (feedback: "correr no funciona... lo tengo en stick izquierdo en modo presionar"):
            // javap on this exact jar shows LocalPlayer.hasEnoughImpulseToStartSprinting() requires
            // this.input.forwardImpulse >= 0.8f on land — a threshold a binary keyboard W press always
            // clears (it is only ever exactly 0 or 1) but this mod's deliberately ANALOG stick movement
            // ("a gentle push walks slowly, a full push runs", this class's own doc) often does not, at
            // any comfortable/moderate push. Holding the sprint bind (default L3) then silently never
            // started a sprint at all unless the stick happened to be pegged to its physical edge — not
            // a feel issue, a hard vanilla gate the analog value fell short of. 1.21.10's equivalent
            // (ClientInput.hasForwardImpulse(), confirmed via the same javap pass) has no such minimum —
            // any nonzero forward push is enough there, so this branch is exclusively a <1.21.5 fix.
            // While sprint is actively requested and the push leans forward at all, rescale
            // (effSide, effFwd) up to full magnitude, preserving its ANGLE — a diagonal push still
            // moves at the same diagonal, just at sprint's full speed instead of a proportionally
            // slower one, matching the "hold the button, get full sprint speed regardless of exactly
            // how hard you tilt" contract analog-movement games use for a dedicated sprint control.
            if (wantSprint && effFwd > 0.05f) {
                float mag = (float) Math.sqrt(effFwd * effFwd + effSide * effSide);
                if (mag > 1.0E-4f && mag < 1f) {
                    float rescale = 1f / mag;
                    effFwd *= rescale;
                    effSide *= rescale;
                }
            }
            float scale = slowDown ? movementMultiplier : 1.0f;
            this.forwardImpulse = effFwd * scale;
            this.leftImpulse = effSide * scale;
            *///?}
        }

        // Bug fixed here (feedback: "movimiento relativo a cámara... revisa el codigo original"):
        // these booleans used to always read the RAW pre-remap fwd/side, even when camera-relative
        // mode had just swapped the actual movement vector for a completely different (0, magnitude)
        // pair. The body DID turn and walk the correct world direction (the movement vector drives the
        // real physics), but sprint eligibility and leg/arm animation — which read these booleans, not
        // the vector — kept judging by the OLD, pre-turn stick direction: push "forward" relative to a
        // sideways-facing camera and the body would turn and correctly walk that way, but play a strafe
        // animation and refuse to sprint. Reading the same effFwd/effSide the vector itself just used
        // keeps both in sync.
        boolean wantForward  = stickMoving && effFwd > 0.05f;
        boolean wantBackward = stickMoving && effFwd < -0.05f;
        boolean wantLeft     = stickMoving && effSide > 0.05f;
        boolean wantRight    = stickMoving && effSide < -0.05f;
        // wantSprint already computed above (needed earlier now, for the <1.21.5 impulse-boost fix).

        //? if >=1.21.2 {
        Input kb = this.keyPresses;
        this.keyPresses = new Input(
                kb.forward()  || wantForward,
                kb.backward() || wantBackward,
                kb.left()     || wantLeft,
                kb.right()    || wantRight,
                kb.jump()   || ControllerInputState.jump(),
                kb.shift()  || ControllerInputState.sneak(),
                kb.sprint() || wantSprint);
        //?} else {
        /*this.up    = this.up    || wantForward;
        this.down  = this.down  || wantBackward;
        this.left  = this.left  || wantLeft;
        this.right = this.right || wantRight;
        this.jumping      = this.jumping      || ControllerInputState.jump();
        this.shiftKeyDown = this.shiftKeyDown || ControllerInputState.sneak();
        // No sprint field on this version — vanilla polls the sprint keybind instead.
        SprintKeyEmulator.apply(mc, wantSprint);
        *///?}
    }

    /**
     * True while vanilla's own state says a movement key is held — the "the keyboard is being used"
     * signal for exclusive input mode. Read straight from the fields vanilla filled in before this
     * mixin runs, so no separate keyboard mixin (and no per-version bytecode audit of one) is needed.
     *
     * <p><b>Sneak and sprint are deliberately excluded.</b> Both can be TOGGLES in vanilla ("Toggle
     * Sneak" / "Toggle Sprint"), so their booleans can legitimately stay true with no key held at all —
     * and a permanently-true keyboard signal would lock the controller out of movement forever in
     * exclusive mode. That is the same {@code ToggleKeyMapping} trap D177 documented for sprint. The
     * four directions and jump are plain held-key states with no toggle variant, which is all this
     * needs to answer "is a human driving this with the keyboard right now".
     */
    private boolean steampad$keyboardIsMoving() {
        //? if >=1.21.2 {
        Input kb = this.keyPresses;
        return kb.forward() || kb.backward() || kb.left() || kb.right() || kb.jump();
        //?} else {
        /*return this.up || this.down || this.left || this.right || this.jumping;
        *///?}
    }
}
