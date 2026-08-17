package dev.steampad.platform;

import com.sun.jna.Library;
import com.sun.jna.Native;
import dev.steampad.util.LogUtil;

import java.util.Locale;

/**
 * Reads and writes the <b>native</b> process environment (what C's {@code getenv} sees), which Java's
 * own {@link System#getenv} cannot change — it hands back an immutable snapshot taken at JVM start.
 *
 * <p><b>Why this exists.</b> The Steamworks SDK resolves "which app am I?" inside native code, and it
 * consults the {@code SteamAppId} / {@code SteamGameId} environment variables <em>before</em> falling
 * back to {@code steam_appid.txt}. When Minecraft is launched from a non-Steam shortcut, Steam sets
 * those variables to the shortcut's pseudo-AppID — so the file is never even read. Clearing them at
 * runtime is the only way to make the SDK fall through to the file, which is what
 * {@link dev.steampad.steam.SteamBootstrap}'s second attach attempt needs.
 *
 * <p>Java's cached copy is deliberately left alone: the mod's own detection
 * ({@link SteamLaunchDetector}) keeps reporting the real launch AppID for diagnostics and for VDF
 * filenames, while only the native view — the one the SDK reads — changes.
 *
 * <p>Entirely best-effort. On any platform or JNA failure every method returns {@code false} and the
 * caller simply skips that attempt; nothing here can break startup.
 */
public final class NativeEnv {

    private interface LibC extends Library {
        int unsetenv(String name);
        int setenv(String name, String value, int overwrite);
    }

    private static LibC libc;
    private static boolean loadAttempted = false;

    private NativeEnv() {}

    /** True when the native environment can actually be modified on this platform. */
    public static boolean isSupported() {
        return libc() != null;
    }

    private static synchronized LibC libc() {
        if (loadAttempted) return libc;
        loadAttempted = true;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        // Windows' CRT has no unsetenv with these semantics and Steam does not use the shortcut
        // pseudo-AppID scheme there, so the whole mechanism is Unix-only by design.
        if (os.contains("windows")) return null;
        try {
            libc = Native.load("c", LibC.class);
        } catch (Throwable t) {
            LogUtil.warn("[SteamPad] Could not bind libc for environment edits ({}) — the Steam attach "
                    + "fallback that clears SteamAppId is unavailable this session.", t.getMessage());
            libc = null;
        }
        return libc;
    }

    /** Removes a variable from the native environment. Returns true when the call succeeded. */
    public static boolean unset(String name) {
        LibC c = libc();
        if (c == null) return false;
        try {
            return c.unsetenv(name) == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Restores a variable in the native environment. A null/blank value unsets it instead. */
    public static boolean set(String name, String value) {
        LibC c = libc();
        if (c == null) return false;
        try {
            if (value == null || value.isEmpty()) return c.unsetenv(name) == 0;
            return c.setenv(name, value, 1) == 0;
        } catch (Throwable t) {
            return false;
        }
    }
}
