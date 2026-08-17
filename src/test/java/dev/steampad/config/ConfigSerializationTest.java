package dev.steampad.config;

import dev.steampad.util.JsonUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigSerializationTest {

    @Test
    void testGlobalConfigRoundTrip() {
        GlobalConfig original = GlobalConfig.defaults();
        original.uiSounds = false;
        original.ingameButtonGuideScale = 1.5f;
        original.blockReachAround = GlobalConfig.BlockReachAround.SINGLEPLAYER_ONLY;
        original.controllerDisplayNames.put("12345", "My Controller");
        original.steamInputSlots.put("1", "key.zoom.zoom");
        original.steamInputSlots.put("10", "key.inventory");

        String json = JsonUtil.toJson(original);
        GlobalConfig restored = JsonUtil.fromJson(json, GlobalConfig.class);

        assertNotNull(restored);
        assertEquals(false, restored.uiSounds);
        assertEquals(1.5f, restored.ingameButtonGuideScale, 0.001f);
        assertEquals(GlobalConfig.BlockReachAround.SINGLEPLAYER_ONLY, restored.blockReachAround);
        assertEquals("My Controller", restored.controllerDisplayNames.get("12345"));
        assertEquals("key.zoom.zoom", restored.steamInputSlots.get("1"));
        assertEquals("key.inventory", restored.steamInputSlots.get("10"));
    }

    @Test
    void testControllerConfigRoundTrip() {
        ControllerConfig original = ControllerConfig.defaults();
        original.horizontalSensitivity = 2.5f;
        original.invertLookY = true;
        original.sneakMode = ControllerConfig.SneakMode.TOGGLE;
        original.leftStickDeadzone = 0.2f;
        original.vibrationMaster = 0.75f;
        original.gyroRequireButton = ControllerConfig.GyroRequireButton.TOGGLE;

        String json = JsonUtil.toJson(original);
        ControllerConfig restored = JsonUtil.fromJson(json, ControllerConfig.class);

        assertNotNull(restored);
        assertEquals(2.5f, restored.horizontalSensitivity, 0.001f);
        assertTrue(restored.invertLookY);
        assertEquals(ControllerConfig.SneakMode.TOGGLE, restored.sneakMode);
        assertEquals(0.2f, restored.leftStickDeadzone, 0.001f);
        assertEquals(0.75f, restored.vibrationMaster, 0.001f);
        assertEquals(ControllerConfig.GyroRequireButton.TOGGLE, restored.gyroRequireButton);
    }

    @Test
    void testCorruptJsonReturnsNull() {
        String corrupt = "{not valid json!!!}";
        GlobalConfig result = null;
        try {
            result = JsonUtil.fromJson(corrupt, GlobalConfig.class);
        } catch (Exception e) {
            // Expected: Gson throws on invalid JSON
        }
        // loadFromFile handles this with a try-catch and returns defaults
        // Here we just verify fromJson throws rather than producing garbage
        assertNull(result, "Corrupt JSON should fail to parse");
    }

    @Test
    void testBindingConfigDefaultsHaveEntries() {
        BindingConfig defaults = BindingConfig.defaults();
        assertFalse(defaults.bindings.isEmpty(), "Defaults should have at least one binding");
        assertTrue(defaults.bindings.containsKey("gameplay.jump"), "Should have jump binding");
    }

    @Test
    void testRadialConfigDefaultsHaveEightSlots() {
        // Multi-wheel contract: defaults now ship with the user's own wheel (8 active slots, padded
        // to MAX_SLOTS) PLUS the built-in vanilla-shortcuts wheel, seeded once by normalize() and
        // kept pinned to the LAST page (feedback round 2: "nunca debe ser la primera... si se crea
        // una rueda mandar esta a lo último") — addWheel() inserts new user wheels BEFORE it instead
        // of appending after, so it never needs a separate reorder pass to stay last.
        RadialConfig defaults = RadialConfig.defaults();
        assertEquals(2, defaults.wheelCount(), "Defaults: the user's wheel + the vanilla-shortcuts wheel");
        assertEquals(8, defaults.slotCountFor(0), "Wheel 0 defaults to 8 active slots");
        assertEquals(RadialConfig.MAX_SLOTS, defaults.slotsFor(0).size(), "Slot list padded to MAX_SLOTS");
        assertTrue(defaults.wheels.get(1).vanillaShortcutsWheel, "Wheel 1 is the vanilla-shortcuts wheel");

        assertEquals(1, defaults.addWheel(), "A new user wheel is inserted BEFORE the vanilla-shortcuts one");
        assertEquals(3, defaults.wheelCount(), "Wheel was added");
        assertTrue(defaults.wheels.get(2).vanillaShortcutsWheel, "Vanilla-shortcuts wheel is still last");

        assertTrue(defaults.removeWheel(1), "The added wheel can be removed");
        assertEquals(2, defaults.wheelCount(), "Back to 2 wheels");
        assertTrue(defaults.removeWheel(1), "The vanilla-shortcuts wheel itself can be removed (it's optional)");
        assertFalse(defaults.removeWheel(0), "The last remaining wheel can never be removed");
    }

    @Test
    void testBindingConfigRoundTrip() {
        BindingConfig original = BindingConfig.defaults();
        String json = JsonUtil.toJson(original);
        BindingConfig restored = JsonUtil.fromJson(json, BindingConfig.class);

        assertNotNull(restored);
        assertEquals(original.bindings.size(), restored.bindings.size(), "Binding count should match");
    }
}
