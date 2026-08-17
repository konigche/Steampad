package dev.steampad.compat;

import dev.steampad.config.ConfigManager;
import dev.steampad.util.LogUtil;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;

/**
 * Optional compatibility with the <b>Gliders</b> mod ({@code vc_gliders}), for one specific thing its
 * own "Glider Perspective" option cannot do by itself: survive a restart.
 *
 * <p><b>The problem, verified against the installed jar.</b> {@code VCGlidersClient.init()} builds the
 * option as
 * <pre>autoPerspective = OptionInstance.createBoolean("options.glider_perspective", false);</pre>
 * and stores it in a plain {@code public static} field. Its button on the Controls screen
 * ({@code ClientUtil.povButton}) calls {@code createButton(mc.options, …)}, which only edits that
 * in-memory object. Nothing anywhere in the mod writes the value: vanilla's {@code Options} persists
 * only the fields its own {@code processOptions} enumerates, so a standalone {@code OptionInstance} is
 * invisible to {@code options.txt}, and Gliders ships no config file that mentions it either
 * (javap + a full scan of the jar). So the option is <b>structurally unable</b> to persist — it is
 * re-created as {@code false} on every launch, which is exactly the report: "le pongo Sí todas las
 * sesiones pero no lo respeta". There is no file the user could edit to force it.
 *
 * <p><b>What this does.</b> When the SteamPad setting is on, flips that same {@code OptionInstance}
 * back to {@code true} once per session, as soon as a world is entered. Deliberately a one-shot rather
 * than a per-tick enforcement: nothing in the game ever resets the field once set, so re-checking it
 * every tick would be pure overhead (see D190 — this project does not add per-tick work without a
 * measured reason). Toggling it off in Gliders' own screen after that still works for the rest of the
 * session, exactly as it does today.
 *
 * <p><b>Why reflection.</b> Same reasoning as {@link EmfCompat}/{@link PlayerAnimCompat}: an optional
 * mod must not become a build dependency of a multi-version project. Every failure path is a logged
 * no-op that leaves today's behaviour untouched.
 *
 * <p>Off by default. Forcing another mod's camera option is not something a controller mod should do
 * uninvited — it is opt-in from Ajustes Globales.
 */
public final class GlidersCompat {

    private static final String CLIENT_CLASS = "net.venturecraft.gliders.VCGlidersClient";
    private static final String OPTION_FIELD = "autoPerspective";

    /** One-shot latch: cleared on disconnect so the next world entered applies it again. */
    private static boolean appliedThisSession = false;
    /** Set once when the mod is absent or its API doesn't match, so we stop retrying every world. */
    private static boolean unavailable = false;

    private GlidersCompat() {}

    /**
     * Applies the option once per world entry, if the user asked for it. Cheap to call every tick:
     * one boolean read once the work is done (or once it is known to be impossible).
     *
     * <p>The "left the world" re-arm is folded in here rather than hung off a disconnect event, so the
     * whole one-shot lives in one place and there is no second registration to keep in sync.
     */
    public static void clientTick(Minecraft mc) {
        if (unavailable) return;
        if (mc.player == null) {         // between worlds: re-arm, and never touch Gliders here —
            appliedThisSession = false;  // its own init may not even have built the option yet.
            return;
        }
        if (appliedThisSession) return;
        if (!ConfigManager.getGlobal().glidersForcePerspective) return;
        appliedThisSession = true;       // one attempt per world, success or not
        apply();
    }

    private static void apply() {
        try {
            Class<?> clientClass = Class.forName(CLIENT_CLASS);
            Field field = clientClass.getField(OPTION_FIELD);
            Object option = field.get(null);
            if (option == null) {
                LogUtil.debug("[SteamPad] Gliders is present but its perspective option is not built yet.");
                appliedThisSession = false;   // its init hasn't run — try again next world
                return;
            }
            // The field is Minecraft's OWN OptionInstance, so call it directly instead of reflecting.
            //
            // This is what the first cut got wrong, and the user's log said so in as many words:
            // "its perspective option has no set(value) method". It does have one — but in production
            // Fabric remaps the game to INTERMEDIARY, so at runtime the method is `method_41748`, not
            // `set`. A reflective lookup by Mojmap name can never match, and would silently fail on
            // every version forever. A compiled call is remapped per variant by Loom, so it is correct
            // in 1.21.1 and 1.21.10 alike (`public void set(T)` verified with javap on both mapped jars).
            //
            // Reflection stays only where it must: reaching the Gliders CLASS and FIELD, which this
            // project cannot compile against. The cast is guarded by the instanceof, so a future Gliders
            // that changes the field's type degrades to the logged no-op below instead of throwing.
            if (!(option instanceof net.minecraft.client.OptionInstance<?> instance)) {
                unavailable = true;
                LogUtil.warn("[SteamPad] Gliders' perspective option is no longer an OptionInstance ({}) "
                        + "— leaving it alone.", option.getClass().getName());
                return;
            }
            @SuppressWarnings("unchecked")
            net.minecraft.client.OptionInstance<Boolean> typed =
                    (net.minecraft.client.OptionInstance<Boolean>) instance;
            typed.set(Boolean.TRUE);
            LogUtil.info("[SteamPad] Gliders detected — 'Glider Perspective' turned back on for this "
                    + "session (the mod itself never saves it; disable this in Ajustes Globales).");
        } catch (ClassNotFoundException notInstalled) {
            unavailable = true;
            LogUtil.debug("[SteamPad] Gliders not installed — perspective compat not needed.");
        } catch (Throwable t) {
            unavailable = true;
            LogUtil.warn("[SteamPad] Could not set Gliders' perspective option ({}): leaving it alone.",
                    t.toString());
        }
    }
}
