# TASKS.md — Backlog por Fases (Estado Real)

**Última actualización:** 2026-07-18 (sesión 29 cont. 8 — v0.61.0: FASE 79, autocorrección del pivote de piernas + investigación de "cadera bloqueada" + chip de Rueda +30% + Debug Dump con movimiento del jugador)

---

## FASE 79: v0.61.0 — Autocorrección del pivote de piernas + investigación de cadera + chip +30% + Debug Dump ampliado (sesión 29 cont. 8) ✅ CÓDIGO / ⚠️ INVESTIGACIÓN SIN CIERRE 100%

> El usuario probó v0.60.0 y reportó que la cadera sigue sin verse bien (se sienta en el aire) y que
> algunas animaciones deberían tener las piernas más quietas pero se mueven como la Macarena. Pidió
> otro 30% de tamaño en el chip de la Rueda y un Debug Dump aún más detallado. Detalle completo:
> D101. Checklist de pruebas: B095.

- [x] **Autocorrección: el fix de pivote de piernas de v0.60.0 (Z 0.1→0.0) estaba mal fundamentado
      y se revirtió a 0.1.** Al releer con más cuidado la fuente MIT ya cacheada
      (`PlayerModelMixin.setDefaultPivot()`), se confirmó que la propia librería de animación fuerza
      leg.z=0.1 como SU baseline antes de posar cada frame — ese es el valor contra el que los datos
      de los emotes están calibrados, no el pivote crudo de vanilla (0.0) verificado la ronda pasada.
- [x] **Investigación de "cadera bloqueada/se sienta en el aire/piernas como Macarena":** verificadas
      directamente (mismo método público que usa el render) las curvas reales de "Sit Adorably",
      "Hopelessly fall to the knees", "Hug" y "Macarena". El torso apenas se mueve (~0.6 unidades);
      las piernas logran la pose "sentada" casi enteramente por rotación de pitch (~-120°, estable),
      sin parecerse a la curva cíclica de Macarena. Sin evidencia de mezcla de datos. Hipótesis más
      plausible (sin cierre 100%): limitación inherente de emotes de solo-pose, que no mueven la
      posición real del personaje en el mundo.
- [x] **Chip de la Rueda +30% adicional** (pedido explícito): 1.45/1.10 → 1.885/1.43.
- [x] **Debug Dump ampliado** (pedido explícito, "hasta lo más mínimo"): nueva sub-sección de
      Rueda/Biblioteca abiertas; nueva sección "Player Movement" (input crudo, sprinting/sneaking/
      onGround, velocidad) — la señal exacta que usa `EmoteAnimator.clientTick` para cancelar un
      emote por movimiento.
- [x] Los 21 archivos reales re-verificados contra el parser Java real tras el revert: 21/21.
- [x] `mod_version` → 0.61.0. Build limpio + suite completa de tests en verde → `dist/steampad-0.61.0.jar`.
- [ ] Validación visual en hardware → checklist completo en B095. Necesita el NOMBRE EXACTO del/de
      los emote(s) donde persista "piernas como Macarena" para investigar más a fondo.

---

## FASE 78: v0.60.0 — Ajustes finos de emotes tras confirmación de gran avance + cámara libre durante emotes (sesión 29 cont. 8) ✅ CÓDIGO + 21/21 EMPÍRICO / ⚠️ SIN VALIDAR VISUALMENTE

> El usuario confirmó el mayor avance del histórico de emotes tras v0.59.0 (deformación "casi
> resuelta... se ve mucho mejor", 21/21 archivos, previews congelados "perfecto", foco+animación
> "perfecto"). Quedaron dos detalles finos (cadera, rotación "bloqueada" en algunas animaciones) +
> 3 pedidos nuevos (foco más grande, cámara libre durante la animación, volcado detallado). Detalle
> completo: D100. Checklist de pruebas: B094.

- [x] **Pivote real de piernas corregido, verificado por bytecode (no supuesto):** se desensambló
      (`javap`) `BipedEntityModel.getModelData` del propio jar mapeado 1.21.10 del proyecto — el
      pivote real es `(±1.9, 12.0, 0.0)`, Z=0.0. `EmoteData.defaultValue` tenía Z=0.1 para ambas
      piernas — un dato hardcodeado incorrecto, invisible con keyframes reales pero causante de un
      pop real en la cadera en el fallback "sin keyframe aún". Cabeza/torso/brazos re-verificados
      contra el mismo desensamblado — correctos.
- [x] **Colisión de keyframes body/torso encontrada y corregida:** se descartó primero la hipótesis
      de que el torso debía propagar rotación a hijos (bytecode confirma que son hermanos, no un
      árbol padre-hijo — la ausencia de propagación de v0.59.0 era correcta). La causa real: dump
      de los 21 archivos reales por parte-fuente sin fusionar mostró que "Friendship Round Dance"
      (MineEmotes) declara transformación REAL en AMBOS "torso" y "body" — v0.59.0 los fusionaba sin
      protección, intercalando dos pistas de keyframes en un eje. Fix: `body` se procesa antes que
      `torso`, y un eje que `torso` intenta reclamar YA tomado por `body` se descarta (con log de
      evidencia); ejes exclusivos de `torso` (sin colisión) se siguen aplicando normalmente.
- [x] **Foco aún más grande** (pedido explícito): Rueda 1.05/0.80 → 1.45/1.10; Biblioteca 52/19 →
      76/27 (desborda deliberadamente el tamaño de celda de 68px).
- [x] **Cámara libre en tercera persona forzada durante un emote real:** reutiliza el sistema de
      cámara libre existente (v0.52.0) en vez de construir uno nuevo — `isFreeLookEnabled()` ahora
      también es true mientras el jugador corre un emote GENUINO, sin tocar el toggle persistido del
      usuario. Nuevo flag `Playback.isRealEmote` + `EmoteAnimator.isLocalRealEmotePlaying()` evita
      que esto se active solo por NAVEGAR la Biblioteca/Rueda (que también usan el mismo mecanismo
      de playback para sus previews en vivo). `tickRotateStrategy` se salta durante el emote real
      para que el personaje no gire a encarar la cámara mientras el usuario orbita.
- [x] **Volcado de pose detallado agregado al Debug Dump** (pedido explícito): nueva sección "Local
      pose" con cada parte/eje (habilitado, keyframes, valor en vivo del frame actual, default),
      siempre fresca (no atada al throttle de 2s del log `[emote-pose]`); además muestra si el emote
      activo es real o solo preview, y si la cámara libre está forzada.
- [x] **Test nuevo** (`bodyWinsOnRotationCollisionWithTorso`) reproduce la colisión real y confirma
      que body gana sin perder datos legítimos de ejes sin colisión. Suite completa en verde.
- [x] Los 21 archivos reales re-verificados contra el parser Java real tras TODOS los fixes: 21/21.
- [x] `mod_version` → 0.60.0. Build limpio → `dist/steampad-0.60.0.jar`.
- [ ] Validación visual en hardware → checklist completo en B094.

---

## FASE 77: v0.59.0 — Emotes perfectos: semántica real (pivotes absolutos), parser binario completo 21/21, previews por celda/chip (sesión 29 cont. 8) ✅ CÓDIGO + 21/21 EMPÍRICO / ⚠️ SIN VALIDAR VISUALMENTE

> Sexta ronda de deformación. El usuario ordenó: "lee el codigo completo de la github de Emotecraft…
> LEELA Completa, deja de suponer" y adjuntó sus 21 archivos `.emotecraft` reales. Detalle completo:
> D099 (incluye el cambio de procedencia: playerAnimator MIT portado con atribución; emotes GPL
> leído solo para hechos de formato por orden explícita del dueño). Checklist de pruebas: B093.

- [x] **Causa raíz definitiva de la deformación (verificada por fuente + aritmética del log):** los
      valores de posición son PIVOTES ABSOLUTOS en espacio vanilla (defaults = pivotes vanilla;
      `AnimationApplier` asigna directo, sin suma al reposo, sin flip de Y, sin matriz de torso).
      El log del usuario lo confirmaba solo: R_ARM distFromRest ≡ 5.385 = |(-5,2,0)|. Los "fixes"
      D082–D087 eran compensaciones y se eliminaron todos.
- [x] **`EmoteData` reescrito:** canales por eje (deshabilitado = pasa el valor vanilla vivo;
      habilitado vacío = clava el default), port fiel de `Axis.getValueAtCurrentTick` (keyframes
      virtuales de borde → ease-in [0→begin] y ease-out [end→stop] contra el valor vanilla VIVO,
      costura de loop, clampToRadian, easingBefore), clamps de constructor de la referencia.
- [x] **`Easing` reescrito con la matemática REAL de KosmX** (BACK/ELASTIC/BOUNCE no son las de
      easings.net), easingArg, tabla completa de IDs binarios (0,1,6…35,36,37 — CUBIC antes que
      QUAD), `fromId()`. Desviaciones documentadas: STEP clampa su arg; CATMULLROM upstream es
      degenerado → toca como INOUTSINE.
- [x] **`EmoteAnimator.apply` reescrito:** asignación absoluta por eje con el valor vanilla como
      entrada del sampler; fade de fin ahora por eje vía keyframes virtuales; tick crudo desde 0
      (el ease-in por fin se reproduce); loop con span inclusivo `end−return+1` + `loopStarted`.
- [x] **`EmoteCraftBinaryParser` reescrito COMPLETO (v1–v4):** wrapper + 0x00/0x11/0x12, partes
      nombradas v2 con bendable por nombre, tolerancia `-1=disabled` de escritores viejos (13/21),
      stride keyframeSize, merge body+torso→TORSO, unwrap de nombres JSON, icono del 0x12,
      metadatos/loop/ticks REALES por primera vez.
- [x] **VERIFICADO EMPÍRICO:** los 21 archivos reales del usuario parsean **21/21** con el parser
      Java real (VerifyReal standalone) — nombres/autores/loops/ticks/iconos correctos.
- [x] **`EmoteJsonParser` corregido contra `AnimationJson`:** `degrees` default TRUE; `turn` =
      segundo keyframe +2π×turn; `easingArg`/`easeBeforeKeyframe`; isLoop requiere returnTick.
- [x] **Miniaturas congeladas POR CELDA/CHIP con el personaje** (Biblioteca + Rueda): duck interface
      `EmotePreviewState` en `PlayerEntityRenderState` + `EmotePreviewTagger` + mixin TAIL en
      `PlayerEntityRenderer.updateRenderState` (firma por javap) — cada draw encolado lleva su
      frame congelado a través del flush diferido de D092.
- [x] **B092 resuelto — previews que no animaban:** el emote en loop del usuario los bloqueaba →
      park/restore del playback real al abrir/cerrar Biblioteca y Rueda (respetando el fix D093 si
      confirmas un emote nuevo dentro); preview no-loop terminado se relanza en vez de congelarse.
- [x] **Chip/celda con foco MÁS GRANDE y animando en vivo** (pedido explícito): Rueda 0.72/0.55 →
      1.05/0.80 (solo seleccionado); Biblioteca 44/16 → 52/19 en foco, 40/15 congeladas.
- [x] **Tests:** contrato real de muestreo (ease-back tras endTick), defaults absolutos, tabla de
      IDs, contenedor binario sintético (tolerancia -1, merge, icono, basura→null). Suite en verde.
- [x] `mod_version` → 0.59.0. Build limpio → `dist/steampad-0.59.0.jar`.
- [ ] Validación visual en hardware → checklist completo en B093.

---

## FASE 76: v0.58.0 — Lag del mouse virtual: doble input (ciclo esconder/teleport) + volcado de debug de mod completo (sesión 29 cont. 7) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> v0.57.0 probado: el cambio de backend FUNCIONÓ (log del usuario: 8BitDo por SDL3, paddles P1-P4
> por primera vez) pero el lag PERSISTIÓ — el backend nunca fue la causa (post-mortem en D097).
> El usuario exigió "DEJA DE ADIVINAR, lee codigo" y aportó el dato clave: "Pasa con el mouse
> virtual y cuando se conecta el 8bitdo". Detalle completo: D098, TODO_BLOCKERS.md B091.

- [x] **Mecanismo rastreado línea por línea (hecho del código, no hipótesis):** movimiento de mouse
      EXTERNO correlacionado con el stick → `MouseMixin` (>20px/evento) → `markMouseForce()` (salta la
      ventana de protección a propósito) → `onPhysicalMouseTookOver()` esconde el cursor → el tick
      siguiente `onStickUsed()` lo re-muestra TELETRANSPORTADO al puntero del OS (`syncFromOsMouse`)
      + borra el foco — varias veces por segundo mientras el stick esté empujado = "lag terrible".
      Explica cada rasgo del reporte: solo mouse virtual, solo 8BitDo (único pad doblemente
      consumido), "cuando se conecta el 8bitdo", e inmune a todos los cambios de backend anteriores.
- [x] **Fuente #1 del movimiento externo (a confirmar con la instrumentación):** Steam Input desktop
      layout emulando mouse desde el MISMO pad — "Steam Virtual Gamepad" visible en el volcado del
      usuario; mismo problema de doble input que Controlify documenta.
- [x] **Fix — árbitro en `MouseMixin`:** mientras el stick dirige activamente el cursor
      (`isShown() && isMovingByStick()`, pad fallback activo, pantalla abierta), el movimiento de
      mouse externo se traga (+cuenta +loggea). Stick quieto → todo igual que antes; soltar el stick
      → el mouse recupera control en ≤1 tick; click físico → gana siempre; gameplay intacto.
- [x] **`MouseEventStats` (nueva):** contadores por ventana ~5s + totales de sesión para cada camino
      de un evento de cursor; log `[mouse-arb]` throttled con instrucción de arreglo de raíz; banner
      "DOUBLE INPUT DETECTED" en el volcado.
- [x] **Volcado de debug expandido a TODO el mod (pedido explícito):** versión; Backends & Mappings
      (SDL3 versión, resultado real del load GLFW+SDL3, conteos crudos, capacidades por pad abierto);
      etiqueta de backend decodificada por handle (@SDL3/@GLFW/@STEAM) en cada mando; warning cuando
      coexisten pad crudo + Steam Virtual Gamepad; Input Flow (dispositivo activo, cursor virtual
      completo, contadores de mouse); Performance (TickProfiler SIEMPRE capturado + timer de cámara
      libre); Active Controller Config; Global Feature State; Emotes.
- [x] `mod_version` → 0.58.0. Build + 29/29 tests → `dist/steampad-0.58.0.jar`.
- [ ] Validación en hardware → B091. **Prueba discriminante clave: cerrar Steam por completo y probar
      el 8BitDo — si el lag desaparece y las líneas `[mouse-arb]` se van del log, fuente confirmada.**

---

## FASE 75: v0.57.0 — Lag del joystick 8BitDo (nunca llegaba a SDL3) + gap de limpieza al fallar sobre otro control (sesión 29 cont. 6) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> El usuario pasó un log completo de una sesión real (ROG Ally + 8BitDo) con dos reportes nuevos,
> exigiendo investigación real ("lee codigo, no adivines") en vez de otro ajuste de valores a ciegas.
> Un intento de usar el Workflow tool con 4 agentes de investigación en paralelo falló por completo
> (límite de sesión) — toda la investigación se hizo leyendo código directamente. Detalle completo:
> D097, TODO_BLOCKERS.md B090.

- [x] **Causa raíz REAL del lag del 8BitDo, confirmada por aritmética exacta sobre los handles del log
      real** (no supuesta): cada handle lleva una etiqueta de bits fija según qué backend lo abrió —
      decodificando los handles reales del log con BigInt, el ROG Ally llevaba la etiqueta "SDL3" pero
      los DOS handles del 8BitDo decodificaban a ASCII "GLFW". El conteo crudo `SDL3=1` se mantuvo fijo
      durante TODA la sesión — el 8BitDo nunca fue visible para SDL3 ni un instante, a pesar del hint
      HIDAPI ya activado para él, y caía siempre al camino de respaldo GLFW (joystick genérico) en vez
      del camino SDL3 (HIDAPI-capaz, optimizado activamente).
- [x] **Causa raíz de por qué el 8BitDo nunca era visible para SDL3:** `GamepadMappings` solo enseñaba
      el layout curado del 8BitDo a GLFW (`glfwUpdateGamepadMappings`) — nunca a libSDL3.
      `SDL_GetGamepads()` solo enumera dispositivos que la propia base de mapeos de SDL YA reconoce
      como gamepad; sin una entrada válida, el 8BitDo era invisible para SDL3 y el merge de
      `ControllerManager` lo recogía de GLFW en su lugar, en silencio.
- [x] **Fix:** `Sdl3Native` gana `SDL_AddGamepadMapping(String)`; nuevo
      `Sdl3GamepadProvider.loadMappings(content)` registra las MISMAS líneas que GLFW ya recibía, una
      por línea. `GamepadMappings.apply()` enseña a ambos backends con una sola fuente de contenido.
      Reordenada la inicialización en `SteamPadClient` (SDL3 antes que `GamepadMappings.loadAll()`).
- [x] **Gap real de limpieza al fallar sobre otro control, encontrado en el propio código:** el
      failover automático de `SteamPadClient` cambia el handle activo a un control DISTINTO dentro del
      MISMO tick en que detecta la desconexión — `GamepadInputDispatcher.tick()` nunca vuelve a ver el
      handle viejo, así que su única limpieza de "controlador desaparecido" nunca se dispara para él.
      Cualquier estado que el control viejo mantuviera exactamente al desconectarse (keybind de mod
      sostenido, zoom activo) quedaba sin liberar hasta reiniciar el juego.
- [x] **Fix:** nuevo `GamepadInputDispatcher.releaseAllHeldStateOnControllerLoss()` público, llamado
      desde `SteamPadClient` en el punto exacto donde se detecta la desconexión — antes del failover —
      con logging de diagnóstico (solo si algo realmente estaba sostenido) para la próxima prueba real.
- [x] Build + 29/29 tests → `dist/steampad-0.57.0.jar`, compiló limpio a la primera.
- [ ] Validación en hardware (checklist completo) → B090. **El gap de limpieza no se pudo confirmar al
      100% como la ÚNICA causa de "botones mal configurados" — el logging nuevo dirá en la próxima
      prueba si cierra el problema o si queda algo más por encontrar.**

---

## FASE 74: v0.56.0 — Lote grande: Tercera Persona (3 bugs reales), previos de emotes (causa raíz), SlotSnap, onboarding, haptics (sesión 29 cont. 5) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> El usuario pegó el reporte de validación más detallado de la sesión (36 OK / 18 fallas / 59 ítems)
> con instrucciones explícitas de resolver todo lo marcado como fallo, priorizando la Cámara de
> Tercera Persona ("el que más bugs tiene") y la deformación de emotes (prioridad #1, sexta ronda).
> Detalle completo: D092-D096, TODO_BLOCKERS.md B089.

- [x] **Causa raíz REAL de "se reproducen todos" en previos de emotes** (Rueda + Biblioteca),
      confirmada por `javap` sobre el jar mapeado 1.21.10: `InventoryScreen.drawEntity` ENCOLA su
      dibujo en vez de renderizarlo de inmediato, y el posado real ocurre después, en un flush que lee
      un mapa GLOBAL por-ID-de-entidad — todas las casillas encoladas para el mismo jugador terminan
      leyendo el mismo valor final. Fix: solo la casilla/fila enfocada dibuja una entidad 3D en vivo;
      el resto vuelve a su ícono plano normal — elimina el conflicto por construcción.
- [x] **Biblioteca de Emotes rediseñada como cuadrícula** (antes lista vertical), con el mismo criterio
      de "solo la celda con foco/hover anima" aplicado a cada celda.
- [x] **Tercera Persona — bug #1 (confirmado por bytecode):** el mixin de free-look cancelaba
      `Camera.update()` completo en HEAD, impidiendo que vanilla fijara su propio campo `thirdPerson`
      (exactamente lo que decide si el propio cuerpo se dibuja) — de ahí "se ve como primera persona,
      alguien invisible". Fix: TAIL en vez de HEAD-cancelable, igual que el hook de offset que siempre
      fue correcto.
- [x] **Tercera Persona — bug #2 (confirmado por 3 métodos independientes):** el vector "derecha" del
      offset de hombro/cámara libre estaba invertido — Izquierda y Derecha producían el efecto
      contrario. Fix: helper compartido `rightVectorXZ()` con el signo corregido (NO toca
      `applyCameraRelativeMovement`, que usa la misma forma de fórmula por una razón distinta y ya
      verificada byte-a-byte contra vanilla).
- [x] **Tercera Persona — bug #3:** movimiento relativo a cámara giraba el cuerpo y caminaba en la
      dirección correcta, pero los booleans de sprint/animación seguían leyendo el input crudo
      pre-remapeo — fix en `KeyboardInputMixin` para que ambos usen los mismos valores efectivos.
- [x] **Tercera Persona — rendimiento:** sin causa algorítmica encontrada por inspección; se agregó un
      temporizador con volcado cada ~2s (min/avg/max ms) a `computeFreePose` en vez de una
      "optimización" a ciegas.
- [x] **`SlotSnap` revertido + mejorado de verdad:** el radio angosto de casillas (8px, de FASE 68/D087)
      que arreglaba Traveler's Backpack había roto la sensación del inventario GENERAL con el cursor
      virtual (confirmado: no afectaba al D-pad). Radio de casillas vuelto a 22px; el problema original
      (botón de mod perdiendo contra una casilla vecina) resuelto en la puntuación — distancia
      normalizada por radio + prioridad para widgets — no en el radio.
- [x] **Onboarding nunca se disparaba — causa raíz real:** estaba anidado dentro de la re-verificación
      de handle de la vibración de inicio; si el handle cambiaba en la ventana de 750ms (riesgo real en
      streaming), AMBOS se saltaban en silencio para siempre. Desacoplados — onboarding ahora consulta
      el handle activo actual de forma independiente.
- [x] **Diagnóstico de haptics de arma cuerpo a cuerpo** (persistente, 4ª+ ronda, sin causa confirmada
      por lectura de código): logging sin throttle en `onMeleeHit` cubriendo cada gate de la cadena
      (weapon/tier/mag/dur/handle/allowVibration/multiplicadores/canFire/tier ocupando el canal).
- [x] **Slime:** nueva pulsación suave en reposo (0.16/180ms), no solo caminando — respondiendo a la
      descripción original del usuario ("como estar en algo pegajoso").
- [x] **Vibración de inicio:** log nuevo cuando se salta por cambio de handle en la ventana de 750ms.
- [x] **Jugosidad:** boost global 1.2× (`SHAKE_BOOST`/`FOV_KICK_BOOST` en `JuiceController`) tras
      confirmación del usuario de que el efecto ya funciona y solo necesita un poco más.
- [x] **Diagnóstico de deformación de emotes (6ª ronda):** volcado completo de pose por parte animada
      (origen/rotación/distancia al reposo, NaN marcado) en `EmoteAnimator.apply()`, throttled a 1/2s
      por entidad — en vez de una séptima corrección a ciegas.
- [x] Build + 29/29 tests → `dist/steampad-0.56.0.jar`, compiló limpio a la primera.
- [ ] Validación en hardware (checklist completo) → B089. **Los 3 puntos de diagnóstico puro
      (deformación, haptics de arma, rendimiento de cámara) necesitan el CONTENIDO REAL de
      `latest.log` en la próxima prueba, no solo "funcionó"/"no funcionó".**

---

## FASE 73: v0.55.0 — Preview "AAA" en la Rueda de Emotes durante gameplay (sesión 29 cont. 4) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> El usuario preguntó directamente si FASE 72 había tocado la rueda en gameplay — no, solo la
> Biblioteca — y pidió extenderlo ahí "de la mejor forma". Detalle completo: D091,
> TODO_BLOCKERS.md B088.

- [x] **Hook opcional por-casilla en `RadialRenderer`** (`SlotThumbnailRenderer`), invocado desde
      DENTRO de su bucle de dibujo existente, con la posición/radio de cada casilla ya calculados —
      evita duplicar la matemática de geometría (ángulo, centro con offset de carrusel, radio) en otro
      archivo. Nueva sobrecarga de `render(...)`; la firma vieja de 7 argumentos sigue intacta y
      delega con `null` — confirmado que el radial normal, `RadialEditorScreen` y `RadialStyleScreen`
      siguen llamándola sin el hook nuevo, cero cambio de comportamiento ahí.
- [x] **Cada casilla no vacía de la Rueda de Emotes** muestra al jugador posado en un frame fijo de
      su emote; la casilla SELECCIONADA reproduce el baile completo en tiempo real, ahí mismo.
      Mismo mecanismo de pose-y-dibuja secuencial + snapshot/restore de FASE 72, aplicado a la
      geometría circular de la rueda en vez de una lista vertical.
- [x] **Panel lateral fijo eliminado** de `EmoteWheelOverlay` (igual que se hizo en la Biblioteca) —
      el nombre del emote seleccionado lo sigue mostrando el propio renderizador del radial.
- [x] **Editor de la Rueda de Emotes (pantalla de pausa) sin tocar** — sigue con el panel fijo.
- [x] Build + 29/29 tests → `dist/steampad-0.55.0.jar`, compiló limpio a la primera.
- [ ] Validación en hardware (checklist completo) → B088.

---

## FASE 72: v0.54.0 — Preview "AAA" de la Biblioteca de Emotes: thumbnail fijo por fila, animado solo el enfocado (sesión 29 cont. 3) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> El usuario retomó una idea documentada deliberadamente en el backlog varias sesiones atrás.
> Confirmó el diseño exacto antes de programar (disparador = solo foco/selección, sin A; al mover
> el foco la fila anterior se congela de vuelta a su frame fijo). Detalle completo: D090,
> TODO_BLOCKERS.md B087.

- [x] **Eliminado el panel lateral fijo de la Biblioteca de Emotes.** Cada fila ahora muestra su
      propia miniatura del personaje, posado en un frame fijo representativo de ESE baile (35% entre
      inicio y fin — heurístico documentado, no una curación manual por emote).
- [x] **La fila con foco/selección cobra vida** y reproduce el baile completo ahí mismo — reutiliza
      el mecanismo de reproducción continua ya existente (token de generación de D080/D081), sin
      tocarlo. Al mover el foco, se congela de vuelta a su foto fija.
- [x] **Nuevo mecanismo en `EmoteAnimator`** (`applyPinnedFrame`/`snapshotPlayback`/`restorePlayback`)
      para posar-y-dibujar una fila a la vez dentro del mismo frame, dado que Minecraft comparte una
      sola instancia de modelo entre todos los jugadores (solo puede haber una pose "viva" a la vez).
      Protege cualquier emote real ("▶") de ser pisado por las poses fijas de las demás filas.
- [x] **Alcance acotado a la Biblioteca**, tal como se pidió — el editor de rueda y el overlay en
      pleno juego siguen usando el panel lateral fijo, sin tocar.
- [x] **Confirmado sin cambios:** la Rueda de Emotes ya comparte la misma vibración de selección que
      el menú radial normal (`EmoteWheelController.updateAnalog` ya llama a
      `HapticsController.radialSelectPulse()`) — verificado leyendo el código, no hacía falta ningún fix.
- [x] Build + 29/29 tests → `dist/steampad-0.54.0.jar`, compiló limpio a la primera.
- [ ] Validación en hardware (checklist completo) → B087.

---

## FASE 71: v0.53.0 — Autocrítica "AAA" ejecutada: haptics por arma, optimización, movimiento relativo a cámara, jugosidad, onboarding, perfiles (sesión 29 cont. 2) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> El usuario pidió una autoevaluación crítica y objetiva de qué le falta al mod para ser "la mejor
> experiencia de gamepad de todos" (AAA). Se entregaron 8 puntos; el usuario filtró personalmente
> cuáles ejecutar. Detalle completo: D089, TODO_BLOCKERS.md B086.

- [x] **Haptics por arma + muerte confirmada:** propuesta analizada antes de programar (pedido
      explícito del usuario) — espada/hacha/tridente/maza cada una con su propia firma de golpe;
      maza con "ataque de aplastamiento" real (requiere caer, la misma señal que ya existía para
      críticos); flecha confirmada como toque corto y agudo; muerte siempre promueve a la vibración
      más fuerte disponible, con cualquier arma. Detección real vía `ItemTags` (`SwordItem` ya no
      existe como clase en 1.21.10 — verificado con `javap` antes de programar).
- [x] **Optimización de la cámara libre:** el pick de crosshair/entidades ya no se recalcula cada
      frame de render — se limita a ~50ms, reutilizando el patrón de caché-con-expiración que
      `ControllerManager` ya usa.
- [x] **Movimiento relativo a cámara**, opción nueva apagada por defecto: toca `KeyboardInputMixin`
      pero el camino existente queda byte-por-byte intacto si no se activa.
- [x] **Jugosidad:** screen shake + "FOV kick" en golpes/muertes/explosiones/daño recibido/caídas
      fuertes, en primera Y tercera persona — enganchado a los mismos eventos que ya disparan
      haptics. Deliberadamente SIN hit-stop real (congelar la simulación) — la historia propia del
      proyecto con bugs de cámara/temporización (D046-D053) hizo que no valiera la pena el riesgo.
- [x] **Onboarding:** pantalla de bienvenida mostrada una sola vez en la vida del mod, la primera vez
      que un mando se activa — puntero directo a radial/emotes/zoom/tercera persona/haptics/botones.
- [x] **Perfiles de configuración:** guardar/cargar/eliminar paquetes con nombre de botones+
      sensibilidad+radial, reutilizando el mismo patrón de copia de archivos de la migración por
      reconexión (D086).
- [x] Build + 29/29 tests → `dist/steampad-0.53.0.jar`, compiló limpio a la primera.
- [ ] Validación en hardware (checklist completo) → B086.
- [ ] **Descartado, no pendiente:** triggers adaptativos (dependencia bloqueada), API pública para
      mods de terceros (valor incierto), rediseño de UI 10-foot (pospuesto por el usuario).

---

## FASE 70: v0.52.0 — Cámara Libre en tercera persona: rotación desacoplada, mira funcional, puntería predictiva, ajuste en vivo (sesión 29 cont.) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> El usuario pidió explícitamente el feature set COMPLETO del mod de referencia
> (github.com/Leawind/Third-Person, MIT) — no solo el offset de hombro ya enviado en v0.46.0 (D082) —
> por tercera vez a lo largo de las sesiones, tras dos decisiones previas de dejar el free-look fuera
> de alcance (D082, D083). Esta vez se implementó completo, releyendo el código fuente real del mod
> (versión estable publicada, no la rama en desarrollo). Detalle completo: D088, TODO_BLOCKERS.md B085.

- [x] **Rotación libre:** el stick derecho gira la cámara sin girar el cuerpo del personaje — se
      redirige en la fuente (`CameraController.frame()`, el único punto por el que el stick llega a
      `changeLookDirection`) en vez de agregar un mixin para cancelar el turno del jugador.
- [x] **Cámara con centro de rotación + colisión + distancia ajustable**, reemplazando el offset
      simple SOLO cuando la cámara libre está activa (el offset simple de D082 sigue intacto y sin
      cambios cuando el toggle nuevo está apagado — cero riesgo de regresión).
- [x] **Mira funcional ("shoot like first-person"):** atacar/usar/minar en tercera persona ahora
      apunta a lo que el crosshair señala (línea de mira de la cámara), no a hacia dónde mira
      literalmente el cuerpo — 2 mixins nuevos, verificados con `javap` contra el jar Yarn real.
- [x] **Puntería predictiva:** al apuntar con arco/ballesta/tridente, el personaje se orienta hacia el
      objetivo más probable bajo el crosshair (misma función de costo distancia²×ángulo^2.5 que
      `AimAssistController` ya usa), para que el disparo real llegue ahí.
- [x] **Hacia dónde mira el cuerpo (parado):** 3 modos — Mirar hacia la mira (por defecto), Seguir a
      la cámara, Quedarse quieto. Apuntar/interactuar siempre anulan el modo elegido. Simplificación
      deliberada: solo actúa con el jugador QUIETO — replicar "seguir dirección de movimiento" con
      fidelidad exigiría tocar el mixin de movimiento más crítico del proyecto; mientras caminas, el
      personaje se comporta exactamente igual que sin cámara libre.
- [x] **Ajuste en vivo:** nuevo bind THIRD_PERSON_ADJUST (mantener) repropone el stick derecho al
      offset y D-pad arriba/abajo a la distancia. Nuevo bind THIRD_PERSON_FREE_LOOK_TOGGLE.
- [x] Build + 29/29 tests → `dist/steampad-0.52.0.jar`, compiló limpio a la primera.
- [ ] Validación en hardware (checklist completo) → B085.
- [ ] **NO implementado, deliberadamente:** transparencia del jugador (cosmética, apagada por defecto
      incluso en el mod real) — ver TODO_BLOCKERS.md B085 para la razón completa.

---

## FASE 69: v0.51.0 — Deformación real #5 (traslación del torso) + fix de slime + vibración de inicio + snap de Traveler's Backpack + investigación `.emotecraft` v1 + checklist HTML de 3 estados (sesión 29) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> Primera ronda de resultados reales usando el checklist HTML consolidado (29/42 confirmados de un
> solo jar). Detalle completo: D087, TODO_BLOCKERS.md B084.

- [x] **Deformación de emotes — causa raíz REAL #5:** la traslación del torso (canales x/y/z, común
      en bailes con rebote/cadera) nunca se propagaba a cabeza/brazos/piernas — cada miembro se
      quedaba anclado a su propio reposo mientras el torso se desplazaba. Las causas #3 (rotación,
      D084) y #4 (pose agachada, D085) ya estaban corregidas; esta era una quinta causa
      independiente, no cubierta por ninguna de las dos. `EmoteAnimator.applyTorso` ahora retorna
      su delta de traslación; `applyChild` lo suma al origen de cada miembro que la animación toca
      (las partes no tocadas siguen puramente vanilla, sin cambios).
- [x] **Vibración de slime — bug real de detección, no de magnitud:** el mismo preset se siente bien
      al dispararlo manualmente desde el Panel de Prueba de Haptics pero nunca al caminar sobre
      slime real — descarta la teoría previa de "el driver no interpreta pulsos superpuestos".
      Causa real: `getBlockPos().down()` sufre parpadeo de punto flotante justo al caminar (a veces
      lee el bloque de ABAJO del slime). Fix: `BlockPos.ofFloored(x, y-0.2, z)`, el mismo margen que
      usa vanilla internamente para este problema.
- [x] **Vibración de inicio — condición de carrera plausible corregida:** el disparo ocurría en el
      MISMO tick en que el mando se marca activo, antes de que SDL3/GLFW terminen de reconocer ese
      handle — un fallo silencioso ahí significa cero vibración el resto de la sesión (disparo de una
      sola vez). Diferido ~750ms, verificando que sea el mismo handle antes de disparar.
- [x] **Traveler's Backpack — implementada la solución propuesta por el usuario:** las casillas ahora
      solo "jalan" el cursor dentro de su propia celda (8px, antes 22px compartido con los widgets),
      dejando el radio amplio solo para los botones de mods — el botón en el hueco angosto entre dos
      casillas ya no pierde contra ellas antes de tiempo.
- [x] **`.emotecraft` v1 — investigación pública, resultado parcial:** la wiki oficial de KosmX/emotes
      documenta el envoltorio del contenedor (modular, versionado por diseño) pero no la codificación
      interna de keyframes — sigue sin poder decodificarse "versión 1" sin más muestras o sin leer
      código GPL. Sin cambios al parser.
- [x] **Bind de Tercera Persona — aclarado, no era un bug:** agregado un botón directo "Asignar
      ciclado rápido..." en Ajustes Globales → Tercera Persona que abre Botones.
- [x] **Checklist HTML rediseñado:** de checkbox binario a 3 estados (No probado/Falló/OK) + nota de
      texto libre por ítem + exportar reporte, con todos los ítems en un único array de datos
      (`CHECKLIST.html`, movido a la raíz del repo) para que actualizarlo en sesiones futuras sea una
      edición barata, no una reescritura de HTML.
- [x] Regenerados `gradlew`/`gradlew.bat` (faltaban del repo) apuntando a Gradle 8.14.
- [x] `mod_version` → 0.51.0. Build + 29/29 tests → `dist/steampad-0.51.0.jar`.
- [ ] Validación en hardware (checklist completo) → B084.

---

## BACKLOG — ideas pendientes, sin implementar (última revisión: sesión 29 cont. 4)

> Se mantiene sincronizado con el panel "🔧 Pendiente de implementar" de `CHECKLIST.html` — edítalos
> juntos. ~~Preview "AAA" de emotes (Biblioteca)~~ implementado en v0.54.0 (FASE 72, D090).
> ~~Thumbnails animados en la Rueda de Emotes en gameplay~~ implementado en v0.55.0 (FASE 73, D091) —
> ambos retirados de esta lista.

- [ ] **Silueta en negro para el preview del menú de ajustes:** cuando no hay mundo cargado (no existe
      una entidad de jugador real que posar), mostrar una silueta genérica en vez de solo texto
      aclaratorio.
- [ ] **.emotecraft "versión 1" y la variante "Friendship Round Dance":** bloqueado — ver
      TODO_BLOCKERS.md B084 para el detalle completo de por qué.
- [ ] **Transparencia del jugador en Cámara Libre:** pospuesto por riesgo — ver TODO_BLOCKERS.md B085.
- [ ] **Hit-stop real (congelar la simulación en golpes):** descartado deliberadamente — ver
      TODO_BLOCKERS.md B086.
- [ ] **Rediseño de UI "10-foot":** pospuesto por decisión explícita del usuario, no técnica.
- [ ] **API pública para compatibilidad con mods de terceros:** valor incierto sin adopción externa.
- [ ] **Triggers adaptativos:** bloqueado — `steamworks4j` no expone la API nueva de Steam Input.

---

## FASE 68: v0.50.0 — `.emotecraft` real (12/21) + fix de ícono + fix de reconexión de mando (sesión 28 cont. 6) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> Quinta ronda de feedback, con 20 archivos `.emotecraft` reales adicionales (21 en total) y un bug
> nuevo de reconexión de mando. Detalle completo: D086, TODO_BLOCKERS.md B083.

- [x] **`.emotecraft`: de 1 a 12 de 21 archivos reales.** Los límites de seguridad del parser
      binario (D085) estaban calibrados con UNA sola muestra simple; con 21 muestras reales se
      subió el tope de keyframes por canal (64→10,000) y se quitó el límite de ventana de
      resincronización (512 bytes→sin límite, hasta el fin del archivo). Verificado con test real
      contra los 21 archivos: 10 con las 6 partes completas, 2 con 5/6 (falta solo una extremidad).
- [x] **Ícono no aparecía — bug real de overload de `drawTexture`** encontrado y corregido:
      `EmoteIconProvider` usaba el overload de 8 parámetros (sin `regionWidth`/`regionHeight`); se
      cambió al overload de 10 parámetros, igualando el patrón ya probado en `ButtonIcon`/
      `ControllerBrandIcon`.
- [x] **Bug NUEVO encontrado y corregido — reconexión de mando pierde la configuración:** los
      archivos de config por-control se guardaban por HANDLE numérico, pero SDL3 asigna un handle
      NUEVO cada vez que el mismo mando físico se reconecta (confirmado en el log del usuario).
      Fix: `ConfigManager.migrateControllerConfigByName` — índice persistente nombre→handle que
      copia la config del handle anterior al nuevo la primera vez que aparece (nunca sobrescribe).
- [x] **Preview "no funciona en menú ajustes":** no se encontró un bug de código distinto al del
      ícono — el preview animado requiere una entidad de jugador real (imposible sin mundo
      cargado); se agregó una línea aclaratoria en el panel en vez de dejarlo en blanco sin
      explicación.
- [x] **Hallazgo colateral:** los archivos `.emotecraft` de esta muestra vienen de una herramienta
      de terceros llamada "MineEmotes" (firma de texto en la metadata), no del exportador oficial
      de KosmX — explica por qué hay variantes estructurales dentro de "versión 2".
- [x] `mod_version` → 0.50.0. Build + 29/29 tests → `dist/steampad-0.50.0.jar`.
- [ ] Validación en hardware (checklist completo) → B083.

---

## FASE 67: v0.49.0 — Deformación real #4 (pose agachado) + fix de regresión crítica + `.emotecraft` binario real (parcial) + íconos reales (sesión 28 cont. 5) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> Cuarta ronda de feedback. La deformación seguía pasando pese a FASE 66 — pedido explícito de
> "valoración profunda" revisando cómo lo hace Emotecraft. Además: bug NUEVO y crítico (el emote
> seleccionado en la rueda de gameplay no se reproducía), el preview animado "desapareció" de la
> Biblioteca, pedido explícito de íconos reales en vez de letras, y tercer intento de soporte real
> para `.emotecraft` con 3 archivos reales adjuntados por el usuario. Detalle completo: D085,
> TODO_BLOCKERS.md B082.

- [x] **Deformación — causa raíz REAL #4:** `javap` sobre `BipedEntityModel.setAngles` confirmó que
      vanilla desplaza `originY`/`originZ` 3.2-4.2 unidades en la pose de agachado. El caché estático
      de rest-origin (FASE 65) podía aprender ese valor contaminado PARA SIEMPRE si la primera entidad
      "limpia" observada resultaba estar agachada. Fix: leer siempre `ModelPart.getDefaultTransform()`
      (pivote horneado inmutable) en vez de los campos en vivo — elimina la clase de riesgo, no solo
      el síntoma.
- [x] **Fix de regresión crítica:** seleccionar un emote en la rueda de gameplay cancelaba la
      reproducción real un frame después de iniciar (el preview animado y la reproducción real
      compartían la misma entidad sin forma de distinguirse). Fix: token de generación
      (`EmoteAnimator.currentGeneration`) en `EmoteWheelOverlay`, `EmoteLibraryScreen` y
      `EmoteWheelScreen` — mismo riesgo latente en las 3, mismo fix.
- [x] **`.emotecraft` binario real:** confirmado con evidencia hexadecimal que NO es un `.json`
      renombrado (la asunción de FASE 65 estaba equivocada) sino el formato binario NATIVO real de
      Emotecraft. Reverse-engineering clean-room (solo de los bytes, cero código GPL leído) del
      sub-formato "versión 2" completo, implementado en `EmoteCraftBinaryParser` — verificado
      byte-exacto contra un archivo real del usuario. El sub-formato "versión 1" (2 de los 3 archivos
      reales del usuario) no tiene ningún ancla segura para reverse-engineer — documentado como
      blocker abierto en vez de adivinar (ver TODO_BLOCKERS B082).
- [x] **Íconos reales por emote:** `EmoteData.iconPng` + `EmoteIconProvider` — extraído del PNG
      embebido en un `.emotecraft` binario (ambas versiones) o de un `<nombre>.png` hermano de un
      `.json`. `EmoteWheelScreen` ya no usa la letra fija por defecto.
- [x] `mod_version` → 0.49.0. Build + 29/29 tests → `dist/steampad-0.49.0.jar`.
- [ ] Validación en hardware (checklist completo) → B082.

---

## FASE 66: v0.48.0 — Deformación real (torso-relativo) + transición de cámara + preview animado ×3 + multi-rueda (sesión 28 cont. 4) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> Tercera ronda de feedback sobre la deformación del emote (con capturas) — resultó ser una tercera
> causa raíz distinta a las de FASE 64/65, esta vez confirmada en la documentación oficial de
> Emotecraft. Detalle completo: D084, TODO_BLOCKERS.md B081.

- [x] **Deformación — causa raíz REAL #3:** los docs oficiales dicen "la ubicación de cabeza/piernas/
      brazos es relativa a la ubicación Y ROTACIÓN del torso" — el código aplicaba offsets planos sin
      rotar, correcto solo con el torso sin girar. Fix: el offset local de cada hijo se rota por la
      matriz de rotación ACTUAL del torso (`Matrix3f.rotationZYX`, mismo orden que `ModelPart.rotate()`,
      verificado con javap) antes de sumarse a su origen de reposo.
- [x] **Transición de cámara zoom out/in** al entrar/salir de un emote desde 1ª persona (smoothstep,
      220ms, vía el mismo mixin de `ThirdPersonCameraMixin`).
- [x] **Preview animado extendido** a `EmoteWheelScreen` (editor) y `EmoteWheelOverlay` (rueda en
      gameplay) — mismo mecanismo que la Biblioteca, nunca pisa un emote real, nunca se sincroniza.
- [x] **`.emotecraft` reescrito** sin depender del dialecto de glob de `DirectoryStream` (comparación
      manual de extensión) + logging de diagnóstico (archivos vistos/cargables/parseados).
- [x] **Multi-rueda de emotes:** `EmoteWheelScreen` ahora tiene la misma fila de gestión de ruedas que
      el editor radial (◀ ▶ + −); el controlador ya soportaba múltiples páginas desde la decoupling.
- [x] Bug real encontrado en revisión propia (herramientas de compilación temporalmente no disponibles):
      la fila de conteo de espacios llamaba al método de las ruedas RADIALES en vez del de emotes —
      corregido ANTES de la primera compilación real.
- [x] `mod_version` → 0.48.0. Build + 29/29 tests → `dist/steampad-0.48.0.jar`.
- [ ] Validación en hardware (checklist completo) → B081.

---

## FASE 65: v0.47.0 — Deformación real + teclado ×6 + preview animado + Third-Person v2 (sesión 28 cont. 3) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> Segunda ronda de feedback tras v0.46.0: la deformación seguía pasando, más 6 pedidos de teclado y la
> insistencia del usuario en el port completo de Third-Person. Detalle completo: D083, B080.

- [x] **Deformación — causa raíz REAL (D082 no bastaba):** el modelo de jugador es COMPARTIDO entre
      TODAS las entidades (solo 2 instancias: brazos anchos/flacos) — el reposo por-Playback de D082
      podía capturar un valor ya corrupto por OTRO emote tocando el mismo modelo. Fix: caché estática
      `IdentityHashMap<ModelPart,float[]>`, aprendida de cualquier entidad SIN emote activo (garantizado
      limpio) — autocorrectiva.
- [x] **Biblioteca de emotes:** soporta `*.emotecraft` (misma causa que "Actualizar no detectaba" —
      docs oficiales de Emotecraft confirman que el formato real es siempre `.json`; `.emotecraft` es
      solo una extensión renombrada de la comunidad, mismo contenido).
- [x] **Teclado — selección oculta hasta mover un stick**, auto-oculta tras 1.4s de inactividad (D-pad
      exento, necesita resaltado persistente).
- [x] **Teclado — "A" respeta el último stick usado** (D-pad cuenta como actividad del slot izquierdo).
- [x] **Teclado — footer de hints comprime el espacio entre hints** en vez de perder silenciosamente
      los que no caben (el hint de mover el teclado había desaparecido así).
- [x] **Glifos de inventario ocultos** mientras el teclado virtual está activo.
- [x] **Gesto golpe-vs-mantener:** un flick rápido avanza una tecla (como D-pad); mantener sigue
      exactamente igual que antes (cero latencia añadida, la corrección es retroactiva al soltar).
- [x] **Rebase de velocidad del teclado:** CRUISE/MAX a la mitad — 50% guardado debe subirse a 100%.
- [x] **Preview animado en Biblioteca de emotes:** `InventoryScreen.drawEntity` (mecanismo ya optimizado
      de Mojang) renderiza a `mc.player` posado por el emote previsualizado — nunca si hay un emote
      real corriendo, sin pasar por `playLocal()` (sin salto de perspectiva ni red).
- [x] **Third-Person v2:** confirmado (leyendo `AbstractConfig.java` completo) que el free-look es un
      problema de ACOPLAMIENTO DE ENTRADA, no de cámara — se mantiene fuera de esta sesión a propósito.
      Ampliado en cambio: suavizado exponencial por vida-media en la transición de lado/offset, y
      perfil de cámara de apuntado (se acerca al cargar arco/ballesta/tridente, misma detección que
      `AimAssistController`).
- [x] `mod_version` → 0.47.0. Build + 29/29 tests → `dist/steampad-0.47.0.jar`.
- [ ] Validación en hardware (checklist completo) → B080.

---

## FASE 64: v0.46.0 — Fixes de emotes ×4 + LogUtil + diagnósticos + Mejor Tercera Persona (sesión 28 cont. 2) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> Primera prueba real en hardware de FASE 63 y de Steam Input v0.43-0.45. Detalle completo: D082,
> TODO_BLOCKERS.md B079.

- [x] **Deformación del emote (causa raíz real):** `EmoteAnimator.applyPart()` sumaba el offset sobre
      `part.originX/Y/Z` cada frame sin una base — el pivote de un `ModelPart` es una constante que
      vanilla nunca resetea por frame, así que el offset se acumulaba sin límite mientras el emote
      seguía. Fix: cada `Playback` captura el origin de reposo una vez y calcula absoluto (resto+delta).
- [x] **Cámara vuelve a 1ª persona sola** al terminar el emote local (`autoSwitchedPerspective`,
      solo revierte si el jugador no cambió de perspectiva por su cuenta mientras tanto).
- [x] **Rueda de emotes 100% desacoplada del radial:** nueva lista independiente
      `RadialConfig.emoteWheels` (migración automática desde el flag legado `WheelConfig.emoteWheel`) +
      controlador propio `EmoteWheelController` + overlay propio `EmoteWheelOverlay`.
      `RadialRenderer.render()` ahora recibe wheelCount/page como parámetros explícitos.
- [x] **Preview fijo del lado derecho** (`EmotePreviewPanel`): Biblioteca de emotes (lista dividida,
      actualiza con foco/hover), editor de la rueda, y la rueda en gameplay. Silueta animada
      deliberadamente NO implementada esta sesión (nice-to-have, presupuesto de tiempo).
- [x] **`LogUtil.debug()` enrutado a INFO** — el root logger de un cliente en producción descarta DEBUG
      por defecto; esto explica por qué el diagnóstico de slime (3 sesiones atrás) nunca tuvo evidencia.
- [x] **Diagnóstico (no fix a ciegas) de slime y Traveler's Backpack:** logging dedicado nuevo en
      `HapticsController`/`ExternalWidgetScanner`, ahora realmente visible gracias al fix de LogUtil.
- [x] **Detección de la Ally documentada:** `GlfwControllerProvider.poll()` no filtra por reconocimiento
      de mapeo — si no ve el pad sin el 8BitDo, es el SO/Steam, no el mod. Log de confirmación agregado.
- [x] **"Mejor tercera persona"** — port acotado de `Leawind/Third-Person` (MIT, verificado): offset
      lateral de cámara (`ThirdPersonCameraController` + `ThirdPersonCameraMixin`, TAIL inject sobre
      `Camera.update()`, firma verificada con javap) con raycast propio para nunca atravesar paredes.
      Toggle + lado (Izq/Centro/Der) + slider de offset en Ajustes Globales; bind ciclable
      `THIRD_PERSON_SIDE_CYCLE`. El free-look desacoplado del mod original, apuntado predictivo y
      transparencia del jugador quedan explícitamente FUERA de esta sesión (ver D082).
- [x] `mod_version` → 0.46.0. Build + 29/29 tests → `dist/steampad-0.46.0.jar`.
- [ ] Validación en hardware (checklist completo) → B079.

---

## FASE 63: v0.45.0 — Sistema de emotes nativo (sesión 28 cont.) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> Compatible con los .json de emotes que la comunidad ya comparte, sin una línea de código ajeno
> (Emotecraft es GPL-3.0). Detalle completo: D080; pendientes: B077.

- [x] Motor propio: `Easing` + `EmoteData` + `EmoteJsonParser` (formato desde documentación pública,
      tolerante) + `EmoteAnimator` (wall-clock, reemplazo selectivo de canales, fade de salida,
      cancelación al moverse, salto a 3ª persona) + mixin fino `PlayerEntityModelMixin` (TAIL).
- [x] Sin dependencia externa: PAL no tiene build 1.21.10 (verificado en su Maven) — motor propio evita
      el hazard del Fix 12.
- [x] Biblioteca: bundled CC0 ×12 (curados, sin NSFW, LICENSE.md de atribución) + `.minecraft/emotes`
      (compat directa con lo ya descargado) + `config/steampad/emotes`. Test de build valida los 12.
- [x] Rueda de emotes: rueda dedicada en el sistema radial existente (`emoteWheel` flag,
      `RadialActionType.EMOTE`, `openAt`), bind `EMOTE_WHEEL` (unbound, hold-select-release).
- [x] UI mando: `EmoteWheelScreen` (editor, junto a "Configurar menú radial" en Botones) +
      `EmoteLibraryScreen` (búsqueda + ▶ preview + refresh). i18n ×3.
- [x] Red v1: `steampad:emote_c2s/s2c`, relay en servidor con SteamPad, no-op silencioso en vanilla.
- [x] Build + 29/29 tests → `dist/steampad-0.45.0.jar`.
- [ ] Hardware: reproducir bundled, asignar rueda, verlo entre 2 instancias — y calibrar offsets → B077.

---

## FASE 62: v0.44.0 — Revertir supresión del gamepad virtual + nativas + anti-lag (sesión 28) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> v0.43.0 reprobó en hardware (0 pads en Game Mode, lag masivo). Detalle: D079, B076.

- [x] `deployIgaManifest` (default OFF) + `cleanupOurManifests()` al arranque (borra solo VDFs con
      huella SteamPad) + toggle experimental con advertencia en Ajustes de Steam Input, i18n ×3.
- [x] `SteamNativeLoader`: customNativesPath → `SharedLibraryExtractPath` (extracción de bundleadas,
      cascada de fallback; verificado contra el fuente steamworks4j 1.9.0). Hint de diagnóstico corregido.
- [x] Anti-lag: throttle pump SDL (1/4 ms), puntero del teclado por tiempo real (`tickScale`),
      escáner memo 250 ms + off con teclado activo.
- [x] `TickProfiler` (nuevo): desglose por sección en el log SOLO si el mod excede presupuesto — el
      próximo latest.log identifica al culpable si el lag persiste.
- [x] Build + tests → `dist/steampad-0.44.0.jar`.
- [ ] Validación en hardware → B076.

---

## FASE 61: v0.43.0 — Fix ActivateActionSet + puente de acciones + revisión del VDF + lote de 6 (sesión 27) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> B074 reprobó COMPLETO en hardware pese al panel en verde. Cuarta causa raíz encontrada leyendo el
> código: los ActionSets jamás se activaban en el entorno real del usuario. Detalle completo en
> DECISIONS.md D078, TODO_BLOCKERS.md B075.

- [x] **Causa raíz #4:** `ActivateActionSet` solo se llamaba tras el early-return del path fallback
      (nunca en Game Mode). Movido a `SteamSlotDispatcher.tick()` — cada tick, ambos paths, todos los
      controles Steam conectados, con log al cambiar de set. La llamada muerta se eliminó.
- [x] **Puente completo de acciones:** ~16 acciones nombradas (saltar/atacar/usar/radial/…) →
      `VirtualBindInput` fuente ACTION (OR con la fuente SLOT — mapas independientes); sticks
      Steam → movimiento/cámara con merge por magnitud en `GamepadInputDispatcher`/`CameraController`.
      Log one-shot `First live Steam Input action data received` como evidencia de pipeline.
- [x] **VDF:** `major_revision 3` (invalida la plantilla vieja del usuario construida contra los sets
      renombrados — doc oficial de Valve) + joystick_move/camera en LOS 4 sets (el picker solo ofrece
      las acciones del set en edición). Copia suelta y recurso bundleado sincronizados y verificados
      dentro del jar.
- [x] **Overlay de binds con START sostenido (~500 ms):** HUD a FULL + binds apilados en inventarios;
      tap corto = pausa al soltar; compuesto con chords (modificador vía gate existente, trigger vía
      `pauseHoldShadowed`); PAUSE con chord propio o por ranura virtual sin cambios.
- [x] **Teclado dual-stick estilo Steam:** 2 punteros con la misma curva compartida (`floatSpeed`),
      orbes semitransparentes, LB/RB presionan por puntero, A intacta, caret → L3/R3, POINTER mode
      por mitades, teclas que se hunden 130 ms. Toggle `virtualKeyboardDualStick` (ON default).
- [x] **Traveler's Backpack:** resolución de espacio de coordenadas (relativas al panel vs absolutas,
      por intersección con el rect del GUI — accessors backgroundWidth/Height verificados con javap).
- [x] **REI:** `ReiCompat` — puente por reflexión a la API pública (`REIRuntime.getOverlay()` →
      children recursivo → `getBounds()`), targets individuales del catálogo, cap 400, auto-off ante
      error. Memo de 90 ms en `ExternalWidgetScanner.discover()`.
- [x] **Snap del mouse virtual:** toggle global `virtualMouseSnapEnabled` (nuevo Kind.TOGGLE en el
      catálogo) en Botones → Mouse Virtual — solo apaga el imán, el D-pad sigue.
- [x] **Cambiar de mano** → sección Movimiento (junto a saltar/agacharse) + Category.MOVEMENT.
- [x] **Vibración de inicio:** `connectRumble()` único (0.12/15 ms) + stop explícito a ~100 ms +
      cooldown de 3 s contra ráfagas de enumeración — fix de mecanismo, no de valor.
- [x] i18n ×3 (en/es_mx/es_es), JSON validado. `mod_version` → 0.43.0. Build + 24/24 tests →
      `dist/steampad-0.43.0.jar`.
- [ ] Validación en hardware (checklist completo) → B075.

---

## FASE 60: v0.42.0 — Fix orden de arranque de config + VDF de 4 sets por contexto (sesión 26 cont. 20) ❌ REPROBADO EN HARDWARE (sesión 27) → continúa en FASE 61

> v0.41.0 validado a medias en hardware: el overflow SÍ quedó corregido (detected positivo, manifiesto
> desplegado, Steam muestra las acciones), pero los botones asignados no reaccionaban — tercera causa
> raíz encontrada en el log. Detalle completo en DECISIONS.md D077, TODO_BLOCKERS.md B074.

- [x] **Causa raíz #3 (orden de arranque):** `SteamBootstrap.init()` corría ANTES de
      `ConfigManager.loadAll()`, y `getGlobal()` devolvía DEFAULTS (steamAttachMode=NUNCA) — el
      "Siempre" guardado nunca se leía al arranque. Doble fix: loadAll() al paso 0 del init del
      cliente + getGlobal() con carga perezosa del archivo real.
- [x] **VDF rediseñado:** 4 sets (`SteamPad_Gameplay/Menu/Inventory/Mounted`) = las 4 capas del mod;
      acciones en orden lógico; sticks "Joystick izquierdo — Mover"/"Joystick derecho — Cámara"
      (la opción de joystick puro pedida); ranuras en todos los sets con "(se asigna en el mod)";
      localización en/es/latam. Copia suelta sincronizada.
- [x] **`SteamActionRegistry.activateSetFor(Context)`** con fallback a Menú/Jugabilidad para VDFs
      viejos de 2 sets; `InputBindingManager` activa por `SteamSlotDispatcher.currentContext(mc)`.
- [x] Ranuras: se quedan (puente paddles→acciones del mod por capa), ahora autoexplicativas en Steam.
- [x] Bindings por defecto: documentado honestamente como imposible sin AppID de tienda (partner site).
- [x] `mod_version` → 0.42.0. Build + 24/24 tests → `dist/steampad-0.42.0.jar`.
- [ ] Validación en hardware (arranque con ALWAYS persistido, 4 sets en el configurador tras reiniciar
      Steam, paddle→Saltar y paddle→Ranura 1→Menú Radial, cambio de set al montar/abrir inventario)
      → B074.

---

## FASE 59: v0.41.0 — Fix overflow de AppID + modo de conexión al instante + reorg de UI (sesión 26 cont. 19) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> El usuario probó v0.40.0 en Game Mode y "no funcionó" — el log real reveló DOS causas raíz exactas.
> Detalle completo en DECISIONS.md D076, TODO_BLOCKERS.md B073.

- [x] **Overflow de AppID:** `SteamAppId=4221053437 → detected=-73913859` en el log real. Los
      pseudo-AppIDs de shortcuts (crc32 | 0x80000000) SIEMPRE exceden Integer.MAX_VALUE — pipeline
      completo convertido a `long` (`SteamLaunchDetector`, `EnvironmentReport`, `SteamBootstrap`,
      `SteamControllerConfigDeployer`). Nuevo `resolveSdkAppId()`: steam_appid.txt recibe 480 (AppID
      real validable) en sesiones de shortcut; el VDF se despliega al pseudo-AppID del shortcut + 480.
- [x] **Cambio de modo aplica al instante:** `applyAttachModeNow()` — AUTO/SIEMPRE llama
      `SteamBootstrap.init()` (retry-safe existente), NUNCA llama `shutdown()`. El log real mostró
      NEVER al arranque pese a la captura con "Siempre" — el cambio en juego no hacía nada.
- [x] **Reorg de UI:** sección "Steam Input" propia bajo Backends (solo modo de conexión); capas
      Menú/Inventario/Montado movidas a Botones → sección Steam Input (nuevo `Kind.LAYER`).
- [x] `mod_version` → 0.41.0. Build + 24/24 tests → `dist/steampad-0.41.0.jar`.
- [ ] Validación en hardware (Game Mode: attach en vivo, `detected` positivo en el log, VDF para el
      pseudo-AppID, acciones visibles en el configurador de Steam) → B073.

---

## FASE 58: v0.40.0 — Steam Input: attach mode expuesto + ranuras a cualquier acción + 4 capas (sesión 26 cont. 18) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> El usuario marcó Steam Input como prioridad máxima ("resolverlo ya") y pidió profundizar por completo:
> layers auto-detectados y que las ranuras acepten cualquier acción, no solo keybinds. Detalle completo
> en DECISIONS.md D074/D075, TODO_BLOCKERS.md B072.

- [x] **Causa raíz real de por qué Steam Input nunca conectaba:** `GlobalConfig.steamAttachMode`
      defaultea a `NEVER` y nunca estuvo expuesto en la UI, solo editable a mano en el JSON. Nueva
      pantalla `SteamInputSettingsScreen` (Ajustes Globales → "Ajustes de Steam Input…") con control
      cíclico AUTO/SIEMPRE/NUNCA + descripción honesta de la contrapartida (Steam puede quitarle el
      control a SDL3 al conectarse).
- [x] **Ranuras asignables a cualquier acción interna de SteamPad:** nuevo `VirtualBindInput` — un OR
      adicional al inicio de `bHeld()`/`bPressed()` en `GamepadInputDispatcher`, sin tocar la lógica
      física existente. Nuevo selector `SteamSlotTargetPickerScreen` (Acciones de SteamPad + Atajos de
      teclado, ambos buscables). Formato `"bind:NOMBRE"`, 100% retrocompatible.
- [x] **4 capas de ranuras** (`SteamSlotDispatcher.Context`): Jugabilidad (sin cambios, en Botones) +
      3 nuevas (Menú/Inventario/Montado), cada una con su propio mapa global, contexto recalculado
      cada tick, re-resolución en vivo si cambia de capa a mitad de una pulsación sostenida. 3 nuevas
      pantallas `SteamSlotLayerScreen`.
- [x] `mod_version` → 0.40.0. Build + 24/24 tests → `dist/steampad-0.40.0.jar`.
- [ ] Validación en hardware (AUTO/SIEMPRE realmente conecta, las 4 capas disparan correctamente,
      Menú Radial/Zoom asignados desde una ranura funcionan) → B072.

---

## FASE 57: v0.39.0 — Panel de prueba de haptics + debug dump ampliado (sesión 26 cont. 17) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> Herramientas de diagnóstico pedidas explícitamente por el usuario. Detalle completo en
> DECISIONS.md D073.

- [x] `HapticsController.TEST_PRESETS` (26 presets) + `testFire()` — dispara cualquier efecto por
      nombre con sus valores reales, ignorando el cooldown/ocupación del canal.
- [x] Nueva pantalla `HapticsTestScreen` — lista los 26 presets en 4 secciones, accesible desde
      Ajustes Avanzados del control.
- [x] Debug dump ampliado: sección `-- Haptics --` (canal ocupado, multiplicadores del control activo,
      estado del radial) y `-- Screen Widgets --` (children() + objetivos externos detectados con
      coordenadas).
- [x] `mod_version` → 0.39.0. Build + 24/24 tests → `dist/steampad-0.39.0.jar`.
- [ ] Validación en hardware (que cada botón del panel vibre, formato del debug dump) → pendiente.

---

## FASE 56: v0.38.0 — Causa raíz real de Traveler's Backpack + bug real de vibración de inicio + diagnóstico de slime (sesión 26 cont. 16) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> El usuario reportó 3 puntos "sigue sin funcionar" y cuestionó el rigor de la investigación anterior.
> Esta ronda usa evidencia real (código fuente de Traveler's Backpack) en vez de otro ajuste a ciegas.
> Detalle completo en DECISIONS.md D071/D072, TODO_BLOCKERS.md B070.

- [x] **Traveler's Backpack — causa raíz confirmada leyendo su código fuente real** (rama
      `1.21.10-fabric`, repo oficial): usa su propia interfaz `IButton`, sin relación con
      `ClickableWidget` de Minecraft — el escáner anterior nunca podía encontrarlos.
      `ExternalWidgetScanner` reescrito para reconocer por duck-typing cualquier objeto con campos
      x/y/width/height, sin requerir un tipo específico.
- [x] **Vibración de inicio — bug real:** 2 llamadas de rumble de reconexión quedaron en el valor
      viejo (0.45/80ms) sin tocar en 3 rondas previas de ajuste. Unificadas en una sola constante
      compartida (0.15/20ms).
- [x] **Vibración de slime — diagnóstico en vez de un 4º ajuste a ciegas:** logging agregado a
      `tickSquishyUnderfoot()` para confirmar si el pulso se dispara o no en la próxima prueba.
- [x] `radialSelectHapticsIntensity` default 100%→40% (piso medido por el usuario).
- [x] `mod_version` → 0.38.0. Build + 24/24 tests → `dist/steampad-0.38.0.jar`.
- [ ] Validación en hardware, especialmente Traveler's Backpack (prioridad más alta del usuario) → B070.

---

## FASE 55: v0.37.0 — Vibración de selección radial: más baja/breve + slider de intensidad (sesión 26 cont. 15) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> Ajuste directo sobre D067 (v0.34.0), confirmada como "genial" pero un poco fuerte. Detalle completo en
> DECISIONS.md D070, TODO_BLOCKERS.md B069.

- [x] Intensidad base 0.08→0.05, duración 35ms→25ms.
- [x] Nuevo `GlobalConfig.radialSelectHapticsIntensity` (10%-150%, default 100%) + slider en Ajustes
      Globales debajo del interruptor existente.
- [x] `mod_version` → 0.37.0. Build + 24/24 tests → `dist/steampad-0.37.0.jar`.
- [ ] Validación en hardware → B069.

---

## FASE 54: v0.36.0 — Fix real: detección de botones de mods también en pantallas con casillas (sesión 26 cont. 14) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> El usuario probó v0.35.0 con captura de un botón de Traveler's Backpack entre dos casillas — seguía sin
> funcionar. Detalle completo en DECISIONS.md D069, TODO_BLOCKERS.md B068.

- [x] Causa raíz real: `ExternalWidgetScanner` de v0.35.0 solo se conectó a `WidgetSnap`/
      `GuiFocusNavigator` (pantallas sin casillas) — `SlotSnap` (el sistema que de verdad maneja el
      snap dentro de inventarios con casillas) nunca lo consultaba, así que un botón de mod fuera de
      `children()` nunca era candidato ahí y las casillas vecinas siempre ganaban por descarte.
- [x] `SlotSnap.targets()` ahora agrega también los widgets de `ExternalWidgetScanner.discover(screen)`
      — compiten por distancia real contra casillas y widgets normales, igual que ya hacían estos
      últimos dos.
- [x] Vibración de inicio: tercera reducción, 45ms/0.22 → 25ms/0.15.
- [x] `mod_version` → 0.36.0. Build + 24/24 tests → `dist/steampad-0.36.0.jar`.
- [ ] Validación en hardware con Traveler's Backpack (LA PRUEBA CLAVE) y REI si es posible → B068.

---

## FASE 53: v0.35.0 — Detección genérica de botones de mods para D-pad/snap (sesión 26 cont. 13) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> Aclaración de alcance del punto 6 pendiente de v0.34.0: no una auditoría manual, sino detección
> genérica. Detalle completo en DECISIONS.md D068, TODO_BLOCKERS.md B067.

- [x] Nuevo `ExternalWidgetScanner`: descubre por reflexión `ClickableWidget`s que una pantalla posee
      pero nunca registra en `screen.children()` (patrón REI/Architectury) — campos cacheados por
      clase, escanea campo directo + un nivel dentro de List/array/Map.
- [x] `WidgetSnap.nearest()` y `GuiFocusNavigator.navigables()` ahora incluyen estos widgets externos.
- [x] `GuiFocusNavigator.activate()`: widgets normales siguen con `mouseClicked()` directo (sin
      cambios); widgets externos usan `VirtualMouseController.simulateLeftClick()` (misma ruta real
      que ya funciona para REI, D063) en vez de invocar su `mouseClicked()` a mano.
- [x] `mod_version` → 0.35.0. Build + 24/24 tests → `dist/steampad-0.35.0.jar`.
- [ ] Validación en hardware con REI y, si es posible, otro mod similar (ej. Traveler's Backpack) → B067.

---

## FASE 52: v0.34.0 — Fix regresión DUP+Radial + tercer intento de slime + haptic de selección radial (sesión 26 cont. 12) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> 4 puntos de feedback sobre el lote v0.33.0 + 2 auditorías grandes explícitamente pospuestas por
> presupuesto de tokens del usuario. Detalle completo en DECISIONS.md D067, TODO_BLOCKERS.md B066.

- [x] Fix regresión: `tickChordModifierGate`/`bPressed()` en `GamepadInputDispatcher` diferían el
      flanco de apertura de CUALQUIER bind `held=true` (no solo tap) que comparta botón con un
      modificador de chord — rompía "mantener para abrir" en Menú Radial (síntoma exacto reportado con
      DUP) y potencialmente en Sprint/Zoom/Lista de jugadores/Ataque/Usar. Acotado con `!bind.held` en
      la condición del gate.
- [x] Vibración de slime al caminar — tercer intento: tier COSMETIC→AMBIENT, magnitud 0.13→0.3,
      cadencia calcada del vuelo con élitros (150ms intervalo / 200ms duración, se solapan) en vez de
      seguir ajustando a ciegas los dos intentos previos que no se sintieron.
- [x] Vibración de inicio reducida de nuevo: 90ms/0.4 → 45ms/0.22.
- [x] Nueva vibración haptic al seleccionar en el menú radial (gameplay): pulso breve (35ms, 0.08 base)
      en `RadialMenuController.navigate()`/`updateAnalog()` vía `HapticsController.radialSelectPulse()`;
      toggle propio en Ajustes Globales (`radialSelectHaptics`, activo por defecto).
- [x] Confirmado sin cambios de código: aislamiento de configuración por control ya correcto
      (`ConfigManager` cachea por handle, mapas separados por tipo de config).
- [ ] **NO abordado este lote (pospuesto, requiere acotar alcance con el usuario):** auditoría completa
      de chords en todos los escenarios; auditoría de snap del mouse virtual + alcance del D-pad en
      mods de terceros con estructura tipo REI (ej. Traveler's Backpack).
- [x] `mod_version` → 0.34.0. Build + 24/24 tests → `dist/steampad-0.34.0.jar`.
- [ ] Validación en hardware → B066 (todos los puntos, especialmente el tercer intento de slime).

---

## FASE 51: v0.33.0 — Vibración de slime realmente continua + el Warden respeta el "enganche" (sesión 26 cont. 11) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> 2 puntos de seguimiento sobre el lote de haptics de v0.32.0. Detalle completo en DECISIONS.md D066,
> TODO_BLOCKERS.md B065.

- [x] Vibración de slime: rediseñada de 90ms/0.045 (aún imperceptible en hardware real) a disparo en
      CADA tick de cliente (~50ms) con pulsos de 140ms que se superponen — zumbido continuo real en el
      motor, no solo en la lógica de intervalos — a magnitud 0.13. Acotado a "solo en slime"; la miel
      quedó en una rama separada sin tocar (280ms/0.05, como antes).
- [x] El Warden ahora respeta el "enganche" al golpearlo: causa raíz real encontrada — está
      deliberadamente excluido del sistema `engagedBosses`/`pollBossProximity` porque su pulso de
      dread vive en `pollMobs`, una función separada que nunca consultaba ese set. Nuevo flag
      independiente `wardenEngaged` + helper compartido `markEngagedIfBossOrWarden` usado por
      `onMeleeHit`/`onEntityDamaged`, que dirige al Warden a su propio flag y a cualquier otro jefe al
      set genérico. Confirmado que no es un tema de modo creativo — la causa era arquitectónica.
- [x] `mod_version` → 0.33.0. Build + 24/24 tests (rerun forzado) → `dist/steampad-0.33.0.jar`.
- [ ] Validación visual en hardware → B065.

---

## FASE 50: v0.32.0 — Glifo de teclado en inventario + reposición DUP+RT + selector de rueda a eliminar + 3 fixes de haptics (sesión 26 cont. 10) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> 6 puntos de feedback. Detalle completo en DECISIONS.md D065, TODO_BLOCKERS.md B064.

- [x] Glifo de "Abrir teclado" agregado a `renderContainerHints` — solo visible cuando el bind está
      asignado, mismo patrón que el resto de hints opcionales.
- [x] Nuevo chord DUP+RT mueve el panel del teclado arriba/abajo (`VirtualKeyboard.positionTop` +
      `togglePosition()`, `KeyboardGeometry.layout` con parámetro `top`). RT sin DUP sigue siendo
      Enter normalmente. Glifo agregado al footer del teclado (`FOOTER_HINTS`).
- [x] Chat: `chatPushUp()` retorna 0 si el teclado está arriba — la caja de texto vanilla ya no se
      empuja innecesariamente.
- [x] Nueva pantalla `RadialWheelDeleteScreen`: selector explícito de qué rueda eliminar, etiquetada
      vacía/con N atajos (`RadialConfig.configuredSlotCountFor`), con confirmación si tiene contenido.
      El botón ✕ del editor ahora abre este selector en vez de borrar directo la rueda abierta (bug
      real corregido: podía borrar la rueda CON atajos mientras una vacía quedaba intacta).
- [x] Vibración de slime al caminar: intervalo 280ms→90ms, duración de pulso 60ms→80ms (casi sin
      hueco entre pulsos, se lee como zumbido continuo), magnitud ligeramente bajada a 0.045 para
      compensar la frecuencia triplicada.
- [x] Vibración de jefes con daño a distancia: `ClientPlayNetworkHandlerMixin.onEntityDamage` ya
      recibía el paquete de daño de cualquier entidad cercana pero lo descartaba si no era el propio
      jugador — nuevo `HapticsController.onEntityDamaged` lo aprovecha cuando el atacante es el
      jugador, cubriendo flechas y cualquier daño a distancia (no solo `onMeleeHit`).
- [x] Vibración se detiene en el menú de pausa: nuevo guard al inicio de `HapticsController.tick()`
      que corta el motor (`ControllerManager.rumble(handle, 0, 0, 0)`) una vez al entrar a pausa y
      salta todo el polling hasta cerrar el menú.
- [x] `mod_version` → 0.32.0. Build + 24/24 tests (rerun forzado) → `dist/steampad-0.32.0.jar`.
- [ ] Validación visual en hardware → B064.

---

## FASE 49: v0.31.0 — Fix de chords bloqueando su propio modificador + apertura manual del teclado + límites de REI documentados (sesión 26 cont. 9) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> 3 puntos de seguimiento tras confirmar que REI ya funciona para clics. Detalle completo en
> DECISIONS.md D064, TODO_BLOCKERS.md B063.

- [x] Fix real: DDOWN=Tirar (simple) y DDOWN+A=Chat (chord) disparaban AMBOS al mantener DDOWN+A —
      generalizado el patrón "diferir a la liberación" (ya usado solo para el chord Select+RB del
      splitscreen) a TODOS los chords del usuario vía nuevo `tickChordModifierGate` en
      `GamepadInputDispatcher`. `bPressed`/`bHeld` consultan el nuevo estado para cualquier bind sin
      chord propio cuyo botón coincida con el modificador de otro bind.
- [x] Nuevo bind `OPEN_KEYBOARD` (sin valor por defecto, se configura en Botones → Inventario) fuerza
      la apertura del teclado virtual en cualquier inventario vía nuevo `VirtualKeyboard.forceActivate()`
      — necesario porque REI (y mods con la misma estructura) no exponen su campo de texto de forma
      detectable. Corregido `VirtualKeyboard.update()`, que antes cerraba cualquier sesión forzada en
      el siguiente tick por no ser "elegible" (nuevo campo `forcedOpen`).
- [x] Registrado el nuevo bind en `ActionCatalog` (confirmado que es una lista explícita, no una
      iteración automática del enum — sin este registro el bind habría quedado invisible en Botones).
- [x] Investigado (sin fix): "empujar" la caja de texto de REI y el snap del cursor sobre sus ítems —
      ambos comparten la misma causa raíz (el overlay de REI vive fuera del árbol de la pantalla,
      confirmado en D063) y no tienen arreglo seguro sin un jar de REI real para verificar su API
      pública. Documentado como límite arquitectónico, no como bug pendiente.
- [x] Verificado que `ChordResolver`/`InputBindingManager` (con sus propios tests) es un sistema
      separado para Steam Input ActionSets, no duplicado por este fix — los controladores fallback
      (GLFW/SDL3, el path de toda esta sesión) nunca llegan a ese código.
- [x] `mod_version` → 0.31.0. Build + 24/24 tests (rerun forzado) → `dist/steampad-0.31.0.jar`.
- [ ] Validación visual en hardware → B063.

---

## FASE 48: v0.30.0 — Fix de Selección de Mundo (Borrar→Jugar) + compatibilidad real con REI (clic + tecleo) (sesión 26 cont. 8) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> 2 puntos de feedback: el fix de B061 (A con D-pad en Selección de Mundo) aterrizaba en "Borrar" en vez
> de "Jugar mundo"; y el usuario compartió el repo real de REI para investigar compatibilidad de
> interacción (no integración). Detalle completo en DECISIONS.md D063, TODO_BLOCKERS.md B062.

- [x] Selección de Mundo: `GuiFocusNavigator.activate()` ahora busca el botón por su texto traducido
      (`findByMessage("selectWorld.select")`) en vez de por geometría (`pickDirectional`, que aterrizaba
      en "Borrar") — con el pick geométrico como fallback defensivo solo si el botón no se encuentra.
- [x] REI investigado con el repo real: causa raíz 100% confirmada — REI usa Architectury API
      (`ClientScreenInputEvent`), implementada con mixins `@WrapOperation` sobre la llamada a
      `Screen.mouseClicked/keyPressed/charTyped` DENTRO de `Mouse.onMouseButton`/`Keyboard.onKey`/
      `onChar` — el mouse/teclado virtual del mod llamaba a `Screen` directamente, saltándose esos
      call sites por completo.
- [x] `VirtualMouseController` (clickAt/pressLeftDown/releaseLeft/pressRightDown/releaseRight) y
      `VirtualKeyboard` (typeChar/pressKey) ahora enrutan por los métodos reales
      (`Mouse.onMouseButton`, ya ensanchado y usado por `ActionExecutor`; `Keyboard.onKey`/`onChar`,
      recién ensanchados en el accesswidener) — arregla CUALQUIER mod basado en Architectury, no solo
      REI. Mismo patrón `INJECTING` que ya usa `moveOsCursor`/`ActionExecutor.pressMouseButton`.
- [x] `isTextWidget` amplía la detección con el patrón "SearchField" (cubre `OverlaySearchField` de
      REI y similares) — cambio de cero riesgo, solo amplía qué cuenta como campo de texto detectable.
- [x] Límite real encontrado y documentado (no arreglado): el buscador de REI nunca es hijo de
      `screen.children()`, así que el teclado virtual no puede detectar/abrir automáticamente sobre él.
      Se evaluó y descartó un heurístico basado en `FabricLoader.isModLoaded` por riesgo de secuestrar
      el botón A en todos los inventarios.
- [x] Scroll deliberadamente no tocado (mismo mecanismo disponible, pero riesgo de alterar la
      velocidad ya calibrada para usuarios con sensibilidad de mouse de Minecraft distinta de 1.0).
- [x] `mod_version` → 0.30.0. Build + 24/24 tests (rerun forzado) → `dist/steampad-0.30.0.jar`.
      `validateAccessWidener` (Loom) confirmó las firmas nuevas del accesswidener.
- [ ] Validación visual en hardware (con REI real instalado) → B062.

---

## FASE 47: v0.29.0 — Vibración de jefes con enganche pegajoso + aim assist sticky lock + íconos radiales + fix de chords en el HUD + X en Normal (sesión 26 cont. 7) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> 5 puntos de feedback de hardware. Detalle completo en DECISIONS.md D062, TODO_BLOCKERS.md B061.

- [x] Vibración de jefes: nuevo `Set<Integer> engagedBosses` (pegajoso, mismo patrón `retainAll` que
      `seenLightning`) — se calla tras ver al jefe de cerca O tras el primer golpe de melee (nuevo
      parámetro `target` en `ClientPlayerInteractionManagerMixin`→`onMeleeHit`), y solo se reinicia
      cuando el jefe sale de `BOSS_PING_RANGE`. Ya no se reactiva por micro-cortes de línea de visión
      en medio de la pelea.
- [x] Aim assist AAA: encontrado y corregido el `return` temprano en `CameraController.update()` que
      cortaba la llamada al aim assist en cuanto el stick derecho estaba quieto. Nuevo "sticky lock" en
      `AimAssistController` (timer de 350ms, se arma con stick activo O retículo ya casi centrado en el
      objetivo) — el magnetismo ahora tira brevemente incluso en reposo, imitando el rotational aim
      assist / "target sticking" de COD/BF.
- [x] Rueda radial: nuevo `RadialConfig.iconScale` (default 1.4, mismo patrón defensivo que
      `chipRadius`) aplicado con matriz de escala en `RadialRenderer`, sin tocar los 3 proveedores de
      ícono. Nuevo slider "Tamaño de íconos" en Apariencia, independiente de "Tamaño de espacios".
- [x] Fix de chords en el HUD: `GameplayHudOverlay` ahora consulta `GamepadBinds.chord()` además del
      botón principal y dibuja `[chord]+[principal]` cuando corresponde (confirmado que `BindingsScreen`
      ya lo hacía bien — el bug estaba solo en el HUD de gameplay). Cualquier bind con chord asignado se
      promueve de Full a Normal.
- [x] X (SWAP_HANDS) promovido de Full a Normal en los glifos de gameplay.
- [x] `mod_version` → 0.29.0. Build + 24/24 tests (rerun forzado) → `dist/steampad-0.29.0.jar`.
- [ ] Validación visual en hardware → B061.

---

## FASE 46: v0.28.0 — Fix de sombra del efecto de presión + slider de escala de glifos + selección de mundo con D-pad + investigación de REI (sesión 26 cont. 6) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> Feedback de hardware sobre v0.27.0, 5 puntos. Detalle completo en DECISIONS.md D061, TODO_BLOCKERS.md B060.

- [x] Splitscreen: re-investigado el flash reportado como "regresión" — `grep` confirmó cero cambios de
      código entre v0.26.0 y v0.27.0 en las rutas de ventana. Sin cambio de código, no es una regresión.
- [x] Fix real de la sombra del efecto de presión: el rectángulo `ctx.fill` (ignoraba el alpha del PNG)
      reemplazado por el overload de 13 args de `DrawContext.drawTexture` con `color` tint (encontrado
      con `javap -c`), que solo tiñe los píxeles que la textura realmente dibuja. Aplicado en
      `ButtonIcon` y `ControllerGlyphs`.
- [x] Slider de escala de glifos (Ajustes Globales → HUD → "Escala de la guía de botones", 50%-200%,
      reutiliza i18n huérfano ya existente) — aplicado en `GameplayHudOverlay` (HUD + hints de
      inventario) y `RadialRenderer` (hints de la rueda), deliberadamente NO en pantallas de ajustes de
      ancho fijo (Botones/Avanzado/teclado virtual) para evitar recortes.
- [x] Selección de Mundo: A con D-pad sobre una entrada resaltada ahora mueve el foco a la fila de
      botones de abajo (vía `pickDirectional`) en vez de unirse directo — una segunda A sí une. Acotado
      a `SelectWorldScreen`; el mouse virtual sigue entrando directo. Otras `EntryListWidget` (servidores,
      recursos) sin cambios.
- [x] REI (Roughly Enough Items): investigado sin jar local disponible. Confirmado que "REI Plugin
      Compatibilities" NO es una API de mando (shim de compatibilidad JEI→REI). Causa raíz de la falla
      de mouse virtual/teclado dentro de REI queda sin confirmar — documentado como bloqueador abierto
      con próximos pasos concretos, sin fix de código a ciegas.
- [x] `mod_version` → 0.28.0. Build + 24/24 tests (rerun forzado) → `dist/steampad-0.28.0.jar`.
- [ ] Validación visual en hardware → B060.

---

## FASE 45: v0.27.0 — Actualización de assets: 198 glifos nuevos + ícono del mod + efecto de presión (sesión 26 cont. 5) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> El usuario colocó arte nuevo en `import_buttons/` (6 carpetas de marca + ícono) y pidió actualizar
> todos los glifos, el ícono del mod, y un efecto visual de presión al presionar un botón. Detalle
> completo en STATE.md sesión 26 cont. 5, DECISIONS.md D060, TODO_BLOCKERS.md B059.

- [x] 198 archivos PNG (33 por marca × 6 marcas: 8bitdo/generic/ps/steam/xbox/xbox_elite) copiados a
      `textures/buttons/<marca>/`, mapeando MAYÚSCULA→stem contra `ButtonTextureManager.stemFor()`.
      Un archivo mal nombrado en el export (`RB-1.png`) identificado por inspección visual como el
      glifo real de `RT` antes de copiarlo.
- [x] `controller.png` (silueta de marca) deliberadamente NO tocado — las 6 imágenes del import son
      idénticas byte a byte a las existentes (verificado antes de copiar, habría sido un no-op).
- [x] Ícono del mod actualizado (`fabric.mod.json`, única referencia real confirmada por grep).
- [x] Efecto de presión nuevo: `GamepadInputDispatcher.isPhysicallyHeld(id)` + tratamiento visual
      (nudge 1px + overlay oscuro) en `ButtonIcon` y `ControllerGlyphs` — cubre menús, HUD de gameplay,
      radial, pestañas y teclado virtual. Sin impacto en el ancho devuelto (cero riesgo de layout).
- [x] `mod_version` → 0.27.0. Build + 24/24 tests → `dist/steampad-0.27.0.jar`.
- [ ] Validación visual en hardware → B059.

---

## FASE 44: v0.26.0 — Bug sistémico de i18n en controles cíclicos + auditoría de traducciones + investigación de flash + aim assist para mods de armas (sesión 26 cont. 4) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> El usuario dio 4 puntos de feedback sobre v0.25.0. Uno reveló un bug SISTÉMICO (no aislado) en el
> helper compartido de controles cíclicos, encontrado con `javap -c` (desensamblado de bytecode).
> Detalle completo en STATE.md sesión 26 cont. 4, DECISIONS.md D059, TODO_BLOCKERS.md B058.

- [x] **Fix sistémico:** `ColumnSettingsScreen.cycling()` ya no antepone manualmente el nombre de la
      opción — vanilla (`CyclingButtonWidget`) ya lo hace automáticamente vía
      `ScreenTexts.composeGenericOptionText`. Corrige TODOS los controles cíclicos del mod de una vez
      (Block Reach Around, Sneak/Sprint Mode, Gyro Behaviour, marcador de zoom, HUD, etc.).
- [x] Auditoría de i18n: panel de diagnóstico de `ControllerSelectScreen` completamente traducido
      (~35 claves nuevas `steampad.diag.*` ×3 idiomas); `steampad.controller.status.active/.connected`
      y las notificaciones de batería/debug conectadas a claves que ya existían pero nunca se usaban.
- [x] Flash de Splitscreen: investigado (orden real de eventos de Fabric API) — confirmado que no hay
      hook seguro disponible que lo elimine del todo. Documentado como limitación conocida, sin mixin
      riesgoso sobre el constructor de `Window` sin poder probarlo en hardware real.
- [x] Aim assist: investigados mods populares de armas — "Ranged Weapon API" confirmado cubierto por
      el chequeo `instanceof RangedWeaponItem` de v0.25.0; TaCZ confirmado como sistema no cubierto
      (disparo 100% propio), sin integración a ciegas. Descripción del ajuste actualizada con el
      alcance real.
- [x] Boost de render distance: tope del slider 16→8 chunks + descripción reforzada sobre el costo de
      rendimiento real (confirmado por el usuario que el fix de B057 ya funciona).
- [x] `mod_version` → 0.26.0. Build + 24/24 tests → `dist/steampad-0.26.0.jar`.
- [ ] Validación en hardware → B058.

---

## FASE 43: v0.25.0 — Feedback de v0.24.0: 2 bugs reales + flash de ventana + marcador live/contorno + barras cinemáticas + sensibilidad + aim assist + 3 fixes de UI (sesión 26 cont. 3) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> El usuario probó v0.24.0 y reportó 10 puntos. Dos resultaron ser bugs reales del lote anterior (no
> mejoras): el boost de render distance no hacía nada, y la detección de entidades del marcador nunca
> encontraba nada. Ambos investigados con javap y corregidos de raíz, no parcheados a ciegas. Detalle
> completo en STATE.md sesión 26 cont. 3, DECISIONS.md D058, TODO_BLOCKERS.md B057.

- [x] **Fix real — boost de render distance:** faltaba `GameOptions.sendClientSettings()` (el servidor
      nunca se enteraba del cambio) y subir `simulationDistance` junto con `viewDistance` (el servidor
      integrado no tiene cargados los chunks fuera de simulationDistance sin importar lo que pida el
      cliente). Ambas correcciones aplicadas + documentadas en la descripción del ajuste.
- [x] **Fix real — detección de entidades del marcador:** `ProjectileUtil.raycast` (intento anterior)
      nunca encontraba nada; reescrito con búsqueda manual (`World.getOtherEntities` + `Box.raycast`
      por candidato, mismo patrón que `AimAssistController`).
- [x] Splitscreen: hook adicional en `ScreenEvents.BEFORE_INIT` (antes del primer frame del render
      loop) para eliminar el flash de apertura centrada reportado.
- [x] Marcador de entidad: seguimiento en vivo (recalcula posición cada tick) + contorno vía
      `Entity.setGlowing(true)`, apagado automático al expirar/reemplazar/morir la entidad.
- [x] Barras cinemáticas nuevas en el zoom (toggle + slider de altura, Avanzado → Zoom).
- [x] `ControllerConfig.SENSITIVITY_REBASE = 0.65f` aplicado en `CameraController` +
      `InputBindingManager` — 1.0 en el slider ahora entrega el feel que antes daba 0.65.
- [x] Aim assist: también detecta `instanceof RangedWeaponItem` (cubre arcos/ballestas moddeados),
      además de `UseAction.BOW/CROSSBOW/SPEAR` vanilla.
- [x] `SteamPadBaseScreen`: scrollbar ahora arrastrable con mouse (`mouseClicked`/`mouseDragged`/
      `mouseReleased` nuevos) — beneficia a todas las pantallas con scroll.
- [x] `BindingsScreen`: fix de texto/glifo sin recortar en los bordes de scroll (criterio de
      visibilidad unificado con el que ya usa el widget subyacente).
- [x] `ControllerSelectScreen`: panel de diagnóstico colapsado por defecto (resumen de una línea),
      click para expandir/colapsar.
- [x] `mod_version` → 0.25.0. Build + 24/24 tests → `dist/steampad-0.25.0.jar`.
- [ ] Validación en hardware → B057.

---

## FASE 42: v0.24.0 — Feedback de Splitscreen (persistencia + fix de hueco) + HUD global + zoom (brillo, boost de render distance, marcar entidades) (sesión 26 cont. 2) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> El usuario probó v0.23.0 ("funciona sorprendentemente bien") y dio 6 puntos de feedback en un solo
> mensaje. También investigamos a fondo splitscreen REAL (viewports simultáneos) — decisión: dejarlo
> pendiente como proyecto experimental futuro, no como trabajo de esta sesión. Detalle completo:
> STATE.md sesión 26 cont. 2, DECISIONS.md D056 (splitscreen real) y D057 (este lote),
> TODO_BLOCKERS.md B056 (checklist) y P001 (proyecto futuro).

- [x] `GlobalConfig.windowArrangeMode` (persistido) — `WindowArrangeController.onFirstTick()` reaplica
      el último layout al arrancar si el toggle seguía activo; `setEnabled(bool)` captura/restaura un
      baseline (fullscreen o bounds de ventana) al activar/desactivar en vivo.
- [x] Fix de orden GLFW: `GLFW_DECORATED` se aplica ANTES de `glfwSetWindowMonitor` (antes al revés) —
      hipótesis del hueco visto solo en la primera transición decorada→sin-decorar.
- [x] `ButtonGuideDetail` (detalle de glifos del HUD) movido de `ControllerConfig` (per-mando) a
      `GlobalConfig` — control cíclico reubicado de Avanzado→HUD a Ajustes Globales→HUD.
- [x] Marcador de zoom: partículas más grandes (escala 1.2→2.0) y más densas (cada tick, antes cada 2).
- [x] `ControllerConfig.zoomRenderDistanceBoost` (0-16 chunks, default 0): sube temporalmente
      `mc.options.getViewDistance()` mientras se hace zoom, restaura al soltar — slider en Avanzado→Zoom.
- [x] `ZoomController.placeMarker` ahora también raycastea entidades vivas (`ProjectileUtil.raycast`)
      y marca la más cercana entre entidad y bloque — snapshot de posición, no sigue al mob.
- [x] Investigación de splitscreen REAL (WebSearch + javap): arquitectura viable identificada
      (host normal + sub-clientes invitados vía `ClientConnection.connectLocal`), probabilidades de
      éxito estimadas, riesgo de compatibilidad con el modpack de ~80 mods cuantificado. Dejado
      PENDIENTE como proyecto experimental futuro por decisión explícita del usuario — sin código.
- [x] `mod_version` → 0.24.0. Build + 24/24 tests → `dist/steampad-0.24.0.jar`.
- [ ] Validación en hardware → B056.

---

## FASE 41: v0.23.0 — "Splitscreen" (acomodo de ventana, no real): integración de pcal43/splitscreen + chord Select+RB (sesión 26) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> El usuario pidió integrar `https://github.com/pcal43/splitscreen` como ajuste global con interruptor
> activo/inactivo, activado con Select+RB (cada RB con Select sostenido cicla la posición de ventana).
> Leído el código real del repo antes de programar: no es splitscreen dentro del juego, solo acomodo de
> ventana del SO — el propio usuario ya lo había identificado así. También preguntó qué tan factible
> sería splitscreen REAL dentro del mod; respondido en el chat, sin código (choca con la Restricción 2
> de CLAUDE.md y es una tarea de escala distinta — ver D055). Detalle completo en STATE.md sesión 26,
> DECISIONS.md D055, TODO_BLOCKERS.md B055.

- [x] `WindowArrangeMode` (enum, `client/window/`): los 10 layouts del mod original (Windowed, 4
      mitades, 4 esquinas, Fullscreen) reimplementados limpio, con atribución MIT en el javadoc.
- [x] `WindowBounds` (record): rectángulo x/y/width/height compartido por la ventana y el monitor.
- [x] `WindowAccessor` (mixin `@Accessor` sobre `net.minecraft.client.util.Window`, mismo patrón que
      `HandledScreenAccessor`): expone `x/y/width/height` y `windowedX/Y/width/height` — nombres
      verificados con javap contra el jar mapeado 1.21.10 antes de escribir código. El flag
      `fullscreen` NO se expone; entrar/salir de pantalla completa usa el método público
      `Window.toggleFullscreen()` para no romper el estado interno de vanilla. Mixin puramente
      accessor, cero lógica (Restricción 4 de CLAUDE.md).
- [x] `WindowArrangeController` (clase plana): ciclo de modos, bounds del monitor vía `getMonitor()`/
      `getCurrentVideoMode()` (públicos, sin widener), reposición real vía GLFW. No auto-posiciona al
      arrancar (a diferencia del mod original) — solo actúa por el chord del usuario.
- [x] `GlobalConfig.windowArrangeEnabled` (default OFF) + `windowArrangeGap` (0–16px, default 1px).
- [x] Chord Select(BACK)+RB en `GamepadInputDispatcher` — gesto global hardcodeado (no un
      `GamepadBinds.Bind` rebindable), resuelto con hold-to-modify + defer-to-release para no chocar
      con las acciones normales de BACK (cursor virtual en menús, Perspective en gameplay). Con el
      toggle desactivado, cero cambio de comportamiento.
- [x] Sección "Splitscreen" en Ajustes Globales (interruptor + slider de espacio entre ventanas) +
      i18n ×3 (en_us/es_mx/es_es).
- [x] `mod_version` → 0.23.0. Build + 24/24 tests → `dist/steampad-0.23.0.jar`.
- [ ] Validación en hardware → B055.

---

## FASE 40: v0.22.0 — Steam Input "AAA": detección real de AppID + auto-deploy del VDF (sesión 25 cont. 5) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> El usuario pidió retomar B040 (6 sesiones en pausa) con investigación a fondo antes de programar.
> Research externo confirmó la hipótesis original (env vars `SteamAppId`/`SteamGameId`), reveló que
> `ISteamInput` no expone nombres reales de marca (B035 probablemente no es un bug), y que el VDF tiene
> una ruta de auto-descubrimiento (`controller_config/`) que el mod nunca había usado. Detalle completo
> en STATE.md sesión 25 cont. 5, DECISIONS.md D054, TODO_BLOCKERS.md B040/B054.

- [x] `SteamLaunchDetector` (nuevo, `platform/`): detecta el AppID real de la sesión desde
      `SteamAppId`/`SteamGameId` (con el algoritmo de hash de Valve para shortcuts no-Steam).
- [x] `SteamBootstrap.resolveEffectiveAppId()`: prefiere el AppID detectado sobre el 480 fijo; 480
      se conserva exactamente como fallback cuando no hay señal.
- [x] `SteamControllerConfigDeployer` (nuevo): auto-despliega `game_actions_<appid>.vdf` en
      `<Steam>/controller_config/` (ruta de auto-descubrimiento de Valve, sin importación manual) —
      siempre para 480, y también para el AppID real cuando se detecta.
- [x] `steamAttachMode.AUTO` amplía su gatillo: `gamescope OR launchedFromSteam` (antes solo gamescope).
      `NEVER`/`ALWAYS` sin cambio.
- [x] Confirmado por research: `ISteamInput` no expone nombre de marca real de los controles (solo
      categorías genéricas) — B035 no es un bug del mod, es limitación de la API. Sin cambio de código.
- [x] Confirmado: `steamworks4j` no envuelve `ISteamInput` en ninguna versión — se queda en
      `ISteamController` (Valve documenta paridad de funciones, sin pérdida real).
- [x] `mod_version` → 0.22.0. Build + 24/24 tests → `dist/steampad-0.22.0.jar`.
- [ ] Validación en hardware → B054 (incluye el diagnóstico que B040 pedía desde hace 6 sesiones,
      ahora automático vía log en vez de un comando manual de SSH).

---

## FASE 39: v0.21.0 — Niveles de detalle del HUD + marcador con distancia extendida y color (sesión 25 cont. 4) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> Petición directa del usuario (no un bug): (1) el HUD ampliado a 21 acciones en v0.20.2 satura la
> pantalla — pidió Completo/Normal/Mínimo, dejando a mi criterio qué va en "Normal" (estilo Bedrock).
> (2) el marcador de zoom deja de funcionar a cierta distancia — pidió una distancia muy grande o
> infinita si no hay costo de rendimiento. (3) colores configurables para el marcador, pidió que
> sugiriera ~6. Detalle en STATE.md sesión 25 cont. 4.

- [x] `ControllerConfig.ButtonGuideDetail` (MINIMAL/NORMAL/FULL, default NORMAL) + control cíclico
      "Detalle de glifos en juego" en Avanzado → HUD (sección nueva).
- [x] `GameplayHudOverlay.Hint` con campo `tier`: MINIMAL = 5 acciones básicas (Saltar/Agachar/Atacar/
      Usar/Inventario); NORMAL = + 5 más (Anterior/Siguiente/Radial/Chat/Zoom, el set de 10 original);
      FULL = las 21 completas de v0.20.2. Filtro por `ordinal()`, sin huecos entre niveles.
- [x] `ZoomController.MARKER_RAYCAST_DISTANCE` = 4096 bloques (antes 256) — raycast de un solo tiro por
      press de A, sin costo de rendimiento medible; aplica al hit real y al fallback de MISS.
- [x] `ControllerConfig.ZoomMarkerColor` (CYAN/WHITE/GOLD/MAGENTA/LIME/RED, default CYAN) + control
      cíclico "Color del marcador" en Avanzado → Zoom.
- [x] Partícula del marcador cambiada de `END_ROD` (sin color) a `DustParticleEffect` (color RGB
      arbitrario) — constructor `DustParticleEffect(int, float)` verificado con javap contra el jar
      mapeado 1.21.10 antes de escribir código.
- [x] `mod_version` → 0.21.0. Build + 24/24 tests → `dist/steampad-0.21.0.jar`.
- [ ] Validación en hardware → B053 (niveles de detalle, marcador a larga distancia, colores).

---

## FASE 38: v0.20.2 — Cobertura completa de glifos del HUD + fix del marcador en techo/saliente (sesión 25 cont. 3) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> El usuario validó B052 y B051 en hardware. B050 seguía fallando en 2 puntos; se preguntó para aclarar
> antes de tocar código. Respuestas: (1) glifos — el problema real era que el HUD de gameplay solo
> cubría 10 de las 21 acciones asignables, no un tema de timing del zoom. (2) marcador — "no pasa nada
> en absoluto", consistente con que la columna de partículas queda enterrada en roca sólida al marcar un
> techo. Detalle en STATE.md sesión 25 cont. 3, TODO_BLOCKERS.md B050.

- [x] `GameplayHudOverlay.LEFT/RIGHT` ampliados de 10 a las 21 acciones de `GamepadBinds.Bind` —
      cualquier acción asignada a un botón ahora tiene un hint en algún lado del HUD (reusa el
      `labelKey` propio de cada bind, ya traducido ×3, sin i18n nuevo).
- [x] `ZoomController.tickMarker()`: la columna de partículas crece hacia abajo (cara visible) en vez de
      hacia arriba (enterrada en roca) cuando el bloque marcado tiene un bloque sólido encima
      (`BlockState.isAir()` en `markerPos.up()`).
- [x] `mod_version` → 0.20.2. Build + 24/24 tests → `dist/steampad-0.20.2.jar`.
- [ ] Validar en hardware → B050: cobertura de glifos con acciones antes invisibles (sprint, drop,
      pick block, etc.) asignadas a un botón; marcador en un techo/saliente ahora visible.
- [ ] Puntos 1/2 originales de B050 (merge del pad de la Ally + filtro NVTK0603) siguen pendientes de
      repetir — sin dato nuevo esta sesión.

---

## FASE 37: v0.20.1 — HOTFIX: crash de arranque `IndexOutOfBoundsException` en GlfwSnapshotSource (sesión 25 cont. 2) ✅ CONFIRMADO EN HARDWARE

> El usuario reportó el juego SIN ARRANCAR con log de crash completo. Bug preexistente (no introducido
> en esta sesión) disparado por primera vez porque SDL3 no enumeró a tiempo y la cascada cayó a
> GLFW_FALLBACK. Detalle en STATE.md sesión 25 cont. 2, TODO_BLOCKERS.md B052.

- [x] Diagnóstico: `GlfwSnapshotSource.read()` iteraba hasta `GamepadSnapshot.BUTTON_COUNT` (23,
      incluye extras SDL3-only) en vez de `STANDARD_BUTTON_COUNT` (15, la constante que ya existía
      para esto) — `GLFWGamepadState.buttons()` solo tiene 15 slots reales, revienta en i=15.
- [x] Fix: loop primario capado a `STANDARD_BUTTON_COUNT`; índices 15-22 puestos en `false`
      explícitamente en la ruta GLFW (nunca legibles ahí, solo por SDL3).
- [x] `mod_version` → 0.20.1. Build + 24/24 tests → `dist/steampad-0.20.1.jar`.
- [x] Confirmar en hardware que el juego arranca sin crash (B052) — **CONFIRMADO** por el usuario
      (2026-07-10, sesión 25 cont. 3: "esta funcionando").

---

## FASE 36: v0.20.0 — Vibración de jefes + firmas de daño + limpieza de movimiento (sesión 25 cont.) ✅ MAYORMENTE VALIDADO EN HARDWARE

> El usuario comparó una matriz de vibración externa contra `HapticsController` y pidió planificar
> antes de programar. Tras 4 decisiones de alcance confirmadas (sprint sin vibración, boss bar +
> heurística ancho+alto+vida, cosmético curado conservando el cofre de tesoro, firmas por tipo de daño
> Y por atacante), se implementó todo el lote. Detalle en STATE.md sesión 25 cont., TODO_BLOCKERS.md B051.

- [x] Movimiento: caminar/sprint confirmados sin vibración; única excepción slime/miel EN MOVIMIENTO
      (silencio si el jugador está quieto encima).
- [x] Jefes cercanos (`isBossLike`): boss bar vanilla activa (widener nuevo `BossBarHud.bossBars`,
      cubre Wither/Ender Dragon gratis) O vida>100 O (ancho>1.4 Y alto>1.8 A LA VEZ — evita el falso
      positivo de un Enderman alto-pero-delgado). Warden excluido a propósito (ya tiene su propio
      sistema). Ping misterioso DANGER/WORLD que se apaga al estar cerca Y a la vista.
- [x] Golpes de jefe amplificados (Tier.CRITICAL, ×1.5 magnitud, ×1.4 duración) vía el nuevo hook de
      `DamageSource`.
- [x] Hook nuevo `HapticsController.onPlayerDamaged(DamageSource)` + mixin
      `ClientPlayNetworkHandler.onEntityDamage` (firma verificada con javap, mismo patrón que
      `onExplosion`) — el poll de HP por tick nunca pudo ver la fuente real del daño por sí solo.
      `PendingHit` de un solo uso (expira 250ms) puentea el paquete con la siguiente caída de HP.
- [x] Firmas de daño: veneno/wither/pociones dañinas (irregular, Tier.DANGER) y por atacante
      (zombie/spider/skeleton/enderman, firma propia cada uno) — fuego/explosión/caída/ahogo/
      congelación excluidos a propósito (ya tienen sus firmas dedicadas, sin tocar).
- [x] Teletransporte de Enderman cercano: detectado por salto de posición anómalo entre polls (sin
      evento público de Mojang para esto).
- [x] Splash (entrada al agua) + elytra glide (ambiente continuo suave) — cofre de tesoro intacto
      (ya se sentía bien, confirmado por el usuario). Descartado a propósito: puertas, cofres
      normales, fluidos, notas, block place (bajo valor / riesgo de saturar).
- [x] Todas las firmas verificadas con javap contra el jar mapeado 1.21.10 antes de escribir código.
- [x] `mod_version` → 0.20.0. Build + 24/24 tests → `dist/steampad-0.20.0.jar`.
- [x] Validación en hardware → B051: ✅ **MAYORMENTE CONFIRMADO** ("la gran mayoría funcionando",
      validación holística del usuario). Cerrado; ajustes puntuales se notificarán si surgen.

---

## FASE 35: v0.19.1 — Feedback de B050: glifos realmente en tiempo real + marcador mejorado (sesión 25) ❌ REPROBADO EN HARDWARE (sesión 25 cont. 3) → continúa en FASE 38

> El usuario probó B050 (v0.19.0): puntos 1/2 (merge de pad + filtro táctil) quedaron pendientes de
> repetir; punto 3 (glifos de zoom) funcionaba pero no cumplía "tiempo real" de verdad — pidió que los
> hints BASE de un botón repurposed desaparezcan, no solo que se sumen los nuevos encima, como norma de
> todo el mod (estilo consola/Controlify); punto 4 (marcador) funciona pero pidió más estilos de baliza
> + fix de que no detecta apuntando muy alto. Detalle en TODO_BLOCKERS.md B050.

- [x] `ZoomController.isButtonRepurposed(cfg, button)` — fuente única de verdad de qué botón físico
      está tomado por el zoom ahora mismo; el dispatcher (`zoomEatsDpad`) y `GameplayHudOverlay` la
      comparten — el HUD ahora OCULTA el hint base del botón repurposed en el mismo tick en que deja de
      funcionar, en vez de solo sumar el hint nuevo encima (el bug real de "no tiempo real").
- [x] `ControllerConfig.ZoomMarkerStyle` (COLUMN/SHORT_COLUMN/RING/BURST) + control cíclico "Estilo
      del marcador" en Avanzado → Zoom, mismo patrón que los temas de color del teclado/radial.
- [x] `ZoomController.placeMarker`: fallback al punto final de la línea de mira cuando el raycast da
      MISS (apuntando al cielo abierto) — ya no falla en silencio.
- [x] `mod_version` → 0.19.1. Build + 24/24 tests → `dist/steampad-0.19.1.jar`.
- [ ] Validación en hardware → B050: **REPROBADO** (2026-07-10, sesión 25 cont. 3). Glifos siguen sin
      tiempo real (además el usuario pide que sea la norma en todo el mod); marcador tiene un bug nuevo
      con bloques arriba de la línea de vista. Continúa en FASE 38 (pendiente de aclaración).

---

## FASE 34: v0.19.0 — Merge de backends + marcador de zoom (sesión 24 cont. 5) ✅ CÓDIGO / ⚠️ 1/2 pendientes de repetir, 3/4 → FASE 35

> Cámara de mouse CONFIRMADA ambiental (Moonlight) por el usuario. Su log reveló el bug real del pad
> de la Ally. Detalle en PROGRESS.md sesión 24 cont. 5, B050.

- [x] Merge SDL3+GLFW con dedupe por nombre (el pad de la Ally visible junto al 8BitDo).
- [x] Filtro i2c-HID (`NVTK0603:NN` = pantalla táctil) — ya no se lista ni auto-activa.
- [x] Glifos contextuales de zoom en tiempo real (DUP/DDOWN/A) solo durante el zoom.
- [x] Marcador de zoom: A → baliza de partículas 2–15s + toggle de compartir en chat; 3 ajustes.
- [x] `mod_version` → 0.19.0. Build + 24/24 → `dist/steampad-0.19.0.jar`.
- [ ] Validación en hardware → B050: puntos 1/2 pendientes de repetir; puntos 3/4 tuvieron feedback
      real (glifos no eran "tiempo real" de verdad, marcador necesitaba más estilos + fix) → corregidos
      en FASE 35 (v0.19.1).

---

## FASE 33: v0.18.0 — Hallazgo Moonlight + lote de 4 (sesión 24 cont. 4) ✅ CÓDIGO / ⚠️ SIN VALIDAR

> La captura del usuario reveló que juega vía Moonlight/Sunshine contra la Ally (Bazzite) — los
> "controles" fantasma y (probablemente) la cámara de mouse muerta son del stack de streaming.
> Detalle en `PROGRESS.md` sesión 24 cont. 4, D053, B049.

- [x] Filtro de dispositivos de inyección falsos (passthrough/extest/fake device) en
      `ControllerManager` — no se listan ni auto-activan; Steam Virtual Gamepad se conserva.
- [x] Cámara de mouse: re-diagnóstico ambiental (puntero absoluto de Sunshine) + 4 verificaciones
      de entorno para el usuario (B049); fixes internos previos conservados.
- [x] Teclado: 0.55× → nuevo 1.0× default (CRUISE 11, MAX 45 px/tick).
- [x] Vibración: comer/beber, crujido de cofre, quemarse; startup 90ms.
- [x] `mod_version` → 0.18.0. Build + 24/24 tests → `dist/steampad-0.18.0.jar`.
- [x] **v0.18.1 — gatillos en el aire:** flanco del gatillo = `setKeyPressed` + `onKeyPressed`
      (espejo exacto de `Mouse.onMouseButton`) — RT golpea sin objetivo, como el click. Revisión de
      gameplay del dispatcher: resto correcto.
- [x] **v0.18.1 — auto-golpe Bedrock (aprobado por el usuario):** `attackAutoRepeat` ON default,
      toggle en Básico → Movimiento; re-golpe al ritmo del cooldown, mira fuera de bloques, sin
      doble golpe en el flanco. Build + 24/24 → `dist/steampad-0.18.1.jar`.
- [ ] **Validación en hardware pendiente** — checklist B049.

---

## FASE 32: v0.17.0 — Lote de 4 tras feedback de v0.16.0, con análisis de Controlify (sesión 24 cont. 3) ✅ CÓDIGO / ⚠️ SIN VALIDAR EN HARDWARE

> v0.16.0: scroll+D-pad, reset de zoom, i18n y panel VALIDADOS. Reprobados: cámara de mouse (3ª
> iteración, ahora con el código real de Controlify como referencia), Apuntador no entendido, aim
> assist imperceptible. Nuevos: snap a botones de mods, defaults del teclado centrados. Detalle en
> `PROGRESS.md` sesión 24 cont. 3, D051–D052, B048.

- [x] Cámara de mouse: regla de Controlify (never fight the grab) en `setOsCursorHidden` + self-heal
      del modo GLFW real con warning de confirmación en el log — ver D051.
- [x] Teclado: Apuntador verificado correcto (explicación de uso en B048); sliders recentrados
      (velocidad 0.5–1.5×, altura 20–40% con default 30%); fix del clamp muerto de altura.
- [x] Snap/D-pad sobre botones de mods en inventarios (`SlotSnap` generalizado a widgets).
- [x] Aim assist v3: des-apilado del reduce-aim, fricción sqrt, compensación de caída de proyectil —
      ver D052.
- [x] `mod_version` → 0.17.0. Build + 24/24 tests → `dist/steampad-0.17.0.jar`.
- [ ] **Validación en hardware pendiente** — checklist en B048 (cámara de mouse = experimento
      concluyente vía log).

---

## FASE 31: v0.16.0 — Lote de 7 tras feedback de v0.15.0 (sesión 24 cont. 2) ✅ CÓDIGO / ⚠️ SIN VALIDAR EN HARDWARE

> Feedback sobre v0.15.0: "todo lo demás funciona bien, aún no detecto bugs" — mouse-atorado, chat,
> radial, cámara AAA y glifos VALIDADOS. Reprobados: cámara de mouse en entrada mixta, feel del
> teclado, aim assist imperceptible. Más 4 pedidos nuevos. Detalle en `PROGRESS.md` sesión 24 cont. 2,
> D049–D050, B047.

- [x] Cámara de mouse en gameplay: auto-candado por invariante (`!isCursorLocked() && focused →
      lockCursor()`) en `tickInGame` — ver D049 (fix de invariante, causa no reproducida; nota de
      honestidad y dato diagnóstico a pedir si persiste).
- [x] Teclado: velocidad −15% + opción "Modo del stick" (Velocidad | Apuntador estilo Steam Big
      Picture, mapeo absoluto, soltar conserva la tecla) — ver D050.
- [x] Aim assist reforzado: cono 3.5°/×2.6, fricción 0.35, magnetismo 12°/s desde 0.02, rango 28;
      jugadores confirmados como objetivos.
- [x] Foco tras scroll: D-pad arranca en la primera fila visible del viewport, no en el foco viejo.
- [x] Zoom: "Restablecer zoom al soltar" (`zoomResetOnRelease`, default OFF).
- [x] Auditoría i18n: LIMPIA — 393 claves ×3 idiomas idénticas, código cubierto, enums completos.
- [x] Panel de diagnóstico de Selección de control a 0.75×.
- [x] `mod_version` → 0.16.0. Build + 24/24 tests → `dist/steampad-0.16.0.jar`.
- [ ] **Validación en hardware pendiente** — checklist en B047 (prueba clave: cámara de mouse).

---

## FASE 30: v0.15.0 — Lote de 8 tras feedback de v0.14.0 (sesión 24 cont.) ✅ CÓDIGO / ⚠️ SIN VALIDAR EN HARDWARE

> Feedback del usuario sobre v0.14.0: vibración (B043) ✅ y crash (B044) ✅ validados; mouse-atorado
> NO resuelto (causa raíz real encontrada ahora, verificada en bytecode) y stick del teclado aún
> rápido. Además pidió: chat sobre el teclado, editor radial claro + tamaños, cámara AAA, aim assist,
> glifos en vivo y entrada mixta. Detalle en `PROGRESS.md` sesión 24 cont., D046–D048, B046.

- [x] Causa raíz real del mouse-atorado: guard `currentScreen != null` en `PauseGate.shouldSuppress()`
      (el focus-pause espurio SOLO puede venir de gameplay — verificado con javap en `openGameMenu`).
- [x] Stick del teclado v3: doble zona (85% precisión / turbo al tope) + freno por desaceleración.
- [x] Chat empujado sobre el teclado: `ChatScreenMixin` + `ChatHudMixin` (traslado por matrices;
      sugerencias de comandos ancladas a height-12 hardcoded, verificado en bytecode).
- [x] Editor radial: filas Rueda/Espacios separadas y etiquetadas; pantalla "Apariencia" nueva
      (radio, tamaño de espacios `chipRadius`, fondo, tema al final) con previo en vivo.
- [x] Cámara AAA: curva de potencia sobre magnitud (`lookCurve` 2.2) + velocidades yaw/pitch
      separadas + aceleración de giro al tope (`lookTurnBoost`).
- [x] Aim assist de proyectiles (`AimAssistController`): fricción + magnetismo suave, solo cargando
      arco/ballesta/tridente, con línea de visión; toggle + fuerza en Básico.
- [x] Glifos de gameplay en vivo: RADIAL/CHAT/ZOOM añadidos, solo si tienen botón asignado.
- [x] Entrada mixta: `KeyboardInputMixin` merge (no overwrite) + `holdOnChange` por flanco en
      attack/use/playerList — teclado y mouse funcionan con el pad conectado.
- [x] `mod_version` → 0.15.0. Build + 24/24 tests → `dist/steampad-0.15.0.jar`.
- [ ] **Validación en hardware pendiente** — checklist completo en B046.

---

## FASE 29: v0.14.0 — Lote de 4 fixes/features post-v0.13.2 (sesión 24) ✅ CÓDIGO / ⚠️ SIN VALIDAR EN HARDWARE

> El usuario probó v0.13.2 y pidió 4 cosas en el mismo mensaje: stick del teclado más controlable +
> slider de velocidad, previo de color del teclado, un fix similar a D037 pero para Ajustes del
> gamepad, y los mismos temas de color del teclado también en el radial. Detalle completo en
> `PROGRESS.md` sesión 24, `DECISIONS.md` D044/D045.

- [x] **Stick del teclado más controlable:** `FLOAT_MAX_SPEED` 95→62 px/tick, `FLOAT_CURVE` 2.4→2.7,
      `PULL_SETTLE` 0.5→0.7, `SETTLE_MAG` 0.12→0.16 (snap más agresivo). Nuevo
      `GlobalConfig.virtualKeyboardStickSpeed` (0.5×–2.0×, default 1.0×) + slider "Velocidad del stick"
      en `KeyboardSettingsScreen`.
- [x] **Previo de color del teclado:** `VirtualKeyboardRenderer.renderThemePreview(...)` — franja de 3
      teclas de muestra con la paleta real del tema, dibujada en vivo bajo el selector de tema en
      Ajustes de teclado.
- [x] **Fix mouse-atorado también en Ajustes del gamepad:** invariante de auto-sanación en
      `GamepadInputDispatcher.tick()` — si `captureMode==true` y la pantalla activa no es
      `BindingsScreen`, se fuerza `false`. Ver D045 para el razonamiento completo y la nota de
      honestidad sobre validación (no reproducido interactivamente, fix estructuralmente correcto).
- [x] **Temas de color en el menú radial:** `PixelTheme` extraído a `config/PixelTheme.java` (compartido
      con el teclado, ver D044). `RadialConfig.theme` nuevo campo. `RadialRenderer` gana
      `record Palette` + `palette(PixelTheme)` aplicado a chips/backdrop/anillo de selección/texto de
      pistas (gelatina y ruedas fantasma quedan neutrales a propósito). `RadialEditorScreen` gana un
      control cíclico "Tema"; el previo ya existía (la rueda en vivo del editor se re-renderiza con el
      tema al instante).
- [x] `mod_version` → 0.14.0. Build 0.14.0 + 24/24 tests → `dist/steampad-0.14.0.jar`.
- [ ] **Validación en hardware pendiente** — ninguno de los 4 puntos se ha probado en el Deck todavía.
      Ver TODO_BLOCKERS.md para el checklist nuevo de esta sesión (se suma a B043/B044, aún abiertos).

---

## FASE 28: v0.13.2 — TitleScreenMixin eliminado del todo (corrección de un error propio, sesión 23 cont.) ✅ CORREGIDO

> El usuario aclaró que "quitar la A y la B del menú principal" se refería a los glifos X/B del
> título que YO reactivé por error en la sesión 20 (auditoría), creyendo que un mixin sin registrar
> era un bug — en realidad había sido removido a propósito en una sesión anterior. Detalle en
> `PROGRESS.md` sesión 23 cont. y STATE.md.

- [x] `TitleScreenMixin.java` eliminado por completo (no solo removido de `steampad.mixins.json` —
      así no puede volver a "descubrirse" como dormido en una futura auditoría).
- [x] Verificado con grep que no tenía otras dependencias en el código antes de borrarlo.
- [x] Build 0.13.2 + 24/24 tests → `dist/steampad-0.13.2.jar`, confirmado que la clase ya no está en
      el jar.
- [x] Lección documentada (DECISIONS.md D043): un mixin no registrado no es automáticamente un bug.

---

## FASE 27: v0.13.1 — Fix de crash IncompatibleClassChangeError (sesión 23) ✅ CAUSA RAÍZ CORREGIDA / ⚠️ fix sin re-confirmar en hardware

> Primera validación real en hardware de todo lo hecho en la sesión 22: el usuario probó v0.13.0 en
> su Deck y crasheó al entrar a Ajustes. Detalle en `PROGRESS.md` sesión 23, D042.

- [x] Diagnóstico: `OptionsScreenMixin$GamepadButton` (única clase anidada dentro de un `@Mixin` en
      todo el proyecto) tenía su atributo `InnerClasses` mal reescrito por Mixin — invisible hasta que
      `VirtualKeyboard.isTextWidget()` (código nuevo de la sesión 22) llamó `getClass().getSimpleName()`.
- [x] Fix de raíz: `GamepadButton` extraído a clase de nivel superior `client/ui/GamepadOptionsButton`,
      fuera de cualquier `@Mixin`.
- [x] Fix defensivo: `isTextWidget()` ahora atrapa `Throwable` (no solo `Exception` —
      `IncompatibleClassChangeError` es un `Error`) — protege contra cualquier otro mod del modpack
      (80 mods instalados) con un problema similar en sus propias clases generadas por mixins.
- [x] Icono del gamepad en Ajustes: quitados los 2 acentos de color (azul/blanco) de los botones de
      cara, ahora monocromo — resultó ser un cambio válido mantener, aunque el usuario en realidad se
      refería al TitleScreenMixin (ver Fase 28) para el pedido original de "quita la A y la B".
- [x] Build 0.13.1 + 24/24 tests → `dist/steampad-0.13.1.jar`.
- [ ] Re-probar en hardware: entrar a Ajustes de Minecraft repetidamente sin crash; confirmar que el
      menú principal ya no muestra los glifos X/B (Fase 28).

---

## FASE 26: v0.13.0 — Vibración Tier 2: scheduler de prioridad + 8 eventos + tesoro filtrado (sesión 22 cont.) ✅ código+tests / ⚠️ SIN VALIDAR

> Diseño discutido a fondo con el usuario (RDR2, God of War Ragnarök, Cyberpunk 2077, Silent Hill 2,
> Forza) antes de programar — decisión explícita de dejar fuera textura de superficie al caminar y
> tesoro enterrado genérico. Detalle en `PROGRESS.md` sesión 22 cont., D040. Validación: **B043**.

- [x] `HapticsController` reescrito con árbitro de prioridad de 5 niveles (Tier) — un solo canal de
      rumble, necesidad técnica real de arbitraje, no decoración.
- [x] Portal del Nether (ping acelerado por cercanía), creeper cargando (pulsos crecientes), Warden
      cerca (rumble opresivo), geoda de amatista (ping de descubrimiento único).
- [x] Hambre crítica, ahogo (refuerza la UI de burbujas), congelación (temblor irregular) — extienden
      el heartbeat de Tier 1 con timings distintos.
- [x] Caída + aterrizaje independiente del daño real (antes solo vibraba si hubo daño).
- [x] Minería por valor: mineral=sólido, diamante/esmeralda/ancient debris=pulso limpio en tier IMPACT.
- [x] Cofre de tesoro con filtro de 3 señales: cerca de spawner (dungeon real) + no cerca del punto de
      spawn/cama del jugador (`ClientWorld.getSpawnPoint()`, confirmado que es el respawn REAL, no el
      del mundo) + no abierto antes (`UseBlockCallback`, set en memoria).
- [x] Todas las firmas verificadas con javap antes de escribir — compiló a la primera.
- [x] Build 0.13.0 + 24/24 tests → `dist/steampad-0.13.0.jar`.
- [ ] Validar en hardware (B043, checklist actualizado con los 8 eventos nuevos).

---

## FASE 25: v0.12.0 — Vibración AAA event-driven, Tier 1 (sesión 22) ✅ código+tests / ⚠️ SIN VALIDAR

> Investigación: Bedrock NO tiene vibración nativa (nunca implementada por Mojang); diseño propio
> informado por Controlify + wishlist de la comunidad + principios AAA (Returnal, God of War Ragnarök).
> Detalle en `PROGRESS.md` sesión 22, D039. Validación: **B043** (incluye la propuesta Tier 2).

- [x] Fix de paso: `TitleScreenMixin` estaba escrito pero nunca registrado en `steampad.mixins.json`
      (mixin dormido desde que se creó) — activado tras verificar firma con javap.
- [x] `ControllerManager.rumble` — overload asimétrico low/high-freq (textura boom/buzz real del hw).
- [x] `haptics/HapticsController` (nuevo): cablea las 6 categorías de vibración de `ControllerConfig`
      (existían en Ajustes sin efecto) a eventos reales — daño, heartbeat vida baja, muerte, golpe
      cuerpo a cuerpo (heurística de crítico), romper bloque, explosión cercana, rayo cercano.
- [x] 2 mixins nuevos (`ClientPlayNetworkHandlerMixin`, `ClientPlayerInteractionManagerMixin`) +
      1 evento Fabric API (`ClientPlayerBlockBreakEvents.AFTER`) — firmas verificadas con javap antes
      de escribir código.
- [x] Build 0.12.0 + 24/24 tests → `dist/steampad-0.12.0.jar`.
- [ ] Validar Tier 1 en hardware (B043).
- [ ] Tier 2 (momentos AAA-inmersivos: portal del Nether con vibración creciente, End, Warden/sculk,
      tormenta, elytra, vehículo…) — propuesta pendiente de que el usuario elija cuáles agregar.

---

## FASE 24: v0.11.0 — Lote de mejoras + feature ZOOM (sesión 21) ✅ código+tests / ⚠️ SIN VALIDAR

> 7 secciones pedidas por el usuario (B040 sigue en pausa a propósito). Detalle por sección en
> `PROGRESS.md` sesión 21; decisiones en D037 (fix del click muerto) y D038 (diseño del zoom).
> Validación en hardware: **B042**.

- [x] S1 — Stick izq del teclado: curva `mag^2.4`, máx 95 px/tick solo a fondo, imán 8%/50% (el 45%
      previo a baja deflexión atrapaba el punto — esa era la falta de respuesta).
- [x] S2 — Teclado pixel-art vanilla MC + 8 presets de color (`KeyboardTheme`) en Ajustes ×3 idiomas.
- [x] S3 — Detección universal de campos de texto (foco vanilla + duck-typing + barrido recursivo por
      `isFocused()`; entrega con fallback directo al widget) — cubre el caso Xaero's.
- [x] S4 — Glifos LB/RB por marca en las pestañas de los 3 menús de ajustes.
- [x] S5 — Fix click muerto: `hasActivity()` con umbral real para gatillos + `markMouseForce()`
      (barrido >20px / click físico ganan siempre) + mixin `Mouse.onMouseButton` con guard INJECTING.
- [x] S6 — Carrusel radial: siluetas de rueda anterior/siguiente con nº real de chips + glifos LB/RB.
- [x] S7 — ZOOM (BetterZoom-style): `ZoomController` + mixin `getFov` (verificado javap) + bind ZOOM
      sin default + cruceta ajusta nivel + cámara ralentizada + sección Avanzado (9 opciones) + i18n ×3.
- [x] S8 — Build 0.11.0 + 24/24 tests → `dist/steampad-0.11.0.jar` + docs actualizados.
- [ ] Validar TODO en hardware (B042).

---

## FASE 23: Auditoría de código + limpieza + bugfixes (sesión 20) ✅ código+tests / N/A runtime (sin cambios de comportamiento visible)

> A petición del usuario: pausa deliberada de B040 (Steam Input nativo en Game Mode, usuario fuera de
> casa) para auditar el código en general — orden, limpieza, bugs — sin romper nada. Detalle completo
> en STATE.md sesión 20. Ningún cambio aquí toca la investigación de B040.

- [x] Auditoría de `radial/` + `mixin/` + entry points vía subagente (completó antes de que el resto
      del fan-out tocara el límite de sesión de Claude; el resto se auditó a mano).
- [x] Fix: `RadialMenuController.openSubmenu()` no reabría la rueda (guard `open` bloqueaba el reopen
      en 2 de 3 caminos de ejecución de slot).
- [x] Fix: `RadialRenderer` podía estilizarse con el config de un mando distinto al que realmente
      muestra (handle ahora pasado explícito por cada llamador, no leído de forma implícita).
- [x] Fix: tipo de slot radial no reconocido ahora loguea (antes fallaba en silencio).
- [x] Fix: `SteamPadClient.ensureFallbackBackendsInit()` podía quedar bloqueado PERMANENTEMENTE si el
      primer intento fallaba (sin GamepadMappings/SDL3/ControllerClaimService/restore por el resto de
      la sesión) — ahora reintenta ~10s antes de rendirse (mismo patrón que el retry de ActionSets).
- [x] Fix de robustez: `JsonUtil.saveToFile()` ahora escribe atómico (temp + move) — antes un
      crash/corte de luz a mitad de escritura dejaba el JSON corrupto (ya pasó una vez con Loom, B016).
- [x] Limpieza: imports sin uso (`ItemIconProvider`, `EffectIconProvider`, `ActionExecutor`) y
      accessors estáticos sin ningún call site (`RadialMenuController.getSlotCount/hasMultipleWheels`)
      eliminados, verificados con grep de todo el repo antes de borrar.
- [x] Auditoría de `input/`, `steam/config/service/platform/compat/`, `screen/client-ui` (a mano,
      archivos de mayor riesgo/historial de bugs) — sin bugs adicionales confirmados más allá de los
      de arriba.
- [x] Hallazgo documentado, NO corregido (riesgo de corromper bindings del usuario sin poder validar
      en hardware): config por-mando clave por handle sintético inestable entre sesiones. Ver B041.
- [x] Build + 24/24 tests → `dist/steampad-0.10.6.jar`. Sin cambios de gameplay/UI — nada que validar
      en hardware para esta sesión específicamente.

---

## FASE 22: Steam Input NATIVO en Game Mode — investigación en curso (sesión 19 cont. 6) 🔍 SIN CÓDIGO

> El usuario rechaza F13-F22 como solución final: quiere las 10 ranuras nombradas nativamente en el
> menú de Steam, como cualquier juego AAA compatible con Steam Input. Detalle en `PROGRESS.md` sesión
> 19 cont. 6, `TODO_BLOCKERS.md` B040. NO empezar a programar sin el diagnóstico del usuario.

- [x] Aclarar por qué se usa AppID 480 (mecanismo oficial de Valve, igual para todos los usuarios).
- [x] Hipótesis formulada: choque de "un solo juego activo" en Game Mode (Steam ya trackea el acceso directo del usuario con su propio AppID; Minecraft reclama 480 por separado y choca).
- [x] Plan de fix propuesto (detectar AppID real vía env vars, usarlo en gamescope, mod auto-escribe su VDF con el nombre correcto — sin Taller de Steam).
- [ ] **BLOQUEADO:** diagnóstico pendiente del usuario (comando de env vars con Minecraft vivo en Game Mode, vía SSH o Decky Terminal).
- [ ] Implementar el fix SOLO tras confirmar la hipótesis con el diagnóstico.

## FASE 21: v0.10.5 — Ranuras por tecla F13–F22 + icono del mod (sesión 19 cont. 5) ✅ código+tests / ⚠️ SIN VALIDAR

> B038 validado: escritorio ✅ perfecto; en Game Mode el attach falla benigno (acceso directo no-Steam
> ocupa el slot de juego) y todo funciona por SDL3 — los paddles necesitaban otra vía. Detalle en
> `PROGRESS.md` sesión 19 cont. 5 y D034. Validación: **B039**.

- [x] `SteamSlotDispatcher`: fuente dual por ranura — acción Steam Input O tecla F(12+N) (F13–F22) vía `glfwGetKey`.
- [x] `steamAttachMode` default → NEVER (attach solo para MC lanzado desde Steam como título real).
- [x] UI: etiquetas "Ranura N (FX)" + descripciones/avisos reescritos ×3 idiomas.
- [x] Icono del mod: pixel-art ABXY 128px generado (el declarado en fabric.mod.json nunca existió); pendiente reemplazar por el arte original del usuario.
- [x] Build 0.10.5 + 24/24 tests → `dist/steampad-0.10.5.jar` (icono verificado dentro del jar).
- [ ] Validar en Game Mode: paddle→F13 en la disposición de Steam → Ranura 1 dispara su keybind (B039).
- [ ] Confirmar icono en ModMenu y sin regresión en escritorio (B039).

## FASE 20: v0.10.4 — Política de conexión a Steam por entorno (`steamAttachMode`) (sesión 19 cont. 4) ✅ escritorio validado / Game Mode: attach falla benigno

> Causa raíz real del "nada responde": conectar a Steam con AppID 480 hace que Steam tome los
> mandos (aplica la disposición de Spacewar) — el 8BitDo desaparece de SDL3 y el resto queda mudo.
> Detalle en `PROGRESS.md` sesión 19 cont. 4 y D033. Validación: **B038**.

- [x] Diagnóstico por log: SteamAPI.init() → Steam secuestra mandos; "stick derecho funciona" = emulación de ratón de Steam, no el mod.
- [x] `GlobalConfig.steamAttachMode` (AUTO/ALWAYS/NEVER; AUTO = solo gamescope/Game Mode).
- [x] Gate en `SteamBootstrap.init()` + `isAttachSkippedByPolicy()`.
- [x] UI honesta: "not attached (desktop: raw input)" en verde en el selector; aviso `slot_desktop` en el panel de ranuras (×3 idiomas).
- [x] Build 0.10.4 + 24/24 tests → `dist/steampad-0.10.4.jar`.
- [ ] Validar en escritorio: 8BitDo de vuelta con paddles crudos P1..P4 asignables en BOTONES (B038).
- [ ] Validar en Game Mode (script nativo): conexión + ranuras + riesgo abierto del gamepad virtual (B038).

## FASE 19: v0.10.2/0.10.3 — Retry de ActionSets + reversión arquitectónica SDL3-principal (sesión 19 cont. 3) ✅ código+tests / ⚠️ SIN VALIDAR

> B034 validado reveló dos problemas nuevos en cadena: ActionSets tardaban en aparecer (B036,
> resuelto con retry) y luego, al volverse válidos, Steam Input tomaba el control total del
> gameplay dejando el juego mudo (B037) — se revierte la Restricción 1 de CLAUDE.md con
> aprobación explícita del usuario. Detalle en `PROGRESS.md` sesión 19 cont. 3 y D032.

- [x] `SteamBootstrap.retryActionSetRegistrationIfNeeded()`: reintenta el registro de ActionSets cada ~1s durante ~10s si vienen inválidos pese al VDF presente.
- [x] Confirmado en hardware que el retry funciona (`Action Sets: loaded` tras algunos reintentos).
- [x] Diagnóstico del mudo total: Steam Input promovido a "activo" solo reenvía acciones mapeadas explícitamente en Steam; con solo los paddles mapeados, el resto del juego no respondía.
- [x] Pregunta directa al usuario antes de tocar código (reversa una Restricción Inamovible) — aprobado: SDL3 siempre principal, Steam Input solo en paralelo para ranuras.
- [x] `ControllerManager.refreshCache()` invertido: SDL3 → GLFW → Steam Input (último recurso).
- [x] `SteamSlotDispatcher` sin cambios (ya independiente de `ControllerManager`, D030).
- [x] `CLAUDE.md` Restricción 1 actualizada (tachada + nota).
- [x] Build 0.10.2 y 0.10.3 + 24/24 tests → `dist/steampad-0.10.3.jar`.
- [ ] Validar en el hardware del usuario que el gameplay normal + BOTONES + paddles funcionan todos juntos (B037).

## FASE 18: v0.10.1 — Fix falso negativo de detección de Steam (sesión 19 cont.) ✅ código+tests / ⚠️ SIN VALIDAR

> Encontrado al validar B033: Steam Input no conectaba ni en escritorio ni en Game Mode (ambos
> lanzaban por Flatpak). Migrado a Prism nativo; persistía el fallo por un chequeo nativo viejo
> poco fiable. Detalle en `PROGRESS.md` sesión 19 cont. y D031. Validación: **B034**.

- [x] Diagnóstico: Flatpak bloqueaba `~/.steam/` (afecta también al script de Game Mode del usuario, que lanzaba por Flatpak — B032 nunca se probó con Steam Input realmente activo).
- [x] Migración documentada: Prism Launcher nativo (AppImage), instancias sincronizadas, Flatpak desinstalado.
- [x] Hallazgo lateral: en nativo, SDL3/HIDAPI ya expone los paddles crudos (P1-P4=true) sin Steam Input.
- [x] Causa raíz real: `SteamAPI.isSteamRunning()` (nativo Steamworks4j 1.9.0) da falso negativo contra clientes de Steam modernos.
- [x] Fix: `SteamBootstrap.isSteamProcessAlive()` — escaneo cross-platform con `ProcessHandle`, sin nativo, aplicado en `init()` y en el chequeo periódico de `runCallbacks()`.
- [x] Build 0.10.1 + 24/24 tests → `dist/steampad-0.10.1.jar`.
- [ ] Validar en el hardware del usuario que ahora sí conecta (B034).

---

## FASE 17: v0.10.0 — Steam Input Slots: paddles vía VDF (sesión 19) ✅ código+tests / ⚠️ SIN PROBAR

> Respuesta a B032 (paddles inaccesibles en Game Mode). Detalle en `PROGRESS.md` sesión 19 y D030.
> Validación hardware + pasos de re-importación del VDF: **B033**.

- [x] VDF: 10 acciones `steampad_slot_1..10` en `SteamPad_InGame` + localización en/es.
- [x] `SteamActionRegistry.actionSlots[10]` + `ControllerState.DigitalAction.SLOT_1..10` (al final, en sync).
- [x] `GlobalConfig.steamInputSlots` (global a propósito, D030) + round-trip en tests.
- [x] `SteamSlotDispatcher`: HOLD vía KeyTap, edge-detect, solo gameplay, fuente híbrida (Steam aunque el activo sea SDL3), releaseAll defensivo.
- [x] Cableado en `InputBindingManager.tick()` (ambos caminos).
- [x] UI: sección "Steam Input" en BOTONES (10 ranuras → KeybindPickerScreen, reset, undo, aviso si Steam Input inactivo) + i18n ×3.
- [x] Build 0.10.0 + 24/24 tests → `dist/steampad-0.10.0.jar`.
- [ ] Re-importar el VDF en Steam y validar TODO el flujo en hardware (B033).

## FASE 16: v0.9.0 — Respuesta al reporte de hardware de v0.8.0 (sesión 18) ✅ código+tests / ⚠️ runtime

> Detalle en `PROGRESS.md` sesión 18. Validación hardware: B031.

- [x] CRASH al guardar keybind en la rueda (NPE screen null tras click que cierra pantalla).
- [x] Overlay de acciones menú→gameplay: supresión de botones held al cerrar pantallas/radial.
- [x] Teclado v2: brackets estilo inventario (sin punto) + snap magnético fuerte.
- [x] Vibración: resultado de rumble verificado + log de causa (sospecha sandbox Flatpak).
- [x] Paddles/M1: hints HIDAPI(+8BitDo) + diagnóstico HasButton/HasRumble/versión SDL.
- [x] Radial: ruedas 1–6 con añadir/eliminar, glifos en overlay, gelatina pixel-art (BG3).
- [x] Rate-limit del log de supresión de pausa.
- [ ] Validar en hardware (B031).

## FASE 15: v0.8.0 — Teclado Controlify-style + auditoría + bloques B/D/E/F (sesión 17) ✅ código+tests / ⚠️ runtime

> Detalle completo en `PROGRESS.md` sesión 17. Validación hardware: B030.

- [x] Teclado: apertura estilo Controlify (A abre; auto solo en chat/carteles/libros) + stick libre flotante con snap (`KeyboardGeometry`).
- [x] F13 — Extra binds/chords con semántica HOLD (`KeyTap.hold/release`) → keybinds de mods `isPressed()` funcionan.
- [x] F6 — Vibración: botón de prueba enruta por `ControllerManager.rumble`; `allowVibration` respetado.
- [x] D14 — `swallowGuiTick`: un press = una acción al terminar captura.
- [x] D17 — D-pad navega dentro de `EntryListWidget` (mundos/servidores); A = Enter sintético.
- [x] B9 — HUD contextual con radial abierto. B16 — Radial en grises MC.
- [x] E10 — Segunda rueda (carrusel LB/RB, selector en editor, i18n ×3). E11 — Blob gelatina.
- [x] Auditoría de TODAS las opciones: gyro completo cableado (estaba inerte), `buttonActivationThreshold`, `screenRepeatNavigationDelay` (hold-repeat), `reduceAimingSensitivity`, `autoJump`→vanilla, `showScreenButtonGuide`, `virtualKeyboardAutoShow`; 3 campos legacy eliminados; pendientes-con-causa documentados.
- [x] Tests: 24/24 PASSED (test obsoleto del radial corregido).
- [ ] Validar en hardware (B030).

## FASE 14: PauseGate — menú de pausa espurio al perder foco (sesión 17) ✅ VALIDADO EN HARDWARE

- [x] v0.7.3: `PauseGate` + bypass de foco en `lockCursor` + resync de prev-state. Usuario: "funcionó perfecto".
- [x] Análisis corregido: origen = `GameRenderer.render` + `pauseOnLostFocus` vanilla (500 ms).
**Estado global:** Build contra MC **1.21.10** funcional. Crash de render RESUELTO (confirmado por usuario). Detección + control de mando vía multi-backend (Steam→SDL3→GLFW) + 8BitDo + UI renovada completa. Pruebas en hardware real: PENDIENTES.

> **Nota:** Las fases 0–6 abajo reflejan el estado original (target 1.21.4). Las fases 7–8 (sesiones 4–5) reflejan la migración a 1.21.10 y el trabajo nuevo. `ControllerSettingsScreen` fue eliminado (bug de bucle) — ya no existe.

---

## FASE 13: Texturas de botones por marca (sesión 13 — Fix 29) ✅ código / ⚠️ runtime

> Detalle por check en `PROGRESS.md` (B1–B6).

- [x] B1 — Assets PNG 64×64 por marca copiados a `resources` (210 PNG en el jar).
- [x] B2 — `ButtonTextureManager` (marca activa→id→textura, fallback genérico→vector, caché).
- [x] B3 — `ButtonIcon` (ajustes) usa texturas con fallback vector.
- [x] B4 — `ControllerGlyphs` (HUD gameplay/inventario) usa texturas.
- [x] B5 — `ControllerBrandIcon` (selector) usa `controller.png` (silueta de marca).
- [x] B6 — Build + versión 0.6.0 + docs.
- [ ] Validar en hardware (B028): cada control muestra su set en todas las interfaces.

---

## FASE 12: Auditoría + UI en columnas + sliders finos + optimización (sesión 12 — Fix 28) ✅ código / ⚠️ runtime

> Detalle por check en `PROGRESS.md` (A1–A9).

- [x] A8 — Widgets `SteamToggle` (interruptor visual) + `SteamSlider` (ajuste fino, scroll-safe).
- [x] A2 — `ColumnSettingsScreen`: lista + panel de descripción. Refactor de las 4 pantallas de ajustes.
- [x] A4 — Scroll/stick-scroll ya no cambian valores de opciones.
- [x] A3 — Ajuste fino de sliders con stick derecho (proporcional).
- [x] A7 — Badge en selector: versión, ElDon, bandera MX, bandera Yucatán, corazón.
- [x] A1 — Auditoría + correcciones (scroll/sliders, polls, código muerto teclado).
- [x] A6 — Descripciones por opción + etiquetas de valores enum (×3 idiomas).
- [x] A5 — Optimización (caché de backends, etc.).
- [x] A9 — Build + versión 0.5.0 + docs.
- [ ] Validar en hardware (B027).

---

## FASE 11: Teclado virtual AAA (sesión 11 — Fix 27) ✅ código / ⚠️ runtime

> Detalle por check en `PROGRESS.md` (K1–K6).

- [x] K1 — Config (`GlobalConfig`) + sección "Teclado" en Ajustes globales (`KeyboardSettingsScreen`).
- [x] K2 — `KeyboardLayout` (capas QWERTY/símbolos) + núcleo `VirtualKeyboard` (emisión vía charTyped/keyPressed).
- [x] K3 — Detección de campo de texto enfocado (TextFieldWidget/EditBoxWidget + signs/books) → auto-mostrar.
- [x] K4 — Navegación con mando en el dispatcher (cruceta/stick snap/atajos A·Y·X·RT·LT·LB·RB·Back·B).
- [x] K5 — Render retro-MC (~1/5 inferior) + franja de previsualización (ver lo que se escribe).
- [x] K6 — Build + lang ×3 + versión 0.4.0 + docs.
- [ ] Validar en hardware (B026). Add-on opcional: levantar físicamente el cuadro de chat nativo.

---

## FASE 10: Lote de bugs de hardware (sesión 10 — Fix 26) ✅ código / ⚠️ runtime

> Detalle por check en `PROGRESS.md`. Metodología: trabajar por secciones con checklist (ver memoria work-methodology).

- [x] S1 — Ratón virtual AUTO cede al ratón físico y se re-activa con el stick.
- [x] S2 — Radial ejecuta todos los tipos (KEYBIND/SCREEN/SUBMENU) vía `KeyTap`.
- [x] S3 — Editor radial: "Tipo" muestra solo el nombre + descripción por tipo.
- [x] S4 — BOTONES: zona de mods agrupada por mod, "Others"→"Otros", con chord.
- [x] S5 — Todas las acciones (incl. mods) con opción de chord.
- [x] S6 — Captura de chord de 2 botones (modificador + gatillo).
- [x] S7 — Chord anula la acción base del botón gatillo en gameplay.
- [x] S8 — Mando predeterminado: auto-selección al iniciar y en hotplug (por nombre).
- [x] S9a — `steam_appid.txt` auto-creado → Steam Input puede inicializar.
- [x] S9b — Marca genérica mejorada; resto, marcas estilizadas originales.
- [x] S9c — Botones extra 8BitDo (paddles/misc) leídos por SDL3 y asignables.
- [ ] Re-validar las 9 secciones en hardware (B025).

---

## FASE 9: Lote de bugs de hardware (sesión 9 — Fix 25) ✅ código / ⚠️ runtime

- [x] **Bug 1 — Radial ejecuta:** selección sticky (`updateAnalog` ya no resetea a -1 bajo umbral) + **A activa** el slot resaltado (`activateSelected`/`dismiss`). Arregla ON_CLICK y ON_RELEASE.
- [x] **Bug 2 — Sensibilidad ratón virtual ×0.4:** `BASE_SPEED_PER_SEC` 850→340; default 1.0 intacto.
- [x] **Bug 3 — Vibración de arranque:** rumble único al activar el primer mando (restaurado de config o auto-seleccionado) en `SteamPadClient`.
- [x] **Bug 4 — Fabricante en la tarjeta:** `ControllerBrandIcon.manufacturer()` (8BitDo/Sony/Microsoft/Nintendo/Valve) en vez de `type.name()`.
- [x] **Bug 5 — Diagnóstico Steam fiel:** línea "Steam API" amarilla con fallback activo; ayuda nombra la fuente real (SDL3/GLFW).
- [x] Extra: "Version: null" arreglado (lee de FabricLoader); versión del mod **0.2.0**.
- [ ] Re-validar los 5 en hardware (B024).

---

## FASE 7: Migración a 1.21.10 + arreglo de crashes ✅ (sesión 4–5)

- [x] Migrar build a MC 1.21.10 (gradle.properties: yarn 1.21.10+build.3, Fabric API 0.138.4, Loom 1.13.6, Cloth 20, ModMenu 16)
- [x] Toolchain: Gradle **8.14** + JDK 21 (ver B011)
- [x] **Fix 12** — causa raíz del NoSuchMethodError: build target ≠ runtime (drawText int→void). Resuelto recompilando contra 1.21.10. Verificado a nivel de bytecode.
- [x] **Fix B016** — cache Loom corrupto por apagón recuperado.
- [x] **Fix B008** — access widener para `Mouse.onCursorPos` (privado en 1.21.10).
- [x] Crash al abrir SteamPad en Ajustes: RESUELTO y **confirmado por el usuario**.

---

## FASE 8: Multi-backend + 8BitDo + UI renovada ✅ (sesión 5) / ⚠️ runtime

### Backends de mando (Steam→SDL3→GLFW)
- [x] `GamepadSnapshot` (estado normalizado) + `GamepadInputDispatcher` (despacho agnóstico)
- [x] **GLFW**: `GlfwControllerProvider` (detección) + `GlfwSnapshotSource` (con **fallback a joystick crudo**)
- [x] **SDL3** vía JNA: `Sdl3Native` + `Sdl3GamepadProvider` (degrada a GLFW si falta libSDL3)
- [x] `ControllerManager` (fachada en cascada) + flags `useSdl3Fallback`/`useGlfwFallback`
- [x] **8BitDo**: `GamepadMappings` + `gamecontrollerdb.txt` empaquetado + override de usuario
- [x] Auto-activación al conectar; limpia handle al desconectar; libera teclas si el mando desaparece
- [x] Input de gameplay (movimiento/cámara/minar/usar/salto/sneak/sprint/hotbar/inventario/etc.)
- [ ] **[NO VERIFICADO]** Mando 8BitDo (Ultimate 2/Pro 3/SN30) detectado y controlando el juego — requiere hardware

### UI renovada (B018)
- [x] Tema fresco compartido (`SteamPadBaseScreen`: chrome, secciones, paleta, scroll reutilizable)
- [x] Todas las pantallas migradas (Global, Basic, Advanced, Select, Bindings, Calibration, Radial)
- [x] Descripción (tooltip) por opción + secciones con encabezado
- [x] Navegación por foco estilo Bedrock (`GuiFocusNavigator`)
- [x] Bug de bucle de Back en ajustes de control CORREGIDO (eliminado `ControllerSettingsScreen`)
- [x] i18n: `en_us` + `es_mx` + `es_es` (~120 claves con descripciones)
- [ ] **[NO VERIFICADO]** Navegación completa con mando en hardware

### Detalles Bedrock/AAA (B020)
- [x] Rumble al conectar (`ControllerManager.rumble`; SDL3/Steam, GLFW no-op)
- [x] Select alterna cursor/foco en cualquier pantalla (`GamepadInputDispatcher`)
- [x] Snap suave a casillas de inventario (`SlotSnap` + `HandledScreenAccessor`)
- [x] HUD de botones en gameplay estilo Bedrock (`GameplayHudOverlay`, esquinas inferiores)
- [x] i18n HUD (`steampad.hud.*`)
- [ ] **[NO VERIFICADO]** Sensación de rumble/snap/HUD en hardware

### Glyphs, logos, radial (B021)
- [x] Primitivas de dibujo sin assets (`client/ui/Draw`)
- [x] Glyphs de botón por tipo de mando (`ControllerGlyphs`) — Xbox/PS/Switch
- [x] Logos de marca en selección (`ControllerBrandIcon`) — Deck/8BitDo/Xbox/PS/Switch/Steam
- [x] **BUG FIX**: radial inalcanzable desde fallback → cableado a R3 (`GamepadInputDispatcher`)
- [x] Radial rediseñado limpio (chips, sin bloques) (`RadialRenderer`)
- [x] Editor radial funcional (tipo/disparo/acción/etiqueta/icono por slot) (`RadialEditorScreen`)
- [x] Repaso Steam Input: compat. con todos los mandos vía ISteamController/VDF (automático)
- [ ] **[NO VERIFICADO]** Radial, glyphs y logos en hardware

---

## (Histórico) Estado original target 1.21.4

---

## FASE 0: Documentación y Scaffold ✅ COMPLETADA

- [x] Crear CLAUDE.md
- [x] Crear SPEC.md
- [x] Crear ARCHITECTURE.md
- [x] Crear TASKS.md
- [x] Crear STATE.md
- [x] Crear TESTPLAN.md
- [x] Crear DECISIONS.md
- [x] Crear TODO_BLOCKERS.md
- [x] Crear estructura de directorios del proyecto
- [x] Crear build.gradle con dependencias correctas
- [x] Crear settings.gradle
- [x] Crear gradle.properties
- [x] Crear gradle wrapper (Gradle 8.12.1)
- [x] Crear fabric.mod.json
- [x] Crear steampad.mixins.json
- [x] Crear steampad.accesswidener (widener para Mouse privado)
- [x] Crear SteamPadMod.java (entry point)
- [x] Crear SteamPadClient.java (entry point cliente)
- [x] `gradle build` produce JAR sin errores ← VERIFICADO

---

## FASE 1: Steam Integration Base ✅ COMPLETADA (código) / ⚠️ NO VERIFICADA (runtime)

- [x] SteamNativeLoader — carga de natives con path custom opcional
- [x] SteamBootstrap — init/shutdown/runCallbacks de Steam API
- [x] SteamInputManager — GetConnectedControllers, RunFrame, state snapshots
- [x] SteamControllerHandleRef — value object con tipo y nombre de controlador
- [x] SteamActionRegistry — ActionSets + handles digitales/análogos via ISteamController
- [x] ControllerState — snapshot inmutable por tick
- [x] EnvironmentReport — detección de plataforma (Steam, Linux, Gamescope, Steam Deck)
- [x] LinuxRuntimeInspector — lectura de env vars Linux
- [x] GamescopeDetector — detección via GAMESCOPE_WAYLAND_DISPLAY
- [x] SteamDeckDetector — detección via SteamOS vars
- [x] GlobalConfig POJO con todos los campos de la spec
- [x] ControllerConfig POJO completo
- [x] ConfigManager — load/save, autosave, defaults
- [x] LogUtil
- [x] MinecraftClientMixin — hook shutdown (tick via ClientTickEvents)
- [ ] **[NO VERIFICADO]** Steam detecta controladores conectados — requiere hardware + Steam corriendo

---

## FASE 2: UI Base y Persistencia ✅ COMPLETADA (código) / ⚠️ NO VERIFICADA (runtime)

- [x] ControllerSelectScreen — lista de controladores con scroll, botones Select/Settings
- [x] ActiveControllerService — singleton con handle activo y persistencia
- [x] ControllerIsolationService — filtro de dispatch por handle activo ← UNIT TESTED
- [x] GlobalSettingsScreen — ajustes globales (Natives, Server, Misc)
- [x] ControllerSettingsScreen — contenedor de pestañas Basic/Advanced
- [x] ControllerBasicSettingsScreen — ajustes básicos + sección de bindings
- [x] ConfigManager — autosave funcional
- [x] ClipboardDebugService — copia debug dump al portapapeles
- [x] SteamRuntimeDiagnostics — generador de dump completo
- [x] BatteryMonitorService — monitoreo y notificación por nivel de batería
- [x] UiSoundService — sonidos de navegación
- [x] KeyBind para abrir ControllerSelectScreen (unbound por defecto)
- [ ] **[NO VERIFICADO]** Seleccionar controlador persiste entre reinicios — requiere MC real

---

## FASE 3: Sistema de Input ✅ COMPLETADA (código) / ⚠️ PARCIALMENTE VERIFICADA

- [x] InputAction — enum con 70+ acciones en 8 categorías
- [x] InputBinding — asociación acción → handle de Steam
- [x] BindingConfig POJO serializable (por controlador)
- [x] InputDispatchContext — contexto de dispatch (in-game, GUI, radial, vmouse)
- [x] DeadzoneProcessor — deadzone circular + escalado normalizado ← UNIT TESTED (8/8)
- [x] ChordInput — par modifier+main
- [x] ChordResolver — resolución y supresión de doble acción ← UNIT TESTED (7/7)
- [x] InputBindingManager — dispatcher completo con contexto y gyro
- [x] VirtualMouseController — cursor virtual via Mouse.onCursorPos (access widener)
- [x] GyroHandler — gyro → cámara con modos (Relative/Absolute, YawMode, RequireButton, FlickStick)
- [x] BindingsScreen — pantalla de configuración de bindings por categoría
- [x] CalibrationScreen — pantalla de calibración visual de sticks
- [x] MouseMixin — inyección para interceptar vmouse
- [x] ScreenMixin — inyección de ButtonGuideWidget
- [ ] **[NO VERIFICADO]** Bindings de gameplay funcionan en juego — requiere MC + controlador
- [ ] **[NO VERIFICADO]** GUI navigation funciona en pantallas — requiere MC + controlador

---

## FASE 4: Sistema Radial ✅ COMPLETADA (código) / ⚠️ NO VERIFICADA (runtime)

- [x] RadialSlot — datos de slot (acción, label, icono, tipo trigger)
- [x] RadialActionType — tipos de acciones radiales
- [x] RadialConfig POJO con 8 slots configurables
- [x] RadialMenuController — apertura, navegación, ejecución al cerrar
- [x] RadialRenderer — dibuja segmentos, texto, iconos
- [x] RadialMenuOverlay — overlay HUD integrado con HudRenderCallback
- [x] GameRendererMixin — placeholder vacío (⚠️ @Inject original tenía descriptor incorrecto → eliminado; ver D018 y Fix 4 en STATE.md)
- [x] RadialIconResolver + ItemIconProvider + EffectIconProvider + CharacterIconProvider
- [x] RadialEditorScreen — editor visual de slots
- [x] MalilibCompat — soft dependency con detección en runtime
- [ ] **[NO VERIFICADO]** Radial abre, navega y ejecuta acciones — requiere MC + controlador
- [ ] **[NO VERIFICADO]** Editor radial persiste configuración

---

## FASE 5: Features Avanzados ⚠️ PARCIAL

- [x] SteamHapticsService — vibración básica via ISteamController.triggerVibration ← STUB parcial (sin trigger motors)
- [x] GyroHandler — lógica de flick stick implementada (sin verificar)
- [x] SteamGlyphService — glifos via getGlyphForActionOrigin ← API verificada en JAR
- [x] SDLFallbackProvider — STUB documentado (no implementado intencionalmente)
- [x] ControllerAdvancedSettingsScreen — pestaña avanzada con mapping, vibración, gyro
- [ ] **[NO VERIFICADO]** Vibración funciona en juego con controlador real
- [ ] **[NO VERIFICADO]** Gyro funciona en juego con controlador que lo soporta
- [ ] **[PENDIENTE]** Enhanced Steam Deck driver flag — código existe pero sin efecto real en runtime

---

## FASE 6: Testing, Build y Entrega ⚠️ PARCIAL

- [x] Unit tests: ChordResolver — 7/7 PASSED
- [x] Unit tests: DeadzoneProcessor — 8/8 PASSED
- [x] Unit tests: ConfigManager serialización — 6/6 PASSED
- [x] Unit tests: ControllerIsolationService — 3/3 PASSED
- [x] `gradle test` — BUILD SUCCESSFUL, 24/24 tests pasan
- [ ] **[PENDIENTE]** Test manual completo según TESTPLAN.md — requiere hardware real
- [ ] **[PENDIENTE]** Fix de bugs encontrados en test manual
- [x] Build final: `gradle build` — BUILD SUCCESSFUL
- [x] Verificar JAR — steampad-0.1.0.jar (1.16MB) en `C:\Users\RChe\.gradle\controlify-build\steampad\_\libs\`
- [x] Actualizar TESTPLAN.md con resultados reales — ESTA SESIÓN
- [x] Actualizar STATE.md con estado real — ESTA SESIÓN
- [x] Actualizar DECISIONS.md con decisiones finales — ESTA SESIÓN
- [x] Actualizar TODO_BLOCKERS.md con estado actual — ESTA SESIÓN
- [ ] **[PENDIENTE]** README / documentación de instalación para usuario final
- [x] **[COMPLETADO 2026-06-24]** Archivo VDF de Steam Input → `steampad_steam_input/game_actions_480.vdf` con todos los ActionSets y localización en/es
- [ ] **[PENDIENTE]** Validación en Linux/SteamOS/Gamescope

### Fixes de runtime aplicados esta sesión (2026-06-24)
- [x] **Fix B010**: `SteamNativeLoader` ahora llama `SteamAPI.loadLibraries()` explícitamente — corrige `SteamException: Native libraries not loaded`
- [x] **Fix B009**: `SteamPadClient.createOpenMenuKeyBinding()` detecta constructor de `KeyBinding` via reflexión — corrige `NoSuchMethodError` en MC 1.21.10
- [x] **Fix B002**: VDF completo creado con 22 acciones InGame + 15 GUI + analógicos + localización
- [x] **Fix GameRendererMixin crash**: Eliminado `@Inject(method = "renderWorld")` con descriptor incorrecto `(float, long)` — la firma real es `(class_9779, boolean)`. Body era vacío, overlay ya registrado via `HudRenderCallback`. Mixin ahora vacío. Ver D018.

### Fixes de runtime aplicados (2026-06-24, sesión 1 — continuación)
- [~] **Fix 5 — Blur crash INTENTO FALLIDO**: `shouldPause()` = true + flag guard → compila pero sigue crasheando en MC real. Causa raíz incorrecta. REEMPLAZADO por Fix 7.
- [x] **Fix 6 — Icon button en Options junto a Controls**: `OptionsScreenMixin` reemplaza botón de texto 120×20 inferior-izquierda por `GamepadButton` 20×20 con icono de gamepad programático y tooltip, posicionado a la derecha del botón "Controls" (detección locale-independent por translation key).

### Fixes de runtime aplicados (2026-06-24, sesión 2)
- [x] **Fix 7 — Blur crash DEFINITIVO (SteamPadBaseScreen)**: Creado `SteamPadBaseScreen extends Screen` con override de `renderBackground()` usando `fillGradient` sin llamar `super`. Los 8 screens del mod extienden `SteamPadBaseScreen`. Eliminado flag `backgroundRendered` de ControllerSelectScreen. BUILD SUCCESSFUL. Ver D021 en DECISIONS.md.
- [x] **Fix 8 — IllegalFormatConversionException identificado**: Origen en el crash reporter de MC 1.21.10 (bug vanilla: `%f` con integer para "Screen size"). No accionable desde el mod. Desaparece al eliminar el crash primario. B013 cerrado.
- [x] **Fix 9 — Detección temprana de controladores**: `SteamPadClient.onInitializeClient()` paso 4.5 hace early scan tras `restoreFromConfig()`. Best-effort — puede retornar 0 si Steam no fue polled aún. Log indica cuántos se encontraron.
- [ ] **[PENDIENTE VALIDACIÓN]** ControllerSelectScreen abre sin crash con Steam unavailable y 0 controllers en MC real (Bazzite/Gamescope). ⚠️ Sin verificar.
- [ ] **[PENDIENTE VALIDACIÓN]** GlobalSettingsScreen y screens hijos abren sin crash en MC real. ⚠️ Sin verificar (todos heredan SteamPadBaseScreen).

### Fixes de runtime aplicados (2026-06-24, sesión 3)
- [x] **Fix 10 — NoSuchMethodError drawTextWithShadow(Text) en MC 1.21.10**: Reemplazadas todas las llamadas `ctx.drawTextWithShadow(textRenderer, Text.literal(...), x, y, color)` por el overload `OrderedText` (`.asOrderedText()`). Afectados: ControllerSelectScreen (drawStatusLine, renderDiagnosticPanel, entry loop), BindingsScreen (3 líneas), CalibrationScreen (1 línea), RadialEditorScreen (1 línea). BUILD SUCCESSFUL.
- [x] **Fix 11 — Diagnóstico AppID mejorado**: ControllerSelectScreen distingue "no AppID file" de "Steam not running" en el panel diagnóstico. Verifica ambas variantes de filename. Loguea rutas exactas. Muestra ruta del run dir en mensaje de ayuda. Botón "Retry Steam Init" añadido. SteamBootstrap.init() tiene guard de doble-init y loguea working directory en fallo.
- [ ] **[PENDIENTE VALIDACIÓN]** ControllerSelectScreen renderiza sin NoSuchMethodError en MC 1.21.10 real. ⚠️ Sin verificar.
- [ ] **[PENDIENTE VALIDACIÓN]** Panel diagnóstico muestra "no AppID file" cuando falta steam_appid.txt, "Steam not running" cuando existe pero Steam no corre.
- [ ] **[PENDIENTE VALIDACIÓN]** Botón "Retry Steam Init" aparece cuando Steam down, desaparece después de retry exitoso.

### Bloqueos de entorno descubiertos (2026-06-24)
- ⚠️ **B011**: Java 25.0.3 en PATH rompe `gradle test` (falla en config, no en ejecución). Comando de build válido: `set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot` antes de `gradle build --no-daemon -x test`

### Sesión 7 (2026-06-25) — Pulido ratón virtual/navegación + reestructura menú BOTONES (Fix 22)
- [x] **Sensibilidad del ratón virtual**: eliminada la doble multiplicación (×64); aplicación única; default 1.0; slider 0.2–3.0 en Básico y en la sección Ratón virtual.
- [x] **Lag del ratón virtual**: `onCursorPos` solo al cambiar posición; `glfwSetInputMode` solo en transición; snap solo al mover.
- [x] **Cursor**: punto blanco mediano con sombra (sin azul).
- [x] **Notificación de modo** (Activo/Desactivado/Auto) arriba-izquierda.
- [x] **Doble selección**: foco limpiado al mostrar cursor; cursor del SO sincronizado al widget enfocado en navegación por cruceta.
- [x] **Snap a widgets** en menús normales (`WidgetSnap`) + snap a casilla en contenedores.
- [x] **Navegación de cruceta espacial** (arriba/abajo/izq/der por geometría) en pantallas SteamPad y vanilla.
- [x] **Inventario**: cursor por defecto; cruceta salta casilla a casilla.
- [x] **Nombre del mando** en el encabezado de todas las pantallas.
- [x] **Menú BOTONES (4 zonas)**: pestañas, lista categorizada + sección de keybinds de mods, iconos de botón propios, panel lateral con descripción + Reiniciar/Deshacer/Aceptar, cuadrados Reiniciar/Chord por fila.
- [x] **Binds**: nuevos GYRO_TOGGLE/DROP_STACK/PICK_BLOCK/PLAYER_LIST/SCREENSHOT/HUD_TOGGLE; chords por bind; extra binds (botón→keybind de mod).
- [x] **i18n** en/es-MX/es-ES ampliados y validados (JSON OK).
- [x] **Build** `BUILD SUCCESSFUL`, jar 1.25 MB.
- [ ] **[PENDIENTE VALIDACIÓN]** Todo lo anterior en hardware (Bazzite/Deck). El remapeo de defaults cambia el layout previo; re-probar gameplay.
- [ ] **[PENDIENTE]** Acciones Creativo avanzadas (NBT, guardar/cargar barra), F3 y selector de gamemode: sin keybind vanilla estable; añadir vía extra binds si un mod las expone.

### Sesión 8 (2026-06-25) — Convivencia mouse/gamepad + rework radial (Fix 23)
- [x] **HUD sincronizado con binds** (item 1): glifos derivados de `GamepadBinds` (Y inventario, etc.).
- [x] **Selector radial no invertido** (item 2): fórmula de ángulo de la referencia.
- [x] **Inventario A coloca/suelta** (item 3): press+release en el clic simulado.
- [x] **Pistas de inventario** (item 4): A/X/Y/B/Select + Y quick-move cableado.
- [x] **Optimización ratón virtual** (item 5): `InputRouter` cancela fantasmas del ratón físico; menos onCursorPos.
- [x] **Puntero más pequeño** (item 6).
- [x] **Selección blanca** (item 7): casillas y radial.
- [x] **Notificación temporal** (item 8): ~2.5 s con fade.
- [x] **Memoria por contexto** (item 9): modo del ratón por clase de pantalla.
- [x] **Cruceta oculta ratón físico** (item 10).
- [x] **Convivencia + entrada mixta** (item 11): glifos se ocultan al mover el ratón si la entrada mixta está apagada.
- [x] **Mods en BOTONES** (item 12): todos los keybinds por categoría, mapeables.
- [x] **Rework radial** (items 13–17): editor dinámico, picker de keybinds, picker de iconos de MC, etiquetas, 2–12 slots equitativos, entrar al editor en gameplay (seleccionar + LT).
- [x] **Build** OK, jar 1.26 MB exportado a `dist/`. JSON validados.
- [ ] **[PENDIENTE VALIDACIÓN]** Todo en hardware (Bazzite/Deck): convivencia mouse/gamepad, lag, radial.
- [ ] **[PENDIENTE]** Radial: submenús reales; picker de pantallas como lista; iconos de efecto/carácter en el picker.
