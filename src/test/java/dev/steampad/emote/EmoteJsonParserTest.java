package dev.steampad.emote;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the clean-room emote parser against the REAL bundled CC0 emote files — the same data
 * users share in the wild, so parse regressions surface at build time, not on hardware (FASE 63).
 */
class EmoteJsonParserTest {

    private EmoteData load(String file) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/assets/steampad/emotes/" + file)) {
            assertNotNull(in, "bundled emote missing: " + file);
            return EmoteJsonParser.parse(file.replace(".json", ""),
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void allBundledEmotesParse() throws Exception {
        String[] files = {"dab.json", "floss.json", "heart.json", "hearthands.json", "bow1.json",
                "hug.json", "cool_sit.json", "campfire_sit1.json", "headspin.json",
                "club_penguin_dance.json", "inspect.json", "grace.json"};
        for (String f : files) {
            EmoteData d = load(f);
            assertNotNull(d, f + " failed to parse");
            assertTrue(d.endTick > d.beginTick, f + " has an empty timeline");
        }
    }

    @Test
    void dabHasHeadKeyframesAndMetadata() throws Exception {
        EmoteData d = load("dab.json");
        assertEquals("Dab", d.name);
        assertTrue(d.animates(EmoteData.Part.HEAD), "dab animates the head");
        assertFalse(d.loop, "dab is a one-shot emote");
    }

    @Test
    void loopingEmoteFoldsTicksIntoLoopWindow() throws Exception {
        EmoteData d = load("heart.json");   // isLoop: "true" (string form) in the source file
        assertTrue(d.loop, "heart declares isLoop=\"true\" as a string — parser must accept it");
        assertTrue(d.returnTick < d.endTick);
    }

    /** The REAL Emotecraft sampling contract (D099): values interpolate between keyframes, hold to
     *  endTick, then EASE BACK to the live vanilla value (the default here) by stopTick — they do
     *  NOT hold forever after the last keyframe (the pre-D099 assumption, proven wrong against
     *  playerAnimator's Axis.getValueAtCurrentTick). */
    @Test
    void samplingFollowsRealEmotecraftContract() {
        EmoteData d = new EmoteData("t", "t", "", "", false, 0, 20, 23, 0);
        d.addKeyframe(EmoteData.Part.HEAD, EmoteData.Axis.YAW, 0f, 0f, Easing.LINEAR);
        d.addKeyframe(EmoteData.Part.HEAD, EmoteData.Axis.YAW, 10f, 1f, Easing.LINEAR);
        assertEquals(0.5f, d.sample(EmoteData.Part.HEAD, EmoteData.Axis.YAW, 5f), 1e-4);
        assertEquals(1f, d.sample(EmoteData.Part.HEAD, EmoteData.Axis.YAW, 15f), 1e-4,
                "holds the last keyframe's value until endTick");
        assertEquals(0.5f, d.sample(EmoteData.Part.HEAD, EmoteData.Axis.YAW, 21.5f), 1e-4,
                "eases back toward the live (default) value between endTick and stopTick");
        assertEquals(0f, d.sample(EmoteData.Part.HEAD, EmoteData.Axis.YAW, 99f), 1e-4,
                "fully back to the live (default) value after stopTick");
    }

    /** A disabled axis must pass the live vanilla value through untouched; positions have per-part
     *  vanilla-pivot defaults (the core D099 fact — absolute pivots, not deltas). */
    @Test
    void axisSemanticsMatchReference() {
        assertEquals(-5f, EmoteData.defaultValue(EmoteData.Part.RIGHT_ARM, EmoteData.Axis.X), 1e-6);
        assertEquals(12f, EmoteData.defaultValue(EmoteData.Part.LEFT_LEG, EmoteData.Axis.Y), 1e-6);
        // D107: BEND_DIRECTION/BEND_AMOUNT must default to 0f on every part, including legs — found by
        // numerically cross-checking against the real MIT reference (KeyframeAnimationPlayer), where a
        // leg's bend axis fell into RIGHT_LEG/LEFT_LEG's 0.1f Z-position default before this fix.
        for (EmoteData.Part p : EmoteData.Part.values()) {
            assertEquals(0f, EmoteData.defaultValue(p, EmoteData.Axis.BEND_DIRECTION), 1e-6, p + " bendDir default");
            assertEquals(0f, EmoteData.defaultValue(p, EmoteData.Axis.BEND_AMOUNT), 1e-6, p + " bendAmount default");
        }
        EmoteData d = new EmoteData("t", "t", "", "", false, 0, 20, 23, 0);
        d.addKeyframe(EmoteData.Part.RIGHT_ARM, EmoteData.Axis.X, 10f, -3f, Easing.LINEAR);
        assertEquals(7.7f, d.sampleAxis(EmoteData.Part.RIGHT_ARM, EmoteData.Axis.Y, 10f, false, 7.7f),
                1e-4, "disabled axis passes the current vanilla value through");
        assertEquals(-3f, d.sampleAxis(EmoteData.Part.RIGHT_ARM, EmoteData.Axis.X, 10f, false, -5f),
                1e-4, "enabled axis returns the absolute keyframe value");
    }

    @Test
    void easingParsesAllDocumentedSpellings() {
        assertEquals(Easing.INOUTSINE, Easing.parse("easeinoutsine"));
        assertEquals(Easing.INOUTSINE, Easing.parse("InoUtSInE"));
        assertEquals(Easing.INOUTQUAD, Easing.parse("EASEINOUTQUAD"));
        assertEquals(Easing.LINEAR, Easing.parse("LINEAR"));
        assertEquals(Easing.LINEAR, Easing.parse("something-future"), "unknown falls back to linear");
    }

    /** The binary ease-id table from the reference (note CUBIC ids come BEFORE QUAD ids). */
    @Test
    void easingIdTableMatchesReference() {
        assertEquals(Easing.LINEAR, Easing.fromId((byte) 0));
        assertEquals(Easing.CONSTANT, Easing.fromId((byte) 1));
        assertEquals(Easing.INSINE, Easing.fromId((byte) 6));
        assertEquals(Easing.INCUBIC, Easing.fromId((byte) 9));
        assertEquals(Easing.INQUAD, Easing.fromId((byte) 12));
        assertEquals(Easing.INOUTBOUNCE, Easing.fromId((byte) 35));
        assertEquals(Easing.STEP, Easing.fromId((byte) 37));
        assertEquals(Easing.LINEAR, Easing.fromId((byte) 99), "unknown id falls back to linear");
    }

    /**
     * D110 — the fix for "sitting emotes float in the air". {@code body} is the WHOLE-MODEL transform
     * (applied to the render matrix stack, in block units); {@code torso} is the body BONE. They were
     * merged into one channel until v0.70.0, so an emote authored to physically lower the character
     * onto the floor instead just rotated its torso while it stayed standing at full height.
     *
     * <p>The version rule is the reference's own ({@code AnimationJson}:
     * {@code if(version < 3 && name.equals("torso")) name = "body";}) — a pre-v3 file's "torso" block
     * really carries whole-model data. Every bundled emote (and, judging by them, most community
     * files) omits {@code version} entirely, so this path is the common one, not an edge case.
     */
    @Test
    void bodyIsTheWholeModelChannelAndTorsoIsTheBone() throws Exception {
        // No "version" declared => version 1 => "torso" IS the whole-model channel.
        EmoteData legacy = parseInline("""
            {"name":"legacy","emote":{"beginTick":0,"endTick":20,"stopTick":21,"degrees":false,
             "moves":[{"tick":10,"easing":"LINEAR","torso":{"y":-0.5,"pitch":1.2}}]}}""");
        assertTrue(legacy.animates(EmoteData.Part.BODY),
                "a pre-v3 \"torso\" block carries whole-model data — this is what drops a sitting "
                        + "character to the floor");
        assertEquals(-0.5f, legacy.sample(EmoteData.Part.BODY, EmoteData.Axis.Y, 10f), 1e-4);
        assertEquals(1.2f, legacy.sample(EmoteData.Part.BODY, EmoteData.Axis.PITCH, 10f), 1e-4);
        assertFalse(legacy.animates(EmoteData.Part.TORSO),
                "it must NOT also land on the torso bone — applying it there was the bug");

        // "version": 3 => modern split, both channels taken literally and kept apart.
        EmoteData modern = parseInline("""
            {"name":"modern","version":3,"emote":{"beginTick":0,"endTick":20,"stopTick":21,
             "degrees":false,"moves":[{"tick":10,"easing":"LINEAR","torso":{"pitch":0.4},
             "body":{"y":-0.5}}]}}""");
        assertEquals(0.4f, modern.sample(EmoteData.Part.TORSO, EmoteData.Axis.PITCH, 10f), 1e-4,
                "v3 keeps torso as the bone");
        assertEquals(-0.5f, modern.sample(EmoteData.Part.BODY, EmoteData.Axis.Y, 10f), 1e-4,
                "v3 keeps body as the whole-model channel");
        assertFalse(modern.animates(EmoteData.Part.TORSO) && modern.hasChannel(EmoteData.Part.TORSO, EmoteData.Axis.Y),
                "the two channels must stay independent");
    }

    private static EmoteData parseInline(String json) {
        EmoteData d = EmoteJsonParser.parse("inline", new java.io.StringReader(json));
        assertNotNull(d, "inline test emote failed to parse");
        return d;
    }
}
