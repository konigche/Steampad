package dev.steampad.input;

import java.util.Objects;

/**
 * Defines a chord binding: a modifier button held while pressing a main button.
 * Visual representation: [modifier] + [main]
 */
public final class ChordInput {

    public final ControllerState.DigitalAction modifier;
    public final ControllerState.DigitalAction main;

    public ChordInput(ControllerState.DigitalAction modifier, ControllerState.DigitalAction main) {
        if (modifier == null || main == null)
            throw new IllegalArgumentException("ChordInput requires non-null modifier and main");
        if (modifier == main)
            throw new IllegalArgumentException("ChordInput modifier and main must be different buttons");
        this.modifier = modifier;
        this.main = main;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChordInput c)) return false;
        return modifier == c.modifier && main == c.main;
    }

    @Override
    public int hashCode() {
        return Objects.hash(modifier, main);
    }

    @Override
    public String toString() {
        return "[" + modifier.name() + "] + [" + main.name() + "]";
    }
}
