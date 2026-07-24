package dev.steampad.client.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 20×20 button with a programmatic gamepad icon and "SteamPad Settings" tooltip, injected into the
 * vanilla Options screen next to "Controls" (see {@code mixin.OptionsScreenMixin}).
 *
 * <p><b>Deliberately a top-level class, NOT nested inside the Mixin class.</b> It used to be a
 * private static nested class inside {@code OptionsScreenMixin} — Mixin's inner-class handling
 * rewrote its InnerClasses bytecode attribute to (incorrectly) point at {@code ButtonWidget} (the
 * class it extends) instead of its real enclosing class. That mismatch is invisible until something
 * calls {@code getClass().getSimpleName()} on an instance, at which point the JVM throws
 * {@code IncompatibleClassChangeError} and crashes the whole game — which is exactly what happened
 * the first time {@link dev.steampad.client.keyboard.VirtualKeyboard}'s text-field detection walked
 * the Options screen's widget tree. Living outside any {@code @Mixin} class means Mixin never
 * touches this class's InnerClasses attribute at all.
 */
public final class GamepadOptionsButton extends ButtonWidget {

    public GamepadOptionsButton(int x, int y, ButtonWidget.PressAction onPress) {
        super(x, y, 20, 20,
              Text.translatable("steampad.options.open"),
              onPress,
              DEFAULT_NARRATION_SUPPLIER);
        setTooltip(Tooltip.of(Text.translatable("steampad.options.open")));
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta); // standard button background
        drawGamepadIcon(context, getX(), getY());
    }

    /**
     * Draws a simple, fully monochrome gamepad silhouette in a 16×16 area at (bx+2, by+2).
     * Uses DrawContext.fill() only — no external texture assets required.
     */
    private static void drawGamepadIcon(DrawContext ctx, int bx, int by) {
        final int GREY = 0xFF888888;
        final int DARK = 0xFF444444;
        final int WHIT = 0xFFCCCCCC;

        int x = bx + 2, y = by + 2;

        // Controller body (wide center rectangle)
        ctx.fill(x + 1,  y + 3,  x + 15, y + 11, GREY);
        // Left grip (extends below body)
        ctx.fill(x,      y + 7,  x + 4,  y + 14, GREY);
        // Right grip
        ctx.fill(x + 12, y + 7,  x + 16, y + 14, GREY);

        // D-pad cross (left side of body)
        ctx.fill(x + 2,  y + 6,  x + 6,  y + 8,  DARK); // horizontal bar
        ctx.fill(x + 3,  y + 5,  x + 5,  y + 9,  DARK); // vertical bar

        // Left thumbstick (white dot)
        ctx.fill(x + 7,  y + 5,  x + 9,  y + 7,  WHIT);

        // Face buttons (right side of body) — monochrome dots, no per-button color accents.
        ctx.fill(x + 10, y + 5,  x + 12, y + 7,  WHIT);
        ctx.fill(x + 12, y + 7,  x + 14, y + 9,  WHIT);
    }
}
