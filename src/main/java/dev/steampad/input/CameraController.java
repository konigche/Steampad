package dev.steampad.input;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.ControllerConfig;
import dev.steampad.radial.RadialMenuController;
import dev.steampad.service.ActiveControllerService;
import dev.steampad.service.ControllerManager;
import net.minecraft.client.Minecraft;

/**
 * Applies right-stick camera look <b>every frame</b> (not every tick), so the camera is smooth and
 * frame-rate independent — fixing the previous tick-based look that felt laggy and far too slow.
 *
 * <p><b>Console-AAA response model</b> (the standard shape used by COD/Halo/Destiny-style shooters):
 * <ul>
 *   <li><b>Power curve over the stick MAGNITUDE</b> (not per-axis — per-axis curving distorts
 *       diagonals): {@code out = dir * mag^lookCurve}. Exponent ~2.2 gives a wide precise band in
 *       the middle of the stick's travel while full tilt still turns fast.</li>
 *   <li><b>Separate yaw/pitch base speeds</b> — consoles pan noticeably faster than they tilt
 *       (vertical overshoot feels much worse than horizontal).</li>
 *   <li><b>Turn acceleration ("edge boost")</b>: holding the stick pegged at the edge briefly ramps
 *       yaw up to a turbo over a fraction of a second — fast 180s without giving up mid-range
 *       precision. Pitch gets only a small share of the boost.</li>
 * </ul>
 * Aim assist (bow/crossbow/trident, see {@link AimAssistController}) multiplies the final speed
 * (friction) and adds a gentle drift (magnetism), both after the curve.
 *
 * <p>Speeds are real degrees/second scaled by frame delta, then converted to the units
 * {@code changeLookDirection} expects (it multiplies its args by 0.15 internally).
 */
public final class CameraController {

    /** Yaw degrees per second at full stick deflection, sensitivity 1.0, before the edge boost. */
    private static final double BASE_YAW_DEG_PER_SEC = 260.0;
    /** Pitch degrees per second — deliberately slower than yaw (console standard, ~0.75×). */
    private static final double BASE_PITCH_DEG_PER_SEC = 195.0;
    /** Yaw multiplier once the edge boost is fully ramped (260 → ~430 °/s for 180s). */
    private static final double EDGE_BOOST_MAX = 1.65;
    /** Pitch shares only this fraction of the boost. */
    private static final double EDGE_BOOST_PITCH_SHARE = 0.25;
    /** Stick magnitude that counts as "pegged at the edge". */
    private static final double EDGE_MAG = 0.98;
    /** Seconds at the edge before the boost starts ramping (prevents accidental turbo). */
    private static final double EDGE_DELAY_SEC = 0.15;
    /** Seconds the boost takes to ramp from 0 to full once past the delay. */
    private static final double EDGE_RAMP_SEC = 0.35;
    private static final double LOOK_UNIT = 0.15;   // changeLookDirection internal multiplier

    private static final GamepadSnapshot snap = new GamepadSnapshot();
    private static long lastNanos = 0L;
    /** Seconds the stick has been held pegged at the edge (drives the boost ramp). */
    private static double edgeHoldSec = 0.0;

    private CameraController() {}

    /** Call once per rendered frame (from the HUD render callback). */
    public static void frame() {
        long now = System.nanoTime();
        double dt = lastNanos == 0L ? (1.0 / 60.0) : (now - lastNanos) / 1_000_000_000.0;
        lastNanos = now;
        if (dt <= 0 || dt > 0.25) dt = 1.0 / 60.0;   // clamp pauses/hitches

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (RadialMenuController.isOpen() || dev.steampad.emote.EmoteWheelController.isOpen()
                || dev.steampad.itemradial.ItemRadialController.isOpen()) return;

        long handle = ActiveControllerService.getActiveHandle();
        if (handle == 0L || !ControllerManager.isFallbackHandle(handle)) return;
        if (!ControllerManager.readSnapshot(handle, snap)) return;

        ControllerConfig cfg = ConfigManager.getControllerConfig(handle);
        float[] r = DeadzoneProcessor.process(
                new float[]{snap.axis(GamepadSnapshot.AXIS_RIGHT_X), snap.axis(GamepadSnapshot.AXIS_RIGHT_Y)},
                cfg.rightStickDeadzone);
        // Merge the bridged Steam Input joystick_camera action (a physical stick bound to "Joystick
        // derecho — Cámara" in Steam's configurator): when Steam routes a stick as an action, the
        // virtual gamepad's own axis goes silent, so without this the camera would die the moment
        // the user makes that binding (B075). Whichever source is deflected further wins the frame;
        // zero-cost when the Steam side is idle/unbound (its values are 0).
        {
            float sx = SteamSlotDispatcher.steamRightX(), sy = SteamSlotDispatcher.steamRightY();
            if (sx * sx + sy * sy > r[0] * r[0] + r[1] * r[1]) r = new float[]{sx, sy};
        }

        // Aim-assist target is refreshed every frame (cheap when not charging a projectile), even
        // with the stick idle — friction must already be active on the first frame of a flick.
        AimAssistController.frame(mc, cfg);

        // Third-person free-look: while ON and the player is actively holding THIRD_PERSON_ADJUST,
        // the stick repositions the camera offset instead of turning anything — skip look entirely.
        //
        // Bug fixed here (feedback: "cuando se activa movimiento libre en los ajustes en primera
        // persona deja de funcionar el movimiento de camara... esto no debe de afectar en primera
        // persona"): this used to be just isFreeLookEnabled() — the user's persisted TOGGLE, with no
        // perspective check. turnFreeCamera() is a silent no-op until freeInitialized is seeded, and
        // that only ever happens inside computeFreePoseInner() when NOT in first person (it returns
        // null early otherwise) — so with the toggle on but the player still in first person, every
        // frame here called turnFreeCamera() (which did nothing) INSTEAD of changeLookDirection(),
        // and the right stick stopped turning the camera at all until switching to third person.
        // Requiring !isFirstPerson() here makes first-person camera look byte-for-byte the vanilla/
        // normal path regardless of the free-look toggle, exactly like it was before free-look shipped.
        //
        // Front-view (mirror mode, F5×2) — D123, third iteration of this decision and the one that
        // matches the actual request: the stick routes to the PLAYER here (vanilla's own front-view
        // convention: the stick turns your character, and the camera stays on your face), while the
        // camera POSE itself still comes from the free pipeline, smoothly trailing the player's front
        // (see ThirdPersonCameraController.computeFreePoseInner's mirror block). D120 excluded both
        // (→ vanilla's hard-locked laggy camera), D121 excluded neither (→ mirror mode became a
        // second back view, "parece que desapareció") — mirror identity lives in the INPUT ROUTING,
        // smoothness lives in the POSE, and they needed opposite answers.
        // isMirrorMode (not raw isFrontView): during a REAL emote the free orbit wins even in front
        // view — the dancer's body is frozen, so routing the stick to it would do nothing, and
        // orbiting the dance is the emote camera's whole purpose (D100).
        //
        // Mounted (D170/D171): the stick routes to the PLAYER by default (steering — D170's fix
        // depends on mc.player.turn() still being called every tick a mount is ridden), UNLESS the
        // player deliberately shoves the stick past MOUNTED_FREE_LOOK_ENTER_MAG, which hands it to
        // the free camera instead (D171 round 2, feedback: "cuando presione el stick derecho este
        // la camara libre actual... cuando lo suelte pase un pequeño instante y regrese hacia donde
        // se dirige la parte de enfrente" — a genuinely decoupled look, not just a subtle chase-lag on
        // top of steering, which the first D171 attempt turned out to be too subtle to notice). See
        // ThirdPersonCameraController.tickMountedFreeLook's own doc for the full gesture state
        // machine (magnitude-gated on purpose, not duration-gated, so an ordinary sustained steering
        // hold — this project's own look-based turning has no "instant snap", any real turn requires
        // holding the stick for a while — never gets silently handed off to the camera mid-turn).
        double mag = Math.min(1.0, Math.sqrt((double) r[0] * r[0] + (double) r[1] * r[1]));
        boolean mountedFreeLookNow = ThirdPersonCameraController.tickMountedFreeLook(mc, mag, dt);

        boolean freeLook = (ThirdPersonCameraController.isFreeLookEnabled()
                && !mc.options.getCameraType().isFirstPerson()
                && !ThirdPersonCameraController.isMirrorMode(mc)
                && !ThirdPersonCameraController.isMounted(mc))
                || mountedFreeLookNow;
        if (freeLook && ThirdPersonCameraController.isAdjusting()) {
            ThirdPersonCameraController.adjustOffset(r[0] * dt * 2.0, -r[1] * dt * 2.0);
            edgeHoldSec = 0.0;
            return;
        }

        if (mag < 0.001) {
            edgeHoldSec = 0.0;
            // AAA-style aim assist can still gently pull the camera onto a target with a fully idle
            // stick (feedback: "tambien funcione cuando estamos parados, no necesariamente tienes que
            // mover el stick... se engancha un momentito y se desengancha") — everything else below
            // (turn boost, sensitivity curve, zoom look-slow) only applies while actively steering, so
            // this is the one piece that still needs to run when the stick is at rest.
            double[] idleMagnet = AimAssistController.magnetism(cfg, 0.0, dt);
            applyLook(mc, freeLook, idleMagnet[0], idleMagnet[1], dt);
            return;
        }

        // Power curve on the magnitude, direction preserved: |out| = mag^lookCurve.
        double exponent = cfg.lookCurve < 1.0f || cfg.lookCurve > 3.0f ? 2.2 : cfg.lookCurve;
        double shaped = Math.pow(mag, exponent);
        double dirX = r[0] / mag, dirY = r[1] / mag;

        // Edge boost: pegged at the edge → after a short delay, yaw ramps to the turbo.
        double boost = 0.0;
        if (cfg.lookTurnBoost && mag >= EDGE_MAG) {
            edgeHoldSec += dt;
            boost = Math.min(1.0, Math.max(0.0, (edgeHoldSec - EDGE_DELAY_SEC) / EDGE_RAMP_SEC));
            boost = boost * boost * (3 - 2 * boost);   // smoothstep — no speed "kick"
        } else {
            edgeHoldSec = 0.0;
        }
        double yawBoost   = 1.0 + boost * (EDGE_BOOST_MAX - 1.0);
        double pitchBoost = 1.0 + boost * (EDGE_BOOST_MAX - 1.0) * EDGE_BOOST_PITCH_SHARE;

        double yawDeg   = dirX * shaped * cfg.horizontalSensitivity * dev.steampad.config.ControllerConfig.SENSITIVITY_REBASE
                * BASE_YAW_DEG_PER_SEC * yawBoost * dt;
        double pitchDeg = dirY * shaped * cfg.verticalSensitivity * dev.steampad.config.ControllerConfig.SENSITIVITY_REBASE
                * BASE_PITCH_DEG_PER_SEC * pitchBoost * dt;
        if (cfg.invertLookY) pitchDeg = -pitchDeg;
        // "Reduce aiming sensitivity": slower camera while using an item (bow, spyglass, shield…).
        // Skipped while the aim assist has a target: stacking a blanket ×0.45 under the assist's own
        // friction turned the assist imperceptible (everything was equally slow) — when a target is
        // engaged, the assist owns the slowdown for that frame.
        if (cfg.reduceAimingSensitivity && mc.player.isUsingItem() && !AimAssistController.hasTarget()) {
            yawDeg *= 0.45;
            pitchDeg *= 0.45;
        }
        // Zoom: slow the camera to match the magnified view (auto = tracks the eased FOV factor,
        // including during the glide; fixed = configured multiplier). 1.0 when not zooming.
        double zoomLook = ZoomController.lookFactor(cfg);
        if (zoomLook != 1.0) {
            yawDeg *= zoomLook;
            pitchDeg *= zoomLook;
        }
        // Aim assist: friction slows the camera over a target; magnetism drifts gently toward it.
        double friction = AimAssistController.frictionFactor(cfg);
        if (friction != 1.0) {
            yawDeg *= friction;
            pitchDeg *= friction;
        }
        double[] magnet = AimAssistController.magnetism(cfg, mag, dt);
        yawDeg += magnet[0];
        pitchDeg += magnet[1];

        applyLook(mc, freeLook, yawDeg, pitchDeg, dt);
    }

    /**
     * Final step for every look-input path: optionally runs the delta through the cinematic-bars
     * stabilizer, then dispatches to the free camera or the player's own turn.
     *
     * <p><b>Must be the ONLY exit point that applies look input</b> — see the bug this fixes. The
     * stabilizer carries a leftover motion buffer between ticks (see {@code ZoomController.stabilize}),
     * and this method used to be inlined only at the bottom of {@link #frame}, past the early return
     * for an idle stick (reported: "cuando se mueve se detiene muy de golpe"). The moment the player
     * released the stick, {@code frame()} took that early return and NEVER called {@code stabilize()}
     * again — so whatever motion the buffer hadn't drained yet simply froze instead of easing out.
     * Routing the idle path (which still needs to feed idle aim-assist magnetism, possibly zero,
     * through the SAME drain) through this one helper is what keeps the buffer draining every tick
     * regardless of which path produced this tick's raw delta.
     */
    private static void applyLook(Minecraft mc, boolean freeLook, double yawDeg, double pitchDeg, double dt) {
        if (ZoomController.stabilizerActive()) {
            double[] eased = ZoomController.stabilize(yawDeg, pitchDeg, dt);
            yawDeg = eased[0];
            pitchDeg = eased[1];
        }
        if (yawDeg == 0.0 && pitchDeg == 0.0) return;
        // changeLookDirection multiplies by 0.15; divide so our value is in real degrees. Free-look
        // (third person, decoupled) redirects the exact same shaped output to the free camera's own
        // rotation instead of the player's — every curve/boost/assist/zoom-slow computed above is
        // already folded into yawDeg/pitchDeg by this point, so nothing else here needs to change.
        if (freeLook) {
            ThirdPersonCameraController.turnFreeCamera(yawDeg, pitchDeg);
        } else {
            mc.player.turn(yawDeg / LOOK_UNIT, pitchDeg / LOOK_UNIT);
            // Mounted steering keeps driving player.turn() above (D170 depends on it), but the chase
            // camera must ALSO see this frame's yaw so a small nudge produces a small camera move —
            // see nudgeMountedChaseYaw's doc for why the ease alone could not deliver that.
            if (ThirdPersonCameraController.isMounted(mc)
                    && !mc.options.getCameraType().isFirstPerson()) {
                ThirdPersonCameraController.nudgeMountedChaseYaw(yawDeg);
            }
        }
    }
}
