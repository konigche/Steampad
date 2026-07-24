package dev.steampad.screen;

import dev.steampad.config.ConfigManager;
import dev.steampad.service.ActiveControllerService;
import dev.steampad.service.ControllerManager;
import dev.steampad.service.UiSoundService;
import dev.steampad.steam.SteamActionRegistry;
import dev.steampad.steam.SteamBootstrap;
import dev.steampad.steam.SteamControllerHandleRef;
import dev.steampad.steam.SteamNativeLoader;
import dev.steampad.util.LogUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ControllerSelectScreen extends SteamPadBaseScreen {

    private static final int CONTENT_TOP  = 64;   // cards sit right below the top buttons (prominent)
    private static final int ENTRY_HEIGHT = 50;
    private static final int ENTRY_MARGIN = 6;

    private static final int PANEL_MARGIN_X = 10;
    private static final int PANEL_LINE_H   = 10;
    /** Diagnostic text is drawn at 3/4 size — informative, not shouting (user request). */
    private static final float PANEL_SCALE  = 0.75f;
    /** On-screen height of the scaled diagnostic block (expanded state: 6 status lines + help + collapse hint). */
    private static final int PANEL_CONTENT_H_EXPANDED = Math.round(8 * PANEL_LINE_H * PANEL_SCALE);
    /** Collapsed state: just the one-line summary. */
    private static final int PANEL_CONTENT_H_COLLAPSED = Math.round(1 * PANEL_LINE_H * PANEL_SCALE) + 2;
    /** Collapsed by default (feedback: the full dump felt like too much info up front) — click to expand. */
    private boolean diagnosticExpanded = false;
    private int panelContentH() { return diagnosticExpanded ? PANEL_CONTENT_H_EXPANDED : PANEL_CONTENT_H_COLLAPSED; }

    // NOTE: colors MUST include a full alpha byte. In MC 1.21.10 DrawContext.drawText no longer forces
    // opaque when the alpha bits are 0, so 0xRRGGBB (alpha 0) renders INVISIBLE — that was why the
    // controller name never showed.
    private static final int COLOR_OK       = 0xFF55FF55;
    private static final int COLOR_FAIL     = 0xFFFF5555;
    private static final int COLOR_WARN     = 0xFFFFCC44;
    private static final int COLOR_LABEL    = 0xFFFFFFFF;
    private static final int COLOR_DIM      = 0xFFAAAAAA;
    private static final int COLOR_ACTIVE   = 0xFF44CC44;
    private static final int COLOR_PANEL_BG = 0x88000000;

    private final Screen parent;
    private List<SteamControllerHandleRef> controllers = new ArrayList<>();
    private long lastKnownActive = -1L;

    public ControllerSelectScreen(Screen parent) {
        super(Text.translatable("steampad.screen.controller_select.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.controllers = new ArrayList<>(ControllerManager.getConnectedControllers());
        LogUtil.info("[SteamPad] ControllerSelectScreen opened. Detected {} controller(s) (source: {}).",
            controllers.size(), ControllerManager.activeSource());

        // Log expected AppID file locations when Steam is unavailable (diagnostics on screen open)
        if (!SteamBootstrap.isSteamAvailable() && SteamNativeLoader.isLoaded()) {
            String wd = System.getProperty("user.dir", "?");
            LogUtil.warn("[SteamPad] Steam not available. Expected steam_appid.txt locations:");
            LogUtil.warn("[SteamPad]   {}/steam_appid.txt  (or steamappid.txt)", wd);
            if (client != null) {
                LogUtil.warn("[SteamPad]   {}/steam_appid.txt  (or steamappid.txt)",
                    client.runDirectory.getAbsolutePath());
            }
        }

        // Row 1: Global Settings | Refresh (below the header bar)
        int row1Y = 40;
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("steampad.screen.global_settings.short"),
            btn -> { UiSoundService.playSelect(); client.setScreen(new GlobalSettingsScreen(this)); }
        ).dimensions(this.width / 2 - 130, row1Y, 120, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("steampad.button.refresh"),
            btn -> { UiSoundService.playSelect(); refresh(); }
        ).dimensions(this.width / 2 + 10, row1Y, 120, 20).build());

        // Controller entry rows — responsive (fit narrow handheld screens). Buttons live on the
        // right of each card: [Select][Settings] on top, [Default] below.
        int bx = boxX();
        int bw = boxW();
        int btnCol = bx + bw - 152;          // left edge of the button column
        int entryY = CONTENT_TOP;
        for (SteamControllerHandleRef ref : controllers) {
            final long handle = ref.handle;
            final String name = ref.displayName;
            final int finalEntryY = entryY;
            boolean isPreferred = name.equals(ConfigManager.getGlobal().preferredControllerName);

            this.addDrawableChild(ButtonWidget.builder(
                Text.translatable(ActiveControllerService.getActiveHandle() == handle
                    ? "steampad.button.active"
                    : "steampad.button.select"),
                btn -> {
                    UiSoundService.playSelect();
                    ActiveControllerService.setActive(handle);
                    refresh();
                }
            ).dimensions(btnCol, finalEntryY + 7, 72, 18).build());

            this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("steampad.button.settings"),
                btn -> { UiSoundService.playSelect(); client.setScreen(new ControllerBasicSettingsScreen(this, handle)); }
            ).dimensions(btnCol + 76, finalEntryY + 7, 72, 18).build());

            this.addDrawableChild(ButtonWidget.builder(
                Text.translatable(isPreferred ? "steampad.button.default_active" : "steampad.button.default"),
                btn -> {
                    UiSoundService.playSelect();
                    var g = ConfigManager.getGlobal();
                    g.preferredControllerName = isPreferred ? "" : name;
                    ConfigManager.saveGlobal();
                    ActiveControllerService.setActive(handle);
                    refresh();
                }
            ).dimensions(btnCol, finalEntryY + 27, 148, 18).build());

            entryY += ENTRY_HEIGHT + ENTRY_MARGIN;
        }

        // "Retry Steam Init" — shown when Steam failed but natives loaded and no controllers
        if (controllers.isEmpty() && !SteamBootstrap.isSteamAvailable() && SteamNativeLoader.isLoaded()) {
            this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("steampad.diag.retry_steam_init"),
                btn -> { UiSoundService.playSelect(); retrySteamInit(); }
            ).dimensions(this.width / 2 - 75, CONTENT_TOP + 48, 150, 20).build());
        }

        // Back button
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, btn -> close())
            .dimensions(this.width / 2 - 75, this.height - 28, 150, 20).build());

        lastKnownActive = ActiveControllerService.getActiveHandle();
    }

    private void refresh() {
        List<SteamControllerHandleRef> live = ControllerManager.getConnectedControllers();
        LogUtil.info("[SteamPad] Refreshing controller list. Found {} controller(s).", live.size());
        this.clearChildren();
        this.init();
    }

    private void retrySteamInit() {
        LogUtil.info("[SteamPad] Retrying Steam initialization from ControllerSelectScreen...");
        boolean ok = SteamBootstrap.init();
        LogUtil.info("[SteamPad] Retry result: {}", ok ? "Steam init OK — controllers should appear" : "Still failed — check log for details");
        refresh();
    }

    @Override
    public void tick() {
        super.tick();
        List<SteamControllerHandleRef> live = ControllerManager.getConnectedControllers();
        long nowActive = ActiveControllerService.getActiveHandle();
        if (live.size() != controllers.size() || !live.equals(controllers) || nowActive != lastKnownActive) {
            LogUtil.debug("[SteamPad] Controller state changed during screen open — auto-refreshing.");
            refresh();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Step 1 — shared chrome (header bar with title + accent, footer band) over the native blur.
        renderChrome(context);

        // Developer badge (top-right, small): build version · ElDon · MX flag · Yucatán flag · heart.
        drawDevBadge(context);

        // Step 2 — diagnostic panel background fill (compact, anchored to the bottom)
        int panelX = PANEL_MARGIN_X;
        int panelW = this.width - PANEL_MARGIN_X * 2;
        int panelTop = panelTop();
        int panelBot = panelTop + panelContentH() + 4;
        context.fill(panelX, panelTop - 2, panelX + panelW, panelBot, COLOR_PANEL_BG);

        // Step 3 — controller entry box fills
        int entryY = CONTENT_TOP;
        for (SteamControllerHandleRef ref : controllers) {
            boolean isActive = ActiveControllerService.getActiveHandle() == ref.handle;
            int boxColor    = isActive ? 0xFF1A4A1A : 0xFF252525;
            int borderColor = isActive ? COLOR_ACTIVE : 0xFF505050;
            int boxX = boxX();
            int boxW = boxW();

            context.fill(boxX,            entryY,                    boxX + boxW, entryY + ENTRY_HEIGHT, boxColor);
            context.fill(boxX,            entryY,                    boxX + boxW, entryY + 1,            borderColor);
            context.fill(boxX,            entryY + ENTRY_HEIGHT - 1, boxX + boxW, entryY + ENTRY_HEIGHT, borderColor);
            context.fill(boxX,            entryY,                    boxX + 1,    entryY + ENTRY_HEIGHT, borderColor);
            context.fill(boxX + boxW - 1, entryY,                    boxX + boxW, entryY + ENTRY_HEIGHT, borderColor);

            entryY += ENTRY_HEIGHT + ENTRY_MARGIN;
        }

        // Step 4 — buttons (Drawable children)
        super.render(context, mouseX, mouseY, delta);

        // Step 5 — text drawn on top.
        // Text rendering uses drawText(textRenderer, text, x, y, color, shadow=true).
        // All drawTextWithShadow overloads (Text, OrderedText, String) were removed in MC 1.21.10.
        // drawText(TextRenderer, Text, ...) is the underlying call used by drawCenteredTextWithShadow
        // and is confirmed stable in both MC 1.21.4 and 1.21.10.
        // Title is drawn by renderChrome(); panel content here.
        renderDiagnosticPanel(context, panelX + 4);

        entryY = CONTENT_TOP;
        for (SteamControllerHandleRef ref : controllers) {
            boolean isActive = ActiveControllerService.getActiveHandle() == ref.handle;
            boolean isPreferred = ref.displayName.equals(ConfigManager.getGlobal().preferredControllerName);
            int boxX = boxX();
            String name = (ref.displayName == null || ref.displayName.isBlank())
                ? ("Controller " + ref.handle) : ref.displayName;

            // Brand/category logo on the left, drawn from primitives (Steam Deck, 8BitDo, Xbox, …).
            int iconSize = 36;
            try {
                dev.steampad.client.ui.ControllerBrandIcon.draw(context,
                    boxX + 7, entryY + (ENTRY_HEIGHT - iconSize) / 2, iconSize, ref.type, ref.displayName);
            } catch (Throwable ignored) { /* never let the logo abort the name/text below */ }
            context.fill(boxX + 50, entryY + 8, boxX + 51, entryY + ENTRY_HEIGHT - 8, 0xFF3A3F47);
            int textX = boxX + 56;

            context.drawText(textRenderer,
                Text.literal(name + (isActive ? " ✓" : "") + (isPreferred ? " *" : "")),
                textX, entryY + 9, isActive ? COLOR_OK : COLOR_LABEL, true);
            // Sub-line: manufacturer/brand (e.g. "8BitDo", "Sony"), not the raw enum ("GENERIC").
            context.drawText(textRenderer,
                Text.literal(dev.steampad.client.ui.ControllerBrandIcon.manufacturer(ref.type, ref.displayName)),
                textX, entryY + 21, COLOR_DIM, true);
            context.drawText(textRenderer,
                Text.translatable(isActive ? "steampad.controller.status.active" : "steampad.controller.status.connected"),
                textX, entryY + 33, isActive ? COLOR_OK : 0xFF888888, true);

            entryY += ENTRY_HEIGHT + ENTRY_MARGIN;
        }

        // Empty state — no controllers from EITHER backend (Steam Input or GLFW fallback)
        if (controllers.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("steampad.diag.no_controllers"),
                this.width / 2, CONTENT_TOP + 12, COLOR_DIM);
            context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("steampad.diag.connect_and_refresh"),
                this.width / 2, CONTENT_TOP + 24, COLOR_WARN);
            if (!SteamBootstrap.isSteamAvailable()) {
                context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("steampad.diag.glfw_fallback_note"),
                    this.width / 2, CONTENT_TOP + 36, COLOR_DIM);
            }
        }
    }

    private void renderDiagnosticPanel(DrawContext ctx, int x) {
        boolean nativesOk = SteamNativeLoader.isLoaded();
        boolean steamOk   = SteamBootstrap.isSteamAvailable();
        boolean inputOk   = SteamBootstrap.isInputAvailable();
        boolean actionsOk = SteamActionRegistry.isRegistered()
                         && SteamActionRegistry.isValidHandle(SteamActionRegistry.actionSetGameplay);
        ControllerManager.Source source = ControllerManager.activeSource();
        int controllerCount = ControllerManager.getConnectedControllers().size();
        String activeName = ActiveControllerService.getActiveRef()
                .map(r -> r.displayName).orElse("None");
        boolean appIdExists = checkAppIdFileExists();

        // The whole block renders at PANEL_SCALE (smaller, denser info text). Draw in LOCAL
        // coordinates under a translate+scale so the line layout stays untouched.
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, panelTop());
        ctx.getMatrices().scale(PANEL_SCALE, PANEL_SCALE);
        x = 0;
        int y = 0;

        if (!diagnosticExpanded) {
            // Collapsed (default): one line — what's actually driving controllers right now — plus a
            // hint to expand. Feedback: the full dump felt cramped/like too much info up front.
            String sourceLabel = tr(sourceLabelKey(source));
            int c = source == ControllerManager.Source.NONE ? COLOR_WARN : COLOR_OK;
            drawStatusLine(ctx, x, y, tr("steampad.diag.working_via"),
                    sourceLabel + " " + tr("steampad.diag.click_for_details"), c);
            ctx.getMatrices().popMatrix();
            return;
        }

        // Line 1 — Steam API status. When a fallback (SDL3/GLFW) is actually driving controllers,
        // a missing Steam context is NOT a hard failure — show it as a warning, not alarming red.
        boolean fallbackActive = !steamOk
                && (source == ControllerManager.Source.SDL3 || source == ControllerManager.Source.GLFW_FALLBACK);
        String steamApiLabel = tr("steampad.diag.steam_api");
        if (!nativesOk) {
            drawStatusLine(ctx, x, y, steamApiLabel, tr("steampad.diag.v.natives_not_loaded"), COLOR_FAIL);
        } else if (SteamBootstrap.isAttachSkippedByPolicy()) {
            // Deliberate policy, not a failure: on desktop the mod does not attach to Steam, so raw
            // SDL3 keeps full control of the pads (attaching makes Steam seize them — D033).
            drawStatusLine(ctx, x, y, steamApiLabel, tr("steampad.diag.v.not_attached_desktop"), COLOR_OK);
        } else if (!steamOk) {
            int c = fallbackActive ? COLOR_WARN : COLOR_FAIL;
            if (appIdExists) {
                drawStatusLine(ctx, x, y, steamApiLabel, tr("steampad.diag.v.not_initialized"), c);
            } else if (fallbackActive) {
                drawStatusLine(ctx, x, y, steamApiLabel, tr("steampad.diag.v.not_in_steam_context"), c);
            } else {
                drawStatusLine(ctx, x, y, steamApiLabel, tr("steampad.diag.v.no_appid_file"), c);
            }
        } else {
            drawStatusLine(ctx, x, y, steamApiLabel, tr("steampad.diag.v.ok"), COLOR_OK);
        }
        y += PANEL_LINE_H;

        // Line 2 — Steam Input layer
        String steamInputLabel = tr("steampad.diag.steam_input");
        if (steamOk && !inputOk) {
            drawStatusLine(ctx, x, y, steamInputLabel, tr("steampad.diag.v.init_failed"), COLOR_FAIL);
        } else if (!steamOk) {
            drawStatusLine(ctx, x, y, steamInputLabel, tr("steampad.diag.v.unavailable"), COLOR_DIM);
        } else {
            drawStatusLine(ctx, x, y, steamInputLabel, tr("steampad.diag.v.ready"), COLOR_OK);
        }
        y += PANEL_LINE_H;

        // Line 3 — Active input source (which backend is actually providing controllers)
        drawStatusLine(ctx, x, y, tr("steampad.diag.input_source"), tr(sourceLabelKey(source)),
                source == ControllerManager.Source.NONE ? COLOR_WARN : COLOR_OK);
        y += PANEL_LINE_H;

        // Line 4 — Controller count
        drawStatusLine(ctx, x, y, tr("steampad.diag.controllers"), String.valueOf(controllerCount),
                controllerCount > 0 ? COLOR_OK : COLOR_WARN);
        y += PANEL_LINE_H;

        // Line 4 — Active controller
        boolean hasActive = ActiveControllerService.hasActiveController();
        drawStatusLine(ctx, x, y, tr("steampad.diag.active"), activeName,
                hasActive ? COLOR_OK : COLOR_DIM);
        y += PANEL_LINE_H;

        // Line 5 — Action Sets / VDF
        String actionSetsLabel = tr("steampad.diag.action_sets");
        if (!inputOk) {
            drawStatusLine(ctx, x, y, actionSetsLabel, tr("steampad.diag.v.steam_input_unavailable"), COLOR_DIM);
        } else if (!actionsOk) {
            drawStatusLine(ctx, x, y, actionSetsLabel, tr("steampad.diag.v.not_loaded_vdf"), COLOR_FAIL);
        } else {
            drawStatusLine(ctx, x, y, actionSetsLabel, tr("steampad.diag.v.loaded"), COLOR_OK);
        }
        y += PANEL_LINE_H;

        // Line 6 — Contextual help / action hint
        String help = buildHelpMessage(nativesOk, steamOk, inputOk, controllerCount, actionsOk, appIdExists, source);
        if (help != null) {
            ctx.drawText(textRenderer, Text.literal(help), x, y, COLOR_WARN, true);
        }
        y += PANEL_LINE_H;
        ctx.drawText(textRenderer, Text.translatable("steampad.diag.click_to_collapse"), x, y, COLOR_DIM, true);

        ctx.getMatrices().popMatrix();
    }

    /** Shorthand: resolved (localized) string for a translation key — the diagnostic panel draws
     *  with {@link #drawStatusLine} (String label/value), so keys are resolved once per call here. */
    private static String tr(String key) { return Text.translatable(key).getString(); }

    private static String sourceLabelKey(ControllerManager.Source source) {
        return switch (source) {
            case STEAM_INPUT   -> "steampad.diag.src_steam_input";
            case SDL3          -> "steampad.diag.src_sdl3";
            case GLFW_FALLBACK -> "steampad.diag.src_glfw";
            case NONE          -> "steampad.diag.src_none";
        };
    }

    /** Small developer badge in the top-right corner: version · ElDon · flags · heart. */
    private void drawDevBadge(DrawContext ctx) {
        String version = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("steampad")
                .map(c -> "v" + c.getMetadata().getVersion().getFriendlyString())
                .orElse("");
        int right = this.width - 4;
        int fy = 4;          // flags/heart row top
        int fh = 9, fw = 14, gap = 3;

        // Right-to-left: heart, Yucatán flag, Mexico flag.
        int x = right - 7;
        drawHeart(ctx, x, fy, 0xFFE23B4E);
        x -= (fw + gap);
        drawYucatanFlag(ctx, x, fy, fw, fh);
        x -= (fw + gap);
        drawMexicoFlag(ctx, x, fy, fw, fh);

        // "ElDon" then version, right-aligned to the left of the flags.
        int textRight = x - 4;
        String dev = "ElDon";
        int devW = textRenderer.getWidth(dev);
        ctx.drawText(textRenderer, Text.literal(dev), textRight - devW, fy + 1, COLOR_LABEL, true);
        if (!version.isEmpty()) {
            int vW = textRenderer.getWidth(version);
            ctx.drawText(textRenderer, Text.literal(version), textRight - devW - 6 - vW, fy + 1, COLOR_DIM, true);
        }
    }

    private void drawMexicoFlag(DrawContext ctx, int x, int y, int w, int h) {
        int t = w / 3;
        ctx.fill(x, y, x + t, y + h, 0xFF006847);             // green
        ctx.fill(x + t, y, x + 2 * t, y + h, 0xFFFFFFFF);      // white
        ctx.fill(x + 2 * t, y, x + w, y + h, 0xFFCE1126);      // red
        ctx.fill(x + t + t / 2 - 1, y + h / 2 - 1, x + t + t / 2 + 1, y + h / 2 + 1, 0xFF7A5230); // emblem dot
    }

    private void drawYucatanFlag(DrawContext ctx, int x, int y, int w, int h) {
        int hoist = Math.max(4, w * 2 / 5);
        // Fly: five horizontal stripes red/white/red/white/red.
        for (int i = 0; i < 5; i++) {
            int sy = y + i * h / 5;
            int sy2 = (i == 4) ? y + h : y + (i + 1) * h / 5;
            ctx.fill(x + hoist, sy, x + w, sy2, (i % 2 == 0) ? 0xFFCE1126 : 0xFFFFFFFF);
        }
        // Hoist: green field with five white stars (corners + center).
        ctx.fill(x, y, x + hoist, y + h, 0xFF1C7A3D);
        int cx = x + hoist / 2, cy = y + h / 2;
        ctx.fill(cx, cy, cx + 1, cy + 1, 0xFFFFFFFF);
        ctx.fill(x + 1, y + 1, x + 2, y + 2, 0xFFFFFFFF);
        ctx.fill(x + hoist - 2, y + 1, x + hoist - 1, y + 2, 0xFFFFFFFF);
        ctx.fill(x + 1, y + h - 2, x + 2, y + h - 1, 0xFFFFFFFF);
        ctx.fill(x + hoist - 2, y + h - 2, x + hoist - 1, y + h - 1, 0xFFFFFFFF);
    }

    private void drawHeart(DrawContext ctx, int x, int y, int color) {
        // 7x6 pixel heart.
        String[] rows = { "0110110", "1111111", "1111111", "0111110", "0011100", "0001000" };
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int c = 0; c < row.length(); c++) {
                if (row.charAt(c) == '1') ctx.fill(x + c, y + r, x + c + 1, y + r + 1, color);
            }
        }
    }

    private void drawStatusLine(DrawContext ctx, int x, int y, String label, String value, int valueColor) {
        // Uses DrawContext.drawText(TextRenderer, Text, int, int, int, boolean) with shadow=true.
        // All drawTextWithShadow overloads (Text, OrderedText, String) were removed in MC 1.21.10.
        // drawText(TextRenderer, Text, ...) is the underlying method called by drawCenteredTextWithShadow
        // and is confirmed stable in MC 1.21.10.
        ctx.drawText(textRenderer, Text.literal(label), x, y, COLOR_DIM, true);
        ctx.drawText(textRenderer, Text.literal(value), x + 80, y, valueColor, true);
    }

    private boolean checkAppIdFileExists() {
        return new File("steam_appid.txt").exists()
            || new File("steamappid.txt").exists()
            || (client != null && new File(client.runDirectory, "steam_appid.txt").exists())
            || (client != null && new File(client.runDirectory, "steamappid.txt").exists());
    }

    private String shortRunDir() {
        if (client == null) return System.getProperty("user.dir", "?");
        String p = client.runDirectory.getAbsolutePath();
        return p.length() > 45 ? "..." + p.substring(p.length() - 42) : p;
    }

    private String buildHelpMessage(boolean nativesOk, boolean steamOk, boolean inputOk,
                                    int controllerCount, boolean actionsOk, boolean appIdExists,
                                    ControllerManager.Source source) {
        if (!nativesOk) {
            return tr("steampad.diag.help.enable_natives");
        }
        if (!steamOk) {
            // A fallback is detecting the pad even though Steam Input is down — name the actual
            // source (SDL3/GLFW) instead of nagging about steam_appid.txt, which would wrongly imply
            // detection failed.
            if (controllerCount > 0) {
                String src = source == ControllerManager.Source.SDL3 ? "SDL3" : "GLFW";
                return Text.translatable("steampad.diag.help.detected_via_fallback", src).getString();
            }
            if (!appIdExists) {
                // Show truncated path to the run directory where the file is expected
                return Text.translatable("steampad.diag.help.add_appid_file", shortRunDir()).getString();
            }
            return tr("steampad.diag.help.steam_not_running");
        }
        if (!inputOk) {
            return tr("steampad.diag.help.steam_input_failed");
        }
        if (controllerCount == 0) {
            return tr("steampad.diag.help.connect_and_refresh");
        }
        if (!actionsOk) {
            return tr("steampad.diag.help.install_vdf");
        }
        return null;
    }

    /** Responsive controller-card geometry (fits narrow handheld widths). */
    private int boxW() { return Math.min(420, this.width - 16); }
    private int boxX() { return (this.width - boxW()) / 2; }

    /** Diagnostic panel sits at the bottom, above the footer (content drawn at PANEL_SCALE). */
    private int panelTop() { return this.height - FOOTER_H - panelContentH() - 8; }

    /** Click anywhere in the diagnostic panel (that isn't already a widget) toggles collapsed/expanded. */
    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) return true;
        int panelX = PANEL_MARGIN_X;
        int panelW = this.width - PANEL_MARGIN_X * 2;
        int panelTop = panelTop();
        int panelBot = panelTop + panelContentH() + 4;
        if (click.x() >= panelX && click.x() <= panelX + panelW
                && click.y() >= panelTop - 2 && click.y() <= panelBot) {
            diagnosticExpanded = !diagnosticExpanded;
            UiSoundService.playNavigate();
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}
