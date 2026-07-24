package dev.steampad.steam;

import com.codedisaster.steamworks.SteamAPI;
import dev.steampad.util.LogUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles loading of Steamworks4j native libraries.
 *
 * MUST call SteamAPI.loadLibraries() explicitly before SteamAPI.init() —
 * otherwise Steamworks4j throws "Native libraries not loaded" even when
 * the natives are bundled inside the mod JAR.
 *
 * <p><b>How loading works (B076 — verified against the steamworks4j 1.9.0 source):</b> the natives
 * ({@code libsteam_api.so} + {@code libsteamworks4j.so} / {@code steam_api64.dll} +
 * {@code steamworks4j64.dll}) ship INSIDE the bundled steamworks4j jar and are extracted at runtime,
 * then {@code System.load}ed. The extraction target is discovered per call with an internal cascade:
 * the {@code com.codedisaster.steamworks.SharedLibraryExtractPath} system property (if writable) →
 * {@code java.io.tmpdir} → a NIO temp dir → {@code user.home} — so a bad first choice degrades
 * instead of failing.
 *
 * <p>{@code customNativesPath} therefore means "extract HERE" (a writable directory, for sandboxed
 * launchers whose /tmp is blocked or noexec) — it is wired into that system property, which must be
 * set BEFORE the first steamworks class initializes (it is read into a {@code static final} at
 * class-init). It does NOT mean "load pre-existing libs from here": the old code passed it to
 * {@code SteamAPI.loadLibraries(path)}, whose non-null-path branch REQUIRES both .so files to already
 * exist at that path — no extraction, no fallback. That is exactly how a user following our own log
 * hint ended up pointing it at Steam's {@code linux64/} dir (which contains {@code steamclient.so},
 * never {@code libsteam_api.so} — that lib is a per-game redistributable, not part of the Steam
 * client) and losing Steam natives entirely: {@code FileNotFoundException}, Steam API dead all
 * session. Always using the bundled natives also guarantees the .so matches the steamworks4j JNI
 * wrapper version — loading a foreign copy risks ABI drift.
 */
public final class SteamNativeLoader {

    /** Read into a static final by SteamSharedLibraryLoader at class-init — set it early, once. */
    private static final String EXTRACT_PATH_PROP = "com.codedisaster.steamworks.SharedLibraryExtractPath";

    private static boolean loaded = false;
    private static Path customPath = null;

    private SteamNativeLoader() {}

    public static void setCustomPath(Path path) {
        customPath = path;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Extracts and loads the Steamworks4j native libraries bundled in the mod JAR. Must be called
     * before SteamBootstrap.init(). Safe to call again after a failure (e.g. the settings screen's
     * "Retry Steam Init").
     */
    public static boolean load() {
        if (loaded) return true;

        // Route the custom path into steamworks4j's own extraction cascade. Must happen BEFORE any
        // SteamAPI/SteamSharedLibraryLoader class touch (static-final read at class-init). If the
        // dir is missing we create it; if it is unwritable, the loader's cascade falls through to
        // tmpdir/home on its own — a bad custom path can no longer kill natives loading (B076).
        if (customPath != null) {
            try {
                Files.createDirectories(customPath);
            } catch (Throwable t) {
                LogUtil.warn("[SteamPad] customNativesPath not creatable ({}): {} — the loader will "
                        + "fall back to the default temp locations.", customPath, t.getMessage());
            }
            System.setProperty(EXTRACT_PATH_PROP, customPath.toString());
            LogUtil.info("[SteamPad] Steam natives extraction target (custom): {}", customPath);
        }

        if (isFlatpak()) {
            LogUtil.warn("[SteamPad] Flatpak container detected.");
            LogUtil.warn("[SteamPad] Steam natives extraction to /tmp may fail in sandboxed launchers.");
            if (customPath == null) {
                LogUtil.warn("[SteamPad] Set 'customNativesPath' in SteamPad config to a writable directory.");
            }
        }

        try {
            LogUtil.info("[SteamPad] Loading bundled Steam natives (extract + System.load)...");
            SteamAPI.loadLibraries();   // null-path variant: extract from the bundled jar, cascade target
            loaded = true;
            LogUtil.info("[SteamPad] Steam natives loaded OK.");
            return true;
        } catch (Throwable t) {
            LogUtil.error("[SteamPad] SteamAPI.loadLibraries() FAILED: {}", t.getMessage());
            logNativesHint();
            return false;
        }
    }

    private static boolean isFlatpak() {
        return new File("/.flatpak-info").exists()
            || System.getenv("FLATPAK_ID") != null
            || "flatpak".equals(System.getenv("container"));
    }

    private static void logNativesHint() {
        LogUtil.error("[SteamPad] Fix options:");
        LogUtil.error("[SteamPad]   1. Set 'customNativesPath' in .minecraft/config/steampad/global.json to a");
        LogUtil.error("[SteamPad]      WRITABLE + EXECUTABLE directory (e.g. /home/<user>/.local/share/steampad/natives/).");
        LogUtil.error("[SteamPad]      The mod EXTRACTS its own bundled natives there — do NOT point it at Steam's");
        LogUtil.error("[SteamPad]      install folders; they never contain libsteam_api.so.");
        LogUtil.error("[SteamPad]   2. If in Prism Launcher Flatpak: add --filesystem=/tmp to Flatpak override, OR");
        LogUtil.error("[SteamPad]      set JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/var/home/<user>/.local/share/steampad/tmp");
    }
}
