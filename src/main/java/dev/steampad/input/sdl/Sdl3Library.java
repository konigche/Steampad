package dev.steampad.input.sdl;

import com.sun.jna.Native;
import dev.steampad.platform.LinuxRuntimeInspector;
import dev.steampad.util.LogUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Finds and loads libSDL3 for {@link Sdl3GamepadProvider}, trying every place it realistically lives
 * instead of only the OS default search path.
 *
 * <p><b>Why this exists.</b> The mod does not ship SDL3 — it uses whatever the system has. A bare
 * {@code Native.load("SDL3")} only searches the OS library path, which is fine on Linux (SteamOS,
 * Bazzite and most desktops ship libSDL3 system-wide) but fails on <b>Windows</b>, where nothing puts
 * an SDL3.dll on PATH. That's the whole reason a Windows session silently falls back to GLFW and
 * loses paddles, rumble and SDL's device database — a platform gap, not a version regression: it
 * would fail identically on any Minecraft version.
 *
 * <p>The fix is that Steam itself ships a full <b>x64</b> {@code SDL3.dll} inside its install folder
 * (verified on a real install: {@code C:\Program Files (x86)\Steam\SDL3.dll} — the "(x86)" is just
 * where the 32-bit Steam <em>client</em> installs; the DLL beside it is 64-bit and loads fine into
 * Minecraft's JVM). Since this mod exists for Steam users, that copy is nearly always present.
 *
 * <p>Two Linux gaps are covered too: JNA maps the plain name {@code "SDL3"} to {@code libSDL3.so},
 * which is the <em>development</em> symlink — a runtime-only install may have just
 * {@code libSDL3.so.0}, so that soname is tried explicitly; and Flatpak/Snap layouts get their own
 * candidate paths.
 *
 * <p>Entirely best-effort: if nothing loads, the caller disables SDL3 and GLFW keeps working.
 */
public final class Sdl3Library {

    private Sdl3Library() {}

    /** How the library was found, for the debug dump — null until {@link #load} succeeds. */
    private static volatile String loadedFrom = null;

    /** Everything that was tried and failed, so the log explains itself in one pass. */
    private static final List<String> attempts = new ArrayList<>();

    /** Human-readable origin of the loaded library ("system path", or an absolute path). */
    public static String loadedFrom() {
        return loadedFrom != null ? loadedFrom : "not loaded";
    }

    /** One line per failed attempt (for the debug dump when SDL3 is unavailable). */
    public static List<String> attempts() {
        return List.copyOf(attempts);
    }

    /**
     * Loads libSDL3, trying (in order): an explicit override, the OS default search path, then every
     * known concrete location. Returns null when nothing worked.
     *
     * @param overridePath user-configured absolute path to the library, or null/blank for none
     */
    public static Sdl3Native load(String overridePath) {
        attempts.clear();

        // 1. Explicit user override always wins — the escape hatch for a layout we don't predict.
        if (overridePath != null && !overridePath.isBlank()) {
            Sdl3Native lib = tryLoad(overridePath, "config override");
            if (lib != null) return lib;
        }

        // 2. OS default search path. This is what always worked on Linux, so it stays first among the
        //    automatic attempts — no behaviour change for setups that were already fine.
        Sdl3Native lib = tryLoad("SDL3", "system search path");
        if (lib != null) return lib;

        // 3. Concrete candidates, most likely first.
        for (Path candidate : candidates()) {
            lib = tryLoad(candidate.toString(), "known location");
            if (lib != null) return lib;
        }
        return null;
    }

    private static Sdl3Native tryLoad(String nameOrPath, String how) {
        try {
            Sdl3Native lib = Native.load(nameOrPath, Sdl3Native.class);
            loadedFrom = nameOrPath + " (" + how + ")";
            LogUtil.info("[SteamPad] libSDL3 loaded from {} [{}].", nameOrPath, how);
            return lib;
        } catch (Throwable t) {
            // UnsatisfiedLinkError for "not there"/"wrong architecture", anything else for a broken
            // file. Either way this candidate is out; record it and move on.
            attempts.add(nameOrPath + " -> " + t.getClass().getSimpleName()
                    + (t.getMessage() != null && t.getMessage().length() < 160 ? ": " + t.getMessage() : ""));
            return null;
        }
    }

    /** Concrete files to try, in priority order, deduplicated and filtered to ones that exist. */
    private static List<Path> candidates() {
        Set<Path> out = new LinkedHashSet<>();
        String home = System.getProperty("user.home", "");
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
        boolean mac = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

        String libName = windows ? "SDL3.dll" : (mac ? "libSDL3.dylib" : "libSDL3.so.0");

        // Steam's own copy — the highest-value candidate on Windows, and harmless elsewhere.
        for (Path steam : steamInstallCandidates(home, windows, mac)) {
            out.add(steam.resolve(libName));
            // Linux Steam keeps its bundled libs in an arch subfolder rather than the root.
            if (!windows && !mac) {
                out.add(steam.resolve("ubuntu12_64").resolve(libName));
                out.add(steam.resolve("linux64").resolve(libName));
            }
        }

        if (!windows && !mac) {
            // Runtime-only Linux installs: the plain "SDL3" name maps to libSDL3.so (a devel symlink)
            // which may be absent even though the runtime library is installed.
            out.add(Path.of("/usr/lib64").resolve(libName));
            out.add(Path.of("/usr/lib").resolve(libName));
            out.add(Path.of("/usr/lib/x86_64-linux-gnu").resolve(libName));
            out.add(Path.of("/usr/local/lib").resolve(libName));
            out.add(Path.of("/app/lib").resolve(libName));          // Flatpak runtime
            out.add(Path.of("/var/lib/snapd/lib").resolve(libName)); // Snap
        }

        // Beside the game itself — the "drop the DLL next to the mod" escape hatch, documented in the
        // failure log so a user without Steam has something to do.
        Path gameDir = Path.of(System.getProperty("user.dir", "."));
        out.add(gameDir.resolve(libName));
        out.add(gameDir.resolve("natives").resolve(libName));

        List<Path> existing = new ArrayList<>();
        for (Path p : out) {
            try {
                if (Files.isRegularFile(p)) existing.add(p);
            } catch (Throwable ignored) {
                // An unreadable path is simply not a candidate.
            }
        }
        return existing;
    }

    /** Steam install roots, per OS, including a registry lookup on Windows for non-default drives. */
    private static List<Path> steamInstallCandidates(String home, boolean windows, boolean mac) {
        List<Path> out = new ArrayList<>();

        // Set by Proton/Steam's own compat tooling to the real install path — the most direct signal.
        String compat = System.getenv("STEAM_COMPAT_CLIENT_INSTALL_PATH");
        if (compat != null && !compat.isBlank()) out.add(Path.of(compat));

        if (windows) {
            Path fromRegistry = steamPathFromWindowsRegistry();
            if (fromRegistry != null) out.add(fromRegistry);
            out.add(Path.of("C:\\Program Files (x86)\\Steam"));
            out.add(Path.of("C:\\Program Files\\Steam"));
        } else if (mac) {
            out.add(Path.of(home, "Library", "Application Support", "Steam"));
        } else if (LinuxRuntimeInspector.isLinux()) {
            out.add(Path.of(home, ".local", "share", "Steam"));
            out.add(Path.of(home, ".steam", "steam"));
            out.add(Path.of(home, ".steam", "root"));
            out.add(Path.of(home, ".var", "app", "com.valvesoftware.Steam", ".local", "share", "Steam"));
        }
        return out;
    }

    /**
     * Steam's install path from {@code HKCU\Software\Valve\Steam\SteamPath}, for installs on a
     * non-default drive. Uses {@code reg.exe} rather than a registry API because Minecraft bundles
     * plain JNA, not {@code jna-platform} (which is where {@code Advapi32Util} lives) — depending on
     * it would risk a NoClassDefFoundError at runtime for a nice-to-have lookup.
     */
    private static Path steamPathFromWindowsRegistry() {
        try {
            Process p = new ProcessBuilder("reg", "query",
                    "HKCU\\Software\\Valve\\Steam", "/v", "SteamPath")
                    .redirectErrorStream(true)
                    .start();
            String out;
            try (var in = p.getInputStream()) {
                out = new String(in.readAllBytes());
            }
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            String value = parseRegQuerySteamPath(out);
            if (value != null) return Path.of(value);
        } catch (Throwable ignored) {
            // reg.exe missing, blocked, or an odd output format — the hardcoded paths still cover the
            // default install, so this is purely a bonus lookup.
        }
        return null;
    }

    /**
     * Pulls the path out of {@code reg query} output. Split out as pure logic so it can be tested
     * without touching the registry — the real output is easy to get subtly wrong: the value is
     * whitespace-separated (not tab-delimited as often assumed), and Steam stores it with FORWARD
     * slashes and lowercase drive letter (verified: {@code c:/program files (x86)/steam}), which is
     * valid for {@code Path.of} on Windows but is normalised here anyway.
     *
     * @return the path string with separators normalised, or null when the output has no usable value
     */
    static String parseRegQuerySteamPath(String regOutput) {
        if (regOutput == null) return null;
        for (String line : regOutput.split("\\r?\\n")) {
            int idx = line.indexOf("REG_SZ");
            if (idx < 0) continue;
            // Require the key name on the same line, before the type. The query asks for /v SteamPath
            // so nothing else should be present, but keying off "any REG_SZ line" would silently
            // return a neighbouring value's data (e.g. the UI language) if that ever stopped holding.
            String keyPart = line.substring(0, idx);
            if (!keyPart.toLowerCase(Locale.ROOT).contains("steampath")) continue;
            String value = line.substring(idx + "REG_SZ".length()).trim();
            if (!value.isEmpty()) return value.replace('/', '\\');
        }
        return null;
    }
}
