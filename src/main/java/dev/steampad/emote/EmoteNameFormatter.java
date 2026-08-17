package dev.steampad.emote;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shortens a community-authored emote's display name for space-constrained UI (Wheel chips, Library
 * grid cells, Wheel-editor rows) — e.g. {@code "Macarena (dance) - MineEmote"} becomes
 * {@code "Macarena"} (D111, the user's own example). Two independent, conservative passes:
 * <ol>
 *   <li>Strip a trailing {@code " - <attribution>"} suffix (hyphen or en/em dash, spaced on both
 *       sides — a bare hyphen with no surrounding spaces, like {@code "T-Pose"}, is left alone since
 *       it's clearly part of the core name, not a separator).</li>
 *   <li>Strip a trailing parenthetical ONLY when its content is a known generic category/type word
 *       (dance, movement, loop, sit, …) — a conservative allow-list so a meaningfully disambiguating
 *       parenthetical like {@code "Wave (Left)"} survives untouched.</li>
 * </ol>
 *
 * <p>Deliberately NOT a translation system: some community files carry names already in Russian or
 * another non-Latin script, with no attribution/category wrapper to strip at all — this mod has no
 * live translation service to call, and adding one would be a new network dependency at odds with
 * this project's "zero external dependency" standing (D080). Those names pass through unchanged
 * except for the same cruft-stripping any other name gets. The full original name is always still
 * available separately (search, tooltips) via {@link EmoteData#name} — this class never mutates it,
 * only formats a copy for display.
 */
public final class EmoteNameFormatter {

    private EmoteNameFormatter() {}

    // Trailing " - attribution" / " – attribution" / " — attribution" — hyphen or en/em dash, spaced
    // on BOTH sides so hyphenated core names ("T-Pose") never match. Greedy .* means the FIRST (left-
    // most) separator found wins when a name has more than one — a known, documented tradeoff, not a
    // guaranteed-perfect parse (no general parser could be, for arbitrary community-authored names).
    private static final Pattern ATTRIBUTION_SUFFIX = Pattern.compile("\\s+[-\\u2013\\u2014]\\s+\\S.*$");

    private static final Pattern TRAILING_PAREN = Pattern.compile("\\s*\\(([^()]*)\\)\\s*$");
    private static final Set<String> GENERIC_TAGS = Set.of(
            "dance", "dancing", "movement", "move", "emote", "animation", "anim",
            "loop", "pose", "sit", "sitting", "idle", "taunt");

    /** Best-effort short name for UI. Falls back to the original (trimmed) name whenever stripping
     *  would leave nothing usable. Null-safe (returns "" for null). */
    public static String shorten(String rawName) {
        if (rawName == null) return "";
        String name = rawName.trim();
        if (name.isEmpty()) return name;

        String result = stripGenericParen(stripAttribution(name)).trim();
        return result.isEmpty() ? name : result;
    }

    private static String stripAttribution(String name) {
        Matcher m = ATTRIBUTION_SUFFIX.matcher(name);
        if (!m.find()) return name;
        String head = name.substring(0, m.start()).trim();
        return head.isEmpty() ? name : head;
    }

    private static String stripGenericParen(String name) {
        Matcher m = TRAILING_PAREN.matcher(name);
        if (!m.find()) return name;
        String tag = m.group(1).trim().toLowerCase(Locale.ROOT);
        if (!GENERIC_TAGS.contains(tag)) return name;
        String head = name.substring(0, m.start()).trim();
        return head.isEmpty() ? name : head;
    }
}
