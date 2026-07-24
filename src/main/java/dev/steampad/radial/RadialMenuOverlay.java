package dev.steampad.radial;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Renders the radial menu overlay on top of the game.
 * Called from GameRendererMixin during the HUD render phase.
 */
public final class RadialMenuOverlay {

    private RadialMenuOverlay() {}

    public static void render(DrawContext context, float tickDelta) {
        if (!RadialMenuController.isOpen()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        RadialRenderer.render(context, screenW / 2, screenH / 2,
                RadialMenuController.getSlots(),
                RadialMenuController.getSelectedSlot(),
                RadialMenuController.getActiveHandle(),
                RadialMenuController.wheelCount(),
                RadialMenuController.getPage());
    }
}
