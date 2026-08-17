package dev.steampad.client.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * SteamPad slider with a consistent "Label: value" message and scroll disabled so scrolling the list
 * never changes the value (A4). Values map a normalized 0..1 internal position to a [min,max] float
 * range. Right-stick fine-adjust is generic now — see {@code GamepadInputDispatcher.nudgeSlider} and
 * {@link dev.steampad.mixin.AbstractSliderButtonAccessor}, which work on this class the same way they
 * work on any {@link AbstractSliderButton}, vanilla's included.
 */
public class SteamSlider extends AbstractSliderButton {

    private final float min, max;
    private final String fmt;
    private final Component label;
    private final Consumer<Float> onChange;

    public SteamSlider(int x, int y, int w, int h, Component label, float value,
                       float min, float max, String fmt, Consumer<Float> onChange) {
        super(x, y, w, h, Component.empty(), clamp01((value - min) / (max - min)));
        this.min = min;
        this.max = max;
        this.fmt = fmt;
        this.label = label;
        this.onChange = onChange;
        updateMessage();
    }

    public float floatValue() { return (float) (this.value * (max - min) + min); }

    @Override
    protected void updateMessage() {
        if (label == null) return;   // guard: super() may call before fields are set
        setMessage(label.copy().append(": " + String.format(fmt, floatValue())));
    }

    @Override
    protected void applyValue() {
        if (onChange != null) onChange.accept(floatValue());
    }

    // Scrolling must NOT change the value — the list scroll owns the wheel/stick (A4).
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        return false;
    }

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
}
