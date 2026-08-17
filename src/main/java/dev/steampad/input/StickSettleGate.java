package dev.steampad.input;

/**
 * Decides whether a stick sample should be allowed to move a SELECTION, so that letting go of the
 * stick keeps whatever you were aiming at instead of the thing next to it.
 *
 * <p>Shared by every surface a stick can pick something on: the virtual keyboard's two pointers and
 * all three wheels (radial menu, emote wheel, item radial). It lives in {@code dev.steampad.input}
 * rather than beside any one of them because the thing it models is the STICK, not the surface.
 *
 * <p><b>The bug.</b> "El snap a veces al soltar el stick no queda donde ya seleccioné la tecla, creo
 * que al soltar el stick siente ese movimiento y muchas veces regresa una letra." And, later and
 * identically, on every wheel: "selecciono una cosa y al soltar hay un latigazo y escoge otra cosa
 * que tiene al lado." That is mechanical, not a tuning problem: a self-centring stick does not stop
 * at centre when released, it springs PAST it. Releasing from a push to the right, the stick swings
 * back through zero and reads a real deflection to the LEFT — commonly 0.2–0.3 of full range — for a
 * tenth of a second before it settles.
 *
 * <p>Nothing in the existing response could absorb that. The deceleration brake only engages while the
 * magnitude is FALLING; during the overshoot the magnitude is RISING again (in the opposite direction),
 * so the pointer got the full, unbraked speed backwards. The settle magnet only takes over below 0.24,
 * which an overshoot of 0.25–0.30 clears. The pointer therefore drifted one key back, and the magnet
 * then obligingly locked it there — a wrong letter, arrived at "smoothly".
 *
 * <p><b>The fix.</b> Treat a release as a real state change rather than as just another low reading:
 * once the stick drops below {@link #RELEASE_MAG} the pointer is frozen and the selection committed,
 * and it stays frozen until either the stick is pushed hard enough that it cannot be spring-back
 * ({@link #REENGAGE_MAG}) or {@link #LOCKOUT_MS} has passed — long enough for the spring to have
 * settled, short enough to be imperceptible between two deliberate movements. Gentle, slow pushes are
 * deliberately still honoured: they only ever have to clear the ordinary release threshold, because by
 * the time a player makes one the lockout is long over.
 *
 * <p>Pure state machine with the clock passed in, one instance per pointer.
 */
public final class StickSettleGate {

    /** Below this the stick counts as released. Above the deadzone, so it is a real "let go". */
    public static final double RELEASE_MAG = 0.18;
    /**
     * Deflection that restarts movement immediately during the lockout. Set above the largest
     * overshoot a self-centring stick produces, and below the flick that arms the tap gesture (0.55),
     * so a deliberate quick flick is never delayed.
     *
     * <p><b>Also the wheels' "this is a real pick" threshold</b>, and deliberately the same number:
     * both questions are the same question — "is this deflection too big to be spring-back?". The
     * wheels used to answer it with 0.35, which sits INSIDE the rebound band this constant was
     * measured to sit above, so a release could re-point the selection at a different sector one
     * frame before the button-release executed it. See {@code RadialMenuController#updateAnalog}.
     */
    public static final double REENGAGE_MAG = 0.45;
    /** How long a release stays locked out. Covers the spring's ring-down without being noticeable. */
    public static final long LOCKOUT_MS = 180L;

    /** Starts settled: nothing has been pushed yet, so nothing should be drifting. */
    private boolean settling = true;
    private long settleUntilMs = 0L;
    private boolean justSettled = false;

    /**
     * @param magnitude stick deflection, 0..1 (already deadzone-processed)
     * @param nowMs     current time in milliseconds
     * @return whether this sample may move the pointer
     */
    public boolean accept(double magnitude, long nowMs) {
        if (magnitude < RELEASE_MAG) {
            if (!settling) {
                settling = true;
                justSettled = true;
                settleUntilMs = nowMs + LOCKOUT_MS;
            }
            return false;
        }
        if (settling) {
            if (magnitude >= REENGAGE_MAG || nowMs >= settleUntilMs) {
                settling = false;
                return true;
            }
            return false;   // spring-back, not a new push
        }
        return true;
    }

    /**
     * True exactly once per release, on the sample where the stick was let go — the caller uses it to
     * commit the selection to whatever it was aiming at, hard, with no easing left to carry it
     * anywhere else. The wheels have nothing to commit (their selection is already discrete and
     * sticky), so they simply never call this.
     */
    public boolean consumeJustSettled() {
        boolean r = justSettled;
        justSettled = false;
        return r;
    }

    /** True while the pointer is frozen (released, or riding out the lockout). */
    public boolean isSettling() { return settling; }

    /** Back to the initial state — used when the keyboard opens/changes screen or layer, and when a
     *  wheel opens (a fresh gesture must never inherit the previous one's lockout). */
    public void reset() {
        settling = true;
        settleUntilMs = 0L;
        justSettled = false;
    }
}
