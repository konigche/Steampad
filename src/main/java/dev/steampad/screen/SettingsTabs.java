package dev.steampad.screen;

import dev.steampad.service.UiSoundService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The shared three-tab header for per-controller settings — Basic / Configure Buttons / Advanced.
 * Tabs are buttons (click) and also switch with LB/RB via {@link TabbedScreen}. All three tabs share
 * the same parent (the controller-select screen) so Back always returns there.
 */
public final class SettingsTabs {

    public static final int BASIC = 0, BUTTONS = 1, ADVANCED = 2;

    /** Horizontal space reserved on each side of the tab row for the LB / RB switch glyphs. */
    private static final int GLYPH_INSET = 18;
    private static final int GLYPH_SIZE  = 13;

    private SettingsTabs() {}

    /** Adds the three tab buttons to {@code screen} (the active one disabled). Space is reserved on
     *  both sides for the LB/RB glyphs — draw them with {@link #renderGlyphs} using the SAME x/y/totalW. */
    public static void add(SteamPadBaseScreen screen, int active, Screen parent, long handle,
                           int x, int y, int totalW) {
        int gap = 4;
        int tx = x + GLYPH_INSET;
        int tw = totalW - 2 * GLYPH_INSET;
        int w = (tw - 2 * gap) / 3;
        addTab(screen, active, BASIC,    "steampad.tab.basic",    parent, handle, tx,                 y, w);
        addTab(screen, active, BUTTONS,  "steampad.tab.buttons",  parent, handle, tx + w + gap,       y, w);
        addTab(screen, active, ADVANCED, "steampad.tab.advanced", parent, handle, tx + 2 * (w + gap), y, tw - 2 * (w + gap));
    }

    /**
     * Draws the LB / RB tab-switch glyphs beside the tab row (brand-specific texture via
     * {@link dev.steampad.client.ui.ButtonIcon}, vector fallback). Call from the screen's render()
     * with the same x/y/totalW passed to {@link #add}.
     */
    public static void renderGlyphs(net.minecraft.client.gui.GuiGraphics ctx,
                                    net.minecraft.client.gui.Font tr,
                                    int x, int y, int totalW) {
        int gy = y + (18 - GLYPH_SIZE) / 2;   // vertically centered on the 18px tab row
        dev.steampad.client.ui.ButtonIcon.draw(ctx, tr, x, gy, GLYPH_SIZE, "LB");
        int rw = dev.steampad.client.ui.ButtonIcon.width(tr, GLYPH_SIZE, "RB");
        dev.steampad.client.ui.ButtonIcon.draw(ctx, tr, x + totalW - rw, gy, GLYPH_SIZE, "RB");
    }

    private static void addTab(SteamPadBaseScreen screen, int active, int idx, String key,
                               Screen parent, long handle, int x, int y, int w) {
        Button b = Button.builder(Component.translatable(key), btn -> {
            if (idx != active) { UiSoundService.playNavigate(); switchTo(idx, parent, handle); }
        }).bounds(x, y, w, 18).build();
        if (idx == active) b.active = false;
        screen.addDrawableChildPublic(b);
    }

    /** Opens the screen for the given tab index, preserving the parent. */
    public static void switchTo(int idx, Screen parent, long handle) {
        Minecraft mc = Minecraft.getInstance();
        Screen next = switch (idx) {
            case BUTTONS -> new BindingsScreen(parent, handle);
            case ADVANCED -> new ControllerAdvancedSettingsScreen(parent, handle);
            default -> new ControllerBasicSettingsScreen(parent, handle);
        };
        mc.setScreen(next);
    }

    /** Cycle helper for LB/RB. */
    public static void cycle(int active, int dir, Screen parent, long handle) {
        switchTo((active + dir + 3) % 3, parent, handle);
    }
}
