package dev.steampad.config;

/** Per-controller settings. Serialized to controllers/{handle}.json */
public final class ControllerConfig {

    // Sensitivity
    public float horizontalSensitivity = 1.0f;
    public float verticalSensitivity = 1.0f;
    // Feedback (2026-07-11): the whole 0.1-5.0 dial felt too fast — the value that felt right sat at
    // 0.65. Rather than change the stored default (which would silently shift every saved config),
    // both consumers (CameraController, InputBindingManager) multiply the raw slider value by this
    // constant, so "1.0 on the dial" now delivers what "0.65" used to — same relative rescale at
    // every point on the slider, not just the default.
    public static final float SENSITIVITY_REBASE = 0.65f;
    public float virtualMouseSensitivity = 1.0f;   // 0.2–3.0; applied once in VirtualMouseController
    public boolean invertLookY = false;
    public boolean reduceAimingSensitivity = false;
    // AAA camera response (see CameraController): power-curve exponent over the stick MAGNITUDE
    // (2.0–2.5 is the console-shooter standard; 1.0 = linear) and full-tilt turn acceleration
    // (hold the stick at the edge briefly → yaw ramps up to a turbo for fast 180s, like COD/Halo).
    public float lookCurve = 2.2f;
    public boolean lookTurnBoost = true;
    // Aim assist for aimed projectiles (bow/crossbow/trident): camera friction over targets plus
    // gentle magnetism, console-shooter style. Strength 0–1 scales both.
    public boolean aimAssistEnabled = true;
    public float aimAssistStrength = 1.0f;

    // Controls
    // Bedrock-style hold-to-swing: while ATTACK is held, re-swing automatically each time the weapon
    // cooldown refills (blocks keep vanilla continuous mining). Console-native combat feel.
    public boolean attackAutoRepeat = true;
    public SneakMode sneakMode = SneakMode.HOLD;
    public SprintMode sprintMode = SprintMode.HOLD;
    // Item Radial (LB/RB) — see dev.steampad.itemradial. OFF: LB/RB keep today's classic hotbar-scroll
    // tap behaviour, untouched. HOLD_TO_OPEN: a quick tap still scrolls one slot; holding past the
    // threshold opens the wheel instead — best of both, no toggle needed to switch back and forth.
    // ALWAYS_RADIAL: every press opens the wheel directly, classic scroll is fully replaced.
    public HotbarRadialMode hotbarRadialMode = HotbarRadialMode.OFF;
    public boolean autoJump = true;
    public boolean noFlyDrifting = false;
    // Both of the next two apply ONLY while mounted in a vehicle/mount (feedback: "cuando monto un
    // coche o helicoptero... siento muy sensible el stick izquierdo, como que esta temblando", vs a
    // rock-solid keyboard W). On foot, vanilla's own movement acceleration/friction already absorbs
    // small stick deviations, but a modded vehicle typically applies the analog value straight to its
    // own turn rate with no inertia of its own, so both hand tremor AND a thumb resting a few degrees
    // off-axis become visible steering wobble.
    //
    // TIME filter (halflife in seconds, same semantics as util.SmoothValue, already used for the
    // camera): absorbs genuine tremor — deviations that come and go. 0 = off.
    public float mountedSteeringSmoothing = 0.08f;
    // AXIS dead band on the LATERAL axis only (0 = off). This is the one that fixes "si empujo el stick
    // izq al frente siempre se va de lado... nunca va solo adelante como cuando presiono solo W".
    // leftStickDeadzone above is CIRCULAR: it only zeroes the stick near its CENTRE and above that
    // preserves the vector's ANGLE exactly, so there is no dead band around the forward AXIS. A
    // keyboard's lateral impulse is only ever exactly -1, 0 or +1 (hence W drives dead straight), but
    // an analog stick essentially never reads exactly zero, and a vehicle turning proportionally to
    // that value integrates the residue into a permanent slow curve. Confirmed in the user's own debug
    // dump while mounted: movement input (-0.01, 1.00) — a lateral 100x smaller than forward and still
    // enough to never drive straight. Attenuating it (the power curve this replaces, v0.92.0) was not
    // enough: inside the band it has to be EXACTLY zero. Above the band the response stays linear, so
    // turning while driving forward keeps working exactly like holding W+D on the keyboard.
    // Forward/back deliberately has no such band: it is the throttle and must stay proportional.
    public float mountedSteeringDeadzone = 0.25f;
    public boolean lceStyleControls = false;

    // Accessibility
    public boolean showIngameButtonGuide = true;
    public ButtonGuidePosition ingameButtonGuidePosition = ButtonGuidePosition.BOTTOM;
    // Moved to GlobalConfig.ingameButtonGuideDetail (feedback: it's a HUD display preference, not
    // something that should vary per-controller) — see GameplayHudOverlay's LEFT/RIGHT tables.
    public boolean showScreenButtonGuide = true;
    // (showOnScreenKeyboard / onScreenKeyboardHeight / controllerTheme were removed: superseded by
    // the GLOBAL virtual-keyboard settings and by automatic brand detection for button textures.
    // Gson silently ignores the old keys in existing config files.)

    // Deadzones
    public float leftStickDeadzone = 0.15f;
    public float rightStickDeadzone = 0.15f;
    public float buttonActivationThreshold = 0.5f;

    // Vibration
    public boolean allowVibration = true;
    public boolean hdHaptics = false;
    public float vibrationMaster = 1.0f;
    public float vibrationPlayer = 1.0f;
    public float vibrationWorld = 1.0f;
    public float vibrationInteraction = 1.0f;
    public float vibrationGui = 0.5f;
    public float vibrationGlobalEvent = 1.0f;
    public float vibrationMisc = 0.5f;

    // Gyro
    public boolean gyroEnabled = false;   // off by default; opt-in motion aiming
    public float gyroSensitivity = 1.0f;
    public GyroMode gyroBehaviour = GyroMode.RELATIVE;
    public YawMode yawMode = YawMode.YAW_ONLY;
    public boolean gyroInvertX = false;
    public boolean gyroInvertY = false;
    public GyroRequireButton gyroRequireButton = GyroRequireButton.OFF;
    public boolean flickStick = false;

    // Zoom (BetterZoom-style, gamepad-native — see input/ZoomController). The ZOOM bind ships with
    // no default button; these only take effect once the user assigns one in BOTONES.
    public boolean zoomHoldMode = true;        // true = hold to zoom, false = press to toggle
    public float zoomFov = 15f;                // zoom level in FOV degrees (1..60), D-pad adjustable
    public float zoomStep = 5f;                // degrees per D-pad adjust step (1..20)
    public boolean zoomSmooth = true;          // eased zoom transitions
    public float zoomSmoothing = 0.15f;        // easing factor 0.05..0.30 (higher = snappier)
    public boolean zoomAutoSensitivity = true; // camera speed follows the zoom factor
    public float zoomSensitivity = 0.3f;       // fixed camera multiplier while zoomed (auto off)
    public boolean zoomDpadAdjust = true;      // the Zoom in/out binds adjust the level while zoomed
    // Ramp the level continuously while the in/out button is held, instead of one discrete step per
    // press. OFF keeps the original stepped feel, which is what every existing config expects.
    public boolean zoomContinuous = false;
    public boolean zoomResetOnRelease = false; // true = D-pad adjustments discard on zoom end (back to
                                               // the configured level); false = the last level persists
    public boolean zoomMarkerEnabled = true;   // A while zoomed drops a temporary particle beacon
    public float zoomMarkerSeconds = 6f;       // how long the beacon lasts (2..15 s)
    public boolean zoomMarkerShareChat = false;// also send the marked coords to chat (visible to others)
    public ZoomMarkerStyle zoomMarkerStyle = ZoomMarkerStyle.COLUMN; // visual shape of the beacon
    public ZoomMarkerColor zoomMarkerColor = ZoomMarkerColor.CYAN;  // beacon particle color
    public boolean zoomDisableBobbing = true;  // suppress view bobbing while zoomed
    // Temporarily raises the client's OWN render distance (chunks) while actively zooming, reverted the
    // instant the zoom ends — lets a scoped-in view actually SEE terrain beyond the player's normal
    // render/simulation distance without paying that cost all the time. 0 = off/default (feedback:
    // "se ve limitado por el renderizado de chunks simulados... ¿se puede hackear esto?").
    public int zoomRenderDistanceBoost = 0;   // 0..16 extra chunks added to the current setting
    // "Cinematic" letterbox bars that close in from top/bottom while zooming, like a movie aspect
    // ratio — purely a HUD overlay (2 black rectangles), off by default. Height is a % of screen
    // height at FULL zoom engagement; the close/open animation itself is a fixed-rate ease (see
    // ZoomController.renderCinematicBars), independent of how deep the configured zoom FOV is.
    public boolean zoomCinematicBars = false;
    public float zoomCinematicBarsHeightPct = 12f;   // 5..20% of screen height per bar
    // Shooter-style zoom: eases to first person while zooming, eases back to third on release — the
    // same 220ms blend PerspectiveTransition already uses for emotes and the manual perspective
    // cycle. Restricted to back-view (see ZoomController) — front-view/mirror mode keeps its own
    // identity, no switch there. Shipped off, then turned ON by default at the requester's own call
    // once they'd seen it: aiming down a zoom from behind the character is the case it exists for.
    public boolean zoomFirstPerson = true;

    // Advanced
    public boolean mixedInput = false;
    public int screenRepeatNavigationDelay = 200;

    // Physical button bindings for the fallback (GLFW/SDL3) path: GamepadBind name -> button id
    // (A,B,X,Y,LB,RB,LT,RT,L3,R3,DUP,DDOWN,DLEFT,DRIGHT,START,BACK). Absent key = built-in default;
    // empty string value = explicitly unbound.
    public java.util.Map<String, String> buttonBindings = new java.util.HashMap<>();

    // Optional chord (modifier) button per GamepadBind: GamepadBind name -> button id. The action
    // only fires while this button is also held.
    public java.util.Map<String, String> chordBindings = new java.util.HashMap<>();

    // Extra bindings to arbitrary keybinds (vanilla + modded): button id -> keybind translation key.
    // Lets any installed mod's keybind be triggered from a controller button (dispatched as a tap).
    public java.util.Map<String, String> extraBinds = new java.util.HashMap<>();

    // Optional chord (modifier) per extra bind: keybind translation key -> modifier button id. The
    // extra bind only fires while this modifier is also held (mirrors chordBindings for BIND actions).
    public java.util.Map<String, String> extraChords = new java.util.HashMap<>();

    // Per-CONTEXT ("layer") button bindings, so the same physical button can mean different things
    // depending on where you are — the request: "cuando estoy en Mochila podría poner A para ordenar
    // mochila y sigue funcionando sin interrumpir el A de saltar de gameplay". Outer key = layer name
    // (SteamSlotDispatcher.Context: MENU / INVENTORY / MOUNTED), inner map = same shape as extraBinds
    // (button id -> keybind translation key) and extraChords. GAMEPLAY is deliberately absent: it
    // already lives in extraBinds/extraChords above and stays there, exactly like the Steam Input slot
    // layers left their own Gameplay layer where it was. Everything reads these through
    // dev.steampad.input.ContextualBinds, which hides the split.
    // Buttons whose action fires on a LONG PRESS instead of on the press edge, so one button can
    // carry two actions: a short tap does what it always did, a long hold does this one. Empty by
    // default. See dev.steampad.input.LongPressGate for where a long press is refused and why.
    public java.util.List<String> holdButtons = new java.util.ArrayList<>();

    // Enums
    public enum SneakMode { HOLD, TOGGLE }
    public enum SprintMode { HOLD, TOGGLE }
    public enum HotbarRadialMode { OFF, HOLD_TO_OPEN, ALWAYS_RADIAL }
    public enum ButtonGuidePosition { TOP, BOTTOM }
    // Zoom marker beacon shapes — see input/ZoomController#tickMarker.
    public enum ZoomMarkerStyle { COLUMN, SHORT_COLUMN, RING, BURST }
    // Zoom marker beacon color (packed 0xRRGGBB, fed straight into DustParticleEffect).
    public enum ZoomMarkerColor {
        CYAN(0x00E5FF), WHITE(0xFFFFFF), GOLD(0xFFD700), MAGENTA(0xFF3DF0), LIME(0x4CFF4C), RED(0xFF3B30);
        public final int rgb;
        ZoomMarkerColor(int rgb) { this.rgb = rgb; }
    }
    public enum GyroMode { RELATIVE, ABSOLUTE }
    public enum YawMode { YAW_ONLY, ROLL_ONLY, BOTH }
    public enum GyroRequireButton { HOLD, INVERT, TOGGLE, OFF }

    public static ControllerConfig defaults() {
        return new ControllerConfig();
    }
}
