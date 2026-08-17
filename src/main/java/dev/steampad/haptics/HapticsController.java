package dev.steampad.haptics;

import dev.steampad.compat.mc.UseAnimCompat;
import dev.steampad.config.ConfigManager;
import dev.steampad.config.ControllerConfig;
import dev.steampad.service.ActiveControllerService;
import dev.steampad.service.ControllerManager;
import dev.steampad.util.LogUtil;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Event-driven controller haptics — "does the game vibrate, and for what". Minecraft (both editions)
 * ships no built-in controller rumble; this is a from-scratch design informed by (1) the community's
 * long-standing wishlist for it (damage, explosions, breaking blocks, lightning, mounts — see the
 * public Minecraft Feedback threads), (2) Controlify's proven Java-Fabric implementation (damage,
 * block-break, lightning), and (3) AAA haptic design principles researched and discussed with the
 * user across several games: Returnal's continuous ambient rumble for environmental presence, God of
 * War Ragnarök's builds-then-pays-off pattern (the Leviathan Axe recall "gradually swells" to a
 * "meaty thud"), and RDR2/Silent Hill 2's proximity/heartbeat-style guidance that substitutes for HUD.
 *
 * <p><b>Hardware reality:</b> our motors are SDL3/Steamworks4j dual-motor rumble — two float
 * intensities (low-freq "heavy", high-freq "sharp") and a duration, no HD haptic textures, no
 * adaptive triggers (see B003). Every event picks a {@code freqBalance} leaning toward one motor to
 * approximate a "feel" within that real constraint, and because it's a <em>single channel</em>, only
 * one thing can be "playing" at any instant — see {@link Tier} below, which exists specifically to
 * arbitrate that.
 *
 * <p>All six {@link ControllerConfig} vibration category multipliers (previously configured in the UI
 * but wired to nothing) are live: every event fires through exactly one category.
 */
public final class HapticsController {

    /**
     * Priority for the single rumble channel, highest first. A lower-tier request is silently
     * dropped while a higher (or equal) tier pulse's estimated duration is still running — this is
     * the direct consequence of having one motor pair, not an aesthetic choice: two effects cannot
     * literally play at once, so whichever matters most wins and the loser is simply skipped (it's
     * transient — the next occurrence gets its own chance).
     */
    public enum Tier { CRITICAL, DANGER, IMPACT, AMBIENT, COSMETIC }

    public enum Category { PLAYER, WORLD, INTERACTION, GUI, GLOBAL_EVENT, MISC }

    /** A named, on-demand preview of a real effect's tier/category/intensity/duration/freqBalance —
     *  exact values for fixed effects, representative mid-range values for ones whose real magnitude
     *  depends on runtime data (fall distance, damage taken, proximity...). Purely a diagnostic/tuning
     *  aid — never wired into any real gameplay trigger. */
    public record HapticPreset(String key, Tier tier, Category category, float intensity, int durationMs, float freqBalance) {}

    /** Mirrors every real effect in this file so a player can feel exactly what a slider change would
     *  produce without reproducing the real trigger (take damage, walk on slime, get near a boss...).
     *  Keep this in sync by eye when tuning an effect above — there's no automated link between the two
     *  on purpose (duplicating a magic number here is far lower-risk than reflecting into private tick
     *  methods to "derive" it). */
    public static final java.util.List<HapticPreset> TEST_PRESETS = java.util.List.of(
            new HapticPreset("steampad.haptics.test.damage_light", Tier.IMPACT, Category.PLAYER, 0.3f, 150, 0.35f),
            new HapticPreset("steampad.haptics.test.damage_heavy", Tier.IMPACT, Category.PLAYER, 0.75f, 300, 0.35f),
            new HapticPreset("steampad.haptics.test.heartbeat", Tier.DANGER, Category.PLAYER, 0.45f, 90, 0.15f),
            new HapticPreset("steampad.haptics.test.death", Tier.CRITICAL, Category.PLAYER, 0.85f, 450, 0.2f),
            new HapticPreset("steampad.haptics.test.fall_light", Tier.IMPACT, Category.PLAYER, 0.25f, 200, 0.1f),
            new HapticPreset("steampad.haptics.test.fall_heavy", Tier.IMPACT, Category.PLAYER, 0.9f, 380, 0.1f),
            new HapticPreset("steampad.haptics.test.hunger", Tier.AMBIENT, Category.PLAYER, 0.22f, 70, 0.55f),
            new HapticPreset("steampad.haptics.test.freezing", Tier.AMBIENT, Category.PLAYER, 0.2f, 60, 0.65f),
            new HapticPreset("steampad.haptics.test.drowning", Tier.DANGER, Category.PLAYER, 0.55f, 90, 0.3f),
            new HapticPreset("steampad.haptics.test.burning", Tier.DANGER, Category.PLAYER, 0.4f, 90, 0.6f),
            new HapticPreset("steampad.haptics.test.eating", Tier.COSMETIC, Category.PLAYER, 0.14f, 55, 0.7f),
            new HapticPreset("steampad.haptics.test.slime_walk", Tier.AMBIENT, Category.PLAYER, 0.45f, 320, 0.25f),
            new HapticPreset("steampad.haptics.test.honey_walk", Tier.COSMETIC, Category.PLAYER, 0.05f, 60, 0.75f),
            new HapticPreset("steampad.haptics.test.water_splash", Tier.COSMETIC, Category.PLAYER, 0.18f, 70, 0.5f),
            new HapticPreset("steampad.haptics.test.glide", Tier.AMBIENT, Category.PLAYER, 0.1f, 100, 0.55f),
            new HapticPreset("steampad.haptics.test.lightning", Tier.AMBIENT, Category.WORLD, 0.8f, 300, 0.7f),
            new HapticPreset("steampad.haptics.test.creeper_fuse", Tier.DANGER, Category.WORLD, 0.85f, 150, 0.55f),
            new HapticPreset("steampad.haptics.test.warden", Tier.DANGER, Category.WORLD, 0.7f, 320, 0.1f),
            new HapticPreset("steampad.haptics.test.enderman_teleport", Tier.DANGER, Category.WORLD, 0.3f, 45, 0.85f),
            new HapticPreset("steampad.haptics.test.boss_ping", Tier.DANGER, Category.WORLD, 0.65f, 240, 0.15f),
            new HapticPreset("steampad.haptics.test.portal", Tier.DANGER, Category.WORLD, 0.75f, 240, 0.2f),
            new HapticPreset("steampad.haptics.test.explosion", Tier.CRITICAL, Category.WORLD, 0.8f, 375, 0.15f),
            new HapticPreset("steampad.haptics.test.melee_hit", Tier.IMPACT, Category.INTERACTION, 0.4f, 140, 0.6f),
            new HapticPreset("steampad.haptics.test.melee_crit", Tier.IMPACT, Category.INTERACTION, 0.55f, 190, 0.6f),
            new HapticPreset("steampad.haptics.test.weapon_sword", Tier.IMPACT, Category.INTERACTION, 0.45f, 150, 0.55f),
            new HapticPreset("steampad.haptics.test.weapon_axe", Tier.IMPACT, Category.INTERACTION, 0.5f, 170, 0.4f),
            new HapticPreset("steampad.haptics.test.weapon_trident", Tier.IMPACT, Category.INTERACTION, 0.5f, 170, 0.25f),
            new HapticPreset("steampad.haptics.test.weapon_mace", Tier.IMPACT, Category.INTERACTION, 0.65f, 200, 0.15f),
            new HapticPreset("steampad.haptics.test.weapon_mace_smash", Tier.IMPACT, Category.INTERACTION, 0.9f, 300, 0.05f),
            new HapticPreset("steampad.haptics.test.weapon_arrow", Tier.IMPACT, Category.INTERACTION, 0.22f, 55, 0.9f),
            new HapticPreset("steampad.haptics.test.weapon_kill", Tier.CRITICAL, Category.INTERACTION, 0.75f, 260, 0.2f),
            new HapticPreset("steampad.haptics.test.block_broken", Tier.IMPACT, Category.INTERACTION, 0.5f, 190, 0.35f),
            new HapticPreset("steampad.haptics.test.radial_select", Tier.COSMETIC, Category.GUI, 0.05f, 25, 0.85f)
    );

    /** Fires a test preset immediately, clearing whatever occupies the rumble channel first — a manual
     *  preview should never get silently dropped by the same tier-arbitration that paces real events
     *  (see {@link #canFire}). Still goes through {@link #fire}, so it still respects "Allow Vibration"
     *  and the master/category sliders — what you feel here is exactly what those same slider values
     *  would produce for the real effect. */
    public static void testFire(HapticPreset p) {
        activeTier = null;
        fire(p.tier(), p.category(), p.intensity(), p.durationMs(), p.freqBalance());
    }

    private HapticsController() {}

    // ---- Channel arbitration ---------------------------------------------------------------

    private static Tier activeTier = null;
    private static long activeUntilMs = 0L;

    /** The tier currently occupying the rumble channel, or null if it's free (expired or never set).
     *  Debug-dump / diagnostic use only. */
    public static Tier occupyingTier() {
        return (activeTier != null && System.currentTimeMillis() < activeUntilMs) ? activeTier : null;
    }

    /** How much longer {@link #occupyingTier()} will keep occupying the channel, in ms (0 if free). */
    public static long occupiedForMs() {
        long remaining = activeUntilMs - System.currentTimeMillis();
        return remaining > 0 ? remaining : 0;
    }

    private static boolean canFire(Tier tier) {
        long now = System.currentTimeMillis();
        if (activeTier == null || now >= activeUntilMs) return true;
        return tier.ordinal() <= activeTier.ordinal();
    }

    private static void occupy(Tier tier, int durationMs) {
        activeTier = tier;
        activeUntilMs = System.currentTimeMillis() + durationMs;
    }

    // ---- Tick-based state -------------------------------------------------------------------

    private static float lastHealth = -1f;
    private static long lastHeartbeatMs = 0L;
    private static boolean wasDeathScreen = false;

    private static boolean wasOnGroundForFall = true;
    private static double maxFallSeen = 0.0;

    private static long lastHungerPulseMs = 0L;
    private static long lastFreezePulseMs = 0L;
    private static long lastAirPulseMs = 0L;
    private static long lastBurnPulseMs = 0L;
    private static long lastEatPulseMs = 0L;
    private static long lastWardenPulseMs = 0L;
    /** True once the player has damaged the nearby Warden — silences its dread pulse, mirroring
     *  {@link #engagedBosses} for regular bosses (the Warden is deliberately kept out of that generic
     *  system — see {@link #pollMobs}). Re-armed when no Warden is left in the 40-block scan box. */
    private static boolean wardenEngaged = false;
    private static long lastPortalPingMs = 0L;
    private static boolean wasNearGeode = false;
    private static boolean wasNearTreasureChest = false;

    private static long lastSquishyPulseMs = 0L;
    private static long lastSquishyDebugMs = 0L;
    private static double lastSquishyX = Double.NaN;
    private static double lastSquishyZ = Double.NaN;
    private static boolean wasTouchingWaterForSplash = false;
    private static long lastGlideRumbleMs = 0L;
    private static long lastBossPingMs = 0L;

    private static int tickCounter = 0;
    private static final int LIGHTNING_POLL_TICKS = 10;    // ~0.5s — a rare event, no rush
    private static final int MOB_SCAN_POLL_TICKS = 4;      // ~0.2s — fuse timing wants responsiveness
    private static final int WORLD_SCAN_POLL_TICKS = 20;   // ~1s — portal/geode/chest don't move

    private static final Set<Integer> seenLightning = new HashSet<>();
    private static final Map<Integer, Long> creeperIgnitedSinceMs = new HashMap<>();
    private static final Map<Integer, Long> creeperLastPingMs = new HashMap<>();
    private static final Map<Integer, Vec3> endermanLastPos = new HashMap<>();
    /** Session-local (cleared on game restart, not persisted) — "don't ping a chest twice". */
    private static final Set<BlockPos> openedChests = new HashSet<>();
    /**
     * Boss-like entity IDs the player has already "engaged" (seen up close, or landed a hit on) — see
     * {@link #pollBossProximity}. Sticky and pruned only when the boss leaves {@link #BOSS_PING_RANGE}
     * (feedback: "cuando le demos el primer golpe que ya pase la vibración... si nos alejamos se
     * reinicie"), so the mystery ping doesn't restart every poll just because line-of-sight breaks for
     * a moment mid-fight.
     */
    private static final Set<Integer> engagedBosses = new HashSet<>();

    /**
     * Entities recently hit by the LOCAL player (entity id → confirmation deadline, wall-clock ms).
     * A kill can never be decided at hit time on the client — the target's client-side health only
     * updates when the server answers, one or more ticks AFTER the attack packet left — so every
     * player-attributed hit arms this window instead, and {@link #pollPendingKills} confirms the
     * death from the entity's actual (server-replicated) state. See D114.
     */
    private static final Map<Integer, Long> recentPlayerHits = new HashMap<>();
    private static final long KILL_CONFIRM_WINDOW_MS = 800L;

    /**
     * Dedup between the two melee-hit pulse sources (D115): {@link #onMeleeHit} (the vanilla
     * {@code attackEntity} mixin — immediate, but bypassed entirely by combat-overhaul mods that
     * send their own attack packets) and {@link #onEntityDamaged} (the server's damage packet —
     * 1-2 ticks later, but fires for EVERY hit the player lands no matter what initiated it).
     * Whichever runs first for a given target claims the pulse; the other stays quiet.
     */
    private static int lastMeleePulseTargetId = -1;
    private static long lastMeleePulseMs = 0L;
    private static final long MELEE_PULSE_DEDUP_MS = 400L;

    // ---- Boss detection heuristic (see #isBossLike) -----------------------------------------
    private static final double BOSS_PING_RANGE = 40.0;
    private static final double BOSS_REVEAL_RANGE = 20.0;   // close + in sight → the mystery is over
    private static final float BOSS_HEALTH_THRESHOLD = 100f;
    // Both width AND height must clear these together (not "either") — a lone tall-but-thin mob like
    // an Enderman (height ~2.9, width 0.6) would otherwise false-positive on height alone.
    private static final float BOSS_WIDTH_THRESHOLD = 1.4f;
    private static final float BOSS_HEIGHT_THRESHOLD = 1.8f;

    // ---- Pending hit descriptor: bridges the DamageSource packet (onPlayerDamaged) to the next
    // HP-drop tick (tickDamageAndHealth) so a single hit gets one signature, not two competing ones.
    private record PendingHit(Tier tierOverride, float freqBalance, float magMult, float durMult) {}
    private static PendingHit pendingHit = null;
    private static long pendingHitExpireMs = 0L;

    private static boolean wasPaused = false;

    /** Call once per client tick (from the main tick handler). */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            resetTransientState();
            return;
        }

        // Pause menu: the world (and gameplay) freezes, but wall-clock time — what every interval
        // timer below is measured against — keeps advancing, so without this the boss ping (and any
        // other periodic pulse) kept firing while paused (feedback: "no se detiene la vibracion...
        // al pasar al menu pausa... se deben detener las vibraciones del mando"). Stop whatever is
        // currently buzzing exactly once, on the pause EDGE, then skip polling entirely until resumed.
        boolean paused = mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen;
        if (paused) {
            if (!wasPaused) stopRumbleNow();
            wasPaused = true;
            return;
        }
        wasPaused = false;

        tickDamageAndHealth(mc);
        tickFallImpact(mc);
        tickBodyStates(mc);
        tickSquishyUnderfoot(mc);
        tickAquaticAndGliding(mc);
        pollPendingKills(mc);

        tickCounter++;
        if (tickCounter % LIGHTNING_POLL_TICKS == 0) pollLightning(mc);
        if (tickCounter % MOB_SCAN_POLL_TICKS == 0) { pollMobs(mc); pollBossProximity(mc); }
        if (tickCounter % WORLD_SCAN_POLL_TICKS == 0) pollWorldProximity(mc);
    }

    private static void resetTransientState() {
        lastHealth = -1f;
        wasDeathScreen = false;
        wasOnGroundForFall = true;
        maxFallSeen = 0.0;
        wasNearGeode = false;
        wasNearTreasureChest = false;
        wasTouchingWaterForSplash = false;
        pendingHit = null;
        endermanLastPos.clear();
        engagedBosses.clear();
        wardenEngaged = false;
        recentPlayerHits.clear();
        lastSquishyX = Double.NaN;
        lastSquishyZ = Double.NaN;
        lastMeleePulseTargetId = -1;
        lastMeleePulseMs = 0L;
    }

    // ---- Body: damage, low-health heartbeat, death -----------------------------------------

    private static void tickDamageAndHealth(Minecraft mc) {
        float hp = mc.player.getHealth();
        float maxHp = Math.max(1f, mc.player.getMaxHealth());

        // Damage taken: any HP drop since last tick. Magnitude (and duration) scales with the hit,
        // so a fall/explosion naturally feels heavier than a zombie nibble — no cause-tracking needed.
        // The lastHealth<0 guard skips the first tick after (re)spawn, where health is SET not lost.
        if (lastHealth >= 0f && hp < lastHealth - 0.01f) {
            float delta = lastHealth - hp;
            float mag = clamp(delta / maxHp * 2.2f, 0.15f, 0.9f);
            int dur = (int) clamp(120 + delta * 22f, 120, 320);
            float freqBalance = 0.35f;
            Tier tier = Tier.IMPACT;

            // If onPlayerDamaged (the DamageSource packet hook) classified this hit — boss, magic/
            // wither, or a known mob attacker — use its signature instead of the plain generic one.
            // Fire/explosion/fall/drowning/freezing never set a pending hit (they already own their
            // own dedicated signatures elsewhere), so this can never compete with those.
            PendingHit hit = consumePendingHit();
            if (hit != null) {
                freqBalance = hit.freqBalance();
                mag = clamp(mag * hit.magMult(), 0.15f, 1.0f);
                dur = (int) (dur * hit.durMult());
                if (hit.tierOverride() != null) tier = hit.tierOverride();
            }
            // Diagnostics (feedback: "cuando recibo daño de un jugador o mob ya no vibro, cuando hago
            // daño si") — mirrors the melee-hit diagnostic line so a future report can tell "never
            // reached fire()" (nothing prints — tick/health-poll problem) from "reached it but
            // blocked" (canFire/config) from "hardware rejected it" (rumbleAccepted=false).
            long dmgHandle = ActiveControllerService.getActiveHandle();
            ControllerConfig dmgCfg = dmgHandle != 0L ? ConfigManager.getControllerConfig(dmgHandle) : null;
            boolean dmgPreCanFire = canFire(tier);
            boolean dmgAccepted = fire(tier, Category.PLAYER, mag, dur, freqBalance);
            LogUtil.debug("[SteamPad][haptics-damage-taken] delta={} tier={} mag={} dur={} handle={} "
                            + "allowVibration={} vibrationPlayer={} vibrationMaster={} canFireBefore={} "
                            + "occupyingTierBefore={} rumbleAccepted={}",
                    delta, tier, mag, dur, dmgHandle,
                    dmgCfg != null && dmgCfg.allowVibration,
                    dmgCfg != null ? dmgCfg.vibrationPlayer : -1f,
                    dmgCfg != null ? dmgCfg.vibrationMaster : -1f,
                    dmgPreCanFire, occupyingTier(), dmgAccepted);
            // Getting hit deserves the same visual punch as landing one — "juice" cuts both ways.
            dev.steampad.input.JuiceController.shake(mag * 0.13, dur);
            dev.steampad.input.JuiceController.fovKick(mag * 0.1f);
        }
        lastHealth = hp;

        // Low-health heartbeat: a thump under 20% HP that quickens near zero — the tactile echo of
        // vanilla's own low-health heartbeat SOUND, reinforcing it instead of competing with it.
        float hpFrac = hp / maxHp;
        if (hp > 0f && hpFrac < 0.2f) {
            long now = System.currentTimeMillis();
            long interval = (long) (500 + 900 * (hpFrac / 0.2f));
            if (now - lastHeartbeatMs > interval) {
                lastHeartbeatMs = now;
                fire(Tier.DANGER, Category.PLAYER, 0.35f + (0.2f - hpFrac) * 2f, 90, 0.15f);
            }
        }

        boolean isDeathScreen = mc.screen instanceof DeathScreen;
        if (isDeathScreen && !wasDeathScreen) fire(Tier.CRITICAL, Category.PLAYER, 0.85f, 450, 0.2f);
        wasDeathScreen = isDeathScreen;
    }

    // ---- Body: fall + landing impact (independent of whether it actually damaged you) -------

    private static void tickFallImpact(Minecraft mc) {
        if (!mc.player.onGround()) {
            maxFallSeen = Math.max(maxFallSeen, mc.player.fallDistance);
            wasOnGroundForFall = false;
            return;
        }
        if (wasOnGroundForFall) return;   // was already grounded — nothing to report
        wasOnGroundForFall = true;
        double fell = maxFallSeen;
        maxFallSeen = 0.0;
        if (fell < 3.0) return;   // skip steps/tiny hops
        boolean cushioned = mc.player.isInWater();
        float mag = cushioned
                ? (float) clamp(fell / 40.0, 0.08, 0.3)
                : (float) clamp(fell / 12.0, 0.2, 0.9);
        int dur = (int) clamp(150 + fell * 8, 150, 380);
        fire(Tier.IMPACT, Category.PLAYER, mag, dur, cushioned ? 0.45f : 0.1f);   // water = softer/duller
        // Juice bumped this round (feedback: "subele la intensidad, sobre todo cuando cae de un lugar
        // alto") — falls previously only shook the camera (0.12x, no FOV punch at all); a hard landing
        // from height is meant to be the biggest "juice" moment in the vocabulary, on par with a kill,
        // so both the shake multiplier and a genuine fovKick (missing entirely before) now scale with it.
        if (!cushioned) {
            dev.steampad.input.JuiceController.shake(mag * 0.22, dur);   // splash landing stays visually calm
            dev.steampad.input.JuiceController.fovKick(mag * 0.16f);
        }
    }

    // ---- Body: hunger, freezing, drowning ---------------------------------------------------

    private static void tickBodyStates(Minecraft mc) {
        long now = System.currentTimeMillis();

        // Hunger critical: the same threshold vanilla uses to disable sprinting — a meaningful
        // in-game line, not an arbitrary number. Spaced further apart than the health heartbeat so
        // the two are never confused for one another.
        if (mc.player.getFoodData().getFoodLevel() <= 6) {
            if (now - lastHungerPulseMs > 1400L) {
                lastHungerPulseMs = now;
                fire(Tier.AMBIENT, Category.PLAYER, 0.22f, 70, 0.55f);
            }
        }

        // Freezing (powder snow): an irregular shiver, not a clean rhythm — randomized interval and
        // intensity so it reads as "trembling", not "ticking".
        if (mc.player.isFullyFrozen()) {
            long interval = 480L + ThreadLocalRandom.current().nextInt(420);
            if (now - lastFreezePulseMs > interval) {
                lastFreezePulseMs = now;
                float mag = 0.16f + ThreadLocalRandom.current().nextFloat() * 0.1f;
                fire(Tier.AMBIENT, Category.PLAYER, mag, 60, 0.65f);
            }
        }

        // Drowning: accelerating panic pulses as air runs out, mirroring the vanilla bubble UI going
        // critical instead of replacing it.
        if (mc.player.isUnderWater()) {
            int air = mc.player.getAirSupply();
            int maxAir = Math.max(1, mc.player.getMaxAirSupply());
            float lowFrac = 1f - clamp((float) air / (maxAir / 3f), 0f, 1f);   // ramps up in the last third
            if (lowFrac > 0f) {
                long interval = (long) (700 - lowFrac * 550);
                if (now - lastAirPulseMs > interval) {
                    lastAirPulseMs = now;
                    fire(Tier.DANGER, Category.PLAYER, 0.3f + lowFrac * 0.4f, 90, 0.3f);
                }
            }
        }

        // Burning: pain pulses while on fire — DANGER, urgent cadence, sharp-leaning motor. Distinct
        // from the damage-taken thump (which also fires on each fire tick) by being continuous.
        if (mc.player.isOnFire()) {
            if (now - lastBurnPulseMs > 420L) {
                lastBurnPulseMs = now;
                fire(Tier.DANGER, Category.PLAYER, 0.4f, 90, 0.6f);
            }
        }

        // Eating/drinking: soft munch pulses matching the chew cadence while the item is consumed —
        // cosmetic texture, never competes with anything that matters.
        if (mc.player.isUsingItem()) {
            if (UseAnimCompat.isConsuming(mc.player.getUseItem())) {
                if (now - lastEatPulseMs > 260L) {
                    lastEatPulseMs = now;
                    fire(Tier.COSMETIC, Category.PLAYER, 0.14f, 55, 0.7f);
                }
            }
        }
    }

    // ---- Movement: deliberately silent (walking, sprinting) except slime/honey underfoot ---

    /**
     * Walking and sprinting stay silent by design (avoids the "everything buzzes" fatigue) — the one
     * exception is slime underfoot: a strong, genuinely continuous LOW hum for as long as you're
     * moving across it — the "stuck in something sticky, slowing you down" sensation (feedback: "debe
     * de ser constante, como cuando estas en algo pegajoso, debemos transmitir esa sensacion").
     * Scoped to slime ONLY (feedback: "solo en slime") — honey keeps its original, gentler occasional
     * tick below, unreported and unchanged this round.
     *
     * <p><b>Standing still on slime stays silent</b> (approved behavior: "cuando esta quieto debe de
     * dejar de vibrar aunque estemos encima del slime") — the hum is moving-only.</p>
     *
     * <p><b>D114 — root cause of "la primera vez vibra, despues cuando sigo caminando ya no lo hace"
     * found and fixed, superseding D113's wireless-rumble hypothesis</b> (refuted by the user's own
     * evidence: every haptic test preset — same {@code fire()} path, same sliders — IS felt on this
     * pad, so the pipeline works and the trigger was the broken half). The old gate was
     * {@code isOnGround() && getVelocity().horizontalLengthSquared() >= 0.003}: the FIRST step onto
     * slime passes it (normal-ground speed carried in, ground state still clean), but SUSTAINED
     * walking on a block that both slows the entity and micro-bounces it makes velocity/onGround
     * flicker under those thresholds, so the hum died right after the first pulse — exactly the
     * report. The gate is now the actual per-tick horizontal POSITION delta (ground truth for "am I
     * moving", however the movement happened), with a deeper block probe and no hard onGround
     * requirement. Magnitude also raised to this pad's established in-gameplay perceptibility floor
     * (0.45/320ms low-motor-biased; the felt-once pulse was 0.3/300).</p>
     */
    private static void tickSquishyUnderfoot(Minecraft mc) {
        // Actual horizontal displacement since the previous tick — ground truth for "am I moving",
        // measured from position, not velocity. getVelocity() proved untrustworthy exactly here
        // (D114): the first step onto slime still carries normal-ground speed (that pulse fired),
        // while SUSTAINED walking on a slowing, micro-bouncing block reads low/flickering, so the
        // velocity gate killed every pulse after the first. Position delta doesn't care HOW the
        // movement happened.
        double px = mc.player.getX(), pz = mc.player.getZ();
        double movedSq;
        if (Double.isNaN(lastSquishyX)) {
            movedSq = 0.0;
        } else {
            double dx = px - lastSquishyX, dz = pz - lastSquishyZ;
            movedSq = dx * dx + dz * dz;
            if (movedSq > 1.0) movedSq = 0.0;   // teleport/dimension jump — not walking
        }
        lastSquishyX = px;
        lastSquishyZ = pz;

        // Probe 0.35 under the live feet Y (was 0.2, and NOT getBlockPos().down() — see D114 and the
        // old flicker analysis it extends): deep enough that the micro-hops slime itself causes while
        // being walked on can't flick the check onto air, shallow enough that genuinely jumping off
        // the slime stops the hum within a tick or two. Same reasoning removes the old hard
        // isOnGround() gate — the probe depth already bounds how far off the block you can be.
        BlockPos underfootPos = BlockPos.containing(px, mc.player.getY() - 0.35, pz);
        Block underfoot = mc.level.getBlockState(underfootPos).getBlock();
        long now = System.currentTimeMillis();

        if (underfoot == Blocks.SLIME_BLOCK && now - lastSquishyDebugMs > 1000L) {
            lastSquishyDebugMs = now;
            LogUtil.debug("[SteamPad] Slime underfoot: movedSq={} (moving-hum needs >=0.0005) "
                            + "canFireAmbient={} lastRumbleAccepted={}", movedSq,
                    canFire(Tier.AMBIENT), lastRumbleAccepted());
        }

        // ~0.022 blocks/tick: comfortably below even slime-slowed sneak-walking, above standing
        // drift — standing still on slime stays silent (approved behavior, unchanged).
        boolean moving = movedSq >= 5.0e-4;
        if (underfoot == Blocks.SLIME_BLOCK && moving) {
            if (now - lastSquishyPulseMs > 240L) {
                lastSquishyPulseMs = now;
                // 320ms > 240ms cadence: consecutive pulses overlap into one continuous sticky drag.
                boolean accepted = fire(Tier.AMBIENT, Category.PLAYER, 0.45f, 320, 0.25f);
                LogUtil.debug("[SteamPad] Slime pulse firing (walking): 0.45 intensity, 320ms, AMBIENT "
                        + "tier. rumbleAccepted={}", accepted);
            }
        } else if (underfoot == Blocks.HONEY_BLOCK && moving) {
            if (now - lastSquishyPulseMs > 280L) {
                lastSquishyPulseMs = now;
                fire(Tier.COSMETIC, Category.PLAYER, 0.05f, 60, 0.75f);
            }
        }
    }

    // ---- Movement: water splash (entry edge) + elytra glide (continuous while airborne) ----

    private static void tickAquaticAndGliding(Minecraft mc) {
        // Splash: only the entry edge, not "underwater" as a whole — a soft cosmetic pulse. A deep
        // dive that also qualifies as a fall-impact naturally wins the shared channel (higher tier),
        // so this never doubles up with that heavier landing thump.
        boolean touchingWater = mc.player.isInWater();
        if (touchingWater && !wasTouchingWaterForSplash) {
            fire(Tier.COSMETIC, Category.PLAYER, 0.18f, 70, 0.5f);
        }
        wasTouchingWaterForSplash = touchingWater;

        // Elytra glide: a very soft continuous presence, never competing with anything that matters
        // (lowest tier, short refresh) — just enough to feel "you are flying".
        if (mc.player.isFallFlying()) {
            long now = System.currentTimeMillis();
            if (now - lastGlideRumbleMs > 150L) {
                lastGlideRumbleMs = now;
                double speed = mc.player.getDeltaMovement().length();
                float mag = (float) clamp(0.05 + speed * 0.03, 0.05, 0.14);
                fire(Tier.AMBIENT, Category.PLAYER, mag, 100, 0.55f);
            }
        }
    }

    // ---- World: lightning (polled, no extra mixin needed for a rare event) -----------------

    private static void pollLightning(Minecraft mc) {
        var box = mc.player.getBoundingBox().inflate(48.0);
        for (LightningBolt bolt : mc.level.getEntitiesOfClass(LightningBolt.class, box, e -> true)) {
            if (!seenLightning.add(bolt.getId())) continue;
            double dist = mc.player.distanceTo(bolt);
            float falloff = (float) clamp(1.0 - dist / 48.0, 0.0, 1.0);
            if (falloff < 0.02f) continue;
            fire(Tier.AMBIENT, Category.WORLD, falloff, (int) (150 + falloff * 200), 0.7f);   // sharp/crackly
        }
        if (seenLightning.size() > 64) seenLightning.clear();   // defensive cap, bolts are short-lived
    }

    // ---- World: creeper fuse anticipation + Warden dread ------------------------------------

    /** Roughly vanilla's base fuse duration — a feel target, not read from the entity (private). */
    private static final long CREEPER_FUSE_MS = 1500L;

    private static void pollMobs(Minecraft mc) {
        long now = System.currentTimeMillis();

        var mobBox = mc.player.getBoundingBox().inflate(24.0);
        Set<Integer> stillIgnited = new HashSet<>();
        for (Creeper c : mc.level.getEntitiesOfClass(Creeper.class, mobBox, e -> true)) {
            if (!c.isIgnited()) continue;
            int id = c.getId();
            stillIgnited.add(id);
            long since = creeperIgnitedSinceMs.computeIfAbsent(id, k -> now);
            double progress = clamp((now - since) / (double) CREEPER_FUSE_MS, 0.0, 1.0);
            long lastPing = creeperLastPingMs.getOrDefault(id, 0L);
            long interval = (long) lerp(520, 80, progress);
            if (now - lastPing > interval) {
                creeperLastPingMs.put(id, now);
                fire(Tier.DANGER, Category.WORLD, (float) (0.35 + progress * 0.5), (int) (70 + progress * 80), 0.55f);
            }
        }
        creeperIgnitedSinceMs.keySet().retainAll(stillIgnited);
        creeperLastPingMs.keySet().retainAll(stillIgnited);

        var wardenBox = mc.player.getBoundingBox().inflate(40.0);
        Warden nearestWarden = null;
        double bestD2 = Double.MAX_VALUE;
        for (Warden w : mc.level.getEntitiesOfClass(Warden.class, wardenBox, e -> true)) {
            double d2 = mc.player.distanceToSqr(w);
            if (d2 < bestD2) { bestD2 = d2; nearestWarden = w; }
        }
        if (nearestWarden == null) {
            wardenEngaged = false;   // re-arm once no Warden is within the 40-block scan box at all
        } else if (!wardenEngaged) {
            double closeness = clamp(1.0 - Math.sqrt(bestD2) / 40.0, 0.0, 1.0);
            long interval = (long) lerp(2200, 550, closeness);
            if (now - lastWardenPulseMs > interval) {
                lastWardenPulseMs = now;
                // Low, spaced, heavy — "opresivo", not a clean alert.
                fire(Tier.DANGER, Category.WORLD, (float) (0.25 + closeness * 0.45), (int) (160 + closeness * 160), 0.1f);
            }
        }
        // wardenEngaged==true here just skips the block above — the dread pulse stays quiet, exactly
        // like a regular boss once engaged (see onMeleeHit/onEntityDamaged and D062/D065). The Warden
        // was deliberately excluded from the generic engagedBosses/pollBossProximity system (its own
        // continuous dread is a different design), so it needed this separate flag instead — feedback:
        // "al golpear... al Warden, no se detiene la vibracion de cercania con el".

        // Enderman teleport: no public event for this (server-private methods), so a nearby Enderman
        // whose position jumped further than normal movement could cover in one poll window (~0.2s)
        // is treated as a teleport — a short, uncanny pulse, distinct from the mob-attack signature.
        Set<Integer> stillTrackedEnderman = new HashSet<>();
        for (EnderMan en : mc.level.getEntitiesOfClass(EnderMan.class, mobBox, e -> true)) {
            int id = en.getId();
            stillTrackedEnderman.add(id);
            Vec3 pos = en.position();
            Vec3 last = endermanLastPos.put(id, pos);
            if (last != null && last.distanceToSqr(pos) > 64.0) {   // > 8 blocks in one poll = teleport
                fire(Tier.DANGER, Category.WORLD, 0.3f, 45, 0.85f);
            }
        }
        endermanLastPos.keySet().retainAll(stillTrackedEnderman);
    }

    // ---- World: mysterious boss proximity + notorious boss hits -----------------------------

    /**
     * Same "misterioso ping, se apaga al aparecer" language as the Nether portal, but for any nearby
     * boss-like entity — vanilla's own Wither/Ender Dragon (neither had a dedicated signature before
     * this) or a modded boss, detected without needing the mod to say "boss" anywhere (see
     * {@link #isBossLike}). The ping stops once the boss is close AND in plain sight — "you've seen
     * it now, the mystery is over" — and its own hits are amplified separately, in
     * {@link #onPlayerDamaged}.
     */
    private static void pollBossProximity(Minecraft mc) {
        var box = mc.player.getBoundingBox().inflate(BOSS_PING_RANGE);
        LivingEntity nearest = null;
        double bestD2 = Double.MAX_VALUE;
        Set<Integer> stillInRange = new HashSet<>();
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, box,
                le -> le.isAlive() && !(le instanceof Player) && !(le instanceof Warden) && isBossLike(le))) {
            stillInRange.add(e.getId());
            double d2 = mc.player.distanceToSqr(e);
            if (d2 < bestD2) { bestD2 = d2; nearest = e; }
        }
        // Walked away (or the boss left range) without killing it — re-arm the mystery ping for next
        // time (feedback: "si no lo matamos nos alejamos se reinicie").
        engagedBosses.retainAll(stillInRange);
        if (nearest == null) return;
        double dist = Math.sqrt(bestD2);
        if (dist > BOSS_PING_RANGE) return;

        // Once engaged — seen up close, or already hit (see onMeleeHit) — the mystery ping's job is
        // done: stay quiet for the rest of the fight so real attack-hit feedback isn't buried under a
        // repeating ambient pulse (feedback: "no todo el tiempo que estemos peleando esté vibrando, y
        // se pueda sentir cuando ellos nos atacan"). Sticky rather than re-checked every poll, so a
        // momentary line-of-sight break mid-fight (dust, the boss ducking behind terrain) can't
        // restart it — only actually leaving BOSS_PING_RANGE does (the retainAll above).
        if (engagedBosses.contains(nearest.getId())) return;
        if (dist < BOSS_REVEAL_RANGE && mc.player.hasLineOfSight(nearest)) {   // seen it — mystery over
            engagedBosses.add(nearest.getId());
            return;
        }

        double closeness = clamp(1.0 - dist / BOSS_PING_RANGE, 0.0, 1.0);
        long interval = (long) lerp(2000, 450, closeness);
        long now = System.currentTimeMillis();
        if (now - lastBossPingMs > interval) {
            lastBossPingMs = now;
            float mag = (float) clamp(0.2 + closeness * 0.45, 0.2, 0.65);
            fire(Tier.DANGER, Category.WORLD, mag, (int) (110 + closeness * 130), 0.15f);
        }
    }

    /**
     * Boss detection without relying on mods declaring "boss" anywhere: an active vanilla boss bar
     * (100% reliable — the entity's UUID is the boss bar's key, standard convention for Wither/Ender
     * Dragon and any mod boss that reuses the vanilla boss-bar system) OR a health/size heuristic for
     * mods that don't bother with a boss bar. Warden is excluded (it already has its own dedicated
     * dread signature) despite easily clearing the health/size heuristic.
     */
    private static boolean isBossLike(LivingEntity e) {
        if (isBossBarActive(e)) return true;
        if (e.getMaxHealth() > BOSS_HEALTH_THRESHOLD) return true;
        return e.getBbWidth() > BOSS_WIDTH_THRESHOLD && e.getBbHeight() > BOSS_HEIGHT_THRESHOLD;
    }

    private static boolean isBossBarActive(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return false;
        return mc.gui.getBossOverlay().events.containsKey(e.getUUID());
    }

    // ---- World: Nether portal ping, amethyst geode, dungeon-chest treasure -----------------

    private static void pollWorldProximity(Minecraft mc) {
        BlockPos origin = mc.player.blockPosition();
        final int hR = 10, vR = 6;

        double bestPortalD2 = Double.MAX_VALUE;
        double bestGeodeD2 = Double.MAX_VALUE;
        BlockPos nearestSpawner = null;
        double bestSpawnerD2 = Double.MAX_VALUE;
        BlockPos nearestChest = null;
        double bestChestD2 = Double.MAX_VALUE;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -hR; dx <= hR; dx++) {
            int wx = origin.getX() + dx;
            for (int dz = -hR; dz <= hR; dz++) {
                int wz = origin.getZ() + dz;
                for (int dy = -vR; dy <= vR; dy++) {
                    cursor.set(wx, origin.getY() + dy, wz);
                    BlockState st = mc.level.getBlockState(cursor);
                    if (st.isAir()) continue;
                    Block b = st.getBlock();
                    double d2 = cursor.distSqr(origin);
                    if (b == Blocks.NETHER_PORTAL) {
                        if (d2 < bestPortalD2) bestPortalD2 = d2;
                    } else if (b == Blocks.AMETHYST_CLUSTER || b == Blocks.BUDDING_AMETHYST) {
                        if (d2 < bestGeodeD2) bestGeodeD2 = d2;
                    } else if (b == Blocks.SPAWNER) {
                        if (d2 < bestSpawnerD2) { bestSpawnerD2 = d2; nearestSpawner = cursor.immutable(); }
                    } else if (b instanceof ChestBlock) {
                        if (d2 < bestChestD2) { bestChestD2 = d2; nearestChest = cursor.immutable(); }
                    }
                }
            }
        }

        // Portal: periodic ping that quickens with proximity (not a continuous rumble — cheaper and
        // reads as "searching" rather than "droning").
        if (bestPortalD2 < Double.MAX_VALUE) {
            double dist = Math.sqrt(bestPortalD2);
            if (dist <= hR) {
                double closeness = clamp(1.0 - dist / hR, 0.0, 1.0);
                long interval = (long) lerp(1800, 320, closeness);
                long now = System.currentTimeMillis();
                if (now - lastPortalPingMs > interval) {
                    lastPortalPingMs = now;
                    float mag = (float) clamp(0.25 + closeness * 0.5, 0.25, 0.75);
                    fire(Tier.DANGER, Category.WORLD, mag, (int) (120 + closeness * 120), 0.2f);   // heavy/grave
                }
            }
        }

        // Geode: one clean "discovery" ping on approach; quiet again until you leave and come back.
        boolean nearGeode = bestGeodeD2 < 144.0;   // 12 blocks
        if (nearGeode && !wasNearGeode) {
            fire(Tier.COSMETIC, Category.WORLD, 0.3f, 180, 0.6f);
        }
        wasNearGeode = nearGeode;

        // Dungeon-chest treasure ping: near a spawner (a real generated dungeon/mineshaft — players
        // essentially never place spawners themselves), never opened before, and not near the
        // player's own bed/respawn point (so a base full of storage chests stays silent).
        boolean treasureWorthy = false;
        if (nearestChest != null && bestChestD2 < 144.0) {
            boolean nearSpawner = nearestSpawner != null && nearestChest.distSqr(nearestSpawner) <= 36.0;
            boolean alreadyOpened = openedChests.contains(nearestChest);
            boolean nearHome = isNearHome(mc, nearestChest);
            treasureWorthy = nearSpawner && !alreadyOpened && !nearHome;
        }
        if (treasureWorthy && !wasNearTreasureChest) {
            fire(Tier.COSMETIC, Category.WORLD, 0.35f, 200, 0.55f);
        }
        wasNearTreasureChest = treasureWorthy;
    }

    /** Within ~32 blocks of the player's current bed/respawn-anchor point, in this dimension. */
    private static boolean isNearHome(Minecraft mc, BlockPos pos) {
        //? if >=1.21.2 {
        var spawn = mc.level.getRespawnData();
        if (spawn == null) return false;
        var globalPos = spawn.globalPos();
        if (globalPos == null || !globalPos.dimension().equals(mc.level.dimension())) return false;
        return globalPos.pos().distSqr(pos) <= 1024.0;   // 32 blocks
        //?} else {
        /*// The client only learns the player's PERSONAL respawn point (bed / respawn anchor) from the
        // respawn-data sync added in 1.21.2. Before that it is simply not available client-side, so
        // "home" falls back to the world spawn — a coarser reference, but the haptic keeps working and
        // still fires around the place players actually build. Same 32-block radius; no dimension
        // check needed since getSharedSpawnPos is already per-level.
        BlockPos spawn = mc.level.getSharedSpawnPos();
        if (spawn == null) return false;
        return spawn.distSqr(pos) <= 1024.0;
        *///?}
    }

    // ---- Hooks called from mixins / Fabric API events --------------------------------------

    /** {@code ClientPlayNetworkHandlerMixin} — an explosion packet just arrived. */
    public static void onExplosion(Vec3 center, float radius, boolean localKnockback) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double dist = mc.player.position().distanceTo(center);
        double feelRange = Math.max(8.0, radius * 8.0);   // shockwave felt well past the blast radius
        double falloff = clamp(1.0 - dist / feelRange, 0.0, 1.0);
        if (localKnockback) falloff = Math.max(falloff, 0.55);   // the game already confirms "it hit you"
        if (falloff < 0.02) return;
        float mag = (float) falloff;
        int dur = (int) (200 + mag * 220);
        fire(Tier.CRITICAL, Category.WORLD, mag, dur, 0.15f);   // heavy boom
        dev.steampad.input.JuiceController.shake(mag * 0.3, dur);
        dev.steampad.input.JuiceController.fovKick(mag * 0.18f);
    }

    /**
     * {@code ClientPlayNetworkHandlerMixin} — the local player just took damage, with the real
     * {@code DamageSource} (type + attacker) the server sent — something the HP-drop poll in
     * {@link #tickDamageAndHealth} can never see on its own. Classifies the hit into a
     * {@link PendingHit} signature that the very next HP-drop tick consumes instead of the plain
     * generic one. Fire/explosion/lava, explosions, falls, drowning, and freezing are deliberately
     * left alone here — they already fire their own dedicated signatures elsewhere (tickBodyStates,
     * tickFallImpact, onExplosion), so setting a pending hit for them would just compete with a
     * better-tuned effect for no benefit.
     */
    public static void onPlayerDamaged(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypeTags.IS_FREEZING)) {
            return;
        }

        Entity attacker = source.getEntity();

        // Boss override wins over everything else — a boss hit should always read as "this matters
        // more", regardless of damage type or the fact it might also be, say, a projectile.
        if (attacker != null && isBossLike(attacker)) {
            pendingHit = new PendingHit(Tier.CRITICAL, 0.3f, 1.5f, 1.4f);
        } else if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.WITHER)
                || source.is(DamageTypes.INDIRECT_MAGIC)) {
            // Poison/wither/harmful-potion — irregular and insidious, promoted a tier above the
            // plain physical hit so it can interrupt a lesser event instead of being dropped by one.
            pendingHit = new PendingHit(Tier.DANGER, 0.5f, 1.0f, 1.0f);
        } else if (attacker instanceof Zombie) {
            pendingHit = new PendingHit(null, 0.15f, 1.0f, 1.0f);   // heavy, blunt
        } else if (attacker instanceof Spider) {
            pendingHit = new PendingHit(null, 0.65f, 1.0f, 1.0f);   // nervous, sharp-leaning
        } else if (attacker instanceof Skeleton) {
            pendingHit = new PendingHit(null, 0.75f, 1.0f, 1.0f);   // punzante, arrow-like
        } else if (attacker instanceof EnderMan) {
            pendingHit = new PendingHit(null, 0.8f, 1.0f, 1.0f);    // sobrenatural, high-dominant
        } else {
            return;   // unclassified attacker/source — leave the generic HP-drop signature untouched
        }
        pendingHitExpireMs = System.currentTimeMillis() + 250L;
    }

    private static PendingHit consumePendingHit() {
        PendingHit h = pendingHit;
        pendingHit = null;
        return (h != null && System.currentTimeMillis() <= pendingHitExpireMs) ? h : null;
    }

    /** Overload for the Enderman/isBossLike(LivingEntity) helper above — accepts any Entity safely. */
    private static boolean isBossLike(Entity e) {
        return e instanceof LivingEntity living && !(living instanceof Player) && isBossLike(living);
    }

    /**
     * Per-weapon melee "impact signature" — the same God of War-style "builds-then-pays-off" language
     * already cited in this class's own doc, applied per weapon class instead of one generic thud for
     * everything. {@code freqBalance} follows this file's convention (0 = heavy/low motor, 1 =
     * sharp/high motor). Detected via {@link ItemTags} (swords/axes lost their dedicated item classes
     * in the 1.20.5+ data-component refactor — {@code SwordItem} no longer exists) and the two weapons
     * that DO still have real classes ({@link MaceItem}, {@link TridentItem}).
     */
    private record WeaponSignature(float magnitude, int durationMs, float freqBalance) {}

    private static WeaponSignature weaponSignature(net.minecraft.world.item.ItemStack weapon, boolean critLike) {
        // D114: every signature now sits at or above ~0.4/140ms with weight pushed toward the LOW
        // (strong) motor. This pad's own tuning history establishes the in-gameplay perceptibility
        // floor around 0.3-0.4/150ms+ (the slime hum took three rounds to reach 0.3/300 before it
        // registered even once; the arrow-kill pulse at 0.5/160 is reliably felt) — the previous
        // sword/fist taps (0.22/90, high-motor-biased) sat BELOW that floor mid-combat, even though
        // the identical test-screen preset, felt in calm attention, technically "worked". The
        // per-weapon LANGUAGE (mace heaviest/lowest → sword sharpest/lightest) is preserved, just
        // shifted up into the range this hardware actually renders.
        if (weapon.getItem() instanceof net.minecraft.world.item.MaceItem) {
            // The mace's real "smash attack" requires falling — the exact same signal critLike already
            // computes — so a critLike mace hit IS a smash attack: the heaviest, lowest-frequency thud
            // in the whole haptic vocabulary, on purpose (it's the biggest single hit in the game).
            return critLike ? new WeaponSignature(0.9f, 300, 0.05f) : new WeaponSignature(0.65f, 200, 0.15f);
        }
        if (weapon.getItem() instanceof net.minecraft.world.item.TridentItem) {
            return new WeaponSignature(critLike ? 0.65f : 0.5f, critLike ? 230 : 170, 0.25f);
        }
        if (weapon.is(net.minecraft.tags.ItemTags.AXES)) {
            return new WeaponSignature(critLike ? 0.65f : 0.5f, critLike ? 230 : 170, 0.4f);
        }
        if (weapon.is(net.minecraft.tags.ItemTags.SWORDS)) {
            return new WeaponSignature(critLike ? 0.6f : 0.45f, critLike ? 210 : 150, 0.55f);
        }
        // Fists / unrecognized item.
        return new WeaponSignature(critLike ? 0.55f : 0.4f, critLike ? 190 : 140, 0.6f);
    }

    /** {@code ClientPlayerInteractionManagerMixin} — the player's melee attack just landed on {@code target}. */
    public static void onMeleeHit(Player attacker, Entity target) {
        // Local approximation of vanilla's crit conditions (airborne, not on ground/water/vehicle,
        // not blinded) — a feel heuristic, not a guarantee of the server's actual crit roll.
        boolean critLike = attacker.fallDistance > 0.0
                && !attacker.onGround()
                && !attacker.isInWater()
                && !attacker.isPassenger()
                && !attacker.hasEffect(MobEffects.BLINDNESS);

        WeaponSignature sig = weaponSignature(attacker.getMainHandItem(), critLike);
        float mag = sig.magnitude();
        int dur = sig.durationMs();
        Tier tier = Tier.IMPACT;

        // D114 — kills are deliberately NOT detected here anymore. This hook runs the instant the
        // attack packet LEAVES the client, and the target's client-side health still holds its
        // pre-hit value until the server answers one or more ticks later — so the old
        // "health <= 0 right now → promote to CRITICAL" check only ever matched an already-dying
        // corpse being whacked again, never the actual killing blow. That is precisely why melee
        // kills never got their big pulse while arrow kills (evaluated in onEntityDamaged, AFTER the
        // server's own damage packet) did — the exact reported asymmetry ("solo se siente cuando
        // matas con flecha"). The hit now just arms the kill-confirmation window; pollPendingKills
        // fires the one unified kill pulse when the entity's replicated state actually shows the
        // death, a tick or two later.
        if (target instanceof LivingEntity) {
            recentPlayerHits.put(target.getId(), System.currentTimeMillis() + KILL_CONFIRM_WINDOW_MS);
        }

        // Diagnostics kept from the earlier investigation rounds (unthrottled, one line per real
        // hit): if melee ever goes quiet again, this line distinguishes "never reached fire()"
        // (nothing prints — mixin/input problem) from "reached it but blocked" (canFire/config
        // fields) from "hardware rejected it" (rumbleAccepted=false with everything else clean).
        long handle = ActiveControllerService.getActiveHandle();
        ControllerConfig diagCfg = handle != 0L ? ConfigManager.getControllerConfig(handle) : null;
        boolean preCanFire = canFire(tier);
        lastMeleePulseTargetId = target.getId();
        lastMeleePulseMs = System.currentTimeMillis();
        boolean accepted = fire(tier, Category.INTERACTION, mag, dur, sig.freqBalance());
        LogUtil.debug("[SteamPad][haptics-melee] weapon={} critLike={} tier={} mag={} dur={} handle={} "
                        + "allowVibration={} vibrationInteraction={} vibrationMaster={} canFireBefore={} "
                        + "occupyingTierBefore={} rumbleAccepted={}",
                attacker.getMainHandItem().getItem(), critLike, tier, mag, dur, handle,
                diagCfg != null && diagCfg.allowVibration,
                diagCfg != null ? diagCfg.vibrationInteraction : -1f,
                diagCfg != null ? diagCfg.vibrationMaster : -1f,
                preCanFire, occupyingTier(), accepted);
        dev.steampad.input.JuiceController.shake(mag * 0.15, dur);
        dev.steampad.input.JuiceController.fovKick(mag * 0.12f);

        // First hit landed on a boss — the mystery proximity ping's job is done (feedback: "cuando le
        // demos el primer golpe que ya pase la vibración de cercanía del jefe"); see pollBossProximity.
        // Also caught generically by onEntityDamaged below (the server's own damage packet) — marking
        // it here too just means melee doesn't have to wait for that round-trip.
        markEngagedIfBossOrWarden(target);
    }

    /** Marks {@code target} as "engaged" so its proximity dread/mystery ping stops — regular bosses
     *  via {@link #engagedBosses}, the Warden via its own {@link #wardenEngaged} flag (kept separate
     *  on purpose, see {@link #pollMobs}). Shared by {@link #onMeleeHit} and {@link #onEntityDamaged}. */
    private static void markEngagedIfBossOrWarden(Entity target) {
        if (target instanceof Warden) {
            wardenEngaged = true;
        } else if (isBossLike(target)) {
            engagedBosses.add(target.getId());
        }
    }

    /**
     * {@code ClientPlayNetworkHandlerMixin} — some entity OTHER than the local player just took
     * damage, with the real {@code DamageSource}. The generic "player dealt damage to a boss" signal —
     * covers melee (redundantly with {@link #onMeleeHit}, harmless) AND ranged attacks like arrows,
     * which have no other client-side hook (feedback: "no se detiene la vibracion de los jefes al
     * darle el primer golpe... esto incluye flechas, lo mejor es al hacerle daño"). Any damage type
     * counts, unlike {@link #onPlayerDamaged}'s exclusions — a boss doesn't care whether the killing
     * blow was fire/magic/a projectile, only that the player is the one dealing it.
     */
    public static void onEntityDamaged(Entity target, DamageSource source) {
        if (source.getEntity() != Minecraft.getInstance().player) return;
        markEngagedIfBossOrWarden(target);

        // Every player-attributed hit — melee (redundant with onMeleeHit, same map entry), arrows,
        // thrown tridents/snowballs, anything — arms the kill-confirmation window (D114);
        // pollPendingKills owns the actual kill pulse.
        if (target instanceof LivingEntity) {
            recentPlayerHits.put(target.getId(), System.currentTimeMillis() + KILL_CONFIRM_WINDOW_MS);
        }

        // D115 — the melee HIT pulse also fires from THIS packet hook. Hard evidence from the v0.74.0
        // hardware round: the kill pulse (this file's packet-driven pollPendingKills) IS felt, the
        // radial-select pulse at a mere 0.05/25ms IS felt (user asked to TONE IT DOWN once, D070),
        // yet the 0.45-0.65/150-200ms hit pulse from the attackEntity mixin produces nothing — so
        // magnitude was never the problem (D114's floor rationale is refuted as the explanation for
        // hit silence): the mixin path simply never runs in the user's real install. Their ~80-mod
        // pack is the leading suspect — combat-overhaul mods replace the vanilla doAttack/attackEntity
        // client path wholesale with their own attack packets (the mob still takes damage server-side,
        // so kills and this packet keep working). The server's damage packet doesn't care who
        // initiated the swing. getSource()==player selects direct-contact (melee) damage — for
        // projectiles getSource() is the arrow entity, handled below. Deduped against onMeleeHit so
        // vanilla setups where both paths are alive don't double-buzz a single hit.
        Minecraft mc = Minecraft.getInstance();
        if (source.getDirectEntity() == mc.player && target instanceof LivingEntity) {
            long now = System.currentTimeMillis();
            if (target.getId() != lastMeleePulseTargetId || now - lastMeleePulseMs > MELEE_PULSE_DEDUP_MS) {
                lastMeleePulseTargetId = target.getId();
                lastMeleePulseMs = now;
                Player p = mc.player;
                boolean critLike = p.fallDistance > 0.0
                        && !p.onGround()
                        && !p.isInWater()
                        && !p.isPassenger()
                        && !p.hasEffect(MobEffects.BLINDNESS);
                WeaponSignature sig = weaponSignature(p.getMainHandItem(), critLike);
                boolean accepted = fire(Tier.IMPACT, Category.INTERACTION,
                        sig.magnitude(), sig.durationMs(), sig.freqBalance());
                LogUtil.debug("[SteamPad][haptics-melee] Hit pulse via damage packet (D115): weapon={} "
                                + "critLike={} mag={} dur={} rumbleAccepted={}",
                        p.getMainHandItem().getItem(), critLike, sig.magnitude(), sig.durationMs(), accepted);
                dev.steampad.input.JuiceController.shake(sig.magnitude() * 0.15, sig.durationMs());
                dev.steampad.input.JuiceController.fovKick(sig.magnitude() * 0.12f);
            }
        }

        // Confirmed ranged hit (arrow/trident throw) — a short, sharp "point" tap, deliberately
        // distinct from melee's fuller pulse (feedback request: "con la flecha se sienta como un
        // punto"). Gated to projectile-sourced damage only, so this never doubles up with onMeleeHit
        // (a melee hit's DamageSource#getSource() is the player, never a projectile entity). The old
        // lethal special case moved to pollPendingKills (D114) — a killing arrow now reads as this
        // tap plus the unified kill thump right after, same payoff language as melee.
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow) {
            fire(Tier.IMPACT, Category.INTERACTION, 0.22f, 55, 0.9f);
            dev.steampad.input.JuiceController.shake(0.02, 55);
            dev.steampad.input.JuiceController.fovKick(0.015f);
        }
    }

    /**
     * Confirms kills the moment the entity's server-replicated state shows them (D114): any tracked
     * recently-hit entity that is now dead — or gone from the client world — inside its confirmation
     * window gets the one unified kill pulse, with the exact values of the long-standing (and
     * hardware-confirmed felt) {@code weapon_kill} test preset. CRITICAL tier, so no lesser pulse —
     * including the very hit that caused the death — can occupy the channel over the payoff moment.
     */
    private static void pollPendingKills(Minecraft mc) {
        if (recentPlayerHits.isEmpty()) return;
        long now = System.currentTimeMillis();
        var it = recentPlayerHits.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (now > e.getValue()) { it.remove(); continue; }
            Entity ent = mc.level.getEntity(e.getKey());
            if (ent == null || !ent.isAlive()) {
                it.remove();
                fire(Tier.CRITICAL, Category.INTERACTION, 0.75f, 260, 0.2f);
                dev.steampad.input.JuiceController.shake(0.1, 260);
                dev.steampad.input.JuiceController.fovKick(0.1f);
                LogUtil.debug("[SteamPad][haptics-melee] Kill confirmed for entity id={} — unified "
                        + "kill pulse fired (0.75, 260ms, CRITICAL).", e.getKey());
            }
        }
    }

    /**
     * Fabric API {@code ClientPlayerBlockBreakEvents.AFTER} — common blocks get a minimal tick, ores
     * a solid tap, and diamond/emerald/ancient debris a distinct, longer, cleaner pulse promoted to
     * IMPACT tier so it isn't silently dropped under a lesser event — the "this matters" moment.
     */
    public static void onBlockBroken(BlockState state) {
        Block b = state.getBlock();
        if (isPreciousBlock(b)) {
            fire(Tier.IMPACT, Category.INTERACTION, 0.5f, 190, 0.35f);
        } else if (isOreBlock(b)) {
            fire(Tier.COSMETIC, Category.INTERACTION, 0.28f, 100, 0.6f);
        } else {
            fire(Tier.COSMETIC, Category.INTERACTION, 0.16f, 65, 0.8f);
        }
    }

    /** Fabric API {@code UseBlockCallback} — remembers opened chests so their treasure ping retires. */
    public static void onBlockUsed(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock) {
            openedChests.add(pos.immutable());
            // Soft "creak" accompanying the lid animation (audit: chests felt mute) — cosmetic on
            // purpose, the treasure system already owns the "this one matters" signal.
            fire(Tier.COSMETIC, Category.INTERACTION, 0.18f, 130, 0.5f);
        }
    }

    private static boolean isPreciousBlock(Block b) {
        return b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE
                || b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE
                || b == Blocks.ANCIENT_DEBRIS;
    }

    private static boolean isOreBlock(Block b) {
        return b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE
                || b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE
                || b == Blocks.COPPER_ORE || b == Blocks.DEEPSLATE_COPPER_ORE
                || b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE
                || b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE
                || b == Blocks.NETHER_GOLD_ORE || b == Blocks.NETHER_QUARTZ_ORE
                || b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE;
    }

    /** Brief, soft pulse on radial-menu slot-selection change (gameplay wheel). Gated by its own
     *  global toggle, scaled by its own global strength slider, in addition to the usual GUI-category
     *  vibration multiplier. */
    public static void radialSelectPulse() {
        var g = dev.steampad.config.ConfigManager.getGlobal();
        if (!g.radialSelectHaptics) return;
        // Second pass (feedback: "lo siento fuerte... que sea un poquito mas breve"): base intensity
        // and duration both trimmed down from the first cut (0.08/35ms), on top of the new strength
        // slider so the user can graduate it further from here.
        fire(Tier.COSMETIC, Category.GUI, 0.05f * g.radialSelectHapticsIntensity, 25, 0.85f);
    }

    // ---- Dispatch ---------------------------------------------------------------------------

    /** Diagnostic-only: whether the LAST {@link #fire} call that reached {@link ControllerManager#rumble}
     *  was actually ACCEPTED by the backend (see that method's own doc) — null if nothing has reached
     *  that point yet this session. Surfaced in the melee/slime debug lines (D113) so a REPEATED
     *  rejection (as opposed to "canFire()/config blocked it before even trying") is visible without
     *  depending on {@code Sdl3GamepadProvider}'s own 3-line warning cap, which one unrelated earlier
     *  failure could exhaust before the effect actually worth diagnosing ever fires. */
    private static Boolean lastRumbleAccepted = null;

    public static Boolean lastRumbleAccepted() { return lastRumbleAccepted; }

    /**
     * Resolves the active controller + its category multiplier, gates on {@link Tier} priority
     * (single rumble channel — see class doc), converts {@code freqBalance} (0 = heavy/low-motor,
     * 1 = sharp/high-motor) into a low/high split, and fires. No-ops below a negligible intensity so
     * events don't produce an inaudible/imperceptible buzz, and below the current tier occupying the
     * channel so a trivial event can never talk over something that matters more.
     *
     * @return true only if the request reached {@link ControllerManager#rumble} AND was accepted by
     *         the backend — false for every early-out (tier blocked, no controller, vibration off,
     *         negligible intensity) AND for a backend-level rejection. Diagnostic use — callers that
     *         don't care can ignore it, exactly as before this return type existed.
     */
    private static boolean fire(Tier tier, Category cat, float baseIntensity, int durationMs, float freqBalance) {
        if (!canFire(tier)) return false;

        long handle = ActiveControllerService.getActiveHandle();
        if (handle == 0L) return false;
        ControllerConfig cfg = ConfigManager.getControllerConfig(handle);
        if (!cfg.allowVibration) return false;

        float catMult = switch (cat) {
            case PLAYER -> cfg.vibrationPlayer;
            case WORLD -> cfg.vibrationWorld;
            case INTERACTION -> cfg.vibrationInteraction;
            case GUI -> cfg.vibrationGui;
            case GLOBAL_EVENT -> cfg.vibrationGlobalEvent;
            case MISC -> cfg.vibrationMisc;
        };
        float intensity = clamp(baseIntensity * cfg.vibrationMaster * catMult, 0f, 1f);
        if (intensity < 0.02f) return false;

        float low = intensity * (1f - 0.5f * freqBalance);
        float high = intensity * (0.5f + 0.5f * freqBalance);
        boolean accepted = ControllerManager.rumble(handle, low, high, durationMs);
        lastRumbleAccepted = accepted;
        occupy(tier, durationMs);
        return accepted;
    }

    /** Cuts off whatever is currently buzzing (pause menu — see {@link #tick}) and releases the
     *  channel so gameplay resumes fresh instead of "still occupied" by a pulse from before the pause. */
    private static void stopRumbleNow() {
        long handle = ActiveControllerService.getActiveHandle();
        if (handle != 0L) ControllerManager.rumble(handle, 0f, 0f, 0);
        activeTier = null;
    }

    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : Math.min(v, hi); }
    private static double clamp(double v, double lo, double hi) { return v < lo ? lo : Math.min(v, hi); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
