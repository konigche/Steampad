package dev.steampad.screen;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.GlobalConfig;
import dev.steampad.service.ClipboardDebugService;
import dev.steampad.service.UiSoundService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/**
 * Global SteamPad settings — two-column layout (option list + right description panel) matching the
 * Buttons screen, with the rest of the screens.
 */
public class GlobalSettingsScreen extends ColumnSettingsScreen {

    private final Screen parent;
    private GlobalConfig cfg;

    public GlobalSettingsScreen(Screen parent) {
        super(Text.translatable("steampad.screen.global_settings.title"));
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
        button("steampad.settings.steam_input_config", () -> client.setScreen(new SteamInputSettingsScreen(this)));

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

        section("steampad.settings.section.keyboard");
        button("steampad.settings.keyboard_config", () -> client.setScreen(new KeyboardSettingsScreen(this)));

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
                () -> client.setScreen(new BindingsScreen(this,
                        dev.steampad.service.ActiveControllerService.getActiveHandle())));

        section("steampad.settings.section.third_person_free_look");
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
                () -> client.setScreen(new ProfilesScreen(this,
                        dev.steampad.service.ActiveControllerService.getActiveHandle())));
        button("steampad.settings.copy_debug", ClipboardDebugService::copyToClipboard);

        finishLayout();

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, btn -> close())
                .dimensions(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());
    }

    private void addCurrentServerToWhitelist() {
        if (client != null && client.getCurrentServerEntry() != null) {
            String addr = client.getCurrentServerEntry().address;
            if (!cfg.keyboardMovementWhitelist.contains(addr)) {
                cfg.keyboardMovementWhitelist.add(addr);
                save();
            }
        }
        UiSoundService.playSelect();
    }

    private void save() { ConfigManager.saveGlobal(); }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        renderColumns(ctx, mouseX, mouseY);
    }

    @Override
    public void close() { client.setScreen(parent); }
}
