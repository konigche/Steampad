package dev.steampad.config;

import java.util.ArrayList;
import java.util.List;

/** Configuration for the radial menu — visual appearance and any number of wheels with slots. */
public final class RadialConfig {

    public static final int MAX_SLOTS = 12;
    public static final int MAX_WHEELS = 6;

    /** One wheel: its active slot count (2–12) and slot definitions (padded to MAX_SLOTS). */
    public static final class WheelConfig {
        public int slotCount = 8;
        public List<SlotConfig> slots = new ArrayList<>();
        /** Legacy marker from when the emote wheel lived inside {@link #wheels} (pre-decoupling) —
         *  kept only so {@link #normalize()} can migrate an old config's wheel into
         *  {@link #emoteWheels} once. Never set on a NEW wheel; never checked at runtime anymore. */
        public boolean emoteWheel = false;
    }

    /**
     * The wheels (E10): as many as needed (1–{@link #MAX_WHEELS}), added/removed from the editor.
     * In-game LB/RB cycles between them with a carousel effect.
     */
    public List<WheelConfig> wheels = new ArrayList<>();

    /**
     * The dedicated emote wheel(s) — a FULLY INDEPENDENT list from {@link #wheels} (feedback: "la
     * rueda de emotes con la de menu radial deben ser independientes no deben compartir información
     * ... si se necesita poner más ruedas para emotes no se interpongan"). Originally the emote wheel
     * was just another entry in {@link #wheels} sharing its budget and carousel state with the user's
     * own wheels; splitting it into its own list means adding more emote wheels later can never eat
     * into MAX_WHEELS regular wheels or fight over which page is showing. Driven by
     * {@link dev.steampad.emote.EmoteWheelController}, never by {@link dev.steampad.radial.RadialMenuController}.
     */
    public List<WheelConfig> emoteWheels = new ArrayList<>();

    // ---- Legacy fields (pre-multi-wheel configs) — migrated by normalize(), kept for Gson reads.
    public int slotCount = 8;
    public boolean secondWheelEnabled = false;
    public int slotCount2 = 8;
    public List<SlotConfig> slots2 = new ArrayList<>();

    // Visual
    public float innerRadius = 30f;
    public float outerRadius = 80f;
    // Radius of each round slot chip (px) — bigger chips also push the ring/labels outward.
    public float chipRadius = 18f;
    // Scale of the icon drawn INSIDE each chip (item/effect/character), independent of chipRadius —
    // the icon providers always draw at a fixed 16px footprint (feedback: "haz más grandes los iconos
    // de menu radial... pon un slider para controlar el tamaño"). Bumped above 1.0 by default so
    // existing wheels look noticeably bigger out of the box, not just when the user finds the slider.
    public float iconScale = 1.4f;
    public float segmentGap = 3f;
    public int backgroundColor = 0xAA000000;
    public int selectedColor = 0xCCFFFFFF;   // white selection (unified with the cursor look)
    public int textColor = 0xFFFFFFFF;
    public boolean showBackground = true;
    // Color preset for the wheel's pixel-art look (see PixelTheme, shared with the virtual keyboard).
    // Per-controller, like the rest of this config — unknown/missing values fall back to VANILLA.
    public PixelTheme theme = PixelTheme.VANILLA;

    // Slots (always kept padded to MAX_SLOTS; only the first slotCount are used)
    public List<SlotConfig> slots = new ArrayList<>();

    public static final class SlotConfig {
        public String type = "KEYBIND";       // RadialActionType name
        public String action = "";            // action ID or command
        public String iconType = "NONE";      // ITEM, EFFECT, CHARACTER, EMOTE, NONE
        public String iconValue = "";         // item id, effect id, character, or emote id
        public String displayName = "";       // empty = no label
        public String trigger = "ON_RELEASE"; // ON_CLICK or ON_RELEASE
    }

    /** Index of the dedicated emote wheel in {@link #emoteWheels}, or -1 if none exists yet. */
    public int emoteWheelIndex() {
        normalize();
        return emoteWheels.isEmpty() ? -1 : 0;
    }

    /**
     * Returns the emote wheel's index, creating it if missing (first visit to the Rueda de Emotes
     * screen / first use of the EMOTE_WHEEL bind) — in its own independent list, never inside
     * {@link #wheels}. Returns -1 only once MAX_WHEELS emote wheels already exist.
     */
    public int ensureEmoteWheel() {
        normalize();
        if (!emoteWheels.isEmpty()) return 0;
        if (emoteWheels.size() >= MAX_WHEELS) return -1;
        WheelConfig w = new WheelConfig();
        w.emoteWheel = true;
        w.slotCount = 8;
        emoteWheels.add(w);
        clampWheels(emoteWheels);
        return 0;
    }

    /** Number of configured emote wheels (0 or more — 0 until the Rueda de Emotes screen or the
     *  EMOTE_WHEEL bind first creates one via {@link #ensureEmoteWheel()}). */
    public int emoteWheelCount() { normalize(); return emoteWheels.size(); }

    private WheelConfig emoteWheelAt(int page) {
        int i = Math.max(0, Math.min(page, emoteWheels.size() - 1));
        return emoteWheels.get(i);
    }

    public List<SlotConfig> slotsForEmote(int page) { return emoteWheelAt(page).slots; }
    public int slotCountForEmote(int page) { return emoteWheelAt(page).slotCount; }
    public void setSlotCountForEmote(int page, int v) { emoteWheelAt(page).slotCount = v; }

    /** Non-empty slot count for an emote wheel page — mirrors {@link #configuredSlotCountFor}. */
    public int configuredSlotCountForEmote(int page) {
        WheelConfig w = emoteWheelAt(page);
        int n = 0;
        for (int i = 0; i < w.slotCount && i < w.slots.size(); i++) {
            SlotConfig s = w.slots.get(i);
            if (s.type != null && !s.type.equals("NONE") && s.action != null && !s.action.isEmpty()) n++;
        }
        return n;
    }

    /** Adds a new empty emote wheel (up to MAX_WHEELS) — forward-looking: the UI only manages the
     *  first one today, but the data model already supports more without another migration. */
    public int addEmoteWheel() {
        if (emoteWheels.size() >= MAX_WHEELS) return -1;
        WheelConfig w = new WheelConfig();
        w.emoteWheel = true;
        while (w.slots.size() < MAX_SLOTS) w.slots.add(new SlotConfig());
        emoteWheels.add(w);
        return emoteWheels.size() - 1;
    }

    public boolean removeEmoteWheel(int page) {
        if (page < 0 || page >= emoteWheels.size()) return false;
        emoteWheels.remove(page);
        return true;
    }

    /**
     * Migrates legacy single/second-wheel configs into {@link #wheels}, guarantees at least one
     * wheel, clamps slot counts and pads every wheel's slot list to MAX_SLOTS.
     */
    public void normalize() {
        if (wheels == null) wheels = new ArrayList<>();
        if (wheels.isEmpty()) {
            // Migrate the legacy layout: main slots (+ optional second wheel).
            WheelConfig w0 = new WheelConfig();
            w0.slotCount = slotCount;
            if (slots != null) w0.slots = slots;
            wheels.add(w0);
            if (secondWheelEnabled) {
                WheelConfig w1 = new WheelConfig();
                w1.slotCount = slotCount2;
                if (slots2 != null) w1.slots = slots2;
                wheels.add(w1);
            }
        }
        if (wheels.size() > MAX_WHEELS) wheels = new ArrayList<>(wheels.subList(0, MAX_WHEELS));

        // One-time migration (FASE 63 follow-up, decoupling): an emote wheel that used to live
        // inside `wheels` moves OUT into its own independent `emoteWheels` list — never leaves
        // `wheels` empty in the process (that wheel just stays a normal wheel in the rare case it
        // was the user's only one).
        if (emoteWheels == null) emoteWheels = new ArrayList<>();
        for (int i = wheels.size() - 1; i >= 0; i--) {
            if (wheels.get(i).emoteWheel && wheels.size() > 1) {
                emoteWheels.add(wheels.remove(i));
            }
        }
        if (emoteWheels.size() > MAX_WHEELS) emoteWheels = new ArrayList<>(emoteWheels.subList(0, MAX_WHEELS));

        clampWheels(wheels);
        clampWheels(emoteWheels);

        // Keep the legacy mirror of wheel 0 coherent for very old readers.
        slotCount = wheels.get(0).slotCount;
        slots = wheels.get(0).slots;
        secondWheelEnabled = wheels.size() > 1;
    }

    private static void clampWheels(List<WheelConfig> list) {
        for (WheelConfig w : list) {
            if (w.slotCount < 2) w.slotCount = 2;
            if (w.slotCount > MAX_SLOTS) w.slotCount = MAX_SLOTS;
            if (w.slots == null) w.slots = new ArrayList<>();
            while (w.slots.size() < MAX_SLOTS) w.slots.add(new SlotConfig());
        }
    }

    public int wheelCount() { return Math.max(1, wheels.size()); }

    private WheelConfig wheel(int page) {
        int i = Math.max(0, Math.min(page, wheels.size() - 1));
        return wheels.get(i);
    }

    /** Slots of a wheel page (0-based). */
    public List<SlotConfig> slotsFor(int page) { return wheel(page).slots; }

    /** Active slot count of a wheel page. */
    public int slotCountFor(int page) { return wheel(page).slotCount; }

    /**
     * Number of configured (non-empty) slots on a wheel page, within its active slot count — used by
     * the wheel-delete picker to warn before removing a wheel that actually has shortcuts (feedback:
     * "si esta con atajos preguntar si esa rueda quieres eliminar").
     */
    public int configuredSlotCountFor(int page) {
        WheelConfig w = wheel(page);
        int n = 0;
        for (int i = 0; i < w.slotCount && i < w.slots.size(); i++) {
            SlotConfig s = w.slots.get(i);
            if (s.type != null && !s.type.equals("NONE") && s.action != null && !s.action.isEmpty()) n++;
        }
        return n;
    }

    /** Sets the active slot count of a wheel page. */
    public void setSlotCountFor(int page, int v) { wheel(page).slotCount = v; }

    /** Adds a new empty wheel (up to MAX_WHEELS). Returns the new wheel's page index, or -1. */
    public int addWheel() {
        if (wheels.size() >= MAX_WHEELS) return -1;
        WheelConfig w = new WheelConfig();
        while (w.slots.size() < MAX_SLOTS) w.slots.add(new SlotConfig());
        wheels.add(w);
        return wheels.size() - 1;
    }

    /** Removes a wheel (at least one always remains). Returns true if removed. */
    public boolean removeWheel(int page) {
        if (wheels.size() <= 1 || page < 0 || page >= wheels.size()) return false;
        wheels.remove(page);
        return true;
    }

    public static RadialConfig defaults() {
        RadialConfig cfg = new RadialConfig();
        cfg.normalize();
        return cfg;
    }
}
