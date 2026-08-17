package dev.steampad.compat.mc;

import com.mojang.blaze3d.platform.Window;

/**
 * Version shim for the GLFW window handle getter, renamed between versions. Both are public, so this
 * is a pure rename with no access widening involved:
 *
 * <ul>
 *   <li>1.21.1 — {@code Window.getWindow()} (backing field {@code window})</li>
 *   <li>1.21.10 — {@code Window.handle()} (backing field {@code handle})</li>
 * </ul>
 *
 * <p>The exact boundary between the two was not probed — every version this project builds falls
 * cleanly on one side or the other, and a variant in between would fail loudly at compile time rather
 * than silently. Used by the virtual cursor, the window-tiling controller, the gamepad dispatcher and
 * the synthetic-input paths, all of which need the raw handle to talk to GLFW.
 */
public final class WindowCompat {

    private WindowCompat() {}

    /** The raw GLFW window handle. */
    public static long handle(Window window) {
        //? if >=1.21.2 {
        return window.handle();
        //?} else {
        /*return window.getWindow();*/
        //?}
    }
}
