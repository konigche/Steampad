package dev.steampad.input;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.MouseInput;

import java.util.ArrayList;
import java.util.List;

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

    private static List<ClickableWidget> navigables(Screen s) {
        List<ClickableWidget> out = new ArrayList<>();
        for (Element e : s.children()) {
            if (e instanceof ClickableWidget w && w.visible && w.active) out.add(w);
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
        Element focused = s.getFocused();

        // Inside an entry list: step the selection entry-by-entry; leave only at the edges.
        if (dy != 0 && focused instanceof net.minecraft.client.gui.widget.EntryListWidget<?> list
                && moveListSelection(list, dy)) {
            return;
        }

        List<ClickableWidget> ws = navigables(s);
        if (ws.isEmpty()) return;

        ClickableWidget cur = focused instanceof ClickableWidget c ? c : null;
        ClickableWidget target;
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
        if (target instanceof net.minecraft.client.gui.widget.EntryListWidget<?> list
                && list.getSelectedOrNull() == null) {
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
    private static boolean moveListSelection(net.minecraft.client.gui.widget.EntryListWidget list, int dy) {
        List entries = list.children();
        if (entries.isEmpty()) return false;
        Object sel = list.getSelectedOrNull();
        int idx = sel == null ? -1 : entries.indexOf(sel);
        int next = (idx == -1) ? (dy > 0 ? 0 : entries.size() - 1) : idx + dy;
        if (next < 0 || next >= entries.size()) return false;
        Object entry = entries.get(next);
        // setFocused(entry) selects the entry (AlwaysSelected lists follow focus) and scrolls it
        // into view in vanilla; the Entry type itself is protected, so it can't be named here.
        list.setFocused((Element) entry);
        return true;
    }

    private static ClickableWidget pickDirectional(List<ClickableWidget> ws, ClickableWidget cur, int dx, int dy) {
        double curX = cur.getX() + cur.getWidth() / 2.0, curY = cur.getY() + cur.getHeight() / 2.0;
        ClickableWidget best = null;
        double bestScore = Double.MAX_VALUE;
        for (ClickableWidget w : ws) {
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
        if (s instanceof net.minecraft.client.gui.screen.world.SelectWorldScreen
                && s.getFocused() instanceof net.minecraft.client.gui.widget.EntryListWidget<?> worldList
                && worldList.getSelectedOrNull() != null) {
            // Land specifically on "Play Selected World" (vanilla key selectWorld.select), not
            // whatever button happens to sit geometrically closest — the nearest one turned out to be
            // Delete, which the user flagged as dangerous ("me manda directo a Borrar... para prevenir
            // accidentalmente borrar el mundo"). Falls back to the old geometric pick only if that
            // button can't be found at all (unexpected layout), so this never silently does nothing.
            List<ClickableWidget> ws = navigables(s);
            ClickableWidget target = findByMessage(ws, "selectWorld.select");
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
        if (s.getFocused() instanceof net.minecraft.client.gui.widget.EntryListWidget<?> list
                && list.getSelectedOrNull() != null) {
            int scancode = 0;
            try {
                int sc = org.lwjgl.glfw.GLFW.glfwGetKeyScancode(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
                if (sc > 0) scancode = sc;
            } catch (Throwable ignored) {}
            s.keyPressed(new net.minecraft.client.input.KeyInput(
                    org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, scancode, 0));
            return true;
        }
        if (s.getFocused() instanceof ClickableWidget w) {
            double cx = w.getX() + w.getWidth() / 2.0;
            double cy = w.getY() + w.getHeight() / 2.0;
            if (isChildOf(s, w)) {
                w.mouseClicked(new Click(cx, cy, new MouseInput(0, 0)), false);
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

    private static boolean isChildOf(Screen s, ClickableWidget w) {
        for (Element e : s.children()) {
            if (e == w) return true;
        }
        return false;
    }

    /** True if some widget currently holds focus. */
    public static boolean hasFocus(Screen s) {
        return s.getFocused() instanceof ClickableWidget;
    }

    /**
     * Finds the widget whose displayed message matches {@code translationKey}, resolved through the
     * current locale on both sides so this works regardless of display language. Used instead of
     * geometric picking when a SPECIFIC button matters (see {@link #activate} World Select branch).
     */
    private static ClickableWidget findByMessage(List<ClickableWidget> ws, String translationKey) {
        String want = net.minecraft.text.Text.translatable(translationKey).getString();
        for (ClickableWidget w : ws) {
            if (want.equals(w.getMessage().getString())) return w;
        }
        return null;
    }
}
