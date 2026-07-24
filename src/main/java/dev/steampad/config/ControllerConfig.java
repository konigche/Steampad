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
    public boolean autoJump = true;
    public boolean noFlyDrifting = false;
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
    public boolean zoomDpadAdjust = true;      // D-pad ↑/↓ adjusts the level while zoomed
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

    // Enums
    public enum SneakMode { HOLD, TOGGLE }
    public enum SprintMode { HOLD, TOGGLE }
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
