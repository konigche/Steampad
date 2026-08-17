package dev.steampad.util;

/**
 * Exponentially-smoothed values with <b>render interpolation</b> — a faithful port of the smoothing
 * primitives from Leawind's
 * <a href="https://github.com/Leawind/Third-Person">Third-Person</a> mod (MIT license,
 * {@code util.math.smoothvalue.ExpSmoothValue} and its Double/Vector2d/Vector3d subclasses), ported
 * with attribution as permitted by that license.
 *
 * <p><b>Why this exists / what SteamPad was missing before v0.76.0 (D116).</b> The old free-camera
 * code smoothed with a bare {@code current += (target - current) * factor} evaluated once per RENDER
 * FRAME against wall-clock {@code System.nanoTime()}. That shape has no way to express the thing the
 * reference mod actually relies on: a value that <em>advances once per CLIENT TICK</em> (20 Hz) but
 * is <em>read back interpolated by {@code partialTick}</em> during every render frame in between.
 * Without that split, anything driven by tick-rate data (above all the camera's own rotate centre,
 * which follows the player's position) visibly advances in 20 Hz steps while the game renders at
 * 60–144 fps. That is invisible while standing still — the value simply isn't changing — and becomes
 * severe stair-stepping the moment the player walks. See {@code ThirdPersonCameraController} for the
 * full root-cause note.
 *
 * <p>The contract, matching the reference exactly:
 * <ul>
 *   <li>{@link #setHalflife} sets how fast the value chases its target: after {@code halflife}
 *       seconds, the remaining distance to the target has halved. A halflife of {@code 0} disables
 *       smoothing entirely (the value snaps to its target on the next update).</li>
 *   <li>{@link #update} advances the smoothed value by one period, first stashing the previous value
 *       so it can be interpolated against. Call this once per client tick with
 *       {@code period = 0.05}.</li>
 *   <li>{@link #get(float)} reads the value back interpolated between the previous tick's value and
 *       the current one — call this every render frame with vanilla's own {@code tickDelta}.</li>
 * </ul>
 *
 * <p>The frame-rate independence comes from the exponent: {@code t = 1 - factor^period}, so halving
 * the period genuinely halves the progress toward the target rather than approximating it.
 */
public final class SmoothValue {

    private SmoothValue() {}

    /** {@code factor} such that the remaining distance halves every {@code halflife} seconds; a
     *  halflife of 0 yields 0, which makes {@code t} below evaluate to 1 (snap straight to target). */
    private static double factorForHalflife(double halflife) {
        return halflife <= 0 ? 0.0 : Math.pow(0.5, 1.0 / halflife);
    }

    /** Fraction of the remaining distance to cover over {@code period} seconds at {@code factor}. */
    private static double progress(double factor, double period) {
        return 1.0 - Math.pow(factor, period);
    }

    /** A single smoothed scalar — used for the camera distance and the aiming FOV divisor. */
    public static final class Scalar {
        private double factor;
        private double target;
        private double value;
        private double lastValue;

        public Scalar(double initial) {
            this.value = initial;
            this.lastValue = initial;
            this.target = initial;
        }

        public void setHalflife(double halflife) { this.factor = factorForHalflife(halflife); }
        public void setTarget(double target) { this.target = target; }

        /** Current (un-interpolated) value — the state as of the last {@link #update}. */
        public double get() { return value; }

        /** Value interpolated between the previous tick and the current one. */
        public double get(float partialTick) {
            return lastValue + (value - lastValue) * partialTick;
        }

        /** Overwrites the smoothed value without touching the target (used by wall clipping, which
         *  must pull the camera in immediately rather than ease into a wall). */
        public void setValue(double v) { this.value = v; }

        /** Hard-sets value AND target — for (re)seeding, so nothing eases in from a stale state. */
        public void set(double v) { this.value = v; this.lastValue = v; this.target = v; }

        public void update(double period) {
            lastValue = value;
            value += (target - value) * progress(factor, period);
        }
    }

    /** A smoothed 2-component value — used for the screen-space camera offset ratio (x, y). */
    public static final class Vec2 {
        private double factor;
        private double targetX, targetY;
        private double valueX, valueY;
        private double lastX, lastY;

        public void setHalflife(double halflife) { this.factor = factorForHalflife(halflife); }

        public void setTarget(double x, double y) { this.targetX = x; this.targetY = y; }

        public double getX(float partialTick) { return lastX + (valueX - lastX) * partialTick; }
        public double getY(float partialTick) { return lastY + (valueY - lastY) * partialTick; }

        public void set(double x, double y) {
            valueX = lastX = targetX = x;
            valueY = lastY = targetY = y;
        }

        public void update(double period) {
            double t = progress(factor, period);
            lastX = valueX;
            lastY = valueY;
            valueX += (targetX - valueX) * t;
            valueY += (targetY - valueY) * t;
        }
    }

    /**
     * A smoothed 3D position — used for the camera's rotate centre (the point the camera orbits,
     * which tracks the player's eyes). This is the one that matters most: it is the only value in the
     * whole camera whose target genuinely moves every client tick during normal play.
     *
     * <p>Supports a per-axis halflife because the reference mod smooths vertical motion differently
     * from horizontal (a player climbing stairs should not make the camera bob as hard as it would
     * if the same halflife were used in all three axes).
     */
    public static final class Vec3 {
        private double factorX, factorY, factorZ;
        private double targetX, targetY, targetZ;
        private double valueX, valueY, valueZ;
        private double lastX, lastY, lastZ;

        public void setHalflife(double x, double y, double z) {
            factorX = factorForHalflife(x);
            factorY = factorForHalflife(y);
            factorZ = factorForHalflife(z);
        }

        public void setTarget(net.minecraft.world.phys.Vec3 t) { targetX = t.x; targetY = t.y; targetZ = t.z; }

        /** Lets the wall-clipping pass push the target's Y back out after it clamped the value. */
        public void setTargetY(double y) { targetY = y; }

        public net.minecraft.world.phys.Vec3 get(float partialTick) {
            return new net.minecraft.world.phys.Vec3(
                    lastX + (valueX - lastX) * partialTick,
                    lastY + (valueY - lastY) * partialTick,
                    lastZ + (valueZ - lastZ) * partialTick);
        }

        public void set(net.minecraft.world.phys.Vec3 v) {
            valueX = lastX = targetX = v.x;
            valueY = lastY = targetY = v.y;
            valueZ = lastZ = targetZ = v.z;
        }

        public void update(double period) {
            lastX = valueX;
            lastY = valueY;
            lastZ = valueZ;
            valueX += (targetX - valueX) * progress(factorX, period);
            valueY += (targetY - valueY) * progress(factorY, period);
            valueZ += (targetZ - valueZ) * progress(factorZ, period);
        }
    }
}
