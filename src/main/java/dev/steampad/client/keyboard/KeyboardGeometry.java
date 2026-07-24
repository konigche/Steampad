package dev.steampad.client.keyboard;

import dev.steampad.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for the on-screen keyboard's pixel layout. Both the renderer (drawing) and
 * {@link VirtualKeyboard} (free-floating stick cursor → nearest-key snap) use the same rects, so the
 * highlighted key always matches what is on screen.
 */
public final class KeyboardGeometry {

    /** Pixel rect of one key, tagged with its grid position. */
    public record KeyRect(int row, int col, int x, int y, int w, int h) {
        public int centerX() { return x + w / 2; }
        public int centerY() { return y + h / 2; }

        /** Squared distance from a point to this rect (0 inside) — fair to wide keys like Space. */
        public double distSq(double px, double py) {
            double cx = Math.max(x, Math.min(px, x + w));
            double cy = Math.max(y, Math.min(py, y + h));
            double dx = px - cx, dy = py - cy;
            return dx * dx + dy * dy;
        }
    }

    /** Full panel metrics plus every key rect for the current grid. */
    public record Panel(int panelTop, int panelBot, int footerY,
                        int gridTop, int gridBottom, int gridLeft, int gridRight,
                        List<KeyRect> keys) {

        /** The rect for a grid position, or null if out of range. */
        public KeyRect rectAt(int row, int col) {
            for (KeyRect k : keys) if (k.row() == row && k.col() == col) return k;
            return null;
        }

        /** The key nearest to a point (never null while the grid is non-empty). */
        public KeyRect nearest(double px, double py) {
            KeyRect best = null;
            double bd = Double.MAX_VALUE;
            for (KeyRect k : keys) {
                double d = k.distSq(px, py);
                if (d < bd) { bd = d; best = k; }
            }
            return best;
        }
    }

    private KeyboardGeometry() {}

    /** Computes the panel + key rects for the given screen size and layer grid, anchored at the
     *  bottom of the screen (the original/default position — see the {@code top} overload). */
    public static Panel layout(int sw, int sh, boolean chatScreen, List<List<KeyboardLayout.Key>> grid) {
        return layout(sw, sh, chatScreen, false, grid);
    }

    /**
     * Computes the panel + key rects for the given screen size and layer grid.
     *
     * @param top anchors the panel to the TOP of the screen instead of the bottom — the manual
     *            reposition toggle (feedback: "agrega un chord de teclado DUP+RT pra mover el teclado
     *            hasta arriba... esto es para que no tape la caja de escritura de algunos mods"), for
     *            mods whose own text field sits where the keyboard would normally cover it.
     */
    public static Panel layout(int sw, int sh, boolean chatScreen, boolean top, List<List<KeyboardLayout.Key>> grid) {
        // Honor the configured height within the slider's real range (20–40%). The old code silently
        // clamped anything below 22% upward, so most of the 12–30% slider did nothing — fixed along
        // with re-centering the default at 30%.
        float pct = Math.max(0.20f, Math.min(0.40f, ConfigManager.getGlobal().virtualKeyboardHeightPct));
        // Chat no longer reserves a strip BELOW the panel: the whole chat (input + suggestions +
        // history) is pushed ABOVE the keyboard by the ChatScreen/ChatHud mixins instead, so the
        // panel sits flush at the bottom on every screen (see VirtualKeyboard.chatPushUp).
        int bottomPad = 0;
        int panelH = Math.max(80, Math.round(sh * pct));
        int panelTop = top ? 0 : sh - panelH - bottomPad;
        int panelBot = top ? panelH : sh - bottomPad;
        int footerH = 14;
        int footerY = panelBot - footerH - 2;
        int gridTop = panelTop + 4;
        int gridBottom = footerY - 2;
        int gap = 2, sideMargin = 6;
        int gridW = sw - sideMargin * 2;

        List<KeyRect> keys = new ArrayList<>();
        int rows = grid.size();
        if (rows > 0) {
            int rowH = (gridBottom - gridTop - gap * (rows - 1)) / rows;
            for (int r = 0; r < rows; r++) {
                List<KeyboardLayout.Key> rowKeys = grid.get(r);
                int totalUnits = 0;
                for (KeyboardLayout.Key k : rowKeys) totalUnits += k.units();
                int avail = gridW - gap * (rowKeys.size() - 1);
                int ry = gridTop + r * (rowH + gap);
                int x = sideMargin;
                for (int c = 0; c < rowKeys.size(); c++) {
                    int kw = Math.max(8, avail * rowKeys.get(c).units() / Math.max(1, totalUnits));
                    keys.add(new KeyRect(r, c, x, ry, kw, rowH));
                    x += kw + gap;
                }
            }
        }
        return new Panel(panelTop, panelBot, footerY, gridTop, gridBottom, sideMargin, sw - sideMargin, keys);
    }
}
