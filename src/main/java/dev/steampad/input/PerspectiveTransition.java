package dev.steampad.input;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * The 220ms "camera leaves / re-enters the body" ease that used to live inside
 * {@code EmoteAnimator} (D084) — generalized in D123 so it serves BOTH of its users:
 *
 * <ul>
 *   <li><b>Emotes</b> (the original): {@code EmoteAnimator.playLocal} eases OUT of the eye when it
 *       auto-switches 1st→3rd, and eases back IN before flipping to first person at the end.</li>
 *   <li><b>Manual perspective cycling</b> (round-4 request: "tambien agregacelo cuando se cambia a
 *       tercera persona y a primera"): the gamepad PERSPECTIVE bind now runs the same ease via
 *       {@link #cyclePerspective} instead of tapping vanilla's instant toggle key. Keyboard F5 still
 *       cuts instantly — this is a gamepad mod; vanilla's own key is deliberately left untouched.</li>
 * </ul>
 *
 * <p><b>Why the move (the actual round-4 bug):</b> the blend used to be applied only in the camera
 * mixin's plain-offset hook, which bails whenever free-look is enabled — and a REAL emote FORCES
 * free-look on (D100). So the emote's own transition had been unreachable during emotes ever since
 * D100 quietly: the effect the user described as "sale del personaje y entra del personaje...
 * habia quedado muy bien" only ever rendered for free-look-off players. Both camera-mixin hooks now
 * apply {@link #blend} on top of whatever pose they computed, so the ease shows regardless of how
 * the camera is being driven that frame.
 *
 * <p>Mechanics: perspective flips to third person IMMEDIATELY on the way out (the body must be
 * visible while the camera pulls back), but flips to first person only AFTER the ease-in visually
 * lands (see {@link #tick}) — flipping first would cut to the eye camera and skip the whole effect.
 */
public final class PerspectiveTransition {

    private static final long TRANSITION_MS = 220;

    private static boolean active = false;
    private static boolean entering = false;   // true = 1st→3rd (ease OUT), false = 3rd→1st (ease IN)
    private static long startMs = 0L;
    /** Eye position frozen at the moment an ENTER transition starts (the point the camera grows out
     *  of). EXIT recomputes the eye live each frame instead — the player may well be walking (that is
     *  what cancels an emote), and easing toward a frozen point would drift visibly behind them. */
    private static Vec3 enterEyePos = null;
    /** The perspective an EXIT transition started from — the completion flip only fires if the player
     *  is still there, so a perspective they picked mid-ease is never fought. */
    private static CameraType exitFrom = null;

    private PerspectiveTransition() {}

    /** Ease the camera OUT from {@code eye} into the current third-person position. Call AFTER
     *  switching the perspective to third person. */
    public static void beginEnter(Vec3 eye) {
        active = true;
        entering = true;
        startMs = System.currentTimeMillis();
        enterEyePos = eye;
        exitFrom = null;
    }

    /** Ease the camera IN toward the eye; once visually there, {@link #tick} flips the perspective to
     *  first person (only if still on {@code from}). */
    public static void beginExitToFirstPerson(CameraType from) {
        active = true;
        entering = false;
        startMs = System.currentTimeMillis();
        enterEyePos = null;
        exitFrom = from;
    }

    public static boolean isActive() { return active; }

    /** Hard drop (disconnect/world change) — never leave the camera permanently blended at the eye. */
    public static void cancel() {
        active = false;
        exitFrom = null;
        enterEyePos = null;
    }

    /**
     * Once per client tick (SteamPadClient). Completes transitions: an ENTER simply ends; an EXIT
     * additionally flips the perspective to first person, deliberately delayed until the ease-in has
     * visually landed so the camera travels IN third-person space the whole way instead of
     * jump-cutting the instant the exit begins.
     */
    public static void tick(Minecraft mc) {
        if (!active) return;
        if (mc.player == null) { cancel(); return; }   // belt-and-braces alongside EmoteAnimator.clearAll
        if (progress() < 1.0) return;
        boolean wasEntering = entering;
        CameraType from = exitFrom;
        cancel();
        if (!wasEntering && from != null && mc.options.getCameraType() == from) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    /**
     * The eased eye-blend for this frame, applied on TOP of whatever camera position the caller just
     * computed (vanilla+offset or the free pose — both camera-mixin hooks call this). Returns
     * {@code null} when no transition is active — the overwhelmingly common case, one boolean check.
     */
    public static Vec3 blend(Minecraft mc, Vec3 computedPos, float tickDelta) {
        if (!active || mc.player == null) return null;
        double t = progress();
        double eased = t * t * (3 - 2 * t);   // smoothstep — no velocity "kick" at either end
        // ENTER uses the frozen start eye; EXIT tracks the live (render-interpolated — D116 rule:
        // never feed tick-quantised positions into per-frame math) eye of a possibly-walking player.
        Vec3 eye = enterEyePos != null
                ? enterEyePos
                : mc.player.getPosition(tickDelta).add(0, mc.player.getEyeHeight(), 0);
        double blend = entering ? eased : (1.0 - eased);
        return eye.add(computedPos.subtract(eye).scale(blend));
    }

    /**
     * The gamepad PERSPECTIVE bind's transition-aware replacement for vanilla's instant
     * {@code togglePerspectiveKey} tap. Same cycle order as vanilla (1st → 3rd-back → 3rd-front →
     * 1st), with the ease on the two legs that cross the first-person boundary; back→front keeps the
     * camera at the same distance, so an ease there would have nothing visible to do. A press while a
     * transition is already running is ignored — 220ms, imperceptible as lost input, and it keeps a
     * button mash from stacking half-finished eases.
     */
    public static void cyclePerspective(Minecraft mc) {
        if (active || mc.player == null) return;
        CameraType current = mc.options.getCameraType();
        if (current == CameraType.FIRST_PERSON) {
            Vec3 eye = mc.player.getEyePosition();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            beginEnter(eye);
        } else if (current == CameraType.THIRD_PERSON_BACK) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        } else {
            beginExitToFirstPerson(current);   // perspective flips in tick() once the ease lands
        }
    }

    private static double progress() {
        double t = (System.currentTimeMillis() - startMs) / (double) TRANSITION_MS;
        return t < 0 ? 0 : (t > 1 ? 1 : t);
    }
}
