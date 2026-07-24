package dev.steampad.input;

import dev.steampad.steam.SteamControllerHandleRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.steampad.input.ControllerState.DigitalAction.*;
import static org.junit.jupiter.api.Assertions.*;

class ChordResolverTest {

    private ChordResolver resolver;
    private static final SteamControllerHandleRef REF =
        new SteamControllerHandleRef(1L, "Test Controller", SteamControllerHandleRef.ControllerType.GENERIC);

    @BeforeEach
    void setup() {
        resolver = new ChordResolver();
    }

    /** Build a ControllerState with only the given buttons pressed. */
    private ControllerState stateWith(ControllerState.DigitalAction... pressed) {
        boolean[] digital = new boolean[ControllerState.DigitalAction.values().length];
        for (var a : pressed) digital[a.ordinal()] = true;
        return new ControllerState(REF, digital,
            new float[]{0,0}, new float[]{0,0}, new float[]{0,0}, new float[]{0,0}, -1f);
    }

    private ControllerState emptyState() {
        return stateWith();
    }

    @Test
    void testSimpleBindingFiresWhenPressed() {
        var binding = InputBinding.simple(InputAction.JUMP, JUMP);
        var bindings = List.of(binding);

        // First tick: nothing pressed
        resolver.resolve(emptyState(), bindings);
        // Second tick: JUMP newly pressed
        List<InputAction> fired = resolver.resolve(stateWith(JUMP), bindings);

        assertTrue(fired.contains(InputAction.JUMP), "JUMP should fire when pressed");
    }

    @Test
    void testSimpleBindingDoesNotFireWhenHeld() {
        var binding = InputBinding.simple(InputAction.JUMP, JUMP);
        var bindings = List.of(binding);

        // Tick 1: press
        resolver.resolve(stateWith(JUMP), bindings);
        // Tick 2: still held
        List<InputAction> fired = resolver.resolve(stateWith(JUMP), bindings);

        assertFalse(fired.contains(InputAction.JUMP), "JUMP should not re-fire while held");
    }

    @Test
    void testChordPreventsSimpleBindingForMain() {
        var chord = new ChordInput(SNEAK, JUMP);
        var chordBinding = InputBinding.chord(InputAction.INVENTORY, chord);
        var simpleJump = InputBinding.simple(InputAction.JUMP, JUMP);
        var bindings = List.of(chordBinding, simpleJump);

        // Tick 1: press SNEAK (modifier held)
        resolver.resolve(stateWith(SNEAK), bindings);
        // Tick 2: SNEAK held, JUMP newly pressed → chord fires
        List<InputAction> fired = resolver.resolve(stateWith(SNEAK, JUMP), bindings);

        assertTrue(fired.contains(InputAction.INVENTORY), "Chord INVENTORY should fire");
        assertFalse(fired.contains(InputAction.JUMP), "Simple JUMP must be suppressed by chord");
    }

    @Test
    void testSimpleFiresWhenModifierNotHeld() {
        var chord = new ChordInput(SNEAK, JUMP);
        var chordBinding = InputBinding.chord(InputAction.INVENTORY, chord);
        var simpleJump = InputBinding.simple(InputAction.JUMP, JUMP);
        var bindings = List.of(chordBinding, simpleJump);

        // Tick 1: nothing
        resolver.resolve(emptyState(), bindings);
        // Tick 2: only JUMP (no modifier)
        List<InputAction> fired = resolver.resolve(stateWith(JUMP), bindings);

        assertTrue(fired.contains(InputAction.JUMP), "JUMP should fire without modifier");
        assertFalse(fired.contains(InputAction.INVENTORY), "Chord should not fire without modifier");
    }

    @Test
    void testChordNotFiredWhenMainAlreadyHeld() {
        // Chord should only fire when MAIN is *newly* pressed while modifier is held.
        // If JUMP was already held from a previous tick, chord should not re-fire.
        var chord = new ChordInput(SNEAK, JUMP);
        var chordBinding = InputBinding.chord(InputAction.INVENTORY, chord);
        var bindings = List.of(chordBinding);

        // Tick 1: JUMP held before SNEAK — JUMP is now "old"
        resolver.resolve(stateWith(JUMP), bindings);
        // Tick 2: SNEAK also held — JUMP is not "newly pressed"
        List<InputAction> fired = resolver.resolve(stateWith(SNEAK, JUMP), bindings);

        assertFalse(fired.contains(InputAction.INVENTORY), "Chord should not fire if main was already held");
    }

    @Test
    void testResetClearsState() {
        var binding = InputBinding.simple(InputAction.JUMP, JUMP);
        var bindings = List.of(binding);

        // Hold JUMP
        resolver.resolve(stateWith(JUMP), bindings);
        // Reset
        resolver.reset();
        // After reset, JUMP should be treated as newly pressed again
        List<InputAction> fired = resolver.resolve(stateWith(JUMP), bindings);

        assertTrue(fired.contains(InputAction.JUMP), "After reset, JUMP should fire again");
    }

    @Test
    void testMultipleChordsWithSameModifier() {
        var chord1 = new ChordInput(SNEAK, JUMP);
        var chord2 = new ChordInput(SNEAK, ATTACK);
        var b1 = InputBinding.chord(InputAction.INVENTORY, chord1);
        var b2 = InputBinding.chord(InputAction.OPEN_CHAT, chord2);
        var bindings = List.of(b1, b2);

        // Hold SNEAK
        resolver.resolve(stateWith(SNEAK), bindings);
        // Press ATTACK only
        List<InputAction> fired = resolver.resolve(stateWith(SNEAK, ATTACK), bindings);

        assertTrue(fired.contains(InputAction.OPEN_CHAT), "SNEAK+ATTACK chord should fire");
        assertFalse(fired.contains(InputAction.INVENTORY), "SNEAK+JUMP chord should NOT fire");
    }
}
