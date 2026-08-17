package dev.steampad.screen;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.GlobalConfig;
import dev.steampad.service.ClipboardDebugService;
import dev.steampad.service.UiSoundService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Global SteamPad settings — two-column layout (option list + right description panel) matching the
 * Buttons screen, with the rest of the screens.
 */
public class GlobalSettingsScreen extends ColumnSettingsScreen {

    private final Screen parent;
    private GlobalConfig cfg;

    public GlobalSettingsScreen(Screen parent) {
        super(Component.translatable("steampad.screen.global_settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.cfg = ConfigManager.getGlobal();
        beginLayout();

        section("steampad.settings.section.backends");
        toggle("steampad.settings.use_sdl3", cfg.useSdl3Fallback, v -> { cfg.useSdl3Fallback = v; save(); });
        toggle("steampad.settings.use_glfw", cfg.useGlfwFallback, v -> { cfg.useGlfwFallback = v; save(); });
        toggle("steampad.settings.load_natives", cfg.loadNatives, v -> { cfg.loadNatives = v; save(); });

        // Own section right below Input Backends (feedback: "ajustes de entrada debe estar debajo de
        // backends de entrada") — the slot LAYERS moved to Botones' Steam Input section, next to the
        // slots themselves; this screen keeps the attach mode.
        section("steampad.settings.section.steam_input");
        button("steampad.settings.steam_input_config", () -> minecraft.setScreen(new SteamInputSettingsScreen(this)));

        section("steampad.settings.section.server");
        cycling("steampad.settings.block_reach_around", GlobalConfig.BlockReachAround.values(),
                cfg.blockReachAround, v -> { cfg.blockReachAround = v; save(); });
        toggle("steampad.settings.allow_server_vibration", cfg.allowServerVibration,
                v -> { cfg.allowServerVibration = v; save(); });
        toggle("steampad.settings.keyboard_movement", cfg.keyboardLikeMovement,
                v -> { cfg.keyboardLikeMovement = v; save(); });
        button("steampad.settings.add_server_whitelist", this::addCurrentServerToWhitelist);

        section("steampad.settings.section.interface");
        toggle("steampad.settings.ui_sounds", cfg.uiSounds, v -> { cfg.uiSounds = v; save(); });
        toggle("steampad.settings.notify_low_battery", cfg.notifyLowBattery,
                v -> { cfg.notifyLowBattery = v; save(); });
        toggle("steampad.settings.out_of_focus_input", cfg.outOfFocusInput,
                v -> { cfg.outOfFocusInput = v; save(); });
        // Manual replay (feedback: "podemos poner un boton para que salga de forma manual ademas de la
        // primera vez"): the automatic popup is shown ONCE EVER (SteamPadClient, gated on
        // GlobalConfig.hasSeenOnboarding — already true for any long-running save, which is exactly why
        // it "stopped firing"), so this is the only way back to it afterwards. Doesn't touch
        // hasSeenOnboarding either way — reopening the tour manually shouldn't reset or re-arm anything.
        button("steampad.settings.replay_onboarding", () -> {
            long active = dev.steampad.service.ActiveControllerService.getActiveHandle();
            minecraft.setScreen(new OnboardingScreen(this, active));
        });

        section("steampad.settings.section.keyboard");
        button("steampad.settings.keyboard_config", () -> minecraft.setScreen(new KeyboardSettingsScreen(this)));

        section("steampad.settings.section.hud");
        cycling("steampad.cset.ingame_guide_detail", GlobalConfig.ButtonGuideDetail.values(),
                cfg.ingameButtonGuideDetail == null ? GlobalConfig.ButtonGuideDetail.NORMAL : cfg.ingameButtonGuideDetail,
                v -> { cfg.ingameButtonGuideDetail = v; save(); });
        slider("steampad.settings.button_guide_scale", cfg.ingameButtonGuideScale * 100f, 50f, 200f, "%.0f%%",
                v -> { cfg.ingameButtonGuideScale = v / 100f; save(); });
        toggle("steampad.settings.radial_select_haptics", cfg.radialSelectHaptics,
                v -> { cfg.radialSelectHaptics = v; save(); });
        slider("steampad.settings.radial_select_haptics_strength", cfg.radialSelectHapticsIntensity * 100f, 10f, 150f, "%.0f%%",
                v -> { cfg.radialSelectHapticsIntensity = v / 100f; save(); });

        section("steampad.settings.section.third_person");
        toggle("steampad.settings.third_person_enabled", cfg.thirdPersonCameraEnabled,
                v -> { cfg.thirdPersonCameraEnabled = v; save(); });
        cycling("steampad.settings.third_person_side", GlobalConfig.ThirdPersonCameraSide.values(),
                cfg.thirdPersonCameraSide == null ? GlobalConfig.ThirdPersonCameraSide.RIGHT : cfg.thirdPersonCameraSide,
                v -> { cfg.thirdPersonCameraSide = v; save(); });
        slider("steampad.settings.third_person_offset", cfg.thirdPersonCameraOffset * 100f, 20f, 150f, "%.0f%%",
                v -> { cfg.thirdPersonCameraOffset = v / 100f; save(); });
        toggle("steampad.settings.third_person_aiming", cfg.thirdPersonAimingProfile,
                v -> { cfg.thirdPersonAimingProfile = v; save(); });
        slider("steampad.settings.third_person_aiming_offset", cfg.thirdPersonAimingOffset * 100f, 10f, 100f, "%.0f%%",
                v -> { cfg.thirdPersonAimingOffset = v / 100f; save(); });
        // The quick-cycle bind (THIRD_PERSON_SIDE_CYCLE) lives in Botones -> Jugabilidad like every
        // other bind, unbound by default like OPEN_KEYBOARD/etc — but feedback this round was simply
        // "no me fije donde esta" for this specific one, since nothing here points at it. A direct
        // shortcut from the feature's own section is cheap and doesn't require restructuring Botones.
        button("steampad.settings.third_person_assign_bind",
                () -> minecraft.setScreen(new BindingsScreen(this,
                        dev.steampad.service.ActiveControllerService.getActiveHandle())));
        toggle("steampad.settings.third_person_auto_switch_mount", cfg.thirdPersonAutoSwitchOnMount,
                v -> { cfg.thirdPersonAutoSwitchOnMount = v; save(); });
        // Sits in this section because that is where a player looks for "third person happens
        // automatically when X" — even though it drives ANOTHER mod's option, not SteamPad's camera.
        // Shown unconditionally (the row is a no-op without Gliders, and hiding it would need a
        // classload probe just to draw a screen).
        toggle("steampad.settings.gliders_force_perspective", cfg.glidersForcePerspective,
                v -> { cfg.glidersForcePerspective = v; save(); });
        slider("steampad.settings.third_person_mounted_pitch", cfg.thirdPersonMountedCameraPitch, 0f, 50f, "%.0f°",
                v -> { cfg.thirdPersonMountedCameraPitch = v; save(); });
        slider("steampad.settings.third_person_mounted_distance", cfg.thirdPersonMountedCameraDistance * 100f,
                150f, 800f, "%.0f cm",
                v -> { cfg.thirdPersonMountedCameraDistance = v / 100f; save(); });

        // D124: the free-look rows live in the SAME "Cámara en tercera persona" section now (round-4
        // feedback: "todos estos ajustes deben corresponder solo a Camara en tercera persona") — the
        // separate "Cámara libre en tercera persona" header is gone, and the master toggle above
        // gates every row below it (see ThirdPersonCameraController.isFreeLookEnabled).
        toggle("steampad.settings.third_person_free_look_enabled", cfg.thirdPersonFreeLookEnabled,
                v -> { cfg.thirdPersonFreeLookEnabled = v; save(); });
        cycling("steampad.settings.third_person_rotate_mode", GlobalConfig.ThirdPersonRotateMode.values(),
                cfg.thirdPersonRotateMode == null ? GlobalConfig.ThirdPersonRotateMode.INTEREST_POINT : cfg.thirdPersonRotateMode,
                v -> { cfg.thirdPersonRotateMode = v; save(); });
        toggle("steampad.settings.third_person_pitch_lock", cfg.thirdPersonPitchLock,
                v -> { cfg.thirdPersonPitchLock = v; save(); });
        slider("steampad.settings.third_person_free_distance", cfg.thirdPersonFreeDistance * 100f, 30f, 600f, "%.0fcm",
                v -> { cfg.thirdPersonFreeDistance = v / 100f; save(); });
        toggle("steampad.settings.third_person_crosshair_enabled", cfg.thirdPersonCrosshairEnabled,
                v -> { cfg.thirdPersonCrosshairEnabled = v; save(); });
        toggle("steampad.settings.third_person_predictive_aim", cfg.thirdPersonPredictiveAim,
                v -> { cfg.thirdPersonPredictiveAim = v; save(); });
        toggle("steampad.settings.third_person_camera_relative_movement", cfg.thirdPersonCameraRelativeMovement,
                v -> { cfg.thirdPersonCameraRelativeMovement = v; save(); });

        section("steampad.settings.section.splitscreen");
        toggle("steampad.settings.window_arrange_enabled", cfg.windowArrangeEnabled,
                v -> { cfg.windowArrangeEnabled = v; save(); dev.steampad.client.window.WindowArrangeController.setEnabled(v); });
        slider("steampad.settings.window_arrange_gap", cfg.windowArrangeGap, 0f, 16f, "%.0f px",
                v -> { cfg.windowArrangeGap = Math.round(v); save(); });

        section("steampad.settings.section.advanced");
        toggle("steampad.settings.enhanced_deck_driver", cfg.useEnhancedSteamDeckDriver,
                v -> { cfg.useEnhancedSteamDeckDriver = v; save(); });
        button("steampad.settings.manage_profiles",
                () -> minecraft.setScreen(new ProfilesScreen(this,
                        dev.steampad.service.ActiveControllerService.getActiveHandle())));
        button("steampad.settings.copy_debug", ClipboardDebugService::copyToClipboard);

        finishLayout();

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, btn -> onClose())
                .bounds(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());
    }

    private void addCurrentServerToWhitelist() {
        if (minecraft != null && minecraft.getCurrentServer() != null) {
            String addr = minecraft.getCurrentServer().ip;
            if (!cfg.keyboardMovementWhitelist.contains(addr)) {
                cfg.keyboardMovementWhitelist.add(addr);
                save();
            }
        }
        UiSoundService.playSelect();
    }

    private void save() { ConfigManager.saveGlobal(); }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        renderColumns(ctx, mouseX, mouseY);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
