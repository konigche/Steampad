package dev.steampad.radial;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders the radial menu overlay on top of the game.
 * Called from GameRendererMixin during the HUD render phase.
 */
public final class RadialMenuOverlay {

    private RadialMenuOverlay() {}

    public static void render(GuiGraphics context, float tickDelta) {
        if (!RadialMenuController.isOpen()) return;

        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        RadialRenderer.render(context, screenW / 2, screenH / 2,
                RadialMenuController.getSlots(),
                RadialMenuController.getSelectedSlot(),
                RadialMenuController.getActiveHandle(),
                RadialMenuController.wheelCount(),
                RadialMenuController.getPage());
    }
}
