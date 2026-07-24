# ARCHITECTURE.md — Arquitectura del Mod SteamPad

## Capas de la Arquitectura

```
┌────────────────────────────────────────────────────────┐
│  CAPA UI       screen/*, radial/RadialMenuOverlay      │
├────────────────────────────────────────────────────────┤
│  CAPA SERVICE  service/*, config/*                     │
├────────────────────────────────────────────────────────┤
│  CAPA INPUT    input/* (bindings, chords, vmouse, gyro)│
├────────────────────────────────────────────────────────┤
│  CAPA STEAM    steam/* (bootstrap, manager, actions)   │
├────────────────────────────────────────────────────────┤
│  CAPA PLATFORM platform/* (Linux, Gamescope, Deck)     │
├────────────────────────────────────────────────────────┤
│  CAPA COMPAT   compat/* (SDL fallback, MaLiLib)        │
├────────────────────────────────────────────────────────┤
│  CAPA MIXIN    mixin/* (hooks delgados en MC)          │
└────────────────────────────────────────────────────────┘
```

## Paquetes y Responsabilidades

### `dev.steampad` (raíz)
| Clase | Rol |
|-------|-----|
| `SteamPadMod.java` | Entry point servidor, registra keybind global de apertura de UI |
| `SteamPadClient.java` | Entry point cliente Fabric, inicializa todos los servicios en orden correcto |

### `dev.steampad.steam`
| Clase | Rol |
|-------|-----|
| `SteamNativeLoader` | Extrae y carga las natives de Steamworks4j desde el JAR |
| `SteamBootstrap` | Llama `SteamAPI.init()`, gestiona ciclo de vida Steam, callbacks loop, resuelve el AppID efectivo de la sesión (`resolveEffectiveAppId()`, D054) |
| `SteamInputManager` | Wrapper sobre `ISteamController` (Steamworks4j no envuelve `ISteamInput` en ninguna versión — Valve documenta paridad de funciones entre ambas, ver D054): GetConnectedControllers, RunFrame, GetDigitalActionData, GetAnalogActionData |
| `SteamControllerHandleRef` | Value object que encapsula un `InputHandle_t` con metadata (nombre, tipo) |
| `SteamActionRegistry` | Define y registra los ActionSet y ActionHandles con Steam Input |
| `SteamControllerConfigDeployer` | Despliega `game_actions_<appid>.vdf` directo en `<Steam>/controller_config/` (ruta de auto-descubrimiento de Valve, sin importación manual — D054) |
| `SteamGlyphService` | Resuelve glyphs/iconos de botones via `GetGlyphForActionOrigin` |
| `SteamHapticsService` | Envía vibración via `TriggerVibration` o `TriggerHapticPulse` |
| `SteamRuntimeDiagnostics` | Genera el debug dump del estado completo de Steam en esta instancia |

### `dev.steampad.input`
| Clase | Rol |
|-------|-----|
| `ControllerState` | Snapshot inmutable del estado de un controlador en un tick |
| `InputBinding` | Asocia una acción lógica a un botón/eje de controlador (puede ser ChordInput) |
| `InputBindingManager` | Lee el state del controlador activo y despacha acciones; ignora los inactivos |
| `InputAction` | Enum/class de todas las acciones lógicas disponibles (Walk Forward, Jump, etc.) |
| `InputDispatchContext` | Contexto del tick actual: qué pantalla está abierta, si es juego o GUI |
| `ChordInput` | Par (modifier, main) que define un chord |
| `ChordResolver` | Lógica de resolución y supresión de chords |
| `VirtualMouseController` | Mueve el cursor virtual en pantallas GUI con análogos |
| `GyroHandler` | Lee datos de giroscopio y los convierte en movimiento de cámara |
| `DeadzoneProcessor` | Aplica deadzone circular/cuadrada y escalado a valores de ejes |

### `dev.steampad.config`
| Clase | Rol |
|-------|-----|
| `ConfigManager` | Carga/guarda JSON; valida esquemas; gestiona migración de versiones |
| `GlobalConfig` | POJO de configuración global serializable |
| `ControllerConfig` | POJO de configuración por controlador |
| `RadialConfig` | POJO de configuración del menú radial (visual + slots) |
| `BindingConfig` | Map de `InputAction` → `InputBinding` serializable |

### `dev.steampad.screen`
| Clase | Rol |
|-------|-----|
| `ControllerSelectScreen` | Pantalla principal: lista controladores, selección activa |
| `GlobalSettingsScreen` | Pantalla de ajustes globales del mod |
| `ControllerSettingsScreen` | Contenedor con tabs (Basic/Advanced) |
| `BindingsScreen` | Lista y editor de bindings por categoría |
| `CalibrationScreen` | Calibración visual de deadzones |
| `RadialEditorScreen` | Editor visual del menú radial |

#### `dev.steampad.screen.widgets`
| Clase | Rol |
|-------|-----|
| `ControllerEntryWidget` | Widget de un controlador en la lista de selección |
| `TabBarWidget` | Barra de tabs navegable con mando |
| `ButtonGuideWidget` | Guía de botones en pantalla (in-game y in-screen) |
| `SettingRowWidget` | Fila genérica de ajuste (label + control) |

### `dev.steampad.radial`
| Clase | Rol |
|-------|-----|
| `RadialMenuController` | Gestiona apertura, navegación y ejecución del radial |
| `RadialMenuOverlay` | Renderiza el overlay radial sobre el juego |
| `RadialSlot` | Datos de un slot: tipo, acción, icono, nombre, trigger |
| `RadialAction` | Acción concreta de un slot (ejecutable) |
| `RadialActionType` | Enum: CHAT_COMMAND, KEYBIND, SUBMENU, SCREEN_SHORTCUT, MALILIB_KEYBIND |
| `RadialRenderer` | Lógica de dibujo: segmentos, iconos, texto, hover |

#### `dev.steampad.radial.icon`
| Clase | Rol |
|-------|-----|
| `RadialIconResolver` | Dispatcher: elige qué provider usar según tipo de icono |
| `ItemIconProvider` | Renderiza ítem de inventario (con NBT) |
| `EffectIconProvider` | Renderiza icono de efecto de poción |
| `CharacterIconProvider` | Renderiza un carácter custom (unicode) |

### `dev.steampad.service`
| Clase | Rol |
|-------|-----|
| `ActiveControllerService` | Singleton que mantiene el handle del controlador activo en esta instancia |
| `ControllerIsolationService` | Asegura que solo el controlador activo genere eventos de acción |
| `BatteryMonitorService` | Monitorea nivel de batería y notifica si es bajo |
| `ClipboardDebugService` | Genera y copia al portapapeles el debug dump |
| `UiSoundService` | Reproduce sonidos UI del mod |

### `dev.steampad.platform`
| Clase | Rol |
|-------|-----|
| `LinuxRuntimeInspector` | Detecta variables de entorno y características Linux |
| `GamescopeDetector` | Detecta GAMESCOPE_WAYLAND_DISPLAY y otros flags de Gamescope |
| `SteamDeckDetector` | Detecta si el hardware es Steam Deck |
| `SteamLaunchDetector` | Detecta el AppID real que Steam asignó a esta sesión vía `SteamAppId`/`SteamGameId` (D054) |
| `EnvironmentReport` | Agrega toda la info de plataforma en un objeto reportable |

### `dev.steampad.compat`
| Clase | Rol |
|-------|-----|
| `SDLFallbackProvider` | Usa SDL3 via JNA como fallback de detección de controladores si Steam Input falla |
| `MalilibCompat` | Integración opcional con MaLiLib para keybinds avanzados en radial |

### `dev.steampad.mixin`
| Clase | Rol |
|-------|-----|
| `MinecraftClientMixin` | Hook en el tick del cliente → llama a SteamBootstrap.runCallbacks() |
| `GameRendererMixin` | Hook en render → invoca RadialMenuOverlay si está abierto |
| `MouseMixin` | Intercepta el mouse si VirtualMouseController está activo |
| `ScreenMixin` | Hook en renderizado de pantallas → inyecta ButtonGuideWidget |

### `dev.steampad.util`
| Clase | Rol |
|-------|-----|
| `JsonUtil` | Helpers para serialización/deserialización Gson |
| `TimeUtil` | Utilidades de tiempo (ms, ticks) |
| `MathUtil` | Lerp, deadzone math, clamp |
| `LogUtil` | Logger con prefijo [SteamPad] |

---

## Flujo de Datos Principal

```
Steam Input API (ISteamInput)
    │
    ▼
SteamInputManager.runFrame()  ← llamado desde MinecraftClientMixin cada tick
    │
    ▼
ControllerState (snapshot por controlador)
    │
    ▼
ControllerIsolationService.filter()  ← solo pasa el controlador activo
    │
    ▼
InputBindingManager.dispatch()
    │
    ├──→ ChordResolver.resolve()  ← chords primero
    │        │
    │        └──→ InputAction.execute()
    │
    └──→ Simple binding execute()
         │
         ├──→ Gameplay actions (LWJGL keys)
         ├──→ GUI navigation (VirtualMouseController)
         └──→ RadialMenuController.open()
```

## Flujo de Inicialización

```
SteamPadClient.onInitializeClient()
    ├── SteamNativeLoader.load()
    ├── SteamBootstrap.init()
    │     ├── SteamAPI.init()
    │     └── ISteamInput.init()
    ├── SteamActionRegistry.registerAll()
    ├── ConfigManager.loadAll()
    ├── ActiveControllerService.restoreFromConfig()
    ├── BatteryMonitorService.start()
    └── [register keybind para abrir ControllerSelectScreen]
```

## Ciclo de Vida Steam por Tick

```
MinecraftClientMixin.onTick()
    └── SteamBootstrap.runCallbacks()
         └── SteamInputManager.runFrame()
              └── ISteamInput.runFrame()
                   └── Actualiza estado de todos los controladores
```

## Flujo de Selección de Controlador

```
Usuario abre ControllerSelectScreen
    └── SteamInputManager.getConnectedControllers()
         └── Lista de SteamControllerHandleRef

Usuario presiona [Select] en un controlador
    └── ActiveControllerService.setActive(handle)
         └── ConfigManager.save(global) ← persiste elección
              └── ControllerIsolationService actualiza filtro
```

## Integración Steam Input Action Sets

```
SteamActionRegistry.registerAll() define:
├── ActionSet "SteamPad_InGame"
│    ├── Digital actions: jump, attack, use, sneak, sprint, ...
│    └── Analog actions: leftStick, rightStick, gyro
└── ActionSet "SteamPad_GUI"
     ├── Digital actions: gui_press, gui_back, gui_nav_up, ...
     └── Analog actions: vmouse_move

InputDispatchContext determina qué ActionSet activar según contexto:
- isInGame → activar SteamPad_InGame
- hasScreen → activar SteamPad_GUI
```

## Consideraciones de Thread Safety

- Steam callbacks se procesan en el game thread (via Mixin en onTick)
- ControllerState es inmutable (snapshot por tick)
- ActiveControllerService usa campo volatile para handle activo
- Config I/O se hace en threads separados con write-through a disco

## Dependencias entre Módulos

```
platform → (ninguna interna)
util → (ninguna interna)
steam → platform, util
config → util
input → steam, config, util
service → steam, input, config, platform
radial → input, config, service, util
screen → service, radial, input, config, util
mixin → steam, input, service, radial (solo hooks)
```
