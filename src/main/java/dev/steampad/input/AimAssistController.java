package dev.steampad.input;

import dev.steampad.config.ControllerConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.consume.UseAction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Console-shooter aim assist for AIMED projectiles only — bow, crossbow and trident, i.e. items the
 * player visibly charges before releasing (UseAction BOW/CROSSBOW/SPEAR). Two classic components,
 * both scaled by {@code aimAssistStrength} and fully off via {@code aimAssistEnabled}:
 *
 * <ul>
 *   <li><b>Friction</b> (the big one in COD/BF/Halo): while the reticle is over or near a living
 *       target, camera speed drops — flicking PAST a mob at speed becomes landing ON it.</li>
 *   <li><b>Magnetism</b> (gentle): while the stick is actively deflected, the camera drifts a few
 *       degrees per second toward the target's center. Weak on purpose — it helps track a strafing
 *       mob, it never takes the camera away from the player.</li>
 *   <li><b>Sticky lock</b> (AAA-style, feedback: "tambien funcione cuando estamos parados, no
 *       necesariamente tienes que mover el stick derecho, así funciona en los shooters, BF6 y COD —
 *       se engancha un momentito y se desengancha, si seguimos al objetivo se sigue enganchando"):
 *       once the reticle is already close to dead-center on a target — whether the player steered
 *       there or just happened to already be looking at it — magnetism keeps applying for a short
 *       window ({@link #LOCK_HOLD_MS}) even with a fully idle stick, using a softer at-rest pull than
 *       the actively-steered case. The window re-arms every frame the reticle stays near-centered, so
 *       "still tracking" reads as continuous engagement; letting the target drift out of the tight
 *       inner cone lets the window expire and the pull releases on its own — matching the community
 *       description of COD/BF rotational aim assist ("target sticking"), not a hard aimbot lock.</li>
 * </ul>
 *
 * Target selection: nearest living entity within {@link #RANGE} blocks whose angular offset from the
 * crosshair is inside a distance-scaled cone (closer = wider apparent size), with line of sight
 * required (no assist through walls). All client-side camera shaping — the actual shot is untouched,
 * so this gives no aimbot-grade advantage a mouse flick wouldn't.
 */
public final class AimAssistController {

    /** Max distance (blocks) at which assist engages — matches practical bow-fight range. */
    private static final double RANGE = 28.0;
    /** Base cone half-angle (radians) added on top of the target's apparent angular radius.
     *  Widened from the first cut (2.0°) after hardware feedback that the assist was imperceptible —
     *  a small cone made engagement so rare it read as "not working". */
    private static final double CONE_BASE_RAD = Math.toRadians(3.5);
    /** How much of the target's apparent radius still counts as "near" for friction falloff. */
    private static final double CONE_NEAR_MULT = 2.6;
    /** Camera speed multiplier at dead-center with strength 1.0 (0.30 = firm COD-like slowdown). */
    private static final double FRICTION_FLOOR = 0.30;
    /** Peak magnetism drift toward the target (deg/s) at strength 1.0, full stick, dead-center. */
    private static final double MAGNETISM_DEG_PER_SEC = 16.0;
    /**
     * Projectile-drop compensation: the assist point sits ABOVE the target's center by the arc a
     * full-charge arrow loses over the distance (MC arrow: ~3 blocks/tick, gravity 0.05/tick² →
     * drop ≈ dist² * 0.0028), capped so far targets don't push the point into the sky. This makes
     * the friction/magnetism center match where a projectile shot actually needs to aim — the same
     * idea console shooters use for weapons with travel time.
     */
    private static final double DROP_COMP_PER_DIST_SQ = 0.0028;
    private static final double DROP_COMP_MAX = 2.5;

    /** How long (ms) a sticky lock stays engaged after the last active steer or near-center frame. */
    private static final long LOCK_HOLD_MS = 350L;
    /** Closeness above which the reticle counts as "already on" the target — can (re-)arm the lock
     *  even with a completely idle stick. Deliberately tight — this is "basically already aimed",
     *  not "somewhere in the assist cone". */
    private static final double LOCK_ARM_CLOSENESS = 0.8;
    /** Magnetism "stick magnitude" substitute used while at rest (lock alive, stick idle) — softer
     *  than a full-stick pull so a stationary player is nudged onto target, not snapped there. */
    private static final double LOCK_AT_REST_MAG = 0.5;

    /** The current target and its angular metrics, recomputed once per frame. */
    private static Entity target;
    private static double targetCloseness;   // 1 at dead-center → 0 at the cone's edge
    private static double targetYawDeg, targetPitchDeg;   // camera-relative offset to the target
    private static long lockUntilMs = 0L;

    private AimAssistController() {}

    /**
     * Recomputes the assist target for this frame. Cheap when not aiming (one UseAction check).
     * Call once per frame BEFORE {@link #frictionFactor} / {@link #magnetism}.
     */
    public static void frame(MinecraftClient mc, ControllerConfig cfg) {
        target = null;
        if (!cfg.aimAssistEnabled || cfg.aimAssistStrength <= 0f) return;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || !p.isUsingItem()) return;
        var activeStack = p.getActiveItem();
        UseAction action = activeStack.getUseAction();
        // Vanilla bow/crossbow/trident by UseAction, PLUS any item (vanilla or modded) that subclasses
        // RangedWeaponItem — the common base modded bows/crossbows extend to get vanilla's own charge-
        // and-release mechanics for free, so this generically covers those too. A modded ranged weapon
        // with an entirely custom firing flow (no vanilla "use item" charging at all) has no signal
        // this hook can observe — documented limitation, not fixable without hooking each mod directly.
        boolean vanillaRanged = action == UseAction.BOW || action == UseAction.CROSSBOW || action == UseAction.SPEAR;
        boolean moddedRanged = activeStack.getItem() instanceof net.minecraft.item.RangedWeaponItem;
        if (!vanillaRanged && !moddedRanged) return;

        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVecClient().normalize();
        Box search = new Box(eye, eye.add(look.multiply(RANGE))).expand(3.0);

        Entity best = null;
        double bestScore = 0.0, bestCloseness = 0.0, bestYaw = 0.0, bestPitch = 0.0;
        // LivingEntity covers mobs AND other players (PvP) — getOtherEntities only excludes self.
        for (Entity e : mc.world.getOtherEntities(p, search,
                ent -> ent instanceof LivingEntity le && le.isAlive() && !ent.isSpectator())) {
            Vec3d center = e.getBoundingBox().getCenter();
            Vec3d to = center.subtract(eye);
            double dist = to.length();
            if (dist < 1.5 || dist > RANGE) continue;
            // Aim point = center raised by the projectile's expected drop at this distance.
            double drop = Math.min(DROP_COMP_MAX, dist * dist * DROP_COMP_PER_DIST_SQ);
            center = center.add(0, drop, 0);
            to = center.subtract(eye);
            dist = to.length();
            Vec3d dir = to.multiply(1.0 / dist);
            double dot = MathHelper.clamp(look.dotProduct(dir), -1.0, 1.0);
            double angle = Math.acos(dot);
            // Cone: the target's apparent angular radius (scaled) + a small base, so nearby mobs
            // are "stickier" than distant ones — matches how console assists feel.
            double apparent = Math.atan((e.getBoundingBox().getAverageSideLength() * 0.5) / dist);
            double cone = apparent * CONE_NEAR_MULT + CONE_BASE_RAD;
            if (angle > cone) continue;
            if (!p.canSee(e)) continue;   // no assist through walls
            double closeness = 1.0 - (angle / cone);
            double score = closeness / (1.0 + dist * 0.05);   // prefer centered, then near
            if (score > bestScore) {
                bestScore = score;
                best = e;
                bestCloseness = closeness;
                // Camera-relative angular offset (deg) toward the target, for magnetism.
                double wantYaw = Math.toDegrees(Math.atan2(-dir.x, dir.z));
                double wantPitch = Math.toDegrees(-Math.asin(dir.y));
                bestYaw = MathHelper.wrapDegrees(wantYaw - p.getYaw());
                bestPitch = wantPitch - p.getPitch();
            }
        }
        target = best;
        targetCloseness = bestCloseness;
        targetYawDeg = bestYaw;
        targetPitchDeg = bestPitch;
    }

    /** True while a target is engaged this frame (used to avoid stacking other slowdowns on top). */
    public static boolean hasTarget() { return target != null; }

    /** Camera speed multiplier for this frame: 1.0 = no assist, down to FRICTION_FLOOR at center. */
    public static double frictionFactor(ControllerConfig cfg) {
        if (target == null) return 1.0;
        double strength = MathHelper.clamp(cfg.aimAssistStrength, 0f, 1f);
        // sqrt(closeness): a good chunk of the slowdown arrives as soon as the reticle enters the
        // cone (instead of only at dead-center) — this is what makes the friction FEELABLE.
        double slowdown = (1.0 - FRICTION_FLOOR) * Math.sqrt(targetCloseness) * strength;
        return 1.0 - slowdown;
    }

    /**
     * Gentle drift toward the target — returns {yawDeg, pitchDeg} to ADD this frame. Zero with no
     * target. While actively steering (stick past the deadzone), pulls scaled by the real stick
     * magnitude exactly as before; while the stick is idle, still pulls — but only inside a short,
     * self-renewing "sticky lock" window (see class doc) so a stationary player benefits too, without
     * the camera drifting on its own the instant a target enters range.
     */
    public static double[] magnetism(ControllerConfig cfg, double stickMag, double dt) {
        if (target == null) return ZERO;
        // Engage from the slightest deliberate deflection (0.02, i.e. anything past the deadzone) —
        // the first cut required 0.1 and made the pull feel absent during fine tracking.
        boolean activeSteer = stickMag >= 0.02;
        long now = System.currentTimeMillis();
        if (activeSteer || targetCloseness >= LOCK_ARM_CLOSENESS) {
            lockUntilMs = now + LOCK_HOLD_MS;
        }
        if (now >= lockUntilMs) return ZERO;   // lock expired — fully released until re-armed

        double strength = MathHelper.clamp(cfg.aimAssistStrength, 0f, 1f);
        double effMag = activeSteer ? stickMag : LOCK_AT_REST_MAG;
        double rate = MAGNETISM_DEG_PER_SEC * strength * effMag * targetCloseness * dt;
        double yaw = MathHelper.clamp(targetYawDeg, -rate, rate);
        double pitch = MathHelper.clamp(targetPitchDeg, -rate, rate);
        return new double[]{yaw, pitch};
    }

    private static final double[] ZERO = {0.0, 0.0};
}
