package dev.steampad.screen;

import java.util.ArrayList;
import java.util.List;
import dev.steampad.compat.mc.FrameTimeCompat;
import dev.steampad.compat.mc.InputEventCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base class for all SteamPad screens.
 *
 * <p>Suppresses vanilla's own late {@link #renderBackground} call and re-issues the very same one
 * EARLY, from {@link #renderChrome} — so the blurred backdrop still renders exactly as vanilla draws
 * it, just before our chrome instead of on top of it. See {@link #renderChrome}'s doc for the
 * ordering bug this solves.
 *
 * <p>Also provides a shared, refreshed visual language (header bar, footer, section headers, accent
 * palette) so every SteamPad screen looks cohesive and "console-like". These helpers are additive —
 * screens opt in by calling {@link #renderChrome} / {@link #drawSectionHeader}.
 *
 * <p>All SteamPad screens must extend this class instead of {@link Screen} directly.
 */
public abstract class SteamPadBaseScreen extends Screen {

    /** Tint laid over vanilla's blurred backdrop — darkens it for contrast WITHOUT hiding the blur.
     *  Low alpha on purpose: at the old near-opaque values the blur underneath was invisible, which
     *  read as "the blur was removed". */
    protected static final int BG_TINT_TOP    = 0x660B0E14;
    protected static final int BG_TINT_BOTTOM = 0x8805070A;

    // Refreshed palette.
    protected static final int ACCENT       = 0xFF4FA3FF;  // SteamPad blue
    protected static final int ACCENT_DIM    = 0xFF2C5C8F;
    protected static final int HEADER_BG     = 0xF0141A24;
    protected static final int FOOTER_BG     = 0xF0141A24;
    protected static final int PANEL_BG      = 0x66101620;
    protected static final int DIVIDER       = 0x33FFFFFF;
    protected static final int TEXT_PRIMARY  = 0xFFFFFFFF;
    protected static final int TEXT_MUTED    = 0xFFAAB4C0;
    protected static final int TEXT_OK       = 0xFF66DD88;
    protected static final int TEXT_WARN     = 0xFFFFC044;
    protected static final int TEXT_FAIL     = 0xFFFF6060;

    protected static final int HEADER_H = 30;
    protected static final int FOOTER_H = 34;

    protected SteamPadBaseScreen(Component title) {
        super(title);
    }

    /**
     * Suppressed on purpose — {@link #renderChrome} issues this same background EARLY instead. See
     * that method's doc for the ordering bug.
     */
    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // Intentionally empty: the real background is drawn from renderChrome().
    }

    /**
     * Draws the blurred backdrop, then the shared header bar (title + accent underline) and footer
     * band. Call once, as the FIRST thing a screen's {@code render()} does.
     *
     * <p><b>Why the background is drawn here rather than left to {@code Screen.render()}:</b> every
     * SteamPad screen draws its own header/panels/content BEFORE calling {@code super.render(...)}
     * (which is where the button/slider widgets actually render). {@code Screen.render()} calls
     * {@code this.renderBackground(...)} as its own very first instruction (verified with
     * {@code javap} against the real 1.21.1 jar) — so on a version where that call paints immediately
     * (pre-Blaze3D-rewrite, unlike 1.21.10's queued/deferred compositing), calling
     * {@code super.render()} LATE meant vanilla's background painted LAST, on top of our already-drawn
     * header: the "header renders behind the blur" bug. Suppressing {@link #renderBackground} and
     * re-issuing it here — the actual first thing every screen's {@code render()} does — pins the
     * paint order to background → chrome → content → widgets → text on every version, without
     * touching any individual screen's {@code render()}.
     *
     * <p><b>It calls vanilla's real background, not a substitute.</b> An earlier cut of this fix drew
     * a flat opaque gradient instead, which did fix the ordering but silently dropped the blur the
     * mod is supposed to have (reported as "you removed the blur, now it's just a colour"). Vanilla's
     * {@code renderBackground} is what applies {@code renderBlurredBackground} — present on BOTH
     * supported versions with the same public signature — so the fix is to call it early, not to
     * replace it. The tint on top is deliberately translucent so the blur stays visible through it.
     * Vanilla ignores the mouse coordinates in that method (confirmed in its bytecode: only the
     * partial tick is used, for the blur and the main-menu panorama), so passing 0,0 is exact rather
     * than approximate.
     */
    protected void renderChrome(GuiGraphics ctx, Component title) {
        // Vanilla's own blurred backdrop (see doc above), re-issued early instead of late.
        super.renderBackground(ctx, 0, 0, FrameTimeCompat.partialTick(this.minecraft));
        // Darkening tint for text contrast — translucent, so the blur underneath still reads.
        ctx.fillGradient(0, 0, this.width, this.height, BG_TINT_TOP, BG_TINT_BOTTOM);

        // Header band + accent underline.
        ctx.fill(0, 0, this.width, HEADER_H, HEADER_BG);
        ctx.fill(0, HEADER_H, this.width, HEADER_H + 2, ACCENT);
        ctx.drawCenteredString(this.font, title, this.width / 2, 11, TEXT_PRIMARY);

        // Active controller name, top-left, so it's always clear which pad you're configuring.
        String pad = activeControllerName();
        if (pad != null) {
            ctx.drawString(this.font, Component.literal(pad), 8, 4, ACCENT, true);
        }

        // Footer band + accent underline (drawn above the band).
        int footerTop = this.height - FOOTER_H;
        ctx.fill(0, footerTop, this.width, this.height, FOOTER_BG);
        ctx.fill(0, footerTop, this.width, footerTop + 1, ACCENT_DIM);
    }

    /** Display name of the active controller, or null if none — for the header label. */
    protected String activeControllerName() {
        return dev.steampad.service.ActiveControllerService.getActiveRef()
                .map(r -> (r.displayName == null || r.displayName.isBlank())
                        ? ("Controller " + r.handle) : r.displayName)
                .orElse(null);
    }

    /** Convenience: header chrome using this screen's own title. */
    protected void renderChrome(GuiGraphics ctx) {
        renderChrome(ctx, this.title);
    }

    /** Public hook so helper classes (e.g. SettingsTabs) can add widgets to this screen. */
    public AbstractWidget addDrawableChildPublic(AbstractWidget w) {
        return addRenderableWidget(w);
    }

    /**
     * Draws a section header — a short accent bar, a label, and a divider line spanning the content
     * width. Returns the Y just below the header so callers can continue laying out widgets.
     */
    protected int drawSectionHeader(GuiGraphics ctx, int x, int y, int width, Component label) {
        ctx.fill(x, y + 1, x + 3, y + 9, ACCENT);                 // accent tick
        ctx.drawString(this.font, label, x + 8, y, ACCENT, true);
        int lineY = y + 12;
        ctx.fill(x, lineY, x + width, lineY + 1, DIVIDER);        // divider
        return lineY + 5;
    }

    /** Vertical content region between header and footer (for scroll math / layout). */
    protected int contentTop() { return HEADER_H + 8; }
    protected int contentBottom() { return this.height - FOOTER_H - 6; }

    // ---- Reusable vertical scroll for option-heavy screens --------------------------------
    //
    // A screen registers scrollable widgets via addScroll(widget, baseY), then calls
    // finishScroll(lastY). The base class repositions/culls widgets, handles the wheel, and the
    // controller bumpers (which feed mouseScrolled). Screens draw their own section headers at
    // baseY - scrollY() and cull with isInViewport(). Fixed chrome (Back button) stays out of the
    // scroll set.

    private record ScrollItem(AbstractWidget widget, int baseY) {}
    private final List<ScrollItem> scrollItems = new ArrayList<>();
    private int scrollY = 0;
    private int scrollMax = 0;

    /** Call at the start of init() (after super.init()) to reset the scroll set. */
    protected void resetScroll() {
        scrollItems.clear();
        scrollMax = 0;
    }

    /** Register a widget as scrollable at its base Y; also adds it as a child. Returns the widget. */
    protected <T extends AbstractWidget> T addScroll(T widget, int baseY) {
        addRenderableWidget(widget);
        scrollItems.add(new ScrollItem(widget, baseY));
        return widget;
    }

    /** Call after registering all scroll widgets; {@code contentEndY} is the Y just past the last row. */
    protected void finishScroll(int contentEndY) {
        int viewport = contentBottom() - contentTop();
        scrollMax = Math.max(0, (contentEndY - contentTop()) - viewport);
        scrollY = Math.min(scrollY, scrollMax);
        applyScrollLayout();
    }

    protected int scrollY() { return scrollY; }
    protected int scrollMax() { return scrollMax; }

    /** True if a row at base Y is currently within the visible content region. */
    protected boolean isInViewport(int baseY, int rowH) {
        int y = baseY - scrollY;
        return y >= contentTop() - 2 && y + rowH <= contentBottom() + 2;
    }

    private void applyScrollLayout() {
        int top = contentTop();
        int bottom = contentBottom();
        for (ScrollItem it : scrollItems) {
            int y = it.baseY() - scrollY;
            it.widget().setY(y);
            boolean vis = y >= top - 1 && y + it.widget().getHeight() <= bottom + 1;
            it.widget().visible = vis;
            it.widget().active = vis;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Always consume the wheel for list scrolling — never route it to a hovered widget, so scrolling
        // over a slider/cycler can't change its value (A4). No-op when there's nothing to scroll.
        if (scrollMax > 0) {
            scrollY = clampInt(scrollY - (int) (verticalAmount * 16), 0, scrollMax);
            applyScrollLayout();
        }
        return true;
    }

    // Scrollbar drag state — rightX is captured from the last renderScrollbar() call so
    // mouseClicked/mouseDragged (which don't receive layout params) can hit-test the same track the
    // player actually sees. -1 = no scrollbar currently rendered (nothing to drag).
    private int scrollbarRightX = -1;
    private boolean draggingScrollbar = false;
    private static final int SCROLLBAR_HIT_PAD = 4;   // widen the click target beyond the 3px visual bar

    /** Draws a scrollbar at the right edge of the content column, if scrolling is active. */
    protected void renderScrollbar(GuiGraphics ctx, int rightX) {
        if (scrollMax <= 0) { scrollbarRightX = -1; return; }
        scrollbarRightX = rightX;
        int top = contentTop();
        int bottom = contentBottom();
        int h = bottom - top;
        ctx.fill(rightX, top, rightX + 3, bottom, 0x33FFFFFF);
        int thumbH = Math.max(20, (int) ((long) h * h / (h + scrollMax)));
        int thumbY = top + (int) ((long) (h - thumbH) * scrollY / scrollMax);
        ctx.fill(rightX, thumbY, rightX + 3, thumbY + thumbH, ACCENT);
    }

    /** Jumps the scroll position so the thumb center lands under the mouse Y. */
    private void scrollToMouse(double mouseY) {
        int top = contentTop(), bottom = contentBottom();
        double frac = (mouseY - top) / (double) Math.max(1, bottom - top);
        frac = frac < 0.0 ? 0.0 : Math.min(1.0, frac);
        scrollY = clampInt((int) Math.round(frac * scrollMax), 0, scrollMax);
        applyScrollLayout();
    }

    // The three mouse handlers below are pure adapters: 1.21.9 replaced their loose (x, y, button)
    // parameters with a MouseButtonEvent record, so the DECLARATION has to differ per version — a
    // signature is a contract, it cannot be shimmed away. All real logic lives in the version-agnostic
    // helpers underneath, so there is exactly one implementation of the scrollbar behaviour.

    /** True when this press landed on the scrollbar and started a drag. */
    private boolean steampad$scrollbarPress(double mouseX, double mouseY) {
        if (scrollbarRightX >= 0 && scrollMax > 0
                && mouseX >= scrollbarRightX - SCROLLBAR_HIT_PAD
                && mouseX <= scrollbarRightX + 3 + SCROLLBAR_HIT_PAD
                && mouseY >= contentTop() && mouseY <= contentBottom()) {
            draggingScrollbar = true;
            scrollToMouse(mouseY);
            return true;
        }
        return false;
    }

    /** True when a scrollbar drag is in progress and consumed this move. */
    private boolean steampad$scrollbarDrag(double mouseY) {
        if (draggingScrollbar) {
            scrollToMouse(mouseY);
            return true;
        }
        return false;
    }

    /** True when a scrollbar drag was in progress and this release ended it. */
    private boolean steampad$scrollbarRelease() {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return false;
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubleClick) {
        if (steampad$scrollbarPress(click.x(), click.y())) return true;
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double offsetX, double offsetY) {
        if (steampad$scrollbarDrag(click.y())) return true;
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        if (steampad$scrollbarRelease()) return true;
        return super.mouseReleased(click);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (steampad$scrollbarPress(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (steampad$scrollbarDrag(mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (steampad$scrollbarRelease()) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    *///?}

    protected static int clampInt(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /**
     * Console-style focus navigation that understands scrolling. Legacy linear form kept for callers
     * that only have a +1/-1 sense; maps to vertical directional movement.
     */
    public void focusMove(int dir) {
        focusMoveDir(0, dir > 0 ? 1 : -1);
    }

    /**
     * Spatial focus navigation: moves the highlight to the nearest widget in the given direction
     * (dx/dy each in {-1,0,1}), using each option's logical position so up is up and down is down —
     * fixing "the D-pad slides left-to-right through every button instead of following the layout".
     * Scrolls to reveal the target if it is a hidden scrollable row.
     */
    public void focusMoveDir(int dx, int dy) {
        List<NavEntry> entries = navEntries();
        if (entries.isEmpty()) return;

        AbstractWidget focused = this.getFocused() instanceof AbstractWidget c ? c : null;
        NavEntry cur = null;
        for (NavEntry e : entries) if (e.widget == focused) { cur = e; break; }

        NavEntry target;
        if (cur == null || !isInViewport(cur.widget)) {
            // Nothing focused, or the focus is a row the user scrolled AWAY from (right-stick
            // scroll). Starting directional nav from that stale row (or from the top) yanked the
            // view back to where the focus was — so instead, pick up navigation at the first row
            // the user is currently LOOKING at. The scroll position is the user's intent.
            target = firstVisibleEntry(entries);
            if (target == null) target = entries.get(0);
        } else {
            target = pickDirectional(entries, cur, dx, dy);
            if (target == null) return;   // edge of the layout in that direction — stay put
        }

        scrollToReveal(target.widget);
        if (focused != null) focused.setFocused(false);
        this.setFocused(target.widget);
        target.widget.setFocused(true);
        syncCursorToFocused();   // keep the (hidden) OS pointer on the focused widget → no double highlight
    }

    /** True if the widget is currently visible (scroll rows check the live viewport; fixed = always). */
    private boolean isInViewport(AbstractWidget w) {
        for (ScrollItem it : scrollItems) {
            if (it.widget() == w) {
                int y = it.baseY() - scrollY;
                return y + w.getHeight() > contentTop() && y < contentBottom();
            }
        }
        return true;   // fixed widgets (tabs, footer buttons) never scroll out
    }

    /** First navigable SCROLL row inside the current viewport (entries are top-to-bottom). */
    private NavEntry firstVisibleEntry(List<NavEntry> entries) {
        for (ScrollItem it : scrollItems) {
            int y = it.baseY() - scrollY;
            if (y >= contentTop() && y + it.widget().getHeight() <= contentBottom() && it.widget().active) {
                for (NavEntry e : entries) if (e.widget() == it.widget()) return e;
            }
        }
        return null;
    }

    /** Best candidate strictly in the (dx,dy) direction from {@code cur}; null if none. */
    private NavEntry pickDirectional(List<NavEntry> entries, NavEntry cur, int dx, int dy) {
        NavEntry best = null;
        double bestScore = Double.MAX_VALUE;
        for (NavEntry e : entries) {
            if (e.widget == cur.widget) continue;
            double ddx = e.cx - cur.cx, ddy = e.cy - cur.cy;
            double along = ddx * dx + ddy * dy;         // distance in the travel direction
            if (along <= 1.0) continue;                 // not ahead of us
            double perp = Math.abs(ddx * dy - ddy * dx);// off-axis distance
            double score = along + perp * 2.0;          // prefer aligned, then nearest
            if (score < bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    /** Activates the focused widget (A button). */
    public boolean focusActivate() {
        if (this.getFocused() instanceof AbstractWidget w) {
            double cx = w.getX() + w.getWidth() / 2.0, cy = w.getY() + w.getHeight() / 2.0;
            InputEventCompat.mouseClicked(w, cx, cy, 0);
            return true;
        }
        return false;
    }

    /** Moves the OS pointer onto the focused widget so hover == focus (single highlight). */
    private void syncCursorToFocused() {
        if (this.getFocused() instanceof AbstractWidget w) {
            dev.steampad.input.VirtualMouseController.setPosition(
                    w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0);
        }
    }

    private record NavEntry(AbstractWidget widget, double cx, double cy) {}

    /** Navigable widgets with their logical centers (scroll rows use baseY so off-screen rows count). */
    private List<NavEntry> navEntries() {
        List<AbstractWidget> scrollW = new ArrayList<>();
        List<NavEntry> out = new ArrayList<>();
        for (ScrollItem it : scrollItems) {
            scrollW.add(it.widget());
            out.add(new NavEntry(it.widget(),
                    it.widget().getX() + it.widget().getWidth() / 2.0,
                    it.baseY() + it.widget().getHeight() / 2.0));
        }
        for (var el : this.children()) {
            if (el instanceof AbstractWidget w && w.active && !scrollW.contains(w)) {
                out.add(new NavEntry(w, w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0));
            }
        }
        return out;
    }

    /** All navigable widgets in visual (top-to-bottom) order: scroll rows then fixed buttons. */
    private List<AbstractWidget> navOrder() {
        List<AbstractWidget> scrollW = new ArrayList<>();
        for (ScrollItem it : scrollItems) scrollW.add(it.widget());
        List<AbstractWidget> fixed = new ArrayList<>();
        for (var e : this.children()) {
            if (e instanceof AbstractWidget w && w.active && !scrollW.contains(w)) fixed.add(w);
        }
        // scroll rows already in baseY order (added top-to-bottom); fixed sorted by current Y.
        fixed.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
        List<AbstractWidget> out = new ArrayList<>(scrollW.size() + fixed.size());
        out.addAll(scrollW);
        out.addAll(fixed);
        return out;
    }

    private void scrollToReveal(AbstractWidget w) {
        if (scrollMax <= 0) return;
        for (ScrollItem it : scrollItems) {
            if (it.widget() == w) {
                int top = contentTop(), bottom = contentBottom();
                int y = it.baseY() - scrollY;
                int h = w.getHeight();
                if (y < top) scrollY = clampInt(it.baseY() - top, 0, scrollMax);
                else if (y + h > bottom) scrollY = clampInt(it.baseY() + h - bottom, 0, scrollMax);
                applyScrollLayout();
                return;
            }
        }
    }

    /** Scrolls by a delta (used by right-stick continuous scroll). */
    public void scrollBy(int delta) {
        if (scrollMax <= 0) return;
        scrollY = clampInt(scrollY + delta, 0, scrollMax);
        applyScrollLayout();
    }

    /** True if this screen has scrollable content (for the right-stick scroll path). */
    public boolean hasScroll() { return scrollMax > 0; }
}
