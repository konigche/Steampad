package dev.steampad.radial.icon;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Renders a mob effect icon in a radial slot. */
public final class EffectIconProvider {

    private EffectIconProvider() {}

    public static void render(DrawContext ctx, String effectId, int x, int y) {
        if (effectId == null || effectId.isEmpty()) return;
        try {
            Identifier id = Identifier.tryParse(effectId);
            if (id == null) return;
            var entryHolder = Registries.STATUS_EFFECT.getEntry(id);
            if (entryHolder == null || entryHolder.isEmpty()) return;
            // Sprite is registered in the GUI atlas at "<namespace>:mob_effect/<path>"
            Identifier sprite = Identifier.of(id.getNamespace(), "mob_effect/" + id.getPath());
            // MC 1.21.10: drawGuiTexture takes a RenderPipeline (RenderLayer::getGuiTextured was removed).
            ctx.drawGuiTexture(
                net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                sprite, x, y, 16, 16);
        } catch (Exception ignored) {}
    }
}
