package dev.steampad.input;

import dev.steampad.config.ControllerConfig;

/**
 * Rebindable physical-button bindings for the fallback (GLFW/SDL3) gameplay path. Each {@link Bind}
 * is a game action with a default physical button, whether it is a held or tap action, and a
 * {@link Category} for grouping in the configuration screen. The dispatcher resolves a Bind to its
 * currently-assigned button (per-controller override in {@code ControllerConfig.buttonBindings}, else
 * the default) and reads its state from the normalized {@link GamepadSnapshot}.
 *
 * <p>Optional <b>chord</b> modifier per bind ({@code ControllerConfig.chordBindings}): when set, the
 * action only fires while the chord button is also held — and the chord button's own base action does
 * not double-fire, because each bind gates on its chord independently.
 */
public final class GamepadBinds {

    public enum Category { MOVEMENT, GAMEPLAY, INVENTORY, MISC }

    public enum Bind {
        // Movement
        JUMP("A", true, "steampad.bind.jump", Category.MOVEMENT),
        SPRINT("L3", true, "steampad.bind.sprint", Category.MOVEMENT),
        SNEAK("B", true, "steampad.bind.sneak", Category.MOVEMENT),
        GYRO_TOGGLE("", false, "steampad.bind.gyro_toggle", Category.MOVEMENT),
        // Gameplay
        ATTACK("RT", true, "steampad.bind.attack", Category.GAMEPLAY),
        USE("LT", true, "steampad.bind.use", Category.GAMEPLAY),
        DROP("DDOWN", false, "steampad.bind.drop", Category.GAMEPLAY),
        DROP_STACK("", false, "steampad.bind.drop_stack", Category.GAMEPLAY),
        HOTBAR_PREV("LB", false, "steampad.bind.hotbar_prev", Category.GAMEPLAY),
        HOTBAR_NEXT("RB", false, "steampad.bind.hotbar_next", Category.GAMEPLAY),
        RADIAL("DRIGHT", true, "steampad.bind.radial", Category.GAMEPLAY),
        PERSPECTIVE("BACK", false, "steampad.bind.perspective", Category.GAMEPLAY),
        PAUSE("START", false, "steampad.bind.pause", Category.GAMEPLAY),
        PICK_BLOCK("", false, "steampad.bind.pick_block", Category.GAMEPLAY),
        ZOOM("", true, "steampad.bind.zoom", Category.GAMEPLAY),
        // Quick-cycle the over-the-shoulder camera side (left/center/right) — mirrors the CapsLock
        // quick-toggle in Leawind's Third-Person mod, through SteamPad's own bind system instead of a
        // keyboard key. Unbound by default; only does anything while the feature is enabled.
        THIRD_PERSON_SIDE_CYCLE("", false, "steampad.bind.third_person_side_cycle", Category.GAMEPLAY),
        // Toggles the free-look camera on/off in-game (mirrors the reference mod's TOGGLE_MOD_ENABLE
        // quick key) without having to open Ajustes Globales. Unbound by default.
        THIRD_PERSON_FREE_LOOK_TOGGLE("", false, "steampad.bind.third_person_free_look_toggle", Category.GAMEPLAY),
        // Hold to temporarily repurpose the right stick (camera offset) and D-pad up/down (distance)
        // for adjusting the free camera live — the gamepad equivalent of the reference mod's "hold Z,
        // move the mouse / scroll the wheel". Only does anything while free-look is enabled.
        THIRD_PERSON_ADJUST("", true, "steampad.bind.third_person_adjust", Category.GAMEPLAY),
        // Inventory
        INVENTORY("Y", false, "steampad.bind.inventory", Category.INVENTORY),
        // MOVEMENT (was INVENTORY): swapping hands is a mid-action gameplay move — the user asked for
        // it to live with jump/sneak in Botones ("cambiar de mano creo que deberia estar en la opcion
        // donde esta brincar, agacharse"). Enum position untouched (config stores by name()).
        SWAP_HANDS("X", false, "steampad.bind.swap_hands", Category.MOVEMENT),
        // Hold to open the dedicated EMOTE WHEEL directly (skipping the LB/RB carousel) — same hold/
        // select/release gesture as RADIAL. Ships unbound like ZOOM/OPEN_KEYBOARD (FASE 63).
        EMOTE_WHEEL("", true, "steampad.bind.emote_wheel", Category.GAMEPLAY),
        // Misc
        CHAT("", false, "steampad.bind.chat", Category.MISC),
        PLAYER_LIST("", true, "steampad.bind.player_list", Category.MISC),
        SCREENSHOT("", false, "steampad.bind.screenshot", Category.MISC),
        HUD_TOGGLE("", false, "steampad.bind.hud_toggle", Category.MISC),
        // Unbound by default (no sensible default modifier to assume) — the player wires up their own
        // combo in Botones, e.g. a spare shoulder button + A (feedback: "pon abrir teclado en el
        // inventario con un chord+a"). Force-opens the virtual keyboard in any inventory-like screen,
        // for mods whose own text field SteamPad can't auto-detect (Roughly Enough Items' search box
        // lives entirely outside the screen's widget tree — see D063).
        OPEN_KEYBOARD("", false, "steampad.bind.open_keyboard", Category.MISC);

        public final String defaultButton;
        public final boolean held;
        public final String labelKey;
        public final Category category;

        Bind(String def, boolean held, String labelKey, Category category) {
            this.defaultButton = def;
            this.held = held;
            this.labelKey = labelKey;
            this.category = category;
        }
    }

    private GamepadBinds() {}

    /** The physical button id currently assigned to {@code bind} for this controller ("" = unbound). */
    public static String button(ControllerConfig cfg, Bind bind) {
        String b = cfg.buttonBindings.get(bind.name());
        return (b == null) ? bind.defaultButton : b;
    }

    public static void assign(ControllerConfig cfg, Bind bind, String buttonId) {
        cfg.buttonBindings.put(bind.name(), buttonId);
    }

    /** Clears the binding (unbound). Stored explicitly so it overrides the built-in default. */
    public static void clear(ControllerConfig cfg, Bind bind) {
        cfg.buttonBindings.put(bind.name(), "");
        cfg.chordBindings.remove(bind.name());
    }

    /** Restores a single bind to its built-in default. */
    public static void resetOne(ControllerConfig cfg, Bind bind) {
        cfg.buttonBindings.remove(bind.name());
        cfg.chordBindings.remove(bind.name());
    }

    public static void reset(ControllerConfig cfg) {
        cfg.buttonBindings.clear();
        cfg.chordBindings.clear();
    }

    // ---- Chord modifier -------------------------------------------------------------------

    /** The chord (modifier) button for this bind, or "" if none. */
    public static String chord(ControllerConfig cfg, Bind bind) {
        String c = cfg.chordBindings.get(bind.name());
        return c == null ? "" : c;
    }

    public static void assignChord(ControllerConfig cfg, Bind bind, String buttonId) {
        if (buttonId == null || buttonId.isBlank()) cfg.chordBindings.remove(bind.name());
        else cfg.chordBindings.put(bind.name(), buttonId);
    }

    /** True if the chord requirement (if any) is currently satisfied. */
    private static boolean chordHeld(GamepadSnapshot snap, ControllerConfig cfg, Bind bind) {
        String c = chord(cfg, bind);
        if (c.isEmpty()) return true;
        return rawHeld(snap, c);
    }

    // ---- State resolution -----------------------------------------------------------------

    /** Maps a button id to a {@link GamepadSnapshot} button index, or -1 for triggers/unknown. */
    public static int index(String id) {
        return switch (id) {
            case "A" -> GamepadSnapshot.A;
            case "B" -> GamepadSnapshot.B;
            case "X" -> GamepadSnapshot.X;
            case "Y" -> GamepadSnapshot.Y;
            case "LB" -> GamepadSnapshot.LEFT_BUMPER;
            case "RB" -> GamepadSnapshot.RIGHT_BUMPER;
            case "BACK" -> GamepadSnapshot.BACK;
            case "START" -> GamepadSnapshot.START;
            case "L3" -> GamepadSnapshot.LEFT_THUMB;
            case "R3" -> GamepadSnapshot.RIGHT_THUMB;
            case "DUP" -> GamepadSnapshot.DPAD_UP;
            case "DRIGHT" -> GamepadSnapshot.DPAD_RIGHT;
            case "DDOWN" -> GamepadSnapshot.DPAD_DOWN;
            case "DLEFT" -> GamepadSnapshot.DPAD_LEFT;
            // Extra buttons (8BitDo back paddles / misc), SDL3 only.
            case "M1" -> GamepadSnapshot.MISC1;
            case "P1" -> GamepadSnapshot.PADDLE1;
            case "P2" -> GamepadSnapshot.PADDLE2;
            case "P3" -> GamepadSnapshot.PADDLE3;
            case "P4" -> GamepadSnapshot.PADDLE4;
            case "M2" -> GamepadSnapshot.MISC2;
            case "M3" -> GamepadSnapshot.MISC3;
            case "M4" -> GamepadSnapshot.MISC4;
            default -> -1;   // LT / RT (triggers) or unknown
        };
    }

    public static boolean isLeftTrigger(String id)  { return "LT".equals(id); }
    public static boolean isRightTrigger(String id) { return "RT".equals(id); }

    // ---- Analog trigger threshold ------------------------------------------------------------

    /** Press threshold for LT/RT, set per-tick from the active controller's config (was fixed 0). */
    private static volatile float triggerThreshold = 0f;

    public static void setTriggerThreshold(float t) {
        triggerThreshold = Math.max(0f, Math.min(0.9f, t));
    }

    /** True when a trigger axis value counts as "pressed" under the configured threshold. */
    public static boolean trigDown(float axisValue) {
        return axisValue > triggerThreshold;
    }

    /** Raw held state of a button id (no chord logic). Public so the dispatcher can gate extra binds. */
    public static boolean isHeldRaw(GamepadSnapshot snap, String id) {
        return rawHeld(snap, id);
    }

    private static boolean rawHeld(GamepadSnapshot snap, String id) {
        if (id == null || id.isEmpty()) return false;
        if (isLeftTrigger(id))  return trigDown(snap.axis(GamepadSnapshot.AXIS_LEFT_TRIGGER));
        if (isRightTrigger(id)) return trigDown(snap.axis(GamepadSnapshot.AXIS_RIGHT_TRIGGER));
        int i = index(id);
        return i >= 0 && snap.button(i);
    }

    /**
     * Chord-aware base-action suppression (the heart of "A=jump but DRIGHT+A=Map must NOT jump").
     *
     * <p>A plain action on button X (no chord of its own) is suppressed whenever <em>another</em>
     * action — a {@link Bind} chord or an extra/mod chord — is mapped to that same trigger button X and
     * its modifier is currently held. So while you hold the modifier, the trigger button drives the
     * chord action, not its plain base action.
     *
     * @param button       the trigger button id this base action is on
     * @param exceptBind   the bind being evaluated (skipped when scanning, may be null)
     * @param exceptKeybind the extra-bind keybind id being evaluated (skipped when scanning, may be null)
     */
    public static boolean buttonShadowedByHeldChord(GamepadSnapshot snap, ControllerConfig cfg,
                                                    String button, Bind exceptBind, String exceptKeybind) {
        if (button == null || button.isEmpty()) return false;
        // Bind chords pointing at this trigger button.
        for (Bind b : Bind.values()) {
            if (b == exceptBind) continue;
            String oc = chord(cfg, b);
            if (oc.isEmpty()) continue;
            if (!button(cfg, b).equals(button)) continue;
            if (rawHeld(snap, oc)) return true;
        }
        // Extra (mod) chords pointing at this trigger button.
        for (var e : cfg.extraBinds.entrySet()) {
            String btn = e.getKey();
            String kb = e.getValue();
            if (!button.equals(btn)) continue;
            if (kb != null && kb.equals(exceptKeybind)) continue;
            String oc = cfg.extraChords.getOrDefault(kb, "");
            if (oc.isEmpty()) continue;
            if (rawHeld(snap, oc)) return true;
        }
        return false;
    }

    /** True while the bound button (and its chord, if any) is held. */
    public static boolean held(GamepadSnapshot snap, ControllerConfig cfg, Bind bind) {
        String id = button(cfg, bind);
        if (id.isEmpty()) return false;
        // If this is a plain bind (no chord) and the same button has an active chord elsewhere, the
        // chord wins → suppress the base action.
        if (chord(cfg, bind).isEmpty() && buttonShadowedByHeldChord(snap, cfg, id, bind, null)) return false;
        return chordHeld(snap, cfg, bind) && rawHeld(snap, id);
    }

    /** True only on the rising edge of the bound button (with the chord held, if any). */
    public static boolean pressed(GamepadSnapshot snap, boolean[] prevButtons,
                                  boolean prevLt, boolean prevRt, ControllerConfig cfg, Bind bind) {
        String id = button(cfg, bind);
        if (id.isEmpty()) return false;
        if (chord(cfg, bind).isEmpty() && buttonShadowedByHeldChord(snap, cfg, id, bind, null)) return false;
        if (!chordHeld(snap, cfg, bind)) return false;
        if (isLeftTrigger(id))  return trigDown(snap.axis(GamepadSnapshot.AXIS_LEFT_TRIGGER)) && !prevLt;
        if (isRightTrigger(id)) return trigDown(snap.axis(GamepadSnapshot.AXIS_RIGHT_TRIGGER)) && !prevRt;
        int i = index(id);
        return i >= 0 && snap.button(i) && !prevButtons[i];
    }

    /** Detects which physical button was just pressed (for rebind capture), or null. */
    public static String capturePress(GamepadSnapshot snap, boolean[] prevButtons,
                                      boolean prevLt, boolean prevRt) {
        for (int i = 0; i < GamepadSnapshot.BUTTON_COUNT; i++) {
            if (snap.button(i) && !prevButtons[i]) {
                String id = idForIndex(i);
                if (id != null) return id;
            }
        }
        if (trigDown(snap.axis(GamepadSnapshot.AXIS_LEFT_TRIGGER)) && !prevLt) return "LT";
        if (trigDown(snap.axis(GamepadSnapshot.AXIS_RIGHT_TRIGGER)) && !prevRt) return "RT";
        return null;
    }

    private static String idForIndex(int i) {
        return switch (i) {
            case GamepadSnapshot.A -> "A";
            case GamepadSnapshot.B -> "B";
            case GamepadSnapshot.X -> "X";
            case GamepadSnapshot.Y -> "Y";
            case GamepadSnapshot.LEFT_BUMPER -> "LB";
            case GamepadSnapshot.RIGHT_BUMPER -> "RB";
            case GamepadSnapshot.BACK -> "BACK";
            case GamepadSnapshot.START -> "START";
            case GamepadSnapshot.LEFT_THUMB -> "L3";
            case GamepadSnapshot.RIGHT_THUMB -> "R3";
            case GamepadSnapshot.DPAD_UP -> "DUP";
            case GamepadSnapshot.DPAD_RIGHT -> "DRIGHT";
            case GamepadSnapshot.DPAD_DOWN -> "DDOWN";
            case GamepadSnapshot.DPAD_LEFT -> "DLEFT";
            case GamepadSnapshot.MISC1 -> "M1";
            case GamepadSnapshot.PADDLE1 -> "P1";
            case GamepadSnapshot.PADDLE2 -> "P2";
            case GamepadSnapshot.PADDLE3 -> "P3";
            case GamepadSnapshot.PADDLE4 -> "P4";
            case GamepadSnapshot.MISC2 -> "M2";
            case GamepadSnapshot.MISC3 -> "M3";
            case GamepadSnapshot.MISC4 -> "M4";
            default -> null;
        };
    }
}
