package dev.steampad.radial.icon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Renders a mob effect icon in a radial slot. */
public final class EffectIconProvider {

    private EffectIconProvider() {}

    public static void render(GuiGraphics ctx, String effectId, int x, int y) {
        if (effectId == null || effectId.isEmpty()) return;
        try {
            ResourceLocation id = ResourceLocation.tryParse(effectId);
            if (id == null) return;
            // Registry.get(id) returns an Optional<Holder.Reference<..>> from 1.21.2 and the value
            // itself before that — only used here to check the effect actually exists, since the sprite
            // path is derived from the id rather than from the effect object.
            //? if >=1.21.2 {
            var entryHolder = BuiltInRegistries.MOB_EFFECT.get(id);
            if (entryHolder == null || entryHolder.isEmpty()) return;
            //?} else {
            /*if (BuiltInRegistries.MOB_EFFECT.get(id) == null) return;*/
            //?}
            // Sprite is registered in the GUI atlas at "<namespace>:mob_effect/<path>"
            ResourceLocation sprite = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "mob_effect/" + id.getPath());
            // MC 1.21.10: drawGuiTexture takes a RenderPipeline (RenderLayer::getGuiTextured was removed).
            dev.steampad.compat.mc.RenderCompat.blitSprite(ctx, sprite, x, y, 16, 16);
        } catch (Exception ignored) {}
    }
}
