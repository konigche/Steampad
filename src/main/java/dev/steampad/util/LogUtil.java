package dev.steampad.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LogUtil {

    public static final Logger LOG = LoggerFactory.getLogger("SteamPad");

    private LogUtil() {}

    public static void info(String msg, Object... args) {
        LOG.info("[SteamPad] " + msg, args);
    }

    public static void warn(String msg, Object... args) {
        LOG.warn("[SteamPad] " + msg, args);
    }

    public static void error(String msg, Object... args) {
        LOG.error("[SteamPad] " + msg, args);
    }

    public static void error(String msg, Throwable t) {
        LOG.error("[SteamPad] " + msg, t);
    }

    /**
     * Diagnostic logging that must actually be readable in a real player's {@code latest.log}.
     *
     * <p>This used to call {@code LOG.debug(...)} — but Minecraft's shipped log4j2.xml sets the root
     * logger to INFO in a normal (non-dev) client run, so every DEBUG line was silently dropped. Every
     * past "check the log for this line" diagnostic instruction (haptics, widget scanning, Steam Input
     * bring-up) that relied on {@code debug()} therefore never actually produced evidence for the
     * user — this was discovered while investigating why the slime-vibration diagnostic added 3
     * sessions ago never yielded a usable log. Routed through INFO so future diagnostics are
     * guaranteed visible without requiring a custom log4j config.
     */
    public static void debug(String msg, Object... args) {
        LOG.info("[SteamPad] " + msg, args);
    }
}
