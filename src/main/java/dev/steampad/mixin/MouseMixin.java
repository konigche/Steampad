package dev.steampad.mixin;

import dev.steampad.input.InputRouter;
import dev.steampad.input.VirtualMouseController;
import dev.steampad.service.ActiveControllerService;
import dev.steampad.service.ControllerManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Coexistence layer for the physical mouse and the controller cursor.
 *
 * <p>The virtual cursor moves the OS pointer through {@code onCursorPos} guarded by
 * {@link VirtualMouseController#INJECTING} — those are always allowed. For physical movements we:
 * <ul>
 *   <li>track real movement (non-trivial delta) and flip the active device to MOUSE,</li>
 *   <li>inside a screen, while the controller is the active device, <b>cancel</b> tiny/ghost moves so
 *       a resting mouse can't steal hover or block the A press,</li>
 *   <li>in gameplay (no screen), never cancel — the mouse stays usable for aiming.</li>
 * </ul>
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {

    private static double steampad$lastPhysX = Double.NaN;
    private static double steampad$lastPhysY = Double.NaN;

    @Inject(method = "onCursorPos", at = @At("HEAD"), cancellable = true)
    private void steampad$routeCursor(long window, double x, double y, CallbackInfo ci) {
        if (VirtualMouseController.INJECTING) return;   // our own virtual move — always allow
                                                        // (counted at the injection site, not here)

        // Measure the real physical delta (injected moves don't update these, so deltas stay honest).
        double dx = Double.isNaN(steampad$lastPhysX) ? 0 : x - steampad$lastPhysX;
        double dy = Double.isNaN(steampad$lastPhysY) ? 0 : y - steampad$lastPhysY;
        steampad$lastPhysX = x;
        steampad$lastPhysY = y;
        boolean realMove = (dx * dx + dy * dy) > 9.0;   // >3px → a deliberate user movement (filters jitter)

        MinecraftClient mc = MinecraftClient.getInstance();

        if (realMove) {
            dev.steampad.input.MouseEventStats.recordExternalMove(dx, dy);

            // D098 — the persistent "el mouse virtual laggea, solo con el 8BitDo" bug, mechanism
            // finally traced end-to-end in code: while the user is ACTIVELY steering the virtual
            // cursor with the stick, external mouse motion arriving here (on this setup: Steam
            // Input's desktop layout emulating a mouse from the SAME physical pad SteamPad reads
            // raw — double input, "Steam Virtual Gamepad" visible in the device list is the tell)
            // used to force a takeover below: >20px/event sweeps (which stick-speed mouse emulation
            // produces constantly) bypassed the gamepad-hold window via markMouseForce, hid the
            // cursor (onPhysicalMouseTookOver), and the very next dispatcher tick re-showed it
            // SNAPPED to wherever the emulated motion had dragged the OS pointer (onStickUsed →
            // syncFromOsMouse) — a hide/teleport/refocus cycle several times per second. That IS
            // the reported lag. While the stick is steering, the pointer belongs to the stick:
            // swallow the concurrent motion (counted + throttle-logged as evidence). A real human
            // mouse still takes over instantly the moment the stick is released (≤1 tick), and a
            // physical mouse CLICK always wins immediately (onMouseButton below, unchanged).
            if (mc.currentScreen != null
                    && VirtualMouseController.isShown()
                    && VirtualMouseController.isMovingByStick()) {
                long steeringHandle = ActiveControllerService.getActiveHandle();
                if (steeringHandle != 0L && ControllerManager.isFallbackHandle(steeringHandle)) {
                    dev.steampad.input.MouseEventStats.recordSuppressedDuringStick(dx, dy);
                    ci.cancel();
                    return;
                }
            }

            // A LARGE sweep (>20px in one event) can only be a human dragging the mouse — it must win
            // even inside the gamepad-hold window. The hold window filters trackpad/gyro noise, but a
            // pad that re-marks itself every tick (drift/trigger noise) would otherwise keep the mouse
            // suppressed forever, leaving the visible virtual cursor desynced from where clicks land.
            if (dx * dx + dy * dy > 400.0) InputRouter.markMouseForce();
            else InputRouter.markMouse();  // user actually moved the mouse → mouse is now the active device
            // If the mouse genuinely took over (not within the gamepad-hold window), have the AUTO
            // virtual cursor step aside so the real OS pointer reappears (S1). The stick re-wakes it.
            if (InputRouter.isMouse()) {
                dev.steampad.input.MouseEventStats.recordTakeover();
                VirtualMouseController.onPhysicalMouseTookOver();
            }
            return;                    // and let it through (physical + virtual coexist)
        }

        // Tiny/ghost move. Only suppress it inside a screen while the controller owns input, and only
        // when the controller cursor is active — so it can't ghost-hover or eat clicks.
        if (mc.currentScreen == null) return;            // gameplay: never suppress the mouse
        if (!InputRouter.isGamepad()) return;            // mouse is the active device → allow

        long handle = ActiveControllerService.getActiveHandle();
        if (handle != 0L && ControllerManager.isFallbackHandle(handle)
                && VirtualMouseController.isShown()) {
            dev.steampad.input.MouseEventStats.recordGhostCancelled();
            ci.cancel();
        }
    }

    // Controlify-style out-of-focus grab: vanilla lockCursor() bails when the window is unfocused,
    // so closing a menu with the gamepad in the background left the cursor permanently free (any
    // physical click then landed outside the window → more focus loss). With "Out of Focus Input"
    // enabled and a fallback pad active, the lock proceeds; GLFW applies the capture on focus regain.
    @Redirect(method = "lockCursor",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/MinecraftClient;isWindowFocused()Z"))
    private boolean steampad$lockCursorFocusCheck(MinecraftClient client) {
        return client.isWindowFocused() || dev.steampad.input.PauseGate.allowLockCursorUnfocused();
    }

    // A physical mouse-button press is an unambiguous human mouse action: flip the active device to
    // MOUSE immediately (bypassing the gamepad-hold window) and have the AUTO virtual cursor step
    // aside, so at worst ONE click is lost to a desynced pointer — never a whole dead menu. Virtual
    // clicks injected by the mod (ActionExecutor.pressMouseButton) are excluded via INJECTING.
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void steampad$routeMouseButton(long window, net.minecraft.client.input.MouseInput input,
                                           int action, CallbackInfo ci) {
        if (VirtualMouseController.INJECTING) return;   // mod-injected click, not the physical mouse
        if (action == org.lwjgl.glfw.GLFW.GLFW_PRESS && MinecraftClient.getInstance().currentScreen != null) {
            InputRouter.markMouseForce();
            VirtualMouseController.onPhysicalMouseTookOver();
        }
    }
}
