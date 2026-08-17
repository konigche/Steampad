package dev.steampad.client.ui;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * On/off setting drawn as a sliding switch (green = on, red = off) so the state is visible at a glance
 * (A7-item). Pressing it toggles and notifies the listener. Built on {@link AbstractButton} so it
 * works with mouse, keyboard, and the controller's focus-activate path.
 */
public class SteamToggle extends AbstractButton {

    private boolean on;
    private final Component label;
    private final Consumer<Boolean> onChange;

    public SteamToggle(int x, int y, int w, int h, Component label, boolean on, Consumer<Boolean> onChange) {
        super(x, y, w, h, label);
        this.on = on;
        this.label = label;
        this.onChange = onChange;
    }

    public boolean isOn() { return on; }

    // onPress gained an InputWithModifiers parameter in 1.21.9; before that it took none. Thin
    // adapter per version over one shared implementation.
    //? if >=1.21.9 {
    @Override
    public void onPress(net.minecraft.client.input.InputWithModifiers input) {
        steampad$toggle();
    }
    //?} else {
    /*@Override
    public void onPress() {
        steampad$toggle();
    }
    *///?}

    private void steampad$toggle() {
        on = !on;
        if (onChange != null) onChange.accept(on);
    }

    @Override
    protected void renderWidget(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = isHovered() || isFocused();
        Draw.fillRoundRect(ctx, x, y, x + w, y + h, 3, hover ? 0xFF2A3340 : 0xFF20262E);
        if (hover) ctx.fill(x, y, x + w, y + 1, 0xFF4FA3FF);

        ctx.drawString(Minecraft.getInstance().font, label, x + 6, y + (h - 8) / 2,
                0xFFE6ECF2, false);

        // Switch on the right edge.
        int sw = 26, sh = 12;
        int sx = x + w - sw - 6, sy = y + (h - sh) / 2;
        Draw.fillRoundRect(ctx, sx, sy, sx + sw, sy + sh, sh / 2, on ? 0xFF3D8B40 : 0xFF6A3038);
        int knob = sh - 2;
        int kx = on ? sx + sw - knob - 1 : sx + 1;
        Draw.fillRoundRect(ctx, kx, sy + 1, kx + knob, sy + 1 + knob, knob / 2, 0xFFEAF0F6);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        defaultButtonNarrationText(builder);
    }
}
