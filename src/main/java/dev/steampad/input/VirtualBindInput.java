package dev.steampad.input;

import java.util.EnumMap;
import java.util.Map;

/**
 * Lets a non-physical input source trigger any {@link GamepadBinds.Bind} exactly as if a physical
 * button were pressed, by feeding into the SAME {@code bHeld}/{@code bPressed} resolution every
 * physical bind already goes through (see {@link GamepadInputDispatcher}). A virtual press reaches
 * the exact same code a physical press would, so it behaves identically (same held/tap semantics,
 * same downstream effect) with zero changes to how physical binds are resolved.
 *
 * <p>Two independent sources feed this (OR-ed together, so neither can stomp the other's state):
 * <ul>
 *   <li>{@link Source#SLOT} — a Steam Input slot the user assigned to an internal action
 *       ({@link SteamSlotDispatcher}, "assign a slot to Menú Radial/Zoom").</li>
 *   <li>{@link Source#ACTION} — a named Steam Input action (steampad_jump, steampad_attack, …) the
 *       user bound DIRECTLY to a button/paddle in Steam's controller configurator. Bridged by
 *       {@link SteamSlotDispatcher} on the Game-Mode hybrid path, where the Steam ActionSet pipeline
 *       in InputBindingManager never runs (the active controller is an SDL3/GLFW fallback handle) —
 *       without this bridge those bindings read as data in {@code ControllerState} that nobody
 *       dispatches, which is exactly why "asigné un paddle a Saltar en Steam y no hace nada" (B074).</li>
 * </ul>
 */
final class VirtualBindInput {

    enum Source { SLOT, ACTION }

    private static final Map<GamepadBinds.Bind, Boolean> heldSlot = new EnumMap<>(GamepadBinds.Bind.class);
    private static final Map<GamepadBinds.Bind, Boolean> heldAction = new EnumMap<>(GamepadBinds.Bind.class);
    private static final Map<GamepadBinds.Bind, Boolean> heldPrevTick = new EnumMap<>(GamepadBinds.Bind.class);

    private VirtualBindInput() {}

    /** Sets whether {@code bind} is virtually held right now by the SLOT source (call on the slot's
     *  press/release edges — {@link SteamSlotDispatcher}'s hold semantics). */
    static void setHeld(GamepadBinds.Bind bind, boolean down) {
        heldSlot.put(bind, down);
    }

    /** Sets whether {@code bind} is virtually held by {@code source}. SLOT and ACTION are independent
     *  maps so a per-tick ACTION refresh can never release a press a SLOT is still holding. */
    static void setHeld(GamepadBinds.Bind bind, boolean down, Source source) {
        (source == Source.SLOT ? heldSlot : heldAction).put(bind, down);
    }

    static boolean isHeld(GamepadBinds.Bind bind) {
        return heldSlot.getOrDefault(bind, false) || heldAction.getOrDefault(bind, false);
    }

    /** True on the tick a virtual hold STARTS — the same press-edge semantics {@code bPressed} uses
     *  for a physical button. */
    static boolean isPressedEdge(GamepadBinds.Bind bind) {
        return isHeld(bind) && !heldPrevTick.getOrDefault(bind, false);
    }

    /** Advances the edge-detection snapshot. Called once per tick from
     *  {@link GamepadInputDispatcher#tick}, mirroring how it snapshots physical button state. */
    static void endTick() {
        heldPrevTick.clear();
        for (GamepadBinds.Bind b : GamepadBinds.Bind.values()) {
            boolean h = isHeld(b);
            if (h) heldPrevTick.put(b, true);
        }
    }
}
