package dev.steampad.client.keyboard;

import dev.steampad.config.ConfigManager;
import dev.steampad.input.StickSettleGate;
import dev.steampad.service.UiSoundService;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import dev.steampad.compat.mc.InputEventCompat;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * Controller-driven on-screen keyboard. Self-contained singleton (mirrors {@link
 * dev.steampad.input.VirtualMouseController}'s style) so it can never disturb existing input when off.
 *
 * <p>It types into whatever text field is focused on the current screen by routing the screen's own
 * {@code charTyped} / {@code keyPressed} — the same path the real keyboard uses — so it works for
 * vanilla and modded fields alike. Those signatures changed shape in 1.21.9, so every send goes
 * through {@link dev.steampad.compat.mc.InputEventCompat} rather than naming the types directly. It
 * becomes <b>eligible</b> when a text field is focused (or a known text-entry screen is open) and
 * <b>active</b> while it owns input.
 */
public final class VirtualKeyboard {

    public enum Layer { LETTERS, SYMBOLS }

    private static final List<List<KeyboardLayout.Key>> LETTERS = KeyboardLayout.letters();
    private static final List<List<KeyboardLayout.Key>> SYMBOLS = KeyboardLayout.symbols();

    private static boolean eligible = false;   // a text field is focused on the current screen
    private static boolean active = false;     // keyboard owns input + is drawn
    /** True while the current {@code active} session came from {@link #forceActivate()} — see its doc. */
    private static boolean forcedOpen = false;
    private static boolean shift = false;
    private static Layer layer = Layer.LETTERS;
    private static int row = 1, col = 0;       // start on the first letter row
    private static String lastScreenId = "";

    // Free-floating stick cursor: a point in screen px that the left stick moves DIAGONALLY and
    // freely (not key-to-key); the highlighted key is always the one nearest to it. Gives the
    // "unconstrained but always snapped" feel for fast text entry.
    private static double floatX = 0, floatY = 0;
    private static boolean floatValid = false;

    // Second free-floating pointer (Steam Big Picture dual-orb typing — feedback: "pon que funcionen
    // los dos joystick... igualito como funciona en el teclado de steam... en pantalla se ven dos
    // bolitas sobre las letras semi transparentes y se ve como tiene un snap precizo"): the RIGHT
    // stick drives this one, with its own highlighted key (row2/col2). LB presses the left pointer's
    // key, RB presses this one's (see GamepadInputDispatcher.handleKeyboardInput). Uses the exact
    // same dual-zone speed / brake / magnet response as the left pointer — one tuned feel, two orbs.
    private static double floatX2 = 0, floatY2 = 0;
    private static boolean floatValid2 = false;
    private static double prevMag2 = 0.0;
    private static int row2 = -1, col2 = -1;   // -1 until the right pointer first syncs

    // Selection/orb visibility (feedback: "no estén siempre presente... que se mueva cualquiera de
    // los dos o los dos aparezca" / "cuando utilizo el DPAD no es necesaria la rueda... que con los
    // sticks" / "el teclado sigue mostrando teclas seleccionadas y debe estar limpio hasta que uno de
    // los sticks se mueva, y después de un instante si no se mueve nada desaparece toda selección"):
    // the keyboard now starts "clean" (no orb, no corner brackets) until a stick is actually pushed,
    // and hides again after a short idle period with no further stick movement — D-PAD navigation is
    // exempt from the idle-hide (it's a deliberate discrete input, needs a persistent highlight to be
    // usable at all) and always shows its own selection while it's the active source.
    private static boolean leftEngaged = false;
    private static boolean rightEngaged = false;
    private static long lastLeftMoveMs = 0L;
    private static long lastRightMoveMs = 0L;
    /** True while D-pad (not a stick) is the active source for the main row/col selection. */
    private static boolean dpadShowing = false;
    private static final long SELECTION_IDLE_HIDE_MS = 1400;

    // Tap-vs-hold gesture (feedback: "que detecte un golpe de los sticks y que solo avance una
    // letra, cuando se mantenga actúe de forma normal"): a quick flick past TAP_MAG_THRESHOLD and
    // back down under TAP_RELEASE_THRESHOLD within TAP_MAX_MS steps exactly ONE key in the flick's
    // dominant direction (like a single D-pad press), overriding whatever partial drift the
    // continuous float logic made during that brief window. Holding past TAP_MAX_MS never triggers
    // the correction — the continuous behavior is completely untouched, so there's zero added
    // latency on a real hold (the check only ever fires AFTER release, retroactively).
    private static final double TAP_MAG_THRESHOLD = 0.55;
    private static final double TAP_RELEASE_THRESHOLD = 0.15;
    private static final long TAP_MAX_MS = 220;
    /**
     * How far the pointer may travel and still count as a tap, in nominal key widths.
     *
     * <p>Time alone is not enough to tell a tap from a fast deliberate move, and assuming it was is
     * the SECOND producer of "al soltar el stick... regresa una letra". At full deflection the pointer
     * runs at {@link #FLOAT_MAX_SPEED} px/tick — around 450 px/s — so a firm 200 ms flick legitimately
     * crosses two or three keys while still finishing inside {@link #TAP_MAX_MS}. The correction then
     * threw all of that away, rewound to where the push started and stepped a single key: the player
     * aimed three letters across, let go, and watched the selection jump backwards.
     *
     * <p>A real tap barely moves at all, so the distance settles the question that the clock cannot.
     * Below this the gesture was a flick and gets its clean one-key step; above it the player was
     * traversing and the pointer stays where they drove it.
     */
    /**
     * The tap only fires when the free-float did NOT already move the selection off the key the push
     * started on — the other half of the release "whiplash".
     *
     * <p>Reported as: "cuando llego a una letra y suelto retrocede y no selecciona donde tuve el
     * último foco." The release gate ({@link StickSettleGate}) already commits the aimed key the
     * instant the stick is let go, but the tap block runs AFTER that commit and used to unconditionally
     * rewind {@code row}/{@code col} to the push's starting key and step one key along the DOMINANT
     * axis. So a short diagonal nudge that had legitimately landed on, say, the up-right key was
     * rewritten to the key on the right — the highlight visibly jumping on release. Requiring the
     * selection to be untouched keeps the gesture doing the one job it exists for (a push too small
     * to move the pointer off its own key still steps exactly one key, like a D-pad press) and stops
     * it from overruling a selection the player already saw and aimed at.
     */
    private static final double TAP_MAX_TRAVEL_KEYS = 1.5;
    private static boolean leftPushing = false;
    private static long leftPushStartMs = 0L;
    private static int leftPushStartRow = 0, leftPushStartCol = 0;
    private static double leftPushDirX = 0, leftPushDirY = 0;
    private static double leftPushStartX = 0, leftPushStartY = 0;
    private static boolean rightPushing = false;
    private static long rightPushStartMs = 0L;
    private static int rightPushStartRow2 = 0, rightPushStartCol2 = 0;
    private static double rightPushDirX = 0, rightPushDirY = 0;
    private static double rightPushStartX = 0, rightPushStartY = 0;

    // Release handling, one per pointer. See StickSettleGate for the mechanism and the bug it fixes
    // ("al soltar el stick... muchas veces regresa una letra"): a released stick springs PAST centre
    // and reads a real deflection the other way, which used to drift the pointer one key back.
    private static final StickSettleGate leftGate = new StickSettleGate();
    private static final StickSettleGate rightGate = new StickSettleGate();

    // Press-sink visual (feedback: "al presionar una letra del teclado que se hunda, el mismo efecto
    // que con los botones"): the last key pressed by ANY source (A, LB, RB, D-pad+A) renders sunken
    // for a short moment. Purely visual.
    private static int lastPressRow = -1, lastPressCol = -1;
    private static long lastPressAtMs = 0L;
    private static final long PRESS_SINK_MS = 130;
    // Dual-zone stick response ("fast but with control", v3): the first FINE_ZONE_END of the stick's
    // travel is ALL precision — speed ramps quadratically up to just CRUISE_SPEED — and only the last
    // stretch before full deflection ramps steeply to FLOAT_MAX_SPEED. So the turbo exists (full tilt
    // flies across the keyboard) but it costs a deliberate push to the physical edge; everything
    // before that edge is a wide, controllable band.
    /** Fraction of stick deflection dedicated to the precision band. */
    private static final double FINE_ZONE_END = 0.85;
    /** Top speed (px/tick) of the precision band. Rebased a SECOND time (feedback: "velocidad del
     *  teclado actual 50% sea el 100% actual") — halved again so the slider's old 0.5× sweet spot is
     *  now the 1.0× default. Anyone with a saved slider value from before this rebase should bump it
     *  back up to 100% in Ajustes de Teclado — the number on the slider changed meaning, not just
     *  the underlying feel. */
    private static final double CRUISE_SPEED = 5.5;
    /** Absolute top speed (px/tick), reached only at FULL deflection (same halving, same reason). */
    private static final double FLOAT_MAX_SPEED = 22.5;
    /** Below this magnitude the stick counts as "easing off" and the magnet settles hard on a key. */
    private static final double SETTLE_MAG = 0.24;
    /** Magnetic pull toward the nearest key while actively pushing (gentle — must not trap slow moves). */
    private static final double PULL_MOVING = 0.1;
    /** Magnetic pull while BRAKING (stick magnitude falling): grabs the aimed key immediately, so
     *  releasing from turbo stops on the letter under the point instead of sliding past it. */
    private static final double PULL_BRAKE = 0.5;
    /** Magnetic pull when eased off — final settle, lands exactly on the aimed key. */
    private static final double PULL_SETTLE = 0.85;
    /** Magnitude drop per tick that counts as "the user is releasing" (arms the brake). */
    private static final double BRAKE_DELTA = 0.04;
    /** Speed multiplier while braking — kills the residual velocity during the release frames. */
    private static final double BRAKE_FACTOR = 0.3;
    /** Stick magnitude last tick, for detecting deceleration. */
    private static double prevMag = 0.0;

    // Time-based speed (B076/lag): the constants above are tuned as px per NOMINAL tick (1/20 s),
    // but stickFloat runs on the CLIENT TICK — if anything inflates tick time (heavy modpack, GC,
    // world gen), a px-per-tick pointer slows down linearly with the tick rate, which is exactly
    // "el teclado quedo LENTISIMO" while the game still renders smoothly. Scaling by the REAL time
    // between calls keeps the pointer's on-screen speed constant at any tick rate; at a healthy
    // 20 TPS the factor is 1.0 and the tuned feel is byte-identical. Clamped so a single hitch
    // (pause, breakpoint, world load) can't teleport the pointer across the board.
    private static long lastStickNanos = 0L;
    private static long lastStickNanos2 = 0L;

    /** Real elapsed ticks (1.0 = nominal 50 ms) since the previous call, clamped to [0.5, 3.0]. */
    private static double tickScale(boolean rightPointer) {
        long now = System.nanoTime();
        long last = rightPointer ? lastStickNanos2 : lastStickNanos;
        if (rightPointer) lastStickNanos2 = now; else lastStickNanos = now;
        if (last == 0L) return 1.0;
        double ticks = (now - last) / 50_000_000.0;
        return ticks < 0.5 ? 0.5 : (ticks > 3.0 ? 3.0 : ticks);
    }

    private VirtualKeyboard() {}

    public static boolean isActive() { return active; }
    public static boolean isEligible() { return eligible; }
    public static boolean isShift() { return shift; }
    public static Layer layer() { return layer; }
    public static int selRow() { return row; }
    public static int selCol() { return col; }
    public static List<List<KeyboardLayout.Key>> grid() { return layer == Layer.LETTERS ? LETTERS : SYMBOLS; }

    // ---- Dual-pointer accessors (renderer + dispatcher) ------------------------------------

    /** True when the Steam-style two-pointer mode is enabled in settings. */
    public static boolean isDualStick() { return ConfigManager.getGlobal().virtualKeyboardDualStick; }

    public static int selRow2() { return row2; }
    public static int selCol2() { return col2; }
    public static double pointerLX() { return floatX; }
    public static double pointerLY() { return floatY; }
    public static double pointerRX() { return floatX2; }
    public static double pointerRY() { return floatY2; }
    public static boolean pointerRValid() { return floatValid2; }

    /** Whether the renderer should draw the left/right orb AND its corner-bracket selection — true
     *  only while that stick was pushed recently (auto-hides after {@link #SELECTION_IDLE_HIDE_MS}
     *  of no further movement on it). Left also stays visible while D-pad is the active source (same
     *  row/col slot), since D-pad needs a persistent highlight to be usable. */
    public static boolean leftSelectionVisible() {
        return dpadShowing || (leftEngaged && System.currentTimeMillis() - lastLeftMoveMs < SELECTION_IDLE_HIDE_MS);
    }

    public static boolean rightSelectionVisible() {
        return rightEngaged && System.currentTimeMillis() - lastRightMoveMs < SELECTION_IDLE_HIDE_MS;
    }

    /** Row/col + timestamp of the last pressed key, for the renderer's press-sink effect. */
    public static boolean isKeySunken(int r, int c) {
        return r == lastPressRow && c == lastPressCol
                && System.currentTimeMillis() - lastPressAtMs < PRESS_SINK_MS;
    }

    /**
     * Called by the renderer each frame with the current panel: (re)syncs any invalidated pointer so
     * both orbs are always visible and sitting on a real key — the same lazy resync the stick code
     * does, just guaranteed to happen before drawing. Idempotent.
     */
    public static void ensurePointersForRender(KeyboardGeometry.Panel p) {
        if (!floatValid) { syncFloatToSelection(p); prevMag = 0.0; }
        if (isDualStick() && !floatValid2) syncFloatRight(p);
    }

    /**
     * Recomputes eligibility for the current screen each tick. Eligible when a text field is focused
     * (and the feature is enabled). Active state is controlled manually: A to open, B to close.
     */
    public static void update(Screen screen) {
        boolean enabled = ConfigManager.getGlobal().virtualKeyboardEnabled;
        if (!enabled || screen == null) {
            eligible = false; active = false; forcedOpen = false;
            fieldWasFocused = false; fieldJustFocused = false;
            return;
        }

        // Reset per-screen state when the screen changes.
        String id = screen.getClass().getName();
        boolean screenChanged = !id.equals(lastScreenId);
        if (screenChanged) {
            lastScreenId = id;
            active = false;   // always start closed on a new screen
            forcedOpen = false;
            dismissedScreenId = "";   // a new screen may auto-open again (see autoOpenWanted)
            positionTop = false;   // always start at the default (bottom) position on a new screen
            floatValid = false;
            floatValid2 = false;
            leftEngaged = false;
            rightEngaged = false;
            dpadShowing = false;
            lastLeftMoveMs = 0L;
            lastRightMoveMs = 0L;
            leftPushing = false;
            rightPushing = false;
            leftGate.reset();
            rightGate.reset();
            row2 = -1; col2 = -1;
            clampSelection();
        }

        // Spelled out rather than calling hasTextEntry(screen): this needs the two halves separately
        // (the focus edge below is about the FIELD, not about chat/sign/book screens that are eligible
        // with no field at all), and resolving the field is a widget-tree walk worth doing once.
        // Same two conditions, same order — keep them in step with hasTextEntry.
        boolean hasField = findTextField(screen) != null;
        eligible = hasField || isTextEntryScreen(screen);

        // Focus EDGE, not focus LEVEL: a text field that went unfocused → focused while we stayed on
        // the same screen is the user having just clicked/opened a search box, which is exactly when a
        // phone pops its keyboard up (feedback: "cuando le presionas no sale automaticamente el
        // teclado, deberia de salir" — the recipe book's search, and the creative search tab).
        //
        // Deliberately NOT the level ("a field is focused"): that is the form this file already tried
        // and reverted, because screens that focus a search box in init() popped the keyboard up
        // "antes de tiempo". Seeding the flag on the tick the screen changes is what keeps that fixed —
        // a field focused from the start is never an edge, so those screens still wait for A.
        if (screenChanged) {
            fieldJustFocused = false;
        } else if (hasField && !fieldWasFocused) {
            fieldJustFocused = true;
            // An explicit new focus is a fresh intent to type: it outranks an earlier B-to-close on
            // this same screen, which would otherwise suppress the auto-open forever (see deactivate).
            dismissedScreenId = "";
        }
        fieldWasFocused = hasField;

        // A forceActivate() session (see its doc) is intentionally open despite no detectable field —
        // don't let ineligibility auto-close it the very next tick; only B (deactivate()) or leaving
        // the screen should end it.
        if (!eligible && !forcedOpen) { active = false; }
    }

    /** Was a text field focused on the previous tick — the other half of the edge in {@link #update}. */
    private static boolean fieldWasFocused = false;

    /** A text field gained focus on this screen after it opened; see {@link #autoOpenWanted}. */
    private static boolean fieldJustFocused = false;

    /**
     * Opens the keyboard. Called when A is pressed on a focused text field (Controlify-style manual
     * open), or automatically only on pure text-entry screens (chat, signs, books) where typing is
     * the whole point — see {@link #isTextEntryScreen}.
     */
    public static void activate() {
        if (!eligible) return;
        active = true;
        forcedOpen = false;
        fieldJustFocused = false;   // the edge has been consumed
        floatValid = false;   // the float cursor re-syncs to the selected key on first stick use
        leftGate.reset();
        rightGate.reset();
        leftEngaged = false;
        rightEngaged = false;
        dpadShowing = false;
        lastLeftMoveMs = 0L;
        lastRightMoveMs = 0L;
    }

    /**
     * Opens the keyboard unconditionally, bypassing {@link #eligible} — the manual chord+A toggle
     * (feedback: "pon abrir teclado en el inventario con un chord+a"). Exists because eligibility can
     * only ever detect text fields reachable through the screen's own widget tree; some mods' fields
     * (Roughly Enough Items' search box) live entirely outside it and can never be auto-detected — see
     * D063. Typed characters still reach whatever is actually focused via {@link #typeChar}'s real
     * {@code Keyboard.onChar} routing regardless of what SteamPad itself could detect.
     */
    public static void forceActivate() {
        active = true;
        forcedOpen = true;
        fieldJustFocused = false;   // the edge has been consumed
        floatValid = false;
        leftGate.reset();
        rightGate.reset();
        leftEngaged = false;
        rightEngaged = false;
        dpadShowing = false;
        lastLeftMoveMs = 0L;
        lastRightMoveMs = 0L;
    }

    /** Closes the keyboard (called when B is pressed while keyboard is active). */
    public static void deactivate() {
        active = false;
        forcedOpen = false;
        fieldJustFocused = false;   // B means "not now", so the pending edge dies with it
        // Remember that the user closed it HERE, so the level-triggered auto-open (see
        // {@link #autoOpenWanted}) does not immediately pop it back up on the very next tick.
        dismissedScreenId = lastScreenId;
    }

    /** Screen the user last closed the keyboard on — see {@link #deactivate}/{@link #autoOpenWanted}. */
    private static String dismissedScreenId = "";

    /**
     * True when the keyboard should auto-open on this screen right now.
     *
     * <p>Deliberately LEVEL-triggered (a standing condition), not edge-triggered. The previous
     * edge-triggered form fired only on the single tick where eligibility flipped false→true, so any
     * tick that swallowed that transition — a screen opening while eligibility was already true, a
     * tick consumed by another early-return in the dispatcher — silently lost the auto-open forever
     * with no way to recover except leaving and re-entering the screen. That is consistent with
     * "el teclado sigue sin abrir en Letreros". Checking the condition every tick instead cannot miss
     * it; {@link #dismissedScreenId} is what keeps B-to-close from being undone a tick later.
     */
    public static boolean autoOpenWanted(Screen screen) {
        return eligible && !active
                && (isTextEntryScreen(screen) || fieldJustFocused)
                && !lastScreenId.equals(dismissedScreenId);
    }

    /**
     * Vertical push (px) that chat content must move UP so it sits above the open keyboard — the
     * keyboard panel's full height. 0 when the keyboard is closed, the screen is not the chat, or the
     * keyboard has been manually moved to the TOP of the screen (feedback: "en gamplay en chat, si se
     * mueve hacia arriba vuelve la posicion de la entrada de texto de chat original" — with the
     * keyboard out of the way up top, vanilla's own bottom-anchored chat box no longer needs to move).
     * Read every frame by the Chat/SignEdit/BookEdit screen mixins (Controlify-style push-up).
     *
     * <p>Covers every {@link #isTextEntryScreen} screen, not just chat — sign and book editing
     * auto-open the keyboard exactly the same way (see that method's doc), so their own UI needs the
     * same treatment or the keyboard panel overlaps it just as it would chat's input line.
     */
    public static int textEntryPushUp(Screen screen) {
        if (!active || positionTop || !isTextEntryScreen(screen)) return 0;
        KeyboardGeometry.Panel p = KeyboardGeometry.layout(screen.width, screen.height, true, false, grid());
        int panelHeight = screen.height - p.panelTop();

        // How far to push depends on where the screen anchors its content, and the two cases are
        // opposite. Chat's input line is anchored to the BOTTOM edge, so it has to clear the whole
        // panel — the full height is correct there and is the value already confirmed working.
        //
        // Sign and book editors instead draw their content CENTRED on the screen. Pushing those by the
        // full panel height moved them roughly half a panel too far and shoved the sign off the top
        // ("empuja todo el cartel y el cartel sale de la pantalla"). What they need is to be re-centred
        // inside the space the keyboard leaves free: content centred at height/2 should end up centred
        // at panelTop/2, so the shift is (height - panelTop)/2 — exactly half the panel. Derived from
        // the geometry rather than tuned by eye, so it holds at any GUI scale or keyboard size.
        boolean centredContent = !(screen instanceof net.minecraft.client.gui.screens.ChatScreen);
        return centredContent ? panelHeight / 2 : panelHeight;
    }

    // ---- Push-up re-entrancy guard -------------------------------------------------------------
    //
    // The chat history is NOT drawn independently of the chat screen: ChatScreen.render() calls
    // ChatComponent.render() itself, as its very first statement, on BOTH target versions — and the
    // HUD's own Gui.renderChat() opens with `if (chat.isChatFocused()) return;`, so while the chat
    // screen is up the screen is the ONLY thing drawing that history. All three facts javap-confirmed
    // on the mapped 1.21.1 and 1.21.10 jars.
    //
    // So ChatHudMixin's push was always nested INSIDE ChatScreenMixin's push, and the history got
    // translated twice while the input line got translated once — the history flying far above the
    // typing bar ("cuando abres el teclado el chat se manda muy por encima de la barra de escritura").
    // This flag makes the outer push claim the frame; the inner one stands down and only acts if some
    // other mod ever draws the history outside ChatScreen.render(), which is what it was written for.

    private static boolean pushInProgress = false;

    /** True while a screen-level push-up translation is already applied around this draw. */
    public static boolean isPushInProgress() { return pushInProgress; }

    /** Claimed by the screen-level push (ChatScreenMixin) for the duration of its render. */
    public static void setPushInProgress(boolean inProgress) { pushInProgress = inProgress; }

    // ---- Panel position (feedback: "agrega un chord de teclado DUP+RT pra mover el teclado hasta
    // arriba, y ciclado que si presionas tambien vuelva a su posicion, esto es para que no tape la
    // caja de escritura de algunos mods") -----------------------------------------------------

    private static boolean positionTop = false;

    /** True while the panel is anchored to the top of the screen instead of the bottom. */
    public static boolean isPositionTop() { return positionTop; }

    /** Toggles the panel between its default bottom position and the top (DUP+RT). */
    public static void togglePosition() {
        positionTop = !positionTop;
        floatValid = false;   // the float cursor re-syncs to the selected key at its new panel rect
        floatValid2 = false;  // the right pointer re-syncs at the new panel rect too
        leftGate.reset();     // the panel moved under both pointers — treat it as a fresh start
        rightGate.reset();
    }

    /**
     * True for screens whose sole purpose is text entry — the keyboard auto-opens there (chat,
     * sign editing, book editing). Regular screens with a focused search box do NOT auto-open;
     * they show the "[A] Keyboard" badge and wait for an explicit A press (Controlify behaviour).
     */
    public static boolean isTextEntryScreen(Screen screen) {
        if (screen == null) return false;
        // instanceof, NEVER getClass().getName().contains("…"). In a production (remapped) install
        // Minecraft's classes carry INTERMEDIARY names — screen.getClass().getName() returns
        // "net.minecraft.class_7529", never "SignEditScreen" — while a string literal in our source is
        // not remapped at all. Confirmed by disassembling the shipped jar: the literals
        // "SignEditScreen"/"BookEditScreen" survive verbatim while the ChatScreen type reference became
        // class_408. So the old string test passed in dev and ALWAYS failed in the real game: chat
        // (an instanceof) worked, signs and books never did — no auto-open, no badge, nothing
        // ("el teclado sigue sin abrir en Letreros", "no aparece el BIND"). instanceof is remapped by
        // Loom along with everything else and therefore works in both environments.
        return screen instanceof net.minecraft.client.gui.screens.ChatScreen
                || screen instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
                || screen instanceof net.minecraft.client.gui.screens.inventory.BookEditScreen;
    }

    /**
     * True if the point (in scaled GUI coords) lies inside the focused text field — used so a
     * virtual-cursor click ON the field opens the keyboard, like Controlify's press-to-open.
     */
    public static boolean pointInFocusedTextField(Screen screen, double x, double y) {
        if (screen == null) return false;   // a click can close the screen mid-tick — never NPE
        GuiEventListener f = findTextField(screen);
        if (f instanceof net.minecraft.client.gui.components.AbstractWidget w) {
            return x >= w.getX() && x < w.getX() + w.getWidth()
                    && y >= w.getY() && y < w.getY() + w.getHeight();
        }
        return false;
    }

    // ---- Navigation ----------------------------------------------------------------------

    public static void move(int dx, int dy) {
        var g = grid();
        if (g.isEmpty()) return;
        if (dy != 0) {
            row = Math.floorMod(row + dy, g.size());
        }
        if (dx != 0) {
            col += dx;
        }
        clampSelection();
        floatValid = false;   // D-pad took over — the float cursor re-syncs to this key
        // D-pad navigation doesn't need either stick's orb (feedback: "cuando utilizo el DPAD... no
        // es necesaria la rueda... que con los sticks") — hide both until a stick moves again. D-pad
        // becomes the active source for the main (left/classic) slot — its own highlight stays on
        // regardless of idle time (see leftSelectionVisible()), and "A" should press row/col again
        // even if the right stick was touched more recently before this D-pad move.
        leftEngaged = false;
        rightEngaged = false;
        dpadShowing = true;
        lastLeftMoveMs = System.currentTimeMillis();
    }

    /**
     * Left stick → FREE-FLOATING selection over the keyboard: moves diagonally in any direction
     * without stepping key-to-key, and the highlighted key is always the nearest one (clamped-rect
     * distance, so wide keys like Space attract fairly).
     *
     * <p>Response (v3, dual-zone): the wide precision band (up to {@link #FINE_ZONE_END} of travel)
     * ramps quadratically to {@link #CRUISE_SPEED} only — a small push reliably walks to the ADJACENT
     * key — and the turbo to {@link #FLOAT_MAX_SPEED} lives exclusively in the last stretch before
     * full deflection. On top of that, a deceleration BRAKE: the moment the stick magnitude starts
     * falling (the user is releasing), residual speed is cut hard and the magnet grabs the aimed key,
     * so flying at turbo and letting go stops ON the intended letter instead of sliding past it. The
     * magnet stays GENTLE while actively pushing (a strong magnet at low speed traps the point inside
     * the current key — the old "unresponsive at small deflections" feel).
     */
    public static void stickFloat(float sx, float sy) {
        if (!active) return;
        Screen s = mc().screen;
        if (s == null) return;
        KeyboardGeometry.Panel p = KeyboardGeometry.layout(
                s.width, s.height, s instanceof net.minecraft.client.gui.screens.ChatScreen, positionTop, grid());
        if (p.keys().isEmpty()) return;
        // Any real push of THIS stick makes its orb/selection visible and hands the main slot back
        // to the stick from the D-pad (see the engaged-flag doc above).
        if (sx * sx + sy * sy > 0.01) {
            leftEngaged = true;
            dpadShowing = false;
            lastLeftMoveMs = System.currentTimeMillis();
        }

        if (!floatValid) { syncFloatToSelection(p); prevMag = 0.0; }

        double mag = Math.min(1.0, Math.sqrt(sx * sx + sy * sy));
        // Release gate FIRST, before either response mode — spring-back is a property of the stick,
        // so both the velocity and the absolute-pointer mode need protecting from it.
        boolean drive = leftGate.accept(mag, System.currentTimeMillis());
        if (leftGate.consumeJustSettled()) commitToNearest(p, false);

        if (ConfigManager.getGlobal().virtualKeyboardStickMode == dev.steampad.config.GlobalConfig.KeyboardStickMode.POINTER) {
            if (drive) stickPointer(sx, sy, p);
            return;
        }

        boolean braking = mag < prevMag - BRAKE_DELTA;
        prevMag = mag;

        if (!leftPushing && mag >= TAP_MAG_THRESHOLD) {
            leftPushing = true;
            leftPushStartMs = System.currentTimeMillis();
            leftPushStartRow = row;
            leftPushStartCol = col;
            leftPushDirX = sx;
            leftPushDirY = sy;
            leftPushStartX = floatX;
            leftPushStartY = floatY;
        } else if (leftPushing && mag < TAP_RELEASE_THRESHOLD) {
            leftPushing = false;
            if (System.currentTimeMillis() - leftPushStartMs < TAP_MAX_MS
                    && row == leftPushStartRow && col == leftPushStartCol
                    && isTapTravel(p, floatX - leftPushStartX, floatY - leftPushStartY)) {
                stepSelectionOnce(leftPushDirX, leftPushDirY, false);
                return;   // tap resolved — skip this frame's continuous drift entirely
            }
        }

        if (!drive) {
            tickScale(false);   // frozen by the release gate: no drift AND no re-snap, so the key
            return;             // committed above cannot be pulled off by the spring-back.
        }

        if (mag > 0.001) {
            double speed = floatSpeed(mag, braking) * tickScale(false);
            floatX = clamp(floatX + (sx / mag) * speed, p.gridLeft(), p.gridRight());
            floatY = clamp(floatY + (sy / mag) * speed, p.gridTop(), p.gridBottom());
        } else {
            tickScale(false);   // keep the clock fresh while idle so the next push isn't over-scaled
        }

        KeyboardGeometry.KeyRect nearest = p.nearest(floatX, floatY);
        if (nearest != null) {
            if (nearest.row() != row || nearest.col() != col) {
                row = nearest.row();
                col = nearest.col();
            }
            double pull = mag <= SETTLE_MAG ? PULL_SETTLE : (braking ? PULL_BRAKE : PULL_MOVING);
            floatX += (nearest.centerX() - floatX) * pull;
            floatY += (nearest.centerY() - floatY) * pull;
        }
    }

    /**
     * Whether a gesture's travel is small enough to still count as a tap rather than a deliberate
     * move — see {@link #TAP_MAX_TRAVEL_KEYS}.
     *
     * <p>The nominal key width comes from the grid itself: the letter rows lay ten keys across the
     * full grid width, so a tenth of it is a real key, not an average skewed by the wide Space bar.
     * Deriving it means the threshold holds at any GUI scale, window size or keyboard height.
     */
    private static boolean isTapTravel(KeyboardGeometry.Panel p, double dx, double dy) {
        double keyW = (p.gridRight() - p.gridLeft()) / 10.0;
        if (keyW <= 0) return true;
        double limit = keyW * TAP_MAX_TRAVEL_KEYS;
        return dx * dx + dy * dy <= limit * limit;
    }

    /** Steps the selection by exactly one key in the dominant direction of (dirX, dirY) — the tap
     *  gesture's resolution, same idea as a single D-pad press but without D-pad's side effects
     *  (doesn't touch dpadShowing/engaged — a stick tap is still stick activity, not D-pad takeover). */
    private static void stepSelectionOnce(double dirX, double dirY, boolean rightPointer) {
        var g = grid();
        if (g.isEmpty()) return;
        int ddx, ddy;
        if (Math.abs(dirX) > Math.abs(dirY)) { ddx = dirX > 0 ? 1 : -1; ddy = 0; }
        else { ddx = 0; ddy = dirY > 0 ? 1 : -1; }
        if (rightPointer) {
            if (row2 < 0) return;   // right pointer never synced — nothing to step from
            if (ddy != 0) row2 = Math.floorMod(row2 + ddy, g.size());
            int rs2 = g.get(row2).size();
            if (ddx != 0) {
                col2 += ddx;
                if (col2 < 0) col2 = rs2 - 1;
                if (col2 >= rs2) col2 = 0;
            }
            floatValid2 = false;
        } else {
            if (ddy != 0) row = Math.floorMod(row + ddy, g.size());
            if (ddx != 0) col += ddx;
            clampSelection();
            floatValid = false;
        }
    }

    /** The tuned dual-zone speed curve (see {@link #stickFloat}'s doc) — shared verbatim by both
     *  pointers so the two orbs feel identical. */
    private static double floatSpeed(double mag, boolean braking) {
        double speed;
        if (mag <= FINE_ZONE_END) {
            double t = mag / FINE_ZONE_END;
            speed = t * t * CRUISE_SPEED;
        } else {
            double t = (mag - FINE_ZONE_END) / (1.0 - FINE_ZONE_END);
            speed = CRUISE_SPEED + Math.pow(t, 1.5) * (FLOAT_MAX_SPEED - CRUISE_SPEED);
        }
        speed *= ConfigManager.getGlobal().virtualKeyboardStickSpeed;
        if (braking) speed *= BRAKE_FACTOR;
        return speed;
    }

    /**
     * RIGHT stick → the second floating pointer (dual-stick mode only). Exactly the same response
     * as {@link #stickFloat}: dual-zone speed, deceleration brake, magnetic snap — operating on its
     * own position and its own highlighted key ({@link #selRow2()}/{@link #selCol2()}), pressed with
     * RB. In POINTER (absolute) mode it maps the stick to the RIGHT HALF of the keyboard, mirroring
     * how Steam's own dual-trackpad typing splits the board between the two inputs.
     */
    public static void stickFloatRight(float sx, float sy) {
        if (!active || !isDualStick()) return;
        Screen s = mc().screen;
        if (s == null) return;
        KeyboardGeometry.Panel p = KeyboardGeometry.layout(
                s.width, s.height, s instanceof net.minecraft.client.gui.screens.ChatScreen, positionTop, grid());
        if (p.keys().isEmpty()) return;
        // Any real push of THIS stick makes its orb/selection visible (see the engaged-flag doc above).
        if (sx * sx + sy * sy > 0.01) {
            rightEngaged = true;
            lastRightMoveMs = System.currentTimeMillis();
        }

        if (!floatValid2) syncFloatRight(p);

        double mag = Math.min(1.0, Math.sqrt(sx * sx + sy * sy));
        boolean drive = rightGate.accept(mag, System.currentTimeMillis());
        if (rightGate.consumeJustSettled()) commitToNearest(p, true);

        if (ConfigManager.getGlobal().virtualKeyboardStickMode == dev.steampad.config.GlobalConfig.KeyboardStickMode.POINTER) {
            if (drive) stickPointerRight(sx, sy, p);
            return;
        }

        boolean braking = mag < prevMag2 - BRAKE_DELTA;
        prevMag2 = mag;

        if (!rightPushing && mag >= TAP_MAG_THRESHOLD) {
            rightPushing = true;
            rightPushStartMs = System.currentTimeMillis();
            rightPushStartRow2 = row2;
            rightPushStartCol2 = col2;
            rightPushDirX = sx;
            rightPushDirY = sy;
            rightPushStartX = floatX2;
            rightPushStartY = floatY2;
        } else if (rightPushing && mag < TAP_RELEASE_THRESHOLD) {
            rightPushing = false;
            if (System.currentTimeMillis() - rightPushStartMs < TAP_MAX_MS
                    && row2 == rightPushStartRow2 && col2 == rightPushStartCol2
                    && isTapTravel(p, floatX2 - rightPushStartX, floatY2 - rightPushStartY)) {
                stepSelectionOnce(rightPushDirX, rightPushDirY, true);
                return;   // tap resolved — skip this frame's continuous drift entirely
            }
        }

        if (!drive) {
            tickScale(true);
            return;
        }

        if (mag > 0.001) {
            double speed = floatSpeed(mag, braking) * tickScale(true);
            floatX2 = clamp(floatX2 + (sx / mag) * speed, p.gridLeft(), p.gridRight());
            floatY2 = clamp(floatY2 + (sy / mag) * speed, p.gridTop(), p.gridBottom());
        } else {
            tickScale(true);
        }

        KeyboardGeometry.KeyRect nearest = p.nearest(floatX2, floatY2);
        if (nearest != null) {
            if (nearest.row() != row2 || nearest.col() != col2) {
                row2 = nearest.row();
                col2 = nearest.col();
            }
            double pull = mag <= SETTLE_MAG ? PULL_SETTLE : (braking ? PULL_BRAKE : PULL_MOVING);
            floatX2 += (nearest.centerX() - floatX2) * pull;
            floatY2 += (nearest.centerY() - floatY2) * pull;
        }
    }

    /** POINTER (absolute) mode for the right pointer: deflection maps to the RIGHT half of the grid. */
    private static void stickPointerRight(float sx, float sy, KeyboardGeometry.Panel p) {
        double mag = Math.min(1.0, Math.sqrt(sx * sx + sy * sy));
        if (mag < 0.15) return;   // released/near-center: keep the last selection

        double halfLeft = (p.gridLeft() + p.gridRight()) / 2.0;
        double cx = (halfLeft + p.gridRight()) / 2.0;
        double cy = (p.gridTop() + p.gridBottom()) / 2.0;
        double targetX = clamp(cx + sx * (p.gridRight() - halfLeft) / 2.0 * 1.12, halfLeft, p.gridRight());
        double targetY = clamp(cy + sy * (p.gridBottom() - p.gridTop()) / 2.0 * 1.12, p.gridTop(), p.gridBottom());

        floatX2 += (targetX - floatX2) * 0.6;
        floatY2 += (targetY - floatY2) * 0.6;

        KeyboardGeometry.KeyRect nearest = p.nearest(floatX2, floatY2);
        if (nearest != null && (nearest.row() != row2 || nearest.col() != col2)) {
            row2 = nearest.row();
            col2 = nearest.col();
        }
    }

    /** (Re)syncs the right pointer: onto its remembered key if it has one, else onto a comfortable
     *  home on the right side of the board (~72% width, middle row — the "J-ish" position). */
    private static void syncFloatRight(KeyboardGeometry.Panel p) {
        if (p.keys().isEmpty()) return;
        KeyboardGeometry.KeyRect r = (row2 >= 0) ? p.rectAt(row2, col2) : null;
        if (r == null) {
            double tx = p.gridLeft() + (p.gridRight() - p.gridLeft()) * 0.72;
            double ty = (p.gridTop() + p.gridBottom()) / 2.0;
            r = p.nearest(tx, ty);
        }
        if (r == null) return;
        floatX2 = r.centerX();
        floatY2 = r.centerY();
        row2 = r.row();
        col2 = r.col();
        floatValid2 = true;
        prevMag2 = 0.0;
    }

    /**
     * POINTER mode (Steam Big Picture style): the stick deflection points DIRECTLY at a spot on the
     * keyboard — full up-left = top-left key, center = middle row — with a light ease so the point
     * glides instead of teleporting. Releasing the stick keeps the LAST key (no spring-back to
     * center), so the rhythm is "flick toward the letter, release, press A". Instant to learn,
     * extremely fast, and precision comes from the absolute mapping instead of speed tuning.
     */
    private static void stickPointer(float sx, float sy, KeyboardGeometry.Panel p) {
        double mag = Math.min(1.0, Math.sqrt(sx * sx + sy * sy));
        if (mag < 0.15) return;   // released/near-center: keep the last selection, do not recenter

        // Dual-pointer mode splits the board like Steam's own dual-trackpad typing: the LEFT stick's
        // absolute range is the LEFT half (the right stick covers the right half — stickPointerRight).
        double right = isDualStick() ? (p.gridLeft() + p.gridRight()) / 2.0 : p.gridRight();
        double cx = (p.gridLeft() + right) / 2.0;
        double cy = (p.gridTop() + p.gridBottom()) / 2.0;
        // Slight overscan (1.12) so the corner keys are reachable without pinning the stick perfectly.
        double targetX = cx + sx * (right - p.gridLeft()) / 2.0 * 1.12;
        double targetY = cy + sy * (p.gridBottom() - p.gridTop()) / 2.0 * 1.12;
        targetX = clamp(targetX, p.gridLeft(), right);
        targetY = clamp(targetY, p.gridTop(), p.gridBottom());

        // Ease hard toward the pointed spot (60%/tick) — responsive but not teleporting.
        floatX += (targetX - floatX) * 0.6;
        floatY += (targetY - floatY) * 0.6;

        KeyboardGeometry.KeyRect nearest = p.nearest(floatX, floatY);
        if (nearest != null && (nearest.row() != row || nearest.col() != col)) {
            row = nearest.row();
            col = nearest.col();
        }
    }

    /**
     * Locks a pointer onto whatever key it is aiming at, right now, with no easing left over — the
     * "you let go, so this is your letter" commit. Called on the release edge (see
     * {@link StickSettleGate#consumeJustSettled()}), before the spring-back has a chance to be read.
     */
    private static void commitToNearest(KeyboardGeometry.Panel p, boolean rightPointer) {
        KeyboardGeometry.KeyRect n = p.nearest(rightPointer ? floatX2 : floatX,
                                               rightPointer ? floatY2 : floatY);
        if (n == null) return;
        if (rightPointer) {
            row2 = n.row();
            col2 = n.col();
            floatX2 = n.centerX();
            floatY2 = n.centerY();
        } else {
            row = n.row();
            col = n.col();
            floatX = n.centerX();
            floatY = n.centerY();
        }
    }

    private static void syncFloatToSelection(KeyboardGeometry.Panel p) {
        KeyboardGeometry.KeyRect r = p.rectAt(row, col);
        if (r == null && !p.keys().isEmpty()) r = p.keys().get(0);
        if (r == null) return;
        floatX = r.centerX();
        floatY = r.centerY();
        floatValid = true;
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static void clampSelection() {
        var g = grid();
        if (g.isEmpty()) { row = 0; col = 0; return; }
        if (row < 0) row = 0;
        if (row >= g.size()) row = g.size() - 1;
        int rs = g.get(row).size();
        if (col < 0) col = rs - 1;
        if (col >= rs) col = 0;
    }

    // ---- Actions -------------------------------------------------------------------------

    /**
     * A: presses whichever pointer was used most recently, so dual-stick mode never has a "conflict"
     * where A always confirms the left pointer's key regardless of which stick you just moved
     * (feedback: "que funcione en ambos sticks la tecla A... que respete cuando se seleccione una
     * letra"). D-pad moves count as left-slot activity too (see {@link #move}), so using the D-pad
     * after the right stick correctly hands A back to row/col.
     */
    public static void pressSelected() {
        if (isDualStick() && row2 >= 0 && lastRightMoveMs > lastLeftMoveMs) {
            pressAt(row2, col2);
            return;
        }
        clampSelection();
        pressAt(row, col);
    }

    /**
     * LB: presses the LEFT pointer's own key — always, whichever stick moved last.
     *
     * <p>It used to delegate to {@link #pressSelected()}, which picks by recency, so LB typed the
     * RIGHT pointer's letter whenever the right stick had been touched more recently ("actualmente LB
     * también puede seleccionar una letra de RB si está activo, esto solo debería poder hacerlo el
     * botón A"). Each bumper now owns its own pointer, and only A arbitrates between them.
     *
     * <p>Works whether or not the orb is currently drawn: the selection auto-hides after a moment of
     * stick idleness, but {@code row}/{@code col} still remember where that pointer is ("aunque esté
     * oculto si presiono RB o LB solamente afecte al stick"). Pressing also reveals the orb briefly,
     * so it is visible what was typed.
     */
    public static void pressLeftPointer() {
        clampSelection();
        revealLeft();
        pressAt(row, col);
    }

    /**
     * RB: presses the RIGHT pointer's own key. If that pointer has never been used this session it is
     * first placed at its home position rather than doing nothing, so RB always types something.
     */
    public static void pressRightPointer() {
        if (row2 < 0) {
            Screen s = mc().screen;
            if (s == null) return;
            KeyboardGeometry.Panel p = KeyboardGeometry.layout(
                    s.width, s.height, s instanceof net.minecraft.client.gui.screens.ChatScreen,
                    positionTop, grid());
            if (p.keys().isEmpty()) return;
            syncFloatRight(p);
            if (row2 < 0) return;
        }
        revealRight();
        pressAt(row2, col2);
    }

    /** Makes the left orb/brackets visible and hands the "most recent" slot to it. */
    private static void revealLeft() {
        leftEngaged = true;
        lastLeftMoveMs = System.currentTimeMillis();
    }

    /** Makes the right orb/brackets visible and hands the "most recent" slot to it. */
    private static void revealRight() {
        rightEngaged = true;
        lastRightMoveMs = System.currentTimeMillis();
    }

    /** Presses the key at a grid position — shared by A (selection), LB and RB (pointers). Also
     *  stamps the press-sink visual ({@link #isKeySunken}). */
    private static void pressAt(int r, int c) {
        var g = grid();
        if (r < 0 || r >= g.size()) return;
        var rowKeys = g.get(r);
        if (c < 0 || c >= rowKeys.size()) return;
        KeyboardLayout.Key k = rowKeys.get(c);

        lastPressRow = r;
        lastPressCol = c;
        lastPressAtMs = System.currentTimeMillis();

        switch (k.type()) {
            case CHAR -> typeChar(k.codepoint());
            case SPACE -> typeChar(' ');
            case BACKSPACE -> backspace();
            case ENTER -> enter();
            case SHIFT -> { shift = !shift; sound(); }
            // The GLFW key code lives in codepoint() for this type (see KeyboardLayout.Key's doc).
            // Same pipeline as Backspace/Enter/Tab, so the arrows reach whatever the screen routes
            // them to — caret movement in a field, selection movement in a list.
            case ARROW -> { pressKey(k.codepoint()); sound(); }
            case SELECT_ALL -> { ClipboardActions.selectAll(mc().screen); sound(); }
            case COPY -> { ClipboardActions.copy(mc().screen); sound(); }
            case PASTE -> { ClipboardActions.paste(mc().screen); sound(); }
            case LAYER -> {
                layer = (layer == Layer.LETTERS) ? Layer.SYMBOLS : Layer.LETTERS;
                clampSelection();
                floatValid = false;
                // The grid changed under both pointers — the right one must re-resolve too.
                floatValid2 = false;
                leftGate.reset();
                rightGate.reset();
                row2 = -1;
                col2 = -1;
                sound();
            }
        }
    }

    public static void space() { typeChar(' '); }
    public static void backspace() { pressKey(GLFW.GLFW_KEY_BACKSPACE); sound(); }
    public static void enter() { pressKey(GLFW.GLFW_KEY_ENTER); sound(); }
    public static void caretLeft() { pressKey(GLFW.GLFW_KEY_LEFT); }
    public static void caretRight() { pressKey(GLFW.GLFW_KEY_RIGHT); }
    public static void shiftToggle() { shift = !shift; sound(); }

    /**
     * Sends TAB — command completion while typing in chat.
     *
     * <p>Goes through the same real key pipeline as every other key here, which is what makes it
     * work: {@code ChatScreen.keyPressed} hands the event to its {@code CommandSuggestions} FIRST
     * (confirmed in the bytecode of both target versions), and that is what completes {@code /tp},
     * a player name, an item id and so on. Bound to the right stick click — feedback: "cuando estoy
     * escribiendo en el chat un comando que el click del stick derecho sea el TAB".
     */
    public static void tabComplete() { pressKey(GLFW.GLFW_KEY_TAB); sound(); }

    /** True while the screen under the keyboard is the chat — where {@link #tabComplete()} means
     *  something. Used by the dispatcher and by the footer hint row. */
    public static boolean isChatScreen() {
        return mc().screen instanceof net.minecraft.client.gui.screens.ChatScreen;
    }

    /** Types a whole string through the normal per-character path (used by the paste key's universal
     *  fallback — see {@link ClipboardActions#paste}). Shift stays off: the text is already cased. */
    static void typeText(String text) {
        if (text == null || text.isEmpty()) return;
        boolean savedShift = shift;
        shift = false;
        try {
            text.codePoints().forEach(VirtualKeyboard::typeChar);
        } finally {
            shift = savedShift;
        }
    }

    private static void typeChar(int codepoint) {
        int cp = codepoint;
        if (shift && Character.isLetter(cp)) cp = Character.toUpperCase(cp);
        Screen s = mc().screen;
        if (s == null) return;
        // Real Keyboard.onChar (widened via the access widener) instead of calling Screen.charTyped
        // directly — mods built on Architectury API (Roughly Enough Items and others) intercept typing
        // via mixins on the CALL SITE of Screen.charTyped INSIDE Keyboard.onChar, so a direct call
        // from here never reached them (feedback: "poder escribir en su busqueda"). onChar internally
        // does exactly what Screen.charTyped(input) did before, so vanilla/well-behaved-mod screens —
        // whose focused field vanilla's own getFocused() chain already resolves — are unaffected.
        boolean routedByScreen = isTextWidget(deepestFocused(s)) || recipeBookSearchField(s) != null;
        InputEventCompat.fireCharTyped(mc(), cp, 0);
        // Screens whose text field is focused internally but not via Screen.getFocused() (the Xaero's
        // case) still need direct-to-field delivery — onChar's internal Screen.charTyped call can't
        // find that field either, only our own duck-typed/recursive resolution can.
        if (!routedByScreen) {
            GuiEventListener field = findTextField(s);
            if (field != null) InputEventCompat.charTyped(field, cp, 0);
        }
        // Shift is one-shot for letters (phone-style); release after a letter.
        if (shift && Character.isLetter(codepoint)) shift = false;
        sound();
    }

    private static void pressKey(int glfwKey) {
        Screen s = mc().screen;
        if (s == null) return;
        int scancode = 0;
        try { int sc = GLFW.glfwGetKeyScancode(glfwKey); if (sc > 0) scancode = sc; } catch (Throwable ignored) {}
        // Same real-pipeline routing as typeChar — see its doc for why. The screen may legitimately
        // consume some keys itself (Enter = Done, etc.) — that's the desired order either way.
        boolean routedByScreen = isTextWidget(deepestFocused(s)) || recipeBookSearchField(s) != null;
        InputEventCompat.fireKeyPress(mc(), GLFW.GLFW_PRESS, glfwKey, scancode, 0);
        if (!routedByScreen && mc().screen == s) {   // a key can close the screen — re-check
            GuiEventListener field = findTextField(s);
            if (field != null) InputEventCompat.keyPressed(field, glfwKey, scancode, 0);
        }
    }

    private static void sound() {
        if (ConfigManager.getGlobal().virtualKeyboardSounds) UiSoundService.playNavigate();
    }

    // ---- Focus / preview -----------------------------------------------------------------

    /**
     * True for a pure text-entry screen that exposes NO focusable text widget of its own — sign and
     * book editors, which draw and edit their text themselves. There is nothing for a click or an A
     * press to land on there, so those screens need A to open the keyboard directly; chat is excluded
     * because it does have a real input widget and its existing click behaviour must stay untouched.
     */
    public static boolean isFieldlessTextEntry(Screen screen) {
        return isTextEntryScreen(screen) && findTextField(screen) == null;
    }

    /** True if the current screen has a focused text field (or is a known text-entry screen). */
    public static boolean hasTextEntry(Screen screen) {
        if (findTextField(screen) != null) return true;
        // Screens that handle charTyped at the screen level (no focused text-field widget). Same
        // instanceof-not-class-name rule as isTextEntryScreen — see its comment for the disassembly.
        return isTextEntryScreen(screen);
    }

    /** The text currently in the focused field (for the preview strip), or null. */
    public static String previewText(Screen screen) {
        GuiEventListener f = findTextField(screen);
        if (f instanceof EditBox tf) return tf.getValue();
        return null;
    }

    /**
     * Resolves the screen's active text field, covering mod screens that vanilla-focus detection
     * misses (e.g. Xaero's waypoint-name field, which is focused internally but not wired into
     * {@code Screen.getFocused()} — the case where Controlify's keyboard never shows up):
     * <ol>
     *   <li>the vanilla focus chain, if it ends in a text widget (vanilla + well-behaved mods),</li>
     *   <li>duck-typing on the focused element's class name ("TextField"/"EditBox"/"TextBox"/
     *       "TextInput") for mods with fully custom text widgets,</li>
     *   <li>a recursive walk of the widget tree for a text widget whose own {@code isFocused()} is
     *       true — mods often focus the widget directly without telling the screen.</li>
     * </ol>
     * Returns null when the screen has no active text entry.
     */
    public static GuiEventListener findTextField(Screen screen) {
        if (screen == null) return null;
        GuiEventListener f = deepestFocused(screen);
        if (isTextWidget(f)) return f;
        return findFocusedTextWidget(screen, 0);
    }

    /**
     * Text-widget test: vanilla classes first, then class-name duck-typing for mod widgets.
     *
     * <p>{@code getClass().getSimpleName()} can throw {@code IncompatibleClassChangeError} (an
     * {@code Error}, not an {@code Exception}) on a class whose InnerClasses bytecode attribute was
     * mangled by some mod's Mixin transform — this crashed the whole game once (a nested class
     * declared inside one of our own Mixins had exactly this problem, fixed at the source in
     * {@code GamepadOptionsButton}, but any OTHER mod's mixin-generated widget class could hit the
     * same JVM quirk). Reflection on an arbitrary, unknown widget class from a 100+-mod pack must
     * never be allowed to bring down the tick loop — catching {@code Throwable} here is deliberate.
     */
    private static boolean isTextWidget(GuiEventListener e) {
        if (e == null) return false;
        if (e instanceof EditBox || e instanceof MultiLineEditBox) return true;
        try {
            String n = e.getClass().getSimpleName();
            // "SearchField" covers Roughly Enough Items' OverlaySearchField (feedback: "poder escribir
            // en su busqueda") — a fully custom widget (extends REI's own internal TextFieldWidget
            // base class, not vanilla's) that none of the other patterns would catch.
            return n.contains("TextField") || n.contains("EditBox") || n.contains("TextBox")
                    || n.contains("TextInput") || n.contains("SearchField");
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The recipe book's search field on this screen, when it is focused; null otherwise.
     *
     * <p>Needs its own step because the book is a dead end for the generic walk below: it is a child
     * of the screen, but {@code RecipeBookComponent} is a {@code GuiEventListener} and NOT a
     * {@code ContainerEventHandler} (javap-confirmed on both target versions), so it exposes no
     * {@code children()} to descend into and its {@code searchBox} is private — hence
     * {@link dev.steampad.mixin.RecipeBookSearchAccessor}.
     *
     * <p>Also answers "does the screen itself already route typing here?" for {@link #typeChar} and
     * {@link #pressKey}: it does — {@code CraftingScreen.charTyped/keyPressed} (1.21.1) and
     * {@code AbstractRecipeBookScreen.charTyped/keyPressed} (1.21.10) both hand the event to the book
     * before anything else, javap-confirmed. Without that check those two would ALSO deliver straight
     * to the field and every keystroke would land twice.
     */
    private static EditBox recipeBookSearchField(Screen screen) {
        if (screen == null) return null;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.screens.recipebook.RecipeBookComponent) {
                EditBox box = ((dev.steampad.mixin.RecipeBookSearchAccessor) child).steampad$searchBox();
                if (box != null && box.isFocused()) return box;
            }
        }
        return null;
    }

    /** Depth-first search of the widget tree for a text widget that reports itself focused. */
    private static GuiEventListener findFocusedTextWidget(ContainerEventHandler parent, int depth) {
        if (depth > 6) return null;   // cycle/absurd-nesting guard
        if (parent instanceof Screen s) {
            EditBox book = recipeBookSearchField(s);
            if (book != null) return book;
        }
        for (GuiEventListener child : parent.children()) {
            if (isTextWidget(child) && child.isFocused()) return child;
            if (child instanceof ContainerEventHandler pe) {
                GuiEventListener found = findFocusedTextWidget(pe, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static GuiEventListener deepestFocused(Screen screen) {
        GuiEventListener f = screen.getFocused();
        int guard = 0;
        while (f instanceof ContainerEventHandler pe && pe.getFocused() != null && guard++ < 16) {
            f = pe.getFocused();
        }
        return f;
    }

    private static Minecraft mc() { return Minecraft.getInstance(); }
}
