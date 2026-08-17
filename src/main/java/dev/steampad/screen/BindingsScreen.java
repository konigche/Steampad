package dev.steampad.screen;

import dev.steampad.client.ui.ButtonIcon;
import dev.steampad.config.ConfigManager;
import dev.steampad.config.ControllerConfig;
import dev.steampad.input.ActionCatalog;
import dev.steampad.input.GamepadBinds;
import dev.steampad.input.GamepadInputDispatcher;
import dev.steampad.input.GamepadSnapshot;
import dev.steampad.input.SteamSlotDispatcher;
import dev.steampad.service.ControllerManager;
import dev.steampad.service.UiSoundService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * "Configure Buttons" tab — a console-style, four-zone button configurator:
 * <ul>
 *   <li>header with the controller name + the Basic/Buttons/Advanced tabs (switch with LB/RB),</li>
 *   <li>a scrollable categorized list (≈3/4 width) of every action — movement, gameplay, inventory,
 *       creative, misc, debug, interface, radial, virtual mouse, plus a Mods section reflecting other
 *       installed mods' keybinds — each row showing the action, its bound button icon, a Reset square
 *       and (for rebindable actions) a Chord square,</li>
 *   <li>a side panel (≈1/4 width) showing the selected action, its description and bound button, with
 *       Rebind / Reset / Chord controls,</li>
 *   <li>footer-ish controls in the panel: Reset-all, Undo, Accept.</li>
 * </ul>
 */
public class BindingsScreen extends SteamPadBaseScreen implements TabbedScreen {

    private static final int ROW_H = 22;
    private static final int SQUARE = 16;

    private final Screen parent;
    private final long handle;
    private ControllerConfig cfg;
    private List<ActionCatalog.Section> sections;

    private int listX, listW, mainW, panelX, panelW;

    private record RowUI(ActionCatalog.Entry entry, net.minecraft.client.gui.components.AbstractWidget main, int baseY) {}
    private final List<RowUI> rows = new ArrayList<>();
    private final List<int[]> headerPos = new ArrayList<>();   // {baseY}
    private final List<String> headerText = new ArrayList<>(); // translated/literal title

    private ActionCatalog.Entry selected;

    // Rebind / chord capture.
    private enum Listen { NONE, REBIND, CHORD }
    private Listen listen = Listen.NONE;
    private ActionCatalog.Entry listenEntry;
    private final GamepadSnapshot capSnap = new GamepadSnapshot();
    private final boolean[] capPrev = new boolean[GamepadSnapshot.BUTTON_COUNT];
    private boolean capPrevLt, capPrevRt;
    // Chord capture is a TWO-button flow (S6): step 0 reads the modifier, step 1 reads the trigger.
    private int capStep = 0;
    private String capFirst = null;

    // One-level-deep undo of the binding maps (slots live in GlobalConfig but are edited here too).
    private record Snapshot(Map<String, String> binds, Map<String, String> chords,
                            Map<String, String> extra, Map<String, String> extraChords,
                            Map<String, String> slots) {}
    private final Deque<Snapshot> undo = new ArrayDeque<>();

    public BindingsScreen(Screen parent, long handle) {
        super(Component.translatable("steampad.screen.bindings.title"));
        this.parent = parent;
        this.handle = handle;
    }

    @Override protected int contentTop() { return HEADER_H + 8 + 22 + 4; }

    @Override
    protected void init() {
        super.init();
        resetScroll();
        rows.clear();
        headerPos.clear();
        headerText.clear();
        this.cfg = ConfigManager.getControllerConfig(handle);
        this.sections = ActionCatalog.build();

        this.panelW = Math.max(116, Math.min(190, this.width / 4));
        this.panelX = this.width - panelW - 8;
        this.listX = 8;
        this.listW = panelX - 12 - listX;
        this.mainW = listW - (SQUARE + 4) * 3 - 4;

        // Tabs (Basic / Buttons / Advanced) with LB/RB hints handled by the dispatcher.
        SettingsTabs.add(this, SettingsTabs.BUTTONS, parent, handle, listX, HEADER_H + 8, listW);

        int y = contentTop();
        for (ActionCatalog.Section sec : sections) {
            headerPos.add(new int[]{y});
            headerText.add(sec.titleKey != null ? Component.translatable(sec.titleKey).getString() : sec.literalTitle);
            y += 14;
            for (ActionCatalog.Entry e : sec.entries) {
                addRow(e, y);
                y += ROW_H;
            }
            y += 4;
        }
        finishScroll(y);

        // Side-panel controls.
        int by = this.height - 8 - 20;
        addRenderableWidget(Button.builder(Component.translatable("steampad.btn.accept"), b -> onClose())
                .bounds(panelX, by, panelW, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("steampad.btn.undo"), b -> doUndo())
                .bounds(panelX, by - 22, panelW, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("steampad.btn.reset_all"), b -> doResetAll())
                .tooltip(Tooltip.create(Component.translatable("steampad.bindings.reset_all.tooltip")))
                .bounds(panelX, by - 44, panelW, 18).build());

        if (selected == null && !rows.isEmpty()) selected = rows.get(0).entry;
    }

    private void addRow(ActionCatalog.Entry e, int y) {
        switch (e.kind) {
            case SLIDER -> addSlider(e, y);
            case TOGGLE -> addToggle(e, y);
            case ACTION -> {
                Button main = Button.builder(Component.empty(), b -> {
                    selected = e;
                    UiSoundService.playSelect();
                    // ACTION rows open a sub-editor; which one is keyed by the entry's label
                    // (radial editor vs. the emote wheel editor — FASE 63).
                    if ("steampad.act.emote_wheel_config".equals(e.labelKey)) {
                        minecraft.setScreen(new EmoteWheelScreen(this, handle));
                    } else {
                        minecraft.setScreen(new RadialEditorScreen(this, handle));
                    }
                }).bounds(listX, y, listW, 20).build();
                addScroll(main, y);
                rows.add(new RowUI(e, main, y));
            }
            case FIXED -> {
                Button main = Button.builder(Component.empty(), b -> { selected = e; UiSoundService.playNavigate(); })
                        .bounds(listX, y, listW, 20).build();
                main.active = true;
                addScroll(main, y);
                rows.add(new RowUI(e, main, y));
            }
            case LAYER -> {   // Steam Input slot layer: opens the per-context slot editor
                Button main = Button.builder(Component.empty(), b -> {
                    selected = e;
                    UiSoundService.playSelect();
                    var ctx = dev.steampad.input.SteamSlotDispatcher.Context.values()[e.slotIndex];
                    minecraft.setScreen(new SteamSlotLayerScreen(this, ctx, e.labelKey));
                }).bounds(listX, y, listW, 20).build();
                addScroll(main, y);
                rows.add(new RowUI(e, main, y));
            }
            case SLOT -> {   // Steam Input slot: pick the keybind this slot triggers (no button capture)
                Button main = Button.builder(Component.empty(), b -> {
                    selected = e;
                    UiSoundService.playSelect();
                    minecraft.setScreen(new SteamSlotTargetPickerScreen(this, id -> assignSlot(e, id)));
                }).bounds(listX, y, mainW, 20).build();
                addScroll(main, y);
                rows.add(new RowUI(e, main, y));
                Button reset = Button.builder(Component.empty(), b -> { selected = e; doResetOne(e); })
                        .tooltip(Tooltip.create(Component.translatable("steampad.bindings.reset_one.tooltip")))
                        .bounds(listX + mainW + 4, y + 2, SQUARE, SQUARE).build();
                addScroll(reset, y);
            }
            default -> { // BIND / EXTRA
                Button main = Button.builder(Component.empty(), b -> { selected = e; startRebind(e); })
                        .bounds(listX, y, mainW, 20).build();
                addScroll(main, y);
                rows.add(new RowUI(e, main, y));
                Button reset = Button.builder(Component.empty(), b -> { selected = e; doResetOne(e); })
                        .tooltip(Tooltip.create(Component.translatable("steampad.bindings.reset_one.tooltip")))
                        .bounds(listX + mainW + 4, y + 2, SQUARE, SQUARE).build();
                addScroll(reset, y);
                // Chord square on BOTH bind and extra (mod) rows — every action can have a chord (S5).
                Button chord = Button.builder(Component.empty(), b -> { selected = e; startChord(e); })
                        .tooltip(Tooltip.create(Component.translatable("steampad.bindings.chord.tooltip")))
                        .bounds(listX + mainW + 4 + SQUARE + 4, y + 2, SQUARE, SQUARE).build();
                addScroll(chord, y);
                // HOLD square, right next to the chord one — asked for there by name ("alado de chords
                // que se pueda asignar el boton por mantener mas tiempo"). Toggles this row's action
                // between firing on the press edge and firing after a long hold, leaving the button's
                // other job on a short tap. Disabled, with the reason in its tooltip, wherever the hold
                // is already spoken for: LongPressGate.blockedReason is the same check the dispatcher
                // makes live, so this square can never offer something that would not work.
                Button hold = Button.builder(Component.literal(isHold(e) ? "H" : "·"),
                        b -> { selected = e; toggleHold(e); })
                        .bounds(listX + mainW + 4 + (SQUARE + 4) * 2, y + 2, SQUARE, SQUARE).build();
                String holdBtn = holdButtonOf(e);
                String blocked = dev.steampad.input.LongPressGate.blockedReason(
                        cfg, holdBtn, e.kind == ActionCatalog.Kind.EXTRA ? e.labelKey : null);
                hold.active = !holdBtn.isEmpty() && (blocked == null || isHold(e));
                // "Not available here: X" is the right framing for a button whose hold is taken by
                // another gesture, but it reads as a dead end for the far more common case of a row
                // nobody has assigned yet — which is not a refusal, just a missing first step. That one
                // gets its own plain sentence saying what Hold will do once a button IS assigned.
                Component holdTip;
                if (blocked == null || isHold(e)) {
                    holdTip = Component.translatable("steampad.bindings.hold.tooltip");
                } else if ("steampad.hold.blocked.unbound".equals(blocked)) {
                    holdTip = Component.translatable(blocked);
                } else {
                    holdTip = Component.translatable("steampad.layer.mode.blocked",
                            Component.translatable(blocked));
                }
                hold.setTooltip(Tooltip.create(holdTip));
                addScroll(hold, y);
            }
        }
    }

    // ---- Long-press (hold) mode on the Buttons rows ------------------------------------------

    /** The physical button this row's action sits on ("" = unbound). */
    private String holdButtonOf(ActionCatalog.Entry e) {
        if (e.kind == ActionCatalog.Kind.BIND) return GamepadBinds.button(cfg, e.bind);
        for (var en : cfg.extraBinds.entrySet()) {
            if (en.getValue().equals(e.labelKey)) return en.getKey();
        }
        return "";
    }

    /** Whether this row already fires on a long press (GAMEPLAY layer). */
    private boolean isHold(ActionCatalog.Entry e) {
        String btn = holdButtonOf(e);
        return !btn.isEmpty() && dev.steampad.input.LongPressGate.isHold(cfg, btn);
    }

    private void toggleHold(ActionCatalog.Entry e) {
        String btn = holdButtonOf(e);
        if (btn.isEmpty()) return;
        pushUndo();
        dev.steampad.input.LongPressGate.setHold(cfg, btn, !isHold(e));
        save();
        UiSoundService.playSelect();
        rebuildWidgets();   // the square's own label/state is baked at init, so re-run it
    }

    private void addSlider(ActionCatalog.Entry e, int y) {
        dev.steampad.client.ui.SteamSlider s = new dev.steampad.client.ui.SteamSlider(listX, y, listW, 20,
                Component.translatable("steampad.cset.vmouse_sensitivity"), cfg.virtualMouseSensitivity,
                0.2f, 3f, "%.2f", v -> { selected = e; cfg.virtualMouseSensitivity = v; save(); });
        addScroll(s, y);
        rows.add(new RowUI(e, s, y));
    }

    /** TOGGLE rows — currently only the virtual-mouse snap on/off (global; drawn as a switch). */
    private void addToggle(ActionCatalog.Entry e, int y) {
        var g = ConfigManager.getGlobal();
        dev.steampad.client.ui.SteamToggle t = new dev.steampad.client.ui.SteamToggle(listX, y, listW, 20,
                Component.translatable(e.labelKey), g.virtualMouseSnapEnabled,
                v -> { selected = e; g.virtualMouseSnapEnabled = v; ConfigManager.saveGlobal(); });
        addScroll(t, y);
        rows.add(new RowUI(e, t, y));
    }

    @Override public void steampad$nextTab() { SettingsTabs.cycle(SettingsTabs.BUTTONS, +1, parent, handle); }
    @Override public void steampad$prevTab() { SettingsTabs.cycle(SettingsTabs.BUTTONS, -1, parent, handle); }

    // ---- Editing -------------------------------------------------------------------------

    private Map<String, String> slotMap() {
        return ConfigManager.getGlobal().steamInputSlots;
    }

    private void pushUndo() {
        undo.push(new Snapshot(new HashMap<>(cfg.buttonBindings),
                new HashMap<>(cfg.chordBindings), new HashMap<>(cfg.extraBinds),
                new HashMap<>(cfg.extraChords), new HashMap<>(slotMap())));
        if (undo.size() > 25) undo.removeLast();
    }

    private void doUndo() {
        if (undo.isEmpty()) { UiSoundService.playNavigate(); return; }
        Snapshot s = undo.pop();
        cfg.buttonBindings.clear(); cfg.buttonBindings.putAll(s.binds());
        cfg.chordBindings.clear(); cfg.chordBindings.putAll(s.chords());
        cfg.extraBinds.clear(); cfg.extraBinds.putAll(s.extra());
        cfg.extraChords.clear(); cfg.extraChords.putAll(s.extraChords());
        slotMap().clear(); slotMap().putAll(s.slots());
        ConfigManager.saveControllerConfig(handle);
        ConfigManager.saveGlobal();
        UiSoundService.playSelect();
    }

    private void doResetAll() {
        pushUndo();
        GamepadBinds.reset(cfg);
        cfg.extraBinds.clear();
        cfg.extraChords.clear();
        slotMap().clear();
        ConfigManager.saveControllerConfig(handle);
        ConfigManager.saveGlobal();
        UiSoundService.playSelect();
    }

    private void doResetOne(ActionCatalog.Entry e) {
        pushUndo();
        if (e.kind == ActionCatalog.Kind.BIND) {
            GamepadBinds.resetOne(cfg, e.bind);
        } else if (e.kind == ActionCatalog.Kind.EXTRA) {
            String tk = e.keyBinding.getName();
            cfg.extraBinds.values().removeIf(v -> v.equals(tk));
            cfg.extraChords.remove(tk);
        } else if (e.kind == ActionCatalog.Kind.SLOT) {
            slotMap().remove(SteamSlotDispatcher.configKey(e.slotIndex));
            ConfigManager.saveGlobal();
        }
        ConfigManager.saveControllerConfig(handle);
        UiSoundService.playSelect();
    }

    /** Assigns a keybind (picked in KeybindPickerScreen) to a Steam Input slot. */
    private void assignSlot(ActionCatalog.Entry e, String keybindId) {
        pushUndo();
        slotMap().put(SteamSlotDispatcher.configKey(e.slotIndex), keybindId);
        ConfigManager.saveGlobal();
    }

    private void startRebind(ActionCatalog.Entry e) {
        if (e.kind != ActionCatalog.Kind.BIND && e.kind != ActionCatalog.Kind.EXTRA) return;
        UiSoundService.playSelect();
        listen = Listen.REBIND;
        listenEntry = e;
        beginCapture();
    }

    private void startChord(ActionCatalog.Entry e) {
        if (e.kind != ActionCatalog.Kind.BIND && e.kind != ActionCatalog.Kind.EXTRA) return;
        UiSoundService.playSelect();
        listen = Listen.CHORD;
        listenEntry = e;
        capStep = 0;
        capFirst = null;
        beginCapture();
    }

    private void beginCapture() {
        GamepadInputDispatcher.captureMode = true;
        if (ControllerManager.readSnapshot(handle, capSnap)) {
            System.arraycopy(capSnap.buttons, 0, capPrev, 0, GamepadSnapshot.BUTTON_COUNT);
            capPrevLt = capSnap.axis(GamepadSnapshot.AXIS_LEFT_TRIGGER) > 0f;
            capPrevRt = capSnap.axis(GamepadSnapshot.AXIS_RIGHT_TRIGGER) > 0f;
        }
    }

    private void stopCapture() {
        listen = Listen.NONE;
        listenEntry = null;
        capStep = 0;
        capFirst = null;
        GamepadInputDispatcher.captureMode = false;
        // The button that ended the capture (Select-cancel or the captured trigger) must not ALSO
        // drive GUI navigation in the same tick — e.g. Select used to cancel AND cycle the cursor (D14).
        GamepadInputDispatcher.swallowGuiTick = true;
    }

    /** Routes a captured button to the rebind (single) or chord (two-step) flow. */
    private void onCapturedButton(String buttonId) {
        if (listen == Listen.REBIND) {
            applyRebind(buttonId);
            stopCapture();
            return;
        }
        // CHORD: first press = modifier, second press = trigger button.
        if (capStep == 0) {
            capFirst = buttonId;
            capStep = 1;
            UiSoundService.playNavigate();
            return;
        }
        applyChord(capFirst, buttonId);
        stopCapture();
    }

    private void applyRebind(String buttonId) {
        pushUndo();
        if (listenEntry.kind == ActionCatalog.Kind.BIND) {
            GamepadBinds.assign(cfg, listenEntry.bind, buttonId);
        } else { // EXTRA → map this controller button to the keybind (one button per keybind)
            String tk = listenEntry.keyBinding.getName();
            cfg.extraBinds.values().removeIf(v -> v.equals(tk));
            cfg.extraBinds.put(buttonId, tk);
        }
        ConfigManager.saveControllerConfig(handle);
    }

    /** Stores a two-button chord: modifier + trigger button, for a bind or an extra (mod) keybind. */
    private void applyChord(String modifier, String trigger) {
        pushUndo();
        if (listenEntry.kind == ActionCatalog.Kind.BIND) {
            GamepadBinds.assignChord(cfg, listenEntry.bind, modifier);
            GamepadBinds.assign(cfg, listenEntry.bind, trigger);
        } else { // EXTRA
            String tk = listenEntry.keyBinding.getName();
            cfg.extraBinds.values().removeIf(v -> v.equals(tk));
            cfg.extraBinds.put(trigger, tk);
            cfg.extraChords.put(tk, modifier);
        }
        ConfigManager.saveControllerConfig(handle);
    }

    @Override
    public void tick() {
        super.tick();
        // Keep description panel in sync with D-pad / focus navigation.
        if (listen == Listen.NONE) {
            net.minecraft.client.gui.components.events.GuiEventListener focused = getFocused();
            if (focused != null) {
                for (RowUI r : rows) {
                    if (r.main() == focused) { selected = r.entry(); break; }
                }
            }
            return;
        }
        if (!ControllerManager.readSnapshot(handle, capSnap)) return;
        // Select (BACK) cancels capture — detected with old capPrev before the state copy.
        boolean backEdge = capSnap.button(GamepadSnapshot.BACK) && !capPrev[GamepadSnapshot.BACK];
        // capturePress also uses old capPrev, so do it before the state copy.
        String id = backEdge ? null : GamepadBinds.capturePress(capSnap, capPrev, capPrevLt, capPrevRt);
        System.arraycopy(capSnap.buttons, 0, capPrev, 0, GamepadSnapshot.BUTTON_COUNT);
        capPrevLt = capSnap.axis(GamepadSnapshot.AXIS_LEFT_TRIGGER) > 0f;
        capPrevRt = capSnap.axis(GamepadSnapshot.AXIS_RIGHT_TRIGGER) > 0f;
        if (backEdge) { stopCapture(); UiSoundService.playNavigate(); return; }
        if (id != null) onCapturedButton(id);
    }

    // keyPressed took loose (key, scancode, modifiers) before 1.21.9 and a KeyEvent record after.
    // Adapter per version over one shared check.
    //? if >=1.21.9 {
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        if (steampad$escapeCancelsCapture(input.key())) return true;
        return super.keyPressed(input);
    }
    //?} else {
    /*@Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (steampad$escapeCancelsCapture(key)) return true;
        return super.keyPressed(key, scancode, modifiers);
    }
    *///?}

    /** Escape aborts an in-progress bind capture instead of closing the screen. */
    private boolean steampad$escapeCancelsCapture(int key) {
        if (listen != Listen.NONE && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            stopCapture();
            return true;
        }
        return false;
    }

    private void save() { ConfigManager.saveControllerConfig(handle); }

    // ---- The currently-bound controller button for an entry ("" if none / fixed string) ----

    private String glyphFor(ActionCatalog.Entry e) {
        return switch (e.kind) {
            case FIXED -> e.fixedGlyph;
            case BIND -> GamepadBinds.button(cfg, e.bind);
            case EXTRA -> {
                String tk = e.keyBinding.getName();
                for (var en : cfg.extraBinds.entrySet()) if (en.getValue().equals(tk)) yield en.getKey();
                yield "";
            }
            default -> null;
        };
    }

    /** The chord (modifier) button currently set for an entry, or "" if none. */
    private String chordFor(ActionCatalog.Entry e) {
        return switch (e.kind) {
            case BIND -> GamepadBinds.chord(cfg, e.bind);
            case EXTRA -> cfg.extraChords.getOrDefault(e.keyBinding.getName(), "");
            default -> "";
        };
    }

    // ---- Render --------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        renderChrome(ctx);

        int top = contentTop(), bottom = contentBottom();

        // Section headers.
        for (int i = 0; i < headerPos.size(); i++) {
            int by = headerPos.get(i)[0] - scrollY();
            if (by + 12 < top || by > bottom) continue;
            ctx.fill(listX, by + 9, listX + listW, by + 10, DIVIDER);
            ctx.drawString(font, Component.literal(headerText.get(i)), listX + 2, by, ACCENT, true);
        }

        super.render(ctx, mouseX, mouseY, delta);   // widgets (buttons, sliders)

        // Per-row overlays: action label, bound-button icon, reset & chord squares. Must use the SAME
        // "fully inside" test applyScrollLayout() uses to hide/show the row's own widget (y >= top-1 &&
        // y+h <= bottom+1) — the old any-overlap test (y+20 < top || y > bottom) let a PARTIALLY
        // scrolled-off row keep drawing its hand-painted label/glyph even after the widget itself went
        // invisible, so the text/icon appeared to "float free" with no background at the scroll edges.
        for (RowUI r : rows) {
            int y = r.main().getY();
            if (y < top - 1 || y + 20 > bottom + 1) continue;
            ActionCatalog.Entry e = r.entry();
            if (e.kind == ActionCatalog.Kind.SLIDER
                    || e.kind == ActionCatalog.Kind.TOGGLE) continue;   // these draw themselves

            // Label (left).
            ctx.drawString(font, trim(labelOf(e), mainW - 40), listX + 6, y + 6, 0xFFE6ECF2, false);

            if (e.kind == ActionCatalog.Kind.ACTION
                    || e.kind == ActionCatalog.Kind.LAYER
                    || e.kind == ActionCatalog.Kind.LAYER) continue;   // open-editor rows: no glyph

            if (e.kind == ActionCatalog.Kind.SLOT) {
                // Steam Input slot: show the assigned target's name (keybind or internal SteamPad
                // action — not a button icon) + reset square.
                String kbId = SteamSlotDispatcher.assignedKeybind(e.slotIndex);
                Component value = SteamSlotDispatcher.displayName(kbId);
                Component shown = trim(value, mainW / 2);
                int vw = font.width(shown);
                ctx.drawString(font, shown, listX + mainW - vw - 6, y + 6,
                        kbId.isEmpty() ? 0xFF8090A0 : 0xFFB9E28C, false);
                drawSquare(ctx, listX + mainW + 4, y + 2, "↺");
                continue;
            }

            // Bound controller-button icon, right side of the main button.
            // When a chord is set, show "modifier+trigger" combined in the main area.
            String g = glyphFor(e);
            String chord = (e.kind == ActionCatalog.Kind.BIND || e.kind == ActionCatalog.Kind.EXTRA)
                    ? chordFor(e) : "";
            int mainRight = (e.kind == ActionCatalog.Kind.FIXED ? listX + listW : listX + mainW);
            if (!chord.isEmpty() && g != null && !g.isEmpty()) {
                int modW  = ButtonIcon.width(font, SQUARE, chord);
                int trigW = ButtonIcon.width(font, SQUARE, g);
                int plusW = font.width("+") + 4;
                int ix = mainRight - (modW + plusW + trigW) - 6;
                ButtonIcon.draw(ctx, font, ix, y + 2, SQUARE, chord);
                ctx.drawString(font, Component.literal("+"), ix + modW + 2, y + 6, 0xFF8090A0, false);
                ButtonIcon.draw(ctx, font, ix + modW + plusW, y + 2, SQUARE, g);
            } else {
                int iconW = ButtonIcon.width(font, SQUARE, g == null ? "" : g);
                ButtonIcon.draw(ctx, font, mainRight - iconW - 6, y + 2, SQUARE, g == null ? "" : g);
            }

            if (e.kind == ActionCatalog.Kind.FIXED) continue;   // no reset/chord on fixed rows

            // Reset square (⟲).
            int rx = listX + mainW + 4, ry = y + 2;
            drawSquare(ctx, rx, ry, "↺");
            // Chord square — shows "+" (no chord) or "✓" (chord already shown in main area).
            if (e.kind == ActionCatalog.Kind.BIND || e.kind == ActionCatalog.Kind.EXTRA) {
                int cxs = rx + SQUARE + 4;
                drawSquare(ctx, cxs, ry, chord.isEmpty() ? "+" : "✓");
            }
        }

        renderScrollbar(ctx, listX + listW + 4);
        renderSidePanel(ctx, top);
        SettingsTabs.renderGlyphs(ctx, font, listX, HEADER_H + 8, listW);

        if (listen != Listen.NONE) renderListenOverlay(ctx);
    }

    private void drawSquare(GuiGraphics ctx, int x, int y, String sym) {
        dev.steampad.client.ui.Draw.fillRoundRect(ctx, x, y, x + SQUARE, y + SQUARE, 3, 0x66101620);
        ctx.fill(x, y, x + SQUARE, y + 1, 0x55FFFFFF);
        int tw = font.width(sym);
        ctx.drawString(font, Component.literal(sym), x + (SQUARE - tw) / 2, y + 4, 0xFFCBD6E2, false);
    }

    private void renderSidePanel(GuiGraphics ctx, int top) {
        int x = panelX, w = panelW;
        ctx.fill(x, top, x + w, this.height - 6, PANEL_BG);
        ctx.fill(x, top, x + w, top + 1, ACCENT_DIM);
        int y = top + 6;

        if (selected == null) {
            ctx.drawString(font, Component.translatable("steampad.bind.panel.none"), x + 6, y, TEXT_MUTED, false);
            return;
        }

        Component title = labelOf(selected);
        for (FormattedCharSequence l : font.split(title, w - 12)) {
            ctx.drawString(font, l, x + 6, y, TEXT_PRIMARY, false);
            y += 10;
        }
        y += 3;

        if (selected.kind == ActionCatalog.Kind.SLOT) {
            // Assigned target (name) instead of a bound-button icon.
            String kbId = SteamSlotDispatcher.assignedKeybind(selected.slotIndex);
            Component bound = Component.translatable("steampad.bind.panel.bound").copy().append(" ")
                    .append(SteamSlotDispatcher.displayName(kbId));
            for (FormattedCharSequence l : font.split(bound, w - 12)) {
                ctx.drawString(font, l, x + 6, y, TEXT_PRIMARY, false);
                y += 10;
            }
            y += 3;
        } else {
            // Bound-to icon.
            String g = glyphFor(selected);
            ctx.drawString(font, Component.translatable("steampad.bind.panel.bound"), x + 6, y, TEXT_MUTED, false);
            ButtonIcon.draw(ctx, font, x + 6 + font.width(Component.translatable("steampad.bind.panel.bound")) + 4,
                    y - 4, SQUARE, g == null ? "" : g);
            y += 16;
        }

        // Description / extra info.
        Component desc;
        if (selected.kind == ActionCatalog.Kind.EXTRA) {
            desc = Component.translatable("steampad.bind.panel.from_key", selected.keyBinding.getTranslatedKeyMessage());
        } else if (selected.descKey != null) {
            desc = Component.translatable(selected.descKey);
        } else {
            desc = Component.empty();
        }
        for (FormattedCharSequence l : font.split(desc, w - 12)) {
            ctx.drawString(font, l, x + 6, y, TEXT_MUTED, false);
            y += 10;
        }

        // Hint for fixed actions.
        if (selected.kind == ActionCatalog.Kind.FIXED) {
            y += 4;
            for (FormattedCharSequence l : font.split(Component.translatable("steampad.bind.panel.fixed"), w - 12)) {
                ctx.drawString(font, l, x + 6, y, TEXT_WARN, false);
                y += 10;
            }
        }

        // Slots only fire when Steam Input is live — be honest about why they aren't, per cause:
        // on desktop the mod deliberately does not attach to Steam (paddles arrive RAW as P1..P4
        // instead, D033); otherwise the VDF/Steam is genuinely missing.
        if (selected.kind == ActionCatalog.Kind.SLOT
                && !dev.steampad.steam.SteamInputManager.areActionSetsValid()) {
            String key = dev.steampad.steam.SteamBootstrap.isAttachSkippedByPolicy()
                    ? "steampad.bind.panel.slot_desktop"
                    : "steampad.bind.panel.slot_inactive";
            y += 4;
            for (FormattedCharSequence l : font.split(Component.translatable(key), w - 12)) {
                ctx.drawString(font, l, x + 6, y, TEXT_WARN, false);
                y += 10;
            }
        }
    }

    private void renderListenOverlay(GuiGraphics ctx) {
        ctx.fill(0, 0, this.width, this.height, 0xC0000000);
        Component who = Component.translatable(listenEntry.labelKey);
        Component prompt;
        if (listen == Listen.CHORD) {
            // Two-step chord capture: ask for the modifier, then the trigger button (S6).
            prompt = capStep == 0
                    ? Component.translatable("steampad.bindings.listening_chord_first", who)
                    : Component.translatable("steampad.bindings.listening_chord_second", who,
                          dev.steampad.client.ui.ButtonIcon.label(capFirst));
        } else {
            prompt = Component.translatable("steampad.bindings.listening", who);
        }
        ctx.drawCenteredString(font, prompt,
                this.width / 2, this.height / 2 - 6, 0xFFFFFFFF);
        ctx.drawCenteredString(font, Component.translatable("steampad.bindings.listening_hint"),
                this.width / 2, this.height / 2 + 8, ACCENT);
    }

    /** Row/panel label for an entry — SLOT labels carry the slot number and its F-key (F13..F22). */
    private Component labelOf(ActionCatalog.Entry e) {
        return e.kind == ActionCatalog.Kind.SLOT
                ? Component.translatable(e.labelKey, e.slotIndex + 1, 13 + e.slotIndex)
                : Component.translatable(e.labelKey);
    }

    /** Truncate a text to fit a pixel width (keeps the list tidy). */
    private Component trim(Component t, int maxW) {
        String s = t.getString();
        if (font.width(t) <= maxW) return t;
        while (s.length() > 1 && font.width(Component.literal(s + "…")) > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return Component.literal(s + "…");
    }

    @Override
    public void onClose() {
        stopCapture();
        minecraft.setScreen(parent);
    }
}
