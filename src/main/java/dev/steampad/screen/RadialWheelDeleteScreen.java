package dev.steampad.screen;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.RadialConfig;
import dev.steampad.service.UiSoundService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/**
 * Picker for which radial wheel to delete (feedback: "poder seleccionar que rueda quieres eliminar...
 * si esta con atajos preguntar si esa rueda quieres eliminar"). Replaces the old ✕ button's behavior
 * of always deleting whichever wheel the editor happened to be showing — which silently deleted a
 * wheel WITH shortcuts while an empty one sat untouched elsewhere in the list (the reported bug).
 * Each row is labeled with its content so an empty wheel is the obvious, safe pick; picking one that
 * actually has shortcuts arms a confirm step instead of deleting immediately.
 */
public class RadialWheelDeleteScreen extends SteamPadBaseScreen {

    private final RadialEditorScreen editor;
    private final long handle;
    private RadialConfig cfg;
    /** Wheel armed for confirmation (has content), or -1 while just browsing the list. */
    private int confirming = -1;

    public RadialWheelDeleteScreen(RadialEditorScreen editor, long handle) {
        super(Text.translatable("steampad.radial.wheel.delete.title"));
        this.editor = editor;
        this.handle = handle;
    }

    @Override
    protected void init() {
        super.init();
        this.cfg = ConfigManager.getRadialConfig(handle);
        cfg.normalize();

        int colX = 14, colW = this.width - 28;
        int y = HEADER_H + 16;

        if (confirming >= 0 && confirming < cfg.wheelCount()) {
            int page = confirming;
            int n = cfg.configuredSlotCountFor(page);
            ButtonWidget warn = ButtonWidget.builder(
                    Text.translatable("steampad.radial.wheel.delete.confirm", page + 1, n), b -> {})
                    .dimensions(colX, y, colW, 20).build();
            warn.active = false;   // a label, not a real button
            addDrawableChild(warn);
            y += 28;
            addDrawableChild(ButtonWidget.builder(Text.translatable("steampad.radial.wheel.delete.yes"),
                    b -> doDelete(page)).dimensions(colX, y, colW / 2 - 4, 20).build());
            addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> { confirming = -1; clearAndInit(); })
                    .dimensions(colX + colW / 2 + 4, y, colW / 2 - 4, 20).build());
        } else {
            confirming = -1;
            int wheels = cfg.wheelCount();
            for (int i = 0; i < wheels; i++) {
                int page = i;
                int configured = cfg.configuredSlotCountFor(page);
                Text label = configured == 0
                        ? Text.translatable("steampad.radial.wheel.delete.row_empty", page + 1)
                        : Text.translatable("steampad.radial.wheel.delete.row_used", page + 1, configured);
                ButtonWidget row = ButtonWidget.builder(label, b -> {
                    if (configured == 0) {
                        doDelete(page);
                    } else {
                        confirming = page;
                        UiSoundService.playNavigate();
                        clearAndInit();
                    }
                }).dimensions(colX, y, colW, 20).build();
                row.active = wheels > 1;   // at least one wheel must always remain
                addDrawableChild(row);
                y += 24;
            }
        }

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, b -> close())
                .dimensions(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());
    }

    private void doDelete(int page) {
        if (!cfg.removeWheel(page)) return;
        ConfigManager.saveRadialConfig(handle);
        UiSoundService.playSelect();
        editor.onWheelDeleted();
        close();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void close() { client.setScreen(editor); }
}
