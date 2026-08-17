package dev.steampad.screen;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.ControllerConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Basic per-controller settings: sensitivity, movement, deadzones. Two-column layout (list + description). */
public class ControllerBasicSettingsScreen extends ColumnSettingsScreen implements TabbedScreen {

    private final Screen parent;
    private final long handle;
    private ControllerConfig cfg;

    public ControllerBasicSettingsScreen(Screen parent, long handle) {
        super(Component.translatable("steampad.screen.basic.title"));
        this.parent = parent;
        this.handle = handle;
    }

    /** Reserve a row under the header for the Basic/Advanced tab switcher. */
    @Override
    protected int contentTop() { return super.contentTop() + 24; }

    @Override
    protected void init() {
        super.init();
        this.cfg = ConfigManager.getControllerConfig(handle);
        beginLayout();
        SettingsTabs.add(this, SettingsTabs.BASIC, parent, handle, listX(), HEADER_H + 8, listW());

        section("steampad.settings.section.sensitivity");
        slider("steampad.cset.h_sensitivity", cfg.horizontalSensitivity, 0.1f, 5f, "%.2f",
                v -> { cfg.horizontalSensitivity = v; save(); });
        slider("steampad.cset.v_sensitivity", cfg.verticalSensitivity, 0.1f, 5f, "%.2f",
                v -> { cfg.verticalSensitivity = v; save(); });
        slider("steampad.cset.vmouse_sensitivity", cfg.virtualMouseSensitivity, 0.2f, 3f, "%.2f",
                v -> { cfg.virtualMouseSensitivity = v; save(); });
        toggle("steampad.cset.invert_look_y", cfg.invertLookY, v -> { cfg.invertLookY = v; save(); });
        toggle("steampad.cset.reduce_aim", cfg.reduceAimingSensitivity,
                v -> { cfg.reduceAimingSensitivity = v; save(); });
        slider("steampad.cset.look_curve", cfg.lookCurve, 1.0f, 3.0f, "%.1f",
                v -> { cfg.lookCurve = v; save(); });
        toggle("steampad.cset.turn_boost", cfg.lookTurnBoost, v -> { cfg.lookTurnBoost = v; save(); });

        section("steampad.settings.section.aim_assist");
        toggle("steampad.cset.aim_assist", cfg.aimAssistEnabled, v -> { cfg.aimAssistEnabled = v; save(); });
        slider("steampad.cset.aim_assist_strength", cfg.aimAssistStrength * 100f, 0f, 100f, "%.0f%%",
                v -> { cfg.aimAssistStrength = v / 100f; save(); });

        section("steampad.settings.section.movement");
        toggle("steampad.cset.attack_repeat", cfg.attackAutoRepeat,
                v -> { cfg.attackAutoRepeat = v; save(); });
        cycling("steampad.cset.sneak_mode", ControllerConfig.SneakMode.values(), cfg.sneakMode,
                v -> { cfg.sneakMode = v; save(); });
        cycling("steampad.cset.sprint_mode", ControllerConfig.SprintMode.values(), cfg.sprintMode,
                v -> { cfg.sprintMode = v; save(); });
        cycling("steampad.cset.hotbar_radial_mode", ControllerConfig.HotbarRadialMode.values(),
                cfg.hotbarRadialMode, v -> { cfg.hotbarRadialMode = v; save(); });
        toggle("steampad.cset.auto_jump", cfg.autoJump, v -> {
            cfg.autoJump = v;
            // Apply to the real game option — vanilla owns the auto-jump behaviour (the toggle
            // previously saved the value but never took effect).
            if (minecraft != null) minecraft.options.autoJump().set(v);
            save();
        });
        toggle("steampad.cset.no_fly_drifting", cfg.noFlyDrifting, v -> { cfg.noFlyDrifting = v; save(); });
        slider("steampad.cset.mounted_steering_deadzone", cfg.mountedSteeringDeadzone, 0f, 0.6f, "%.2f",
                v -> { cfg.mountedSteeringDeadzone = v; save(); });
        slider("steampad.cset.mounted_steering_smoothing", cfg.mountedSteeringSmoothing, 0f, 0.30f, "%.2fs",
                v -> { cfg.mountedSteeringSmoothing = v; save(); });

        section("steampad.settings.section.deadzones");
        slider("steampad.cset.left_dz", cfg.leftStickDeadzone, 0f, 0.9f, "%.2f",
                v -> { cfg.leftStickDeadzone = v; save(); });
        slider("steampad.cset.right_dz", cfg.rightStickDeadzone, 0f, 0.9f, "%.2f",
                v -> { cfg.rightStickDeadzone = v; save(); });
        slider("steampad.cset.button_threshold", cfg.buttonActivationThreshold, 0f, 1f, "%.2f",
                v -> { cfg.buttonActivationThreshold = v; save(); });

        section("steampad.settings.section.configure");
        button("steampad.cset.calibration", () -> minecraft.setScreen(new CalibrationScreen(this, handle)));

        finishLayout();

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, btn -> onClose())
                .bounds(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());
    }

    @Override public void steampad$nextTab() { SettingsTabs.cycle(SettingsTabs.BASIC, +1, parent, handle); }
    @Override public void steampad$prevTab() { SettingsTabs.cycle(SettingsTabs.BASIC, -1, parent, handle); }

    private void save() { ConfigManager.saveControllerConfig(handle); }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        renderColumns(ctx, mouseX, mouseY);
        SettingsTabs.renderGlyphs(ctx, font, listX(), HEADER_H + 8, listW());
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
