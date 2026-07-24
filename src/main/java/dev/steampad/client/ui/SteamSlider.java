package dev.steampad.client.ui;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.function.Consumer;

/**
 * SteamPad slider with a consistent "Label: value" message, a {@link #nudge} hook for fine right-stick
 * adjustment, and scroll disabled so scrolling the list never changes the value (A4). Values map a
 * normalized 0..1 internal position to a [min,max] float range.
 */
public class SteamSlider extends SliderWidget {

    private final float min, max;
    private final String fmt;
    private final Text label;
    private final Consumer<Float> onChange;

    public SteamSlider(int x, int y, int w, int h, Text label, float value,
                       float min, float max, String fmt, Consumer<Float> onChange) {
        super(x, y, w, h, Text.empty(), clamp01((value - min) / (max - min)));
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

    /** Fine adjust by a fraction of the normalized 0..1 range (used by the right stick). */
    public void nudge(double deltaNormalized) {
        double nv = MathHelper.clamp(this.value + deltaNormalized, 0.0, 1.0);
        if (nv != this.value) {
            this.value = nv;
            updateMessage();
            applyValue();
        }
    }

    // Scrolling must NOT change the value — the list scroll owns the wheel/stick (A4).
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        return false;
    }

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
}
