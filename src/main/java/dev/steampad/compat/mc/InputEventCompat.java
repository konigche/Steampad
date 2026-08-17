package dev.steampad.compat.mc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.GuiEventListener;

/**
 * Version shim for delivering <em>synthetic</em> input. 1.21.9 reworked the whole input layer: loose
 * parameters became records. Boundary measured with {@code javap} (1.21.8 old, 1.21.10 new), not
 * assumed:
 *
 * <table border="1">
 *   <caption>Signatures per version</caption>
 *   <tr><th></th><th>&le; 1.21.8</th><th>&ge; 1.21.9</th></tr>
 *   <tr><td>{@code keyPressed}</td><td>{@code (int, int, int)}</td><td>{@code (KeyEvent)}</td></tr>
 *   <tr><td>{@code charTyped}</td><td>{@code (char, int)}</td><td>{@code (CharacterEvent)}</td></tr>
 *   <tr><td>{@code mouseClicked}</td><td>{@code (double, double, int)}</td><td>{@code (MouseButtonEvent, boolean)}</td></tr>
 *   <tr><td>{@code AbstractButton.onPress}</td><td>{@code ()}</td><td>{@code (InputWithModifiers)}</td></tr>
 *   <tr><td>{@code MouseHandler} button</td><td>{@code onPress(long, int, int, int)}</td><td>{@code onButton(long, MouseButtonInfo, int)}</td></tr>
 *   <tr><td>{@code KeyboardHandler.keyPress}</td><td>{@code (long, int, int, int, int)}</td><td>{@code (long, int, KeyEvent)}</td></tr>
 *   <tr><td>{@code KeyboardHandler.charTyped}</td><td>{@code (long, int, int)}</td><td>{@code (long, CharacterEvent)}</td></tr>
 * </table>
 *
 * <p>Everything here takes plain primitives, so call sites never name a version-specific type. The
 * {@code MouseHandler}/{@code KeyboardHandler} entries need access widening, which diverges per
 * version — that lives in the per-variant {@code modstitch.ct} files, not here.
 *
 * <p>This shim covers only the "build an event and hand it to vanilla" direction. Methods we
 * <em>override</em> (a {@code Screen}'s own {@code mouseClicked}, a widget's {@code onPress}) cannot
 * be shimmed at all — there the signature <em>is</em> the contract, so those declarations carry
 * Stonecutter conditionals and immediately delegate to version-agnostic logic.
 */
public final class InputEventCompat {

    private InputEventCompat() {}

    /** Delivers a synthetic mouse click to a widget or screen at (x, y). */
    public static boolean mouseClicked(GuiEventListener target, double x, double y, int button) {
        //? if >=1.21.9 {
        return target.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(
                x, y, new net.minecraft.client.input.MouseButtonInfo(button, 0)), false);
        //?} else {
        /*return target.mouseClicked(x, y, button);*/
        //?}
    }

    /** Delivers a synthetic key press to a widget or screen. */
    public static boolean keyPressed(GuiEventListener target, int key, int scancode, int modifiers) {
        //? if >=1.21.9 {
        return target.keyPressed(
                new net.minecraft.client.input.KeyEvent(key, scancode, modifiers));
        //?} else {
        /*return target.keyPressed(key, scancode, modifiers);*/
        //?}
    }

    /**
     * Delivers a synthetic typed character to a widget or screen. Note the narrowing on the old side:
     * pre-1.21.9 {@code charTyped} takes a {@code char}, so codepoints outside the BMP cannot be
     * expressed — that is a limit of the old API itself, not of this shim.
     */
    public static boolean charTyped(GuiEventListener target, int codepoint, int modifiers) {
        //? if >=1.21.9 {
        return target.charTyped(
                new net.minecraft.client.input.CharacterEvent(codepoint, modifiers));
        //?} else {
        /*return target.charTyped((char) codepoint, modifiers);*/
        //?}
    }

    /** Activates a button widget as if it had been clicked. */
    public static void pressButton(AbstractButton button) {
        //? if >=1.21.9 {
        button.onPress(new net.minecraft.client.input.MouseButtonInfo(0, 0));
        //?} else {
        /*button.onPress();*/
        //?}
    }

    /**
     * Routes a synthetic mouse button through the real {@code MouseHandler} callback — the call site
     * Architectury-based mods intercept, which is why this does not just call
     * {@code Screen.mouseClicked}. {@code action} is a GLFW action constant.
     */
    public static void fireMouseButton(Minecraft mc, int button, int action) {
        if (mc.mouseHandler == null) return;
        long window = WindowCompat.handle(mc.getWindow());
        //? if >=1.21.9 {
        mc.mouseHandler.onButton(
                window, new net.minecraft.client.input.MouseButtonInfo(button, 0), action);
        //?} else {
        /*mc.mouseHandler.onPress(window, button, action, 0);*/
        //?}
    }

    /** Routes a synthetic key press through the real {@code KeyboardHandler} callback. */
    public static void fireKeyPress(Minecraft mc, int action, int key, int scancode, int modifiers) {
        if (mc.keyboardHandler == null) return;
        long window = WindowCompat.handle(mc.getWindow());
        //? if >=1.21.9 {
        mc.keyboardHandler.keyPress(window, action,
                new net.minecraft.client.input.KeyEvent(key, scancode, modifiers));
        //?} else {
        /*mc.keyboardHandler.keyPress(window, key, scancode, action, modifiers);*/
        //?}
    }

    /**
     * The modifier bit vanilla's own select-all / copy / cut / paste checks look for (Ctrl, or Cmd on
     * macOS). Pass it as the {@code modifiers} argument of {@link #keyPressed} to synthesize a
     * clipboard shortcut.
     *
     * <p><b>Version note, measured with {@code javap} rather than assumed.</b> From 1.21.9 those
     * checks live on the input record itself — {@code InputWithModifiers.hasControlDown()} is
     * {@code (modifiers() & InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER) != 0} — so a synthetic event
     * carrying this bit is indistinguishable from a real Ctrl+key and every text widget, vanilla or
     * modded, behaves accordingly. Before 1.21.9 the same checks were {@code Screen.hasControlDown()},
     * which POLLS the physical keyboard through {@code InputConstants.isKeyDown}: there, a synthetic
     * modifier is silently ignored, so callers must not rely on this path on the older versions (see
     * {@code ClipboardActions}, which drives vanilla text fields through their own API instead and
     * only uses the event route as a fallback).
     */
    public static int editShortcutModifier() {
        //? if >=1.21.9 {
        return net.minecraft.client.input.InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER;
        //?} else {
        /*return org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;*/
        //?}
    }

    /** Whether a synthetic {@link #editShortcutModifier()} is actually honoured on this version. */
    public static boolean supportsSyntheticEditShortcuts() {
        //? if >=1.21.9 {
        return true;
        //?} else {
        /*return false;*/
        //?}
    }

    /** Routes a synthetic typed character through the real {@code KeyboardHandler} callback. */
    public static void fireCharTyped(Minecraft mc, int codepoint, int modifiers) {
        if (mc.keyboardHandler == null) return;
        long window = WindowCompat.handle(mc.getWindow());
        //? if >=1.21.9 {
        mc.keyboardHandler.charTyped(window,
                new net.minecraft.client.input.CharacterEvent(codepoint, modifiers));
        //?} else {
        /*mc.keyboardHandler.charTyped(window, codepoint, modifiers);*/
        //?}
    }
}
