package dev.steampad.client.keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Key definitions for the virtual keyboard, as rows of {@link Key}. Two layers — letters and symbols —
 * with Shift handled by the controller (letters are upper-cased on emit/render), mirroring how phone
 * keyboards (Gboard/SwiftKey) keep a compact grid. The QWERTY arrangement is a functional standard.
 */
public final class KeyboardLayout {

    public enum KeyType { CHAR, SHIFT, BACKSPACE, ENTER, SPACE, LAYER }

    /** One key. {@code units} is the relative width for rendering (space is wide). */
    public record Key(KeyType type, String label, int codepoint, int units) {
        static Key ch(char c) { return new Key(KeyType.CHAR, String.valueOf(c), c, 1); }
        static Key sp(KeyType t, String label, int units) { return new Key(t, label, 0, units); }
    }

    private KeyboardLayout() {}

    private static List<Key> row(Object... cells) {
        List<Key> r = new ArrayList<>();
        for (Object o : cells) {
            if (o instanceof Key k) r.add(k);
            else if (o instanceof String s) for (char c : s.toCharArray()) r.add(Key.ch(c));
        }
        return r;
    }

    private static final Key SHIFT = Key.sp(KeyType.SHIFT, "⇧", 1);
    private static final Key BACK  = Key.sp(KeyType.BACKSPACE, "⌫", 1);
    private static final Key ENTER = Key.sp(KeyType.ENTER, "⏎", 2);
    private static final Key SPACE = Key.sp(KeyType.SPACE, "␣", 5);
    private static final Key TO_SYM = Key.sp(KeyType.LAYER, "?123", 2);
    private static final Key TO_ABC = Key.sp(KeyType.LAYER, "ABC", 2);

    /** Lower-case letters layer (Shift up-cases letters on emit). */
    public static List<List<Key>> letters() {
        List<List<Key>> g = new ArrayList<>();
        g.add(row("1234567890"));
        g.add(row("qwertyuiop"));
        g.add(row("asdfghjkl"));
        g.add(row(SHIFT, Key.ch('z'), Key.ch('x'), Key.ch('c'), Key.ch('v'),
                Key.ch('b'), Key.ch('n'), Key.ch('m'), BACK));
        g.add(row(TO_SYM, Key.ch(','), SPACE, Key.ch('.'), ENTER));
        return g;
    }

    /** Symbols / punctuation layer. */
    public static List<List<Key>> symbols() {
        List<List<Key>> g = new ArrayList<>();
        g.add(row("1234567890"));
        g.add(row(Key.ch('@'), Key.ch('#'), Key.ch('$'), Key.ch('%'), Key.ch('&'),
                Key.ch('*'), Key.ch('-'), Key.ch('+'), Key.ch('('), Key.ch(')')));
        g.add(row(Key.ch('!'), Key.ch('"'), Key.ch('\''), Key.ch(':'), Key.ch(';'),
                Key.ch('/'), Key.ch('?'), Key.ch('\\'), Key.ch('|')));
        g.add(row(Key.ch('_'), Key.ch('='), Key.ch('<'), Key.ch('>'), Key.ch('['),
                Key.ch(']'), Key.ch('{'), Key.ch('}'), BACK));
        g.add(row(TO_ABC, Key.ch(','), SPACE, Key.ch('.'), ENTER));
        return g;
    }
}
