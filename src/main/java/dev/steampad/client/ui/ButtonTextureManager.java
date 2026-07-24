package dev.steampad.client.ui;

import dev.steampad.service.ActiveControllerService;
import dev.steampad.steam.SteamControllerHandleRef.ControllerType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves per-brand controller button textures (assets/steampad/textures/buttons/&lt;brand&gt;/&lt;stem&gt;.png).
 *
 * <p>The brand folder is chosen from the active controller (Xbox, PlayStation, Steam Deck, 8BitDo,
 * Xbox Elite…) and falls back to {@code generic}. Texture existence is checked once via the resource
 * manager and cached, so a missing PNG cleanly falls back (brand → generic → null, where null means the
 * caller should draw its vector/text glyph). All PNGs are 64×64. Nothing is loaded eagerly — MC's
 * texture manager caches by {@link Identifier} on first draw.
 */
public final class ButtonTextureManager {

    public static final int TEX = 64;   // source PNG size (square)
    private static final String BASE = "textures/buttons/";

    /** Cache of "brand/stem" → Identifier (or absent marker) so existence is checked only once. */
    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private static final Identifier MISSING = Identifier.of("steampad", "missing");

    private ButtonTextureManager() {}

    /** Brand asset folder for the currently active controller (e.g. "xbox", "ps", "steam"). */
    public static String currentBrandFolder() {
        return ActiveControllerService.getActiveRef()
                .map(r -> brandFolder(r.type, r.displayName))
                .orElse("generic");
    }

    /** Maps a controller type/name to an asset folder, mirroring {@link ControllerBrandIcon}'s resolve. */
    public static String brandFolder(ControllerType type, String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.contains("elite")) return "xbox_elite";
        if (n.contains("8bitdo") || n.contains("8bit")) return "8bitdo";
        if (n.contains("steam deck") || type == ControllerType.STEAM_DECK) return "steam";
        if (n.contains("dualsense") || n.contains("dualshock") || n.contains("sony")
                || n.contains("playstation") || type == ControllerType.PLAYSTATION) return "ps";
        if (n.contains("xbox") || type == ControllerType.XBOX) return "xbox";
        if (n.contains("steam") || type == ControllerType.STEAM_CONTROLLER) return "steam";
        return "generic";   // Switch and unknown pads use the neutral set
    }

    /** Filename stem for an internal button id, or null if the id has no texture mapping. */
    public static String stemFor(String id) {
        if (id == null || id.isEmpty()) return null;
        return switch (id) {
            case "A" -> "a";  case "B" -> "b";  case "X" -> "x";  case "Y" -> "y";
            case "LB" -> "lb"; case "RB" -> "rb"; case "LT" -> "lt"; case "RT" -> "rt";
            case "L3" -> "left_stick_click";  case "R3" -> "right_stick_click";
            case "BACK" -> "back"; case "START" -> "start"; case "GUIDE" -> "guide";
            case "DUP" -> "dup"; case "DDOWN" -> "ddown"; case "DLEFT" -> "dleft"; case "DRIGHT" -> "dright";
            case "M1" -> "m1"; case "M2" -> "m2"; case "M3" -> "m3"; case "M4" -> "m4";
            case "P1" -> "paddle1"; case "P2" -> "paddle2"; case "P3" -> "paddle3"; case "P4" -> "paddle4";
            case "LS_UP" -> "left_stick_up"; case "LS_DOWN" -> "left_stick_down";
            case "LS_LEFT" -> "left_stick_left"; case "LS_RIGHT" -> "left_stick_right";
            case "RS_UP" -> "right_stick_up"; case "RS_DOWN" -> "right_stick_down";
            case "RS_LEFT" -> "right_stick_left"; case "RS_RIGHT" -> "right_stick_right";
            default -> null;
        };
    }

    /** Texture for a button id on the active controller's brand, with generic fallback; null if none. */
    public static Identifier resolveButton(String id) {
        String stem = stemFor(id);
        if (stem == null) return null;
        Identifier brandTex = lookup(currentBrandFolder(), stem);
        if (brandTex != null) return brandTex;
        return lookup("generic", stem);
    }

    /** Brand silhouette ("controller.png") for a given controller, with generic fallback; null if none. */
    public static Identifier resolveSilhouette(ControllerType type, String name) {
        Identifier t = lookup(brandFolder(type, name), "controller");
        return t != null ? t : lookup("generic", "controller");
    }

    /** Returns the Identifier if the PNG exists (cached), else null. */
    private static Identifier lookup(String folder, String stem) {
        String key = folder + "/" + stem;
        Identifier cached = CACHE.get(key);
        if (cached != null) return cached == MISSING ? null : cached;
        Identifier id = Identifier.of("steampad", BASE + key + ".png");
        boolean exists;
        try {
            exists = MinecraftClient.getInstance().getResourceManager().getResource(id).isPresent();
        } catch (Throwable t) {
            exists = false;
        }
        CACHE.put(key, exists ? id : MISSING);
        return exists ? id : null;
    }

    /** Clears the existence cache (e.g. on resource reload). */
    public static void invalidate() { CACHE.clear(); }
}
