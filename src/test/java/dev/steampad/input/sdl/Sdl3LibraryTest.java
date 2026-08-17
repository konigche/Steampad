package dev.steampad.input.sdl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Locks in the parsing of {@code reg query} output used to find Steam (and with it Steam's bundled
 * SDL3) on Windows installs that aren't on the default drive.
 *
 * <p>Worth testing despite being three lines: it's string parsing of another program's output, it
 * fails silently (a bad parse just means "Steam not found" → GLFW fallback → no rumble/paddles, with
 * nothing obviously broken in the log), and the real-world format has two traps that a hand-written
 * parser easily gets wrong — the value is separated by runs of spaces rather than tabs, and Steam
 * writes the path with forward slashes and a lowercase drive letter.
 */
class Sdl3LibraryTest {

    @Test
    void parsesRealRegQueryOutput() {
        // Verbatim from a real machine (reg query HKCU\Software\Valve\Steam /v SteamPath).
        String out = "\r\nHKEY_CURRENT_USER\\Software\\Valve\\Steam\r\n"
                + "    SteamPath    REG_SZ    c:/program files (x86)/steam\r\n\r\n";
        assertEquals("c:\\program files (x86)\\steam", Sdl3Library.parseRegQuerySteamPath(out));
    }

    @Test
    void normalisesForwardSlashesButKeepsSpacesInsidePath() {
        String out = "    SteamPath    REG_SZ    D:/Games/Steam Library/Steam\r\n";
        assertEquals("D:\\Games\\Steam Library\\Steam", Sdl3Library.parseRegQuerySteamPath(out));
    }

    @Test
    void acceptsBackslashPathsUnchanged() {
        String out = "    SteamPath    REG_SZ    E:\\Steam\r\n";
        assertEquals("E:\\Steam", Sdl3Library.parseRegQuerySteamPath(out));
    }

    @Test
    void returnsNullWhenValueIsAbsentOrEmpty() {
        assertNull(Sdl3Library.parseRegQuerySteamPath(null));
        assertNull(Sdl3Library.parseRegQuerySteamPath(""));
        // The "key not found" error reg.exe prints when Steam was never installed.
        assertNull(Sdl3Library.parseRegQuerySteamPath(
                "ERROR: The system was unable to find the specified registry key or value."));
        // Present but blank — must not produce an empty path that resolves to the process CWD.
        assertNull(Sdl3Library.parseRegQuerySteamPath("    SteamPath    REG_SZ    \r\n"));
    }

    @Test
    void picksSteamPathAndIgnoresOtherValuesInTheSameOutput() {
        // The query uses /v SteamPath so this shouldn't happen — but keying off "any REG_SZ line"
        // would return "spanish" as a filesystem path here, which is exactly the silent-garbage case
        // worth pinning down.
        String out = "HKEY_CURRENT_USER\\Software\\Valve\\Steam\r\n"
                + "    Language    REG_SZ    spanish\r\n"
                + "    SteamPath    REG_SZ    c:/steam\r\n";
        assertEquals("c:\\steam", Sdl3Library.parseRegQuerySteamPath(out));
    }

    @Test
    void returnsNullWhenOnlyUnrelatedValuesArePresent() {
        assertNull(Sdl3Library.parseRegQuerySteamPath("    Language    REG_SZ    spanish\r\n"));
    }
}
