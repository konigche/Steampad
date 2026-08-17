package dev.steampad.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.steampad.config.ConfigManager;
import dev.steampad.input.GamepadMappings;
import dev.steampad.input.InputBindingManager;
import dev.steampad.input.sdl.Sdl3GamepadProvider;
import dev.steampad.radial.RadialMenuController;
import dev.steampad.radial.RadialMenuOverlay;
import dev.steampad.screen.ControllerSelectScreen;
import dev.steampad.service.ActiveControllerService;
import dev.steampad.service.ControllerClaimService;
import dev.steampad.service.ControllerManager;
import dev.steampad.steam.SteamBootstrap;
import dev.steampad.steam.SteamNativeLoader;
import dev.steampad.util.LogUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * Client entry point for SteamPad.
 *
 * Initialization order:
 * 1. Load Steam natives
 * 2. Init Steam API + Steam Input
 * 3. Load all configs
 * 4. Restore active controller from config
 * 5. Register tick event (callbacks + input dispatch)
 * 6. Register HUD render callback (radial overlay)
 * 7. Register keybind to open ControllerSelectScreen
 */
public class SteamPadClient implements ClientModInitializer {

    private static KeyMapping openMenuKey;

    /** Whether the one-time "controller is live" startup rumble has fired this session. */
    private static boolean startupRumbleDone = false;
    /** Handle armed for the deferred startup rumble, and when to actually fire it — see the comment
     *  at the call site below for why this is delayed instead of fired the instant the controller
     *  becomes active. */
    private static long startupRumbleArmedHandle = 0L;
    private static long startupRumbleFireAtMs = 0L;

    // Shared "controller connected" tap — used both for the very first activation (below) and for
    // later hot-plug/reconnect events (preferred-controller reconnect, auto-activate after a
    // disconnect). Feedback across several rounds ("no ha cambiado... quiero que sea mas corto que el
    // de probar mando") kept landing on this exact value because two OTHER call sites (the hot-plug
    // ones) were still hardcoded at the old, louder 0.45f/80ms and never got touched by earlier tuning
    // passes that only edited the very-first-activation call — from the player's seat "the vibration
    // when the controller connects" is one thing, not three independently-tuned ones.
    //
    // Bumped this round (feedback: "es muy debil, subelo un poco mas") — 0.12/15ms was so faint and so
    // brief (15ms is close to a single rumble frame) it read as barely-there even on hardware that
    // handles the Haptics Test Screen's presets fine. Still deliberately far short of the 300ms
    // "probar mando" test-vibration button (the "shorter than that" preference this round didn't
    // touch) — just no longer at the very floor of what's perceptible at all.
    private static final float CONNECT_RUMBLE_INTENSITY = 0.25f;
    private static final int CONNECT_RUMBLE_MS = 60;
    // The unified 20 ms value STILL felt long in hardware ("la vibracion de inicio sigue siendo
    // larga") — with the number this small, the remaining suspect isn't the number: it's the driver
    // stack. SDL_RumbleGamepad's duration_ms is honored by SDL itself, but Steam's virtual gamepad
    // and streaming stacks (Moonlight/Sunshine, both in this user's setup) are known to run rumble
    // on their own repeat cadence and can stretch a one-shot pulse well past its nominal length. Two
    // mechanism-level fixes instead of a fifth blind tune: (1) an EXPLICIT zero-rumble stop sent
    // ~100 ms after every connect tap — a hard upper bound no driver can ignore, since a new rumble
    // command always supersedes the previous one; (2) a cooldown so an enumeration flicker (device
    // list blinking during startup, common over streaming) can't fire a TRAIN of taps that reads as
    // one long buzz.
    private static long rumbleStopDueAtMs = 0L;
    private static long rumbleStopHandle = 0L;
    private static long lastConnectRumbleMs = 0L;
    private static final long CONNECT_RUMBLE_COOLDOWN_MS = 3000;

    /** GUI depth for the virtual cursor / keyboard / container-hint overlay layer. Above item stacks
     *  (z 150) and above vanilla tooltips (z 400): these are input surfaces the player is actively
     *  aiming at, so nothing the screen draws should ever cover them. Only meaningful on the versions
     *  whose GUI transform still has a z — see {@link dev.steampad.compat.mc.GuiPose#pushOverlay}. */
    private static final float OVERLAY_Z = 450f;

    /** The single entry point for every connect-feedback rumble: cooldown-gated + auto-stop. */
    private static void connectRumble(long handle) {
        long now = System.currentTimeMillis();
        if (now - lastConnectRumbleMs < CONNECT_RUMBLE_COOLDOWN_MS) return;
        lastConnectRumbleMs = now;
        ControllerManager.rumble(handle, CONNECT_RUMBLE_INTENSITY, CONNECT_RUMBLE_MS);
        rumbleStopHandle = handle;
        rumbleStopDueAtMs = now + 100;
    }

    /** Sends the explicit stop once due (called every tick — cheap no-op otherwise). */
    private static void tickRumbleStop() {
        if (rumbleStopDueAtMs != 0L && System.currentTimeMillis() >= rumbleStopDueAtMs) {
            ControllerManager.rumble(rumbleStopHandle, 0f, 0);
            rumbleStopDueAtMs = 0L;
            rumbleStopHandle = 0L;
        }
    }

    /** Handles connected on the previous tick, to detect hot-plug of the preferred controller (S8). */
    private static final java.util.Set<Long> prevConnected = new java.util.HashSet<>();

    /** One-shot latch so "every pad is taken by another instance" is stated once, not every tick. */
    private static boolean warnedNoFreeController = false;

    /**
     * First controller in {@code detected} this instance can actually take, claiming it in the process;
     * null when none is available. {@code preferredOnly} restricts the search to the user's "Default".
     *
     * <p>Claims rather than merely testing: between an "is it free?" check and the matching activation
     * there is a window in which a second instance can pass the same check, and both then drive the
     * same physical pad. {@link ControllerClaimService#tryClaim} acquires atomically, so exactly one
     * instance wins and the loser simply moves on to the next pad in the list.
     */
    private static dev.steampad.steam.SteamControllerHandleRef claimFirstAvailable(
            java.util.List<dev.steampad.steam.SteamControllerHandleRef> detected, boolean preferredOnly) {
        for (var r : detected) {
            if (preferredOnly && !ActiveControllerService.isPreferred(r.handle)) continue;
            if (ControllerClaimService.tryClaim(r.handle, detected)) return r;
        }
        return null;
    }

    @Override
    public void onInitializeClient() {
        LogUtil.info("SteamPad client initializing...");

        // 0. Load configs FIRST. Steps 1-2 below read GlobalConfig (loadNatives, customNativesPath,
        // steamAttachMode) — and getGlobal() used to hand back DEFAULTS until loadAll() ran, which
        // only happened at step 3. Real consequence caught in a Game Mode log: the user saved
        // steamAttachMode=ALWAYS, but startup read the default NEVER and never attached — the
        // in-game change worked (config was loaded by then) yet every restart silently reverted.
        ConfigManager.loadAll();

        // 0.5 IGA manifest hygiene (B076): while the experimental deployIgaManifest flag is OFF
        // (default), remove any game_actions_*.vdf SteamPad deployed in earlier versions — their mere
        // existence makes Steam suppress the virtual gamepad for the shortcut in Game Mode, which is
        // how v0.42/v0.43 ended at "0 controles detectados". Runs before anything Steam-related and
        // needs no natives — it's plain file I/O on Steam's config folder. One dir listing, startup only.
        if (!ConfigManager.getGlobal().deployIgaManifest) {
            dev.steampad.steam.SteamControllerConfigDeployer.cleanupOurManifests();
        }

        // 0.6 Entity Model Features compat: tell EMF to stand down on an entity SteamPad is emoting,
        // so a Fresh Animations pack can't overwrite the dance. Registered at init because EMF's API
        // takes a long-lived condition it evaluates itself per frame — nothing here runs per frame.
        // Pure no-op when EMF isn't installed. See EmfCompat for the conflict it resolves.
        dev.steampad.compat.EmfCompat.init();

        // 1. Load natives
        boolean nativesLoaded = false;
        if (ConfigManager.getGlobal().loadNatives) {
            String customPath = ConfigManager.getGlobal().customNativesPath;
            if (!customPath.isEmpty()) {
                SteamNativeLoader.setCustomPath(java.nio.file.Path.of(customPath));
            }
            nativesLoaded = SteamNativeLoader.load();
        } else {
            LogUtil.info("Native loading disabled in config.");
        }

        // 2. Init Steam
        if (nativesLoaded) {
            boolean steamOk = SteamBootstrap.init();
            if (!steamOk) {
                LogUtil.warn("Steam initialization failed. Controller features disabled.");
                LogUtil.warn("Launch Minecraft from Steam, or ensure steam_appid.txt is present in the run directory.");
            }
        }

        // (configs already loaded at step 0 — see above)

        // NOTE: GLFW/SDL3-touching init (gamepad mappings, SDL3, controller scan) is deferred to the
        // first client tick. In this Fabric/MC version onInitializeClient runs BEFORE MC initializes
        // GLFW, so calling glfwUpdateGamepadMappings / SDL_Init here corrupted GLFW init and crashed
        // the game ("GLFW error before init"). By the first tick, GLFW is fully up and we're on the
        // render thread — the correct place for these calls.

        // 4.6 Splitscreen: apply the saved window layout as early as possible — BEFORE a screen's own
        // init() computes its layout for the OLD window size, instead of at the first client tick
        // (which runs after at least one frame has already been presented at the wrong position/size,
        // visible as a "opens centered, then jumps" flash — feedback from v0.24.0). BEFORE_INIT fires
        // synchronously during MinecraftClient's own startup sequence (title screen), before the render
        // loop's first frame — repositioning here means the screen that's about to init lays itself out
        // for the NEW size from the start. onFirstTick() is itself idempotent (a startupApplied guard),
        // so this and the END_CLIENT_TICK call below can never double-apply.
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.BEFORE_INIT.register(
                (client, screen, scaledWidth, scaledHeight) ->
                        dev.steampad.client.window.WindowArrangeController.onFirstTick(client));

        // 4.7 Aiming body-facing correction (point 5/D122) — registered on START_CLIENT_TICK
        // DELIBERATELY, separate from every other tick-based dispatch below (which all run on
        // END_CLIENT_TICK). Vanilla's own bow-release-and-fire processing happens inside
        // MinecraftClient.tick()'s own body, before END_CLIENT_TICK fires — so a correction applied at
        // END of tick N is only visible starting tick N+1, one tick after a release that happened
        // during tick N would have already used it. Running this specific, narrow piece at START of
        // tick N instead means a release later in that SAME tick sees this tick's fresh correction.
        // See ThirdPersonCameraController.tickAimFacing's own doc for the full mechanism.
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (mc.player != null && mc.level != null) {
                dev.steampad.input.ThirdPersonCameraController.tickAimFacing(mc);
            }
        });

        // 5. Tick event: deferred backend init + Steam callbacks + input dispatch
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            long tProf = dev.steampad.util.TickProfiler.begin();
            dev.steampad.client.window.WindowArrangeController.onFirstTick(mc);
            ensureFallbackBackendsInit();
            SteamBootstrap.runCallbacks();

            // Keep the active controller in sync with what's actually connected:
            //  - clear it if the active controller disconnected (lets another auto-activate, and
            //    input dispatch stops cleanly),
            //  - auto-activate the first present controller when nothing is active, so the mod
            //    "just works" on connect without manual selection.
            long tPoll = dev.steampad.util.TickProfiler.begin();
            var detected = ControllerManager.getConnectedControllers();
            dev.steampad.util.TickProfiler.end(dev.steampad.util.TickProfiler.PAD_POLL, tPoll);

            // NOTE: there is no per-tick "reconnect config migration" any more. Per-controller config
            // files are named after the pad's PERSISTENT IDENTITY (VID/PID, plus the serial when SDL
            // can read one — see dev.steampad.service.ControllerIdentity), so a reconnect resolves to
            // the same key and simply keeps using the same already-loaded config. The old code copied
            // files forward keyed by DISPLAY NAME every time a handle changed, which both mixed up two
            // pads of the same model and rewrote name_index.json on every tick when two of them were
            // connected. ControllerManager.refreshCache() resolves identities as part of the poll.

            // S8: if the preferred ("Default") controller JUST connected, switch to it — even if another
            // controller is currently active. Edge-triggered (only on the tick it appears) so it doesn't
            // fight a deliberate manual selection while the default stays plugged in.
            String preferredId = ConfigManager.getGlobal().preferredControllerId;
            if (preferredId != null && !preferredId.isEmpty()) {
                for (var r : detected) {
                    if (prevConnected.contains(r.handle)) continue;          // not newly connected
                    if (!ActiveControllerService.isPreferred(r.handle)) continue;   // not the default
                    if (ActiveControllerService.getActiveHandle() == r.handle) continue;
                    // Never steal a pad another live instance already holds — otherwise two instances
                    // that share a "Default" both grab it the moment it appears (splitscreen). Claim
                    // FIRST: a plain "is it free?" test leaves a window in which both instances answer
                    // yes on the same tick and both activate it.
                    if (!ControllerClaimService.tryClaim(r.handle, detected)) continue;
                    ActiveControllerService.setActive(r.handle);
                    if (startupRumbleDone) connectRumble(r.handle);
                    LogUtil.info("[SteamPad] Preferred controller connected — switched to {}.", r.displayName);
                    break;
                }
            }
            prevConnected.clear();
            for (var r : detected) prevConnected.add(r.handle);

            long active = ActiveControllerService.getActiveHandle();
            final long activeNow = active;
            if (active != 0L && detected.stream().noneMatch(r -> r.handle == activeNow)) {
                // Tear down whatever the disappearing controller was holding BEFORE a different
                // physical device gets auto-activated below, in the SAME tick — see the javadoc on
                // releaseAllHeldStateOnControllerLoss() for why this can't wait for
                // GamepadInputDispatcher.tick() to notice on its own (it never gets the chance to,
                // for this exact handle, once a replacement takes over first). Only relevant for the
                // fallback (SDL3/GLFW) dispatch path — Steam Input manages its own separate state.
                if (dev.steampad.service.ControllerManager.isFallbackHandle(activeNow)) {
                    dev.steampad.input.GamepadInputDispatcher.releaseAllHeldStateOnControllerLoss();
                }
                ActiveControllerService.clearActive();
                active = 0L;
            }
            if (active == 0L && !detected.isEmpty()) {
                // Prefer the remembered ("Default") controller, else the first pad we can actually
                // CLAIM. Claiming is what makes several instances each end up on a distinct controller
                // instead of all fighting over pad #0.
                //
                // There is deliberately no "if everything is claimed, take pad #0 anyway" fallback any
                // more. That fallback could only ever fire when every connected pad was already held by
                // another LIVE instance, and in that situation grabbing one is not fail-open, it is the
                // bug: it put two instances on one physical pad, so every button pressed in one fired
                // in the other too. A dead instance's claim still expires on its own (heartbeat TTL),
                // so this cannot strand a single-instance session.
                var chosen = claimFirstAvailable(detected, true);
                if (chosen == null) chosen = claimFirstAvailable(detected, false);
                if (chosen != null) {
                    warnedNoFreeController = false;
                    ActiveControllerService.setActive(chosen.handle);
                    active = chosen.handle;
                    LogUtil.info("[SteamPad] Auto-activated controller: {} (handle={}, source: {}).",
                        chosen.displayName, chosen.handle, ControllerManager.activeSource());
                    // Connection feedback for LATER hot-plugs: a brief, light rumble so the player feels
                    // the link. The very first activation at startup is handled by the startup-rumble
                    // block below (so a controller restored from config also vibrates, exactly once).
                    if (startupRumbleDone) connectRumble(chosen.handle);
                } else if (!warnedNoFreeController) {
                    warnedNoFreeController = true;
                    LogUtil.info("[SteamPad] Every connected controller ({}) is in use by another "
                        + "Minecraft instance — none activated here. Free one there, or connect another "
                        + "pad; this instance picks it up automatically.", detected.size());
                }
            }

            // Cross-instance claim upkeep: heartbeat our claim so other instances steer clear, and drop
            // the controller if we no longer own it (another instance won a same-tick race for it).
            long claimActive = ActiveControllerService.getActiveHandle();
            long tClaim = dev.steampad.util.TickProfiler.begin();
            if (claimActive != 0L) {
                if (!ControllerClaimService.ensureClaim(claimActive, detected)) {
                    ActiveControllerService.clearActive();
                    active = 0L;
                }
            } else {
                ControllerClaimService.release();
            }
            dev.steampad.util.TickProfiler.end(dev.steampad.util.TickProfiler.CLAIM_IO, tClaim);

            // Startup vibration: once a controller is active for the first time this session — whether
            // restored from config (the "default") or auto-activated — give a clear rumble so the
            // player knows it's live. Fires exactly once. If the default isn't connected, the
            // auto-activate block above already picked another, so this still vibrates one of them.
            //
            // DEFERRED, not fired the same tick the handle becomes "active": feedback this round was
            // that it feels entirely gone even though the Haptics Test Screen's own preset (same
            // mechanism, comparable intensity) is felt fine on demand — meaning the trigger, not the
            // magnitude, is the suspect. ActiveControllerService can mark a handle active a tick before
            // the backend that actually OWNS rumble for it (Sdl3GamepadProvider.isSdl3Handle /
            // GlfwControllerProvider.isGlfwHandle) has finished registering that same handle — on that
            // exact tick, ControllerManager.rumble() would misroute to the wrong branch (or the wrong
            // branch silently no-ops) and nothing plays, permanently, since this is a one-shot. A short
            // settle window removes the race without needing to know which backend actually lost it.
            if (!startupRumbleDone && startupRumbleFireAtMs == 0L) {
                long activeForRumble = ActiveControllerService.getActiveHandle();
                if (activeForRumble != 0L) {
                    startupRumbleArmedHandle = activeForRumble;
                    startupRumbleFireAtMs = System.currentTimeMillis() + 750;
                }
            }
            if (startupRumbleFireAtMs != 0L && System.currentTimeMillis() >= startupRumbleFireAtMs) {
                startupRumbleDone = true;
                startupRumbleFireAtMs = 0L;
                // Only rumble if the same controller is still active 750ms later — if it disconnected
                // or got swapped in that window, skip silently rather than rumble the wrong/stale handle.
                long handleNowForRumble = ActiveControllerService.getActiveHandle();
                if (handleNowForRumble == startupRumbleArmedHandle) {
                    // Barely-there "I'm alive" tap — via connectRumble(): cooldown-gated and followed
                    // by an explicit zero-rumble stop ~100 ms later, so no driver/streaming stack can
                    // stretch it (the mechanism fix after 4 rounds of value-only tuning — see the
                    // comment on the constants above).
                    connectRumble(startupRumbleArmedHandle);
                    LogUtil.info("[SteamPad] Startup rumble sent to active controller (handle={}).",
                        startupRumbleArmedHandle);
                } else {
                    // Diagnostics (feedback: "vibración de inicio: failed", still failing after 3 prior
                    // mechanism-level hardening rounds): if this line ever prints, the handle genuinely
                    // renumbered inside the 750ms settle window on this user's setup — real evidence for
                    // a longer settle window or a re-arm-on-mismatch retry, instead of a 5th blind tune
                    // of the same already-hardened mechanism.
                    LogUtil.info("[SteamPad] Startup rumble SKIPPED — armed handle={} but active handle "
                            + "is now {} (changed during the 750ms settle window).",
                            startupRumbleArmedHandle, handleNowForRumble);
                }
                // Onboarding fixed here (feedback: "diagnosticar por que el onboarding no se disparó"):
                // this used to be nested INSIDE the handle-still-matches check just above, so on a setup
                // where the active handle renumbers within the 750ms settle window (a real risk on this
                // user's streaming stack — see the comment on the constants above) BOTH the rumble AND
                // onboarding silently skipped together, forever, since startupRumbleDone latches true
                // either way and this whole block never runs again. Onboarding is a one-time UI concern
                // with nothing to do with whether a cosmetic rumble tap happened to survive a hardware
                // race, so it now checks the CURRENT active handle independently instead of piggybacking
                // on the rumble's own stale-handle guard — shown once EVER, the first time any
                // controller settles active, using whichever handle is actually active right now.
                long onboardingHandle = ActiveControllerService.getActiveHandle();
                if (onboardingHandle != 0L && !ConfigManager.getGlobal().hasSeenOnboarding
                        && mc.screen == null) {
                    mc.setScreen(new dev.steampad.screen.OnboardingScreen(null, onboardingHandle));
                }
            }

            tickRumbleStop();   // hard upper bound on the connect tap (see connectRumble)
            InputBindingManager.tick();
            dev.steampad.input.KeyTap.tick();   // release any radial-triggered keybind pulses
            long tHap = dev.steampad.util.TickProfiler.begin();
            dev.steampad.haptics.HapticsController.tick(mc);
            dev.steampad.util.TickProfiler.end(dev.steampad.util.TickProfiler.HAPTICS, tHap);
            dev.steampad.emote.EmoteAnimator.clientTick(mc);   // cancel-on-move + prune (FASE 63)
            // Auto third person on mount / first person on dismount (configurable) - same
            // detect-transition-then-ease shape as EmoteAnimator above, sharing PerspectiveTransition.
            dev.steampad.input.MountPerspectiveController.clientTick(mc);
            // Records where the player died, for the radial's "volver a donde morí" smart command.
            dev.steampad.service.DeathLocationService.clientTick(mc);
            // Gliders' own "Glider Perspective" option cannot persist (it is a bare static
            // OptionInstance the mod never saves) — turn it back on once per world if the user asked.
            // Two boolean reads per tick once it has run or been ruled out; see GlidersCompat.
            dev.steampad.compat.GlidersCompat.clientTick(mc);
            // Takes over / gives back vanilla's hideGui while the cinematic bars are closed. On the
            // TICK, not the render frame, so the restore still runs on the tick the bars retract even
            // if that frame never renders — otherwise the HUD could be left hidden for good.
            dev.steampad.input.ZoomController.tickCinematicHud(mc);
            // Completes any running perspective ease (incl. the delayed 3rd→1st flip) — D123.
            dev.steampad.input.PerspectiveTransition.tick(mc);
            // Advances the free camera's smoothed follow point by exactly one tick. MUST run here
            // (client tick), not in the render frame — that split is what removes the walking
            // stutter. Registered unconditionally so it also covers mouse/keyboard play; it no-ops
            // when free-look is off. See ThirdPersonCameraController.clientTick (D116).
            dev.steampad.input.ThirdPersonCameraController.clientTick(mc);

            // Close radial when button released
            if (RadialMenuController.isOpen()) {
                // Radial close is handled in ActionExecutor — when OPEN_RADIAL button is released
                // For now: also close if no controller active
                if (!ActiveControllerService.hasActiveController()) {
                    RadialMenuController.close();
                }
            }
            // Same safety net for the independent emote wheel (FASE 63 decoupling).
            if (dev.steampad.emote.EmoteWheelController.isOpen() && !ActiveControllerService.hasActiveController()) {
                dev.steampad.emote.EmoteWheelController.close();
            }

            // Lag forensics (B076): one log line per 5s window ONLY when SteamPad's own work exceeds
            // its budget or a single tick spiked — a healthy session logs nothing.
            dev.steampad.util.TickProfiler.end(dev.steampad.util.TickProfiler.CLIENT_TICK, tProf);
            dev.steampad.util.TickProfiler.tickWindow();
        });

        // 6. HUD render (radial overlay + Bedrock-style on-screen button guide).
        //    The HUD callback fires once per rendered frame, so it's also where per-frame camera
        //    look is applied (smooth + frame-rate independent, unlike the 20 Hz tick).
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            long tCam = dev.steampad.util.TickProfiler.begin();
            dev.steampad.input.CameraController.frame();
            dev.steampad.util.TickProfiler.end(dev.steampad.util.TickProfiler.CAMERA_FRAME, tCam);
            RadialMenuOverlay.render(drawContext, tickDelta.getGameTimeDeltaPartialTick(true));
            dev.steampad.emote.EmoteWheelOverlay.render(drawContext, tickDelta.getGameTimeDeltaPartialTick(true));
            dev.steampad.itemradial.ItemRadialOverlay.render(drawContext, tickDelta.getGameTimeDeltaPartialTick(true));
            dev.steampad.client.hud.GameplayHudOverlay.render(drawContext);
            dev.steampad.input.ZoomController.renderCinematicBars(drawContext);
        });

        // 6.5 Draw the controller cursor + Bedrock-style container hints on top of every screen.
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((cl, screen, w, h) ->
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((scr, ctx, mx, my, td) -> {
                // Everything here is a true overlay: it must sit above the screen's own content,
                // INCLUDING item stacks, which on the pre-Blaze3D-rewrite versions are drawn at a
                // raised z and would otherwise punch through (inventory icons over the keyboard/
                // cursor). See GuiPose.pushOverlay for the mechanism; it's a plain push on the
                // versions where draw order alone already decides what's on top.
                dev.steampad.compat.mc.GuiPose.pushOverlay(ctx, OVERLAY_Z);
                try {
                    dev.steampad.client.ui.VirtualCursorRenderer.render(ctx);
                    dev.steampad.client.ui.VirtualKeyboardRenderer.render(ctx);
                    long h2 = ActiveControllerService.getActiveHandle();
                    if (h2 != 0L && ControllerManager.isFallbackHandle(h2)
                            && ConfigManager.getControllerConfig(h2).showScreenButtonGuide) {
                        var t = ActiveControllerService.getActiveRef()
                            .map(r -> r.type).orElse(dev.steampad.steam.SteamControllerHandleRef.ControllerType.GENERIC);
                        if (scr instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) {
                            dev.steampad.client.hud.GameplayHudOverlay.renderContainerHints(ctx, t);
                        }
                    }
                } finally {
                    // finally: a throw from any renderer must not leave the GUI transform unbalanced,
                    // which would corrupt every later screen draw rather than just this overlay.
                    dev.steampad.compat.mc.GuiPose.popOverlay(ctx);
                }
            }));

        // 6.7 Emote sync, client half (FASE 63): S2C receiver + disconnect cleanup.
        dev.steampad.emote.EmoteNetworkClient.registerClient();

        // 7. Keybind to open Controller Select Screen
        openMenuKey = KeyBindingHelper.registerKeyBinding(createOpenMenuKeyBinding());

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openMenuKey.consumeClick()) {
                mc.setScreen(new ControllerSelectScreen(mc.screen));
            }
        });

        // Haptics: block-break and block-use are the two baseline events with ready-made Fabric API
        // hooks (the rest are polled per-tick or hooked via mixin — see HapticsController).
        net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents.AFTER.register(
                (world, player, pos, state) -> dev.steampad.haptics.HapticsController.onBlockBroken(state));
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(
                (player, world, hand, hitResult) -> {
                    dev.steampad.haptics.HapticsController.onBlockUsed(
                            hitResult.getBlockPos(), world.getBlockState(hitResult.getBlockPos()));
                    return net.minecraft.world.InteractionResult.PASS;   // observe only, never intercept
                });

        LogUtil.info("SteamPad client initialized. Steam available: {}, Input available: {}",
            SteamBootstrap.isSteamAvailable(), SteamBootstrap.isInputAvailable());
    }

    /**
     * One-time init of the GLFW/SDL3 fallback backends, run on the first client tick when GLFW is
     * guaranteed initialized (calling these from onInitializeClient crashes MC's GLFW init).
     */
    private static boolean fallbackInitDone = false;
    private static int fallbackInitRetryCount = 0;
    private static final int FALLBACK_INIT_RETRY_TICKS = 20;   // ~1s between attempts (20 ticks/s)
    private static final int FALLBACK_INIT_RETRY_MAX = 10;      // give up after ~10s

    private static void ensureFallbackBackendsInit() {
        if (fallbackInitDone) return;
        // Only the FIRST attempt runs on tick 0; if it throws, retry every ~1s for ~10s instead of
        // silently giving up forever (a transient GLFW/SDL hiccup would otherwise leave the mod with
        // no gamepad mappings, no SDL3, no cross-instance claim coordination for the rest of the session).
        if (fallbackInitRetryCount > 0 && fallbackInitRetryCount % FALLBACK_INIT_RETRY_TICKS != 0) {
            fallbackInitRetryCount++;
            return;
        }
        if (fallbackInitRetryCount / FALLBACK_INIT_RETRY_TICKS >= FALLBACK_INIT_RETRY_MAX) return;
        try {
            // SDL3 must init FIRST: GamepadMappings.loadAll() now teaches BOTH GLFW and SDL3 about
            // 8BitDo etc. (D097) — it can only push the SDL3 half if libSDL3 is already loaded.
            if (ConfigManager.getGlobal().useSdl3Fallback) {
                Sdl3GamepadProvider.init();   // no-op if libSDL3 is absent
            }
            GamepadMappings.loadAll();   // teach GLFW + SDL3 about 8BitDo etc. (and user overrides)
            ControllerClaimService.init();   // cross-instance claim coordination (fail-open)
            ActiveControllerService.restoreFromConfig();
            fallbackInitDone = true;
            LogUtil.info("[SteamPad] Fallback backends ready. Controllers: {} (source: {}).",
                ControllerManager.getConnectedControllers().size(), ControllerManager.activeSource());
        } catch (Throwable t) {
            fallbackInitRetryCount++;
            int attempt = fallbackInitRetryCount / FALLBACK_INIT_RETRY_TICKS + 1;
            if (attempt >= FALLBACK_INIT_RETRY_MAX) {
                LogUtil.warn("[SteamPad] Deferred backend init failed after {} retries — giving up: {}",
                    FALLBACK_INIT_RETRY_MAX, t.getMessage());
            } else {
                LogUtil.warn("[SteamPad] Deferred backend init failed (retry {}/{}): {}",
                    attempt, FALLBACK_INIT_RETRY_MAX, t.getMessage());
            }
        }
    }

    /**
     * Creates the open-menu KeyBinding using the MC 1.21.10 constructor.
     *
     * MC 1.21.10: KeyBinding(String, InputUtil.Type, int, KeyBinding.Category).
     * The category is the project's own, created via KeyBinding.Category.create(Identifier).
     */
    private static KeyMapping createOpenMenuKeyBinding() {
        // Keybind categories became a registered KeyMapping.Category object in 1.21.9; before that they
        // were a plain translation-key String. Both resolve to the same "SteamPad" heading in Controls,
        // via the key.categories.steampad lang entry.
        //? if >=1.21.9 {
        KeyMapping.Category category = KeyMapping.Category.register(
                ResourceLocation.fromNamespaceAndPath("steampad", "main"));
        return new KeyMapping(
            "key.steampad.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            category);
        //?} else {
        /*return new KeyMapping(
            "key.steampad.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.steampad");
        *///?}
    }
}
