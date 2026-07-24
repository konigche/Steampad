package dev.steampad.input;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finds clickable-ish things a screen owns but never adds to {@code screen.children()} — the pattern
 * used by REI (whose overlay widgets are real {@link ClickableWidget}s, just never registered — see
 * D063/D068) and, verified against its real 1.21.10-fabric source, by Traveler's Backpack: its
 * {@code BackpackScreen} keeps a {@code public List<IButton> buttons}, and {@code IButton} (its
 * {@code EquipButton}/{@code MoreButton}/{@code UnequipButton}/etc.) has NOTHING to do with vanilla's
 * widget hierarchy at all — it's a bespoke interface, dispatched by the screen's own overridden
 * {@code mouseClicked()} iterating that list by hand. A scanner that only recognized
 * {@link ClickableWidget} instances would find the {@code buttons} field (it's a {@code List}, a
 * candidate) but skip every element inside it, which is exactly why the first version of this scanner
 * (D068) found nothing there despite compiling clean.
 *
 * <p>So this doesn't require any particular type: real {@link ClickableWidget}s get their bounds from
 * their own getters (fast path); anything else found inside a scanned field is duck-typed — reflected
 * for numeric {@code x}/{@code y}/{@code width}/{@code height} (or {@code w}/{@code h}) fields anywhere
 * in its own class hierarchy, the same near-universal bounds shape vanilla's own widgets use. Only
 * position/size is needed: activation for anything that isn't a real {@link ClickableWidget} already
 * goes through a real cursor click via {@link VirtualMouseController} (see
 * {@code GuiFocusNavigator.activate}), not a direct method call on the discovered object — so there's no
 * need to know the mod's own click method signature at all.
 */
public final class ExternalWidgetScanner {

    private ExternalWidgetScanner() {}

    /** A snap/nav target: a screen-space rectangle, plus the source {@link ClickableWidget} when the
     *  target genuinely is one (REI's case) — null for duck-typed, non-vanilla targets (Traveler's
     *  Backpack's {@code IButton}s and similar), which can only ever be reached via a real cursor click
     *  since there's no common type to call a click method on or to focus through vanilla's own system. */
    public record Target(double x, double y, double width, double height, ClickableWidget widget) {
        public double centerX() { return x + width / 2.0; }
        public double centerY() { return y + height / 2.0; }
    }

    private static final Map<Class<?>, List<Field>> SCREEN_FIELD_CACHE = new IdentityHashMap<>();
    /** Per-class resolved {x, y, width, height} fields for duck-typed bounds, or {@code null} if the
     *  class doesn't have that shape (cached negative results too, so we only walk each class once). */
    private static final Map<Class<?>, Field[]> BOUNDS_FIELD_CACHE = new IdentityHashMap<>();

    // Short-lived memo: discover() is called several times per tick (snap settle, D-pad nav, HUD) —
    // the reflective walk + the REI overlay walk only need to run once per ~2 ticks, not per call.
    private static java.lang.ref.WeakReference<Screen> memoScreen = new java.lang.ref.WeakReference<>(null);
    private static long memoAtNanos = 0L;
    private static List<Target> memoResult = List.of();
    // 250 ms: snap targets don't move faster than that, and in a REI-heavy pack (184 mods) one walk
    // can cost real milliseconds — 4 walks/s bounds it to noise (was 90 ms — lag hardening, B076).
    private static final long MEMO_TTL_NANOS = 250_000_000L;

    // ---- Traveler's Backpack diagnostic (B075 follow-up) -------------------------------------
    // REI's snap/D-pad reach works (confirmed on hardware) but Traveler's Backpack's still doesn't,
    // despite the coordinate-space fix that was supposed to cover exactly this case (D078). Rather
    // than guess a 6th blind fix, log — once every couple seconds while a screen whose class name
    // looks like the backpack is open — exactly what this scanner sees: which fields it considered
    // candidates, and for each element found inside them, whether it resolved as a real
    // ClickableWidget, a duck-typed bounds match, or neither (with that object's own class name, so
    // we know precisely what shape TB's IButton actually has if the assumed x/y/width/height names
    // don't match). This only fires for screens matching "backpack" and is throttled independently
    // of the snap memoization above, so it can't flood the log even if the screen stays open a while.
    private static long lastBackpackDiagNanos = 0L;

    /**
     * Extra snap/nav targets this screen owns but didn't register in {@code children()}. Never throws
     * — any reflective failure (module access, security manager, unexpected field shape) is swallowed
     * and just yields fewer results, since this is a navigation nicety, not a hard requirement (clicking
     * already works without it once the cursor is positioned some other way).
     *
     * <p>Also appends the targets of {@link dev.steampad.compat.ReiCompat}: REI's overlay widgets are
     * NOT fields of the container screen at all — they live in REI's own runtime singleton — so no
     * amount of screen-field scanning could ever find them (the real reason REI stayed unreachable
     * for D-pad/snap through three earlier attempts, B075).
     */
    public static List<Target> discover(Screen screen) {
        // While the virtual keyboard owns input, nothing consumes snap/nav targets — don't pay for
        // the reflective walk (or the REI overlay walk) at all during typing (lag hardening, B076).
        if (dev.steampad.client.keyboard.VirtualKeyboard.isActive()) return memoResult;
        long now = System.nanoTime();
        if (memoScreen.get() == screen && now - memoAtNanos < MEMO_TTL_NANOS) return memoResult;

        long tScan = dev.steampad.util.TickProfiler.begin();
        List<Target> out = new ArrayList<>();
        boolean diag = screen.getClass().getName().toLowerCase(Locale.ROOT).contains("backpack")
                && now - lastBackpackDiagNanos > 2_000_000_000L;
        List<String> trace = diag ? new ArrayList<>() : null;
        try {
            // Panel rect for relative-vs-absolute coordinate resolution (container screens only).
            int[] panel = panelRectOf(screen);

            java.util.Set<Object> known = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            for (var e : screen.children()) known.add(e);
            java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

            List<Field> fields = screenFieldsOf(screen.getClass());
            if (diag) trace.add("fields=" + fields.size() + " " + fields.stream()
                    .map(f -> f.getName() + ":" + f.getType().getSimpleName())
                    .collect(java.util.stream.Collectors.joining(",")));

            for (Field f : fields) {
                Object value;
                try {
                    value = f.get(screen);
                } catch (Throwable ignored) {
                    continue;
                }
                if (value == null) continue;

                if (value instanceof Collection<?> col) {
                    if (diag) trace.add("field '" + f.getName() + "' is a Collection of size " + col.size());
                    for (Object o : col) considerElement(out, known, seen, o, panel, trace);
                } else if (value instanceof Object[] arr) {
                    if (diag) trace.add("field '" + f.getName() + "' is an array of size " + arr.length);
                    for (Object o : arr) considerElement(out, known, seen, o, panel, trace);
                } else if (value instanceof Map<?, ?> map) {
                    if (diag) trace.add("field '" + f.getName() + "' is a Map of size " + map.size());
                    for (Object o : map.values()) considerElement(out, known, seen, o, panel, trace);
                } else {
                    considerElement(out, known, seen, value, panel, trace);
                }
            }

            out.addAll(dev.steampad.compat.ReiCompat.discover(screen));
        } catch (Throwable ignored) {
            // Navigation nicety only — never let a reflection hiccup affect the screen.
        }
        if (diag) {
            lastBackpackDiagNanos = now;
            dev.steampad.util.LogUtil.debug("[SteamPad] Backpack widget scan on {}: panel={} resolved={} targets — {}",
                    screen.getClass().getName(), java.util.Arrays.toString(panelRectOf(screen)), out.size(),
                    String.join(" | ", trace));
        }
        memoScreen = new java.lang.ref.WeakReference<>(screen);
        memoAtNanos = now;
        memoResult = out;
        dev.steampad.util.TickProfiler.end(dev.steampad.util.TickProfiler.WIDGET_SCAN, tScan);
        return out;
    }

    /** The container GUI panel rect as {x, y, w, h}, or null for non-container screens. */
    private static int[] panelRectOf(Screen screen) {
        try {
            if (screen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> hs) {
                var acc = (dev.steampad.mixin.HandledScreenAccessor) hs;
                return new int[]{acc.steampad$getX(), acc.steampad$getY(),
                        acc.steampad$getBackgroundWidth(), acc.steampad$getBackgroundHeight()};
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void considerElement(List<Target> out, java.util.Set<Object> known,
                                        java.util.Set<Object> seen, Object o, int[] panel,
                                        List<String> trace) {
        if (o == null || known.contains(o) || !seen.add(o)) return;

        if (o instanceof ClickableWidget w) {
            if (!w.visible || !w.active || w.getWidth() <= 0 || w.getHeight() <= 0) return;
            out.add(new Target(w.getX(), w.getY(), w.getWidth(), w.getHeight(), w));
            return;
        }

        Field[] bf = boundsFieldsOf(o.getClass());
        if (bf == null) {
            if (trace != null) trace.add("SKIP " + o.getClass().getName() + " — no x/y/width|w/height|h fields found");
            return;
        }
        try {
            double x = ((Number) bf[0].get(o)).doubleValue();
            double y = ((Number) bf[1].get(o)).doubleValue();
            double w = ((Number) bf[2].get(o)).doubleValue();
            double h = ((Number) bf[3].get(o)).doubleValue();
            if (w <= 0 || h <= 0 || w > 4096 || h > 4096) {
                if (trace != null) trace.add("SKIP " + o.getClass().getName() + " — bad size " + w + "x" + h);
                return;
            }

            // Coordinate-space resolution — the real reason the Traveler's Backpack button kept
            // losing the snap through v0.35/0.36/0.38 (B075): its Button stores coordinates RELATIVE
            // to the GUI panel (its own inButton() subtracts getGuiLeft()/getGuiTop() — verified in
            // the 1.21.10-fabric source), while this scanner published them as ABSOLUTE screen px —
            // so the snap target landed near the screen's top-left corner, nowhere near the actual
            // button, and the neighboring slots always won. Decide per target which interpretation
            // actually lands on/near the GUI panel; prefer absolute (the old behavior) on a tie.
            if (panel != null) {
                boolean absPlausible = intersectsPanel(x, y, w, h, panel);
                boolean relPlausible = intersectsPanel(panel[0] + x, panel[1] + y, w, h, panel);
                if (!absPlausible && relPlausible) {
                    x += panel[0];
                    y += panel[1];
                }
            }
            if (trace != null) trace.add("FOUND " + o.getClass().getName() + " at " + x + "," + y + " " + w + "x" + h);
            out.add(new Target(x, y, w, h, null));
        } catch (Throwable t) {
            if (trace != null) trace.add("ERROR " + o.getClass().getName() + " — " + t);
            // Unreadable — skip, don't guess.
        }
    }

    /** True when the rect overlaps the GUI panel expanded by a generous margin (mod buttons often
     *  hang off the panel edge — REI tabs, backpack side buttons). */
    private static boolean intersectsPanel(double x, double y, double w, double h, int[] panel) {
        int margin = 48;
        double px0 = panel[0] - margin, py0 = panel[1] - margin;
        double px1 = panel[0] + panel[2] + margin, py1 = panel[1] + panel[3] + margin;
        return x < px1 && x + w > px0 && y < py1 && y + h > py0;
    }

    /** Declared fields of {@code cls} and every superclass up to (excluding) {@link Object}, that could
     *  hold widget-like things: a widget itself, or a List/array/Map that might hold them. Cached. */
    private static List<Field> screenFieldsOf(Class<?> cls) {
        List<Field> cached = SCREEN_FIELD_CACHE.get(cls);
        if (cached != null) return cached;

        List<Field> fields = new ArrayList<>();
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                boolean candidate = !t.isPrimitive()
                        && (ClickableWidget.class.isAssignableFrom(t)
                        || Collection.class.isAssignableFrom(t)
                        || t.isArray()
                        || Map.class.isAssignableFrom(t));
                if (!candidate) continue;
                try {
                    f.setAccessible(true);
                } catch (Throwable ignored) {
                    continue;
                }
                fields.add(f);
            }
        }
        SCREEN_FIELD_CACHE.put(cls, fields);
        return fields;
    }

    /** Resolves {x, y, width, height}-shaped numeric fields on an arbitrary object's class, or
     *  {@code null} if it doesn't have that shape. Matches by name (case-insensitive): x/y and
     *  width|w / height|h — the same convention vanilla's own widgets use. */
    private static Field[] boundsFieldsOf(Class<?> cls) {
        if (BOUNDS_FIELD_CACHE.containsKey(cls)) return BOUNDS_FIELD_CACHE.get(cls);

        Field fx = null, fy = null, fw = null, fh = null;
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                if (t != int.class && t != float.class && t != double.class) continue;
                String n = f.getName().toLowerCase(Locale.ROOT);
                try {
                    f.setAccessible(true);
                } catch (Throwable ignored) {
                    continue;
                }
                if (fx == null && n.equals("x")) fx = f;
                else if (fy == null && n.equals("y")) fy = f;
                else if (fw == null && (n.equals("width") || n.equals("w"))) fw = f;
                else if (fh == null && (n.equals("height") || n.equals("h"))) fh = f;
            }
        }
        Field[] result = (fx != null && fy != null && fw != null && fh != null)
                ? new Field[]{fx, fy, fw, fh} : null;
        BOUNDS_FIELD_CACHE.put(cls, result);
        return result;
    }
}
