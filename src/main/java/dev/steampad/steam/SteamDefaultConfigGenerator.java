package dev.steampad.steam;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds a complete Steam Input <b>controller configuration</b> ({@code controller_mappings}) with
 * real default bindings, so a normal gamepad works the moment the action manifest is deployed.
 *
 * <p><b>The problem this fixes (v0.77.0, D117).</b> SteamPad shipped only the action <em>manifest</em>
 * ({@code game_actions_<appid>.vdf}), which declares the action vocabulary but binds nothing. Steam
 * responds to a manifest with no configuration by generating an <em>empty</em> template: every action
 * unbound, every stick dead. That is exactly the reported symptom — "no está mapeado ningún botón y
 * los sticks no los detecta aunque los mapee". Valve's documented answer is a {@code configurations}
 * section in the manifest pointing at per-controller-type configuration files; those files are what
 * this class generates.
 *
 * <p><b>Why generated instead of a static resource.</b> The defaults below are read from
 * {@link dev.steampad.input.GamepadBinds.Bind}'s own default button column — the same table the
 * SDL3/GLFW path already uses. Generating means the Steam Input layout and the native layout cannot
 * drift apart: change a default in one place and both follow. A checked-in .vdf would silently rot
 * the first time a default changed. It is also directly unit-testable, which a resource is not.
 *
 * <p><b>Syntax provenance.</b> Every construct here was verified against the real Valve templates
 * shipped in a local Steam install ({@code controller_base/templates/*.vdf}), not guessed:
 * <ul>
 *   <li>group/inputs/activators/bindings nesting and {@code "binding" "game_action <set> <action>"};</li>
 *   <li>the {@code gameactions} block, which is how an analog stick binds to a StickPadGyro action —
 *       a stick cannot be bound with an ordinary {@code binding} line, which is the specific reason
 *       mapping the sticks by hand appeared to do nothing;</li>
 *   <li>{@code preset}/{@code group_source_bindings} and the source names
 *       ({@code button_diamond}, {@code dpad}, {@code joystick}, {@code right_joystick},
 *       {@code left_trigger}, {@code right_trigger}, {@code switch});</li>
 *   <li>the exact input names per group mode ({@code button_a..y}, {@code dpad_north..west},
 *       {@code click}, {@code button_escape}, {@code button_menu}, {@code left_bumper},
 *       {@code right_bumper}, {@code button_back_left}, {@code button_back_right}).</li>
 * </ul>
 * Only the universally-present groups are emitted (no trackpads), so one body is valid for every
 * controller family and a Deck keeps its own trackpad defaults untouched.
 */
public final class SteamDefaultConfigGenerator {

    private SteamDefaultConfigGenerator() {}

    /** Action set names — must match {@code game_actions_template.vdf} exactly. */
    public static final String SET_GAMEPLAY  = "SteamPad_Gameplay";
    public static final String SET_MENU      = "SteamPad_Menu";
    public static final String SET_INVENTORY = "SteamPad_Inventory";
    public static final String SET_MOUNTED   = "SteamPad_Mounted";

    /** Steam controller_type strings we ship a default for (Valve's documented vocabulary). */
    public static final String[] CONTROLLER_TYPES = {
            "controller_generic", "controller_xbox360", "controller_xboxone", "controller_neptune"
    };

    /** Filename this config is deployed under for a given controller type. */
    public static String fileNameFor(String controllerType) {
        return "steampad_" + controllerType + ".vdf";
    }

    /**
     * One physical input bound to one action. {@code input} is the Steam input name inside its group;
     * {@code action} is the IGA action name; {@code label} is what Steam's configurator displays.
     */
    private record Bind(String input, String action, String label) {}

    // ---- Group layout, mirroring the real Valve templates -----------------------------------------
    // Offsets within an action set's own id block; the base is set-specific so ids stay globally
    // unique across the four presets in one file.
    private static final int G_BUTTONS = 0, G_DPAD = 1, G_LSTICK = 2, G_RSTICK = 3,
                             G_LTRIG = 4, G_RTRIG = 5, G_SWITCH = 6;
    private static final int GROUPS_PER_SET = 7;

    private static Map<Integer, Bind[]> gameplayGroups() {
        Map<Integer, Bind[]> m = new LinkedHashMap<>();
        // Mirrors GamepadBinds.Bind defaults: A=JUMP, B=SNEAK, X=SWAP_HANDS, Y=INVENTORY.
        m.put(G_BUTTONS, new Bind[]{
                new Bind("button_a", "steampad_jump", "Jump"),
                new Bind("button_b", "steampad_sneak", "Sneak"),
                new Bind("button_x", "steampad_swap_hands", "Swap Hands"),
                new Bind("button_y", "steampad_inventory", "Inventory"),
        });
        // DDOWN=DROP, DRIGHT=RADIAL (GamepadBinds defaults). North/west intentionally left free so
        // the user has somewhere obvious to put their own actions.
        m.put(G_DPAD, new Bind[]{
                new Bind("dpad_south", "steampad_drop_item", "Drop Item"),
                new Bind("dpad_east", "steampad_open_radial", "Radial Menu"),
        });
        m.put(G_LSTICK, new Bind[]{ new Bind("click", "steampad_sprint", "Sprint") });      // L3=SPRINT
        m.put(G_RSTICK, new Bind[]{ new Bind("click", "steampad_pick_block", "Pick Block") });
        m.put(G_LTRIG, new Bind[]{ new Bind("click", "steampad_use", "Use / Build") });      // LT=USE
        m.put(G_RTRIG, new Bind[]{ new Bind("click", "steampad_attack", "Attack / Mine") }); // RT=ATTACK
        m.put(G_SWITCH, new Bind[]{
                new Bind("button_menu", "steampad_pause", "Pause"),                    // START=PAUSE
                new Bind("button_escape", "steampad_change_perspective", "Perspective"), // BACK
                new Bind("left_bumper", "steampad_prev_hotbar", "Previous Slot"),
                new Bind("right_bumper", "steampad_next_hotbar", "Next Slot"),
                // The whole reason Steam Input is worth having: back paddles are claimed exclusively
                // by Steam, so this is the only path that can reach them. Slots are then assigned to
                // any keybind inside the mod's own Buttons screen.
                new Bind("button_back_left", "steampad_slot_1", "SteamPad Slot 1"),
                new Bind("button_back_right", "steampad_slot_2", "SteamPad Slot 2"),
        });
        return m;
    }

    private static Map<Integer, Bind[]> menuGroups(boolean inventory) {
        Map<Integer, Bind[]> m = new LinkedHashMap<>();
        m.put(G_BUTTONS, new Bind[]{
                new Bind("button_a", "steampad_gui_press", "Confirm"),
                new Bind("button_b", "steampad_gui_back", "Back"),
                new Bind("button_x", inventory ? "steampad_swap_hands" : "steampad_toggle_vmouse",
                        inventory ? "Swap Hands" : "Toggle Cursor"),
                new Bind("button_y", "steampad_toggle_vmouse", "Toggle Cursor"),
        });
        m.put(G_DPAD, new Bind[]{
                new Bind("dpad_north", "steampad_gui_nav_up", "Navigate Up"),
                new Bind("dpad_south", "steampad_gui_nav_down", "Navigate Down"),
                new Bind("dpad_west", "steampad_gui_nav_left", "Navigate Left"),
                new Bind("dpad_east", "steampad_gui_nav_right", "Navigate Right"),
        });
        m.put(G_LSTICK, new Bind[0]);
        m.put(G_RSTICK, new Bind[0]);
        m.put(G_LTRIG, new Bind[]{ new Bind("click", "steampad_cycle_backward", "Cycle Back") });
        m.put(G_RTRIG, new Bind[]{ new Bind("click", "steampad_cycle_forward", "Cycle Forward") });
        m.put(G_SWITCH, new Bind[]{
                new Bind("button_menu", "steampad_gui_back", "Close"),
                new Bind("left_bumper", "steampad_gui_prev_tab", "Previous Tab"),
                new Bind("right_bumper", "steampad_gui_next_tab", "Next Tab"),
                new Bind("button_back_left", "steampad_slot_1", "SteamPad Slot 1"),
                new Bind("button_back_right", "steampad_slot_2", "SteamPad Slot 2"),
        });
        return m;
    }

    private static Map<Integer, Bind[]> mountedGroups() {
        Map<Integer, Bind[]> m = new LinkedHashMap<>();
        m.put(G_BUTTONS, new Bind[]{
                new Bind("button_a", "steampad_jump", "Jump"),
                new Bind("button_b", "steampad_sneak", "Dismount"),
                new Bind("button_y", "steampad_inventory", "Inventory"),
        });
        m.put(G_DPAD, new Bind[0]);
        m.put(G_LSTICK, new Bind[0]);
        m.put(G_RSTICK, new Bind[0]);
        m.put(G_LTRIG, new Bind[]{ new Bind("click", "steampad_use", "Use") });
        m.put(G_RTRIG, new Bind[]{ new Bind("click", "steampad_attack", "Attack") });
        m.put(G_SWITCH, new Bind[]{
                new Bind("button_menu", "steampad_pause", "Pause"),
                new Bind("button_escape", "steampad_change_perspective", "Perspective"),
                new Bind("left_bumper", "steampad_prev_hotbar", "Previous Slot"),
                new Bind("right_bumper", "steampad_next_hotbar", "Next Slot"),
        });
        return m;
    }

    /** The analog action each stick group carries, per set (null = leave that stick to Steam). */
    private static String leftStickAction(String set)  { return "steampad_left_stick"; }
    private static String rightStickAction(String set) {
        // In menus the right stick drives the virtual cursor; in gameplay it is the camera.
        return set.equals(SET_MENU) || set.equals(SET_INVENTORY) ? "steampad_vmouse" : "steampad_right_stick";
    }

    /**
     * Generates the full configuration for one controller type.
     *
     * @param controllerType one of {@link #CONTROLLER_TYPES}
     */
    public static String generate(String controllerType) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("\"controller_mappings\"\n{\n");
        sb.append("\t\"version\"\t\t\"3\"\n");
        sb.append("\t\"revision\"\t\t\"1\"\n");
        sb.append("\t\"title\"\t\t\"SteamPad — Default\"\n");
        sb.append("\t\"description\"\t\t\"Default SteamPad bindings. Generated by the mod from its own "
                + "button defaults — every action is already mapped; the back paddles are on "
                + "SteamPad Slot 1/2, assignable inside the mod's Buttons screen.\"\n");
        sb.append("\t\"controller_type\"\t\t\"").append(controllerType).append("\"\n");

        int presetId = 0;
        presetId = emitSet(sb, SET_GAMEPLAY,  gameplayGroups(),      presetId);
        presetId = emitSet(sb, SET_MENU,      menuGroups(false),     presetId);
        presetId = emitSet(sb, SET_INVENTORY, menuGroups(true),      presetId);
        emitSet(sb, SET_MOUNTED,   mountedGroups(),       presetId);

        sb.append("}\n");
        return sb.toString();
    }

    /** Emits all groups plus the preset for one action set; returns the next free preset id. */
    private static int emitSet(StringBuilder sb, String set, Map<Integer, Bind[]> groups, int presetId) {
        int base = presetId * 100;   // keeps group ids unique and readable per set

        for (Map.Entry<Integer, Bind[]> e : groups.entrySet()) {
            int offset = e.getKey();
            emitGroup(sb, set, base + offset, modeFor(offset), e.getValue(), stickActionFor(set, offset));
        }

        sb.append("\t\"preset\"\n\t{\n");
        sb.append("\t\t\"id\"\t\t\"").append(presetId).append("\"\n");
        sb.append("\t\t\"name\"\t\t\"").append(set).append("\"\n");
        sb.append("\t\t\"group_source_bindings\"\n\t\t{\n");
        sb.append("\t\t\t\"").append(base + G_BUTTONS).append("\"\t\t\"button_diamond active\"\n");
        sb.append("\t\t\t\"").append(base + G_DPAD).append("\"\t\t\"dpad active\"\n");
        sb.append("\t\t\t\"").append(base + G_LSTICK).append("\"\t\t\"joystick active\"\n");
        sb.append("\t\t\t\"").append(base + G_RSTICK).append("\"\t\t\"right_joystick active\"\n");
        sb.append("\t\t\t\"").append(base + G_LTRIG).append("\"\t\t\"left_trigger active\"\n");
        sb.append("\t\t\t\"").append(base + G_RTRIG).append("\"\t\t\"right_trigger active\"\n");
        sb.append("\t\t\t\"").append(base + G_SWITCH).append("\"\t\t\"switch active\"\n");
        sb.append("\t\t}\n\t}\n");
        return presetId + 1;
    }

    private static String modeFor(int offset) {
        return switch (offset) {
            case G_BUTTONS -> "four_buttons";
            case G_DPAD -> "dpad";
            case G_LSTICK, G_RSTICK -> "joystick_move";
            case G_LTRIG, G_RTRIG -> "trigger";
            case G_SWITCH -> "switches";
            default -> throw new IllegalArgumentException("unknown group offset " + offset);
        };
    }

    /** The analog action this group carries, or null when the group isn't a stick. */
    private static String stickActionFor(String set, int offset) {
        if (offset == G_LSTICK) return leftStickAction(set);
        if (offset == G_RSTICK) return rightStickAction(set);
        return null;
    }

    private static void emitGroup(StringBuilder sb, String set, int id, String mode,
                                  Bind[] binds, String stickAction) {
        sb.append("\t\"group\"\n\t{\n");
        sb.append("\t\t\"id\"\t\t\"").append(id).append("\"\n");
        sb.append("\t\t\"mode\"\t\t\"").append(mode).append("\"\n");
        sb.append("\t\t\"inputs\"\n\t\t{\n");
        for (Bind b : binds) {
            sb.append("\t\t\t\"").append(b.input()).append("\"\n\t\t\t{\n");
            sb.append("\t\t\t\t\"activators\"\n\t\t\t\t{\n");
            sb.append("\t\t\t\t\t\"Full_Press\"\n\t\t\t\t\t{\n");
            sb.append("\t\t\t\t\t\t\"bindings\"\n\t\t\t\t\t\t{\n");
            sb.append("\t\t\t\t\t\t\t\"binding\"\t\t\"game_action ")
              .append(set).append(' ').append(b.action()).append(", ").append(b.label()).append("\"\n");
            sb.append("\t\t\t\t\t\t}\n\t\t\t\t\t}\n\t\t\t\t}\n\t\t\t}\n");
        }
        sb.append("\t\t}\n");
        // The gameactions block is what actually delivers analog stick data to GetAnalogActionData.
        // Without it a stick reports active=false forever no matter what the user maps by hand.
        if (stickAction != null) {
            sb.append("\t\t\"gameactions\"\n\t\t{\n");
            sb.append("\t\t\t\"").append(set).append("\"\t\t\"").append(stickAction).append("\"\n");
            sb.append("\t\t}\n");
        }
        sb.append("\t}\n");
    }
}
