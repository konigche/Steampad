# SPEC.md — Especificación Funcional del Mod SteamPad

## 1. Pantalla de Entrada (Controller Select Screen)

Pantalla principal del mod, accesible desde el menú o keybind configurado.

### Estructura Visual
```
┌─────────────────────────────────────────────────────┐
│  SteamPad — Controller Selection                    │
│                                                     │
│  [Global Settings]                                  │
│                                                     │
│  ┌─────────────────────────────────────────────┐    │
│  │  🎮 DualSense Wireless Controller           │    │
│  │  Estado: ACTIVO | Batería: 78%              │    │
│  │  [Select ✓]  [Controller Settings]          │    │
│  └─────────────────────────────────────────────┘    │
│                                                     │
│  ┌─────────────────────────────────────────────┐    │
│  │  🎮 Xbox Wireless Controller                │    │
│  │  Estado: Conectado                          │    │
│  │  [Select]  [Controller Settings]            │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

### Reglas
- Lista todos los controladores visibles para Steam Input (ISteamInput::GetConnectedControllers)
- Display name personalizable por el usuario
- Solo UN controlador puede estar marcado como ACTIVO en esta instancia
- Seleccionar uno desactiva los demás para esta instancia (aislamiento lógico)
- Botón Global Settings → abre GlobalSettingsScreen
- Botón Controller Settings → abre ControllerSettingsScreen para ese device

---

## 2. Global Settings

Navegación: ControllerSelectScreen → [Global Settings]

### 2.1 Natives
- `Load Natives` (Boolean, ON/OFF)
  - Controla si el mod intenta cargar las nativas de Steamworks4j
- `Custom Natives Path` (String, path)
  - Path alternativo a las .so/.dll de Steam nativas

### 2.2 Server Options
- `Block Reach Around` (Enum)
  - OFF | Singleplayer Only | Singleplayer and LAN | Everywhere
- `Allow Server Vibration` (Boolean)
- `Keyboard-like Movement` (Boolean)
- `[Add Current Server to Whitelist]` (Botón)
- `Keyboard-like Movement Whitelist` (Lista de IPs/hostnames)

### 2.3 Miscellaneous
- `UI Sounds` (Boolean)
- `Notify Low Battery` (Boolean)
- `Out of Focus Input` (Boolean)
  - Si ON, el mod procesa input incluso cuando Minecraft no está en foco
- `Ingame Button Guide Scale` (Float, 50%-200%)
- `Use Enhanced Steam Deck Driver` (Boolean, experimental)
- `[Copy Debug Dump to Clipboard]` (Botón)
  - Genera un dump del estado del runtime: Steam disponible, controladores detectados, versión, flags

---

## 3. Controller Settings — Tab: BASIC

Navegación: ControllerSelectScreen → [Controller Settings]

### 3.1 Sensitivity
- `Horizontal Look Sensitivity` (Float)
- `Vertical Look Sensitivity` (Float)
- `Virtual Mouse Sensitivity` (Float)
- `Invert Look Y-Axis` (Boolean)
- `Reduce Aiming Sensitivity` (Boolean)

### 3.2 Controls
- `Sneak` (Enum: Hold / Toggle)
- `Sprint` (Enum: Hold / Toggle)
- `Auto Jump` (Boolean)
- `No Fly Drifting` (Boolean)
- `LCE Style Controls` (Boolean)

### 3.3 Accessibility
- `Show Ingame Button Guide` (Boolean)
- `Ingame Button Guide Position` (Enum: Top / Bottom)
- `Show Screen Button Guide` (Boolean)
- `Show On-Screen Keyboard` (Enum: Off / Controlify / System)
- `On-screen Keyboard Height` (Float)
- `Controller Theme` (Enum: Default / Xbox / PS4 / Steam Deck)

### 3.4 Deadzones
- `Left Stick Deadzone` (Float, 0.0–1.0)
- `Right Stick Deadzone` (Float, 0.0–1.0)
- `Button Activation Threshold` (Float, 0.0–1.0)
- `[Automatic Calibration]` → CalibrationScreen

### 3.5 Radial Menu
Ver sección 5 — Sistema Radial.

### 3.6 Controls — Bindings

Botón: `[Reset All Binds]`

#### Categoría: Gameplay
| Binding | Descripción |
|---------|-------------|
| Walk Forward | Avanzar |
| Walk Backward | Retroceder |
| Strafe Left | Desplazarse izquierda |
| Strafe Right | Desplazarse derecha |
| Look Up | Mirar arriba |
| Look Down | Mirar abajo |
| Look Left | Mirar izquierda |
| Look Right | Mirar derecha |
| Jump | Saltar |
| Sneak | Agacharse |
| Attack | Atacar |
| Use | Usar / Interactuar |
| Sprint | Correr |
| Pause | Pausar |
| Inventory | Inventario |
| Change Perspective | Cambiar perspectiva |
| Swap Hands | Cambiar manos |
| Open Chat | Abrir chat |
| Drop Item | Soltar item |
| Drop Stack | Soltar stack |
| Pick Block | Seleccionar bloque |
| Pick Block (NBT) | Seleccionar bloque con NBT |
| Take Screenshot | Captura de pantalla |
| Toggle HUD Visibility | Ocultar HUD |
| Show Player List | Lista de jugadores |
| Game Mode Switcher | Selector de modo de juego |

#### Categoría: Hotbar
| Binding | Descripción |
|---------|-------------|
| Next Hotbar Slot | Siguiente slot |
| Prev Hotbar Slot | Slot anterior |
| Load Creative Hotbar (Radial) | Cargar hotbar creativo via radial |
| Save Creative Hotbar (Radial) | Guardar hotbar creativo via radial |
| Hotbar Slot Select Radial | Radial de selección de slot |

#### Categoría: GUI
| Binding | Descripción |
|---------|-------------|
| GUI Press | Confirmar / Press |
| GUI Back | Volver atrás |
| GUI Next Tab | Siguiente pestaña |
| GUI Prev Tab | Pestaña anterior |
| GUI Abstract Action 1 | Acción abstracta 1 |
| GUI Abstract Action 2 | Acción abstracta 2 |
| GUI Abstract Action 3 | Acción abstracta 3 |
| GUI Navigate Up | Navegar arriba |
| GUI Navigate Down | Navegar abajo |
| GUI Navigate Left | Navegar izquierda |
| GUI Navigate Right | Navegar derecha |
| Cycle Option Forward | Ciclar opción adelante |
| Cycle Option Backward | Ciclar opción atrás |

#### Categoría: Virtual Mouse
| Binding | Descripción |
|---------|-------------|
| VMouse Move Up | Mover cursor arriba |
| VMouse Move Down | Mover cursor abajo |
| VMouse Move Left | Mover cursor izquierda |
| VMouse Move Right | Mover cursor derecha |
| VMouse Left Click | Click izquierdo |
| VMouse Right Click | Click derecho |
| VMouse Shift Click | Shift + Click |
| VMouse Snap Up | Snap cursor arriba |
| VMouse Snap Down | Snap cursor abajo |
| VMouse Snap Left | Snap cursor izquierda |
| VMouse Snap Right | Snap cursor derecha |
| VMouse Scroll Up | Scroll arriba |
| VMouse Scroll Down | Scroll abajo |
| Key Escape | Tecla Escape |
| Key Shift | Tecla Shift |
| Page Next | Página siguiente |
| Page Prev | Página anterior |
| Page Up | Subir página |
| Page Down | Bajar página |
| Toggle Virtual Mouse | Activar/desactivar VMouse |

#### Categoría: Radial Menu
| Binding | Descripción |
|---------|-------------|
| Open Radial Menu | Abrir menú radial |
| Radial Navigate Up | Navegar radial arriba |
| Radial Navigate Down | Navegar radial abajo |
| Radial Navigate Left | Navegar radial izquierda |
| Radial Navigate Right | Navegar radial derecha |

#### Categoría: Gyro
| Binding | Descripción |
|---------|-------------|
| Gyro Activation Button | Botón de activación de gyro |

#### Categoría: Debug
| Binding | Descripción |
|---------|-------------|
| Debug Radial Menu | Abrir radial de debug |
| Toggle Debug Menu (F3) | Alternar menú debug (F3) |
| FPS / Profiler / Net Graph / Charts | Submenú de charts de debug |

#### Categoría: Chords
Los chords se definen por pares (modifier + main) y aparecen listados aquí con formato visual `[LB] + [A]`.

---

## 4. Sistema de Chords

### Modelo
```
ChordInput {
  modifier: ControllerButton
  main: ControllerButton
}
```

### Flujo de Captura
- **Simple:** presionar un botón → se registra como binding simple
- **Chord automático:** mantener modifier y presionar main → se detecta automáticamente
- **Chord manual:**
  1. Usuario elige "Crear Chord"
  2. Se captura modifier
  3. Se captura main
  4. Se registra el chord

### Estado Temporal de Captura
- `pendingModifier: ControllerButton | null`
- `chordCaptureMode: boolean`
- Reset/cancel seguros sin corromper estado

### Resolución en Runtime
1. Leer inputs held (botones presionados)
2. Detectar newly pressed (nuevo frame)
3. Buscar chords activos que coincidan
4. Resolver chords primero (prioridad alta)
5. Si chord encontrado → disparar acción del chord, suprimir main
6. Si no hay chord → resolver simples

### Regla de Supresión
- Si existe definido `modifier + main`:
  - Al tener modifier held y presionar main → chord tiene prioridad
  - El simple de main se bloquea temporalmente
  - Al soltar modifier → main vuelve a comportarse normal

---

## 5. Sistema Radial

### Apertura
- Binding configurable: `Open Radial Menu`
- Se abre mientras el botón esté presionado (o toggle, configurable)

### Visual
```
         [slot 0]
    [slot 7]  [slot 1]
  [slot 6]  ●  [slot 2]
    [slot 5]  [slot 3]
         [slot 4]
```
- Overlay circular con máximo 8 slots
- Inner radius, outer radius, gap entre segmentos configurables
- Colores de menú y elementos configurables
- Opción para ocultar el donut de fondo
- Live Preview en editor

### Trigger por Slot
- `On Click` — ejecutar al confirmar selección
- `On Release` — ejecutar al soltar botón del menú

### Tipos de Acción por Slot (RadialActionType)
- `CHAT_COMMAND` — ejecutar comando de chat
- `KEYBIND` — disparar un keybind estándar de Minecraft
- `SUBMENU` — abrir un sub-radial
- `SCREEN_SHORTCUT` — abrir una pantalla del mod
- `MALILIB_KEYBIND` — keybind de MaLiLib (si compat activa)

### Iconos por Slot
- Cualquier ítem del juego (con soporte NBT)
- Live Inventory Sync (mostrar ítem actual del inventario)
- Mob Effect Icons
- Custom Characters
- Sin icono (solo texto)

### Naming
- Nombre visible por slot (texto encima del icono)
- Nombre vacío para look minimalista

### Editor de Radial (RadialEditorScreen)
- Lista de slots
- Para cada slot: tipo, acción, icono, nombre, trigger
- Preview en tiempo real del radial resultante
- Navegable 100% con mando

---

## 6. Controller Settings — Tab: ADVANCED

### 6.1 Controller Mapping
- `Mixed Input` (Boolean) — permite mando + teclado/mouse simultáneo
- `[Map to Gamepad]` — abrir mapper
- `[Clear Mapping]` — limpiar mapping

### 6.2 Vibration
- `Allow Vibration` (Boolean)
- `HD Haptics` (Boolean, si aplica)
- Intensidades individuales (Float 0.0–2.0 cada una):
  - Master
  - Player
  - World
  - Interaction
  - GUI
  - Global Event
  - Misc

### 6.3 Gyro
- `Look Sensitivity` (Float)
- `Gyro Behaviour` (Enum: Absolute / Relative)
- `Yaw Mode` (Enum: Yaw Only / Roll Only / Both)
- `Invert X` (Boolean)
- `Invert Y` (Boolean)
- `Require Button` (Enum: Hold / Invert / Toggle / Off)
- `Flick Stick` (Boolean)

### 6.4 Advanced
- `Screen Repeat Navigation Delay` (Integer ms)
- `[Test Vibration]` (Botón)

---

## 7. Calibración (CalibrationScreen)

- Pantalla dedicada para calibrar deadzones manualmente
- Muestra estado actual de ejes en tiempo real
- Permite ajustar con visual feedback
- Guarda al confirmar

---

## 8. Detección de Entorno (Platform)

El mod detecta:
- ¿Steam está disponible? (SteamAPI_IsSteamRunning)
- ¿Steam Input está disponible?
- ¿Parece Steam Deck? (MODEL_ID env var o EDID)
- ¿Parece Gamescope? (GAMESCOPE_WAYLAND_DISPLAY env var)
- ¿Entorno Linux/Windows?
- Versión de Steamworks4j activa

Todo esto se incluye en el debug dump.

---

## 9. Aislamiento por Instancia

- Usuario selecciona controlador activo en esta instancia via ControllerSelectScreen
- InputBindingManager filtra: solo despacha acciones del controlador activo (`activeControllerHandle`)
- Controladores no seleccionados: detectados pero ignorados a nivel de dispatching
- El debug dump indica: cuál es el activo y cuáles están suprimidos
- El aislamiento es lógico (dentro del proceso JVM), no a nivel OS

---

## 10. Persistencia

```
.minecraft/config/steampad/
├── global.json              ← GlobalConfig
└── controllers/
    └── {controller_id}.json ← ControllerConfig + BindingConfig + RadialConfig
```

- Autosave al cambiar cualquier ajuste
- Validación de esquema: si el JSON está corrupto, se carga defaults seguros y se notifica al usuario
- Display names personalizados guardados en global.json (map de handle → name)
