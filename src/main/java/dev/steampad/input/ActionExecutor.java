package dev.steampad.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.steampad.compat.mc.InputEventCompat;
import dev.steampad.compat.mc.InventoryCompat;
import dev.steampad.radial.RadialMenuController;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.lwjgl.glfw.GLFW;

/**
 * Executes a resolved InputAction based on the current dispatch context.
 * Handles the mapping from logical action → concrete game effect.
 */
public final class ActionExecutor {

    private ActionExecutor() {}

    public static void execute(InputAction action, InputDispatchContext ctx, long handle) {
        Minecraft mc = Minecraft.getInstance();

        switch (action) {
            // Gameplay
            case JUMP -> pressKey(mc.options.keyJump);
            case SNEAK -> pressKey(mc.options.keyShift);
            case ATTACK -> pressMouseButton(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            case USE -> pressMouseButton(mc, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            case SPRINT -> pressKey(mc.options.keySprint);
            case INVENTORY -> pressKey(mc.options.keyInventory);
            case SWAP_HANDS -> pressKey(mc.options.keySwapOffhand);
            case OPEN_CHAT -> pressKey(mc.options.keyChat);
            case DROP_ITEM -> pressKey(mc.options.keyDrop);
            case CHANGE_PERSPECTIVE -> pressKey(mc.options.keyTogglePerspective);
            case PICK_BLOCK -> pressMouseButton(mc, GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
            case TAKE_SCREENSHOT -> Screenshot.grab(
                    mc.gameDirectory, mc.getMainRenderTarget(),
                    text -> mc.execute(() -> mc.gui.getChat().addMessage(text)));

            // Hotbar
            case NEXT_HOTBAR -> {
                if (mc.player != null) {
                    var inv = mc.player.getInventory();
                    int size = Inventory.getSelectionSize();
                    InventoryCompat.setSelectedSlot(inv,
                            (InventoryCompat.getSelectedSlot(inv) + 1) % size);
                }
            }
            case PREV_HOTBAR -> {
                if (mc.player != null) {
                    var inv = mc.player.getInventory();
                    int size = Inventory.getSelectionSize();
                    InventoryCompat.setSelectedSlot(inv,
                            (InventoryCompat.getSelectedSlot(inv) + size - 1) % size);
                }
            }

            // Pause
            case PAUSE -> {
                if (mc.player != null && mc.screen == null) {
                    PauseGate.openPauseMenu(mc);
                }
            }
            case DROP_STACK -> {
                if (mc.player != null) mc.player.drop(true);
            }

            // GUI Navigation
            case GUI_BACK -> {
                if (mc.screen != null) mc.screen.onClose();
            }
            case GUI_PRESS -> VirtualMouseController.simulateLeftClick();
            case GUI_NAV_UP    -> navigate(mc, 0, -1);
            case GUI_NAV_DOWN  -> navigate(mc, 0,  1);
            case GUI_NAV_LEFT  -> navigate(mc, -1, 0);
            case GUI_NAV_RIGHT -> navigate(mc,  1, 0);
            case GUI_NEXT_TAB -> {
                if (mc.screen instanceof dev.steampad.screen.TabbedScreen t) t.steampad$nextTab();
            }
            case GUI_PREV_TAB -> {
                if (mc.screen instanceof dev.steampad.screen.TabbedScreen t) t.steampad$prevTab();
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

            // Debug — the F3 overlay toggle moved from the HUD's DebugScreenOverlay to a
            // Minecraft-level DebugHudProfile in 1.21.10. Same user-visible effect either way.
            case TOGGLE_DEBUG_MENU -> {
                //? if >=1.21.9 {
                mc.debugEntries.toggleF3Visible();
                //?} else {
                /*if (mc.gui != null) mc.gui.getDebugOverlay().toggleOverlay();*/
                //?}
            }

            default -> { /* no-op for unimplemented actions */ }
        }
    }

    private static void navigate(Minecraft mc, int dx, int dy) {
        var screen = mc.screen;
        if (screen == null) return;
        if (screen instanceof dev.steampad.screen.SteamPadBaseScreen base) {
            base.focusMoveDir(dx, dy);
        } else {
            dev.steampad.input.GuiFocusNavigator.moveDir(screen, dx, dy);
        }
    }

    private static void pressKey(KeyMapping key) {
        if (key == null) return;
        KeyMapping.click(InputConstants.getKey(key.saveString()));
    }

    private static void pressMouseButton(Minecraft mc, int button) {
        // onMouseButton widened via steampad.accesswidener.
        // MC 1.21.10 signature: onMouseButton(long window, MouseInput input, int action).
        // INJECTING marks this as a mod-injected click so MouseMixin doesn't treat it as the physical
        // mouse taking over (which would flip the active device and hide the controller cursor).
        VirtualMouseController.INJECTING = true;
        try {
            InputEventCompat.fireMouseButton(mc, button, GLFW.GLFW_PRESS);
        } finally {
            VirtualMouseController.INJECTING = false;
        }
    }
}
