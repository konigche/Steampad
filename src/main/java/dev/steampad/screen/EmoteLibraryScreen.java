package dev.steampad.screen;

import dev.steampad.compat.mc.GuiPose;
import dev.steampad.config.ConfigManager;
import dev.steampad.emote.EmoteAnimator;
import dev.steampad.emote.EmoteData;
import dev.steampad.emote.EmoteLibrary;
import dev.steampad.emote.EmoteNameFormatter;
import dev.steampad.emote.EmotePreviewTagger;
import dev.steampad.radial.icon.EmoteIconProvider;
import dev.steampad.service.ActiveControllerService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The emote library: every emote this client can play (bundled CC0 pack + {@code .minecraft/emotes}
 * community files + {@code config/steampad/emotes}), searchable and 100% gamepad-navigable â€” the
 * search box works with the virtual keyboard like any other field, cells are ordinary buttons the
 * focus/cursor systems already know how to drive (FASE 63).
 *
 * <p>Two modes, same screen: as a PICKER (from the Rueda de Emotes editor, {@code onSelect != null})
 * a cell assigns that emote and returns; as a BROWSER (no callback) a cell PLAYS the emote right away
 * â€” the built-in "try it" path. The "â–¶" corner still plays on your own body in both modes.
 *
 * <p><b>Grid layout (FASE 74) + per-cell frozen thumbnails (FASE 77, v0.59.0).</b> EVERY cell now
 * shows the player's own character frozen at a representative frame of that cell's dance â€” the
 * originally requested "photo per cell" â€” and the cell with focus/hover renders BIGGER and plays
 * its dance live in real time. This became possible with D099's duck-tagged render states: each
 * queued GUI entity draw carries its own frozen-frame tag through 1.21.10's deferred flush (D092 â€”
 * the reason the first pose-then-draw attempt showed every cell with one shared pose), so any
 * number of differently-posed cells coexist per frame. The PNG icon remains only as the fallback
 * when there's no player to pose (library opened from the main menu).
 *
 * <p><b>Preview session (FASE 77):</b> while this screen is open, whatever REAL playback the player
 * had running (e.g. a looping dance) is snapshotted and parked, so previews always work â€” before
 * this, an active looping emote made {@code conflictsWithRealEmote} permanently true and the grid
 * previewed nothing (the user's exact report, with club_penguin_dance looping in their log). On
 * close the snapshot is restored â€” unless the user played a NEW emote from this screen (browse
 * mode's cell-click / "â–¶"), which rightfully replaces it (generation-token check).
 */
public class EmoteLibraryScreen extends SteamPadBaseScreen {

    private static final int CELL_SIZE = 68;
    private static final int CELL_GAP = 8;
    private static final int ICON_DRAW_SIZE = 16;   // EmoteIconProvider's fixed native footprint
    private static final int THUMB_BOX = 40;        // frozen-frame thumbnail box (every cell)
    private static final int THUMB_SCALE = 15;
    // Live (focused) cell renders bigger than the cell itself â€” deliberately overflows CELL_SIZE=68,
    // same "destaque mÃ¡s" push as the wheel's focused chip (v0.60.0/D100).
    private static final int ENTITY_BOX = 76;
    private static final int ENTITY_SCALE = 27;
    private static final int PLAY_BADGE = 16;

    private record Cell(Button button, EmoteLibrary.Entry entry, int x, int y) {}

    /** Small badge marking a cell whose emote is already assigned to a slot on the active
     *  controller's emote wheel(s) â€” feedback: "pon un identificador que ese emote ya esta asignada
     *  a la rueda para no repetir" (D111). Purely informational; picking it again still works (a
     *  duplicate assignment isn't invalid, just rarely intentional). */
    private static final int ASSIGNED_BADGE_SIZE = 11;
    private static final int ASSIGNED_BADGE_BG = 0xFF2ECC71;
    private static final int ASSIGNED_BADGE_FG = 0xFF0B2E13;

    private final Screen parent;
    private final Consumer<String> onSelect;   // null = browse/play mode
    private EditBox search;
    private final List<Cell> cells = new ArrayList<>();
    /** Emote ids already on the active controller's emote wheel(s) (any page) â€” recomputed once per
     *  {@code init()} (a picker session never re-assigns mid-browse, so this can't go stale while the
     *  screen is open). Empty when no controller is active (e.g. library opened from the main menu). */
    private Set<String> assignedEmoteIds = Set.of();
    /** Sticky â€” only changes when a cell is actually focused/hovered, never resets to nothing just
     *  because the cursor drifted into empty space (same sticky-selection spirit as the radial). */
    private String previewId;
    /** Animated preview state (feedback: "agrega un previo animado optimizado"). Reuses vanilla's own
     *  inventory-screen player render (InventoryScreen.drawEntity) â€” the same cheap, already-optimized
     *  mechanism Mojang uses for YOUR character in the inventory screen â€” driven by EmoteAnimator's
     *  real playback engine directly on {@code mc.player}, WITHOUT going through playLocal() (so no
     *  perspective flip, no network broadcast â€” this never leaves the local client). Never starts
     *  while the player has a REAL emote actually running that we didn't start ourselves (ownership
     *  tracked via a generation token, see {@link #ownsCurrentPreview}) â€” the cell just shows its
     *  normal icon in that rare case instead of stomping their real animation. */
    private String animatedPreviewId;
    /** Generation token of the playback WE started (see {@code EmoteAnimator#currentGeneration}) â€”
     *  the single source of truth for "is a preview WE own currently active", replacing an earlier
     *  plain boolean that could desync: browse mode's "â–¶" button and cell-click both call
     *  {@code playLocal} for the SAME entity id this preview drives, replacing the {@code Playback}
     *  the preview started with a real one. The boolean had no way to notice that swap, so hovering a
     *  different cell afterward â€” or simply closing the screen â€” would stomp/cancel the emote the user
     *  had just triggered for real. Comparing generations catches the swap: a mismatch means something
     *  else now owns the entity slot, and this class backs off instead of touching it. */
    private long previewGeneration = -1L;
    /** Parked REAL playback + session flag (see class doc). Parked once per screen instance â€”
     *  init() re-runs on resize, but these instance fields survive it. */
    private Object realPlaybackSnapshot;
    private boolean sessionActive;

    private boolean ownsCurrentPreview(Minecraft mc) {
        return mc.player != null && previewGeneration != -1L
                && EmoteAnimator.currentGeneration(mc.player.getId()) == previewGeneration;
    }

    public EmoteLibraryScreen(Screen parent, Consumer<String> onSelect) {
        super(Component.translatable("steampad.emote.library.title"));
        this.parent = parent;
        this.onSelect = onSelect;
    }

    @Override protected int contentTop() { return HEADER_H + 34; }

    private int gridWidth() { return Math.min(560, this.width - 40); }

    @Override
    protected void init() {
        super.init();
        resetScroll();

        long handle = ActiveControllerService.getActiveHandle();
        assignedEmoteIds = handle != 0L
                ? ConfigManager.getRadialConfig(handle).assignedEmoteIds()
                : Set.of();

        int w = gridWidth();
        int x = (this.width - w) / 2;
        search = new EditBox(this.font, x, HEADER_H + 8, w, 18,
                Component.translatable("steampad.radial.search"));
        search.setResponder(t -> rebuild());
        addRenderableWidget(search);
        rebuild();
    }

    private void rebuild() {
        this.clearWidgets();
        resetScroll();
        cells.clear();

        int w = gridWidth();
        int x0 = (this.width - w) / 2;
        addRenderableWidget(search);

        addRenderableWidget(Button.builder(Component.translatable("steampad.button.refresh"), b -> {
            EmoteLibrary.reload();
            rebuild();
        }).bounds(this.width / 2 - 154, this.height - FOOTER_H + 7, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(this.width / 2 + 4, this.height - FOOTER_H + 7, 150, 20).build());

        List<EmoteLibrary.Entry> entries = EmoteLibrary.search(search.getValue());
        int cols = Math.max(1, (w + CELL_GAP) / (CELL_SIZE + CELL_GAP));
        int gridW = cols * CELL_SIZE + (cols - 1) * CELL_GAP;
        int gx0 = x0 + (w - gridW) / 2;
        int y = contentTop();

        int i = 0;
        for (EmoteLibrary.Entry e : entries) {
            int col = i % cols, row = i / cols;
            int cx = gx0 + col * (CELL_SIZE + CELL_GAP);
            int cy = y + row * (CELL_SIZE + CELL_GAP);
            Button cell = Button.builder(Component.empty(), b -> {
                if (onSelect != null) {
                    onSelect.accept(e.id());
                    onClose();
                } else {
                    EmoteAnimator.playLocal(e.id());
                }
            }).bounds(cx, cy, CELL_SIZE, CELL_SIZE).build();
            addScroll(cell, cy);
            cells.add(new Cell(cell, e, cx, cy));
            i++;
        }
        int rows = Math.max(1, (i + cols - 1) / cols);
        int contentEnd = y + rows * (CELL_SIZE + CELL_GAP);

        if (entries.isEmpty()) {
            Button none = Button.builder(Component.translatable("steampad.emote.library.empty"),
                    b -> {}).bounds(x0, y, w, 20).build();
            none.active = false;
            addScroll(none, y);
            contentEnd = y + 24;
        }
        if (previewId == null && !entries.isEmpty()) previewId = entries.get(0).id();

        finishScroll(contentEnd);
        this.setFocused(search);
        search.setFocused(true);
    }

    /** Whichever cell is focused (D-pad) or hovered (mouse/virtual cursor) becomes the live preview â€”
     *  focus wins when both are true, matching this mod's gamepad-first navigation model. */
    private void updatePreview() {
        for (Cell c : cells) {
            if (c.button().isFocused()) { previewId = c.entry().id(); return; }
        }
        for (Cell c : cells) {
            if (c.button().isHovered()) { previewId = c.entry().id(); return; }
        }
    }

    /** Starts/switches the animated preview to match {@link #previewId}, RESTARTING it when a
     *  non-loop preview finished and was pruned (the prune left {@code animatedPreviewId} set with
     *  no playback behind it, so the cell silently froze forever â€” FASE 77 fix). Also opens the
     *  park/restore session on first use (see class doc). Never fights a real emote something else
     *  started (generation mismatch â†’ back off). */
    private void updateAnimatedPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || previewId == null) return;
        if (!sessionActive) {
            sessionActive = true;
            realPlaybackSnapshot = EmoteAnimator.snapshotPlayback(mc.player.getId());
            if (realPlaybackSnapshot != null) EmoteAnimator.restorePlayback(mc.player.getId(), null);
        }
        if (animatedPreviewId != null && previewGeneration != -1L
                && EmoteAnimator.currentGeneration(mc.player.getId()) == -1L) {
            animatedPreviewId = null;   // our non-loop preview ended and was pruned â€” restart below
            previewGeneration = -1L;
        }
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

    /** Draws every visible cell as the player's own character frozen at a representative frame of
     *  that cell's dance (duck-tagged draws â€” see the class doc), except the ONE live cell (matching
     *  {@link #animatedPreviewId}), which renders bigger and animates in real time. The PNG icon is
     *  only the no-player fallback (library opened from the main menu). */
    private void renderCells(GuiGraphics ctx, float delta) {
        Minecraft mc = Minecraft.getInstance();
        boolean conflictsWithRealEmote = mc.player != null
                && EmoteAnimator.isLocalPlaying() && !ownsCurrentPreview(mc);

        for (Cell c : cells) {
            if (!isInViewport(c.y(), CELL_SIZE)) continue;
            int cellY = c.button().getY();   // scroll-adjusted
            int cx = c.x(), cy = cellY;

            boolean isLiveCell = mc.player != null && !conflictsWithRealEmote
                    && c.entry().id().equals(animatedPreviewId);
            if (isLiveCell) {
                // Tagged as a GUI preview pass (D111, no pinned frame) â†’ still poses from the live
                // playback map at flush (real animation), but the tag is what lets this exact draw â€”
                // never the player's normal WORLD render â€” act on a GUI-preview-only playback.
                int ex1 = cx + CELL_SIZE / 2 - ENTITY_BOX / 2, ey1 = cy + CELL_SIZE / 2 - ENTITY_BOX / 2 - 2;
                EmotePreviewTagger.beginLive();
                try {
                    InventoryScreen.renderEntityInInventoryFollowsMouse(ctx, ex1, ey1, ex1 + ENTITY_BOX, ey1 + ENTITY_BOX,
                            ENTITY_SCALE, 0f, 0f, delta, mc.player);
                } finally {
                    EmotePreviewTagger.end();
                }
            } else if (mc.player != null) {
                // Duck-tagged draw â†’ this exact queued draw renders frozen at a representative frame
                // of THIS cell's dance, independent of every other cell in the same flush.
                int ex1 = cx + CELL_SIZE / 2 - THUMB_BOX / 2, ey1 = cy + CELL_SIZE / 2 - THUMB_BOX / 2 - 2;
                EmotePreviewTagger.begin(c.entry().data(), EmoteAnimator.representativeTick(c.entry().data()));
                try {
                    InventoryScreen.renderEntityInInventoryFollowsMouse(ctx, ex1, ey1, ex1 + THUMB_BOX, ey1 + THUMB_BOX,
                            THUMB_SCALE, 0f, 0f, delta, mc.player);
                } finally {
                    EmotePreviewTagger.end();
                }
            } else {
                int scale = CELL_SIZE / (2 * ICON_DRAW_SIZE);
                if (scale <= 1) {
                    EmoteIconProvider.render(ctx, c.entry().id(),
                            cx + CELL_SIZE / 2 - ICON_DRAW_SIZE / 2, cy + CELL_SIZE / 2 - ICON_DRAW_SIZE / 2);
                } else {
                    int px = cx + CELL_SIZE / 2, py = cy + CELL_SIZE / 2;
                    GuiPose.push(ctx);
                    GuiPose.translate(ctx, px, py);
                    GuiPose.scale(ctx, scale, scale);
                    GuiPose.translate(ctx, -px, -py);
                    EmoteIconProvider.render(ctx, c.entry().id(), px - ICON_DRAW_SIZE / 2, py - ICON_DRAW_SIZE / 2);
                    GuiPose.pop(ctx);
                }
            }

            // "Already on the wheel" badge (feedback: "para no repetir", D111) â€” skipped on the live
            // cell, which already overflows this corner with the bigger real-time entity render above.
            if (!isLiveCell && assignedEmoteIds.contains(c.entry().id())) {
                int bx = cx + CELL_SIZE - ASSIGNED_BADGE_SIZE - 2, by = cy + 2;
                ctx.fill(bx, by, bx + ASSIGNED_BADGE_SIZE, by + ASSIGNED_BADGE_SIZE, ASSIGNED_BADGE_BG);
                ctx.drawString(this.font, "✓", bx + 2, by + 1, ASSIGNED_BADGE_FG, false);
            }

            // Name, truncated to the cell width, under the icon/thumbnail â€” short form (D111: "usa
            // el nombre corto... no necesariamente todo el nombre") so a decorated community name
            // like "Macarena (dance) - MineEmote" reads as "Macarena" instead of mid-word truncation.
            String name = EmoteNameFormatter.shorten(c.entry().data().name);
            if (this.font.width(name) > CELL_SIZE - 4) {
                while (name.length() > 1 && this.font.width(name + "…") > CELL_SIZE - 4) {
                    name = name.substring(0, name.length() - 1);
                }
                name = name + "…";
            }
            ctx.drawCenteredString(this.font, Component.literal(name),
                    cx + CELL_SIZE / 2, cy + CELL_SIZE - 10, 0xFFDDDDDD);
        }
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        updatePreview();
        updateAnimatedPreview();
        renderScrollbar(ctx, (this.width + gridWidth()) / 2 + 4);
        renderCells(ctx, delta);

        // Folder hint so users know where to drop downloaded emote files.
        ctx.drawCenteredString(font,
                Component.translatable("steampad.emote.library.folders"),
                this.width / 2, this.height - FOOTER_H - 12, 0xFF9AA4AC);
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (sessionActive && mc.player != null) {
            int id = mc.player.getId();
            long current = EmoteAnimator.currentGeneration(id);
            boolean somethingElseOwns = current != -1L && previewGeneration != current;
            if (!somethingElseOwns) {
                // Nothing but our own preview (or nothing at all) runs â€” restore what was parked
                // at open, or ease our preview out if nothing was.
                if (realPlaybackSnapshot != null) {
                    EmoteAnimator.restorePlayback(id, realPlaybackSnapshot);
                } else if (ownsCurrentPreview(mc)) {
                    EmoteAnimator.requestStop(id);
                }
            }
            // else: the user played a real emote from this screen â€” it replaces the parked one.
        }
        sessionActive = false;
        realPlaybackSnapshot = null;
        animatedPreviewId = null;
        previewGeneration = -1L;
        minecraft.setScreen(parent);
    }
}
