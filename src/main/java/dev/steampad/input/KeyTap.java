package dev.steampad.input;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.KeyMapping;

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

    private record Pulse(KeyMapping kb, InputConstants.Key key, int[] ticksLeft) {}

    private static final List<Pulse> active = new ArrayList<>();

    private KeyTap() {}

    /** Trigger a keybind: press it now, release after a short hold. Safe with bound or unbound keys. */
    public static void press(KeyMapping kb) {
        if (kb == null) return;
        InputConstants.Key key = hold(kb);
        active.add(new Pulse(kb, key, new int[]{HOLD_TICKS}));
    }

    /**
     * Press-and-HOLD a keybind (caller must call {@link #release}). Serves both consumer styles:
     * {@code isPressed()} (held actions like zoom mods) and {@code wasPressed()} (taps). Works for
     * unbound mod keybinds by driving the binding's own pressed state. Returns the physical key used
     * (null when unbound) so the caller can pass it back to release.
     */
    public static InputConstants.Key hold(KeyMapping kb) {
        if (kb == null) return null;
        InputConstants.Key key = InputConstants.getKey(kb.saveString());
        boolean bound = key != null && key != InputConstants.UNKNOWN;
        // Held state is written on the KeyMapping INSTANCE, never through KeyMapping.set(key, …).
        // javap on the real 1.21.1 jar: that static form is `MAP.get(key).setDown(held)`, and MAP is
        // built by resetMapping() as `for (km : ALL.values()) MAP.put(km.key, km)` — ONE mapping per
        // physical key, last writer wins. So when two keybinds share a key (a real, common case in a
        // modpack: this was found with `key.sprint` AND `key.jurassicsaga.swap_seat` both on Left
        // Control in the reporter's options.txt), the static form drives whichever of them happens to
        // have won that map slot — an arbitrary, hash-order-dependent choice this mod does not control,
        // and quite possibly not the keybind the caller asked for at all. Every caller here already
        // holds a direct reference to the mapping it means, so use it: setDown() on the instance is
        // unambiguous, and it is exactly the field consumers read back via isDown().
        kb.setDown(true);
        // TAP-style consumers (consumeClick()) get their click count raised ON THE INSTANCE, via
        // KeyMappingClickAccessor. This is the half that used to REQUIRE a physical key: the only
        // vanilla way to raise the counter is the static KeyMapping.click(Key), which looks the
        // mapping up by key in a map an UNBOUND keybind is simply not in — so "open my backpack"-style
        // mod actions (the common consumeClick() shape) silently did nothing unless the user had also
        // bound a keyboard key. Writing the field directly reaches every registered keybind, bound or
        // not, and drops the old shared-key ambiguity at the same time (the static form fires whichever
        // mapping won that key's map slot, which need not be the one the caller meant). See the
        // accessor's own doc for the javap evidence. Sprint is unaffected either way: vanilla drives it
        // purely off isDown().
        ((dev.steampad.mixin.KeyMappingClickAccessor) (Object) kb).steampad$setClickCount(
                ((dev.steampad.mixin.KeyMappingClickAccessor) (Object) kb).steampad$getClickCount() + 1);
        return bound ? key : null;
    }

    /**
     * Re-asserts the held state of a keybind already pressed with {@link #hold}, WITHOUT registering a
     * new click. For repairing a hold that something else cleared behind our back: vanilla's
     * {@code Minecraft.setScreen} calls {@code KeyMapping.releaseAll()} on every screen open
     * (javap-confirmed), which zeroes {@code isDown} on every mapping in the game — including one this
     * class is deliberately holding down for a controller.
     *
     * <p><b>Caller must check {@code isDown()} first and only call this when it is false.</b> Some
     * vanilla keybinds are {@code ToggleKeyMapping} (sprint and sneak, gated on the game's own
     * "Toggle Sprint"/"Toggle Sneak" options): when that option is on, their {@code setDown(true)}
     * FLIPS the state instead of setting it (javap-confirmed), so re-asserting unconditionally every
     * tick would strobe the key on and off rather than hold it.
     *
     * <p>No {@code click()} here on purpose — that increments the press count {@code consumeClick()}
     * consumers read, and a repair is not a new press by the player.
     */
    public static void reassert(KeyMapping kb, InputConstants.Key key) {
        if (kb != null) kb.setDown(true);
        else if (key != null) KeyMapping.set(key, true);
    }

    /**
     * Releases a keybind previously pressed with {@link #hold}. Prefers the instance for the same
     * shared-key reason documented on {@link #hold} — releasing through the key could clear a
     * DIFFERENT mapping and leave the intended one stuck down forever.
     */
    public static void release(KeyMapping kb, InputConstants.Key key) {
        if (kb != null) kb.setDown(false);
        else if (key != null) KeyMapping.set(key, false);
    }

    /** Release any keybinds whose hold has elapsed. Call once per client tick. */
    public static void tick() {
        if (active.isEmpty()) return;
        for (Iterator<Pulse> it = active.iterator(); it.hasNext(); ) {
            Pulse p = it.next();
            if (--p.ticksLeft()[0] > 0) continue;
            release(p.kb(), p.key());   // instance-first, same shared-key reason as hold()
            it.remove();
        }
    }
}
