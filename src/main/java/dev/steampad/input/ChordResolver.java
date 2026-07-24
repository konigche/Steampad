package dev.steampad.input;

import java.util.*;

/**
 * Resolves chord bindings with suppression of simple bindings when a chord fires.
 *
 * Algorithm (called once per tick with the current controller state):
 * 1. Build the set of currently held buttons.
 * 2. Detect newly pressed buttons (pressed this tick, not held last tick).
 * 3. For each newly pressed button, check if any chord with that as 'main' has its modifier held.
 * 4. If chord found → mark chord as fired, suppress the 'main' simple binding for this tick.
 * 5. Process remaining simple bindings that weren't suppressed.
 */
public final class ChordResolver {

    private final Set<ControllerState.DigitalAction> prevHeld = new HashSet<>();
    private final Set<ControllerState.DigitalAction> suppressedThisTick = new HashSet<>();

    /**
     * Resolves which bindings should fire this tick.
     *
     * @param currentState  current controller state snapshot
     * @param allBindings   all configured bindings (simple + chord)
     * @return list of InputActions that should execute this tick
     */
    public List<InputAction> resolve(ControllerState currentState, List<InputBinding> allBindings) {
        Set<ControllerState.DigitalAction> held = buildHeldSet(currentState);
        Set<ControllerState.DigitalAction> newlyPressed = buildNewlyPressed(held, prevHeld);
        suppressedThisTick.clear();

        List<InputAction> toFire = new ArrayList<>();

        // Pass 1: resolve chords first (higher priority)
        for (InputBinding binding : allBindings) {
            if (!binding.isChord()) continue;
            ChordInput chord = binding.chord;
            // Chord fires if: modifier is held AND main is newly pressed
            if (held.contains(chord.modifier) && newlyPressed.contains(chord.main)) {
                toFire.add(binding.action);
                suppressedThisTick.add(chord.main);  // suppress simple binding for main
            }
        }

        // Pass 2: resolve simple bindings, skipping suppressed ones
        for (InputBinding binding : allBindings) {
            if (!binding.isSimple()) continue;
            if (suppressedThisTick.contains(binding.simpleButton)) continue;
            if (newlyPressed.contains(binding.simpleButton)) {
                toFire.add(binding.action);
            }
        }

        prevHeld.clear();
        prevHeld.addAll(held);
        return toFire;
    }

    private static Set<ControllerState.DigitalAction> buildHeldSet(ControllerState state) {
        Set<ControllerState.DigitalAction> held = new HashSet<>();
        for (ControllerState.DigitalAction action : ControllerState.DigitalAction.values()) {
            if (state.isPressed(action)) held.add(action);
        }
        return held;
    }

    private static Set<ControllerState.DigitalAction> buildNewlyPressed(
            Set<ControllerState.DigitalAction> current,
            Set<ControllerState.DigitalAction> previous) {
        Set<ControllerState.DigitalAction> newlyPressed = new HashSet<>(current);
        newlyPressed.removeAll(previous);
        return newlyPressed;
    }

    /** Reset internal state. Call when the controller changes or on screen transition. */
    public void reset() {
        prevHeld.clear();
        suppressedThisTick.clear();
    }

    /** Returns the set of buttons suppressed during last resolve call (for debug). */
    public Set<ControllerState.DigitalAction> getLastSuppressed() {
        return Collections.unmodifiableSet(suppressedThisTick);
    }
}
