package dev.steampad.radial.icon;

import dev.steampad.radial.RadialSlot;
import net.minecraft.client.gui.DrawContext;

/** Dispatches icon rendering based on slot icon type. */
public final class RadialIconResolver {

    private RadialIconResolver() {}

    public static void render(DrawContext ctx, RadialSlot slot, int x, int y) {
        switch (slot.iconType) {
            case "ITEM"      -> ItemIconProvider.render(ctx, slot.iconValue, x, y);
            case "EFFECT"    -> EffectIconProvider.render(ctx, slot.iconValue, x, y);
            case "CHARACTER" -> CharacterIconProvider.render(ctx, slot.iconValue, x, y);
            case "EMOTE"     -> EmoteIconProvider.render(ctx, slot.iconValue, x, y);
            default          -> { /* no icon */ }
        }
    }
}
