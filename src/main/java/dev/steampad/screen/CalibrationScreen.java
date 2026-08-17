package dev.steampad.screen;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.ControllerConfig;
import dev.steampad.input.DeadzoneProcessor;
import dev.steampad.input.GamepadSnapshot;
import dev.steampad.service.ControllerManager;
import dev.steampad.service.UiSoundService;
import dev.steampad.steam.SteamInputManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Live calibration screen showing stick positions in real time.
 * Allows fine-tuning of deadzone values with visual feedback.
 */
public class CalibrationScreen extends SteamPadBaseScreen {

    private final Screen parent;
    private final long handle;
    private ControllerConfig cfg;

    private static final int VIS_SIZE = 80;

    private final GamepadSnapshot snapshot = new GamepadSnapshot();

    public CalibrationScreen(Screen parent, long handle) {
        super(Component.translatable("steampad.screen.calibration.title"));
        this.parent = parent;
        this.handle = handle;
    }

    @Override
    protected void init() {
        super.init();
        this.cfg = ConfigManager.getControllerConfig(handle);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> {
            UiSoundService.playSelect();
            ConfigManager.saveControllerConfig(handle);
            onClose();
        }).bounds(this.width / 2 - 75, this.height - 30, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderChrome(context);

        // Live stick values — from Steam Input if active, otherwise the GLFW/SDL3 fallback snapshot.
        float[] left = {0f, 0f};
        float[] right = {0f, 0f};
        var steamState = SteamInputManager.getState(handle);
        if (steamState.isPresent()) {
            left = steamState.get().leftStick;
            right = steamState.get().rightStick;
        } else if (ControllerManager.readSnapshot(handle, snapshot)) {
            left = new float[]{snapshot.axis(GamepadSnapshot.AXIS_LEFT_X), snapshot.axis(GamepadSnapshot.AXIS_LEFT_Y)};
            right = new float[]{snapshot.axis(GamepadSnapshot.AXIS_RIGHT_X), snapshot.axis(GamepadSnapshot.AXIS_RIGHT_Y)};
        }

        int lx = this.width / 2 - VIS_SIZE - 20;
        int ly = this.height / 2 - VIS_SIZE / 2;
        renderStickVis(context, lx, ly, left[0], left[1], cfg.leftStickDeadzone,
                "Left Stick (dz=" + String.format("%.2f", cfg.leftStickDeadzone) + ")");

        int rx = this.width / 2 + 20;
        int ry = this.height / 2 - VIS_SIZE / 2;
        renderStickVis(context, rx, ry, right[0], right[1], cfg.rightStickDeadzone,
                "Right Stick (dz=" + String.format("%.2f", cfg.rightStickDeadzone) + ")");

        context.drawCenteredString(font,
            Component.translatable("steampad.calibration.hint"),
            this.width / 2, contentBottom() - 4, TEXT_MUTED);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderStickVis(GuiGraphics ctx, int bx, int by, float rawX, float rawY,
                                 float deadzone, String label) {
        // Border
        ctx.fill(bx, by, bx + VIS_SIZE, by + VIS_SIZE, 0xFF333333);
        ctx.fill(bx + 1, by + 1, bx + VIS_SIZE - 1, by + VIS_SIZE - 1, 0xFF111111);

        // Deadzone circle approximation (square for simplicity)
        int dz = (int)(deadzone * VIS_SIZE / 2);
        int cx = bx + VIS_SIZE / 2;
        int cy = by + VIS_SIZE / 2;
        ctx.fill(cx - dz, cy - dz, cx + dz, cy + dz, 0x44FF0000);

        // Raw dot (red)
        int rawPx = (int)(bx + VIS_SIZE / 2 + rawX * VIS_SIZE / 2);
        int rawPy = (int)(by + VIS_SIZE / 2 + rawY * VIS_SIZE / 2);
        ctx.fill(rawPx - 2, rawPy - 2, rawPx + 2, rawPy + 2, 0xFFFF4444);

        // Processed dot (green)
        float[] proc = DeadzoneProcessor.process(rawX, rawY, deadzone);
        int procPx = (int)(bx + VIS_SIZE / 2 + proc[0] * VIS_SIZE / 2);
        int procPy = (int)(by + VIS_SIZE / 2 + proc[1] * VIS_SIZE / 2);
        ctx.fill(procPx - 2, procPy - 2, procPx + 2, procPy + 2, 0xFF44FF44);

        ctx.drawString(font, Component.literal(label), bx, by - 12, 0xFFAAAAAA, true);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
