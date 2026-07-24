package dev.steampad.input;

import dev.steampad.steam.SteamControllerHandleRef;
import dev.steampad.util.LogUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controller detection fallback built on GLFW's joystick/gamepad API.
 *
 * <p>Why this exists: Steam Input (via Steamworks4j / ISteamController) only initializes when
 * Minecraft runs inside a real Steam app context. On Bazzite / Steam Deck / ROG Ally launched
 * from Prism (Flatpak), {@code SteamAPI.init()} fails with "no appID found" because the Flatpak
 * sandbox isolates the Java process from the Steam client — so no controller is ever reported.
 *
 * <p>GLFW is always available (LWJGL bundles it and Minecraft already initializes it), and it
 * sees gamepads directly from the OS. When Steam Input is active in Game Mode/gamescope, Steam
 * exposes a virtual Xbox pad via uinput which GLFW detects as a standard gamepad. On the desktop
 * it sees the raw device. This makes controller detection work everywhere Steam Input does not.
 *
 * <p>All GLFW joystick calls must run on the render thread (the GLFW main thread). Callers here
 * are the client tick and screen render/init, all of which are on the render thread.
 *
 * <p>This is detection only. Reading buttons/axes for input dispatch through GLFW is a separate
 * concern (future work); Steam Input remains the primary input backend per the project's design
 * constraints.
 */
public final class GlfwControllerProvider {

    /**
     * High tag in the synthetic handle so a GLFW-sourced controller can be told apart from a real
     * Steam native handle (which is a small pointer-sized long). ASCII "GLFW" in the top 4 bytes.
     */
    public static final long GLFW_HANDLE_BASE = 0x474C465700000000L;

    private GlfwControllerProvider() {}

    /** True if the handle was minted by this provider (i.e. the controller is a GLFW gamepad). */
    public static boolean isGlfwHandle(long handle) {
        return (handle & 0xFFFFFFFF00000000L) == GLFW_HANDLE_BASE;
    }

    /** Recovers the GLFW joystick id (0..15) from a synthetic handle, or -1 if not a GLFW handle. */
    public static int joystickId(long handle) {
        if (!isGlfwHandle(handle)) return -1;
        return (int) (handle & 0xFFL) - 1;
    }

    private static long handleForJoystick(int jid) {
        return GLFW_HANDLE_BASE | (long) (jid + 1);
    }

    /**
     * Enumerates every connected GLFW joystick/gamepad as a {@link SteamControllerHandleRef}.
     * Reuses the existing controller data model so all UI/services work unchanged.
     */
    public static List<SteamControllerHandleRef> poll() {
        List<SteamControllerHandleRef> result = new ArrayList<>();
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            try {
                if (!GLFW.glfwJoystickPresent(jid)) continue;

                boolean isGamepad = GLFW.glfwJoystickIsGamepad(jid);
                String name = isGamepad ? GLFW.glfwGetGamepadName(jid) : GLFW.glfwGetJoystickName(jid);
                if (name == null || name.isBlank()) {
                    name = (isGamepad ? "Gamepad " : "Joystick ") + (jid + 1);
                }

                result.add(new SteamControllerHandleRef(
                        handleForJoystick(jid),
                        name,
                        guessType(name)));
            } catch (Throwable t) {
                // GLFW not ready or a transient query failure — skip this slot, keep enumerating.
                LogUtil.debug("[SteamPad] GLFW joystick query failed for jid {}: {}", jid, t.getMessage());
            }
        }
        return result;
    }

    /** Best-effort controller family from the device name, for icon/theme selection. */
    public static SteamControllerHandleRef.ControllerType guessType(String rawName) {
        String n = rawName.toLowerCase(Locale.ROOT);
        if (n.contains("steam deck")) return SteamControllerHandleRef.ControllerType.STEAM_DECK;
        if (n.contains("steam")) return SteamControllerHandleRef.ControllerType.STEAM_CONTROLLER;
        if (n.contains("xbox") || n.contains("x-box") || n.contains("xinput") || n.contains("microsoft"))
            return SteamControllerHandleRef.ControllerType.XBOX;
        if (n.contains("dualsense") || n.contains("dualshock") || n.contains("playstation")
                || n.contains("sony") || n.contains("ps3") || n.contains("ps4") || n.contains("ps5")
                || n.contains("wireless controller"))
            return SteamControllerHandleRef.ControllerType.PLAYSTATION;
        if (n.contains("switch") || n.contains("joy-con") || n.contains("joycon") || n.contains("nintendo"))
            return SteamControllerHandleRef.ControllerType.SWITCH;
        return SteamControllerHandleRef.ControllerType.GENERIC;
    }
}
