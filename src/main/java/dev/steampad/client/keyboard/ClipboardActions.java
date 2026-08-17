package dev.steampad.client.keyboard;

import dev.steampad.compat.mc.InputEventCompat;
import dev.steampad.util.LogUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Select-all / copy / paste for the on-screen keyboard, against the <b>operating system's</b>
 * clipboard.
 *
 * <p>There is nothing to invent about the clipboard itself: {@code KeyboardHandler.getClipboard()} /
 * {@code setClipboard()} are GLFW's own {@code glfwGetClipboardString}/{@code glfwSetClipboardString},
 * i.e. the same clipboard every other application on the machine uses. What needed care is reaching
 * the text field, because Ctrl cannot be faked uniformly:
 *
 * <ol>
 *   <li><b>Vanilla {@link EditBox} (chat input, most search boxes, most mod fields that extend it)</b>
 *       — driven through its own public API. Deterministic, identical on every supported Minecraft
 *       version, and it reproduces exactly what vanilla's own Ctrl+A/C/V branches do.</li>
 *   <li><b>Anything else</b> — a synthetic Ctrl+key event through the real input pipeline. This works
 *       from 1.21.9 on, where the modifier travels inside the event record; on older versions vanilla
 *       polls the physical keyboard for Ctrl, so the event route cannot work and paste falls back to
 *       simply typing the clipboard text character by character (which reaches any field that accepts
 *       typing at all, since it is the same path every other key on this keyboard uses).</li>
 * </ol>
 *
 * <p>Every entry point is a no-op on a null/absent field rather than an error: these are keys on a
 * keyboard, and a key that does nothing is much better than one that throws mid-tick.
 */
public final class ClipboardActions {

    private ClipboardActions() {}

    /** Selects the whole contents of the focused field. */
    public static void selectAll(Screen screen) {
        EditBox box = editBox(screen);
        if (box != null) {
            // Exactly what vanilla's own isSelectAll branch does: caret to the end, selection anchor
            // to the start (verified against EditBox.keyPressed's bytecode on both target versions).
            box.setCursorPosition(box.getValue().length());
            box.setHighlightPos(0);
            return;
        }
        sendShortcut(screen, GLFW.GLFW_KEY_A);
    }

    /**
     * Copies the field's selection to the OS clipboard — or, when nothing is selected, the whole
     * field. Copying "nothing" from a keyboard with no visible selection would just look broken, and
     * copy-the-whole-line is what the player is after in every realistic case here.
     */
    public static void copy(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        EditBox box = editBox(screen);
        if (box != null) {
            String selected = box.getHighlighted();
            String text = selected.isEmpty() ? box.getValue() : selected;
            if (text.isEmpty()) { notifyPlayer(mc, "steampad.keyboard.clipboard.empty"); return; }
            setClipboard(mc, text);
            notifyPlayer(mc, "steampad.keyboard.clipboard.copied");
            return;
        }
        if (sendShortcut(screen, GLFW.GLFW_KEY_C)) return;
        notifyPlayer(mc, "steampad.keyboard.clipboard.unsupported");
    }

    /** Inserts the OS clipboard's contents at the caret. */
    public static void paste(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        String text = getClipboard(mc);
        if (text.isEmpty()) { notifyPlayer(mc, "steampad.keyboard.clipboard.empty"); return; }

        EditBox box = editBox(screen);
        if (box != null) {
            box.insertText(text);   // replaces the selection when there is one, like vanilla's paste
            return;
        }
        if (sendShortcut(screen, GLFW.GLFW_KEY_V)) return;

        // Last resort, and the one that works absolutely everywhere typing works: send the clipboard
        // through the same char pipeline every other key on this keyboard uses. Newlines are dropped
        // rather than sent as Enter, which on most screens would submit the field mid-paste.
        VirtualKeyboard.typeText(text.replace("\r", "").replace('\n', ' '));
    }

    // ---- internals ------------------------------------------------------------------------

    /** The focused field when it is a vanilla {@link EditBox}, else null. */
    private static EditBox editBox(Screen screen) {
        GuiEventListener f = VirtualKeyboard.findTextField(screen);
        return f instanceof EditBox box ? box : null;
    }

    /**
     * Fires a synthetic Ctrl+{@code key} at the screen and reports whether it was consumed. Always
     * false on versions where vanilla polls the physical keyboard for the modifier — there is no point
     * pretending a shortcut landed when the receiver could not possibly have seen the Ctrl.
     */
    private static boolean sendShortcut(Screen screen, int key) {
        if (screen == null || !InputEventCompat.supportsSyntheticEditShortcuts()) return false;
        int scancode = 0;
        try {
            int sc = GLFW.glfwGetKeyScancode(key);
            if (sc > 0) scancode = sc;
        } catch (Throwable ignored) {
        }
        try {
            return InputEventCompat.keyPressed(screen, key, scancode, InputEventCompat.editShortcutModifier());
        } catch (Throwable t) {
            LogUtil.debug("[SteamPad] Clipboard shortcut {} threw on {}: {}", key,
                    screen.getClass().getSimpleName(), t.toString());
            return false;
        }
    }

    private static String getClipboard(Minecraft mc) {
        try {
            String s = mc.keyboardHandler == null ? null : mc.keyboardHandler.getClipboard();
            return s == null ? "" : s;
        } catch (Throwable t) {
            return "";
        }
    }

    private static void setClipboard(Minecraft mc, String text) {
        try {
            if (mc.keyboardHandler != null) mc.keyboardHandler.setClipboard(text);
        } catch (Throwable t) {
            LogUtil.debug("[SteamPad] Could not write the clipboard: {}", t.toString());
        }
    }

    /** Brief action-bar feedback — the clipboard is invisible, so silence reads as "it didn't work". */
    private static void notifyPlayer(Minecraft mc, String key) {
        if (mc.player == null) return;
        mc.player.displayClientMessage(Component.translatable(key), true);
    }
}
