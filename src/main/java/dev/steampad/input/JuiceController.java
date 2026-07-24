package dev.steampad.input;

import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Purely cosmetic "impact feedback" — screen shake + a brief FOV punch-in — layered on top of
 * {@code HapticsController}'s existing combat/impact events (weapon hits, kills, explosions, heavy
 * falls, taking damage). This is the visual half of the same moment the controller already buzzes
 * for; neither system knows about the other, they're just triggered from the same call sites so a
 * "big hit" reads as one coherent moment instead of "a rumble happens, and separately, nothing else
 * does" — the gap most AAA console games (God of War, Returnal, DOOM Eternal) close with screen
 * shake + a camera kick on every meaningful hit, in both first and third person.
 *
 * <p>Deliberately NOT implemented: real hit-stop (freezing the simulation/animation for a few frames
 * on impact). This project's own history (STATE.md D046-D053) is a long run of camera/timing bugs —
 * freezing camera rotation independently of position while the world keeps ticking risks the exact
 * class of desync-feeling bug that history warns about, for a refinement a strong shake pulse already
 * gets most of the way toward. A held-off idea, not a silent gap — see TODO_BLOCKERS.md.
 *
 * <p>Zero cost while idle: both {@link #cameraOffset()} and {@link #fovMultiplier()} short-circuit to
 * a single comparison once decayed to nothing, mirroring {@link ZoomController}'s own idle fast path.
 */
public final class JuiceController {

    private JuiceController() {}

    // Global intensity boost (feedback, confirmed working: "hasta podria subir un poquito mas el
    // efecto, muy poco") — a single tunable knob applied to every trigger below, instead of hand-
    // adjusting each of HapticsController's half-dozen call sites individually and risking an
    // inconsistent bump across events. A modest, deliberately small nudge, matching "muy poco".
    private static final double SHAKE_BOOST = 1.2;
    private static final float FOV_KICK_BOOST = 1.2f;

    // ---- Screen shake ---------------------------------------------------------------------------

    private static double shakeIntensity = 0.0;   // current amplitude, blocks
    private static double shakeDecayPerSec = 0.0;
    private static long lastShakeNanos = 0L;

    /**
     * Triggers a shake — {@code amplitude} in blocks (world-space camera jitter), decaying to zero
     * over {@code durationMs}. A weaker trigger while a stronger one is already decaying is ignored
     * (the stronger shake keeps playing out) rather than stacking additively — several small hits
     * landing the same frame should never add up to a violent, unintentional-looking shake.
     */
    public static void shake(double amplitude, int durationMs) {
        amplitude *= SHAKE_BOOST;
        if (amplitude <= shakeIntensity) return;
        shakeIntensity = amplitude;
        shakeDecayPerSec = amplitude / Math.max(0.05, durationMs / 1000.0);
    }

    /** World-space camera offset to add this frame — {@link Vec3d#ZERO} once fully decayed. */
    public static Vec3d cameraOffset() {
        long now = System.nanoTime();
        double dt = lastShakeNanos == 0L ? (1.0 / 60.0) : (now - lastShakeNanos) / 1_000_000_000.0;
        lastShakeNanos = now;
        if (dt <= 0 || dt > 0.25) dt = 1.0 / 60.0;

        if (shakeIntensity <= 0.0008) { shakeIntensity = 0.0; return Vec3d.ZERO; }
        shakeIntensity = Math.max(0.0, shakeIntensity - shakeDecayPerSec * dt);
        if (shakeIntensity <= 0.0) return Vec3d.ZERO;

        var r = ThreadLocalRandom.current();
        double x = (r.nextDouble() * 2 - 1) * shakeIntensity;
        double y = (r.nextDouble() * 2 - 1) * shakeIntensity * 0.5;   // less vertical jitter — reads as impact, not seasick
        double z = (r.nextDouble() * 2 - 1) * shakeIntensity;
        return new Vec3d(x, y, z);
    }

    // ---- Impact FOV kick --------------------------------------------------------------------------

    /** Current eased FOV multiplier from the kick (1.0 = no effect); {@code fovTarget} is what it
     *  chases — a kick pulls the TARGET down instantly (stronger kick wins, same "don't stack" rule
     *  as shake), then the target itself climbs linearly back to 1.0 while {@code fovFactor} closely
     *  (fast halflife) follows it — net effect: punches in fast, eases back out over ~2 seconds. */
    private static float fovFactor = 1.0f;
    private static float fovTarget = 1.0f;
    private static long lastFovNanos = 0L;
    private static final double FOV_EASE_HALFLIFE_SEC = 0.06;
    private static final float FOV_RECOVERY_PER_SEC = 0.5f;   // target reaches 1.0 ~2s after a kick

    /** Punches the FOV in by {@code amount} (e.g. 0.06 = 6% narrower), clamped to a sane max so even a
     *  huge magnitude can't produce a disorienting fisheye snap. */
    public static void fovKick(float amount) {
        float target = 1.0f - Math.max(0f, Math.min(0.3f, amount * FOV_KICK_BOOST));
        if (target < fovTarget) fovTarget = target;
    }

    /** Multiplier {@code GameRendererMixin} should apply on top of the zoom factor this frame. */
    public static float fovMultiplier() {
        long now = System.nanoTime();
        double dt = lastFovNanos == 0L ? (1.0 / 60.0) : (now - lastFovNanos) / 1_000_000_000.0;
        lastFovNanos = now;
        if (dt <= 0 || dt > 0.25) dt = 1.0 / 60.0;

        if (fovFactor >= 1.0f && fovTarget >= 1.0f) return 1.0f;   // fully settled — fast path

        if (fovTarget < 1.0f) fovTarget = Math.min(1.0f, fovTarget + (float) (FOV_RECOVERY_PER_SEC * dt));
        double factor = 1.0 - Math.pow(2.0, -dt / FOV_EASE_HALFLIFE_SEC);
        fovFactor += (float) ((fovTarget - fovFactor) * factor);
        if (Math.abs(fovFactor - 1.0f) < 0.0008f) fovFactor = 1.0f;
        return fovFactor;
    }
}
