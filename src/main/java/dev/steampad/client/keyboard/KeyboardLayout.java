package dev.steampad.client.keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Key definitions for the virtual keyboard, as rows of {@link Key}. Two layers — letters and symbols —
 * with Shift handled by the controller (letters are upper-cased on emit/render), mirroring how phone
 * keyboards (Gboard/SwiftKey) keep a compact grid. The QWERTY arrangement is a functional standard.
 */
public final class KeyboardLayout {

    public enum KeyType { CHAR, SHIFT, BACKSPACE, ENTER, SPACE, LAYER, SELECT_ALL, COPY, PASTE, ARROW }

    /**
     * One key. {@code units} is the relative width for rendering (space is wide).
     *
     * <p>{@code labelKey} is an optional translation key: the symbol keys draw a fixed glyph, but the
     * clipboard keys spell a word, and a word has to follow the game's language. It is resolved at
     * DRAW time rather than here, because these grids are built once when the class loads — possibly
     * before the language files are.
     *
     * <p>{@code codepoint} carries the character to type for {@link KeyType#CHAR}, and the raw GLFW
     * key code for {@link KeyType#ARROW} — the only two types that need a value at all, and they are
     * never both live on the same key, so one field serves both (the renderer reads neither: it draws
     * from {@code label}/{@code labelKey}).
     */
    public record Key(KeyType type, String label, int codepoint, int units, String labelKey) {
        /** Fixed-glyph key (every key that existed before the clipboard row). */
        public Key(KeyType type, String label, int codepoint, int units) {
            this(type, label, codepoint, units, null);
        }

        static Key ch(char c) { return new Key(KeyType.CHAR, String.valueOf(c), c, 1); }
        static Key sp(KeyType t, String label, int units) { return new Key(t, label, 0, units); }
        /** Word key whose caption is translated — see {@link #labelKey()}. */
        static Key word(KeyType t, String translationKey, int units) {
            return new Key(t, "", 0, units, translationKey);
        }
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

    // Clipboard keys, on the number row of BOTH layers (feedback: "agrega botones en la parte de
    // numeros de selecionar todo, copiar y pegar y que respondan al portapapeles del sistema operativo
    // en curso"). One unit each, like the digits, so the row stays evenly divided instead of squeezing
    // the numbers to make room. See ClipboardActions for how they reach the focused field.
    private static final Key SELECT_ALL = Key.word(KeyType.SELECT_ALL, "steampad.keyboard.key.select_all", 1);
    private static final Key COPY = Key.word(KeyType.COPY, "steampad.keyboard.key.copy", 1);
    private static final Key PASTE = Key.word(KeyType.PASTE, "steampad.keyboard.key.paste", 1);

    // Caret/navigation arrows, on the symbols layer (feedback: "agrega en el area de simbolos las
    // flechas del teclado para que se pueda navegar"). They send the real GLFW arrow keys through the
    // same key pipeline as Backspace/Enter/Tab, so they do whatever the focused thing already does
    // with them — move the caret inside a text field, move the selection in a list, page a book.
    // Glyphs are U+2190..2193, the same extended-glyph family the existing ⇧ ⌫ ⏎ ␣ keys already use.
    private static final Key ARROW_LEFT  = new Key(KeyType.ARROW, "←", org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT, 1);
    private static final Key ARROW_DOWN  = new Key(KeyType.ARROW, "↓", org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, 1);
    private static final Key ARROW_UP    = new Key(KeyType.ARROW, "↑", org.lwjgl.glfw.GLFW.GLFW_KEY_UP, 1);
    private static final Key ARROW_RIGHT = new Key(KeyType.ARROW, "→", org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 1);

    /** The digits plus the three clipboard keys — the top row of every layer. */
    private static List<Key> numberRow() {
        return row("1234567890", SELECT_ALL, COPY, PASTE);
    }

    /** Lower-case letters layer (Shift up-cases letters on emit). */
    public static List<List<Key>> letters() {
        List<List<Key>> g = new ArrayList<>();
        g.add(numberRow());
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
        g.add(numberRow());
        g.add(row(Key.ch('@'), Key.ch('#'), Key.ch('$'), Key.ch('%'), Key.ch('&'),
                Key.ch('*'), Key.ch('-'), Key.ch('+'), Key.ch('('), Key.ch(')')));
        // 9 punctuation keys + the 4 arrows = 13 units, exactly the width of the number row above, so
        // the arrows fit without adding a sixth row (a taller symbols panel would make the whole
        // keyboard — and the chat/sign content pushed above it — jump on every layer switch).
        g.add(row(Key.ch('!'), Key.ch('"'), Key.ch('\''), Key.ch(':'), Key.ch(';'),
                Key.ch('/'), Key.ch('?'), Key.ch('\\'), Key.ch('|'),
                ARROW_LEFT, ARROW_DOWN, ARROW_UP, ARROW_RIGHT));
        g.add(row(Key.ch('_'), Key.ch('='), Key.ch('<'), Key.ch('>'), Key.ch('['),
                Key.ch(']'), Key.ch('{'), Key.ch('}'), BACK));
        g.add(row(TO_ABC, Key.ch(','), SPACE, Key.ch('.'), ENTER));
        return g;
    }
}
