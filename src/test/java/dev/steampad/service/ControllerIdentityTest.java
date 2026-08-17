package dev.steampad.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

import dev.steampad.service.ControllerIdentity.Signals;

/**
 * The identity rules that decide which saved configuration a physical controller gets back.
 *
 * <p>Every case here is a scenario the user actually reported ("cuando se conectan recuerdan otras
 * configuraciones antiguas", "no lo recuerda como predeterminado", two instances confusing pads) —
 * the point of the class under test is that these are decided by tested logic instead of by whatever
 * number SDL happened to hand out this time.
 */
class ControllerIdentityTest {

    private static final Signals PRO3 =
            new Signals(0x2DC8, 0x3012, 0x0100, "", "8BitDo Pro 3");
    private static final Signals ULTIMATE3 =
            new Signals(0x2DC8, 0x3106, 0x0100, "", "8BitDo Ultimate 3");

    private static Map<String, Signals> registry(Object... pairs) {
        Map<String, Signals> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], (Signals) pairs[i + 1]);
        return m;
    }

    private static Set<String> taken(String... keys) {
        return new LinkedHashSet<>(java.util.Arrays.asList(keys));
    }

    @Test
    @DisplayName("A brand new pad gets a key built from its USB vendor/product pair")
    void newPadUsesUsbIds() {
        assertEquals("2dc8-3012", ControllerIdentity.resolve(PRO3, registry(), taken()));
    }

    @Test
    @DisplayName("The same pad resolves to the same key regardless of the handle it arrives on")
    void sameKeyAcrossReconnects() {
        Map<String, Signals> known = registry("2dc8-3012", PRO3);
        // A reconnect changes the handle and can change the reported NAME (different backend/mode);
        // neither participates in the decision.
        Signals afterReconnect = new Signals(0x2DC8, 0x3012, 0x0100, "",
                "Xbox 360 Controller for Windows");
        assertEquals("2dc8-3012", ControllerIdentity.resolve(afterReconnect, known, taken()));
    }

    @Test
    @DisplayName("Two different models never share a key, whatever order they enumerate in")
    void differentModelsStayApart() {
        Map<String, Signals> known = registry("2dc8-3012", PRO3, "2dc8-3106", ULTIMATE3);
        Set<String> used = taken();
        String a = ControllerIdentity.resolve(ULTIMATE3, known, used);
        used.add(a);
        String b = ControllerIdentity.resolve(PRO3, known, used);
        assertEquals("2dc8-3106", a);
        assertEquals("2dc8-3012", b);
    }

    @Test
    @DisplayName("Two identical pads get distinct keys instead of collapsing onto one")
    void identicalPadsGetDistinctKeys() {
        Map<String, Signals> known = registry();
        Set<String> used = taken();
        String first = ControllerIdentity.resolve(PRO3, known, used);
        used.add(first);
        known.put(first, PRO3);
        String second = ControllerIdentity.resolve(PRO3, known, used);
        assertNotEquals(first, second, "a second identical pad must not reuse the first one's config");
        assertEquals("2dc8-3012", first);
        assertEquals("2dc8-3012_2", second);
    }

    @Test
    @DisplayName("Serial numbers pin two identical pads to their own config across sessions")
    void serialsPinIdenticalPads() {
        Signals unitA = new Signals(0x2DC8, 0x3012, 0x0100, "AAAA1111", "8BitDo Pro 3");
        Signals unitB = new Signals(0x2DC8, 0x3012, 0x0100, "BBBB2222", "8BitDo Pro 3");
        Map<String, Signals> known = registry("2dc8-3012", unitA, "2dc8-3012_2", unitB);
        // Enumerated in the OPPOSITE order to how they were first registered — the whole point of the
        // serial is that this must not swap their configurations.
        Set<String> used = taken();
        String b = ControllerIdentity.resolve(unitB, known, used);
        used.add(b);
        String a = ControllerIdentity.resolve(unitA, known, used);
        assertEquals("2dc8-3012_2", b);
        assertEquals("2dc8-3012", a);
    }

    @Test
    @DisplayName("A pad whose serial is unreadable this session still finds its own config")
    void missingSerialStillMatches() {
        // The registry learned the serial on a session where SDL used its HIDAPI driver; today the pad
        // came up on evdev (Flatpak sandbox, Steam holding the HID device) and reports nothing. Losing
        // the whole profile over that would be the very bug this class exists to prevent.
        Signals remembered = new Signals(0x2DC8, 0x3012, 0x0100, "AAAA1111", "8BitDo Pro 3");
        Map<String, Signals> known = registry("2dc8-3012", remembered);
        assertEquals("2dc8-3012", ControllerIdentity.resolve(PRO3, known, taken()));
    }

    @Test
    @DisplayName("A key already given to another connected pad is never handed out twice")
    void takenKeysAreRespected() {
        Map<String, Signals> known = registry("2dc8-3012", PRO3);
        String second = ControllerIdentity.resolve(PRO3, known, taken("2dc8-3012"));
        assertEquals("2dc8-3012_2", second);
    }

    @Test
    @DisplayName("Pads with no USB ids fall back to the name, but still resolve stably")
    void noUsbIdsFallsBackToName() {
        // GLFW's synthetic XInput GUIDs encode no real VID/PID, and Steam Input handles report none.
        Signals xinput = new Signals(0, 0, 0, "", "XInput Controller #1");
        String key = ControllerIdentity.resolve(xinput, registry(), taken());
        assertEquals("name-xinput_controller_1", key);
        assertEquals(key, ControllerIdentity.resolve(xinput, registry(key, xinput), taken()));
    }

    @Test
    @DisplayName("Keys are always safe to use as file names")
    void keysAreFilenameSafe() {
        Signals weird = new Signals(0, 0, 0, "", "Mando \"raro\"/con: símbolos*?");
        String key = ControllerIdentity.resolve(weird, registry(), taken());
        assertTrue(key.matches("[a-z0-9_-]+"), "not filename-safe: " + key);
    }

    @Test
    @DisplayName("An unnamed, unidentifiable device still produces a usable key")
    void emptyDeviceStillResolves() {
        String key = ControllerIdentity.resolve(new Signals(0, 0, 0, null, null), registry(), taken());
        assertEquals("unknown", key);
    }
}
