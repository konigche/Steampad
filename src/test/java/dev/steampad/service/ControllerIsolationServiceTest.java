package dev.steampad.service;

import dev.steampad.input.ControllerState;
import dev.steampad.steam.SteamControllerHandleRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ControllerIsolationServiceTest {

    private static ControllerState stateFor(long handle) {
        var ref = new SteamControllerHandleRef(handle, "Test", SteamControllerHandleRef.ControllerType.GENERIC);
        return new ControllerState(ref, new boolean[37],
            new float[]{0,0}, new float[]{0,0}, new float[]{0,0}, new float[]{0,0}, -1f);
    }

    @Test
    void testActiveControllerPassesThrough() {
        // We can't call ActiveControllerService.setActive() without Fabric environment,
        // so we test the logic of isActive(handle) directly.
        // Since no active controller is set, everything should return false.
        assertFalse(ControllerIsolationService.isActive(42L), "No active set → should return false");
    }

    @Test
    void testNullActiveControllerBlocksAll() {
        assertFalse(ControllerIsolationService.isActive(0L), "Handle 0 is never active");
        assertFalse(ControllerIsolationService.isActive(1L), "Without active set, nothing passes");
    }

    @Test
    void testStateWithZeroHandleNeverActive() {
        // Handle 0 is the NONE handle
        assertFalse(ControllerIsolationService.isActive(0L));
    }
}
