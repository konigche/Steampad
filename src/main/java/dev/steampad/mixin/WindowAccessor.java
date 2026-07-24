package dev.steampad.mixin;

import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code Window}'s private windowed-bounds bookkeeping so
 * {@link dev.steampad.client.window.WindowArrangeController} can reposition/resize the OS window for
 * the Splitscreen (window-tiling) feature, keeping vanilla's own fullscreen-toggle bookkeeping
 * (windowedX/Y/width/height) consistent with the actual GLFW-level move — the same fields vanilla's
 * own F11 handler updates internally, just written from our gamepad chord instead. Pure field
 * accessors, no logic (CLAUDE.md Restriction 4: mixins are thin hooks only).
 *
 * <p>Field names verified against the mapped 1.21.10 jar via javap
 * ({@code net.minecraft.client.util.Window}): {@code x, y, width, height} (live bounds) and
 * {@code windowedX, windowedY, windowedWidth, windowedHeight} (remembered pre-fullscreen bounds) are
 * all private with no public setters. The fullscreen flag itself is intentionally NOT exposed here —
 * entering/exiting fullscreen goes through the public {@code Window.toggleFullscreen()} so vanilla's
 * own state machine (it also tracks a second {@code currentFullscreen} flag and does GL/viewport work)
 * stays correct; only the windowed/tiled bounds are ever written directly.
 */
@Mixin(Window.class)
public interface WindowAccessor {

    @Accessor("x") void steampad$setX(int x);
    @Accessor("y") void steampad$setY(int y);
    @Accessor("width") void steampad$setWidth(int width);
    @Accessor("height") void steampad$setHeight(int height);

    @Accessor("windowedX") void steampad$setWindowedX(int x);
    @Accessor("windowedY") void steampad$setWindowedY(int y);
    @Accessor("windowedWidth") void steampad$setWindowedWidth(int width);
    @Accessor("windowedHeight") void steampad$setWindowedHeight(int height);
}
