package dev.steampad.emote;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** D111: covers the exact user-reported example plus the bundled pack's real names (never any
 *  attribution/category cruft to strip — see the bundled *.json "name" fields) so the heuristic is
 *  proven to be a no-op on names that are already short. */
class EmoteNameFormatterTest {

    @Test
    void stripsAttributionAndGenericCategoryTag_theUsersOwnExample() {
        assertEquals("Macarena", EmoteNameFormatter.shorten("Macarena (dance) - MineEmote"));
    }

    @Test
    void stripsGenericCategoryTagAlone() {
        assertEquals("Crawl", EmoteNameFormatter.shorten("Crawl (Movement)"));
    }

    @Test
    void stripsAttributionAlone() {
        assertEquals("Fresh", EmoteNameFormatter.shorten("Fresh - Fortnite"));
    }

    @Test
    void leavesHyphenatedCoreNamesAlone_noSpacesAroundTheHyphen() {
        assertEquals("T-Pose", EmoteNameFormatter.shorten("T-Pose"));
    }

    @Test
    void leavesNonGenericParentheticalsAlone_meaningfulDisambiguation() {
        assertEquals("Wave (Left)", EmoteNameFormatter.shorten("Wave (Left)"));
    }

    @Test
    void bundledPackNamesAreAlreadyShort_noOp() {
        // Real "name" fields from src/main/resources/assets/steampad/emotes/*.json — none carry an
        // attribution suffix or category tag, so the formatter must be a pure no-op on all of them.
        for (String name : new String[]{
                "Ground sit", "Bow 1", "Headspin", "Inspect", "grace", "Hug",
                "The floss", "<3", "cool sit", "Hand on heart", "Dab", "club penguin dance"}) {
            assertEquals(name, EmoteNameFormatter.shorten(name));
        }
    }

    @Test
    void handlesNullAndBlank() {
        assertEquals("", EmoteNameFormatter.shorten(null));
        assertEquals("", EmoteNameFormatter.shorten("   "));
    }

    @Test
    void neverReturnsEmpty_fallsBackToOriginalTrimmed() {
        // A name that IS entirely a generic tag has nothing left after stripping — must not vanish.
        assertEquals("(dance)", EmoteNameFormatter.shorten("(dance)"));
    }

    @Test
    void nonLatinNameWithoutWrapperPassesThroughUnchanged() {
        // No translation is attempted (D111) — only the decorative wrapper, if any, is removed.
        String russian = "Русский танец";
        assertEquals(russian, EmoteNameFormatter.shorten(russian));
    }

    @Test
    void nonLatinNameWithAttributionStillGetsTheWrapperStripped() {
        assertEquals("Русский танец", EmoteNameFormatter.shorten("Русский танец - MineEmote"));
    }
}
