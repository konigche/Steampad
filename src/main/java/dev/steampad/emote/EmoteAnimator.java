package dev.steampad.emote;

import dev.steampad.util.LogUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.math.Vec3d;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SteamPad's own player-emote playback engine (zero external animation dependency — D080 records
 * why: the animation libraries in this space have no artifact built for MC 1.21.10, and a
 * cross-version jar is exactly the build/runtime-mismatch hazard this project already got burned
 * by, STATE Fix 12).
 *
 * <p><b>v0.59.0 (D099):</b> the application step is now a faithful port of playerAnimator's
 * {@code AnimationApplier.updatePart} (<b>MIT</b>): each enabled axis's sampled value is assigned
 * DIRECTLY to the model part ({@code part.originX = sampled}) with the part's CURRENT vanilla value
 * fed into the sampler (it's the blend boundary for the ease-in before beginTick and the ease-out
 * to stopTick). Values are absolute pivots in vanilla model space — there is deliberately NO rest
 * addition, NO Y flip, and NO torso rotation/translation propagation here anymore; those were
 * compensations for misread value semantics and were the true root cause of the six reported
 * deformation rounds (see {@link EmoteData}'s class doc).
 *
 * <p>Playback state lives per entity id (the local player and any remote player synced via
 * {@link EmoteNetwork}). The render pipeline asks {@link #apply} once per rendered player from a
 * thin TAIL mixin on {@code PlayerEntityModel.setAngles} — vanilla computes its full pose first,
 * then channels the emote actually animates OVERRIDE that pose (a dance must own the limbs it
 * uses; parts the emote never touches keep vanilla behavior, so walking legs still walk under an
 * arms-only emote).
 *
 * <p>Time base is wall-clock milliseconds converted to ticks (20/s) — sampling is per-FRAME smooth
 * and immune to tick-rate dips (the same lesson as the virtual keyboard's tickScale, B076).
 *
 * <p>The LOCAL player's emote cancels on movement input (walking mid-dance looks broken and is the
 * ecosystem's established behavior) — checked cheaply once per client tick via {@link #clientTick}.
 */
public final class EmoteAnimator {

    /** One running emote on one entity. */
    private static final class Playback {
        final EmoteData data;
        final long startMs;
        final long generation;   // ownership token — see currentGeneration()
        /** True only for a genuine {@link #playLocal} — false for previews (Library/Wheel's
         *  {@code playFor} on the local player) and pinned static frames. Distinguishes "the user is
         *  actually dancing" from "a menu/wheel is just showing a live thumbnail" for effects that
         *  must NOT engage during mere browsing — see {@code isLocalRealEmotePlaying()} and its use
         *  in {@code ThirdPersonCameraController} (v0.60.0/D100: forcing free-look camera). */
        final boolean isRealEmote;
        /** Non-null = a PINNED, non-advancing single frame (see {@link #applyPinnedFrame}) — never
         *  read the wall clock, never expires, never counts as "ownable" by a preview/real emote. */
        final Float pinnedTick;
        boolean stopping = false;   // eased stop requested (plays end→stop fade, then removes)
        long stopStartMs = 0L;
        float stopFromTick = 0f;
        boolean stopLoopStarted = false;   // loop-seam state frozen with the pose at stop time

        Playback(EmoteData data, boolean isRealEmote) {
            this.data = data;
            this.isRealEmote = isRealEmote;
            this.pinnedTick = null;
            this.startMs = System.currentTimeMillis();
            this.generation = GENERATION_COUNTER.incrementAndGet();
        }

        Playback(EmoteData data, float pinnedTick) {
            this.data = data;
            this.isRealEmote = false;
            this.pinnedTick = pinnedTick;
            this.startMs = System.currentTimeMillis();
            this.generation = -2L;   // sentinel — never matches a real currentGeneration() lookup
        }
    }

    private static final Map<Integer, Playback> playing = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong GENERATION_COUNTER =
            new java.util.concurrent.atomic.AtomicLong();

    /**
     * Rest-pose origin (vanilla pivot), keyed by the ACTUAL {@link ModelPart} object identity — not
     * per-playback (Minecraft renders every player with a SHARED model instance, only two exist:
     * wide/slim arms). Sourced from {@link ModelPart#getDefaultTransform()} — the BAKED, immutable
     * pivot recorded at model-bake time — never from the live {@code originX/Y/Z} fields.
     *
     * <p>An earlier version read the live fields "opportunistically" from any entity caught NOT
     * emoting, on the assumption that a non-emoting entity is guaranteed vanilla-clean. That
     * assumption was FALSE: {@code BipedEntityModel.setAngles} itself shifts {@code originY}/
     * {@code originZ} by 3.2–4.2 units for the sneaking pose (confirmed via javap on the mapped jar —
     * see DECISIONS). If the very first "clean" entity sampled happened to be sneaking, that shifted
     * value was cached forever ({@code putIfAbsent} never re-reads), silently offsetting every emote
     * for every player sharing that model variant for the rest of the session — a fourth, independent
     * deformation root cause layered on top of the previous three. The default transform is immune to
     * this by construction: nothing except {@code ModelPart.setDefaultTransform} (never called on
     * player models post-bake) can change it.
     */
    private static final Map<ModelPart, float[]> restOrigin = new IdentityHashMap<>();

    private static float[] restOf(ModelPart part) {
        return restOrigin.computeIfAbsent(part, p -> {
            var t = p.getDefaultTransform();
            return new float[]{t.x(), t.y(), t.z()};
        });
    }

    /** The ownership token of whatever is currently playing for {@code entityId}, or -1 if nothing
     *  is. Lets a caller that started a {@code playFor} (a preview) verify — before stopping it —
     *  that nothing else (a real {@link #playLocal}) has since taken over the same entity slot. */
    public static long currentGeneration(int entityId) {
        Playback p = playing.get(entityId);
        return p == null ? -1L : p.generation;
    }

    /** Number of entities with a playback registered right now (debug dump, D098). */
    public static int activePlaybackCount() { return playing.size(); }

    /** Ease-back window (ticks) when an emote is cancelled mid-animation. */
    private static final float CANCEL_FADE_TICKS = 6f;

    /** True while the LOCAL player is sitting in third person because {@link #playLocal} put them
     *  there (first-person can't see your own body) — used to hop back to first person once the
     *  emote ends, but only if the player never deliberately changed perspective themselves meanwhile. */
    private static boolean autoSwitchedPerspective = false;

    // ---- Camera transition (feedback: "agrega una animación como zoom out/zoom in... cuando el
    // emote inicia/termina, si se encuentra en primera persona") — the instant FIRST_PERSON<->
    // THIRD_PERSON_BACK cut felt jarring. Instead of teleporting the perspective enum, the camera
    // EASES between the eye position and vanilla's third-person position over a short window —
    // "leaving"/"entering" the body — while perspective itself flips to THIRD_PERSON_BACK immediately
    // (so the body is visible right away) and only flips BACK to FIRST_PERSON once the exit transition
    // visually finishes (see clientTick). Consumed by ThirdPersonCameraMixin via computeCameraOverride.
    private static final long TRANSITION_MS = 220;
    private static boolean transitionActive = false;
    private static boolean transitionEntering = false;   // true = 1st→3rd (zoom out), false = reverse
    private static long transitionStartMs = 0L;
    private static Vec3d transitionEyePos = null;

    private static void startTransition(boolean entering, Vec3d eyePos) {
        transitionActive = true;
        transitionEntering = entering;
        transitionStartMs = System.currentTimeMillis();
        transitionEyePos = eyePos;
    }

    private static double transitionProgress() {
        double t = (System.currentTimeMillis() - transitionStartMs) / (double) TRANSITION_MS;
        return t < 0 ? 0 : (t > 1 ? 1 : t);
    }

    private EmoteAnimator() {}

    // ---- Control -----------------------------------------------------------------------------

    /** Starts an emote on the LOCAL player and announces it to the server (multiplayer sync). */
    public static void playLocal(String emoteId) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        EmoteData data = EmoteLibrary.byId(emoteId);
        if (data == null) {
            LogUtil.warn("[SteamPad] Emote '{}' is not in the library — not playing.", emoteId);
            return;
        }
        playing.put(mc.player.getId(), new Playback(data, /* isRealEmote */ true));
        EmoteNetworkClient.sendStart(emoteId);
        // First-person can't see your own body — hop to third person so the emote is visible (the
        // established behavior in this mod ecosystem). Only in gameplay; never fights a screen.
        // Remembers that WE made this switch so it can be undone once the emote ends (see
        // restorePerspectiveIfNeeded) without fighting a perspective the player picked themselves.
        if (mc.currentScreen == null
                && mc.options.getPerspective() == net.minecraft.client.option.Perspective.FIRST_PERSON) {
            Vec3d eye = mc.player.getEyePos();
            mc.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
            autoSwitchedPerspective = true;
            startTransition(true, eye);   // eases the camera OUT from the eye instead of a hard cut
        }
        LogUtil.info("[SteamPad] Playing emote '{}' ({}).", data.name, emoteId);
    }

    /** Stops the LOCAL player's emote (eased) and announces the stop. */
    public static void stopLocal() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (requestStop(mc.player.getId())) EmoteNetworkClient.sendStop();
    }

    /** True while the local player has ANY playback running — a genuine dance OR a menu/wheel
     *  preview (used by the existing "conflictsWithRealEmote" ownership checks, which need to know
     *  "is the entity slot occupied at all", not specifically by what). */
    public static boolean isLocalPlaying() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && playing.containsKey(mc.player.getId());
    }

    /** True only while the local player is running a GENUINE emote started via {@link #playLocal} —
     *  false while merely previewing in the Library/Wheel (v0.60.0/D100). Effects that must not
     *  engage just from browsing (e.g. forcing the free-look camera) should check this, not
     *  {@link #isLocalPlaying}. */
    public static boolean isLocalRealEmotePlaying() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        Playback p = playing.get(mc.player.getId());
        return p != null && p.isRealEmote;
    }

    /** Starts a PREVIEW on any entity id (remote players via network sync; local Library/Wheel
     *  thumbnails) — never counts as a "real" emote, see {@link #isLocalRealEmotePlaying}. */
    public static void playFor(int entityId, EmoteData data) {
        playing.put(entityId, new Playback(data, /* isRealEmote */ false));
    }

    // ---- Static-frame thumbnails (FASE 72, "AAA" emote list) -----------------------------------
    //
    // A caller rendering MANY thumbnails of the SAME physical entity in one frame (e.g. every row of
    // the emote library, each posed as a different emote's frozen frame) needs to pose-then-draw once
    // per row, sequentially — Minecraft's player model is a SHARED instance, so only one pose can be
    // "current" at any instant. These three methods let a caller safely interleave one-shot static
    // poses with whatever REAL continuous playback (a genuine emote the player triggered, or another
    // caller's own live preview) already owns that entity id, without ever disturbing it: snapshot it
    // once before the batch of static draws, restore it before drawing the row that should show it
    // live, and restore it again after the whole batch finishes.

    /** Opaque snapshot of whatever is currently registered for {@code entityId} (may be {@code null}
     *  if nothing is playing) — pass back to {@link #restorePlayback}. */
    public static Object snapshotPlayback(int entityId) {
        return playing.get(entityId);
    }

    /** Restores a snapshot taken by {@link #snapshotPlayback} — removes the entry entirely if the
     *  snapshot was {@code null} (nothing was playing at snapshot time). */
    public static void restorePlayback(int entityId, Object snapshot) {
        if (snapshot == null) playing.remove(entityId);
        else playing.put(entityId, (Playback) snapshot);
    }

    /** Poses {@code entityId} to a FIXED, non-advancing frame of {@code data} at {@code tick} — for
     *  exactly the next render of that entity. Never persists meaningfully on its own; see the class
     *  doc above for the snapshot/restore dance a multi-row caller needs around a batch of these. */
    public static void applyPinnedFrame(int entityId, EmoteData data, float tick) {
        playing.put(entityId, new Playback(data, tick));
    }

    /** A single representative tick for a static thumbnail — partway into the emote rather than its
     *  rest/bind pose at tick 0, which reads as "broken" or "no animation" for most dances. A fixed
     *  heuristic (35% of the way from begin to end), not a per-emote curated "best" frame — there's no
     *  general way to know which frame looks best for an arbitrary community-authored emote. */
    public static float representativeTick(EmoteData data) {
        return data.beginTick + (data.endTick - data.beginTick) * 0.35f;
    }

    /** Eased stop for any entity id. Returns true if something was actually playing. */
    public static boolean requestStop(int entityId) {
        Playback p = playing.get(entityId);
        if (p == null) return false;
        if (!p.stopping) {
            p.stopping = true;
            p.stopStartMs = System.currentTimeMillis();
            float raw = rawTick(p);
            p.stopFromTick = foldTick(p.data, raw);
            p.stopLoopStarted = p.data.loop && raw > p.data.endTick;
        }
        return true;
    }

    /** Hard clear (disconnect, world change) — also drops any in-progress camera transition so a
     *  disconnect mid-transition can never leave the camera permanently stuck blended at the eye
     *  position (the completion check in {@link #clientTick} needs a live {@code mc.player}, which a
     *  disconnect removes). */
    public static void clearAll() {
        playing.clear();
        transitionActive = false;
        autoSwitchedPerspective = false;
    }

    /**
     * Once per client tick from SteamPadClient: cancels the local emote when the player MOVES
     * (input or knockback), and prunes finished non-loop playbacks so the map never leaks.
     */
    public static void clientTick(MinecraftClient mc) {
        // Finish the exit transition (3rd→1st) once it's visually done — the actual perspective flip
        // is deliberately delayed until here so the camera keeps easing IN third-person space the
        // whole time instead of jump-cutting the instant the emote ends.
        if (transitionActive && !transitionEntering && mc.player != null && transitionProgress() >= 1.0) {
            transitionActive = false;
            if (mc.options.getPerspective() == net.minecraft.client.option.Perspective.THIRD_PERSON_BACK) {
                mc.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
            }
            autoSwitchedPerspective = false;
        } else if (transitionActive && transitionEntering && transitionProgress() >= 1.0) {
            transitionActive = false;
        }
        if (playing.isEmpty()) return;
        if (mc.player != null) {
            Playback p = playing.get(mc.player.getId());
            if (p != null) {
                if (!p.stopping) {
                    var mv = mc.player.input != null ? mc.player.input.getMovementInput() : null;
                    boolean moving = (mv != null && mv.x * mv.x + mv.y * mv.y > 0.01f)
                            || mc.player.isSprinting()
                            || mc.player.getVelocity().horizontalLengthSquared() > 0.02;
                    if (moving) stopLocal();
                }
                if (isFinished(p)) restorePerspectiveIfNeeded(mc);
            }
        }
        // Prune anything past its end (finished fade-outs, finished one-shot emotes).
        playing.entrySet().removeIf(e -> isFinished(e.getValue()));
    }

    /** Starts easing back to first person once the local emote truly ends, but only if this class was
     *  the one that switched away from it, and only if the player hasn't since picked a perspective
     *  of their own (still sitting exactly where {@link #playLocal} left them). The actual perspective
     *  flip happens once the transition finishes (see {@link #clientTick}), not here — so the camera
     *  keeps easing IN third-person space instead of jump-cutting the instant the emote ends. */
    private static void restorePerspectiveIfNeeded(MinecraftClient mc) {
        if (!autoSwitchedPerspective || transitionActive) return;
        if (mc.options.getPerspective() == net.minecraft.client.option.Perspective.THIRD_PERSON_BACK) {
            startTransition(false, null);   // eyePos recomputed live each frame for the exit blend
        } else {
            autoSwitchedPerspective = false;   // perspective already changed some other way — nothing to do
        }
    }

    /**
     * Called from {@code ThirdPersonCameraMixin}'s TAIL hook, once per {@code Camera.update()} —
     * blends the camera between the eye position and vanilla's own (already collision-clipped)
     * third-person position while an emote-triggered perspective transition is in progress. Returns
     * {@code null} when no transition is active (the overwhelmingly common case — one boolean check).
     */
    public static Vec3d computeCameraOverride(MinecraftClient mc, Vec3d vanillaPos) {
        if (!transitionActive || mc.player == null) return null;
        double t = transitionProgress();
        double eased = t * t * (3 - 2 * t);   // smoothstep — no velocity "kick" at either end
        Vec3d eye = transitionEyePos != null ? transitionEyePos : mc.player.getEyePos();
        double blend = transitionEntering ? eased : (1.0 - eased);
        return eye.add(vanillaPos.subtract(eye).multiply(blend));
    }

    // ---- Render-side application (called from the PlayerEntityModel mixin, per frame) ---------

    /**
     * Applies the running emote (if any) for {@code entityId} onto an already-vanilla-posed biped
     * model. Channels present in the emote REPLACE the vanilla value for that axis; everything else
     * is left exactly as vanilla computed it.
     */
    public static void apply(BipedEntityModel<?> model, int entityId) {
        Playback p = playing.get(entityId);
        if (p == null) return;   // not emoting — leave vanilla's pose exactly as computed

        float rawTick = rawTick(p);
        boolean loopStarted = p.data.loop && rawTick > p.data.endTick;
        float tick = foldTick(p.data, rawTick);
        float weight = 1f;   // 1 = full emote pose; <1 = blending back to vanilla (user-cancel fade)

        if (p.stopping) {
            float fade = (System.currentTimeMillis() - p.stopStartMs) / 50f;
            weight = 1f - Math.min(1f, fade / CANCEL_FADE_TICKS);
            tick = p.stopFromTick;             // freeze the pose while fading it out
            loopStarted = p.stopLoopStarted;
            if (weight <= 0f) return;          // fully faded — clientTick prunes it
        }
        // NOTE: no special endTick→stopTick weight handling anymore — the reference semantics ease
        // each axis back to the LIVE vanilla value through virtual boundary keyframes inside
        // EmoteData.sampleAxis (findBefore/findAfter), which is both smoother (per-axis, eased) and
        // exactly what Emotecraft does.

        applyPart(model.head, p.data, EmoteData.Part.HEAD, tick, loopStarted, weight);
        applyPart(model.body, p.data, EmoteData.Part.TORSO, tick, loopStarted, weight);
        applyPart(model.rightArm, p.data, EmoteData.Part.RIGHT_ARM, tick, loopStarted, weight);
        applyPart(model.leftArm, p.data, EmoteData.Part.LEFT_ARM, tick, loopStarted, weight);
        applyPart(model.rightLeg, p.data, EmoteData.Part.RIGHT_LEG, tick, loopStarted, weight);
        applyPart(model.leftLeg, p.data, EmoteData.Part.LEFT_LEG, tick, loopStarted, weight);
        // The hat layer must follow the head (vanilla copies it after posing; we re-sync — 1.21.10
        // ModelPart has no copyTransform, transforms move via get/setTransform, verified by javap).
        model.hat.setTransform(model.head.getTransform());

        debugDumpPose(entityId, p, tick, weight, model);
    }

    /**
     * Poses the model to a FIXED frame of {@code data} — the per-cell static thumbnail path (render
     * states duck-tagged at queue time, see {@link EmotePreviewState}). Bypasses the playback map
     * entirely, so any number of DIFFERENTLY-posed thumbnails of the same player can be queued in a
     * single frame — the thing the map-keyed path structurally cannot do (D092/D099).
     */
    public static void applyStatic(BipedEntityModel<?> model, EmoteData data, float tick) {
        applyPart(model.head, data, EmoteData.Part.HEAD, tick, false, 1f);
        applyPart(model.body, data, EmoteData.Part.TORSO, tick, false, 1f);
        applyPart(model.rightArm, data, EmoteData.Part.RIGHT_ARM, tick, false, 1f);
        applyPart(model.leftArm, data, EmoteData.Part.LEFT_ARM, tick, false, 1f);
        applyPart(model.rightLeg, data, EmoteData.Part.RIGHT_LEG, tick, false, 1f);
        applyPart(model.leftLeg, data, EmoteData.Part.LEFT_LEG, tick, false, 1f);
        model.hat.setTransform(model.head.getTransform());
    }

    /**
     * One part, all six axes — the faithful mirror of {@code AnimationApplier.updatePart} (MIT):
     * the part's CURRENT (vanilla-posed) value goes IN to the sampler and the sampled value is
     * assigned straight back. A disabled axis returns the current value unchanged (vanilla keeps
     * driving it — walking legs still walk under an arms-only emote); an enabled axis's value is an
     * absolute pivot / radian angle. {@code weight} (user-cancel fade) lerps between the two.
     */
    private static void applyPart(ModelPart part, EmoteData data, EmoteData.Part which,
                                  float tick, boolean loopStarted, float weight) {
        if (!data.animates(which)) return;
        part.originX = lerp(part.originX,
                data.sampleAxis(which, EmoteData.Axis.X, tick, loopStarted, part.originX), weight);
        part.originY = lerp(part.originY,
                data.sampleAxis(which, EmoteData.Axis.Y, tick, loopStarted, part.originY), weight);
        part.originZ = lerp(part.originZ,
                data.sampleAxis(which, EmoteData.Axis.Z, tick, loopStarted, part.originZ), weight);
        part.pitch = lerp(part.pitch,
                data.sampleAxis(which, EmoteData.Axis.PITCH, tick, loopStarted, part.pitch), weight);
        part.yaw = lerp(part.yaw,
                data.sampleAxis(which, EmoteData.Axis.YAW, tick, loopStarted, part.yaw), weight);
        part.roll = lerp(part.roll,
                data.sampleAxis(which, EmoteData.Axis.ROLL, tick, loopStarted, part.roll), weight);
    }

    // ---- Diagnostics (sesión 29 cont. 5; recalibrated for the D099 semantics) ------------------
    //
    // Dumps the ACTUAL computed numbers once every ~2s per emoting entity, throttled so it's
    // readable instead of a flood. Under the corrected (absolute-pivot) semantics a healthy driven
    // part sits NEAR its baked rest pivot most of the time — it was precisely this dump showing
    // R_ARM's distFromRest pinned at |(-5,2,0)| = 5.385 that exposed the delta-vs-absolute misread
    // (the emote was setting the pivot to its own default and the old code ADDED that to rest,
    // doubling every offset). NaNs or a part parked far from rest remain the red flags.
    private static final Map<Integer, Long> lastPoseDumpMs = new java.util.concurrent.ConcurrentHashMap<>();
    /** Full per-part/per-axis breakdown of the LOCAL player's active playback, rebuilt every frame
     *  (cheap — string building + already-computed lerp reads, no extra sampling) so an on-demand
     *  Debug Dump (v0.60.0/D100 — "pon volcado de cada cosa... para que sepas donde pasa") always
     *  shows the CURRENT frame's numbers, independent of the {@code [emote-pose]} log line's own 2s
     *  throttle. Null when the local player has never emoted this session. */
    private static volatile String lastLocalPoseDump = null;

    /** For {@link dev.steampad.steam.SteamRuntimeDiagnostics}: the local player's current emote pose,
     *  per part/axis — or an explanatory placeholder if nothing is (or has ever been) playing. */
    public static String lastLocalPoseSummary() {
        if (!isLocalPlaying()) return "(sin playback activo ahora mismo)";
        return lastLocalPoseDump == null ? "(playback activo, aún sin datos de pose)" : lastLocalPoseDump;
    }

    private static void debugDumpPose(int entityId, Playback p, float tick, float weight,
                                      BipedEntityModel<?> model) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(java.util.Locale.ROOT,
                "id='%s' real=%s tick=%.2f weight=%.2f loop=%s begin=%.1f end=%.1f stop=%.1f return=%.1f easingBefore=%s%n",
                p.data.id, p.isRealEmote, tick, weight, p.data.loop, p.data.beginTick, p.data.endTick,
                p.data.stopTick, p.data.returnTick, p.data.easingBefore));
        dumpPart(sb, "TORSO", EmoteData.Part.TORSO, p.data, model.body);
        dumpPart(sb, "HEAD", EmoteData.Part.HEAD, p.data, model.head);
        dumpPart(sb, "R_ARM", EmoteData.Part.RIGHT_ARM, p.data, model.rightArm);
        dumpPart(sb, "L_ARM", EmoteData.Part.LEFT_ARM, p.data, model.leftArm);
        dumpPart(sb, "R_LEG", EmoteData.Part.RIGHT_LEG, p.data, model.rightLeg);
        dumpPart(sb, "L_LEG", EmoteData.Part.LEFT_LEG, p.data, model.leftLeg);
        String summary = sb.toString();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player.getId() == entityId) lastLocalPoseDump = summary;

        long now = System.currentTimeMillis();
        long last = lastPoseDumpMs.getOrDefault(entityId, 0L);
        if (now - last < 2000L) return;
        lastPoseDumpMs.put(entityId, now);
        LogUtil.debug("[SteamPad][emote-pose] entity={}\n{}", entityId, summary);
    }

    /** One part, EVERY axis (not just position) — enabled?, keyframe count, live value, this axis's
     *  own default. {@code distFromRest} stays the quick eyeball red-flag it always was (NaN or far
     *  from the baked pivot); the per-axis rows are the actual "where/what" the user asked for. */
    private static void dumpPart(StringBuilder sb, String label, EmoteData.Part which, EmoteData data, ModelPart part) {
        if (!data.animates(which)) {
            sb.append(String.format(java.util.Locale.ROOT, "  %-5s (no animado por este emote)%n", label));
            return;
        }
        float[] rest = restOf(part);
        double distFromRest = Math.sqrt(
                Math.pow(part.originX - rest[0], 2) + Math.pow(part.originY - rest[1], 2) + Math.pow(part.originZ - rest[2], 2));
        boolean suspicious = distFromRest > 8.0 || Float.isNaN(part.originX) || Float.isNaN(part.originY)
                || Float.isNaN(part.originZ) || Float.isNaN(part.pitch) || Float.isNaN(part.yaw) || Float.isNaN(part.roll);
        sb.append(String.format(java.util.Locale.ROOT,
                "  %-5s rest=(%.2f,%.2f,%.2f) distFromRest=%.3f%s%n",
                label, rest[0], rest[1], rest[2], distFromRest,
                suspicious ? "  <-- SOSPECHOSO (lejos del reposo o NaN)" : ""));
        for (EmoteData.Axis axis : EmoteData.Axis.values()) {
            boolean enabled = data.hasChannel(which, axis);
            int count = data.keyframeCount(which, axis);
            float live = switch (axis) {
                case X -> part.originX; case Y -> part.originY; case Z -> part.originZ;
                case PITCH -> part.pitch; case YAW -> part.yaw; case ROLL -> part.roll;
            };
            sb.append(String.format(java.util.Locale.ROOT,
                    "        %-5s enabled=%-5s kfs=%-3d live=%8.4f default=%8.4f%n",
                    axis, enabled, count, live, EmoteData.defaultValue(which, axis)));
        }
    }

    // ---- Timing --------------------------------------------------------------------------------

    /** Raw wall-clock ticks since start (NOT loop-folded; starts at 0, so the [0→beginTick] ease-in
     *  window plays — the reference player also starts its tick counter at 0). */
    private static float rawTick(Playback p) {
        if (p.pinnedTick != null) return p.pinnedTick;
        return (System.currentTimeMillis() - p.startMs) / 50f;
    }

    /** Folds a raw tick into the loop window — the reference wraps PAST endTick back to returnTick
     *  with an INCLUSIVE span of {@code endTick − returnTick + 1} (KeyframeAnimationPlayer.tick). */
    private static float foldTick(EmoteData d, float t) {
        if (d.loop && t > d.endTick) {
            float span = d.endTick - d.returnTick + 1f;
            return d.returnTick + ((t - d.returnTick) % span);
        }
        return t;
    }

    private static boolean isFinished(Playback p) {
        if (p.pinnedTick != null) return false;   // caller-managed, never auto-pruned
        if (p.stopping) {
            return (System.currentTimeMillis() - p.stopStartMs) / 50f > CANCEL_FADE_TICKS + 1f;
        }
        if (p.data.loop) return false;
        return rawTick(p) > p.data.stopTick + 1f;
    }

    private static float lerp(float from, float to, float w) {
        return from + (to - from) * w;
    }
}
