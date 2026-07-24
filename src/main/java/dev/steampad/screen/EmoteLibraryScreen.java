package dev.steampad.screen;

import dev.steampad.emote.EmoteAnimator;
import dev.steampad.emote.EmoteData;
import dev.steampad.emote.EmoteLibrary;
import dev.steampad.emote.EmotePreviewTagger;
import dev.steampad.radial.icon.EmoteIconProvider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The emote library: every emote this client can play (bundled CC0 pack + {@code .minecraft/emotes}
 * community files + {@code config/steampad/emotes}), searchable and 100% gamepad-navigable — the
 * search box works with the virtual keyboard like any other field, cells are ordinary buttons the
 * focus/cursor systems already know how to drive (FASE 63).
 *
 * <p>Two modes, same screen: as a PICKER (from the Rueda de Emotes editor, {@code onSelect != null})
 * a cell assigns that emote and returns; as a BROWSER (no callback) a cell PLAYS the emote right away
 * — the built-in "try it" path. The "▶" corner still plays on your own body in both modes.
 *
 * <p><b>Grid layout (FASE 74) + per-cell frozen thumbnails (FASE 77, v0.59.0).</b> EVERY cell now
 * shows the player's own character frozen at a representative frame of that cell's dance — the
 * originally requested "photo per cell" — and the cell with focus/hover renders BIGGER and plays
 * its dance live in real time. This became possible with D099's duck-tagged render states: each
 * queued GUI entity draw carries its own frozen-frame tag through 1.21.10's deferred flush (D092 —
 * the reason the first pose-then-draw attempt showed every cell with one shared pose), so any
 * number of differently-posed cells coexist per frame. The PNG icon remains only as the fallback
 * when there's no player to pose (library opened from the main menu).
 *
 * <p><b>Preview session (FASE 77):</b> while this screen is open, whatever REAL playback the player
 * had running (e.g. a looping dance) is snapshotted and parked, so previews always work — before
 * this, an active looping emote made {@code conflictsWithRealEmote} permanently true and the grid
 * previewed nothing (the user's exact report, with club_penguin_dance looping in their log). On
 * close the snapshot is restored — unless the user played a NEW emote from this screen (browse
 * mode's cell-click / "▶"), which rightfully replaces it (generation-token check).
 */
public class EmoteLibraryScreen extends SteamPadBaseScreen {

    private static final int CELL_SIZE = 68;
    private static final int CELL_GAP = 8;
    private static final int ICON_DRAW_SIZE = 16;   // EmoteIconProvider's fixed native footprint
    private static final int THUMB_BOX = 40;        // frozen-frame thumbnail box (every cell)
    private static final int THUMB_SCALE = 15;
    // Live (focused) cell renders bigger than the cell itself — deliberately overflows CELL_SIZE=68,
    // same "destaque más" push as the wheel's focused chip (v0.60.0/D100).
    private static final int ENTITY_BOX = 76;
    private static final int ENTITY_SCALE = 27;
    private static final int PLAY_BADGE = 16;

    private record Cell(ButtonWidget button, EmoteLibrary.Entry entry, int x, int y) {}

    private final Screen parent;
    private final Consumer<String> onSelect;   // null = browse/play mode
    private TextFieldWidget search;
    private final List<Cell> cells = new ArrayList<>();
    /** Sticky — only changes when a cell is actually focused/hovered, never resets to nothing just
     *  because the cursor drifted into empty space (same sticky-selection spirit as the radial). */
    private String previewId;
    /** Animated preview state (feedback: "agrega un previo animado optimizado"). Reuses vanilla's own
     *  inventory-screen player render (InventoryScreen.drawEntity) — the same cheap, already-optimized
     *  mechanism Mojang uses for YOUR character in the inventory screen — driven by EmoteAnimator's
     *  real playback engine directly on {@code mc.player}, WITHOUT going through playLocal() (so no
     *  perspective flip, no network broadcast — this never leaves the local client). Never starts
     *  while the player has a REAL emote actually running that we didn't start ourselves (ownership
     *  tracked via a generation token, see {@link #ownsCurrentPreview}) — the cell just shows its
     *  normal icon in that rare case instead of stomping their real animation. */
    private String animatedPreviewId;
    /** Generation token of the playback WE started (see {@code EmoteAnimator#currentGeneration}) —
     *  the single source of truth for "is a preview WE own currently active", replacing an earlier
     *  plain boolean that could desync: browse mode's "▶" button and cell-click both call
     *  {@code playLocal} for the SAME entity id this preview drives, replacing the {@code Playback}
     *  the preview started with a real one. The boolean had no way to notice that swap, so hovering a
     *  different cell afterward — or simply closing the screen — would stomp/cancel the emote the user
     *  had just triggered for real. Comparing generations catches the swap: a mismatch means something
     *  else now owns the entity slot, and this class backs off instead of touching it. */
    private long previewGeneration = -1L;
    /** Parked REAL playback + session flag (see class doc). Parked once per screen instance —
     *  init() re-runs on resize, but these instance fields survive it. */
    private Object realPlaybackSnapshot;
    private boolean sessionActive;

    private boolean ownsCurrentPreview(MinecraftClient mc) {
        return mc.player != null && previewGeneration != -1L
                && EmoteAnimator.currentGeneration(mc.player.getId()) == previewGeneration;
    }

    public EmoteLibraryScreen(Screen parent, Consumer<String> onSelect) {
        super(Text.translatable("steampad.emote.library.title"));
        this.parent = parent;
        this.onSelect = onSelect;
    }

    @Override protected int contentTop() { return HEADER_H + 34; }

    private int gridWidth() { return Math.min(560, this.width - 40); }

    @Override
    protected void init() {
        super.init();
        resetScroll();

        int w = gridWidth();
        int x = (this.width - w) / 2;
        search = new TextFieldWidget(this.textRenderer, x, HEADER_H + 8, w, 18,
                Text.translatable("steampad.radial.search"));
        search.setChangedListener(t -> rebuild());
        addDrawableChild(search);
        rebuild();
    }

    private void rebuild() {
        this.clearChildren();
        resetScroll();
        cells.clear();

        int w = gridWidth();
        int x0 = (this.width - w) / 2;
        addDrawableChild(search);

        addDrawableChild(ButtonWidget.builder(Text.translatable("steampad.button.refresh"), b -> {
            EmoteLibrary.reload();
            rebuild();
        }).dimensions(this.width / 2 - 154, this.height - FOOTER_H + 7, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, b -> close())
                .dimensions(this.width / 2 + 4, this.height - FOOTER_H + 7, 150, 20).build());

        List<EmoteLibrary.Entry> entries = EmoteLibrary.search(search.getText());
        int cols = Math.max(1, (w + CELL_GAP) / (CELL_SIZE + CELL_GAP));
        int gridW = cols * CELL_SIZE + (cols - 1) * CELL_GAP;
        int gx0 = x0 + (w - gridW) / 2;
        int y = contentTop();

        int i = 0;
        for (EmoteLibrary.Entry e : entries) {
            int col = i % cols, row = i / cols;
            int cx = gx0 + col * (CELL_SIZE + CELL_GAP);
            int cy = y + row * (CELL_SIZE + CELL_GAP);
            ButtonWidget cell = ButtonWidget.builder(Text.empty(), b -> {
                if (onSelect != null) {
                    onSelect.accept(e.id());
                    close();
                } else {
                    EmoteAnimator.playLocal(e.id());
                }
            }).dimensions(cx, cy, CELL_SIZE, CELL_SIZE).build();
            addScroll(cell, cy);
            cells.add(new Cell(cell, e, cx, cy));
            i++;
        }
        int rows = Math.max(1, (i + cols - 1) / cols);
        int contentEnd = y + rows * (CELL_SIZE + CELL_GAP);

        if (entries.isEmpty()) {
            ButtonWidget none = ButtonWidget.builder(Text.translatable("steampad.emote.library.empty"),
                    b -> {}).dimensions(x0, y, w, 20).build();
            none.active = false;
            addScroll(none, y);
            contentEnd = y + 24;
        }
        if (previewId == null && !entries.isEmpty()) previewId = entries.get(0).id();

        finishScroll(contentEnd);
        this.setFocused(search);
        search.setFocused(true);
    }

    /** Whichever cell is focused (D-pad) or hovered (mouse/virtual cursor) becomes the live preview —
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
     *  no playback behind it, so the cell silently froze forever — FASE 77 fix). Also opens the
     *  park/restore session on first use (see class doc). Never fights a real emote something else
     *  started (generation mismatch → back off). */
    private void updateAnimatedPreview() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || previewId == null) return;
        if (!sessionActive) {
            sessionActive = true;
            realPlaybackSnapshot = EmoteAnimator.snapshotPlayback(mc.player.getId());
            if (realPlaybackSnapshot != null) EmoteAnimator.restorePlayback(mc.player.getId(), null);
        }
        if (animatedPreviewId != null && previewGeneration != -1L
                && EmoteAnimator.currentGeneration(mc.player.getId()) == -1L) {
            animatedPreviewId = null;   // our non-loop preview ended and was pruned — restart below
            previewGeneration = -1L;
        }
        boolean conflictsWithRealEmote = EmoteAnimator.isLocalPlaying() && !ownsCurrentPreview(mc);
        if (conflictsWithRealEmote) return;
        if (!previewId.equals(animatedPreviewId)) {
            EmoteData data = EmoteLibrary.byId(previewId);
            if (data != null) {
                EmoteAnimator.playFor(mc.player.getId(), data);
                animatedPreviewId = previewId;
                previewGeneration = EmoteAnimator.currentGeneration(mc.player.getId());
            }
        }
    }

    /** Draws every visible cell as the player's own character frozen at a representative frame of
     *  that cell's dance (duck-tagged draws — see the class doc), except the ONE live cell (matching
     *  {@link #animatedPreviewId}), which renders bigger and animates in real time. The PNG icon is
     *  only the no-player fallback (library opened from the main menu). */
    private void renderCells(DrawContext ctx, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean conflictsWithRealEmote = mc.player != null
                && EmoteAnimator.isLocalPlaying() && !ownsCurrentPreview(mc);

        for (Cell c : cells) {
            if (!isInViewport(c.y(), CELL_SIZE)) continue;
            int cellY = c.button().getY();   // scroll-adjusted
            int cx = c.x(), cy = cellY;

            boolean isLiveCell = mc.player != null && !conflictsWithRealEmote
                    && c.entry().id().equals(animatedPreviewId);
            if (isLiveCell) {
                // Untagged draw → poses from the live playback map at flush (real animation).
                int ex1 = cx + CELL_SIZE / 2 - ENTITY_BOX / 2, ey1 = cy + CELL_SIZE / 2 - ENTITY_BOX / 2 - 2;
                InventoryScreen.drawEntity(ctx, ex1, ey1, ex1 + ENTITY_BOX, ey1 + ENTITY_BOX,
                        ENTITY_SCALE, 0f, 0f, delta, mc.player);
            } else if (mc.player != null) {
                // Duck-tagged draw → this exact queued draw renders frozen at a representative frame
                // of THIS cell's dance, independent of every other cell in the same flush.
                int ex1 = cx + CELL_SIZE / 2 - THUMB_BOX / 2, ey1 = cy + CELL_SIZE / 2 - THUMB_BOX / 2 - 2;
                EmotePreviewTagger.begin(c.entry().data(), EmoteAnimator.representativeTick(c.entry().data()));
                try {
                    InventoryScreen.drawEntity(ctx, ex1, ey1, ex1 + THUMB_BOX, ey1 + THUMB_BOX,
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
                    ctx.getMatrices().pushMatrix();
                    ctx.getMatrices().translate(px, py);
                    ctx.getMatrices().scale(scale, scale);
                    ctx.getMatrices().translate(-px, -py);
                    EmoteIconProvider.render(ctx, c.entry().id(), px - ICON_DRAW_SIZE / 2, py - ICON_DRAW_SIZE / 2);
                    ctx.getMatrices().popMatrix();
                }
            }

            // Name, truncated to the cell width, under the icon/thumbnail.
            String name = c.entry().data().name;
            if (this.textRenderer.getWidth(name) > CELL_SIZE - 4) {
                while (name.length() > 1 && this.textRenderer.getWidth(name + "…") > CELL_SIZE - 4) {
                    name = name.substring(0, name.length() - 1);
                }
                name = name + "…";
            }
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(name),
                    cx + CELL_SIZE / 2, cy + CELL_SIZE - 10, 0xFFDDDDDD);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        updatePreview();
        updateAnimatedPreview();
        renderScrollbar(ctx, (this.width + gridWidth()) / 2 + 4);
        renderCells(ctx, delta);

        // Folder hint so users know where to drop downloaded emote files.
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("steampad.emote.library.folders"),
                this.width / 2, this.height - FOOTER_H - 12, 0xFF9AA4AC);
    }

    @Override
    public void close() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (sessionActive && mc.player != null) {
            int id = mc.player.getId();
            long current = EmoteAnimator.currentGeneration(id);
            boolean somethingElseOwns = current != -1L && previewGeneration != current;
            if (!somethingElseOwns) {
                // Nothing but our own preview (or nothing at all) runs — restore what was parked
                // at open, or ease our preview out if nothing was.
                if (realPlaybackSnapshot != null) {
                    EmoteAnimator.restorePlayback(id, realPlaybackSnapshot);
                } else if (ownsCurrentPreview(mc)) {
                    EmoteAnimator.requestStop(id);
                }
            }
            // else: the user played a real emote from this screen — it replaces the parked one.
        }
        sessionActive = false;
        realPlaybackSnapshot = null;
        animatedPreviewId = null;
        previewGeneration = -1L;
        client.setScreen(parent);
    }
}
