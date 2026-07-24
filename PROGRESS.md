# PROGRESS.md — Sesión 29 cont. 8 (v0.61.0 — autocorrección de pivote + investigación de cadera + chip +30% + Debug Dump)

## SESIÓN 29 cont. 8 — parte 4 (2026-07-18) — v0.61.0 autocorrección + investigación (D101, B095, FASE 79)

El usuario probó v0.60.0 y reportó que la cadera sigue sin verse bien (se sienta en el aire en vez
de tocar el piso) y que algunas animaciones deberían tener las piernas más quietas pero se mueven
"como el de la Macarena". Pidió otro 30% de tamaño en el chip de la Rueda y un Debug Dump aún más
detallado "para todo el mod, hasta lo más mínimo".

- [x] **Autocorrección real:** al releer con más cuidado la fuente MIT ya cacheada
  (`PlayerModelMixin.setDefaultPivot()`), se confirmó que el fix de pivote de piernas de v0.60.0
  (Z 0.1→0.0) estaba mal fundamentado — la propia librería de animación fuerza 0.1 como su propio
  baseline antes de posar cada frame, no el pivote crudo de vanilla (0.0). Revertido a 0.1.
- [x] **Investigación honesta de "cadera bloqueada/piernas como Macarena":** verificadas
  directamente las curvas reales de "Sit Adorably" con el mismo método público que usa el render —
  el torso apenas se mueve, las piernas logran la pose "sentada" por rotación estable, sin evidencia
  de mezcla de datos. Hipótesis más plausible (sin cierre 100%): limitación inherente de emotes de
  solo-pose que no mueven la posición real del personaje. Se necesita el nombre exacto del emote
  para seguir investigando "piernas como Macarena".
- [x] **Chip de la Rueda +30% adicional:** 1.45/1.10 → 1.885/1.43.
- [x] **Debug Dump ampliado:** Rueda/Biblioteca abiertas + sección "Player Movement" nueva (input,
  sprinting/sneaking/onGround, velocidad).
- [x] Los 21 archivos reales re-verificados tras el revert: 21/21.
- [x] `mod_version` → 0.61.0; build limpio; jar en `dist/steampad-0.61.0.jar`.
- [x] Documentación: D101, B095 (+B094 actualizado), FASE 79, STATE.md, PROGRESS.md, CHECKLIST.html,
  memoria del proyecto.
- [ ] Validación visual en hardware + nombre específico del emote afectado (checklist en B095).

## SESIÓN 29 cont. 8 — parte 3 (2026-07-18) — v0.60.0 ajustes finos + cámara libre (D100, B094, FASE 78)

El usuario probó v0.59.0 y confirmó el mayor avance del histórico de emotes: deformación "casi
resuelta... se ve mucho mejor", los 21 archivos cargan, preview congelado por celda/chip "quedó
perfecto", foco+animación "quedó perfecto". Reportó 2 detalles finos + pidió 3 cosas nuevas.

- [x] **Cadera:** verificado por `javap` sobre el jar mapeado 1.21.10 del propio proyecto — el
  pivote real de piernas es Z=0.0, no 0.1 como estaba hardcodeado. Cabeza/torso/brazos
  re-verificados contra el mismo desensamblado — correctos, sin cambios.
- [x] **Rotación "bloqueada":** descartada primero la hipótesis de propagación torso→hijos (bytecode
  confirma que son hermanos, no un árbol padre-hijo). Causa real encontrada con datos reales:
  "Friendship Round Dance.emotecraft" (MineEmotes) declara transformación REAL en AMBOS "torso" y
  "body" — colisión de keyframes al fusionar. Fix: body gana la colisión, ejes exclusivos de torso
  se siguen aplicando. Test nuevo reproduce el patrón exacto.
- [x] **Foco más grande:** Rueda 1.05/0.80→1.45/1.10; Biblioteca 52/19→76/27.
- [x] **Cámara libre durante el emote** (pedido nuevo): reutiliza el sistema de cámara libre
  existente (v0.52.0) — `isFreeLookEnabled()` ahora también true durante un emote REAL (nuevo flag
  `Playback.isRealEmote` + `isLocalRealEmotePlaying()`, evita activarse solo por navegar
  Biblioteca/Rueda); `tickRotateStrategy` se salta para que el cuerpo no gire hacia la cámara.
- [x] **Volcado de pose detallado** (pedido nuevo): nueva sección "Local pose" en el Debug Dump,
  por parte/eje, siempre fresca (no atada al throttle de 2s del log).
- [x] Los 21 archivos reales re-verificados contra el parser Java real tras todos los fixes: 21/21.
- [x] `mod_version` → 0.60.0; build limpio; jar en `dist/steampad-0.60.0.jar`.
- [x] Documentación: D100, B094 (+B093 marcado confirmado), FASE 78, STATE.md, PROGRESS.md,
  CHECKLIST.html, memoria del proyecto.
- [ ] Validación visual en hardware (checklist concreto en B094).

## SESIÓN 29 cont. 8 — parte 2 (2026-07-18) — v0.59.0 "Emotes perfectos" (D099, B093, FASE 77)

El usuario ordenó leer COMPLETAS las fuentes de Emotecraft ("deja de suponer") y adjuntó sus 21
archivos `.emotecraft` reales. Ronda de programación completa sobre el subsistema de emotes.

- [x] **Fuentes reales leídas** (cambio de procedencia documentado en D099): playerAnimator (MIT —
  portado con atribución: KeyframeAnimation/Player, AnimationApplier, AnimationBinary, AnimationJson,
  Ease/Easing) + emotes (GPL — SOLO hechos de formato de contenedor, por orden explícita del dueño).
- [x] **Causa raíz definitiva de la deformación (6 rondas):** valores = PIVOTES ABSOLUTOS en espacio
  vanilla, asignados directo — el motor viejo los sumaba al reposo (offset duplicado) + flip de Y +
  matriz de torso. Confirmado por fuente Y por aritmética sobre el log del usuario (distFromRest ≡
  |pivote| constante). D082–D087 eran compensaciones: eliminadas.
- [x] **Reescritos:** `EmoteData` (canales por eje, sampler port fiel con keyframes virtuales de
  borde contra el valor vanilla vivo, loop inclusivo), `Easing` (matemática real de KosmX + tabla de
  IDs binarios + fromId), `EmoteAnimator.apply` (asignación absoluta por eje), `EmoteCraftBinaryParser`
  (spec completo v1–v4: 0x00/0x11/0x12, tolerancia -1, merge body+torso, metadatos e icono reales),
  `EmoteJsonParser` (degrees default TRUE, turn como 2º keyframe, easingArg, easeBeforeKeyframe).
- [x] **Verificación empírica:** verificador Node 21/21 sobre los archivos reales → luego el PARSER
  JAVA REAL corrido standalone contra los mismos 21: **21/21 OK, 21 iconos**, metadatos correctos.
- [x] **Previews (FASE 77):** miniaturas congeladas POR CELDA/CHIP con el personaje (duck interface
  `EmotePreviewState` + `EmotePreviewTagger` + mixins `PlayerEntityRenderStateMixin`/
  `PlayerEntityRendererMixin`, firmas por javap) — compatible con el render diferido de D092; foco
  MÁS GRANDE y animando en vivo; park/restore del playback real al abrir/cerrar Biblioteca y Rueda
  (resuelve B092: el loop del usuario bloqueaba los previews); relanzamiento tras prune.
- [x] **Tests:** 4 nuevos/reescritos (contrato real de muestreo, defaults absolutos, tabla de IDs,
  contenedor binario sintético). Suite completa en verde.
- [x] `mod_version` → 0.59.0; build limpio; jar en `dist/steampad-0.59.0.jar`.
- [x] Documentación: D099, B093 (+B092 cerrado), FASE 77, STATE.md, PROGRESS.md, CHECKLIST.html.
- [ ] Validación visual en hardware (checklist concreto en B093).

## SESIÓN 29 cont. 8 — parte 1 (2026-07-17) — Confirmación de hardware del fix de lag (con una condición sin explicar) + nueva regresión de previos de emotes

Sesión de SOLO DOCUMENTACIÓN — el usuario pidió explícitamente "no programes, solo documenta". Sin
cambios de código, sin bump de versión (sigue en v0.58.0).

- [x] **B091/D098 (lag del mouse virtual) — CONFIRMADO CORREGIDO por el usuario**, con una condición
  inicial anotada: la PRIMERA vez que abrió el juego con el jar v0.58.0 el lag seguía apareciendo;
  apagó y volvió a encender el 8BitDo y a partir de ahí (incluyendo reinicios posteriores del juego
  con el control ya conectado) funcionó correctamente. Post-mortem agregado a D098 con 2 candidatos
  SIN evidencia de código todavía (pad abierto antes del mapeo SDL3 / renegociación de Steam Input) —
  anotado como observación de campo, no como causa confirmada, para no adivinar sin haber leído nada.
- [x] **Nueva regresión reportada — previos "AAA" de emotes volvieron al ícono plano** (no la
  deformación, que sigue abierta por separado — el usuario mismo distingue ambos problemas).
  Documentada como B092 con las preguntas exactas que hacen falta ANTES de tocar código: qué pantalla
  (Biblioteca/Rueda/ambas), si nunca anima nada o si anima pero sale mal, un log buscando presencia/
  ausencia de líneas `[emote-pose]`, y qué versión era "la que sí funcionaba".
- [x] Documentado en DECISIONS.md (post-mortem en D098), TODO_BLOCKERS.md (B091 actualizado a
  CONFIRMADO, nuevo B092), STATE.md, CHECKLIST.html — sin tocar ningún archivo `.java` ni `gradle.properties`.

## SESIÓN 29 cont. 7 (2026-07-17) — "DEJA DE ADIVINAR" → mecanismo del lag rastreado línea por línea + volcado súper completo

v0.57.0 se probó en hardware: el cambio de backend FUNCIONÓ (log del usuario: 8BitDo por SDL3 con
paddles P1-P4 por primera vez, mappings "4 added, 2 updated, 0 failed") — pero el lag PERSISTIÓ
idéntico. El backend nunca fue la causa; post-mortem honesto agregado a D097. El usuario exigió
"DEJA DE ADIVINAR, lee codigo, dime como te puedo ayudar", pasó volcado + log de v0.57.0, aportó el
dato clave ("Pasa con el mouse virtual y cuando se conecta el 8bitdo") y pidió el volcado de debug
expandido a TODO el mod.

- [x] **Leída la cadena COMPLETA del mouse virtual de punta a punta** (`VirtualMouseController`,
  `MouseMixin`, `InputRouter`, call sites del dispatcher, `VirtualCursorRenderer`) — sin subagentes,
  lectura directa.
- [x] **Mecanismo del lag rastreado línea por línea (hecho del código, no hipótesis):**
  1. Stick empujado → `onStickUsed()` + `setStick()` cada tick (`GamepadInputDispatcher` ~923-925).
  2. Movimiento de mouse EXTERNO correlacionado (candidato #1: Steam Input desktop layout emulando
     mouse desde el MISMO 8BitDo — "Steam Virtual Gamepad" en el volcado del usuario lo delata; el
     Ally no sufre porque Steam lo consume completo, un solo flujo).
  3. Barridos >20px/evento → `MouseMixin:49 markMouseForce()` (salta la ventana de protección a
     propósito) → `:53 onPhysicalMouseTookOver()` → cursor ESCONDIDO.
  4. ≤50ms después → `onStickUsed()` → re-mostrado TELETRANSPORTADO al puntero del OS
     (`syncFromOsMouse`, `VirtualMouseController:120`) + foco borrado.
  5. Repetido varias veces/segundo = "lag terrible, sin control" — explica CADA rasgo del reporte
     (solo vmouse, solo 8BitDo, al conectarlo, inmune a los cambios de backend anteriores).
- [x] **Fix — árbitro en `MouseMixin`:** mientras el stick dirige el cursor activamente, el
  movimiento externo se traga (+cuenta +loggea). Stick quieto → mouse físico igual que siempre;
  soltar stick → mouse recupera en ≤1 tick; click físico gana siempre; gameplay intacto.
- [x] **`MouseEventStats` (nueva):** contadores por ventana + totales por cada camino de evento;
  log `[mouse-arb]` throttled con la instrucción de arreglo de raíz (desactivar Steam Input para
  ese pad); banner "DOUBLE INPUT DETECTED" en el volcado.
- [x] **Volcado de debug expandido a TODO el mod:** versión; Backends & Mappings; etiqueta
  @SDL3/@GLFW/@STEAM decodificada por handle en cada mando (el decode que destapó D097, ahora
  automático); warning de doble claim; Input Flow completo; Performance (TickProfiler SIEMPRE
  capturado + timer de cámara libre); Active Controller Config; Global Feature State; Emotes.
- [x] `mod_version` → 0.58.0. Build + 29/29 tests → `dist/steampad-0.58.0.jar`.
- [ ] Validación en hardware (B091). Prueba discriminante: cerrar Steam por completo → si el lag
  desaparece y `[mouse-arb]` se va del log, fuente confirmada al 100%.

## SESIÓN 29 cont. 6 (2026-07-17) — Log real de hardware → causa raíz del lag 8BitDo + gap de limpieza en el failover

El usuario pasó un log completo de una sesión real (ROG Ally + 8BitDo, Bazzite/gamescope/Sunshine) con
dos reportes: lag del mouse/cámara específico del joystick del 8BitDo (no del ROG Ally), y botones mal
configurados en el ROG Ally tras un failover automático desde el 8BitDo desconectado (arreglado solo
reiniciando el juego). Exigencia explícita: "investiga no adivines, lee codigo... siempre necesito
veracidad y comprobaciones". Un primer intento de usar el Workflow tool con 4 agentes de investigación
en paralelo + verificación adversarial FALLÓ POR COMPLETO (límite de sesión en los 5 agentes) — toda la
investigación de esta ronda se hizo leyendo el código directamente, sin subagentes.

- [x] **Descartada una hipótesis por lectura directa:** `GamepadMappings.java` usa
  `glfwUpdateGamepadMappings`, confirmado por GLFW como acotado por GUID de dispositivo — no podía
  "filtrar" el layout del 8BitDo hacia el ROG Ally por sí solo.
- [x] **Causa raíz REAL del lag, confirmada por aritmética exacta (Node + BigInt) sobre los handles
  reales del log, no supuesta:** el ROG Ally llevaba la etiqueta de bits "SDL3"
  (`Sdl3GamepadProvider.SDL3_HANDLE_BASE`), pero los dos handles del 8BitDo decodificaban a ASCII
  "GLFW". El conteo crudo `SDL3=1` se mantuvo fijo durante TODA la sesión — el 8BitDo nunca fue visible
  para SDL3 ni un instante, cayendo siempre al camino de respaldo GLFW (`GlfwSnapshotSource`, polling
  de joystick genérico) en vez del camino SDL3 (`Sdl3GamepadProvider`, HIDAPI-capaz, optimizado
  activamente) — a pesar del hint `SDL_JOYSTICK_HIDAPI_8BITDO` ya activado específicamente para él.
- [x] **Causa raíz de por qué el 8BitDo nunca era visible para SDL3:** `GamepadMappings.loadAll()`
  únicamente llamaba `glfwUpdateGamepadMappings` — nunca enseñaba las mismas líneas de mapeo a
  libSDL3. `SDL_GetGamepads()` solo enumera dispositivos que la propia base de mapeos de SDL YA
  reconoce como gamepad; sin una entrada válida para ese 8BitDo en ese modo, era invisible para SDL3,
  y `ControllerManager` lo recogía de GLFW en su lugar, en silencio (sin error ni advertencia).
- [x] **Fix:** `Sdl3Native` gana `SDL_AddGamepadMapping(String)` (contraparte real de SDL3 a
  `glfwUpdateGamepadMappings`, una línea a la vez). Nuevo `Sdl3GamepadProvider.loadMappings(content)`
  parsea el mismo contenido multi-línea ya usado para GLFW y registra cada línea. `GamepadMappings.apply()`
  ahora enseña a AMBOS backends con el mismo contenido — una sola fuente de verdad. Reordenada la
  inicialización en `SteamPadClient` (`Sdl3GamepadProvider.init()` antes de `GamepadMappings.loadAll()`,
  ya que hace falta libSDL3 cargado para poder registrarle mappings).
- [x] **Gap real de limpieza al fallar sobre otro control, encontrado en el propio código (no en el
  log):** rastreado el ciclo completo del cambio de control activo. `ConfigManager`/
  `ActiveControllerService`/`GamepadBinds` están correctamente aislados por handle — descartados tras
  lectura completa. El gap real: `SteamPadClient` detecta la desconexión y auto-activa un control
  DISTINTO, todo dentro del MISMO tick, ANTES de que `GamepadInputDispatcher.tick()` vuelva a correr —
  su única limpieza de "controlador desaparecido" (libera ataque/usar/lista de jugadores, keybinds de
  mod sostenidos vía `heldExtras`, desactiva zoom) solo se dispara si `tick()` se llama de nuevo con el
  MISMO handle desconectado, cosa que con el failover automático nunca vuelve a pasar.
- [x] **Fix:** nuevo `GamepadInputDispatcher.releaseAllHeldStateOnControllerLoss()` (público), llamado
  desde `SteamPadClient` en el punto exacto de detección de desconexión, antes de auto-activar el
  reemplazo. Incluye logging de diagnóstico (solo si algo realmente estaba sostenido).
- [x] `mod_version` → 0.57.0. Build + 29/29 tests → `dist/steampad-0.57.0.jar`, compiló limpio a la
  primera en cada paso.
- [ ] Validación en hardware — pendiente. El gap de limpieza no se pudo confirmar al 100% como la ÚNICA
  causa del reporte original — el logging nuevo (`[controller-loss]` en `latest.log`) dirá en la
  próxima prueba si cierra el problema o si queda algo más por encontrar.

## SESIÓN 29 cont. 5 (2026-07-17) — Reporte de validación más detallado hasta ahora (36/59 OK) → lote grande de fixes reales

El usuario cambió a Sonnet 5 a mitad de sesión y pegó un reporte de validación mucho más detallado
que los anteriores, con notas específicas por ítem fallido. Instrucción explícita: resolver TODO,
sin detenerse a confirmar entre tareas, con foco especial en Tercera Persona ("el que más bugs
tiene, peor funciona") y la deformación de emotes (prioridad #1, sexta ronda reportada). Detalle
completo: DECISIONS.md D092-D096, TODO_BLOCKERS.md B089, TASKS.md FASE 74.

- [x] **Previos de emotes — causa raíz REAL de "se reproducen todos"** (Rueda + Biblioteca),
  confirmada por `javap` sobre el jar mapeado 1.21.10: `InventoryScreen.drawEntity` ENCOLA su dibujo
  (`DrawContext.addEntity`) en vez de renderizarlo de inmediato; el posado real (`setAngles`) ocurre
  después, en un flush que lee `EmoteAnimator.playing` por ID DE ENTIDAD — un mapa global compartido
  por todas las casillas encoladas para el mismo jugador. La técnica de "posa-y-dibuja secuencial"
  de FASE 72/73 asumía renderizado inmediato; era la premisa equivocada. Fix: eliminado el posado
  secuencial — solo la casilla/fila enfocada dibuja una entidad 3D en vivo, el resto usa su ícono
  plano normal (`EmoteIconProvider`). Sin colas compitiendo por el mismo ID, el conflicto desaparece
  por construcción.
- [x] **Biblioteca de Emotes rediseñada como cuadrícula** (pedido explícito: "vamos a hacerle
  cuadrícula") — celdas de 68px, columnas automáticas según ancho, mismo criterio de "solo la celda
  con foco/hover anima" que la Rueda.
- [x] **Tercera Persona — 3 bugs reales confirmados, no supuestos:**
  1. *"Se ve como primera persona, alguien invisible":* el mixin de free-look cancelaba
     `Camera.update()` completo en HEAD. Decompilado el bytecode real: las primeras instrucciones del
     método son `this.ready = true` y `this.thirdPerson = <parámetro>` — el campo exacto que
     `isThirdPerson()` expone y que vanilla consulta para decidir si dibuja el propio cuerpo del
     jugador. Cancelar en HEAD significa que esa asignación NUNCA corre mientras el free-look esté
     activo. Fix: TAIL en vez de HEAD-cancelable (igual que el hook de offset, que siempre fue
     correcto) — vanilla corre completo, luego se sobreescribe posición/rotación.
  2. *Izquierda/Derecha invertidos:* el vector "derecha" (`cos(yaw), sin(yaw)`) usado por el offset de
     hombro y el ajuste lateral de cámara libre resultó ser el vector IZQUIERDA — confirmado por 3
     métodos independientes (geometría cardinal a yaw=0 y yaw=90, más la regla de la mano derecha
     `adelante × arriba`). Fix: helper compartido `rightVectorXZ()` con el signo corregido. Verificado
     que esto NO aplica a `applyCameraRelativeMovement` (misma forma de fórmula, pero esa SÍ coincide
     byte-a-byte con `Entity.movementInputToVelocity` real de vanilla — preguntas distintas).
  3. *Movimiento relativo a cámara "no funciona bien":* el cuerpo giraba y caminaba en la dirección
     correcta, pero los booleans de `PlayerInput` (sprint/animación) seguían leyendo el input crudo
     pre-remapeo en vez del par efectivo `(0, magnitud)` — fix en `KeyboardInputMixin`.
  4. *Rendimiento:* revisado el costo por frame sin encontrar una causa algorítmica obvia (mismo
     perfil que la cámara de tercera persona de vanilla). Se agregó un temporizador con volcado cada
     ~2s (min/avg/max ms) en vez de una "optimización" sin evidencia.
- [x] **`SlotSnap`/Traveler's Backpack — revert + solución real:** el radio angosto de casillas (8px,
  de una ronda anterior) rompía la sensación del inventario GENERAL con el cursor virtual (confirmado
  que el D-pad nunca lo usó — coincide con el reporte "con el DPAD no pasa, solo con el mouse
  virtual"). Radio de casillas vuelto a 22px (igual que los widgets); el problema ORIGINAL (botón de
  mod perdiendo contra casilla vecina) resuelto con puntuación normalizada por radio + prioridad para
  widgets — arregla ambos lados sin sacrificar uno por el otro.
- [x] **Onboarding nunca se disparaba — causa raíz real:** estaba anidado DENTRO de la
  re-verificación de "¿sigue siendo el mismo handle 750ms después?" de la vibración de inicio — pero
  `startupRumbleDone` se marca `true` sin importar si esa verificación pasa, así que un cambio de
  handle en esa ventana (riesgo real en el setup de streaming del usuario) saltaba AMBOS en silencio
  para siempre. Desacoplados — onboarding ahora consulta el handle activo actual de forma
  independiente.
- [x] **Diagnóstico (no fix a ciegas) para 2 bugs persistentes de varias rondas:** haptics de arma
  cuerpo a cuerpo (mixin/lógica revisados sin encontrar bug — log sin throttle por golpe) y
  deformación de emotes (volcado completo de pose por parte animada, throttled a 1/2s).
- [x] **Slime:** nueva pulsación suave en reposo (0.16/180ms), respondiendo a la descripción original
  del usuario ("como estar en algo pegajoso") — antes solo pulsaba caminando.
- [x] **Jugosidad:** boost global 1.2× tras confirmación de que ya funciona y solo necesita un poco más.
- [x] `mod_version` → 0.56.0. Build + 29/29 tests, compiló limpio a la primera en cada paso.
- [ ] Validación en hardware — pendiente. Los 3 diagnósticos puros necesitan el LOG REAL de la
  próxima prueba (`[emote-pose]`, `[haptics-melee]`, `[thirdperson-perf]`, `Startup rumble SKIPPED`).

## SESIÓN 29 cont. 4 (2026-07-17) — Preview "AAA" extendido a la Rueda de Emotes durante gameplay

El usuario preguntó directamente si la ronda anterior (v0.54.0, solo Biblioteca) había tocado la
Rueda de Emotes en pleno juego. Se le respondió que no, con la razón (acotado a propósito). Pidió
implementarlo ahí también, "de la mejor forma".

- [x] **Reutilizada la geometría existente, no duplicada:** en vez de recalcular la posición de cada
  casilla en otro archivo (arriesgando desincronizarse del blob de gelatina/carrusel, que son estado
  animado compartido dentro de `RadialRenderer`), se agregó un hook opcional
  (`RadialRenderer.SlotThumbnailRenderer`) invocado DESDE DENTRO del bucle de dibujo existente, con la
  posición/radio de cada casilla ya calculados.
- [x] **Confirmado cero regresión:** los 3 llamadores existentes de `RadialRenderer.render()` (menú
  radial normal, `RadialEditorScreen`, `RadialStyleScreen`) siguen usando la firma de 7 argumentos sin
  el hook nuevo — verificado leyendo cada uno antes de dar por cerrado el cambio.
- [x] **Mismo mecanismo de FASE 72** (pose-y-dibuja secuencial + snapshot/restore de `EmoteAnimator`),
  aplicado ahora a casillas en círculo: cada casilla no vacía muestra al jugador posado en un frame
  fijo de su emote; la seleccionada reproduce el baile completo en tiempo real.
- [x] **Panel lateral fijo eliminado** de `EmoteWheelOverlay` — el nombre del emote seleccionado lo
  sigue mostrando el propio renderizador del radial en el centro de la rueda, sin cambios ahí.
- [x] **Editor de la Rueda de Emotes (pantalla de pausa) sin tocar** — el pedido fue específicamente
  sobre la rueda EN GAMEPLAY.
- [x] `mod_version` → 0.55.0. Build + 29/29 tests → `dist/steampad-0.55.0.jar`, compiló limpio a la
  primera.
- [x] Backlog de `TASKS.md`/`CHECKLIST.html` actualizado — retirado el ítem ya implementado.
- [ ] **Nada de esto se ha probado en hardware.**

**Lección de proceso:** cuando una funcionalidad ya construida para un contexto (la Biblioteca) se
pide extender a otro (la rueda en gameplay), vale la pena revisar primero si la geometría/posición ya
vive en algún lado reutilizable ANTES de reimplementarla — evitó duplicar matemática de círculo y
mantuvo cero riesgo de regresión en los otros 3 usos de `RadialRenderer`.

---

## SESIÓN 29 cont. 3 (2026-07-17) — Preview "AAA" de la Biblioteca de Emotes, retomando una idea del backlog

El usuario retomó una idea que había quedado deliberadamente en el backlog varias sesiones atrás:
reemplazar el panel lateral fijo de vista previa de la Biblioteca de Emotes por un thumbnail POR FILA
(frame fijo del baile), que cobra vida solo cuando esa fila tiene el foco. Antes de programar, se le
pidió confirmar dos detalles de diseño — confirmó ambos exactamente como se había entendido — y
también preguntó (sin estar seguro) si la Rueda de Emotes comparte la vibración del menú radial.

- [x] **Confirmación de diseño antes de programar** (pedido explícito del usuario, sin código en esa
  respuesta): disparador = solo foco/selección (sin necesidad de A); al mover el foco, la fila
  anterior se congela de vuelta a su frame fijo.
- [x] **Resuelto el obstáculo técnico real:** Minecraft comparte una sola instancia de modelo entre
  todos los jugadores — solo una pose puede estar "viva" a la vez. Solución: nuevo mecanismo de "pose
  de un solo cuadro" (`EmoteAnimator.applyPinnedFrame`) que posa y dibuja una fila a la vez,
  secuencialmente, dentro del mismo frame — visualmente indistinguible de "cada foto es
  independiente" aunque técnicamente comparten la misma instancia física.
- [x] **Protección de la reproducción real:** nuevo par `snapshotPlayback`/`restorePlayback` guarda
  cualquier emote real que el jugador dispare con "▶" antes del lote de poses fijas y lo restaura
  después — mismo espíritu de protección que el token de generación de D080/D081 ya usaba.
- [x] **Frame representativo:** heurístico documentado (35% entre inicio y fin de la animación), no
  una curación manual por emote — no hay forma general de saber cuál es "el mejor" cuadro de un baile
  arbitrario, incluidos los subidos por la comunidad.
- [x] **Alcance acotado a la Biblioteca**, tal como se pidió — editor de rueda y overlay en juego
  siguen con el panel fijo, sin tocar.
- [x] **Confirmado sin cambios:** la Rueda de Emotes ya comparte la vibración de selección del radial
  normal — verificado leyendo el código (`EmoteWheelController.updateAnalog`), no hacía falta fix.
- [x] `mod_version` → 0.54.0. Build + 29/29 tests → `dist/steampad-0.54.0.jar`, compiló limpio a la
  primera.
- [x] **`CHECKLIST.html` ampliado** con un panel nuevo de solo lectura ("🔧 Pendiente de implementar")
  que espeja el backlog de TASKS.md — pedido explícito del usuario para poder "entrar en contexto"
  del estado del proyecto sin abrir varios archivos.
- [ ] **Nada de esto se ha probado en hardware** — ni siquiera se ha podido VER renderizado (no hay
  forma de tomar una captura de Minecraft desde este entorno), así que el encuadre/escala de la
  miniatura es un valor elegido a mano.

**Lección de proceso:** cuando el usuario pide confirmar el entendimiento de una idea antes de
programar, vale la pena hacer preguntas puntuales y cerradas (sí/no, o A-o-B) en vez de una pregunta
abierta — permitió confirmar el diseño completo en una sola respuesta corta del usuario.

---

## SESIÓN 29 cont. 2 (2026-07-17) — Autocrítica objetiva pedida por el usuario → 6 mejoras implementadas

El usuario pidió una autoevaluación muy crítica y objetiva: "hay algo que crees que debe mejorar para
ser un AAA, para tener la mejor experiencia de gamepad de todos?". Se entregó un análisis honesto de
8 puntos (haptics por superficie, jugosidad, vocabulario de haptics, movimiento relativo a cámara,
rendimiento de la cámara libre, API de mods, perfiles, rediseño de UI). El usuario respondió con
dirección precisa por punto: rechazó haptics por superficie proponiendo en su lugar haptics por ARMA,
aprobó 5 de los 8 puntos para implementación inmediata, pospuso el rediseño de UI, y dejó la API de
mods pendiente.

- [x] **Haptics por arma + muerte confirmada:** propuesta de diseño presentada antes de programar
  (pedido explícito) — espada/hacha/tridente/maza cada una con su propia firma de golpe; el "ataque
  de aplastamiento" real de la maza (requiere caer) reutiliza exactamente la misma señal que ya
  existía para críticos; flecha confirmada como toque corto; muerte siempre promueve a la vibración
  más fuerte, con cualquier arma.
- [x] **Corregido un supuesto propio a mitad del diseño:** `SwordItem` ya no existe como clase en
  1.21.10 (refactor de Mojang a componentes de datos, ~1.20.5+) — verificado con `javap` antes de
  escribir el código; se detecta por `ItemTags.SWORDS`/`AXES` en su lugar. `MaceItem`/`TridentItem`
  sí siguen siendo clases reales.
- [x] **Optimización de la cámara libre:** el pick de crosshair/entidades pasó de recalcularse cada
  frame de render a cada ~50ms — la optimización que yo mismo señalé pendiente en la autocrítica.
- [x] **Movimiento relativo a cámara**, opción nueva apagada por defecto — toca `KeyboardInputMixin`
  (el mixin de movimiento más crítico del proyecto) pero el camino existente queda byte-por-byte
  intacto si no se activa.
- [x] **Jugosidad:** nuevo `JuiceController` — screen shake + "FOV kick" enganchados a los mismos
  eventos que ya disparan haptics (golpes, muertes, explosiones, daño recibido, caídas), en primera
  Y tercera persona. Deliberadamente SIN hit-stop real — la propia historia de bugs de cámara del
  proyecto (D046-D053) hizo que no valiera la pena arriesgarlo sin poder probar en hardware.
- [x] **Onboarding:** pantalla de bienvenida de una sola vez, disparada la primera vez que un mando
  se activa, apuntando a las funciones principales — respuesta directa al patrón de descubribilidad
  que la propia autocrítica señaló (el bind de tercera persona que nadie encontraba, esta sesión).
- [x] **Perfiles de configuración:** guardar/cargar/eliminar paquetes con nombre de botones+
  sensibilidad+radial, reutilizando el patrón de copia de archivos de la migración por reconexión.
- [ ] **Descartado, no pendiente:** triggers adaptativos (steamworks4j bloqueado en la API vieja de
  Steam Input), API pública para mods de terceros (valor incierto), rediseño de UI 10-foot (el
  usuario lo pospuso explícitamente).
- [x] `mod_version` → 0.53.0. Build + 29/29 tests → `dist/steampad-0.53.0.jar`, compiló limpio a la
  primera.
- [ ] **Nada de esto se ha probado en hardware.**

**Lección de proceso:** cuando el usuario pide una autocrítica, vale la pena dar un veredicto de
viabilidad honesto por punto (sí/parcial/no y por qué) en vez de solo listar ideas — eso es lo que le
permitió al usuario decidir con precisión qué ejecutar y qué posponer, en vez de aprobar u objetar el
paquete completo de una vez.

---

## SESIÓN 29 cont. (2026-07-17) — Cámara Libre: el resto del feature set de Third-Person, pedido por tercera vez

El usuario, sin poder probar en hardware ("aun no estoy en casa"), pidió adelantar el resto del
feature set de Leawind's Third-Person que D082/D083 habían dejado fuera de alcance dos veces:
rotación libre, mira/crosshair funcional en tercera persona, "todo lo que dice su descripción". Pidió
releer el código real del repo (MIT, no GPL — sin restricción de clean-room) antes de programar.

- [x] **Investigación:** descargado el código fuente completo vía GitHub API/curl de la versión
  ESTABLE publicada más reciente (`v2.5.0-mc1.21.11`, no `main`, que ya iba adelante con un refactor
  de arquitectura). Leídos a fondo los mixins de desacople de rotación, `CameraAgent`/`EntityAgent`
  (687+434 líneas), el mapa de decisión de rotación del cuerpo, la predicción de objetivo, los mixins
  de crosshair/FOV/transparencia, y las ~50 opciones de configuración reales del mod.
- [x] **Corregido un error propio a mitad de la verificación:** el primer intento de `javap` apuntó
  por accidente a un jar de OTRO proyecto cacheado en la misma máquina (mappings de Mojang, no Yarn)
  — detectado por el paquete incorrecto, corregido localizando el jar Yarn 1.21.10+build.3 real antes
  de escribir cualquier mixin.
- [x] **Rotación libre:** redirigida en `CameraController.frame()` (único punto por el que el stick
  llega a `changeLookDirection`) — sin necesidad de un mixin nuevo sobre el turno del jugador.
- [x] **Cámara con centro de rotación + colisión + distancia ajustable**, activa solo cuando el nuevo
  toggle `thirdPersonFreeLookEnabled` está prendido — el offset simple de D082 queda intacto apagado.
- [x] **Mira funcional + puntería predictiva:** 2 mixins nuevos (`Entity#raycast`,
  `InGameHud#renderCrosshair`), firmas verificadas contra el jar correcto antes de escribirlos.
- [x] **3 modos de hacia-dónde-mira-el-cuerpo**, simplificados de los 5 del mod real — deliberadamente
  solo actúan con el jugador quieto (replicar el modo "sigue el movimiento" exigiría tocar el mixin de
  movimiento más crítico del proyecto; se decidió no arriesgarlo sin poder probar en hardware).
- [x] **Ajuste en vivo** de offset/distancia con un bind de mantener, reutilizando el patrón de
  D-pad-reasignado que el Zoom ya usa.
- [ ] **NO implementado, deliberadamente:** transparencia del jugador (cosmética, apagada por defecto
  incluso en el mod real) — la parte de mayor riesgo/menor beneficio del feature set.
- [x] `mod_version` → 0.52.0. Build + 29/29 tests → `dist/steampad-0.52.0.jar`, compiló limpio a la
  primera (incluidos los 2 mixins nuevos).
- [ ] **Nada de esto se ha probado en hardware** — es la feature de mayor alcance/riesgo implementada
  en una sola ronda en la historia del proyecto, justamente por hacerse sin poder probar en vivo.

**Lección de proceso:** verificar el jar de `javap` NO solo por la ruta del caché sino por el PAQUETE
real dentro (`net.minecraft.entity.Entity` = Yarn, `net.minecraft.world.entity.Entity` = Mojang) —
varios proyectos de mods comparten la misma carpeta `.gradle` y es fácil apuntar al jar equivocado.

---

## SESIÓN 29 (2026-07-17) — Primera ronda con el checklist HTML consolidado (29/42 confirmados)

El usuario probó v0.50.0 usando el checklist HTML consolidado por primera vez y exportó un reporte
completo con notas por ítem — mucha más señal por ronda que mensajes sueltos. Pidió aplicar los fixes
correspondientes a lo no confirmado/con nota, dejar pendiente (sin tocar) lo no marcado y sin
comentario, y rediseñar el checklist con estado de 3 niveles + mantenerlo sincronizado con la
documentación del proyecto en adelante.

- [x] **(1) Deformación de emotes — causa raíz REAL #5 encontrada por relectura de código.** Las
  causas #3 (rotación, D084) y #4 (pose agachada, D085) ya estaban corregidas, pero el usuario
  confirmó que la deformación seguía pasando en ambos escenarios de prueba con la misma descripción.
  Gap real encontrado: la traslación del torso (canales x/y/z, comunes en bailes con rebote/cadera)
  nunca se propagaba a los hijos — cada miembro se quedaba anclado a su reposo mientras el torso se
  desplazaba. Fix en `EmoteAnimator.java`: `applyTorso` retorna su delta; `applyChild` lo suma al
  origen de cada miembro que la animación toca.
- [x] **(2) Vibración de slime — bug real de detección, no de magnitud (dato nuevo del usuario lo
  reveló).** El mismo preset SÍ se siente manual desde el Panel de Prueba de Haptics pero nunca al
  caminar sobre slime real — descarta la teoría de "el driver no interpreta pulsos superpuestos".
  Causa real: `getBlockPos().down()` sufre parpadeo de punto flotante justo al caminar. Fix en
  `HapticsController.java`: `BlockPos.ofFloored(x, y-0.2, z)`.
- [x] **(3) Vibración de inicio — condición de carrera plausible corregida.** El disparo ocurría en
  el MISMO tick en que el mando se marca activo, antes de que SDL3/GLFW reconozcan ese handle — un
  fallo silencioso ahí significa cero vibración el resto de la sesión (disparo de una sola vez). Fix
  en `SteamPadClient.java`: diferido ~750ms.
- [x] **(4) Traveler's Backpack — implementada la propuesta EXACTA del usuario.** Las casillas ahora
  solo "jalan" el cursor dentro de su propia celda (8px, antes 22px compartido con los widgets),
  dejando el radio amplio para los botones de mods. Fix en `SlotSnap.java`.
- [x] **(5) `.emotecraft` v1 — investigación pública vía WebSearch/WebFetch a la wiki oficial de
  KosmX/emotes (sin leer código GPL).** Encontrada la página "Emote binary": el contenedor es
  modular y versionado por diseño (cabecera + módulos con su propio id/versión/tamaño) — explica por
  qué coexisten sub-formatos, pero no documenta la codificación interna de keyframes. Sigue
  bloqueado, ahora con una explicación creíble del porqué.
- [x] **(6) Bind de Tercera Persona — aclarado, no era un bug.** Vivía sin indicación al final de
  Botones → Jugabilidad. Agregado un botón directo en `GlobalSettingsScreen.java` (Ajustes Globales
  → Tercera Persona) que abre Botones.
- [x] **(7) Checklist HTML rediseñado** de checkbox binario a 3 estados (No probado/Falló/OK) + nota
  de texto libre por ítem + exportar reporte (con fallback de copia manual). Todos los ítems viven en
  un único array de datos JS — actualizar en el futuro es una edición barata, no una reescritura de
  HTML. Movido de un archivo temporal de sesión a `CHECKLIST.html` en la raíz del repo.
- [x] Regenerados `gradlew`/`gradlew.bat` (faltaban del repo, sin rastro por no tener git) apuntando
  a Gradle 8.14 — la versión que CLAUDE.md documenta como exigida por Loom 1.13.6, corrigiendo la
  8.12.1 desactualizada de `gradle-wrapper.properties`.
- [x] `mod_version` → 0.51.0. Build + 29/29 tests → `dist/steampad-0.51.0.jar`.
- [ ] Validación en hardware pendiente → checklist completo en `CHECKLIST.html` / TODO_BLOCKERS.md B084.

**Lección de proceso:** el checklist HTML con export de reporte funcionó mucho mejor que mensajes de
chat sueltos para recibir feedback estructurado — 42 puntos con notas específicas en un solo pegado,
en vez de tener que preguntar uno por uno. Vale la pena mantenerlo como el mecanismo estándar de
validación en adelante.

---

## SESIÓN 28 cont. 6 (2026-07-16) — Quinta ronda: 21 archivos `.emotecraft` reales + bug de reconexión de mando

El usuario adjuntó 20 archivos `.emotecraft` reales adicionales (21 en total contando la ronda
anterior) y reportó: el PNG del ícono "no aparece nada"; las animaciones seguían mal ("como si el
pivote de cada parte del modelo se moviera al centro u otro sitio"); algunos `.emotecraft` cargan y
otros no; el preview "funciona parcialmente solo en gameplay, en menú ajustes parece no funcionar";
y un bug NUEVO — desconectar/reconectar el mando durante gameplay "descuatrapea" los botones
configurados, solo arreglable reiniciando el juego.

- [x] **(1) `.emotecraft`: de 1 a 12 de 21 archivos reales cargando.** Con las 21 muestras reales
  se corrió el mismo parser y se descubrió que los límites de seguridad puestos la ronda anterior
  (`RESYNC_WINDOW=512` bytes, máximo 64 keyframes por canal) se habían calibrado con UNA sola
  muestra simple (máximo 4 keyframes por canal) — insuficiente: las muestras nuevas tienen hasta 86
  keyframes en un solo canal. Fix: tope de keyframes sube a 10,000 (el límite real sigue siendo el
  chequeo de no leer fuera del buffer) y la ventana de resincronización pasa a buscar sin límite
  hasta el fin del archivo (seguro: una coincidencia falsa en bytes de punto flotante crudo es
  astronómicamente improbable). Resultado verificado con test temporal contra los 21 archivos
  reales: 10 con las 6 partes completas, 2 con 5 de 6 (falta solo una extremidad, sin datos
  incorrectos en lo que sí cargó). Los 9 restantes: 8 son el sub-formato "versión 1" ya documentado;
  1 ("Friendship Round Dance") tiene una variante estructural distinta dentro de "versión 2" — se
  investigó con dump de bytes pero sin evidencia suficiente para decodificar con confianza, se deja
  documentado en vez de adivinar.
  - **Hallazgo colateral:** metadata legible (`{"translate":"mineeemotes.emote.name...",...}` y la
    firma `"MineEmotes"`) confirma que estos archivos vienen de un tool comunitario de terceros, no
    del exportador oficial de KosmX/Emotecraft — explica por qué hay variantes estructurales.
- [x] **(2) Ícono no aparecía — bug real de overload de `drawTexture`, no de extracción.** La
  extracción del PNG funciona al 100% en las 21 muestras (mismo test lo confirmó), así que el
  problema estaba en el renderizado: `EmoteIconProvider` usaba el overload de 8 parámetros (sin
  `regionWidth`/`regionHeight`); se corrigió al overload de 10 parámetros, igualando el patrón ya
  probado en `ButtonIcon`/`ControllerBrandIcon`.
- [x] **(3) Deformación — sin causa nueva encontrada; la #4 de la ronda anterior ya cubre la
  descripción.** No se encontró evidencia de una quinta causa distinta a las 4 ya documentadas; la
  descripción "el pivote se mueve al centro" es consistente con la causa #4 (pose de agachado
  contaminando el caché de reposo), corregida en el jar recién construido cuando llegó este reporte
  — posible que el usuario describiera el estado ANTERIOR a ese fix. Pendiente confirmar con 0.50.0.
- [x] **(4) Bug NUEVO — reconexión de mando pierde la configuración — causa raíz real encontrada.**
  Los archivos de config por-control se guardan por HANDLE numérico, pero SDL3 asigna un handle
  NUEVO cada vez que el mismo mando físico se reconecta (confirmado en el log: el mismo 8BitDo pasó
  de handle `...386369` a `...386370`). El código que decide "cuál mando está activo" ya resolvía
  esto por NOMBRE (estable) al reconectar, pero los ARCHIVOS de configuración seguían buscándose por
  el handle nuevo, que nunca existió — creando binds/rueda en blanco silenciosamente. Fix:
  `ConfigManager.migrateControllerConfigByName` — índice persistente nombre→último-handle-visto que
  copia los 3 archivos de config del handle anterior al nuevo la primera vez que aparece (nunca
  sobrescribe si el handle nuevo ya tiene su propia config).
- [x] **(5) Preview "no funciona en menú ajustes" — no se encontró un bug de código distinto al del
  ícono.** El preview animado requiere una entidad de jugador real — imposible sin mundo cargado, no
  es un bug corregible. Se agregó una línea aclaratoria en el panel en vez de dejarlo en blanco sin
  explicación; pendiente confirmar si el problema persiste con un mundo realmente cargado.
- [x] `mod_version` → 0.50.0. Build + 29/29 tests → `dist/steampad-0.50.0.jar`.
- [ ] Validación en hardware pendiente → checklist completo en TODO_BLOCKERS.md B083.

**Lección de proceso:** el valor de probar contra 21 muestras reales en vez de 1 fue enorme — los
límites "de seguridad" elegidos con una sola muestra simple estaban rechazando datos perfectamente
válidos de animaciones más complejas. Cuando el usuario ofrece un corpus más grande, vale la pena
re-correr la validación completa contra TODO el corpus antes de asumir que un fix anterior ya cubrió
el caso general.

---

## SESIÓN 28 cont. 5 (2026-07-16) — Cuarta ronda de feedback: deformación, regresión crítica, `.emotecraft` con evidencia real

El usuario adjuntó un archivo `.emotecraft` real (`Dance Moves.emotecraft`) junto con: la deformación
seguía pasando pese a D084, pidiendo una "valoración profunda" revisando cómo lo hace Emotecraft; un
bug NUEVO y crítico — al seleccionar un emote en la rueda de gameplay la cámara transicionaba pero el
emote "enseguida regresa" (no se reproducía); el preview animado "desapareció" de la Biblioteca; el
ícono por letra no era lo pedido ("te dije generalo, en el código original viene así, también lo
debemos tener, es un AAA"); y `.emotecraft` seguía sin cargar, con 3 archivos reales adjuntados y un
log + debug dump completos. Instrucción explícita: "no termines hasta que cumplas todos los pasos".

- [x] **(1) Deformación — causa raíz REAL #4, confirmada por bytecode, no inferida.** `javap` sobre
  `BipedEntityModel.setAngles` (mismo rigor que D084) encontró que vanilla desplaza `originY`/
  `originZ` 3.2-4.2 unidades en la pose de agachado. El caché estático de rest-origin (D083) leía los
  campos EN VIVO de la primera entidad "no emotando" que se renderizara, asumiendo que eso bastaba
  para estar limpia — falso: si esa entidad resultaba estar agachada, el valor contaminado quedaba
  cacheado PARA SIEMPRE (`putIfAbsent` nunca relee), descolocando cada emote de cada jugador que
  compartiera ese modelo el resto de la sesión — una CUARTA causa real, independiente de las tres
  anteriores. Fix: `restOf(ModelPart)` lee `ModelPart.getDefaultTransform().x/y/z()` — el pivote
  HORNEADO e inmutable, nunca tocado por lógica de pose por-frame — en vez de los campos en vivo. Esto
  elimina la clase entera de riesgo de contaminación, no solo mitiga un síntoma. Simplificación:
  `captureRest()` y las 6 llamadas oportunistas en `apply()` ya no hacen falta y se eliminaron.
- [x] **(2) Regresión crítica encontrada y corregida — "la cámara transiciona pero el emote no se
  reproduce".** Causa exacta: `EmoteWheelOverlay`'s preview animado llama `EmoteAnimator.playFor()`
  sobre la MISMA entidad (jugador local) que la reproducción real. Confirmar una selección reemplaza
  ese `Playback` por uno real Y cierra la rueda en la misma acción; un frame después, `stopPreview()`
  veía la rueda cerrada y llamaba `requestStop()` incondicionalmente, cancelando el emote real recién
  iniciado. Fix: `EmoteAnimator.Playback` ahora lleva un token de generación (`AtomicLong`), expuesto
  vía `currentGeneration(entityId)`; cada preview guarda su generación al iniciar y solo se detiene si
  esa generación SIGUE siendo la activa. Mismo patrón aplicado en `EmoteLibraryScreen` y
  `EmoteWheelScreen` — ambas tenían el mismo riesgo latente vía el botón "▶"/clic de fila.
- [x] **(3) "Preview desaparecido de Biblioteca" — explicación más probable, no un bug separado.** El
  guard original (`isLocalPlaying() && !weStartedPreview`) es correcto en intención pero con un efecto
  confuso: un emote en BUCLE dejado corriendo bloquea el preview indefinidamente. El log adjunto
  muestra varios emotes en bucle reproducidos justo antes de aperturas de rueda/biblioteca — consistente
  con este escenario. El fix del punto 2 además hace que el panel muestre el personaje si CUALQUIER
  cosa está reproduciéndose (nuestra o real), no solo si nosotros iniciamos el preview.
- [x] **(4) `.emotecraft` — investigación profunda con evidencia hexadecimal real, resultado PARCIAL
  y documentado honestamente.** Análisis byte a byte de los 3 archivos reales del usuario confirmó que
  NO es un `.json` renombrado (la asunción de D082/v0.47.0 estaba equivocada — los bytes no son UTF-8
  válido en absoluto). Investigación en la documentación pública oficial de Emotecraft (nunca su código
  GPL) confirmó que es el formato binario NATIVO real del mod: *"the binary format can store the icon
  and the data in one file"*. Reverse-engineering clean-room (solo de los bytes de muestra) encontró
  que el byte 7 del header es un discriminador de versión; los archivos versión 2 codifican cada parte
  del cuerpo como nombre con prefijo de longitud + exactamente 6 canales (x/y/z/pitch/yaw/roll, mismo
  vocabulario que el JSON) — verificado byte-exacto contra una muestra real (la suma de tamaños de
  canal de una parte cae EXACTO en el offset de la siguiente, para las 7 partes). Un bloque separado
  "torso" (distinto de "body") resultó ser el canal bend/axis ya no soportado — tratado como opaco.
  Implementado en `EmoteCraftBinaryParser`, verificado con un test temporal (borrado tras usar) contra
  "The Honored One (levitation).emotecraft": 6 de 7 partes parseadas con valores simétricos y físicamente
  sensatos. Los archivos versión 1 (2 de los 3 del usuario, incluyendo el que adjuntó primero) no
  tienen ningún ancla de texto legible para reverse-engineer con seguridad — NO se implementó un parser
  a ciegas para esa variante (arriesgaba reproducir el mismo bug de deformación por quinta vez). Ver
  TODO_BLOCKERS B082 para la evidencia completa.
- [x] **(5) Íconos reales por emote, no la letra fija.** `EmoteData.iconPng` se puebla desde el PNG
  embebido de un `.emotecraft` binario (SIEMPRE, sin importar si su animación decodificó), un
  `<nombre>.png` hermano de un `.json` suelto (convención documentada oficialmente), o el mismo hermano
  dentro del pack CC0. `EmoteIconProvider` (nuevo) decodifica y registra la textura de forma perezosa
  y cacheada (`NativeImage.read` + `NativeImageBackedTexture` + `TextureManager.registerTexture`, API
  verificada con javap), con caída a la letra SOLO si el emote de verdad no tiene ícono.
- [x] `mod_version` → 0.49.0. Build + 29/29 tests → `dist/steampad-0.49.0.jar`.
- [ ] Validación en hardware pendiente → checklist completo en TODO_BLOCKERS.md B082.

**Lección de proceso:** la investigación del formato binario fue la parte más costosa en tiempo de esta
sesión — vale la pena documentar que el hallazgo clave (byte 7 = discriminador de versión, con
codificaciones internas DISTINTAS entre versiones) solo se encontró al intentar validar el parser
contra los 3 archivos reales del usuario en vez de solo el primero; probar contra múltiples muestras
reales desde el principio hubiera ahorrado un ciclo de reverse-engineering a ciegas.

---

## SESIÓN 28 cont. 4 (2026-07-16) — Tercera ronda de feedback: la deformación seguía, con capturas

El usuario probó v0.47.0 y mandó capturas: la deformación SEGUÍA pasando ("como que el esqueleto de
la animación lo deforma"); pidió transición de cámara (zoom out/in) al entrar/salir de un emote desde
1ª persona; preview animado también en el editor de la rueda y en la rueda de gameplay (no solo
Biblioteca); un preview fijo (imagen) por espacio en la rueda de gameplay en vez de solo letras;
`.emotecraft` seguía sin cargar 3 archivos probados; y que la rueda de emotes soporte múltiples
ruedas igual que el menú radial, con sus mismos glifos de cambio.

- [x] **(1) Deformación — causa raíz REAL #3, confirmada en la documentación oficial, no inferida.**
  Se releyeron los docs de Emotecraft (kosmx.gitbook.io) y ahí estaba: *"The head, legs and arms
  location is relative to the torso's location AND rotation."* El código aplicaba esos offsets como
  deltas PLANOS en espacio de modelo — correcto solo mientras el torso está sin rotar. En cuanto un
  baile inclina/gira el torso (la mayoría lo hace), los miembros se seguían colocando en su offset
  "de pie" — el síntoma exacto reportado, y una causa TOTALMENTE distinta de las dos ya corregidas
  (acumulación sin base / modelo compartido entre entidades). Fix: el torso se aplica PRIMERO; se
  construye una matriz de rotación con SU rotación actual (`Matrix3f.rotationZYX(roll, yaw, pitch)`,
  mismo orden que usa `ModelPart.rotate()` internamente, verificado con javap contra el jar mapeado);
  el offset local de cada hijo se ROTA por esa matriz antes de sumarse a su propio reposo.
- [x] **(2) Transición de cámara al entrar/salir de un emote:** en vez de un corte instantáneo, la
  cámara se desliza (smoothstep, 220ms) entre la posición del ojo y la de 3ª persona ya calculada por
  vanilla. La perspectiva cambia a 3ª persona de inmediato al iniciar (el cuerpo debe verse ya), pero
  el cambio DE VUELTA a 1ª persona se retrasa hasta que la transición visual termina.
- [x] **(3) Preview animado extendido a los 3 lugares:** `EmoteWheelScreen` (editor) y
  `EmoteWheelOverlay` (rueda en gameplay) ahora usan el mismo mecanismo que la Biblioteca
  (`InventoryScreen.drawEntity` + `EmoteAnimator.playFor` sin red), nunca si hay un emote real
  corriendo, y se detiene explícitamente en cuanto la rueda/editor cierra.
- [x] **(4) Preview fijo por espacio en la rueda de gameplay — NO implementado, alcance explicado:**
  generar una miniatura única por emote sin costo de rendimiento (sin un render 3D por cada uno de
  hasta 12 espacios) no tiene una solución barata clara con el tiempo de esta sesión; se mantiene el
  ícono de letra por espacio y el preview animado grande del panel lateral es donde se ve "qué tipo
  de emote es" en detalle.
- [x] **(5) `.emotecraft` reescrito de forma más robusta:** el glob `*.{json,emotecraft}` de
  `DirectoryStream` debería funcionar según la especificación de Java, pero se reemplazó por
  comparación manual de extensión (cero dependencia de dialecto de glob) + logging de diagnóstico
  (archivos totales vistos vs. con extensión cargable vs. que fallan al parsear).
- [x] **(6) Multi-rueda de emotes:** `EmoteWheelScreen` ahora tiene la misma fila de gestión de ruedas
  que `RadialEditorScreen` (◀ ▶ N/M, + agregar, − quitar). El controlador (`EmoteWheelController`,
  LB/RB en juego, siluetas fantasma) ya soportaba múltiples páginas desde la decoupling de la sesión
  anterior — solo faltaba la UI del editor para crearlas.
- [x] **Nota de proceso:** dos herramientas de compilación (Bash y PowerShell) estuvieron
  temporalmente no disponibles durante buena parte de esta sesión (clasificador de seguridad caído).
  En vez de esperar sin hacer nada, se hizo una revisión manual línea por línea de TODO el código
  escrito antes de poder compilar — y esa revisión encontró un bug real: la fila de conteo de
  espacios de `EmoteWheelScreen` llamaba `cfg.setSlotCountFor(page, ...)` (el método de las ruedas
  RADIALES regulares) en vez de `cfg.setSlotCountForEmote(page, ...)` — habría corrompido en
  silencio la rueda radial normal del usuario en ese índice de página. Corregido ANTES de la primera
  compilación real, no después.
- [x] `mod_version` → 0.48.0. **BUILD SUCCESSFUL + 29/29 TESTS → `dist/steampad-0.48.0.jar`.**
- [ ] Validación en hardware pendiente de TODO el lote → B081 (checklist completo).

---

## SESIÓN 28 cont. 3 (2026-07-16) — Segunda ronda de feedback sobre v0.46.0

El usuario probó v0.46.0: la deformación del emote SEGUÍA pasando ("se separan piernas y brazos");
pidió preview animado optimizado en la rueda de gameplay (con referencia visual tipo Emotecraft);
reportó que "Actualizar" en Biblioteca no detectaba archivos `.emotecraft` descargados; dio 6 puntos
de teclado virtual; e insistió explícitamente (segunda vez) en portar Third-Person completo.

- [x] **(1) Deformación — causa raíz REAL esta vez.** El fix de la sesión anterior (rest-origin por
  Playback) no bastaba: Minecraft renderiza a TODOS los jugadores con un modelo COMPARTIDO (solo 2
  instancias: brazos anchos/flacos), reposeándolo por turno. Si el modelo compartido ya estaba tocado
  por OTRO emote antes del primer toque de una reproducción, el "reposo" capturado ya venía corrupto
  — y cualquier entidad renderizada justo después heredaba el offset. Fix real: caché ESTÁTICA
  `IdentityHashMap<ModelPart,float[]>`, aprendida oportunistamente de cualquier entidad SIN emote
  activo (garantizado limpio) — autocorrectiva en cuanto se renderiza cualquier jugador sin emote.
- [x] **(2) `.emotecraft` detectado:** `EmoteLibrary.loadFolder()` escaneaba solo `*.json` — verificado
  en los docs oficiales de Emotecraft (kosmx.gitbook.io) que el formato real siempre es `.json`;
  `.emotecraft` es una extensión renombrada de algunos sitios de la comunidad, mismo contenido JSON.
  Glob ahora `*.{json,emotecraft}` — esto también explica por qué "Actualizar" parecía no funcionar.
- [x] **(3) Teclado — selección oculta hasta mover un stick + auto-oculta tras inactividad** (1.4s),
  D-pad exento (resaltado persistente, necesario para que sea usable con D-pad solo).
- [x] **(4) Teclado — "A" respeta el último stick usado** en modo dual (D-pad cuenta como actividad
  del slot izquierdo, para que un D-pad después del stick derecho también recupere A correctamente).
- [x] **(5) Teclado — footer de hints ya no pierde hints por overflow:** mide el ancho total primero
  y comprime el espacio ENTRE hints si no caben (nunca el texto/ícono) — antes simplemente dejaba de
  dibujar lo que no cupiera, perdiendo el hint de "mover teclado" en silencio.
- [x] **(6) Glifos de inventario ocultos** mientras el teclado virtual está activo, reaparecen al cerrarlo.
- [x] **(7) Gesto golpe-vs-mantener:** un flick que pasa de 0.55 de magnitud y baja de 0.15 en menos
  de 220ms avanza UNA tecla en la dirección dominante (como D-pad); mantener más tiempo no dispara la
  corrección — el "hold" existente queda 100% intacto, sin ningún retraso añadido (la corrección solo
  ocurre EN RETROSPECTIVA, al soltar, nunca de antemano).
- [x] **(8) Rebase de velocidad:** `CRUISE_SPEED`/`FLOAT_MAX_SPEED` a la mitad (11→5.5, 45→22.5) — el
  50% guardado del usuario debe subirse a 100% manualmente para sentir lo mismo (el número cambió de
  significado, no solo la sensación).
- [x] **(9) Preview animado en Biblioteca de emotes:** `InventoryScreen.drawEntity` (el mismo mecanismo
  ya optimizado que usa el propio inventario de Minecraft) renderiza a `mc.player` posado por el emote
  previsualizado, sin pasar por `playLocal()` (sin salto de perspectiva, sin red) — y SOLO cuando el
  jugador no tiene un emote real corriendo (nunca pisa una animación real). Alcance acotado a la
  Biblioteca (no la rueda de gameplay ni el editor) por presupuesto de esta sesión.
- [x] **(10) Third-Person v2:** confirmado leyendo `AbstractConfig.java` completo (todos los ~50
  campos reales) que el free-look (`PlayerRotateMode`) es un problema de acoplamiento ENTRADA→ROTACIÓN
  del jugador, no de posicionamiento de cámara — se mantiene deliberadamente fuera de esta sesión.
  Ampliado en cambio: suavizado exponencial por vida-media en la transición de lado/offset (mismo
  algoritmo público que usa el mod real, reimplementado independiente, no copiado), y perfil de cámara
  de apuntado (se acerca/centra al cargar arco/ballesta/tridente, misma detección de `UseAction` que
  ya usa `AimAssistController`) — ambos créditados a Leawind en Ajustes Globales.
- [x] `mod_version` → 0.47.0. **BUILD SUCCESSFUL + 29/29 TESTS → `dist/steampad-0.47.0.jar`.**
- [ ] Validación en hardware pendiente de TODO el lote → B080 (checklist completo).

---

## SESIÓN 28 cont. 2 (2026-07-16) — Primera prueba real en hardware de FASE 63 + Steam Input, lote de 9

El usuario probó v0.45.x en su ROG Ally X + 8BitDo (Bazzite). Buenas noticias sin código: el lag de
v0.45.1 era Bazzite (un reinicio lo arregló), y el mixin de emotes cargó sin crash. Reportó 3 bugs
reales de emotes + 2 pedidos de UX, y de Steam Input: overlay ✅, pero pidió que las bolitas del
teclado dual-stick solo aparezcan con el stick que se mueve, reportó detección de la Ally solo con
el 8BitDo conectado, slime sin vibrar pese al panel de prueba, y Traveler's Backpack sin snap pese al
fix de v0.43. Pidió además portar `Leawind/Third-Person` (MIT) como toggle nativo con crédito.

- [x] **(1) Deformación del personaje al emotear — causa raíz real:** `EmoteAnimator.applyPart()`
  hacía `part.originX/Y/Z += sample(tick)*weight` CADA frame. El pivote de un `ModelPart` de bípedo es
  una constante horneada que vanilla nunca reescribe por frame (solo pitch/yaw/roll cambian ahí) — sin
  una base de referencia, el offset se componía sin límite mientras el emote seguía corriendo. Fix:
  cada `Playback` captura el origin de reposo la primera vez que toca cada parte y calcula siempre
  "reposo + delta" (nunca `+=` sobre el campo ya modificado).
- [x] **(2) Cámara vuelve sola a 1ª persona al terminar el emote:** nuevo flag `autoSwitchedPerspective`
  — se marca solo cuando el propio código hizo el salto a 3ª persona, y se revierte cuando la
  reproducción local termina de verdad, pero solo si el jugador no cambió de perspectiva por su cuenta
  mientras tanto (no le pisa una elección manual).
- [x] **(3) Rueda de emotes 100% desacoplada del menú radial:** antes vivía como un `WheelConfig` más
  dentro de `RadialConfig.wheels`, compitiendo por `MAX_WHEELS` y compartiendo el estado estático único
  de `RadialMenuController` con las ruedas del usuario. Ahora: lista independiente
  `RadialConfig.emoteWheels` (migración automática desde el flag legado en `normalize()`), controlador
  propio `EmoteWheelController` (mismo mecanismo probado, estado separado), overlay propio
  `EmoteWheelOverlay`. El dispatcher abre uno U OTRO, nunca ambos. `RadialRenderer.render()` ahora
  recibe wheelCount/page como parámetros explícitos en vez de leerlos de `RadialMenuController`
  directamente, para que cualquier sistema de ruedas independiente lo pueda reusar.
- [x] **(4) Preview fijo del lado derecho:** nuevo `EmotePreviewPanel` (nombre/autor/duración o "en
  bucle"), reusado en Biblioteca de emotes (lista dividida, actualiza con foco de D-pad o hover de
  mouse, "sticky"), en el editor de la rueda, y en la rueda de gameplay. Silueta animada NO
  implementada esta sesión (mencionada como "si se puede" — motor de render-en-GUI aparte, presupuesto
  de esta sesión fue a otro lado).
- [x] **(5) Hallazgo grande: `LogUtil.debug()` nunca se veía en producción.** El log4j2 por defecto de
  un cliente de Minecraft real (no `runClient`) tiene el root logger en INFO — cero líneas DEBUG llegan
  a `latest.log` nunca. Esto explica por qué el diagnóstico de slime de hace 3 sesiones nunca tuvo
  evidencia real: se pedía revisar un log que estructuralmente no podía mostrar nada. Fix: `debug()`
  ahora enruta a `LOG.info()` (23 call sites revisados, ninguno es spam de por-frame sin throttle).
- [x] **(6) Vibración de slime — sin una 5ª corrección a ciegas:** el código usa los MISMOS parámetros
  que el preset de prueba que el usuario confirma que sí se siente — no hay un bug de lógica evidente.
  Con el fix de (5), el próximo log sí mostrará evidencia real (`Slime underfoot`/`Slime pulse firing`).
- [x] **(7) Snap de Traveler's Backpack — diagnóstico, no otro intento a ciegas:** logging dedicado en
  `ExternalWidgetScanner.discover()` (solo cuando la clase de pantalla contiene "backpack", acotado a
  1/2s) que vuelca qué campos se consideraron y por qué cada elemento se aceptó o descartó.
- [x] **(8) Detección de la Ally "solo con el 8BitDo":** `GlfwControllerProvider.poll()` no filtra por
  reconocimiento de mapeo (`glfwJoystickPresent`, no `glfwJoystickIsGamepad`) — si no ve el pad sin el
  8BitDo, el SO/Steam no expone el nodo, no es el mod. Log de conteo crudo agregado para confirmar.
- [x] **(9) "Mejor tercera persona" — port acotado de Leawind/Third-Person (MIT verificado):** su
  arquitectura real cancela COMPLETO `Camera#setup`/`update()` y lo reemplaza con un sistema propio de
  cámara con amortiguación por vida-media que además desacopla la rotación de cámara de la del cuerpo
  del jugador — cambio profundo justo en el subsistema con más historial de bugs de este proyecto
  (D046-D053). Implementado en cambio: `ThirdPersonCameraController` + `ThirdPersonCameraMixin`, un
  `@Inject TAIL` no-cancelable sobre `Camera.update(BlockView,Entity,boolean,boolean,float)` (firma
  verificada con javap contra el jar mapeado 1.21.10) que desplaza LATERALMENTE la posición YA
  calculada/recortada por colisión de vanilla, con su propio raycast (`RaycastContext.ShapeType.COLLIDER`)
  para nunca atravesar una pared — el sub-feature de offset al hombro, sin el riesgo del free-look.
  Toggle + lado L/C/R + slider de offset en Ajustes Globales, bind ciclable sin asignar por defecto,
  crédito a Leawind en la descripción. Free-look, apuntado predictivo y transparencia del jugador
  quedan explícitamente fuera de esta sesión.
- [x] `mod_version` → 0.46.0. **BUILD SUCCESSFUL + 29/29 TESTS → `dist/steampad-0.46.0.jar`.**
- [ ] Validación en hardware pendiente de TODO el lote → B079 (checklist completo).

---

## SESIÓN 24 cont. 5 (2026-07-09) — Cámara confirmada ambiental + log del pad de la Ally

- [x] Cámara de mouse: **usuario confirma** que era Moonlight (con mouse directo funciona). Cerrado.
- [x] Pad de la Ally invisible: la cascada SDL3→GLFW era todo-o-nada → **MERGE** con dedupe por
  nombre (`ControllerManager.refreshCache`). El fantasma loco era la PANTALLA TÁCTIL `NVTK0603`
  (i2c-HID) auto-activada → el filtro de falsos ahora veta el patrón `XXXX:NN `.
- [x] Glifos de zoom en tiempo real en el HUD (DUP/DDOWN "Zoom ±", A "Marcador") solo mientras el
  zoom está activo.
- [x] **Marcador de zoom:** A durante el zoom → baliza de partículas END_ROD en el bloque apuntado
  (raycast 256), duración configurable 2–15s, toggle de compartir coordenadas en chat (lo único
  visible para otros desde un mod cliente). A no salta durante el zoom (misma regla que la cruceta).
  3 ajustes nuevos + i18n ×3. Fix: `addParticle` → `addParticleClient` en 1.21.10.
- [x] `mod_version` → **0.19.0**. **BUILD + 24/24 → `dist/steampad-0.19.0.jar`.** Checklist → B050.

---


## SESIÓN 24 cont. 4 (2026-07-09) — La captura reveló el entorno: Moonlight/Sunshine

- [x] **(1) Filtro de dispositivos fantasma (bug 5):** la captura mostró 6 "controles" — NVTK0603
  (pantalla táctil), Mouse/Touch/Pen passthrough (Sunshine), extest fake device (gamescope), Steam
  Virtual Gamepad (el pad REAL de la Ally reclamado por Steam). `ControllerManager.dropFakes()`
  descarta nombres con passthrough/extest/fake device de SDL3 y GLFW — ya no se listan ni se
  auto-activan (causa del mouse virtual loco al desconectar el 8BitDo). Ver D053.
- [x] **(2) Cámara de mouse re-diagnosticada como AMBIENTAL:** el mouse del usuario viaja por el
  "Mouse passthrough (absolute)" de Sunshine — un puntero absoluto no genera movimiento relativo
  para un cursor agarrado. 4 verificaciones de entorno para el usuario en B049 (vanilla vía
  Moonlight, toggle de Moonlight, Raw Input MC, mouse directo en la Ally). Fixes previos se quedan.
- [x] **(3) Mando de la Ally "no detectado":** SÍ está — es el "Steam Virtual Gamepad" (Steam lo
  reclama). Explicado en B049.
- [x] **(4) Teclado:** 0.55× (punto dulce declarado) rebasado como el nuevo 1.0× (CRUISE 11, MAX 45).
- [x] **(5) Vibración:** comer/beber (COSMETIC ~260ms), abrir cofre (crujido 130ms con la animación),
  quemarse (DANGER ~420ms continuo); startup 0.6f/250ms → 0.4f/90ms.
- [x] `mod_version` → **0.18.0**. **BUILD SUCCESSFUL + 24/24 TESTS → `dist/steampad-0.18.0.jar`.**
- [x] **v0.18.1 — gatillos en el aire:** `holdOnChange` ahora espeja a `Mouse.onMouseButton` en el
  flanco de subida (`setKeyPressed` + `onKeyPressed`) — RT golpea también sin objetivo (antes solo
  el camino continuo de minado funcionaba). Revisión de gameplay del dispatcher completa: resto OK.
- [x] **v0.18.1 — auto-golpe Bedrock (preguntado y APROBADO):** `attackAutoRepeat` default ON, toggle
  en Básico → Movimiento: RT sostenido re-golpea cuando el cooldown del arma se llena (auto-ritmo,
  sin timer), solo con la mira fuera de bloques (minado intacto), sin doble golpe en el flanco.
  i18n ×3. **BUILD + 24/24 → `dist/steampad-0.18.1.jar`.**
- [ ] Validación → B049 (las 4 verificaciones de la cámara son lo más valioso a reportar).

---


## SESIÓN 24 cont. 3 (2026-07-09) — Feedback de v0.16.0 + análisis del repo de Controlify

Resultados de v0.16.0: scroll+D-pad ✅, reset de zoom ✅, i18n ✅, panel ✅ ("todo lo demás parece que
funciona bien"). **Reprobados:** cámara de mouse ❌ (2 fixes no bastaron), Apuntador no entendido,
aim assist sigue sin sentirse. Nuevos: snap a botones de mods, defaults del teclado centrados.

- [x] **(1) Cámara de mouse, 3ª iteración — con el código real de Controlify (GitHub, sugerencia del
  usuario):** su `DualInput` valida nuestro merge de movimiento; su regla `canProcessLookInput` exige
  `isMouseGrabbed()` → NADIE pelea contra el agarre. Único hueco nuestro: desync `cursorLocked=true`
  pero modo GLFW real ≠ DISABLED (solo `VirtualCursorRenderer` toca `GLFW_CURSOR` directo). Fix 2
  capas SOLO gameplay: (a) `setOsCursorHidden` return si `isCursorLocked()`; (b) self-heal verifica
  el modo GLFW real y re-asserta DISABLED **con warning de confirmación en el log**. Ver D051.
- [x] **(2) Teclado:** Apuntador verificado (cableado correcto — es flick→soltar→A, explicado en
  B048); default queda Velocidad. Sliders recentrados: velocidad 0.5–1.5× (1.0×=centro), altura
  20–40% (30%=centro, default `GlobalConfig` 0.20→0.30). Fix de paso: el clamp interno de 22% en
  `KeyboardGeometry` anulaba media escala del slider viejo — ahora honra 20–40%.
- [x] **(3) Snap/D-pad sobre botones de MODS:** `SlotSnap` generalizado a slots + `ClickableWidget`s
  activos/visibles (mochila de mods, libro de recetas, ordenar...) — imán y `moveToNeighbor`
  unificados; `nearestSlot*` siguen slot-only (corchetes y quick-move necesitan slot real).
- [x] **(4) Aim assist v3:** hallazgo real — con reduce-aim activo TODO iba al 45% cargando el arco →
  la fricción relativa desaparecía; ahora el assist OMITE ese ×0.45 cuando tiene objetivo. Fricción
  `sqrt(closeness)` (se siente al entrar al cono), 0.30 de piso, magnetismo 16°/s, y **compensación
  de caída** (punto de assist elevado `dist²×0.0028`, tope 2.5) — el punto pegajoso está donde hay
  que apuntar de verdad con arco/ballesta/tridente. Ver D052.
- [x] `mod_version` → **0.17.0**. **BUILD SUCCESSFUL + 24/24 TESTS → `dist/steampad-0.17.0.jar`.**
- [ ] Validación → B048. La cámara de mouse ahora es un experimento concluyente: funciona + línea
  "Repaired a GLFW cursor-mode desync" en el log = causa confirmada; muerta sin la línea = otra causa.

---


## SESIÓN 24 cont. 2 (2026-07-09) — Feedback de v0.15.0 ("todo lo demás funciona bien") + lote de 7

Resultados de hardware sobre v0.15.0: mouse-atorado ✅ RESUELTO (D046 confirmado), chat sobre teclado
✅, editor radial ✅, cámara AAA ✅, glifos ✅ — "aún no detecto bugs". **Reprobados:** entrada mixta
parcial (cámara de mouse ❌), stick del teclado aún no convence, aim assist imperceptible.

- [x] **(1) Cámara de mouse en gameplay:** diagnóstico por descarte contra bytecode — único estado
  consistente: cursor sin candado GLFW (puntero al borde → deltas cero; clicks siguen pasando).
  `lockCursor()` hace return sin foco → menú cerrado sin foco deja el gameplay des-bloqueado. Fix:
  auto-candado por invariante en `tickInGame` (si `!isCursorLocked() && isWindowFocused()` →
  `lockCursor()`). Honestidad en D049: causa no reproducida; si persiste, reportar si revive tras un
  click en el mundo.
- [x] **(2) Teclado:** velocidad general −15% (CRUISE 20, MAX 82) + nueva opción **"Modo del stick"**:
  Velocidad (actual) vs **Apuntador** (mapeo absoluto estilo Steam Big Picture — el stick apunta
  directo a la zona del teclado, soltar conserva la tecla; flick→soltar→A). Ver D050.
- [x] **(3) Aim assist reforzado:** cono 3.5°/×2.6 (era 2°/×2.2 — engagement tan raro que parecía
  apagado), fricción a 0.35, magnetismo 12°/s desde deflexión 0.02, rango 28. Jugadores YA contaban
  como objetivo (LivingEntity ⊃ PlayerEntity) — documentado explícito.
- [x] **(4) Scroll + D-pad:** `focusMoveDir` arranca en la primera fila VISIBLE si el foco quedó fuera
  del viewport (la posición de scroll es la intención del usuario) — ya no regresa al inicio.
- [x] **(5) Zoom:** nueva opción "Restablecer zoom al soltar" (`zoomResetOnRelease`, default OFF =
  conducta actual); ON descarta los ajustes de cruceta al soltar y vuelve al nivel configurado.
- [x] **(6) Auditoría i18n LIMPIA:** 393 claves idénticas ×3 idiomas (diff automatizado), claves de
  código todas presentes (los `steampad_*` sueltos son IDs de acciones VDF), familias dinámicas de
  enums completas. Fallback a inglés = comportamiento nativo de MC.
- [x] **(7) Panel de diagnóstico (Selección de control) a 0.75×** con traslación+escala de matriz;
  fondo y anclaje recalculados (`PANEL_CONTENT_H`).
- [x] i18n ×3: stick_mode (4 claves), zoom_reset (2).
- [x] `mod_version` → **0.16.0**. **BUILD SUCCESSFUL + 24/24 TESTS → `dist/steampad-0.16.0.jar`.**
- [ ] Validación en hardware pendiente → B047 (la prueba clave: cámara de mouse en gameplay).

---

## SESIÓN 24 cont. (2026-07-09) — Feedback de hardware sobre v0.14.0 + lote de 8 puntos

Resultados de hardware reportados por el usuario: **B043 (vibración Tier 1+2) ✅ "funciona muy bien"**,
**B044 (crash de Ajustes) ✅ sin crash** — primeras validaciones positivas del bloque de vibración.
**Reprobado:** el fix v0.14.0 del mouse-atorado NO resolvió el caso (ver punto 1), y la velocidad del
stick del teclado seguía sin convencer (punto 2).

- [x] **(1) CAUSA RAÍZ REAL del mouse-atorado** (v0.14.0/captureMode era un bug real pero no ESTE):
  verificado con javap que `MinecraftClient.openGameMenu` tiene `if (currentScreen != null) return;`
  — el focus-pause espurio SOLO dispara desde gameplay. `PauseGate.shouldSuppress()` cancelaba
  también los `setScreen(GameMenuScreen)` legítimos (el `close()` de una hija volviendo al menú de
  pausa) → la cadena B→B→B se atascaba en ese salto con la ventana sin foco. Fix: guard
  `currentScreen != null → false` en `shouldSuppress()`. Universal (vanilla Options, ajustes del
  gamepad, pantallas de mods). Ver D046.
- [x] **(2) Stick del teclado v3 — doble zona + freno:** 85% del recorrido = precisión (cuadrática
  hasta 24 px/tick), turbo 95 px/tick solo en el último tramo; freno por desaceleración (soltar desde
  turbo corta la velocidad a 30% y el imán agarra la tecla apuntada: pull 0.5 frenando, 0.85 al
  aflojar). El slider del usuario sigue multiplicando encima.
- [x] **(3) Chat empujado sobre el teclado (Controlify-style):** las sugerencias de comandos anclan
  en `owner.height - 12` HARDCODED (verificado en bytecode) → mover el campo no bastaba. 2 mixins
  nuevos (`ChatScreenMixin`, `ChatHudMixin`) trasladan con matrices campo + franja + sugerencias +
  historial por la altura del teclado; el teclado ahora va al ras del fondo (pad de 16px eliminado).
- [x] **(4) Editor radial reestructurado:** filas separadas y etiquetadas "Rueda 1/3" (◀ ▶, "+ Rueda",
  "− Rueda") y "Espacios: N" (− +); tema movido a la nueva pantalla **"Apariencia"**
  (`RadialStyleScreen`, botón junto a Listo): radio 54–130, tamaño de espacios 12–26
  (`RadialConfig.chipRadius` nuevo, chips grandes abren la rueda), fondo on/off, tema AL FINAL, previo
  de rueda en vivo.
- [x] **(5) Cámara AAA:** curva de potencia sobre MAGNITUD (exponente `lookCurve` 2.2 default,
  1.0–3.0), yaw 260°/s / pitch 195°/s separados, edge boost 1.65× yaw con delay 0.15s + rampa
  smoothstep 0.35s (`lookTurnBoost`). 2 ajustes nuevos en Básico → Sensibilidad. Ver D047.
- [x] **(6) AIM ASSIST de proyectiles:** nuevo `input/AimAssistController` — SOLO cargando
  arco/ballesta/tridente (UseAction BOW/CROSSBOW/SPEAR): fricción hasta 0.45× sobre objetivo (cono
  angular escalado por distancia) + magnetismo ≤8°/s solo con stick activo; `canSee` obligatorio,
  rango 24 bloques. Sección "Asistencia de apuntado" (toggle + fuerza %). Ver D047.
- [x] **(7) Glifos en tiempo real:** RADIAL/CHAT (izq) y ZOOM (der) añadidos al HUD de gameplay —
  derivados de binds en vivo, solo aparecen si tienen botón asignado.
- [x] **(8) Entrada mixta reparada (2 bugs):** `KeyboardInputMixin` overwrite→MERGE (stick solo toma
  el vector empujado; booleanos OR con teclado — WASD/Space/Shift vivos con pad conectado);
  `hold()` de attack/use/playerList por FLANCO (`holdOnChange`) — el click sostenido del mouse ya no
  se corta. Ver D048.
- [x] i18n ×3: curva/turbo/aim assist (7 claves), radial Apariencia (12 claves), hud radial/chat/zoom.
- [x] `mod_version` → **0.15.0**. **BUILD SUCCESSFUL + 24/24 TESTS → `dist/steampad-0.15.0.jar`.**
- [ ] Validación en hardware pendiente de TODO el lote → B046 (checklist completo).

---

## SESIÓN 24 (2026-07-09) — Lote de 4 fixes/features pedidos tras probar v0.13.2

El usuario probó v0.13.2 (fix de crash + TitleScreenMixin corregido) y en el mismo mensaje pidió 4
cosas puntuales, todas resueltas en esta sesión. Constrainsts estándar: "no rompas nada, programa
limpio, documenta" + "si es sustancial pregúntame primero" (nada de este lote requirió preguntar —
ninguno era una corrección sustancial de una decisión previa, a diferencia de la sesión 23).

- [x] **(1) Stick del teclado virtual — "muy rápido", pidió rápido pero controlable.**
  `VirtualKeyboard.java`: `FLOAT_MAX_SPEED` 95→62 px/tick (a fondo), `FLOAT_CURVE` 2.4→2.7 (más fino
  cerca del centro), `SETTLE_MAG` 0.12→0.16 y `PULL_SETTLE` 0.5→0.7 (snap mucho más agresivo al
  aflojar el stick, tal como pidió el usuario). Además, nuevo `GlobalConfig.virtualKeyboardStickSpeed`
  (multiplicador 0.5×–2.0×, default 1.0×) aplicado directamente en `stickFloat()`, con un **slider
  "Velocidad del stick" en `KeyboardSettingsScreen`** para que el usuario ajuste fino sin más código.
- [x] **(2) Previo de color donde se escoge el tema del teclado.** Nuevo método público
  `VirtualKeyboardRenderer.renderThemePreview(ctx, tr, x, y, w, h, theme)` — dibuja una franja de 3
  teclas de muestra (A, S, espaciadora) reutilizando la MISMA `palette()`/`drawKey()` que usa el
  teclado real (no hay tabla de colores duplicada, lo que ves es exactamente lo que obtienes).
  `KeyboardSettingsScreen` reserva espacio bajo el selector de tema y lo dibuja en vivo — cambia al
  instante al ciclar entre los 8 presets.
- [x] **(3) El fix del "mouse atorado" (D037/sesión 17) reaparece dentro de Ajustes del gamepad.**
  El usuario reportó que si el mouse sale de la ventana estando en `BindingsScreen` (Ajustes del
  gamepad), después no se puede salir ni siquiera del menú del juego con el mando — no solo de la
  pantalla de ajustes. **Causa estructural:** `GamepadInputDispatcher.captureMode` es un flag que SOLO
  `BindingsScreen` pone en `true`/`false`, y bloquea TODO `tickGui()` mientras esté en `true` — pero
  `MinecraftClient.setScreen()` vanilla NO llama al `close()` de la pantalla saliente (solo
  `removed()`), así que si `PauseGate` deja pasar un `setScreen(GameMenuScreen)` espurio por pérdida
  de foco mientras `BindingsScreen` sigue "capturando", `captureMode` queda huérfano en `true` para
  siempre. **Fix (auto-sanación, ver D045):** al inicio de `GamepadInputDispatcher.tick()`, si
  `captureMode==true` y la pantalla activa NO es `BindingsScreen`, se fuerza `false`
  incondicionalmente — recupera la invariante sin importar cómo se perdió.
  **Honestidad:** no se pudo reproducir interactivamente el gesto exacto (mouse fuera de la ventana
  ESTANDO en Ajustes) en este entorno de desarrollo — el fix está confirmado correcto a nivel de
  invariante estructural, pero sigue pendiente de validación 1:1 en hardware (ver B045).
- [x] **(4) Los mismos temas de color del teclado, también en el menú radial, con su propio previo.**
  `PixelTheme` (los 8 valores: VANILLA/OAK/STONE/EMERALD/REDSTONE/LAPIS/AMETHYST/NETHER) extraído de
  `GlobalConfig.KeyboardTheme` a `config/PixelTheme.java` (tipo compartido, ver D044 — Gson serializa
  por nombre de valor, migración retrocompatible con configs guardados sin tocar nada). Nuevo campo
  `RadialConfig.theme`. `RadialRenderer` gana `record Palette` + `palette(PixelTheme)` (mismo patrón
  que el teclado) aplicado a fondo de chips, backdrop, anillo de selección/acento y color del texto de
  las pistas de control — la gelatina (E11) y las siluetas de rueda fantasma se dejaron neutrales
  (blanco) a propósito, son decoración, no superficies principales de tema. `RadialEditorScreen` gana
  un control cíclico "Tema" justo bajo la fila de cambio de rueda; el previo YA EXISTÍA (la rueda en
  vivo a la derecha del editor ya llama a `RadialRenderer.render(...)` en cada frame), así que cambiar
  el tema se refleja al instante sin necesidad de ningún widget de previo adicional.
- [x] i18n: 2 claves nuevas (`steampad.keyboard.stick_speed[.desc]`, `steampad.radial.theme[.desc]`)
  en `en_us.json`/`es_mx.json`/`es_es.json`. Las 8 etiquetas de valor de tema (`steampad.keyboard.theme.*`)
  se reutilizaron tal cual para el radial — no hicieron falta 8 claves nuevas.
- [x] `mod_version` 0.13.2 → **0.14.0**. Fix de compilación de paso: un import roto
  (`import PixelTheme;` sin paquete, arrastrado de una edición anterior de `VirtualKeyboardRenderer.java`)
  se corrigió a `import dev.steampad.config.PixelTheme;` antes de que el build pasara.
- [x] **BUILD SUCCESSFUL + 24/24 TESTS → `dist/steampad-0.14.0.jar`.**
- [ ] Pendiente: validación en hardware de los 4 puntos — ninguno se ha probado todavía en el Deck.
  Ver TODO_BLOCKERS.md B045 para el checklist completo (se suma a B043/B044, aún abiertos también).

---

## SESIÓN 23 cont. (2026-07-09) — "Quita la A y la B" era el TitleScreenMixin, no el icono de Ajustes

- [x] El usuario aclaró: "quitar la A y la B" se refería a dos botones en el **menú principal**, uno
  para salir del juego (B) — es decir, el `TitleScreenMixin` (glifos X/B junto a Opciones/Salir) que
  YO había reactivado por error en la auditoría de la sesión 20, creyendo que un mixin sin registrar
  era un descuido. En realidad había sido removido a propósito en una sesión anterior.
- [x] `TitleScreenMixin.java` eliminado por completo (verificado con grep que nada más lo referenciaba)
  — no solo desregistrado de `steampad.mixins.json`, para que no pueda "reaparecer" en una futura
  auditoría de código muerto.
- [x] El cambio del icono monocromo en Ajustes (mi primera interpretación, incorrecta) se mantiene de
  todas formas — es un cambio válido por sí mismo, solo no era lo que se pedía originalmente.
- [x] Lección documentada en DECISIONS.md D043: un mixin sin registrar no es automáticamente un bug.
- [x] **BUILD SUCCESSFUL + 24/24 TESTS → `dist/steampad-0.13.2.jar`**, confirmado que la clase ya no
  está empaquetada en el jar.
- [ ] Pendiente: el usuario confirme en hardware que el menú principal ya no muestra los glifos X/B,
  y que el crash de Ajustes (B044) tampoco vuelve a aparecer.

---

## SESIÓN 23 (2026-07-09) — Primera validación real en hardware: crash + fix de raíz

El usuario probó `steampad-0.13.0.jar` en su Steam Deck (Bazzite, modpack de 80 mods) — primera vez
que algo de las sesiones 21-22 se prueba en hardware real. Resultado: **crash** al entrar a Ajustes
de Minecraft (`IncompatibleClassChangeError`).

- [x] **Diagnóstico:** `OptionsScreenMixin$GamepadButton` (el botón de entrada a SteamPad junto a
  "Controls") era la ÚNICA clase anidada dentro de una clase `@Mixin` en todo el proyecto (confirmado
  con grep). Mixin reescribió mal su atributo bytecode `InnerClasses` para apuntar a `ButtonWidget`
  (su superclase) en vez de su clase contenedora real — invisible hasta que algo hace reflexión sobre
  una instancia. `VirtualKeyboard.isTextWidget()` (código nuevo de la sesión 22, detección universal
  de campos de texto) fue lo que lo destapó al llamar `getClass().getSimpleName()` — pero el defecto
  bytecode ya existía desde antes, sesiones atrás; el código nuevo no lo causó, solo lo hizo visible.
- [x] **Fix de raíz:** `GamepadButton` extraído a clase de nivel superior `client/ui/GamepadOptionsButton`,
  fuera de cualquier `@Mixin` — Mixin ya no toca su `InnerClasses`.
- [x] **Fix defensivo (protege contra otros mods, no solo este caso):** `isTextWidget()` ahora atrapa
  `Throwable`, no `Exception` — `IncompatibleClassChangeError` es un `Error`. Con 80 mods instalados,
  cualquier otra clase mal formada por el mixin de OTRO mod podría dar el mismo problema.
- [x] Cambio cosmético de paso: quitados los 2 acentos de color (azul/blanco) de los botones de cara
  del icono del gamepad en Ajustes, ahora monocromo — interpretación de "quita la A y la B" del
  usuario, **sin confirmar todavía**.
- [x] **BUILD SUCCESSFUL + 24/24 TESTS → `dist/steampad-0.13.1.jar`.**
- [ ] Pendiente: el usuario confirme que el crash ya no ocurre, y que el cambio del icono era lo que
  pedía (o indique el elemento de UI correcto si no).

---

# PROGRESS.md — Sesión 22 cont. (vibración Tier 2, v0.13.0)

## SESIÓN 22 cont. (2026-07-09) — Tier 2 completo: scheduler de prioridad + 8 eventos + tesoro filtrado

Diseño discutido y aprobado en conversación con el usuario (comparó RDR2, God of War, Cyberpunk 2077,
Silent Hill 2, Forza — el usuario aportó su propio análisis muy completo). Se acordó: dejar fuera
textura de superficie al caminar (mayor riesgo de saturación, peor encaje con hardware de 2 motores) y
cofre/tesoro enterrado genérico (sin criterio confiable); SÍ implementar dungeon-chest con un filtro de
3 señales resuelto en conversación (spawner cercano = dungeon real; punto de spawn/cama = "es mi casa",
vía `ClientWorld.getSpawnPoint()` que sí sincroniza el respawn point real del jugador; ya abierto =
no repetir, set en memoria).

- [x] **`HapticsController` reescrito con árbitro de prioridad** (`Tier`: CRITICAL > DANGER > IMPACT >
  AMBIENT > COSMETIC) — necesidad técnica real, no solo estética: el hardware es UN canal de rumble,
  dos eventos no pueden sonar literalmente a la vez, así que el de menor prioridad se descarta en
  silencio mientras el de mayor sigue "ocupando" el canal (estimado por duración enviada).
- [x] Portal del Nether: ping periódico que se acelera con la cercanía (no rumble continuo — más barato
  y se siente "búsqueda", no "zumbido"). Tier DANGER, balance grave/pesado.
- [x] Creeper cargando: pulsos cada vez más juntos mientras el fuse cuenta (`CreeperEntity.isIgnited()`,
  tiempo propio desde que se detectó encendido — no depende de leer el fuse interno real). Tier DANGER.
- [x] Warden cerca: rumble bajo, espaciado, "opresivo" (balance muy grave) — escaneo de entidad como
  el creeper. Tier DANGER.
- [x] Geoda de amatista cerca: un ping limpio de "descubrimiento" al entrar en rango, silencio hasta
  salir y volver a entrar. Tier COSMETIC (sin falsos positivos posibles — es 100% natural).
- [x] Hambre crítica (≤6, el mismo umbral que usa vanilla para bloquear el sprint), ahogo (acelera cerca
  del límite de aire, refuerza la UI de burbujas sin reemplazarla), congelación en powder snow (temblor
  irregular con intervalo e intensidad aleatorios, no un ritmo limpio) — los 3 extienden el patrón de
  heartbeat de Tier 1 con timings distintos para no confundirse entre sí.
- [x] Caída + aterrizaje independiente del daño real: antes solo vibraba si hubo daño; ahora cualquier
  caída ≥3 bloques da un golpe de impacto (amortiguado si aterrizas en agua).
- [x] Minería por valor: común=tick mínimo (ya existía), mineral=toque sólido, diamante/esmeralda/ancient
  debris=pulso limpio y más largo promovido a tier IMPACT (para que nunca se pierda bajo otra cosa).
- [x] **Cofre de tesoro con el filtro de 3 señales:** un solo escaneo de bloques combinado (portal +
  geoda + spawner + cofre, un solo triple-loop en vez de 4 separados) detecta cofre cerca de spawner
  (dungeon real) Y no cerca del punto de spawn/cama del jugador (`ClientWorld.getSpawnPoint().globalPos()`,
  confirmado que refleja el respawn REAL del jugador, no el spawn del mundo, verificado por cómo
  `PlayerSpawnPositionS2CPacket` alimenta ese campo) Y no abierto antes (`UseBlockCallback` de Fabric
  API registra la posición al abrir, set en memoria — se reinicia solo al cerrar el juego).
- [x] Todas las firmas de API verificadas con javap ANTES de escribir código: `ClientWorld.getSpawnPoint`,
  `CreeperEntity.isIgnited/getLerpedFuseTime`, `WardenEntity`, `HungerManager.getFoodLevel`,
  `Entity.getAir/getMaxAir/getFrozenTicks/isFrozen`, `UseBlockCallback`, `ActionResult.PASS`,
  `BlockPos.getSquaredDistance`, todos los `Blocks.*_ORE`/`ANCIENT_DEBRIS`.
- [x] **BUILD SUCCESSFUL a la primera + 24/24 TESTS → `dist/steampad-0.13.0.jar`.**
- [ ] Validar Tier 2 completo en hardware — checklist en B043 (actualizado).

---

# PROGRESS.md — Sesión 22 (sistema de vibración AAA, v0.12.0)

## SESIÓN 22 (2026-07-09) — Vibración event-driven + fix de mixin dormido

Investigación previa: **Bedrock NO tiene vibración nativa** (feature pedida por años, nunca implementada
por Mojang — confirmado en feedback.minecraft.net). La referencia real más cercana es **Controlify**
(Java, vibra en daño/romper bloques/rayos). AAA: Returnal usa vibración ambiental continua para
presencia del entorno (lluvia); God of War Ragnarök usa el patrón "se hincha antes de pagar" (el hacha
recall "gradually swells" hasta un "meaty thud") — ese es justo el patrón para la idea del portal del
usuario, guardado para la Tier 2 de consulta (ver TODO_BLOCKERS B043).

- [x] **Hallazgo de paso:** `TitleScreenMixin.java` existía (glifos X/B en el título) pero NUNCA estuvo
  registrado en `steampad.mixins.json` — mixin completamente dormido desde que se escribió. Corregido
  (firma `render(DrawContext,int,int,float)` verificada con javap antes de activarlo).
- [x] **`ControllerManager.rumble`** ahora tiene overload asimétrico (low/high freq por separado) — es
  la única "textura" real que da el hardware (motor pesado vs. motor agudo, sin HD haptics, B003), y ya
  bastaba para simular "boom" vs. "buzz" sin costo extra.
- [x] **`haptics/HapticsController` (nuevo):** motor central, cablea las 6 categorías de
  `ControllerConfig` que existían en Ajustes pero no hacían nada. Eventos Tier 1 (reactivos, discretos):
  - Daño recibido (escala con la caída de HP — la caída de altura ya se siente más fuerte sin lógica
    especial), heartbeat de vida baja (<20%, se acelera acercándose a 0), muerte (pulso fuerte).
  - Golpe cuerpo a cuerpo con heurística de crítico (aproximación local de las condiciones de vanilla:
    en el aire, no en el suelo/agua/vehículo, sin ceguera — no garantiza el roll real del servidor).
  - Romper bloque (Fabric API `ClientPlayerBlockBreakEvents.AFTER`, verificado con javap: paquete real
    `net.fabricmc.fabric.api.event.client.player`, no `.player`).
  - Explosión cercana (mixin en `ClientPlayNetworkHandler.onExplosion`, escala por distancia real +
    boost si `playerKnockback()` está presente — el juego ya confirma que SÍ te dio).
  - Rayo cercano (poll cada 10 ticks de `LightningEntity` en radio 48, sin mixin nuevo).
- [x] Firmas verificadas con javap contra el jar mapeado 1.21.10 ANTES de escribir cada mixin
  (`onExplosion`, `attackEntity`, `Entity.getEntityPos()` — `getPos()` ya no existe, renombrado).
- [x] **BUILD SUCCESSFUL + 24/24 TESTS → `dist/steampad-0.12.0.jar`.**
- [ ] Propuesta Tier 2 (momentos AAA-inmersivos adicionales) — consultada al usuario, NO implementada
  sin confirmación. Ver B043.
- [ ] Validar Tier 1 en hardware — checklist en B043.

---

# PROGRESS.md — Sesión 21 (7 features/fixes: teclado responsivo+tema+detección, glifos pestañas, click muerto, radial previews, ZOOM)

## SESIÓN 21 (2026-07-09) — Lote de mejoras + feature Zoom

> **Si se interrumpe:** "Continúa SteamPad desde PROGRESS.md, sección S-N".
> Decisiones del usuario (preguntadas antes de empezar): color del teclado = PRESETS estilo MC
> (ciclables); ajuste de zoom en caliente = D-pad ↑/↓ mientras el zoom está activo (suprime acciones
> base del D-pad durante el zoom).

- ☑ **S1 — Stick izq del teclado más responsivo** (`VirtualKeyboard.stickFloat`): curva `mag^2.4`
  normalizada por dirección (diagonales = cardinales), velocidad máx 46→95 px/tick SOLO al fondo del
  stick; el imán ahora es suave (8%) mientras se empuja — el 45% previo a baja deflexión ATRAPABA el
  punto dentro de la tecla actual (esa era la falta de respuesta) — y fuerte (50%) solo al aflojar
  (mag<0.12) para aterrizar exacto en la letra.
- ☑ **S2 — Teclado tema vanilla MC pixel-art + presets de color:** `VirtualKeyboardRenderer` reescrito
  a estética de botón vanilla (contorno negro 1px nítido, bisel claro arriba/izquierda + bisel oscuro
  grueso 2px abajo, texto con sombra, sin esquinas redondeadas). 8 presets de color estilo materiales
  MC (`GlobalConfig.KeyboardTheme`: Vanilla/Roble/Piedra/Esmeralda/Redstone/Lapis/Amatista/Nether),
  paleta cacheada (cero costo por frame), ciclables en Ajustes de teclado (`cycling`), null-safe para
  configs viejos. i18n ×3.
- ☑ **S3 — Detección universal de campos de texto** (`VirtualKeyboard.findTextField`): (1) cadena de
  foco vanilla, (2) duck-typing por nombre de clase (TextField/EditBox/TextBox/TextInput) para widgets
  custom de mods, (3) barrido recursivo del árbol de widgets buscando un campo con `isFocused()`
  propio (el caso Xaero's: el mod enfoca el widget sin avisarle a la Screen). Entrega de texto en dos
  pasos: `Screen.charTyped/keyPressed` primero (ruta del teclado real) y si la screen no lo enruta,
  directo al widget encontrado. Aplica también a preview y click-para-abrir.
- ☑ **S4 — Glifos LB/RB en las pestañas:** `SettingsTabs` reserva 18px a cada lado de la fila de
  pestañas y expone `renderGlyphs()` (mismos x/y/totalW que `add()`); los 3 screens con pestañas
  (Básico/BOTONES/Avanzado) lo llaman en su render. `ButtonIcon` ya resuelve marca→textura→vector,
  así que los glifos salen con el arte del mando activo automáticamente.
- ☑ **S5 — Click de ratón muerto — CAUSA RAÍZ + fix en 3 capas:** el bug NO era de foco: `hasActivity()`
  contaba los gatillos con umbral `>0` — cualquier ruido eléctrico del eje marcaba GAMEPAD cada tick,
  `markMouse()` nunca aterrizaba, el cursor virtual visible quedaba DESINCRONIZADO del puntero real
  (los movimientos físicos sí pasan y mueven la posición interna de MC) y los clicks caían donde no
  se veía → "el ratón no puede hacer clic". Fixes: (1) `hasActivity()` usa `trigDown()` (umbral
  configurado) para gatillos; (2) `InputRouter.markMouseForce()` — un barrido >20px o un CLICK físico
  es acción humana inequívoca y gana SIEMPRE sobre la ventana anti-fantasma; (3) mixin nuevo en
  `Mouse.onMouseButton`: click físico → fuerza MOUSE + cede el cursor virtual (guard `INJECTING` para
  los clicks virtuales de `ActionExecutor.pressMouseButton`, ahora envueltos). Cubre pausa, ajustes,
  y cualquier pantalla en cualquier escenario — el peor caso posible es UN click perdido, nunca un
  menú muerto.
- ☑ **S6 — Radial: carrusel visual completo:** el mini-círculo pobre de un solo lado se reemplazó por
  `drawGhostWheel()` — silueta de la rueda ANTERIOR (izquierda) y SIGUIENTE (derecha), cada una con su
  número REAL de chips (`cfg.slotCountFor(página vecina)`) distribuidos igual que la rueda viva, anillo
  tenue + punto central + glifo LB/RB debajo de cada una. Siguen el offset del carrusel al cambiar de
  página. Solo se dibujan con 2+ ruedas; costo: un puñado de fills.
- ☑ **S7 — ZOOM estilo BetterZoom, nativo para mando (¡funcional!):** diseño extraído del código real
  de BetterZoom (rama 1.21.1-neoforge: factor = zoomFov/fovOpciones clampeado, easing smoothstep
  `e·e·(3−2e)`, paso por scroll, hold/toggle, bobbing off, sensibilidad auto). Implementación SteamPad:
  - `input/ZoomController` (nuevo): estado estático; **fast-path idle = 1 comparación** (cero costo
    inactivo); easing normalizado a delta-time real (misma velocidad a cualquier FPS); bobbing
    guardado/restaurado con restauración defensiva; el nivel ajustado con la cruceta se persiste UNA
    vez al soltar el zoom (no por paso).
  - `GameRendererMixin`: @Inject en `getFov(Camera,F,Z)F` (firma verificada con javap contra el jar
    mapeado 1.21.10) — multiplica solo el FOV del MUNDO (`changingFov=true`, la mano no se deforma).
    El placeholder D018 por fin tiene su hook real.
  - `GamepadBinds.Bind.ZOOM` (held, SIN botón por defecto) + fila en ActionCatalog → aparece en
    BOTONES con rebind y chord gratis.
  - Dispatcher: hold o toggle según config; cruceta ↑/↓ ajusta nivel durante el zoom con supresión de
    las acciones base de DUP/DDOWN (`zoomEatsDpad`, cubre binds Y extra binds); liberación defensiva
    al abrir cualquier pantalla y al desconectar el mando (nunca queda el FOV atascado).
  - `CameraController`: velocidad de cámara × factor de zoom (auto = sigue el easing; fijo = slider).
  - Sección "Zoom" en Avanzado (9 opciones, equivalentes a las de BetterZoom); i18n ×3.
- ☑ **S8 — BUILD SUCCESSFUL + 24/24 TESTS → `dist/steampad-0.11.0.jar`** (ZoomController verificado
  dentro del jar). Docs actualizados.

---

# PROGRESS.md — Sesión 20 (auditoría de código + limpieza + fixes, v0.10.6)

## SESIÓN 20 (2026-07-09) — Auditoría de código a petición del usuario, B040 en pausa a propósito

El usuario pidió dejar B040 (Steam Input nativo en Game Mode) en pausa — está fuera de casa y no puede
probar — y en su lugar auditar el código en general: orden/organización, limpieza, y bugs reales, sin
romper nada. Checklist de esta sesión:

- [x] **Sección 1 — Fan-out de auditoría por subsistema.** Se lanzaron 4 subagentes Explore en paralelo
  (`radial/+mixin/+entry points`, `input/`, `steam/config/service/platform/compat/`, `screen/client-ui`).
  Solo el de `radial/+mixin/` completó — los otros 3 murieron por **límite de sesión de Claude**
  ("You've hit your session limit"). El resto de la auditoría se hizo a mano (Read/Grep directo),
  priorizando los archivos de mayor riesgo/historial de bugs en cada área.
- [x] **Sección 2 — Bugs confirmados y corregidos (4):**
  - `RadialMenuController.openSubmenu()`: `open()` no-opeaba por su propio guard `if (open) return`
    cuando se llamaba desde `confirmSelection()`/`activateSelected()` (con `open` aún en `true`) → el
    tipo SUBMENU nunca reabría la rueda salvo desde `close()`. Fix: fuerza `open=false` antes de reabrir.
  - `RadialRenderer.getConfig()` leía el mando GLOBALMENTE activo (`ActiveControllerService`) en vez del
    handle que realmente estaba mostrando `RadialMenuController` (o el que edita `RadialEditorScreen`)
    — podían divergir un tick y estilizar con el config equivocado. Fix: el handle ahora se pasa
    explícito en cada llamada a `RadialRenderer.render(...)`.
  - Tipo de slot radial no reconocido (typo/config corrupta) fallaba a `NONE` sin loguear, a diferencia
    de los demás fallbacks similares en la misma clase. Fix: `LogUtil.debug` añadido.
  - `SteamPadClient.ensureFallbackBackendsInit()` marcaba el flag "ya hecho" ANTES del `try` — si algo
    fallaba en el primer intento (GamepadMappings/SDL3/ControllerClaimService/restore), el mod quedaba
    sin esos backends el RESTO DE LA SESIÓN, sin más reintentos. Fix: retry cada ~1s por ~10s (mismo
    patrón que el retry de ActionSets de `SteamBootstrap`).
- [x] **Sección 3 — Fix de robustez (no gameplay, pero real):** `JsonUtil.saveToFile()` escribía directo
  al archivo destino; un crash/corte de luz a mitad de escritura deja el JSON corrupto (ya pasó una vez,
  B016 con el cache de Loom) — y el mod autoguarda en cada cambio, así que el riesgo está presente toda
  la sesión, no solo al cerrar. Fix: escribe a `.tmp` y hace `Files.move` atómico.
- [x] **Sección 4 — Limpieza de código muerto:** imports sin uso (`MinecraftClient` en
  `ItemIconProvider`/`EffectIconProvider`; `ControllerSelectScreen`/`ClipboardDebugService` en
  `ActionExecutor`) y accessors estáticos de `RadialMenuController` sin ningún call site
  (`getSlotCount()`, `hasMultipleWheels()`) — verificados con grep de todo el repo antes de borrar.
- [x] **Sección 5 — Hallazgo real, documentado pero NO corregido** (riesgo de romper bindings del
  usuario sin poder validar en hardware): el config por-mando usa como clave el handle sintético de
  GLFW/SDL3 (índice de slot de joystick), que NO es estable entre sesiones — si el orden de conexión de
  varios mandos cambia, el usuario puede ver sus binds/radial "reseteados a defaults" sin haber tocado
  nada. `ActiveControllerService` ya resuelve el mando ACTIVO por nombre pero eso no migra los archivos
  de config. Documentado como **B041** en TODO_BLOCKERS.md con plan de fix propuesto (reusar el patrón
  de clave estable nombre+ordinal que YA usa `ControllerClaimService.keyFor()`).
- [x] **Sección 6 — Build + tests:** `BUILD SUCCESSFUL`, 24/24 tests, versión bump 0.10.5 → 0.10.6 (jar
  en `dist/`). Sin cambios de gameplay/UI visibles — nada que validar en hardware para esta sesión.
- [x] **Sección 7 — Docs actualizados:** STATE.md, TASKS.md (FASE 23), TODO_BLOCKERS.md (B041),
  PROGRESS.md (este archivo). B040 permanece exactamente donde estaba, a propósito.

**Continuación si se interrumpe:** esta sesión ya terminó completa (todas las secciones ☑). La próxima
sesión debería retomar B040 (ver STATE.md "▶ REANUDAR AQUÍ") o, si el usuario lo pide, atacar B041.

---

# PROGRESS.md — Sesión 19 (v0.10.0 a v0.10.5 + investigación en curso: Steam Input Slots)

## SESIÓN 19 cont. 6 (2026-07-08) — SOLO ANÁLISIS, sin código — el usuario exige Steam Input nativo en Game Mode (B040)

- El usuario probó v0.10.5 (F13-F22) — confirmó que técnicamente es correcto (Valve lo documenta como
  vía oficial para apps no publicadas), pero **lo rechaza como solución final**: el objetivo del
  proyecto es que SteamPad se comporte como un juego 100% nativo de Steam Input en Game Mode, con las
  10 ranuras apareciendo NOMBRADAS en el propio menú de Steam (como cualquier AAA compatible) — no
  requerir que el usuario mapee manualmente F13-F22 como intermediario.
- Se le explicó y aclaró por qué se usa AppID 480 (Spacewar): mecanismo oficial de Valve para apps no
  publicadas en Steam, IDÉNTICO para todos los usuarios del mod (no depende de cómo cada quien nombró
  su acceso directo) — descartando la idea de que era "solo para pruebas" o que dependía del setup de
  cada usuario.
- 🔍 **Nueva hipótesis (sin confirmar) sobre por qué `SteamAPI.init()` falló en Game Mode** (revisando
  el log de la sesión 19 cont. 5, 21:45): en escritorio Steam no tiene "un juego actual" fijado a
  nivel de sesión — cualquier proceso que reclame ser 480 es aceptado (por eso ahí SÍ conectó). En
  **Game Mode, Steam SÍ tiene un juego actual fijado**: el acceso directo del usuario, con su propio
  AppID auto-generado al agregarlo como "Juego que no es de Steam". Minecraft, corriendo DENTRO de ese
  acceso directo, intenta reclamar independientemente ser el AppID 480 (distinto al que la sesión de
  Steam ya tiene fijado) — probablemente choca con el seguimiento de "un solo juego activo a la vez".
- **Plan propuesto (NO implementado, pendiente de diagnóstico):**
  1. Detectar el AppID real que Steam pasa al proceso vía `SteamAppId`/`SteamGameId` (variables de
     entorno que Steam hereda a los procesos hijos del acceso directo, incluido el JVM anidado en Sway).
  2. En gamescope, usar ese AppID real en vez del 480 hardcodeado.
  3. El mod escribiría su propio `game_actions_<APPID>.vdf` automáticamente al detectar su AppID real
     — sin que el usuario copie nada a mano.
  4. Se descartó la idea del usuario de "subir una plantilla al Taller de Steam Community" — más
     simple y confiable que el mod se autoconfigure, y evita que el Taller de 480 (compartido por
     muchos proyectos no relacionados) sea un cajón confuso para buscar "SteamPad".
  5. Paso manual irreducible en cualquier caso (pasa en TODO juego con Steam Input): el usuario abre
     la config de mando de Steam una vez para bindear botón físico → acción nombrada.
- **Diagnóstico pendiente, EN PAUSA** (usuario fuera de casa): correr, con Minecraft VIVO en Game Mode
  (sin cerrarlo), vía SSH o **Decky Terminal** (confirmado que sirve — terminal flotante sin salir del
  juego en Deck/handhelds Bazzite):
  ```bash
  cat /proc/$(pgrep -f "PrismLauncher.AppImage")/environ | tr '\0' '\n' | grep -i steam
  ```
- **NADA de código tocado esta sesión** (petición explícita: "no programes hasta que te diga"). El jar
  vigente sigue siendo `steampad-0.10.5.jar`. B039 (F13-F22) permanece como fallback funcional
  validado en concepto, no se descarta aunque B040 tenga éxito.

---

## SESIÓN 19 cont. 5 (2026-07-08) — v0.10.5 — Ranuras por tecla F13–F22 + icono del mod (D034, B039)

**Resultado de validar v0.10.4 en hardware:**
- ✅ **Escritorio: PERFECTO** ("funciona tal cual lo describes") — 8BitDo de vuelta, todo responde.
- **Game Mode:** `Gamescope=true` bien detectado, attach intentado (AUTO) pero `SteamAPI.init()`
  devolvió **false**: el acceso directo no-Steam (sway) ya ocupa el slot de "juego corriendo" con su
  propio AppID — no se puede suplantar a Spacewar desde dentro. El fallo fue BENIGNO y revelador:
  sin attach, Steam mantiene sus gamepads virtuales y AMBOS mandos funcionaron completos por SDL3.
  Los paddles quedaron en manos de la disposición de Steam del acceso directo (usuario los tenía en A/B).

**v0.10.5 — la vía universal (D034):**
- ☑ Cada Ranura N escucha además la tecla **F(12+N)** (Ranura 1=F13 … 10=F22) vía `glfwGetKey` en
  `SteamSlotDispatcher.tick()` (fuente dual: acción Steam O tecla F; mismo edge-detect/HOLD/gate).
- Flujo Game Mode SIN nada de Steam API: disposición del acceso directo → paddle → F13 → Ranura 1 →
  keybind de BOTONES. Sin AppID, sin VDF, sin attach, sin secuestro; cross-platform.
- ☑ `steamAttachMode` default → **NEVER** (attach solo tiene sentido lanzando MC desde Steam como
  título real con disposición completa; el camino VDF/ActionSets sigue intacto para ALWAYS).
- ☑ UI: etiquetas "Ranura N (FX)"; descripción y avisos reescritos ×3 idiomas (la vía F-key siempre
  disponible; en escritorio se recomienda P1..P4 crudos).
- ☑ **Icono del mod:** `fabric.mod.json` declaraba `assets/steampad/textures/gui/icon.png` pero el
  archivo NUNCA existió (carpeta vacía) — generado pixel-art ABXY 128px (diamantes YXBA con letras).
  El usuario pasará su arte original por LocalSend → sobrescribir ese archivo y recompilar.
- Limitación documentada: teclas F sintéticas → solo la ventana con FOCO (multi-instancia: los
  paddles actúan en la instancia enfocada; los botones normales siguen aislados por mando/claims).
- **Build:** `BUILD SUCCESSFUL` + 24/24 tests → **`dist/steampad-0.10.5.jar`** (icono verificado en el jar).
- ⚠️ Sin validar. Checklist → B039.

---

## SESIÓN 19 cont. 4 (2026-07-08) — v0.10.4 — CAUSA RAÍZ: conectarse a Steam secuestra los mandos (D033, B038)

- Con v0.10.3 (SDL3 primario) el problema PERSISTIÓ: 8BitDo invisible para SDL3 (aunque
  `bluetoothctl` lo confirmaba conectado), Legion Go S detectado pero MUDO, y "solo el stick
  derecho funciona" en gameplay.
- 🔍 **Análisis del log completo:** la secuencia lo delató — 21:20:07 `SteamAPI initialized` →
  21:20:13 SDL3 solo abre el Legion (el 8BitDo ya no está) → mismo segundo, ActionSets válidos
  (retry #1 ✓). **Al conectar con AppID 480, Steam cree que "Spacewar está corriendo" y toma
  posesión de los mandos que gestiona** para aplicarles la disposición de Spacewar: el 8BitDo
  desaparece de la enumeración, el Legion queda silenciado a nivel evdev, y el "stick derecho que
  funciona" era la **emulación de ratón** de la disposición de Steam moviendo la cámara — no el
  mod. Mismo mecanismo exclusivo de Game Mode (B032), auto-infligido en escritorio.
- **Conclusión arquitectónica:** "SDL3 crudo + Steam Input en paralelo" es imposible para un mando
  gestionado por Steam — Steam da eventos crudos O acciones, nunca ambos. Y en escritorio conectar
  es pura pérdida: SDL3/HIDAPI ya entrega TODO crudo incluidos los paddles (P1..P4=true en el log
  de las 16:08) → en escritorio los paddles se asignan DIRECTO en BOTONES, sin Steam.
- ☑ **Fix v0.10.4:** `GlobalConfig.steamAttachMode` (AUTO/ALWAYS/NEVER, default **AUTO** = conectar
  solo bajo gamescope/Game Mode, detectado con `EnvironmentReport.isGamescope`, ya probado fiable
  en ambos entornos). Gate al inicio de `SteamBootstrap.init()` + flag `isAttachSkippedByPolicy()`.
- ☑ Diagnóstico honesto: selector muestra "Steam API: not attached (desktop: raw input)" en VERDE
  (no es un fallo); el panel de ranuras en BOTONES muestra un aviso específico de escritorio
  (nueva clave `steampad.bind.panel.slot_desktop` ×3 idiomas) que dirige a usar P1..P4 crudos.
- **Build:** `BUILD SUCCESSFUL` + 24/24 tests → **`dist/steampad-0.10.4.jar`**.
- ⚠️ Sin validar. Checklist (escritorio + Game Mode + riesgo abierto del gamepad virtual) → B038.
- Nota: el `preferredControllerName` del usuario quedó guardado como "Xbox One Controller" (época
  Steam-primario) — debe re-marcar Predeterminado en el 8BitDo; sin fix de código (dato de config).

---

## SESIÓN 19 cont. 3 (2026-07-08) — v0.10.2 y v0.10.3 — retry de ActionSets + reversión arquitectónica

**v0.10.2 — ActionSets en 0 pese al VDF confirmado (B036):**
- Con Steam Input ya conectando bien (v0.10.1), una sesión posterior mostró `InGame ActionSet
  valid: false` pese a que Spacewar mostraba correctamente las 10 "SteamPad Slot" en su
  configurador — el VDF estaba bien, pero `ISteamController.getActionSetHandle()` tardaba en
  reflejarlo. Esperar ANTES de lanzar Minecraft no ayudó (se probó explícitamente).
- ☑ Fix: `SteamBootstrap.retryActionSetRegistrationIfNeeded()` — reintenta
  `SteamActionRegistry.registerAll()` cada ~1s durante ~10s tras el init si los handles siguen
  inválidos, en vez de rendirse una sola vez. Re-registrar es barato (solo relee handles).
- Build 0.10.2, 24/24 tests. El retry SÍ funcionó en la siguiente prueba del usuario
  (`Input Source: Steam Input`, `Action Sets: loaded`) — pero eso destapó el problema real:

**v0.10.3 — el juego se quedó mudo: REVERSIÓN de la Restricción 1 de CLAUDE.md (B037, D032):**
- Con los ActionSets ya válidos, `ControllerManager` (siguiendo la política original "Steam Input
  principal") promovió Steam Input a fuente activa para TODO el gameplay. Consecuencia: **ningún
  botón de ningún mando respondía** — Steam Input solo reenvía acciones que el usuario mapeó
  explícitamente en el configurador de Steam, y el usuario solo había mapeado los 2 paddles (a las
  ranuras), no el resto del juego (mover, cámara, menús, todo BOTONES).
- Se detectaron 3 "controladores" bajo Steam Input (uno real activo con nombre "Xbox One
  Controller" — el 8BitDo mal identificado, B035 — y 2 fantasmas "Controller (Unknown)"); ninguno
  de los 3 respondía a ningún botón, confirmando que el problema no era selección de mando sino
  que Steam Input nunca recibía datos de acciones no mapeadas.
- **Antes de tocar código:** se le preguntó explícitamente al usuario (vía pregunta directa) cómo
  resolverlo, dado que esto reversa una Restricción Inamovible del propio CLAUDE.md. El usuario
  confirmó: SDL3 siempre principal, Steam Input en paralelo solo para las ranuras.
- ☑ Fix: `ControllerManager.refreshCache()` invertido — SDL3 primero, GLFW después, Steam Input
  solo como último recurso si NINGUNO de los dos fallbacks ve ningún dispositivo físico.
  `SteamSlotDispatcher` no necesitó cambios (ya leía directo de `SteamInputManager`, sin depender
  de `ControllerManager` — el diseño híbrido de D030 ya estaba preparado para esto).
- ☑ `CLAUDE.md` actualizado: Restricción 1 tachada con nota explicando el porqué de la reversión.
- **Build:** `BUILD SUCCESSFUL` + 24/24 tests → **`dist/steampad-0.10.3.jar`**.
- ⚠️ Sin validar todavía en el hardware del usuario. Checklist → B037.

---

## SESIÓN 19 cont. (2026-07-08) — v0.10.1 — Steam Input nunca conectaba: Flatpak + falso negativo de isSteamRunning()

**Contexto de la validación de v0.10.0 en hardware real (Bazzite):**
1. El usuario probó primero en escritorio (Flatpak) → `Steam is not running` en el log pese a
   tener Steam corriendo. Diagnóstico inicial: sandbox de Flatpak sin acceso a `~/.steam/`.
2. Descubrimos que el propio **script de Game Mode del usuario** (dos instancias en una ventana
   Sway para split local) también lanzaba `flatpak run org.prismlauncher.PrismLauncher` — es
   decir, las pruebas anteriores de Game Mode (documentadas en B032) **tampoco** habían tenido
   Steam Input real activo; corrían bajo el mismo sandbox bloqueado. La teoría de B032 ("Steam
   Input toma el HID en exclusiva en Game Mode") sigue siendo plausible pero **nunca se validó
   con Steam Input realmente inicializado** — queda abierta para reprobar.
3. **Migración a Prism Launcher nativo:** AppImage oficial descargada, instancias sincronizadas
   desde el Flatpak (que tenía la config al día), Flatpak desinstalado (`--system`, era instalación
   de sistema no de usuario), ícono/`.desktop` propio creado para diferenciarlo. Buen efecto
   secundario confirmado en el log: sin sandbox, **SDL3 ya expone los paddles crudos por HIDAPI**
   (`P1=true P2=true P3=true P4=true`) — en escritorio nativo probablemente ya se pueden mapear
   paddles directo por `extraBinds`, sin pasar por Steam Input, para el caso de escritorio.
4. Con Steam demostrablemente corriendo (PID de `~/.steam/steam.pid` coincide con el proceso real,
   pipe existe) el log **seguía** diciendo `Steam is not running`. Intento fallido: `customNativesPath`
   apuntando a `~/.local/share/Steam/linux64` — **no aplica**, ese campo solo controla dónde
   EXTRAER las natives viejas empaquetadas del mod, no permite usar las del sistema.
5. **Causa raíz real:** `SteamAPI.isSteamRunning()` es la implementación nativa empaquetada de
   Steamworks4j 1.9.0 (~2018) — dio **falso negativo** contra el layout IPC de un cliente de
   Steam moderno tipo Bazzite/SteamOS (`~/.local/share/Steam`), pese a que Steam corría de
   verdad. No es un problema de sandbox esta vez; es una incompatibilidad de versión del propio
   chequeo nativo.
6. ☑ **Fix (`SteamBootstrap.init()` y `runCallbacks()`):** nuevo `isSteamProcessAlive()` — escaneo
   **cross-platform puro Java** (`ProcessHandle.allProcesses()`, sin JNI/nativo) que busca un
   proceso `steam`/`steam.exe`. Si el chequeo nativo dice "no" pero este escaneo encuentra el
   proceso vivo, el mod procede con `init()` de todas formas (log claro de que se usó la vía de
   respaldo). Se aplicó también al chequeo periódico de 10 s en `runCallbacks()` — si no, el mod
   se habría "auto-desconectado" 10 s después de conectar por la vía de respaldo, por el mismo
   falso negativo.
- **Build:** `BUILD SUCCESSFUL` + 24/24 tests → **`dist/steampad-0.10.1.jar`**.
- ⚠️ Pendiente: instalar en el hardware del usuario y confirmar que `InGame ActionSet valid: true`.
  Checklist → B034.

---

## SESIÓN 19 (2026-07-08) — v0.10.0 — 10 ranuras genéricas de Steam Input (paddles en Game Mode)

> Contexto: en Game Mode los paddles NUNCA llegan crudos a SDL3 (Steam Input toma el HID en
> exclusiva, B032). Única vía: declarar acciones genéricas en el VDF, que el usuario mapee
> paddle→ranura en la configuración de mando de Steam, y que el mod dispare el keybind que el
> usuario asigne a cada ranura. TODO ESTO ESTÁ SIN PROBAR EN HARDWARE (B033).

- ☑ **VDF (`steampad_steam_input/game_actions_480.vdf`):** 10 acciones digitales nuevas
  `steampad_slot_1..10` en el ActionSet `SteamPad_InGame` + localización en ("SteamPad Slot N") y
  es ("Ranura SteamPad N"). Estos nombres son los que Steam muestra al mapear el paddle.
- ☑ **`SteamActionRegistry`:** `SLOT_COUNT=10` + array `actionSlots[]` registrado en `registerAll`.
- ☑ **`ControllerState.DigitalAction`:** `SLOT_1..SLOT_10` añadidos AL FINAL del enum (contiguos;
  el despachador los indexa `SLOT_1.ordinal()+i`); `getHandles()` concatena los handles del array.
- ☑ **`GlobalConfig.steamInputSlots`** (ranura "1".."10" → id de keybind): almacenamiento GLOBAL
  a propósito — el mapeo físico vive en la config por-juego de Steam (no por mando) y los handles
  de Steam no son estables entre sesiones (per-controller perdería asignaciones). Ver D030.
- ☑ **`input/SteamSlotDispatcher` (NUEVO):** edge-detect por ranura sobre el estado Steam con
  semántica HOLD (`KeyTap.hold/release`, como F13 → keybinds tipo zoom funcionan mantenidos).
  Solo dispara en gameplay; `releaseAll()` al abrir pantalla / perder mando / perder Steam.
  **Fuente híbrida:** si el mando activo va por Steam se lee de él; si va por SDL3/GLFW (el caso
  Game Mode) se lee del primer mando Steam conectado — `SteamBootstrap.runCallbacks()` ya mantiene
  los estados de Steam frescos cada tick aunque el backend activo sea un fallback. Keybind
  desinstalado/vacío = no-op silencioso.
- ☑ **Cableado:** `InputBindingManager.tick()` llama `SteamSlotDispatcher.tick()` en AMBOS caminos
  (Steam y fallback) y `releaseAll()` cuando no hay mando activo.
- ☑ **UI (`ActionCatalog` + `BindingsScreen`):** nueva `Kind.SLOT` + sección "Steam Input" con las
  10 ranuras, colocada después de Ratón virtual y ANTES de las secciones de mods (como se pidió).
  Click en una ranura abre el `KeybindPickerScreen` existente (buscable, todos los keybinds) — sin
  captura de botón físico. La fila muestra el nombre del keybind asignado (verde) o "(sin asignar)"
  (gris) + cuadrado Reiniciar. Panel lateral: keybind asignado + descripción del flujo + AVISO en
  amarillo cuando Steam Input no está activo (VDF sin importar / Steam no corre). Undo y
  Reiniciar-todo incluyen las ranuras.
- ☑ **i18n ×3** (en_us/es_mx/es_es): sección, etiqueta con número, descripción, vacío, aviso.
- ☑ **Test:** round-trip de `steamInputSlots` añadido a `ConfigSerializationTest`.
- **Build:** `BUILD SUCCESSFUL` + 24/24 tests → **`dist/steampad-0.10.0.jar`** (1.52 MB).
- ⚠️ **Sin probar en hardware.** Checklist de validación → B033. Además el VDF nuevo hay que
  RE-IMPORTARLO en Steam (el viejo no tiene las ranuras) — pasos en B033.

---


- ☑ **CRASH al guardar keybind en la rueda (log del usuario):** NPE en
  `VirtualKeyboard.pointInFocusedTextField` — el click virtual sobre el botón de guardar CIERRA la
  pantalla y luego consultábamos `mc.currentScreen` (null). Fix: se captura la pantalla antes del
  click y solo se consulta si sigue siendo la actual; null-guard adicional en el método.
- ☑ **Overlay de acciones menú→gameplay ("A de Volver a partida también salta"):** las acciones
  MANTENIDAS (salto/agacharse/atacar/usar/sprint) leían el estado actual sin edge — el botón que
  cerró el menú seguía presionado al volver al gameplay. Fix: supresión de botones held
  (`armHeldSuppression`): al cerrarse cualquier pantalla O el radial, todo lo presionado queda
  suprimido para los held-binds hasta soltarse físicamente una vez. Cubre menús, ajustes,
  inventario, radial y editor del radial (los taps ya estaban protegidos por prevButtons).
- ☑ **Teclado:** (a) punto del stick ELIMINADO — la selección ahora son esquinas blancas estilo
  inventario alrededor de la tecla (también resuelve "con D-pad aparece el punto"); (b) snap más
  fuerte: atracción magnética al centro de la tecla más cercana (12% empujando fuerte para
  conservar la sensación libre en diagonal, 45% al aflojar → aterriza exacto en la letra).
- ☑ **Vibración (F6, sigue sin funcionar):** ahora `SDL_RumbleGamepad` se comprueba y loguea el
  motivo del rechazo (`SDL_GetError` + `SDL_GamepadHasRumble`, máx 3 líneas). Sospecha principal
  bajo Prism FLATPAK: el sandbox bloquea force-feedback en /dev/input (dar permiso de dispositivos
  de entrada con Flatseal si el log lo confirma). Además los hints HIDAPI (abajo) pueden habilitar
  rumble vía hidraw.
- ☑ **Paddles/M1 (F4):** hints `SDL_JOYSTICK_HIDAPI=1` y `SDL_JOYSTICK_HIDAPI_8BITDO=1` ANTES de
  `SDL_Init` (el driver HIDAPI de SDL3 es el que expone los paddles del 8BitDo Ultimate 2; el
  mapeo evdev no los trae). Diagnóstico al abrir el mando: log de `SDL_GamepadHasButton` por cada
  extra (MISC1/P1-P4/MISC2) + `hasRumble` + versión de SDL — el próximo log dirá exactamente qué
  expone el sistema. Si HIDAPI no está disponible en el SDL3 de Bazzite/Flatpak, es límite externo.
- ☑ **Radial — ruedas ilimitadas (1–6) con añadir/eliminar:** `RadialConfig.wheels`
  (List<WheelConfig>, migración automática de configs legacy). Editor: fila compacta
  [−] [◀] [1/3] [▶] [＋] [✕] [+] (slots y ruedas en una fila, cabe en el Deck). LB/RB en juego
  recorre TODAS las ruedas con carrusel; indicador de N puntos.
- ☑ **Radial — glifos en el propio overlay:** fila centrada bajo la rueda con RS=Seleccionar,
  A=Usar, LT=Editar (+RB=Cambiar rueda si hay varias) usando los glifos de marca. Se quitaron las
  pistas duplicadas de la esquina del HUD.
- ☑ **Radial — gelatina PIXEL-ART (BG3-inspired):** cadena de cuadrados cuantizados a rejilla de
  2 px con taper (se estira al viajar) + cabeza de diamante pixelado. Mismo easing fluido.
- ☑ **Log de supresión de pausa con rate-limit** (el log del usuario tenía miles de líneas —
  vanilla reintenta cada frame sin foco): stacktrace la primera vez, luego 1 línea cada 10 s.
- ☑ Confirmado por el log del usuario: la supresión de pausa FUNCIONA (origen = GameRenderer
  vanilla, como se predijo) y el mando 8BitDo entra por SDL3.
- **Build:** `BUILD SUCCESSFUL` + 24/24 tests → **`dist/steampad-0.9.0.jar`**. Validación → B031.
- ☑ **(v0.9.1) Paddles/M1 + vibración — CAUSA RAÍZ CONFIRMADA por el log del usuario:** SDL 3.2.30
  abrió el 8BitDo SIN su driver HIDAPI (`P1..P4=false, rumble=false`, "operation is not supported")
  porque el Flatpak de Prism bloquea `/dev/hidraw*`. Es EXTERNO al mod → fix en el host (flatpak
  override --device=all + regla udev para vendor 2dc8; ver B032). El mod ahora imprime ese fix en el
  log cuando detecta el caso. `dist/steampad-0.9.1.jar` (24/24 tests).
- 🔍 **(2026-07-08) Diagnóstico refinado — el usuario probó conectando desde Steam Game Mode:**
  la **vibración SÍ funciona ahí** — confirma que en escritorio era 100% el sandbox de Flatpak
  bloqueando hidraw (en Game Mode Steam ya tiene acceso privilegiado al HID y reenvía el rumble).
  **Los paddles NO funcionan tampoco en Game Mode** — causa DISTINTA: con Steam Input activo, Steam
  toma el HID físico en modo exclusivo y expone a SDL3 (y por tanto a SteamPad) solo un gamepad
  virtual estándar sin botones de paddle; no es un permiso que se pueda destrabar desde el mod ni
  desde SDL3, es cómo Steam Input intercepta el dispositivo. Los paddles solo llegarían mapeados
  dentro del VDF de Steam Input (B002) como una acción del ActionSet — eso exige que el backend
  activo del mod sea Steam Input real (no el fallback SDL3), pendiente de validar el flujo VDF
  completo end-to-end. B032 actualizado con el diagnóstico separado por causa.

---

# (Histórico) Sesión 17 (v0.8.0: teclado Controlify-style + auditoría completa + bloques B/D/E/F)

## SESIÓN 17 (2026-07-07) — v0.8.0 — TODO COMPILA, 24/24 TESTS PASAN

### Corrección del análisis D8b (importante)
La verificación bytecode de S16/S17 estaba INCOMPLETA (extracción parcial del jar). Con el jar completo:
**SÍ existe camino vanilla foco→pausa en 1.21.10**: `GameRenderer.render` → si `!isWindowFocused()`
y `options.pauseOnLostFocus` durante >500 ms → `client.openGameMenu()`. ESO era el "click fuera →
menú". El fix v0.7.3 (PauseGate suprime GameMenuScreen sin foco con mando activo) lo neutraliza
exactamente — validado por el usuario. Equivale al mixin `pauseIfInactive` de Controlify.

### Bloque TECLADO (pedido explícito del usuario) ☑
- ☑ **Apertura estilo Controlify** (verificado contra su código: el teclado abre al PRESIONAR A sobre
  el campo, nunca por mero foco): auto-abre SOLO en pantallas de texto puro (chat/carteles/libros,
  gated por `virtualKeyboardAutoShow`); en las demás, badge "[A] Keyboard" + A abre (modo foco) o
  click del cursor SOBRE el campo enfocado abre (modo cursor). Adiós al "se abre antes de tiempo".
- ☑ **Stick izquierdo = cursor LIBRE flotante** (`KeyboardGeometry` nueva: geometría compartida
  lógica/render): se desplaza en diagonal sin pasar tecla por tecla, siempre con snap a la tecla más
  cercana (distancia a rect, el espacio atrae justo). Punto blanco visible. Curva signed-square
  (preciso al centro, rápido a fondo, 46 px/tick). D-pad sigue moviendo tecla a tecla y re-sincroniza.

### Bloque F (bugs hardware) ☑ código
- ☑ **F13 — Chords/extra binds no disparaban:** causa raíz: `dispatchExtraBinds` hacía TAP one-shot
  (`onKeyPressed`) — los keybinds de mods que leen `isPressed()` (zoom, push-to-talk) nunca lo veían.
  Ahora semántica HOLD: press en el edge, mantenido mientras botón+chord sigan, release al soltar
  (`KeyTap.hold/release` nuevos). `releaseAllMovement` libera todos (nada queda pegado).
- ☑ **F6 — Vibración no funcionaba:** causa raíz: el botón "Probar vibración" llamaba
  `SteamHapticsService.pulse` (SOLO Steam; no-op con SDL3/GLFW). Ahora enruta por
  `ControllerManager.rumble`. Extra: `allowVibration` ahora SE RESPETA (existía pero no se aplicaba).
- ⚠ **F4 — Paddles:** el código SDL3 los lee (MISC/PADDLE) y son capturables; que lleguen depende del
  mapeo SDL del dispositivo/modo (externo). Diagnóstico ya loguea cuando se activan. Validar con el
  8BitDo en modo dongle/X-input; si SDL no los expone, no hay señal que leer.

### Bloque D (navegación) ☑ código
- ☑ **D14 — Doble acción:** el mismo edge (Select-cancelar, o el botón capturado) disparaba captura Y
  navegación GUI en el mismo tick. `stopCapture()` ahora activa `swallowGuiTick` → un press = una acción.
- ☑ **D17 — D-pad brinca en lista de mundos:** `GuiFocusNavigator` ahora navega DENTRO de
  `EntryListWidget` (mundos/servidores): arriba/abajo mueve la selección entrada por entrada (con
  scroll-into-view vía setFocused), sale de la lista solo en los extremos; al entrar selecciona una;
  A = Enter sintético (confirma la entrada seleccionada, no un click al centro).

### Bloque B (visual) ☑ código
- ☑ **B9 — Glifos por contexto:** con el radial abierto, el HUD muestra RS=Seleccionar, A=Usar,
  LT=Editar (+LB/RB=Cambiar rueda si hay segunda) en vez de las pistas de gameplay.
- ☑ **B16 — Colores del radial estilo MC:** grises neutros vanilla (chips 0xE6212121/0xF0333333,
  fondo 0xB0101010), selección blanca. Aplica también al preview del editor (mismo renderer).

### Bloque E (radial) ☑ código
- ☑ **E10 — Segunda rueda:** `RadialConfig.secondWheelEnabled/slotCount2/slots2` (+accessors por
  página, normalize ampliado). En juego LB/RB alterna con CARRUSEL (la rueda entra deslizándose,
  mini-rueda fantasma al lado, indicador de 2 puntos). Editor: selector compacto en la fila −/+
  («Rueda única / Rueda 1 de 2 / Rueda 2 de 2») que habilita y elige página; LT desde el juego abre
  el editor en la página actual. i18n ×3.
- ☑ **E11 — Efecto gelatina:** blob que fluye del centro hacia la selección con easing por delta-time
  real (tau 55 ms), cadena de círculos con taper (se estira al viajar, se asienta como punto).

### AUDITORÍA de opciones (pedida por el usuario) ☑
Se auditó CADA campo de GlobalConfig/ControllerConfig buscando consumidores reales. **Cableadas hoy
(estaban muertas):** `gyroSensitivity`+`gyroBehaviour`+`yawMode`+`gyroRequireButton`+`gyroInvertX/Y`+
`flickStick` (¡`GyroHandler.configure` no tenía llamadores — TODA la sección gyro era inerte!),
`buttonActivationThreshold` (umbral real de LT/RT vía `GamepadBinds.trigDown`, default 0.5),
`screenRepeatNavigationDelay` (hold-repeat del D-pad en GUI: retardo inicial configurable, repite
cada 3 ticks), `reduceAimingSensitivity` (cámara ×0.45 mientras se usa ítem), `autoJump` (aplica a la
opción vanilla), `showScreenButtonGuide` (gatea las pistas de contenedor), `virtualKeyboardAutoShow`
(gatea el auto-abrir del teclado), `allowVibration` (gatea todo rumble).
**Eliminadas (legacy sin UI ni consumidor):** `showOnScreenKeyboard`, `onScreenKeyboardHeight`,
`controllerTheme` (superseded por teclado global + detección de marca). Gson ignora las claves viejas.
**Documentadas como pendientes con causa (no cableadas):** intensidades de vibración por categoría
(player/world/interaction/gui/global/misc — requieren hooks de eventos de juego, feature futura),
`hdHaptics` (limitación Steamworks4j 1.9.0, B003), `ingameButtonGuideScale` (requiere API de matrices
1.21.10), opciones de servidor (`blockReachAround`, `allowServerVibration`, `keyboardLikeMovement` —
sin componente servidor aún), `useEnhancedSteamDeckDriver` (documentado sin efecto).
**Tests:** obsoleto `testRadialConfigDefaultsHaveEightSlots` corregido (contrato real: lista a 12,
slotCount=8) + referencia a campo eliminado. **24/24 PASSED con JDK 21.**

### A15 — Revisión de convivencia de backends ☑ (revisión de código)
Cascada Steam(VDF válido)→SDL3→GLFW correcta y cacheada (80 ms); `readSnapshot` enruta por etiqueta
de handle (sin flips silenciosos); hint SDL de background events colocado ANTES de `SDL_Init`;
`prevButtons` se resincroniza al cambiar handle (sin edges fantasma en el cambio de backend);
rumble enruta por dueño del handle y respeta `allowVibration`. Doble-apertura SDL3+GLFW del mismo
dispositivo: sin conflicto observado en código (GLFW solo lee estado; SDL posee el handle abierto);
validar en hardware que el rumble no se duplique.

### Build
`BUILD SUCCESSFUL` + **24/24 tests** (Gradle 8.14 + JDK 21) → **`dist/steampad-0.8.0.jar`**.
Validación en hardware: B030 en TODO_BLOCKERS.md.

---

# (Histórico) Sesión 16 (regresión multi-instancia + 17 mejoras)

**Reanudación:** "Continúa SteamPad desde PROGRESS.md, bloque N"
Estado: ☐ pendiente · ⏳ en progreso · ☑ hecho
> Build: `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot"; & "C:\Users\RChe\.gradle\wrapper\dists\gradle-8.14-bin\38aieal9i53h9rfe7vjup95b9\gradle-8.14\bin\gradle.bat" -p C:\Dev\Steampad build --no-daemon -x test`

> **Alcance real:** 18 puntos sustanciales. Se trabaja en BLOQUES funcionales a lo largo de varias
> sesiones. No romper lo que funciona. Subir versión por bloque cerrado.

---

## BLOQUE A — Multi-instancia + backends de input (CRÍTICO)

- ☑ **A1 — Regresión "fuera de foco" / cross-talk entre instancias.** (v0.7.0)
  - **Causa raíz (confirmada por código):** El input NO depende del foco de ventana (se despacha en
    `ClientTickEvents.END_CLIENT_TICK` siempre) — por eso "se mueve" sin foco. El problema: en
    `SteamPadClient` (auto-activación, líneas ~116-123) cuando no hay preferido, **cada instancia
    elige `detected.get(0)`**, o sea el MISMO primer mando para todas. No hay coordinación entre
    instancias. Resultado: pulsar el botón menú en ese mando abre el menú en TODAS las instancias.
  - **NO es** auto-pausa por foco: en MC 1.21.10 `openGameMenu` solo lo llama `Keyboard` (tecla Esc);
    no existe camino vanilla foco→menú (verificado con javap del jar merged).
  - **Fix:** `ControllerClaimService` — reclamo entre procesos por archivo (fail-open). Cada instancia
    reclama un mando distinto (heartbeat con PID/timestamp); la auto-activación evita mandos ya
    reclamados por otra instancia viva. Degrada a comportamiento actual si el dir no es escribible.
  - **Workaround inmediato para el usuario:** en cada instancia, abrir selector y asignar un mando
    distinto + "guardar predeterminado" (la config es por-instancia, así que aísla ya hoy).
- ☐ **A15 — Investigación profunda SDL3 ↔ GLFW ↔ Steam natives, convivencia automática.**
  - Asegurar cascada limpia y sin peleas: Steam Input (si VDF válido) → SDL3 → GLFW. Documentar y
    automatizar selección. Revisar doble apertura de dispositivos y rumble entre backends.

## BLOQUE B — Visual / glifos

- ☑ **B1 — Cruceta izq/der invertidas (iconos) en 8BitDo.** (v0.7.0) Intercambiados los PNG
  `8bitdo/dleft.png` ↔ `dright.png` (el código/stem mapeaba bien; el asset estaba invertido).
  → Confirmar visualmente en hardware.
- ☑ **B5 — Glifos del TitleScreen desfasados.** (v0.7.0) Opciones → glifo a la DERECHA del botón
  (`x+width+4`); Salir → glifo a la IZQUIERDA (`x-icon-4`), centrado vertical al botón.
  → Confirmar visualmente; si sigue desfasado puede ser layout del TitleScreen.
- ☐ **B9 — Glifos de UI cambian según contexto** (radial abierto / edición de radial).
- ☐ **B16 — Color de UI del radial (gameplay y ajustes) al estilo Minecraft.**

## BLOQUE C — Teclado virtual

- ☐ **C2 — Campo de texto → teclado sale enseguida** (sin "presiona A"): seleccionar campo activa
  teclado automáticamente.
- ☐ **C3 — Teclado con stick derecho tipo inventario** (movimiento libre + snap bueno a la tecla),
  efecto solo dentro del teclado (no como en inventario global).
- ☐ **C7 — Teclado empuja la barra de chat en gameplay** + al abrir chat (botón/tecla) aparece
  enseguida el teclado.

## BLOQUE D — Menús / jerarquía ratón-mando / navegación

- ☑✅ **D8b — Click fuera de la ventana → menú de pausa espurio + imposible salir con gamepad.** (v0.7.3, **VALIDADO EN HARDWARE 2026-07-07: "funcionó perfecto"**)
  - **Análisis definitivo (bytecode 1.21.10):** NO existe camino vanilla foco→pausa (`pauseOnLostFocus`
    es opción muerta, solo la toca F3+P; `GameMenuScreen` solo se construye vía Esc en `Keyboard.onKey`;
    `openGameMenu` no tiene más llamadores). El menú lo abre algo EXTERNO al vanilla: Esc sintético
    (Steam Input → layout de escritorio al perder foco) o edge espurio de PAUSE al cambiar handle.
    OJO: `setScreen(null)` en 1.21.10 SÍ llama `lockCursor()` internamente (la premisa de D8 v0.7.1
    era incorrecta) — pero `lockCursor` tiene guard `if(!isWindowFocused()) return` → cerrar menú sin
    foco dejaba el cursor libre para siempre → clicks fuera → más pérdidas de foco → bucle.
  - **Fix v0.7.3 (paridad Controlify, verificado contra su código fuente):**
    1. `PauseGate` (nuevo): TODAS las aperturas de pausa de SteamPad pasan por `openPauseMenu()`;
       mixin fino en `MinecraftClient.setScreen` cancela `GameMenuScreen` si la ventana NO tiene foco,
       hay mando fallback activo y el open NO lo inició SteamPad. Primera supresión loguea STACKTRACE
       del origen (para identificar al culpable real en hardware).
    2. `MouseMixin`: `@Redirect` del `isWindowFocused()` dentro de `lockCursor` — con "Out of Focus
       Input" activo + mando fallback, el lock procede sin foco (estilo Controlify `grabMouse`).
    3. `GamepadInputDispatcher`: `prevButtons` se resincroniza al cambiar el handle activo (un cambio
       de dispositivo/backend ya no puede fabricar edges fantasma, incl. START→pausa).
  - → Validar en hardware: click fuera durante gameplay NO debe abrir menú; revisar log por
    "Suppressed a spurious pause-menu open" + stacktrace para confirmar el origen real.
- ⏳ **D8 — Bug "Volver al juego" / salir del menú con gamepad.** (v0.7.1 parcial)
  - **Causa raíz (confirmada con bytecode):** `Mouse.lockCursor()` = `if(!isWindowFocused()) return;`
    y al agarrar hace `setScreen(null)`. El botón "Volver al juego" hace `setScreen(null)+lockCursor()`,
    y `lockCursor` **AGARRA el ratón físico** (lo atrapa en esa ventana) — el origen del bucle
    multi-instancia y del "necesito el ratón físico". NO hay camino vanilla foco→menú en 1.21.10.
  - **Fix v0.7.1:** START ahora CIERRA el pause menu (`setScreen(null)` SIN `lockCursor`) → vuelve al
    juego sin atrapar el ratón. Salida garantizada con gamepad. (`GamepadInputDispatcher.tickGui`.)
  - **Pendiente (opcional, con tradeoff):** evitar el agarre del cursor en juego cuando un mando
    fallback es el dispositivo activo (estilo Controlify) → ratón nunca atrapado entre instancias.
    Tradeoff: puntero del SO visible sobre el juego. Requiere confirmación del usuario.
- ☐ **D14 — Sobremontaje de acciones.** Ej.: en Chord, `select`/`esc` cierra PERO `select` también
  cambia el cursor virtual. Auditar todos los menús para que una acción no dispare dos cosas.
- ☐ **D17 — D-pad en selección de mundo brinca** (cuesta entrar a la lista de mundos).

## BLOQUE E — Radial

- ☐ **E10 — Segundo menú radial** configurable desde ajustes; con dos, RB/LB alterna entre radiales
  con efecto carrusel (uno enfocado, el otro apenas visible).
- ☐ **E11 — Efecto "gelatina"** al seleccionar en el radial (bolita desde el centro que se deforma
  hacia la selección; fluido y rápido).

## BLOQUE F — Acciones / binds / hardware

- ☐ **F12 — Acciones faltantes.** Auditar y completar gameplay (cambiar mano X, soltar, …), menús,
  inventario, radial y ajustes.
- ☐ **F13 — Chords no funcionan** (ej. `dpadup+x` para zoom de un mod). Revisar `ChordResolver` /
  `GamepadBinds` ruta de chords y por qué no dispara.
- ☐ **F4 — Botones extra (paddles/M1…M4) no funcionan en ningún mando** (8BitDo incl.). Revisar
  config global por si algo se está peleando; mapear extras de SDL3.
- ☐ **F6 — Vibración no funciona en ningún mando** (incluido "probar vibración" en avanzado).
  Revisar ruta de rumble SDL3/Steam y el botón de prueba.

---

## Bitácora
- (S16) Diagnóstico crash multi-instancia previo = **voxy + RocksDB** (no SteamPad). Resuelto quitando voxy.
- (S16) A1 causa raíz identificada: auto-activación elige el mismo mando en todas las instancias.
- (S16) **Bloque 1 cerrado → v0.7.0**: A1 (ControllerClaimService, fail-open), B1 (swap PNG dpad 8bitdo),
  B5 (posición glifos titlescreen). BUILD SUCCESSFUL → `dist/steampad-0.7.0.jar`.
  Caveat A1: arranque simultáneo de 2 instancias en el MISMO segundo puede competir por el mismo mando
  (raro); lanzar con unos segundos de diferencia o asignar manualmente evita la carrera.
- (S16) **v0.7.2**: causa del "click fuera → menú / mando se congela" = SteamPad **NO** activaba
  eventos de joystick en segundo plano en SDL (Controlify SÍ). Fix: `SDL_SetHint(
  "SDL_JOYSTICK_ALLOW_BACKGROUND_EVENTS","1")` antes de `SDL_Init`. Confirmado por bytecode que MC
  1.21.10 NO tiene camino vanilla foco→menú (`windowFocused` solo lo lee `isWindowFocused()` → `lockCursor`).
  También: quitada la UI X/B del TitleScreen (mixin desregistrado); teclado se auto-abre al enfocar
  campo de texto (rising-edge, sin "presiona A") — C2/C7 parcial.
- (S17, 2026-07-07) **v0.7.3 — D8b (bug crítico reportado):** click del mouse físico fuera de la ventana
  abría el menú de pausa en gameplay sin salida por gamepad. Bytecode 1.21.10: NO hay camino vanilla
  foco→pausa ⇒ el open es espurio (Esc sintético de Steam Input desktop-layout, o edge fantasma).
  Fix: `PauseGate` (supresión + stacktrace de diagnóstico), bypass de foco en `lockCursor`
  (gated por Out of Focus Input), resync de `prevButtons` al cambiar handle.
  BUILD SUCCESSFUL → `dist/steampad-0.7.3.jar`.
- (S17, v0.8.0) **Cerrados en código:** A15(revisión), B9, B16, C2/C3(teclado definitivo), D14, D17,
  E10, E11, F12(auditoría), F13, F6. **F4:** código listo, depende del mapeo SDL del device (externo).
  **D8(grab opcional):** ya no necesario — PauseGate + lockCursor-bypass cubren el caso.
- **Pendiente real (con causa):** intensidades de vibración por categoría (hooks de eventos),
  `hdHaptics` (B003), `ingameButtonGuideScale` (matrices 1.21.10), opciones de servidor (sin
  componente servidor). Validación en hardware de todo v0.8.0 → B030.
