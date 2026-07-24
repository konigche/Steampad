package dev.steampad.steam;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.ControllerConfig;
import dev.steampad.haptics.HapticsController;
import dev.steampad.input.ExternalWidgetScanner;
import dev.steampad.platform.EnvironmentReport;
import dev.steampad.platform.LinuxRuntimeInspector;
import dev.steampad.service.ActiveControllerService;
import dev.steampad.service.ControllerManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.List;

/**
 * Generates a human-readable debug dump of the current Steam + controller runtime state.
 *
 * <p>Expanded to cover the WHOLE mod's live state in v0.58.0 (explicit user request after the
 * virtual-mouse lag investigation: "este volcado vuélvelo súper completo, para que analice TODO el
 * mod para futuros bugs") — every section is cheap getters only, no scans, safe to run mid-game.
 */
public final class SteamRuntimeDiagnostics {

    private SteamRuntimeDiagnostics() {}

    public static String generateDump() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SteamPad Debug Dump ===\n");
        String version = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("steampad")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        sb.append("Mod version: ").append(version).append("\n\n");

        // Environment
        EnvironmentReport env = SteamBootstrap.getEnvironmentReport();
        if (env != null) {
            sb.append("-- Environment --\n");
            sb.append(env.toDebugString()).append("\n\n");
        }

        // Linux details
        if (LinuxRuntimeInspector.isLinux()) {
            sb.append("-- Linux Runtime --\n");
            sb.append(LinuxRuntimeInspector.buildReport()).append("\n\n");
        }

        // Steam API
        sb.append("-- Steam API --\n");
        sb.append("Steam Available: ").append(SteamBootstrap.isSteamAvailable()).append("\n");
        sb.append("Input Available: ").append(SteamBootstrap.isInputAvailable()).append("\n");
        sb.append("ActionSet Gameplay valid: ").append(SteamActionRegistry.isValidHandle(SteamActionRegistry.actionSetGameplay)).append("\n");
        sb.append("ActionSet Menu valid: ").append(SteamActionRegistry.isValidHandle(SteamActionRegistry.actionSetMenu)).append("\n");
        sb.append("ActionSet Inventory valid: ").append(SteamActionRegistry.isValidHandle(SteamActionRegistry.actionSetInventory)).append("\n");
        sb.append("ActionSet Mounted valid: ").append(SteamActionRegistry.isValidHandle(SteamActionRegistry.actionSetMounted)).append("\n\n");

        // Backends & mappings — which native layer owns what, and what the mapping load actually did.
        sb.append("-- Backends & Mappings --\n");
        sb.append("SDL3 available: ").append(dev.steampad.input.sdl.Sdl3GamepadProvider.isAvailable())
          .append(" (version ").append(dev.steampad.input.sdl.Sdl3GamepadProvider.sdlVersionString()).append(")\n");
        sb.append("Mapping load: ").append(dev.steampad.input.GamepadMappings.lastLoadSummary()).append("\n");
        sb.append("Raw poll counts: SDL3=").append(ControllerManager.lastRawSdl3Count())
          .append(" GLFW=").append(ControllerManager.lastRawGlfwCount()).append(" (before filtering/merge)\n");
        for (String line : dev.steampad.input.sdl.Sdl3GamepadProvider.describeOpenPads()) {
            sb.append("SDL3 pad: ").append(line).append("\n");
        }
        sb.append("\n");

        // Connected controllers — each with its backend tag decoded from the handle bits (the decode
        // that identified D097's silent GLFW fallback; now every dump carries it automatically).
        sb.append("-- Controllers --\n");
        sb.append("Input Source: ").append(ControllerManager.activeSource()).append("\n");
        List<SteamControllerHandleRef> controllers = ControllerManager.getConnectedControllers();
        sb.append("Connected: ").append(controllers.size()).append("\n");
        boolean anySteamVirtual = false;
        boolean anyRealPad = false;
        for (SteamControllerHandleRef ref : controllers) {
            sb.append("  [").append(ref.handle).append(" @").append(ControllerManager.backendTagOf(ref.handle))
              .append("] ").append(ref.displayName)
              .append(" (").append(ref.type).append(")\n");
            String lower = ref.displayName == null ? "" : ref.displayName.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("steam virtual gamepad") || lower.contains("steam deck controller")) {
                anySteamVirtual = true;
            } else {
                anyRealPad = true;
            }
        }
        if (anySteamVirtual && anyRealPad) {
            sb.append("*** WARNING: a raw physical pad AND a Steam Virtual Gamepad are BOTH visible. ")
              .append("Steam Input is claiming at least one controller in this session — if it claims ")
              .append("the same pad SteamPad reads raw, its desktop layout can double-translate that ")
              .append("pad into mouse/keyboard input (double input; see the Input Flow section below ")
              .append("for live evidence). Fix at the source: Steam -> Settings -> Controller -> ")
              .append("disable Steam Input for the affected controller. ***\n");
        }

        // Active controller
        sb.append("\n-- Active Controller in This Instance --\n");
        long active = ActiveControllerService.getActiveHandle();
        if (active == 0L) {
            sb.append("None selected.\n");
        } else {
            sb.append("Handle: ").append(active).append("\n");
            controllers.stream()
                    .filter(r -> r.handle == active)
                    .findFirst()
                    .ifPresent(r -> sb.append("Name: ").append(r.displayName).append("\n"));
        }

        // Suppressed controllers
        sb.append("\n-- Suppressed Controllers (active but not selected) --\n");
        for (SteamControllerHandleRef ref : controllers) {
            if (ref.handle != active) {
                sb.append("  [SUPPRESSED] ").append(ref.displayName).append(" (").append(ref.handle).append(")\n");
            }
        }

        // Input flow — the live mouse/gamepad arbitration state and event counters. THIS is the
        // section that answers "el mouse virtual laggea" reports with data instead of guesses
        // (D098): if external mouse motion arrives while the stick steers the cursor, the counters
        // and the DOUBLE INPUT banner below say so directly.
        sb.append("\n-- Input Flow (mouse/gamepad arbitration) --\n");
        sb.append("Active device: ").append(dev.steampad.input.InputRouter.active())
          .append(" (last mouse move: ").append(fmtAge(dev.steampad.input.InputRouter.msSinceLastMouse()))
          .append(", last gamepad input: ").append(fmtAge(dev.steampad.input.InputRouter.msSinceLastGamepad()))
          .append(")\n");
        sb.append("Virtual cursor: mode=").append(dev.steampad.input.VirtualMouseController.getMode())
          .append(" shown=").append(dev.steampad.input.VirtualMouseController.isShown())
          .append(" pos=(").append((int) dev.steampad.input.VirtualMouseController.getX())
          .append(",").append((int) dev.steampad.input.VirtualMouseController.getY())
          .append(") vel=(").append((int) dev.steampad.input.VirtualMouseController.debugVelX())
          .append(",").append((int) dev.steampad.input.VirtualMouseController.debugVelY())
          .append(" px/s) steering=").append(dev.steampad.input.VirtualMouseController.isMovingByStick())
          .append(" sensitivity=").append(dev.steampad.input.VirtualMouseController.debugSensitivity())
          .append("\n");
        sb.append(dev.steampad.input.MouseEventStats.dumpSummary()).append("\n");

        // Performance — SteamPad's own tick/frame cost (always captured, not only when over budget)
        // plus the free camera's own timer when it has run.
        sb.append("\n-- Performance --\n");
        sb.append("Tick profile ").append(dev.steampad.util.TickProfiler.lastWindowSummary()).append("\n");
        sb.append("Free camera computeFreePose: ")
          .append(dev.steampad.input.ThirdPersonCameraController.lastPerfSummary()).append("\n");

        // Active controller config — the values that shape how input FEELS, so a "se siente raro"
        // report carries the numbers it was felt under.
        if (active != 0L) {
            ControllerConfig acfg = ConfigManager.getControllerConfig(active);
            sb.append("\n-- Active Controller Config --\n");
            sb.append("Sensitivity: H=").append(acfg.horizontalSensitivity)
              .append(" V=").append(acfg.verticalSensitivity)
              .append(" vmouse=").append(acfg.virtualMouseSensitivity)
              .append(" lookCurve=").append(acfg.lookCurve)
              .append(" turnBoost=").append(acfg.lookTurnBoost).append("\n");
            sb.append("Deadzones: left=").append(acfg.leftStickDeadzone)
              .append(" right=").append(acfg.rightStickDeadzone)
              .append(" triggerThreshold=").append(acfg.buttonActivationThreshold).append("\n");
            sb.append("Aim assist: ").append(acfg.aimAssistEnabled)
              .append(" (strength ").append(acfg.aimAssistStrength).append(")")
              .append("  gyro: ").append(acfg.gyroEnabled)
              .append("  mixedInput: ").append(acfg.mixedInput)
              .append("  attackAutoRepeat: ").append(acfg.attackAutoRepeat).append("\n");
            sb.append("Bind overrides: ").append(acfg.buttonBindings.size())
              .append("  chords: ").append(acfg.chordBindings.size())
              .append("  extra binds: ").append(acfg.extraBinds.size()).append("\n");
        }

        // Global feature toggles — which of the mod's systems are even ON right now.
        var gtoggle = ConfigManager.getGlobal();
        sb.append("\n-- Global Feature State --\n");
        sb.append("Backends: useSdl3=").append(gtoggle.useSdl3Fallback)
          .append(" useGlfw=").append(gtoggle.useGlfwFallback)
          .append(" steamAttachMode=").append(gtoggle.steamAttachMode).append("\n");
        sb.append("Third person: shoulderOffset=").append(gtoggle.thirdPersonCameraEnabled)
          .append(" freeLook(saved)=").append(gtoggle.thirdPersonFreeLookEnabled)
          .append(" freeLook(active now)=").append(dev.steampad.input.ThirdPersonCameraController.isFreeLookEnabled())
          .append(" forcedByEmote=").append(dev.steampad.emote.EmoteAnimator.isLocalRealEmotePlaying())
          .append(" crosshair=").append(gtoggle.thirdPersonCrosshairEnabled)
          .append(" cameraRelativeMovement=").append(gtoggle.thirdPersonCameraRelativeMovement).append("\n");
        sb.append("Virtual mouse snap: ").append(gtoggle.virtualMouseSnapEnabled)
          .append("  windowArrange: ").append(gtoggle.windowArrangeEnabled)
          .append("  onboardingSeen: ").append(gtoggle.hasSeenOnboarding).append("\n");
        sb.append("Preferred controller: '").append(gtoggle.preferredControllerName).append("'\n");

        // Emotes — library + playback state.
        sb.append("\n-- Emotes --\n");
        try {
            sb.append("Library: ").append(dev.steampad.emote.EmoteLibrary.all().size()).append(" emotes loaded\n");
        } catch (Throwable t) {
            sb.append("Library: unavailable (").append(t.getMessage()).append(")\n");
        }
        sb.append("Active playbacks: ").append(dev.steampad.emote.EmoteAnimator.activePlaybackCount())
          .append("  local playing: ").append(dev.steampad.emote.EmoteAnimator.isLocalPlaying())
          .append("  (real emote: ").append(dev.steampad.emote.EmoteAnimator.isLocalRealEmotePlaying()).append(")\n");
        MinecraftClient emoteMc = MinecraftClient.getInstance();
        Screen emoteScreen = emoteMc.currentScreen;
        sb.append("Wheel open: ").append(dev.steampad.emote.EmoteWheelController.isOpen())
          .append("  screen: ").append(emoteScreen == null ? "none"
                  : emoteScreen instanceof dev.steampad.screen.EmoteLibraryScreen ? "EmoteLibraryScreen"
                  : emoteScreen instanceof dev.steampad.screen.EmoteWheelScreen ? "EmoteWheelScreen"
                  : "other").append("\n");
        // Per-part/per-axis pose breakdown of the LOCAL player's active playback — same numbers the
        // throttled [emote-pose] log line shows, but always current and included right here so a
        // single Debug Dump carries its own animation evidence (v0.60.0/D100).
        sb.append("Local pose:\n").append(dev.steampad.emote.EmoteAnimator.lastLocalPoseSummary());

        // Player movement — the exact signal EmoteAnimator.clientTick uses to auto-cancel a local
        // emote, so "the animation stopped/looked wrong for no reason" can be checked against real
        // input/velocity instead of guessed (D101: user asked for "hasta lo más mínimo" coverage).
        sb.append("\n-- Player Movement --\n");
        if (emoteMc.player != null) {
            var mv = emoteMc.player.input != null ? emoteMc.player.input.getMovementInput() : null;
            sb.append("Movement input: ").append(mv == null ? "n/a"
                    : String.format(java.util.Locale.ROOT, "(%.2f, %.2f)", mv.x, mv.y))
              .append("  sprinting=").append(emoteMc.player.isSprinting())
              .append("  sneaking=").append(emoteMc.player.isSneaking())
              .append("  onGround=").append(emoteMc.player.isOnGround()).append("\n");
            sb.append("Velocity: ").append(String.format(java.util.Locale.ROOT, "horiz²=%.4f",
                    emoteMc.player.getVelocity().horizontalLengthSquared())).append("\n");
        } else {
            sb.append("No player loaded.\n");
        }

        // Haptics — channel state + the active controller's vibration sliders, so a "this pulse never
        // fires" report can be checked against what's actually occupying the shared rumble channel
        // and what multiplier would apply, instead of guessing (see D072 — the slime investigation
        // this was built for).
        sb.append("\n-- Haptics --\n");
        var occupying = HapticsController.occupyingTier();
        if (occupying != null) {
            sb.append("Channel occupied by: ").append(occupying).append(" (")
              .append(HapticsController.occupiedForMs()).append("ms remaining)\n");
        } else {
            sb.append("Channel free.\n");
        }
        if (active != 0L) {
            ControllerConfig hcfg = ConfigManager.getControllerConfig(active);
            sb.append("Allow Vibration: ").append(hcfg.allowVibration).append("\n");
            sb.append("Master: ").append(hcfg.vibrationMaster)
              .append("  Player: ").append(hcfg.vibrationPlayer)
              .append("  World: ").append(hcfg.vibrationWorld)
              .append("  Interaction: ").append(hcfg.vibrationInteraction)
              .append("  GUI: ").append(hcfg.vibrationGui)
              .append("  GlobalEvent: ").append(hcfg.vibrationGlobalEvent)
              .append("  Misc: ").append(hcfg.vibrationMisc).append("\n");
        } else {
            sb.append("No active controller — sliders not applicable.\n");
        }
        var gcfg = ConfigManager.getGlobal();
        sb.append("Radial select haptics: ").append(gcfg.radialSelectHaptics)
          .append(" (strength ").append(gcfg.radialSelectHapticsIntensity).append(")\n");

        // Screen widgets — what's on the currently open screen and what ExternalWidgetScanner finds
        // there beyond vanilla's children() list, so a "the D-pad can't reach this mod's button"
        // report can be checked directly instead of re-deriving it from scratch each time (D071).
        Screen screen = MinecraftClient.getInstance().currentScreen;
        sb.append("\n-- Screen Widgets --\n");
        if (screen == null) {
            sb.append("No screen open.\n");
        } else {
            sb.append("Screen: ").append(screen.getClass().getName()).append("\n");
            sb.append("children(): ").append(screen.children().size()).append("\n");
            List<ExternalWidgetScanner.Target> extra = ExternalWidgetScanner.discover(screen);
            sb.append("External targets found: ").append(extra.size()).append("\n");
            int shown = 0;
            for (var t : extra) {
                if (shown++ >= 20) { sb.append("  ... (").append(extra.size() - 20).append(" more)\n"); break; }
                sb.append("  [").append(t.widget() != null ? "ClickableWidget" : "duck-typed").append("] ")
                  .append("x=").append((int) t.x()).append(" y=").append((int) t.y())
                  .append(" w=").append((int) t.width()).append(" h=").append((int) t.height()).append("\n");
            }
        }

        sb.append("\n=== End Dump ===\n");
        return sb.toString();
    }

    /** "1234ms ago" / "never" formatter for the arbitration ages. */
    private static String fmtAge(long ms) {
        return ms < 0 ? "never" : ms + "ms ago";
    }
}
