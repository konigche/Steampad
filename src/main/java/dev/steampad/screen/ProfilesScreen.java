package dev.steampad.screen;

import dev.steampad.config.ConfigManager;
import dev.steampad.service.UiSoundService;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Save/load named bundles of the active controller's config (button bindings, chords, radial layout,
 * sensitivity, aim assist, haptics — everything in {@code ControllerConfig}/{@code BindingConfig}/
 * {@code RadialConfig}) — e.g. an "Exploración" profile (low sensitivity, no aim assist) and a
 * "Combate" profile (aim assist strong, tighter camera), switchable with one button instead of
 * re-tuning every slider each time. See {@link ConfigManager}'s profile methods for the on-disk shape.
 */
public class ProfilesScreen extends SteamPadBaseScreen {

    private static final int ROW_H = 24;

    private final Screen parent;
    private final long handle;
    private EditBox nameField;
    private Component status = Component.empty();

    public ProfilesScreen(Screen parent, long handle) {
        super(Component.translatable("steampad.screen.profiles.title"));
        this.parent = parent;
        this.handle = handle;
    }

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    private void rebuild() {
        String keepTyped = nameField != null ? nameField.getValue() : "";
        this.clearWidgets();
        resetScroll();

        int w = Math.min(360, this.width - 40);
        int x = (this.width - w) / 2;

        nameField = new EditBox(this.font, x, HEADER_H + 8, w - 90, 18,
                Component.translatable("steampad.screen.profiles.name_field"));
        nameField.setMaxLength(48);
        nameField.setValue(keepTyped);
        addRenderableWidget(nameField);

        addRenderableWidget(Button.builder(Component.translatable("steampad.screen.profiles.save"), b -> {
                    String name = nameField.getValue();
                    if (name == null || name.isBlank()) return;
                    boolean ok = ConfigManager.saveProfile(name, handle);
                    status = Component.translatable(ok ? "steampad.screen.profiles.saved"
                            : "steampad.screen.profiles.save_failed", name);
                    UiSoundService.playSelect();
                    rebuild();
                })
                .bounds(x + w - 85, HEADER_H + 8, 85, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());

        List<String> profiles = ConfigManager.listProfiles();
        int y = HEADER_H + 8 + 26;
        int halfW = (w - 6) / 2;
        for (String name : profiles) {
            Button loadBtn = Button.builder(
                            Component.translatable("steampad.screen.profiles.load_row", name),
                            b -> {
                                boolean ok = ConfigManager.loadProfile(name, handle);
                                status = Component.translatable(ok ? "steampad.screen.profiles.loaded"
                                        : "steampad.screen.profiles.load_failed", name);
                                UiSoundService.playSelect();
                            })
                    .bounds(x, y, halfW, 20).build();
            addScroll(loadBtn, y);

            Button deleteBtn = Button.builder(Component.translatable("steampad.screen.profiles.delete"),
                            b -> {
                                ConfigManager.deleteProfile(name);
                                status = Component.translatable("steampad.screen.profiles.deleted", name);
                                UiSoundService.playSelect();
                                rebuild();
                            })
                    .bounds(x + halfW + 6, y, halfW, 20).build();
            addScroll(deleteBtn, y);

            y += ROW_H;
        }
        finishScroll(y);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        int w = Math.min(360, this.width - 40);
        renderScrollbar(ctx, (this.width + w) / 2 + 4);
        if (!status.getString().isEmpty()) {
            ctx.drawCenteredString(this.font, status, this.width / 2,
                    this.height - FOOTER_H - 14, TEXT_MUTED);
        }
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
