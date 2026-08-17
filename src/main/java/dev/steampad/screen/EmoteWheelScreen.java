package dev.steampad.screen;

import dev.steampad.client.ui.EmotePreviewPanel;
import dev.steampad.config.ConfigManager;
import dev.steampad.config.RadialConfig;
import dev.steampad.emote.EmoteAnimator;
import dev.steampad.emote.EmoteData;
import dev.steampad.emote.EmoteLibrary;
import dev.steampad.emote.EmoteNameFormatter;
import dev.steampad.emote.EmotePreviewTagger;
import dev.steampad.service.UiSoundService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Editor for the dedicated EMOTE WHEEL(S) (FASE 63) — the sibling of the radial-menu editor, reachable
 * from the same Botones section. The wheels live in their OWN independent list
 * ({@link RadialConfig#emoteWheels}, driven by {@link dev.steampad.emote.EmoteWheelController}) —
 * decoupled from the user's regular radial wheels (feedback: "no deben compartir información...
 * si se necesita poner más ruedas para emotes no se interpongan") — while still reusing the same
 * proven visuals/UX ({@link dev.steampad.radial.RadialRenderer}, and now the SAME add/remove/switch
 * wheel row as {@link RadialEditorScreen} — feedback: "también debería dejarme agregar más ruedas...
 * el funcionamiento debe ser similar [al] radial menu").
 *
 * <p>Row = slot: shows the assigned emote's display name; activating it opens the
 * {@link EmoteLibraryScreen} as a picker for that slot; the "✖" square clears it. Slot count is
 * adjustable (2–12) like the radial editor. Everything is plain buttons — gamepad-navigable for
 * free via the existing focus/cursor systems.
 */
public class EmoteWheelScreen extends SteamPadBaseScreen {

    private static final int ROW_H = 24;
    private static final int PREVIEW_W = 130;
    private static final int PREVIEW_GAP = 12;
    private static final int ENTITY_INSET = 78;
    /** Vertical space {@link dev.steampad.client.ui.EmotePreviewPanel} needs for its own text block
     *  (name/author/duration, plus the occasional "no world" hint line) below the entity inset — the
     *  panel used to stretch all the way to the footer regardless of content, leaving a big empty box
     *  (D111 feedback: "esta muy grande el espacio que sobra... de la caja"). */
    private static final int PREVIEW_TEXT_H = 70;

    private record Row(Button button, String emoteId) {}

    private final Screen parent;
    private final long handle;
    private final List<Row> rows = new ArrayList<>();
    private String previewId;
    private String animatedPreviewId;
    /** Generation token of the playback WE started — see EmoteLibraryScreen's field doc for why a
     *  plain boolean isn't enough (a real emote can replace our preview's Playback for the same
     *  entity id without this class knowing, unless generations are compared). */
    private long previewGeneration = -1L;
    private boolean ownsCurrentPreview(Minecraft mc) {
        return mc.player != null && previewGeneration != -1L
                && EmoteAnimator.currentGeneration(mc.player.getId()) == previewGeneration;
    }
    /** Which emote wheel is being edited (0-based page) — starts on whichever page is showing
     *  in-game, same convention as {@link RadialEditorScreen}. */
    private int page;

    public EmoteWheelScreen(Screen parent, long handle) {
        super(Component.translatable("steampad.emote.wheel.title"));
        this.parent = parent;
        this.handle = handle;
        this.page = dev.steampad.emote.EmoteWheelController.getPage();
    }

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    private int listWidth() { return Math.min(260, this.width - 40 - PREVIEW_W - PREVIEW_GAP); }

    private void rebuild() {
        this.clearWidgets();
        resetScroll();
        rows.clear();

        RadialConfig cfg = ConfigManager.getRadialConfig(handle);
        // Seeded the same way the in-game wheel seeds itself, so reaching the editor first doesn't
        // create the blank wheel the seeding exists to avoid.
        cfg.ensureEmoteWheel(dev.steampad.emote.EmoteWheelController.defaultSeedIds());
        cfg.normalize();
        int wheels = cfg.emoteWheelCount();
        if (page >= wheels) page = Math.max(0, wheels - 1);

        int w = listWidth();
        int x = 20;
        int y = contentTop();

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(this.width / 2 - 75, this.height - FOOTER_H + 7, 150, 20).build());

        // Wheel row — add/remove/switch, same compact layout as RadialEditorScreen's own wheel row
        // (D111 feedback: "las flechas hazla más pequeñas esta demasiado grandes" — they used to be
        // stretched to a third/the full row width; now a small fixed size, ◀/▶ together on the left,
        // remove(−)/add(+) together on the right — "derecha es más y quitar es izquierda").
        int third = (w - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal((page + 1) + "/" + wheels), b -> {})
                .bounds(x, y, w, 14).build()).active = false;
        y += 14;
        Button prevW = Button.builder(Component.literal("◀"), b -> switchWheel(-1))
                .bounds(x, y, 44, 18)
                .tooltip(Tooltip.create(Component.translatable("steampad.radial.wheel.desc"))).build();
        prevW.active = wheels > 1;
        addScroll(prevW, y);
        Button nextW = Button.builder(Component.literal("▶"), b -> switchWheel(+1))
                .bounds(x + 48, y, 44, 18)
                .tooltip(Tooltip.create(Component.translatable("steampad.radial.wheel.desc"))).build();
        nextW.active = wheels > 1;
        addScroll(nextW, y);
        Button delW = Button.builder(Component.translatable("steampad.radial.wheel.remove_short"), b -> removeWheel())
                .bounds(x + w - 104, y, 50, 18)
                .tooltip(Tooltip.create(Component.translatable("steampad.radial.wheel.remove"))).build();
        delW.active = wheels > 1;
        addScroll(delW, y);
        Button addW = Button.builder(Component.translatable("steampad.radial.wheel.add_short"), b -> addWheel())
                .bounds(x + w - 50, y, 50, 18)
                .tooltip(Tooltip.create(Component.translatable("steampad.radial.wheel.add"))).build();
        addW.active = wheels < RadialConfig.MAX_WHEELS;
        addScroll(addW, y);
        y += ROW_H;

        int slotCount = cfg.slotCountForEmote(page);

        // Slot count row: − [n slots] + (setSlotCountForEmote — NOT setSlotCountFor, which targets
        // the unrelated regular radial wheels and would silently corrupt the user's own wheel config).
        Button minus = Button.builder(Component.literal("−"), b -> {
            if (slotCount > 2) { cfg.setSlotCountForEmote(page, slotCount - 1); save(); rebuild(); }
        }).bounds(x, y, third, 20).build();
        Button count = Button.builder(
                Component.translatable("steampad.emote.wheel.slots", slotCount), b -> {})
                .bounds(x + third + 4, y, third, 20).build();
        count.active = false;
        Button plus = Button.builder(Component.literal("+"), b -> {
            if (slotCount < RadialConfig.MAX_SLOTS) { cfg.setSlotCountForEmote(page, slotCount + 1); save(); rebuild(); }
        }).bounds(x + (third + 4) * 2, y, third, 20).build();
        addScroll(minus, y);
        addScroll(count, y);
        addScroll(plus, y);
        y += ROW_H + 4;

        // One row per slot: pick / clear.
        int clearW = 24;
        List<RadialConfig.SlotConfig> slots = cfg.slotsForEmote(page);
        for (int i = 0; i < slotCount; i++) {
            final int slotIdx = i;
            RadialConfig.SlotConfig sc = slots.get(i);
            boolean isEmote = "EMOTE".equals(sc.type) && !sc.action.isEmpty();
            Component label = isEmote
                    ? Component.literal((slotIdx + 1) + ". " + displayNameFor(sc))
                    : Component.translatable("steampad.emote.wheel.slot_empty", slotIdx + 1);
            Button row = Button.builder(label, b -> {
                UiSoundService.playSelect();
                minecraft.setScreen(new EmoteLibraryScreen(this, emoteId -> assign(slotIdx, emoteId)));
            }).bounds(x, y, w - clearW - 4, 20).build();
            addScroll(row, y);
            rows.add(new Row(row, isEmote ? sc.action : null));
            Button clear = Button.builder(Component.literal("✖"), b -> {
                clearSlot(slotIdx);
                rebuild();
            }).bounds(x + w - clearW, y, clearW, 20).build();
            clear.active = isEmote;
            addScroll(clear, y);
            y += ROW_H;
        }

        finishScroll(y);
    }

    private void switchWheel(int dir) {
        RadialConfig cfg = ConfigManager.getRadialConfig(handle);
        int n = cfg.emoteWheelCount();
        if (n <= 1) return;
        UiSoundService.playNavigate();
        page = Math.floorMod(page + dir, n);
        rebuild();
    }

    private void addWheel() {
        RadialConfig cfg = ConfigManager.getRadialConfig(handle);
        int idx = cfg.addEmoteWheel();
        if (idx < 0) return;
        UiSoundService.playSelect();
        page = idx;
        save();
        rebuild();
    }

    /** Removes the wheel currently being viewed. Unlike the regular radial editor's dedicated
     *  confirmation screen, this removes directly — emote wheels are lower-stakes (re-assigning from
     *  the library is one click) and this keeps the editor's own scope from growing further. */
    private void removeWheel() {
        RadialConfig cfg = ConfigManager.getRadialConfig(handle);
        if (cfg.emoteWheelCount() <= 1) return;
        UiSoundService.playSelect();
        cfg.removeEmoteWheel(page);
        page = Math.min(page, Math.max(0, cfg.emoteWheelCount() - 1));
        save();
        rebuild();
    }

    /** Whichever row is focused (D-pad) or hovered (mouse/virtual cursor) becomes the preview, same
     *  sticky behavior as {@link EmoteLibraryScreen}. Empty slots simply don't change the preview. */
    private void updatePreview() {
        for (Row r : rows) {
            if (r.button().isFocused() && r.emoteId() != null) { previewId = r.emoteId(); return; }
        }
        for (Row r : rows) {
            if (r.button().isHovered() && r.emoteId() != null) { previewId = r.emoteId(); return; }
        }
    }

    /** Starts/switches the animated preview to match {@link #previewId} — same rules as
     *  {@link EmoteLibraryScreen}: never while the player has a real emote actually running. */
    private void updateAnimatedPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || previewId == null) return;
        boolean conflictsWithRealEmote = EmoteAnimator.isLocalPlaying() && !ownsCurrentPreview(mc);
        if (conflictsWithRealEmote) return;
        if (!previewId.equals(animatedPreviewId)) {
            EmoteData data = EmoteLibrary.byId(previewId);
            if (data != null) {
                EmoteAnimator.playForGuiPreview(mc.player.getId(), data);
                animatedPreviewId = previewId;
                previewGeneration = EmoteAnimator.currentGeneration(mc.player.getId());
            }
        }
    }

    private String displayNameFor(RadialConfig.SlotConfig sc) {
        var data = EmoteLibrary.byId(sc.action);
        return data != null ? EmoteNameFormatter.shorten(data.name)
                : sc.displayName.isEmpty() ? sc.action : sc.displayName + " (?)";
    }

    private void assign(int slotIdx, String emoteId) {
        assignAndPersist(slotIdx, emoteId);
        rebuild();
    }

    /** Same persistence {@link #assign} performs, without forcing a UI rebuild — for callers that
     *  invoke this BEFORE this screen instance is actually shown (D111:
     *  {@link dev.steampad.emote.EmoteWheelController#openEditorForSelected}, the in-game wheel's
     *  "press the trigger to enter" shortcut straight to the Library), where {@code this.width}/
     *  {@code height} may still be unset — this screen's own {@code init()} rebuilds correctly once
     *  it actually becomes the active screen, reading whatever was just persisted here. */
    public void assignAndPersist(int slotIdx, String emoteId) {
        RadialConfig cfg = ConfigManager.getRadialConfig(handle);
        cfg.normalize();
        if (page >= cfg.emoteWheelCount()) return;
        RadialConfig.SlotConfig sc = cfg.slotsForEmote(page).get(slotIdx);
        var data = EmoteLibrary.byId(emoteId);
        sc.type = "EMOTE";
        sc.action = emoteId;
        sc.displayName = data != null ? data.name : emoteId;
        // A real per-emote image (EmoteIconProvider) instead of a single letter (feedback: "no te
        // pregunté si querías generar el ícono, te dije generalo, en el código original viene así") —
        // iconValue is the emote id itself; EmoteIconProvider resolves the actual artwork (embedded in
        // a .emotecraft binary, or a sibling .png) and only falls back to a letter when an emote
        // genuinely has neither.
        sc.iconType = "EMOTE";
        sc.iconValue = emoteId;
        sc.trigger = "ON_RELEASE";   // release-to-play — the wheel's natural gesture
        save();
    }

    private void clearSlot(int slotIdx) {
        RadialConfig cfg = ConfigManager.getRadialConfig(handle);
        cfg.normalize();
        if (page >= cfg.emoteWheelCount()) return;
        RadialConfig.SlotConfig sc = cfg.slotsForEmote(page).get(slotIdx);
        sc.type = "NONE";
        sc.action = "";
        sc.displayName = "";
        sc.iconType = "NONE";
        sc.iconValue = "";
        save();
    }

    private void save() {
        ConfigManager.saveRadialConfig(handle);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        updatePreview();
        updateAnimatedPreview();
        renderScrollbar(ctx, 20 + listWidth() + 4);

        int px = 20 + listWidth() + PREVIEW_GAP;
        int py = contentTop();
        boolean hasPlayer = Minecraft.getInstance().player != null;
        boolean showEntity = EmoteAnimator.isLocalPlaying() && hasPlayer;
        EmoteData previewData = previewId != null ? EmoteLibrary.byId(previewId) : null;
        int entityInset = showEntity ? ENTITY_INSET : 0;
        // Sized to its actual content (entity + text block) instead of always stretching to the
        // footer — D111 feedback: the box left most of its height empty when there wasn't much to show.
        int panelH = Math.min(this.height - FOOTER_H - py - 8, entityInset + PREVIEW_TEXT_H);
        EmotePreviewPanel.render(ctx, font, px, py, PREVIEW_W, panelH, previewData,
                entityInset, previewData != null && !hasPlayer);
        if (showEntity) {
            // Tagged as a GUI preview pass (D111, no pinned frame) so this draw — not the player's
            // normal WORLD render — is what's allowed to act on this screen's GUI-preview-only playback.
            int ex1 = px + PREVIEW_W / 2 - 24, ex2 = px + PREVIEW_W / 2 + 24;
            EmotePreviewTagger.beginLive();
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(ctx, ex1, py + 4, ex2, py + ENTITY_INSET,
                        26, 0f, 0f, delta, Minecraft.getInstance().player);
            } finally {
                EmotePreviewTagger.end();
            }
        }
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (ownsCurrentPreview(mc)) {
            EmoteAnimator.requestStop(mc.player.getId());
        }
        animatedPreviewId = null;
        previewGeneration = -1L;
        minecraft.setScreen(parent);
    }
}
