package dev.steampad.client;

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
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
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

    private static KeyBinding openMenuKey;

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
    private static final float CONNECT_RUMBLE_INTENSITY = 0.12f;
    private static final int CONNECT_RUMBLE_MS = 15;
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
            var detected = ControllerManager.getConnectedControllers();

            // Reconnect config migration (feedback: "cuando desconecto el gamepad... se desconfiguran
            // los botones... tengo que reiniciar el juego"): SDL3/GLFW hand a NEW numeric handle to
            // the SAME physical controller on every reconnect, but saved config files are keyed by
            // that handle — without this, a reconnect silently fell back to blank defaults. Cheap
            // no-op for controllers that were already connected last tick (see ConfigManager's doc).
            for (var r : detected) {
                if (!prevConnected.contains(r.handle)) {
                    ConfigManager.migrateControllerConfigByName(r.handle, r.displayName);
                }
            }

            // S8: if the preferred ("Default") controller JUST connected, switch to it — even if another
            // controller is currently active. Edge-triggered (only on the tick it appears) so it doesn't
            // fight a deliberate manual selection while the default stays plugged in.
            String preferredName = ConfigManager.getGlobal().preferredControllerName;
            if (preferredName != null && !preferredName.isEmpty()) {
                for (var r : detected) {
                    if (prevConnected.contains(r.handle)) continue;          // not newly connected
                    if (!preferredName.equals(r.displayName)) continue;       // not the default
                    if (ActiveControllerService.getActiveHandle() == r.handle) continue;
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
                // Prefer the remembered ("Default") controller if present AND not already held by
                // another live instance; else the first UNCLAIMED controller; else (all claimed) the
                // first — fail-open so the user is never left without a pad. This is what lets several
                // instances each grab a distinct controller instead of all fighting over pad #0.
                String preferred = ConfigManager.getGlobal().preferredControllerName;
                final var detectedF = detected;
                var chosen = detected.stream()
                    .filter(r -> !preferred.isEmpty() && preferred.equals(r.displayName))
                    .filter(r -> !ControllerClaimService.isClaimedByOther(r.handle, detectedF))
                    .findFirst()
                    .orElseGet(() -> detectedF.stream()
                        .filter(r -> !ControllerClaimService.isClaimedByOther(r.handle, detectedF))
                        .findFirst()
                        .orElse(detectedF.get(0)));
                ActiveControllerService.setActive(chosen.handle);
                active = chosen.handle;
                LogUtil.info("[SteamPad] Auto-activated controller: {} (source: {}).",
                    chosen.displayName, ControllerManager.activeSource());
                // Connection feedback for LATER hot-plugs: a brief, light rumble so the player feels
                // the link. The very first activation at startup is handled by the startup-rumble
                // block below (so a controller restored from config also vibrates, exactly once).
                if (startupRumbleDone) connectRumble(chosen.handle);
            }

            // Cross-instance claim upkeep: if the active controller is already held by another live
            // instance (e.g. a restore-by-name collision), drop it so the next tick auto-selects a
            // free one; otherwise hold + heartbeat our claim so other instances steer clear.
            long claimActive = ActiveControllerService.getActiveHandle();
            if (claimActive != 0L) {
                if (ControllerClaimService.isClaimedByOther(claimActive, detected)) {
                    ActiveControllerService.clearActive();
                    active = 0L;
                } else {
                    ControllerClaimService.ensureClaim(claimActive, detected);
                }
            } else {
                ControllerClaimService.release();
            }

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
                        && mc.currentScreen == null) {
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
            RadialMenuOverlay.render(drawContext, tickDelta.getTickProgress(true));
            dev.steampad.emote.EmoteWheelOverlay.render(drawContext, tickDelta.getTickProgress(true));
            dev.steampad.client.hud.GameplayHudOverlay.render(drawContext);
            dev.steampad.input.ZoomController.renderCinematicBars(drawContext);
        });

        // 6.5 Draw the controller cursor + Bedrock-style container hints on top of every screen.
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((cl, screen, w, h) ->
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((scr, ctx, mx, my, td) -> {
                dev.steampad.client.ui.VirtualCursorRenderer.render(ctx);
                dev.steampad.client.ui.VirtualKeyboardRenderer.render(ctx);
                long h2 = ActiveControllerService.getActiveHandle();
                if (h2 != 0L && ControllerManager.isFallbackHandle(h2)
                        && scr instanceof net.minecraft.client.gui.screen.ingame.HandledScreen
                        && ConfigManager.getControllerConfig(h2).showScreenButtonGuide) {
                    var t = ActiveControllerService.getActiveRef()
                        .map(r -> r.type).orElse(dev.steampad.steam.SteamControllerHandleRef.ControllerType.GENERIC);
                    dev.steampad.client.hud.GameplayHudOverlay.renderContainerHints(ctx, t);
                }
            }));

        // 6.7 Emote sync, client half (FASE 63): S2C receiver + disconnect cleanup.
        dev.steampad.emote.EmoteNetworkClient.registerClient();

        // 7. Keybind to open Controller Select Screen
        openMenuKey = KeyBindingHelper.registerKeyBinding(createOpenMenuKeyBinding());

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openMenuKey.wasPressed()) {
                mc.setScreen(new ControllerSelectScreen(mc.currentScreen));
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
                    return net.minecraft.util.ActionResult.PASS;   // observe only, never intercept
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
    private static KeyBinding createOpenMenuKeyBinding() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("steampad", "main"));
        return new KeyBinding(
            "key.steampad.open_menu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            category);
    }
}
