package dev.steampad.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.steampad.config.ConfigManager;
import dev.steampad.service.ActiveControllerService;
import dev.steampad.service.ControllerManager;
import dev.steampad.steam.SteamActionRegistry;
import dev.steampad.steam.SteamInputManager;
import dev.steampad.util.LogUtil;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

/**
 * Dispatches the generic Steam Input slots (steampad_slot_1..N) to a user-assigned target — either a
 * vanilla/mod keybind, or (see {@link VirtualBindInput}) one of SteamPad's own internal actions like
 * Menú Radial or Zoom (feedback: "esas ranuras de Steaminput no puedo asignar menu radial ni zoom,
 * debemos poder asignar cualquier cosa").
 *
 * <p>These slots exist so inputs that ONLY Steam Input can see — e.g. the back paddles that Steam
 * claims exclusively in Game Mode (B032) — become usable in-game: the user maps paddle → slot in
 * Steam's controller configurator, and slot → target in the mod's UI.
 *
 * <p><b>Layers/contexts:</b> each slot has up to 4 independent assignments — {@link Context#GAMEPLAY},
 * {@link Context#MENU}, {@link Context#INVENTORY}, {@link Context#MOUNTED} — so the same physical
 * paddle can do something different depending on what you're doing when you press it (feedback:
 * "varios layers... MENU, GAMPLAY, INVENTORIO, MONTADO"). Empty in a given context = no-op there,
 * exactly like before this existed — an existing single-context config keeps working unchanged.
 *
 * <p>Slots are read from Steam Input even when the active controller is served by a fallback backend
 * (SDL3/GLFW): SteamBootstrap.runCallbacks() keeps Steam states fresh every tick, so in the Game-Mode
 * hybrid case (SDL3 drives the virtual gamepad, Steam Input owns the paddles) both paths work at once.
 * Requires the VDF to be imported ({@link SteamInputManager#areActionSetsValid()}); without it every
 * slot handle is 0 and this dispatcher is a silent no-op for the Steam Input trigger source (the F13+
 * keyboard trigger source still works independently — see {@link #tick}).
 *
 * <p>HOLD semantics via {@link KeyTap#hold}/{@link KeyTap#release} for keybind targets, or
 * {@link VirtualBindInput#setHeld} for internal-bind targets (same effect for the actions that check
 * it — see {@code GamepadInputDispatcher.bHeld}/{@code bPressed}).
 */
public final class SteamSlotDispatcher {

    public static final int SLOT_COUNT = SteamActionRegistry.SLOT_COUNT;
    private static final String BIND_PREFIX = "bind:";

    /** Which "layer" a slot press belongs to right now — see the class doc. */
    public enum Context { GAMEPLAY, MENU, INVENTORY, MOUNTED }

    private static final boolean[] down = new boolean[SLOT_COUNT];
    private static final Context[] heldContext = new Context[SLOT_COUNT];
    private static final KeyMapping[] heldKb = new KeyMapping[SLOT_COUNT];
    private static final InputConstants.Key[] heldKey = new InputConstants.Key[SLOT_COUNT];
    private static final GamepadBinds.Bind[] heldBind = new GamepadBinds.Bind[SLOT_COUNT];

    // ---- Named-action bridge (B074/B075) --------------------------------------------------------
    //
    // Every named Steam Input action (steampad_jump, steampad_attack, …) the user binds DIRECTLY to a
    // button/paddle in Steam's configurator is bridged into the same VirtualBindInput OR that the
    // slots already use. Without this, those bindings only worked on the Steam-primary path
    // (InputBindingManager past the fallback early-return) — which never runs in the Game-Mode hybrid
    // setup this project actually targets (SDL3 serves the virtual gamepad, so the active handle is
    // ALWAYS a fallback handle there). This was the 4th real root cause of "asigné botones a acciones
    // y no hacen nada": the data arrived in ControllerState but nobody dispatched it.
    private static final java.util.Map<ControllerState.DigitalAction, GamepadBinds.Bind> ACTION_TO_BIND =
            new java.util.EnumMap<>(java.util.Map.ofEntries(
                    java.util.Map.entry(ControllerState.DigitalAction.JUMP, GamepadBinds.Bind.JUMP),
                    java.util.Map.entry(ControllerState.DigitalAction.SNEAK, GamepadBinds.Bind.SNEAK),
                    java.util.Map.entry(ControllerState.DigitalAction.SPRINT, GamepadBinds.Bind.SPRINT),
                    java.util.Map.entry(ControllerState.DigitalAction.ATTACK, GamepadBinds.Bind.ATTACK),
                    java.util.Map.entry(ControllerState.DigitalAction.USE, GamepadBinds.Bind.USE),
                    java.util.Map.entry(ControllerState.DigitalAction.PAUSE, GamepadBinds.Bind.PAUSE),
                    java.util.Map.entry(ControllerState.DigitalAction.INVENTORY, GamepadBinds.Bind.INVENTORY),
                    java.util.Map.entry(ControllerState.DigitalAction.SWAP_HANDS, GamepadBinds.Bind.SWAP_HANDS),
                    java.util.Map.entry(ControllerState.DigitalAction.OPEN_CHAT, GamepadBinds.Bind.CHAT),
                    java.util.Map.entry(ControllerState.DigitalAction.DROP_ITEM, GamepadBinds.Bind.DROP),
                    java.util.Map.entry(ControllerState.DigitalAction.DROP_STACK, GamepadBinds.Bind.DROP_STACK),
                    java.util.Map.entry(ControllerState.DigitalAction.PICK_BLOCK, GamepadBinds.Bind.PICK_BLOCK),
                    java.util.Map.entry(ControllerState.DigitalAction.CHANGE_PERSPECTIVE, GamepadBinds.Bind.PERSPECTIVE),
                    java.util.Map.entry(ControllerState.DigitalAction.NEXT_HOTBAR, GamepadBinds.Bind.HOTBAR_NEXT),
                    java.util.Map.entry(ControllerState.DigitalAction.PREV_HOTBAR, GamepadBinds.Bind.HOTBAR_PREV),
                    java.util.Map.entry(ControllerState.DigitalAction.OPEN_RADIAL, GamepadBinds.Bind.RADIAL)));

    /** Steam analog joystick actions (bound to a physical stick in Steam's configurator), bridged to
     *  the fallback gameplay path — SDL convention (Y+ = down); zero while idle/unbound/unattached. */
    private static float steamLeftX, steamLeftY, steamRightX, steamRightY;

    public static float steamLeftX()  { return steamLeftX; }
    public static float steamLeftY()  { return steamLeftY; }
    public static float steamRightX() { return steamRightX; }
    public static float steamRightY() { return steamRightY; }

    /** Last context whose ActionSet was activated (diagnostic: log only on change). */
    private static Context lastActivatedCtx = null;
    /** One-shot: logged the first time any Steam action delivered live data this session. */
    private static boolean loggedFirstActionData = false;

    private SteamSlotDispatcher() {}

    /** Config key for a slot ("1".."10"). Kept in one place so UI and dispatch never drift. */
    public static String configKey(int slotIndex) {
        return String.valueOf(slotIndex + 1);
    }

    /** The map backing a given context — all four are global (see the field doc in GlobalConfig). */
    public static Map<String, String> mapFor(Context ctx) {
        var g = ConfigManager.getGlobal();
        return switch (ctx) {
            case GAMEPLAY -> g.steamInputSlots;
            case MENU -> g.steamInputSlotsMenu;
            case INVENTORY -> g.steamInputSlotsInventory;
            case MOUNTED -> g.steamInputSlotsMounted;
        };
    }

    /** The context a slot press belongs to right now. */
    public static Context currentContext(Minecraft mc) {
        Screen screen = mc.screen;
        if (screen == null) {
            return (mc.player != null && mc.player.isPassenger()) ? Context.MOUNTED : Context.GAMEPLAY;
        }
        return screen instanceof AbstractContainerScreen<?> ? Context.INVENTORY : Context.MENU;
    }

    /** The raw target string assigned to a slot in a given context ("" = unassigned there). */
    public static String assignedTarget(Context ctx, int slotIndex) {
        return mapFor(ctx).getOrDefault(configKey(slotIndex), "");
    }

    /** The keybind id assigned to a slot in the GAMEPLAY layer, or "" — kept for the existing Botones
     *  screen, which only ever edited (and still only edits) this one layer. */
    public static String assignedKeybind(int slotIndex) {
        return assignedTarget(Context.GAMEPLAY, slotIndex);
    }

    /** True if {@code raw} names a SteamPad internal bind (the "bind:NAME" form) rather than an
     *  external keybind id. */
    public static boolean isBindTarget(String raw) {
        return raw != null && raw.startsWith(BIND_PREFIX);
    }

    /** Encodes an internal bind as a slot target string. */
    public static String encodeBind(GamepadBinds.Bind bind) {
        return BIND_PREFIX + bind.name();
    }

    private static GamepadBinds.Bind parseBind(String raw) {
        if (!isBindTarget(raw)) return null;
        try {
            return GamepadBinds.Bind.valueOf(raw.substring(BIND_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Human-readable name for a stored slot target, for UI display — resolves both keybind ids and
     *  "bind:NAME" internal actions to their translated label; "" shows as unassigned. */
    public static Component displayName(String raw) {
        if (raw == null || raw.isEmpty()) return Component.translatable("steampad.bind.slot.empty");
        GamepadBinds.Bind bind = parseBind(raw);
        if (bind != null) return Component.translatable(bind.labelKey);
        return Component.translatable(raw);
    }

    /**
     * Called once per client tick (from InputBindingManager, on both the Steam and fallback paths).
     * Skips entirely only with no world loaded — otherwise resolves the current {@link Context} every
     * tick and dispatches from that layer's map.
     *
     * <p>Each slot has TWO trigger sources, checked every tick:
     * <ol>
     *   <li><b>Steam Input digital action</b> (steampad_slot_N) — the VDF path; only live when the mod
     *       attached to Steam and the ActionSets are valid.</li>
     *   <li><b>Keyboard key F13..F22</b> (slot N ↔ F(12+N)) — the universal path: in Steam's controller
     *       layout the user maps a paddle to the F-key and Steam emits it as a real key press. Works in
     *       Game Mode WITHOUT attaching to Steam (no AppID/VDF needed, no controller seizure — B038),
     *       and on any OS. F13+ never collides with a physical keyboard in practice.</li>
     * </ol>
     */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Context ctx = currentContext(mc);

        // Activate the ActionSet matching the current context on EVERY connected Steam controller,
        // every tick (Valve's own recommended cadence — the call is cheap and idempotent). This is
        // the single place set activation happens now: the previous call site in InputBindingManager
        // sat AFTER its fallback early-return, so on the Game-Mode hybrid path (active controller =
        // SDL3 handle — the project's actual target environment) it NEVER ran, and with no active
        // set Steam Input reports NO data for any action. That was the real reason every button the
        // user bound to an action in Steam's configurator did nothing (B074 → B075).
        activateContextSets(ctx);

        if (mc.player == null) {
            releaseAll();
            clearSteamAnalog();
            return;
        }
        ControllerState steamState = steamStateOrNull();
        dispatchNamedActions(steamState);
        updateSteamAnalog(steamState);
        long window = windowHandle(mc);
        Map<String, String> slots = mapFor(ctx);
        int base = ControllerState.DigitalAction.SLOT_1.ordinal();
        ControllerState.DigitalAction[] actions = ControllerState.DigitalAction.values();

        for (int i = 0; i < SLOT_COUNT; i++) {
            boolean pressed = steamState != null && steamState.isPressed(actions[base + i]);
            if (!pressed && window != 0L) {
                pressed = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_F13 + i)
                        == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            }
            if (down[i] && pressed && heldContext[i] != ctx) {
                // Context changed while the slot is physically still held (e.g. opened the inventory
                // mid-hold) — release whatever the OLD context was doing and re-resolve fresh under the
                // new one immediately, instead of requiring a physical release+re-press.
                releaseSlot(i);
                applyTarget(i, slots.get(configKey(i)), ctx);
                continue;
            }
            if (pressed == down[i]) continue;
            down[i] = pressed;
            if (pressed) {
                applyTarget(i, slots.get(configKey(i)), ctx);
            } else {
                releaseSlot(i);
            }
        }
    }

    private static void applyTarget(int i, String target, Context ctx) {
        heldContext[i] = ctx;
        if (target == null || target.isEmpty()) return;   // unassigned in this context — silent no-op
        GamepadBinds.Bind bind = parseBind(target);
        if (bind != null) {
            heldBind[i] = bind;
            VirtualBindInput.setHeld(bind, true);
            return;
        }
        KeyMapping kb = findKeybind(target);
        if (kb == null) return;   // unknown keybind id (mod no longer installed) — silent no-op
        heldKb[i] = kb;
        heldKey[i] = KeyTap.hold(kb);
    }

    /** Activates the ActionSet for {@code ctx} on every Steam-connected controller (see tick()). */
    private static void activateContextSets(Context ctx) {
        if (!SteamInputManager.areActionSetsValid()) return;
        var steamControllers = SteamInputManager.getConnectedControllers();
        if (steamControllers.isEmpty()) return;
        for (var ref : steamControllers) {
            SteamActionRegistry.activateSetFor(ctx, ref.handle);
        }
        if (lastActivatedCtx != ctx) {
            lastActivatedCtx = ctx;
            LogUtil.info("[SteamPad] Steam Input action set activated: {} ({} controller(s)).",
                    ctx, steamControllers.size());
        }
    }

    /**
     * Bridges every named gameplay action (steampad_jump, steampad_attack, …) into
     * {@link VirtualBindInput}'s ACTION source — refreshed per tick with the live pressed state, so
     * hold semantics (mine while held, radial stays open, …) work exactly like a physical button.
     * A null state (Steam path down) releases everything, never leaves an action stuck.
     */
    private static void dispatchNamedActions(ControllerState steamState) {
        boolean logged = false;
        for (var e : ACTION_TO_BIND.entrySet()) {
            boolean pressed = steamState != null && steamState.isPressed(e.getKey());
            VirtualBindInput.setHeld(e.getValue(), pressed, VirtualBindInput.Source.ACTION);
            if (pressed && !loggedFirstActionData && !logged) {
                logged = true;
                loggedFirstActionData = true;
                LogUtil.info("[SteamPad] First live Steam Input action data received ({} → {}) — "
                        + "set activation + Steam bindings CONFIRMED working this session.",
                        e.getKey(), e.getValue());
            }
        }
    }

    /** Refreshes the bridged analog stick values (SDL convention: Y+ = down — Steam reports Y+ = up,
     *  so Y is flipped here once, at the boundary). */
    private static void updateSteamAnalog(ControllerState steamState) {
        if (steamState == null) {
            clearSteamAnalog();
            return;
        }
        steamLeftX = steamState.leftStick[0];
        steamLeftY = -steamState.leftStick[1];
        steamRightX = steamState.rightStick[0];
        steamRightY = -steamState.rightStick[1];
    }

    private static void clearSteamAnalog() {
        steamLeftX = steamLeftY = steamRightX = steamRightY = 0f;
    }

    /** Live Steam Input state for the slot source controller, or null when the Steam path is down. */
    private static ControllerState steamStateOrNull() {
        if (!SteamInputManager.areActionSetsValid()) return null;
        long source = resolveSourceHandle();
        if (source == 0L) return null;
        return SteamInputManager.getState(source).orElse(null);
    }

    /** GLFW window handle for keyboard polling (0 when the window isn't up yet). Never throws. */
    private static long windowHandle(Minecraft mc) {
        try {
            return mc.getWindow() != null
                    ? dev.steampad.compat.mc.WindowCompat.handle(mc.getWindow()) : 0L;
        } catch (Throwable t) {
            return 0L;
        }
    }

    /**
     * The Steam controller whose slot actions we read: the active controller when Steam Input serves
     * it directly; otherwise (fallback active — the Game-Mode hybrid) the first Steam-connected
     * controller, which is the same physical device seen through Steam's side. Only one controller
     * is active per instance by design, so "first" is unambiguous in practice.
     */
    private static long resolveSourceHandle() {
        long active = ActiveControllerService.getActiveHandle();
        if (active != 0L && !ControllerManager.isFallbackHandle(active)) return active;
        if (active == 0L) return 0L;   // no active controller → slots stay silent (isolation)
        var steamControllers = SteamInputManager.getConnectedControllers();
        return steamControllers.isEmpty() ? 0L : steamControllers.get(0).handle;
    }

    /** Looks up a keybind by id in the live registry. Null when unset, unknown, or too early. */
    private static KeyMapping findKeybind(String keybindId) {
        if (keybindId == null || keybindId.isEmpty()) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return null;
        for (KeyMapping kb : mc.options.keyMappings) {
            if (kb.getName().equals(keybindId)) return kb;
        }
        LogUtil.debug("Steam slot keybind not found: {}", keybindId);
        return null;
    }

    private static void releaseSlot(int i) {
        if (heldBind[i] != null) {
            VirtualBindInput.setHeld(heldBind[i], false);
            heldBind[i] = null;
        }
        if (heldKb[i] != null || heldKey[i] != null) {
            KeyTap.release(heldKb[i], heldKey[i]);
            heldKb[i] = null;
            heldKey[i] = null;
        }
    }

    /** Releases every held slot target (screen opened, controller lost, Steam gone) — and every
     *  bridged named action, so nothing can stay virtually held with no source refreshing it. */
    public static void releaseAll() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            down[i] = false;
            releaseSlot(i);
        }
        for (GamepadBinds.Bind b : ACTION_TO_BIND.values()) {
            VirtualBindInput.setHeld(b, false, VirtualBindInput.Source.ACTION);
        }
        clearSteamAnalog();
    }
}
