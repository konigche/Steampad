package dev.steampad.screen;

import dev.steampad.config.ConfigManager;
import dev.steampad.input.SteamSlotDispatcher;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/**
 * Edits the 10 Steam Input slot assignments for ONE context/layer (Menú, Inventario, or Montado — the
 * Gameplay layer keeps living in {@link BindingsScreen}, unchanged, since that's what already existed
 * and is already tested). Each row opens {@link SteamSlotTargetPickerScreen}, so a slot in this layer
 * can point at a SteamPad internal action or an external keybind exactly like the Gameplay layer can.
 */
public class SteamSlotLayerScreen extends SteamPadBaseScreen {

    private static final int ROW_H = 24;

    private final Screen parent;
    private final SteamSlotDispatcher.Context context;
    private final String titleKey;

    public SteamSlotLayerScreen(Screen parent, SteamSlotDispatcher.Context context, String titleKey) {
        super(Text.translatable(titleKey));
        this.parent = parent;
        this.context = context;
        this.titleKey = titleKey;
    }

    @Override
    protected void init() {
        super.init();
        resetScroll();

        int w = Math.min(420, this.width - 40);
        int x = (this.width - w) / 2;
        int y = contentTop();

        for (int i = 0; i < SteamSlotDispatcher.SLOT_COUNT; i++) {
            final int slot = i;
            String assigned = SteamSlotDispatcher.displayName(
                    SteamSlotDispatcher.mapFor(context).getOrDefault(SteamSlotDispatcher.configKey(slot), "")
            ).getString();
            Text label = Text.translatable("steampad.act.steam_slot", slot + 1, 13 + slot)
                    .copy().append(": ").append(assigned);
            ButtonWidget row = ButtonWidget.builder(label, b -> client.setScreen(
                    new SteamSlotTargetPickerScreen(this, id -> {
                        SteamSlotDispatcher.mapFor(context).put(SteamSlotDispatcher.configKey(slot), id);
                        ConfigManager.saveGlobal();
                        client.setScreen(new SteamSlotLayerScreen(parent, context, titleKey));
                    }))).dimensions(x, y, w, 20).build();
            addScroll(row, y);
            y += ROW_H;
        }
        finishScroll(y);

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, btn -> close())
                .dimensions(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        int w = Math.min(420, this.width - 40);
        renderScrollbar(ctx, (this.width + w) / 2 + 4);
        int hy = contentTop() - 12;
        ctx.drawText(textRenderer, Text.translatable("steampad.slot.layer.hint"),
                (this.width - w) / 2, hy, 0xFF8090A0, false);
    }

    @Override
    public void close() { client.setScreen(parent); }
}
