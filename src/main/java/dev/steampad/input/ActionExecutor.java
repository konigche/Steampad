package dev.steampad.input;

import dev.steampad.radial.RadialMenuController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.glfw.GLFW;

/**
 * Executes a resolved InputAction based on the current dispatch context.
 * Handles the mapping from logical action → concrete game effect.
 */
public final class ActionExecutor {

    private ActionExecutor() {}

    public static void execute(InputAction action, InputDispatchContext ctx, long handle) {
        MinecraftClient mc = MinecraftClient.getInstance();

        switch (action) {
            // Gameplay
            case JUMP -> pressKey(mc.options.jumpKey);
            case SNEAK -> pressKey(mc.options.sneakKey);
            case ATTACK -> pressMouseButton(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            case USE -> pressMouseButton(mc, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            case SPRINT -> pressKey(mc.options.sprintKey);
            case INVENTORY -> pressKey(mc.options.inventoryKey);
            case SWAP_HANDS -> pressKey(mc.options.swapHandsKey);
            case OPEN_CHAT -> pressKey(mc.options.chatKey);
            case DROP_ITEM -> pressKey(mc.options.dropKey);
            case CHANGE_PERSPECTIVE -> pressKey(mc.options.togglePerspectiveKey);
            case PICK_BLOCK -> pressMouseButton(mc, GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
            case TAKE_SCREENSHOT -> ScreenshotRecorder.saveScreenshot(
                    mc.runDirectory, mc.getFramebuffer(),
                    text -> mc.execute(() -> mc.inGameHud.getChatHud().addMessage(text)));

            // Hotbar
            case NEXT_HOTBAR -> {
                if (mc.player != null) {
                    int slot = (mc.player.getInventory().getSelectedSlot() + 1) % 9;
                    mc.player.getInventory().setSelectedSlot(slot);
                }
            }
            case PREV_HOTBAR -> {
                if (mc.player != null) {
                    int slot = (mc.player.getInventory().getSelectedSlot() + 8) % 9;
                    mc.player.getInventory().setSelectedSlot(slot);
                }
            }

            // Pause
            case PAUSE -> {
                if (mc.player != null && mc.currentScreen == null) {
                    PauseGate.openPauseMenu(mc);
                }
            }
            case DROP_STACK -> {
                if (mc.player != null) mc.player.dropSelectedItem(true);
            }

            // GUI Navigation
            case GUI_BACK -> {
                if (mc.currentScreen != null) mc.currentScreen.close();
            }
            case GUI_PRESS -> VirtualMouseController.simulateLeftClick();
            case GUI_NAV_UP    -> navigate(mc, 0, -1);
            case GUI_NAV_DOWN  -> navigate(mc, 0,  1);
            case GUI_NAV_LEFT  -> navigate(mc, -1, 0);
            case GUI_NAV_RIGHT -> navigate(mc,  1, 0);
            case GUI_NEXT_TAB -> {
                if (mc.currentScreen instanceof dev.steampad.screen.TabbedScreen t) t.steampad$nextTab();
            }
            case GUI_PREV_TAB -> {
                if (mc.currentScreen instanceof dev.steampad.screen.TabbedScreen t) t.steampad$prevTab();
            }
            case TOGGLE_VMOUSE -> VirtualMouseController.toggle();

            // Radial
            case OPEN_RADIAL -> RadialMenuController.open(handle);
            case RADIAL_NAV_UP    -> RadialMenuController.navigate(0);
            case RADIAL_NAV_RIGHT -> RadialMenuController.navigate(2);
            case RADIAL_NAV_DOWN  -> RadialMenuController.navigate(4);
            case RADIAL_NAV_LEFT  -> RadialMenuController.navigate(6);

            // Virtual Mouse
            case VMOUSE_LEFT_CLICK  -> VirtualMouseController.simulateLeftClick();
            case VMOUSE_RIGHT_CLICK -> VirtualMouseController.simulateRightClick();
            case VMOUSE_SCROLL_UP   -> VirtualMouseController.simulateScroll(1.0);
            case VMOUSE_SCROLL_DOWN -> VirtualMouseController.simulateScroll(-1.0);

            // Debug — F3 overlay toggle moved to DebugHudProfile in MC 1.21.10
            case TOGGLE_DEBUG_MENU -> mc.debugHudEntryList.toggleF3Enabled();

            default -> { /* no-op for unimplemented actions */ }
        }
    }

    private static void navigate(MinecraftClient mc, int dx, int dy) {
        var screen = mc.currentScreen;
        if (screen == null) return;
        if (screen instanceof dev.steampad.screen.SteamPadBaseScreen base) {
            base.focusMoveDir(dx, dy);
        } else {
            dev.steampad.input.GuiFocusNavigator.moveDir(screen, dx, dy);
        }
    }

    private static void pressKey(KeyBinding key) {
        if (key == null) return;
        KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey()));
    }

    private static void pressMouseButton(MinecraftClient mc, int button) {
        // onMouseButton widened via steampad.accesswidener.
        // MC 1.21.10 signature: onMouseButton(long window, MouseInput input, int action).
        // INJECTING marks this as a mod-injected click so MouseMixin doesn't treat it as the physical
        // mouse taking over (which would flip the active device and hide the controller cursor).
        VirtualMouseController.INJECTING = true;
        try {
            mc.mouse.onMouseButton(mc.getWindow().getHandle(), new MouseInput(button, 0), GLFW.GLFW_PRESS);
        } finally {
            VirtualMouseController.INJECTING = false;
        }
    }
}
