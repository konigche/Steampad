package dev.steampad.compat;

import dev.steampad.util.LogUtil;

import java.lang.reflect.Method;

/**
 * Optional bridge to <b>KosmX's Player Animator</b> ({@code dev.kosmx.playerAnim}), the animation
 * library that mods like Gliders, Emotecraft and Bosses of Mass Destruction bundle to animate the
 * player. SteamPad neither depends on it nor uses it — this exists purely so
 * {@link EmfCompat} can ask "is another mod animating this player right now?".
 *
 * <p><b>The bug this fixes.</b> Entity Model Features (EMF, the engine behind the Fresh Animations /
 * FA+Player resource pack) applies its {@code .jem} animation from a hook in the entity RENDERER,
 * strictly after the whole {@code setupAnim(...)} call returns — the exact problem already documented
 * on {@link EmfCompat} for SteamPad's own emotes. Player Animator applies its pose at {@code RETURN}
 * of {@code PlayerModel.setupAnim}, i.e. <i>inside</i> that call, so EMF overwrites head, arms, legs
 * and torso the moment it returns and the animation never reaches the screen. Reported here as the
 * Gliders mod not playing its glider-grab animation.
 *
 * <p><b>Why EMF's own compat does not cover it.</b> EMF ships {@code mod_compat/PALCompat}, which
 * pauses its animations for a player being animated by a player-animation library — but the version
 * shipped with EMF 3.2.4 tests {@code com.zigythebird.playeranim.accessors.IAnimatedPlayer}, the
 * <i>other</i> fork of the library (verified with javap on the installed EMF jar). The build that
 * mods actually bundle here is KosmX's original, {@code dev.kosmx.playerAnim} — a different package
 * entirely, so EMF's check can never match it and EMF keeps animating right over the top. SteamPad is
 * already registered with EMF's pause API for its own emotes, so extending that same registration to
 * cover this library costs one extra condition and fixes every mod built on it, not just Gliders.
 *
 * <p><b>Why reflection.</b> Same reasoning as {@link EmfCompat}: a multi-version project should not
 * pin an optional library per Minecraft variant. Every failure path here is a no-op that leaves
 * today's behaviour untouched. Signatures verified with {@code javap} against the real bundled jar
 * ({@code playeranimator 2.0.1+1.21.1}, shipped inside {@code gliders-1.21.1-fabric-1.1.8.jar}):
 * {@code IAnimatedPlayer.playerAnimator_getAnimation()} returns {@code AnimationApplier}, which
 * inherits {@code public boolean isActive()} from {@code AnimationProcessor}.
 */
public final class PlayerAnimCompat {

    private static final String IFACE = "dev.kosmx.playerAnim.impl.IAnimatedPlayer";
    private static final String GET_ANIMATION = "playerAnimator_getAnimation";
    private static final String IS_ACTIVE = "isActive";

    private static final Class<?> ANIMATED_PLAYER;
    private static final Method GET_ANIMATION_METHOD;
    private static final Method IS_ACTIVE_METHOD;

    static {
        Class<?> iface = null;
        Method getAnimation = null;
        Method isActive = null;
        try {
            iface = Class.forName(IFACE);
            // No-arg overload on purpose: the interface also declares a ResourceLocation-keyed
            // playerAnimator_getAnimation(id) returning a single layer, which is NOT the thing to ask.
            getAnimation = iface.getMethod(GET_ANIMATION);
            // Resolved off the declared return type rather than by naming AnimationApplier, so a
            // version that moves or renames that class still works as long as the shape holds.
            // isActive() is inherited (declared on AnimationProcessor) — getMethod walks superclasses.
            isActive = getAnimation.getReturnType().getMethod(IS_ACTIVE);
            LogUtil.info("[SteamPad] Player Animator detected — its animations will now suppress "
                    + "Entity Model Features for that player, so mods built on it (Gliders and "
                    + "friends) animate correctly under Fresh Animations.");
        } catch (ClassNotFoundException e) {
            LogUtil.debug("[SteamPad] Player Animator not installed — no animation bridge needed.");
            iface = null;
        } catch (Throwable t) {
            LogUtil.warn("[SteamPad] Player Animator is installed but its API did not match ({}): mods "
                    + "built on it may be overridden by Entity Model Features' animations.", t.toString());
            iface = null;
        }
        ANIMATED_PLAYER = iface;
        GET_ANIMATION_METHOD = iface == null ? null : getAnimation;
        IS_ACTIVE_METHOD = iface == null ? null : isActive;
    }

    private PlayerAnimCompat() {}

    /** True when the library is present and its API resolved — otherwise every call here is a no-op. */
    public static boolean isAvailable() {
        return ANIMATED_PLAYER != null && GET_ANIMATION_METHOD != null && IS_ACTIVE_METHOD != null;
    }

    /**
     * True while Player Animator is currently animating {@code entity}.
     *
     * <p>Takes {@code Object} rather than {@code Entity} because the caller's argument arrives typed
     * as EMF's own entity interface; the library mixes {@code IAnimatedPlayer} onto the player entity
     * itself, so the same object answers both. Anything that is not an animated player — every mob,
     * every block entity, every player with nothing playing — returns false.
     *
     * <p>Called once per entity per frame, so it must never throw: a reflective call that fails is
     * treated as "not animating", which is exactly the behaviour that existed before this class.
     */
    public static boolean isAnimationActive(Object entity) {
        if (!isAvailable() || !ANIMATED_PLAYER.isInstance(entity)) return false;
        try {
            Object applier = GET_ANIMATION_METHOD.invoke(entity);
            return applier != null && Boolean.TRUE.equals(IS_ACTIVE_METHOD.invoke(applier));
        } catch (Throwable t) {
            return false;
        }
    }
}
