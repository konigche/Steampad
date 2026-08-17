package dev.steampad.client.hud;

import dev.steampad.client.ui.ControllerGlyphs;
import dev.steampad.config.ConfigManager;
import dev.steampad.config.ControllerConfig;
import dev.steampad.config.GlobalConfig;
import dev.steampad.input.GamepadBinds;
import dev.steampad.input.GamepadInputDispatcher;
import dev.steampad.service.ActiveControllerService;
import dev.steampad.service.ControllerManager;
import dev.steampad.steam.SteamControllerHandleRef.ControllerType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

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
    // per-hint tier check in render()); FULL = all the always-available ones. Zoom in/out are NOT here
    // on purpose: they only exist while the zoom is active, and the contextual zoom block below draws
    // them from their bindings at exactly that moment. Most NORMAL-tier hints use a short hand-picked
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

    /**
     * LB/RB's hint text depends on {@link ControllerConfig.HotbarRadialMode}: OFF keeps the classic
     * "Anterior"/"Siguiente" scroll labels, any other mode means the same physical buttons now open
     * the item radial wheels instead — RB=Hotbar, LB=Inventario/mochila (swapped from the original
     * LB/RB assignment per hardware feedback, D127) — so the hint must say what actually happens
     * (feedback: "cuando esta activo el sistema... no actualizaste los keybinds en gameplay").
     */
    private static Component hintLabel(Hint hint, ControllerConfig cfg) {
        if (cfg.hotbarRadialMode != ControllerConfig.HotbarRadialMode.OFF) {
            if (hint.bind() == GamepadBinds.Bind.HOTBAR_PREV) {
                return Component.translatable("steampad.hud.item_radial_inventory");
            }
            if (hint.bind() == GamepadBinds.Bind.HOTBAR_NEXT) {
                return Component.translatable("steampad.hud.item_radial_hotbar");
            }
        }
        return Component.translatable(hint.labelKey());
    }
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

    public static void render(GuiGraphics ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;

        long handle = ActiveControllerService.getActiveHandle();
        if (handle == 0L || !ControllerManager.isFallbackHandle(handle)) return;

        ControllerConfig cfg = ConfigManager.getControllerConfig(handle);

        // D122/D123: during a REAL emote, none of the normal gameplay hints are actionable (you
        // can't meaningfully attack/interact/open inventory mid-dance) — replace the whole row with
        // ONLY the two D-pad zoom hints, a full override rather than the additive/selective-hide
        // pattern the FOV-zoom block below uses. Checked before showIngameButtonGuide/mixedInput below
        // on purpose: the emote hint should show even for a player who has the normal button guide
        // turned off entirely — it's the only way to discover the feature exists.
        //
        // ALSO checked before the hudHidden bail below (round-4 hardware report: "no se ven los Binds
        // de DUP y DDOWN"): the emote itself FORCES mc.options.hudHidden=true for the dance's duration
        // (EmoteAnimator.clientTick) — bailing on hudHidden first meant the one overlay that is
        // supposed to survive the hidden HUD was hidden by its own feature. The vanilla HUD stays
        // hidden either way; only these two hints render on top.
        if (dev.steampad.emote.EmoteAnimator.isLocalRealEmotePlaying()) {
            renderEmoteZoomHints(ctx, mc, cfg);
            return;
        }
        if (mc.options.hideGui) return;

        if (!cfg.showIngameButtonGuide) return;
        // Mixed input OFF: hide the glyphs while the mouse is the active device; the controller brings
        // them back. Mixed input ON: always show (both devices coexist).
        if (!cfg.mixedInput && dev.steampad.input.InputRouter.isMouse()) return;

        ControllerType type = ActiveControllerService.getActiveRef()
                .map(r -> r.type).orElse(ControllerType.GENERIC);
        Font tr = mc.font;
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        boolean top = cfg.ingameButtonGuidePosition == ControllerConfig.ButtonGuidePosition.TOP;
        int rowH = badgeH() + GAP_Y;

        // Radial (or the independent emote wheel, FASE 63 decoupling) open → the wheel overlay
        // carries its own control glyphs (B9); the gameplay hints would be wrong while it owns
        // input, so draw nothing here.
        if (dev.steampad.radial.RadialMenuController.isOpen()
                || dev.steampad.emote.EmoteWheelController.isOpen()
                || dev.steampad.itemradial.ItemRadialController.isOpen()) return;

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
            hintLeft(ctx, tr, type, MARGIN, yL, btn, chordBtn, hintLabel(hint, cfg));
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
            hintRight(ctx, tr, type, w - MARGIN, yR, btn, chordBtn, hintLabel(hint, cfg));
            yR += top ? rowH : -rowH;
        }

        // Mod bindings assigned in Botones, only where they are live (see drawExtraRows).
        yR = drawExtraRows(ctx, mc, tr, type, cfg, w, yR, rowH, top);

        // Contextual, REAL-TIME zoom hints: while zoomed, the D-pad owns the level and A owns the
        // marker — show exactly those controls for exactly that duration.
        if (dev.steampad.input.ZoomController.isZooming()) {
            if (cfg.zoomDpadAdjust) {
                // Resolved from the bindings, not hardcoded to the D-pad: moving Zoom in/out to LB/RB
                // has to move the glyphs with them, or the guide would point at buttons that no longer
                // do it. An unbound one draws nothing, same rule as every other hint row.
                String zIn = GamepadBinds.button(cfg, GamepadBinds.Bind.ZOOM_IN);
                String zOut = GamepadBinds.button(cfg, GamepadBinds.Bind.ZOOM_OUT);
                if (!zIn.isEmpty()) {
                    hintRight(ctx, tr, type, w - MARGIN, yR, zIn,
                            GamepadBinds.chord(cfg, GamepadBinds.Bind.ZOOM_IN),
                            Component.translatable("steampad.hud.zoom_in"));
                    yR += top ? rowH : -rowH;
                }
                if (!zOut.isEmpty()) {
                    hintRight(ctx, tr, type, w - MARGIN, yR, zOut,
                            GamepadBinds.chord(cfg, GamepadBinds.Bind.ZOOM_OUT),
                            Component.translatable("steampad.hud.zoom_out"));
                    yR += top ? rowH : -rowH;
                }
            }
            if (cfg.zoomMarkerEnabled) {
                hintRight(ctx, tr, type, w - MARGIN, yR, "A", "", Component.translatable("steampad.hud.zoom_marker"));
            }
        }
    }

    /**
     * The full-override hint row shown DURING a real emote (point 3/D122) — ONLY D-pad up/down,
     * reusing the exact same "steampad.hud.zoom_in"/"zoom_out" labels the FOV-zoom hints already use
     * (already translated ×3 languages; the text "Zoom in"/"Zoom out" reads correctly here too — it's
     * still a zoom, just via camera distance instead of FOV). Independent of
     * {@code cfg.showIngameButtonGuide}/{@code mixedInput} — see {@link #render}'s own note for why.
     */
    private static void renderEmoteZoomHints(GuiGraphics ctx, Minecraft mc, ControllerConfig cfg) {
        ControllerType type = ActiveControllerService.getActiveRef()
                .map(r -> r.type).orElse(ControllerType.GENERIC);
        Font tr = mc.font;
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        boolean top = cfg.ingameButtonGuidePosition == ControllerConfig.ButtonGuidePosition.TOP;
        int rowH = badgeH() + GAP_Y;

        int yR = top ? MARGIN + 2 : h - MARGIN - badgeH();
        hintRight(ctx, tr, type, w - MARGIN, yR, "DUP", "", Component.translatable("steampad.hud.zoom_in"));
        yR += top ? rowH : -rowH;
        hintRight(ctx, tr, type, w - MARGIN, yR, "DDOWN", "", Component.translatable("steampad.hud.zoom_out"));
    }

    /** Inventory hints — the in-GUI actions, drawn at the bottom corners while a container is open. */
    public static void renderContainerHints(GuiGraphics ctx, ControllerType type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        // Hide while the virtual keyboard owns the screen (feedback: "oculta los glifos de UI en
        // Inventario cuando el teclado esté activo, y cuando esté desactivo que regrese") — the
        // keyboard's own footer already carries its own control glyphs, and the two rows overlapped.
        if (dev.steampad.client.keyboard.VirtualKeyboard.isActive()) return;
        Font tr = mc.font;
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
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
        for (String[] hint : left)  { hintLeft(ctx, tr, type, MARGIN, yL, hint[0], "", Component.translatable(hint[1])); yL -= rowH; }
        int yR = h - MARGIN - badgeH();
        for (String[] hint : right) { hintRight(ctx, tr, type, w - MARGIN, yR, hint[0], "", Component.translatable(hint[1])); yR -= rowH; }

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
                        Component.translatable(GamepadBinds.Bind.OPEN_KEYBOARD.labelKey));
                yR -= rowH;
            }

            // The mod bindings that are live inside THIS screen, right under the fixed rows.
            yR = drawExtraRows(ctx, mc, tr, type, cfg, w, yR, rowH, false);

            // Long-press PAUSE inside an inventory: stack EVERY assigned gameplay bind (with its
            // chord) above the fixed in-container rows — the "reference card" the show-all hold is
            // for. Purely visual; disappears the moment the button is released.
            if (showAllBinds) {
                int yL2 = yL;
                for (Hint hint : LEFT) {
                    String btn = GamepadBinds.button(cfg, hint.bind());
                    if (btn.isEmpty()) continue;
                    hintLeft(ctx, tr, type, MARGIN, yL2, btn, GamepadBinds.chord(cfg, hint.bind()),
                            Component.translatable(hint.labelKey()));
                    yL2 -= rowH;
                }
                for (Hint hint : RIGHT) {
                    String btn = GamepadBinds.button(cfg, hint.bind());
                    if (btn.isEmpty()) continue;
                    hintRight(ctx, tr, type, w - MARGIN, yR, btn, GamepadBinds.chord(cfg, hint.bind()),
                            Component.translatable(hint.labelKey()));
                    yR -= rowH;
                }
            }
        }
    }

    /**
     * Draws one right-anchored row per mod binding that is LIVE right now, and returns the next free y.
     *
     * <p>Two reports, one method. First: "cuando se asigna un botón no aparece en la UI de gameplay,
     * entonces no sé a qué botón se lo asigné" — the hint tables only ever covered the built-in Binds,
     * so a keybind the player wired up themselves, the single hardest one to remember, was the one the
     * guide never mentioned. Then: those rows started showing on the title screen and the pause menu,
     * because they were drawn everywhere. Both are answered by drawing exactly the set that would fire —
     * {@link GamepadInputDispatcher#relevantExtraBinds}, the very map the dispatcher iterates — so the
     * guide cannot advertise a button that does nothing, and cannot stay silent about one that works.
     */
    private static int drawExtraRows(GuiGraphics ctx, Minecraft mc, Font tr, ControllerType type,
                                     ControllerConfig cfg, int w, int yR, int rowH, boolean stackUp) {
        // The mod's own name above its rows, so a linked group reads as what it is ("con una pantalla
        // abierta debe decir algo del mod"). Only when a screen actually links one; never in gameplay.
        String group = dev.steampad.input.ModKeybindContext.linkedGroupName(mc, cfg.extraBinds.values());
        boolean headerDrawn = false;
        for (var en : GamepadInputDispatcher.relevantExtraBinds(mc, cfg).entrySet()) {
            String btn = en.getKey();
            String kbId = en.getValue();
            if (btn == null || btn.isEmpty()) continue;
            if (!dev.steampad.input.ModKeybindContext.isVisible(mc, kbId)) continue;
            if (!group.isEmpty() && !headerDrawn) {
                int gw = tr.width(group);
                ctx.drawString(tr, group, w - MARGIN - gw, yR + (badgeH() - 8) / 2, 0xFF9AA7B4, true);
                yR += stackUp ? rowH : -rowH;
                headerDrawn = true;
            }
            // While zooming, the level/marker buttons are repurposed and their normal action is
            // suppressed — hide the stale row too, exactly as the Bind rows already do.
            if (dev.steampad.input.ZoomController.isButtonRepurposed(cfg, btn)) continue;
            // Marked when it takes a LONG press: a guide that implies a tap will do it is worse than no
            // guide, because the player concludes the binding is broken.
            Component label = dev.steampad.input.LongPressGate.isHold(cfg, btn)
                    ? Component.translatable("steampad.layer.hold_hint", Component.translatable(kbId))
                    : Component.translatable(kbId);
            hintRight(ctx, tr, type, w - MARGIN, yR, btn,
                    cfg.extraChords.getOrDefault(kbId, ""), label);
            yR += stackUp ? rowH : -rowH;
        }
        return yR;
    }

    /** Width of a literal "+" chord separator at the current badge height. */
    private static int plusWidth(Font tr) { return tr.width("+") + 2; }

    /**
     * Draws a hint badge whose button glyph may be a two-button chord ({@code chordBtn+btn}), left-
     * anchored at (x,y). Fixes chords showing as just their main button (feedback: "si tengo A para
     * saltar pero tengo un chord de DUP+A para el chat, también muestra como solo A como chat,
     * debería mostrar para el chat DUP+A") — {@code chordBtn} empty draws exactly as before.
     */
    private static void hintLeft(GuiGraphics ctx, Font tr, ControllerType type, int x, int y,
                                 String btn, String chordBtn, Component label) {
        int bh = badgeH();
        int textY = y + (bh - 8) / 2;
        int cursor = x;
        if (!chordBtn.isEmpty()) {
            cursor += ControllerGlyphs.draw(ctx, tr, cursor, y, bh, type, chordBtn);
            ctx.drawString(tr, Component.literal("+"), cursor + 1, textY, LABEL_COLOR, true);
            cursor += plusWidth(tr);
        }
        cursor += ControllerGlyphs.draw(ctx, tr, cursor, y, bh, type, btn);
        ctx.drawString(tr, label, cursor + 4, textY, LABEL_COLOR, true);
    }

    /** Right-anchored counterpart of {@link #hintLeft} — see its doc for the chord glyph fix. */
    private static void hintRight(GuiGraphics ctx, Font tr, ControllerType type, int rightX, int y,
                                  String btn, String chordBtn, Component label) {
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
        int lw = tr.width(label);
        ctx.drawString(tr, label, gx - 4 - lw, textY, LABEL_COLOR, true);
        int cursor = gx;
        if (!chordBtn.isEmpty()) {
            ControllerGlyphs.draw(ctx, tr, cursor, y, bh, type, chordBtn);
            cursor += chordW;
            ctx.drawString(tr, Component.literal("+"), cursor + 1, textY, LABEL_COLOR, true);
            cursor += plusWidth(tr);
        }
        ControllerGlyphs.draw(ctx, tr, cursor, y, bh, type, btn);
    }
}
