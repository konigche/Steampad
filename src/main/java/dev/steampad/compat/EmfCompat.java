package dev.steampad.compat;

import dev.steampad.emote.EmoteAnimator;
import dev.steampad.util.LogUtil;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * Optional compatibility with <b>Entity Model Features</b> (EMF), the mod that executes OptiFine-format
 * custom entity models — the engine behind the "Fresh Animations / FA+Player" resource pack.
 *
 * <p><b>The conflict.</b> EMF does not replace the {@code PlayerModel} class, so SteamPad's emote
 * mixins still load and still run. It applies its own {@code .jem} animation from a hook in the entity
 * RENDERER, positioned immediately after the whole {@code setupAnim(...)} call returns — which is
 * strictly later than any TAIL injection *inside* {@code setupAnim}, where SteamPad's emote pose is
 * applied. So EMF overwrites exactly the bones the pack animates (head, body, arms, legs) while bones
 * it doesn't touch keep SteamPad's values. The reported symptom matches precisely: the character looks
 * <i>stiff</i> rather than deformed, with the dance "happening behind" — the emote genuinely is
 * running and being applied, it just never survives to the screen.
 *
 * <p>Critically, this is <b>not</b> a mixin-priority contest: it is a different, later call site, so no
 * priority value could win it. The fix has to come from EMF standing down.
 *
 * <p><b>The fix.</b> EMF ships a public API for exactly this, and already uses it itself: its bundled
 * {@code PALCompat} registers a pause condition so a player-animation library's dance suppresses EMF's
 * animation for that entity. That compat is gated on that library's own mod id, so SteamPad (an
 * independent port, not that library) gets none of it — hence this class registering the equivalent
 * condition for SteamPad's own emote state.
 *
 * <p><b>Why animation-pause and not model-lock.</b> EMF also exposes a "force the vanilla model"
 * switch, which sounds closer to "disable the mod while an emote plays" — but model substitution is
 * decided when resources load, not per frame, so flipping it mid-render is the wrong lever. The
 * animation pause is genuinely evaluated per frame and per entity, which is what this needs.
 *
 * <p><b>Why reflection instead of a compile-time dependency.</b> This is a multi-version project:
 * pinning an EMF artifact would mean a different EMF build per Minecraft variant, in the build files,
 * for an optional integration. Reflection keeps the dependency matrix untouched and degrades to a
 * clean no-op when EMF is absent or has moved its API — which is exactly the behaviour that existed
 * before this class, so a failure here can never be worse than not having it.
 */
public final class EmfCompat {

    private static final String API_CLASS = "traben.entity_model_features.EMFAnimationApi";
    private static final String REGISTER_METHOD = "registerPauseCondition";

    /** One-shot latch for the "what did EMF actually hand us?" diagnostic — see {@link #shouldPause}. */
    private static boolean loggedFirstCall = false;

    private EmfCompat() {}

    /**
     * Registers SteamPad's pause condition with EMF, if EMF is present. Safe to call unconditionally;
     * every failure path is a logged no-op.
     */
    public static void init() {
        Class<?> api;
        try {
            api = Class.forName(API_CLASS);
        } catch (ClassNotFoundException e) {
            LogUtil.debug("[SteamPad] Entity Model Features not installed — no emote compat needed.");
            return;
        }
        try {
            Method register = null;
            for (Method m : api.getMethods()) {
                // Matched by name + a single Function parameter rather than by exact signature: the
                // declared parameter is Function<EMFEntity,Boolean>, whose type argument is erased at
                // runtime and whose EMFEntity type we deliberately never reference.
                if (m.getName().equals(REGISTER_METHOD)
                        && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == Function.class) {
                    register = m;
                    break;
                }
            }
            if (register == null) {
                LogUtil.warn("[SteamPad] Entity Model Features is installed but its {}(Function) API was "
                        + "not found — emotes may be overridden by its animations. This usually means EMF "
                        + "changed its API; SteamPad needs an update to match.", REGISTER_METHOD);
                return;
            }
            Function<Object, Boolean> condition = EmfCompat::shouldPause;
            register.invoke(null, condition);
            // Touch the bridge here (not on the first frame) so its own detection line lands next to
            // this one in the log — the two together say exactly what the pause condition covers.
            boolean playerAnim = PlayerAnimCompat.isAvailable();
            LogUtil.info("[SteamPad] Entity Model Features detected — registered pause condition, so "
                    + "SteamPad emotes{} take priority over its custom animations while they play.",
                    playerAnim ? " and Player Animator animations (Gliders and friends)" : "");
        } catch (Throwable t) {
            LogUtil.warn("[SteamPad] Could not register the Entity Model Features compat ({}): emotes may "
                    + "be overridden by its animations.", t.toString());
        }
    }

    /**
     * EMF's per-entity, per-frame question: should its custom animation be skipped for this entity?
     *
     * <p>Answered by "is SteamPad currently posing that entity", so the pause lasts exactly as long as
     * the emote does and nothing has to be un-paused later — a stateless condition cannot leak a
     * permanently-disabled EMF if a dance ends abnormally (disconnect, world change, crash mid-emote).
     *
     * <p>The parameter arrives typed as EMF's own {@code EMFEntity}, which this deliberately never
     * references. EMF mixes that interface onto entities themselves (its own bundled compat relies on
     * exactly that, testing the argument against another mod's entity-side interface), so the argument
     * is expected to BE the {@link Entity}. If some version ever changes that to a wrapper, the
     * {@code instanceof} simply fails and this returns false — EMF keeps animating, i.e. today's
     * behaviour, no crash. The one-shot log below records which of the two it actually was, so that
     * case is diagnosable from a single log instead of another round of guessing.
     */
    private static boolean shouldPause(Object emfEntity) {
        if (!loggedFirstCall) {
            loggedFirstCall = true;
            if (emfEntity instanceof Entity) {
                LogUtil.debug("[SteamPad] EMF pause condition is live (entity-typed argument, as expected).");
            } else {
                LogUtil.warn("[SteamPad] EMF handed its pause condition a {} instead of an Entity — SteamPad "
                        + "cannot tell which entity it refers to, so its emotes will keep being overridden "
                        + "by EMF's animations. Please report this with your EMF version.",
                        emfEntity == null ? "null" : emfEntity.getClass().getName());
            }
        }
        if (!(emfEntity instanceof Entity e)) return false;
        // Two reasons to stand down, not one. The second covers mods that animate the player through
        // KosmX's Player Animator (Gliders' glider-grab pose was the report): EMF's own PALCompat only
        // recognises the OTHER fork of that library, so without this it overwrites their animation
        // exactly the way it overwrote SteamPad's emotes. Full evidence in PlayerAnimCompat.
        return EmoteAnimator.isPlayingFor(e.getId()) || PlayerAnimCompat.isAnimationActive(e);
    }
}
