package dev.steampad.steam;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the two invariants that broke Steam Input in practice (D117), both of which were invisible
 * at build time before this test existed:
 *
 * <ol>
 *   <li><b>The manifest and the registry must agree.</b> Four actions
 *       ({@code steampad_radial_nav_*}) were being registered in {@link SteamActionRegistry} and read
 *       every tick by {@code ControllerState}, but were never declared in the manifest — so their
 *       handles were permanently 0 and the reads silently returned nothing. Nothing failed; the
 *       feature was simply dead. This test cross-checks the two files against each other.</li>
 *   <li><b>The generated default configuration must be structurally valid.</b> A malformed
 *       {@code controller_mappings} is rejected wholesale by Steam, which degrades to exactly the
 *       symptom being fixed here (an empty layout with nothing bound), again with no error anywhere
 *       the mod can see.</li>
 * </ol>
 */
class SteamInputManifestTest {

    private static final Path MANIFEST = Path.of(
            "src/main/resources/assets/steampad/steam_input/game_actions_template.vdf");
    private static final Path REGISTRY = Path.of(
            "src/main/java/dev/steampad/steam/SteamActionRegistry.java");

    private static String read(Path p) throws Exception {
        assertTrue(Files.exists(p), "missing file: " + p.toAbsolutePath());
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    /** Every {@code steampad_*} action name the manifest declares anywhere. */
    private static Set<String> manifestActions(String vdf) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("\"(steampad_[a-z0-9_]+)\"").matcher(vdf);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** Every action name the registry asks Steam to resolve a handle for. */
    private static Set<String> registeredActions(String java) {
        Set<String> out = new LinkedHashSet<>();
        // The trailing \) matters: it excludes the concatenated slot form
        // (getDigitalActionHandle("steampad_slot_" + (i + 1))), which would otherwise be captured as
        // the partial name "steampad_slot_" and reported as missing. Slots are expanded below.
        Matcher m = Pattern.compile("get(?:Digital|Analog)ActionHandle\\(\"(steampad_[a-z0-9_]+)\"\\)")
                .matcher(java);
        while (m.find()) out.add(m.group(1));
        // The numbered slots are registered in a loop, not as literals.
        if (java.contains("\"steampad_slot_\" + (i + 1)")) {
            for (int i = 1; i <= SteamActionRegistry.SLOT_COUNT; i++) out.add("steampad_slot_" + i);
        }
        return out;
    }

    @Test
    void everyRegisteredActionIsDeclaredInTheManifest() throws Exception {
        Set<String> declared = manifestActions(read(MANIFEST));
        Set<String> registered = registeredActions(read(REGISTRY));

        assertFalse(registered.isEmpty(), "parsed no actions out of the registry — test is stale");

        Set<String> missing = new LinkedHashSet<>(registered);
        missing.removeAll(declared);
        assertTrue(missing.isEmpty(),
                "These actions are registered in SteamActionRegistry but NOT declared in the manifest, "
                + "so Steam resolves handle 0 for them and they can never fire: " + missing);
    }

    @Test
    void everyDeclaredActionIsActuallyRegistered() throws Exception {
        Set<String> declared = manifestActions(read(MANIFEST));
        Set<String> registered = registeredActions(read(REGISTRY));

        Set<String> unused = new LinkedHashSet<>(declared);
        unused.removeAll(registered);
        // Action SET names also match the steampad_ prefix filter in neither direction; the sets are
        // PascalCase ("SteamPad_Gameplay") so they don't appear here. Anything left really is an
        // action the configurator will offer the user that the mod would then ignore.
        assertTrue(unused.isEmpty(),
                "These actions are declared in the manifest (so Steam shows them in the configurator) "
                + "but the mod never reads them — binding one would appear to do nothing: " + unused);
    }

    @Test
    void manifestDeclaresAConfigurationForEveryGeneratedControllerType() throws Exception {
        String vdf = read(MANIFEST);
        assertTrue(vdf.contains("\"configurations\""),
                "manifest has no configurations block — Steam would generate an EMPTY layout with "
                + "nothing bound, which is the exact bug D117 fixes");
        for (String type : SteamDefaultConfigGenerator.CONTROLLER_TYPES) {
            assertTrue(vdf.contains("\"" + type + "\""),
                    "manifest does not reference controller type " + type);
            assertTrue(vdf.contains(SteamDefaultConfigGenerator.fileNameFor(type)),
                    "manifest does not point at the config file generated for " + type);
        }
    }

    @Test
    void generatedConfigIsStructurallyValid() {
        for (String type : SteamDefaultConfigGenerator.CONTROLLER_TYPES) {
            String cfg = SteamDefaultConfigGenerator.generate(type);

            assertTrue(cfg.startsWith("\"controller_mappings\""), type + ": wrong root key");
            assertTrue(cfg.contains("\"controller_type\"\t\t\"" + type + "\""),
                    type + ": controller_type not substituted");

            int open = cfg.length() - cfg.replace("{", "").length();
            int close = cfg.length() - cfg.replace("}", "").length();
            assertEquals(open, close, type + ": unbalanced braces (" + open + " open, " + close + " close)");

            // One preset per action set, each naming a set the manifest actually declares.
            for (String set : new String[]{"SteamPad_Gameplay", "SteamPad_Menu",
                                           "SteamPad_Inventory", "SteamPad_Mounted"}) {
                assertTrue(cfg.contains("\"name\"\t\t\"" + set + "\""),
                        type + ": no preset for action set " + set);
            }
        }
    }

    @Test
    void generatedConfigBindsBothSticksViaGameactions() {
        // A stick CANNOT be bound with an ordinary "binding" line — it needs a gameactions block.
        // Getting this wrong is precisely why mapping the sticks by hand appeared to do nothing.
        String cfg = SteamDefaultConfigGenerator.generate("controller_generic");
        assertTrue(cfg.contains("\"gameactions\""), "no gameactions block at all — sticks would be dead");
        assertTrue(cfg.contains("\"steampad_left_stick\""), "left stick not bound to its analog action");
        assertTrue(cfg.contains("\"steampad_right_stick\""), "right stick not bound in gameplay");
        assertTrue(cfg.contains("\"steampad_vmouse\""), "virtual cursor not bound in the menu sets");
    }

    @Test
    void generatedConfigUsesUniqueGroupIds() {
        for (String type : SteamDefaultConfigGenerator.CONTROLLER_TYPES) {
            String cfg = SteamDefaultConfigGenerator.generate(type);
            Set<String> ids = new LinkedHashSet<>();
            Matcher m = Pattern.compile("\"id\"\\t\\t\"(\\d+)\"").matcher(cfg);
            int total = 0;
            while (m.find()) {
                total++;
                // Preset ids share the numeric space with group ids in this regex; count duplicates
                // only among group blocks by requiring the id to be followed by a "mode" line.
            }
            assertTrue(total > 0, type + ": no ids emitted");

            Matcher g = Pattern.compile("\"id\"\\t\\t\"(\\d+)\"\\n\\t\\t\"mode\"").matcher(cfg);
            int groups = 0;
            while (g.find()) {
                groups++;
                assertTrue(ids.add(g.group(1)), type + ": duplicate group id " + g.group(1)
                        + " — Steam silently drops the later group, losing those bindings");
            }
            assertEquals(4 * 7, groups, type + ": expected 7 groups for each of the 4 action sets");
        }
    }

    @Test
    void manifestPathsAreRewrittenToAbsoluteLocations() throws Exception {
        // Guards the v0.77.0 follow-up fix: Valve documents the "path" field as relative to the
        // game's install directory, which a non-Steam shortcut doesn't have. The rewrite sidesteps
        // that ambiguity by writing an absolute path instead — this locks in that the substitution
        // actually replaces every placeholder and leaves no relative filename behind.
        String template = read(MANIFEST);
        Path fakeConfigDir = Path.of("C:", "Fake", "Steam", "controller_config");

        String rewritten = SteamControllerConfigDeployer.substitutePaths(template, fakeConfigDir);

        for (String type : SteamDefaultConfigGenerator.CONTROLLER_TYPES) {
            String relName = SteamDefaultConfigGenerator.fileNameFor(type);
            assertFalse(rewritten.contains("\"" + relName + "\""),
                    "relative path for " + type + " was not substituted: still contains \"" + relName + "\"");
            String expectedAbs = fakeConfigDir.resolve(relName).toAbsolutePath().normalize()
                    .toString().replace('\\', '/');
            assertTrue(rewritten.contains("\"" + expectedAbs + "\""),
                    "expected absolute path for " + type + " (" + expectedAbs + ") not found in output");
        }
        // Never emits a raw backslash — VDF's escape character — regardless of platform path style.
        assertFalse(rewritten.contains("\\"), "rewritten manifest contains a raw backslash, which VDF "
                + "treats as an escape character and would corrupt the file");
    }

    @Test
    void pathSubstitutionIsIdempotentAndDoesNotTouchUnrelatedContent() throws Exception {
        String template = read(MANIFEST);
        Path fakeConfigDir = Path.of("/fake/steam/controller_config");

        String once = SteamControllerConfigDeployer.substitutePaths(template, fakeConfigDir);
        String twice = SteamControllerConfigDeployer.substitutePaths(once, fakeConfigDir);
        assertEquals(once, twice, "substituting an already-absolute path changed the output — "
                + "re-deploying (e.g. on every mod update) would corrupt the manifest further each time");

        // Everything outside the configurations block (actions, localization) must be untouched.
        assertEquals(manifestActions(template), manifestActions(once),
                "path substitution altered the declared action set — it must only touch path values");
    }

    // ------------------------------------------------------------------ v0.81.0 guards

    /** Index of the brace that closes the one opened at {@code openIdx}. */
    private static int matchingBrace(String s, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }

    /** The {@code localization} block's body. */
    private static String localizationBlock(String vdf) {
        int at = vdf.indexOf("\"localization\"");
        assertTrue(at >= 0, "manifest has no localization block");
        int open = vdf.indexOf('{', at);
        int close = matchingBrace(vdf, open);
        assertTrue(close > open, "unbalanced braces in the localization block");
        return vdf.substring(open + 1, close);
    }

    /** Localization keys per language, in file order. Line-anchored so only the KEY of each
     *  {@code "key" "value"} line is captured — never the value. */
    private static java.util.Map<String, java.util.List<String>> localizationKeys(String vdf) {
        String block = localizationBlock(vdf);
        java.util.Map<String, java.util.List<String>> out = new java.util.LinkedHashMap<>();
        Matcher lang = Pattern.compile("\"([a-z]+)\"\\s*\\{").matcher(block);
        while (lang.find()) {
            int open = block.indexOf('{', lang.start());
            int close = matchingBrace(block, open);
            if (close < 0) continue;
            java.util.List<String> keys = new java.util.ArrayList<>();
            Matcher kv = Pattern.compile("^\\s*\"([^\"]+)\"\\s+\"", Pattern.MULTILINE)
                    .matcher(block.substring(open + 1, close));
            while (kv.find()) keys.add(kv.group(1));
            out.put(lang.group(1), keys);
        }
        assertFalse(out.isEmpty(), "parsed no language blocks — test is stale");
        return out;
    }

    @Test
    void localizationKeysDoNotCollideCaseInsensitively() throws Exception {
        // VDF/KeyValues lookups are case-INSENSITIVE, so "SteamPad_Inventory" (an action set title)
        // and "steampad_inventory" (an action title) were the same key: whichever came last in the
        // file won, and Steam labelled the Inventory action set "Open Inventory". Confirmed on real
        // hardware from a screenshot of Steam's own configurator before this was fixed.
        for (var e : localizationKeys(read(MANIFEST)).entrySet()) {
            java.util.Map<String, String> seen = new java.util.LinkedHashMap<>();
            for (String key : e.getValue()) {
                String prior = seen.put(key.toLowerCase(java.util.Locale.ROOT), key);
                assertNull(prior, "language '" + e.getKey() + "': localization keys '" + prior
                        + "' and '" + key + "' differ only in case, so VDF treats them as ONE key and "
                        + "the later one silently overwrites the earlier one");
            }
        }
    }

    @Test
    void everyTitleTokenHasALocalizationEntryInEveryLanguage() throws Exception {
        String vdf = read(MANIFEST);
        // Titles are referenced as "#Token" and resolved out of the localization block.
        Set<String> tokens = new LinkedHashSet<>();
        Matcher m = Pattern.compile("\"title\"\\s*\"#([A-Za-z0-9_]+)\"").matcher(vdf);
        while (m.find()) tokens.add(m.group(1));
        assertFalse(tokens.isEmpty(), "parsed no title tokens — test is stale");

        for (var e : localizationKeys(vdf).entrySet()) {
            Set<String> keys = new LinkedHashSet<>(e.getValue());
            Set<String> missing = new LinkedHashSet<>(tokens);
            missing.removeAll(keys);
            assertTrue(missing.isEmpty(), "language '" + e.getKey() + "' is missing localization "
                    + "entries for these title tokens, so Steam shows the raw token instead: " + missing);
        }
    }

    @Test
    void manifestRevisionIsAtLeastTheOneThatShippedConfigurations() throws Exception {
        // Steam caches the layout it built for a title against the manifest revision it last saw. The
        // configurations block was added WITHOUT bumping this, so Steam kept serving the user's stale
        // template ("Gamepad", nothing bound) and ignored the new default bindings entirely.
        Matcher m = Pattern.compile("\"major_revision\"\\s*\"(\\d+)\"").matcher(read(MANIFEST));
        assertTrue(m.find(), "manifest declares no major_revision");
        int revision = Integer.parseInt(m.group(1));
        assertTrue(revision >= 4, "major_revision must be >= 4 (the revision that first shipped the "
                + "configurations block). Bump it whenever the manifest structure changes, or Steam "
                + "will not rebuild the layout. Found: " + revision);
    }

    @Test
    void generatedConfigBindsTheBackPaddlesToSlots() {
        // The paddles are the one thing ONLY Steam Input can reach (it claims them exclusively), so a
        // default that leaves them unbound defeats the main reason to run this path at all.
        String cfg = SteamDefaultConfigGenerator.generate("controller_generic");
        assertTrue(cfg.contains("button_back_left"), "left paddle unbound");
        assertTrue(cfg.contains("button_back_right"), "right paddle unbound");
        assertTrue(cfg.contains("steampad_slot_1"), "left paddle not routed to a SteamPad slot");
        assertTrue(cfg.contains("steampad_slot_2"), "right paddle not routed to a SteamPad slot");
    }
}
