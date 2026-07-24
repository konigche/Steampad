package dev.steampad.input;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.ControllerConfig;
import net.minecraft.client.MinecraftClient;

/**
 * BetterZoom-style FOV zoom, gamepad-native. Static singleton in the style of
 * {@link VirtualMouseController} / {@link CameraController}.
 *
 * <p>Flow: the {@code ZOOM} bind (no default button) activates hold- or toggle-mode zoom; while
 * zoomed, D-pad ↑/↓ adjusts the level in configurable steps (their normal bind actions are
 * suppressed during the zoom); the FOV multiplier is applied by {@code GameRendererMixin} in
 * {@code getFov}, eased with BetterZoom's smoothstep formula but normalized to real delta-time so
 * the glide is identical at any frame rate; camera look speed follows the eased factor (auto) or a
 * fixed multiplier, mirroring BetterZoom's mouse-sensitivity reduction; view bobbing is optionally
 * suppressed while zoomed and always restored.
 *
 * <p>Performance: while idle the per-frame cost is one volatile read and one float compare
 * ({@link #fovFactor} fast path) — no allocation, no config lookup, nothing else runs.
 */
public final class ZoomController {

    private static volatile boolean zooming = false;
    /** Eased FOV multiplier actually applied by the mixin (1 = no zoom). */
    private static volatile float factor = 1.0f;
    /** Target multiplier while zooming (zoomFov / options FOV), recomputed each tick. */
    private static float targetFactor = 1.0f;
    private static boolean smooth = true;
    private static float easing = 0.15f;
    private static long lastNanos = 0L;

    // View-bobbing suppression while zoomed. Saved/restored around the zoom; defensively restored
    // in deactivate() so a controller disconnect can never leave the user's option flipped.
    private static boolean bobbingModified = false;
    private static boolean savedBobbing = true;

    // Temporary render-distance boost while zoomed (feedback: distant terrain is invisible beyond the
    // player's normal render distance no matter how much the zoom magnifies, because those chunks were
    // never loaded/rendered). Two things are needed, not just one — v0.24.0 only did the first and the
    // user correctly reported no effect at all:
    //  (1) simulationDistance, not just viewDistance — the integrated server only LOADS/keeps chunks
    //      out to simulationDistance; a chunk beyond it doesn't exist server-side to send regardless of
    //      how far the client asks to render, so raising viewDistance alone hits an invisible ceiling.
    //  (2) GameOptions.sendClientSettings() — SimpleOption.setValue() only changes the local value; the
    //      integrated server never learns the client wants more chunks until this is called explicitly
    //      (the vanilla Options screen calls it itself on change, which zoom bypasses by design).
    // Saved/restored around the zoom, same pattern as bobbing.
    private static boolean viewDistanceModified = false;
    private static int savedViewDistance = 0;
    private static int savedSimulationDistance = 0;

    /** D-pad level adjustments are persisted once — when the zoom ends — not on every step. */
    private static boolean levelDirty = false;
    /** Zoom state last tick (edge detection for the reset-on-release option). */
    private static boolean wasZooming = false;
    /** The configured level captured when the zoom started — restored on release if the user chose
     *  "reset on release" instead of keeping the D-pad-adjusted level. */
    private static float sessionBaseFov = 15f;

    private ZoomController() {}

    public static boolean isZooming() { return zooming; }
    public static void setZooming(boolean active) { zooming = active; }
    public static void toggle() { zooming = !zooming; }

    /**
     * True while zooming if {@code button} is currently repurposed by the zoom (D-pad ↑/↓ for the
     * level, A for the marker) — whatever bind normally lives there is suppressed and should not show
     * its usual glyph either. Single source of truth shared by the input dispatcher (suppresses the
     * action) and the HUD (suppresses the stale glyph), so the two can never disagree in real time.
     */
    public static boolean isButtonRepurposed(ControllerConfig cfg, String button) {
        if (!zooming) return false;
        if (cfg.zoomDpadAdjust && ("DUP".equals(button) || "DDOWN".equals(button))) return true;
        return cfg.zoomMarkerEnabled && "A".equals(button);
    }

    /** D-pad step: dir=+1 zooms in (narrower FOV), -1 zooms out. Clamped to [1, options FOV]. */
    public static void adjust(int dir, ControllerConfig cfg, MinecraftClient mc) {
        float maxFov = Math.max(2f, mc.options.getFov().getValue());
        cfg.zoomFov = clamp(cfg.zoomFov - dir * cfg.zoomStep, 1f, maxFov);
        levelDirty = true;
    }

    /** Per-tick upkeep: refresh the target from config, bobbing suppression, deferred persist. */
    public static void tick(MinecraftClient mc, ControllerConfig cfg, long handle) {
        float optionsFov = Math.max(1f, mc.options.getFov().getValue());
        targetFactor = clamp(cfg.zoomFov / optionsFov, 0.01f, 1f);
        smooth = cfg.zoomSmooth;
        easing = clamp(cfg.zoomSmoothing, 0.05f, 0.30f);

        boolean wantBobOff = zooming && cfg.zoomDisableBobbing;
        if (wantBobOff && !bobbingModified) {
            savedBobbing = mc.options.getBobView().getValue();
            mc.options.getBobView().setValue(false);
            bobbingModified = true;
        } else if (!wantBobOff && bobbingModified) {
            mc.options.getBobView().setValue(savedBobbing);
            bobbingModified = false;
        }

        boolean wantViewBoost = zooming && cfg.zoomRenderDistanceBoost > 0;
        if (wantViewBoost && !viewDistanceModified) {
            savedViewDistance = mc.options.getViewDistance().getValue();
            savedSimulationDistance = mc.options.getSimulationDistance().getValue();
            mc.options.getViewDistance().setValue(Math.min(32, savedViewDistance + cfg.zoomRenderDistanceBoost));
            mc.options.getSimulationDistance().setValue(Math.min(32, savedSimulationDistance + cfg.zoomRenderDistanceBoost));
            mc.options.sendClientSettings();   // without this the integrated server never sends the extra chunks
            viewDistanceModified = true;
        } else if (!wantViewBoost && viewDistanceModified) {
            mc.options.getViewDistance().setValue(savedViewDistance);
            mc.options.getSimulationDistance().setValue(savedSimulationDistance);
            mc.options.sendClientSettings();
            viewDistanceModified = false;
        }

        // Reset-on-release: capture the configured level when the zoom starts; on release, either
        // restore it (discarding the D-pad adjustments) or keep the last level (default behaviour).
        if (zooming && !wasZooming) sessionBaseFov = cfg.zoomFov;
        if (!zooming && wasZooming && cfg.zoomResetOnRelease) {
            cfg.zoomFov = sessionBaseFov;
            levelDirty = false;   // nothing to persist — the adjustments were transient by choice
        }
        wasZooming = zooming;

        if (!zooming && levelDirty) {   // persist the D-pad-adjusted level once, on zoom end
            levelDirty = false;
            ConfigManager.saveControllerConfig(handle);
        }

        tickMarker(mc);   // beacon outlives the zoom on purpose — it's a ping, not a zoom overlay
    }

    // ---- Zoom marker (A while zoomed): a temporary particle beacon at the aimed spot -----------
    private static net.minecraft.util.math.BlockPos markerPos = null;
    private static long markerExpireMs = 0L;
    /** Non-null while the beacon is tracking a LIVING entity instead of a fixed block — the particles
     *  and outline follow it each tick until it dies/unloads or the beacon expires (feedback: "el
     *  marcador también se mueva con el zombie"). */
    private static net.minecraft.entity.LivingEntity markerEntity = null;

    // How far the raycast reaches when looking for a block to mark. A single raycast is a bounded
    // voxel walk that stops at the edge of loaded chunks anyway (unloaded chunks have no block data
    // to hit), and it only runs once per A-press — not per frame — so a generous distance costs
    // nothing measurable even at the low end of a Steam Deck. 4096 blocks comfortably outreaches any
    // real render distance (vanilla tops out at 32 chunks = 512 blocks) while staying far short of
    // where float precision on block coordinates would start to matter.
    private static final double MARKER_RAYCAST_DISTANCE = 4096.0;

    /**
     * Drops the beacon at whatever the player is aiming at — a mob/entity if one is closer along the
     * sightline than any block (feedback: "¿se pueden marcar entidades o mobs?"), else the block hit
     * (long raycast — it's a zoom feature), else the far end of the sightline itself if the raycast
     * reports MISS entirely (aiming at open sky/over the horizon) so aiming there still marks a usable
     * point instead of silently doing nothing. Entity marks TRACK the entity live (see
     * {@link #markerEntity}) instead of freezing at a snapshot position.
     *
     * <p>Entity detection is a manual per-entity {@code Box.raycast} (candidates from
     * {@code World.getOtherEntities}, same pattern {@link AimAssistController} already uses to find
     * nearby living entities) rather than {@code ProjectileUtil.raycast} — the first cut used that
     * helper and it never found anything in testing (a zombie directly in the crosshair went
     * undetected); a manual box-vs-ray test is both simpler to reason about and directly verifiable.
     */
    public static void placeMarker(MinecraftClient mc, ControllerConfig cfg) {
        if (mc.player == null || mc.world == null) return;
        var eye = mc.player.getEyePos();
        var look = mc.player.getRotationVec(1.0f);
        var end = eye.add(look.multiply(MARKER_RAYCAST_DISTANCE));

        var blockHit = mc.player.raycast(MARKER_RAYCAST_DISTANCE, 1.0f, false);
        boolean hasBlockHit = blockHit instanceof net.minecraft.util.hit.BlockHitResult
                && blockHit.getType() != net.minecraft.util.hit.HitResult.Type.MISS;
        double blockDistSq = hasBlockHit ? eye.squaredDistanceTo(blockHit.getPos()) : Double.MAX_VALUE;

        // Manual entity search: every living entity whose (slightly padded) bounding box the sightline
        // ray actually intersects, closest intersection wins.
        var searchBox = new net.minecraft.util.math.Box(eye, end).expand(2.0);
        net.minecraft.entity.LivingEntity closestEntity = null;
        double closestDistSq = Double.MAX_VALUE;
        for (var e : mc.world.getOtherEntities(mc.player, searchBox,
                ent -> ent instanceof net.minecraft.entity.LivingEntity le && le.isAlive() && !ent.isSpectator())) {
            var hit = e.getBoundingBox().expand(0.3).raycast(eye, end);
            if (hit.isEmpty()) continue;
            double d = eye.squaredDistanceTo(hit.get());
            if (d < closestDistSq) { closestDistSq = d; closestEntity = (net.minecraft.entity.LivingEntity) e; }
        }

        // Replacing an in-progress entity mark with a new one (or a block mark) — stop glowing the old
        // target so it doesn't stay outlined forever.
        if (markerEntity != null && markerEntity != closestEntity) markerEntity.setGlowing(false);

        net.minecraft.util.math.BlockPos pos;
        if (closestEntity != null && closestDistSq < blockDistSq) {
            markerEntity = closestEntity;
            pos = closestEntity.getBlockPos();
            closestEntity.setGlowing(true);   // vanilla's own outline render path — cheap, no custom draw code
        } else {
            markerEntity = null;
            if (hasBlockHit) {
                pos = ((net.minecraft.util.hit.BlockHitResult) blockHit).getBlockPos().toImmutable();
            } else {
                pos = net.minecraft.util.math.BlockPos.ofFloored(end);
            }
        }
        markerPos = pos;
        markerExpireMs = System.currentTimeMillis()
                + (long) (Math.max(2f, Math.min(15f, cfg.zoomMarkerSeconds)) * 1000L);
        markerStyle = cfg.zoomMarkerStyle == null ? ControllerConfig.ZoomMarkerStyle.COLUMN : cfg.zoomMarkerStyle;
        markerColor = cfg.zoomMarkerColor == null ? ControllerConfig.ZoomMarkerColor.CYAN : cfg.zoomMarkerColor;
        if (cfg.zoomMarkerShareChat && mc.player.networkHandler != null) {
            // A plain chat line is the only thing OTHER players can actually see from a client mod.
            mc.player.networkHandler.sendChatMessage(
                    "[📍] " + markerPos.getX() + " " + markerPos.getY() + " " + markerPos.getZ());
        }
    }

    private static ControllerConfig.ZoomMarkerStyle markerStyle = ControllerConfig.ZoomMarkerStyle.COLUMN;
    private static ControllerConfig.ZoomMarkerColor markerColor = ControllerConfig.ZoomMarkerColor.CYAN;

    /**
     * Per-tick particles while the beacon is alive (called from tick()) — shape per
     * {@link ControllerConfig.ZoomMarkerStyle}, color per {@link ControllerConfig.ZoomMarkerColor}.
     *
     * <p>Column-shaped styles grow away from the marked block toward whichever side is actually open.
     * The original always grew upward from the block's top face — fine for a floor block, but a marker
     * on the underside of a ceiling/overhang (block has more solid terrain above it) would spawn every
     * particle embedded inside that rock, invisible to the player: "nothing happens" even though the
     * beacon was placed correctly. Checking the block directly above picks the visible side instead.
     *
     * <p>Uses {@code DustParticleEffect} (redstone-dust style) instead of the fixed-color END_ROD
     * sparkle so the beacon can actually take on the configured color; constructor verified against
     * the 1.21.10 mapped jar: {@code DustParticleEffect(int packedRgb, float scale)}.
     */
    private static void tickMarker(MinecraftClient mc) {
        if (markerPos == null) return;
        if (System.currentTimeMillis() > markerExpireMs) {
            markerPos = null;
            if (markerEntity != null) { markerEntity.setGlowing(false); markerEntity = null; }
            return;
        }
        if (mc.world == null) return;
        // Live-follow: if the beacon is tracking an entity, recompute its position every tick instead
        // of the frozen snapshot (feedback: "el marcador también se mueva con el zombie o mob"). If it
        // died, unloaded, or left the world since the last tick, fall back to a frozen beacon at its
        // last known spot rather than crashing or vanishing abruptly.
        if (markerEntity != null) {
            if (!markerEntity.isAlive() || markerEntity.getEntityWorld() != mc.world) {
                markerEntity.setGlowing(false);
                markerEntity = null;
            } else {
                markerPos = markerEntity.getBlockPos();
            }
        }
        // Feedback: "puede ser más brillante?" — DustParticleEffect has no glow/HDR knob, so the two
        // levers that read as "brighter" are bigger particles (scale 1.2 -> 2.0) and denser spawning
        // (every tick instead of every other) — doubles the on-screen particle count for the same
        // beacon lifetime.
        var dust = new net.minecraft.particle.DustParticleEffect(markerColor.rgb, 2.0f);
        boolean openAbove = mc.world.getBlockState(markerPos.up()).isAir();
        double dir = openAbove ? 1.0 : -1.0;
        double x = markerPos.getX() + 0.5, z = markerPos.getZ() + 0.5;
        double y = openAbove ? markerPos.getY() + 1.0 : markerPos.getY();
        switch (markerStyle) {
            case SHORT_COLUMN -> {
                for (int i = 0; i <= 4; i += 1) {
                    mc.world.addParticleClient(dust, x, y + i * dir, z, 0.0, 0.02 * dir, 0.0);
                }
            }
            case RING -> {
                int points = 12;
                for (int i = 0; i < points; i++) {
                    double a = (2 * Math.PI * i) / points;
                    mc.world.addParticleClient(dust,
                            x + Math.cos(a) * 0.6, y, z + Math.sin(a) * 0.6, 0.0, 0.0, 0.0);
                }
            }
            case BURST -> mc.world.addParticleClient(dust, x, y, z, 0.0, 0.05 * dir, 0.0);
            case COLUMN -> {
                for (int i = 0; i <= 14; i += 2) {
                    mc.world.addParticleClient(dust, x, y + i * dir, z, 0.0, 0.02 * dir, 0.0);
                }
            }
        }
    }

    /** Hard off (controller vanished / GUI opened in hold mode): stop zooming, restore bobbing. */
    public static void deactivate(MinecraftClient mc) {
        zooming = false;
        if (bobbingModified && mc != null) {
            mc.options.getBobView().setValue(savedBobbing);
            bobbingModified = false;
        }
        if (viewDistanceModified && mc != null) {
            mc.options.getViewDistance().setValue(savedViewDistance);
            mc.options.getSimulationDistance().setValue(savedSimulationDistance);
            mc.options.sendClientSettings();
            viewDistanceModified = false;
        }
    }

    /**
     * FOV multiplier for {@code GameRendererMixin} — called once per frame from {@code getFov}.
     * Idle fast path: a single compare. Easing uses BetterZoom's smoothstep of the easing factor,
     * frame-normalized to 60 fps equivalents so the glide speed doesn't depend on frame rate.
     */
    public static float fovFactor() {
        float target = zooming ? targetFactor : 1.0f;
        if (factor == target) { lastNanos = 0L; return factor; }

        long now = System.nanoTime();
        double dt = lastNanos == 0L ? (1.0 / 60.0) : (now - lastNanos) / 1.0e9;
        lastNanos = now;
        if (dt <= 0 || dt > 0.25) dt = 1.0 / 60.0;   // clamp pauses/hitches

        if (!smooth) { factor = target; return factor; }
        double e = easing;
        double eased = e * e * (3.0 - 2.0 * e);              // BetterZoom's smoothstep(easing)
        double a = 1.0 - Math.pow(1.0 - eased, dt * 60.0);   // frame-rate independent
        float next = factor + (float) ((target - factor) * a);
        if (Math.abs(target - next) < 0.0008f) next = target;   // settle → re-enables the fast path
        factor = next;
        return next;
    }

    /**
     * Camera-speed multiplier while zoomed (the gamepad-native equivalent of BetterZoom's mouse
     * sensitivity reduction): auto mode tracks the eased FOV factor so aim speed matches what you
     * see (including during the glide); fixed mode applies the configured multiplier while zoomed.
     */
    public static double lookFactor(ControllerConfig cfg) {
        if (!zooming && factor >= 0.999f) return 1.0;
        return cfg.zoomAutoSensitivity ? Math.max(0.02f, factor)
                                       : (zooming ? cfg.zoomSensitivity : 1.0);
    }

    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : Math.min(v, hi); }

    // ---- Cinematic bars (feedback: letterbox bars that close in while zooming) -------------------

    private static float barProgress = 0f;   // 0 = no bars, 1 = fully closed to the configured height
    private static long barLastNanos = 0L;
    /** Bars fully close/open in ~0.3s regardless of the configured zoom FOV — a fixed-rate ease,
     *  deliberately decoupled from {@link #factor} so the "curtain" always feels the same speed. */
    private static final float BAR_PROGRESS_PER_SEC = 3.3f;

    /**
     * Draws two black letterbox bars (top/bottom) that close in toward the center while zooming and
     * open back up on release — purely a 2D HUD overlay, no render-pipeline hooks. Called once per
     * frame from the same {@code HudRenderCallback} the rest of SteamPad's HUD uses. Off (0 cost
     * beyond the toggle check) unless {@code ControllerConfig.zoomCinematicBars} is enabled.
     */
    public static void renderCinematicBars(net.minecraft.client.gui.DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        long handle = dev.steampad.service.ActiveControllerService.getActiveHandle();
        if (handle == 0L) return;
        ControllerConfig cfg = dev.steampad.config.ConfigManager.getControllerConfig(handle);
        if (!cfg.zoomCinematicBars) {
            barProgress = 0f;   // stay reset so toggling it back on doesn't resume mid-animation
            return;
        }

        long now = System.nanoTime();
        double dt = barLastNanos == 0L ? (1.0 / 60.0) : (now - barLastNanos) / 1.0e9;
        barLastNanos = now;
        if (dt <= 0 || dt > 0.25) dt = 1.0 / 60.0;

        float target = zooming ? 1f : 0f;
        float step = (float) (BAR_PROGRESS_PER_SEC * dt);
        if (barProgress < target) barProgress = Math.min(target, barProgress + step);
        else if (barProgress > target) barProgress = Math.max(target, barProgress - step);
        if (barProgress <= 0f) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        int barH = Math.round(h * (clamp(cfg.zoomCinematicBarsHeightPct, 5f, 20f) / 100f) * barProgress);
        if (barH <= 0) return;
        ctx.fill(0, 0, w, barH, 0xFF000000);
        ctx.fill(0, h - barH, w, h, 0xFF000000);
    }
}
