package dev.steampad.client.window;

/**
 * The window layouts cycled by the "Splitscreen" feature (Select+RB) — an OS window position/size
 * helper for local multiplayer with multiple Minecraft instances, NOT in-game split rendering (see
 * CLAUDE.md Restriction 2 and DECISIONS.md for the "why" of that distinction).
 *
 * <p>Positioning math ported from <a href="https://github.com/pcal43/splitscreen">pcal43/splitscreen</a>
 * (MIT License, Copyright (c) 2023 pcal.net) — a mod that solves exactly this "position windows for
 * local multiplayer" problem. Reimplemented against SteamPad's own {@link WindowArrangeController} /
 * mixin plumbing rather than vendoring the mod's code directly.
 */
public enum WindowArrangeMode {
    WINDOWED, LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, FULLSCREEN;

    public boolean isFullscreen() {
        return this == FULLSCREEN;
    }

    /**
     * Target window bounds for this mode. {@code savedWindowed} is the bounds to restore for
     * {@link #WINDOWED} (remembered from before tiling started); {@link #FULLSCREEN} is handled by the
     * caller (it doesn't reposition, it asks the window to go exclusive-fullscreen) so it is never
     * passed through this method in practice, but returns the full screen rect for completeness.
     */
    public WindowBounds bounds(WindowBounds screen, WindowBounds savedWindowed, int gap) {
        int sx = screen.x(), sy = screen.y(), sw = screen.width(), sh = screen.height();
        int halfW = Math.max(1, sw / 2 - gap);
        int halfH = Math.max(1, sh / 2 - gap);
        int rightX = sx + sw / 2 + gap;
        int bottomY = sy + sh / 2 + gap;
        return switch (this) {
            case WINDOWED     -> savedWindowed;
            case LEFT         -> new WindowBounds(sx, sy, halfW, sh);
            case RIGHT        -> new WindowBounds(rightX, sy, halfW, sh);
            case TOP          -> new WindowBounds(sx, sy, sw, halfH);
            case BOTTOM       -> new WindowBounds(sx, bottomY, sw, halfH);
            case TOP_LEFT     -> new WindowBounds(sx, sy, halfW, halfH);
            case TOP_RIGHT    -> new WindowBounds(rightX, sy, halfW, halfH);
            case BOTTOM_LEFT  -> new WindowBounds(sx, bottomY, halfW, halfH);
            case BOTTOM_RIGHT -> new WindowBounds(rightX, bottomY, halfW, halfH);
            case FULLSCREEN   -> screen;
        };
    }
}
