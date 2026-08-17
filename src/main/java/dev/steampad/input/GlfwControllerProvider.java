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

                String guid = safeGuid(jid);
                result.add(new SteamControllerHandleRef(
                        handleForJoystick(jid),
                        name,
                        guessType(name),
                        vendorFromGuid(guid),
                        productFromGuid(guid),
                        versionFromGuid(guid),
                        ""));   // GLFW exposes no serial — see SteamControllerHandleRef.serial
            } catch (Throwable t) {
                // GLFW not ready or a transient query failure — skip this slot, keep enumerating.
                LogUtil.debug("[SteamPad] GLFW joystick query failed for jid {}: {}", jid, t.getMessage());
            }
        }
        return result;
    }

    private static String safeGuid(int jid) {
        try { return GLFW.glfwGetJoystickGUID(jid); } catch (Throwable t) { return null; }
    }

    /**
     * USB vendor ID out of a GLFW joystick GUID, or 0 when it isn't encoded there.
     *
     * <p>GLFW hands back the joystick GUID in SDL's own format — the same 16 bytes SDL builds in
     * {@code SDL_CreateJoystickGUID}, rendered as 32 lowercase hex chars, byte 0 first:
     *
     * <pre>
     *   bytes 0-1   bus type          chars 0-3
     *   bytes 2-3   CRC16 of the name chars 4-7
     *   bytes 4-5   VENDOR  (LE)      chars 8-11
     *   bytes 6-7   zero              chars 12-15
     *   bytes 8-9   PRODUCT (LE)      chars 16-19
     *   bytes 10-11 zero              chars 20-23
     *   bytes 12-13 version (LE)      chars 24-27
     * </pre>
     *
     * <p>This is the only identity GLFW and SDL3 share for the same physical device: their reported
     * NAMES can differ completely for one pad (real case on Windows: SDL3 says "8Bitdo Ultimate 2
     * Wireless Controller" while GLFW says "Eje 6 botón 24 controlador para juegos con pulsador
     * superior"), which is what made the same pad show up twice in the controller list.
     *
     * <p>Returns 0 for the synthetic GUIDs some backends use (notably XInput on Windows), where no
     * real VID/PID is encoded — callers must treat 0 as "unknown", never as a vendor.
     */
    static int vendorFromGuid(String guid) {
        return le16(guid, 8);
    }

    /** USB product ID out of a GLFW joystick GUID, or 0 — see {@link #vendorFromGuid}. */
    static int productFromGuid(String guid) {
        return le16(guid, 16);
    }

    /** Product/driver version out of a GLFW joystick GUID (bytes 12-13), or 0 — see
     *  {@link #vendorFromGuid} for the byte layout. */
    static int versionFromGuid(String guid) {
        return le16(guid, 24);
    }

    /** Little-endian 16-bit value from two hex bytes at {@code start}, or 0 if unparseable. */
    private static int le16(String guid, int start) {
        if (guid == null || guid.length() < start + 4) return 0;
        try {
            int lo = Integer.parseInt(guid.substring(start, start + 2), 16);
            int hi = Integer.parseInt(guid.substring(start + 2, start + 4), 16);
            return (hi << 8) | lo;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Best-effort controller family from the device name, for glyph/theme selection.
     *
     * <p>Note this is the FUNCTIONAL family (which button glyphs to draw), not the manufacturer —
     * a third-party pad running in an Xbox-compatible mode correctly lands on {@code XBOX} here even
     * though its brand label, resolved separately from the USB vendor ID, says otherwise.
     */
    public static SteamControllerHandleRef.ControllerType guessType(String rawName) {
        String n = rawName == null ? "" : rawName.toLowerCase(Locale.ROOT).trim();
        if (n.contains("steam deck")) return SteamControllerHandleRef.ControllerType.STEAM_DECK;
        if (n.contains("steam")) return SteamControllerHandleRef.ControllerType.STEAM_CONTROLLER;
        if (n.contains("xbox") || n.contains("x-box") || n.contains("xinput") || n.contains("microsoft"))
            return SteamControllerHandleRef.ControllerType.XBOX;
        if (n.contains("dualsense") || n.contains("dualshock") || n.contains("playstation")
                || n.contains("sony") || n.contains("ps3") || n.contains("ps4") || n.contains("ps5")
                // "Wireless Controller" is the LITERAL, whole name a DualShock 4 reports — so it only
                // counts as a PlayStation signal when it IS the name, not when it merely appears
                // inside a longer branded one. As a substring it matched things like "8Bitdo Ultimate
                // 2 Wireless Controller", which then drew PlayStation glyphs on an Xbox-layout pad.
                || n.equals("wireless controller"))
            return SteamControllerHandleRef.ControllerType.PLAYSTATION;
        if (n.contains("switch") || n.contains("joy-con") || n.contains("joycon") || n.contains("nintendo"))
            return SteamControllerHandleRef.ControllerType.SWITCH;
        return SteamControllerHandleRef.ControllerType.GENERIC;
    }
}
