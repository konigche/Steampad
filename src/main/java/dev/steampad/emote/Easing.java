package dev.steampad.emote;

import java.util.Locale;

/**
 * Easing functions for emote keyframe interpolation — a faithful port of playerAnimator's
 * {@code Ease} + {@code Easing} classes (<a href="https://github.com/KosmX/minecraftPlayerAnimator">
 * KosmX/minecraftPlayerAnimator</a>, <b>MIT license</b>; see DECISIONS D099). Ported rather than
 * written from easings.net because the reference's BACK/ELASTIC/BOUNCE curves are NOT the
 * easings.net ones (e.g. elastic is {@code 1 − cos³(tπ/2)·cos(t·n·π)}), and playback must look
 * identical to Emotecraft's.
 *
 * <p>Each member carries the numeric id used by the {@code .emotecraft} binary format (LINEAR=0,
 * CONSTANT=1, INSINE=6 … INOUTBOUNCE=35, CATMULLROM=36, STEP=37 — note CUBIC comes BEFORE QUAD in
 * id order). BACK/ELASTIC/BOUNCE/STEP accept an optional easing argument (binary v4 / JSON
 * "easingArg"); null means the curve's default.
 *
 * <p>Deliberate deviations from the reference, both safety-motivated:
 * <ul>
 *   <li>{@code STEP} clamps its argument to ≥2 instead of throwing (a bad file must not crash the
 *       render thread).</li>
 *   <li>{@code CATMULLROM}'s upstream formula is degenerate (algebraically it always evaluates to
 *       {@code n+2}, escaping [0,1] — clearly an upstream bug, and no known emote uses id 36), so
 *       it plays as INOUTSINE here.</li>
 * </ul>
 */
public enum Easing {
    LINEAR(0), CONSTANT(1),
    INSINE(6), OUTSINE(7), INOUTSINE(8),
    INCUBIC(9), OUTCUBIC(10), INOUTCUBIC(11),
    INQUAD(12), OUTQUAD(13), INOUTQUAD(14),
    INQUART(15), OUTQUART(16), INOUTQUART(17),
    INQUINT(18), OUTQUINT(19), INOUTQUINT(20),
    INEXPO(21), OUTEXPO(22), INOUTEXPO(23),
    INCIRC(24), OUTCIRC(25), INOUTCIRC(26),
    INBACK(27), OUTBACK(28), INOUTBACK(29),
    INELASTIC(30), OUTELASTIC(31), INOUTELASTIC(32),
    INBOUNCE(33), OUTBOUNCE(34), INOUTBOUNCE(35),
    CATMULLROM(36), STEP(37);

    public final byte id;

    Easing(int id) {
        this.id = (byte) id;
    }

    /** Parses an easing name from an emote JSON file. Null/unknown → LINEAR (permissive: unknown
     *  future names stay playable). Accepts "InOutSine", "easeinoutsine", "EASE_IN_OUT_SINE"… */
    public static Easing parse(String raw) {
        if (raw == null || raw.isEmpty()) return LINEAR;
        String n = raw.toLowerCase(Locale.ROOT).replace("ease", "").replace("_", "").trim();
        for (Easing e : values()) {
            if (e.name().toLowerCase(Locale.ROOT).equals(n)) return e;
        }
        return LINEAR;
    }

    /** Maps a binary-format ease id to its curve; unknown ids → LINEAR (mirror of Ease.getEase). */
    public static Easing fromId(byte id) {
        for (Easing e : values()) {
            if (e.id == id) return e;
        }
        return LINEAR;
    }

    /** Maps progress t ∈ [0,1] through this curve with its default argument. */
    public float apply(float t) {
        return apply(t, null);
    }

    /**
     * Maps progress t ∈ [0,1] through this curve. {@code arg} tunes BACK (overshoot), ELASTIC
     * (oscillations), BOUNCE (bounciness) and STEP (step count); ignored by the fixed curves.
     * No input clamping — the caller (EmoteData.lerpKeyframes) already clamps t, and CONSTANT must
     * return 0 even at t=1 to hold the previous keyframe's value across its whole segment.
     */
    public float apply(float t, Float arg) {
        return switch (this) {
            case LINEAR -> t;
            case CONSTANT -> 0f;
            case INSINE -> sine(t);
            case OUTSINE -> 1 - sine(1 - t);
            case INOUTSINE, CATMULLROM -> inOutSine(t);
            case INCUBIC -> t * t * t;
            case OUTCUBIC -> 1 - pow3(1 - t);
            case INOUTCUBIC -> t < 0.5f ? pow3(t * 2) / 2 : 1 - pow3((1 - t) * 2) / 2;
            case INQUAD -> t * t;
            case OUTQUAD -> 1 - pow2(1 - t);
            case INOUTQUAD -> t < 0.5f ? pow2(t * 2) / 2 : 1 - pow2((1 - t) * 2) / 2;
            case INQUART -> pow4(t);
            case OUTQUART -> 1 - pow4(1 - t);
            case INOUTQUART -> t < 0.5f ? pow4(t * 2) / 2 : 1 - pow4((1 - t) * 2) / 2;
            case INQUINT -> pow5(t);
            case OUTQUINT -> 1 - pow5(1 - t);
            case INOUTQUINT -> t < 0.5f ? pow5(t * 2) / 2 : 1 - pow5((1 - t) * 2) / 2;
            case INEXPO -> exp(t);
            case OUTEXPO -> 1 - exp(1 - t);
            case INOUTEXPO -> t < 0.5f ? exp(t * 2) / 2 : 1 - exp((1 - t) * 2) / 2;
            case INCIRC -> circle(t);
            case OUTCIRC -> 1 - circle(1 - t);
            case INOUTCIRC -> t < 0.5f ? circle(t * 2) / 2 : 1 - circle((1 - t) * 2) / 2;
            case INBACK -> back(t, arg);
            case OUTBACK -> 1 - back(1 - t, arg);
            case INOUTBACK -> t < 0.5f ? back(t * 2, arg) / 2 : 1 - back((1 - t) * 2, arg) / 2;
            case INELASTIC -> elastic(t, arg);
            case OUTELASTIC -> 1 - elastic(1 - t, arg);
            case INOUTELASTIC -> t < 0.5f ? elastic(t * 2, arg) / 2 : 1 - elastic((1 - t) * 2, arg) / 2;
            case INBOUNCE -> bounce(t, arg);
            case OUTBOUNCE -> 1 - bounce(1 - t, arg);
            case INOUTBOUNCE -> t < 0.5f ? bounce(t * 2, arg) / 2 : 1 - bounce((1 - t) * 2, arg) / 2;
            case STEP -> step(t, arg);
        };
    }

    // ---- Base curves (ported from KosmX Easing, MIT) ----

    private static float sine(float n) {
        return 1 - (float) Math.cos(n * Math.PI / 2);
    }

    private static float inOutSine(float t) {
        return t < 0.5f ? sine(t * 2) / 2 : 1 - sine((1 - t) * 2) / 2;
    }

    private static float exp(float n) {
        return (float) Math.pow(2, 10 * (n - 1));
    }

    private static float circle(float n) {
        return 1 - (float) Math.sqrt(1 - n * n);
    }

    private static float back(float t, Float n) {
        float n2 = n == null ? 1.70158f : n * 1.70158f;
        return t * t * ((n2 + 1) * t - n2);
    }

    private static float elastic(float t, Float n) {
        float n2 = n == null ? 1f : n;
        return (float) (1 - Math.pow(Math.cos(t * Math.PI / 2), 3) * Math.cos(t * n2 * Math.PI));
    }

    private static float bounce(float t, Float n) {
        float n2 = n == null ? 0.5f : n;
        float one = 121f / 16f * t * t;
        float two = (float) (121f / 4f * n2 * Math.pow(t - 6f / 11f, 2) + 1 - n2);
        float three = (float) (121 * n2 * n2 * Math.pow(t - 9f / 11f, 2) + 1 - n2 * n2);
        float four = (float) (484 * n2 * n2 * n2 * Math.pow(t - 10.5f / 11f, 2) + 1 - n2 * n2 * n2);
        return Math.min(Math.min(one, two), Math.min(three, four));
    }

    private static float step(float t, Float n) {
        int steps = Math.max(2, (int) (n == null ? 2f : n));
        if (t < 0) return 0;
        float stepLength = 1f / steps;
        float last = (steps - 1) * stepLength;
        if (t > last) return last;
        return (int) (t / stepLength) * stepLength;
    }

    private static float pow2(float v) { return v * v; }
    private static float pow3(float v) { return v * v * v; }
    private static float pow4(float v) { return v * v * v * v; }
    private static float pow5(float v) { return v * v * v * v * v; }
}
