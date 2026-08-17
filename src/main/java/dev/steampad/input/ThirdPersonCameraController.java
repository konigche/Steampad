package dev.steampad.input;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.GlobalConfig;
import dev.steampad.emote.EmoteAnimator;
import dev.steampad.util.LogUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import dev.steampad.compat.mc.UseAnimCompat;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Over-the-shoulder camera offset for third person â€” design credited to Leawind's
 * <a href="https://github.com/Leawind/Third-Person">Third-Person</a> mod (MIT license): its "quick
 * camera positioning" (toggle left/center/right), aiming-mode camera profile (closer/more centered
 * while charging a ranged weapon), and halflife-smoothed transitions are the pieces this reproduces
 * natively.
 *
 * <p>This is a deliberately narrow reimplementation, not a line-for-line port. The original mod
 * cancels vanilla's entire {@code Camera#setup} and replaces it with its own camera that ALSO
 * decouples camera rotation from the player's body rotation (5 configurable "player rotate modes" â€”
 * free-look, essentially). That piece touches the single most regression-prone system in this
 * project's own history (STATE.md's long run of camera bugs, D046-D053) at its input-coupling root,
 * not just its rendering â€” it would mean overriding how look input turns the player entity itself
 * everywhere that currently assumes camera-yaw == player-yaw ({@link CameraController}, aim assist,
 * zoom, movement direction). Deliberately NOT attempted here â€” see DECISIONS.md for the honest scope
 * note and why it needs its own dedicated session with real hardware checkpoints, not a fold-in.
 *
 * <p>Everything else is safe to reproduce: this hooks the END of {@code Camera#update} (a TAIL
 * inject â€” see {@code dev.steampad.mixin.ThirdPersonCameraMixin}) and nudges the ALREADY
 * vanilla-computed, already collision-clipped position sideways along the camera's own right vector,
 * with the offset amount halflife-smoothed toward a target that depends on side (L/C/R) and whether
 * the player is currently aiming a ranged weapon.
 */
public final class ThirdPersonCameraController {

    private ThirdPersonCameraController() {}

    /** Halflife (seconds) for the smoothed offset amount â€” same "critically-damped exponential
     *  toward target" shape the reference mod uses throughout (ExpSmoothDouble), reimplemented from
     *  the well-documented public technique, not copied code: {@code factor = 1 - 2^(-dt/halflife)}. */
    private static final double SMOOTH_HALFLIFE_SEC = 0.15;

    private static double smoothedAmount = 0.0;
    private static long lastFrameNanos = 0L;

    /** Cycles LEFT â†’ CENTER â†’ RIGHT â†’ LEFT (Botones-bindable action, mirrors the original mod's
     *  CapsLock quick-toggle but through SteamPad's own action system instead of a keyboard key). */
    public static void cycleSide() {
        GlobalConfig g = ConfigManager.getGlobal();
        g.thirdPersonCameraSide = switch (g.thirdPersonCameraSide) {
            case LEFT -> GlobalConfig.ThirdPersonCameraSide.CENTER;
            case CENTER -> GlobalConfig.ThirdPersonCameraSide.RIGHT;
            case RIGHT -> GlobalConfig.ThirdPersonCameraSide.LEFT;
        };
        ConfigManager.saveGlobal();
    }

    /** True while the player is charging a bow/crossbow/trident â€” the same UseAction check
     *  {@link AimAssistController} already uses, reused here for the aiming camera profile. */
    private static boolean isAiming(Minecraft mc) {
        if (mc.player == null || !mc.player.isUsingItem()) return false;
        return UseAnimCompat.isRangedAim(mc.player.getUseItem());
    }

    /**
     * The world-space delta to ADD to the vanilla third-person camera position this frame, or
     * {@link Vec3#ZERO} when the feature is off, centered, or the focused entity isn't the player.
     *
     * @param camera        the camera, already fully positioned/rotated by vanilla this call
     * @param focusedEntity vanilla's own {@code update()} parameter â€” only offset when it's the
     *                      local player's own camera (never in spectator-of-another-entity, etc.)
     */
    public static Vec3 computeOffset(Minecraft mc, Camera camera, Entity focusedEntity) {
        if (!camera.isDetached() || focusedEntity == null) return Vec3.ZERO;
        if (mc.player == null || focusedEntity != mc.player) return Vec3.ZERO;

        GlobalConfig g = ConfigManager.getGlobal();
        if (!g.thirdPersonCameraEnabled) return Vec3.ZERO;

        // Target offset magnitude for this frame: 0 while centered, otherwise the configured amount â€”
        // eased down toward the closer aiming profile while charging a ranged weapon.
        double target = g.thirdPersonCameraSide == GlobalConfig.ThirdPersonCameraSide.CENTER
                ? 0.0 : Math.max(0f, Math.min(1.5f, g.thirdPersonCameraOffset));
        if (g.thirdPersonAimingProfile && isAiming(mc) && target != 0.0) {
            target = Math.min(target, Math.max(0f, Math.min(1.5f, g.thirdPersonAimingOffset)));
        }
        if (g.thirdPersonCameraSide == GlobalConfig.ThirdPersonCameraSide.LEFT) target = -target;

        smoothedAmount = smoothTowards(smoothedAmount, target);
        double amount = smoothedAmount;
        if (Math.abs(amount) < 0.001) return Vec3.ZERO;

        // Camera-right vector in the horizontal plane (yaw only â€” the shoulder offset stays level
        // regardless of pitch). See rightVectorXZ() for the handedness fix (D093/tp-3).
        double[] r = rightVectorXZ(camera.getYRot());
        double rightX = r[0], rightZ = r[1];

        Vec3 base = camera.getPosition();
        Vec3 end = base.add(rightX * amount, 0, rightZ * amount);

        if (mc.level == null) return end.subtract(base);
        BlockHitResult hit = mc.level.clip(new ClipContext(base, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.MISS) return end.subtract(base);

        // Something's in the way â€” clamp the offset to just short of the hit, never past it.
        double safeDist = Math.max(0.0, base.distanceTo(hit.getLocation()) - 0.15);
        double ratio = safeDist / Math.abs(amount);
        return end.subtract(base).scale(ratio);
    }

    /** Critically-damped exponential ease of {@code smoothedAmount} toward {@code target}, at
     *  {@link #SMOOTH_HALFLIFE_SEC} â€” frame-rate independent (uses real elapsed time, clamped against
     *  hitches/pauses the same way {@link CameraController} already does). */
    private static double smoothTowards(double current, double target) {
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0L ? (1.0 / 60.0) : (now - lastFrameNanos) / 1_000_000_000.0;
        lastFrameNanos = now;
        if (dt <= 0 || dt > 0.25) dt = 1.0 / 60.0;
        double factor = 1.0 - Math.pow(2.0, -dt / SMOOTH_HALFLIFE_SEC);
        return current + (target - current) * factor;
    }

    /**
     * The camera's own world-space RIGHT vector in the horizontal plane (yaw only, level regardless
     * of pitch), as {x, z}. Shared by {@link #computeOffset} and {@link #computeFreePose} â€” both
     * offset the camera sideways along this exact vector, so a handedness bug in one is a handedness
     * bug in both.
     *
     * <p><b>Fixed in v0.56.0 (D093) after real-hardware testing showed "Izquierda" visually moving the
     * camera to the RIGHT (checklist tp-3):</b> the previous formula, {@code (cos(yaw), sin(yaw))}, is
     * actually the camera's LEFT vector, not its right â€” confirmed three independent ways: (1) at
     * yaw=0 (facing south, vanilla convention) a player's true right hand points west (-X), but
     * {@code (cos 0, sin 0) = (1, 0)} = east; (2) at yaw=90 (facing west) true right points north
     * (-Z), but the old formula gives south (0, 1); (3) the standard right-hand-rule camera basis
     * {@code right = forward Ã— up} â€” with {@code forward = (-sin(yaw), 0, cos(yaw))} (this class's own
     * {@link #directionFromYawPitch} at pitch 0) and {@code up = (0, 1, 0)} â€” works out to
     * {@code (-cos(yaw), 0, -sin(yaw))}, the exact negation of the old formula. All three agree, so
     * the fix is a plain sign flip, not a different formula.
     *
     * <p>This is UNRELATED to (and does not affect) {@link #applyCameraRelativeMovement}'s own use of
     * {@code (cos(yaw), sin(yaw))} for the sideways movement AXIS â€” that formula is verified
     * byte-for-byte against vanilla's own {@code Entity.movementInputToVelocity}, which apparently
     * treats vanilla's own "positive sideways input" as the LEFT direction. Two different questions
     * ("where is the camera's right shoulder" vs. "which world direction does vanilla's own sideways
     * input axis point") that happen to share a formula shape but need opposite signs â€” touching one
     * must never be assumed to fix or break the other.
     */
    private static double[] rightVectorXZ(double yawDeg) {
        double yawRad = Math.toRadians(yawDeg);
        return new double[]{-Math.cos(yawRad), -Math.sin(yawRad)};
    }

    // ================================================================================================
    // FREE CAMERA â€” the rest of Leawind's Third-Person feature set: free rotation decoupled from the
    // player's body, a functional/predictive crosshair, and live offset/distance adjustment. Fully
    // independent of the offset-only path above; {@code ThirdPersonCameraMixin} picks ONE path per
    // frame from {@code GlobalConfig.thirdPersonFreeLookEnabled} â€” the offset-only path is completely
    // unchanged when this is off, zero regression risk for anyone who already validated it.
    //
    // REWRITTEN in v0.76.0 (D116) as a real port of the reference's camera pipeline, after hardware
    // testing reported "no lag at all while orbiting, terrible lag as soon as I walk". Five concrete
    // divergences from the reference were found, each with its own visible symptom:
    //
    //  1. THE WALKING STUTTER. The orbit point was read as mc.player.getEyePos() â€” the raw,
    //     tick-quantised eye position â€” inside per-frame camera math. That value moves 20 times a
    //     second while the game draws 60-144 frames a second, so the camera advanced in visible
    //     steps. Standing still it isn't changing at all, hence "smooth when I only rotate". The
    //     reference splits this in two: a smoothed follow point advanced once per client tick, read
    //     back interpolated by tickDelta each frame. Both halves are now ported â€” see clientTick()
    //     and SmoothValue. This also explains why previous perf profiling never found anything: the
    //     per-frame work was never slow, it was reading data that only existed at 20 Hz.
    //  2. The offset was a world-space nudge in blocks; the reference projects a screen-space RATIO
    //     through the real rendered FOV and window aspect, so the player holds their place on screen
    //     instead of drifting whenever the FOV changes (sprinting, speed effect, drawing a bow).
    //  3. No pitch squeeze, so looking near-straight-up/down aimed past the block you meant to hit.
    //  4. Wall clipping cast ONE ray from the centre, which slips through corners and thin walls; the
    //     reference casts 8 from the corners of a small cube and keeps the nearest hit.
    //  5. The crosshair pick measured vanilla reach FROM THE CAMERA, which sits behind the player â€”
    //     silently losing roughly the whole camera distance (~4 blocks at default) of effective
    //     reach. A large part of why "mira funcional" felt like it was missing things.
    //
    // Still deliberately NOT ported (unchanged decisions, see DECISIONS.md/TODO_BLOCKERS.md):
    //  - The body-facing "rotate strategy" only acts while the player is IDLE (stick released).
    //  - Player transparency (cosmetic, off by default even upstream).
    // ================================================================================================

    private static final double PITCH_LIMIT = 89.8;
    private static final double MIN_DISTANCE = 0.3;
    private static final double MAX_DISTANCE = 6.0;
    private static final double BODY_ROTATE_HALFLIFE_SEC = 0.12;
    /** Half-angle (degrees) of the cone used to find a predicted ranged-weapon target â€” matches the
     *  reference mod's own tight prediction cone. */
    private static final double PREDICT_CONE_DEG = 12.0;

    // ---- Ported constants (Leawind/Third-Person, MIT â€” ThirdPersonConstants + AbstractConfig) -----
    /** One vanilla client tick, in seconds â€” the period the rotate centre advances by. */
    private static final double CLIENT_TICK_SEC = 0.05;
    /** The orbit point sits this far ABOVE the eyes, so the camera frames the head rather than
     *  staring at eye level (reference {@code rotate_center_height_offset}). */
    private static final double ROTATE_CENTER_HEIGHT_OFFSET = 0.3;
    /** The follow halflife is scaled by {@code sqrt(distance) * this} â€” a camera further out is
     *  allowed to lag further behind, which is what keeps a distant camera from feeling twitchy
     *  (reference {@code EYE_HALFLIFE_MULTIPLIER}). */
    private static final double EYE_HALFLIFE_MULTIPLIER = 0.1;
    private static final double NORMAL_HALFLIFE_HORIZON = 0.25;
    private static final double NORMAL_HALFLIFE_VERTICAL = 0.20;
    private static final double AIMING_HALFLIFE = 0.05;
    private static final double NORMAL_OFFSET_HALFLIFE = 0.08;
    /** D122: tightened from 0.03 (feedback: "quiero que sea mÃ¡s rÃ¡pido el posicionamiento") â€” the
     *  aim-in snap reads noticeably crisper at 0.02 without overshoot/jitter (still well above the
     *  per-frame dt at any realistic framerate). */
    private static final double AIMING_OFFSET_HALFLIFE = 0.02;
    private static final double NORMAL_DISTANCE_HALFLIFE = 0.72;
    /** D122: tightened from 0.04, same reasoning as {@link #AIMING_OFFSET_HALFLIFE}. */
    private static final double AIMING_DISTANCE_HALFLIFE = 0.025;
    /** D123: the emote zoom gets its OWN constants â€” the hardware-confirmed v0.78.0 values. D120
     *  had emotes SHARE the aiming halflifes ("already fast, already proven"), which silently broke
     *  the moment D122 tightened aiming for the shoulder cam: at 0.025s the emote's 0.8-block ease
     *  completes in ~0.1s â€” visually a cut, the round-4 "el efecto ya no esta". Lesson: a constant
     *  serving two features WILL eventually be retuned for one of them; the emote feel the user
     *  signed off on deserves its own numbers. */
    private static final double EMOTE_OFFSET_HALFLIFE = 0.03;
    private static final double EMOTE_DISTANCE_HALFLIFE = 0.04;
    /** D123 mirror mode: how quickly the front-view camera swings back around to face the player's
     *  front after the body turns. Vanilla's front view is a hard per-frame lock (the reported
     *  "como que esta bloqueada"); this trails it smoothly instead â€” same value as the legacy
     *  offset smoothing (SMOOTH_HALFLIFE_SEC), a feel the user has long since signed off on. */
    private static final double MIRROR_FOLLOW_HALFLIFE = 0.15;
    /** D171: how quickly the mounted chase camera catches up to directly behind the mount after it
     *  turns. Own constant, not shared with MIRROR_FOLLOW_HALFLIFE (D122 lesson: a halflife serving
     *  two features WILL get retuned for one and silently break the other's feel) â€” slower than
     *  mirror's 0.15s on purpose, so a quick turn leaves visible "weight" behind it (the AAA
     *  vehicle-camera feel: "similar a como manejas un coche... RDR2, Crimson Desert") instead of
     *  snapping back instantly. Not yet hardware-validated; easy to retune if it feels sluggish. */
    private static final double MOUNTED_CHASE_HALFLIFE = 0.3;
    /** D171 round 2: how far past center (0..1) the right stick must be pushed while mounted before
     *  it counts as a deliberate "look around" gesture instead of an ordinary steering nudge.
     *  Magnitude-gated on purpose, not duration-gated: this project's own look-based turning has no
     *  "instant snap" (see CameraController's own power curve), so any real steering turn already
     *  requires holding the stick for a while at whatever deflection the player chooses — gating on
     *  DURATION would silently hand a long, ordinary turn off to the free camera partway through.
     *  Gating on how FAR the stick is pushed instead means an ordinary moderate steering hold, of any
     *  length, never triggers it; only a stick shoved close to its edge does. Not hardware-validated. */
    private static final double MOUNTED_FREE_LOOK_ENTER_MAG = 0.8;
    /** Below this, the stick counts as "let go" for ending a held mounted free-look gesture â€” lower
     *  than the enter threshold on purpose (hysteresis) so easing the stick back through the middle of
     *  its travel doesn't flicker the gesture on and off right at one boundary value. */
    private static final double MOUNTED_FREE_LOOK_EXIT_MAG = 0.1;
    /** D171 round 2: "cuando la sueltes pase un pequeÃ±o instante" â€” seconds the camera keeps orbiting
     *  freely after the stick drops below {@link #MOUNTED_FREE_LOOK_EXIT_MAG} before
     *  {@link #shouldMountedChaseEase} lets the chase-back-to-the-mount ease (below) start moving it. */
    private static final double MOUNTED_RETURN_GRACE_SEC = 0.35;
    private static final double ADJUST_OFFSET_HALFLIFE = 0.04;
    private static final double ADJUST_DISTANCE_HALFLIFE = 0.08;
    /** D122: minimum lateral offset ratio while aiming, regardless of the user's configured
     *  {@code thirdPersonAimingOffset} â€” feedback: "quiero que...sea mÃ¡s como al hombro del
     *  personaje, tipo shooter en tercera persona". A user with a conservative general aiming-offset
     *  slider (the default is 30%, ratio â‰ˆ0.10) would otherwise get a barely-there shift that doesn't
     *  read as a deliberate over-the-shoulder frame; this floors it to a clearly visible shoulder
     *  shift while still letting a LARGER configured value win (this is a floor, not a fixed value). */
    private static final double MIN_AIMING_OFFSET_RATIO = 0.22;
    /** D122: absolute bounds for the emote camera distance (automatic zoom-in target + the live
     *  D-pad zoom nudge combined, point 3) â€” wide enough for a real close-up or a pulled-back full-
     *  body view, tight enough to never clip through the character or fly off absurdly far. */
    private static final double EMOTE_DISTANCE_ABS_MIN = 0.8;
    private static final double EMOTE_DISTANCE_ABS_MAX = 5.5;
    /** D122: the D-pad zoom nudge itself is clamped to this range â€” bounding it here too (not just
     *  the final combined distance) prevents a "dead zone" where holding the stick past the visible
     *  limit keeps accumulating an ever-larger invisible offset that then takes many presses of the
     *  opposite direction to work back out of. */
    private static final double EMOTE_ZOOM_OFFSET_MIN = -1.1;
    private static final double EMOTE_ZOOM_OFFSET_MAX = 2.2;
    private static final double EMOTE_ZOOM_STEP = 0.2;
    /** Above this absolute pitch the shoulder offset eases back to centre, so looking near-straight
     *  up or down still lets you interact with the block above/below you instead of aiming past it
     *  (reference {@code CAMERA_OFFSET_SQUEEZE_PITCH_THRESHOLD}). */
    private static final double OFFSET_SQUEEZE_PITCH_THRESHOLD = 80.0;
    /** Half-extent of the 8-corner cube the wall probe is cast from (reference
     *  {@code CAMERA_THROUGH_WALL_DETECTION}) â€” a single centre ray slips through corners. */
    private static final double WALL_DETECTION = 0.18;
    /** Vanilla's own near-plane distance, needed to turn a screen-space ratio into an angle. */
    private static final double NEAR_PLANE = 0.05;
    /** Our own side-offset slider is in blocks (0â€“1.5, user-facing and already persisted); the ported
     *  camera works in screen-space ratios. This maps one to the other so an existing saved config
     *  keeps meaning roughly what it did, at the top end filling about half a screen-width. */
    private static final double BLOCKS_TO_OFFSET_RATIO = 0.5 / 1.5;

    private static double freeYaw = 0.0;
    private static double freePitch = 0.0;
    private static boolean freeInitialized = false;

    /**
     * The point the camera orbits â€” smoothed, advanced ONCE PER CLIENT TICK, and read back
     * interpolated by {@code tickDelta} every render frame. This split is the whole fix for the
     * walking stutter; see {@link #clientTick} and {@link #computeFreePoseInner}.
     */
    private static final dev.steampad.util.SmoothValue.Vec3 smoothRotateCenter =
            new dev.steampad.util.SmoothValue.Vec3();
    /** Screen-space offset ratio (x = lateral, y = vertical), each in about [-1, 1]. */
    private static final dev.steampad.util.SmoothValue.Vec2 smoothOffsetRatio =
            new dev.steampad.util.SmoothValue.Vec2();
    private static final dev.steampad.util.SmoothValue.Scalar smoothDistance =
            new dev.steampad.util.SmoothValue.Scalar(4.0);

    /** Live user adjustment (THIRD_PERSON_ADJUST), kept as ratios, fed into the target above. */
    private static double adjustOffsetX = 0.0;
    private static double adjustOffsetY = 0.0;
    private static boolean adjusting = false;
    /** Live D-pad zoom nudge during a real emote (point 3, D122) â€” a delta ADDED on top of the
     *  automatic D111 emote zoom-in target, never persisted, never touching
     *  {@link GlobalConfig#thirdPersonFreeDistance} or {@link dev.steampad.input.ZoomController}'s own
     *  FOV zoom (deliberately kept fully independent â€” "no debemos combinarlo con el zoom del juego
     *  actual"). Reset to 0 by {@link #resetEmoteZoom} the instant the emote stops, so it can never be
     *  left "stuck" applied afterward â€” see that method's own doc for where that reset is called from. */
    private static double emoteZoomOffset = 0.0;
    /** Wall-clock stamp of the previous render frame â€” drives the per-frame smoothers (distance and
     *  offset ratio). The rotate centre deliberately does NOT use this; it advances on the client
     *  tick instead. See {@link #clientTick}. */
    private static long lastFreeFrameNanos = 0L;

    /** The free camera's own last-computed world position â€” refreshed every {@link #computeFreePose}
     *  call, read back by the (tick-rate) rotate strategy and predictive-aim scan. One render frame
     *  stale at most; imperceptible for either purpose. */
    private static Vec3 lastCameraPos = Vec3.ZERO;
    /** This render tick's hit result from the free camera's own sightline â€” what the crosshair shows
     *  and what attack/use/mine actually targets. Null until the first frame computes it. */
    private static HitResult lastHitResult = null;
    private static long lastPickMs = 0L;
    /** Roughly one client tick â€” the pick (block raycast + entity scan, see {@link #pick}) recomputes
     *  at most this often instead of every render frame. See the call site's own comment for why. */
    private static final long PICK_THROTTLE_MS = 50L;

    private static double smoothedBodyYaw = Double.NaN;
    private static long lastRotateTickNanos = 0L;

    /**
     * True while the free/orbit camera POSE pipeline should compute this frame â€” the user's own
     * persisted toggle, OR the local player is running a GENUINE emote (v0.60.0/D100: "que durante
     * la animaciÃ³n se desbloquee en tercera persona para que pueda girar alrededorâ€¦ verlo desde
     * cualquier Ã¡ngulo"), OR mounted with the mount-chase camera enabled (v0.91.0/D171). Deliberately
     * checks {@link EmoteAnimator#isLocalRealEmotePlaying}, NOT the broader {@code isLocalPlaying} â€”
     * the latter is also true while the Emote Library/Wheel merely shows a live PREVIEW thumbnail (no
     * dance actually confirmed yet), and engaging the free camera just from browsing those menus would
     * be an unrequested, surprising side effect. The emote case is a pure per-frame OR, never written
     * back to {@link GlobalConfig#thirdPersonFreeLookEnabled} â€” the user's own setting is untouched.
     *
     * <p><b>This answers ONLY "should the pose pipeline run", not "should the stick/crosshair/pick
     * also engage".</b> Mirror mode already established that split (pose stays in the free pipeline
     * with DRIVEN, not stick-driven, yaw/pitch; the stick itself routes to the player instead, checked
     * separately via {@link #isMirrorMode}). Mounted (D171) needs the exact same split and for the
     * same reason: the pose should be smooth/elastic (not vanilla's rigid instant snap), but the STICK
     * must keep driving {@code mc.player.turn()} â€” never {@code turnFreeCamera()} â€” or the mount loses
     * its only steering input (bug fixed in D170: {@code AbstractHorse.getRiddenRotation} copies the
     * rider's OWN yaw/pitch onto the mount every tick, javap-confirmed). So {@link #isMounted} is
     * checked HERE (as a reason the pose should run) AND separately, as an EXCLUSION, at every other
     * free-look call site ({@code CameraController.frame}, {@code MouseMixin}, the crosshair/pick/
     * entity-target mixins, {@link #isCameraRelativeMovementActive}) â€” confirmed by reading each one.
     * Gated behind {@link GlobalConfig#thirdPersonAutoSwitchOnMount} (feedback: "solo cosmetico... asi
     * no afectamos vanilla" â€” the user explicitly did not want mounted free-look to touch steering).
     */
    public static boolean isFreeLookEnabled() {
        // D124: thirdPersonCameraEnabled is now the MASTER "Mejor tercera persona" switch â€” with it
        // off, EVERYTHING in the third-person section (this free camera included) is off and the game
        // is pure vanilla, regardless of the free-look sub-toggle's remembered value. The emote OR
        // stays OUTSIDE the master gate on purpose: the emote camera belongs to the Emote system
        // (D100), lasts only for the dance, and losing it just because third-person is off would
        // break "verlo desde cualquier Ã¡ngulo" for first-person players.
        GlobalConfig g = ConfigManager.getGlobal();
        boolean mountedChase = isMounted(Minecraft.getInstance()) && g.thirdPersonAutoSwitchOnMount;
        return (g.thirdPersonCameraEnabled && (g.thirdPersonFreeLookEnabled || mountedChase))
                || EmoteAnimator.isLocalRealEmotePlaying();
    }

    /**
     * True while the camera should behave as MIRROR mode this frame (D123): vanilla's front view
     * (F5Ã—2) â€” camera on the player's face side, stick driving the BODY, pose smoothly trailing the
     * body's front â€” EXCEPT during a real emote, which wins: the whole point of the emote camera is
     * a free orbit around the dancer (D100), and the dancer's body is deliberately frozen anyway, so
     * "trail the body's front" would just pin the orbit. Every mirror-mode call site (the pose's own
     * mirror block, the stick routing in {@code CameraController}, the pick/entity-target redirect
     * mixins, the shooter aim-in) reads THIS method, so the emote exception reaches all of them.
     */
    public static boolean isMirrorMode(Minecraft mc) {
        return mc.options.getCameraType().isMirrored() && !EmoteAnimator.isLocalRealEmotePlaying();
    }

    /**
     * True while the LOCAL player is riding any entity (horse, boat, minecart, a mod's own mount or
     * vehicle - {@link net.minecraft.world.entity.Entity#isPassenger()} is a base Entity contract every
     * one of them satisfies identically), EXCEPT during a real emote - same carve-out as
     * {@link #isMirrorMode}, and for the same reason: the free orbit camera must still win during a
     * dance no matter what the player is sitting on.
     *
     * <p><b>Dual role, exactly like {@link #isMirrorMode} (D171):</b> {@link #isFreeLookEnabled} reads
     * this as a REASON the pose pipeline should run (so mounted gets the smooth/elastic chase camera
     * instead of a rigid vanilla one), while every OTHER free-look consumer reads it as an EXCLUSION
     * layered on top (so mounted still behaves like free-look is off for anything besides the pose:
     * the crosshair/pick/entity-target redirects, aim assist and camera-relative movement all stay
     * vanilla). Never fold a new mounted check into just one of the two directions - it has to be
     * BOTH, at every site, or either the pose stays vanilla-rigid (pose-DIRECTION missing) or one of
     * those other consumers wrongly starts treating a mount's chase camera as real free-look
     * (exclusion-DIRECTION missing).
     *
     * <p>The STICK is the one exception with a THIRD answer, not just "on" or "excluded": see
     * {@link #tickMountedFreeLook} - by default it still steers via {@code mc.player.turn()} exactly
     * like this exclusion implies, but a deliberate hard push hands it to the free camera instead
     * (round 2 of D171 - the pose alone chasing the stick's steering was too subtle to read as "the
     * camera is doing something").
     */
    public static boolean isMounted(Minecraft mc) {
        return mc.player != null && mc.player.isPassenger() && !EmoteAnimator.isLocalRealEmotePlaying();
    }

    /** True while a deliberate mounted "look around" push (see {@link #tickMountedFreeLook}) currently
     *  owns the stick. False the rest of the time, including during {@link #MOUNTED_RETURN_GRACE_SEC}'s
     *  grace window and the ease back to the mount's facing that follows it. */
    private static boolean mountedFreeLookHeld = false;
    /** Counts down, once per frame, after a held mounted free-look ends â€” see
     *  {@link #shouldMountedChaseEase}'s own doc for why this delays the ease instead of starting it
     *  the instant the stick drops. */
    private static double mountedReturnGraceSec = 0.0;

    /**
     * Ticks the mounted free-look gesture state machine with this frame's right-stick magnitude
     * (0..1, post-deadzone) and returns whether the stick should drive the free camera THIS frame
     * (true) instead of steering (false) â€” read directly into {@code CameraController.frame()}'s own
     * {@code freeLook} decision. Must be called every frame regardless of mount state (it self-guards
     * and resets when not applicable) so the grace countdown keeps advancing on an idle stick and
     * never gets stuck mid-return if the player dismounts or the master/auto-switch settings change
     * while a gesture is in flight.
     *
     * <p>Hysteresis by design ({@link #MOUNTED_FREE_LOOK_ENTER_MAG} vs.
     * {@link #MOUNTED_FREE_LOOK_EXIT_MAG}): once a push crosses the (high) enter threshold, easing the
     * stick back down doesn't drop out of free-look until it crosses the LOWER exit threshold, so
     * hovering a stick near one single boundary value can't flicker the gesture on and off frame to
     * frame.
     */
    static boolean tickMountedFreeLook(Minecraft mc, double mag, double dt) {
        if (!isMounted(mc)) {
            mountedFreeLookHeld = false;
            mountedReturnGraceSec = 0.0;
            mountedYawOffset = 0.0;
            return false;
        }
        GlobalConfig g = ConfigManager.getGlobal();
        if (!g.thirdPersonCameraEnabled || !g.thirdPersonAutoSwitchOnMount) {
            mountedFreeLookHeld = false;
            mountedReturnGraceSec = 0.0;
            return false;
        }

        if (mountedFreeLookHeld) {
            if (mag < MOUNTED_FREE_LOOK_EXIT_MAG) {
                mountedFreeLookHeld = false;
                mountedReturnGraceSec = MOUNTED_RETURN_GRACE_SEC;
            }
        } else if (mag > MOUNTED_FREE_LOOK_ENTER_MAG) {
            mountedFreeLookHeld = true;
            mountedReturnGraceSec = 0.0;
        }
        if (!mountedFreeLookHeld && mountedReturnGraceSec > 0.0) {
            mountedReturnGraceSec = Math.max(0.0, mountedReturnGraceSec - dt);
        }
        return mountedFreeLookHeld;
    }

    /**
     * True while {@code computeFreePoseInner}'s mounted-chase block should ease {@code freeYaw}/
     * {@code freePitch} toward the mount's own facing this frame. False while a free-look push
     * currently owns the stick (easing toward the mount at the same time the stick is actively
     * orbiting away from it would just fight the player's own input), AND for
     * {@link #MOUNTED_RETURN_GRACE_SEC} seconds after releasing one â€” the "pase un pequeÃ±o instante"
     * pause the feedback asked for, so letting go doesn't snap the camera back the instant the stick
     * crosses the exit threshold.
     */
    static boolean shouldMountedChaseEase() {
        return !mountedFreeLookHeld && mountedReturnGraceSec <= 0.0;
    }

    /** Read-only view of {@link #shouldMountedChaseEase} for the debug dump. */
    public static boolean isMountedChaseEasing() {
        return shouldMountedChaseEase();
    }

    /**
     * One exponential-ease step of an ANGLE (degrees) toward a target, always along the short way
     * around the circle â€” pure and package-visible for the same reason as {@link #distanceTargetFor}
     * (the D120 precedent: sign/wrap branching is exactly where visual review has already failed
     * once in this file). Without the inner wrap, easing from +170Â° toward âˆ’170Â° would swing 340Â°
     * the long way through 0 instead of 20Â° through Â±180.
     */
    static double easeAngleToward(double currentDeg, double targetDeg, double factor) {
        return Mth.wrapDegrees(
                currentDeg + Mth.wrapDegrees(targetDeg - currentDeg) * factor);
    }

    public static void toggleFreeLook() {
        GlobalConfig g = ConfigManager.getGlobal();
        g.thirdPersonFreeLookEnabled = !g.thirdPersonFreeLookEnabled;
        freeInitialized = false;   // re-seed from vanilla's own camera next frame â€” never a snap
        smoothedBodyYaw = Double.NaN;
        ConfigManager.saveGlobal();
    }

    public static boolean isAdjusting() { return adjusting; }
    public static void beginAdjust() { adjusting = true; }
    public static void endAdjust() { adjusting = false; }

    /** Live offset nudge while THIRD_PERSON_ADJUST is held (right stick repurposed) â€” dx/dy are plain
     *  per-frame deltas, already curve-shaped by the caller exactly like camera look is. Applied as a
     *  screen-space ratio now that the camera projects its offset through the real FOV, so the same
     *  nudge moves the player by the same fraction of the screen at any FOV or aspect ratio. */
    public static void adjustOffset(double dx, double dy) {
        double max = 1.0;
        adjustOffsetX = Mth.clamp(adjustOffsetX + dx * BLOCKS_TO_OFFSET_RATIO, -max, max);
        adjustOffsetY = Mth.clamp(adjustOffsetY + dy * BLOCKS_TO_OFFSET_RATIO, -max, max);
    }

    /** D-pad up/down step while adjusting â€” dir=+1 pulls the camera closer, -1 pushes it back. */
    public static void adjustDistance(int dir) {
        GlobalConfig g = ConfigManager.getGlobal();
        g.thirdPersonFreeDistance = (float) Mth.clamp(
                g.thirdPersonFreeDistance - dir * 0.25, MIN_DISTANCE, MAX_DISTANCE);
        ConfigManager.saveGlobal();
    }

    /**
     * D-pad up/down step DURING A REAL EMOTE (point 3, D122) â€” lets the player zoom in to see detail
     * or pull back for the whole dance, without touching {@link GlobalConfig#thirdPersonFreeDistance}
     * (this is a transient nudge, never saved) or {@link dev.steampad.input.ZoomController}'s own FOV
     * zoom (a completely separate system â€” see {@link #emoteZoomOffset}'s own doc for why they're kept
     * apart). dir=+1 (D-pad up) pulls closer, -1 (down) pushes back, matching {@link #adjustDistance}'s
     * own sign convention so the two feel consistent if a player uses both.
     */
    public static void adjustEmoteZoom(int dir) {
        emoteZoomOffset = Mth.clamp(
                emoteZoomOffset - dir * EMOTE_ZOOM_STEP, EMOTE_ZOOM_OFFSET_MIN, EMOTE_ZOOM_OFFSET_MAX);
    }

    /**
     * Snaps the manual emote-zoom nudge back to zero â€” called the instant a real emote stops (see
     * {@code EmoteAnimator.clientTick}'s start/stop transition detection), so the camera ALWAYS returns
     * to its normal, un-zoomed distance once the dance ends or is interrupted. This is the piece that
     * makes point 3's "siempre debe de regresar a su posicion" true regardless of how the emote ended
     * (finished naturally, cancelled by movement, disconnected mid-dance) â€” every one of those paths
     * already routes through {@code isLocalRealEmotePlaying()} flipping to false, which is the ONLY
     * condition this reset depends on.
     */
    public static void resetEmoteZoom() {
        emoteZoomOffset = 0.0;
    }

    /** Package-visible for {@code ThirdPersonCameraControllerLogicTest} â€” lets the test observe what
     *  {@link #adjustEmoteZoom}/{@link #resetEmoteZoom} actually did to the private field, the same
     *  way the real per-frame call site in {@code computeFreePoseInner} reads it. Not used by any
     *  production call site (which read the field directly, being in the same class). */
    static double currentEmoteZoomOffset() { return emoteZoomOffset; }

    /** Called from {@link CameraController#frame} INSTEAD OF {@code changeLookDirection} whenever
     *  free-look owns the stick this frame â€” same yaw/pitch degree units the caller already computed
     *  (aim assist, sensitivity curve, zoom slow-down all already folded in upstream). */
    public static void turnFreeCamera(double yawDeg, double pitchDeg) {
        turnCalls++;
        if (!freeInitialized) { turnSkippedUnseeded++; return; }   // computeFreePose() seeds it first
        turnYawSum += Math.abs(yawDeg);
        turnPitchSum += Math.abs(pitchDeg);
        freeYaw = Mth.wrapDegrees(freeYaw + yawDeg);
        if (!ConfigManager.getGlobal().thirdPersonPitchLock) {
            freePitch = Mth.clamp(freePitch + pitchDeg, -PITCH_LIMIT, PITCH_LIMIT);
        }
    }

    /**
     * Mounted steering: mirrors the SAME shaped yaw delta the rider just turned by onto the chase
     * camera, so a small stick nudge moves the camera a small amount — the fine low-end response that
     * on foot comes for free.
     *
     * <p><b>Why it was missing.</b> While mounted and merely steering (i.e. not past
     * {@link #MOUNTED_FREE_LOOK_ENTER_MAG}), the stick goes to {@code mc.player.turn()} and the camera
     * is driven ONLY by the chase ease toward the VEHICLE's heading, at
     * {@link #MOUNTED_CHASE_HALFLIFE} (0.3 s ≈ 3.8% of the gap per frame at 60 fps). So a small nudge
     * either arrived heavily lagged (a horse, whose yaw follows the rider's) or never arrived at all
     * (a boat or a modded vehicle, whose yaw is its own — see the chase block's own doc). Only a
     * deliberate hard shove past the gate gave direct camera control. Reported as: mounted, the right
     * stick has none of the "delicadeza" it has on foot.
     *
     * <p><b>Why adding a delta cannot run away.</b> The chase eases toward an ABSOLUTE target
     * (the mount's yaw), not by accumulation — so this only ever changes how fast the camera reaches
     * that target, never where it settles. On a horse the mount follows the rider, so the two stay
     * converged and the camera simply tracks the steering 1:1 instead of trailing it. On a boat the
     * nudge reads as a small look-around that the ease returns to the hull's heading within the same
     * ~0.3 s the release gesture already used — which is the behaviour D171 asked for to begin with.
     *
     * <p><b>Yaw only, deliberately.</b> {@code freePitch} while mounted is a configured driving tilt
     * ({@code thirdPersonMountedCameraPitch}); D171 round 3 set it precisely because inheriting the
     * rider's pitch put the camera at mount height staring into its back ("está muy bajo el ángulo").
     * Feeding stick pitch in here would re-open that. The report is about turning ("doblar").
     *
     * <p>No-op unless the free pose is actually seeded and driving the camera this session, so with
     * the third-person camera off (or in first person) this changes nothing.
     */
    public static void nudgeMountedChaseYaw(double yawDeg) {
        if (!freeInitialized || yawDeg == 0.0) return;
        // Into the OFFSET, not into freeYaw. D191 added it straight to freeYaw and that is why the
        // report came back: the mounted chase block re-eases freeYaw toward the vehicle's own yaw
        // EVERY FRAME, so at 100+ fps against a 20 Hz nudge the push was erased before it could be
        // seen. On a horse it half-worked by accident (the horse copies the rider's yaw, so the ease
        // target moved too); on a vehicle that steers itself — the reported Jurassic car/helicopter —
        // the target never moved and the camera simply did not respond.
        //
        // Steering an OFFSET fixes both at once: the ease target becomes mountYaw + offset, so a small
        // push moves the camera a little and a big push moves it a lot, immediately and proportionally,
        // and the offset then decays back to zero so the camera still settles behind the mount (the
        // D171 "que regrese hacia donde se dirige la parte de enfrente" behaviour, preserved).
        mountedYawOffset = Mth.clamp(mountedYawOffset + yawDeg,
                -MOUNTED_YAW_OFFSET_MAX, MOUNTED_YAW_OFFSET_MAX);
    }

    /** Stick-steered camera offset from the mount's own facing, in degrees. Decays to zero. */
    private static double mountedYawOffset = 0.0;
    /** How far the stick can swing the mounted camera off the mount's facing. Wide enough to look
     *  where you are turning, short of letting it become a full free orbit (that is the free-look
     *  gesture's job, past MOUNTED_FREE_LOOK_ENTER_MAG). */
    private static final double MOUNTED_YAW_OFFSET_MAX = 75.0;
    /** Half-life of the offset's return to zero. Deliberately slower than the chase itself, so the
     *  camera holds your look for a moment instead of snapping back mid-turn. */
    private static final double MOUNTED_OFFSET_DECAY_HALFLIFE = 0.55;

    // ---- "is the stick actually reaching the free camera?" counters ------------------------------
    // Added because a "the free camera feels locked" report is otherwise undiagnosable from a dump:
    // the existing perf line only proves computeFreePose RAN, which says nothing about whether any
    // rotation input arrived. These separate the three ways it can silently do nothing: never called
    // (input routed to the player instead — CameraController's own freeLook gate), called but not yet
    // seeded (bails at the guard above), or called with a zero-magnitude value (deadzone/curve ate it).
    private static long turnCalls = 0L;
    private static long turnSkippedUnseeded = 0L;
    private static double turnYawSum = 0.0;
    private static double turnPitchSum = 0.0;

    /** One line for the debug dump: how much rotation input actually reached the free camera. */
    public static String turnInputSummary() {
        return String.format(java.util.Locale.ROOT,
                "turnFreeCamera calls=%d (skipped-unseeded=%d) |yaw|sum=%.1f° |pitch|sum=%.1f° "
                        + "| live yaw=%.1f pitch=%.1f seeded=%b adjusting=%b",
                turnCalls, turnSkippedUnseeded, turnYawSum, turnPitchSum,
                freeInitialized ? freeYaw : Double.NaN, freeInitialized ? freePitch : Double.NaN,
                freeInitialized, adjusting);
    }

    /** World-space forward vector for the given vanilla-convention yaw/pitch (degrees). */
    private static Vec3 directionFromYawPitch(double yawDeg, double pitchDeg) {
        double yawRad = Math.toRadians(yawDeg);
        double pitchRad = Math.toRadians(pitchDeg);
        double xz = Math.cos(pitchRad);
        return new Vec3(-Math.sin(yawRad) * xz, -Math.sin(pitchRad), Math.cos(yawRad) * xz);
    }

    private static double yawFromDirection(Vec3 dir) {
        return Math.toDegrees(Math.atan2(-dir.x, dir.z));
    }

    private static double pitchFromDirection(Vec3 dir) {
        double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        return Math.toDegrees(-Math.atan2(dir.y, horiz));
    }

    /** A computed free-camera pose the mixin should apply this frame, or null if free-look shouldn't
     *  run right now (first person, no world/player yet). */
    public record FreePose(Vec3 pos, float yaw, float pitch) {}

    // ---- Performance diagnostics (feedback: "rendimiento pÃ©simo, laggea el juego") ------------------
    //
    // Nothing in this class's own per-frame work looked like an obvious hotspot on inspection (one
    // raycast per frame â€” the same cost profile vanilla's OWN third-person camera already pays every
    // frame â€” plus a throttled block/entity pick at roughly tick rate, not frame rate). Rather than
    // guess an "optimization" for a cost that may not even be the real source, this times the actual
    // per-frame work and logs a min/avg/max summary every ~2s while free-look is active, so the next
    // hardware test replaces the guess with real numbers (same diagnostic-over-blind-fix approach
    // already used for the emote deformation investigation this session).
    private static long perfWindowStartMs = 0L;
    private static long perfSumNanos = 0L, perfMaxNanos = 0L;
    private static int perfSamples = 0;
    /** Last completed perf window, kept for the debug dump (D098). */
    private static volatile String lastPerfSummary = "no data (free camera not used yet)";

    public static String lastPerfSummary() { return lastPerfSummary; }

    private static void recordPerfSample(long elapsedNanos) {
        long now = System.currentTimeMillis();
        if (perfWindowStartMs == 0L) perfWindowStartMs = now;
        perfSumNanos += elapsedNanos;
        if (elapsedNanos > perfMaxNanos) perfMaxNanos = elapsedNanos;
        perfSamples++;
        if (now - perfWindowStartMs < 2000L) return;

        double avgMs = (perfSumNanos / (double) perfSamples) / 1_000_000.0;
        double maxMs = perfMaxNanos / 1_000_000.0;
        lastPerfSummary = String.format(java.util.Locale.ROOT,
                "%d samples, avg=%.3fms max=%.3fms over %dms", perfSamples, avgMs, maxMs, now - perfWindowStartMs);
        LogUtil.debug("[SteamPad][thirdperson-perf] computeFreePose: {} samples, avg={}ms max={}ms "
                        + "over {}ms — compare against your actual frame budget (e.g. 16.6ms @ 60fps) "
                        + "to judge whether THIS code is the source of any observed stutter.",
                perfSamples, String.format(java.util.Locale.ROOT, "%.3f", avgMs),
                String.format(java.util.Locale.ROOT, "%.3f", maxMs), now - perfWindowStartMs);
        perfWindowStartMs = now;
        perfSumNanos = 0L;
        perfMaxNanos = 0L;
        perfSamples = 0;
    }

    /**
     * Computes this render frame's free-camera position + rotation. Called from the mixin once per
     * frame in place of vanilla's own third-person camera math. Thin timing wrapper around
     * {@link #computeFreePoseInner} â€” see the perf-diagnostics note above.
     */
    public static FreePose computeFreePose(Minecraft mc, Camera camera, float tickDelta) {
        long t0 = System.nanoTime();
        try {
            return computeFreePoseInner(mc, camera, tickDelta);
        } finally {
            recordPerfSample(System.nanoTime() - t0);
        }
    }

    /** The un-smoothed point the camera wants to orbit this instant: the render-interpolated eye
     *  position, raised by {@link #ROTATE_CENTER_HEIGHT_OFFSET}. */
    private static Vec3 rotateCenterTarget(Minecraft mc, float tickDelta) {
        Vec3 eye = mc.player.getEyePosition(tickDelta);
        return new Vec3(eye.x, eye.y + ROTATE_CENTER_HEIGHT_OFFSET, eye.z);
    }

    /** Half the player's body diagonal â€” the camera measures its distance from the SURFACE of this
     *  sphere, not from the eye point, so it never ends up inside the model (reference
     *  {@code EntityAgent.getBodyRadius}). */
    private static double bodyRadius(Minecraft mc) {
        return mc.player.getBbWidth() * 0.8660254037844386;   // 0.5 * sqrt(3)
    }

    /**
     * Advances the camera's smoothed follow state by exactly one client tick. Called once per client
     * tick from {@code SteamPadClient}.
     *
     * <p><b>This method is the fix for "no lag while orbiting, terrible lag while walking".</b> The
     * old code read {@code mc.player.getEyePos()} â€” the raw, tick-quantised eye position â€” directly
     * inside the per-render-frame camera math. That value only changes 20 times a second, so at
     * 60â€“144 fps the camera held still for two to seven frames and then jumped, over and over. When
     * the player stands still the eye position isn't changing at all, so there is nothing to
     * stair-step and the camera looks perfectly smooth â€” which is exactly why the stutter appeared
     * only while walking, and why it survived every previous round of profiling: the per-frame work
     * was never slow, it was reading data that only existed at 20 Hz.
     *
     * <p>The fix is the reference mod's own two-part structure: the follow target advances here, once
     * per tick, into a {@link dev.steampad.util.SmoothValue.Vec3}; the render frame then reads it
     * back through {@code get(tickDelta)}, which interpolates between the previous tick's value and
     * this one. Both halves are required â€” interpolating an unsmoothed target would still snap
     * whenever the player changed speed, and smoothing without interpolating would still be 20 Hz.
     */
    public static void clientTick(Minecraft mc) {
        if (!isFreeLookEnabled() || mc.player == null || mc.level == null) return;
        // D121: front-view is NOT excluded â€” see CameraController.frame()'s own note. Reverts v0.78.0's
        // exclusion after hardware testing showed vanilla's own front-view camera feels locked/laggy;
        // front-view now advances through the exact same tick as back-view.
        if (mc.options.getCameraType().isFirstPerson()) return;

        Vec3 target = rotateCenterTarget(mc, 1.0f);
        if (!freeInitialized) {
            // Not seeded yet (first frame hasn't run): park the smoother on the target so the camera
            // never flies in from wherever it last was.
            smoothRotateCenter.set(target);
            return;
        }

        boolean aiming = isAiming(mc);
        double hHalf = aiming ? AIMING_HALFLIFE : NORMAL_HALFLIFE_HORIZON;
        double vHalf = aiming ? AIMING_HALFLIFE : NORMAL_HALFLIFE_VERTICAL;

        // A camera further from the player is allowed to trail further behind â€” without this a
        // long-distance camera feels nervous and a close one feels sluggish (reference behaviour).
        double dist = smoothRotateCenter.get(1.0f).distanceTo(lastCameraPos);
        double scale = Math.sqrt(Math.max(0.0, dist)) * EYE_HALFLIFE_MULTIPLIER;
        smoothRotateCenter.setHalflife(hHalf * scale, vHalf * scale, hHalf * scale);
        smoothRotateCenter.setTarget(target);
        smoothRotateCenter.update(CLIENT_TICK_SEC);
    }

    private static FreePose computeFreePoseInner(Minecraft mc, Camera camera, float tickDelta) {
        if (mc.player == null || mc.level == null) { freeInitialized = false; return null; }
        // D121: front-view is NOT excluded â€” see CameraController.frame()'s own note (reverts v0.78.0).
        if (mc.options.getCameraType().isFirstPerson()) {
            freeInitialized = false;
            return null;
        }

        long now = System.nanoTime();
        double dt = lastFreeFrameNanos == 0L ? (1.0 / 60.0) : (now - lastFreeFrameNanos) / 1_000_000_000.0;
        lastFreeFrameNanos = now;
        if (dt <= 0 || dt > 0.25) dt = 1.0 / 60.0;

        GlobalConfig g = ConfigManager.getGlobal();
        // Mirror mode never gets the shooter aim-in treatment (D123): the camera there faces the
        // PLAYER â€” "pull in close over the shoulder" would shove it into their face, and the whole
        // aiming stack (pick redirect, predictive facing) is vanilla-driven in that view anyway.
        boolean aiming = isAiming(mc) && !isMirrorMode(mc);
        if (!freeInitialized) {
            freeYaw = camera.getYRot();
            freePitch = camera.getXRot();
            smoothDistance.set(g.thirdPersonFreeDistance);
            smoothRotateCenter.set(rotateCenterTarget(mc, tickDelta));
            smoothOffsetRatio.set(sideOffsetRatio(g) + adjustOffsetX, adjustOffsetY);
            freeInitialized = true;
        }

        // ---- mirror mode (front view, F5Ã—2) â€” D123 ---------------------------------------------
        // Round-4 feedback: "es exactamente el mismo que el tercera persona... era copiar valores
        // para evitar el lag pero que siempre este viendo de frente". D121 made front view IDENTICAL
        // to back view â€” which erased the mode's whole identity (a free orbit doesn't "always face
        // you"). The correct synthesis of the two failed attempts (D120 excluded everything including
        // the pose â†’ vanilla's hard-locked laggy camera; D121 included everything including the stick
        // â†’ a second back view): the POSE pipeline is ours (same distance smoothing, same offset
        // projection, same wall clamp, same rotate-centre â€” "copiar valores para evitar el lag"), but
        // yaw/pitch are DRIVEN, not stick-driven: they ease toward the player's own front
        // (bodyYaw+180, pitch mirrored â€” vanilla front-view's own convention, minus the hard lock).
        // The stick meanwhile rotates the PLAYER, exactly like vanilla front view (see
        // CameraController.frame) â€” so walking or hitting turns the body, and the camera swings
        // smoothly back around to the face: "cuando golpee regrese a la posicion frontal", the mirror
        // image of back view's "golpea y te da la espalda".
        //
        // getYaw(tickDelta)/getPitch(tickDelta) are the render-interpolated reads (confirmed via
        // javap) â€” the D116 rule: never feed tick-quantised data into per-frame math.
        if (isMirrorMode(mc)) {
            double mirrorYaw = Mth.wrapDegrees(mc.player.getViewYRot(tickDelta) + 180.0);
            double mirrorPitch = -mc.player.getViewXRot(tickDelta);
            double factor = 1.0 - Math.pow(2.0, -dt / MIRROR_FOLLOW_HALFLIFE);
            freeYaw = easeAngleToward(freeYaw, mirrorYaw, factor);
            freePitch = freePitch + (mirrorPitch - freePitch) * factor;
        } else if (isMounted(mc)) {
            // ---- mounted chase camera (D171, round 2) ------------------------------------------
            // Request: "que la camara se acople detras siempre automaticamente... a menos que
            // muevas el stick derecho... cuando la sueltes pase un pequeño instante y regrese hacia
            // donde se dirige la parte de enfrente" - round 1 kept freeYaw permanently chase-only
            // (never stick-driven while mounted), which turned out to be too subtle to notice: the
            // stick was ALWAYS simultaneously setting mountYaw (steering, via player.turn()) AND
            // being chased by freeYaw the same frame, so the 0.3s lag rarely became visible. Round 2
            // genuinely decouples the two: CameraController.frame()'s tickMountedFreeLook lets a
            // deliberate hard push (past MOUNTED_FREE_LOOK_ENTER_MAG) hand the stick to
            // turnFreeCamera() instead of player.turn() - see that method's own doc. This block only
            // still owns freeYaw/freePitch while shouldMountedChaseEase() says the stick ISN'T doing
            // that (ordinary steering, or the grace pause right after releasing a push) - during an
            // active push it must NOT also ease toward the mount, or it would fight the stick's own
            // orbit input every frame.
            if (shouldMountedChaseEase()) {
                // Round 3 (feedback: "solo esta en una direccion, y no precisamente hacia donde avanza
                // el personaje" / "esta muy bajo el angulo y no se aprecia mucho"). TWO real bugs here,
                // both in these two lines:
                //
                // 1. YAW — the variable was NAMED mountYaw but read the PLAYER's own view yaw, which is
                //    NOT the mount's heading. javap on the real 1.21.1 jar: Boat.controlBoat() steers
                //    from inputLeft/inputRight (the LEFT stick's strafe keys), accumulates its own
                //    `deltaRotation` field and applies setYRot(getYRot() + deltaRotation) — the boat
                //    owns its yaw outright and the rider's yaw never feeds into it. So while driving a
                //    boat the hull turned and the camera did not: the camera sat wherever the player's
                //    own look had last been left, which is exactly "una sola direccion, no hacia donde
                //    avanza". (A horse hid the bug: AbstractHorse.getRiddenRotation copies the rider's
                //    yaw ONTO the horse, so the two values happened to agree there.) Reading the
                //    VEHICLE's own rotation is correct for both, and for a mod's vehicle by definition —
                //    every mount stores its real heading in its own entity yaw. Entity.getViewYRot
                //    (javap-confirmed: lerps yRotO→getYRot by partialTick) exists on Entity, not just
                //    Player, so the render-interpolated read the D116 rule demands is available for any
                //    vehicle.
                // 2. PITCH — copying the player's pitch meant the chase inherited whatever the rider
                //    happened to be looking at, which while riding is roughly level: the camera ended up
                //    at the mount's own height, staring into its back ("muy bajo... no se aprecia
                //    mucho"). A driving camera wants a deliberate downward tilt instead, which places
                //    the camera high and behind (camPos = rotateCenter - direction*reach, so tilting the
                //    look direction DOWN lifts the camera UP). It's a pure feel value, so it's a config
                //    slider rather than a constant baked in here (D122's lesson about signed-off feel).
                Entity vehicle = mc.player.getVehicle();
                double mountYaw = vehicle != null ? vehicle.getViewYRot(tickDelta)
                                                  : mc.player.getViewYRot(tickDelta);
                double mountPitch = g.thirdPersonMountedCameraPitch;
                double factor = 1.0 - Math.pow(2.0, -dt / MOUNTED_CHASE_HALFLIFE);
                // The stick's steered offset rides ON TOP of the mount's facing, and decays back to
                // zero on its own clock — see nudgeMountedChaseYaw for why it cannot live in freeYaw.
                mountedYawOffset *= Math.pow(2.0, -dt / MOUNTED_OFFSET_DECAY_HALFLIFE);
                if (Math.abs(mountedYawOffset) < 0.01) mountedYawOffset = 0.0;
                freeYaw = easeAngleToward(freeYaw, mountYaw + mountedYawOffset, factor);
                freePitch = freePitch + (mountPitch - freePitch) * factor;
            }
        }

        // ---- distance -------------------------------------------------------------------------
        boolean emoting = EmoteAnimator.isLocalRealEmotePlaying();
        double distanceTarget = distanceTargetFor(g, emoting, aiming, emoteZoomOffset, isMounted(mc));
        // D120 gave emotes a fast halflife (0.72s "normal" made a short dance's zoom invisible);
        // D123 moved them onto their OWN constants after sharing with aiming backfired â€” see
        // EMOTE_DISTANCE_HALFLIFE's doc. Emoting checked first: dancing wins over a stale aim state.
        smoothDistance.setHalflife(adjusting ? ADJUST_DISTANCE_HALFLIFE
                : emoting ? EMOTE_DISTANCE_HALFLIFE
                : aiming ? AIMING_DISTANCE_HALFLIFE : NORMAL_DISTANCE_HALFLIFE);
        smoothDistance.setTarget(distanceTarget);
        smoothDistance.update(dt);

        // ---- offset ratio ---------------------------------------------------------------------
        double offsetTargetX = offsetTargetXFor(g, emoting, aiming, adjustOffsetX);
        double offsetTargetY = emoting ? 0.0 : adjustOffsetY;
        smoothOffsetRatio.setHalflife(adjusting ? ADJUST_OFFSET_HALFLIFE
                : emoting ? EMOTE_OFFSET_HALFLIFE
                : aiming ? AIMING_OFFSET_HALFLIFE : NORMAL_OFFSET_HALFLIFE);
        smoothOffsetRatio.setTarget(offsetTargetX, offsetTargetY);
        smoothOffsetRatio.update(dt);

        double offsetX = smoothOffsetRatio.getX(tickDelta);
        double offsetY = smoothOffsetRatio.getY(tickDelta);
        // Ease the offset back to centre near vertical, so looking almost straight up or down still
        // targets the block directly above/below instead of one pushed aside by the shoulder offset.
        double absPitch = Math.abs(freePitch);
        if (absPitch > OFFSET_SQUEEZE_PITCH_THRESHOLD) {
            double squeeze = (90.0 - absPitch) / (90.0 - OFFSET_SQUEEZE_PITCH_THRESHOLD);
            squeeze = Mth.clamp(squeeze, 0.0, 1.0);
            offsetX *= squeeze;
            offsetY *= squeeze;
        }

        // ---- camera basis + FOV-projected direction -------------------------------------------
        Vec3 forward = directionFromYawPitch(freeYaw, freePitch);
        double[] r = rightVectorXZ(freeYaw);
        Vec3 left = new Vec3(-r[0], 0, -r[1]);              // left = -right, same shared basis
        Vec3 up = upFromYawPitch(freeYaw, freePitch);

        // Turn the screen-space ratio into an angle using the REAL rendered FOV, so the player stays
        // at the same place on screen whatever the FOV or window aspect happens to be â€” the piece the
        // old world-space "nudge N blocks sideways" approach could not express.
        double fovDeg = currentFovDegrees(mc, camera, tickDelta);
        double aspect = Math.max(0.1, (double) mc.getWindow().getScreenWidth() / mc.getWindow().getScreenHeight());
        // Half-extents of the near plane, which is what turns "fraction of the screen" into an angle.
        // tanH reduces to (aspect * halfHeight) / NEAR_PLANE â€” the reference writes it the long way
        // round as tan(atan(halfWidth / near)); the near-plane distance cancels, leaving the vertical
        // tangent scaled by the aspect ratio, which is why a wide window pushes the player further
        // sideways for the same configured offset.
        double tanV = Math.tan(Math.toRadians(fovDeg) / 2.0);
        double tanH = aspect * tanV;

        Vec3 direction = forward
                .subtract(up.scale(offsetY * tanV))
                .subtract(left.scale(offsetX * tanH));
        if (direction.lengthSqr() < 1e-10) direction = forward;
        direction = direction.normalize();

        Vec3 rotateCenter = smoothRotateCenter.get(tickDelta);
        double reach = bodyRadius(mc) + smoothDistance.get();
        Vec3 camPos = rotateCenter.subtract(direction.scale(reach));

        camPos = clampAgainstWalls(mc, rotateCenter, camPos);

        lastCameraPos = camPos;
        // Throttled, not recomputed every render frame: the pick is a block raycast PLUS an entity
        // scan (see #pick) â€” real cost at 60-144fps on Deck-class hardware, for a result (what the
        // crosshair/attack targets) that being a few milliseconds stale is imperceptible on.
        //
        // EXCEPT while aiming (point 5/D120): both the camera (rotate centre follows the player) and
        // a moving target shift their SCREEN-RELATIVE position continuously, every render frame. A
        // ~50ms-stale pick â€” up to 2-3 ticks behind â€” means predictTargetEntity() and the body-facing
        // "shoot like first-person" logic sometimes chase where the target WAS, not where it is now:
        // exactly the reported "a veces sÃ­, a veces no, sobre todo en movimiento". Charging a bow is a
        // deliberate, relatively rare action (never sustained every frame of ordinary gameplay like
        // walking/looking around is), so paying the full per-frame cost here specifically is the right
        // trade â€” precision when it matters, the cheap throttle everywhere else.
        long nowMs = System.currentTimeMillis();
        if (aiming || nowMs - lastPickMs >= PICK_THROTTLE_MS) {
            lastPickMs = nowMs;
            // MUST be `forward` (the camera's own yaw/pitch look vector), NEVER `direction`.
            // ThirdPersonCameraMixin applies this pose with setRotation(pose.yaw(), pose.pitch()) —
            // i.e. the camera is AT camPos ORIENTED along forward — so the screen centre, and
            // therefore the crosshair, is exactly the ray camPos + t*forward.
            //
            // `direction` is only the vector used to PLACE the camera (camPos = rotateCenter -
            // direction*reach) so the player renders off to one side; it points back at the player's
            // body, not at the crosshair. A v0.87.0 change swapped this to `direction` on the theory
            // that it was the "offset-corrected" ray — that was wrong, and it broke aiming everywhere
            // (reported: "apunto a algo y ya no lo hace bien", arrows no longer following the
            // crosshair). Restored. Do not "fix" this to `direction` again without first re-reading
            // ThirdPersonCameraMixin's setRotation call.
            lastHitResult = pick(mc, camPos, forward);
        }
        return new FreePose(camPos, (float) freeYaw, (float) freePitch);
    }

    /** The configured shoulder side/amount as a screen-space ratio (0 when centred). */
    private static double sideOffsetRatio(GlobalConfig g) {
        if (g.thirdPersonCameraSide == GlobalConfig.ThirdPersonCameraSide.CENTER) return 0.0;
        double blocks = Mth.clamp(g.thirdPersonCameraOffset, 0f, 1.5f);
        double ratio = blocks * BLOCKS_TO_OFFSET_RATIO;
        return g.thirdPersonCameraSide == GlobalConfig.ThirdPersonCameraSide.LEFT ? -ratio : ratio;
    }

    /**
     * The camera-distance target for this frame â€” pure function of state, package-visible so
     * {@code ThirdPersonCameraControllerLogicTest} can exercise every combination directly (points
     * 3/6, D120): a real emote zooms in (D111's clamp), aiming pulls in shooter-style (The
     * Division/Gears â€” closer + tighter, floored so the camera never clips into the character's own
     * model at a very short configured distance), otherwise the plain configured free distance.
     * Emoting wins over aiming should the two somehow overlap (dancing is the more deliberate state).
     */
    /** On-foot overload — same as the full form with {@code mounted=false}. */
    static double distanceTargetFor(GlobalConfig g, boolean emoting, boolean aiming, double emoteZoomOffset) {
        return distanceTargetFor(g, emoting, aiming, emoteZoomOffset, false);
    }

    static double distanceTargetFor(GlobalConfig g, boolean emoting, boolean aiming, double emoteZoomOffset,
                                    boolean mounted) {
        if (emoting) {
            // The automatic D111 zoom-in is computed and clamped EXACTLY as before (point 3 hardware
            // feedback: "quedo perfecto" â€” this baseline must not shift even by a few centimeters).
            // The live D-pad zoom nudge (point 3, D122) is added AFTER that clamp and only THEN
            // bounded by the wider absolute range, so emoteZoomOffset=0 is bit-for-bit identical to
            // the confirmed-good v0.78.0 behavior, and manual zoom has real room to move in either
            // direction on top of it.
            double base = Mth.clamp(g.thirdPersonFreeDistance * 0.68f, 1.9f, 3.3f);
            return Mth.clamp(base + emoteZoomOffset, EMOTE_DISTANCE_ABS_MIN, EMOTE_DISTANCE_ABS_MAX);
        }
        // D122: tightened from 0.6/1.3 â€” feedback: "quiero que...sea mas como al hombro del
        // personaje, tipo shooter tercera persona que cuando apuntan la camara la ponen casi al
        // hombro". 0.4/0.9 reads as genuinely close (The Division/Gears-style ADS) rather than just
        // "a bit nearer than usual".
        if (aiming) return Math.max(0.9, g.thirdPersonFreeDistance * 0.4);
        // Mounted gets its own pull-back ("solo aleja un poco mas la camara cuando esta montado"): a
        // horse or a boat fills much more of the frame than a player does, so the on-foot distance
        // frames a mount too tightly. Deliberately checked AFTER aiming — charging a bow should still
        // pull the camera in close whether you're on foot or on a horse — and after emoting, which
        // owns the framing outright while a dance plays.
        if (mounted) return g.thirdPersonMountedCameraDistance;
        return g.thirdPersonFreeDistance;
    }

    /**
     * The lateral (X) offset-ratio target for this frame â€” pure function of state, same testability
     * rationale as {@link #distanceTargetFor} (points 2/6, D120).
     *
     * <p>A real emote centres the camera outright â€” a dance framed off to one side, with the
     * functional crosshair still showing, doesn't make sense mid-animation. Aiming blends toward the
     * EXISTING {@code thirdPersonAimingOffset} setting (already user-facing in Ajustes Globales,
     * already documented for "closer/more centered while charging a ranged weapon" â€” previously wired
     * only into the legacy offset-only {@link #computeOffset}, never into this, the free-look path).
     *
     * <p><b>CENTER is excluded from the aiming blend on purpose</b> â€” caught by
     * {@code ThirdPersonCameraControllerLogicTest} during this very round before it shipped: the first
     * draft treated "not LEFT" as "RIGHT", which pushed the camera off to the right the instant you
     * aimed even with the side set to CENTER. The legacy {@link #computeOffset}'s own aiming profile
     * has the same rule for the same reason â€” aiming only ever pulls an EXISTING left/right offset IN
     * toward centre, it never introduces one from a dead-centre start.
     */
    static double offsetTargetXFor(GlobalConfig g, boolean emoting, boolean aiming, double adjustOffsetX) {
        if (emoting) return 0.0;
        if (aiming && g.thirdPersonCameraSide != GlobalConfig.ThirdPersonCameraSide.CENTER) {
            // D122: floored at MIN_AIMING_OFFSET_RATIO â€” a user with a conservative general
            // thirdPersonAimingOffset (default 30% â‰ˆ ratio 0.10) would otherwise get a barely-there
            // shift while aiming. The configured value still WINS if it's larger, so this only ever
            // raises the floor, never overrides someone who deliberately wants an even bigger shift.
            double configuredRatio = Mth.clamp(g.thirdPersonAimingOffset, 0f, 1.5f) * BLOCKS_TO_OFFSET_RATIO;
            double aimRatio = Math.max(configuredRatio, MIN_AIMING_OFFSET_RATIO);
            return g.thirdPersonCameraSide == GlobalConfig.ThirdPersonCameraSide.LEFT ? -aimRatio : aimRatio;
        }
        return sideOffsetRatio(g) + adjustOffsetX;
    }

    /** Vanilla's own currently-rendered vertical FOV in degrees â€” includes the runtime adjustments
     *  (sprinting, speed effect, drawn bow) that the raw options slider does not. Falls back to the
     *  slider if the invoker is somehow unavailable. */
    private static double currentFovDegrees(Minecraft mc, Camera camera, float tickDelta) {
        try {
            return ((dev.steampad.mixin.GameRendererFovInvoker) mc.gameRenderer)
                    .steampad$getFov(camera, tickDelta, true);
        } catch (Throwable ignored) {
            return mc.options.fov().get();
        }
    }

    /** World-space UP vector for a vanilla-convention yaw/pitch â€” the screen's own up, which tilts
     *  with pitch (at pitch 0 it is (0,1,0); looking straight up it becomes the horizontal forward). */
    private static Vec3 upFromYawPitch(double yawDeg, double pitchDeg) {
        double yawRad = Math.toRadians(yawDeg);
        double pitchRad = Math.toRadians(pitchDeg);
        double sinP = Math.sin(pitchRad);
        return new Vec3(Math.sin(yawRad) * sinP, Math.cos(pitchRad), -Math.cos(yawRad) * sinP);
    }

    /**
     * Pulls the camera in until nothing is between it and the player, probing from the 8 corners of a
     * small cube around the orbit point rather than from its centre alone.
     *
     * <p>A single centre ray is what let the old camera clip through the edge of a wall or the corner
     * of a doorway: the ray itself passes cleanly through the gap while the camera's actual near
     * plane â€” which has width â€” does not. Casting the same ray from all 8 corners and keeping the
     * shortest hit gives the ray the thickness the camera really has. Uses {@code VISUAL} shapes to
     * match the reference, so the camera is also stopped by blocks that block sight but not movement.
     */
    private static Vec3 clampAgainstWalls(Minecraft mc, Vec3 rotateCenter, Vec3 camPos) {
        Vec3 toCamera = camPos.subtract(rotateCenter);
        double full = toCamera.length();
        if (full < 1e-5) return camPos;

        double limit = full;
        for (int i = 0; i < 8; i++) {
            double ox = WALL_DETECTION * ((i & 1) * 2 - 1);
            double oy = WALL_DETECTION * ((i >> 1 & 1) * 2 - 1);
            double oz = WALL_DETECTION * ((i >> 2 & 1) * 2 - 1);

            Vec3 from = rotateCenter.add(ox, oy, oz);
            Vec3 to = from.add(toCamera);
            BlockHitResult hit = mc.level.clip(new ClipContext(from, to,
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) {
                limit = Math.min(limit, hit.getLocation().distanceTo(from));
            }
        }
        if (limit >= full) return camPos;

        // Keep the smoothed distance in step with the clamp, so leaving the wall eases back out from
        // where the camera actually is instead of snapping from where it merely wanted to be.
        smoothDistance.setValue(Math.max(0.0, limit - bodyRadius(mc)));
        return rotateCenter.add(toCamera.scale(limit / full));
    }

    /** The free camera's current sightline hit result (block or entity) â€” what the crosshair shows
     *  and what {@code ThirdPersonPickMixin} redirects attack/use/mine targeting to. */
    public static HitResult currentHitResult() { return lastHitResult; }

    /** The free camera's own current world position, or {@code null} if it hasn't been seeded this
     *  session (see {@link #freeInitialized}) â€” e.g. still in first person. Used by
     *  {@code ThirdPersonEntityTargetMixin} to redirect vanilla's OWN entity-targeting raycast
     *  ({@code GameRenderer.findCrosshairTarget}) onto the free camera's sightline; see that mixin's
     *  doc for why {@link #currentHitResult()} alone isn't enough to make "mira funcional" work. */
    public static Vec3 currentCameraPos() { return freeInitialized ? lastCameraPos : null; }

    /** The free camera's own current look direction, or {@code null} if not seeded yet â€” see
     *  {@link #currentCameraPos()}. Same vanilla-convention forward vector {@link #pick} already
     *  raycasts along. */
    public static Vec3 currentLookVec() {
        return freeInitialized ? directionFromYawPitch(freeYaw, freePitch) : null;
    }

    /** The free camera's own current yaw/pitch (degrees, vanilla convention), or {@code NaN} if not
     *  seeded â€” see {@link #currentCameraPos()}. Lets {@link AimAssistController} compute its
     *  camera-relative target offset against the direction {@link #turnFreeCamera} will actually
     *  nudge (point 1/D120), instead of the player's body facing, which free-look deliberately
     *  decouples from the camera. */
    public static double currentFreeYaw() { return freeInitialized ? freeYaw : Double.NaN; }
    public static double currentFreePitch() { return freeInitialized ? freePitch : Double.NaN; }

    /** True while the free camera is the thing aim assist should target against this frame: free-look
     *  is on, initialized, and neither first-person nor mirror nor mounted (all three leave the stick
     *  driving the player's own body â€” see {@link CameraController#frame}, D120/D123/D171) â€” aiming
     *  from horseback should target where the player is actually facing, not the chase camera's own
     *  (deliberately lagging) sightline. */
    public static boolean isFreeCameraActive(Minecraft mc) {
        return isFreeLookEnabled() && freeInitialized
                && !mc.options.getCameraType().isFirstPerson()
                && !isMirrorMode(mc)
                && !isMounted(mc);
    }

    /** Block-then-entity raycast from an arbitrary origin/direction â€” the free camera's own sightline
     *  instead of the player's actual eye/facing, using vanilla's real interaction reach so this never
     *  grants a longer reach than playing in first person would. */
    private static HitResult pick(Minecraft mc, Vec3 from, Vec3 forward) {
        // The ray starts at the CAMERA, which sits behind the player â€” so the player's own
        // interaction range alone is not enough to reach as far as they could in first person. The
        // camera's setback (body radius + current distance) has to be added back, exactly as the
        // reference does (bodyRadius + smoothDistance + trace length).
        //
        // FIXED in v0.76.0 (D116): the old code used the bare interaction range from the camera
        // position, silently costing roughly the whole camera distance â€” about four blocks of reach
        // at the default setting â€” which is a large part of why "mira funcional" felt like it was
        // failing to hit things that were plainly under the crosshair. Reach measured FROM THE PLAYER
        // is still exactly vanilla's; nothing here grants more than first person would.
        double reach = Math.max(mc.player.blockInteractionRange(), mc.player.entityInteractionRange());
        double setback = bodyRadius(mc) + smoothDistance.get();
        double range = setback + reach;
        Vec3 to = from.add(forward.scale(range));

        BlockHitResult blockHit = mc.level.clip(new ClipContext(from, to,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        double blockDist = blockHit.getType() == HitResult.Type.MISS ? range : from.distanceTo(blockHit.getLocation());

        // Vanilla's own entity raycast instead of a hand-rolled scan: it applies the same margin and
        // the same nearest-hit rules the game itself uses, so a shot that looks like it connects in
        // third person connects for exactly the same reasons it would in first person. Capped just
        // past the block hit so an entity behind a wall can never win.
        AABB search = new AABB(from, to).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(mc.player, from, to, search,
                ent -> !ent.isSpectator() && ent.isPickable(), Math.min(range, blockDist + 1.0));
        if (entityHit != null && from.distanceTo(entityHit.getLocation()) < blockDist) {
            return entityHit;
        }
        return blockHit;
    }

    /** Nearest living entity within a tight cone of the free camera's sightline while aiming a ranged
     *  weapon â€” same distanceÂ²Ã—angle^2.5 cost shape as {@link AimAssistController}'s own target score,
     *  reused here so the player's body (and therefore the actual shot) faces the intended target
     *  instead of just wherever the camera happens to look. */
    private static Entity predictTargetEntity(Minecraft mc) {
        if (lastHitResult != null && lastHitResult.getType() == HitResult.Type.BLOCK) return null;
        Vec3 camDir = directionFromYawPitch(freeYaw, freePitch).normalize();

        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        AABB search = new AABB(lastCameraPos, lastCameraPos.add(camDir.scale(48))).inflate(4.0);
        for (Entity e : mc.level.getEntities(mc.player, search,
                ent -> ent instanceof LivingEntity le && le.isAlive() && !ent.isSpectator())) {
            double dist = e.distanceTo(mc.player);
            if (dist < 2 || dist > 48) continue;
            Vec3 to = e.getBoundingBox().getCenter().subtract(lastCameraPos);
            double len = to.length();
            if (len < 1e-5) continue;
            double dot = Mth.clamp(camDir.dot(to.scale(1.0 / len)), -1.0, 1.0);
            double angleDeg = Math.toDegrees(Math.acos(dot));
            if (angleDeg > PREDICT_CONE_DEG) continue;
            double score = Math.pow(len, 2) * Math.pow(Math.max(0.01, angleDeg), 2.5);
            if (score < bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    /**
     * Body-facing "rotate strategy" â€” call once per CLIENT TICK (not per frame). Aiming a ranged
     * weapon or actively interacting always wins (that's what makes "shoot like first-person" work
     * regardless of the idle mode); otherwise the configured {@link GlobalConfig.ThirdPersonRotateMode}
     * governs, and only while the player is idle (see this class's own scope note on why moving is
     * left untouched).
     */
    public static void tickRotateStrategy(Minecraft mc, boolean interacting) {
        if (!isFreeLookEnabled() || mc.player == null || mc.level == null) return;
        if (mc.options.getCameraType().isFirstPerson() || !freeInitialized) return;
        // Mirror mode (D123): the stick drives the BODY directly there (vanilla front-view
        // convention) and the camera trails the body's front â€” auto-facing the body toward the free
        // camera's pick would fight the player's own stick every tick.
        if (isMirrorMode(mc)) return;
        // Mounted (D171): this method turns the BODY to face the free camera/crosshair while idle —
        // while mounted, freeYaw only ever CHASES the mount's own yaw (see computeFreePoseInner's
        // mounted block), so letting this pull the body toward freeYaw right back would fight that
        // chase and risks slow oscillation, on top of quietly reintroducing "looking around redirects
        // the mount" through a second mechanism (feedback: "solo cosmetico... no afectamos vanilla").
        if (isMounted(mc)) return;
        // While emoting, the camera orbits freely (isFreeLookEnabled() above already covers that),
        // but the DANCER's own body orientation must stay exactly where the emote left it â€” turning
        // the body to face wherever the free camera happens to be looking would spin the whole
        // character as you orbit, defeating "verlo desde cualquier Ã¡ngulo" (you'd always see the
        // same side). Skip entirely; the orbit itself doesn't depend on this method at all.
        if (EmoteAnimator.isLocalRealEmotePlaying()) return;
        // Aiming has its OWN entry point now â€” see tickAimFacing's doc for why (point 5/D122: tick
        // ordering). Not handled here at all, so calling both in the same tick (which the dispatcher
        // still does, unchanged) never double-applies anything meaningfully different â€” this method
        // simply has nothing left to do for that case.
        if (isAiming(mc)) return;

        GlobalConfig g = ConfigManager.getGlobal();
        Vec3 eye = mc.player.getEyePosition();

        if (interacting) {
            faceDirection(mc, lastHitResult == null ? null : lastHitResult.getLocation().subtract(eye), false);
            return;
        }

        boolean moving = Math.abs(dev.steampad.input.ControllerInputState.forward()) > 0.02f
                || Math.abs(dev.steampad.input.ControllerInputState.sideways()) > 0.02f;
        if (moving) return;   // vanilla's own body-relative movement, untouched â€” see scope note above

        switch (g.thirdPersonRotateMode) {
            case FOLLOW_CAMERA -> faceDirection(mc, directionFromYawPitch(freeYaw, freePitch), false);
            case INTEREST_POINT ->
                    faceDirection(mc, lastHitResult == null ? null : lastHitResult.getLocation().subtract(eye), false);
            case NONE -> { /* body stays exactly where it is */ }
        }
    }

    /**
     * Aiming-only body-facing correction (point 5/D122) â€” split out of {@link #tickRotateStrategy} and
     * called separately, EARLY in the client tick (see {@code SteamPadClient}'s
     * {@code ClientTickEvents.START_CLIENT_TICK} registration), instead of from the dispatcher's
     * existing {@code END_CLIENT_TICK}-based tick.
     *
     * <p><b>Why this needed its own, earlier entry point.</b> Every other piece of SteamPad's own input
     * dispatch runs on {@code END_CLIENT_TICK} â€” deliberately, so it reacts to a fully-settled tick.
     * But vanilla's own "release the bow, compute the shot direction from the player's CURRENT
     * rotation, spawn the arrow" happens as part of {@code MinecraftClient.tick()}'s own body, which
     * runs BEFORE {@code END_CLIENT_TICK} fires. The reported symptom â€” "cuando esta quieto va donde
     * seÃ±ala el crosshair pero si...estoy caminando va hacia otro lado" â€” is the signature of that
     * ordering gap: the correction from tick N only became visible at the very END of tick N, so
     * vanilla's own release-and-fire processing for tick N (which runs near the START of that same
     * tick) still saw the yaw/pitch left over from the END of tick N-1 â€” one whole tick stale. Standing
     * still, a one-tick-old target is imperceptibly different from the current one; walking, both the
     * player's own position and a predicted moving target shift enough per tick that the staleness
     * reads as "the shot went somewhere else".
     *
     * <p>Moving JUST the aiming correction to {@code START_CLIENT_TICK} means it runs before vanilla's
     * own tick body this tick, so a release-triggered fire later in the SAME tick sees this tick's
     * fresh correction, not last tick's. Every input this reads (the physical draw-the-bow state,
     * predictive target, world entities) is already valid at the very start of a tick â€” none of it
     * depends on anything else in {@code tickInGame} having run first â€” so moving only this piece
     * carries no ordering risk of its own. The interacting/idle-rotate-mode branches deliberately stay
     * exactly where they already work today, in {@link #tickRotateStrategy} at {@code END_CLIENT_TICK}
     * â€” this is a narrow, single-purpose extraction, not a wholesale reordering of the input pipeline.
     */
    public static void tickAimFacing(Minecraft mc) {
        if (!isFreeLookEnabled() || mc.player == null || mc.level == null) return;
        if (mc.options.getCameraType().isFirstPerson() || !freeInitialized) return;
        if (isMirrorMode(mc)) return;   // mirror mode: body is stick-driven (D123)
        // Mounted (D171): same reasoning as tickRotateStrategy just above — aiming from horseback
        // should work exactly like vanilla (the stick already drives player.turn() directly), not
        // get redirected toward the chase camera's own (deliberately lagging) pick.
        if (isMounted(mc)) return;
        if (EmoteAnimator.isLocalRealEmotePlaying()) return;
        if (!isAiming(mc)) return;

        GlobalConfig g = ConfigManager.getGlobal();
        Vec3 eye = mc.player.getEyePosition();
        Entity predicted = g.thirdPersonPredictiveAim ? predictTargetEntity(mc) : null;
        Vec3 aimAt = predicted != null ? predicted.getBoundingBox().getCenter()
                : (lastHitResult != null ? lastHitResult.getLocation() : null);
        faceDirection(mc, aimAt == null ? null : aimAt.subtract(eye), true);
    }

    private static void faceDirection(Minecraft mc, Vec3 towards, boolean hard) {
        if (towards == null || towards.lengthSqr() < 1e-6) return;
        double targetYaw = yawFromDirection(towards);
        double targetPitch = Mth.clamp(pitchFromDirection(towards), -90, 90);

        if (hard || Double.isNaN(smoothedBodyYaw)) {
            mc.player.setYRot((float) targetYaw);
            mc.player.setXRot((float) targetPitch);
            smoothedBodyYaw = targetYaw;
            return;
        }
        long now = System.nanoTime();
        double dt = lastRotateTickNanos == 0L ? (1.0 / 20.0) : (now - lastRotateTickNanos) / 1_000_000_000.0;
        lastRotateTickNanos = now;
        if (dt <= 0 || dt > 0.25) dt = 1.0 / 20.0;
        double factor = 1.0 - Math.pow(2.0, -dt / BODY_ROTATE_HALFLIFE_SEC);

        double deltaYaw = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
        mc.player.setYRot((float) (mc.player.getYRot() + deltaYaw * factor));
        double newPitch = mc.player.getXRot() + (targetPitch - mc.player.getXRot()) * factor;
        mc.player.setXRot((float) Mth.clamp(newPitch, -90, 90));
        smoothedBodyYaw = mc.player.getYRot();
    }

    // ================================================================================================
    // CAMERA-RELATIVE MOVEMENT â€” opt-in on top of free-look (off by default). Deliberately NOT part of
    // the idle rotate-strategy above: this governs facing WHILE moving, called from KeyboardInputMixin
    // itself, once per client tick, in the SAME tick vanilla's own body-relative movement math runs in
    // â€” see that mixin's own comment for why the ordering makes this safe. With the config flag off,
    // KeyboardInputMixin's existing code path is completely untouched.
    // ================================================================================================

    private static final double MOVEMENT_FACE_HALFLIFE_SEC = 0.08;
    private static long lastMovementFaceNanos = 0L;

    /** Checked by KeyboardInputMixin before reinterpreting stick input as camera-relative. Mirror
     *  mode is excluded (D123): the camera there faces the PLAYER, so "away from the camera" would
     *  invert forward/back â€” vanilla body-relative movement is the only sane mapping in that view.
     *  Mounted is excluded too (D171): this turns the player's body to face the LEFT stick's world
     *  direction, and since a ridden mount copies the player's yaw every tick, letting this fire while
     *  mounted would silently reintroduce steering-by-a-second-mechanism â€” exactly what the mounted
     *  chase camera was built to stay clear of ("solo cosmetico... no afectamos vanilla"). */
    public static boolean isCameraRelativeMovementActive() {
        Minecraft mc = Minecraft.getInstance();
        return isFreeLookEnabled() && freeInitialized
                && !isMirrorMode(mc)
                && !isMounted(mc)
                && ConfigManager.getGlobal().thirdPersonCameraRelativeMovement;
    }

    /**
     * Reinterprets raw left-stick input (side, fwd â€” vanilla's own body-relative convention) as
     * relative to the FREE CAMERA's yaw instead of the player's body ("push forward" = away from the
     * camera), turns the body to face that world direction, and returns the (side, fwd) the caller
     * should actually feed vanilla: always {0, magnitude}, since the body now faces the intended
     * direction directly and vanilla's own untouched movement math reproduces it exactly.
     */
    public static float[] applyCameraRelativeMovement(net.minecraft.client.player.LocalPlayer player,
                                                       float side, float fwd) {
        // AIMING A RANGED WEAPON: the body must NOT be turned by movement. tickAimFacing has already
        // pointed it at the crosshair this tick (that is the only way a projectile can fly there — the
        // server spawns the arrow from the player's own rotation), and rotating it here toward the
        // walk direction silently undid that a few lines later in the same tick. That is exactly the
        // reported behaviour: standing still the arrow followed the crosshair (this method early-outs
        // at mag < 1e-4 and never rotates), but moving the stick sent it toward the movement direction
        // instead ("no se dirige a donde apunto con el Crosshair si estoy moviendo el stick").
        // Instead of turning the body, express the SAME desired world direction in the body's current
        // (aim-locked) frame, so the player still strafes/walks exactly where the stick points while
        // staying aimed at the crosshair.
        if (isAiming(Minecraft.getInstance())) {
            return bodyRelativeMove(freeYaw, player.getYRot(), side, fwd);
        }
        double yawRad = Math.toRadians(freeYaw);
        double worldX = side * Math.cos(yawRad) - fwd * Math.sin(yawRad);
        double worldZ = side * Math.sin(yawRad) + fwd * Math.cos(yawRad);
        double mag = Math.min(1.0, Math.sqrt(worldX * worldX + worldZ * worldZ));
        if (mag < 1e-4) return new float[]{0f, 0f};

        double targetYaw = Math.toDegrees(Math.atan2(-worldX, worldZ));
        long now = System.nanoTime();
        double dt = lastMovementFaceNanos == 0L ? (1.0 / 20.0) : (now - lastMovementFaceNanos) / 1_000_000_000.0;
        lastMovementFaceNanos = now;
        if (dt <= 0 || dt > 0.25) dt = 1.0 / 20.0;
        double factor = 1.0 - Math.pow(2.0, -dt / MOVEMENT_FACE_HALFLIFE_SEC);
        double deltaYaw = Mth.wrapDegrees(targetYaw - player.getYRot());
        player.setYRot((float) (player.getYRot() + deltaYaw * factor));

        return new float[]{0f, (float) mag};
    }

    /**
     * Camera-relative movement WITHOUT turning the body: converts a stick push, interpreted relative to
     * the camera, into the (side, forward) pair vanilla needs for a body that is facing some other way.
     *
     * <p>Pure math, no Minecraft state — deliberately extracted so it can be unit-tested (the same
     * approach that caught a sign error in this class's camera math before it ever compiled). Uses the
     * exact rotation convention {@link #applyCameraRelativeMovement} already uses to build the world
     * vector ({@code worldX = side·cos y − fwd·sin y}, {@code worldZ = side·sin y + fwd·cos y}); this
     * simply applies that rotation's inverse for the body's yaw, so the resulting world motion is
     * identical while the body stays put.
     *
     * @param camYawDeg  yaw the stick is interpreted against (the free camera's)
     * @param bodyYawDeg the player's actual yaw, which this must NOT change
     * @return {@code {side, forward}} to hand vanilla, magnitude-clamped to 1
     */
    static float[] bodyRelativeMove(double camYawDeg, double bodyYawDeg, float side, float fwd) {
        double cam = Math.toRadians(camYawDeg);
        double worldX = side * Math.cos(cam) - fwd * Math.sin(cam);
        double worldZ = side * Math.sin(cam) + fwd * Math.cos(cam);
        double mag = Math.sqrt(worldX * worldX + worldZ * worldZ);
        if (mag < 1e-4) return new float[]{0f, 0f};
        if (mag > 1.0) { worldX /= mag; worldZ /= mag; }
        double body = Math.toRadians(bodyYawDeg);
        double outSide = worldX * Math.cos(body) + worldZ * Math.sin(body);
        double outFwd = -worldX * Math.sin(body) + worldZ * Math.cos(body);
        return new float[]{(float) outSide, (float) outFwd};
    }
}
