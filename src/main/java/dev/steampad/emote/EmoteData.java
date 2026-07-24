package dev.steampad.emote;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One parsed emote: metadata + per-part, per-axis keyframe channels, with sampling that faithfully
 * reproduces the REAL Emotecraft/playerAnimator semantics.
 *
 * <p><b>Provenance update (v0.59.0, D099):</b> the original implementation was clean-room from the
 * public wiki (D080) — and got the core semantics WRONG in three compounding ways that were the true
 * root cause of six reported rounds of "limbs overlap/separate" deformation. At the owner's explicit
 * direction, the REAL sources were read: <a href="https://github.com/KosmX/minecraftPlayerAnimator">
 * KosmX/minecraftPlayerAnimator</a> (<b>MIT license</b> — {@code KeyframeAnimation},
 * {@code KeyframeAnimationPlayer}, {@code AnimationApplier}), whose sampling/application semantics
 * this class now ports with attribution, and KosmX/emotes (GPL, read for container-format facts
 * only, no code copied). The three corrected facts, each verified BOTH in the MIT source and
 * empirically against the user's 21 real {@code .emotecraft} files:
 * <ol>
 *   <li><b>Position values are ABSOLUTE PIVOTS in vanilla model space</b> (Y grows down, exactly
 *       vanilla's convention — no Y flip). Each axis has a per-part DEFAULT equal to the vanilla
 *       pivot ({@code rightArm=(-5,2,0)}, {@code leftLeg=(1.9,12,0.1)}, …) and a keyframe simply
 *       SETS the pivot. The old code treated values as deltas added to the part's rest pivot —
 *       doubling every offset ("se montan/se separan"), the exact reported symptom.</li>
 *   <li><b>Channels are enabled PER AXIS</b>, and a disabled axis passes the CURRENT vanilla value
 *       through untouched. An enabled axis with no keyframes pins the part at the axis default.</li>
 *   <li><b>The ease-in (0→beginTick) and ease-out (endTick→stopTick) blend against the LIVE vanilla
 *       value</b> via virtual boundary keyframes — which is why sampling needs the current vanilla
 *       value as an input, not just the tick.</li>
 * </ol>
 * There is deliberately no torso-relative child math and no rest-pivot addition here anymore — the
 * reference applier has none ({@code AnimationApplier.updatePart} assigns {@code part.x = value}
 * directly); D084/D087's rotation-matrix/delta-propagation "fixes" were compensating for the wrong
 * value semantics and are removed.
 */
public final class EmoteData {

    /** Body parts, named as in the community format ("torso"/"body" both mean vanilla "body"). */
    public enum Part { HEAD, TORSO, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

    /** Channels per part. X/Y/Z are absolute pivot coords (model units); PITCH/YAW/ROLL radians. */
    public enum Axis { X, Y, Z, PITCH, YAW, ROLL }

    /** One keyframe: value at a tick, its easing curve and optional easing argument. Per the real
     *  semantics the easing describes the travel FROM the earlier frame to the later one, read from
     *  the EARLIER frame (unless the emote sets easeBeforeKeyframe). */
    public record Keyframe(float tick, float value, Easing easing, Float easingArg) {}

    /**
     * Default value per part+axis — the value playerAnimator's OWN reference actually treats as
     * "no keyframe controls this axis yet".
     *
     * <p><b>v0.60.0/D100 REVERTED a v0.60.0 same-day "fix" that was wrong</b> — leg Z briefly became
     * 0.0f after disassembling this project's own mapped 1.21.10 jar (vanilla's true baked pivot IS
     * 0.0, confirmed by {@code javap} on {@code BipedEntityModel.getModelData}). That was the wrong
     * ground truth for THIS constant: the reference's own {@code PlayerModelMixin.setDefaultPivot()}
     * (read from the cached MIT source) explicitly does {@code this.rightLeg.z = 0.1F;
     * this.leftLeg.z = 0.1F;} — deliberately overriding vanilla's 0.0 with 0.1 as ITS OWN normalized
     * baseline, executed BEFORE vanilla's per-frame pose math runs each frame (so vanilla's own logic
     * never touches leg Z afterward and this value survives untouched into whatever "current" value
     * the emote system reads). Since keyframe data is authored and interpreted against THAT baseline,
     * not vanilla's raw bake, 0.1 is the value that actually matches real Emotecraft/playerAnimator
     * behavior — reverted back. LESSON: for "what does the reference treat as default", the
     * reference's OWN reset/normalization code is the authority, not the game's raw model data,
     * even when the two happen to disagree. Every other part's value (head/body=0,0,0; arms=±5,2,0)
     * matches both sources and stays unchanged.
     */
    public static float defaultValue(Part part, Axis axis) {
        if (axis == Axis.PITCH || axis == Axis.YAW || axis == Axis.ROLL) return 0f;
        return switch (part) {
            case HEAD, TORSO -> 0f;
            case RIGHT_ARM -> axis == Axis.X ? -5f : axis == Axis.Y ? 2f : 0f;
            case LEFT_ARM  -> axis == Axis.X ?  5f : axis == Axis.Y ? 2f : 0f;
            case RIGHT_LEG -> axis == Axis.X ? -1.9f : axis == Axis.Y ? 12f : 0.1f;
            case LEFT_LEG  -> axis == Axis.X ?  1.9f : axis == Axis.Y ? 12f : 0.1f;
        };
    }

    public final String id;            // library id (filename stem, lowercase)
    public final String name;
    public final String author;
    public final String description;

    public final boolean loop;
    public final float beginTick;
    public final float endTick;
    public final float stopTick;      // after endTick: pose eases back to vanilla until this tick
    public final float returnTick;    // loop restarts here (must be <= endTick)
    /** "the easing is defined on the keyframe AFTER the segment" flag (JSON easeBeforeKeyframe /
     *  binary isEasingBefore) — flips which frame's curve shapes each segment. */
    public boolean easingBefore = false;

    /** Raw PNG bytes for this emote's own icon, or null (falls back to a generic glyph). Set by the
     *  loader after construction (sibling {@code <id>.png} or the binary container's embedded PNG). */
    public byte[] iconPng;

    /** One channel: explicit enabled flag + sorted keyframes. Enabled with zero keyframes is a
     *  meaningful state (the axis is pinned to its default for the emote's whole active window). */
    private static final class Channel {
        boolean enabled = false;
        final List<Keyframe> keyframes = new ArrayList<>();
    }

    private final Map<Part, Map<Axis, Channel>> channels = new EnumMap<>(Part.class);

    public EmoteData(String id, String name, String author, String description,
                     boolean loop, float beginTick, float endTick, float stopTick, float returnTick) {
        this.id = id;
        this.name = name;
        this.author = author;
        this.description = description;
        this.loop = loop;
        // Same clamps as KeyframeAnimation's constructor: end > begin; stop > end (else end+3).
        this.beginTick = Math.max(beginTick, 0);
        this.endTick = Math.max(this.beginTick + 1, endTick);
        this.stopTick = stopTick <= this.endTick ? this.endTick + 3 : stopTick;
        this.returnTick = Math.min(Math.max(returnTick, 0), this.endTick);
    }

    private Channel channel(Part part, Axis axis) {
        return channels.computeIfAbsent(part, p -> new EnumMap<>(Axis.class))
                .computeIfAbsent(axis, a -> new Channel());
    }

    /** Adds a keyframe (parser use). Keeps each channel sorted by tick; enables the channel. */
    void addKeyframe(Part part, Axis axis, float tick, float value, Easing easing) {
        addKeyframe(part, axis, tick, value, easing, null);
    }

    /** Adds a keyframe with an easing argument (elastic/back/bounce tuning; null = curve default). */
    void addKeyframe(Part part, Axis axis, float tick, float value, Easing easing, Float easingArg) {
        Channel c = channel(part, axis);
        c.enabled = true;
        Keyframe kf = new Keyframe(tick, value, easing, easingArg);
        int i = c.keyframes.size();
        while (i > 0 && c.keyframes.get(i - 1).tick() > tick) i--;
        c.keyframes.add(i, kf);
    }

    /** Marks an axis enabled/disabled explicitly (binary parser — enabled can arrive with zero
     *  keyframes, which per the real semantics pins the axis at its default). */
    void setAxisEnabled(Part part, Axis axis, boolean enabled) {
        channel(part, axis).enabled = enabled;
    }

    /** True if this emote drives {@code part} at all (any enabled axis). */
    public boolean animates(Part part) {
        Map<Axis, Channel> m = channels.get(part);
        if (m == null) return false;
        for (Channel c : m.values()) if (c.enabled) return true;
        return false;
    }

    /** True if this specific axis is enabled (animation-controlled). */
    public boolean hasChannel(Part part, Axis axis) {
        Map<Axis, Channel> m = channels.get(part);
        Channel c = m == null ? null : m.get(axis);
        return c != null && c.enabled;
    }

    /** Keyframe count of an axis (debug dump / diagnostics). */
    public int keyframeCount(Part part, Axis axis) {
        Map<Axis, Channel> m = channels.get(part);
        Channel c = m == null ? null : m.get(axis);
        return c == null ? 0 : c.keyframes.size();
    }

    /**
     * Samples one axis at time {@code t} (ticks, fractional) — the faithful port of playerAnimator's
     * {@code KeyframeAnimationPlayer.Axis.getValueAtCurrentTick} (MIT):
     *
     * @param currentValue the part's CURRENT (vanilla-posed) value for this axis — returned verbatim
     *                     when the axis is disabled, and used as the blend boundary before beginTick
     *                     (ease-in from the live pose) and after endTick (ease-out back to it)
     * @param loopStarted  whether a looping emote has wrapped at least once (changes which keyframe
     *                     pairs bracket ticks near the loop seam)
     */
    public float sampleAxis(Part part, Axis axis, float t, boolean loopStarted, float currentValue) {
        Map<Axis, Channel> m = channels.get(part);
        Channel c = m == null ? null : m.get(axis);
        if (c == null || !c.enabled) return currentValue;

        boolean isAngle = axis == Axis.PITCH || axis == Axis.YAW || axis == Axis.ROLL;
        if (isAngle) currentValue = clampToRadian(currentValue);

        int currentTick = (int) Math.floor(t);
        float tickDelta = t - currentTick;
        List<Keyframe> kfs = c.keyframes;

        int pos = findAtTick(kfs, currentTick);
        Keyframe before = findBefore(kfs, pos, currentTick, currentValue, part, axis);
        if (loop && loopStarted && before.tick() < returnTick) {
            before = findBefore(kfs, findAtTick(kfs, (int) endTick), currentTick, currentValue, part, axis);
        }
        Keyframe after = findAfter(kfs, pos, currentTick, currentValue, part, axis);
        if (loop && after.tick() > endTick) {
            after = findAfter(kfs, findAtTick(kfs, (int) returnTick - 1), currentTick, currentValue, part, axis);
        }

        float value = lerpKeyframes(before, after, currentTick, tickDelta);
        return isAngle ? clampToRadian(value) : value;
    }

    /** Index of the last keyframe at or before {@code tick}, or -1 (mirror of State.findAtTick). */
    private static int findAtTick(List<Keyframe> kfs, int tick) {
        int lo = 0, hi = kfs.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (kfs.get(mid).tick() <= tick) { ans = mid; lo = mid + 1; } else hi = mid - 1;
        }
        return ans;
    }

    /** Mirror of Axis.findBefore: virtual boundary frames when no real keyframe precedes the tick. */
    private Keyframe findBefore(List<Keyframe> kfs, int pos, int currentTick, float currentValue,
                                Part part, Axis axis) {
        if (pos == -1) {
            // No keyframe yet: before beginTick ease FROM the live vanilla pose; inside the active
            // window sit at the axis default (an enabled-but-sparse axis pins to default).
            if (currentTick < beginTick) return new Keyframe(0, currentValue, Easing.INOUTSINE, null);
            return new Keyframe(currentTick < endTick ? beginTick : endTick,
                    defaultValue(part, axis), Easing.INOUTSINE, null);
        }
        Keyframe frame = kfs.get(pos);
        if (!loop && currentTick >= endTick && pos == kfs.size() - 1 && frame.tick() < endTick) {
            return new Keyframe(endTick, frame.value(), frame.easing(), frame.easingArg());
        }
        return frame;
    }

    /** Mirror of Axis.findAfter: virtual frames at the window edges / loop seam / stop fade-out. */
    private Keyframe findAfter(List<Keyframe> kfs, int pos, int currentTick, float currentValue,
                               Part part, Axis axis) {
        if (kfs.size() > pos + 1) return kfs.get(pos + 1);
        float def = defaultValue(part, axis);
        if (loop) return new Keyframe(endTick + 1, def, Easing.INOUTSINE, null);
        if (currentTick < endTick && !kfs.isEmpty()) {
            Keyframe last = kfs.get(kfs.size() - 1);
            return new Keyframe(endTick, last.value(), last.easing(), last.easingArg());
        }
        return currentTick >= endTick
                ? new Keyframe(stopTick, currentValue, Easing.INOUTSINE, null)   // ease OUT to vanilla
                : new Keyframe(currentTick >= beginTick ? endTick : beginTick, def, Easing.INOUTSINE, null);
    }

    /** Mirror of Axis.getValueFromKeyframes (with a safety clamp on f — the reference player never
     *  ticks past stopTick, so f>1 is out-of-contract there; clamping keeps us safe if a caller
     *  samples slightly past it before the prune runs). */
    private float lerpKeyframes(Keyframe before, Keyframe after, int currentTick, float tickDelta) {
        float tickBefore = before.tick();
        float tickAfter = after.tick();
        if (tickBefore >= tickAfter) {   // loop-seam wrap: unfold one side so the span is positive
            if (currentTick < tickBefore) tickBefore -= endTick - returnTick + 1;
            else tickAfter += endTick - returnTick + 1;
        }
        if (tickBefore == tickAfter) return before.value();
        float f = (currentTick + tickDelta - tickBefore) / (tickAfter - tickBefore);
        f = Math.max(0f, Math.min(1f, f));
        Keyframe easeFrom = easingBefore ? after : before;
        float eased = easeFrom.easing().apply(f, easeFrom.easingArg());
        return before.value() + (after.value() - before.value()) * eased;
    }

    /** Wraps an angle to (-π, π] — playerAnimator clamps rotation samples the same way so the
     *  virtual-boundary lerps against vanilla's (possibly multi-turn) values stay sane. */
    public static float clampToRadian(float angle) {
        double twoPi = Math.PI * 2;
        double a = (angle + Math.PI) % twoPi;
        if (a < 0) a += twoPi;
        return (float) (a - Math.PI);
    }

    /**
     * Convenience sampler for callers without a live vanilla pose (tests, tools): currentValue is
     * the axis default and loopStarted=false.
     */
    public float sample(Part part, Axis axis, float tick) {
        return sampleAxis(part, axis, tick, false, defaultValue(part, axis));
    }

    /** Length of one full pass (ticks) — the point where a non-loop emote is fully over. */
    public float totalTicks() {
        return stopTick;
    }
}
