package dev.steampad.input;

import dev.steampad.steam.SteamControllerHandleRef.ControllerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins down two controller-identity bugs that were only visible in a real hardware debug dump. Both
 * fail silently (wrong button glyphs) rather than throwing, so nothing else would catch them.
 *
 * <p>The cross-backend merge that consumes the VID/PID parsed here lives in another package and has
 * its own test — see {@code dev.steampad.service.ControllerMergeTest}.
 */
class ControllerIdentityTest {

    // ---- name → functional family ----------------------------------------------------------

    @Test
    void eightBitDoIsNotMistakenForAPlayStationPad() {
        // Verbatim from the user's dump. It used to hit the "wireless controller" substring rule and
        // resolve to PLAYSTATION, which draws Sony glyphs on an Xbox-layout pad.
        assertEquals(ControllerType.GENERIC,
                GlfwControllerProvider.guessType("8Bitdo Ultimate 2 Wireless Controller"));
    }

    @Test
    void aRealDualShock4StillResolvesToPlayStation() {
        // The DualShock 4's whole reported name really is exactly this — the reason the rule exists.
        assertEquals(ControllerType.PLAYSTATION, GlfwControllerProvider.guessType("Wireless Controller"));
        assertEquals(ControllerType.PLAYSTATION, GlfwControllerProvider.guessType("  Wireless Controller  "));
        assertEquals(ControllerType.PLAYSTATION, GlfwControllerProvider.guessType("DualSense Wireless Controller"));
    }

    @Test
    void otherFamiliesAreUnaffected() {
        assertEquals(ControllerType.XBOX, GlfwControllerProvider.guessType("Xbox Elite Wireless Controller"));
        assertEquals(ControllerType.SWITCH, GlfwControllerProvider.guessType("Nintendo Switch Pro Controller"));
        assertEquals(ControllerType.STEAM_DECK, GlfwControllerProvider.guessType("Steam Deck Controller"));
        assertEquals(ControllerType.GENERIC, GlfwControllerProvider.guessType(""));
        assertEquals(ControllerType.GENERIC, GlfwControllerProvider.guessType(null));
    }

    // ---- GLFW joystick GUID → VID/PID ------------------------------------------------------

    @Test
    void parsesVendorAndProductOutOfAnSdlFormatGuid() {
        // SDL layout, little-endian: bytes 4-5 vendor, bytes 8-9 product. Values here are the 8BitDo
        // Ultimate 2 the user actually has (vid 0x2DC8 / pid 0x6012, per the dump).
        //             bus  crc  vend  ----  prod  ----  vers  --
        String guid = "0300" + "0000" + "c82d" + "0000" + "1260" + "0000" + "0101" + "0000";
        assertEquals(0x2DC8, GlfwControllerProvider.vendorFromGuid(guid));
        assertEquals(0x6012, GlfwControllerProvider.productFromGuid(guid));
    }

    @Test
    void returnsZeroForGuidsWithNoUsableIds() {
        // XInput's synthetic GUID on Windows encodes no real VID/PID — must read as "unknown" (0)
        // rather than as vendor 0x0000, which would make unrelated pads look like the same device.
        assertEquals(0, GlfwControllerProvider.vendorFromGuid("00000000000000000000000000000000"));
        assertEquals(0, GlfwControllerProvider.vendorFromGuid(null));
        assertEquals(0, GlfwControllerProvider.vendorFromGuid("tooshort"));
        assertEquals(0, GlfwControllerProvider.vendorFromGuid("zzzz0000zzzz00000000000000000000"));
    }
}
