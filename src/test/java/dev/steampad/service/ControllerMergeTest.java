package dev.steampad.service;

import dev.steampad.steam.SteamControllerHandleRef;
import dev.steampad.steam.SteamControllerHandleRef.ControllerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the SDL3 + GLFW controller merge, whose failure mode is a phantom extra controller in the
 * selection screen rather than an exception — so only a test (or a user screenshot) catches it.
 *
 * <p>Lives in this package on purpose: {@code mergeBackends} stays package-private, since nothing
 * outside {@link ControllerManager} should be merging backend lists.
 */
class ControllerMergeTest {

    private static SteamControllerHandleRef pad(long handle, String name, int vid, int pid) {
        return new SteamControllerHandleRef(handle, name, ControllerType.GENERIC, vid, pid);
    }

    @Test
    void theSamePhysicalPadSeenByBothBackendsIsListedOnce() {
        // The exact reported case: one pad, two backends, two completely unrelated names — which is
        // why the old name-based dedup could never have caught it.
        var sdl = List.of(pad(1L, "8Bitdo Ultimate 2 Wireless Controller", 0x2DC8, 0x6012));
        var glfw = List.of(pad(2L, "Eje 6 botón 24 controlador para juegos con pulsador superior",
                0x2DC8, 0x6012));
        var merged = ControllerManager.mergeBackends(sdl, glfw);
        assertEquals(1, merged.size(), "same VID/PID on both backends must collapse to one entry");
        assertEquals(1L, merged.get(0).handle, "the SDL3 entry is the one kept");
    }

    @Test
    void aGenuinelyDifferentPadOnlyGlfwCanSeeIsStillListed() {
        // Regression guard for the ROG Ally case the merge exists for in the first place.
        var sdl = List.of(pad(1L, "8Bitdo Ultimate 2", 0x2DC8, 0x6012));
        var glfw = List.of(pad(2L, "ASUS ROG Ally Controller", 0x0B05, 0x1ABE));
        assertEquals(2, ControllerManager.mergeBackends(sdl, glfw).size(),
                "a pad SDL3 cannot see must not be dropped");
    }

    @Test
    void twoIdenticalPadsAreNotCollapsedIntoOne() {
        // Why the match is one-to-one instead of set-based: the same model twice shares a VID/PID.
        var sdl = List.of(pad(1L, "8Bitdo Ultimate 2", 0x2DC8, 0x6012),
                          pad(2L, "8Bitdo Ultimate 2", 0x2DC8, 0x6012));
        var glfw = List.of(pad(3L, "Generic Pad A", 0x2DC8, 0x6012),
                           pad(4L, "Generic Pad B", 0x2DC8, 0x6012));
        assertEquals(2, ControllerManager.mergeBackends(sdl, glfw).size(),
                "two SDL3 pads absorb exactly two GLFW duplicates");
    }

    @Test
    void padsWithoutUsableIdsFallBackToNameMatching() {
        var sdl = List.of(pad(1L, "Some Controller", 0, 0));
        var glfw = List.of(pad(2L, "some controller", 0, 0),      // same name, different case
                           pad(3L, "Another Controller", 0, 0));
        assertEquals(2, ControllerManager.mergeBackends(sdl, glfw).size(),
                "name dedup still applies when no VID/PID is available");
    }

    @Test
    void anEmptyGlfwListLeavesTheSdlListUntouched() {
        var sdl = List.of(pad(1L, "8Bitdo Ultimate 2", 0x2DC8, 0x6012));
        assertEquals(1, ControllerManager.mergeBackends(sdl, List.of()).size());
    }
}
