package dev.steampad.emote;

import dev.steampad.util.LogUtil;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The emote library: every playable emote known to this client, indexed by id (filename stem,
 * lowercase). Sources, later ones overriding earlier ones on id collision:
 * <ol>
 *   <li><b>Bundled pack</b> — curated CC0 emotes shipped in the jar (see
 *       {@code assets/steampad/emotes/} and its LICENSE note; enumerated via an index file because
 *       classpath directories can't be listed reliably from inside a JiJ'd jar).</li>
 *   <li><b>Shared community folder</b> — {@code .minecraft/emotes}: the SAME folder the existing
 *       emote ecosystem uses, so everything the user already downloaded for Emotecraft appears in
 *       SteamPad automatically, no copying (the compatibility requirement of FASE 63).</li>
 *   <li><b>SteamPad's own folder</b> — {@code config/steampad/emotes}: for users who want SteamPad-
 *       only emotes, created on first load.</li>
 * </ol>
 * Load is lazy (first access) — emote files never cost anything at game startup — and
 * {@link #reload()} rescans on demand (the library screen's refresh).
 */
public final class EmoteLibrary {

    /** One library entry (id + parsed data; source shown in the UI so users know where it lives). */
    public record Entry(String id, EmoteData data, String source) {}

    private static final String BUNDLED_DIR = "/assets/steampad/emotes/";
    private static final String BUNDLED_INDEX = BUNDLED_DIR + "index.txt";

    private static final Map<String, Entry> emotes = new TreeMap<>();
    private static boolean loaded = false;

    private EmoteLibrary() {}

    /** All emotes, sorted by id. Triggers the lazy first scan. */
    public static synchronized List<Entry> all() {
        ensureLoaded();
        return new ArrayList<>(emotes.values());
    }

    /** Case-insensitive name/author/id filter for the library screen's search box. */
    public static synchronized List<Entry> search(String query) {
        ensureLoaded();
        if (query == null || query.isBlank()) return all();
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Entry> out = new ArrayList<>();
        for (Entry e : emotes.values()) {
            if (e.id().contains(q)
                    || e.data().name.toLowerCase(Locale.ROOT).contains(q)
                    || e.data().author.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(e);
            }
        }
        return out;
    }

    /** The emote behind an id, or null (unknown / not installed on this client). */
    public static synchronized EmoteData byId(String id) {
        ensureLoaded();
        if (id == null) return null;
        Entry e = emotes.get(id.toLowerCase(Locale.ROOT));
        return e == null ? null : e.data();
    }

    /** Forgets everything and rescans all three sources (library screen's refresh button). */
    public static synchronized void reload() {
        loaded = false;
        emotes.clear();
        ensureLoaded();
    }

    /** SteamPad's own emotes folder (created on demand) — shown in the library screen's footer. */
    public static Path steamPadEmotesDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("steampad").resolve("emotes");
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        int bundled = loadBundled();
        int shared = loadFolder(FabricLoader.getInstance().getGameDir().resolve("emotes"), "emotes/");
        Path own = steamPadEmotesDir();
        try {
            Files.createDirectories(own);
        } catch (Throwable ignored) {
        }
        int mine = loadFolder(own, "steampad/");
        LogUtil.info("[SteamPad] Emote library loaded: {} bundled + {} from .minecraft/emotes + {} own "
                + "({} total after overrides).", bundled, shared, mine, emotes.size());
    }

    private static int loadBundled() {
        int count = 0;
        try (InputStream idx = EmoteLibrary.class.getResourceAsStream(BUNDLED_INDEX)) {
            if (idx == null) return 0;
            List<String> names = new BufferedReader(new InputStreamReader(idx, StandardCharsets.UTF_8))
                    .lines().map(String::trim).filter(s -> !s.isEmpty() && !s.startsWith("#")).toList();
            for (String file : names) {
                try (InputStream in = EmoteLibrary.class.getResourceAsStream(BUNDLED_DIR + file)) {
                    if (in == null) continue;
                    String id = stem(file);
                    EmoteData data = EmoteJsonParser.parse(id,
                            new InputStreamReader(in, StandardCharsets.UTF_8));
                    if (data != null) {
                        data.iconPng = readBundledIcon(id);
                        emotes.put(id, new Entry(id, data, "bundled"));
                        count++;
                    }
                }
            }
        } catch (Throwable t) {
            LogUtil.warn("[SteamPad] Bundled emote pack failed to load: {}", t.getMessage());
        }
        return count;
    }

    /** Sibling {@code <id>.png} next to a bundled emote, if the pack includes one — same convention
     *  documented for loose files (see {@link #readSiblingIcon}), just from the classpath instead. */
    private static byte[] readBundledIcon(String id) {
        try (InputStream in = EmoteLibrary.class.getResourceAsStream(BUNDLED_DIR + id + ".png")) {
            return in == null ? null : in.readAllBytes();
        } catch (Throwable t) {
            return null;
        }
    }

    private static final java.util.Set<String> LOADABLE_EXTENSIONS = java.util.Set.of("json", "emotecraft");

    private static int loadFolder(Path dir, String sourceTag) {
        if (!Files.isDirectory(dir)) return 0;
        int count = 0;
        List<Path> files = new ArrayList<>();
        // Manual extension check instead of a DirectoryStream glob — no dependency on this JVM's glob
        // dialect actually supporting "{a,b}" brace groups (it should, per the java.nio.file.FileSystem
        // spec, but a plain case-insensitive extension compare removes ALL doubt).
        int totalFiles = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                if (Files.isDirectory(p)) continue;
                totalFiles++;
                String name = p.getFileName().toString();
                int dot = name.lastIndexOf('.');
                String ext = dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
                if (LOADABLE_EXTENSIONS.contains(ext)) files.add(p);
            }
        } catch (Throwable t) {
            LogUtil.warn("[SteamPad] Could not scan emote folder {}: {}", dir, t.getMessage());
            return 0;
        }
        LogUtil.debug("[SteamPad] Emote folder {}: {} files total, {} with a loadable extension.",
                dir, totalFiles, files.size());
        files.sort(Comparator.comparing(p -> p.getFileName().toString()));
        for (Path p : files) {
            String name = p.getFileName().toString();
            String id = stem(name);
            boolean binary = name.toLowerCase(Locale.ROOT).endsWith(".emotecraft");
            try {
                EmoteData data;
                byte[] icon;
                if (binary) {
                    // .emotecraft is Emotecraft's own NATIVE BINARY container. As of v0.59.0 the
                    // parser implements the full verified spec — every binary version (v1–v4),
                    // metadata, loop windows, easings and the embedded icon — validated against all
                    // 21 of the user's real community files (21/21, see DECISIONS D099). Icon and
                    // animation stay independent extractions purely as defense-in-depth for
                    // corrupted files.
                    byte[] bytes = Files.readAllBytes(p);
                    icon = EmoteCraftBinaryParser.extractIcon(bytes);
                    data = EmoteCraftBinaryParser.parse(id, bytes);
                    if (data == null) {
                        LogUtil.warn("[SteamPad] Emote file {} did not decode as a .emotecraft binary "
                                + "(icon {} extracted) — skipped. If this is a real Emotecraft file, "
                                + "please report it; every known container version decodes as of v0.59.0.",
                                p, icon != null ? "was" : "was NOT");
                        continue;
                    }
                } else {
                    try (BufferedReader reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                        data = EmoteJsonParser.parse(id, reader);
                    }
                    icon = data != null ? readSiblingIcon(p) : null;
                }
                if (data != null) {
                    data.iconPng = icon;
                    emotes.put(id, new Entry(id, data, sourceTag));
                    count++;
                } else {
                    LogUtil.warn("[SteamPad] Emote file {} did not parse into valid emote data (see the "
                            + "warning above, if any) — skipped.", p);
                }
            } catch (Throwable t) {
                LogUtil.warn("[SteamPad] Could not read emote file {}: {}", p, t.getMessage());
            }
        }
        return count;
    }

    /** Sibling {@code <name>.png} next to a {@code .json} emote — the documented icon convention
     *  (kosmx.gitbook.io: "create an icon for the emote by putting a square image with the emote's
     *  filename into the emotes folder"). */
    private static byte[] readSiblingIcon(Path jsonFile) {
        String name = jsonFile.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        Path png = jsonFile.resolveSibling(stem + ".png");
        try {
            return Files.isRegularFile(png) ? Files.readAllBytes(png) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String stem(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot > 0 ? fileName.substring(0, dot) : fileName).toLowerCase(Locale.ROOT);
    }
}
