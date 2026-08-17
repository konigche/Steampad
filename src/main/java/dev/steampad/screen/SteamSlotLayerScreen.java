package dev.steampad.screen;

import dev.steampad.config.ConfigManager;
import dev.steampad.input.SteamSlotDispatcher;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

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
        super(Component.translatable(titleKey));
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
            Component label = Component.translatable("steampad.act.steam_slot", slot + 1, 13 + slot)
                    .copy().append(": ").append(assigned);
            Button row = Button.builder(label, b -> minecraft.setScreen(
                    new SteamSlotTargetPickerScreen(this, id -> {
                        SteamSlotDispatcher.mapFor(context).put(SteamSlotDispatcher.configKey(slot), id);
                        ConfigManager.saveGlobal();
                        minecraft.setScreen(new SteamSlotLayerScreen(parent, context, titleKey));
                    }))).bounds(x, y, w, 20).build();
            addScroll(row, y);
            y += ROW_H;
        }
        finishScroll(y);

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, btn -> onClose())
                .bounds(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        int w = Math.min(420, this.width - 40);
        renderScrollbar(ctx, (this.width + w) / 2 + 4);
        int hy = contentTop() - 12;
        ctx.drawString(font, Component.translatable("steampad.slot.layer.hint"),
                (this.width - w) / 2, hy, 0xFF8090A0, false);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
