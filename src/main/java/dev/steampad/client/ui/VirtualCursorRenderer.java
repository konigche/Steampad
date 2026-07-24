package dev.steampad.client.ui;

import dev.steampad.input.InputRouter;
import dev.steampad.input.SlotSnap;
import dev.steampad.input.VirtualMouseController;
import dev.steampad.service.ActiveControllerService;
import dev.steampad.service.ControllerManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Draws the controller cursor on top of every screen and manages the OS pointer:
 * <ul>
 *   <li>a small white dot (with a soft shadow) in normal windows,</li>
 *   <li>white corner brackets around the snapped slot inside container screens,</li>
 *   <li>a brief top-left mode indicator (ON / OFF / AUTO) that fades out after a change,</li>
 *   <li>hides the OS pointer while the controller is the active device (so the D-pad also hides it),
 *       and restores it the moment the user moves the physical mouse.</li>
 * </ul>
 * Registered on Fabric's after-render screen event so it sits above held items and tooltips.
 */
public final class VirtualCursorRenderer {

    // Transition-tracked OS cursor visibility (avoids a glfwSetInputMode call every frame).
    private static int osHiddenState = -1;   // -1 unknown, 0 normal, 1 hidden

    private VirtualCursorRenderer() {}

    /** Called when a screen (re)opens; MC resets the OS cursor to NORMAL, so re-assert next frame. */
    public static void invalidateOsCursorState() {
        osHiddenState = -1;
    }

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null) return;
        long tProf = dev.steampad.util.TickProfiler.begin();
        // Smooth, frame-rate-independent cursor motion (integrate the stick velocity each frame).
        VirtualMouseController.frameUpdate();
        dev.steampad.util.TickProfiler.end(dev.steampad.util.TickProfiler.CURSOR_RENDER, tProf);
        long window = mc.getWindow().getHandle();

        long handle = ActiveControllerService.getActiveHandle();
        boolean fallback = handle != 0L && ControllerManager.isFallbackHandle(handle);
        if (!fallback) { setOsCursorHidden(window, false); return; }

        // Cursor ownership is driven by the cursor MODE (stable), not the per-frame active device, so a
        // controller that also emits trackpad/gyro mouse motion (Steam Deck / HHD) can't make the dot
        // flicker. We hide the OS pointer while the cursor is shown OR while navigating with the D-pad.
        boolean cursorOwns = VirtualMouseController.isShown();
        boolean gamepadActive = InputRouter.isGamepad();
        setOsCursorHidden(window, cursorOwns || gamepadActive);

        // While the on-screen keyboard owns input, don't draw the mouse cursor over it.
        if (dev.steampad.client.keyboard.VirtualKeyboard.isActive()) return;

        if (VirtualMouseController.badgeVisible()) drawModeBadge(ctx);

        if (!cursorOwns) return;

        int x = (int) Math.round(VirtualMouseController.getX());
        int y = (int) Math.round(VirtualMouseController.getY());

        // Container: white selection corner-brackets around the slot under the cursor.
        if (mc.currentScreen instanceof HandledScreen<?> hs) {
            Slot slot = SlotSnap.nearestSlot(hs);
            int[] r = slot == null ? null : SlotSnap.slotRect(hs, slot);
            if (r != null) {
                drawSlotBrackets(ctx, r[0] - 1, r[1] - 1, 18, 18, 0xFFFFFFFF);
                return;   // brackets stand in for the dot inside containers
            }
        }

        // Normal windows: a small white dot with a soft shadow.
        Draw.fillCircle(ctx, x + 1, y + 1, 2, 0x90000000);   // shadow
        Draw.fillCircle(ctx, x, y, 2, 0xFFFFFFFF);           // white dot
    }

    /** Small top-left badge: "Mouse: On / Off / Auto" — shown briefly after a change, then fades. */
    private static void drawModeBadge(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float a = VirtualMouseController.badgeAlpha();
        if (a <= 0f) return;
        VirtualMouseController.Mode mode = VirtualMouseController.getMode();
        String key = switch (mode) {
            case ON -> "steampad.vmouse.on";
            case OFF -> "steampad.vmouse.off";
            case AUTO -> "steampad.vmouse.auto";
        };
        int dot = switch (mode) {
            case ON -> 0xFF66DD88;
            case OFF -> 0xFFFF6060;
            case AUTO -> 0xFFFFC044;
        };
        Text label = Text.translatable("steampad.vmouse.label").append(": ").append(Text.translatable(key));
        int tw = mc.textRenderer.getWidth(label);
        int x = 6, y = 6, padX = 6, h = 14;
        int w = tw + padX * 2 + 10;
        Draw.fillRoundRect(ctx, x, y, x + w, y + h, 3, withAlpha(0xCC121A24, a));
        ctx.fill(x, y, x + 2, y + h, withAlpha(dot, a));
        Draw.fillCircle(ctx, x + padX + 2, y + h / 2, 3, withAlpha(dot, a));
        ctx.drawText(mc.textRenderer, label, x + padX + 8, y + (h - 8) / 2, withAlpha(0xFFE6ECF2, a), false);
    }

    private static int withAlpha(int argb, float a) {
        int base = (argb >>> 24) & 0xFF;
        int na = Math.round(base * Math.max(0f, Math.min(1f, a)));
        return (na << 24) | (argb & 0x00FFFFFF);
    }

    /** Four corner brackets around a cell (Bedrock/console selection look). */
    private static void drawSlotBrackets(DrawContext ctx, int x, int y, int w, int h, int c) {
        int len = 5, t = 2;
        ctx.fill(x, y, x + len, y + t, c);
        ctx.fill(x, y, x + t, y + len, c);
        ctx.fill(x + w - len, y, x + w, y + t, c);
        ctx.fill(x + w - t, y, x + w, y + len, c);
        ctx.fill(x, y + h - t, x + len, y + h, c);
        ctx.fill(x, y + h - len, x + t, y + h, c);
        ctx.fill(x + w - len, y + h - t, x + w, y + h, c);
        ctx.fill(x + w - t, y + h - len, x + w, y + h, c);
    }

    private static void setOsCursorHidden(long window, boolean hidden) {
        // NEVER fight the gameplay grab (Controlify's rule): if vanilla has the cursor locked
        // (GLFW_CURSOR_DISABLED), overwriting the mode with HIDDEN/NORMAL silently kills mouse
        // camera deltas while vanilla still believes it's grabbed — the "mouse camera dead in
        // gameplay" desync. This method must only ever manage the pointer while a screen owns it.
        if (MinecraftClient.getInstance().mouse.isCursorLocked()) return;
        int want = hidden ? 1 : 0;
        if (osHiddenState == want) return;   // only call the native API on a real transition
        osHiddenState = want;
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR,
                hidden ? GLFW.GLFW_CURSOR_HIDDEN : GLFW.GLFW_CURSOR_NORMAL);
    }
}
