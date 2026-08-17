package dev.steampad.input;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.ControllerConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

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
        if (button == null || button.isEmpty()) return false;
        // Reads the CONFIGURED zoom-in/out buttons rather than a hardcoded D-pad: move the level
        // controls to LB/RB and the suppression (and the HUD glyph that shares this method) follows
        // them on its own, with nothing left claiming the D-pad it no longer uses.
        if (cfg.zoomDpadAdjust
                && (button.equals(GamepadBinds.button(cfg, GamepadBinds.Bind.ZOOM_IN))
                 || button.equals(GamepadBinds.button(cfg, GamepadBinds.Bind.ZOOM_OUT)))) return true;
        return cfg.zoomMarkerEnabled && "A".equals(button);
    }

    /** One discrete step: dir=+1 zooms in (narrower FOV), -1 zooms out. Clamped to [1, options FOV]. */
    public static void adjust(int dir, ControllerConfig cfg, Minecraft mc) {
        applyDelta(dir * cfg.zoomStep, cfg, mc);
    }

    /**
     * Continuous ramp for one tick, used instead of {@link #adjust} while {@code zoomContinuous} is on:
     * the level slides for as long as the button is held instead of jumping one step per press.
     *
     * <p>The rate is derived from {@code zoomStep} rather than a second setting — {@link #STEPS_PER_SEC}
     * steps per second — so the existing "step size" slider keeps meaning the same thing in both modes
     * (bigger step = faster ramp) and there is nothing new to tune to get a sane feel.
     */
    public static void adjustContinuous(int dir, ControllerConfig cfg, Minecraft mc) {
        applyDelta(dir * cfg.zoomStep * (STEPS_PER_SEC / 20f), cfg, mc);
    }

    /** Steps per second the continuous ramp travels. 20 ticks/s is Minecraft's client tick rate. */
    private static final float STEPS_PER_SEC = 4f;

    private static void applyDelta(float degrees, ControllerConfig cfg, Minecraft mc) {
        float maxFov = Math.max(2f, mc.options.fov().get());
        float next = clamp(cfg.zoomFov - degrees, 1f, maxFov);
        if (next == cfg.zoomFov) return;   // already at the rail: nothing changed, nothing to persist
        cfg.zoomFov = next;
        levelDirty = true;
    }

    /** Per-tick upkeep: refresh the target from config, bobbing suppression, deferred persist. */
    public static void tick(Minecraft mc, ControllerConfig cfg, long handle) {
        float optionsFov = Math.max(1f, mc.options.fov().get());
        targetFactor = clamp(cfg.zoomFov / optionsFov, 0.01f, 1f);
        smooth = cfg.zoomSmooth;
        easing = clamp(cfg.zoomSmoothing, 0.05f, 0.30f);

        boolean wantBobOff = zooming && cfg.zoomDisableBobbing;
        if (wantBobOff && !bobbingModified) {
            savedBobbing = mc.options.bobView().get();
            mc.options.bobView().set(false);
            bobbingModified = true;
        } else if (!wantBobOff && bobbingModified) {
            mc.options.bobView().set(savedBobbing);
            bobbingModified = false;
        }

        boolean wantViewBoost = zooming && cfg.zoomRenderDistanceBoost > 0;
        if (wantViewBoost && !viewDistanceModified) {
            savedViewDistance = mc.options.renderDistance().get();
            savedSimulationDistance = mc.options.simulationDistance().get();
            mc.options.renderDistance().set(Math.min(32, savedViewDistance + cfg.zoomRenderDistanceBoost));
            mc.options.simulationDistance().set(Math.min(32, savedSimulationDistance + cfg.zoomRenderDistanceBoost));
            mc.options.broadcastOptions();   // without this the integrated server never sends the extra chunks
            viewDistanceModified = true;
        } else if (!wantViewBoost && viewDistanceModified) {
            mc.options.renderDistance().set(savedViewDistance);
            mc.options.simulationDistance().set(savedSimulationDistance);
            mc.options.broadcastOptions();
            viewDistanceModified = false;
        }

        // Reset-on-release: capture the configured level when the zoom starts; on release, either
        // restore it (discarding the D-pad adjustments) or keep the last level (default behaviour).
        if (zooming && !wasZooming) sessionBaseFov = cfg.zoomFov;
        if (!zooming && wasZooming && cfg.zoomResetOnRelease) {
            cfg.zoomFov = sessionBaseFov;
            levelDirty = false;   // nothing to persist — the adjustments were transient by choice
        }
        if (zooming != wasZooming) tickFirstPersonZoom(mc, cfg, zooming);
        wasZooming = zooming;

        if (!zooming && levelDirty) {   // persist the D-pad-adjusted level once, on zoom end
            levelDirty = false;
            ConfigManager.saveControllerConfig(handle);
        }

        tickMarker(mc);   // beacon outlives the zoom on purpose — it's a ping, not a zoom overlay
    }

    // ---- Zoom marker (A while zoomed): a temporary particle beacon at the aimed spot -----------
    private static net.minecraft.core.BlockPos markerPos = null;
    private static long markerExpireMs = 0L;
    /** Non-null while the beacon is tracking a LIVING entity instead of a fixed block — the particles
     *  and outline follow it each tick until it dies/unloads or the beacon expires (feedback: "el
     *  marcador también se mueva con el zombie"). */
    private static net.minecraft.world.entity.LivingEntity markerEntity = null;

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
    public static void placeMarker(Minecraft mc, ControllerConfig cfg) {
        if (mc.player == null || mc.level == null) return;
        var eye = mc.player.getEyePosition();
        var look = mc.player.getViewVector(1.0f);
        var end = eye.add(look.scale(MARKER_RAYCAST_DISTANCE));

        var blockHit = mc.player.pick(MARKER_RAYCAST_DISTANCE, 1.0f, false);
        boolean hasBlockHit = blockHit instanceof net.minecraft.world.phys.BlockHitResult
                && blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS;
        double blockDistSq = hasBlockHit ? eye.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;

        // Manual entity search: every living entity whose (slightly padded) bounding box the sightline
        // ray actually intersects, closest intersection wins.
        var searchBox = new net.minecraft.world.phys.AABB(eye, end).inflate(2.0);
        net.minecraft.world.entity.LivingEntity closestEntity = null;
        double closestDistSq = Double.MAX_VALUE;
        for (var e : mc.level.getEntities(mc.player, searchBox,
                ent -> ent instanceof net.minecraft.world.entity.LivingEntity le && le.isAlive() && !ent.isSpectator())) {
            var hit = e.getBoundingBox().inflate(0.3).clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = eye.distanceToSqr(hit.get());
            if (d < closestDistSq) { closestDistSq = d; closestEntity = (net.minecraft.world.entity.LivingEntity) e; }
        }

        // Replacing an in-progress entity mark with a new one (or a block mark) — stop glowing the old
        // target so it doesn't stay outlined forever.
        if (markerEntity != null && markerEntity != closestEntity) markerEntity.setGlowingTag(false);

        net.minecraft.core.BlockPos pos;
        if (closestEntity != null && closestDistSq < blockDistSq) {
            markerEntity = closestEntity;
            pos = closestEntity.blockPosition();
            closestEntity.setGlowingTag(true);   // vanilla's own outline render path — cheap, no custom draw code
        } else {
            markerEntity = null;
            if (hasBlockHit) {
                pos = ((net.minecraft.world.phys.BlockHitResult) blockHit).getBlockPos().immutable();
            } else {
                pos = net.minecraft.core.BlockPos.containing(end);
            }
        }
        markerPos = pos;
        markerExpireMs = System.currentTimeMillis()
                + (long) (Math.max(2f, Math.min(15f, cfg.zoomMarkerSeconds)) * 1000L);
        markerStyle = cfg.zoomMarkerStyle == null ? ControllerConfig.ZoomMarkerStyle.COLUMN : cfg.zoomMarkerStyle;
        markerColor = cfg.zoomMarkerColor == null ? ControllerConfig.ZoomMarkerColor.CYAN : cfg.zoomMarkerColor;
        if (cfg.zoomMarkerShareChat && mc.player.connection != null) {
            // A plain chat line is the only thing OTHER players can actually see from a client mod.
            mc.player.connection.sendChat(
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
    private static void tickMarker(Minecraft mc) {
        if (markerPos == null) return;
        if (System.currentTimeMillis() > markerExpireMs) {
            markerPos = null;
            if (markerEntity != null) { markerEntity.setGlowingTag(false); markerEntity = null; }
            return;
        }
        if (mc.level == null) return;
        // Live-follow: if the beacon is tracking an entity, recompute its position every tick instead
        // of the frozen snapshot (feedback: "el marcador también se mueva con el zombie o mob"). If it
        // died, unloaded, or left the world since the last tick, fall back to a frozen beacon at its
        // last known spot rather than crashing or vanishing abruptly.
        if (markerEntity != null) {
            if (!markerEntity.isAlive() || markerEntity.level() != mc.level) {
                markerEntity.setGlowingTag(false);
                markerEntity = null;
            } else {
                markerPos = markerEntity.blockPosition();
            }
        }
        // Feedback: "puede ser más brillante?" — DustParticleEffect has no glow/HDR knob, so the two
        // levers that read as "brighter" are bigger particles (scale 1.2 -> 2.0) and denser spawning
        // (every tick instead of every other) — doubles the on-screen particle count for the same
        // beacon lifetime.
        // DustParticleOptions took a Vector3f colour before 1.21.2 and a packed ARGB int after.
        //? if >=1.21.2 {
        var dust = new net.minecraft.core.particles.DustParticleOptions(markerColor.rgb, 2.0f);
        //?} else {
        /*var dust = new net.minecraft.core.particles.DustParticleOptions(
                new org.joml.Vector3f(
                        ((markerColor.rgb >> 16) & 0xFF) / 255f,
                        ((markerColor.rgb >> 8) & 0xFF) / 255f,
                        (markerColor.rgb & 0xFF) / 255f),
                2.0f);
        *///?}
        boolean openAbove = mc.level.getBlockState(markerPos.above()).isAir();
        double dir = openAbove ? 1.0 : -1.0;
        double x = markerPos.getX() + 0.5, z = markerPos.getZ() + 0.5;
        double y = openAbove ? markerPos.getY() + 1.0 : markerPos.getY();
        switch (markerStyle) {
            case SHORT_COLUMN -> {
                for (int i = 0; i <= 4; i += 1) {
                    mc.level.addParticle(dust, x, y + i * dir, z, 0.0, 0.02 * dir, 0.0);
                }
            }
            case RING -> {
                int points = 12;
                for (int i = 0; i < points; i++) {
                    double a = (2 * Math.PI * i) / points;
                    mc.level.addParticle(dust,
                            x + Math.cos(a) * 0.6, y, z + Math.sin(a) * 0.6, 0.0, 0.0, 0.0);
                }
            }
            case BURST -> mc.level.addParticle(dust, x, y, z, 0.0, 0.05 * dir, 0.0);
            case COLUMN -> {
                for (int i = 0; i <= 14; i += 2) {
                    mc.level.addParticle(dust, x, y + i * dir, z, 0.0, 0.02 * dir, 0.0);
                }
            }
        }
    }

    /** Hard off (controller vanished / GUI opened in hold mode): stop zooming, restore bobbing. */
    public static void deactivate(Minecraft mc) {
        zooming = false;
        if (bobbingModified && mc != null) {
            mc.options.bobView().set(savedBobbing);
            bobbingModified = false;
        }
        if (viewDistanceModified && mc != null) {
            mc.options.renderDistance().set(savedViewDistance);
            mc.options.simulationDistance().set(savedSimulationDistance);
            mc.options.broadcastOptions();
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
    public static void renderCinematicBars(net.minecraft.client.gui.GuiGraphics ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
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

        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        int barH = Math.round(h * (clamp(cfg.zoomCinematicBarsHeightPct, 5f, 20f) / 100f) * barProgress);
        if (barH <= 0) return;
        ctx.fill(0, 0, w, barH, 0xFF000000);
        ctx.fill(0, h - barH, w, h, 0xFF000000);
    }

    // ---- shooter-style zoom: ease to first person and back (opt-in, cfg.zoomFirstPerson) ----------
    // Feature request: "cuando hago zoom en tercera persona que se pase a primera persona, que solo
    // afecte cuando esté de espalda o la primera opción de tercera persona" — i.e. never in mirror/
    // front-view, where the camera facing the player IS the point of that mode.

    /** True while the CURRENT first-person state was entered by THIS mechanism — so releasing zoom
     *  knows whether to ease back to third person, versus leaving alone a first-person the player
     *  reached some other way (plain F5, an emote, the manual PERSPECTIVE cycle bind). */
    private static boolean ownsFirstPerson = false;

    private static void tickFirstPersonZoom(Minecraft mc, ControllerConfig cfg, boolean nowZooming) {
        if (!cfg.zoomFirstPerson || mc.player == null) return;
        if (nowZooming) {
            // Only from plain back-view — front/mirror keeps its own identity (D123), and if the
            // player is already in first person there is nothing to transition. Skip if some OTHER
            // transition (an emote, the manual cycle bind) is already running rather than fight it.
            if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) return;
            if (PerspectiveTransition.isActive()) return;
            PerspectiveTransition.beginExitToFirstPerson(CameraType.THIRD_PERSON_BACK);
            ownsFirstPerson = true;
        } else if (ownsFirstPerson) {
            ownsFirstPerson = false;
            if (PerspectiveTransition.isActive()) {
                // The ease-to-first-person from zoom start hasn't landed yet (a quick tap) — the flip
                // in PerspectiveTransition.tick() is still pending. Cancel outright rather than let a
                // released zoom still cut to first person a moment later on its own.
                PerspectiveTransition.cancel();
            } else if (mc.options.getCameraType() == CameraType.FIRST_PERSON) {
                // Landed while still zoomed — ease back out, mirroring cyclePerspective's own
                // "flip immediately on the way out" convention (the body must be visible right away).
                Vec3 eye = mc.player.getEyePosition();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                PerspectiveTransition.beginEnter(eye);
            }
            // else: the player already changed perspective themselves mid-zoom — leave it alone.
        }
    }

    // ---- "clean shot" while the bars are closed --------------------------------------------------
    // Letterbox bars alone still left the hotbar, health, hunger and the held item on screen, which
    // defeats the point of a cinematic framing ("no guarda la UI ni los objetos de la mano, debe
    // guardarlo todo"). Hiding both is gated on the SAME barProgress the bars themselves use, so the
    // three always agree and nothing can be left hidden once the bars retract.

    /** True once the bars are meaningfully closed — the gate for hiding the HUD and the hands. */
    private static boolean cinematicFramingActive() {
        return barProgress > 0.05f;
    }

    /**
     * Read by {@code ItemInHandRendererMixin} to skip the first-person hand/item pass.
     *
     * <p>Covers the ORDINARY zoom too, not only the cinematic-bars mode: arms and the held item stay
     * planted in the corner of a magnified view where they read as oversized and block the shot
     * ("quita los brazos en el zoom normal, permanecen cuando se hace zoom"). Only the hands are
     * affected — the HUD is left alone outside cinematic framing, which is the "puedes dejar lo demás"
     * part of that request.
     *
     * <p>Kept as an OR rather than folding into one flag because the two have different lifetimes:
     * {@code zooming} flips the instant the bind is released, while the bars ease out over ~0.3s and
     * must keep the hands hidden for that whole retraction.
     */
    public static boolean shouldHideHands() {
        return zooming || cinematicFramingActive();
    }

    /** Vanilla's own hideGui setting from before the bars took over, so an F1 the player had set
     *  themselves is restored rather than clobbered. Mirrors the emote transition's approach. */
    private static boolean hudHiddenSnapshot = false;
    private static boolean hudHiddenByBars = false;

    /**
     * Once per client tick: takes over {@code hideGui} while the cinematic bars are closed and gives
     * it back exactly as it was afterwards.
     *
     * <p>Driven from the client tick rather than the render callback so the restore still happens on
     * the tick the bars finish retracting even if nothing renders that frame — otherwise a player who
     * toggled the feature off mid-zoom could be left with a permanently hidden HUD.
     */
    public static void tickCinematicHud(Minecraft mc) {
        boolean active = cinematicFramingActive();
        if (active && !hudHiddenByBars) {
            hudHiddenSnapshot = mc.options.hideGui;
            mc.options.hideGui = true;
            hudHiddenByBars = true;
        } else if (!active && hudHiddenByBars) {
            mc.options.hideGui = hudHiddenSnapshot;
            hudHiddenByBars = false;
            resetStabilizer();
        }
    }

    // ---- camera stabilizer (feedback: "al mover la cámara pareciera que está sobre un estabilizador")
    // A steadicam feel: while the bars are closed, look input is eased toward the stick's request
    // instead of following it 1:1, so pans start and stop softly. Deliberately a MULTIPLIER on the
    // already-shaped look delta (curve, sensitivity, aim assist are all folded in upstream) rather
    // than a second smoothing stage, so it can never fight those.

    /** Fraction of the requested look delta applied per tick while stabilized — lower = heavier rig. */
    private static final double STABILIZER_HALFLIFE_SEC = 0.16;
    private static double stabYaw = 0.0, stabPitch = 0.0;

    /** True when look input should be run through the stabilizer (bars closed and enabled). */
    public static boolean stabilizerActive() {
        return cinematicFramingActive();
    }

    /**
     * Eases a per-frame look delta for the steadicam feel. Returns the delta that should actually be
     * applied this frame; the remainder is carried and released over the following frames, so no input
     * is lost — a pan ends where the player asked, just more gently.
     */
    public static double[] stabilize(double yawDeg, double pitchDeg, double dt) {
        stabYaw += yawDeg;
        stabPitch += pitchDeg;
        double factor = 1.0 - Math.pow(2.0, -dt / STABILIZER_HALFLIFE_SEC);
        double outYaw = stabYaw * factor;
        double outPitch = stabPitch * factor;
        stabYaw -= outYaw;
        stabPitch -= outPitch;
        return new double[]{outYaw, outPitch};
    }

    /** Drops any carried-over stabilizer motion — called when the bars retract so the next zoom
     *  doesn't start by replaying leftover input. */
    public static void resetStabilizer() {
        stabYaw = 0.0;
        stabPitch = 0.0;
    }
}
