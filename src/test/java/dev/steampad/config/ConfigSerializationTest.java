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
        // Multi-wheel contract: defaults have exactly ONE wheel with 8 active slots and its slot
        // list padded to MAX_SLOTS. Extra wheels are added/removed from the editor (max MAX_WHEELS).
        RadialConfig defaults = RadialConfig.defaults();
        assertEquals(1, defaults.wheelCount(), "Defaults have a single wheel");
        assertEquals(8, defaults.slotCountFor(0), "Wheel defaults to 8 active slots");
        assertEquals(RadialConfig.MAX_SLOTS, defaults.slotsFor(0).size(), "Slot list padded to MAX_SLOTS");
        assertEquals(1, defaults.addWheel(), "Adding a wheel returns its page index");
        assertEquals(2, defaults.wheelCount(), "Wheel was added");
        assertTrue(defaults.removeWheel(1), "Added wheel can be removed");
        assertFalse(defaults.removeWheel(0), "The last wheel can never be removed");
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
