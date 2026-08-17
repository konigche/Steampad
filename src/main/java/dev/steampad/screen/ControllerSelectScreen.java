package dev.steampad.screen;

import dev.steampad.compat.mc.GuiPose;
import dev.steampad.config.ConfigManager;
import dev.steampad.service.ActiveControllerService;
import dev.steampad.service.ControllerClaimService;
import dev.steampad.service.ControllerManager;
import dev.steampad.service.UiSoundService;
import dev.steampad.steam.SteamActionRegistry;
import dev.steampad.steam.SteamBootstrap;
import dev.steampad.steam.SteamControllerHandleRef;
import dev.steampad.steam.SteamNativeLoader;
import dev.steampad.util.LogUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class ControllerSelectScreen extends SteamPadBaseScreen {

    private static final int CONTENT_TOP  = 64;   // cards sit right below the top buttons (prominent)
    private static final int ENTRY_HEIGHT = 50;
    private static final int ENTRY_MARGIN = 6;

    private static final int PANEL_MARGIN_X = 10;
    private static final int PANEL_LINE_H   = 10;
    /** Diagnostic text is drawn at 3/4 size â€” informative, not shouting (user request). */
    private static final float PANEL_SCALE  = 0.75f;
    /** On-screen height of the scaled diagnostic block (expanded state: 6 status lines + help + collapse hint). */
    private static final int PANEL_CONTENT_H_EXPANDED = Math.round(8 * PANEL_LINE_H * PANEL_SCALE);
    /** Collapsed state: just the one-line summary. */
    private static final int PANEL_CONTENT_H_COLLAPSED = Math.round(1 * PANEL_LINE_H * PANEL_SCALE) + 2;
    /** Collapsed by default (feedback: the full dump felt like too much info up front) â€” click to expand. */
    private boolean diagnosticExpanded = false;
    private int panelContentH() { return diagnosticExpanded ? PANEL_CONTENT_H_EXPANDED : PANEL_CONTENT_H_COLLAPSED; }

    // NOTE: colors MUST include a full alpha byte. In MC 1.21.10 DrawContext.drawText no longer forces
    // opaque when the alpha bits are 0, so 0xRRGGBB (alpha 0) renders INVISIBLE â€” that was why the
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
    /** Transient explanation for a refused selection (pad held by another instance); null when none. */
    private Component notice;
    private long noticeUntilMs;
    /** Handles currently held by ANOTHER live instance, refreshed on a timer — see refreshBusySet(). */
    private final java.util.Set<Long> busyHandles = new java.util.HashSet<>();
    private long busyScanAtMs;

    public ControllerSelectScreen(Screen parent) {
        super(Component.translatable("steampad.screen.controller_select.title"));
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
            if (minecraft != null) {
                LogUtil.warn("[SteamPad]   {}/steam_appid.txt  (or steamappid.txt)",
                    minecraft.gameDirectory.getAbsolutePath());
            }
        }

        // Row 1: Global Settings | Refresh (below the header bar)
        int row1Y = 40;
        this.addRenderableWidget(Button.builder(
            Component.translatable("steampad.screen.global_settings.short"),
            btn -> { UiSoundService.playSelect(); minecraft.setScreen(new GlobalSettingsScreen(this)); }
        ).bounds(this.width / 2 - 130, row1Y, 120, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.translatable("steampad.button.refresh"),
            btn -> { UiSoundService.playSelect(); refresh(); }
        ).bounds(this.width / 2 + 10, row1Y, 120, 20).build());

        // Controller entry rows â€” responsive (fit narrow handheld screens). Buttons live on the
        // right of each card: [Select][Settings] on top, [Default] below.
        int bx = boxX();
        int bw = boxW();
        int btnCol = bx + bw - 152;          // left edge of the button column
        int entryY = CONTENT_TOP;
        for (SteamControllerHandleRef ref : controllers) {
            final long handle = ref.handle;
            final String name = ref.displayName;
            final int finalEntryY = entryY;
            boolean isPreferred = ActiveControllerService.isPreferred(handle);

            this.addRenderableWidget(Button.builder(
                Component.translatable(ActiveControllerService.getActiveHandle() == handle
                    ? "steampad.button.active"
                    : "steampad.button.select"),
                btn -> {
                    UiSoundService.playSelect();
                    activateOrExplain(handle);
                    refresh();
                }
            ).bounds(btnCol, finalEntryY + 7, 72, 18).build());

            this.addRenderableWidget(Button.builder(
                Component.translatable("steampad.button.settings"),
                btn -> { UiSoundService.playSelect(); minecraft.setScreen(new ControllerBasicSettingsScreen(this, handle)); }
            ).bounds(btnCol + 76, finalEntryY + 7, 72, 18).build());

            this.addRenderableWidget(Button.builder(
                Component.translatable(isPreferred ? "steampad.button.default_active" : "steampad.button.default"),
                btn -> {
                    UiSoundService.playSelect();
                    // By persistent identity, not by name — see ActiveControllerService.setPreferred.
                    // The preference is stored either way: it is this instance's stated choice for
                    // future launches and is meaningful even while the pad is busy right now.
                    ActiveControllerService.setPreferred(isPreferred ? 0L : handle);
                    if (!isPreferred) activateOrExplain(handle);
                    refresh();
                }
            ).bounds(btnCol, finalEntryY + 27, 148, 18).build());

            entryY += ENTRY_HEIGHT + ENTRY_MARGIN;
        }

        // "Retry Steam Init" â€” shown when Steam failed but natives loaded and no controllers
        if (controllers.isEmpty() && !SteamBootstrap.isSteamAvailable() && SteamNativeLoader.isLoaded()) {
            this.addRenderableWidget(Button.builder(
                Component.translatable("steampad.diag.retry_steam_init"),
                btn -> { UiSoundService.playSelect(); retrySteamInit(); }
            ).bounds(this.width / 2 - 75, CONTENT_TOP + 48, 150, 20).build());
        }

        // Back button
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, btn -> onClose())
            .bounds(this.width / 2 - 75, this.height - 28, 150, 20).build());

        lastKnownActive = ActiveControllerService.getActiveHandle();
    }

    /**
     * Activates a controller the user explicitly picked — unless another live Minecraft instance holds
     * it, in which case nothing changes and the screen says so.
     *
     * <p>Before this, the click always called {@code setActive()} and the per-tick claim upkeep in
     * {@code SteamPadClient} undid it a fraction of a second later, then auto-activation grabbed a
     * DIFFERENT pad and persisted that as the active one. From the user's side the button appeared to
     * reject the choice and jump to another controller by itself — the reported "le doy al botón y lo
     * bota, selecciona otro control". Refusing up front, with a reason, keeps the isolation guarantee
     * (two instances never share a pad) while making the outcome legible instead of looking broken.
     */
    private void activateOrExplain(long handle) {
        if (ControllerClaimService.ensureClaim(handle, controllers)) {
            ActiveControllerService.setActive(handle);
            notice = null;
            return;
        }
        notice = Component.translatable("steampad.controller.busy_other_instance");
        noticeUntilMs = System.currentTimeMillis() + 5_000L;
        LogUtil.info("[SteamPad] '{}' (handle={}) is claimed by another Minecraft instance — "
            + "selection refused, active controller left unchanged.", nameOf(handle), handle);
    }

    private String nameOf(long handle) {
        for (SteamControllerHandleRef r : controllers) {
            if (r.handle == handle) return r.displayName;
        }
        return "handle " + handle;
    }

    private void refresh() {
        List<SteamControllerHandleRef> live = ControllerManager.getConnectedControllers();
        LogUtil.info("[SteamPad] Refreshing controller list. Found {} controller(s).", live.size());
        this.clearWidgets();
        this.init();
    }

    private void retrySteamInit() {
        LogUtil.info("[SteamPad] Retrying Steam initialization from ControllerSelectScreen...");
        boolean ok = SteamBootstrap.init();
        LogUtil.info("[SteamPad] Retry result: {}", ok ? "Steam init OK — controllers should appear" : "Still failed — check log for details");
        refresh();
    }

    /**
     * Re-reads which pads other live instances hold, at most a few times a second.
     *
     * <p>Throttled and cached because {@link ControllerClaimService#isClaimedByOther} touches a file
     * per controller: calling it straight from {@code render()} would be one file read per pad per
     * FRAME (hundreds a second on a handheld), and this label is worth nothing like that much.
     */
    private void refreshBusySet() {
        long now = System.currentTimeMillis();
        if (now < busyScanAtMs) return;
        busyScanAtMs = now + 250L;
        busyHandles.clear();
        for (SteamControllerHandleRef r : controllers) {
            if (ControllerClaimService.isClaimedByOther(r.handle, controllers)) busyHandles.add(r.handle);
        }
    }

    @Override
    public void tick() {
        super.tick();
        refreshBusySet();
        List<SteamControllerHandleRef> live = ControllerManager.getConnectedControllers();
        long nowActive = ActiveControllerService.getActiveHandle();
        if (live.size() != controllers.size() || !live.equals(controllers) || nowActive != lastKnownActive) {
            LogUtil.debug("[SteamPad] Controller state changed during screen open — auto-refreshing.");
            refresh();
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Step 1 â€” shared chrome (header bar with title + accent, footer band) over the native blur.
        renderChrome(context);

        // Developer badge (top-right, small): build version Â· ElDon Â· MX flag Â· YucatÃ¡n flag Â· heart.
        drawDevBadge(context);

        // Step 2 â€” diagnostic panel background fill (compact, anchored to the bottom)
        int panelX = PANEL_MARGIN_X;
        int panelW = this.width - PANEL_MARGIN_X * 2;
        int panelTop = panelTop();
        int panelBot = panelTop + panelContentH() + 4;
        context.fill(panelX, panelTop - 2, panelX + panelW, panelBot, COLOR_PANEL_BG);

        // Step 3 â€” controller entry box fills
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

        // Step 4 â€” buttons (Drawable children)
        super.render(context, mouseX, mouseY, delta);

        // Step 5 â€” text drawn on top.
        // Text rendering uses drawText(textRenderer, text, x, y, color, shadow=true).
        // All drawTextWithShadow overloads (Text, OrderedText, String) were removed in MC 1.21.10.
        // drawText(TextRenderer, Text, ...) is the underlying call used by drawCenteredTextWithShadow
        // and is confirmed stable in both MC 1.21.4 and 1.21.10.
        // Title is drawn by renderChrome(); panel content here.
        renderDiagnosticPanel(context, panelX + 4);

        entryY = CONTENT_TOP;
        for (SteamControllerHandleRef ref : controllers) {
            boolean isActive = ActiveControllerService.getActiveHandle() == ref.handle;
            boolean isPreferred = ActiveControllerService.isPreferred(ref.handle);
            int boxX = boxX();
            String name = (ref.displayName == null || ref.displayName.isBlank())
                ? ("Controller " + ref.handle) : ref.displayName;

            // Brand/category logo on the left, drawn from primitives (Steam Deck, 8BitDo, Xbox, â€¦).
            int iconSize = 36;
            try {
                dev.steampad.client.ui.ControllerBrandIcon.draw(context,
                    boxX + 7, entryY + (ENTRY_HEIGHT - iconSize) / 2, iconSize, ref.type, ref.displayName, ref.vendorId);
            } catch (Throwable ignored) { /* never let the logo abort the name/text below */ }
            context.fill(boxX + 50, entryY + 8, boxX + 51, entryY + ENTRY_HEIGHT - 8, 0xFF3A3F47);
            int textX = boxX + 56;

            context.drawString(font,
                Component.literal(name + (isActive ? " ✓" : "") + (isPreferred ? " *" : "")),
                textX, entryY + 9, isActive ? COLOR_OK : COLOR_LABEL, true);
            // Sub-line: manufacturer/brand (e.g. "8BitDo", "Sony"), not the raw enum ("GENERIC").
            context.drawString(font,
                Component.literal(dev.steampad.client.ui.ControllerBrandIcon.manufacturer(ref.type, ref.displayName, ref.vendorId)),
                textX, entryY + 21, COLOR_DIM, true);
            // Third state beyond active/connected: held by ANOTHER Minecraft instance. Without it the
            // card looked identical to a free pad while its Select button quietly did nothing, which
            // is what read as "no deja seleccionar, como que está bloqueado".
            boolean busy = !isActive && busyHandles.contains(ref.handle);
            context.drawString(font,
                Component.translatable(isActive ? "steampad.controller.status.active"
                    : busy ? "steampad.controller.status.busy_other_instance"
                    : "steampad.controller.status.connected"),
                textX, entryY + 33, isActive ? COLOR_OK : busy ? COLOR_WARN : 0xFF888888, true);

            entryY += ENTRY_HEIGHT + ENTRY_MARGIN;
        }

        // Why a click did nothing, stated plainly and briefly, just above the Back button.
        if (notice != null) {
            if (System.currentTimeMillis() > noticeUntilMs) {
                notice = null;
            } else {
                context.drawCenteredString(font, notice, this.width / 2, this.height - 42, COLOR_WARN);
            }
        }

        // Empty state â€” no controllers from EITHER backend (Steam Input or GLFW fallback)
        if (controllers.isEmpty()) {
            context.drawCenteredString(font,
                Component.translatable("steampad.diag.no_controllers"),
                this.width / 2, CONTENT_TOP + 12, COLOR_DIM);
            context.drawCenteredString(font,
                Component.translatable("steampad.diag.connect_and_refresh"),
                this.width / 2, CONTENT_TOP + 24, COLOR_WARN);
            if (!SteamBootstrap.isSteamAvailable()) {
                context.drawCenteredString(font,
                    Component.translatable("steampad.diag.glfw_fallback_note"),
                    this.width / 2, CONTENT_TOP + 36, COLOR_DIM);
            }
        }
    }

    private void renderDiagnosticPanel(GuiGraphics ctx, int x) {
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
        GuiPose.push(ctx);
        GuiPose.translate(ctx, x, panelTop());
        GuiPose.scale(ctx, PANEL_SCALE, PANEL_SCALE);
        x = 0;
        int y = 0;

        if (!diagnosticExpanded) {
            // Collapsed (default): one line â€” what's actually driving controllers right now â€” plus a
            // hint to expand. Feedback: the full dump felt cramped/like too much info up front.
            String sourceLabel = tr(sourceLabelKey(source));
            int c = source == ControllerManager.Source.NONE ? COLOR_WARN : COLOR_OK;
            drawStatusLine(ctx, x, y, tr("steampad.diag.working_via"),
                    sourceLabel + " " + tr("steampad.diag.click_for_details"), c);
            GuiPose.pop(ctx);
            return;
        }

        // Line 1 â€” Steam API status. When a fallback (SDL3/GLFW) is actually driving controllers,
        // a missing Steam context is NOT a hard failure â€” show it as a warning, not alarming red.
        boolean fallbackActive = !steamOk
                && (source == ControllerManager.Source.SDL3 || source == ControllerManager.Source.GLFW_FALLBACK);
        String steamApiLabel = tr("steampad.diag.steam_api");
        if (!nativesOk) {
            drawStatusLine(ctx, x, y, steamApiLabel, tr("steampad.diag.v.natives_not_loaded"), COLOR_FAIL);
        } else if (SteamBootstrap.isAttachSkippedByPolicy()) {
            // Deliberate policy, not a failure: on desktop the mod does not attach to Steam, so raw
            // SDL3 keeps full control of the pads (attaching makes Steam seize them â€” D033).
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

        // Line 2 â€” Steam Input layer
        String steamInputLabel = tr("steampad.diag.steam_input");
        if (steamOk && !inputOk) {
            drawStatusLine(ctx, x, y, steamInputLabel, tr("steampad.diag.v.init_failed"), COLOR_FAIL);
        } else if (!steamOk) {
            drawStatusLine(ctx, x, y, steamInputLabel, tr("steampad.diag.v.unavailable"), COLOR_DIM);
        } else {
            drawStatusLine(ctx, x, y, steamInputLabel, tr("steampad.diag.v.ready"), COLOR_OK);
        }
        y += PANEL_LINE_H;

        // Line 3 â€” Active input source (which backend is actually providing controllers)
        drawStatusLine(ctx, x, y, tr("steampad.diag.input_source"), tr(sourceLabelKey(source)),
                source == ControllerManager.Source.NONE ? COLOR_WARN : COLOR_OK);
        y += PANEL_LINE_H;

        // Line 4 â€” Controller count
        drawStatusLine(ctx, x, y, tr("steampad.diag.controllers"), String.valueOf(controllerCount),
                controllerCount > 0 ? COLOR_OK : COLOR_WARN);
        y += PANEL_LINE_H;

        // Line 4 â€” Active controller
        boolean hasActive = ActiveControllerService.hasActiveController();
        drawStatusLine(ctx, x, y, tr("steampad.diag.active"), activeName,
                hasActive ? COLOR_OK : COLOR_DIM);
        y += PANEL_LINE_H;

        // Line 5 â€” Action Sets / VDF
        String actionSetsLabel = tr("steampad.diag.action_sets");
        if (!inputOk) {
            drawStatusLine(ctx, x, y, actionSetsLabel, tr("steampad.diag.v.steam_input_unavailable"), COLOR_DIM);
        } else if (!actionsOk) {
            drawStatusLine(ctx, x, y, actionSetsLabel, tr("steampad.diag.v.not_loaded_vdf"), COLOR_FAIL);
        } else {
            drawStatusLine(ctx, x, y, actionSetsLabel, tr("steampad.diag.v.loaded"), COLOR_OK);
        }
        y += PANEL_LINE_H;

        // Line 6 â€” Contextual help / action hint
        String help = buildHelpMessage(nativesOk, steamOk, inputOk, controllerCount, actionsOk, appIdExists, source);
        if (help != null) {
            ctx.drawString(font, Component.literal(help), x, y, COLOR_WARN, true);
        }
        y += PANEL_LINE_H;
        ctx.drawString(font, Component.translatable("steampad.diag.click_to_collapse"), x, y, COLOR_DIM, true);

        GuiPose.pop(ctx);
    }

    /** Shorthand: resolved (localized) string for a translation key â€” the diagnostic panel draws
     *  with {@link #drawStatusLine} (String label/value), so keys are resolved once per call here. */
    private static String tr(String key) { return Component.translatable(key).getString(); }

    private static String sourceLabelKey(ControllerManager.Source source) {
        return switch (source) {
            case STEAM_INPUT   -> "steampad.diag.src_steam_input";
            case SDL3          -> "steampad.diag.src_sdl3";
            case GLFW_FALLBACK -> "steampad.diag.src_glfw";
            case NONE          -> "steampad.diag.src_none";
        };
    }

    /** Small developer badge in the top-right corner: version Â· ElDon Â· flags Â· heart. */
    private void drawDevBadge(GuiGraphics ctx) {
        String version = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("steampad")
                .map(c -> "v" + c.getMetadata().getVersion().getFriendlyString())
                .orElse("");
        int right = this.width - 4;
        int fy = 4;          // flags/heart row top
        int fh = 9, fw = 14, gap = 3;

        // Right-to-left: heart, YucatÃ¡n flag, Mexico flag.
        int x = right - 7;
        drawHeart(ctx, x, fy, 0xFFE23B4E);
        x -= (fw + gap);
        drawYucatanFlag(ctx, x, fy, fw, fh);
        x -= (fw + gap);
        drawMexicoFlag(ctx, x, fy, fw, fh);

        // "ElDon" then version, right-aligned to the left of the flags.
        int textRight = x - 4;
        String dev = "ElDon";
        int devW = font.width(dev);
        ctx.drawString(font, Component.literal(dev), textRight - devW, fy + 1, COLOR_LABEL, true);
        if (!version.isEmpty()) {
            int vW = font.width(version);
            ctx.drawString(font, Component.literal(version), textRight - devW - 6 - vW, fy + 1, COLOR_DIM, true);
        }
    }

    private void drawMexicoFlag(GuiGraphics ctx, int x, int y, int w, int h) {
        int t = w / 3;
        ctx.fill(x, y, x + t, y + h, 0xFF006847);             // green
        ctx.fill(x + t, y, x + 2 * t, y + h, 0xFFFFFFFF);      // white
        ctx.fill(x + 2 * t, y, x + w, y + h, 0xFFCE1126);      // red
        ctx.fill(x + t + t / 2 - 1, y + h / 2 - 1, x + t + t / 2 + 1, y + h / 2 + 1, 0xFF7A5230); // emblem dot
    }

    private void drawYucatanFlag(GuiGraphics ctx, int x, int y, int w, int h) {
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

    private void drawHeart(GuiGraphics ctx, int x, int y, int color) {
        // 7x6 pixel heart.
        String[] rows = { "0110110", "1111111", "1111111", "0111110", "0011100", "0001000" };
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int c = 0; c < row.length(); c++) {
                if (row.charAt(c) == '1') ctx.fill(x + c, y + r, x + c + 1, y + r + 1, color);
            }
        }
    }

    private void drawStatusLine(GuiGraphics ctx, int x, int y, String label, String value, int valueColor) {
        // Uses DrawContext.drawText(TextRenderer, Text, int, int, int, boolean) with shadow=true.
        // All drawTextWithShadow overloads (Text, OrderedText, String) were removed in MC 1.21.10.
        // drawText(TextRenderer, Text, ...) is the underlying method called by drawCenteredTextWithShadow
        // and is confirmed stable in MC 1.21.10.
        ctx.drawString(font, Component.literal(label), x, y, COLOR_DIM, true);
        ctx.drawString(font, Component.literal(value), x + 80, y, valueColor, true);
    }

    private boolean checkAppIdFileExists() {
        return new File("steam_appid.txt").exists()
            || new File("steamappid.txt").exists()
            || (minecraft != null && new File(minecraft.gameDirectory, "steam_appid.txt").exists())
            || (minecraft != null && new File(minecraft.gameDirectory, "steamappid.txt").exists());
    }

    private String shortRunDir() {
        if (minecraft == null) return System.getProperty("user.dir", "?");
        String p = minecraft.gameDirectory.getAbsolutePath();
        return p.length() > 45 ? "..." + p.substring(p.length() - 42) : p;
    }

    private String buildHelpMessage(boolean nativesOk, boolean steamOk, boolean inputOk,
                                    int controllerCount, boolean actionsOk, boolean appIdExists,
                                    ControllerManager.Source source) {
        if (!nativesOk) {
            return tr("steampad.diag.help.enable_natives");
        }
        if (!steamOk) {
            // A fallback is detecting the pad even though Steam Input is down â€” name the actual
            // source (SDL3/GLFW) instead of nagging about steam_appid.txt, which would wrongly imply
            // detection failed.
            if (controllerCount > 0) {
                String src = source == ControllerManager.Source.SDL3 ? "SDL3" : "GLFW";
                return Component.translatable("steampad.diag.help.detected_via_fallback", src).getString();
            }
            if (!appIdExists) {
                // Show truncated path to the run directory where the file is expected
                return Component.translatable("steampad.diag.help.add_appid_file", shortRunDir()).getString();
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

    /** Click anywhere in the diagnostic panel (that isn't already a widget) toggles collapsed/expanded.
     *  Adapter per version — 1.21.9 replaced the loose click parameters with a record. Note the order
     *  matters: widgets get first refusal, the panel only sees clicks nothing else claimed. */
    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) return true;
        return steampad$togglePanelAt(click.x(), click.y());
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        return steampad$togglePanelAt(mouseX, mouseY);
    }
    *///?}

    private boolean steampad$togglePanelAt(double x, double y) {
        int panelX = PANEL_MARGIN_X;
        int panelW = this.width - PANEL_MARGIN_X * 2;
        int panelTop = panelTop();
        int panelBot = panelTop + panelContentH() + 4;
        if (x >= panelX && x <= panelX + panelW && y >= panelTop - 2 && y <= panelBot) {
            diagnosticExpanded = !diagnosticExpanded;
            UiSoundService.playNavigate();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
