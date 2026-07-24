# TESTPLAN.md — Plan y Resultados de Pruebas SteamPad

**Última actualización:** 2026-07-16 sesión 28 cont. 6 (RF-26: v0.50.0 — `.emotecraft` recalibrado con 21 archivos reales del usuario (de 1 a 12 cargando exitosamente), fix del renderizado del ícono (overload de `drawTexture` sin regionWidth/regionHeight), y fix de un bug real de reconexión de mando que perdía la configuración guardada (SDL3 asigna un handle nuevo por reconexión; migración de config por nombre de control). Build 0.50.0 + 29/29; checklist en B083.)

**Anterior:** 2026-07-16 sesión 28 cont. 5 (RF-25: v0.49.0 — cuarta causa raíz REAL de la deformación del emote (pose de agachado contaminaba el caché de rest-origin, confirmado por bytecode vía javap), fix de regresión crítica (emote cancelado tras la transición de cámara, token de generación en las 3 pantallas de preview), `.emotecraft` binario real implementado para el sub-formato v2 (reverse-engineering clean-room verificado byte-exacto contra un archivo real del usuario; el sub-formato v1 queda documentado como blocker abierto), e íconos reales por emote. Build 0.49.0 + 29/29; checklist en B082.)

**Anterior:** 2026-07-16 sesión 28 cont. 4 (RF-24: v0.48.0 — tercera causa raíz REAL de la deformación del emote (offsets de miembros relativos a la rotación del torso, confirmado en la documentación oficial de Emotecraft), transición de cámara zoom out/in al entrar/salir de un emote desde 1ª persona, preview animado extendido a editor de rueda + rueda de gameplay, `.emotecraft` reescrito sin depender de glob, y multi-rueda de emotes en el editor. Build 0.48.0 + 29/29; checklist en B081.)

**Anterior:** 2026-07-16 sesión 28 cont. 3 (RF-23: v0.47.0 — causa raíz REAL de la deformación del emote (modelo de jugador compartido entre entidades, caché estática por identidad de ModelPart), soporte de archivos `.emotecraft`, lote de 6 en el teclado virtual (selección oculta hasta mover un stick, "A" respeta el último stick usado, footer sin perder hints, glifos de inventario ocultos, gesto golpe-vs-mantener, rebase de velocidad), preview animado en Biblioteca de emotes, y segunda pasada de Third-Person (suavizado + perfil de apuntado, free-look deliberadamente fuera de alcance). Build 0.47.0 + 29/29; checklist en B080.)

**Anterior:** 2026-07-16 sesión 28 cont. 2 (RF-22: v0.46.0 — lote de 9 tras la primera prueba real en hardware de FASE 63/Steam Input: fix de deformación del emote, cámara vuelve a 1ª persona sola, rueda de emotes desacoplada del radial, preview fijo, fix de `LogUtil.debug` invisible en producción, diagnósticos de slime/Traveler's Backpack, hallazgo de detección de la Ally, y port acotado de Leawind/Third-Person. Build 0.46.0 + 29/29; checklist en B079.)

**Anterior:** 2026-07-09 sesión 24 cont. 5 (RF-21: v0.19.0 — merge SDL3+GLFW con dedupe (pad de la Ally visible junto al 8BitDo), filtro i2c-HID de la pantalla táctil NVTK0603, glifos de zoom en tiempo real, marcador de zoom con A (baliza de partículas + chat opcional). Cámara de mouse CONFIRMADA ambiental (Moonlight) — frente cerrado. Build 0.19.0 + 24/24; checklist en B050.)

---

## ESTADO GLOBAL DE TESTING

| Categoría | Ejecutado | Resultado |
|-----------|-----------|-----------|
| Unit Tests | ✅ SÍ (2026-06-24) | 24/24 PASSED |
| Tests de integración con MC | ❌ NO | PENDIENTE |
| Tests manuales en Linux | ❌ NO | PENDIENTE |
| Tests en Game Mode / SteamOS | ❌ NO | BLOQUEADO (hardware) |
| Tests multi-controlador | ❌ NO | PENDIENTE |
| Tests multi-instancia | ❌ NO | PENDIENTE |

---

## 1. Unit Tests (JUnit 5) — EJECUTADOS

### Comando ejecutado
```
gradle test --rerun-tasks
```
**Resultado: BUILD SUCCESSFUL — 24/24 tests PASSED (2026-06-24)**

### 1.1 ConfigSerializationTest (6/6 PASSED)
**Archivo:** `src/test/java/dev/steampad/config/ConfigSerializationTest.java`

| Test | Resultado |
|------|-----------|
| testGlobalConfigRoundTrip | ✅ PASSED |
| testControllerConfigRoundTrip | ✅ PASSED |
| testCorruptJsonReturnsNull | ✅ PASSED |
| testBindingConfigDefaultsHaveEntries | ✅ PASSED |
| testRadialConfigDefaultsHaveEightSlots | ✅ PASSED |
| testBindingConfigRoundTrip | ✅ PASSED |

### 1.2 ChordResolverTest (7/7 PASSED)
**Archivo:** `src/test/java/dev/steampad/input/ChordResolverTest.java`

| Test | Resultado |
|------|-----------|
| testSimpleBindingFiresWhenPressed | ✅ PASSED |
| testSimpleFiresWhenModifierNotHeld | ✅ PASSED |
| testSimpleBindingDoesNotFireWhenHeld | ✅ PASSED |
| testChordPreventsSimpleBindingForMain | ✅ PASSED |
| testChordNotFiredWhenMainAlreadyHeld | ✅ PASSED |
| testMultipleChordsWithSameModifier | ✅ PASSED |
| testResetClearsState | ✅ PASSED |

**Nota:** La lógica de resolución de chords y supresión está verificada en aislamiento. El comportamiento en juego real (donde los handles de Steam son raw longs) NO ha sido verificado.

### 1.3 DeadzoneProcessorTest (8/8 PASSED)
**Archivo:** `src/test/java/dev/steampad/input/DeadzoneProcessorTest.java`

| Test | Resultado |
|------|-----------|
| testDeadzoneMapsZeroWithinThreshold | ✅ PASSED |
| testThresholdReturnsBinary | ✅ PASSED |
| testScalingIsNormalized | ✅ PASSED |
| testZeroInputIsZeroOutput | ✅ PASSED |
| testFullDeflectionPassesThrough | ✅ PASSED |
| testCircularDeadzoneIsCircular | ✅ PASSED |
| testOutsideDeadzoneHasValue | ✅ PASSED |
| testNegativeAxis | ✅ PASSED |

### 1.4 ControllerIsolationServiceTest (3/3 PASSED)
**Archivo:** `src/test/java/dev/steampad/service/ControllerIsolationServiceTest.java`

| Test | Resultado |
|------|-----------|
| testActiveControllerPassesThrough | ✅ PASSED |
| testStateWithZeroHandleNeverActive | ✅ PASSED |
| testNullActiveControllerBlocksAll | ✅ PASSED |

---

## 1b. Checklist de Validación de Runtime Fixes (2026-06-24)

Estos tests verifican los tres fixes aplicados en la sesión 2026-06-24. Ejecutar en orden.
**Rebuild antes de cada prueba:**
```
gradle build --no-daemon -x test
```
**JAR:** `C:\Users\RChe\.gradle\controlify-build\steampad\_\libs\steampad-0.1.0.jar`

---

### RF-01 — Fix de Steam natives (SteamNativeLoader)

**Precondición:** Steam corriendo. MC 1.21.4 con Fabric Loader 0.16.14. Mod instalado.

**Escenario A — Carga exitosa (Steam disponible):**
Verificar en `latest.log` los siguientes mensajes en este orden:
```
[SteamPad] Loading Steam natives from bundled JAR (default temp dir).
[SteamPad] Steam natives loaded OK.
Steam API initialized (Steamworks4j 1.9.0 / ISteamController).
```
**Criterio pass:** Las tres líneas aparecen. NO aparece `SteamException: Native libraries not loaded`.

**Escenario B — Steam no disponible (sin Steam corriendo):**
```
[SteamPad] Loading Steam natives from bundled JAR (default temp dir).
[SteamPad] Steam natives loaded OK.
[WARN] SteamAPI.init() returned false. Steam may not be running or AppID is missing.
[WARN] Launch Minecraft from Steam, or ensure steam_appid.txt is present in the run directory.
SteamPad client initialized. Steam available: false, Input available: false
```
**Criterio pass:** MC arranca. NO hay crash. Mod reporta soft fallback.

**Escenario C — Entorno Flatpak (si disponible):**
Desde Prism Launcher Flatpak, sin `customNativesPath` configurado:
```
[SteamPad] Flatpak container detected.
[SteamPad] Steam natives extraction to /tmp may fail in sandboxed launchers.
[SteamPad] Set 'customNativesPath' in SteamPad config to a writable directory.
[SteamPad] Loading Steam natives from bundled JAR (default temp dir).
```
Si `/tmp` es inaccesible en el sandbox, configurar `customNativesPath` en `.minecraft/config/steampad/global.json`:
```json
{ "customNativesPath": "/home/user/.local/share/steampad/natives" }
```
**Log esperado con custom path:**
```
[SteamPad] Loading Steam natives to custom path: /home/user/.local/share/steampad/natives
[SteamPad] Steam natives loaded OK.
```

---

### RF-02 — Fix de KeyBinding constructor (MC 1.21.10 compat)

**Escenario A — MC 1.21.4 (target de compilación):**
```
[SteamPad] Using MC 1.21.4 KeyBinding constructor (String category).
```
**Criterio pass:** Esta línea aparece. NO hay `NoSuchMethodError`.

**Escenario B — MC 1.21.10:**
```
[SteamPad] Using MC 1.21.10+ KeyBinding constructor (Category object).
```
**Criterio pass:** Esta línea aparece. NO hay `NoSuchMethodError`. El mod carga correctamente.

**Escenario C — Fallo de reflexión (fallback de seguridad):**
Si ningún constructor encaja (versión desconocida), el log debe mostrar:
```
[WARN] [SteamPad] KeyBinding reflection failed (<mensaje>). Falling back to 1.21.4 constructor.
[SteamPad] Using MC 1.21.4 KeyBinding constructor (String category).
```
**Criterio pass:** Aunque la reflexión falle, el mod NO crashea. Usa constructor 1.21.4 como fallback.

---

### RF-03 — VDF de Steam Input importado

**Precondición:** Copiar `steampad_steam_input/game_actions_480.vdf` a la carpeta `controller_config/` de Steam:
- Linux: `~/.steam/steam/controller_config/game_actions_480.vdf`
- Windows: `C:\Program Files (x86)\Steam\controller_config\game_actions_480.vdf`

Reiniciar Steam. Iniciar MC con Steam corriendo.

**Log esperado con VDF válido:**
```
Steam Input action handles registered. InGame ActionSet valid: true, GUI ActionSet valid: true
```

**Sin VDF importado:**
```
Steam Input ActionSet handles are 0. VDF config may not be imported in Steam.
```

**Acciones verificables con VDF importado:**
1. Abrir debug dump (portapapeles): `actionSetHandle(InGame) != 0`
2. Mover stick izquierdo → `steampad_left_stick` tiene valor != 0
3. Presionar botón de salto mapeado → `steampad_jump` se activa

---

### RF-05 — Fix de GameRendererMixin (descriptor @Inject incorrecto)

**Qué se corrigió:** Eliminado `@Inject(method = "renderWorld")` con descriptor `(float, long)V` que no existe en MC 1.21.4 ni 1.21.10. La clase mixin queda vacía.

**Verificado en build (2026-06-24):**
```
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot
gradle build --no-daemon -x test
→ BUILD SUCCESSFUL in 18s
```

**Test en MC real (⚠️ PENDIENTE):**
Con el JAR instalado, verificar que MC arranca sin esta traza en `latest.log`:
```
[Mixin] FAILED to apply mixin GameRendererMixin to net.minecraft.class_757
org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException
```
**Criterio pass:** MC arranca. Aparece `SteamPad client initialized.` sin `InvalidInjectionException` ni `MixinApplyError` referenciando `GameRendererMixin`.
**Criterio fail:** `MixinApplyError` con referencia a `GameRendererMixin` (indicaría que el mixin vacío es rechazado — muy improbable pero posible con perfiles Mixin estrictos).

---

### RF-04 — Resumen de logs esperados en arranque limpio exitoso

Secuencia completa en `latest.log` al arrancar con Steam corriendo, VDF importado, MC 1.21.4:
```
SteamPad client initializing...
[SteamPad] Loading Steam natives from bundled JAR (default temp dir).
[SteamPad] Steam natives loaded OK.
[SteamPad] Using MC 1.21.4 KeyBinding constructor (String category).
Steam API initialized (Steamworks4j 1.9.0 / ISteamController).
Steam Input action handles registered. InGame ActionSet valid: true, GUI ActionSet valid: true
SteamPad client initialized. Steam available: true, Input available: true
```

Secuencia en MC 1.21.10 con VDF importado:
```
SteamPad client initializing...
[SteamPad] Loading Steam natives from bundled JAR (default temp dir).
[SteamPad] Steam natives loaded OK.
[SteamPad] Using MC 1.21.10+ KeyBinding constructor (Category object).
Steam API initialized (Steamworks4j 1.9.0 / ISteamController).
Steam Input action handles registered. InGame ActionSet valid: true, GUI ActionSet valid: true
SteamPad client initialized. Steam available: true, Input available: true
```

---

### RF-06 — Fix crash al abrir ControllerSelectScreen (shouldPause + blur guard) — INTENTO FALLIDO
**Build:** SUCCESS (sesión 1). **MC real:** CRASH CONFIRMADO (sesión 2). Reemplazado por RF-08.

### RF-08 — Fix definitivo: SteamPadBaseScreen (sesión 2, 2026-06-24)

**Qué se cambió:**
- CREADO `SteamPadBaseScreen.java` con `renderBackground()` usando `fillGradient` sin `super`
- Los 8 screens del mod extienden `SteamPadBaseScreen` en lugar de `Screen`
- Eliminado flag `backgroundRendered` y override de `renderBackground()` de ControllerSelectScreen

**Build verificado (2026-06-24):** `BUILD SUCCESSFUL in 18s`

**Test en MC real (⚠️ PENDIENTE):**
1. Abrir Minecraft con el mod instalado (Bazzite desktop o Gamescope)
2. Ir a Options → clic en botón icon SteamPad junto a Controls
3. **Criterio PASS A:** Se abre `ControllerSelectScreen` sin crash
4. **Criterio PASS B:** Panel diagnóstico muestra estado correcto (Steam unavailable, 0 controllers o datos reales)
5. **Criterio PASS C:** Botones visibles y funcionales (Refresh, Back, Global Settings)
6. **Criterio PASS D:** Con Steam no disponible: pantalla muestra "No controllers detected." sin crash
7. **Criterio FAIL:** `IllegalStateException: Can only blur once per frame` en log

**Sub-test con Steam no disponible (degradación segura):**
- Panel: "Steam API: Steam not running" o "natives not loaded" en rojo
- Mensaje de ayuda contextual visible ("Launch Minecraft from Steam..." etc.)
- Sin NullPointerException o crash por estado Steam inválido

**Sub-test de screens hijos (todos heredan SteamPadBaseScreen):**
- GlobalSettingsScreen abre desde ControllerSelectScreen sin crash
- ControllerBasicSettingsScreen abre sin crash
- Todos los demás screens del mod abren sin crash

### RF-09 — Detección temprana de controladores en log de startup

**Test:**
1. Arrancar Minecraft con el mod y Steam disponible + controlador conectado
2. **Criterio PASS:** `[SteamPad] Early controller scan: N controller(s) detected.` en latest.log
3. **Criterio PASS (Steam unavailable):** El scan es omitido (sin `isInputAvailable()`) — no aparece la línea, sin crash
4. **Nota:** El scan puede reportar 0 controllers incluso con uno conectado si el poll de Steam aún no ocurrió. El valor correcto aparece cuando el usuario abre ControllerSelectScreen.

---

### RF-10 — Fix NoSuchMethodError: drawTextWithShadow(Text) en MC 1.21.10

**Qué se corrigió:**
- `ControllerSelectScreen.drawStatusLine()`: `ctx.drawTextWithShadow(textRenderer, Text.literal(x), ...)` → `.asOrderedText()`
- `ControllerSelectScreen.renderDiagnosticPanel()`: help line → `.asOrderedText()`
- `ControllerSelectScreen.render()`: entry loop con String args → `.asOrderedText()`
- `BindingsScreen`, `CalibrationScreen`, `RadialEditorScreen`: todas las llamadas `drawTextWithShadow` → `.asOrderedText()`

**Test en MC real (⚠️ PENDIENTE):**
1. Lanzar Minecraft 1.21.10 con el mod
2. Abrir ControllerSelectScreen (aunque Steam no esté disponible)
3. **Criterio PASS:** La pantalla se renderiza completamente — panel diagnóstico visible, líneas de estado, mensaje de ayuda — sin `NoSuchMethodError`
4. **Criterio PASS B:** Abrir BindingsScreen, CalibrationScreen, RadialEditorScreen — ninguna crashea al renderizar texto
5. **Criterio FAIL:** Cualquier `NoSuchMethodError: method27535` o similar en el log

---

### RF-11 — Diagnóstico Steam AppID y botón Retry Steam Init

**Qué se añadió:**
- Panel diagnóstico Línea 1 distingue "no AppID file" de "Steam not running"
- `buildHelpMessage()` muestra ruta real del run directory cuando falta el AppID
- Log en `init()` muestra rutas exactas donde se espera `steam_appid.txt`
- `SteamBootstrap.init()` loguea working directory cuando `SteamAPI.init()` falla
- Botón "Retry Steam Init" aparece cuando Steam down + natives OK + 0 controllers
- Estado vacío: mensaje específico según Steam disponible o no

**Test en MC real sin Steam (⚠️ PENDIENTE):**
1. Lanzar Minecraft con el mod, SIN `steam_appid.txt` y sin Steam corriendo
2. Abrir ControllerSelectScreen
3. **Criterio PASS A:** Panel muestra "Steam API: no AppID file (see help)" (línea roja)
4. **Criterio PASS B:** Línea 6 (help) muestra "Add steam_appid.txt (content: 480) to: [ruta real]"
5. **Criterio PASS C:** Área central muestra "Steam not initialized — see diagnostic panel above." (no "No controllers detected")
6. **Criterio PASS D:** Botón "Retry Steam Init" es visible en el área central
7. **Criterio PASS E:** En `latest.log`, líneas con rutas esperadas del AppID file
8. Crear `steam_appid.txt` con `480` en la ruta que indica el panel/log
9. **Criterio PASS F:** Click en "Retry Steam Init" → si Steam corre, Steam se inicializa; botón desaparece; panel muestra "Steam API: OK"
10. Sin Steam corriendo (pero con AppID file): Panel muestra "Steam API: Steam not running"

---

### RF-07 — Icon button de Options junto al botón Controls

**Qué se corrigió:**
- Botón de texto 120×20 en esquina inferior izquierda → icono 20×20 junto a Controls
- Detección del botón Controls por translation key "options.controls" (locale-independent)
- Tooltip "SteamPad Settings" en hover

**Test en MC real (⚠️ PENDIENTE):**
1. Abrir Options (Esc → Options)
2. **Criterio PASS A:** Aparece un botón cuadrado pequeño (20×20) inmediatamente a la derecha del botón "Controls"
3. **Criterio PASS B:** Hovering sobre el botón muestra tooltip "SteamPad Settings"
4. **Criterio PASS C:** Clic en el botón abre `ControllerSelectScreen`
5. **Criterio PASS D:** El botón tiene un icono de gamepad visible (gris + d-pad + stick + botones)
6. **Criterio FAIL:** El botón aparece en la esquina inferior izquierda (fallback) — indicaría que la detección del botón "Controls" falló en esta versión de MC

---

## 2. Tests de Integración con Minecraft — PENDIENTES

Estos tests requieren un entorno de Minecraft real con el mod instalado y Steam corriendo.

### 2.1 Carga de Steam
**Precondición:** Steam corriendo, Minecraft lanzado con steam_appid.txt=480 (dev) o desde Steam

| Test | Descripción | Estado |
|------|-------------|--------|
| IT-01 | `SteamBootstrap.init()` retorna true con Steam corriendo | ❌ NO EJECUTADO |
| IT-02 | `SteamInputManager.isAvailable()` = true después de init | ❌ NO EJECUTADO |
| IT-03 | `EnvironmentReport.generate()` no lanza excepción | ❌ NO EJECUTADO |
| IT-04 | Logs muestran "Steam API initialized (ISteamController)" | ❌ NO EJECUTADO |
| IT-05 | Sin Steam: logs muestran warn, mod no crashea (ver RF-01 Escenario B) | ❌ NO EJECUTADO EN HARDWARE |

### 2.2 Lectura de Controladores
**Precondición:** Controlador físico conectado, Steam corriendo

| Test | Descripción | Estado |
|------|-------------|--------|
| IT-06 | GetConnectedControllers retorna ≥1 con controlador conectado | ❌ NO EJECUTADO |
| IT-07 | Handle del controlador != 0 | ❌ NO EJECUTADO |
| IT-08 | DisplayName no vacío y legible | ❌ NO EJECUTADO |
| IT-09 | ControllerSelectScreen muestra el controlador | ❌ NO EJECUTADO |

### 2.3 ActionSets y VDF
**Precondición:** VDF importado. Archivo disponible: `steampad_steam_input/game_actions_480.vdf`.
**Instalación:** copiar a `<Steam>/controller_config/game_actions_480.vdf`, luego reiniciar Steam.

| Test | Descripción | Estado |
|------|-------------|--------|
| IT-10 | ActionSetHandle InGame != 0 después de VDF importado | ❌ PENDIENTE (VDF creado, no importado aún) |
| IT-11 | Handles de acciones digitales != 0 | ❌ PENDIENTE |
| IT-12 | Sin VDF: handles = null, mod funciona en modo fallback | ❌ NO EJECUTADO |

**Log esperado con VDF válido:**
```
Steam Input action handles registered. InGame ActionSet valid: true, GUI ActionSet valid: true
```
**Log esperado sin VDF (modo fallback):**
```
Steam Input ActionSet handles are 0. VDF config may not be imported in Steam.
```

---

## 3. Pruebas Manuales — PENDIENTES

### 3.1 Linux Escritorio (Ubuntu/Fedora/Bazzite)

| # | Escenario | Estado | Notas |
|---|-----------|--------|-------|
| M01 | Arranque básico, ControllerSelectScreen muestra controladores | ❌ PENDIENTE | |
| M02 | Seleccionar un controlador | ❌ PENDIENTE | |
| M03 | Persistencia de selección entre reinicios | ❌ PENDIENTE | |
| M04 | Movimiento en juego con stick izquierdo | ❌ PENDIENTE | Crítico — verifica input básico |
| M05 | Cámara con stick derecho | ❌ PENDIENTE | |
| M06 | Saltar | ❌ PENDIENTE | |
| M07 | Abrir inventario | ❌ PENDIENTE | |
| M08 | Navegar inventario con vmouse | ❌ PENDIENTE | |
| M09 | Chord funcional (LB+A dispara chord, A solo no) | ❌ PENDIENTE | Lógica verificada en unit test |
| M10 | Radial abre al mantener botón | ❌ PENDIENTE | |
| M11 | Navegar radial con stick | ❌ PENDIENTE | |
| M12 | Ejecutar slot radial | ❌ PENDIENTE | |
| M13 | Debug dump copiado al portapapeles | ❌ PENDIENTE | |
| M14 | Config persiste entre reinicios | ❌ PENDIENTE | |
| M15 | UI sounds al navegar | ❌ PENDIENTE | |
| M16 | Notificación de batería baja | ❌ PENDIENTE | |

### 3.2 Steam / Game Mode (SteamOS o Bazzite)

| # | Escenario | Estado | Notas |
|---|-----------|--------|-------|
| G01 | Steam detectado en Game Mode | ❌ BLOQUEADO | Requiere hardware SteamOS/Bazzite |
| G02 | Controladores visibles en ControllerSelectScreen | ❌ BLOQUEADO | |
| G03 | UI navegable sin mouse físico | ❌ BLOQUEADO | |
| G04 | `isGamescope = true` en debug dump | ❌ BLOQUEADO | |
| G05 | Enhanced Steam Deck driver flag sin crash | ❌ BLOQUEADO | |

### 3.3 Múltiples Mandos

| # | Escenario | Estado |
|---|-----------|--------|
| MU01 | Ambos detectados en lista | ❌ PENDIENTE |
| MU02 | Solo mando seleccionado dispara acciones | ❌ PENDIENTE |
| MU03 | Mando no seleccionado silenciado | ❌ PENDIENTE |
| MU04 | Cambiar selección en runtime | ❌ PENDIENTE |

### 3.4 Múltiples Instancias

| # | Escenario | Estado |
|---|-----------|--------|
| MI01 | Instancia 1 mando A, Instancia 2 mando B | ❌ PENDIENTE |
| MI02 | Mismo mando en ambas (limitación conocida) | ❌ PENDIENTE |

### 3.5 Gyro

| # | Escenario | Estado | Notas |
|---|-----------|--------|-------|
| GY01 | Gyro activado → mando mueve cámara | ❌ PENDIENTE | Requiere controlador con gyro |
| GY02 | Require button → gyro solo al mantener botón | ❌ PENDIENTE | |
| GY03 | Flick stick → snap de ángulo | ❌ PENDIENTE | Lógica implementada, sin verificar |

### 3.6 Vibración

| # | Escenario | Estado | Notas |
|---|-----------|--------|-------|
| VIB01 | Test vibration en Advanced tab | ❌ PENDIENTE | |
| VIB02 | Daño → vibración | ❌ PENDIENTE | Requiere hook de daño (no implementado aún) |
| VIB03 | Master intensity = 0 → sin vibración | ❌ PENDIENTE | |

**Limitación documentada:** ISteamController solo expone motores L/R, no trigger motors. Haptics avanzados (DualSense, Xbox HD) no disponibles con Steamworks4j 1.9.0.

### 3.7 Chords

| # | Escenario | Estado |
|---|-----------|--------|
| CH01 | Crear chord con mando en BindingsScreen | ❌ PENDIENTE |
| CH02 | Chord visible en lista como `[LB] + [A]` | ❌ PENDIENTE |
| CH03 | Chord dispara acción correcta | ❌ PENDIENTE |
| CH04 | Main solo no dispara acción de chord | ❌ PENDIENTE |
| CH05 | Cancelar captura limpia estado | ❌ PENDIENTE |

---

### RF-12 — Bugs de la auditoría de código (sesión 20, v0.10.6)

**Qué se corrigió (ver STATE.md/DECISIONS.md D035-D036 para detalle):**
1. `RadialMenuController.openSubmenu()` no reabría la rueda para un slot tipo SUBMENU activado por ON_CLICK o por A (press-to-activate).
2. `RadialRenderer` podía estilizar la rueda con el `RadialConfig` de un mando distinto al que realmente la muestra.
3. Tipo de slot radial no reconocido ahora loguea en vez de fallar en silencio.
4. `SteamPadClient.ensureFallbackBackendsInit()` podía quedarse permanentemente sin GamepadMappings/SDL3/ControllerClaimService/restore-de-config si el primer intento fallaba.
5. `JsonUtil.saveToFile()` ahora escribe atómico (no relacionado a gameplay, robustez de persistencia).

**Verificado en build (2026-07-09):**
```
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot
gradle -p C:\Dev\Steampad build --no-daemon
→ BUILD SUCCESSFUL in 14s, 24/24 tests PASSED
```

**Test en MC real (⚠️ PENDIENTE — ninguno de estos cambios altera comportamiento visible en el uso normal, solo casos borde):**
- **RM01 (radial submenu):** configurar un slot como tipo SUBMENU en el editor radial; en gameplay, abrir la rueda, seleccionar ese slot y activarlo con A (o con ON_CLICK) — la rueda debe reabrirse en vez de quedarse cerrada/sin reaccionar.
- **RM02 (radial config handle):** con 2 mandos conectados, cada uno con un `RadialConfig` distinto (radio/colores/nº de slots diferente), cambiar el mando activo mientras la rueda de edición (`RadialEditorScreen`) de OTRO mando está abierta — el preview debe seguir mostrando el estilo del mando que se está editando, no el recién activado.
- **RM03 (retry de backends):** difícil de forzar manualmente (requiere que GLFW/SDL3 fallen en el primer tick) — criterio pass es indirecto: si en algún log aparece `"Deferred backend init failed (retry N/10)"` seguido eventualmente de `"Fallback backends ready"`, el retry funcionó; si aparece solo el primer warning y nunca más "Fallback backends ready" ni error de "giving up", habría un problema.
- **RM04 (config atómico):** no observable en uso normal; criterio pass implícito es "nunca aparece un config `.json` corrupto/con JSON inválido tras un cierre anormal" (no hay forma de probar esto sin forzar un crash a mitad de escritura).

**Criterio pass general:** ninguna regresión en el uso normal del radial, selección de mando, o carga de config al iniciar — el checklist RM01-RM04 es para casos borde específicos, no gameplay básico.

---

### RF-13 — Lote v0.11.0 (sesión 21): teclado + pestañas + click muerto + radial + ZOOM

**Qué se cambió:** ver STATE.md sesión 21 y DECISIONS.md D037/D038. El checklist de validación en
hardware detallado (paso por paso, con los 2 escenarios reales del click muerto y el flujo completo
del zoom) vive en **B042** (TODO_BLOCKERS.md) — no se duplica aquí.

**Verificado en build (2026-07-09):**
```
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot
gradle -p C:\Dev\Steampad build --no-daemon
→ BUILD SUCCESSFUL in 18s, 24/24 tests PASSED, jar steampad-0.11.0.jar en dist/
```

**Puntos de riesgo específicos a vigilar en runtime (además del checklist B042):**
- El mixin nuevo `GameRendererMixin.getFov` — si Mixin no encuentra el target al arrancar, el mod
  entero aborta la carga (defaultRequire). La firma se verificó con javap contra el jar mapeado
  1.21.10, pero la confirmación definitiva es que MC ARRANQUE con el jar 0.11.0.
- El mixin nuevo en `Mouse.onMouseButton` (D037) — mismo riesgo de target; verificar arranque.
- `VirtualKeyboard.findTextField` hace un barrido recursivo del árbol de widgets por tick mientras
  hay pantalla — si algún mod tiene árboles de widgets gigantes, vigilar hitching (cap de
  profundidad 6 ya incluido).
- Zoom + spyglass vanilla simultáneos: ambos multiplican el FOV (spyglass vía fovMultiplier vanilla,
  nuestro zoom multiplica el resultado) — se acumulan; no es un bug, pero confirmar que se siente bien.

---

### RF-14 — Vibración AAA event-driven (sesión 22, v0.12.0)

**Qué se implementó:** ver STATE.md sesión 22 y DECISIONS.md D039. Checklist detallado de validación
en hardware (evento por evento) vive en **B043** (TODO_BLOCKERS.md) — no se duplica aquí.

**Verificado en build (2026-07-09):**
```
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot
gradle -p C:\Dev\Steampad build --no-daemon
→ BUILD SUCCESSFUL in 18s, 24/24 tests PASSED, jar steampad-0.12.0.jar en dist/
```

**Puntos de riesgo específicos (Tier 1):**
- 2 mixins nuevos (`ClientPlayNetworkHandlerMixin`, `ClientPlayerInteractionManagerMixin`) más la
  reactivación de `TitleScreenMixin` (dormido desde su creación, nunca antes cargado en MC real) —
  el riesgo real es si MC ARRANCA con el jar. Las firmas se verificaron con javap contra el
  jar mapeado 1.21.10, pero eso no reemplaza el arranque real.
- La heurística de "golpe crítico" es una aproximación local (condiciones de vanilla replicadas del
  lado cliente) — no garantiza que coincida con el roll real del servidor; es solo para el "feel",
  no afecta el daño real.
- El evento de explosión depende de que el paquete `ExplosionS2CPacket` llegue con
  `playerKnockback()` presente cuando corresponde — confirmar que el boost de intensidad se sienta
  correcto cuando una explosión te golpea directamente vs. cuando solo la escuchas de lejos.

**Actualización Tier 2 (v0.13.0) — ver DECISIONS.md D040/D041 y TODO_BLOCKERS.md B043 para el
checklist evento-por-evento completo, no se duplica aquí.** Puntos de riesgo adicionales:
- El árbitro de prioridad (`Tier`) es la pieza más nueva conceptualmente — validar específicamente
  que un evento CRITICAL/DANGER (daño, portal, creeper, Warden) nunca se sienta "tapado" por un
  evento COSMETIC trivial (romper un bloque común) que estuviera sonando en ese instante.
- El filtro de cofre-tesoro depende de tres señales combinadas (spawner cercano, punto de spawn del
  jugador, set de abiertos) — es el punto de mayor riesgo de falso-positivo/falso-negativo de toda
  la sesión; validar explícitamente el caso "cofre de mi propia base" (debe quedar en silencio).
- `ClientWorld.getSpawnPoint()` se confirmó por inspección de bytecode que refleja el respawn point
  REAL del jugador (vía `PlayerSpawnPositionS2CPacket`), no el spawn del mundo — pero esto nunca se
  ha visto correr en MC real; si el jugador nunca ha dormido en una cama, confirmar que el punto de
  spawn por defecto del mundo sigue siendo un límite de exclusión razonable (no debería pingear cerca
  del punto de aparición original tampoco).

---

### RF-15 — Fix de crash IncompatibleClassChangeError en Ajustes (sesión 23, v0.13.1)

**Contexto:** primera validación REAL en hardware del trabajo de las sesiones 21-22. El usuario probó
`steampad-0.13.0.jar` en Bazzite (Steam Deck, modpack de 80 mods) y el juego crasheó al entrar a
Ajustes de Minecraft. Diagnóstico y fix completos en DECISIONS.md D042 y TODO_BLOCKERS.md B044 — no
se duplican aquí.

**Verificado en build (2026-07-09):**
```
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot
gradle -p C:\Dev\Steampad build --no-daemon
→ BUILD SUCCESSFUL in 25s, 24/24 tests PASSED, jar steampad-0.13.1.jar en dist/
```

**Test en MC real (checklist detallado en B044):** entrar a Ajustes repetidamente sin crash; confirmar
que el icono junto a "Controls" se ve monocromo; confirmar que esto libera la validación pendiente de
B043 (Tier 1 + Tier 2 de vibración, bloqueada hasta ahora por este crash).

**Criterio pass:** el juego no crashea al abrir Ajustes, con o sin un widget de mod enfocado en la
pantalla. **Criterio fail:** cualquier crash con `IncompatibleClassChangeError` o similar al navegar
menús — indicaría otra clase con el mismo problema en algún mod del pack (el catch defensivo en
`isTextWidget()` debería evitar que llegue a crashear, pero solo cubre ESE punto de reflexión
específico, no otros lugares del código que pudieran hacer algo similar en el futuro).

---

### RF-16 — Lote de 4 fixes/features post-v0.13.2 (sesión 24, v0.14.0)

**Contexto:** el usuario probó v0.13.2 y pidió, en el mismo mensaje, 4 cosas: stick del teclado más
controlable + slider de velocidad, previo de color donde se elige el tema del teclado, un fix similar
a D037 pero reproducido dentro de Ajustes del gamepad, y los mismos temas de color del teclado en el
menú radial. Detalle de diseño en DECISIONS.md D044/D045, checklist completo de hardware en
TODO_BLOCKERS.md B045 — no se duplica aquí.

**Verificado en build (2026-07-09):**
```
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot
gradle -p C:\Dev\Steampad build --no-daemon
→ BUILD SUCCESSFUL in 22s, 24/24 tests PASSED, jar steampad-0.14.0.jar en dist/
```

**Pendiente de test en MC real (checklist detallado en B045):**
- Stick del teclado: a fondo se siente más lento/controlable que v0.13.2; el slider "Velocidad del
  stick" en Ajustes → Teclado cambia el comportamiento en vivo.
- Previo de tema del teclado: la franja de 3 teclas bajo el selector de tema cambia de color al ciclar.
- Fix del mouse-atorado en `BindingsScreen`: mouse fuera de la ventana estando en Ajustes del gamepad
  → recuperar el control del menú SOLO con el mando (B/Start) hasta volver al gameplay.
- Temas del radial: el control "Tema" en `RadialEditorScreen` cicla los 8 presets y la rueda de previo
  (ya existente) cambia de color al instante.

**Criterio pass:** los 4 puntos se comportan como se describe arriba, sin regresión en el resto del
teclado virtual, `BindingsScreen` o el editor radial. **Criterio fail:** el mouse-atorado reaparece
igual que antes (indicaría que la causa real es otra, no `captureMode` huérfano — ver la nota de
honestidad en D045), o el previo de color no coincide con el teclado/rueda real en juego.

---

### RF-17 — Lote de 8 fixes/features post-v0.14.0 (sesión 24 cont., v0.15.0)

**Contexto:** feedback de hardware sobre v0.14.0 — B043 (vibración) ✅ y B044 (crash) ✅ VALIDADOS por
el usuario; el mouse-atorado seguía y el stick del teclado aún se sentía rápido. Lote de 8 puntos:
causa raíz real del mouse-atorado (PauseGate, verificada en bytecode), stick v3 doble-zona+freno,
chat sobre el teclado, editor radial + Apariencia, cámara AAA, aim assist, glifos en vivo, entrada
mixta. Detalle en D046–D048; checklist de hardware en B046 — no se duplica aquí.

**Verificado en build (2026-07-09):**
```
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot
gradle -p C:\Dev\Steampad build --no-daemon
→ BUILD SUCCESSFUL in 25s, 24/24 tests PASSED, jar steampad-0.15.0.jar en dist/
```

**Criterio pass (resumen — detalle por punto en B046):** la cadena B→B→B con el mouse fuera llega
hasta el gameplay desde CUALQUIER pantalla hija del menú de pausa; el teclado escribe rápido pero
frena en la letra apuntada; los comandos del chat se ven encima del teclado; el editor radial ya no
confunde ruedas con espacios y la Apariencia previsualiza en vivo; la cámara se siente de consola
(precisión a media palanca + 180s al tope); el aim assist frena sobre mobs solo cargando proyectil;
los glifos reflejan los binds al instante; y teclado+mouse funcionan con el pad conectado sin romper
el manejo del mouse existente. **Criterio fail:** cualquier regresión de D8b (pausa espuria en
gameplay al perder foco), del click del mouse en menús (S5/B042), o del movimiento analógico del pad.

---

### RF-18 — Lote de 7 tras feedback de v0.15.0 (sesión 24 cont. 2, v0.16.0)

**Contexto:** v0.15.0 mayormente validada en hardware ("todo lo demás funciona bien, aún no detecto
bugs" — mouse-atorado/chat/radial/cámara AAA/glifos ✅). Reprobados: cámara de mouse en entrada mixta,
feel del teclado, aim assist imperceptible; más scroll+D-pad, reset de zoom, auditoría i18n y panel
más pequeño. Detalle en D049–D050; checklist de hardware en B047 — no se duplica aquí.

**Verificado en build (2026-07-09):**
```
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot
gradle -p C:\Dev\Steampad build --no-daemon
→ BUILD SUCCESSFUL in 30s, 24/24 tests PASSED, jar steampad-0.16.0.jar en dist/
```

**Verificado además en build (auditoría i18n):** diff automatizado de claves — 393/393/393 idénticas
en en_us/es_mx/es_es; todas las claves `Text.translatable` del código presentes; familias dinámicas
de enums (temas ×8, tipos radial ×6, stick_mode, sneak/sprint/gyro/yaw/require_button) completas.

**Criterio pass (resumen — detalle en B047):** la cámara del mouse físico gira en gameplay con el pad
conectado (incluso tras cerrar menús o perder/recuperar foco); el modo Apuntador del teclado se siente
rápido y preciso; el aim assist se percibe claramente sobre mobs/jugadores al cargar proyectil; el
D-pad tras scroll no regresa al inicio; el reset de zoom respeta su toggle; los 3 idiomas completos;
el panel de diagnóstico legible a 0.75×. **Criterio fail crítico:** cualquier regresión del manejo del
mouse en menús (S5/B042) o del fix D046 — las dos zonas que este lote toca de cerca.

---

### RF-19 — Lote de 4 tras feedback de v0.16.0, con análisis de Controlify (sesión 24 cont. 3, v0.17.0)

**Contexto:** v0.16.0 mayormente validada (scroll+D-pad, reset de zoom, i18n, panel ✅). Reprobados:
cámara de mouse (3ª iteración — ahora con el código real del repo de Controlify como referencia y un
warning de confirmación en el log), Apuntador no entendido (cableado verificado, explicación en B048,
sliders recentrados), aim assist imperceptible (v3: des-apilado + sqrt + caída de proyectil). Nuevo:
snap/D-pad sobre botones de mods. Detalle en D051–D052; checklist de hardware en B048.

**Verificado en build (2026-07-09):**
```
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot
gradle -p C:\Dev\Steampad build --no-daemon
→ BUILD SUCCESSFUL in 29s, 24/24 tests PASSED, jar steampad-0.17.0.jar en dist/
```

**Criterio pass (resumen — detalle en B048):** la cámara del mouse gira en gameplay (y si el log
muestra "Repaired a GLFW cursor-mode desync", el diagnóstico queda confirmado); los sliders del
teclado tienen su default al centro y la altura responde en todo el rango; el D-pad y el imán
alcanzan los botones de mods en inventarios; el frenado del aim assist se siente al entrar al área
del objetivo y el punto pegajoso compensa la caída a distancia. **Criterio fail crítico:** cámara de
mouse muerta SIN la línea de desync en el log (⇒ causa distinta, descartar este frente), o cualquier
regresión del cursor virtual en menús/inventarios (zona que D051 toca con su guard).

---

### RF-20 — Hallazgo Moonlight + lote de 4 (sesión 24 cont. 4, v0.18.0)

**Contexto:** la captura del usuario reveló el entorno real (Moonlight/Sunshine → ROG Ally/Bazzite).
Los "controles" fantasma eran uinput de Sunshine/gamescope; la cámara de mouse muerta apunta al
puntero ABSOLUTO de Sunshine (moriría igual en vanilla). Detalle en D053; checklist en B049.

**Verificado en build (2026-07-09):** BUILD SUCCESSFUL, 24/24 tests, jar `steampad-0.18.0.jar`.

**Test clave en hardware (B049):** las 4 verificaciones de entorno de la cámara (vanilla vía
Moonlight → toggle de mouse de Moonlight → Raw Input MC → mouse directo en la Ally); desconectar el
8BitDo ya no muestra passthrough/extest ni enloquece el cursor; comer/cofre/quemarse vibran; startup
es un tap corto. **Criterio fail:** un dispositivo REAL filtrado por error (nombre que contenga las
palabras vetadas), o la cámara muerta incluso con mouse directo en la Ally y sin línea de desync en
el log (⇒ reabrir el frente interno).

---

### RF-22 — Lote de 9 tras primera prueba real en hardware (sesión 28 cont. 2, v0.46.0)

**Contexto:** primera validación real en hardware de FASE 63 (emotes) y de Steam Input v0.43-0.45.
Checklist completo de validación vive en **B079** (TODO_BLOCKERS.md) — no se duplica aquí. Detalle
técnico completo en DECISIONS.md → D082.

**Verificado en build (2026-07-16):**
```
gradle -p C:\Dev\Steampad build --no-daemon
→ BUILD SUCCESSFUL, 29/29 tests PASSED, jar steampad-0.46.0.jar en dist/
```

**Puntos de riesgo específicos a vigilar en runtime (además del checklist B079):** el mixin nuevo
`ThirdPersonCameraMixin` (`@Inject TAIL` sobre `Camera.update()`) — si Mixin no encuentra el target al
arrancar, el mod entero aborta la carga; la firma se verificó con javap contra el jar mapeado 1.21.10,
pero la confirmación definitiva es que MC ARRANQUE con el jar 0.46.0. El sentido L/I/D del offset de
cámara no se pudo verificar sin mando físico (ver honestidad en D082) — riesgo puramente cosmético.

---

### RF-23 — Segunda ronda tras v0.46.0: deformación real + teclado ×6 + Third-Person v2 (sesión 28 cont. 3, v0.47.0)

**Contexto:** checklist completo de validación vive en **B080** (TODO_BLOCKERS.md) — no se duplica
aquí. Detalle técnico completo en DECISIONS.md → D083.

**Verificado en build (2026-07-16):**
```
gradle -p C:\Dev\Steampad build --no-daemon
→ BUILD SUCCESSFUL, 29/29 tests PASSED, jar steampad-0.47.0.jar en dist/
```

**Puntos de riesgo específicos a vigilar en runtime (además del checklist B080):** la caché estática
de rest-origin por `ModelPart` es el cambio más profundo de este lote — verificar específicamente el
caso de DOS jugadores visibles a la vez, uno emoteando y otro no, para confirmar que ya no hay
contagio de deformación entre ellos. El preview animado (`InventoryScreen.drawEntity`) usa una API
estable de Minecraft pero no se ha visto renderizar en hardware real todavía.

---

### RF-26 — Quinta ronda: `.emotecraft` con 21 archivos reales (1→12) + fix de ícono + fix de reconexión de mando (sesión 28 cont. 6, v0.50.0)

**Contexto:** checklist completo de validación vive en **B083** (TODO_BLOCKERS.md) — no se duplica
aquí. Detalle técnico completo en DECISIONS.md → D086.

**Verificado en build (2026-07-16):**
```
gradle build --no-daemon
→ BUILD SUCCESSFUL, 29/29 tests PASSED, jar steampad-0.50.0.jar en dist/
```

**Verificación adicional del parser binario `.emotecraft` (test temporal, borrado tras usar) —
ahora contra los 21 archivos reales acumulados del usuario (no solo 1 como en RF-25):** antes de
este round: 1/21 parseaba. Después: **12/21 parsean** (10 con las 6 partes reales completas, 2 con
5 de 6 — falta solo una extremidad, el resto de sus datos es correcto). Los 9 restantes: 8 son el
sub-formato "versión 1" ya documentado (B082/B083), 1 ("Friendship Round Dance.emotecraft") es una
variante estructural dentro de "versión 2" sin evidencia suficiente para decodificar con confianza
— ver B083 para el detalle byte a byte. El ícono embebido se extrajo correctamente en el 100% de
los 21 archivos, sin importar si la animación pudo decodificarse.

**Puntos de riesgo específicos a vigilar en runtime (además del checklist B083):** el fix de
reconexión de mando (`ConfigManager.migrateControllerConfigByName`) es el más importante de validar
en hardware real — requiere desconectar/reconectar un mando físico durante gameplay, algo que no se
puede simular sin hardware. El mecanismo nunca sobrescribe una config existente (solo copia si el
handle nuevo no tiene ningún archivo propio todavía), así que en el peor caso de un fallo debería
degradar a "config en blanco como antes", no a corromper datos existentes — pero esto es una
suposición de diseño, no algo verificado en juego real.

---

### RF-25 — Cuarta ronda: deformación real #4 (pose agachado) + fix de regresión crítica + `.emotecraft` binario real (parcial) + íconos (sesión 28 cont. 5, v0.49.0)

**Contexto:** checklist completo de validación vive en **B082** (TODO_BLOCKERS.md) — no se duplica
aquí. Detalle técnico completo en DECISIONS.md → D085.

**Verificado en build (2026-07-16):**
```
gradle build --no-daemon
→ BUILD SUCCESSFUL, 29/29 tests PASSED, jar steampad-0.49.0.jar en dist/
```

**Verificación adicional del parser binario `.emotecraft` (test temporal, borrado tras usar):**
contra los 3 archivos `.emotecraft` reales adjuntados por el usuario — "The Honored One
(levitation).emotecraft" (formato versión 2) parseó 6 de 7 partes conocidas con valores físicamente
sensatos y simétricos (brazo/pierna izquierdo y derecho son reflejo exacto uno del otro, esperable en
una pose de levitación); "Dance Moves.emotecraft" y "Sit Adorably.emotecraft" (formato versión 1)
devolvieron `null` de forma limpia — sin excepción, con log explicando por qué — el comportamiento
honesto y seguro por diseño en vez de arriesgar una animación mal decodificada. El ícono embebido
(PNG) se extrajo correctamente de los 3 archivos, sin importar la versión.

**Puntos de riesgo específicos a vigilar en runtime (además del checklist B082):** el fix de
deformación #4 depende de que ALGUIEN se haya agachado cerca de una emote en sesiones anteriores para
que el bug se manifestara — si el usuario nunca probó eso, es posible que #1-#3 (D082-D084) ya
hubieran resuelto todo lo que él veía y #4 sea una corrección preventiva sin síntoma visible previo;
de cualquier forma vale la pena confirmar que sigue sin deformarse. El fix de la regresión crítica
(token de generación) es mecánicamente distinto a cualquier cosa ya probada en este proyecto —
vigilar de cerca que cerrar la rueda/Biblioteca en distintos órdenes (con y sin preview activo, con y
sin emote real corriendo) nunca deje un emote real cancelado a medias ni un preview huérfano
reproduciéndose tras cerrar.

---

### RF-24 — Tercera ronda: deformación real #3 (torso-relativo) + transición de cámara + multi-rueda (sesión 28 cont. 4, v0.48.0)

**Contexto:** checklist completo de validación vive en **B081** (TODO_BLOCKERS.md) — no se duplica
aquí. Detalle técnico completo en DECISIONS.md → D084.

**Verificado en build (2026-07-16):**
```
gradle build --no-daemon
→ BUILD SUCCESSFUL, 29/29 tests PASSED, jar steampad-0.48.0.jar en dist/
```

**Puntos de riesgo específicos a vigilar en runtime (además del checklist B081):** verificar
específicamente emotes con MOVIMIENTO DE TORSO pronunciado (giros, inclinaciones) — es exactamente el
caso que la rotación de offsets corrige; un emote que solo mueve brazos/piernas sin tocar el torso no
habría mostrado el bug de todos modos, así que no es una prueba tan reveladora. La regresión más
importante a confirmar: que el conteo de espacios de las ruedas RADIALES regulares del usuario no
cambió (bug de `setSlotCountFor`/`setSlotCountForEmote` encontrado y corregido antes de compilar, sin
poder confirmar en hardware que el fix realmente resuelve el síntoma).

---

## 4. Criterios de Aceptación

Para considerar el mod **MVP jugable** (siguiente milestone real):
- [ ] M01, M02, M03, M04, M05, M06, M07 verificados
- [ ] IT-01 a IT-09 verificados
- [ ] VIB01 verificado
- [ ] CH03, CH04 verificados

Para considerar el mod **release candidate**:
- [ ] Todos los criterios de MVP
- [ ] M08 a M16 verificados
- [ ] G01 a G04 verificados (si hardware disponible)
- [ ] MU01 a MU04 verificados
- [ ] GY01 verificado
- [ ] 0 bugs críticos sin documentar
- [ ] README de instalación/uso completo
- [ ] VDF de Steam Input incluido y documentado
