package dev.steampad.service;

import dev.steampad.util.LogUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Remembers where the local player last died, so the radial's "teleport to my death point" command has
 * somewhere to send you.
 *
 * <p>Two sources, best first:
 * <ol>
 *   <li><b>Observed client-side</b> — the exact {@code Vec3} the player was standing on the tick they
 *       died. Sub-block precise, and it exists the instant it happens.</li>
 *   <li><b>{@code Player.getLastDeathLocation()}</b> — the vanilla {@code GlobalPos} the server keeps
 *       and syncs (it is what the respawn screen's recovery compass points at). Block-precision only,
 *       but it survives relaunching the game and joining from another client, which the observed one
 *       cannot.</li>
 * </ol>
 *
 * <p>Purely observational: it reads the player's own state on the client tick and never touches
 * anything. Tracking is latched per death so a corpse lying there for several seconds records the
 * position once, at the moment of death, rather than following the body.
 */
public final class DeathLocationService {

    private static Vec3 observedPos;
    private static ResourceKey<Level> observedDimension;
    /** True while the player is dead and this death has already been recorded. */
    private static boolean recordedThisDeath = false;

    private DeathLocationService() {}

    /** Called once per client tick from the mod's tick hook. */
    public static void clientTick(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) {
            // Back at the main menu / disconnected: the observed position belongs to a world we are no
            // longer in, and leaving it would let the next world's teleport use the previous world's
            // coordinates. Vanilla's own getLastDeathLocation still covers "where I died in THIS world"
            // once we join one.
            reset();
            return;
        }
        boolean dead = mc.player.isDeadOrDying();
        if (!dead) {
            recordedThisDeath = false;
            return;
        }
        if (recordedThisDeath) return;
        recordedThisDeath = true;
        observedPos = mc.player.position();
        observedDimension = mc.level.dimension();
        LogUtil.debug("[SteamPad] Death position recorded: {} in {}", observedPos,
                observedDimension.location());
    }

    /** Forgets the observed position — e.g. on disconnect, where it belongs to another world. */
    public static void reset() {
        observedPos = null;
        observedDimension = null;
        recordedThisDeath = false;
    }

    /** Where to teleport, or null when neither source knows of a death yet. */
    public static Target lastDeath(Minecraft mc) {
        if (observedPos != null && observedDimension != null) {
            return new Target(observedDimension, observedPos.x, observedPos.y, observedPos.z);
        }
        if (mc != null && mc.player != null) {
            GlobalPos gp = mc.player.getLastDeathLocation().orElse(null);
            if (gp != null) {
                // Block position → its centre, so the teleport doesn't land in a corner.
                return new Target(gp.dimension(),
                        gp.pos().getX() + 0.5, gp.pos().getY(), gp.pos().getZ() + 0.5);
            }
        }
        return null;
    }

    /** A resolved teleport destination. */
    public record Target(ResourceKey<Level> dimension, double x, double y, double z) {
        /** Dimension id as a command argument, e.g. {@code minecraft:the_nether}. */
        public String dimensionId() { return dimension.location().toString(); }
    }
}
