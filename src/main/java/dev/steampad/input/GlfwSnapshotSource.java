package dev.steampad.input;

import dev.steampad.util.LogUtil;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Reads a {@link GamepadSnapshot} from a GLFW joystick.
 *
 * <p>Primary path: {@code glfwGetGamepadState} (uses the SDL gamepad mapping DB, extended at startup
 * with 8BitDo entries via {@link GamepadMappings}). Fallback path: raw joystick buttons/axes/hats,
 * for devices that lack a gamepad mapping (e.g. an 8BitDo pad in a non-XInput mode). The raw mapping
 * assumes the common DirectInput-style layout, which covers most pads well enough to be usable until
 * a proper mapping is present.
 */
public final class GlfwSnapshotSource {

    private static final GLFWGamepadState GP = GLFWGamepadState.create();

    private GlfwSnapshotSource() {}

    /** Fills {@code out} from joystick {@code jid}. Returns false if the joystick is absent. */
    public static boolean read(int jid, GamepadSnapshot out) {
        if (jid < 0 || !GLFW.glfwJoystickPresent(jid)) return false;

        if (GLFW.glfwGetGamepadState(jid, GP)) {
            // GLFW's mapped gamepad state only exposes the 15 standard buttons (0..14) — it has no
            // concept of the extra paddle/misc buttons (15..22, SDL3-only). Looping to BUTTON_COUNT
            // here crashes with an IndexOutOfBoundsException from GLFWGamepadState.buttons() the
            // moment i reaches 15.
            for (int i = 0; i < GamepadSnapshot.STANDARD_BUTTON_COUNT; i++) {
                out.buttons[i] = GP.buttons(i) == GLFW.GLFW_PRESS;
            }
            for (int i = GamepadSnapshot.STANDARD_BUTTON_COUNT; i < GamepadSnapshot.BUTTON_COUNT; i++) {
                out.buttons[i] = false;   // extras unavailable on this path — explicit, not stale data
            }
            out.axes[GamepadSnapshot.AXIS_LEFT_X]       = GP.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X);
            out.axes[GamepadSnapshot.AXIS_LEFT_Y]       = GP.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y);
            out.axes[GamepadSnapshot.AXIS_RIGHT_X]      = GP.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X);
            out.axes[GamepadSnapshot.AXIS_RIGHT_Y]      = GP.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y);
            out.axes[GamepadSnapshot.AXIS_LEFT_TRIGGER]  = GP.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER);
            out.axes[GamepadSnapshot.AXIS_RIGHT_TRIGGER] = GP.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER);
            return true;
        }

        // No gamepad mapping — read raw and apply a best-effort DirectInput-style layout.
        //
        // This path GUESSES the button order, so a pad that lands here has its controls in the wrong
        // places (the reported "LB queda para tercera persona, RB para menu" after switching a pad off
        // and on again). Until now it was completely silent, which is why several rounds of fixes had
        // no evidence to work from: the log looked healthy while the pad was being read through a
        // layout nobody chose. Warned once per joystick slot, naming the pad, so the next log says
        // outright which controller fell off the mapped path and when.
        if (rawWarned.add(jid)) {
            String nm;
            try { nm = GLFW.glfwGetJoystickName(jid); } catch (Throwable t) { nm = null; }
            LogUtil.warn("[SteamPad] '{}' (GLFW slot {}) has NO gamepad mapping — its buttons are being "
                    + "GUESSED with a generic DirectInput layout, so they will NOT match what you "
                    + "configured. This is what \"los botones quedan locos\" looks like from the inside. "
                    + "It means the pad came back on the GLFW path instead of SDL3's. Runtime detected: "
                    + "{}. Restarting the game with the pad already switched on restores the correct "
                    + "mapping. Please report this line — it identifies which pad and when.",
                    nm == null || nm.isBlank() ? ("joystick " + (jid + 1)) : nm, jid,
                    dev.steampad.platform.LinuxRuntimeInspector.containerName());
        }
        return readRaw(jid, out);
    }

    /** GLFW slots already warned about the unmapped raw path, so the warning can't spam per frame. */
    private static final java.util.Set<Integer> rawWarned = new java.util.HashSet<>();

    /** Clears the once-per-slot warning latch so a re-plug can report again. */
    public static void forgetRawWarning(int jid) {
        rawWarned.remove(jid);
    }

    private static boolean readRaw(int jid, GamepadSnapshot out) {
        FloatBuffer axes = GLFW.glfwGetJoystickAxes(jid);
        ByteBuffer btns = GLFW.glfwGetJoystickButtons(jid);
        ByteBuffer hats = GLFW.glfwGetJoystickHats(jid);
        if (axes == null && btns == null) return false;

        // Reset.
        for (int i = 0; i < out.buttons.length; i++) out.buttons[i] = false;
        out.axes[GamepadSnapshot.AXIS_LEFT_TRIGGER] = -1f;
        out.axes[GamepadSnapshot.AXIS_RIGHT_TRIGGER] = -1f;

        if (axes != null) {
            int n = axes.remaining();
            if (n > 0) out.axes[GamepadSnapshot.AXIS_LEFT_X]  = axes.get(0);
            if (n > 1) out.axes[GamepadSnapshot.AXIS_LEFT_Y]  = axes.get(1);
            // Right stick is commonly axes 2/3 (some pads use 3/4); prefer 2/3.
            if (n > 2) out.axes[GamepadSnapshot.AXIS_RIGHT_X] = axes.get(2);
            if (n > 3) out.axes[GamepadSnapshot.AXIS_RIGHT_Y] = axes.get(3);
            // Triggers, when exposed as axes, are often 4/5.
            if (n > 4) out.axes[GamepadSnapshot.AXIS_LEFT_TRIGGER]  = axes.get(4);
            if (n > 5) out.axes[GamepadSnapshot.AXIS_RIGHT_TRIGGER] = axes.get(5);
        }

        if (btns != null) {
            int n = btns.remaining();
            // DirectInput face/shoulder ordering used by most pads incl. 8BitDo in D-input.
            mapRawButton(out, GamepadSnapshot.A, btns, n, 0);
            mapRawButton(out, GamepadSnapshot.B, btns, n, 1);
            mapRawButton(out, GamepadSnapshot.X, btns, n, 2);
            mapRawButton(out, GamepadSnapshot.Y, btns, n, 3);
            mapRawButton(out, GamepadSnapshot.LEFT_BUMPER, btns, n, 4);
            mapRawButton(out, GamepadSnapshot.RIGHT_BUMPER, btns, n, 5);
            mapRawButton(out, GamepadSnapshot.BACK, btns, n, 6);
            mapRawButton(out, GamepadSnapshot.START, btns, n, 7);
            mapRawButton(out, GamepadSnapshot.LEFT_THUMB, btns, n, 8);
            mapRawButton(out, GamepadSnapshot.RIGHT_THUMB, btns, n, 9);
            mapRawButton(out, GamepadSnapshot.GUIDE, btns, n, 10);
        }

        // D-pad usually arrives as a hat.
        if (hats != null && hats.remaining() > 0) {
            int h = hats.get(0);
            out.buttons[GamepadSnapshot.DPAD_UP]    = (h & GLFW.GLFW_HAT_UP) != 0;
            out.buttons[GamepadSnapshot.DPAD_RIGHT] = (h & GLFW.GLFW_HAT_RIGHT) != 0;
            out.buttons[GamepadSnapshot.DPAD_DOWN]  = (h & GLFW.GLFW_HAT_DOWN) != 0;
            out.buttons[GamepadSnapshot.DPAD_LEFT]  = (h & GLFW.GLFW_HAT_LEFT) != 0;
        }
        return true;
    }

    private static void mapRawButton(GamepadSnapshot out, int target, ByteBuffer btns, int count, int rawIndex) {
        if (rawIndex < count) out.buttons[target] = btns.get(rawIndex) == GLFW.GLFW_PRESS;
    }
}
