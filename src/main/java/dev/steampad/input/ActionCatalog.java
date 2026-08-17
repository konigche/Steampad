package dev.steampad.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * The full, categorized catalog of actions shown in the Buttons configuration screen. It mixes:
 * <ul>
 *   <li><b>BIND</b> rows — rebindable gameplay actions ({@link GamepadBinds.Bind}),</li>
 *   <li><b>FIXED</b> rows — actions hard-wired to a stick/button (movement, look, GUI nav, virtual-mouse
 *       clicks); shown with their icon so the layout is complete, but not rebindable,</li>
 *   <li><b>SLIDER</b> rows — the virtual-mouse sensitivity slider,</li>
 *   <li><b>ACTION</b> rows — open a sub-editor (the radial menu),</li>
 *   <li><b>EXTRA</b> rows — any vanilla/mod keybind, bindable to a controller button so installed mods
 *       can be driven from the pad (the keyboard key is shown alongside),</li>
 *   <li><b>SLOT</b> rows — the generic Steam Input slots (steampad_slot_1..10): mapped to a physical
 *       input in Steam's controller configurator, assigned to any keybind here (paddles in Game Mode).</li>
 * </ul>
 */
public final class ActionCatalog {

    public enum Kind { BIND, FIXED, SLIDER, TOGGLE, ACTION, EXTRA, SLOT, LAYER }

    public static final class Entry {
        public final Kind kind;
        public final String labelKey;        // translation key OR literal (EXTRA uses keybind name key)
        public final GamepadBinds.Bind bind; // BIND
        public final String fixedGlyph;      // FIXED — glyph id (e.g. LS_UP, A)
        public final String descKey;         // optional description translation key (may be null)
        public final KeyMapping keyBinding;  // EXTRA — the keybind
        public final int slotIndex;          // SLOT — 0-based Steam Input slot index (-1 otherwise)

        private Entry(Kind kind, String labelKey, GamepadBinds.Bind bind,
                      String fixedGlyph, String descKey, KeyMapping kb, int slotIndex) {
            this.kind = kind;
            this.labelKey = labelKey;
            this.bind = bind;
            this.fixedGlyph = fixedGlyph;
            this.descKey = descKey;
            this.keyBinding = kb;
            this.slotIndex = slotIndex;
        }

        static Entry bind(GamepadBinds.Bind b) {
            return new Entry(Kind.BIND, b.labelKey, b, null, b.labelKey + ".desc", null, -1);
        }
        static Entry fixed(String labelKey, String glyph) {
            return new Entry(Kind.FIXED, labelKey, null, glyph, labelKey + ".desc", null, -1);
        }
        static Entry slider(String labelKey) {
            return new Entry(Kind.SLIDER, labelKey, null, null, labelKey + ".desc", null, -1);
        }
        static Entry toggle(String labelKey) {
            return new Entry(Kind.TOGGLE, labelKey, null, null, labelKey + ".desc", null, -1);
        }
        static Entry action(String labelKey) {
            return new Entry(Kind.ACTION, labelKey, null, null, labelKey + ".desc", null, -1);
        }
        static Entry extra(KeyMapping kb) {
            return new Entry(Kind.EXTRA, kb.getName(), null, null, null, kb, -1);
        }
        static Entry slot(int slotIndex) {
            return new Entry(Kind.SLOT, "steampad.act.steam_slot", null, null,
                    "steampad.act.steam_slot.desc", null, slotIndex);
        }
        /** A Steam Input slot LAYER row — opens the per-context slot editor (Menú/Inventario/Montado);
         *  {@code slotIndex} carries the {@link SteamSlotDispatcher.Context} ordinal. */
        static Entry layer(String labelKey, SteamSlotDispatcher.Context ctx) {
            return new Entry(Kind.LAYER, labelKey, null, null, labelKey + ".desc", null, ctx.ordinal());
        }
    }

    public static final class Section {
        public final String titleKey;       // translation key (null → use literalTitle)
        public final String literalTitle;   // literal title for dynamic mod sections (null → titleKey)
        public final List<Entry> entries = new ArrayList<>();
        Section(String titleKey, String literalTitle) {
            this.titleKey = titleKey;
            this.literalTitle = literalTitle;
        }
        Section add(Entry e) { entries.add(e); return this; }
    }

    private ActionCatalog() {}

    /** Builds the full catalog. The Mods sections are rebuilt from the live keybind registry each call. */
    public static List<Section> build() {
        List<Section> out = new ArrayList<>();

        out.add(new Section("steampad.cat.movement", null)
                .add(Entry.fixed("steampad.act.move_forward", "LS_UP"))
                .add(Entry.fixed("steampad.act.move_back", "LS_DOWN"))
                .add(Entry.fixed("steampad.act.move_left", "LS_LEFT"))
                .add(Entry.fixed("steampad.act.move_right", "LS_RIGHT"))
                .add(Entry.fixed("steampad.act.look_up", "RS_UP"))
                .add(Entry.fixed("steampad.act.look_down", "RS_DOWN"))
                .add(Entry.fixed("steampad.act.look_left", "RS_LEFT"))
                .add(Entry.fixed("steampad.act.look_right", "RS_RIGHT"))
                .add(Entry.bind(GamepadBinds.Bind.GYRO_TOGGLE))
                .add(Entry.bind(GamepadBinds.Bind.JUMP))
                .add(Entry.bind(GamepadBinds.Bind.SPRINT))
                .add(Entry.bind(GamepadBinds.Bind.SNEAK))
                // Moved here from the Inventory section (feedback: "cambiar de mano... deberia estar
                // en la opcion donde esta brincar, agacharse") — it's a gameplay move, not a GUI one.
                .add(Entry.bind(GamepadBinds.Bind.SWAP_HANDS)));

        out.add(new Section("steampad.cat.gameplay", null)
                .add(Entry.bind(GamepadBinds.Bind.ATTACK))
                .add(Entry.bind(GamepadBinds.Bind.USE))
                .add(Entry.bind(GamepadBinds.Bind.DROP))
                .add(Entry.bind(GamepadBinds.Bind.DROP_STACK))
                .add(Entry.bind(GamepadBinds.Bind.PAUSE))
                .add(Entry.bind(GamepadBinds.Bind.PERSPECTIVE))
                .add(Entry.bind(GamepadBinds.Bind.HOTBAR_NEXT))
                .add(Entry.bind(GamepadBinds.Bind.HOTBAR_PREV))
                .add(Entry.bind(GamepadBinds.Bind.RADIAL))
                .add(Entry.bind(GamepadBinds.Bind.PICK_BLOCK))
                .add(Entry.bind(GamepadBinds.Bind.THIRD_PERSON_SIDE_CYCLE))
                .add(Entry.bind(GamepadBinds.Bind.THIRD_PERSON_FREE_LOOK_TOGGLE))
                .add(Entry.bind(GamepadBinds.Bind.THIRD_PERSON_ADJUST)));

        out.add(new Section("steampad.cat.zoom", null)
                .add(Entry.bind(GamepadBinds.Bind.ZOOM))
                .add(Entry.bind(GamepadBinds.Bind.ZOOM_IN))
                .add(Entry.bind(GamepadBinds.Bind.ZOOM_OUT)));

        out.add(new Section("steampad.cat.inventory", null)
                .add(Entry.bind(GamepadBinds.Bind.INVENTORY))
                .add(Entry.fixed("steampad.act.inv_select", "A"))
                .add(Entry.fixed("steampad.act.inv_split", "X"))
                .add(Entry.fixed("steampad.act.inv_close", "B"))
                .add(Entry.bind(GamepadBinds.Bind.OPEN_KEYBOARD)));

        out.add(new Section("steampad.cat.misc", null)
                .add(Entry.bind(GamepadBinds.Bind.CHAT))
                .add(Entry.bind(GamepadBinds.Bind.HUD_TOGGLE))
                .add(Entry.bind(GamepadBinds.Bind.PLAYER_LIST))
                .add(Entry.bind(GamepadBinds.Bind.SCREENSHOT)));

        out.add(new Section("steampad.cat.interface", null)
                .add(Entry.fixed("steampad.act.gui_press", "A"))
                .add(Entry.fixed("steampad.act.gui_back", "B"))
                .add(Entry.fixed("steampad.act.gui_next_tab", "RB"))
                .add(Entry.fixed("steampad.act.gui_prev_tab", "LB")));

        out.add(new Section("steampad.cat.radial", null)
                .add(Entry.action("steampad.act.radial_config"))
                // Emote wheel editor — sibling of the radial editor, same section, same row kind
                // ("en ajustes del mando debe estar tambien un boton que te lleve a ajustes de la
                // rueda del emote tal como sucede actualmente con menu radial" — FASE 63).
                .add(Entry.action("steampad.act.emote_wheel_config"))
                .add(Entry.bind(GamepadBinds.Bind.EMOTE_WHEEL)));

        out.add(new Section("steampad.cat.vmouse", null)
                .add(Entry.slider("steampad.cset.vmouse_sensitivity"))
                .add(Entry.toggle("steampad.cset.vmouse_snap"))
                .add(Entry.fixed("steampad.act.vm_left", "A"))
                .add(Entry.fixed("steampad.act.vm_right", "X"))
                .add(Entry.fixed("steampad.act.vm_shift_click", "Y"))
                .add(Entry.fixed("steampad.act.vm_scroll_down", "RS_DOWN"))
                .add(Entry.fixed("steampad.act.vm_scroll_up", "RS_UP"))
                .add(Entry.fixed("steampad.act.vm_shift", "L3"))
                .add(Entry.fixed("steampad.act.vm_toggle", "BACK")));

        // Steam Input slots — generic actions the user maps to paddles/extra buttons in Steam's
        // controller configurator; each slot triggers the keybind picked here. HIDDEN from the
        // catalog (feedback: "oculta... los botones que tengan que ver con Steam Input, Ranura(F13),
        // etc, también capa menú/inventario/montado — hasta que no tenga solución la compatibilidad
        // con Steam Input no lo pondremos") — the section-building code is kept intact (not deleted)
        // so re-enabling it later is a one-line change, but `out.add(steamSlots)` is deliberately
        // skipped so none of these rows appear in the Buttons screen for now.
        Section steamSlots = new Section("steampad.cat.steaminput", null);
        for (int i = 0; i < SteamSlotDispatcher.SLOT_COUNT; i++) {
            steamSlots.add(Entry.slot(i));
        }
        // The slot LAYERS live right next to the slots they modify (feedback: "las Capas de Ranura en
        // los botones en la seccion que actualmente tenemos de Steaminput... organizalo"). The 10 rows
        // above ARE the Gameplay layer; these three open the same editor for the other contexts.
        steamSlots.add(Entry.layer("steampad.slot.layer.menu", SteamSlotDispatcher.Context.MENU));
        steamSlots.add(Entry.layer("steampad.slot.layer.inventory", SteamSlotDispatcher.Context.INVENTORY));
        steamSlots.add(Entry.layer("steampad.slot.layer.mounted", SteamSlotDispatcher.Context.MOUNTED));
        // out.add(steamSlots);   // re-enable once Steam Input compatibility is solved

        // Dynamic: EVERY keybind (vanilla + mods), grouped by the OWNING MOD (S4), so a mod's keybinds
        // sit together under that mod's display name. Vanilla keybinds keep their localized category
        // name; anything unparseable falls into a localized "Others" bucket. Robust (no fragile "is it a
        // mod?" heuristic) and nothing mappable is hidden.
        Map<String, List<KeyMapping>> byGroup = collectKeybindsByGroup();
        for (var e : byGroup.entrySet()) {
            Section s = new Section(null, e.getKey());
            for (KeyMapping kb : e.getValue()) s.add(Entry.extra(kb));
            out.add(s);
        }
        return out;
    }

    /**
     * The mod that OWNS a keybind — the very same value that decides which section it lands in on the
     * Buttons screen ("así como en los botones lo agrupa"). Empty for vanilla and for anything whose
     * owner cannot be resolved.
     *
     * <p>Exposed so {@link ModKeybindContext} can link an open screen to the same grouping the player
     * already sees, instead of inventing a second, parallel notion of "which mod is this".
     */
    public static String modIdOf(KeyMapping kb) {
        if (kb == null) return "";
        java.lang.reflect.Method getCategory = null;
        try { getCategory = KeyMapping.class.getMethod("getCategory"); } catch (Throwable ignored) {}
        String ns = namespaceOf(categoryString(kb, getCategory));
        if (ns == null) ns = namespaceOfKeybindId(kb.getName());
        return (ns == null || ns.isEmpty() || ns.equals("minecraft")) ? "" : ns;
    }

    /** The friendly name of a mod id, for the on-screen group header. Never null. */
    public static String modDisplayName(String modId) {
        if (modId == null || modId.isEmpty()) return "";
        String n = modName(modId);
        return n != null ? n : capitalize(modId);
    }

    /** All keybinds grouped by owning mod (vanilla → localized category). Never throws. */
    private static Map<String, List<KeyMapping>> collectKeybindsByGroup() {
        Map<String, List<KeyMapping>> byGroup = new TreeMap<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return byGroup;
        java.lang.reflect.Method getCategory = null;
        try { getCategory = KeyMapping.class.getMethod("getCategory"); } catch (Throwable ignored) {}
        String othersLabel = translate("steampad.cat.other", "Others");
        try {
            for (KeyMapping kb : mc.options.keyMappings) {
                if (kb.getName().equals("key.steampad.open_menu")) continue;   // our own — skip
                String cat = categoryString(kb, getCategory);
                String group = groupLabel(cat, kb.getName(), othersLabel);
                byGroup.computeIfAbsent(group, k -> new ArrayList<>()).add(kb);
            }
        } catch (Throwable ignored) {
            byGroup.clear();
        }
        return byGroup;
    }

    private static String categoryString(KeyMapping kb, java.lang.reflect.Method getCategory) {
        if (getCategory == null) return null;
        try {
            Object c = getCategory.invoke(kb);
            return c == null ? null : c.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Resolves the display group for a keybind: the owning mod's name, or (for vanilla) the localized
     * category, falling back to the keybind id's namespace, then "Others".
     */
    private static String groupLabel(String categoryStr, String keybindId, String othersLabel) {
        // Try to read "namespace:path" from the category (1.21.10 Category record holds an Identifier).
        String ns = namespaceOf(categoryStr);
        String path = pathOf(categoryStr);
        if (ns == null) ns = namespaceOfKeybindId(keybindId);   // fall back to the keybind's own namespace

        if (ns == null || ns.isEmpty()) return othersLabel;
        if (ns.equals("minecraft")) {
            // Vanilla: localized category name (e.g. "key.categories.movement").
            if (path != null && !path.isBlank()) {
                String label = translate("key.categories." + path, null);
                if (label != null) return label;
            }
            return translate("key.categories.misc", "Minecraft");
        }
        // Modded: the mod's friendly name (e.g. "xaerominimap" → "Xaero's Minimap").
        String modName = modName(ns);
        return modName != null ? modName : capitalize(ns);
    }

    /** Mod display name from the Fabric registry, or null if unknown. */
    private static String modName(String namespace) {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getModContainer(namespace)
                    .map(c -> c.getMetadata().getName())
                    .orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Extracts the namespace from a category string like "...id=modid:path]". */
    private static String namespaceOf(String raw) {
        String id = identifierOf(raw);
        if (id == null) return null;
        int colon = id.indexOf(':');
        return colon > 0 ? id.substring(0, colon) : null;
    }

    private static String pathOf(String raw) {
        String id = identifierOf(raw);
        if (id == null) return null;
        int colon = id.indexOf(':');
        return colon >= 0 && colon < id.length() - 1 ? id.substring(colon + 1) : null;
    }

    /** Pulls "namespace:path" out of a Category toString (e.g. "Category[id=minecraft:movement]"). */
    private static String identifierOf(String raw) {
        if (raw == null) return null;
        String s = raw;
        int idEq = s.indexOf("id=");
        if (idEq >= 0) s = s.substring(idEq + 3);
        s = s.replaceAll("[\\]\\[]", "").trim();
        // Keep only the first token that looks like an identifier.
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([a-z0-9_.-]+:[a-z0-9_./-]+)").matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static String namespaceOfKeybindId(String id) {
        // Keybind ids look like "key.modid.action"; the second dotted token is usually the mod id.
        if (id == null) return null;
        String s = id.startsWith("key.") ? id.substring(4) : id;
        int dot = s.indexOf('.');
        if (dot <= 0) return null;
        String ns = s.substring(0, dot);
        return ns.equals("categories") ? null : ns;
    }

    private static String capitalize(String s) {
        s = s.replace('_', ' ').trim();
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Translate a key; returns {@code fallback} if the key is missing (null fallback → null on miss). */
    private static String translate(String key, String fallback) {
        try {
            String s = net.minecraft.network.chat.Component.translatable(key).getString();
            if (!s.equals(key)) return s;
        } catch (Throwable ignored) {}
        return fallback;
    }
}
