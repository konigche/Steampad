# CREDITS.md — Third-Party Notices

SteamPad's own original code is licensed under **LGPL-3.0-or-later** — see `LICENSE` and
`LICENSE.LESSER`. Everything on this page is **not** SteamPad's code: it is either (a) code from
another project ported into SteamPad with attribution, (b) data bundled with SteamPad, or
(c) a feature independently written after observing another mod's behavior. Each item below
keeps its own original license, unaffected by SteamPad's own license.

---

## 1. Code ported with attribution (MIT License)

These pieces were read from the real, published source of MIT-licensed projects and ported —
adapted to SteamPad's own mappings, APIs, and architecture — with attribution kept in the
Javadoc of every file involved. MIT explicitly permits this: reuse, modification, and
redistribution are allowed as long as the original copyright notice is preserved, which is what
the notices below (and the in-code Javadoc `@see`/credit comments) do.

### Leawind/Third-Person
**https://github.com/Leawind/Third-Person** — MIT License, Copyright (c) Leawind.
Camera pipeline for SteamPad's free-look third-person camera: the tick/frame split for smooth
follow motion, FOV-projected screen-space offset, pitch squeeze, and 8-corner wall detection.
Ported into `dev.steampad.util.SmoothValue`, `dev.steampad.mixin.GameRendererFovInvoker`, and
`dev.steampad.input.ThirdPersonCameraController`.

### KosmX/minecraftPlayerAnimator
**https://github.com/KosmX/minecraftPlayerAnimator** — MIT License, Copyright (c) KosmX.
Keyframe sampling/easing semantics and the whole-model "body" transform mechanism for SteamPad's
emote engine. Ported into `dev.steampad.emote.EmoteAnimator`, `dev.steampad.emote.Easing`,
`dev.steampad.emote.EmoteData`, `dev.steampad.emote.EmoteCraftBinaryParser`, and
`dev.steampad.mixin.PlayerBodyTransformMixin`.

### KosmX/bendy-lib
**https://github.com/KosmX/bendy-lib** — MIT License, Copyright (c) KosmX.
Per-vertex cuboid-bend deformation math. Ported into `dev.steampad.emote.bend.CuboidBender`.

### pcal43/splitscreen
**https://github.com/pcal43/splitscreen** — MIT License, Copyright (c) 2023 pcal.net.
Window-tiling positioning math for SteamPad's local-multiplayer window arrangement feature
(not in-game split rendering). Reimplemented against SteamPad's own window/mixin plumbing —
ported logic, not vendored code — in `dev.steampad.client.window.WindowArrangeMode`.

---

## 2. Third-party data bundled with SteamPad

Data files are not code and are not covered by copyright the way source code is (a set of facts
or public-domain content isn't creative expression) — listed here anyway, in full, for
transparency.

### Emote animations (CC0-1.0)
**https://github.com/KosmX/Emotecraft-emotes** — Creative Commons Zero v1.0 Universal (public
domain dedication, no attribution legally required; credited here in good faith). 12 curated
emote files bundled at `assets/steampad/emotes/`. Full notice: `assets/steampad/emotes/LICENSE.md`.
Per-file `author` fields from the original collection are preserved and shown in SteamPad's own
emote library UI.

### Gamepad mapping database (zlib License)
**https://github.com/mdqinc/SDL_GameControllerDB** — zlib License, Copyright (c) the
SDL_GameControllerDB contributors. `assets/steampad/gamecontrollerdb.txt` bundles a curated
subset of mapping lines from this community database (focused on 8BitDo controllers) alongside
SteamPad's own additions, loaded via SDL3's mapping system. The zlib License permits
redistribution provided the origin isn't misrepresented and this notice isn't removed — see the
file's own header for the copy of this notice kept with the data.

---

## 3. Features inspired by observed behavior — independent implementation

**Controlify** — **https://github.com/isXander/Controlify** — LGPL-3.0-or-later,
Copyright (c) isXander.

Several SteamPad features (the gamepad-driven virtual mouse cursor, the on-screen virtual
keyboard opened by pressing the confirm button on a focused text field, and the "keep the
cursor grabbed while the window is unfocused" behavior) were **conceived after playing and
observing Controlify**, a full-featured Fabric controller mod. SteamPad's implementations were
written independently, from scratch, without reading or copying Controlify's source — verified
directly, not assumed: SteamPad's virtual-mouse motion model (3-state OFF/ON/AUTO cycling,
exponential velocity easing, simultaneous physical-mouse-and-controller-cursor coexistence),
its out-of-focus cursor-lock mixin (targeting a different vanilla method, built from a bug
independently traced through this project's own bytecode investigation), and its virtual
keyboard (a static controller class, not a `Screen` subclass) are all architecturally distinct
from Controlify's own code, which was cloned and read line-by-line for this comparison before
writing this notice. Under LGPL — which protects Controlify's own code, not the idea or
observed behavior of "a controller mod can have a virtual cursor" — this is a clean
independent implementation, credited here for the design inspiration regardless.

---

## Standard build dependencies (not bundled inside SteamPad's own code)

These are separate libraries the mod links against at build/runtime, distributed as their own
jars, not code copied into this repository. Listed for completeness:

| Dependency | License | Role |
|---|---|---|
| Fabric Loader / Fabric API | Apache-2.0 | Mod loading, game lifecycle hooks |
| Steamworks4j | zlib/libpng-style | ISteamController / ISteamInput bindings |
| Cloth Config API | LGPL-3.0 | Settings-screen framework |
| SDL3 (via JNA) | zlib | Cross-platform gamepad/rumble backend |
