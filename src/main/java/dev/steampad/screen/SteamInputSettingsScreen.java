package dev.steampad.screen;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.GlobalConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Steam Input hub: the attach-mode switch (previously config-file-only, see D074), applying changes
 * immediately (D076). The slot layers live in Botones' Steam Input section, next to the slots.
 */
public class SteamInputSettingsScreen extends ColumnSettingsScreen {

    private final Screen parent;
    private GlobalConfig cfg;

    public SteamInputSettingsScreen(Screen parent) {
        super(Component.translatable("steampad.screen.steam_input.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.cfg = ConfigManager.getGlobal();
        beginLayout();

        // The slot LAYERS moved to Botones' Steam Input section, right next to the 10 slot rows they
        // modify (feedback: "las Capas de Ranura en los botones en la seccion que actualmente tenemos
        // de Steaminput") — this screen keeps only the attach policy.
        section("steampad.settings.section.steam_input_attach");
        cycling("steampad.cset.steam_attach_mode", GlobalConfig.SteamAttachMode.values(), cfg.steamAttachMode,
                v -> { cfg.steamAttachMode = v; save(); applyAttachModeNow(v); });
        // EXPERIMENTAL (B076): deploying the IGA manifest makes Steam treat the shortcut as a native
        // Steam Input game and STOP emitting the virtual gamepad in Game Mode — only for users who
        // bind EVERYTHING in Steam's configurator. Turning it OFF cleans up our manifests right away
        // (effective after one Steam restart).
        toggle("steampad.cset.deploy_iga_manifest", cfg.deployIgaManifest, v -> {
            cfg.deployIgaManifest = v;
            save();
            if (v) {
                // Deploy RIGHT NOW. Previously only the OFF path acted immediately (it cleaned up),
                // while turning it ON did nothing until the next game start — with no feedback either
                // way, which reads as "the toggle is broken". Both directions are now symmetric.
                dev.steampad.steam.SteamBootstrap.deployManifestNow();
            } else {
                dev.steampad.steam.SteamControllerConfigDeployer.cleanupOurManifests();
            }
        });

        finishLayout();

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, btn -> onClose())
                .bounds(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());
    }

    private void save() { ConfigManager.saveGlobal(); }

    /**
     * Makes an attach-mode change take effect THIS session instead of silently waiting for a restart —
     * the exact trap the user fell into (real Game Mode log: mode read once at startup as NEVER, the
     * in-game change to SIEMPRE did nothing, and nothing said so). Switching to ALWAYS/AUTO re-runs
     * {@link dev.steampad.steam.SteamBootstrap#init()} (already retry-safe — the Retry button in
     * Controller Select calls it the same way); switching to NEVER while attached runs the existing
     * {@link dev.steampad.steam.SteamBootstrap#shutdown()}. Both paths log their outcome.
     */
    private void applyAttachModeNow(GlobalConfig.SteamAttachMode mode) {
        boolean attached = dev.steampad.steam.SteamBootstrap.isSteamAvailable();
        if (mode == GlobalConfig.SteamAttachMode.NEVER) {
            if (attached) {
                dev.steampad.util.LogUtil.info("[SteamPad] Attach mode set to NEVER — detaching from Steam now.");
                dev.steampad.steam.SteamBootstrap.shutdown();
            }
            return;
        }
        if (!attached) {
            dev.steampad.util.LogUtil.info("[SteamPad] Attach mode set to {} — attempting Steam attach now "
                    + "(no restart needed).", mode);
            boolean ok = dev.steampad.steam.SteamBootstrap.init();
            dev.steampad.util.LogUtil.info("[SteamPad] Immediate attach result: {}", ok ? "OK" : "failed — see log above");
        }
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        renderColumns(ctx, mouseX, mouseY);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
