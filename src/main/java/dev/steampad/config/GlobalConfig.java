package dev.steampad.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Serializable POJO for global mod settings. */
public final class GlobalConfig {

    // Natives
    public boolean loadNatives = true;
    public String customNativesPath = "";

    // Steam Input. The Steamworks SDK needs an AppID to talk to a running Steam client: it reads it
    // from the SteamAppId env var (set when launched FROM Steam) or from steam_appid.txt in the working
    // directory. Outside a Steam launch (e.g. Prism/Flatpak) neither exists → init fails ("no appID").
    // We auto-write steam_appid.txt with this id so Steam Input can initialize when Steam is running.
    // 480 = Spacewar, the canonical dev AppID that connects to any running Steam without owning a game.
    public int steamAppId = 480;
    public boolean autoWriteAppIdFile = true;
    // Auto-deploys our Steam Input action manifest (game_actions_<appid>.vdf) straight into Steam's
    // own controller_config folder — the location Valve documents for developer-supplied action
    // manifests, picked up with no "import" step in Steam's UI (see SteamControllerConfigDeployer).
    public boolean autoWriteControllerConfig = true;

    /** EXPERIMENTAL, off by default (B076): deploy the In-Game-Actions manifest
     *  (game_actions_<appid>.vdf) into Steam's controller_config. Once that file exists for a title,
     *  Steam treats it as a native Steam Input API game — in Game Mode it claims the pad and STOPS
     *  emitting the virtual gamepad that SDL3/GLFW read, killing normal detection unless EVERY game
     *  action is bound in Steam's configurator AND the mod's Steam attach works. While off, startup
     *  auto-removes any manifest SteamPad previously deployed (content-fingerprinted — only ours). */
    public boolean deployIgaManifest = false;

    // WHEN to attach to Steam at all. Attaching (SteamAPI.init with our AppID) makes Steam believe a
    // game is running and SEIZE the controllers to apply that game's controller layout — killing raw
    // SDL3 input for every managed pad (validated in hardware, B038/D033). On desktop that is pure
    // loss: SDL3/HIDAPI already reads everything raw, including back paddles (P1..P4). In Game Mode
    // (non-Steam shortcut) the attach FAILS anyway (the shortcut occupies the running-app slot,
    // validated B039) — and it isn't needed: paddles reach the mod via the F13..F22 keyboard route
    // (Steam layout maps paddle → F-key → slot; see SteamSlotDispatcher). Attaching only makes sense
    // when MC is launched FROM Steam as a real title with a full Steam layout for our VDF actions.
    //   NEVER  — never attach (default): SDL3/GLFW raw input + slots via F13..F22 keys
    //   AUTO   — attach only under gamescope/Game Mode (fails benignly under non-Steam shortcuts)
    //   ALWAYS — attach whenever Steam is running (requires a full Steam layout to play)
    public SteamAttachMode steamAttachMode = SteamAttachMode.NEVER;

    public enum SteamAttachMode { AUTO, ALWAYS, NEVER }

    // Fallback backends (used when Steam Input is unavailable). Both degrade gracefully.
    public boolean useSdl3Fallback = true;   // try SDL3 (via JNA) before GLFW for richer device support
    public boolean useGlfwFallback = true;   // GLFW joystick detection — the always-available baseline

    // Absolute path to libSDL3, for installs the automatic search can't predict. Blank = search
    // automatically (Steam's own copy, then system locations — see Sdl3Library). Only needed when the
    // log says libSDL3 wasn't found; SDL3 is what provides rumble, back paddles and the wide device
    // database, so GLFW-only is a real downgrade rather than an equivalent path.
    public String sdl3LibraryPath = "";

    // Server Options
    public BlockReachAround blockReachAround = BlockReachAround.OFF;
    public boolean allowServerVibration = false;
    public boolean keyboardLikeMovement = false;
    public List<String> keyboardMovementWhitelist = new ArrayList<>();

    // Virtual keyboard (controller-driven on-screen keyboard). Appears when a text field is focused
    // (AUTO) and is driven entirely by the pad. Height is a fraction of the screen (~1/5, non-invasive).
    public boolean virtualKeyboardEnabled = true;
    public boolean virtualKeyboardAutoShow = true;
    public float virtualKeyboardHeightPct = 0.30f;   // 0.20–0.40; default sits mid-slider
    public boolean virtualKeyboardSounds = true;
    // Color preset for the keyboard's pixel-art look (see PixelTheme). Gson stores the enum name;
    // unknown/missing values fall back to VANILLA.
    public PixelTheme virtualKeyboardTheme = PixelTheme.VANILLA;
    // Stick response multiplier for the keyboard's free-floating cursor (see VirtualKeyboard) — 1.0
    // is the tuned default; lower = slower/more controlled, higher = faster.
    public float virtualKeyboardStickSpeed = 1.0f;
    // How the stick drives the keyboard cursor: VELOCITY = the stick moves the point (tuned dual-zone
    // response); POINTER = the deflection points DIRECTLY at a keyboard region (Steam Big Picture
    // style — full up-left = top-left key; releasing keeps the last key). Gson stores the enum name.
    public KeyboardStickMode virtualKeyboardStickMode = KeyboardStickMode.VELOCITY;

    /** Steam Big Picture-style dual-pointer typing (feedback: "que funcionen los dos joystick...
     *  igualito como funciona en el teclado de steam... LB y RB sean los clicks"): both sticks each
     *  drive their own floating pointer over the keys, LB presses the left pointer's key, RB the
     *  right pointer's, A keeps pressing the D-pad/left selection. OFF = classic single-stick
     *  keyboard with LB/RB as caret movement, exactly as before. */
    public boolean virtualKeyboardDualStick = true;

    /** Master switch for the virtual-mouse magnet (feedback: "agrega una opcion al mouse virtual,
     *  en las opciones de mouse virtual del snap, que pueda prenderse o desactivarse"). OFF = the
     *  cursor drifts freely; D-pad cell-hopping keeps working (that's navigation, not the magnet). */
    public boolean virtualMouseSnapEnabled = true;

    public enum KeyboardStickMode { VELOCITY, POINTER }

    // "Splitscreen" window tiling — ported from pcal43/splitscreen (MIT License, Copyright (c) 2023
    // pcal.net). NOT real in-game splitscreen (see CLAUDE.md Restriction 2 and DECISIONS.md): it is a
    // pure OS window position/size helper for setting up local multiplayer across several Minecraft
    // instances on one screen, exactly like the upstream mod. Off by default (mutates the OS window;
    // opt-in only, mirrors the conservative-default pattern used for the rest of the mod's
    // window/input-affecting toggles). Hold Select (BACK) and press RB to cycle through the layouts —
    // see WindowArrangeController / WindowArrangeMode for the layout table and GamepadInputDispatcher
    // for the chord handling.
    public boolean windowArrangeEnabled = false;
    // Pixel gap left between tiled windows so borders don't visually touch (matches upstream's default
    // of 1px; exposed as a slider so users on high-DPI displays can widen it).
    public int windowArrangeGap = 1;
    // Last layout cycled to — persisted so re-enabling (or relaunching with the toggle already on)
    // recalls it instead of always restarting at WINDOWED (feedback: "que recuerde la última posición").
    public dev.steampad.client.window.WindowArrangeMode windowArrangeMode =
            dev.steampad.client.window.WindowArrangeMode.WINDOWED;

    // Gameplay HUD glyph coverage (moved from per-controller ControllerConfig — feedback: it's a
    // display preference, not something that should vary per controller). MINIMAL = the 5 core survival
    // actions only; NORMAL = the original Bedrock-style curated set of 10; FULL = every action that has
    // a button, however obscure. See GameplayHudOverlay's LEFT/RIGHT tables.
    public ButtonGuideDetail ingameButtonGuideDetail = ButtonGuideDetail.NORMAL;

    public enum ButtonGuideDetail { MINIMAL, NORMAL, FULL }

    // Miscellaneous
    public boolean uiSounds = true;
    public boolean notifyLowBattery = true;
    public boolean outOfFocusInput = false;
    public float ingameButtonGuideScale = 1.0f;
    public boolean useEnhancedSteamDeckDriver = false;
    public boolean radialSelectHaptics = true;
    // Default set to the user's own measured floor on real hardware: below 40% nothing is felt at
    // all, so anything higher is just needless extra buzz for no more perceptibility (feedback: "a
    // partir del 40% se empieza a sentir, mas abajo ya no se siente nada... es de lo mas bajo").
    public float radialSelectHapticsIntensity = 0.4f;

    // Third-person over-the-shoulder camera offset — design credited to Leawind's "Third-Person"
    // mod (github.com/Leawind/Third-Person, MIT license); reimplemented natively here as a lateral
    // shift of vanilla's own already-collision-safe third-person camera position (see
    // dev.steampad.input.ThirdPersonCameraController / the Camera mixin), not a line-for-line port of
    // that mod's own halflife-damper/free-look system — see DECISIONS.md for the scope this pass
    // deliberately covers vs. defers. Off by default so nobody's camera changes without opting in.
    public boolean thirdPersonCameraEnabled = false;
    public ThirdPersonCameraSide thirdPersonCameraSide = ThirdPersonCameraSide.RIGHT;
    // Lateral shift in blocks at full offset (LEFT/RIGHT) — clamped by a raycast so the camera never
    // ends up clipping into a wall next to the player. Default 30% (point 7/D120) — the UI's own
    // slider (GlobalSettingsScreen) already displays this value ×100 as a literal percentage.
    public float thirdPersonCameraOffset = 0.30f;
    // Aiming-mode camera profile (credited design, same source as thirdPersonCameraEnabled): while
    // charging a bow/crossbow/trident, the offset eases to a closer, more centered position — mirrors
    // the reference mod's separate "aiming" offset profile. Off = same offset always, aim or not.
    public boolean thirdPersonAimingProfile = true;
    public float thirdPersonAimingOffset = 0.3f;
    // Auto-switch to third person on boarding anything rideable (horse, boat, minecart, a mod's own
    // mount/vehicle) and back to first person on dismount - independent of free-look, so it also
    // applies with just the plain over-the-shoulder offset above. On by default (the feature was
    // requested WITH its own opt-out, not as an opt-in), still fully covered by the master switch
    // above like everything else in this section.
    public boolean thirdPersonAutoSwitchOnMount = true;
    // Mod compat, not a SteamPad camera feature: the Gliders mod's own "Glider Perspective" option
    // (third person while gliding) is re-created as OFF on every launch because that mod never saves
    // it anywhere — proven against its jar, see GlidersCompat. With this on, SteamPad flips it back on
    // once per world entry. OFF by default: forcing another mod's option is opt-in.
    public boolean glidersForcePerspective = false;
    // Downward tilt (degrees) the chase camera settles at while mounted, once the right stick is back
    // at rest — the "driving camera" angle. Positive = looking DOWN, which places the camera HIGH and
    // behind the mount (the pose is built as rotateCenter - lookDirection*distance, so tilting the look
    // down lifts the camera up). Feedback that produced it: "esta muy bajo el angulo y no se aprecia
    // mucho" — the previous behaviour copied the RIDER's own pitch, which while riding is roughly
    // level, leaving the camera at the mount's own height staring into its back. 20° reads as a clearly
    // elevated chase view without becoming top-down. Only consulted while mounted and while the player
    // is NOT holding a deliberate free-look push (that gesture owns the pitch outright while it lasts).
    public float thirdPersonMountedCameraPitch = 20.0f;
    // Camera distance (blocks) while mounted, separate from thirdPersonFreeDistance. Requested
    // straight after the angle landed: "solo aleja un poco mas la camara cuando esta montado". A mount
    // is simply bigger than a player — a horse or a boat fills far more of the frame at the on-foot
    // distance — so the driving view wants its own pull-back rather than a compromise value shared with
    // walking around. Default 4.0 vs the on-foot 2.7. Aiming still wins over this (charging a bow
    // pulls the camera in close whether you're mounted or not); so does a real emote.
    public float thirdPersonMountedCameraDistance = 4.0f;

    public enum ThirdPersonCameraSide { LEFT, CENTER, RIGHT }

    // Third-person FREE CAMERA — the rest of Leawind's "Third-Person" feature set (free rotation
    // decoupled from the body, functional crosshair/shooting-like-first-person, quick offset/distance
    // adjustment). Off by default and fully independent of thirdPersonCameraEnabled above: that toggle
    // keeps meaning "simple over-the-shoulder offset only" for anyone who already validated it: turning
    // THIS on is what actually decouples the camera from the player's facing. See DECISIONS.md for the
    // full port notes (what's faithful, what's a deliberate simplification, what's deferred).
    public boolean thirdPersonFreeLookEnabled = false;
    public ThirdPersonRotateMode thirdPersonRotateMode = ThirdPersonRotateMode.INTEREST_POINT;
    // Force-renders the crosshair in 3rd person and redirects attack/use/mine targeting to what the
    // crosshair (camera's own sightline) actually points at, instead of the player body's real facing —
    // "shooting like first-person" from the README.
    public boolean thirdPersonCrosshairEnabled = true;
    // While aiming a bow/crossbow/trident, predicts which nearby entity you're trying to hit and faces
    // the player at it — so the arrow actually goes where the crosshair is, not just where the camera
    // looks. Only matters when thirdPersonCrosshairEnabled is also on (uses the same hit-test).
    public boolean thirdPersonPredictiveAim = true;
    public boolean thirdPersonPitchLock = false;
    // blocks — default 2.7 (270cm, point 7/D120; the UI's own slider already displays this ×100 as
    // literal cm). Previously 4.0, matching vanilla's fixed 3rd-person distance.
    public float thirdPersonFreeDistance = 2.7f;
    // Reinterprets the left stick relative to the free camera's own facing (push "up" = away from the
    // camera, not the body) and turns the body to match, instead of vanilla's always-body-relative
    // movement. Shipped OFF (D088) purely as risk-aversion — it touches KeyboardInputMixin, the most
    // movement-critical file in the project — and that caution has since been paid off by real use.
    //
    // NOW ON by default: with free-look on and this off, the body's yaw has NOTHING that can change it
    // while moving (the right stick turns only the camera, and the idle rotate-strategy deliberately
    // bails while moving), so the character literally cannot turn — reported as "quiero que el monito
    // gire sobre su propio eje como un trompo y no lo permite". This is the completion of free-look,
    // not an extra. Scope of the change is narrow: it is only ever read when free-look is enabled,
    // which is itself opt-in and off by default, so nobody who hasn't opted into free-look is affected.
    public boolean thirdPersonCameraRelativeMovement = true;
    // NOTE: the reference mod's "player transparency" (fade the model out when the camera sits close
    // behind/inside it) is deliberately NOT implemented — see TODO_BLOCKERS.md for why. No config field
    // for it on purpose: never ship a toggle for a feature that would silently do nothing.

    /** How the player's BODY faces while idle and the free camera looks independently — simplified
     *  from the reference mod's 5-mode decision map (see DECISIONS.md's honest scope note on why:
     *  faithfully reproducing "auto-face movement direction" needs camera-relative movement input,
     *  which touches the movement-critical mixin and was deliberately left alone this round). Regardless
     *  of mode, aiming a ranged weapon or actively interacting (attack/use held) always overrides to
     *  face the crosshair/predicted target — that's what makes "shooting like first-person" work in
     *  every mode. WHILE ACTIVELY MOVING (stick pushed), the body keeps behaving exactly like vanilla
     *  today (unaffected by this setting) — only the idle/stationary case is what these modes govern. */
    public enum ThirdPersonRotateMode {
        /** Idle: face wherever the crosshair points. Default — matches the reference mod's own
         *  default and reads as the most natural out of the box. */
        INTEREST_POINT,
        /** Tank-style: body always faces exactly where the camera faces (the pre-free-look behavior). */
        FOLLOW_CAMERA,
        /** Body never auto-turns from this system at all while idle — pure decoupled look, the
         *  literal "keep the body stationary" reading of the feature's own description. */
        NONE
    }

    // Steam Input slots: generic actions (steampad_slot_1..10) the user maps to physical inputs
    // (paddles, extra buttons) inside Steam's controller configurator. Key = slot number "1".."10",
    // value = the keybind id (vanilla/mod) OR "bind:<GamepadBinds.Bind name>" (a SteamPad internal
    // action, e.g. "bind:RADIAL") that slot triggers in-game — see VirtualBindInput/SteamSlotDispatcher
    // (feedback: "esas ranuras de Steaminput no puedo asignar menu radial ni zoom, debemos poder
    // asignar cualquier cosa"). GLOBAL on purpose: the physical mapping lives in Steam's per-game
    // config (not per-pad), and Steam controller handles are not stable across sessions, so
    // per-controller storage would silently lose assignments.
    //
    // This is the GAMEPLAY layer specifically (no screen open, not mounted) — three more layers exist
    // for the OTHER contexts a slot might be pressed in (feedback: "varios layers... MENU, GAMPLAY,
    // INVENTORIO, MONTADO"), same key/value shape, all independently assignable per slot so the same
    // physical paddle can do something different depending on what you're doing when you press it.
    public Map<String, String> steamInputSlots = new HashMap<>();
    public Map<String, String> steamInputSlotsMenu = new HashMap<>();
    public Map<String, String> steamInputSlotsInventory = new HashMap<>();
    public Map<String, String> steamInputSlotsMounted = new HashMap<>();

    // Display names for controllers (handle string → custom name)
    public Map<String, String> controllerDisplayNames = new HashMap<>();

    // Last active controller handle per session (restored on startup)
    public long lastActiveControllerHandle = 0L;

    /**
     * Last active controller by PERSISTENT IDENTITY key — what {@link #lastActiveControllerHandle}
     * should always have been for cross-restart use.
     *
     * <p>A handle is a per-process number: SDL restarts its instance-id counter in every new process
     * and GLFW hands out the first free slot, so "handle 1" in this session is frequently a DIFFERENT
     * physical pad than "handle 1" was in the last one. Restoring by that number therefore did not
     * merely fail to find the old pad, it silently activated whichever pad happened to land on the
     * saved number — the reported "cuando se apaga y se vuelve a prender queda como loco". The handle
     * field is still written and still consulted, but only as a same-session fallback, and only after
     * this key has had its chance. Empty = nothing recorded yet.
     */
    public String lastActiveControllerId = "";

    /** Shown once ever, the first time any controller becomes active — a short "here's what exists"
     *  screen (radial, emotes, zoom, cámara libre...) so a growing feature list doesn't just live
     *  buried in Ajustes Globales waiting to be stumbled on. See OnboardingScreen. */
    public boolean hasSeenOnboarding = false;

    /**
     * Preferred ("Default") controller, by PERSISTENT IDENTITY key — auto-activated whenever it is
     * detected. See {@link dev.steampad.service.ControllerIdentity}: the key is built from the USB
     * vendor/product pair (plus the serial when SDL can read one), so it survives reconnects, restarts
     * and backend switches, and it distinguishes two pads of the same model.
     *
     * <p>Replaces matching by display NAME, which is what made "no lo recuerda como predeterminado"
     * happen: the same 8BitDo reports a different name string depending on the backend that enumerated
     * it and on which compatibility mode it powered on in, so an exact-string comparison against a name
     * saved in an earlier session simply failed and the default silently did not apply. Empty = no
     * preference. {@link #preferredControllerName} is kept only to migrate an existing setting once.
     */
    public String preferredControllerId = "";

    /** @deprecated superseded by {@link #preferredControllerId}; read once at startup to migrate a
     *  pre-existing preference, then cleared. Never write this. */
    @Deprecated
    public String preferredControllerName = "";

    public enum BlockReachAround {
        OFF, SINGLEPLAYER_ONLY, SINGLEPLAYER_AND_LAN, EVERYWHERE
    }

    public static GlobalConfig defaults() {
        return new GlobalConfig();
    }
}
