package dev.steampad.input;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.ControllerConfig;
import dev.steampad.service.ControllerManager;
import dev.steampad.service.UiSoundService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/**
 * Gameplay + GUI input driver for fallback controllers (GLFW or SDL3), reading a normalized
 * {@link GamepadSnapshot} so it is backend-agnostic. This is what makes the controller actually
 * <em>do</em> something on Bazzite / Steam Deck / ROG Ally / 8BitDo, where Steam Input never inits.
 *
 * <p>Drives the game through vanilla {@link KeyBinding}s — held actions (movement, mining, using,
 * jump, sneak, sprint) via {@code setKeyPressed}, discrete actions via {@code onKeyPressed}, camera
 * via {@code changeLookDirection}. Default layout is Xbox-style. Only one controller is active at a
 * time (project constraint), so a single set of edge/toggle state is sufficient.
 *
 * <p>All calls run on the render thread (the client tick).
 */
public final class GamepadInputDispatcher {

    private static final GamepadSnapshot cur = new GamepadSnapshot();
    private static final boolean[] prevButtons = new boolean[GamepadSnapshot.BUTTON_COUNT];

    /**
     * True if the physical button {@code id} is currently held on the active fallback (GLFW/SDL3)
     * controller — for UI press-feedback only (button glyph icons), reads the same live snapshot this
     * class already keeps current every tick. Raw hardware state, independent of any bind/chord.
     * Only meaningful while a fallback controller is driving input; if Steam Input is the active
     * source instead, {@code cur} is never touched and this simply reads as "nothing held" (safe,
     * just means the press effect doesn't light up under Steam Input — a separate glyph system).
     */
    public static boolean isPhysicallyHeld(String id) {
        return GamepadBinds.isHeldRaw(cur, id);
    }
    private static boolean prevLt = false, prevRt = false;
    /** Handle the prev-state belongs to — a handle/backend switch must not synthesize edges. */
    private static long prevHandle = 0L;

    private static boolean sneakToggled = false;
    private static boolean sprintToggled = false;

    // Splitscreen (window-tiling) chord: hold Select (BACK) + press RB to cycle window layouts (see
    // WindowArrangeController). This only changes behavior while GlobalConfig.windowArrangeEnabled is
    // true — tickWindowArrangeChord() is a no-op otherwise, so BACK behaves exactly as before when the
    // feature is off.
    private static boolean backHeldForWindowArrange = false;
    private static boolean backUsedAsWindowArrangeChord = false;
    /**
     * True for exactly one tick: BACK was just released as a plain tap (never used as the window-arrange
     * chord this hold) while the feature is enabled. The two call sites that used to fire BACK's own tap
     * action on its PRESS edge (GUI cursor-mode cycle, gameplay Perspective when still bound to BACK)
     * defer to this signal instead — so holding Select to cycle window layouts doesn't ALSO fire those.
     * A plain quick tap still works exactly as before, just resolved on release instead of press (an
     * imperceptible one-tick-or-less delay, since these are one-shot toggles either way).
     */
    private static boolean windowArrangeBackTapReleased = false;

    // Generic chord-modifier gate: any button used as some bind's CHORD (modifier) also suppresses
    // that SAME button's own plain (unchorded) bind while it's actively completing a chord — the
    // same defer-to-release technique proven above for the Select+RB splitscreen chord, generalized
    // to every user-configured chord (feedback: "Ddown = tirar, cuando asigno DDOWN+A=chat... tambien
    // hace la primera accion de DDOWN... Hay forma de que convivan perfectamente?"). A plain tap on a
    // modifier button still fires normally — just resolved on RELEASE instead of PRESS, and only if
    // no chord was completed with it during that hold (imperceptible for one-shot actions like drop).
    private static final java.util.Map<String, Boolean> modifierHeldLastTick = new java.util.HashMap<>();
    private static final java.util.Map<String, Boolean> modifierUsedThisHold = new java.util.HashMap<>();
    private static final java.util.Map<String, Boolean> modifierTapReleasedThisTick = new java.util.HashMap<>();

    private static boolean wasScreenOpen = false;
    /** Ticks the D-pad has been held in GUI navigation (drives hold-to-repeat). */
    private static int navHeldTicks = 0;

    // GUI→gameplay button suppression: the press that closed a menu (A on "Back to game", B closing
    // the inventory, A activating a radial slot…) must NOT bleed into gameplay as a HELD action
    // (jump/sneak/attack/use). Buttons held at the moment of closing are ignored by held-binds
    // until each is physically released once. Edge actions are already safe via prevButtons.
    private static final boolean[] suppressHeld = new boolean[GamepadSnapshot.BUTTON_COUNT];
    private static boolean suppressLt = false, suppressRt = false;
    /** Whether the radial wheel was open last tick (falling edge arms the suppression too). */
    private static boolean wasRadialOpen = false;
    /** Same, for the independent emote wheel (FASE 63 decoupling — its own controller, own
     *  open/close edge, so a held EMOTE_WHEEL button can't bleed into gameplay either). */
    private static boolean wasEmoteOpen = false;

    /** Arms suppression for everything currently held (called when a GUI/radial closes). */
    private static void armHeldSuppression() {
        System.arraycopy(cur.buttons, 0, suppressHeld, 0, GamepadSnapshot.BUTTON_COUNT);
        suppressLt = GamepadBinds.trigDown(cur.axis(GamepadSnapshot.AXIS_LEFT_TRIGGER));
        suppressRt = GamepadBinds.trigDown(cur.axis(GamepadSnapshot.AXIS_RIGHT_TRIGGER));
    }

    /** Clears suppression for anything no longer physically held (called every tick). */
    private static void updateHeldSuppression() {
        for (int i = 0; i < GamepadSnapshot.BUTTON_COUNT; i++) {
            if (suppressHeld[i] && !cur.button(i)) suppressHeld[i] = false;
        }
        if (suppressLt && !GamepadBinds.trigDown(cur.axis(GamepadSnapshot.AXIS_LEFT_TRIGGER))) suppressLt = false;
        if (suppressRt && !GamepadBinds.trigDown(cur.axis(GamepadSnapshot.AXIS_RIGHT_TRIGGER))) suppressRt = false;
    }

    /** True while the physical button behind this id is suppressed (held since a menu closed). */
    private static boolean isSuppressed(String buttonId) {
        if (GamepadBinds.isLeftTrigger(buttonId))  return suppressLt;
        if (GamepadBinds.isRightTrigger(buttonId)) return suppressRt;
        int i = GamepadBinds.index(buttonId);
        return i >= 0 && suppressHeld[i];
    }
    /** Tracks text-field focus edge so the keyboard auto-opens once when a field gains focus. */
    private static boolean prevKbEligible = false;

    // Hold state for virtual-mouse drag support (cursor-visible mode only).
    private static boolean virtualLmbDown = false;
    private static boolean virtualRmbDown = false;

    /** Set by the bindings screen while listening for a rebind, so GUI nav doesn't eat the press. */
    public static volatile boolean captureMode = false;
    /**
     * Set when a capture just ended so the SAME button edge (Select-cancel or the captured trigger)
     * doesn't also drive GUI navigation in this tick — one press must do exactly one thing (D14).
     */
    public static volatile boolean swallowGuiTick = false;

    private GamepadInputDispatcher() {}

    /** Entry point — called from the client tick when the active controller is a fallback handle. */
    public static void tick(long handle) {
        MinecraftClient mc = MinecraftClient.getInstance();

        // Self-heal: captureMode may only legitimately be true while BindingsScreen owns the rebind
        // capture UI (it's the only class that ever sets it). If vanilla's focus-loss auto-pause
        // races past PauseGate's isWindowFocused() check and replaces the current screen with
        // GameMenuScreen directly (MinecraftClient.setScreen() does NOT call the old screen's own
        // close()/stopCapture()), this flag is orphaned true forever — and since it short-circuits
        // ALL of tickGui() below (including B/Start to close menus), that stranded the player unable
        // to navigate ANY subsequent menu with the gamepad, not just get back into the settings
        // screen. Enforcing the invariant here recovers unconditionally, regardless of how the
        // screen actually got swapped out from under the capture flow.
        if (captureMode && !(mc.currentScreen instanceof dev.steampad.screen.BindingsScreen)) {
            captureMode = false;
        }

        if (!ControllerManager.readSnapshot(handle, cur)) {
            // Controller vanished (e.g. disconnected mid-move) — never leave keys stuck down.
            releaseAllMovement(mc);
            prevHandle = 0L;   // force a prev-state resync when a device comes back
            return;
        }

        // The active handle changed (hotplug, claim switch, backend cascade): the prev-state belongs
        // to another device/mapping, so comparing against it can fabricate button EDGES — including a
        // phantom START that opens the pause menu. Sync prev to the fresh state; edges resume next tick.
        if (handle != prevHandle) {
            prevHandle = handle;
            System.arraycopy(cur.buttons, 0, prevButtons, 0, GamepadSnapshot.BUTTON_COUNT);
            prevLt = GamepadBinds.trigDown(cur.axis(GamepadSnapshot.AXIS_LEFT_TRIGGER));
            prevRt = GamepadBinds.trigDown(cur.axis(GamepadSnapshot.AXIS_RIGHT_TRIGGER));
        }

        ControllerConfig cfg = ConfigManager.getControllerConfig(handle);
        GamepadBinds.setTriggerThreshold(cfg.buttonActivationThreshold);   // LT/RT press point (audit)
        tickWindowArrangeChord();   // Select+RB splitscreen window cycling — before GUI/gameplay dispatch
        tickChordModifierGate(cfg); // user-configured chords: modifier buttons defer their own plain bind
        if (hasActivity()) InputRouter.markGamepad();   // any pad input → controller is the active device
        boolean screenOpen = mc.currentScreen != null;
        updateHeldSuppression();

        // Radial closed this tick → the buttons still held (A that activated a slot, the open
        // button…) must not become gameplay held-actions.
        boolean radialOpen = dev.steampad.radial.RadialMenuController.isOpen();
        boolean emoteOpen = dev.steampad.emote.EmoteWheelController.isOpen();
        if (wasRadialOpen && !radialOpen) armHeldSuppression();
        if (wasEmoteOpen && !emoteOpen) armHeldSuppression();
        wasRadialOpen = radialOpen;
        wasEmoteOpen = emoteOpen;

        if (screenOpen) {
            if (!wasScreenOpen) onGuiOpened();
            tickGui(mc, cfg);
        } else {
            if (wasScreenOpen) { onGuiClosed(); armHeldSuppression(); }
            tickInGame(mc, cfg, handle);
        }
        wasScreenOpen = screenOpen;

        System.arraycopy(cur.buttons, 0, prevButtons, 0, GamepadSnapshot.BUTTON_COUNT);
        prevLt = GamepadBinds.trigDown(cur.axis(GamepadSnapshot.AXIS_LEFT_TRIGGER));
        prevRt = GamepadBinds.trigDown(cur.axis(GamepadSnapshot.AXIS_RIGHT_TRIGGER));
        VirtualBindInput.endTick();
    }

    // Bind resolution helpers (read the current snapshot through the per-controller binding map).
    private static boolean bHeld(ControllerConfig cfg, GamepadBinds.Bind bind) {
        // A Steam Input slot assigned to this bind (see SteamSlotDispatcher) — bypasses all the
        // physical-button gating below (suppression/chord/zoom-repurpose don't apply to it; there's no
        // real button behind it) so it behaves exactly like a dedicated physical button would.
        if (VirtualBindInput.isHeld(bind)) return true;
        // A held button that just closed a menu stays suppressed until physically released.
        String btn = GamepadBinds.button(cfg, bind);
        if (isSuppressed(btn)) return false;
        if (zoomEatsDpad(cfg, bind)) return false;
        if (thirdPersonAdjustEatsDpad(cfg, bind)) return false;
        // This button is someone ELSE's chord modifier and is currently completing that chord — a
        // plain held-type bind on it must not also fire for the rest of the hold (see tickChordModifierGate).
        if (GamepadBinds.chord(cfg, bind).isEmpty() && isChordModifierButton(cfg, btn)
                && modifierUsedThisHold.getOrDefault(btn, false)) {
            return false;
        }
        return GamepadBinds.held(cur, cfg, bind);
    }
    private static boolean bPressed(ControllerConfig cfg, GamepadBinds.Bind bind) {
        // See bHeld's comment — a slot-triggered virtual press skips physical gating entirely.
        if (VirtualBindInput.isPressedEdge(bind)) return true;
        if (zoomEatsDpad(cfg, bind)) return false;
        if (thirdPersonAdjustEatsDpad(cfg, bind)) return false;
        String btn = GamepadBinds.button(cfg, bind);
        // A bind currently resolved to BACK (default: PERSPECTIVE) defers to the Splitscreen chord's
        // release-signal instead of firing on BACK's press edge — see tickWindowArrangeChord().
        if (ConfigManager.getGlobal().windowArrangeEnabled && "BACK".equals(btn)) {
            return windowArrangeBackTapReleased;
        }
        // Generic chord-modifier gate (feedback: "DDOWN = tirar, cuando asigno DDOWN+A=chat... tambien
        // hace la primera accion de DDOWN"): if btn is used as some OTHER bind's chord modifier, this
        // plain bind's own tap defers to release-without-chord-completion — see tickChordModifierGate.
        // Scoped to TAP-only binds (bind.held==false): a held-type bind like RADIAL also calls
        // bPressed for its own edge-detection alongside bHeld's continuous open state — deferring that
        // edge to release broke "hold to open" for any bind sharing a button with someone else's chord
        // modifier (feedback: "DUP... si lo mantengo presionado, lo suelto y se abre y se cierra").
        if (!bind.held && GamepadBinds.chord(cfg, bind).isEmpty() && isChordModifierButton(cfg, btn)) {
            return modifierTapReleasedThisTick.getOrDefault(btn, false);
        }
        return GamepadBinds.pressed(cur, prevButtons, prevLt, prevRt, cfg, bind);
    }

    /**
     * While the zoom is active with D-pad adjust enabled, D-pad ↑/↓ belong to the zoom — any bind
     * sitting on those buttons (DROP on DDOWN by default, or user reassignments) is suppressed for
     * the duration so adjusting the level can't drop items / fire actions.
     */
    private static boolean zoomEatsDpad(ControllerConfig cfg, GamepadBinds.Bind bind) {
        if (bind == GamepadBinds.Bind.ZOOM) return false;   // the zoom bind itself may live on the D-pad
        return ZoomController.isButtonRepurposed(cfg, GamepadBinds.button(cfg, bind));
    }

    /**
     * While THIRD_PERSON_ADJUST is held, D-pad ↑/↓ belong to the free camera's distance instead of
     * whatever they normally do (DROP on DDOWN by default) — same button-based suppression shape as
     * zoomEatsDpad, so a user rebind onto DUP/DDOWN is still correctly suppressed.
     */
    private static boolean thirdPersonAdjustEatsDpad(ControllerConfig cfg, GamepadBinds.Bind bind) {
        if (bind == GamepadBinds.Bind.THIRD_PERSON_ADJUST || !ThirdPersonCameraController.isAdjusting()) return false;
        String btn = GamepadBinds.button(cfg, bind);
        return "DUP".equals(btn) || "DDOWN".equals(btn);
    }

    /**
     * Hold-to-modify handling for the Splitscreen chord (Select/BACK + RB). Runs every tick before
     * GUI/gameplay dispatch. While BACK is held with the feature enabled: each RB press cycles the
     * window layout ({@link dev.steampad.client.window.WindowArrangeController#cycle()}) and marks the
     * hold as "used", so BACK's own tap action does NOT also fire on release. A hold that ends without
     * any RB press sets {@link #windowArrangeBackTapReleased} for one tick — the signal the two BACK-tap
     * call sites (GUI cursor-mode cycle, gameplay Perspective) wait on instead of the press edge.
     */
    private static void tickWindowArrangeChord() {
        windowArrangeBackTapReleased = false;
        if (captureMode) return;   // BindingsScreen owns all input during a rebind capture
        if (!ConfigManager.getGlobal().windowArrangeEnabled) {
            backHeldForWindowArrange = false;   // stay reset so a later toggle-on starts clean
            return;
        }
        boolean backHeld = cur.button(GamepadSnapshot.BACK);
        if (backHeld && !backHeldForWindowArrange) backUsedAsWindowArrangeChord = false;   // fresh press
        if (backHeld && pressed(GamepadSnapshot.RIGHT_BUMPER)) {
            dev.steampad.client.window.WindowArrangeController.cycle();
            backUsedAsWindowArrangeChord = true;
        }
        if (!backHeld && backHeldForWindowArrange && !backUsedAsWindowArrangeChord) {
            windowArrangeBackTapReleased = true;   // plain tap — fire BACK's deferred action now
        }
        backHeldForWindowArrange = backHeld;
    }

    /** BACK's own tap edge, respecting the Splitscreen chord deferral above (see {@link #tickWindowArrangeChord()}). */
    private static boolean backTapPressed() {
        return ConfigManager.getGlobal().windowArrangeEnabled
                ? windowArrangeBackTapReleased
                : pressed(GamepadSnapshot.BACK);
    }

    /**
     * Runs every tick, for every user-configured chord: tracks whether its modifier button just
     * completed that chord (the chord's own main button was pressed while the modifier was held), and
     * whether the modifier was released without ever completing one — the release-without-use signal
     * that {@link #bPressed}/{@link #bHeld} consult to fire (or suppress) that same button's OWN plain
     * bind. See the {@link #modifierTapReleasedThisTick} field doc for why.
     */
    private static void tickChordModifierGate(ControllerConfig cfg) {
        modifierTapReleasedThisTick.clear();
        for (GamepadBinds.Bind b : GamepadBinds.Bind.values()) {
            String mod = GamepadBinds.chord(cfg, b);
            if (mod.isEmpty()) continue;
            boolean modHeldNow = GamepadBinds.isHeldRaw(cur, mod);
            boolean modHeldLast = modifierHeldLastTick.getOrDefault(mod, false);
            if (modHeldNow && !modHeldLast) modifierUsedThisHold.put(mod, false);   // fresh press, reset
            if (modHeldNow && rawPressedEdge(GamepadBinds.button(cfg, b))) {
                modifierUsedThisHold.put(mod, true);   // this chord just fired — the modifier is "spent"
            }
            if (!modHeldNow && modHeldLast && !modifierUsedThisHold.getOrDefault(mod, false)) {
                modifierTapReleasedThisTick.put(mod, true);   // plain tap — fire the deferred action now
            }
            modifierHeldLastTick.put(mod, modHeldNow);
        }
    }

    /** True if {@code btn} is used as the chord (modifier) button for at least one bind. */
    private static boolean isChordModifierButton(ControllerConfig cfg, String btn) {
        if (btn == null || btn.isEmpty()) return false;
        for (GamepadBinds.Bind b : GamepadBinds.Bind.values()) {
            if (btn.equals(GamepadBinds.chord(cfg, b))) return true;
        }
        return false;
    }

    /** Raw press-edge test for a physical button id, independent of any bind (mirrors GamepadBinds' own). */
    private static boolean rawPressedEdge(String btn) {
        if (btn == null || btn.isEmpty()) return false;
        if (GamepadBinds.isLeftTrigger(btn)) {
            return GamepadBinds.trigDown(cur.axis(GamepadSnapshot.AXIS_LEFT_TRIGGER)) && !prevLt;
        }
        if (GamepadBinds.isRightTrigger(btn)) {
            return GamepadBinds.trigDown(cur.axis(GamepadSnapshot.AXIS_RIGHT_TRIGGER)) && !prevRt;
        }
        int i = GamepadBinds.index(btn);
        return i >= 0 && cur.button(i) && !prevButtons[i];
    }

    // ---- In-game ---------------------------------------------------------------------------

    private static void tickInGame(MinecraftClient mc, ControllerConfig cfg, long handle) {
        if (mc.player == null) {
            releaseAllMovement(mc);
            return;
        }

        // Self-heal the cursor grab (mixed-input: "mouse camera dead in gameplay"), two layers:
        //
        // (1) Vanilla flag says UNLOCKED → lock it. GLFW then streams absolute positions that pin at
        //     the window edge → mouse deltas collapse to zero → the camera can't turn while clicks
        //     still work. Vanilla only re-locks on a mouse press / setScreen(null), and lockCursor()
        //     bails when unfocused — a menu closed out-of-focus leaves gameplay unlocked forever.
        //
        // (2) Vanilla flag says LOCKED but the REAL GLFW mode is not DISABLED → re-assert DISABLED.
        //     Vanilla never re-checks the actual mode once cursorLocked is true, so a desync (our
        //     cursor renderer is the only code that sets GLFW_CURSOR directly) is permanent and
        //     invisible: same dead-camera symptom, but layer (1) can't see it. Controlify's rule is
        //     the model here: never fight the grab, and in gameplay the grab must really be applied.
        //     glfwGetInputMode is a trivial getter — negligible at 20 Hz.
        if (!mc.mouse.isCursorLocked() && mc.isWindowFocused()) {
            mc.mouse.lockCursor();
        } else if (mc.mouse.isCursorLocked()) {
            long win = mc.getWindow().getHandle();
            if (org.lwjgl.glfw.GLFW.glfwGetInputMode(win, org.lwjgl.glfw.GLFW.GLFW_CURSOR)
                    != org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED) {
                org.lwjgl.glfw.GLFW.glfwSetInputMode(win, org.lwjgl.glfw.GLFW.GLFW_CURSOR,
                        org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED);
                dev.steampad.client.ui.VirtualCursorRenderer.invalidateOsCursorState();
                dev.steampad.util.LogUtil.warn(
                        "[SteamPad] Repaired a GLFW cursor-mode desync in gameplay (vanilla thought "
                        + "the cursor was grabbed but the real mode was not DISABLED). If mouse camera "
                        + "was dead, this line in the log CONFIRMS the diagnosis.");
            }
        }

        float[] right = DeadzoneProcessor.process(
                new float[]{cur.axis(GamepadSnapshot.AXIS_RIGHT_X), cur.axis(GamepadSnapshot.AXIS_RIGHT_Y)},
                cfg.rightStickDeadzone);
        // Merge the Steam Input joystick_camera action (a stick bound to "Joystick derecho — Cámara"
        // in Steam's configurator): when Steam routes the stick as an action, the virtual gamepad's
        // own axis goes silent, so without this merge that binding would kill the camera (B075).
        right = mergeStick(right, SteamSlotDispatcher.steamRightX(), SteamSlotDispatcher.steamRightY());

        // Radial menu: hold its bound button to open, right stick selects, release executes.
        // The dedicated EMOTE WHEEL (FASE 63) is a fully independent wheel system as of the
        // decoupling — its own EmoteWheelController, own config list, own open/close state — so the
        // two branches below never touch each other's selection/carousel state, and only one of the
        // two can be open at a time (each branch returns before the other bind is ever checked).
        if (dev.steampad.radial.RadialMenuController.isOpen()) {
            dev.steampad.radial.RadialMenuController.updateAnalog(right[0], right[1]);
            // LB/RB: carousel to the other wheel when a second one is configured (E10).
            if (pressed(GamepadSnapshot.LEFT_BUMPER))  dev.steampad.radial.RadialMenuController.switchPage(-1);
            if (pressed(GamepadSnapshot.RIGHT_BUMPER)) dev.steampad.radial.RadialMenuController.switchPage(1);
            // LT (while the wheel is open) opens the editor for the selected slot — like right-click in
            // the reference mod, but with the gamepad: select a slot, press LT → editor.
            boolean ltEdge = GamepadBinds.trigDown(cur.axis(GamepadSnapshot.AXIS_LEFT_TRIGGER)) && !prevLt;
            if (ltEdge) {
                releaseAllMovement(mc);
                dev.steampad.radial.RadialMenuController.openEditorForSelected();
                return;
            }
            // A = press-to-activate the highlighted slot now (works regardless of the slot's trigger
            // mode), then dismiss without re-executing. Gives a clear "press to use" path.
            if (pressed(GamepadSnapshot.A)) {
                dev.steampad.radial.RadialMenuController.activateSelected();
                dev.steampad.radial.RadialMenuController.dismiss();
                releaseAllMovement(mc);
                return;
            }
            if (!bHeld(cfg, GamepadBinds.Bind.RADIAL)) {
                dev.steampad.radial.RadialMenuController.confirmSelection();
                dev.steampad.radial.RadialMenuController.close();
                releaseAllMovement(mc);
            }
            return;   // suspend all other gameplay input while the wheel is open
        }
        if (dev.steampad.emote.EmoteWheelController.isOpen()) {
            dev.steampad.emote.EmoteWheelController.updateAnalog(right[0], right[1]);
            if (pressed(GamepadSnapshot.LEFT_BUMPER))  dev.steampad.emote.EmoteWheelController.switchPage(-1);
            if (pressed(GamepadSnapshot.RIGHT_BUMPER)) dev.steampad.emote.EmoteWheelController.switchPage(1);
            boolean ltEdge = GamepadBinds.trigDown(cur.axis(GamepadSnapshot.AXIS_LEFT_TRIGGER)) && !prevLt;
            if (ltEdge) {
                releaseAllMovement(mc);
                dev.steampad.emote.EmoteWheelController.openEditorForSelected();
                return;
            }
            if (pressed(GamepadSnapshot.A)) {
                dev.steampad.emote.EmoteWheelController.activateSelected();
                dev.steampad.emote.EmoteWheelController.dismiss();
                releaseAllMovement(mc);
                return;
            }
            if (!bHeld(cfg, GamepadBinds.Bind.EMOTE_WHEEL)) {
                dev.steampad.emote.EmoteWheelController.confirmSelection();
                dev.steampad.emote.EmoteWheelController.close();
                releaseAllMovement(mc);
            }
            return;   // suspend all other gameplay input while the emote wheel is open
        }
        if (bPressed(cfg, GamepadBinds.Bind.RADIAL)) {
            dev.steampad.radial.RadialMenuController.open(handle);
            releaseAllMovement(mc);
            return;
        }
        if (bPressed(cfg, GamepadBinds.Bind.EMOTE_WHEEL)) {
            dev.steampad.emote.EmoteWheelController.open(handle);
            releaseAllMovement(mc);
            return;
        }

        // Zoom (BetterZoom-style, ZoomController): hold or toggle per config; while active, D-pad
        // ↑/↓ steps the level (their normal bind actions are suppressed via zoomEatsDpad).
        if (cfg.zoomHoldMode) {
            ZoomController.setZooming(bHeld(cfg, GamepadBinds.Bind.ZOOM));
        } else if (bPressed(cfg, GamepadBinds.Bind.ZOOM)) {
            ZoomController.toggle();
        }
        if (ZoomController.isZooming() && cfg.zoomDpadAdjust) {
            if (pressed(GamepadSnapshot.DPAD_UP))   ZoomController.adjust(+1, cfg, mc);
            if (pressed(GamepadSnapshot.DPAD_DOWN)) ZoomController.adjust(-1, cfg, mc);
        }
        // A while zoomed = drop the temporary "let's go there" beacon (see ZoomController).
        if (ZoomController.isZooming() && cfg.zoomMarkerEnabled && pressed(GamepadSnapshot.A)) {
            ZoomController.placeMarker(mc, cfg);
        }
        ZoomController.tick(mc, cfg, handle);

        // Left stick → ANALOG movement intent (applied to the player's movement vector by the
        // KeyboardInput mixin, so a gentle push walks slowly and a full push runs — console feel).
        float[] left = DeadzoneProcessor.process(
                new float[]{cur.axis(GamepadSnapshot.AXIS_LEFT_X), cur.axis(GamepadSnapshot.AXIS_LEFT_Y)},
                cfg.leftStickDeadzone);
        // Same Steam Input merge as the camera above, for "Joystick izquierdo — Mover" (B075).
        left = mergeStick(left, SteamSlotDispatcher.steamLeftX(), SteamSlotDispatcher.steamLeftY());

        // Sneak (hold or toggle).
        boolean sneak;
        if (cfg.sneakMode == ControllerConfig.SneakMode.TOGGLE) {
            if (bPressed(cfg, GamepadBinds.Bind.SNEAK)) sneakToggled = !sneakToggled;
            sneak = sneakToggled;
        } else {
            sneak = bHeld(cfg, GamepadBinds.Bind.SNEAK);
        }
        // Sprint (hold or toggle).
        boolean sprint;
        if (cfg.sprintMode == ControllerConfig.SprintMode.TOGGLE) {
            if (bPressed(cfg, GamepadBinds.Bind.SPRINT)) sprintToggled = !sprintToggled;
            sprint = sprintToggled;
        } else {
            sprint = bHeld(cfg, GamepadBinds.Bind.SPRINT);
        }
        ControllerInputState.set(-left[0], -left[1], bHeld(cfg, GamepadBinds.Bind.JUMP), sneak, sprint);

        // Mine / use / player list (held) — written EDGE-TRIGGERED, not every tick. A continuous
        // "false" write while the pad is idle releases the KeyBinding state the PHYSICAL mouse set
        // on its own press, which broke mouse-held mining/using in gameplay (mixed-input fix): the
        // pad only touches the key state when ITS OWN held-state changes.
        boolean attackHeldNow = bHeld(cfg, GamepadBinds.Bind.ATTACK);
        boolean attackEdge = attackHeldNow && !prevAttackHeld;
        prevAttackHeld = holdOnChange(mc.options.attackKey, attackHeldNow, prevAttackHeld);
        prevUseHeld    = holdOnChange(mc.options.useKey,    bHeld(cfg, GamepadBinds.Bind.USE),    prevUseHeld);
        prevListHeld   = holdOnChange(mc.options.playerListKey, bHeld(cfg, GamepadBinds.Bind.PLAYER_LIST), prevListHeld);

        // Bedrock-style hold-to-swing (user-approved): while ATTACK stays held and the crosshair is
        // NOT on a block (block mining keeps vanilla's continuous path untouched), register a new
        // click each time the weapon cooldown refills — the console re-swing rhythm. Skipped on the
        // press edge itself (holdOnChange already registered that click). Our tick runs AFTER
        // vanilla consumed input this tick, so each tap is processed next tick, which resets the
        // cooldown — the >= 1.0 gate self-paces to the weapon's real speed with no extra timer.
        if (cfg.attackAutoRepeat && attackHeldNow && !attackEdge
                && !(mc.crosshairTarget != null
                        && mc.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK)
                && mc.player.getAttackCooldownProgress(0f) >= 1.0f) {
            tap(mc.options.attackKey);
        }

        // (Camera look is applied per-frame in CameraController; jump/sneak/sprint/move via the mixin.)

        // Discrete (edge) actions.
        if (bPressed(cfg, GamepadBinds.Bind.INVENTORY))   tap(mc.options.inventoryKey);
        if (bPressed(cfg, GamepadBinds.Bind.SWAP_HANDS))  tap(mc.options.swapHandsKey);
        if (bPressed(cfg, GamepadBinds.Bind.HOTBAR_PREV)) cycleHotbar(mc, -1);
        if (bPressed(cfg, GamepadBinds.Bind.HOTBAR_NEXT)) cycleHotbar(mc, +1);
        if (bPressed(cfg, GamepadBinds.Bind.DROP))        tap(mc.options.dropKey);
        if (bPressed(cfg, GamepadBinds.Bind.PERSPECTIVE)) tap(mc.options.togglePerspectiveKey);
        if (bPressed(cfg, GamepadBinds.Bind.CHAT))        tap(mc.options.chatKey);
        tickPauseLongPress(mc, cfg);   // PAUSE: tap = pause menu (on release), long hold = binds overlay
        if (bPressed(cfg, GamepadBinds.Bind.PICK_BLOCK))  tap(mc.options.pickItemKey);
        if (bPressed(cfg, GamepadBinds.Bind.SCREENSHOT))  tap(mc.options.screenshotKey);
        if (bPressed(cfg, GamepadBinds.Bind.HUD_TOGGLE))  mc.options.hudHidden = !mc.options.hudHidden;
        if (bPressed(cfg, GamepadBinds.Bind.THIRD_PERSON_SIDE_CYCLE))
            dev.steampad.input.ThirdPersonCameraController.cycleSide();
        if (bPressed(cfg, GamepadBinds.Bind.THIRD_PERSON_FREE_LOOK_TOGGLE))
            dev.steampad.input.ThirdPersonCameraController.toggleFreeLook();
        boolean adjustHeld = bHeld(cfg, GamepadBinds.Bind.THIRD_PERSON_ADJUST);
        if (adjustHeld && !dev.steampad.input.ThirdPersonCameraController.isAdjusting()) {
            dev.steampad.input.ThirdPersonCameraController.beginAdjust();
        } else if (!adjustHeld && dev.steampad.input.ThirdPersonCameraController.isAdjusting()) {
            dev.steampad.input.ThirdPersonCameraController.endAdjust();
        }
        if (dev.steampad.input.ThirdPersonCameraController.isAdjusting()) {
            if (pressed(GamepadSnapshot.DPAD_UP))   dev.steampad.input.ThirdPersonCameraController.adjustDistance(+1);
            if (pressed(GamepadSnapshot.DPAD_DOWN)) dev.steampad.input.ThirdPersonCameraController.adjustDistance(-1);
        }
        // Body-facing "rotate strategy" for the free camera — see ThirdPersonCameraController's own
        // scope note. No-ops entirely unless free-look is on.
        dev.steampad.input.ThirdPersonCameraController.tickRotateStrategy(mc,
                bHeld(cfg, GamepadBinds.Bind.ATTACK) || bHeld(cfg, GamepadBinds.Bind.USE));
        if (bPressed(cfg, GamepadBinds.Bind.DROP_STACK) && mc.player != null) {
            mc.player.dropSelectedItem(true);
        }
        if (bPressed(cfg, GamepadBinds.Bind.GYRO_TOGGLE)) {
            cfg.gyroEnabled = !cfg.gyroEnabled;
            ConfigManager.saveControllerConfig(handle);
        }

        // Extra bindings: any vanilla/mod keybind mapped to a controller button (dispatched as a tap).
        dispatchExtraBinds(mc, cfg);
    }

    // ---- PAUSE tap vs long-press binds overlay (feedback: "DEJAR PRESIONADO UN TIEMPO LARGO START
    // TE SALEN EN LA UI TODOS LOS BOTONES Y CHORDS ASIGNADOS... AL SOLTARLO DESAPARECE Y CAE EN LO
    // QUE LO TIENES CONFIGURADO") ---------------------------------------------------------------

    /** Ticks the PAUSE button must stay held before the overlay appears (~500 ms). */
    private static final int PAUSE_LONG_TICKS = 10;
    private static int pauseHeldTicks = 0;
    /** True only while a hold that BEGAN in gameplay is in progress — a hold left over from closing
     *  a menu (suppressed) or carried across a screen change must never fire the deferred pause. */
    private static boolean pauseHoldActive = false;
    /** True if at any point of the hold the PAUSE button was shadowed by another bind's held chord
     *  (e.g. "DUP+START = screenshot" firing) — the release must then NOT also open the pause menu,
     *  exactly the suppression buttonShadowedByHeldChord gave the old press-edge dispatch. */
    private static boolean pauseHoldShadowed = false;

    /**
     * PAUSE dispatch, rebuilt as tap-vs-hold: a short tap opens the pause menu on RELEASE (the same
     * imperceptible defer-to-release already used for BACK and for chord modifiers); holding past
     * {@link #PAUSE_LONG_TICKS} instead shows EVERY assigned bind + chord in the HUD
     * ({@code GameplayHudOverlay.setShowAllBinds}) until released — and that long hold does NOT open
     * the pause menu. Composes with the chord system: when the PAUSE button doubles as some other
     * bind's chord modifier, the deferred tap additionally requires the existing gate's
     * released-without-completing-a-chord signal, so a chord press can never also pause. A PAUSE
     * bind that itself HAS a chord keeps the classic edge dispatch untouched (a chorded pause can't
     * be a long-press candidate — the modifier hold would fight the overlay hold). Slot-assigned
     * virtual PAUSE presses stay instant via {@link VirtualBindInput}.
     */
    private static void tickPauseLongPress(MinecraftClient mc, ControllerConfig cfg) {
        if (VirtualBindInput.isPressedEdge(GamepadBinds.Bind.PAUSE)) {
            resetPauseHold();
            PauseGate.openPauseMenu(mc);
            return;
        }
        String btn = GamepadBinds.button(cfg, GamepadBinds.Bind.PAUSE);
        if (btn.isEmpty()) { resetPauseHold(); return; }
        if (!GamepadBinds.chord(cfg, GamepadBinds.Bind.PAUSE).isEmpty()) {
            resetPauseHold();
            if (bPressed(cfg, GamepadBinds.Bind.PAUSE)) PauseGate.openPauseMenu(mc);
            return;
        }
        boolean held = GamepadBinds.isHeldRaw(cur, btn) && !isSuppressed(btn);
        if (held) {
            if (!pauseHoldActive && rawPressedEdge(btn)) pauseHoldActive = true;
            if (pauseHoldActive) {
                if (GamepadBinds.buttonShadowedByHeldChord(cur, cfg, btn, GamepadBinds.Bind.PAUSE, null)) {
                    pauseHoldShadowed = true;
                }
                pauseHeldTicks++;
                if (pauseHeldTicks == PAUSE_LONG_TICKS) {
                    dev.steampad.client.hud.GameplayHudOverlay.setShowAllBinds(true);
                }
            }
            return;
        }
        boolean wasActive = pauseHoldActive;
        boolean wasShadowed = pauseHoldShadowed;
        int ticks = pauseHeldTicks;
        resetPauseHold();
        if (wasActive && !wasShadowed && ticks > 0 && ticks < PAUSE_LONG_TICKS
                && (!isChordModifierButton(cfg, btn)
                        || modifierTapReleasedThisTick.getOrDefault(btn, false))) {
            PauseGate.openPauseMenu(mc);
        }
    }

    private static void resetPauseHold() {
        pauseHeldTicks = 0;
        pauseHoldActive = false;
        pauseHoldShadowed = false;
        dev.steampad.client.hud.GameplayHudOverlay.setShowAllBinds(false);
    }

    /** Per-stick merge with the bridged Steam Input joystick actions: whichever source is deflected
     *  further wins the tick — zero-risk when the Steam side is idle/unbound (its values are 0). */
    private static float[] mergeStick(float[] sdl, float steamX, float steamY) {
        float sdlMag = sdl[0] * sdl[0] + sdl[1] * sdl[1];
        float stMag = steamX * steamX + steamY * steamY;
        return stMag > sdlMag ? new float[]{steamX, steamY} : sdl;
    }

    /** An extra (mod) keybind currently held down by a controller button. */
    private record HeldExtra(KeyBinding kb, InputUtil.Key key, String chord) {}
    private static final java.util.Map<String, HeldExtra> heldExtras = new java.util.HashMap<>();

    /**
     * Drives controller buttons mapped to arbitrary keybinds (chord-aware) with HOLD semantics:
     * press on the rising edge, keep pressed while the button (and its chord) stays held, release on
     * the falling edge. This is what makes mod keybinds that poll {@code isPressed()} — zoom mods,
     * push-to-talk, etc. — actually work; the old one-shot tap only served {@code wasPressed()}
     * consumers (the F13 "chords no funcionan" bug).
     */
    private static void dispatchExtraBinds(MinecraftClient mc, ControllerConfig cfg) {
        // Release pass: drop any held extra whose trigger button or required chord was released.
        for (var it = heldExtras.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            boolean stillHeld = GamepadBinds.isHeldRaw(cur, e.getKey())
                    && (e.getValue().chord().isEmpty() || GamepadBinds.isHeldRaw(cur, e.getValue().chord()));
            if (!stillHeld) {
                KeyTap.release(e.getValue().kb(), e.getValue().key());
                it.remove();
            }
        }

        if (cfg.extraBinds.isEmpty()) return;
        boolean zoomAdjusting = ZoomController.isZooming() && cfg.zoomDpadAdjust;
        for (var e : cfg.extraBinds.entrySet()) {
            String buttonId = e.getKey();
            String keyTranslation = e.getValue();
            if (keyTranslation == null || keyTranslation.isBlank()) continue;
            // D-pad ↑/↓ belong to the zoom-level adjust while zooming — same rule as zoomEatsDpad.
            if (zoomAdjusting && ("DUP".equals(buttonId) || "DDOWN".equals(buttonId))) continue;
            if (!buttonEdge(buttonId)) continue;
            String chord = cfg.extraChords.getOrDefault(keyTranslation, "");
            if (!chord.isEmpty()) {
                if (!GamepadBinds.isHeldRaw(cur, chord)) continue;   // chord required but not held
            } else if (GamepadBinds.buttonShadowedByHeldChord(cur, cfg, buttonId, null, keyTranslation)) {
                continue;   // plain extra bind suppressed while this button has an active chord
            }
            KeyBinding kb = findKeyBinding(mc, keyTranslation);
            if (kb != null && !heldExtras.containsKey(buttonId)) {
                InputUtil.Key key = KeyTap.hold(kb);
                heldExtras.put(buttonId, new HeldExtra(kb, key, chord));
            }
        }
    }

    /** Releases every extra keybind currently held (screen opened / controller vanished). */
    private static void releaseHeldExtras() {
        for (HeldExtra h : heldExtras.values()) KeyTap.release(h.kb(), h.key());
        heldExtras.clear();
    }

    /** Rising edge of a raw button id (no chord), including triggers. */
    private static boolean buttonEdge(String id) {
        if (GamepadBinds.isLeftTrigger(id))  return GamepadBinds.trigDown(cur.axis(GamepadSnapshot.AXIS_LEFT_TRIGGER)) && !prevLt;
        if (GamepadBinds.isRightTrigger(id)) return GamepadBinds.trigDown(cur.axis(GamepadSnapshot.AXIS_RIGHT_TRIGGER)) && !prevRt;
        int i = GamepadBinds.index(id);
        return i >= 0 && cur.button(i) && !prevButtons[i];
    }

    private static KeyBinding findKeyBinding(MinecraftClient mc, String translationKey) {
        for (KeyBinding kb : mc.options.allKeys) {
            if (kb.getId().equals(translationKey)) return kb;
        }
        return null;
    }

    private static void cycleHotbar(MinecraftClient mc, int dir) {
        if (mc.player == null) return;
        int slot = (mc.player.getInventory().getSelectedSlot() + dir + 9) % 9;
        mc.player.getInventory().setSelectedSlot(slot);
    }

    /** Pad-side held state of the vanilla keys it drives (edge-triggered writes — see tickInGame). */
    private static boolean prevAttackHeld = false, prevUseHeld = false, prevListHeld = false;

    /** Writes the KeyBinding state only when the pad's own held-state changed. Returns {@code down}. */
    private static boolean holdOnChange(KeyBinding key, boolean down, boolean prev) {
        if (down != prev) {
            hold(key, down);
            // Mirror vanilla Mouse.onMouseButton exactly: a press sets the held state AND registers
            // a click event (KeyBinding.onKeyPressed → timesPressed). setKeyPressed alone only feeds
            // the continuous path (mining the block you're LOOKING AT), so RT/LT never swung or used
            // in the AIR while a physical mouse click did both — the "triggers only work when
            // something is targeted" bug.
            if (down) tap(key);
        }
        return down;
    }

    private static void releaseAllMovement(MinecraftClient mc) {
        ControllerInputState.clear();   // stop analog movement (mixin)
        // Only release keys the PAD is holding — a blanket false would also cut a physical
        // mouse-held attack/use (the same mixed-input bug the edge-triggered writes fix).
        if (prevAttackHeld) hold(mc.options.attackKey, false);
        if (prevUseHeld)    hold(mc.options.useKey, false);
        if (prevListHeld)   hold(mc.options.playerListKey, false);
        prevAttackHeld = prevUseHeld = prevListHeld = false;
        releaseHeldExtras();            // never leave a mod keybind stuck down
        ZoomController.deactivate(mc);  // never leave the FOV zoomed / bobbing suppressed
    }

    /**
     * Public entry point for {@code SteamPadClient} to call the MOMENT the active fallback controller
     * is detected as disconnected — BEFORE a different physical device gets auto-activated in its
     * place, if one is available.
     *
     * <p>Bug fixed here (feedback: "confusión de mandos al desconectar... los botones no son como los
     * tenía configurados... tuve que reiniciar"): {@link #tick(long)}'s OWN "controller vanished"
     * cleanup (this same {@link #releaseAllMovement}) only ever ran when {@code tick()} was called
     * AGAIN with the SAME now-disconnected handle and {@code readSnapshot} failed for it. But
     * {@code SteamPadClient}'s own per-tick controller-detection logic clears
     * {@code ActiveControllerService}'s active handle and auto-activates a DIFFERENT device, all
     * within the SAME tick, BEFORE {@code InputBindingManager.tick() -> GamepadInputDispatcher.tick()}
     * even runs for that tick — so {@code tick()} is called with the NEW handle from the very first
     * affected tick onward, and NEVER sees the old handle's disconnect. Anything vanilla/mod key state
     * the old controller was holding at that exact instant (a mod keybind driven through
     * {@code heldExtras}, a held zoom, held attack/use/player-list) was never torn down — left stuck
     * exactly as it was, indefinitely, since nothing else re-evaluates it for a controller that no
     * longer ticks. A full restart "fixed" it only because restarting resets every vanilla
     * {@code KeyBinding.pressed} field regardless. Call this explicitly at the disconnect edge, before
     * the handle switches, so the old controller's state is torn down the same way it always was for
     * every OTHER "stop driving input" case (GUI opened, controller vanished mid-tick, etc.).
     */
    public static void releaseAllHeldStateOnControllerLoss() {
        // Diagnostic (this bug has no confirmed-in-hardware fix yet — log what was actually released,
        // if anything, so the next real test either shows this closing the gap or rules it out with
        // real evidence instead of another guess): only logs when there was something to release, so
        // a clean disconnect (nothing held) stays silent.
        if (prevAttackHeld || prevUseHeld || prevListHeld || !heldExtras.isEmpty() || ZoomController.isZooming()) {
            dev.steampad.util.LogUtil.info("[SteamPad] [controller-loss] Releasing stuck state from the "
                            + "disconnected controller: attack={} use={} playerList={} heldExtras={} zooming={}",
                    prevAttackHeld, prevUseHeld, prevListHeld, heldExtras.keySet(), ZoomController.isZooming());
        }
        releaseAllMovement(MinecraftClient.getInstance());
        prevHandle = 0L;   // force a full prev-state resync once a controller ticks again
    }

    // ---- GUI -------------------------------------------------------------------------------

    private static void onGuiOpened() {
        ControllerInputState.clear();   // no analog walking while a menu is open
        resetPauseHold();               // a screen appeared mid-hold — cancel the deferred pause tap
        MinecraftClient mc = MinecraftClient.getInstance();
        // Release the zoom on any screen: tickInGame stops running here, so a held zoom could never
        // see its button release — and a menu over a zoomed FOV with bobbing suppressed is confusing.
        ZoomController.deactivate(mc);
        boolean container = mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen;
        VirtualMouseController.onScreenOpened(mc.currentScreen, container);
    }

    private static void onGuiClosed() {
        releaseAllMovement(MinecraftClient.getInstance());
        if (virtualLmbDown) { VirtualMouseController.releaseLeft(); virtualLmbDown = false; }
        if (virtualRmbDown) { VirtualMouseController.releaseRight(); virtualRmbDown = false; }
        prevKbEligible = false;   // next screen with a focused field re-triggers the auto-open
    }

    private static void tickGui(MinecraftClient mc, ControllerConfig cfg) {
        if (captureMode) return;   // the bindings screen is capturing a button press
        if (swallowGuiTick) { swallowGuiTick = false; return; }   // capture just ended — eat this edge
        net.minecraft.client.gui.screen.Screen screen = mc.currentScreen;
        if (screen == null) return;

        boolean isTitleScreen = screen instanceof net.minecraft.client.gui.screen.TitleScreen;

        // Menu button (PAUSE/START) also CLOSES the pause menu — a guaranteed gamepad exit so you can
        // never get stuck in the pause menu needing the physical mouse (and "enter AND exit with the
        // menu button", as requested). Done before anything else so it always wins.
        if (screen instanceof net.minecraft.client.gui.screen.GameMenuScreen
                && bPressed(cfg, GamepadBinds.Bind.PAUSE)) {
            mc.setScreen(null);
            releaseAllMovement(mc);
            return;
        }

        // Virtual keyboard (Controlify model): it does NOT auto-open just because a text field got
        // focus — that popped it up "antes de tiempo" on screens with an auto-focused search box.
        // It opens (a) automatically ONLY on pure text-entry screens (chat, signs, books), or
        // (b) when the user explicitly presses A on the focused field / clicks it with the cursor.
        // While eligible-but-closed, the "[A] Keyboard" badge shows how to open it. B closes it.
        dev.steampad.client.keyboard.VirtualKeyboard.update(screen);
        boolean kbEligible = dev.steampad.client.keyboard.VirtualKeyboard.isEligible();
        boolean kbActive   = dev.steampad.client.keyboard.VirtualKeyboard.isActive();
        boolean kbRising   = kbEligible && !prevKbEligible;
        prevKbEligible = kbEligible;

        if (kbActive) {
            handleKeyboardInput(mc);
            VirtualMouseController.setStick(0f, 0f);   // no cursor movement while typing
            return;
        }

        boolean container = screen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>;

        // Long-press PAUSE also works inside menus/inventories ("EN TODOS LOS LUGARES, GAMEPLAY,
        // INVENTARIO, ETC."): while held past the threshold, the show-all flag is on — the container
        // hint row and the gameplay HUD (when this screen closes mid-hold it stays armed until
        // release) both honor it. No pause dispatch here: menu close stays on its own edge above.
        {
            String pauseBtn = GamepadBinds.button(cfg, GamepadBinds.Bind.PAUSE);
            boolean pauseHeld = !pauseBtn.isEmpty() && GamepadBinds.isHeldRaw(cur, pauseBtn)
                    && !isSuppressed(pauseBtn);
            if (pauseHeld) {
                if (++pauseHeldTicks >= PAUSE_LONG_TICKS) {
                    dev.steampad.client.hud.GameplayHudOverlay.setShowAllBinds(true);
                }
            } else if (pauseHeldTicks > 0) {
                resetPauseHold();
            }
        }

        // Manual force-open (feedback: "pon abrir teclado en el inventario con un chord+a, esto
        // deberia funcionar"): some mods' own search fields live entirely outside the screen's widget
        // tree (Roughly Enough Items' OverlaySearchField, confirmed by reading its source — see D063),
        // so eligibility can never auto-detect them. A dedicated, user-configurable chord (unbound by
        // default — assign it in Botones, e.g. a spare shoulder button + A) force-opens the keyboard
        // in any inventory-like screen regardless of what SteamPad itself can detect as focused.
        if (container && bPressed(cfg, GamepadBinds.Bind.OPEN_KEYBOARD)) {
            dev.steampad.client.keyboard.VirtualKeyboard.forceActivate();
            UiSoundService.playSelect();
            return;
        }

        // Auto-open only where typing is the screen's whole purpose (and only if the user wants it).
        if (kbRising && ConfigManager.getGlobal().virtualKeyboardAutoShow
                && dev.steampad.client.keyboard.VirtualKeyboard.isTextEntryScreen(screen)) {
            dev.steampad.client.keyboard.VirtualKeyboard.activate();
            UiSoundService.playNavigate();
            return;
        }

        // Select (BACK) cycles the virtual cursor mode: OFF → ON → AUTO. Deferred to the Splitscreen
        // chord's release-signal (see backTapPressed()) so holding Select to cycle window layouts via
        // Select+RB doesn't ALSO cycle the cursor mode.
        if (backTapPressed()) {
            VirtualMouseController.cycleMode();
            UiSoundService.playNavigate();
        }

        // LB/RB change tabs on tabbed screens.
        if (screen instanceof dev.steampad.screen.TabbedScreen tabbed) {
            if (pressed(GamepadSnapshot.LEFT_BUMPER))  { tabbed.steampad$prevTab(); UiSoundService.playNavigate(); }
            if (pressed(GamepadSnapshot.RIGHT_BUMPER)) { tabbed.steampad$nextTab(); UiSoundService.playNavigate(); }
        }

        // D-pad navigation. In a container the cursor hops cell to cell (Bedrock-style); elsewhere it
        // moves the focus spatially (up=up, down=down, left=left, right=right). In AUTO, the D-pad in a
        // non-container hands navigation to focus (hides the cursor); in a container it stays cursor-driven.
        // Holding a direction REPEATS after "Screen Repeat Navigation Delay" ms (the option existed but
        // navigation was edge-only — long lists needed one press per row).
        boolean anyDpadHeld = button(GamepadSnapshot.DPAD_RIGHT) || button(GamepadSnapshot.DPAD_LEFT)
                || button(GamepadSnapshot.DPAD_DOWN) || button(GamepadSnapshot.DPAD_UP);
        navHeldTicks = anyDpadHeld ? navHeldTicks + 1 : 0;
        int initialDelayTicks = Math.max(2, cfg.screenRepeatNavigationDelay / 50);   // ms → ticks
        boolean navRepeat = navHeldTicks > initialDelayTicks && (navHeldTicks % 3 == 0);

        int ndx, ndy;
        if (navRepeat) {
            ndx = (button(GamepadSnapshot.DPAD_RIGHT) ? 1 : 0) - (button(GamepadSnapshot.DPAD_LEFT) ? 1 : 0);
            ndy = (button(GamepadSnapshot.DPAD_DOWN) ? 1 : 0) - (button(GamepadSnapshot.DPAD_UP) ? 1 : 0);
        } else {
            ndx = (pressed(GamepadSnapshot.DPAD_RIGHT) ? 1 : 0) - (pressed(GamepadSnapshot.DPAD_LEFT) ? 1 : 0);
            ndy = (pressed(GamepadSnapshot.DPAD_DOWN) ? 1 : 0) - (pressed(GamepadSnapshot.DPAD_UP) ? 1 : 0);
        }
        if (ndx != 0 || ndy != 0) {
            if (container && screen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> hsNav) {
                VirtualMouseController.onStickUsed();   // keep/ensure the cursor visible in inventories
                SlotSnap.moveToNeighbor(hsNav, ndx, ndy);
                UiSoundService.playNavigate();
            } else {
                VirtualMouseController.onDpadUsed();
                focusMoveDir(screen, ndx, ndy);
            }
        }

        // Left stick → cursor VELOCITY (integrated per-frame in VirtualMouseController for smoothness).
        float[] mv = DeadzoneProcessor.process(
                new float[]{cur.axis(GamepadSnapshot.AXIS_LEFT_X), cur.axis(GamepadSnapshot.AXIS_LEFT_Y)}, 0.15f);
        boolean moving = mv[0] != 0f || mv[1] != 0f;
        if (moving) VirtualMouseController.onStickUsed();
        VirtualMouseController.setSensitivity(cfg.virtualMouseSensitivity);
        VirtualMouseController.setStick(mv[0], mv[1]);
        // Settle-snap only when the stick is released (so it doesn't fight active movement → no
        // jitter) — and only while the user hasn't turned the magnet off (feedback: "agrega una
        // opcion al mouse virtual... que pueda prenderse o desactivarse").
        if (VirtualMouseController.isShown() && !moving
                && ConfigManager.getGlobal().virtualMouseSnapEnabled) {
            if (screen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> hs) {
                SlotSnap.apply(hs);
            } else {
                WidgetSnap.apply(screen);
            }
        }

        // Right stick: when a slider is focused, X fine-adjusts its value (soft = millimetric, hard =
        // fast); otherwise Y scrolls the list. Scroll never changes option values (sliders ignore the
        // wheel, and the list owns the scroll).
        float[] rs = DeadzoneProcessor.process(
                new float[]{cur.axis(GamepadSnapshot.AXIS_RIGHT_X), cur.axis(GamepadSnapshot.AXIS_RIGHT_Y)}, 0.2f);
        net.minecraft.client.gui.Element focused = screen.getFocused();
        if (focused instanceof dev.steampad.client.ui.SteamSlider slider && Math.abs(rs[0]) > 0.001f) {
            // Squared magnitude → very fine at small deflection, fast at full (max ~3% of range/tick).
            double step = Math.signum(rs[0]) * (rs[0] * rs[0]) * 0.03;
            slider.nudge(step);
        }
        if (rs[1] != 0f) {
            if (screen instanceof dev.steampad.screen.SteamPadBaseScreen base && base.hasScroll()) {
                base.scrollBy((int) (rs[1] * 16));
            } else if (!(focused instanceof dev.steampad.client.ui.SteamSlider)) {
                VirtualMouseController.simulateScroll(-rs[1] * 1.2);
            }
        }

        // A = confirm. Cursor shown → hold-based click (press/release separately for drag support);
        // cursor hidden → edge tap that activates the focused widget.
        boolean aDown = cur.button(GamepadSnapshot.A);
        boolean xDown = cur.button(GamepadSnapshot.X);
        if (VirtualMouseController.isShown()) {
            // Hold-based: send press on down, release on up — allows dragging items.
            if (aDown != virtualLmbDown) {
                virtualLmbDown = aDown;
                if (aDown) {
                    VirtualMouseController.pressLeftDown();
                    // Clicking ON the focused text field opens the keyboard (Controlify press-to-open).
                    // The click itself may CLOSE/replace the screen (e.g. saving a keybind in the
                    // picker) — re-read currentScreen and only proceed if it's still the same one
                    // (this was a hard crash: NPE on the now-null screen).
                    if (kbEligible && mc.currentScreen == screen
                            && dev.steampad.client.keyboard.VirtualKeyboard.pointInFocusedTextField(
                                    screen, VirtualMouseController.getX(), VirtualMouseController.getY())) {
                        dev.steampad.client.keyboard.VirtualKeyboard.activate();
                        UiSoundService.playSelect();
                    }
                } else {
                    VirtualMouseController.releaseLeft();
                }
            }
            // X = right-click (hold-based, cursor visible). Skip on TitleScreen (X → Options).
            if (!isTitleScreen && xDown != virtualRmbDown) {
                virtualRmbDown = xDown;
                if (xDown) VirtualMouseController.pressRightDown();
                else VirtualMouseController.releaseRight();
            }
        } else {
            // No cursor: release any lingering hold state, then edge-activate.
            if (virtualLmbDown) { VirtualMouseController.releaseLeft(); virtualLmbDown = false; }
            if (virtualRmbDown) { VirtualMouseController.releaseRight(); virtualRmbDown = false; }
            if (pressed(GamepadSnapshot.A)) {
                if (kbEligible) {
                    // Focus is on a text field → A opens the keyboard (Controlify press-to-open).
                    dev.steampad.client.keyboard.VirtualKeyboard.activate();
                    UiSoundService.playSelect();
                } else if (!focusActivate(screen)) {
                    VirtualMouseController.simulateLeftClick();
                }
            }
            // X = right-click (no cursor). Skip on TitleScreen (X → Options).
            if (!isTitleScreen && pressed(GamepadSnapshot.X)) VirtualMouseController.simulateRightClick();
        }
        // Y = quick-move (shift-click) the slot under the cursor inside a container.
        if (pressed(GamepadSnapshot.Y) && screen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> hsq) {
            quickMove(mc, hsq);
        }
        // B = back/close. On TitleScreen B → Quit (handled above), not close().
        if (!isTitleScreen && pressed(GamepadSnapshot.B)) screen.close();
    }

    /**
     * Drives the on-screen keyboard while it is active. All input is fully consumed here — nothing
     * leaks to the underlying screen/gameplay. Shortcuts are intentionally scoped to this block.
     */
    private static void handleKeyboardInput(MinecraftClient mc) {
        // B closes the keyboard only — the underlying screen stays open.
        if (pressed(GamepadSnapshot.B)) {
            dev.steampad.client.keyboard.VirtualKeyboard.deactivate();
            UiSoundService.playNavigate();
            return;
        }

        // D-pad: key-to-key navigation (edge).
        if (pressed(GamepadSnapshot.DPAD_LEFT))  dev.steampad.client.keyboard.VirtualKeyboard.move(-1, 0);
        if (pressed(GamepadSnapshot.DPAD_RIGHT)) dev.steampad.client.keyboard.VirtualKeyboard.move(1, 0);
        if (pressed(GamepadSnapshot.DPAD_UP))    dev.steampad.client.keyboard.VirtualKeyboard.move(0, -1);
        if (pressed(GamepadSnapshot.DPAD_DOWN))  dev.steampad.client.keyboard.VirtualKeyboard.move(0, 1);

        // Left stick: FREE-FLOATING cursor over the keys — moves diagonally without stepping
        // key-to-key, always snapped to the nearest key (no virtual mouse while typing).
        float[] mv = DeadzoneProcessor.process(
                new float[]{cur.axis(GamepadSnapshot.AXIS_LEFT_X), cur.axis(GamepadSnapshot.AXIS_LEFT_Y)}, 0.15f);
        dev.steampad.client.keyboard.VirtualKeyboard.stickFloat(mv[0], mv[1]);

        // Dual-pointer mode (Steam Big Picture style): the RIGHT stick drives a second floating
        // orb, LB presses the left orb's key, RB the right orb's — A keeps working on the left/D-pad
        // selection. Caret movement relocates to the stick clicks (L3/R3) since LB/RB are now typing
        // keys. Classic mode keeps LB/RB as caret ←/→ exactly as before.
        boolean dualKb = ConfigManager.getGlobal().virtualKeyboardDualStick;
        if (dualKb) {
            float[] rv = DeadzoneProcessor.process(
                    new float[]{cur.axis(GamepadSnapshot.AXIS_RIGHT_X), cur.axis(GamepadSnapshot.AXIS_RIGHT_Y)}, 0.15f);
            dev.steampad.client.keyboard.VirtualKeyboard.stickFloatRight(rv[0], rv[1]);
        }

        // Shortcuts — all fully contained, do not reach gameplay/menu.
        // A = confirm key, Y = space, X = backspace, RT = enter, LT = shift.
        // Dual: LB/RB = press left/right pointer key, L3/R3 = caret ←/→.
        // Classic: LB/RB = caret ←/→.
        if (pressed(GamepadSnapshot.A)) dev.steampad.client.keyboard.VirtualKeyboard.pressSelected();
        if (pressed(GamepadSnapshot.Y)) dev.steampad.client.keyboard.VirtualKeyboard.space();
        if (pressed(GamepadSnapshot.X)) dev.steampad.client.keyboard.VirtualKeyboard.backspace();
        if (dualKb) {
            if (pressed(GamepadSnapshot.LEFT_BUMPER))  dev.steampad.client.keyboard.VirtualKeyboard.pressLeftPointer();
            if (pressed(GamepadSnapshot.RIGHT_BUMPER)) dev.steampad.client.keyboard.VirtualKeyboard.pressRightPointer();
            if (pressed(GamepadSnapshot.LEFT_THUMB))   dev.steampad.client.keyboard.VirtualKeyboard.caretLeft();
            if (pressed(GamepadSnapshot.RIGHT_THUMB))  dev.steampad.client.keyboard.VirtualKeyboard.caretRight();
        } else {
            if (pressed(GamepadSnapshot.LEFT_BUMPER))  dev.steampad.client.keyboard.VirtualKeyboard.caretLeft();
            if (pressed(GamepadSnapshot.RIGHT_BUMPER)) dev.steampad.client.keyboard.VirtualKeyboard.caretRight();
        }
        boolean rtEdge = GamepadBinds.trigDown(cur.axis(GamepadSnapshot.AXIS_RIGHT_TRIGGER)) && !prevRt;
        boolean ltEdge = GamepadBinds.trigDown(cur.axis(GamepadSnapshot.AXIS_LEFT_TRIGGER)) && !prevLt;
        // DUP+RT moves the panel top/bottom (feedback: "agrega un chord de teclado DUP+RT pra mover
        // el teclado hasta arriba, y ciclado que si presionas tambien vuelva a su posicion"). DUP's
        // own key-navigation "move up" above still fires on its own press edge regardless (a harmless
        // one-row nudge, unlike an irreversible action) — only RT's "enter" is actually suppressed
        // here, since firing Enter while repositioning the panel would be the real surprise.
        if (rtEdge) {
            if (cur.button(GamepadSnapshot.DPAD_UP)) {
                dev.steampad.client.keyboard.VirtualKeyboard.togglePosition();
                UiSoundService.playNavigate();
            } else {
                dev.steampad.client.keyboard.VirtualKeyboard.enter();
            }
        }
        if (ltEdge) dev.steampad.client.keyboard.VirtualKeyboard.shiftToggle();
    }

    /**
     * Title-screen two-press shortcut: first press focuses the button, second press activates it.
     * Buttons are matched by their translation key ("menu.options", "menu.quit", etc.).
     */
    private static void handleTitleButton(net.minecraft.client.gui.screen.Screen screen, String transKey) {
        for (var element : screen.children()) {
            if (!(element instanceof net.minecraft.client.gui.widget.ButtonWidget btn)) continue;
            if (!(btn.getMessage().getContent() instanceof net.minecraft.text.TranslatableTextContent tc)) continue;
            if (!transKey.equals(tc.getKey())) continue;
            if (screen.getFocused() == btn) {
                btn.onPress(new net.minecraft.client.input.MouseInput(0, 0));
                UiSoundService.playSelect();
            } else {
                screen.setFocused(btn);
                UiSoundService.playNavigate();
            }
            return;
        }
    }

    private static void focusMoveDir(net.minecraft.client.gui.screen.Screen s, int dx, int dy) {
        if (s instanceof dev.steampad.screen.SteamPadBaseScreen base) base.focusMoveDir(dx, dy);
        else GuiFocusNavigator.moveDir(s, dx, dy);
    }

    /** Shift-click (quick-move) the slot under the cursor via the interaction manager. */
    private static void quickMove(MinecraftClient mc, net.minecraft.client.gui.screen.ingame.HandledScreen<?> hs) {
        if (mc.interactionManager == null || mc.player == null) return;
        net.minecraft.screen.slot.Slot slot = SlotSnap.nearestSlotUnbounded(hs);
        if (slot == null) return;
        mc.interactionManager.clickSlot(hs.getScreenHandler().syncId, slot.id, 0,
                net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, mc.player);
    }

    private static boolean focusActivate(net.minecraft.client.gui.screen.Screen s) {
        if (s instanceof dev.steampad.screen.SteamPadBaseScreen base) return base.focusActivate();
        return GuiFocusNavigator.activate(s);
    }

    // ---- Helpers ---------------------------------------------------------------------------

    private static boolean button(int i) {
        return cur.button(i);
    }

    /** True if any button is down or a stick/trigger is meaningfully deflected this tick. */
    private static boolean hasActivity() {
        for (int i = 0; i < GamepadSnapshot.BUTTON_COUNT; i++) if (cur.button(i)) return true;
        if (Math.abs(cur.axis(GamepadSnapshot.AXIS_LEFT_X)) > 0.3f) return true;
        if (Math.abs(cur.axis(GamepadSnapshot.AXIS_LEFT_Y)) > 0.3f) return true;
        if (Math.abs(cur.axis(GamepadSnapshot.AXIS_RIGHT_X)) > 0.3f) return true;
        if (Math.abs(cur.axis(GamepadSnapshot.AXIS_RIGHT_Y)) > 0.3f) return true;
        // Triggers gated by the configured press threshold — NOT > 0. An analog trigger idling a hair
        // above zero (electrical noise, worn spring) otherwise counts as "activity" EVERY tick, which
        // keeps InputRouter pinned to GAMEPAD forever: markMouse() never lands, the AUTO cursor never
        // steps aside, and physical clicks land at the invisible desynced OS pointer — the "mouse can't
        // click anything in some menus" bug.
        if (GamepadBinds.trigDown(cur.axis(GamepadSnapshot.AXIS_LEFT_TRIGGER))) return true;
        if (GamepadBinds.trigDown(cur.axis(GamepadSnapshot.AXIS_RIGHT_TRIGGER))) return true;
        return false;
    }

    /** True only on the rising edge (up → down) of a button. */
    private static boolean pressed(int i) {
        return cur.button(i) && !prevButtons[i];
    }

    private static void hold(KeyBinding key, boolean down) {
        if (key == null) return;
        InputUtil.Key k = InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey());
        KeyBinding.setKeyPressed(k, down);
    }

    private static void tap(KeyBinding key) {
        if (key == null) return;
        InputUtil.Key k = InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey());
        KeyBinding.onKeyPressed(k);
    }
}
