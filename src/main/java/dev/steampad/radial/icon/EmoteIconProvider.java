package dev.steampad.radial.icon;

import dev.steampad.emote.EmoteData;
import dev.steampad.emote.EmoteLibrary;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final Map<String, Identifier> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, NativeImage> IMAGES = new ConcurrentHashMap<>();
    private static final java.util.Set<String> FAILED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private EmoteIconProvider() {}

    public static void render(DrawContext ctx, String emoteId, int x, int y) {
        if (emoteId == null || emoteId.isEmpty()) return;
        EmoteData data = EmoteLibrary.byId(emoteId);
        Identifier tex = data != null ? textureFor(emoteId, data) : null;
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
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, SIZE, SIZE, iw, ih, iw, ih);
            return;
        }
        String letter = data != null && !data.name.isEmpty()
                ? data.name.substring(0, 1).toUpperCase(Locale.ROOT) : "♪";
        CharacterIconProvider.render(ctx, letter, x, y);
    }

    private static Identifier textureFor(String emoteId, EmoteData data) {
        if (data.iconPng == null || FAILED.contains(emoteId)) return null;
        return TEXTURES.computeIfAbsent(emoteId, id -> {
            try {
                NativeImage image = NativeImage.read(data.iconPng);
                NativeImageBackedTexture texture =
                        new NativeImageBackedTexture(() -> "steampad_emote_icon_" + id, image);
                Identifier textureId = Identifier.of("steampad", "emote_icon/" + sanitize(id));
                MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
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
