package dev.steampad.input;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Links the screen you have open to the SAME grouping the Buttons screen already shows, so a mod's
 * buttons come alive exactly where that mod is, with nothing for the player to map.
 *
 * <p><b>The design, in the reporter's words.</b> "Con una pantalla abierta debe decir algo del mod de
 * Travelers Backpack; así como en los botones lo agrupa, también debe salir en la pantalla, entonces
 * solo enlaza el botón que le corresponde a esa agrupación." The grouping already existed —
 * {@link ActionCatalog#modIdOf} is the very value that decides which section a keybind lands in. What
 * was missing was the other half of the link: working out which mod owns the screen that is open.
 *
 * <p>This replaced the contextual-layer editors, which asked the player to re-map the same action per
 * context — the work a controller mod exists to remove. The model was already in the mod: A is jump in
 * gameplay and take-item in a container from ONE assignment, because the dispatcher knows the context.
 *
 * <h2>How a screen is matched to a group</h2>
 * A mod's screen classes live under its own package, verified against the installed jars rather than
 * assumed: {@code com.tiviacz.travelersbackpack.client.screens.BackpackScreen} carries
 * {@code travelersbackpack}, which is exactly the group id of {@code key.travelersbackpack.sort}. The
 * match is on a delimited segment, never a bare {@code contains}, so a short id like "tide" cannot hit
 * a random word inside another class name. The item in your main hand is checked the same way, which
 * covers mods whose screen class does not carry the name but whose item does.
 *
 * <h2>The rule</h2>
 * <ul>
 *   <li><b>A screen is open</b> → exactly one group is linked: the mod that owns that screen (or the
 *       held item). Only that group's bindings fire and only they are drawn, under the mod's name.
 *       Everything else is silent — that is what keeps a backpack button off the pause menu.</li>
 *   <li><b>No screen</b> → bindings keep firing as they always have (nothing that worked stops
 *       working), and the guide draws the ones that have not turned out to belong to a screen.</li>
 *   <li><b>A vanilla keybind</b> (no owning mod) is a gameplay action by construction — no screen could
 *       ever be "its mod's" — so with a screen open it is silent too, unless that screen claimed it.</li>
 * </ul>
 *
 * <h2>Learning which bindings belong to a screen</h2>
 * Some mod keybinds are gameplay actions ("inspect sword") and some are screen actions ("sort
 * backpack"), and their names are identical in shape — there is no way to tell them apart up front, so
 * this does not guess. It <em>observes</em>: when a binding is delivered to a screen and the screen
 * reports that it consumed it, that answer comes from the game itself, and the binding is remembered as
 * screen-owned for the session. From then on the guide stops offering it in gameplay, where it is only
 * clutter. Nothing is ever silently disabled — only hidden from a place it did nothing in.
 */
public final class ModKeybindContext {

    private ModKeybindContext() {}

    /** Keybind names a screen has answered "yes, I handled that" for. Session-scoped on purpose: it is
     *  re-learned the first time you open the screen again, and never outlives a modpack change. */
    private static final java.util.Set<String> screenOwned = new java.util.HashSet<>();

    /** Called with the screen's own answer from {@code keyPressed} — see the class doc. */
    public static void rememberScreenHandled(String keybindName) {
        if (keybindName != null && !keybindName.isEmpty()) screenOwned.add(keybindName);
    }

    /** True once a screen has taken responsibility for this binding. */
    public static boolean isScreenOwned(String keybindName) {
        return screenOwned.contains(keybindName);
    }

    // ---- Owning mod of a keybind (the Buttons grouping) ---------------------------------------

    private static final java.util.Map<String, String> groupCache = new java.util.HashMap<>();

    /** The mod group of a keybind name, resolved through {@link ActionCatalog#modIdOf} and cached —
     *  this is asked once per binding per tick and again every frame. */
    public static String groupOf(Minecraft mc, String keybindName) {
        if (keybindName == null || keybindName.isEmpty()) return "";
        String cached = groupCache.get(keybindName);
        if (cached != null) return cached;
        String group = "";
        for (KeyMapping kb : mc.options.keyMappings) {
            if (kb.getName().equals(keybindName)) { group = ActionCatalog.modIdOf(kb); break; }
        }
        groupCache.put(keybindName, group);
        return group;
    }

    // ---- The group linked by what is on screen ------------------------------------------------

    private static Class<?> cachedScreenClass;
    private static String cachedScreenName = "";
    private static Item cachedItem;
    private static String cachedItemNamespace = "";

    private static String screenClassName(Minecraft mc) {
        if (mc.screen == null) return "";
        Class<?> c = mc.screen.getClass();
        if (c != cachedScreenClass) {
            cachedScreenClass = c;
            cachedScreenName = c.getName().toLowerCase(java.util.Locale.ROOT);
        }
        return cachedScreenName;
    }

    private static String heldItemNamespace(Minecraft mc) {
        if (mc.player == null) return "";
        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) return "";
        Item item = stack.getItem();
        if (item != cachedItem) {
            cachedItem = item;
            var key = BuiltInRegistries.ITEM.getKey(item);
            cachedItemNamespace = key == null ? "" : key.getNamespace();
        }
        return cachedItemNamespace;
    }

    /** Delimited-segment match, so "tide" cannot hit the middle of another word. */
    static boolean nameCarries(String haystack, String needle) {   // package-private: tested
        if (needle.isEmpty()) return false;
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) return false;
            char before = at == 0 ? '.' : haystack.charAt(at - 1);
            int end = at + needle.length();
            char after = end >= haystack.length() ? '.' : haystack.charAt(end);
            if (!Character.isLetterOrDigit(before) && !Character.isLetterOrDigit(after)) return true;
            from = at + 1;
        }
    }

    /**
     * True if the open screen (or the held item) belongs to {@code group}. There is no reverse lookup
     * from a class to a mod id in Fabric — every mod shares one class loader — so the question is asked
     * the only way it can be: of each candidate group the player actually has bound.
     */
    public static boolean screenLinks(Minecraft mc, String group) {
        if (group.isEmpty() || mc.screen == null) return false;
        if (mc.screen instanceof dev.steampad.screen.SteamPadBaseScreen) return false;   // our editor
        return nameCarries(screenClassName(mc), group) || group.equals(heldItemNamespace(mc));
    }

    /** The mod name to print above the linked group's rows, or "" when nothing is linked. */
    public static String linkedGroupName(Minecraft mc, java.util.Collection<String> boundKeybinds) {
        for (String kb : boundKeybinds) {
            String g = groupOf(mc, kb);
            if (screenLinks(mc, g)) return ActionCatalog.modDisplayName(g);
        }
        return "";
    }

    // ---- The question everything else asks ----------------------------------------------------

    /** Whether this binding should FIRE right now. */
    public static boolean isRelevant(Minecraft mc, String keybindName) {
        if (mc.screen == null) return true;          // gameplay keeps working exactly as it did
        String group = groupOf(mc, keybindName);
        if (group.isEmpty()) {
            // VANILLA keybind assigned to a button. There is no "vanilla mod" whose screen could claim
            // it, so it is a gameplay action by construction: soltar, inventario, saltar. Letting it
            // through into screens was the same invasion the mod rule fixes — a vanilla bind firing (and
            // drawing a glyph) inside a chest or the pause menu, where it has no business. The one
            // exception is a screen that has actually claimed it, which is the game's own answer.
            return isScreenOwned(keybindName);
        }
        return screenLinks(mc, group);
    }

    /** Whether this binding should be DRAWN in the guide right now. Stricter than {@link #isRelevant}
     *  in exactly one place: a binding a screen has claimed is not advertised out in the world, where
     *  pressing it did nothing anyway. */
    public static boolean isVisible(Minecraft mc, String keybindName) {
        if (!isRelevant(mc, keybindName)) return false;
        return mc.screen != null || !isScreenOwned(keybindName);
    }
}
