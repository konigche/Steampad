package dev.steampad.input;

/**
 * Associates a logical InputAction with a controller button or chord.
 * A binding is either simple (single button) or a chord (modifier + main).
 */
public final class InputBinding {

    public final InputAction action;
    public final ControllerState.DigitalAction simpleButton;  // null if chord
    public final ChordInput chord;                            // null if simple

    private InputBinding(InputAction action, ControllerState.DigitalAction simple, ChordInput chord) {
        this.action = action;
        this.simpleButton = simple;
        this.chord = chord;
    }

    public static InputBinding simple(InputAction action, ControllerState.DigitalAction button) {
        return new InputBinding(action, button, null);
    }

    public static InputBinding chord(InputAction action, ChordInput chord) {
        return new InputBinding(action, null, chord);
    }

    public boolean isChord() {
        return chord != null;
    }

    public boolean isSimple() {
        return simpleButton != null;
    }

    @Override
    public String toString() {
        if (isChord()) return action + " → " + chord;
        return action + " → [" + simpleButton + "]";
    }
}
