package dev.steampad.input;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Robust "press a keybind for a moment, then release it" helper.
 *
 * <p>Used by the radial menu (and anywhere a controller action must trigger an arbitrary keybind).
 * A plain {@code KeyBinding.onKeyPressed} only satisfies consumers that poll {@code wasPressed()}; held
 * actions poll {@code isPressed()} and need the key actually held for a tick. This helper covers both:
 * it presses on {@link #press}, holds for a couple of ticks, then releases via {@link #tick} (driven
 * from the client tick). It also works for <em>unbound</em> mod keybinds by driving the binding's own
 * pressed state directly (no physical key to route through).
 */
public final class KeyTap {

    private static final int HOLD_TICKS = 2;

    private record Pulse(KeyBinding kb, InputUtil.Key key, int[] ticksLeft) {}

    private static final List<Pulse> active = new ArrayList<>();

    private KeyTap() {}

    /** Trigger a keybind: press it now, release after a short hold. Safe with bound or unbound keys. */
    public static void press(KeyBinding kb) {
        if (kb == null) return;
        InputUtil.Key key = hold(kb);
        active.add(new Pulse(kb, key, new int[]{HOLD_TICKS}));
    }

    /**
     * Press-and-HOLD a keybind (caller must call {@link #release}). Serves both consumer styles:
     * {@code isPressed()} (held actions like zoom mods) and {@code wasPressed()} (taps). Works for
     * unbound mod keybinds by driving the binding's own pressed state. Returns the physical key used
     * (null when unbound) so the caller can pass it back to release.
     */
    public static InputUtil.Key hold(KeyBinding kb) {
        if (kb == null) return null;
        InputUtil.Key key = InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey());
        boolean bound = key != null && key != InputUtil.UNKNOWN_KEY;
        if (bound) {
            KeyBinding.setKeyPressed(key, true);   // held state for isPressed() consumers
            KeyBinding.onKeyPressed(key);          // press count for wasPressed() consumers
        } else {
            kb.setPressed(true);                   // unbound mod keybind: drive it directly
        }
        return bound ? key : null;
    }

    /** Releases a keybind previously pressed with {@link #hold}. */
    public static void release(KeyBinding kb, InputUtil.Key key) {
        if (key != null) KeyBinding.setKeyPressed(key, false);
        else if (kb != null) kb.setPressed(false);
    }

    /** Release any keybinds whose hold has elapsed. Call once per client tick. */
    public static void tick() {
        if (active.isEmpty()) return;
        for (Iterator<Pulse> it = active.iterator(); it.hasNext(); ) {
            Pulse p = it.next();
            if (--p.ticksLeft()[0] > 0) continue;
            if (p.key() != null) KeyBinding.setKeyPressed(p.key(), false);
            else p.kb().setPressed(false);
            it.remove();
        }
    }
}
