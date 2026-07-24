package dev.steampad.radial.icon;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Renders a custom text character as icon in a radial slot. */
public final class CharacterIconProvider {

    private CharacterIconProvider() {}

    public static void render(DrawContext ctx, String character, int x, int y) {
        if (character == null || character.isEmpty()) return;
        var tr = MinecraftClient.getInstance().textRenderer;
        ctx.drawCenteredTextWithShadow(tr, character, x + 8, y + 4, 0xFFFFFFFF);
    }
}
