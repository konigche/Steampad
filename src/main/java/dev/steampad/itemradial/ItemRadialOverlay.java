package dev.steampad.itemradial;

import dev.steampad.radial.RadialRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Thin wrapper over the shared {@link RadialRenderer} — same pattern as
 * {@link dev.steampad.radial.RadialMenuOverlay}/{@code EmoteWheelOverlay} — so any future visual
 * improvement to the wheel engine (theme, animation, selection "pop") reaches all three wheels at
 * once instead of three separate renderers.
 */
public final class ItemRadialOverlay {

    private ItemRadialOverlay() {}

    public static void render(GuiGraphics context, float tickDelta) {
        if (!ItemRadialController.isOpen()) return;

        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // editable=false: these wheels have no per-slot editor (feedback: "elimina Editar casilla
        // en las ruedas de inventario y hotbar... estos no deberían de estar"). bloom: the Inventory
        // wheel's dwell-fill/satellite-ring UI (feedback round 4) — null on the Hotbar wheel, which
        // has no categories.
        RadialRenderer.render(context, screenW / 2, screenH / 2,
                ItemRadialController.getSlots(),
                ItemRadialController.getSelectedSlot(),
                ItemRadialController.getActiveHandle(),
                ItemRadialController.wheelCount(),
                ItemRadialController.getPage(),
                null, true, false, ItemRadialController.getBloom());
    }
}
