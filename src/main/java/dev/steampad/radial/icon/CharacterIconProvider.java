package dev.steampad.radial.icon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Renders a custom text character as icon in a radial slot. */
public final class CharacterIconProvider {

    private CharacterIconProvider() {}

    public static void render(GuiGraphics ctx, String character, int x, int y) {
        if (character == null || character.isEmpty()) return;
        var tr = Minecraft.getInstance().font;
        ctx.drawCenteredString(tr, character, x + 8, y + 4, 0xFFFFFFFF);
    }
}
