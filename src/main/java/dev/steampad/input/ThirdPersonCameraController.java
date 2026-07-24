package dev.steampad.input;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.GlobalConfig;
import dev.steampad.emote.EmoteAnimator;
import dev.steampad.util.LogUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.consume.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Over-the-shoulder camera offset for third person — design credited to Leawind's
 * <a href="https://github.com/Leawind/Third-Person">Third-Person</a> mod (MIT license): its "quick
 * camera positioning" (toggle left/center/right), aiming-mode camera profile (closer/more centered
 * while charging a ranged weapon), and halflife-smoothed transitions are the pieces this reproduces
 * natively.
 *
 * <p>This is a deliberately narrow reimplementation, not a line-for-line port. The original mod
 * cancels vanilla's entire {@code Camera#setup} and replaces it with its own camera that ALSO
 * decouples camera rotation from the player's body rotation (5 configurable "player rotate modes" —
 * free-look, essentially). That piece touches the single most regression-prone system in this
 * project's own history (STATE.md's long run of camera bugs, D046-D053) at its input-coupling root,
 * not just its rendering — it would mean overriding how look input turns the player entity itself
 * everywhere that currently assumes camera-yaw == player-yaw ({@link CameraController}, aim assist,
 * zoom, movement direction). Deliberately NOT attempted here — see DECISIONS.md for the honest scope
 * note and why it needs its own dedicated session with real hardware checkpoints, not a fold-in.
 *
 * <p>Everything else is safe to reproduce: this hooks the END of {@code Camera#update} (a TAIL
 * inject — see {@code dev.steampad.mixin.ThirdPersonCameraMixin}) and nudges the ALREADY
 * vanilla-computed, already collision-clipped position sideways along the camera's own right vector,
 * with the offset amount halflife-smoothed toward a target that depends on side (L/C/R) and whether
 * the player is currently aiming a ranged weapon.
 */
public final class ThirdPersonCameraController {

    private ThirdPersonCameraController() {}

    /** Halflife (seconds) for the smoothed offset amount — same "critically-damped exponential
     *  toward target" shape the reference mod uses throughout (ExpSmoothDouble), reimplemented from
     *  the well-documented public technique, not copied code: {@code factor = 1 - 2^(-dt/halflife)}. */
    private static final double SMOOTH_HALFLIFE_SEC = 0.15;

    private static double smoothedAmount = 0.0;
    private static long lastFrameNanos = 0L;

    /** Cycles LEFT → CENTER → RIGHT → LEFT (Botones-bindable action, mirrors the original mod's
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

    /** True while the player is charging a bow/crossbow/trident — the same UseAction check
     *  {@link AimAssistController} already uses, reused here for the aiming camera profile. */
    private static boolean isAiming(MinecraftClient mc) {
        if (mc.player == null || !mc.player.isUsingItem()) return false;
        UseAction action = mc.player.getActiveItem().getUseAction();
        return action == UseAction.BOW || action == UseAction.CROSSBOW || action == UseAction.SPEAR;
    }

    /**
     * The world-space delta to ADD to the vanilla third-person camera position this frame, or
     * {@link Vec3d#ZERO} when the feature is off, centered, or the focused entity isn't the player.
     *
     * @param camera        the camera, already fully positioned/rotated by vanilla this call
     * @param focusedEntity vanilla's own {@code update()} parameter — only offset when it's the
     *                      local player's own camera (never in spectator-of-another-entity, etc.)
     */
    public static Vec3d computeOffset(MinecraftClient mc, Camera camera, Entity focusedEntity) {
        if (!camera.isThirdPerson() || focusedEntity == null) return Vec3d.ZERO;
        if (mc.player == null || focusedEntity != mc.player) return Vec3d.ZERO;

        GlobalConfig g = ConfigManager.getGlobal();
        if (!g.thirdPersonCameraEnabled) return Vec3d.ZERO;

        // Target offset magnitude for this frame: 0 while centered, otherwise the configured amount —
        // eased down toward the closer aiming profile while charging a ranged weapon.
        double target = g.thirdPersonCameraSide == GlobalConfig.ThirdPersonCameraSide.CENTER
                ? 0.0 : Math.max(0f, Math.min(1.5f, g.thirdPersonCameraOffset));
        if (g.thirdPersonAimingProfile && isAiming(mc) && target != 0.0) {
            target = Math.min(target, Math.max(0f, Math.min(1.5f, g.thirdPersonAimingOffset)));
        }
        if (g.thirdPersonCameraSide == GlobalConfig.ThirdPersonCameraSide.LEFT) target = -target;

        smoothedAmount = smoothTowards(smoothedAmount, target);
        double amount = smoothedAmount;
        if (Math.abs(amount) < 0.001) return Vec3d.ZERO;

        // Camera-right vector in the horizontal plane (yaw only — the shoulder offset stays level
        // regardless of pitch). See rightVectorXZ() for the handedness fix (D093/tp-3).
        double[] r = rightVectorXZ(camera.getYaw());
        double rightX = r[0], rightZ = r[1];

        Vec3d base = camera.getPos();
        Vec3d end = base.add(rightX * amount, 0, rightZ * amount);

        if (mc.world == null) return end.subtract(base);
        BlockHitResult hit = mc.world.raycast(new RaycastContext(base, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (hit.getType() == HitResult.Type.MISS) return end.subtract(base);

        // Something's in the way — clamp the offset to just short of the hit, never past it.
        double safeDist = Math.max(0.0, base.distanceTo(hit.getPos()) - 0.15);
        double ratio = safeDist / Math.abs(amount);
        return end.subtract(base).multiply(ratio);
    }

    /** Critically-damped exponential ease of {@code smoothedAmount} toward {@code target}, at
     *  {@link #SMOOTH_HALFLIFE_SEC} — frame-rate independent (uses real elapsed time, clamped against
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
     * of pitch), as {x, z}. Shared by {@link #computeOffset} and {@link #computeFreePose} — both
     * offset the camera sideways along this exact vector, so a handedness bug in one is a handedness
     * bug in both.
     *
     * <p><b>Fixed in v0.56.0 (D093) after real-hardware testing showed "Izquierda" visually moving the
     * camera to the RIGHT (checklist tp-3):</b> the previous formula, {@code (cos(yaw), sin(yaw))}, is
     * actually the camera's LEFT vector, not its right — confirmed three independent ways: (1) at
     * yaw=0 (facing south, vanilla convention) a player's true right hand points west (-X), but
     * {@code (cos 0, sin 0) = (1, 0)} = east; (2) at yaw=90 (facing west) true right points north
     * (-Z), but the old formula gives south (0, 1); (3) the standard right-hand-rule camera basis
     * {@code right = forward × up} — with {@code forward = (-sin(yaw), 0, cos(yaw))} (this class's own
     * {@link #directionFromYawPitch} at pitch 0) and {@code up = (0, 1, 0)} — works out to
     * {@code (-cos(yaw), 0, -sin(yaw))}, the exact negation of the old formula. All three agree, so
     * the fix is a plain sign flip, not a different formula.
     *
     * <p>This is UNRELATED to (and does not affect) {@link #applyCameraRelativeMovement}'s own use of
     * {@code (cos(yaw), sin(yaw))} for the sideways movement AXIS — that formula is verified
     * byte-for-byte against vanilla's own {@code Entity.movementInputToVelocity}, which apparently
     * treats vanilla's own "positive sideways input" as the LEFT direction. Two different questions
     * ("where is the camera's right shoulder" vs. "which world direction does vanilla's own sideways
     * input axis point") that happen to share a formula shape but need opposite signs — touching one
     * must never be assumed to fix or break the other.
     */
    private static double[] rightVectorXZ(double yawDeg) {
        double yawRad = Math.toRadians(yawDeg);
        return new double[]{-Math.cos(yawRad), -Math.sin(yawRad)};
    }

    // ================================================================================================
    // FREE CAMERA — the rest of Leawind's Third-Person feature set: free rotation decoupled from the
    // player's body, a functional/predictive crosshair, and live offset/distance adjustment. Fully
    // independent of the offset-only path above; {@code ThirdPersonCameraMixin} picks ONE path per
    // frame from {@code GlobalConfig.thirdPersonFreeLookEnabled} — the offset-only path is completely
    // unchanged when this is off, zero regression risk for anyone who already validated it.
    //
    // Deliberate simplifications vs. the reference mod (see DECISIONS.md for the full honest note):
    //  - Camera offset is a world-space nudge along the free camera's own right/up axes (reusing this
    //    file's already-validated raycast-clamped approach), not the reference's FOV-precise
    //    screen-space projection.
    //  - The body-facing "rotate strategy" only acts while the player is IDLE (stick released) —
    //    faithfully reproducing "auto-face movement direction" needs camera-relative movement input,
    //    which means touching the movement-critical KeyboardInputMixin; deliberately left untouched
    //    this round so movement itself carries zero regression risk. While actually moving, the body
    //    keeps behaving exactly like vanilla today, free-look or not.
    //  - Player transparency (cosmetic, off by default even upstream) is NOT implemented — see
    //    TODO_BLOCKERS.md for why.
    // ================================================================================================

    private static final double PITCH_LIMIT = 89.5;
    private static final double MIN_DISTANCE = 0.3;
    private static final double MAX_DISTANCE = 6.0;
    private static final double DISTANCE_SMOOTH_HALFLIFE_SEC = 0.15;
    private static final double ADJUST_SMOOTH_HALFLIFE_SEC = 0.05;
    private static final double BODY_ROTATE_HALFLIFE_SEC = 0.12;
    /** Half-angle (degrees) of the cone used to find a predicted ranged-weapon target — matches the
     *  reference mod's own tight prediction cone. */
    private static final double PREDICT_CONE_DEG = 12.0;

    private static double freeYaw = 0.0;
    private static double freePitch = 0.0;
    private static boolean freeInitialized = false;

    private static double smoothedOffsetX = 0.0;   // lateral, along the camera's own right vector
    private static double smoothedOffsetY = 0.0;   // vertical
    private static double smoothedDistance = 4.0;
    private static long lastFreeFrameNanos = 0L;
    private static boolean adjusting = false;

    /** The free camera's own last-computed world position — refreshed every {@link #computeFreePose}
     *  call, read back by the (tick-rate) rotate strategy and predictive-aim scan. One render frame
     *  stale at most; imperceptible for either purpose. */
    private static Vec3d lastCameraPos = Vec3d.ZERO;
    /** This render tick's hit result from the free camera's own sightline — what the crosshair shows
     *  and what attack/use/mine actually targets. Null until the first frame computes it. */
    private static HitResult lastHitResult = null;
    private static long lastPickMs = 0L;
    /** Roughly one client tick — the pick (block raycast + entity scan, see {@link #pick}) recomputes
     *  at most this often instead of every render frame. See the call site's own comment for why. */
    private static final long PICK_THROTTLE_MS = 50L;

    private static double smoothedBodyYaw = Double.NaN;
    private static long lastRotateTickNanos = 0L;

    /**
     * True while the free/orbit camera path should be used this frame — either the user's own
     * persisted toggle, OR the local player is running a GENUINE emote (v0.60.0/D100: "que durante
     * la animación se desbloquee en tercera persona para que pueda girar alrededor… verlo desde
     * cualquier ángulo"). Deliberately checks {@link EmoteAnimator#isLocalRealEmotePlaying}, NOT the
     * broader {@code isLocalPlaying} — the latter is also true while the Emote Library/Wheel merely
     * shows a live PREVIEW thumbnail (no dance actually confirmed yet), and engaging the free camera
     * just from browsing those menus would be an unrequested, surprising side effect. The emote case
     * is a pure per-frame OR, never written back to {@link GlobalConfig#thirdPersonFreeLookEnabled} —
     * the user's own setting is untouched, and the moment the emote ends this reverts to exactly
     * what it was before, with no state to clean up. Every free-look call site (this class, the
     * camera mixin, the crosshair/pick mixins) reads ONLY this method, so the emote override reaches
     * all of them uniformly. See {@link #tickRotateStrategy} for why the player's BODY must still
     * stay put during an emote even though the camera itself is now free to orbit.
     */
    public static boolean isFreeLookEnabled() {
        return ConfigManager.getGlobal().thirdPersonFreeLookEnabled || EmoteAnimator.isLocalRealEmotePlaying();
    }

    public static void toggleFreeLook() {
        GlobalConfig g = ConfigManager.getGlobal();
        g.thirdPersonFreeLookEnabled = !g.thirdPersonFreeLookEnabled;
        freeInitialized = false;   // re-seed from vanilla's own camera next frame — never a snap
        smoothedBodyYaw = Double.NaN;
        ConfigManager.saveGlobal();
    }

    public static boolean isAdjusting() { return adjusting; }
    public static void beginAdjust() { adjusting = true; }
    public static void endAdjust() { adjusting = false; }

    /** Live offset nudge while THIRD_PERSON_ADJUST is held (right stick repurposed) — dx/dy are plain
     *  per-frame deltas in blocks, already curve-shaped by the caller exactly like camera look is. */
    public static void adjustOffset(double dx, double dy) {
        double max = 1.5;
        smoothedOffsetX = MathHelper.clamp(smoothedOffsetX + dx, -max, max);
        smoothedOffsetY = MathHelper.clamp(smoothedOffsetY + dy, -max, max);
    }

    /** D-pad up/down step while adjusting — dir=+1 pulls the camera closer, -1 pushes it back. */
    public static void adjustDistance(int dir) {
        GlobalConfig g = ConfigManager.getGlobal();
        g.thirdPersonFreeDistance = (float) MathHelper.clamp(
                g.thirdPersonFreeDistance - dir * 0.25, MIN_DISTANCE, MAX_DISTANCE);
        ConfigManager.saveGlobal();
    }

    /** Called from {@link CameraController#frame} INSTEAD OF {@code changeLookDirection} whenever
     *  free-look owns the stick this frame — same yaw/pitch degree units the caller already computed
     *  (aim assist, sensitivity curve, zoom slow-down all already folded in upstream). */
    public static void turnFreeCamera(double yawDeg, double pitchDeg) {
        if (!freeInitialized) return;   // computeFreePose() seeds it first; nothing to turn yet
        freeYaw = MathHelper.wrapDegrees(freeYaw + yawDeg);
        if (!ConfigManager.getGlobal().thirdPersonPitchLock) {
            freePitch = MathHelper.clamp(freePitch + pitchDeg, -PITCH_LIMIT, PITCH_LIMIT);
        }
    }

    /** World-space forward vector for the given vanilla-convention yaw/pitch (degrees). */
    private static Vec3d directionFromYawPitch(double yawDeg, double pitchDeg) {
        double yawRad = Math.toRadians(yawDeg);
        double pitchRad = Math.toRadians(pitchDeg);
        double xz = Math.cos(pitchRad);
        return new Vec3d(-Math.sin(yawRad) * xz, -Math.sin(pitchRad), Math.cos(yawRad) * xz);
    }

    private static double yawFromDirection(Vec3d dir) {
        return Math.toDegrees(Math.atan2(-dir.x, dir.z));
    }

    private static double pitchFromDirection(Vec3d dir) {
        double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        return Math.toDegrees(-Math.atan2(dir.y, horiz));
    }

    private static double expSmooth(double current, double target, double halflifeSec, double dt) {
        double factor = 1.0 - Math.pow(2.0, -dt / halflifeSec);
        return current + (target - current) * factor;
    }

    /** A computed free-camera pose the mixin should apply this frame, or null if free-look shouldn't
     *  run right now (first person, no world/player yet). */
    public record FreePose(Vec3d pos, float yaw, float pitch) {}

    // ---- Performance diagnostics (feedback: "rendimiento pésimo, laggea el juego") ------------------
    //
    // Nothing in this class's own per-frame work looked like an obvious hotspot on inspection (one
    // raycast per frame — the same cost profile vanilla's OWN third-person camera already pays every
    // frame — plus a throttled block/entity pick at roughly tick rate, not frame rate). Rather than
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
     * {@link #computeFreePoseInner} — see the perf-diagnostics note above.
     */
    public static FreePose computeFreePose(MinecraftClient mc, Camera camera) {
        long t0 = System.nanoTime();
        try {
            return computeFreePoseInner(mc, camera);
        } finally {
            recordPerfSample(System.nanoTime() - t0);
        }
    }

    private static FreePose computeFreePoseInner(MinecraftClient mc, Camera camera) {
        if (mc.player == null || mc.world == null) { freeInitialized = false; return null; }
        if (mc.options.getPerspective().isFirstPerson()) { freeInitialized = false; return null; }

        long now = System.nanoTime();
        double dt = lastFreeFrameNanos == 0L ? (1.0 / 60.0) : (now - lastFreeFrameNanos) / 1_000_000_000.0;
        lastFreeFrameNanos = now;
        if (dt <= 0 || dt > 0.25) dt = 1.0 / 60.0;

        GlobalConfig g = ConfigManager.getGlobal();
        if (!freeInitialized) {
            freeYaw = camera.getYaw();
            freePitch = camera.getPitch();
            smoothedDistance = g.thirdPersonFreeDistance;
            freeInitialized = true;
        }

        double halflife = adjusting ? ADJUST_SMOOTH_HALFLIFE_SEC : DISTANCE_SMOOTH_HALFLIFE_SEC;
        smoothedDistance = expSmooth(smoothedDistance, g.thirdPersonFreeDistance, halflife, dt);

        Vec3d forward = directionFromYawPitch(freeYaw, freePitch);
        double[] r = rightVectorXZ(freeYaw);
        double rightX = r[0], rightZ = r[1];   // same shared basis as computeOffset()

        Vec3d rotateCenter = mc.player.getEyePos();
        Vec3d camPos = rotateCenter
                .subtract(forward.multiply(smoothedDistance))
                .add(rightX * smoothedOffsetX, smoothedOffsetY, rightZ * smoothedOffsetX);

        BlockHitResult wallHit = mc.world.raycast(new RaycastContext(rotateCenter, camPos,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (wallHit.getType() != HitResult.Type.MISS) {
            Vec3d dir = camPos.subtract(rotateCenter);
            double full = dir.length();
            if (full > 1e-5) {
                double safe = Math.max(0.1, rotateCenter.distanceTo(wallHit.getPos()) - 0.1);
                if (safe < full) camPos = rotateCenter.add(dir.multiply(safe / full));
            }
        }

        lastCameraPos = camPos;
        // Throttled, not recomputed every render frame: the pick is a block raycast PLUS an entity
        // scan over a search box (see #pick) — real cost at 60-144fps, especially in crowded areas on
        // Deck-class hardware, for a result (what the crosshair/attack targets) that being a few
        // milliseconds stale is completely imperceptible on. Same "cache with a short expiry" shape
        // ControllerManager already uses for device enumeration, just tuned to roughly one tick.
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastPickMs >= PICK_THROTTLE_MS) {
            lastPickMs = nowMs;
            lastHitResult = pick(mc, camPos, forward);
        }
        return new FreePose(camPos, (float) freeYaw, (float) freePitch);
    }

    /** The free camera's current sightline hit result (block or entity) — what the crosshair shows
     *  and what {@code ThirdPersonPickMixin} redirects attack/use/mine targeting to. */
    public static HitResult currentHitResult() { return lastHitResult; }

    /** Block-then-entity raycast from an arbitrary origin/direction — the free camera's own sightline
     *  instead of the player's actual eye/facing, using vanilla's real interaction reach so this never
     *  grants a longer reach than playing in first person would. */
    private static HitResult pick(MinecraftClient mc, Vec3d from, Vec3d forward) {
        double range = Math.max(mc.player.getBlockInteractionRange(), mc.player.getEntityInteractionRange());
        Vec3d to = from.add(forward.multiply(range));

        BlockHitResult blockHit = mc.world.raycast(new RaycastContext(from, to,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        double blockDist = blockHit.getType() == HitResult.Type.MISS ? range : from.distanceTo(blockHit.getPos());

        Box search = new Box(from, to).expand(1.0);
        Entity bestEntity = null;
        double bestDist = blockDist;
        for (Entity e : mc.world.getOtherEntities(mc.player, search,
                ent -> ent instanceof LivingEntity le && le.isAlive() && !ent.isSpectator())) {
            var opt = e.getBoundingBox().expand(0.3).raycast(from, to);
            if (opt.isEmpty()) continue;
            double d = from.distanceTo(opt.get());
            if (d < bestDist) { bestDist = d; bestEntity = e; }
        }
        if (bestEntity != null) return new EntityHitResult(bestEntity, from.add(forward.multiply(bestDist)));
        return blockHit;
    }

    /** Nearest living entity within a tight cone of the free camera's sightline while aiming a ranged
     *  weapon — same distance²×angle^2.5 cost shape as {@link AimAssistController}'s own target score,
     *  reused here so the player's body (and therefore the actual shot) faces the intended target
     *  instead of just wherever the camera happens to look. */
    private static Entity predictTargetEntity(MinecraftClient mc) {
        if (lastHitResult != null && lastHitResult.getType() == HitResult.Type.BLOCK) return null;
        Vec3d camDir = directionFromYawPitch(freeYaw, freePitch).normalize();

        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        Box search = new Box(lastCameraPos, lastCameraPos.add(camDir.multiply(48))).expand(4.0);
        for (Entity e : mc.world.getOtherEntities(mc.player, search,
                ent -> ent instanceof LivingEntity le && le.isAlive() && !ent.isSpectator())) {
            double dist = e.distanceTo(mc.player);
            if (dist < 2 || dist > 48) continue;
            Vec3d to = e.getBoundingBox().getCenter().subtract(lastCameraPos);
            double len = to.length();
            if (len < 1e-5) continue;
            double dot = MathHelper.clamp(camDir.dotProduct(to.multiply(1.0 / len)), -1.0, 1.0);
            double angleDeg = Math.toDegrees(Math.acos(dot));
            if (angleDeg > PREDICT_CONE_DEG) continue;
            double score = Math.pow(len, 2) * Math.pow(Math.max(0.01, angleDeg), 2.5);
            if (score < bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    /**
     * Body-facing "rotate strategy" — call once per CLIENT TICK (not per frame). Aiming a ranged
     * weapon or actively interacting always wins (that's what makes "shoot like first-person" work
     * regardless of the idle mode); otherwise the configured {@link GlobalConfig.ThirdPersonRotateMode}
     * governs, and only while the player is idle (see this class's own scope note on why moving is
     * left untouched).
     */
    public static void tickRotateStrategy(MinecraftClient mc, boolean interacting) {
        if (!isFreeLookEnabled() || mc.player == null || mc.world == null) return;
        if (mc.options.getPerspective().isFirstPerson() || !freeInitialized) return;
        // While emoting, the camera orbits freely (isFreeLookEnabled() above already covers that),
        // but the DANCER's own body orientation must stay exactly where the emote left it — turning
        // the body to face wherever the free camera happens to be looking would spin the whole
        // character as you orbit, defeating "verlo desde cualquier ángulo" (you'd always see the
        // same side). Skip entirely; the orbit itself doesn't depend on this method at all.
        if (EmoteAnimator.isLocalRealEmotePlaying()) return;

        GlobalConfig g = ConfigManager.getGlobal();
        Vec3d eye = mc.player.getEyePos();

        if (isAiming(mc)) {
            Entity predicted = g.thirdPersonPredictiveAim ? predictTargetEntity(mc) : null;
            Vec3d aimAt = predicted != null ? predicted.getBoundingBox().getCenter()
                    : (lastHitResult != null ? lastHitResult.getPos() : null);
            faceDirection(mc, aimAt == null ? null : aimAt.subtract(eye), true);
            return;
        }
        if (interacting) {
            faceDirection(mc, lastHitResult == null ? null : lastHitResult.getPos().subtract(eye), false);
            return;
        }

        boolean moving = Math.abs(dev.steampad.input.ControllerInputState.forward()) > 0.02f
                || Math.abs(dev.steampad.input.ControllerInputState.sideways()) > 0.02f;
        if (moving) return;   // vanilla's own body-relative movement, untouched — see scope note above

        switch (g.thirdPersonRotateMode) {
            case FOLLOW_CAMERA -> faceDirection(mc, directionFromYawPitch(freeYaw, freePitch), false);
            case INTEREST_POINT ->
                    faceDirection(mc, lastHitResult == null ? null : lastHitResult.getPos().subtract(eye), false);
            case NONE -> { /* body stays exactly where it is */ }
        }
    }

    private static void faceDirection(MinecraftClient mc, Vec3d towards, boolean hard) {
        if (towards == null || towards.lengthSquared() < 1e-6) return;
        double targetYaw = yawFromDirection(towards);
        double targetPitch = MathHelper.clamp(pitchFromDirection(towards), -90, 90);

        if (hard || Double.isNaN(smoothedBodyYaw)) {
            mc.player.setYaw((float) targetYaw);
            mc.player.setPitch((float) targetPitch);
            smoothedBodyYaw = targetYaw;
            return;
        }
        long now = System.nanoTime();
        double dt = lastRotateTickNanos == 0L ? (1.0 / 20.0) : (now - lastRotateTickNanos) / 1_000_000_000.0;
        lastRotateTickNanos = now;
        if (dt <= 0 || dt > 0.25) dt = 1.0 / 20.0;
        double factor = 1.0 - Math.pow(2.0, -dt / BODY_ROTATE_HALFLIFE_SEC);

        double deltaYaw = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
        mc.player.setYaw((float) (mc.player.getYaw() + deltaYaw * factor));
        double newPitch = mc.player.getPitch() + (targetPitch - mc.player.getPitch()) * factor;
        mc.player.setPitch((float) MathHelper.clamp(newPitch, -90, 90));
        smoothedBodyYaw = mc.player.getYaw();
    }

    // ================================================================================================
    // CAMERA-RELATIVE MOVEMENT — opt-in on top of free-look (off by default). Deliberately NOT part of
    // the idle rotate-strategy above: this governs facing WHILE moving, called from KeyboardInputMixin
    // itself, once per client tick, in the SAME tick vanilla's own body-relative movement math runs in
    // — see that mixin's own comment for why the ordering makes this safe. With the config flag off,
    // KeyboardInputMixin's existing code path is completely untouched.
    // ================================================================================================

    private static final double MOVEMENT_FACE_HALFLIFE_SEC = 0.08;
    private static long lastMovementFaceNanos = 0L;

    /** Checked by KeyboardInputMixin before reinterpreting stick input as camera-relative. */
    public static boolean isCameraRelativeMovementActive() {
        return isFreeLookEnabled() && freeInitialized
                && ConfigManager.getGlobal().thirdPersonCameraRelativeMovement;
    }

    /**
     * Reinterprets raw left-stick input (side, fwd — vanilla's own body-relative convention) as
     * relative to the FREE CAMERA's yaw instead of the player's body ("push forward" = away from the
     * camera), turns the body to face that world direction, and returns the (side, fwd) the caller
     * should actually feed vanilla: always {0, magnitude}, since the body now faces the intended
     * direction directly and vanilla's own untouched movement math reproduces it exactly.
     */
    public static float[] applyCameraRelativeMovement(net.minecraft.client.network.ClientPlayerEntity player,
                                                       float side, float fwd) {
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
        double deltaYaw = MathHelper.wrapDegrees(targetYaw - player.getYaw());
        player.setYaw((float) (player.getYaw() + deltaYaw * factor));

        return new float[]{0f, (float) mag};
    }
}
