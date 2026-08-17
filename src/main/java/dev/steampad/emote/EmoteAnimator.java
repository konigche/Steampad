package dev.steampad.emote;

import dev.steampad.emote.bend.BendableCuboid;
import dev.steampad.mixin.ModelPartCuboidsAccessor;
import dev.steampad.util.LogUtil;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

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
        /** True only for a LOCAL menu preview (Wheel/Library/Wheel-editor "focus" thumbnail) — never
         *  for a genuine network-synced remote dance (also stored via {@code playFor}, but with this
         *  false). D111: a GUI-preview-only playback must pose ONLY a render explicitly tagged as a
         *  GUI preview draw ({@link EmotePreviewTagger}) — never that entity's normal WORLD render —
         *  which is what let merely FOCUSING a wheel slot puppet the player's actual on-screen body.
         *  See {@link #apply} and {@link #computeBodyTransform}. */
        final boolean guiPreviewOnly;
        /** Non-null = a PINNED, non-advancing single frame (see {@link #applyPinnedFrame}) — never
         *  read the wall clock, never expires, never counts as "ownable" by a preview/real emote. */
        final Float pinnedTick;
        boolean stopping = false;   // eased stop requested (plays end→stop fade, then removes)
        long stopStartMs = 0L;
        float stopFromTick = 0f;
        boolean stopLoopStarted = false;   // loop-seam state frozen with the pose at stop time

        Playback(EmoteData data, boolean isRealEmote, boolean guiPreviewOnly) {
            this.data = data;
            this.isRealEmote = isRealEmote;
            this.guiPreviewOnly = guiPreviewOnly;
            this.pinnedTick = null;
            this.startMs = System.currentTimeMillis();
            this.generation = GENERATION_COUNTER.incrementAndGet();
        }

        Playback(EmoteData data, float pinnedTick) {
            this.data = data;
            this.isRealEmote = false;
            this.guiPreviewOnly = false;
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
     * wide/slim arms). Sourced from {@link ModelPart#getInitialPose()} — the BAKED, immutable
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
            var t = p.getInitialPose();
            return new float[]{
                    dev.steampad.compat.mc.ModelPartCompat.poseX(t),
                    dev.steampad.compat.mc.ModelPartCompat.poseY(t),
                    dev.steampad.compat.mc.ModelPartCompat.poseZ(t)};
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
    // THIRD_PERSON_BACK cut felt jarring. The actual ease lives in
    // {@link dev.steampad.input.PerspectiveTransition} since D123 (generalized so the manual gamepad
    // perspective cycle shares it); this class just triggers it at emote start/end and clears it on
    // hard disconnect. See that class's doc for the mechanics AND for the round-4 bug that motivated
    // the move (the ease was unreachable during real emotes — the one case it was built for).

    private EmoteAnimator() {}

    // ---- Control -----------------------------------------------------------------------------

    /** Starts an emote on the LOCAL player and announces it to the server (multiplayer sync). */
    public static void playLocal(String emoteId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        EmoteData data = EmoteLibrary.byId(emoteId);
        if (data == null) {
            LogUtil.warn("[SteamPad] Emote '{}' is not in the library — not playing.", emoteId);
            return;
        }
        playing.put(mc.player.getId(), new Playback(data, /* isRealEmote */ true, /* guiPreviewOnly */ false));
        EmoteNetworkClient.sendStart(emoteId);
        // First-person can't see your own body — hop to third person so the emote is visible (the
        // established behavior in this mod ecosystem). Only in gameplay; never fights a screen.
        // Remembers that WE made this switch so it can be undone once the emote ends (see
        // restorePerspectiveIfNeeded) without fighting a perspective the player picked themselves.
        if (mc.screen == null
                && mc.options.getCameraType() == net.minecraft.client.CameraType.FIRST_PERSON) {
            Vec3 eye = mc.player.getEyePosition();
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            autoSwitchedPerspective = true;
            // Eases the camera OUT from the eye instead of a hard cut (D084, generalized D123).
            dev.steampad.input.PerspectiveTransition.beginEnter(eye);
        }
        LogUtil.info("[SteamPad] Playing emote '{}' ({}).", data.name, emoteId);
    }

    /** Stops the LOCAL player's emote (eased) and announces the stop. */
    public static void stopLocal() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (requestStop(mc.player.getId())) EmoteNetworkClient.sendStop();
    }

    /** True while the local player has ANY playback running — a genuine dance OR a menu/wheel
     *  preview (used by the existing "conflictsWithRealEmote" ownership checks, which need to know
     *  "is the entity slot occupied at all", not specifically by what). */
    public static boolean isLocalPlaying() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && playing.containsKey(mc.player.getId());
    }

    /**
     * True while ANY playback is running for {@code entityId} — a real dance, another player's
     * network-synced dance, or a menu/wheel preview.
     *
     * <p>Exists for {@link dev.steampad.compat.EmfCompat}, which must answer "is SteamPad currently
     * posing this specific entity?" for an arbitrary entity, not just the local player. The broad
     * definition is the right one there: every one of those cases poses the model, so every one of
     * them needs a competing animation system to stand down.
     */
    public static boolean isPlayingFor(int entityId) {
        return playing.containsKey(entityId);
    }

    /** True only while the local player is running a GENUINE emote started via {@link #playLocal} —
     *  false while merely previewing in the Library/Wheel (v0.60.0/D100). Effects that must not
     *  engage just from browsing (e.g. forcing the free-look camera) should check this, not
     *  {@link #isLocalPlaying}. */
    public static boolean isLocalRealEmotePlaying() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        Playback p = playing.get(mc.player.getId());
        return p != null && p.isRealEmote;
    }

    /** Starts a PREVIEW on any entity id — a genuine network-synced REMOTE dance
     *  ({@link EmoteNetworkClient}). Never counts as a "real" emote, see
     *  {@link #isLocalRealEmotePlaying}, but (unlike {@link #playForGuiPreview}) is NOT
     *  GUI-preview-only: another player's dance must render everywhere they're visible, world
     *  included. */
    public static void playFor(int entityId, EmoteData data) {
        playing.put(entityId, new Playback(data, /* isRealEmote */ false, /* guiPreviewOnly */ false));
    }

    /** Starts a LOCAL menu preview (Wheel/Library/Wheel-editor "focus" thumbnail) on {@code entityId}
     *  — same as {@link #playFor} except the resulting pose only ever applies to a render explicitly
     *  tagged as a GUI preview draw (see {@link EmotePreviewTagger#beginLive}/{@code #begin}), never
     *  to that entity's normal WORLD render. D111: before this distinction existed, merely moving the
     *  stick to FOCUS a wheel slot (no confirmation) made the player's actual on-screen body perform
     *  the focused dance too, visible in third person — because both the world render and the GUI
     *  thumbnail read the SAME shared playback map keyed by entity id. */
    public static void playForGuiPreview(int entityId, EmoteData data) {
        playing.put(entityId, new Playback(data, /* isRealEmote */ false, /* guiPreviewOnly */ true));
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
     *  position (the completion logic needs a live {@code mc.player}, which a disconnect removes). */
    public static void clearAll() {
        playing.clear();
        dev.steampad.input.PerspectiveTransition.cancel();
        autoSwitchedPerspective = false;
    }

    /** True on the tick the local player's real emote was last observed playing — compared against
     *  the fresh read every tick to detect the start/stop transition below. Deliberately a plain field
     *  (not derived), since {@link #isLocalRealEmotePlaying} itself becomes false the instant the
     *  playback entry is pruned, and this needs to remember what it was the tick BEFORE that. */
    private static boolean wasRealEmotePlaying = false;
    /** The vanilla HUD-hidden state from the moment a real emote started, restored verbatim when it
     *  stops — see the transition block in {@link #clientTick} (point 3/D122). Snapshot-and-restore
     *  rather than a blind on/off so a player who had already manually hidden their HUD (F1) before
     *  starting a dance doesn't have it forced back to VISIBLE afterward. */
    private static boolean hudHiddenSnapshot = false;

    /**
     * Once per client tick from SteamPadClient: cancels the local emote when the player MOVES
     * (input or knockback), and prunes finished non-loop playbacks so the map never leaks.
     */
    public static void clientTick(Minecraft mc) {
        // Point 3/D122: emote start/stop transition. Hides the vanilla HUD for the dance's duration
        // (SteamPad's own button-hint row handles its own full-override separately, in
        // GameplayHudOverlay — this only owns vanilla's hotbar/health/hunger/XP/etc.) and resets the
        // manual D-pad zoom nudge, so it can never be left "stuck" applied afterward — point 3:
        // "siempre debe de regresar a su posicion". Placed BEFORE the playing.isEmpty() early return
        // below, and before anything else, so the stop half of the transition is still detected on
        // the exact tick a real emote finishes/gets cancelled/disconnects mid-dance — every one of
        // those paths ends the same way: isLocalRealEmotePlaying() flips to false, which is the ONLY
        // condition this depends on.
        boolean isRealEmotePlayingNow = isLocalRealEmotePlaying();
        if (isRealEmotePlayingNow && !wasRealEmotePlaying) {
            hudHiddenSnapshot = mc.options.hideGui;
            mc.options.hideGui = true;
        } else if (!isRealEmotePlayingNow && wasRealEmotePlaying) {
            mc.options.hideGui = hudHiddenSnapshot;
            dev.steampad.input.ThirdPersonCameraController.resetEmoteZoom();
        }
        wasRealEmotePlaying = isRealEmotePlayingNow;

        // (The transition's own completion — including the delayed 3rd→1st perspective flip — runs in
        // PerspectiveTransition.tick, registered right next to this method in SteamPadClient, D123.)
        if (playing.isEmpty()) return;
        if (mc.player != null) {
            Playback p = playing.get(mc.player.getId());
            if (p != null) {
                if (!p.stopping) {
                    var mv = mc.player.input != null ? mc.player.input.getMoveVector() : null;
                    boolean moving = (mv != null && mv.x * mv.x + mv.y * mv.y > 0.01f)
                            || mc.player.isSprinting()
                            || mc.player.getDeltaMovement().horizontalDistanceSqr() > 0.02;
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
     *  flip happens once the ease visually lands ({@code PerspectiveTransition.tick}), not here — so
     *  the camera keeps easing IN third-person space instead of jump-cutting the instant the emote
     *  ends. {@code autoSwitchedPerspective} clears immediately on hand-off: from that moment the
     *  restore is PerspectiveTransition's job, and this method must not re-trigger it next tick. */
    private static void restorePerspectiveIfNeeded(Minecraft mc) {
        if (!autoSwitchedPerspective || dev.steampad.input.PerspectiveTransition.isActive()) return;
        autoSwitchedPerspective = false;
        if (mc.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_BACK) {
            dev.steampad.input.PerspectiveTransition
                    .beginExitToFirstPerson(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        }
        // else: perspective already changed some other way (the player picked one) — nothing to do.
    }

    // ---- Render-side application (called from the PlayerEntityModel mixin, per frame) ---------

    /**
     * Applies the running emote (if any) for {@code entityId} onto an already-vanilla-posed biped
     * model. Channels present in the emote REPLACE the vanilla value for that axis; everything else
     * is left exactly as vanilla computed it.
     *
     * @param guiPass true when THIS specific render is a GUI preview draw (tagged via
     *                {@link EmotePreviewTagger}) rather than the entity's normal world render — see
     *                {@link Playback#guiPreviewOnly} (D111).
     */
    public static void apply(HumanoidModel<?> model, int entityId, boolean guiPass) {
        // Origin/rotation already reset to baked rest by resetToBakedRest(), called from the
        // mixin's HEAD injection before vanilla's own setAngles logic ran this frame (D109) — vanilla
        // has since freely overwritten whatever it manages on top of that clean baseline.
        Playback p = playing.get(entityId);
        if (p == null || (p.guiPreviewOnly && !guiPass)) {
            // Not emoting (or a LOCAL menu preview trying to leak into a non-GUI/world render, D111)
            // — but the SAME shared model instance (see restOrigin's own doc) may still hold bend
            // state a DIFFERENT entity's emote left active on a prior render this frame; must clear
            // it every time, not just when an emote ends (v0.63.0/D103).
            clearAllBend(model);
            return;
        }

        float rawTick = rawTick(p);
        boolean loopStarted = p.data.loop && rawTick > p.data.endTick;
        float tick = foldTick(p.data, rawTick);
        float weight = 1f;   // 1 = full emote pose; <1 = blending back to vanilla (user-cancel fade)

        if (p.stopping) {
            float fade = (System.currentTimeMillis() - p.stopStartMs) / 50f;
            weight = 1f - Math.min(1f, fade / CANCEL_FADE_TICKS);
            tick = p.stopFromTick;             // freeze the pose while fading it out
            loopStarted = p.stopLoopStarted;
            if (weight <= 0f) { clearAllBend(model); return; }   // fully faded — clientTick prunes it
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
        applyBendForAllParts(model, p.data, tick, loopStarted, weight);

        debugDumpPose(entityId, p, tick, weight, model);
    }

    /**
     * Poses the model to a FIXED frame of {@code data} — the per-cell static thumbnail path (render
     * states duck-tagged at queue time, see {@link EmotePreviewState}). Bypasses the playback map
     * entirely, so any number of DIFFERENTLY-posed thumbnails of the same player can be queued in a
     * single frame — the thing the map-keyed path structurally cannot do (D092/D099).
     */
    public static void applyStatic(HumanoidModel<?> model, EmoteData data, float tick) {
        // Same reasoning as apply() — resetToBakedRest() already ran from the mixin's HEAD injection.
        applyPart(model.head, data, EmoteData.Part.HEAD, tick, false, 1f);
        applyPart(model.body, data, EmoteData.Part.TORSO, tick, false, 1f);
        applyPart(model.rightArm, data, EmoteData.Part.RIGHT_ARM, tick, false, 1f);
        applyPart(model.leftArm, data, EmoteData.Part.LEFT_ARM, tick, false, 1f);
        applyPart(model.rightLeg, data, EmoteData.Part.RIGHT_LEG, tick, false, 1f);
        applyPart(model.leftLeg, data, EmoteData.Part.LEFT_LEG, tick, false, 1f);
        applyBendForAllParts(model, data, tick, false, 1f);
    }

    /**
     * Re-syncs the six second-layer "skin overlay" parts (hat, jacket, sleeves, pants) to their base
     * bone AFTER an emote has overridden it. Corrects a real, previously-reported bug: skin overlays
     * visibly lagging/detaching from the body during a fast emote (user-facing: "la barba/el cabello
     * no sigue la cabeza", "la ropa no está sincronizada").
     *
     * <p><b>This is needed on some versions and is a genuine no-op on others — the model tree itself
     * changed shape.</b> Verified with {@code javap} on the real mapped jars of 1.21.1, 1.21.3,
     * 1.21.4, 1.21.5, 1.21.8 and 1.21.10, comparing which receiver {@code PlayerModel}'s constructor
     * calls {@code getChild} on (that call is a flat, non-recursive lookup of a part's OWN children,
     * so the receiver settles the tree shape):
     * <ul>
     *   <li><b>1.21.1 — flat siblings.</b> All six overlay parts come from {@code root.getChild(name)},
     *       the same receiver as {@code head}/{@code body}/the limbs. They therefore do NOT inherit
     *       their base bone's pose, and vanilla compensates by calling {@code ModelPart.copyFrom} for
     *       each pair every frame inside {@code setupAnim}.</li>
     *   <li><b>1.21.3 and later — real nested children.</b> They come from
     *       {@code leftArm.getChild("left_sleeve")}, {@code body.getChild("jacket")},
     *       {@code head.getChild(...)} and so on, so pose inheritance is automatic. Consistently,
     *       {@code copyFrom} no longer exists at all on {@code ModelPart}, and {@code setupAnim} only
     *       sets {@code visible} flags on these parts — it never copies a pose.</li>
     * </ul>
     *
     * <p>Where the sync IS required (the flat-sibling versions), the bug it fixes is one of ordering:
     * vanilla's own {@code copyFrom} runs inside {@code setupAnim}, i.e. between this mixin's HEAD
     * reset and its TAIL {@link #apply}/{@link #applyStatic} call, so it copies the PRE-emote pose.
     * Once TAIL overrides head/body/arms/legs, the overlay keeps showing that stale copy for the rest
     * of the frame unless the same sync runs again afterwards — visible as skin overlays lagging or
     * detaching during a fast emote. Running it unconditionally mirrors vanilla's own unconditional
     * behaviour: with nothing emoting, TAIL never touched the base bones, so this re-copies values
     * that are already correct — harmless, not a case that needs gating.
     *
     * <p>Separate method (not folded into {@link #apply}/{@link #applyStatic}) on purpose: those stay
     * typed {@code HumanoidModel<?>} for testability — {@code EmoteAnimatorTest} builds a bare
     * {@code HumanoidModel} with no overlay fields at all. Only {@code PlayerModel} (the in-game
     * player render path) has them.
     *
     * <p><b>On the history:</b> D111 added this sync, D112 removed it claiming the parts were nested
     * children, and D143 restored it claiming D112 was simply wrong. The evidence above says both
     * halves were right about their own version and neither noticed the boundary — D112's reasoning
     * describes the tree that 1.21.3+ actually has, while D111/D143's describes 1.21.1's. See
     * DECISIONS.md for the full correction.
     *
     * <p>The parameter is deliberately the RAW {@code PlayerModel}: the class is generic
     * ({@code PlayerModel<T extends LivingEntity>}) before the entity-render-state rework and
     * non-generic after it, so a wildcard would compile on one version and fail on the other. The raw
     * type is valid on both and costs nothing here — every field this touches is a plain
     * {@code ModelPart}, with no type parameter involved.
     */
    @SuppressWarnings("rawtypes")
    public static void syncOverlayLayers(PlayerModel model) {
        //? if >=1.21.2 {
        // Nested children — the tree propagates the pose already. Deliberately empty rather than
        // absent so both branches keep one call site (and so this documentation stays attached to it).
        //?} else {
        /*model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        *///?}
    }

    // ---- Whole-model transform (D110) — the "body" channel ---------------------------------------
    //
    // Faithful port of the reference's PlayerRendererMixin.applyBodyTransforms (MIT, with
    // attribution): the "body" channel is NOT a bone, it is a transform on the render matrix stack
    // for the ENTIRE player — position in BLOCK units, rotation about a point ~0.7 blocks up (body
    // centre, not the feet), and scale. This is the only mechanism that can physically drop a player
    // onto the floor to sit, or tip the whole body horizontal to crawl; a torso-bone rotation
    // structurally cannot, which is exactly why sitting emotes floated in the air until now.

    /** One frame's whole-model transform: translation in BLOCKS, rotation in radians, scale factor.
     *  {@code rot} is (pitch, yaw, roll) — applied Z, then Y, then X, the reference's own order. */
    public record BodyTransform(float x, float y, float z,
                                float pitch, float yaw, float roll,
                                float scaleX, float scaleY, float scaleZ) {}

    /**
     * The whole-model transform for {@code entityId} this frame, or {@code null} when nothing is
     * playing, the emote never animates the body channel at all (the overwhelmingly common case —
     * one map lookup and out, so the render hook stays free for every non-emoting player), or the
     * only thing playing is a LOCAL menu preview and this particular render isn't a tagged GUI draw
     * (D111 — see {@link Playback#guiPreviewOnly} and {@link #apply}).
     */
    public static BodyTransform computeBodyTransform(int entityId, boolean guiPass) {
        Playback p = playing.get(entityId);
        if (p == null || (p.guiPreviewOnly && !guiPass) || !p.data.animates(EmoteData.Part.BODY)) return null;

        float rawTick = rawTick(p);
        boolean loopStarted = p.data.loop && rawTick > p.data.endTick;
        float tick = foldTick(p.data, rawTick);
        float weight = 1f;
        if (p.stopping) {
            float fade = (System.currentTimeMillis() - p.stopStartMs) / 50f;
            weight = 1f - Math.min(1f, fade / CANCEL_FADE_TICKS);
            tick = p.stopFromTick;
            loopStarted = p.stopLoopStarted;
            if (weight <= 0f) return null;
        }
        return sampleBodyTransform(p.data, tick, loopStarted, weight);
    }

    /** Same as {@link #computeBodyTransform} but for a pinned, non-advancing preview frame — keeps
     *  GUI thumbnails consistent with how the same emote looks in the world. */
    public static BodyTransform computeStaticBodyTransform(EmoteData data, float tick) {
        if (!data.animates(EmoteData.Part.BODY)) return null;
        return sampleBodyTransform(data, tick, false, 1f);
    }

    private static BodyTransform sampleBodyTransform(EmoteData data, float tick, boolean loopStarted,
                                                     float weight) {
        EmoteData.Part b = EmoteData.Part.BODY;
        // Rest for every body-channel axis is the identity (0 offset / 0 rotation), so a partially
        // faded emote lerps toward "no whole-model transform at all" — the same thing weight<1 means
        // for every bone.
        return new BodyTransform(
                lerp(0f, data.sampleAxis(b, EmoteData.Axis.X, tick, loopStarted, 0f), weight),
                lerp(0f, data.sampleAxis(b, EmoteData.Axis.Y, tick, loopStarted, 0f), weight),
                lerp(0f, data.sampleAxis(b, EmoteData.Axis.Z, tick, loopStarted, 0f), weight),
                lerp(0f, data.sampleAxis(b, EmoteData.Axis.PITCH, tick, loopStarted, 0f), weight),
                lerp(0f, data.sampleAxis(b, EmoteData.Axis.YAW, tick, loopStarted, 0f), weight),
                lerp(0f, data.sampleAxis(b, EmoteData.Axis.ROLL, tick, loopStarted, 0f), weight),
                1f, 1f, 1f);   // scale channels are parsed but not yet driven — see D110
    }

    /**
     * D109: faithful port of the real reference's OWN two-injection-point mechanism (verified in
     * {@code PlayerModelMixin.java}, KosmX/minecraftPlayerAnimator, MIT — ported with attribution,
     * same standing as {@link EmoteData}'s sampler) instead of D108's narrower guess at "which fields
     * are probably safe to touch from TAIL". The reference resets EVERY part's origin/rotation to its
     * baked rest, UNCONDITIONALLY, at the very {@code HEAD} of its pose method — {@code before}
     * vanilla's own per-frame logic (walk cycle, sneaking, riding) runs at all. Called from
     * {@link dev.steampad.mixin.PlayerEntityModelMixin}'s HEAD injection on {@code setAngles}; this
     * class's existing TAIL injection ({@link #apply}/{@link #applyStatic}) still applies the emote
     * afterward, same as before — the reference does the same two-step split (reset first, apply
     * later in the same method).
     *
     * <p>D108 (this method's predecessor) could only reset the handful of fields it could PROVE
     * vanilla never touches under any circumstance, because it ran at TAIL, after vanilla's own
     * conditional logic (sneaking sets {@code body.pitch}; several parts' originY) had already
     * executed — resetting those specific fields from TAIL would have fought a legitimate
     * current-frame vanilla pose instead of clearing a stale one. Resetting FIRST, before vanilla runs
     * at all, removes that whole class of risk: vanilla's own subsequent logic (which still runs in
     * full, this is an {@code @Inject}, not an {@code @Overwrite}) freely overwrites whatever it
     * normally manages on top of a clean baseline, exactly like it would on an entity that never had
     * any SteamPad code touch it. Fields vanilla never touches simply keep this method's reset value
     * all the way to TAIL — closing the {@code body.pitch}/conditional-originY gap D108 left open,
     * with no need to enumerate vanilla's exact conditional behavior at all.
     */
    public static void resetToBakedRest(HumanoidModel<?> model) {
        resetPartToRest(model.head);
        resetPartToRest(model.body);
        resetPartToRest(model.rightArm);
        resetPartToRest(model.leftArm);
        resetPartToRest(model.rightLeg);
        resetPartToRest(model.leftLeg);
    }

    private static void resetPartToRest(ModelPart part) {
        float[] rest = restOf(part);
        part.x = rest[0];
        part.y = rest[1];
        part.z = rest[2];
        part.xRot = 0f;
        part.yRot = 0f;
        part.zRot = 0f;
    }

    // ---- Bend (v0.63.0/D103) — Emotecraft's bendDir/bend channels, the mid-cuboid flex used for
    // sitting/crouching poses. See dev.steampad.emote.bend.CuboidBender for the actual per-vertex
    // math (ported from KosmX/bendy-lib, MIT, with attribution — this file only decides WHICH parts
    // bend and samples the two channels, matching CLAUDE.md's "mixins/hooks stay thin" spirit even
    // though this isn't a mixin itself). ------------------------------------------------------------

    /** Which end of each bendable part is the fixed "base" — matches the real reference's own
     *  registration exactly (KosmX/minecraftPlayerAnimator's BipedEntityModelMixin): torso folds at
     *  the waist (DOWN, base=near the neck), arms/legs fold at the elbow/knee (UP, base=shoulder/hip).
     *  Head is intentionally absent — not bendable in the reference either. */
    private static final Map<EmoteData.Part, Direction> BEND_DIRECTIONS = Map.of(
            EmoteData.Part.TORSO, Direction.DOWN,
            EmoteData.Part.RIGHT_ARM, Direction.UP,
            EmoteData.Part.LEFT_ARM, Direction.UP,
            EmoteData.Part.RIGHT_LEG, Direction.UP,
            EmoteData.Part.LEFT_LEG, Direction.UP);

    private static void applyBendForAllParts(HumanoidModel<?> model, EmoteData data, float tick,
                                             boolean loopStarted, float weight) {
        applyBend(model.body, EmoteData.Part.TORSO, data, tick, loopStarted, weight);
        applyBend(model.rightArm, EmoteData.Part.RIGHT_ARM, data, tick, loopStarted, weight);
        applyBend(model.leftArm, EmoteData.Part.LEFT_ARM, data, tick, loopStarted, weight);
        applyBend(model.rightLeg, EmoteData.Part.RIGHT_LEG, data, tick, loopStarted, weight);
        applyBend(model.leftLeg, EmoteData.Part.LEFT_LEG, data, tick, loopStarted, weight);
    }

    private static void applyBend(ModelPart part, EmoteData.Part which, EmoteData data,
                                  float tick, boolean loopStarted, float weight) {
        Direction direction = BEND_DIRECTIONS.get(which);
        if (direction == null) return;   // HEAD — not bendable, matches the reference
        boolean bending = data.hasChannel(which, EmoteData.Axis.BEND_AMOUNT)
                || data.hasChannel(which, EmoteData.Axis.BEND_DIRECTION);
        List<ModelPart.Cube> cuboids = ((ModelPartCuboidsAccessor) (Object) part).steampad$getCuboids();
        if (cuboids == null) return;
        if (!bending) {
            for (ModelPart.Cube c : cuboids) ((BendableCuboid) c).steampad$clearBend();
            return;
        }
        // No vanilla equivalent exists for "current bend" (vanilla never bends anything) — 0f is both
        // the axis's own default AND the only sane value to ease from/to before beginTick and after
        // endTick, so the fold smoothly grows in and relaxes out exactly like every other axis.
        float axisAngle = data.sampleAxis(which, EmoteData.Axis.BEND_DIRECTION, tick, loopStarted, 0f);
        float amount = data.sampleAxis(which, EmoteData.Axis.BEND_AMOUNT, tick, loopStarted, 0f) * weight;
        for (ModelPart.Cube c : cuboids) {
            ((BendableCuboid) c).steampad$setBend(direction, axisAngle, amount);
        }
    }

    private static void clearAllBend(HumanoidModel<?> model) {
        clearBendOnPart(model.body);
        clearBendOnPart(model.rightArm);
        clearBendOnPart(model.leftArm);
        clearBendOnPart(model.rightLeg);
        clearBendOnPart(model.leftLeg);
    }

    private static void clearBendOnPart(ModelPart part) {
        List<ModelPart.Cube> cuboids = ((ModelPartCuboidsAccessor) (Object) part).steampad$getCuboids();
        if (cuboids == null) return;
        for (ModelPart.Cube c : cuboids) ((BendableCuboid) c).steampad$clearBend();
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
        part.x = lerp(part.x,
                data.sampleAxis(which, EmoteData.Axis.X, tick, loopStarted, part.x), weight);
        part.y = lerp(part.y,
                data.sampleAxis(which, EmoteData.Axis.Y, tick, loopStarted, part.y), weight);
        part.z = lerp(part.z,
                data.sampleAxis(which, EmoteData.Axis.Z, tick, loopStarted, part.z), weight);
        part.xRot = lerp(part.xRot,
                data.sampleAxis(which, EmoteData.Axis.PITCH, tick, loopStarted, part.xRot), weight);
        part.yRot = lerp(part.yRot,
                data.sampleAxis(which, EmoteData.Axis.YAW, tick, loopStarted, part.yRot), weight);
        part.zRot = lerp(part.zRot,
                data.sampleAxis(which, EmoteData.Axis.ROLL, tick, loopStarted, part.zRot), weight);
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
                                      HumanoidModel<?> model) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(java.util.Locale.ROOT,
                "id='%s' real=%s guiPreviewOnly=%s tick=%.2f weight=%.2f loop=%s begin=%.1f end=%.1f stop=%.1f return=%.1f easingBefore=%s usesBend=%s%s%n",
                p.data.id, p.isRealEmote, p.guiPreviewOnly, tick, weight, p.data.loop, p.data.beginTick, p.data.endTick,
                p.data.stopTick, p.data.returnTick, p.data.easingBefore, p.data.usesBend,
                p.data.usesBend ? " (torso/limb flex rendered via CuboidBender — see D104/D105)" : ""));
        dumpPart(sb, "TORSO", EmoteData.Part.TORSO, p.data, model.body);
        dumpPart(sb, "HEAD", EmoteData.Part.HEAD, p.data, model.head);
        dumpPart(sb, "R_ARM", EmoteData.Part.RIGHT_ARM, p.data, model.rightArm);
        dumpPart(sb, "L_ARM", EmoteData.Part.LEFT_ARM, p.data, model.leftArm);
        dumpPart(sb, "R_LEG", EmoteData.Part.RIGHT_LEG, p.data, model.rightLeg);
        dumpPart(sb, "L_LEG", EmoteData.Part.LEFT_LEG, p.data, model.leftLeg);
        String summary = sb.toString();

        Minecraft mc = Minecraft.getInstance();
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
                Math.pow(part.x - rest[0], 2) + Math.pow(part.y - rest[1], 2) + Math.pow(part.z - rest[2], 2));
        boolean suspicious = distFromRest > 8.0 || Float.isNaN(part.x) || Float.isNaN(part.y)
                || Float.isNaN(part.z) || Float.isNaN(part.xRot) || Float.isNaN(part.yRot) || Float.isNaN(part.zRot);
        sb.append(String.format(java.util.Locale.ROOT,
                "  %-5s rest=(%.2f,%.2f,%.2f) distFromRest=%.3f%s%n",
                label, rest[0], rest[1], rest[2], distFromRest,
                suspicious ? "  <-- SOSPECHOSO (lejos del reposo o NaN)" : ""));
        for (EmoteData.Axis axis : EmoteData.Axis.values()) {
            boolean enabled = data.hasChannel(which, axis);
            int count = data.keyframeCount(which, axis);
            // BEND_DIRECTION/BEND_AMOUNT have no ModelPart field to read "live" from — that state
            // lives on the part's Cuboid(s) via BendableCuboidMixin, not on ModelPart itself; kfs/
            // enabled/default above already tell the useful story for this diagnostic.
            float live = switch (axis) {
                case X -> part.x; case Y -> part.y; case Z -> part.z;
                case PITCH -> part.xRot; case YAW -> part.yRot; case ROLL -> part.zRot;
                case BEND_DIRECTION, BEND_AMOUNT -> Float.NaN;
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
