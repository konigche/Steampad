package dev.steampad.client.hud;

import dev.steampad.client.ui.ControllerGlyphs;
import dev.steampad.config.ConfigManager;
import dev.steampad.config.ControllerConfig;
import dev.steampad.config.GlobalConfig;
import dev.steampad.input.GamepadBinds;
import dev.steampad.service.ActiveControllerService;
import dev.steampad.service.ControllerManager;
import dev.steampad.steam.SteamControllerHandleRef.ControllerType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Bedrock-style on-screen control guide. The button glyphs are derived from the controller's
 * <em>actual</em> bindings ({@link GamepadBinds}) so the hints always match what's mapped — fixing
 * "it shows X for inventory when it's now Y". Shown only when a fallback controller is active, the
 * HUD isn't hidden, and the per-controller toggle is on.
 */
public final class GameplayHudOverlay {

    private static final int BADGE_H = 13;
    private static final int GAP_Y = 4;
    private static final int MARGIN = 6;
    private static final int LABEL_COLOR = 0xFFE6ECF2;

    private GameplayHudOverlay() {}

    // ---- Show-all override (long-press PAUSE/START — feedback: "DEJAR PRESIONADO UN TIEMPO LARGO
    // START TE SALEN EN LA UI TODOS LOS BOTONES Y CHORDS ASIGNADOS, EN TODOS LOS LUGARES... AL
    // SOLTARLO DESAPARECE Y CAE EN LO QUE LO TIENES CONFIGURADO, MINIMO, NORMAL O COMPLETO") -------

    private static boolean showAllBinds = false;

    /** While true (PAUSE button held past the long-press threshold — see GamepadInputDispatcher),
     *  the HUD renders at FULL detail regardless of the configured level, and the container hint row
     *  additionally shows every gameplay bind. Cleared on release. */
    public static void setShowAllBinds(boolean on) { showAllBinds = on; }

    public static boolean isShowAllBinds() { return showAllBinds; }

    /** The detail level in effect this frame — the configured one, or FULL while show-all is held. */
    private static GlobalConfig.ButtonGuideDetail effectiveDetail() {
        return showAllBinds ? GlobalConfig.ButtonGuideDetail.FULL
                : ConfigManager.getGlobal().ingameButtonGuideDetail;
    }

    /**
     * Glyph size for the gameplay/inventory hint badges, scaled by the HUD glyph-scale slider
     * (feedback: "agrega un slider para poder cambiar la escala de los glifos en gameplay, debe
     * afectar a todo, inventario, rueda etc."). Row spacing (rowH = badgeH()+GAP_Y) and label
     * centering derive from this, so the whole hint row grows/shrinks together. Clamped defensively —
     * the slider itself limits to [0.5, 2.0].
     */
    private static int badgeH() {
        float s = ConfigManager.getGlobal().ingameButtonGuideScale;
        s = s < 0.5f ? 0.5f : (s > 2.0f ? 2.0f : s);
        return Math.round(BADGE_H * s);
    }

    private record Hint(GamepadBinds.Bind bind, String labelKey, GlobalConfig.ButtonGuideDetail tier) {}

    // Gameplay hints — buttons resolved live from the bindings every frame, so a rebind (or newly
    // assigned action like ZOOM, which ships unbound) appears/updates in real time. Unbound actions
    // draw nothing (the render loop skips empty buttons). Covers every GamepadBinds.Bind, gated by
    // GlobalConfig.ingameButtonGuideDetail (global — a display preference, not per-controller) so the
    // player picks how busy the overlay gets: MINIMAL = the 5 core survival actions; NORMAL = the
    // Bedrock-style curated set (MINIMAL + 6 more, including SWAP_HANDS/X — feedback: "en los glifos
    // normal del gameplay agrega la X también") + any chorded bind promoted up from FULL (see the
    // per-hint tier check in render()); FULL = all 21. Most NORMAL-tier hints use a short hand-picked
    // "steampad.hud.*" label; the rest reuse each bind's own "steampad.bind.*" label (already short
    // and already translated ×3, no new i18n needed).
    private static final GlobalConfig.ButtonGuideDetail MINIMAL = GlobalConfig.ButtonGuideDetail.MINIMAL;
    private static final GlobalConfig.ButtonGuideDetail NORMAL = GlobalConfig.ButtonGuideDetail.NORMAL;
    private static final GlobalConfig.ButtonGuideDetail FULL = GlobalConfig.ButtonGuideDetail.FULL;

    private static final Hint[] LEFT = {
            new Hint(GamepadBinds.Bind.INVENTORY, "steampad.hud.inventory", MINIMAL),
            new Hint(GamepadBinds.Bind.USE, "steampad.hud.use", MINIMAL),
            new Hint(GamepadBinds.Bind.HOTBAR_PREV, "steampad.hud.prev_item", NORMAL),
            new Hint(GamepadBinds.Bind.RADIAL, "steampad.hud.radial", NORMAL),
            new Hint(GamepadBinds.Bind.CHAT, "steampad.hud.chat", NORMAL),
            // Promoted FULL → NORMAL (feedback: "en los glifos normal del gameplay agrega la X
            // también para que sepan para que sirve") — X is a real default binding (SWAP_HANDS) that
            // was previously invisible until the player switched the HUD detail to FULL.
            new Hint(GamepadBinds.Bind.SWAP_HANDS, GamepadBinds.Bind.SWAP_HANDS.labelKey, NORMAL),
            new Hint(GamepadBinds.Bind.DROP, GamepadBinds.Bind.DROP.labelKey, FULL),
            new Hint(GamepadBinds.Bind.DROP_STACK, GamepadBinds.Bind.DROP_STACK.labelKey, FULL),
            new Hint(GamepadBinds.Bind.PICK_BLOCK, GamepadBinds.Bind.PICK_BLOCK.labelKey, FULL),
            new Hint(GamepadBinds.Bind.GYRO_TOGGLE, GamepadBinds.Bind.GYRO_TOGGLE.labelKey, FULL),
            new Hint(GamepadBinds.Bind.PLAYER_LIST, GamepadBinds.Bind.PLAYER_LIST.labelKey, FULL),
            new Hint(GamepadBinds.Bind.EMOTE_WHEEL, GamepadBinds.Bind.EMOTE_WHEEL.labelKey, FULL),
    };
    private static final Hint[] RIGHT = {
            new Hint(GamepadBinds.Bind.JUMP, "steampad.hud.jump", MINIMAL),
            new Hint(GamepadBinds.Bind.SNEAK, "steampad.hud.sneak", MINIMAL),
            new Hint(GamepadBinds.Bind.ATTACK, "steampad.hud.attack", MINIMAL),
            new Hint(GamepadBinds.Bind.HOTBAR_NEXT, "steampad.hud.next_item", NORMAL),
            new Hint(GamepadBinds.Bind.ZOOM, "steampad.hud.zoom", NORMAL),
            new Hint(GamepadBinds.Bind.SPRINT, GamepadBinds.Bind.SPRINT.labelKey, FULL),
            new Hint(GamepadBinds.Bind.PERSPECTIVE, GamepadBinds.Bind.PERSPECTIVE.labelKey, FULL),
            new Hint(GamepadBinds.Bind.PAUSE, GamepadBinds.Bind.PAUSE.labelKey, FULL),
            new Hint(GamepadBinds.Bind.SCREENSHOT, GamepadBinds.Bind.SCREENSHOT.labelKey, FULL),
            new Hint(GamepadBinds.Bind.HUD_TOGGLE, GamepadBinds.Bind.HUD_TOGGLE.labelKey, FULL),
    };

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null || mc.player == null) return;
        if (mc.options.hudHidden) return;

        long handle = ActiveControllerService.getActiveHandle();
        if (handle == 0L || !ControllerManager.isFallbackHandle(handle)) return;

        ControllerConfig cfg = ConfigManager.getControllerConfig(handle);
        if (!cfg.showIngameButtonGuide) return;
        // Mixed input OFF: hide the glyphs while the mouse is the active device; the controller brings
        // them back. Mixed input ON: always show (both devices coexist).
        if (!cfg.mixedInput && dev.steampad.input.InputRouter.isMouse()) return;

        ControllerType type = ActiveControllerService.getActiveRef()
                .map(r -> r.type).orElse(ControllerType.GENERIC);
        TextRenderer tr = mc.textRenderer;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        boolean top = cfg.ingameButtonGuidePosition == ControllerConfig.ButtonGuidePosition.TOP;
        int rowH = badgeH() + GAP_Y;

        // Radial (or the independent emote wheel, FASE 63 decoupling) open → the wheel overlay
        // carries its own control glyphs (B9); the gameplay hints would be wrong while it owns
        // input, so draw nothing here.
        if (dev.steampad.radial.RadialMenuController.isOpen()
                || dev.steampad.emote.EmoteWheelController.isOpen()) return;

        int yL = top ? MARGIN + 2 : h - MARGIN - badgeH();
        for (Hint hint : LEFT) {
            String chordBtn = GamepadBinds.chord(cfg, hint.bind());
            // Chorded binds are the hardest to remember — promote them to at least NORMAL visibility
            // even if their base tier is FULL (feedback: "los chords de dos combinaciones también
            // debería mostrarse en Normal, ya que son los más difíciles de recordar").
            GlobalConfig.ButtonGuideDetail effTier =
                    (!chordBtn.isEmpty() && hint.tier() == FULL) ? NORMAL : hint.tier();
            if (effTier.ordinal() > effectiveDetail().ordinal()) continue;
            String btn = GamepadBinds.button(cfg, hint.bind());
            if (btn.isEmpty()) continue;
            // While zooming, D-pad/A are repurposed (level/marker) and their normal action is
            // suppressed by the dispatcher — hide the stale hint too, in the same tick it stops
            // firing, instead of showing two conflicting hints on the same physical button.
            if (hint.bind() != GamepadBinds.Bind.ZOOM
                    && dev.steampad.input.ZoomController.isButtonRepurposed(cfg, btn)) continue;
            hintLeft(ctx, tr, type, MARGIN, yL, btn, chordBtn, Text.translatable(hint.labelKey()));
            yL += top ? rowH : -rowH;
        }
        int yR = top ? MARGIN + 2 : h - MARGIN - badgeH();
        for (Hint hint : RIGHT) {
            String chordBtn = GamepadBinds.chord(cfg, hint.bind());
            GlobalConfig.ButtonGuideDetail effTier =
                    (!chordBtn.isEmpty() && hint.tier() == FULL) ? NORMAL : hint.tier();
            if (effTier.ordinal() > effectiveDetail().ordinal()) continue;
            String btn = GamepadBinds.button(cfg, hint.bind());
            if (btn.isEmpty()) continue;
            if (hint.bind() != GamepadBinds.Bind.ZOOM
                    && dev.steampad.input.ZoomController.isButtonRepurposed(cfg, btn)) continue;
            hintRight(ctx, tr, type, w - MARGIN, yR, btn, chordBtn, Text.translatable(hint.labelKey()));
            yR += top ? rowH : -rowH;
        }

        // Contextual, REAL-TIME zoom hints: while zoomed, the D-pad owns the level and A owns the
        // marker — show exactly those controls for exactly that duration.
        if (dev.steampad.input.ZoomController.isZooming()) {
            if (cfg.zoomDpadAdjust) {
                hintRight(ctx, tr, type, w - MARGIN, yR, "DUP", "", Text.translatable("steampad.hud.zoom_in"));
                yR += top ? rowH : -rowH;
                hintRight(ctx, tr, type, w - MARGIN, yR, "DDOWN", "", Text.translatable("steampad.hud.zoom_out"));
                yR += top ? rowH : -rowH;
            }
            if (cfg.zoomMarkerEnabled) {
                hintRight(ctx, tr, type, w - MARGIN, yR, "A", "", Text.translatable("steampad.hud.zoom_marker"));
            }
        }
    }

    /** Inventory hints — the in-GUI actions, drawn at the bottom corners while a container is open. */
    public static void renderContainerHints(DrawContext ctx, ControllerType type) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;
        // Hide while the virtual keyboard owns the screen (feedback: "oculta los glifos de UI en
        // Inventario cuando el teclado esté activo, y cuando esté desactivo que regrese") — the
        // keyboard's own footer already carries its own control glyphs, and the two rows overlapped.
        if (dev.steampad.client.keyboard.VirtualKeyboard.isActive()) return;
        TextRenderer tr = mc.textRenderer;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        int rowH = badgeH() + GAP_Y;

        // These are the fixed in-container actions (handled by the cursor dispatch), so the glyphs are
        // constant: A select/place, X take half, Y quick-move, B close, Select = cursor mode.
        String[][] left = {
                {"BACK", "steampad.hud.cursor_mode"},
                {"Y", "steampad.hud.quick_move"},
                {"X", "steampad.hud.split"},
        };
        String[][] right = {
                {"A", "steampad.hud.take_put"},
                {"B", "steampad.hud.close"},
        };
        int yL = h - MARGIN - badgeH();
        for (String[] hint : left)  { hintLeft(ctx, tr, type, MARGIN, yL, hint[0], "", Text.translatable(hint[1])); yL -= rowH; }
        int yR = h - MARGIN - badgeH();
        for (String[] hint : right) { hintRight(ctx, tr, type, w - MARGIN, yR, hint[0], "", Text.translatable(hint[1])); yR -= rowH; }

        // OPEN_KEYBOARD — a user-configured chord (unbound by default), so it's dynamic rather than
        // one of the fixed pairs above: only shown once the player has actually assigned it (feedback:
        // "en inventario no aparece en glifos la combinacion de abrir teclado"). Appended below the
        // right column so it doesn't reflow the fixed hints above it.
        long handle = ActiveControllerService.getActiveHandle();
        if (handle != 0L && ControllerManager.isFallbackHandle(handle)) {
            ControllerConfig cfg = ConfigManager.getControllerConfig(handle);
            String kbBtn = GamepadBinds.button(cfg, GamepadBinds.Bind.OPEN_KEYBOARD);
            if (!kbBtn.isEmpty()) {
                String kbChord = GamepadBinds.chord(cfg, GamepadBinds.Bind.OPEN_KEYBOARD);
                hintRight(ctx, tr, type, w - MARGIN, yR, kbBtn, kbChord,
                        Text.translatable(GamepadBinds.Bind.OPEN_KEYBOARD.labelKey));
                yR -= rowH;
            }

            // Long-press PAUSE inside an inventory: stack EVERY assigned gameplay bind (with its
            // chord) above the fixed in-container rows — the "reference card" the show-all hold is
            // for. Purely visual; disappears the moment the button is released.
            if (showAllBinds) {
                int yL2 = yL;
                for (Hint hint : LEFT) {
                    String btn = GamepadBinds.button(cfg, hint.bind());
                    if (btn.isEmpty()) continue;
                    hintLeft(ctx, tr, type, MARGIN, yL2, btn, GamepadBinds.chord(cfg, hint.bind()),
                            Text.translatable(hint.labelKey()));
                    yL2 -= rowH;
                }
                for (Hint hint : RIGHT) {
                    String btn = GamepadBinds.button(cfg, hint.bind());
                    if (btn.isEmpty()) continue;
                    hintRight(ctx, tr, type, w - MARGIN, yR, btn, GamepadBinds.chord(cfg, hint.bind()),
                            Text.translatable(hint.labelKey()));
                    yR -= rowH;
                }
            }
        }
    }

    /** Width of a literal "+" chord separator at the current badge height. */
    private static int plusWidth(TextRenderer tr) { return tr.getWidth("+") + 2; }

    /**
     * Draws a hint badge whose button glyph may be a two-button chord ({@code chordBtn+btn}), left-
     * anchored at (x,y). Fixes chords showing as just their main button (feedback: "si tengo A para
     * saltar pero tengo un chord de DUP+A para el chat, también muestra como solo A como chat,
     * debería mostrar para el chat DUP+A") — {@code chordBtn} empty draws exactly as before.
     */
    private static void hintLeft(DrawContext ctx, TextRenderer tr, ControllerType type, int x, int y,
                                 String btn, String chordBtn, Text label) {
        int bh = badgeH();
        int textY = y + (bh - 8) / 2;
        int cursor = x;
        if (!chordBtn.isEmpty()) {
            cursor += ControllerGlyphs.draw(ctx, tr, cursor, y, bh, type, chordBtn);
            ctx.drawText(tr, Text.literal("+"), cursor + 1, textY, LABEL_COLOR, true);
            cursor += plusWidth(tr);
        }
        cursor += ControllerGlyphs.draw(ctx, tr, cursor, y, bh, type, btn);
        ctx.drawText(tr, label, cursor + 4, textY, LABEL_COLOR, true);
    }

    /** Right-anchored counterpart of {@link #hintLeft} — see its doc for the chord glyph fix. */
    private static void hintRight(DrawContext ctx, TextRenderer tr, ControllerType type, int rightX, int y,
                                  String btn, String chordBtn, Text label) {
        int bh = badgeH();
        int textY = y + (bh - 8) / 2;
        int mainW = ControllerGlyphs.width(tr, bh, btn);
        int totalW = mainW;
        int chordW = 0;
        if (!chordBtn.isEmpty()) {
            chordW = ControllerGlyphs.width(tr, bh, chordBtn);
            totalW += chordW + plusWidth(tr);
        }
        int gx = rightX - totalW;
        int lw = tr.getWidth(label);
        ctx.drawText(tr, label, gx - 4 - lw, textY, LABEL_COLOR, true);
        int cursor = gx;
        if (!chordBtn.isEmpty()) {
            ControllerGlyphs.draw(ctx, tr, cursor, y, bh, type, chordBtn);
            cursor += chordW;
            ctx.drawText(tr, Text.literal("+"), cursor + 1, textY, LABEL_COLOR, true);
            cursor += plusWidth(tr);
        }
        ControllerGlyphs.draw(ctx, tr, cursor, y, bh, type, btn);
    }
}
