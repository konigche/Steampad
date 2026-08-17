package dev.steampad.radial.icon;

import com.mojang.blaze3d.platform.NativeImage;
import dev.steampad.emote.EmoteData;
import dev.steampad.emote.EmoteLibrary;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders an emote's own icon in a radial slot — a real image (the "AAA" per-emote artwork the
 * user asked for, matching the community format's own design: a downloaded {@code .emotecraft}
 * binary can embed one directly, and a loose {@code .json} can ship a sibling {@code <name>.png}, per
 * kosmx.gitbook.io's documented convention — see {@link EmoteLibrary}). Falls back to a single-letter
 * avatar ({@link CharacterIconProvider}) for emotes that don't have one — the same fallback the wheel
 * already used everywhere before this existed, so nothing regresses for icon-less emotes.
 *
 * <p>Decoding is lazy and cached: the raw PNG bytes only get turned into a GPU texture once per
 * emote id, the first time it's ever drawn, keyed by a dedicated {@code steampad:emote_icon/<id>}
 * identifier so it never collides with anything else the texture manager tracks.
 */
public final class EmoteIconProvider {

    private static final int SIZE = 16;
    private static final Map<String, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, NativeImage> IMAGES = new ConcurrentHashMap<>();
    private static final java.util.Set<String> FAILED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private EmoteIconProvider() {}

    public static void render(GuiGraphics ctx, String emoteId, int x, int y) {
        if (emoteId == null || emoteId.isEmpty()) return;
        EmoteData data = EmoteLibrary.byId(emoteId);
        ResourceLocation tex = data != null ? textureFor(emoteId, data) : null;
        if (tex != null) {
            NativeImage img = IMAGES.get(emoteId);
            int iw = img != null ? img.getWidth() : SIZE;
            int ih = img != null ? img.getHeight() : SIZE;
            // Matches ButtonIcon/ControllerBrandIcon's established call shape — the drawTexture
            // overload with only (x,y,u,v,width,height,textureWidth,textureHeight) and no separate
            // regionWidth/regionHeight rendered nothing (feedback: "el PNG... no aparece nada"), while
            // this 12-region-explicit form is the one already proven working elsewhere in the mod.
            // regionWidth/regionHeight = the FULL source image (no sub-region sampling — the icon PNG
            // IS the whole texture, not an atlas cell).
            dev.steampad.compat.mc.RenderCompat.blitTexture(ctx, tex, x, y, SIZE, SIZE, iw, ih);
            return;
        }
        String letter = data != null && !data.name.isEmpty()
                ? data.name.substring(0, 1).toUpperCase(Locale.ROOT) : "♪";
        CharacterIconProvider.render(ctx, letter, x, y);
    }

    private static ResourceLocation textureFor(String emoteId, EmoteData data) {
        if (data.iconPng == null || FAILED.contains(emoteId)) return null;
        return TEXTURES.computeIfAbsent(emoteId, id -> {
            try {
                NativeImage image = NativeImage.read(data.iconPng);
                // DynamicTexture gained a debug-label supplier as its first argument in 1.21.6 (the
                // Blaze3D rewrite labels GPU resources); before that it only takes the image. The
                // label is diagnostic only, so both branches produce the same texture.
                //? if >=1.21.6 {
                DynamicTexture texture =
                        new DynamicTexture(() -> "steampad_emote_icon_" + id, image);
                //?} else {
                /*DynamicTexture texture = new DynamicTexture(image);*/
                //?}
                ResourceLocation textureId = ResourceLocation.fromNamespaceAndPath("steampad", "emote_icon/" + sanitize(id));
                Minecraft.getInstance().getTextureManager().register(textureId, texture);
                IMAGES.put(id, image);
                return textureId;
            } catch (Exception e) {
                FAILED.add(id);
                return null;
            }
        });
    }

    private static String sanitize(String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
    }
}
