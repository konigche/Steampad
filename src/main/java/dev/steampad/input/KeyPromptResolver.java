package dev.steampad.input;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.ControllerConfig;
import dev.steampad.service.ActiveControllerService;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.Map;

/**
 * Answers "which gamepad button does this keybind sit on right now?" so on-screen prompts can say
 * <em>"Open with LT"</em> instead of <em>"Open with Right Click"</em> while a controller is driving.
 *
 * <p>Feature request: mods that print "press H to …" should print the pad's own button instead. Any
 * mod that builds its prompt from {@link KeyMapping#getTranslatedKeyMessage()} — the standard API for
 * "show the key this action is bound to" — gets this for free through {@code KeyMappingPromptMixin},
 * with no per-mod work. A mod that hardcodes the letter into its own translation string cannot be
 * helped from here, and won't be: nothing else in this class guesses at text.
 *
 * <p>Two sources, checked in this order:
 * <ol>
 *   <li><b>Extra binds</b> — the user's own "this pad button presses that keybind" map
 *       ({@link ControllerConfig#extraBinds}), which is exactly how a MOD keybind reaches the pad.
 *       Checked first so an explicit user binding always wins.</li>
 *   <li><b>Built-in binds</b> — the vanilla keys SteamPad drives natively (attack, use, inventory…),
 *       resolved to whatever button that {@link GamepadBinds.Bind} currently sits on, so a rebound
 *       controller reports its real button rather than the default.</li>
 * </ol>
 */
public final class KeyPromptResolver {

    private KeyPromptResolver() {}

    /**
     * Vanilla keybind name → the built-in bind that drives it. Only entries SteamPad actually presses
     * belong here: a prompt must never claim a button that does nothing. Keyed by
     * {@link KeyMapping#getName()} (the translation key, e.g. {@code "key.attack"}), which is stable
     * across versions and is also what {@code extraBinds} stores.
     */
    private static final Map<String, GamepadBinds.Bind> BUILT_IN = Map.ofEntries(
            Map.entry("key.attack", GamepadBinds.Bind.ATTACK),
            Map.entry("key.use", GamepadBinds.Bind.USE),
            Map.entry("key.inventory", GamepadBinds.Bind.INVENTORY),
            Map.entry("key.swapOffhand", GamepadBinds.Bind.SWAP_HANDS),
            Map.entry("key.drop", GamepadBinds.Bind.DROP),
            Map.entry("key.chat", GamepadBinds.Bind.CHAT),
            Map.entry("key.pickItem", GamepadBinds.Bind.PICK_BLOCK),
            Map.entry("key.screenshot", GamepadBinds.Bind.SCREENSHOT),
            Map.entry("key.playerlist", GamepadBinds.Bind.PLAYER_LIST),
            Map.entry("key.jump", GamepadBinds.Bind.JUMP),
            Map.entry("key.sneak", GamepadBinds.Bind.SNEAK),
            Map.entry("key.sprint", GamepadBinds.Bind.SPRINT),
            Map.entry("key.togglePerspective", GamepadBinds.Bind.PERSPECTIVE));

    /**
     * The pad button label for {@code key} ("LT", "A", "DUP"…), or {@code null} when the pad has no
     * button for it — in which case the caller must leave the original keyboard text alone rather
     * than invent one.
     */
    public static String buttonFor(KeyMapping key) {
        if (key == null) return null;
        long handle = ActiveControllerService.getActiveHandle();
        if (handle == 0L) return null;          // no controller → prompts stay as vanilla wrote them
        ControllerConfig cfg = ConfigManager.getControllerConfig(handle);
        if (cfg == null) return null;
        String name = key.getName();

        // 1. User's own extra binds (buttonId -> keybind name). Inverted here rather than kept as a
        //    reverse map because it's user-editable at runtime and tiny (a handful of entries).
        for (Map.Entry<String, String> e : cfg.extraBinds.entrySet()) {
            if (name.equals(e.getValue())) {
                String btn = e.getKey();
                if (btn != null && !btn.isBlank()) return btn;
            }
        }

        // 2. Built-in binds, resolved through the user's current button assignment.
        GamepadBinds.Bind bind = BUILT_IN.get(name);
        if (bind == null) return null;
        String btn = GamepadBinds.button(cfg, bind);
        return (btn == null || btn.isBlank()) ? null : btn;   // blank = deliberately unbound
    }

    /**
     * Whether prompts should currently be shown in pad form at all.
     *
     * <p>Deliberately restricted to "no screen open", which is where interaction prompts live. The
     * important consequence is that vanilla's own Controls screen — which renders every keybind's
     * name through the very same {@link KeyMapping#getTranslatedKeyMessage()} — keeps showing real
     * KEYBOARD keys, so the player can still read and rebind them. Swapping those for pad buttons
     * would make the keyboard bindings uneditable, which would be a far worse bug than the one this
     * feature fixes.
     */
    public static boolean promptsShouldUsePad() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.screen == null && ActiveControllerService.getActiveHandle() != 0L;
    }
}
