package dev.steampad.radial;

import dev.steampad.service.DeathLocationService;
import dev.steampad.util.LogUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Built-in radial commands that need something the player cannot simply type into a
 * {@link RadialActionType#CHAT_COMMAND} slot — either because the command text has to be computed at
 * the moment it fires, or because it deserves a client-side check before being sent.
 *
 * <p>They sit alongside the plain text command in the slot-type picker rather than replacing it: a
 * SMART_COMMAND slot stores one of these ids in exactly the same {@code action} field a CHAT_COMMAND
 * slot stores its text in, so nothing else about wheels, icons, labels or triggers changes.
 *
 * <p>Everything here ultimately goes out as an ordinary chat command through the player's own
 * connection, so the usual server rules apply — these need permission to run {@code /tp}, exactly as
 * typing the same command by hand would.
 */
public enum SmartCommand {

    /**
     * Teleport back to where you last died.
     *
     * <p>The position comes from {@link DeathLocationService} (observed on the client, falling back to
     * vanilla's own synced last-death location). {@code execute in <dim> run tp} so it also works when
     * you died in another dimension — a plain {@code /tp x y z} would drop you at those coordinates in
     * whichever dimension you happen to be standing in now, which is not where you died.
     */
    TP_LAST_DEATH("tp_last_death") {
        @Override
        public void execute(Minecraft mc) {
            DeathLocationService.Target t = DeathLocationService.lastDeath(mc);
            if (t == null) {
                notifyPlayer(mc, "steampad.smartcmd.tp_last_death.unknown");
                return;
            }
            send(mc, String.format(Locale.ROOT, "execute in %s run tp @s %.2f %.2f %.2f",
                    t.dimensionId(), t.x(), t.y(), t.z()));
        }
    },

    /**
     * Teleport to whichever other player is currently furthest away.
     *
     * <p>Vanilla has no single command for "the farthest player", but its target selectors can express
     * it: {@code @a[limit=1,sort=furthest]} sorts by distance from whoever runs the command. The
     * {@code distance=0.01..} filter is what excludes YOU — without it the selector's furthest match is
     * the executing player (distance 0 sorts last, but with a single player online it is also the only
     * match) and the command would teleport you onto yourself.
     *
     * <p>Checked client-side first: with nobody else connected the selector matches nothing and the
     * server answers with a raw red "No entity was found", which from a radial button reads like the
     * mod broke. The player list the client already has is enough to say something useful instead.
     */
    TP_FARTHEST_PLAYER("tp_farthest_player") {
        @Override
        public void execute(Minecraft mc) {
            if (mc.getConnection() != null && mc.getConnection().getOnlinePlayers().size() <= 1) {
                notifyPlayer(mc, "steampad.smartcmd.tp_farthest_player.alone");
                return;
            }
            send(mc, "tp @s @a[limit=1,sort=furthest,distance=0.01..]");
        }
    };

    private final String id;

    SmartCommand(String id) { this.id = id; }

    /** Stable id stored in the slot's {@code action} field. Never localized — it is persisted. */
    public String id() { return id; }

    /** Display name for the picker and the wheel label. */
    public Component label() { return Component.translatable("steampad.smartcmd." + id); }

    /** One-line explanation shown under the name in the picker. */
    public Component description() { return Component.translatable("steampad.smartcmd." + id + ".desc"); }

    /** Runs the command. Called on the client thread from the radial's slot execution. */
    public abstract void execute(Minecraft mc);

    /** Looks up a stored id, or null when the slot holds something unrecognized. */
    public static SmartCommand byId(String id) {
        if (id == null || id.isBlank()) return null;
        for (SmartCommand c : values()) if (c.id.equals(id)) return c;
        return null;
    }

    /** Sends {@code cmd} (without the leading slash) as the player. */
    private static void send(Minecraft mc, String cmd) {
        if (mc.player == null || mc.player.connection == null) return;
        LogUtil.debug("[SteamPad] Smart command → /{}", cmd);
        mc.player.connection.sendCommand(cmd);
    }

    /** Action-bar feedback for the cases where there is nothing sensible to send. */
    private static void notifyPlayer(Minecraft mc, String key) {
        if (mc.player == null) return;
        mc.player.displayClientMessage(Component.translatable(key), true);
    }
}
