package dev.steampad.input;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import dev.steampad.compat.mc.InputEventCompat;

/**
 * Console-style (Bedrock-like) focus navigation for any {@link Screen}, driven by the controller:
 * the D-pad moves the highlight between widgets and A activates the focused one. Works on vanilla
 * and SteamPad screens alike because it operates on the screen's own widget list — no per-screen
 * wiring needed.
 *
 * <p>Activation uses a synthetic mouse click at the widget's center (the same {@code Click}/
 * {@code MouseInput} path the virtual cursor uses), which avoids the version-specific key-input
 * record API and works for buttons, on/off toggles and cycling buttons.
 */
public final class GuiFocusNavigator {

    private GuiFocusNavigator() {}

    private static List<AbstractWidget> navigables(Screen s) {
        List<AbstractWidget> out = new ArrayList<>();
        for (GuiEventListener e : s.children()) {
            if (e instanceof AbstractWidget w && w.visible && w.active) out.add(w);
        }
        // Widgets a mod owns but never registered in children() — REI's search bar and similar
        // Architectury-based overlays (see ExternalWidgetScanner). Without these the D-pad can never
        // reach them at all, even though a real click there already works. Only targets backed by a
        // real ClickableWidget qualify here — vanilla's own focus system (setFocused) needs an actual
        // Element to highlight; duck-typed non-widget targets (e.g. Traveler's Backpack's IButtons,
        // which aren't Elements at all) are reachable via cursor/D-pad in CONTAINER screens through
        // SlotSnap instead, which is purely position-based and doesn't need a focusable Element.
        for (var t : ExternalWidgetScanner.discover(s)) {
            if (t.widget() != null) out.add(t.widget());
        }
        return out;
    }

    /** Moves focus by {@code dir} (+1 next, -1 previous); maps to vertical directional movement. */
    public static void move(Screen s, int dir) {
        moveDir(s, 0, dir > 0 ? 1 : -1);
    }

    /**
     * Spatial focus movement on a vanilla screen: picks the nearest widget in the (dx,dy) direction
     * from the focused one, so the D-pad follows the visual layout instead of widget creation order.
     *
     * <p>Vanilla entry lists (world selection, server list, …) are handled specially: while the list
     * has focus, D-pad up/down moves the SELECTED ENTRY inside it instead of jumping to the widget
     * above/below — only past the first/last entry does focus leave the list (the D17 "brinca" fix).
     */
    public static void moveDir(Screen s, int dx, int dy) {
        GuiEventListener focused = s.getFocused();

        // Inside an entry list: step the selection entry-by-entry; leave only at the edges.
        if (dy != 0 && focused instanceof net.minecraft.client.gui.components.AbstractSelectionList<?> list
                && moveListSelection(list, dy)) {
            return;
        }

        List<AbstractWidget> ws = navigables(s);
        if (ws.isEmpty()) return;

        AbstractWidget cur = focused instanceof AbstractWidget c ? c : null;
        AbstractWidget target;
        if (cur == null) {
            target = ws.get(0);
        } else {
            target = pickDirectional(ws, cur, dx, dy);
            if (target == null) return;
        }

        if (cur != null) cur.setFocused(false);
        s.setFocused(target);
        target.setFocused(true);
        // Entering a list from outside: make sure something is selected so the highlight is visible.
        if (target instanceof net.minecraft.client.gui.components.AbstractSelectionList<?> list
                && list.getSelected() == null) {
            moveListSelection(list, dy >= 0 ? 1 : -1);
        }
        dev.steampad.input.VirtualMouseController.setPosition(
                target.getX() + target.getWidth() / 2.0, target.getY() + target.getHeight() / 2.0);
    }

    /**
     * Moves the selection of a vanilla entry list by {@code dy} entries. Returns false at the ends
     * (or when empty) so the caller can move focus out of the list instead.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean moveListSelection(net.minecraft.client.gui.components.AbstractSelectionList list, int dy) {
        List entries = list.children();
        if (entries.isEmpty()) return false;
        Object sel = list.getSelected();
        int idx = sel == null ? -1 : entries.indexOf(sel);
        int next = (idx == -1) ? (dy > 0 ? 0 : entries.size() - 1) : idx + dy;
        if (next < 0 || next >= entries.size()) return false;

        Object entry = entries.get(next);
        // setFocused(entry) is what actually SELECTS the entry (AlwaysSelected lists follow focus);
        // the Entry type itself is protected, so it can't be named here.
        //
        // Do NOT replace this with a synthetic UP/DOWN key event: AbstractSelectionList has no
        // keyPressed method at all (verified with javap on both mapped jars), so such an event is
        // silently dropped and the list stops responding entirely — that regression is exactly what
        // broke world selection in v0.87.0 ("ya no selecciona mundo, pasa directo a los menús").
        list.setFocused((GuiEventListener) entry);

        // ...but setFocused only scrolls the list into view when vanilla thinks the last input came
        // from a KEYBOARD (1.21.1), and never scrolls at all on 1.21.10 — see
        // AbstractSelectionListInvoker's doc for the disassembly. SteamPad drives a virtual mouse, so
        // that check is always false on a pad. Ask vanilla to scroll explicitly instead, which is the
        // piece that was genuinely missing ("visualmente no baja la lista, solo la selección").
        // Defensive: a mod-supplied list subclass could in principle not carry our mixin.
        try {
            if (list instanceof dev.steampad.mixin.AbstractSelectionListInvoker inv
                    && entry instanceof net.minecraft.client.gui.components.AbstractSelectionList.Entry<?> e) {
                inv.steampad$scrollToEntry(e);
            }
        } catch (Throwable ignored) {}
        return true;
    }

    private static AbstractWidget pickDirectional(List<AbstractWidget> ws, AbstractWidget cur, int dx, int dy) {
        double curX = cur.getX() + cur.getWidth() / 2.0, curY = cur.getY() + cur.getHeight() / 2.0;
        AbstractWidget best = null;
        double bestScore = Double.MAX_VALUE;
        for (AbstractWidget w : ws) {
            if (w == cur) continue;
            double ddx = (w.getX() + w.getWidth() / 2.0) - curX;
            double ddy = (w.getY() + w.getHeight() / 2.0) - curY;
            double along = ddx * dx + ddy * dy;
            if (along <= 1.0) continue;
            double perp = Math.abs(ddx * dy - ddy * dx);
            double score = along + perp * 2.0;
            if (score < bestScore) { bestScore = score; best = w; }
        }
        return best;
    }

    /** Activates the focused widget via a synthetic click at its center. Returns true if it acted. */
    public static boolean activate(Screen s) {
        // World Select (feedback: "cuando me posiciono en un mundo le doy a A no quiero que me abra
        // el mundo, quiero que me mande a las opciones de abajo, Jugar al mundo selecionado, crear
        // mundo nuevo etc. Ahi si con una A entro, esto solo lo quiero cuando estoy con DPAD"): A on
        // the D-pad-focused world list moves focus to the action button row below instead of joining
        // immediately — a second A then activates whichever button is focused via the normal
        // ClickableWidget path further down. Scoped to SelectWorldScreen only, so other entry lists
        // (server list, resource packs, …) keep the direct-Enter join/connect behavior. The virtual
        // mouse never routes through this method's list branch, so a mouse click on an entry still
        // enters directly via vanilla's own click path, exactly as requested.
        if (s instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
                && s.getFocused() instanceof net.minecraft.client.gui.components.AbstractSelectionList<?> worldList
                && worldList.getSelected() != null) {
            // Land specifically on "Play Selected World" (vanilla key selectWorld.select), not
            // whatever button happens to sit geometrically closest — the nearest one turned out to be
            // Delete, which the user flagged as dangerous ("me manda directo a Borrar... para prevenir
            // accidentalmente borrar el mundo"). Falls back to the old geometric pick only if that
            // button can't be found at all (unexpected layout), so this never silently does nothing.
            List<AbstractWidget> ws = navigables(s);
            AbstractWidget target = findByMessage(ws, "selectWorld.select");
            if (target == null) target = pickDirectional(ws, worldList, 0, 1);
            if (target != null) {
                worldList.setFocused(false);
                s.setFocused(target);
                target.setFocused(true);
                dev.steampad.input.VirtualMouseController.setPosition(
                        target.getX() + target.getWidth() / 2.0, target.getY() + target.getHeight() / 2.0);
                return true;
            }
            // No button found below (unexpected layout) — fall through to the normal join-on-A path.
        }

        // Entry lists: A confirms the SELECTED entry via a synthetic Enter (vanilla's confirm path) —
        // a center click would hit whatever row happens to sit at the widget's center.
        if (s.getFocused() instanceof net.minecraft.client.gui.components.AbstractSelectionList<?> list
                && list.getSelected() != null) {
            int scancode = 0;
            try {
                int sc = org.lwjgl.glfw.GLFW.glfwGetKeyScancode(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
                if (sc > 0) scancode = sc;
            } catch (Throwable ignored) {}
            InputEventCompat.keyPressed(s, org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, scancode, 0);
            return true;
        }
        if (s.getFocused() instanceof AbstractWidget w) {
            double cx = w.getX() + w.getWidth() / 2.0;
            double cy = w.getY() + w.getHeight() / 2.0;
            if (isChildOf(s, w)) {
                InputEventCompat.mouseClicked(w, cx, cy, 0);
            } else {
                // Mod-owned widget outside children() (e.g. REI's search bar): a direct mouseClicked()
                // call would bypass whatever event system the mod uses to learn about clicks (the same
                // reason VirtualMouseController routes through the real Mouse.onMouseButton call site
                // instead of calling Screen.mouseClicked directly — see its doc). Position the cursor
                // there and fire a real click instead, so it's indistinguishable from the user clicking
                // it with the (already working) virtual mouse.
                VirtualMouseController.setPosition(cx, cy);
                VirtualMouseController.simulateLeftClick();
            }
            return true;
        }
        return false;
    }

    private static boolean isChildOf(Screen s, AbstractWidget w) {
        for (GuiEventListener e : s.children()) {
            if (e == w) return true;
        }
        return false;
    }

    /** True if some widget currently holds focus. */
    public static boolean hasFocus(Screen s) {
        return s.getFocused() instanceof AbstractWidget;
    }

    /**
     * Finds the widget whose displayed message matches {@code translationKey}, resolved through the
     * current locale on both sides so this works regardless of display language. Used instead of
     * geometric picking when a SPECIFIC button matters (see {@link #activate} World Select branch).
     */
    private static AbstractWidget findByMessage(List<AbstractWidget> ws, String translationKey) {
        String want = net.minecraft.network.chat.Component.translatable(translationKey).getString();
        for (AbstractWidget w : ws) {
            if (want.equals(w.getMessage().getString())) return w;
        }
        return null;
    }
}
