package dev.steampad.screen;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.ControllerConfig;
import dev.steampad.service.ActiveControllerService;
import dev.steampad.service.ControllerManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Advanced per-controller settings: vibration, gyro. Two-column layout (list + description). */
public class ControllerAdvancedSettingsScreen extends ColumnSettingsScreen implements TabbedScreen {

    private final Screen parent;
    private final long handle;
    private ControllerConfig cfg;

    public ControllerAdvancedSettingsScreen(Screen parent, long handle) {
        super(Text.translatable("steampad.screen.advanced.title"));
        this.parent = parent;
        this.handle = handle;
    }

    @Override
    protected int contentTop() { return super.contentTop() + 24; }

    @Override
    protected void init() {
        super.init();
        this.cfg = ConfigManager.getControllerConfig(handle);
        beginLayout();
        SettingsTabs.add(this, SettingsTabs.ADVANCED, parent, handle, listX(), HEADER_H + 8, listW());

        section("steampad.settings.section.vibration");
        toggle("steampad.cset.allow_vibration", cfg.allowVibration, v -> { cfg.allowVibration = v; save(); });
        toggle("steampad.cset.hd_haptics", cfg.hdHaptics, v -> { cfg.hdHaptics = v; save(); });
        vib("steampad.cset.vib_master", cfg.vibrationMaster, v -> { cfg.vibrationMaster = v; save(); });
        vib("steampad.cset.vib_player", cfg.vibrationPlayer, v -> { cfg.vibrationPlayer = v; save(); });
        vib("steampad.cset.vib_world", cfg.vibrationWorld, v -> { cfg.vibrationWorld = v; save(); });
        vib("steampad.cset.vib_interaction", cfg.vibrationInteraction, v -> { cfg.vibrationInteraction = v; save(); });
        vib("steampad.cset.vib_gui", cfg.vibrationGui, v -> { cfg.vibrationGui = v; save(); });
        vib("steampad.cset.vib_global", cfg.vibrationGlobalEvent, v -> { cfg.vibrationGlobalEvent = v; save(); });
        button("steampad.cset.test_vibration", () -> {
            long active = ActiveControllerService.getActiveHandle();
            // Route through ControllerManager so SDL3 pads rumble too (SteamHapticsService is
            // Steam-only and was a silent no-op for fallback controllers — the F6 bug).
            if (active != 0L) ControllerManager.rumble(active, cfg.vibrationMaster * 0.6f, 300);
        });
        button("steampad.cset.haptics_test", () -> client.setScreen(new HapticsTestScreen(this)));

        section("steampad.settings.section.gyro");
        cycling("steampad.cset.gyro_behaviour", ControllerConfig.GyroMode.values(), cfg.gyroBehaviour,
                v -> { cfg.gyroBehaviour = v; save(); });
        cycling("steampad.cset.yaw_mode", ControllerConfig.YawMode.values(), cfg.yawMode,
                v -> { cfg.yawMode = v; save(); });
        cycling("steampad.cset.require_button", ControllerConfig.GyroRequireButton.values(), cfg.gyroRequireButton,
                v -> { cfg.gyroRequireButton = v; save(); });
        toggle("steampad.cset.invert_gyro_x", cfg.gyroInvertX, v -> { cfg.gyroInvertX = v; save(); });
        toggle("steampad.cset.invert_gyro_y", cfg.gyroInvertY, v -> { cfg.gyroInvertY = v; save(); });
        toggle("steampad.cset.flick_stick", cfg.flickStick, v -> { cfg.flickStick = v; save(); });

        section("steampad.settings.section.zoom");
        toggle("steampad.cset.zoom_hold", cfg.zoomHoldMode, v -> { cfg.zoomHoldMode = v; save(); });
        slider("steampad.cset.zoom_fov", cfg.zoomFov, 1f, 60f, "%.0f°",
                v -> { cfg.zoomFov = v; save(); });
        slider("steampad.cset.zoom_step", cfg.zoomStep, 1f, 20f, "%.0f°",
                v -> { cfg.zoomStep = v; save(); });
        toggle("steampad.cset.zoom_smooth", cfg.zoomSmooth, v -> { cfg.zoomSmooth = v; save(); });
        slider("steampad.cset.zoom_smoothing", cfg.zoomSmoothing, 0.05f, 0.30f, "%.2f",
                v -> { cfg.zoomSmoothing = v; save(); });
        toggle("steampad.cset.zoom_auto_sens", cfg.zoomAutoSensitivity,
                v -> { cfg.zoomAutoSensitivity = v; save(); });
        slider("steampad.cset.zoom_sens", cfg.zoomSensitivity, 0.05f, 1f, "%.2f",
                v -> { cfg.zoomSensitivity = v; save(); });
        toggle("steampad.cset.zoom_dpad", cfg.zoomDpadAdjust, v -> { cfg.zoomDpadAdjust = v; save(); });
        toggle("steampad.cset.zoom_reset", cfg.zoomResetOnRelease,
                v -> { cfg.zoomResetOnRelease = v; save(); });
        toggle("steampad.cset.zoom_marker", cfg.zoomMarkerEnabled,
                v -> { cfg.zoomMarkerEnabled = v; save(); });
        cycling("steampad.cset.zoom_marker_style", ControllerConfig.ZoomMarkerStyle.values(),
                cfg.zoomMarkerStyle == null ? ControllerConfig.ZoomMarkerStyle.COLUMN : cfg.zoomMarkerStyle,
                v -> { cfg.zoomMarkerStyle = v; save(); });
        cycling("steampad.cset.zoom_marker_color", ControllerConfig.ZoomMarkerColor.values(),
                cfg.zoomMarkerColor == null ? ControllerConfig.ZoomMarkerColor.CYAN : cfg.zoomMarkerColor,
                v -> { cfg.zoomMarkerColor = v; save(); });
        slider("steampad.cset.zoom_marker_secs", cfg.zoomMarkerSeconds, 2f, 15f, "%.0f s",
                v -> { cfg.zoomMarkerSeconds = v; save(); });
        toggle("steampad.cset.zoom_marker_chat", cfg.zoomMarkerShareChat,
                v -> { cfg.zoomMarkerShareChat = v; save(); });
        toggle("steampad.cset.zoom_bobbing", cfg.zoomDisableBobbing,
                v -> { cfg.zoomDisableBobbing = v; save(); });
        slider("steampad.cset.zoom_render_distance_boost", cfg.zoomRenderDistanceBoost, 0f, 8f, "%.0f chunks",
                v -> { cfg.zoomRenderDistanceBoost = Math.round(v); save(); });
        toggle("steampad.cset.zoom_cinematic_bars", cfg.zoomCinematicBars,
                v -> { cfg.zoomCinematicBars = v; save(); });
        slider("steampad.cset.zoom_cinematic_bars_height", cfg.zoomCinematicBarsHeightPct, 5f, 20f, "%.0f%%",
                v -> { cfg.zoomCinematicBarsHeightPct = v; save(); });

        section("steampad.settings.section.advanced");
        toggle("steampad.cset.mixed_input", cfg.mixedInput, v -> { cfg.mixedInput = v; save(); });

        finishLayout();

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, btn -> close())
                .dimensions(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());
    }

    /** Vibration slider, range 0..2 (0%–200%). */
    private void vib(String key, float initial, java.util.function.Consumer<Float> onChange) {
        slider(key, initial, 0f, 2f, "%.1f", onChange);
    }

    @Override public void steampad$nextTab() { SettingsTabs.cycle(SettingsTabs.ADVANCED, +1, parent, handle); }
    @Override public void steampad$prevTab() { SettingsTabs.cycle(SettingsTabs.ADVANCED, -1, parent, handle); }

    private void save() { ConfigManager.saveControllerConfig(handle); }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        renderColumns(ctx, mouseX, mouseY);
        SettingsTabs.renderGlyphs(ctx, textRenderer, listX(), HEADER_H + 8, listW());
    }

    @Override
    public void close() { client.setScreen(parent); }
}
