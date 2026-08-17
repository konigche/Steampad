package dev.steampad.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The cross-instance claim rule — "does another live Minecraft instance hold this pad?".
 *
 * <p>This is the decision behind the reported "cuando hay dos instancias abiertas en Game Mode se
 * pelean los controles": whether a pad is free is a fact about a shared file, and it has to stay a
 * fact about that file even when the asking process believes the pad is already its own.
 */
class ControllerClaimRuleTest {

    private static final String ME = "token-mine";
    private static final String OTHER = "token-other";
    private static final long TTL = ControllerClaimService.ttlMs();
    private static final long NOW = 1_000_000L;

    private static String claim(String token, long stampMs) {
        return token + "\n" + stampMs + "\ncontroller-key";
    }

    @Test
    @DisplayName("A fresh claim written by another instance is reported as held by it")
    void freshForeignClaimIsHeld() {
        assertTrue(ControllerClaimService.heldByOther(claim(OTHER, NOW - 100), ME, NOW, TTL));
    }

    @Test
    @DisplayName("Our own fresh claim is not 'held by another'")
    void ownClaimIsNotForeign() {
        assertFalse(ControllerClaimService.heldByOther(claim(ME, NOW - 100), ME, NOW, TTL));
    }

    /**
     * The regression this class exists for. Two instances could pick the same free pad on the same
     * tick, both write the claim, and one then silently kept driving a pad the other had won — because
     * the check short-circuited on "this is the handle I think I own" and never re-read the file. Both
     * instances then reacted to every button on one physical controller. Ownership must come from the
     * file, so the loser of the race can find out it lost.
     */
    @Test
    @DisplayName("Losing a same-tick race is detected: a foreign token wins over local belief")
    void stolenClaimIsDetectedEvenWhenWeThinkWeOwnIt() {
        String weWroteItFirst = claim(ME, NOW - 200);
        assertFalse(ControllerClaimService.heldByOther(weWroteItFirst, ME, NOW, TTL));

        // The other instance's write landed last — the file is the authority, not our local state.
        String theyWroteItLast = claim(OTHER, NOW - 50);
        assertTrue(ControllerClaimService.heldByOther(theyWroteItLast, ME, NOW, TTL),
                "an instance that lost the race must be able to notice it lost");
    }

    @Test
    @DisplayName("A crashed instance's claim expires, so the pad frees itself")
    void staleClaimExpires() {
        assertNull(ControllerClaimService.holderOf(claim(OTHER, NOW - TTL), NOW, TTL));
        assertNull(ControllerClaimService.holderOf(claim(OTHER, NOW - TTL * 10), NOW, TTL));
        assertFalse(ControllerClaimService.heldByOther(claim(OTHER, NOW - TTL), ME, NOW, TTL));
    }

    @Test
    @DisplayName("A claim stays live while its heartbeat is being refreshed")
    void freshClaimWithinTtlIsLive() {
        assertEquals(OTHER, ControllerClaimService.holderOf(claim(OTHER, NOW - (TTL - 1)), NOW, TTL));
    }

    /** Fail-open: an unreadable claim must never look like a held pad, or nobody could ever play. */
    @Test
    @DisplayName("Absent, truncated or malformed claims mean 'nobody holds this'")
    void malformedClaimsFailOpen() {
        assertNull(ControllerClaimService.holderOf(null, NOW, TTL));
        assertNull(ControllerClaimService.holderOf("", NOW, TTL));
        assertNull(ControllerClaimService.holderOf(OTHER, NOW, TTL));                 // no stamp line
        assertNull(ControllerClaimService.holderOf(OTHER + "\nnot-a-number", NOW, TTL));
        assertNull(ControllerClaimService.holderOf("\n" + NOW, NOW, TTL));            // no token
        assertFalse(ControllerClaimService.heldByOther("garbage", ME, NOW, TTL));
    }
}
