# STATE.md — Estado Real del Proyecto SteamPad

> ### ▶️ REANUDAR AQUÍ — (anotado 2026-07-18, sesión 29 cont. 8, jar vigente `dist/steampad-0.61.0.jar`)
>
> **v0.61.0 — autocorrección + investigación abierta de "cadera/piernas" (D101, B095, FASE 79).**
> Tras v0.60.0 el usuario reportó que la cadera sigue mal (se sienta en el aire) y algunas
> animaciones tienen piernas que deberían estar quietas pero se mueven como la Macarena.
>
> 1. **Autocorrección real:** el fix de pivote de piernas de v0.60.0 (Z 0.1→0.0, "verificado por
>    bytecode") estaba basado en la fuente incorrecta. Vanilla SÍ usa 0.0, pero la propia librería
>    de animación de referencia (`PlayerModelMixin.setDefaultPivot()`, MIT) fuerza 0.1 como SU
>    baseline antes de posar cada frame — y ESE es el valor contra el que los datos de los emotes
>    están calibrados. Revertido a 0.1.
> 2. **"Cadera bloqueada / se sienta en el aire":** investigado a fondo con datos reales (curvas de
>    "Sit Adorably" verificadas con el mismo método público que usa el render) — el torso apenas se
>    mueve, las piernas logran la pose "sentada" por rotación (~-120°, estable). Sin evidencia de bug
>    de mezcla de datos. **Hipótesis más plausible, SIN cierre 100%:** limitación inherente de
>    emotes de solo-pose, que no mueven la posición real del personaje. Necesita el nombre exacto
>    del emote donde persista "piernas como Macarena" para seguir investigando.
> 3. **Chip de la Rueda +30% adicional** (pedido explícito).
> 4. **Debug Dump ampliado:** Rueda/Biblioteca abiertas + sección "Player Movement" nueva.
>
> Build limpio + suite completa en verde + 21/21 archivos reales re-verificados tras el revert.
> Falta validación visual y, para el punto 2, el nombre específico del emote afectado.

> ### (histórico) anotado 2026-07-18, sesión 29 cont. 8, jar `dist/steampad-0.60.0.jar`
>
> **v0.60.0 — el usuario CONFIRMÓ el mayor avance del histórico de emotes tras v0.59.0** ("casi
> resuelto la deformacion... ya se ve mucho mejor", los 21 archivos cargan, preview congelado por
> celda/chip "quedó perfecto", foco+animación "quedó perfecto"). Quedaron 2 detalles finos + 3
> pedidos nuevos, todos entregados en esta ronda (D100, TODO_BLOCKERS B094, TASKS FASE 78):
>
> 1. **Cadera:** pivote real de piernas era Z=0.1, debía ser Z=0.0 — verificado por `javap` sobre el
>    propio jar mapeado del proyecto (`BipedEntityModel.getModelData`), no supuesto.
> 2. **Rotación "bloqueada" en algunas animaciones:** encontrada una colisión real de keyframes en
>    "Friendship Round Dance.emotecraft" (body Y torso compitiendo por los mismos ejes) —
>    corregida (body gana, sin perder datos legítimos de ejes exclusivos de torso).
> 3. **Foco más grande:** Rueda 1.05/0.80→1.45/1.10; Biblioteca 52/19→76/27.
> 4. **Cámara libre durante el emote** (pedido nuevo): reutiliza el sistema de cámara libre existente
>    (v0.52.0) — se activa automáticamente mientras el jugador baila de verdad (no solo por navegar
>    la Biblioteca/Rueda), sin tocar el toggle persistido del usuario. El cuerpo del personaje ya no
>    gira para encarar la cámara mientras el usuario orbita.
> 5. **Volcado de pose detallado** (pedido nuevo): el Debug Dump ahora incluye, mientras hay un
>    playback activo, cada parte/eje con su estado en vivo — sección "Local pose".
>
> Build limpio + suite completa en verde (test nuevo de la colisión body/torso) + 21/21 archivos
> reales re-verificados tras todos los fixes. Falta SOLO la validación visual en hardware.

> ### (histórico) anotado 2026-07-18, sesión 29 cont. 8, jar `dist/steampad-0.59.0.jar`
>
> **v0.59.0 — "Emotes perfectos": reescritura completa del subsistema de emotes con la semántica
> REAL de Emotecraft, leída de las fuentes por orden explícita del usuario. Detalle: D099,
> TODO_BLOCKERS B093 (checklist de pruebas), TASKS FASE 77.**
>
> **LA causa raíz de la deformación (6 rondas, D082–D087 eran compensaciones):** los valores de
> posición del formato son **PIVOTES ABSOLUTOS** en espacio del modelo vanilla (Y hacia abajo, sin
> flip; defaults = pivotes vanilla: rightArm=(-5,2,0)…) y se ASIGNAN directo al part — el motor
> viejo los sumaba al reposo (duplicando cada offset) + flip de Y + matriz de torso. Verificado por
> fuente MIT (playerAnimator `AnimationApplier`/`KeyframeAnimationPlayer`) Y por aritmética sobre el
> log del usuario (R_ARM distFromRest ≡ 5.385 = |(-5,2,0)|). Todo el motor reescrito como port fiel
> (canales por eje, ease-in/out contra el valor vanilla VIVO vía keyframes virtuales, loop con span
> inclusivo `end−return+1`, easings con la matemática real de KosmX incl. tabla de IDs binarios).
>
> **Parser binario `.emotecraft` COMPLETO (v1–v4):** wrapper + sub-paquetes (0x00 data / 0x11 header
> / 0x12 icono PNG), partes nombradas v2 con flags bendable por nombre, tolerancia `-1 = disabled`
> de escritores v2 viejos (13/21 archivos del usuario), merge body+torso→TORSO, metadatos y
> loop/ticks REALES por primera vez. **Verificado: los 21 archivos reales del usuario parsean 21/21
> con el parser Java real (nombre/autor/loop/ticks/icono), desde fuera del juego.** Parser JSON
> corregido contra la referencia: `degrees` default TRUE, `turn` = segundo keyframe +2π×turn,
> `easingArg`/`easeBeforeKeyframe` honrados.
>
> **Previews (FASE 77):** TODAS las celdas de la Biblioteca y chips de la Rueda muestran ahora el
> personaje congelado en un frame de SU baile (mecanismo nuevo: render-states taggeados por draw —
> duck interface en `PlayerEntityRenderState` + mixin en `PlayerEntityRenderer.updateRenderState`,
> firma verificada por javap — compatible con el render diferido de D092); la celda/chip con foco se
> ve MÁS GRANDE y anima en vivo. B092 resuelto: los previews no animaban porque el emote en loop del
> propio usuario los bloqueaba (`conflictsWithRealEmote` permanente) → ahora el playback real se
> APARCA al abrir y se restaura al cerrar (salvo que confirmes un emote nuevo dentro), y un preview
> no-loop que termina se RELANZA en vez de quedarse congelado.
>
> **Build:** limpio, suite completa de tests en verde (+4 tests nuevos: contrato real de muestreo,
> defaults absolutos, tabla de IDs, contenedor binario sintético). Pendiente SOLO validación visual
> en hardware — checklist concreto en B093.
>
> _(Lo de v0.58.0 abajo quedó confirmado en hardware: mouse-lag OK; su condición de primer arranque
> sigue anotada sin causa en D098.)_

> ### (histórico) anotado 2026-07-17, sesión 29 cont. 8, jar `dist/steampad-0.58.0.jar` — SIN cambios de código esa ronda, solo documentación a pedido explícito del usuario
>
> **Dos novedades de campo sobre v0.58.0, documentadas sin tocar código:**
>
> **1. ✅ Lag del mouse virtual (B091/D098) — CONFIRMADO CORREGIDO, con una condición inicial sin
> explicar todavía.** El usuario probó v0.58.0: el lag SEGUÍA apareciendo la PRIMERA vez que abrió el
> juego con el jar nuevo; apagó y volvió a encender el 8BitDo, y a partir de ahí (incluyendo reinicios
> posteriores del juego con el control ya conectado) funcionó correctamente. El fix en sí queda
> validado. La condición del primer arranque tiene 2 candidatos SIN evidencia de código todavía (ver
> el post-mortem en D098): (a) un pad ya abierto por SDL3 ANTES de que `loadMappings()` corriera
> podría quedarse con estado/mapeo viejo hasta una apertura fresca; (b) Steam Input necesitando
> refrescar su propia negociación con el pad tras el reinicio del juego. Si vuelve a pasar, el volcado
> nuevo (sección Backends & Mappings) debería mostrar el orden real apertura-vs-mapeo la próxima vez.
>
> **2. NUEVA regresión reportada — previos "AAA" de emotes volvieron al ícono plano (B092, sin
> investigar todavía — el usuario pidió explícitamente "no programes, solo documenta").** El thumbnail
> posado con el propio personaje (Biblioteca y/o Rueda, no especificó cuál) volvió a mostrar solo el
> ícono PNG plano de siempre. El usuario aclara que esto es DISTINTO de la deformación de emotes ya
> conocida (que sigue abierta, sin fix confirmado). **Antes de tocar código la próxima vez, hace falta
> del usuario:** ¿Biblioteca, Rueda, o ambas? ¿nunca anima ninguna celda, o la enfocada intenta posar
> pero sale mal? un `latest.log` de la navegación buscando (o notando la AUSENCIA de) líneas
> `[emote-pose]`; y qué versión exacta era "la que sí funcionaba" para acotar entre qué versiones se
> rompió (sospecha más directa, sin confirmar: la reescritura de D092 en v0.56.0).
>
> _(Todo lo de v0.58.0 y anterior abajo sigue vigente/pendiente de validar también.)_
>
>
> **NUEVO v0.58.0 — Causa raíz REAL del lag del mouse virtual con el 8BitDo (doble input, ciclo
> esconder/teleport rastreado línea por línea) + volcado de debug expandido a TODO el mod. Detalle:
> D098, TODO_BLOCKERS.md B091, TASKS.md FASE 76.**
>
> **Contexto crítico de esta ronda:** v0.57.0 se probó en hardware — su cambio de backend FUNCIONÓ
> (el log del usuario confirma: 8BitDo ahora por SDL3, paddles P1-P4 expuestos por primera vez,
> "SDL3 gamepad mappings loaded: 4 added, 2 updated, 0 failed") — **pero el lag PERSISTIÓ idéntico**.
> El backend nunca fue la causa (post-mortem agregado a D097). El usuario exigió "DEJA DE ADIVINAR,
> lee codigo" y aportó el dato clave: **"Pasa con el mouse virtual y cuando se conecta el 8bitdo"**.
>
> **El mecanismo (hecho del código, verificable línea por línea — D098):** movimiento de mouse
> EXTERNO correlacionado con el stick del 8BitDo (candidato #1: Steam Input desktop layout emulando
> mouse desde el MISMO pad que SteamPad lee crudo — el "Steam Virtual Gamepad" del volcado del
> usuario lo delata; el Ally no sufre porque Steam lo consume COMPLETO, un solo flujo) → barridos
> >20px/evento → `MouseMixin.markMouseForce()` (salta la ventana de protección a propósito) →
> `onPhysicalMouseTookOver()` ESCONDE el cursor → ≤50ms después `onStickUsed()` lo re-muestra
> TELETRANSPORTADO a donde el mouse externo dejó el puntero (`syncFromOsMouse`) + borra el foco —
> varias veces por segundo = exactamente "lag terrible, sin control", solo mouse virtual, solo 8BitDo.
>
> **Fix:** árbitro nuevo en `MouseMixin` — mientras el stick dirige el cursor, el movimiento externo
> no puede robarse el puntero (se traga + cuenta + loggea `[mouse-arb]`). Stick quieto → mouse físico
> intacto; click físico gana siempre; gameplay sin tocar. Nueva `MouseEventStats` mide TODO el flujo.
>
> **Volcado de debug expandido a todo el mod** (pedido explícito del usuario): versión, Backends &
> Mappings, etiqueta @SDL3/@GLFW decodificada por mando, warning de doble claim de Steam, Input Flow
> (con los contadores del mouse — banner "DOUBLE INPUT DETECTED"), Performance (TickProfiler siempre
> capturado), config del mando activo, toggles globales, emotes.
>
> **PRÓXIMA SESIÓN — lo primero:** el resultado de la prueba de hardware de B091, en particular:
> (1) ¿el cursor ya se siente bien con el 8BitDo?; (2) ¿aparecen líneas `[mouse-arb]` en el log?
> (= doble input confirmado y contenido); (3) la prueba discriminante de cerrar Steam por completo;
> (4) el volcado NUEVO completo. Si los contadores salen en cero y el lag persiste, la hipótesis
> queda refutada con evidencia — el mismo volcado (sección Performance) dirá qué mirar después.
>
> _(Todo lo de v0.57.0 y anterior abajo sigue vigente/pendiente de validar también.)_
>
> **v0.57.0 — Causa raíz real del lag del joystick 8BitDo + gap real de limpieza al fallar sobre
> otro control. Detalle completo: D097, TODO_BLOCKERS.md B090, TASKS.md FASE 75.**
> **POST-MORTEM: el cambio de backend se confirmó en hardware, pero el LAG no era eso — ver arriba.**
>
> El usuario pasó un log COMPLETO de una sesión real (ROG Ally + 8BitDo, Bazzite/gamescope/Sunshine)
> con dos reportes, exigiendo explícitamente "investiga no adivines, lee codigo". Un primer intento de
> usar el Workflow tool con 4 agentes de investigación en paralelo + verificación adversarial FALLÓ POR
> COMPLETO (límite de sesión alcanzado en los 5 agentes) — toda la investigación de esta ronda se hizo
> leyendo el código directamente, sin subagentes.
>
> **1. Lag del joystick 8BitDo (alta confianza — confirmado con aritmética exacta, no supuesto):**
> decodificando los handles reales del log (Node + BigInt) contra `Sdl3GamepadProvider.SDL3_HANDLE_BASE`,
> el handle del ROG Ally llevaba la etiqueta "SDL3" pero los dos handles del 8BitDo decodificaban a
> ASCII **"GLFW"** — el 8BitDo nunca fue visible para SDL3 ni un instante en TODA la sesión (`SDL3=1`
> fijo en el log), cayendo siempre al camino de respaldo GLFW (joystick genérico) en vez del camino
> SDL3 (HIDAPI-capaz, optimizado activamente) — a pesar de que el código ya activa el hint
> `SDL_JOYSTICK_HIDAPI_8BITDO` específicamente para él. Causa: `GamepadMappings.loadAll()` únicamente
> llamaba `glfwUpdateGamepadMappings` (GLFW) — nunca enseñaba las mismas líneas de mapeo a libSDL3, y
> `SDL_GetGamepads()` solo enumera dispositivos que la base de mapeos DE SDL ya reconoce como gamepad.
> **Fix:** `Sdl3Native` gana `SDL_AddGamepadMapping`; nuevo `Sdl3GamepadProvider.loadMappings()` enseña
> las MISMAS líneas a SDL3, línea por línea; `SteamPadClient` reordenado para iniciar SDL3 antes.
>
> **2. Confusión de botones al fallar el ROG Ally sobre el 8BitDo desconectado (gap real, no 100%
> confirmado como única causa):** el failover automático de `SteamPadClient` cambia el handle activo a
> un control DISTINTO dentro del MISMO tick en que detecta la desconexión — `GamepadInputDispatcher.tick()`
> nunca vuelve a ver el handle viejo para disparar su limpieza de "controlador desaparecido"
> (`releaseAllMovement` — libera ataque/usar/lista de jugadores, keybinds de mod sostenidos, zoom
> activo). Cualquier estado que el 8BitDo mantuviera exactamente al desconectarse quedaba sin liberar
> hasta reiniciar el juego. **Fix:** nuevo `releaseAllHeldStateOnControllerLoss()` público, llamado en
> el punto exacto de detección de desconexión, con logging de diagnóstico para la próxima prueba real.
>
> Build + 29/29 tests, compiló limpio a la primera. **Nada de esto se ha probado en hardware todavía**
> — el punto 2 en particular puede necesitar otra ronda si el logging nuevo (busca
> `[controller-loss]` en `latest.log`) revela que algo más queda sin liberar.
>
> _(Todo lo de v0.56.0 y anterior abajo sigue vigente/pendiente de validar también.)_
>
> **v0.56.0 — Lote grande tras el reporte de validación más detallado hasta ahora (36 OK / 18
> fallas / 59 ítems), con foco explícito en Tercera Persona ("el que más bugs tiene") y deformación de
> emotes (prioridad #1, sexta ronda). Detalle completo: D092-D096, TODO_BLOCKERS.md B089,
> TASKS.md FASE 74.**
>
> **Lo que quedó REALMENTE arreglado esta ronda (alta confianza, verificado por bytecode/geometría, no
> supuesto):**
> 1. **"Se reproducen todos" en previos de emotes** (Rueda + Biblioteca) — causa raíz confirmada por
>    `javap`: `InventoryScreen.drawEntity` encola su dibujo, el posado real ocurre después leyendo un
>    mapa GLOBAL por-ID-de-entidad — todas las casillas encoladas terminaban compartiendo un solo
>    valor final. Fix: solo la casilla/fila enfocada anima en vivo; el resto usa ícono plano.
> 2. **Tercera Persona — "se ve como primera persona, alguien invisible"** — el mixin de free-look
>    cancelaba `Camera.update()` en HEAD, impidiendo que vanilla fijara su propio campo `thirdPerson`
>    (decide si el cuerpo se dibuja). Fix: TAIL en vez de HEAD-cancelable.
> 3. **Tercera Persona — Izquierda/Derecha invertidos** — confirmado por 3 métodos independientes
>    (geometría cardinal ×2 + regla de la mano derecha) que el vector "derecha" tenía el signo
>    invertido. Fix: helper compartido `rightVectorXZ()`. NO afecta `applyCameraRelativeMovement` (esa
>    fórmula ya estaba verificada byte-a-byte contra vanilla, es una pregunta distinta).
> 4. **Tercera Persona — movimiento relativo a cámara con animación/sprint desincronizados** — el
>    cuerpo giraba y caminaba bien, pero los booleans de `PlayerInput` seguían leyendo el input crudo
>    pre-remapeo. Fix en `KeyboardInputMixin`.
> 5. **SlotSnap/Traveler's Backpack** — revertido el radio angosto de casillas (rompía el inventario
>    general con el cursor virtual); el problema original (botón perdiendo contra casilla vecina) se
>    resuelve ahora con puntuación normalizada + prioridad de widgets, no con radio angosto.
> 6. **Onboarding nunca se disparaba** — estaba anidado dentro de la re-verificación de handle de la
>    vibración de inicio; si el handle cambiaba en la ventana de 750ms, AMBOS se saltaban en silencio
>    para siempre. Desacoplados.
>
> **Lo que sigue siendo DIAGNÓSTICO puro (sin fix confirmado, necesita el LOG REAL de la próxima
> prueba, no solo "funcionó"/"no funcionó"):**
> - **Deformación de emotes (6ª ronda reportada):** volcado completo de pose por `EmoteAnimator.apply()`
>   cada ~2s por entidad — buscar líneas `[SteamPad][emote-pose]` en `latest.log` mientras se ve un
>   emote deforme.
> - **Haptics de arma cuerpo a cuerpo (4ª+ ronda):** el mixin y la lógica se revisaron sin encontrar un
>   bug — logging sin throttle en `onMeleeHit`, buscar `[haptics-melee]` en el log.
> - **Rendimiento de Cámara Libre:** temporizador nuevo en `computeFreePose`, buscar
>   `[thirdperson-perf]` en el log (aparece cada ~2s mientras la cámara libre está activa).
> - **Vibración de inicio:** si sigue fallando, buscar `Startup rumble SKIPPED` en el log — confirmaría
>   la condición de carrera de handle en vez de necesitar otro ajuste de intensidad.
>
> **Ajustes de sensación de bajo riesgo (sin verificar en hardware):** slime ahora también pulsa
> suavemente en reposo, no solo caminando; jugosidad (shake/FOV kick) subida 1.2× global.
>
> Build + 29/29 tests, compiló limpio a la primera. **Nada de esto se ha probado en hardware
> todavía** — la PRIMERA prioridad de la próxima sesión debería ser recibir el reporte de validación
> (checklist + contenido real de `latest.log` para los 3 puntos de diagnóstico puro de arriba) antes
> de tocar más código en estas áreas.
>
> _(Todo lo de v0.55.0 y anterior abajo sigue vigente/pendiente de validar también.)_
>
> **v0.55.0 — Preview "AAA" extendido a la Rueda de Emotes EN PLENO JUEGO (D091/B088):** la
> ronda anterior (v0.54.0) solo tocó la Biblioteca de Emotes, a propósito. El usuario preguntó
> directamente si la rueda en gameplay también había recibido el cambio — no — y pidió implementarlo
> ahí "de la mejor forma".
>
> **Cómo se hizo:** en vez de duplicar la matemática de posición de cada casilla (que vivía dentro
> del bucle privado de `RadialRenderer`, compartiendo estado animado con el blob de gelatina y el
> carrusel), se agregó un HOOK OPCIONAL por-casilla (`RadialRenderer.SlotThumbnailRenderer`) invocado
> desde DENTRO de ese mismo bucle, con la posición/radio ya calculados. Nueva sobrecarga de
> `render(...)` — la firma vieja de 7 argumentos sigue intacta y delega con `null`, así que el menú
> radial normal, el editor de radial y la pantalla de estilo NO cambian de comportamiento (confirmado
> leyendo cada uno de esos 3 llamadores). Cada casilla no vacía de la Rueda de Emotes ahora posa al
> jugador en un frame fijo de su emote; la casilla SELECCIONADA reproduce el baile completo en
> tiempo real, ahí mismo — mismo mecanismo de pose-y-dibuja secuencial + snapshot/restore de la ronda
> anterior, aplicado a una geometría circular en vez de una lista. El panel lateral fijo se eliminó
> de este overlay (igual que en la Biblioteca); el editor de la Rueda de Emotes (pantalla de pausa)
> sigue sin tocar.
>
> Build + 29/29 tests, compiló limpio a la primera. **Nada de esto se ha probado en hardware
> todavía.** Checklist completo en B088/`CHECKLIST.html`.
>
> _(Todo lo de v0.54.0 y anterior abajo sigue vigente/pendiente de validar también.)_
>
> **v0.54.0 — Preview "AAA" de la Biblioteca de Emotes (D090/B087):** el usuario retomó una
> idea que había quedado a propósito en el backlog varias sesiones atrás — reemplazar el panel
> lateral fijo de vista previa por un thumbnail POR FILA (frame fijo del baile), que cobra vida y
> reproduce la animación completa SOLO cuando esa fila tiene el foco/selección, congelándose de
> vuelta al perder el foco. Confirmó el diseño exacto antes de programar.
>
> **Cómo se resolvió el obstáculo real:** Minecraft comparte una sola instancia de modelo entre todos
> los jugadores — solo puede haber UNA pose "viva" a la vez para el cuerpo del jugador local. Como la
> Biblioteca solo necesita una fila viva a la vez (la enfocada, exactamente lo pedido), el resto de
> las filas usan un nuevo mecanismo de "pose de un solo cuadro" (`applyPinnedFrame` en
> `EmoteAnimator`) — se posa y se dibuja una fila a la vez, secuencialmente, dentro del mismo frame.
> Un nuevo par `snapshotPlayback`/`restorePlayback` protege cualquier emote real que el jugador
> dispare con "▶" mientras navega, de ser pisado por las poses fijas de las demás filas.
>
> **Alcance:** solo la Biblioteca de Emotes (lo pedido explícitamente, "el previo que está a la
> derecha") — el editor de rueda y el overlay de la Rueda en pleno juego siguen con el panel lateral
> fijo, sin tocar. Extender el mismo tratamiento a la Rueda EN GAMEPLAY queda en el backlog por su
> costo de rendimiento en tiempo real (renderizar varios modelos 3D durante juego activo es un
> contexto mucho más sensible que un menú en pausa).
>
> **De paso, confirmado sin necesitar ningún cambio:** la Rueda de Emotes ya comparte la misma
> vibración de selección que el menú radial normal (el usuario no estaba seguro) —
> `EmoteWheelController.updateAnalog()` ya llama a `HapticsController.radialSelectPulse()`.
>
> Build + 29/29 tests, compiló limpio a la primera. **Nada de esto se ha probado en hardware
> todavía** — ni siquiera se ha podido VER renderizado (no hay forma de tomar una captura de
> Minecraft desde este entorno), así que el encuadre/escala exacto de la miniatura es un valor
> elegido a mano, documentado como pendiente de ajuste visual. Checklist completo en
> B087/`CHECKLIST.html` (que ahora también tiene un panel de "Pendiente de implementar" espejando el
> backlog de TASKS.md, para leer en 10 segundos qué falta sin abrir ese archivo).
>
> _(Todo lo de v0.53.0 y anterior abajo sigue vigente/pendiente de validar también.)_
>
> **v0.53.0 — Autocrítica "AAA" ejecutada, 6 mejoras (D089/B086):** el usuario pidió una
> autoevaluación crítica y objetiva de qué le falta al mod para ser "la mejor experiencia de gamepad
> de todos" — se entregaron 8 puntos, el usuario filtró personalmente cuáles ejecutar.
>
> **Lo nuevo:** (1) haptics distintas por ARMA al golpear/matar — espada/hacha/tridente/maza, cada una
> con su propia firma, más una "flecha confirmada" como toque corto, y remate garantizado en cada
> muerte; detección real vía `ItemTags` (`SwordItem` ya no existe como clase en 1.21.10, se detecta
> por tag). (2) la cámara libre ya no recalcula su pick de crosshair/entidades cada frame de render,
> solo cada ~50ms — la optimización que yo mismo señalé como pendiente en la autocrítica. (3)
> movimiento relativo a cámara, opción NUEVA apagada por defecto — toca `KeyboardInputMixin` pero el
> camino existente queda intacto si no se activa. (4) "jugosidad": screen shake + un "empujón" rápido
> de FOV en golpes/muertes/explosiones/daño recibido/caídas, en 1ª y 3ª persona — deliberadamente SIN
> hit-stop real (congelar la simulación), por la propia historia de bugs de cámara del proyecto
> (D046-D053). (5) pantalla de bienvenida de una sola vez (onboarding) para el problema de
> descubribilidad detectado esta misma sesión. (6) perfiles de configuración con nombre.
>
> **Descartado explícitamente, no pendiente:** triggers adaptativos (steamworks4j no expone la API de
> Steam Input que los soporta), API pública para mods de terceros (valor incierto sin adopción
> externa), rediseño de UI "10-foot" (el usuario lo pospuso: "no lo veo, estoy en duda con esto").
>
> Build + 29/29 tests, compiló limpio a la primera. **Nada de esto se ha probado en hardware
> todavía.** Checklist completo en B086/`CHECKLIST.html`.
>
> _(Todo lo de v0.52.0 y anterior abajo sigue vigente/pendiente de validar también.)_
>
> **v0.52.0 — Cámara Libre completa, el resto del feature set de Leawind's Third-Person (D088/B085):**
> pedido explícitamente por el usuario por TERCERA vez ("aun no estoy en casa" pero quiso adelantar
> el código) tras dos rondas previas (D082, D083) donde el free-look se dejó fuera de alcance a
> propósito. Esta vez se implementó completo: se leyó el código fuente real del mod (MIT, versión
> estable `v2.5.0-mc1.21.11`, no la rama en desarrollo) para portarlo fielmente en vez de adivinar.
>
> **Lo nuevo:** rotación libre (el stick gira la cámara sin girar el cuerpo — redirigido en
> `CameraController.frame()`, sin mixin nuevo de riesgo sobre el turno del jugador); cámara con centro
> de rotación + colisión + distancia ajustable (reemplaza el offset simple SOLO cuando el toggle nuevo
> está activo — el offset de D082 sigue intacto apagado); mira funcional que redirige atacar/usar/minar
> a lo que el crosshair señala (2 mixins nuevos: `Entity#raycast` y `InGameHud#renderCrosshair`,
> verificados con `javap` contra el jar Yarn real); puntería predictiva al apuntar con arco/ballesta/
> tridente; 3 modos de hacia-dónde-mira-el-cuerpo (simplificado de los 5 del mod real, y solo actúa
> parado — ver la nota de alcance abajo); ajuste en vivo de offset/distancia con un bind de mantener.
>
> **Nota de exploración honesta:** a mitad de la investigación de `javap`, el primer intento apuntó
> por accidente a un jar de OTRO proyecto (`controlify_lts_test`) cacheado en la misma máquina, con
> mappings de Mojang en vez de Yarn — se detectó por el paquete incorrecto y se corrigió localizando
> el jar Yarn 1.21.10+build.3 real ANTES de escribir cualquier mixin. Ningún mixin se escribió contra
> una firma sin verificar.
>
> **Nota de alcance deliberada (no un recorte oculto):** replicar fielmente "el cuerpo gira para
> seguir la dirección de movimiento" exige movimiento relativo a cámara, lo que requeriría tocar
> `KeyboardInputMixin` — el mixin de movimiento más crítico del proyecto. Se decidió NO tocarlo: la
> estrategia de rotación del cuerpo solo actúa con el jugador QUIETO; mientras caminas, todo se
> comporta exactamente igual que sin cámara libre. Transparencia del jugador (cosmética, apagada por
> defecto incluso en el mod real) tampoco se implementó — ver TODO_BLOCKERS.md B085.
>
> **Todo esto vive detrás de `thirdPersonFreeLookEnabled` (apagado por defecto).** Con el toggle
> apagado, absolutamente nada de este código se ejecuta — el comportamiento es idéntico a v0.51.0.
> Build + 29/29 tests, compiló limpio a la primera. **Nada de esto se ha probado en hardware todavía**
> — es la feature de mayor riesgo/alcance implementada en una sola ronda en la historia del proyecto,
> justamente porque se hizo sin poder probar en tiempo real. Checklist completo en B085/`CHECKLIST.html`.
>
> _(Todo lo de v0.51.0 y anterior abajo sigue vigente/pendiente de validar también.)_
>
> **v0.51.0 — primera ronda con el checklist HTML consolidado, 29/42 confirmados de v0.50.0 (D087/B084):**
>
> **1. Deformación de emotes — causa raíz REAL #5:** la traslación del torso (canales x/y/z, común en
> bailes con rebote/cadera — DISTINTA de la rotación ya corregida en D084 y de la pose agachada de
> D085) nunca se propagaba a cabeza/brazos/piernas. Cada miembro se quedaba anclado a su propio
> reposo mientras el torso se desplazaba — exactamente "se separan o se montan sobre el cuerpo" que
> el usuario seguía viendo en AMBOS escenarios de prueba (torso girando Y agachado cerca). Fix en
> `EmoteAnimator.java`: la traslación del torso ahora se propaga a cada miembro que la animación toca.
>
> **2. Vibración de slime — bug real de detección, no de magnitud:** el usuario reveló el dato clave
> — el mismo preset SÍ se siente al probarlo manual en el Panel de Prueba de Haptics, pero nunca al
> caminar sobre slime real. Eso descarta la teoría de 3 sesiones atrás (D067, "el driver no interpreta
> pulsos superpuestos") — el problema era detección, no magnitud: `getBlockPos().down()` sufre
> parpadeo de punto flotante justo al caminar (a veces lee el bloque de ABAJO del slime). Corregido
> con el mismo margen fijo que usa vanilla internamente para este problema exacto.
>
> **3. Vibración de inicio "se eliminó":** posible condición de carrera — el disparo (de una sola vez
> por sesión) ocurría en el mismo tick en que el mando se marca activo, antes de que SDL3/GLFW
> reconozcan ese handle, arriesgando un fallo silencioso permanente. Ahora se difiere ~750ms.
>
> **4. Traveler's Backpack:** implementada la solución que el propio usuario diagnosticó y propuso —
> las casillas de inventario ahora solo "jalan" el cursor cuando ya está prácticamente DENTRO de la
> celda (8px, antes 22px compartidos con los widgets), dejando el radio amplio para los botones de
> mods, que ya no pierden esa franja angosta contra las casillas vecinas.
>
> **5. `.emotecraft` v1:** investigada la documentación PÚBLICA de Emotecraft (wiki oficial, sin leer
> código GPL) por pedido explícito del usuario. Encontrado el envoltorio del contenedor (modular,
> versionado por diseño) — explica por qué existen sub-formatos, pero no la codificación interna de
> keyframes que hace falta para decodificar "versión 1". Sigue bloqueado, con mejor contexto del
> porqué.
>
> **6. Bind de Tercera Persona:** no era un bug — vivía sin indicación en Botones → Jugabilidad.
> Agregado un botón directo en Ajustes Globales → Tercera Persona.
>
> **7. Checklist HTML rediseñado:** de checkbox binario a 3 estados (No probado/Falló/OK) + nota de
> texto libre + exportar reporte, con TODOS los ítems en un único array de datos JS — mantenerlo
> actualizado en sesiones futuras es una edición barata, no una reescritura. Vive ahora en
> `CHECKLIST.html`, en la raíz del repo (antes era un archivo temporal de sesión).
>
> **Nota de infraestructura:** `gradlew`/`gradlew.bat` faltaban del repo (sin git, no queda rastro de
> cuándo se perdieron) — regenerados apuntando a Gradle 8.14, la versión que este mismo archivo
> documenta como exigida por Loom 1.13.6.
>
> _(Todo lo de v0.50.0 y anterior abajo sigue vigente/pendiente de validar también.)_
>
> **v0.50.0 — quinta ronda, 20 archivos `.emotecraft` reales adicionales + bug de reconexión de mando (D086/B083):**
>
> **1. `.emotecraft`: de 1 a 12 de 21 archivos reales cargando.** Los límites de seguridad del
> parser (D085) estaban calibrados con UNA sola muestra simple — con 21 muestras reales del
> usuario se subió el tope de keyframes por canal (64→10,000) y se quitó el límite de la ventana
> de resincronización (512 bytes→sin límite). Verificado con test real: 10 archivos con las 6
> partes completas, 2 con 5 de 6 (falta solo una extremidad). Los 9 restantes siguen sin soporte
> (8 son el sub-formato "versión 1" ya documentado; 1 tiene una variante estructural distinta sin
> evidencia suficiente para decodificar con confianza — ver B083).
>
> **2. Ícono no aparecía — bug real de overload de `drawTexture` encontrado y corregido:** faltaban
> los parámetros `regionWidth`/`regionHeight`; ahora usa el mismo patrón ya probado en
> `ButtonIcon`/`ControllerBrandIcon`.
>
> **3. Bug NUEVO reportado y corregido — reconectar el mando perdía la configuración:** SDL3 asigna
> un handle numérico NUEVO cada vez que el mismo mando físico se reconecta (confirmado en tu log:
> el mismo 8BitDo cambió de handle), pero los archivos de config se guardaban por ese handle — un
> reconectar en pleno juego creaba binds/rueda en blanco. Fix: se migra la config del handle
> anterior al nuevo automáticamente por NOMBRE del control (nunca sobrescribe una config
> existente). Ya no debería hacer falta reiniciar el juego tras reconectar.
>
> **4. Preview "no funciona en menú ajustes":** no se encontró un bug de código distinto al del
> ícono — el preview animado necesita una entidad de jugador real (imposible sin mundo cargado); se
> agregó una línea aclaratoria en el panel en vez de dejarlo en blanco.
>
> **Nota:** estos archivos `.emotecraft` vienen de una herramienta de terceros llamada "MineEmotes"
> (firma de texto encontrada en su metadata), no del exportador oficial de KosmX/Emotecraft.
>
> _(Todo lo de v0.49.0 y anterior abajo sigue vigente/pendiente de validar también.)_
>
> **NUEVO v0.49.0 — cuarta ronda de feedback, "no termines hasta que cumplas todos los pasos" (D085/B082):**
>
> **1. Deformación — causa raíz REAL #4, confirmada por bytecode (javap), no inferida:**
> `BipedEntityModel.setAngles` de vanilla desplaza `originY`/`originZ` 3.2-4.2 unidades en la pose de
> agachado. El caché de "rest origin" (v0.47.0) leía los campos EN VIVO de la primera entidad no-
> emotando que se renderizara, asumiendo que eso bastaba para estar "limpia" — falso: si esa entidad
> resultaba estar agachada, el valor contaminado quedaba cacheado PARA SIEMPRE, descolocando cada
> emote de cada jugador el resto de la sesión. Fix: leer siempre el pivote HORNEADO e inmutable
> (`ModelPart.getDefaultTransform()`) en vez de los campos en vivo — elimina el riesgo por completo,
> no lo mitiga. **Prueba clave:** que alguien se agache cerca y luego reproduce un emote — ya no
> debería deformarse.
>
> **2. Bug NUEVO y crítico, encontrado y corregido — "la cámara transiciona pero el emote no se
> reproduce":** al seleccionar un emote en la rueda de gameplay, el preview animado y la reproducción
> real usan la MISMA entidad (el jugador) sin forma de distinguirse — cerrar la rueda tras confirmar
> cancelaba el emote real un frame después de iniciar. Fix: token de generación
> (`EmoteAnimator.currentGeneration`) — un preview solo se detiene a sí mismo si nadie más tomó su
> lugar mientras tanto. Aplicado también en Biblioteca y editor de rueda por el mismo riesgo latente.
>
> **3. Preview "desaparecido" de la Biblioteca — explicación, no necesariamente un bug de código:** si
> quedó un emote EN BUCLE corriendo sin detenerlo antes de abrir la Biblioteca, el guard (correcto en
> intención: nunca pisar un emote real) lo bloquea indefinidamente — se ve como "ya no está". El panel
> ahora SÍ muestra el personaje si hay cualquier cosa reproduciéndose (nuestra o real), en vez de
> quedar en blanco sin explicación.
>
> **4. `.emotecraft` — investigación profunda con los 3 archivos reales del usuario, resultado
> HONESTO y PARCIAL:** confirmado con evidencia hexadecimal que NO es un `.json` renombrado (la
> asunción anterior estaba mal) sino el formato binario NATIVO real de Emotecraft (confirmado en su
> documentación pública oficial — nunca se leyó su código GPL). Reverse-engineering clean-room
> encontró el sub-formato "versión 2" completo y lo implementó (`EmoteCraftBinaryParser`, verificado
> byte-exacto). El sub-formato "versión 1" — 2 de los 3 archivos reales del usuario, incluyendo el
> primero que adjuntó — no tiene ningún ancla de texto legible para reverse-engineer con seguridad;
> adivinarlo podía reproducir el mismo bug de deformación por quinta vez, así que se documentó como
> blocker abierto (B082) en vez de arriesgar. El ÍCONO embebido sí se extrae para AMBAS versiones.
>
> **5. Íconos reales por emote** (no la letra fija): `EmoteIconProvider` — del PNG embebido en un
> `.emotecraft`, o de un `<nombre>.png` hermano de un `.json`.
>
> _(Todo lo de v0.48.0 y anterior abajo sigue vigente/pendiente de validar también.)_
>
> **NUEVO v0.48.0 — tercera ronda de feedback sobre la deformación del emote, con capturas (D084/B081):**
>
> **1. Deformación — causa raíz REAL #3, esta vez confirmada en la documentación oficial de
> Emotecraft, no inferida:** "la ubicación de cabeza/piernas/brazos es relativa a la ubicación Y
> ROTACIÓN del torso". El código aplicaba esos offsets como deltas planos, sin rotarlos por la
> rotación actual del torso — correcto solo mientras el torso está de pie sin girar. En cuanto un
> baile inclina o gira el torso (la mayoría lo hace), los miembros seguían colocándose en su offset
> "de pie", leyéndose como que se separan del cuerpo. Fix: los offsets de cada miembro ahora se rotan
> por la rotación ACTUAL del torso antes de sumarse. **Prueba clave:** reproduce bailes con movimiento
> de torso por 10-15s — ya no debería deformarse.
>
> **2. Transición de cámara:** al iniciar un emote desde 1ª persona, la cámara ahora se desliza hacia
> atrás (no corta de golpe) hasta 3ª persona; al terminar, se desliza de vuelta antes de volver a 1ª
> persona.
>
> **3. Preview animado ahora en los 3 lugares:** Biblioteca de emotes, editor de la rueda, y la rueda
> de gameplay — tu personaje se ve posado/animado en los tres, no solo texto.
>
> **4. `.emotecraft` reescrito** sin depender de ningún dialecto de glob (comparación manual de
> extensión) + logging de diagnóstico nuevo si aún así algún archivo no aparece.
>
> **5. Multi-rueda de emotes:** el editor ahora tiene botones para agregar/quitar/cambiar entre varias
> ruedas de emotes, igual que el menú radial normal (el controlador ya lo soportaba, solo faltaba la UI).
>
> **Nota de proceso:** dos herramientas de compilación estuvieron temporalmente no disponibles durante
> parte de esta sesión — se hizo una revisión manual línea por línea de todo el código antes de poder
> compilar, y ESO encontró un bug real (un método de conteo de espacios apuntaba a las ruedas
> RADIALES regulares en vez de a las de emotes) que se corrigió antes de la primera compilación. Vale
> la pena confirmar en la prueba que tu menú radial normal no perdió ningún espacio.
>
> _(Todo lo de v0.47.0 y anterior abajo sigue vigente/pendiente de validar también.)_
>
> **NUEVO v0.47.0 — segunda ronda tras probar v0.46.0 (D083/B080):**
>
> **1. Deformación del emote — la causa de D082 no bastaba, esta vez es la real.** Minecraft dibuja a
> TODOS los jugadores con un modelo COMPARTIDO (solo existen 2 instancias: brazos anchos/flacos),
> reposándolo para cada uno en su turno. El fix anterior capturaba el "reposo" la primera vez que
> UNA reproducción tocaba cada parte — pero si el modelo compartido ya venía tocado por OTRO emote
> (de otra entidad, o de un frame anterior), ese reposo capturado ya estaba corrupto, y cualquier
> jugador renderizado justo después de uno que emotea heredaba su offset — "las piernas se separan".
> Ahora el reposo se guarda en una caché aparte de CUALQUIER entidad que en ese momento NO esté
> emoteando (garantizado limpio) — se autocorrige solo. **Prueba clave:** reproduce un emote 10-15s
> seguidos, e idealmente con otro jugador visible al mismo tiempo — ya no debería deformarse ni
> "contagiarse".
>
> **2. Archivos `.emotecraft` ya se detectan** en la Biblioteca (antes solo se buscaba `.json` —
> confirmado en los docs oficiales de Emotecraft que el formato real siempre es `.json`; `.emotecraft`
> es solo una extensión renombrada por algunos sitios de la comunidad, mismo contenido). Esto también
> explica por qué "Actualizar" no mostraba nada nuevo si lo que agregaste era `.emotecraft`.
>
> **3. Teclado — lote de 6:** la selección ya no se ve hasta que mueves un stick, y desaparece sola
> tras ~1.4s sin tocarlo (D-pad no se ve afectado, sigue mostrando su resaltado siempre); "A" ahora
> presiona la tecla del stick que usaste MÁS RECIENTE (izquierdo o derecho); el hint "mover teclado"
> ya no se pierde del footer (se comprime el espacio entre hints en vez de cortar los últimos); los
> glifos de inventario se ocultan mientras el teclado está abierto; un flick corto del stick avanza
> exactamente una tecla (mantenerlo sigue igual que siempre, sin ningún retraso nuevo); y la velocidad
> del stick se rebasó a la mitad — si tenías 50% guardado, súbelo a 100% para sentir lo mismo de antes.
>
> **4. Preview animado en Biblioteca de emotes:** el panel derecho ahora muestra tu propio personaje
> posado/animado con el emote resaltado (antes solo texto), usando el mismo mecanismo ya optimizado
> que usa el inventario de Minecraft — nunca se activa si tienes un emote REAL corriendo (no le pisa
> la animación).
>
> **5. Third-Person, segunda pasada:** el usuario insistió en portar el mod completo. Se leyó a fondo
> `AbstractConfig.java` (todos los ~50 ajustes reales) — el free-look (mirar sin girar el cuerpo) es un
> problema de CÓMO EL INPUT MUEVE AL JUGADOR, no de cómo se posiciona la cámara; tocarlo significa
> reescribir la relación entrada→rotación en todo el mod. Se mantiene deliberadamente fuera. En cambio
> se amplió lo ya enviado: la transición de lado/offset ahora es suave (no instantánea), y un nuevo
> perfil de cámara de "apuntado" acerca la cámara automáticamente al cargar arco/ballesta/tridente.
>
> _(Todo lo de v0.46.0 y anterior abajo sigue vigente/pendiente de validar también.)_
>
> **NUEVO v0.46.0 — primera prueba real en hardware de FASE 63 (emotes) + Steam Input v0.43-0.45,
> lote de 9 (D082/B079).** El lag de v0.45.1 se confirmó AMBIENTAL (un reinicio de Bazzite lo arregló,
> no era el mod) y el mixin de emotes cargó sin crash — buenas noticias, sin código. Lo demás sí:
>
> **1. Deformación del personaje al emotear — causa raíz real:** `EmoteAnimator` sumaba el offset de
> posición sobre el pivote del modelo CADA FRAME sin una base de referencia — como vanilla nunca
> resetea ese campo por frame, se acumulaba sin límite mientras el emote seguía corriendo (a diferencia
> de las rotaciones, que sí sustituyen un valor absoluto). Ahora cada reproducción captura el pivote de
> reposo una sola vez y calcula siempre "reposo + delta", nunca "lo que ya había + delta". **Prueba:**
> reproduce un emote con offsets (varios de los 12 incluidos) por 5-10s seguidos — ya no debería
> deformarse progresivamente.
>
> **2. Cámara ahora vuelve sola a 1ª persona** al terminar el emote (antes se quedaba en 3ª persona para
> siempre). Si cambias de perspectiva tú mismo durante el emote, no te la fuerza de vuelta.
>
> **3. Rueda de emotes 100% independiente del menú radial** (antes compartía el cupo de ruedas y el
> estado de selección con tus ruedas normales — pedido explícito: "no deben compartir información").
> Ahora tiene su propia lista de config, su propio controlador y su propio overlay; abrir una nunca
> toca el estado de la otra. **Prueba:** confirma que LB/RB en tu radial normal ya no muestra la rueda
> de emotes como una página más.
>
> **4. Preview fijo del lado derecho** en la Biblioteca de emotes (ver captura del usuario), en el
> editor de la rueda, y en la rueda de gameplay — se actualiza solo con el foco/hover, sin necesitar
> pulsar ▶ cada vez. Silueta animada NO implementada esta sesión (quedó como "si se puede" pendiente).
>
> **5. Hallazgo grande de infraestructura: el logging de diagnóstico nunca se veía en producción.**
> `LogUtil.debug()` llamaba a un nivel que el Minecraft del jugador real descarta por defecto —
> ninguno de los "revisa el log por esta línea" de sesiones anteriores (incluido el de vibración de
> slime, hace 3 sesiones) pudo haber funcionado nunca. Ahora sí se ve. Esto es LA razón por la que
> los puntos 6 y 7 de abajo siguen sin resolverse — necesitan el próximo log real, no otro ajuste a
> ciegas.
>
> **6. Vibración de slime — sigue sin causa confirmada** (el código usa los mismos valores que el
> panel de prueba que sí se siente) — con el fix de logging de arriba, el PRÓXIMO log sí mostrará
> evidencia real.
>
> **7. Snap de Traveler's Backpack — sigue sin funcionar** pese al fix de v0.43 — logging nuevo y
> acotado agregado para saber exactamente por qué en la próxima prueba (REI sigue funcionando bien).
>
> **8. Detección de la Ally "solo con el 8BitDo conectado":** el código no filtra por reconocimiento
> de mando — muy probablemente un comportamiento de Bazzite/Steam (reclama el mando en exclusiva hasta
> que su propio subsistema "despierta"), no un bug del mod. Log de confirmación agregado.
>
> **9. NUEVO — "Mejor tercera persona":** toggle en Ajustes Globales, crédito a Leawind/Third-Person
> (MIT). Desplaza la cámara de 3ª persona hacia el hombro (Izquierda/Centro/Derecha + slider de
> distancia), sin atravesar nunca una pared — implementado como un ajuste ligero SOBRE la cámara ya
> calculada por vanilla, no como un reemplazo completo (el mod original desacopla la rotación de la
> cámara del cuerpo del jugador; ESO se dejó fuera a propósito por el riesgo en el subsistema con más
> historial de bugs de este proyecto — ver D082). **Prueba:** actívalo, prueba los 3 lados; si
> "Izquierda"/"Derecha" quedan al revés visualmente, es solo la etiqueta, no un fallo funcional —
> avisa cuál lado real corresponde a cuál botón.
>
> _(Todo lo de v0.45.1 y anterior abajo sigue vigente/pendiente de validar también.)_
>
> **NUEVO v0.45.1 — causa raíz real del lag "enorme" (D081/B078).** No estaba en el tick (el log lo
> confirmó — totales modestos): `onCursorPos` se disparaba SIN LÍMITE por cada frame renderizado
> mientras el stick movía el cursor virtual (re-ejecuta todo el hover/tooltip vanilla; con 184 mods
> puede ser caro por llamada). Ahora limitado a ~90 Hz — el punto dibujado sigue perfectamente fluido
> (no depende de esa llamada). Probar: mouse virtual y scroll en menús deben sentirse normales ya.
> Si el lag EN GAMEPLAY (jugando, no en menús) persiste, mandar log de esa sesión específica — el
> log anterior nunca capturó actividad de mundo, sigue sin evidencia ahí.
>
> **NUEVO v0.45.0 — FASE 63: sistema de EMOTES nativo (D080/B077).** Motor clean-room compatible con
> los .json de emotes de la comunidad (12 CC0 incluidos + lee `.minecraft/emotes` directo), rueda de
> emotes dedicada dentro del sistema radial (editor en Botones junto a "Configurar menú radial"),
> bind `EMOTE_WHEEL` (hold-select-release), biblioteca con búsqueda y ▶ preview, sync multijugador
> entre clientes SteamPad cuando el servidor tiene el mod. Probar según checklist B077 — el punto a
> vigilar en el PRIMER arranque es el mixin de render nuevo (`PlayerEntityModelMixin`).
>
> _(Todo lo de v0.44.0 abajo sigue pendiente de validar también — A y B primero.)_
>
> **v0.43.0 REPROBÓ en hardware con 2 regresiones. v0.44.0 las ataca así (D079/B076):**
>
> **A. 0 controles en Game Mode — causa: nuestro propio manifiesto IGA.** Auditoría multi-agente
> descartó por código TODA causa del lado del mod (claims, filtros, hints SDL, GLFW también vio 0 ⇒
> el SO no expuso ningún device). Con `game_actions_<appid>.vdf` desplegado, Steam trata el shortcut
> como juego nativo de Steam Input y DEJA DE emitir el gamepad virtual que alimenta SDL3/GLFW. Fix:
> `deployIgaManifest` nuevo, **OFF por defecto** — al arrancar, el mod BORRA los manifiestos que él
> mismo desplegó (por huella de contenido, nunca archivos ajenos). **Prueba: 1) jar 0.44.0, 2) abre
> el juego UNA VEZ en escritorio (corre la limpieza — busca `Removed SteamPad IGA manifest` en el
> log), 3) REINICIA STEAM, 4) Game Mode: si la plantilla del shortcut sigue vacía, elige plantilla
> "Gamepad" en ajustes de control de Steam → los pads deben volver.**
>
> **B. Nativas muertas — causa: customNativesPath apuntaba a `Steam/linux64` (ahí NUNCA existe
> libsteam_api.so; es redistributable por juego, verificado en el fuente de steamworks4j 1.9.0).**
> Fix: el path custom ahora es DESTINO DE EXTRACCIÓN de las nativas bundleadas (vía
> `SharedLibraryExtractPath`), con cascada de fallback interna — un path malo ya no mata Steam.
> Tu config actual funciona sin tocarla, pero lo limpio es VACIAR `customNativesPath` en global.json.
>
> **C. Lag masivo — sin pistola humeante en el código (4 agentes + lectura propia: tick path limpio).
> Aplicado paquete de mecanismo + forense:** (1) throttle del pump SDL (corría 3+ veces/frame; Bazzite
> subió SDL 3.2.30→3.4.0 entre sesiones — costo nativo ahora acotado a 250 Hz); (2) puntero del
> teclado ahora escala por TIEMPO REAL, no por tick — inmune a ticks lentos (era px/tick: cualquier
> inflación del tick lo arrastraba — el mecanismo exacto de "LENTISIMO"); (3) escáner de widgets:
> memo 250 ms + apagado mientras escribes; (4) **TickProfiler**: si el trabajo del mod pasa de
> 2 ms/s o un tick pica >25 ms, el log trae UNA línea `Tick profile (...)` con el desglose por
> sección — **si aún hay lag, manda el latest.log y esa línea nos dice el culpable exacto.**
>
> _(El checklist de v0.43.0 de abajo sigue vigente para probar Steam Input/overlay/teclado dual/
> TB/REI/vibración — pero solo DESPUÉS de que A y B den pads detectados y nativas OK.)_
>
> **0. Steam Input — CUARTA causa raíz encontrada (v0.43.0), y es la que explica el "no funcionó
> nada" de v0.42.0 con el panel en verde:** `ActivateActionSet` NUNCA se llamaba en el entorno real
> (Game Mode = control activo SDL3 = path fallback, y la única llamada estaba después del return de
> ese path). Sin set activo, Steam Input reporta CERO datos para toda acción — attach OK, handles OK,
> botones muertos. Ahora se activa cada tick desde `SteamSlotDispatcher` en ambos paths, y TODAS las
> acciones (saltar/atacar/sticks…) se puentean al gameplay híbrido. Además: `major_revision 3` en el
> VDF (Steam conservaba tu plantilla vieja construida contra los sets renombrados — por eso "sin
> botones asignados") y joystick_move/camera en LOS 4 SETS (por eso "no hay opción de joystick": el
> picker solo ofrece las acciones del set que estás editando, y Menú/Inventario solo tenían vmouse).
> Ver D078, B075.
>
> **Cómo probar (jar 0.43.0, Game Mode):** 1) copia el jar, arranca SIN tocar nada; 2) REINICIA STEAM
> UNA VEZ (relee controller_config y la revisión nueva del manifiesto) → el configurador debe mostrar
> los 4 sets y al editar un stick debe aparecer "Joystick izquierdo — Mover"/"Joystick derecho —
> Cámara" en TODOS los sets; 3) ata un paddle a "Saltar" → debe saltar YA (busca en el log
> `First live Steam Input action data received` — esa línea confirma TODO el pipeline); 4) ata sticks
> a las acciones de joystick → mover/cámara deben seguir funcionando (puente analógico nuevo).
>
> **1. Overlay de binds (nuevo):** mantén START ~½ segundo (gameplay o inventario) → aparecen TODOS
> los botones y chords asignados; al soltar vuelve a tu nivel (Mínimo/Normal/Completo). Tap corto de
> START = pausa igual que siempre (ahora se abre al soltar — imperceptible). Verifica que tus chords
> con START (si tienes) sigan sin doble-disparo.
>
> **2. Teclado dual-stick (nuevo, estilo Steam):** abre el teclado → dos bolitas semitransparentes
> (blanca = stick izq., azul = stick der.), cada stick mueve la suya con snap; LB presiona la tecla
> de la izquierda, RB la de la derecha, A sigue igual; caret ahora en L3/R3; las teclas se hunden al
> presionar. Toggle en Ajustes de teclado ("Teclado de dos joysticks") si lo prefieres clásico.
>
> **3. Traveler's Backpack / REI — quinto intento, ahora con DOS causas raíz reales distintas:** TB
> guardaba coordenadas relativas al panel (el escáner las trataba como absolutas — el snap apuntaba a
> la esquina de la pantalla); REI ni siquiera es alcanzable escaneando la pantalla (sus widgets viven
> en su propio runtime — nuevo puente por su API pública). Prueba: snap/D-pad al botón de la mochila
> entre dos casillas, y a la búsqueda/catálogo de REI.
>
> **4. Snap del mouse virtual:** ahora hay interruptor en Botones → sección Mouse Virtual.
>
> **5. Cambiar de mano:** ahora vive en Botones → Movimiento (junto a saltar/agacharse).
>
> **6. Vibración de inicio:** fix de mecanismo (stop explícito a los 100 ms + cooldown de 3 s contra
> ráfagas), no otro ajuste de número. Si TODAVÍA se siente larga, el tope duro de 100 ms lo descarta
> como bug del mod — sería el stack de streaming.
>
> **Pendiente previo que sigue vigente:** slime al caminar (log de diagnóstico esperando prueba,
> B070), debug dump formato (B071), DUP+Radial (B066).

---

## Última Actualización
**2026-07-16 (sesión 28 cont. 6 — v0.50.0: quinta ronda de feedback con 20 archivos `.emotecraft` reales adicionales (21 en total) y un bug nuevo de reconexión de mando. Los límites de seguridad del parser binario (D085) estaban calibrados con UNA sola muestra simple — con 21 muestras reales se recalibraron (tope de keyframes por canal 64→10,000, ventana de resincronización 512 bytes→sin límite), llevando los archivos que cargan de 1/3 a 12/21 verificado con test real contra el corpus completo. Bug de renderizado del ícono encontrado y corregido: `EmoteIconProvider` usaba un overload de `drawTexture` sin `regionWidth`/`regionHeight`, corregido para igualar el patrón ya probado en `ButtonIcon`/`ControllerBrandIcon`. Bug NUEVO y real (preexistente, no introducido esta sesión) encontrado: los archivos de config por-control se guardan por HANDLE numérico, pero SDL3 asigna un handle nuevo cada vez que el mismo mando físico se reconecta — un reconectar en gameplay creaba binds/rueda en blanco silenciosamente, solo "arreglable" reiniciando el juego por coincidencia; fix con migración de config por NOMBRE de control (`ConfigManager.migrateControllerConfigByName`, nunca sobrescribe config existente). Hallazgo colateral: los archivos `.emotecraft` de esta muestra vienen de una herramienta de terceros llamada "MineEmotes", no del exportador oficial de KosmX. Build + 29/29 tests → `dist/steampad-0.50.0.jar`. Ver D086, B083.)**

**2026-07-16 (sesión 28 cont. 3 — v0.47.0: segunda ronda de feedback tras v0.46.0. Causa raíz REAL de la deformación del emote (el modelo de jugador es compartido entre todas las entidades — solo 2 instancias existen — así que el reposo por-Playback de la sesión anterior podía heredar corrupción de otro emote; fix con caché estática por identidad de ModelPart); soporte de `.emotecraft` en la Biblioteca; lote de 6 en el teclado virtual (selección oculta hasta mover un stick + auto-oculta, "A" respeta el último stick usado, footer sin perder hints, glifos de inventario ocultos con teclado activo, gesto golpe-vs-mantener, rebase de velocidad); preview animado en Biblioteca de emotes; y segunda pasada de Third-Person con suavizado exponencial + perfil de cámara de apuntado, manteniendo el free-look deliberadamente fuera de alcance (es un problema de acoplamiento de entrada, no de cámara). Build + 29/29 tests → `dist/steampad-0.47.0.jar`. Ver D083, B080.)**

**2026-07-16 (sesión 28 cont. 2 — v0.46.0: lote de 9 tras la primera prueba real en hardware de FASE 63 (emotes) y de Steam Input v0.43-0.45. Fix de causa raíz de la deformación del personaje al emotear (acumulación sin base en el pivote del modelo); cámara vuelve sola a 1ª persona; rueda de emotes 100% desacoplada del menú radial (config/controlador/overlay propios); preview fijo del lado derecho en biblioteca/editor/gameplay; hallazgo grande — `LogUtil.debug()` nunca llegaba a producción (root logger INFO), lo que explica por qué el diagnóstico de slime nunca tuvo evidencia en 3 sesiones — ahora enruta a INFO; diagnóstico honesto (no fix a ciegas) de slime y Traveler's Backpack con logging nuevo; hallazgo de que la detección de la Ally "solo con el 8BitDo" es probablemente Bazzite/Steam, no el mod; y port acotado de Leawind/Third-Person (MIT) como "Mejor tercera persona" — offset lateral de cámara sin tocar el desacople cámara-cuerpo del mod original (fuera de alcance a propósito). Build + 29/29 tests → `dist/steampad-0.46.0.jar`. Ver D082, B079.)**

**2026-07-15 (sesión 27 — v0.43.0: CUARTA causa raíz de Steam Input — `ActivateActionSet` nunca se llamaba en el path híbrido (la única llamada estaba tras el early-return del fallback, que es el path real en Game Mode) → sin set activo, Steam reporta cero datos para TODA acción, con el panel en verde. Fix: activación por tick en `SteamSlotDispatcher` (ambos paths) + puente completo de acciones nombradas y sticks al gameplay híbrido (`ACTION_TO_BIND` → `VirtualBindInput` fuente ACTION; merge analógico por magnitud en dispatcher/cámara). VDF: `major_revision 3` (Steam conservaba la plantilla vieja del usuario tras el renombre de sets de v0.42.0 — el "sin botones asignados") + joystick_move/camera en los 4 sets (el picker solo ofrece las acciones del set en edición — el "no hay opción de joystick"). Nuevas features: overlay de todos los binds/chords con START sostenido ~500 ms (tap corto = pausa al soltar, compuesto con chords en ambas direcciones); teclado dual-stick estilo Steam (2 orbes con snap, LB/RB presionan, A intacta, caret en L3/R3, teclas que se hunden, toggle ON default); toggle del snap del mouse virtual (Botones → Mouse Virtual); Cambiar de mano movido a Movimiento. Fixes: Traveler's Backpack — 5º intento, causa REAL: coordenadas relativas al panel tratadas como absolutas (resolución de espacio por intersección con el rect del GUI, accessors bg verificados con javap); REI — sus widgets viven fuera de la pantalla (singleton REIRuntime): nuevo `ReiCompat` por reflexión a su API pública (overlay → children → getBounds), memo 90 ms en el escáner; vibración de inicio — fix de mecanismo: `connectRumble()` con stop explícito a 100 ms + cooldown 3 s (0.12/15 ms). Build + 24/24 tests → `dist/steampad-0.43.0.jar`.) Ver D078, B075.**

**Honestidad (v0.43.0):** la causa #4 está respaldada por lectura directa del código (call site inalcanzable) y la semántica documentada de Steam Input (sin set activo no hay datos) — máxima confianza sin hardware. El `major_revision` viene de la doc oficial de Valve para invalidar configs viejas; que Steam regenere la plantilla del shortcut tras UN reinicio es lo esperado pero solo el hardware lo confirma. El fix de TB está respaldado por su código fuente real (inButton resta guiLeft/guiTop); el puente de REI usa solo API pública verificada contra el fuente 1.21.9 y se auto-desactiva ante cualquier error. El teclado dual, el overlay de START y el stop de rumble compilan y pasan tests pero no se han sentido en un mando físico.

**2026-07-12 (sesión 26 cont. 20 — v0.42.0: TERCERA causa raíz de Steam Input encontrada en el log real — la decisión de conexión corría ANTES de cargar la config guardada, así que el "Siempre" del usuario revertía a NUNCA en cada arranque; manifiesto VDF rediseñado con 4 sets que coinciden con las capas del mod, acciones ordenadas, sticks con nombre claro, es/latam; activación de set por contexto real). Ver D077, B074.**

El usuario probó v0.41.0 en Game Mode: el fix del overflow FUNCIONÓ (log: `detected=4221053437`
positivo, manifiesto desplegado, Steam ya muestra los sets y todas las acciones — "parece que funcionó
después de unos dos reinicios"). Pero: (1) los botones adicionales M1/M2/PL/PR asignados a acciones no
reaccionaron en el juego; (2) no existe una opción de "joystick puro" al asignar sticks; (3) pidió que
los sets de Steam correspondan con las capas del juego (Menú/Jugabilidad/Inventario/Montado — "solo me
muestra dos, GUI Navigation e INGAME, no tiene sentido"); (4) pidió orden lógico en las acciones ("se
ve desordenado y no sé qué criterio utilizaste"); (5) preguntó si quitar las 10 ranuras o para qué
sirven; (6) pidió que los mandos vengan mapeados por defecto.

**Investigado con el log real (evidencia directa):**
- **Causa raíz #3 — orden de arranque:** el log muestra `Not attaching to Steam (steamAttachMode=NEVER,
  gamescope=true, launchedFromSteam=true)` a las 19:03:50 y `Global config loaded` DESPUÉS. En
  `SteamPadClient.onInitializeClient()`, `SteamBootstrap.init()` (paso 2) corría antes de
  `ConfigManager.loadAll()` (paso 3), y `getGlobal()` devolvía una instancia de DEFAULTS
  (steamAttachMode=NUNCA) hasta que loadAll corriera. El "Siempre" que el usuario guardó la sesión
  anterior nunca se leía al arranque — por eso el attach en vivo de v0.41.0 funcionó esa sesión (config
  ya cargada) pero cada reinicio revertía en silencio. Esto explica el punto (1) completo: sin
  conexión, los sets de acción jamás se activaban y ningún botón asignado en Steam podía reaccionar.
- **Punto (2):** el set "GUI Navigation" (donde estaba parado el editor en la captura) solo define la
  acción analógica `vmouse` (absolute_mouse) — no había NINGUNA acción de joystick puro en ese set, y
  el picker de Steam solo ofrecía emulaciones de mouse. El set In-Game sí tenía Move/Camera
  (joystick_move/joystick_camera) pero con títulos genéricos ("Move"/"Camera").

**Implementado (v0.42.0):**
- **Fix de orden de arranque (doble):** `ConfigManager.loadAll()` movido al paso 0 de
  `onInitializeClient()` (antes de nativos y Steam), y `getGlobal()` ahora carga el archivo real si se
  le llama antes de loadAll — nunca más defaults silenciosos (también corrige que `loadNatives`/
  `customNativesPath` se leyeran de defaults en el paso 1).
- **Manifiesto VDF rediseñado:** 4 sets (`SteamPad_Gameplay/Menu/Inventory/Mounted`) que espejan
  exactamente las 4 capas de ranuras del mod; acciones en orden lógico por set; sticks titulados
  "Joystick izquierdo — Mover"/"Joystick derecho — Cámara" (joystick_move/joystick_camera — la opción
  de joystick puro pedida); las 10 ranuras en TODOS los sets, etiquetadas "(se asigna en el mod)";
  localización english/spanish/latam. Copia suelta `steampad_steam_input/game_actions_480.vdf`
  sincronizada.
- **`SteamActionRegistry`:** 4 handles de set + `activateSetFor(Context)` con fallback (un VDF viejo de
  2 sets sigue funcionando: Inventario→Menú, Montado→Jugabilidad). `InputBindingManager` activa el set
  según `SteamSlotDispatcher.currentContext(mc)` — el mismo criterio de las capas.
- **Respuesta al punto (5):** las ranuras se QUEDAN — son el puente para paddles/botones extra: Steam
  las ve como acciones genéricas, y EN EL MOD (Botones → Steam Input) se les asigna cualquier acción
  interna (Menú Radial, Zoom...) o keybind, con asignación DISTINTA por capa. Ahora con "(se asigna en
  el mod)" en el nombre para que el propósito sea obvio dentro de Steam.
- **Respuesta al punto (6), honesta:** los bindings por defecto por tipo de mando se publican vía el
  sitio de partners de Steamworks (requiere AppID real de la tienda) — no existe mecanismo local
  documentado/confiable para un shortcut no-Steam. Mitigación aplicada: acciones ordenadas y
  autoexplicativas para que el usuario las ate una sola vez.
- `mod_version` → 0.42.0. Build + 24/24 tests → `dist/steampad-0.42.0.jar`.
- **Honestidad:** el fix de orden de arranque está respaldado por las líneas exactas del log (orden
  invertido de los dos mensajes) — confianza máxima. El VDF de 4 sets sigue el mismo formato del de 2
  sets que YA fue validado en hardware esta sesión (Steam lo mostró), así que el riesgo principal es
  de detalle (títulos/orden), no estructural. Sin probar en hardware: la activación por contexto en
  vivo (montarse a caballo → set Montado, etc.) y que el picker de sticks ahora ofrezca las acciones
  de joystick en el set Jugabilidad.

**2026-07-12 (sesión 26 cont. 19 — v0.41.0: DOS causas raíz reales de "Steam Input sigue sin conectar" encontradas en el log real del usuario — overflow de AppID a int negativo para todo shortcut no-Steam, y el cambio de modo de conexión que no aplicaba hasta reiniciar sin avisar. Más la reorganización de UI pedida: capas de ranuras a Botones, sección Steam Input propia bajo Backends). Ver D076, B073.**

El usuario probó v0.40.0 en Game Mode con capturas + el log completo: puso "Conectar a Steam: Siempre"
y el panel siguió en "no conectado"; el configurador de Steam no muestra las acciones del mod para
ninguno de sus dos mandos. También pidió reorganizar: los ajustes de Steam Input justo debajo de
Backends de entrada, y las capas de ranuras dentro de la sección Steam Input de Botones (donde ya viven
las 10 ranuras).

**Investigado con el log real (evidencia directa, no hipótesis):**
- **Bug 1 — overflow de AppID (el crítico):** el log muestra `SteamAppId=4221053437` y
  `detected=-73913859`. 4221053437 > `Integer.MAX_VALUE` (2147483647): los pseudo-AppIDs de shortcuts
  no-Steam se construyen como `crc32 | 0x80000000` — el bit alto SIEMPRE está activado, así que
  SIEMPRE desbordan un `int` con signo de Java. `SteamLaunchDetector.detectAppId()` devolvía el cast
  negativo, y todos los chequeos `> 0` río abajo fallaban: `launchedFromSteam=false` (por eso ni AUTO
  hubiera funcionado por esa vía), `resolveEffectiveAppId()` caía a 480, y el manifiesto VDF nunca se
  desplegaba para el AppID real del shortcut — exactamente por qué el configurador de Steam no muestra
  nada. Roto para el 100% del caso de uso del proyecto (Prism como shortcut no-Steam).
- **Bug 2 — el cambio de modo no aplicaba:** el log muestra `steamAttachMode=NEVER` a las 18:19:49
  (arranque) pero la captura del usuario muestra "Siempre" seleccionado — lo cambió en el juego, y la
  política solo se evaluaba UNA vez en `SteamPadClient.onInitializeClient()`. Nada volvía a intentarlo
  ni avisaba que hacía falta reiniciar.

**Implementado (v0.41.0):**
- **Pipeline de AppID convertido a `long` de punta a punta:** `SteamLaunchDetector.detectAppId()`,
  `EnvironmentReport.steamLaunchAppId`, `SteamBootstrap.resolveEffectiveAppId()`,
  `SteamControllerConfigDeployer.deploy()`. Nuevo helper `isShortcutPseudoAppId(long)` (bit alto
  activado) y `resolveSdkAppId()`: `steam_appid.txt` recibe un AppID REAL validable por el SDK (480
  para sesiones de shortcut — la misma combinación que usan herramientas como GlosSI), mientras que el
  VDF se despliega al pseudo-AppID del shortcut (el nombre de carpeta que Steam de verdad consulta
  para la config de mando del shortcut) ADEMÁS del 480 de siempre.
- **El cambio de modo de conexión aplica al instante:** `SteamInputSettingsScreen.applyAttachModeNow()`
  — a AUTO/SIEMPRE sin estar conectado llama `SteamBootstrap.init()` (ya era seguro de reintentar — el
  botón "Reintentar Steam" lo llama igual); a NUNCA estando conectado llama `shutdown()`. Ambos loguean
  el resultado.
- **Reorganización de UI:** sección propia "Steam Input" en Ajustes Globales justo debajo de Backends
  (solo el modo de conexión); las 3 capas se movieron a Botones → sección Steam Input, como filas
  nuevas (nuevo `Kind.LAYER` en `ActionCatalog`) justo después de las 10 ranuras, abriendo el mismo
  editor `SteamSlotLayerScreen` de antes.
- `mod_version` → 0.41.0. Build + 24/24 tests → `dist/steampad-0.41.0.jar`.
- **Honestidad:** ambos bugs están respaldados por el log real (números exactos), no por teoría — la
  confianza en el diagnóstico es la más alta posible sin hardware propio. Lo que sigue sin poderse
  confirmar desde aquí: que con el AppID correcto el configurador de Steam efectivamente muestre las
  acciones (depende de cómo Steam asocia manifiestos IGA a shortcuts — puede requerir reiniciar Steam
  para que relea `controller_config/`), y el riesgo de "seizure" de mandos al conectarse (advertencia
  vigente de D033/B040).

**2026-07-12 (sesión 26 cont. 18 — v0.40.0: Steam Input — causa raíz real de por qué nunca se conectaba (política de conexión defaulteaba a NUNCA, sin control en la UI), expuesta ahora en Ajustes Globales; ranuras de Steam Input ahora pueden apuntar a CUALQUIER acción interna de SteamPad (Menú Radial, Zoom incluidos), no solo keybinds externos; nuevas 4 capas de ranuras Menú/Jugabilidad/Inventario/Montado). Ver D074, D075, B072.**

El usuario probó v0.39.0: confirmó que el panel de prueba de haptics funciona excelente en los 26
botones, y reportó 3 cosas: (1) el botón "Copiar debug" de Ajustes Globales parecía haber
desaparecido; (2) Steam Input sigue sin conectar, ni en escritorio ni en Game Mode, y recuerda que
antes SÍ funcionaba — pidió resolverlo YA, profundizar por completo en Steam Input para que funcione
"100% como un AAA", y preguntó cómo usarlo; (3) marcó como LO MÁS IMPORTANTE que las ranuras de Steam
Input no pueden asignarse a Menú Radial ni Zoom, pidiendo que CUALQUIER cosa pueda asignarse — y pidió
explícitamente varios "layers" de Steam Input auto-detectados en el juego (Menú, Jugabilidad,
Inventario, Montado) con posibilidad de asignar cualquier cosa desde las ranuras.

**Investigado con evidencia real (código, no suposición):**
- **Punto 1 (debug button):** revisado `GlobalSettingsScreen.java` línea por línea — el botón
  `"steampad.settings.copy_debug"` sigue ahí, en la sección Avanzado, sin ningún cambio de código que
  pudiera romperlo. No se encontró ninguna causa de regresión — la hipótesis más probable es un jar
  desactualizado instalado, o no haber hecho scroll hasta el final (la sección se movió más abajo tras
  agregarse el slider de intensidad del radial en v0.37.0 y ahora el botón de Steam Input en este
  lote). Sin cambios de código; se pide confirmación del jar/scroll antes de seguir investigando.
- **Punto 2 (Steam Input) — causa raíz 100% real, encontrada leyendo `SteamBootstrap.java` y
  `GlobalConfig.java`:** `GlobalConfig.steamAttachMode` (la política que decide si el mod se conecta a
  Steam en absoluto) **defaultea a `NEVER`** — y ese campo NUNCA estuvo expuesto en ninguna pantalla
  del mod, solo editable a mano en el archivo JSON de config. Con `NEVER`, `SteamBootstrap.init()`
  jamás intenta conectarse a Steam sin importar la plataforma (escritorio o Game Mode) — coincide
  exactamente con lo que mostró la captura del usuario ("no conectado (escritorio: entrada cruda)") y
  con que "ni en Game Mode funciona". Esto explica también el recuerdo del usuario de que antes SÍ
  funcionaba: casi seguro estaba lanzando el juego DESDE Steam en ese entonces (lo cual, con el
  default AUTO que existía antes de esta sesión de arreglos, sí disparaba la conexión), y en algún
  punto el default cambió a NEVER como parte de la política D033/B040 (evitar que Steam le quite el
  control a SDL3 en escritorio) sin agregar nunca una forma de que el usuario lo reactivara desde la
  UI si lo necesitaba. Ver D074.
- **Punto 3 (ranuras solo aceptan keybinds externos):** confirmado leyendo `SteamSlotDispatcher.java`
  y `KeybindPickerScreen.java` — el selector de objetivo de una ranura solo enumeraba
  `client.options.allKeys` (keybinds vanilla/de mods, objetos `KeyBinding` reales). Menú Radial, Zoom
  y el resto de las ~19 acciones internas de SteamPad NO son `KeyBinding`s — son valores del enum
  `GamepadBinds.Bind`, despachados a mano dentro de `GamepadInputDispatcher` — así que nunca podían
  aparecer en ese selector, sin importar qué se intentara. Ver D075.

**Implementado (v0.40.0):**
- **`steamAttachMode` expuesto en la UI:** Ajustes Globales → nuevo botón "Ajustes de Steam Input…" →
  `SteamInputSettingsScreen` con un control cíclico AUTO/SIEMPRE/NUNCA y una descripción honesta de
  la contrapartida (conectarse puede hacer que Steam le quite un control gestionado por Steam a la
  entrada cruda de SDL3 — el motivo real por el que el default es NUNCA).
- **`VirtualBindInput` (nuevo):** permite que una fuente no física (una ranura de Steam Input) dispare
  cualquier `GamepadBinds.Bind` exactamente como si fuera un botón físico — se conecta como un OR
  adicional al inicio de `GamepadInputDispatcher.bHeld()`/`bPressed()`, sin tocar ni una línea de la
  lógica existente de gating físico (supresión de menú, chords, D-pad del zoom) — cero riesgo de
  regresión para los binds físicos, que siguen exactamente igual. Esto es lo que hace que CUALQUIER
  acción interna del mod (no solo Menú Radial y Zoom, las ~19) sea asignable a una ranura sin
  duplicar la lógica de cada acción una por una.
- **Nuevo selector `SteamSlotTargetPickerScreen`:** al asignar una ranura, ahora se puede elegir entre
  "Acciones de SteamPad" (todos los binds internos, con su nombre traducido) o "Atajos de teclado" (el
  selector de siempre) — ambos con buscador. Se guarda como `"bind:NOMBRE"` o el id de siempre,
  100% retrocompatible (ninguna asignación existente cambia de significado).
- **4 capas de ranuras** (`SteamSlotDispatcher.Context`: GAMEPLAY/MENU/INVENTORY/MOUNTED), cada una
  con su propio mapa global en `GlobalConfig` (`steamInputSlots` = Jugabilidad, sin tocar; 3 mapas
  nuevos para las otras). El contexto se recalcula cada tick según si hay pantalla abierta (de
  inventario → Inventario, cualquier otra → Menú) o si el jugador va montado — si cambia de capa
  mientras una ranura sigue físicamente presionada, se libera lo anterior y se re-resuelve al vuelo
  bajo la nueva capa (sin necesidad de soltar y volver a presionar). 3 nuevas pantallas
  `SteamSlotLayerScreen` (una por capa nueva) para asignarlas, accesibles desde
  `SteamInputSettingsScreen`.
- `mod_version` → 0.40.0. Build + 24/24 tests → `dist/steampad-0.40.0.jar` (confirmado tras una
  interrupción temporal del servicio de herramientas que retrasó este paso, no un problema de código).
- **Honestidad:** el diagnóstico de `steamAttachMode` y de las ranuras-solo-keybinds está respaldado
  por lectura directa del código real — confianza alta en ambos. Lo que NO se puede confirmar sin
  hardware: si activar AUTO/SIEMPRE realmente conecta a Steam Input en el entorno específico del
  usuario (Steam corriendo, AppID, VDF), y si el "seizure" de controles que D033/B040 documentaron
  como motivo del default NUNCA se repite con la configuración actual del usuario — por eso la
  advertencia explícita en la descripción del ajuste. Las 4 capas de ranuras son código nuevo sin
  validar en hardware; el diseño se pensó para no romper NADA de la capa Jugabilidad existente (mismo
  campo, mismo comportamiento si las 3 capas nuevas quedan vacías).

**2026-07-12 (sesión 26 cont. 17 — v0.39.0: panel de prueba de haptics (26 efectos disparables bajo demanda) + debug dump ampliado con estado de haptics y widgets detectados por pantalla — herramientas de diagnóstico pedidas explícitamente por el usuario tras la investigación de v0.38.0). Ver D073.**

El usuario pidió explícitamente construir las dos ideas de herramientas mencionadas al cierre de
v0.38.0: un panel de prueba de haptics y una ampliación del debug dump, y que se le explicara cómo
usarlas.

**Implementado (v0.39.0):**
- **`HapticsController.TEST_PRESETS`**: lista de 26 `HapticPreset` (tier/categoría/intensidad/
  duración/freqBalance) que espejan cada efecto real del archivo — valores exactos para los efectos
  fijos, valores representativos de rango medio para los que dependen de datos en vivo (distancia de
  caída, daño recibido, cercanía). `HapticsController.testFire(preset)` los dispara ignorando el
  cooldown/ocupación del canal (limpia `activeTier` primero) para que una prueba manual nunca se
  descarte en silencio, pero SÍ sigue respetando "Permitir vibración" y los multiplicadores de
  categoría — lo que se siente en la prueba es representativo de lo que producirían esos mismos
  sliders en el juego real.
- **Nueva pantalla `HapticsTestScreen`**: lista los 26 presets como botones, agrupados en 4 secciones
  por categoría (Jugador/Mundo/Interacción/Interfaz), usando la misma infraestructura de
  `ColumnSettingsScreen` que el resto del mod (scroll, navegación con D-pad, panel de descripción a la
  derecha) — sin código de navegación nuevo. Accesible desde Ajustes Avanzados del control, botón
  "Panel de prueba de haptics" junto al "Probar vibración" ya existente.
- **Debug dump ampliado** (`SteamRuntimeDiagnostics.generateDump()`): nueva sección `-- Haptics --`
  (tier que ocupa el canal ahora mismo + ms restantes vía nuevos getters
  `HapticsController.occupyingTier()`/`occupiedForMs()`, `allowVibration` + los 7 multiplicadores del
  control activo, estado del toggle+slider del radial) y `-- Screen Widgets --` (clase de la pantalla
  abierta, tamaño de `children()`, y la lista completa — hasta 20, con contador de "y N más" — de
  objetivos que `ExternalWidgetScanner` encuentra ahí, marcando cada uno como `ClickableWidget` real o
  `duck-typed`, con sus coordenadas).
- `mod_version` → 0.39.0. Build + 24/24 tests → `dist/steampad-0.39.0.jar`.
- **Honestidad:** ambas herramientas son puramente aditivas — no tocan ningún camino de disparo real de
  haptics ni de navegación, así que el riesgo de regresión es mínimo. Sin probar visualmente en
  hardware todavía (que el panel efectivamente vibre al presionar cada botón, que el debug dump se vea
  bien formateado).

**2026-07-12 (sesión 26 cont. 16 — v0.38.0: investigación real (no más ajustes a ciegas) de 3 bugs reportados como "sigue sin funcionar" — Traveler's Backpack (causa raíz confirmada leyendo su código fuente real: usa su propio sistema de botones, nada que ver con Minecraft), vibración de inicio (dos llamadas de rumble nunca tocadas en rondas anteriores), y diagnóstico agregado para el slime (4 intentos fallidos, ahora con logging para saber si dispara o no). Ver D071, D072, B070.**

El usuario probó v0.36.0/v0.37.0 y reportó que 3 de los 5 puntos SIGUEN sin funcionar pese a los fixes
anteriores, y explícitamente cuestionó si la investigación estaba siendo rigurosa ("estas seguro que
estas investigando bien, parece que no esta surgiendo efecto") sobre el botón de Traveler's Backpack —
marcado como LA PRIORIDAD MÁS ALTA ("no puedo seleccionar botones de otros menus que no sean los
nativos, y eso es muy molesto"). También aportó un dato de calibración real: en el menú radial, por
debajo de 40% de intensidad no se siente nada; pidió usar ese dato para el slime también. Y pidió la
vibración de inicio aún más corta, más corta que el botón "Probar mando" de ajustes.

**Investigado con evidencia real, no adivinando (este lote):**
- **Traveler's Backpack — causa raíz 100% confirmada leyendo su código fuente real** (rama
  `1.21.10-fabric` del repo oficial `Tiviacz1337/Travelers-Backpack`, mismo patrón de rigor que la
  investigación de REI en D063): `BackpackScreen` mantiene `public List<IButton> buttons`, y `IButton`
  (implementado por `EquipButton`/`MoreButton`/`UnequipButton`/`SleepingBagButton`/
  `AbilitySliderButton`) es una interfaz COMPLETAMENTE propia del mod — sin relación alguna con
  `ClickableWidget` de Minecraft. El escáner anterior (`ExternalWidgetScanner`, D068/D069) solo
  reconocía objetos `ClickableWidget`: encontraba el campo `buttons` (es una `List`, candidato válido)
  pero descartaba cada botón dentro porque ninguno era del tipo esperado — exactamente por qué "no
  surgía efecto" pese a que la lógica de conexión (D069) sí estaba bien encaminada. Esto explica
  también la queja más amplia del usuario ("otros menús que no son los nativos" en general, no solo
  esta mochila) — CUALQUIER mod con un sistema de botones propio, no heredado de Minecraft, tenía el
  mismo problema. Ver D071.
- **Vibración de inicio — causa raíz real, no un problema de calibración.** Había DOS llamadas de
  rumble adicionales en `SteamPadClient.java` (reconexión del mando preferido, auto-activación tras una
  desconexión) que quedaron hardcodeadas en el valor viejo (0.45f/80ms) mientras las 3 rondas previas
  de ajuste solo tocaban la llamada de "primera activación de la sesión" — de ahí que "no cambiara pese
  a varios cambios": el usuario probablemente sentía la llamada que nunca se tocó. Unificadas las 3 en
  una sola constante compartida. Ver D072.
- **Vibración de slime — diagnóstico en vez de un 4º ajuste a ciegas.** 0.3 de magnitud (el valor
  actual) es la MITAD de la intensidad del botón "Probar mando" (que el usuario confirma que SÍ se
  siente fuerte) — si 0.3 no se siente en absoluto (no "débil", sino NADA), es mucho más probable que
  el pulso nunca se esté disparando que un problema de magnitud. Se agregó logging de diagnóstico en
  vez de cambiar el número una cuarta vez. Ver D072.

**Implementado (v0.38.0):**
- `ExternalWidgetScanner` reescrito: ya no requiere `ClickableWidget` — reconoce por duck-typing
  cualquier objeto con campos numéricos `x`/`y`/`width`/`height` (o `w`/`h`) en su jerarquía de clases,
  sin importar el tipo/interfaz que implemente. `SlotSnap`/`WidgetSnap` usan estos objetivos
  únicamente por posición (nunca necesitan invocar el método de clic propio del mod — la activación ya
  pasa por un clic real del cursor virtual). `GuiFocusNavigator` sigue exigiendo un `ClickableWidget`
  real (necesario para el sistema de foco de Minecraft) — sin cambios de riesgo ahí.
- 3 llamadas de rumble de conexión en `SteamPadClient.java` unificadas en `CONNECT_RUMBLE_INTENSITY`
  (0.15f) / `CONNECT_RUMBLE_MS` (20).
- Logging de diagnóstico agregado a `tickSquishyUnderfoot()` (throttled a 1/seg): confirma si el
  jugador está en el suelo, la velocidad horizontal, si `canFire(AMBIENT)` permite el pulso, y si el
  pulso realmente se dispara.
- `radialSelectHapticsIntensity` default 100%→40% (piso medido por el usuario en hardware real).
- `mod_version` → 0.38.0. Build + 24/24 tests → `dist/steampad-0.38.0.jar`.
- **Honestidad:** el fix de Traveler's Backpack está respaldado por evidencia real (código fuente
  leído, no teoría) y el de vibración de inicio también (bug de código concreto encontrado), así que
  la confianza en ambos es alta. El de slime NO tiene una causa confirmada todavía — el logging
  agregado es exactamente para obtener esa evidencia en la siguiente prueba, en vez de repetir el
  patrón de "cambiar un número y esperar" que ya falló 3 veces.

El usuario confirmó que el punto 6 (v0.36.0) todavía estaba probándolo, y de paso pidió ajustar la
vibración de selección del menú radial (v0.34.0/D067): la sensación en sí "quedó genial", pero la sintió
un poco fuerte y pidió (1) bajarla un poco, (2) un slider para poder graduarla, y (3) que fuera un poco
más breve.

**Implementado (v0.37.0):**
- **Vibración de selección del menú radial, segunda pasada:** intensidad base 0.08→0.05, duración
  35ms→25ms — sin tocar el resto de la señal (sigue `Tier.COSMETIC`/`Category.GUI`, sigue
  disparándose solo en cambio de selección, no por tick).
- **Nuevo slider de intensidad propio:** `GlobalConfig.radialSelectHapticsIntensity` (10%-150%,
  default 100%), en Ajustes Globales justo debajo del interruptor de encendido/apagado — multiplica la
  intensidad base antes de que `fire()` aplique el multiplicador de categoría GUI existente, así que el
  usuario tiene control fino independiente de esa vibración específica sin afectar el resto de las
  señales de categoría GUI.
- `mod_version` → 0.37.0. Build + 24/24 tests → `dist/steampad-0.37.0.jar`.
- **Honestidad:** compila limpio y pasa los tests; sin probar en hardware todavía — es un ajuste de
  magnitud/duración sobre una señal que el usuario ya confirmó que se siente bien, así que el riesgo de
  que "no se sienta" es mucho menor que con el slime, pero sigue siendo una estimación sin mando físico.

El usuario probó v0.35.0 y confirmó que NO funcionó: mandó una captura de un botón de Traveler's Backpack
que queda entre dos casillas del inventario — el snap sigue enganchando siempre las casillas vecinas
antes que ese botón, y aclaró que pasa "en otros menús que no son del juego" (inventarios con casillas
de otros mods en general, no solo ese). También pidió una tercera reducción de la vibración de inicio.

**Implementado (v0.36.0):**
- **Causa raíz real del fallo de v0.35.0: la detección nunca se conectó a las pantallas CON casillas.**
  `ExternalWidgetScanner` (v0.35.0) se conectó a `WidgetSnap` y `GuiFocusNavigator` — los caminos que
  usan las pantallas SIN casillas (menús normales). Pero el snap dentro de un inventario real (con
  casillas, como una mochila de Traveler's Backpack) usa un sistema completamente aparte, `SlotSnap`,
  que ya combinaba casillas + `screen.children()` como candidatos de snap — solo que nunca conocía los
  widgets EXTERNOS a `children()` (el mismo patrón de REI). Un botón de mod que vive fuera de
  `children()` nunca era ni siquiera un candidato ahí, así que las casillas vecinas ganaban siempre por
  descarte, no por estar más cerca. `SlotSnap.targets()` ahora también agrega los widgets de
  `ExternalWidgetScanner.discover(screen)` a la lista de candidatos — se decide por la MISMA distancia
  que ya se usa para casillas y botones normales, así que un botón de mod puede ahora ganarle el snap a
  una casilla vecina si está genuinamente más cerca del cursor.
- **Vibración de inicio — tercera reducción:** de 45ms/0.22 (v0.34.0) a 25ms/0.15 — un toque apenas
  perceptible.
- `mod_version` → 0.36.0. Build + 24/24 tests → `dist/steampad-0.36.0.jar`.
- **Honestidad:** compila limpio y pasa los tests, pero sin probar en hardware ni contra el jar real de
  Traveler's Backpack — sigue dependiendo de la misma premisa de D068 (que el mod guarda su botón en un
  campo/lista/mapa directo de la pantalla), y aunque ahora el botón SÍ es candidato de snap, no hay
  garantía de que gane la distancia frente a las casillas en todos los layouts posibles — si el usuario
  reporta que sigue sin ganar en un caso concreto, el siguiente paso sería revisar si ese caso en
  particular necesita un sesgo explícito hacia widgets sobre casillas, no solo distancia pura.

El usuario preguntó qué necesitaba del punto 6 pendiente (auditoría de snap) y aclaró el alcance real:
quiere que se DETECTEN los botones que agregan los mods, en inventarios y otros menús — no una auditoría
manual mod por mod, sino una solución genérica.

**Implementado (v0.35.0):**
- **Nuevo `ExternalWidgetScanner`:** encuentra widgets `ClickableWidget` que una pantalla posee pero
  nunca registra en `screen.children()` — el patrón de REI y cualquier mod basado en Architectury API
  (su barra de búsqueda, botones de filtro, etc., que se dibujan y reciben clics por su propio sistema
  de eventos, no por la lista de hijos de Minecraft — ver D063). Escanea por reflexión los campos
  propios de la clase de la pantalla (y un nivel dentro de campos `List`/array/`Map`, el patrón típico
  de "lista privada de widgets" que usan estos mods) buscando cualquier valor asignable a
  `ClickableWidget`, descartando lo que ya está en `children()`. La lista de campos reflectivos se
  cachea por clase (`Map<Class<?>, List<Field>>`) — el costo por tick es solo `Field.get()`, no un
  escaneo reflectivo nuevo cada vez. Nunca lanza: cualquier fallo de reflexión simplemente reduce los
  resultados, ya que esto es una mejora de navegación, no una dependencia dura (el clic en sí ya
  funcionaba desde D063).
- **`WidgetSnap.nearest()`** ahora también considera estos widgets externos para el imán del cursor
  virtual con el stick — antes solo miraba `screen.children()`.
- **`GuiFocusNavigator.navigables()`** ahora también los incluye para la navegación espacial con D-pad
  — pueden recibir foco y aparecer como destino de movimiento igual que un botón normal de Minecraft.
- **`GuiFocusNavigator.activate()` (botón A) distingue el origen del widget enfocado:** si es un widget
  normal (`children()`), sigue llamando a `mouseClicked()` directamente, sin cambios — cero riesgo para
  todo lo que ya funcionaba. Si es un widget externo (mod-owned, fuera de `children()`), en vez de
  llamar a su `mouseClicked()` directamente (lo que saltaría el sistema de eventos propio del mod, ej.
  Architectury), posiciona el cursor virtual en su centro y dispara un clic real vía
  `VirtualMouseController.simulateLeftClick()` — la misma ruta ya probada que hizo que el clic en REI
  funcionara en D063, así que para el mod es indistinguible de un clic real del usuario.
- `mod_version` → 0.35.0. Build + 24/24 tests → `dist/steampad-0.35.0.jar`.
- **Honestidad:** compila limpio y pasa los tests, pero SIN PROBAR en hardware ni contra el jar real de
  REI — la reflexión asume que estos mods guardan sus widgets como campo directo, en una `List`/array, o
  como valores de un `Map` (los tres patrones más comunes); si un mod anida sus widgets un nivel más
  profundo (ej. una lista dentro de otro objeto contenedor, no directamente en un campo de la pantalla),
  ese caso específico no se alcanza — se limitó la profundidad a propósito para no arriesgar
  rendimiento ni recorrer grafos de objetos arbitrarios sin límite.

El usuario probó v0.33.0 y dio 4 puntos: (1) el slime aún no se siente al caminar (sí al brincar) —
tercer intento pedido; (2) confirmó que el Warden quedó perfecto; (3) reportó un bug de regresión: DUP no
abre el Menú Radial si se mantiene presionado, solo al soltar (y pidió auditar chords en general, snap
del mouse virtual/D-pad en mods como Traveler's Backpack, confirmar aislamiento de configuración por
control, y una vibración breve al seleccionar en el menú radial); (4) pidió reducir aún más la vibración
de inicio. El usuario avisó explícitamente estar corto de tokens, así que este lote priorizó los puntos
concretos y acotados sobre las dos auditorías abiertas (chords y snap), que se dejaron pendientes de
alcance en vez de intentarse a ciegas.

**Implementado (v0.34.0):**
- **Fix de regresión: DUP (o cualquier botón usado como modificador de chord) rompía binds "hold to
  open" que comparten ese botón, como Menú Radial.** Causa raíz: el mecanismo `tickChordModifierGate`
  (creado en un lote anterior para que un botón-modificador no dispare también su acción simple) se
  generalizó sin acotarlo por tipo de bind. Menú Radial es `held=true` pero también usa `bPressed()` para
  su propio flanco de apertura además de `bHeld()` para permanecer abierto — el gate le aplicaba
  "diferir hasta soltar" igual que a un bind de un solo toque, y por eso se abría y cerraba de inmediato
  al soltar en vez de quedarse abierto mientras se mantiene. Arreglado acotando el gate a binds
  `held=false` únicamente en `GamepadInputDispatcher.bPressed()` — cualquier otro bind "hold to open" que
  comparta botón con un modificador de chord (Sprint, Zoom, Lista de jugadores, Ataque, Usar) queda
  cubierto por el mismo fix, no solo Radial.
- **Vibración de slime al caminar — tercer intento.** Los dos intentos previos (v0.32.0: pulsos cada
  90ms; v0.33.0: cada tick ~45ms) seguían sin sentirse en hardware real pese a compilar limpio. Este
  intento sube de tier (COSMETIC → AMBIENT, por debajo del impacto de caída) y calca la cadencia del
  vuelo con élitros — el único efecto continuo confirmado como perceptible en este proyecto — en vez de
  seguir ajustando intensidad a ciegas: intervalo 150ms, duración de pulso 200ms (se solapan entre sí),
  magnitud 0.3.
- **Vibración de inicio más corta.** De 90ms/0.4 (ya reducido en v0.33.0) a 45ms/0.22 — un toque
  apenas perceptible de "estoy vivo" en vez de un zumbido notorio.
- **Nueva vibración haptic al seleccionar en el menú radial (gameplay).** Pulso muy breve y suave
  (35ms, intensidad base 0.08 antes de multiplicadores) cada vez que cambia la casilla seleccionada —
  tanto por movimiento del stick como por `navigate()`. Activo por defecto, con su propio interruptor en
  Ajustes Globales ("Vibración al seleccionar en menú radial") independiente de los multiplicadores de
  categoría existentes.
- **Confirmado sin cambios de código:** el aislamiento de configuración por control ya era correcto —
  `ConfigManager` cachea `ControllerConfig`/`BindingConfig`/`RadialConfig` en mapas separados, cada uno
  indexado por el handle del controlador (`computeIfAbsent(handle, ...)`), nunca compartido entre
  controles distintos.
- **Explícitamente NO abordado este lote (presupuesto de tokens del usuario):** auditoría completa de
  chords (todas las combinaciones/escenarios) y auditoría de snap del mouse virtual + alcance del D-pad
  en mods de terceros con estructura tipo REI (ej. Traveler's Backpack) — ambos son pedidos grandes y
  abiertos que conviene acotar con el usuario antes de emprenderlos, no intentos parciales a ciegas.
- `mod_version` → 0.34.0. Build + 24/24 tests → `dist/steampad-0.34.0.jar`.
- **Honestidad:** todo compila limpio y pasa los 24 tests automatizados, pero NINGUNO de los puntos de
  este lote (fix de Radial, tercer intento de slime, vibración de inicio, haptic de radial) se ha
  probado aún en hardware físico — especialmente crítico para el tercer intento de slime dado que los
  dos anteriores fallaron en la prueba real pese a compilar bien.

El usuario probó v0.32.0 y confirmó que casi todo funciona bien, con 2 excepciones: (1) la vibración de
slime al caminar sigue sin sentirse en absoluto — pidió que fuera constante, "como cuando estás en algo
pegajoso", y aclaró que esto es solo para slime, no para miel; (2) golpear al Warden no detiene su
vibración de cercanía, y preguntó si eso lo había tocado — mencionó que estaba en modo creativo, por si
era relevante.

**Implementado (v0.33.0):**
- **Vibración de slime — segundo intento, esta vez realmente continuo.** El primer intento (v0.32.0:
  pulsos cada 90ms) seguía siendo demasiado débil/espaciado para sentirse en un mando real. Ahora
  dispara en CADA tick del juego (~50ms) con una duración de pulso de 140ms — más larga que el hueco
  entre ticks — así que las llamadas al motor se superponen entre sí y producen un zumbido realmente
  continuo (no solo "muy seguido" en la lógica interna), con una intensidad más alta y perceptible.
  Acotado estrictamente a slime — la miel se quedó exactamente como estaba antes, sin tocar.
- **El Warden ahora sí detiene su vibración al golpearlo.** Causa raíz real, sin relación con el modo
  creativo: el sistema que hace que otros jefes se callen al golpearlos NUNCA incluyó al Warden — su
  vibración de cercanía siempre vivió en un sistema completamente aparte (diseñado así a propósito en
  una sesión anterior, para que se sintiera "opresivo" de forma continua). Ahora tiene su propio
  interruptor independiente que responde exactamente igual: se calla al golpearlo, y se reinicia si te
  alejas sin matarlo.
- `mod_version` → 0.33.0. Build + 24/24 tests (rerun forzado) → `dist/steampad-0.33.0.jar`.
- **Honestidad:** ambos puntos compilan limpio y se verificaron paso a paso, pero la vibración de slime
  en particular depende de cómo el motor real del mando interprete comandos superpuestos muy seguidos —
  algo que no se puede confirmar sin hardware físico.

El usuario dio 6 puntos: (1) el glifo del nuevo bind "Abrir teclado" no aparecía en el HUD de inventario;
(2) pidió un chord DUP+RT para mover el teclado arriba/abajo (para no tapar la caja de texto de otros
mods como REI), con su propio glifo, en todos los lugares del teclado, y que en el chat la caja de texto
vuelva a su posición si el teclado sube; (3) bug del editor radial: al eliminar una rueda con atajos
mientras hay otras vacías, borraba la que TENÍA contenido — pidió poder elegir cuál eliminar y que
pregunte si tiene atajos; (4) el slime solo vibraba al brincar/rebotar, pidió que también vibre (más
suave) al caminar, de forma continua; (5) la vibración de jefes seguía sin detenerse con daño a
distancia (flechas); (6) la vibración no se detenía al abrir el menú de pausa.

**Implementado (v0.32.0):**
- **Glifo de "Abrir teclado" en inventario.** El HUD de inventario solo dibujaba hints fijos
  (A/B/X/Y/Select); ahora también consulta el bind configurable `OPEN_KEYBOARD` y lo muestra si el
  jugador lo asignó.
- **Reposición del teclado (DUP+RT).** Nuevo estado en `VirtualKeyboard` que mueve el panel completo
  (footer, teclas, todo) arriba o abajo de la pantalla. RT sin DUP sostenido sigue siendo Enter — solo
  se activa la reposición si DUP está sostenido en el momento de presionar RT. Glifo agregado a la fila
  de atajos del teclado, visible siempre que esté abierto. El chat ya no empuja su caja de texto si el
  teclado está arriba (con el teclado fuera del camino, no hace falta moverla).
- **Selector de rueda a eliminar (bug real corregido).** El botón ✕ del editor radial borraba
  ciegamente la rueda que estuviera abierta en ese momento, sin importar si tenía contenido o no —
  confirmado que así se podía borrar una rueda CON atajos mientras una vacía se quedaba intacta.
  Ahora abre una pantalla nueva que lista todas las ruedas (etiquetadas "vacía" o "N atajos"), el
  jugador elige cuál eliminar, y si esa rueda tiene contenido pide confirmación antes de borrar nada.
- **Vibración de slime al caminar.** El tick existente ya vibraba al caminar sobre slime/miel, pero
  con 280ms entre pulsos se sentía como golpecitos espaciados. Ahora el intervalo es de 90ms con
  pulsos de 80ms (casi sin hueco entre ellos) — se lee como un zumbido bajo y continuo, más suave que
  rebotar o aterrizar sobre el bloque.
- **Vibración de jefes con daño a distancia.** El mod YA recibía la notificación de red de que
  cualquier entidad cercana tomó daño (así vanilla muestra la animación de golpe en mobs y otros
  jugadores) pero la descartaba si no era el propio daño del jugador. Ahora también revisa si el
  jugador fue quien causó ese daño a un jefe — cubre flechas y cualquier otro daño a distancia, no
  solo golpes cuerpo a cuerpo.
- **Vibración se detiene en el menú de pausa.** El mundo se congela al pausar, pero los temporizadores
  de vibración usan el reloj real (que sigue corriendo), así que el ping de jefes seguía sonando.
  Ahora, al abrir el menú de pausa, se corta cualquier vibración en curso de inmediato y no vuelve a
  sonar nada hasta cerrar el menú.
- `mod_version` → 0.32.0. Build + 24/24 tests (rerun forzado) → `dist/steampad-0.32.0.jar`.
- **Honestidad:** todo compila limpio y se verificó paso a paso. Sin probar visualmente en hardware
  real todavía.

---

Con REI ya confirmado funcionando para clics, el usuario dio 3 puntos de seguimiento: (1) pidió abrir el
teclado en el inventario con un chord+A ya que REI no lo abre solo, y de paso mencionó que los chords en
general no bloquean la acción simple de su propio botón modificador; (2) detalló el bug concreto: DDOWN=
Tirar y DDOWN+A=Chat disparan AMBAS acciones al mantener DDOWN+A, cuando deberían convivir sin pisarse;
(3) el cursor virtual no hace snap a nada dentro de REI, y señaló que es importante para otros mods con
la misma estructura.

**Implementado (v0.31.0):**
- **Fix real de chords — causa raíz encontrada.** Ya existía protección para "un botón es el trigger de
  dos binds distintos", pero NINGUNA protección cubría "este botón es el trigger simple de UN bind Y
  el modificador (chord) de OTRO" — exactamente el caso DDOWN=Tirar / DDOWN+A=Chat. Ya existía además
  la técnica correcta para resolver esto ("diferir a la liberación"), pero solo implementada a mano para
  un único caso (el chord Select+RB del splitscreen). Generalizada a TODOS los chords configurados por
  el usuario: el botón modificador ahora "recuerda" si completó algún chord durante ese hold, y solo si
  NO lo hizo dispara su propia acción simple, al soltarlo (no al presionarlo) — el mismo patrón, ahora
  para cualquier combinación que el usuario configure en Botones.
- **Apertura manual del teclado en inventarios.** Nuevo bind configurable "Abrir teclado (inventario)"
  (sin valor por defecto — el usuario elige su propia combinación en Botones) que fuerza el teclado
  virtual a abrirse en cualquier inventario, sin depender de que SteamPad detecte un campo de texto.
- **REI — límites investigados y documentados, sin fix a ciegas.** Tanto "empujar" la caja de búsqueda
  de REI como hacer que el cursor virtual se "enganche" a sus ítems comparten la misma causa raíz ya
  documentada la ronda anterior: el buscador y los ítems de REI viven completamente fuera del sistema
  de pantalla normal de Minecraft, así que SteamPad no puede saber dónde están. Se investigó si REI
  expone alguna forma pública (no interna) de consultar esa información, y sí existe en teoría — pero
  sin poder instalar el REI real para verificarla con certeza, se prefirió documentar el camino a
  seguir en vez de escribir código especulativo que podría fallar en silencio.
- `mod_version` → 0.31.0. Build + 24/24 tests (rerun forzado) → `dist/steampad-0.31.0.jar`.
- **Honestidad:** verificado que existe otro sistema de chords en el proyecto (para Steam Input) que
  es completamente independiente del que se corrigió aquí — no hay duplicación, es el sistema correcto
  para el camino que el mando realmente usa en este entorno. Sin probar visualmente en hardware real.

---

El usuario dio 2 puntos: (1) el fix de la ronda anterior (A con D-pad en Selección de Mundo mueve el foco
a los botones de abajo) funciona, pero aterriza en "Borrar" en vez de "Jugar mundo seleccionado" — riesgo
real de borrar un mundo sin querer; (2) compartió el repo real de Roughly Enough Items
(github.com/shedaniel/RoughlyEnoughItems) y pidió investigar su código para lograr compatibilidad de
INTERACCIÓN (no integración) — que el mouse virtual pueda hacer clic y se pueda escribir en su buscador,
cuidando no chocar con las teclas ya asignadas del inventario.

**Implementado (v0.30.0):**
- **Selección de Mundo — fix real.** El código anterior elegía el botón más cercano geométricamente a la
  lista, que resultó ser "Borrar" en el layout real de la pantalla — impredecible y peligroso. Ahora
  busca el botón por su TEXTO traducido ("Jugar al mundo seleccionado", funciona en cualquier idioma),
  con el pick geométrico como respaldo solo si ese botón no aparece.
- **REI — causa raíz 100% confirmada con el código fuente real** (antes solo se pudo investigar por
  búsquedas indirectas, sin certeza). REI usa Architectury API para recibir clics/tecleo
  (`ClientScreenInputEvent`), que Architectury implementa con mixins que interceptan la llamada a
  `Screen.mouseClicked/keyPressed/charTyped` DESDE DENTRO de `Mouse.onMouseButton`/`Keyboard.onKey`/
  `onChar` — como el mouse y teclado virtual del mod llamaban a la pantalla directamente (saltándose
  esos métodos), REI (y Architectury en general) nunca se enteraba de nada.
- **Fix real, no solo para REI.** El mouse y teclado virtual ahora pasan por los métodos REALES de
  `Mouse`/`Keyboard` (el mismo patrón que ya usaba `ActionExecutor` para otro caso) — esto arregla clic
  y tecleo para CUALQUIER mod construido sobre Architectury, no es un parche solo para REI.
- **Límite real encontrado y documentado, no arreglado a la fuerza.** El buscador de texto de REI vive
  completamente fuera del sistema de pantalla de Minecraft (confirmado leyendo el código de REI) — el
  teclado virtual no puede detectar automáticamente que tiene el foco para abrirse solo. Se evaluó una
  solución basada en "si REI está instalado, cualquier inventario cuenta" pero se descartó: habría hecho
  que el botón A abriera el teclado en CUALQUIER inventario en vez de agarrar objetos — un riesgo real
  de romper algo que sí funciona, por un caso que no se puede confirmar sin instalar REI de verdad.
- `mod_version` → 0.30.0. Build + 24/24 tests (rerun forzado) → `dist/steampad-0.30.0.jar`.
  `validateAccessWidener` (Loom) confirmó que las firmas nuevas del accesswidener son correctas.
- **Honestidad:** el mecanismo se verificó leyendo el código fuente REAL de REI y de Architectury (no
  supuesto ni copiado a ciegas), pero sigue sin poder probarse contra el REI real instalado — eso solo
  lo puede confirmar el usuario.

---

El usuario dio 5 puntos de feedback: (1) la vibración de cercanía de jefes suena todo el combate en vez
de callarse tras el primer golpe; (2) el aim assist debería funcionar también sin mover el stick derecho,
como en shooters AAA (BF6, COD); (3) pedido de íconos más grandes en la rueda radial + un slider en
Apariencia; (4) los chords de dos botones se muestran en el HUD como solo el botón principal (bug real,
con ejemplo concreto: DUP+A para chat se veía igual que A de saltar); (5) agregar X a los glifos de
Normal.

**Implementado (v0.29.0):**
- **Vibración de jefes — enganche pegajoso.** La condición que silenciaba el ping ("cerca y a la vista")
  se re-evaluaba cada poll (~0.2s) — cualquier micro-corte de línea de visión en plena pelea la reactivaba.
  Nuevo `Set<Integer> engagedBosses`, pegajoso (mismo patrón `retainAll` que otros trackers de esta
  clase): una vez el jefe entra al set (por cercanía+visión, o por el primer golpe de melee — nuevo
  parámetro `target` en el mixin de ataque), el ping se calla el resto de la pelea; solo se reinicia si
  el jefe sale del rango de ping. Documentado el mismo riesgo de máscara en el pulso del Warden (Tier
  DANGER sobre golpes reales del Warden a Tier IMPACT) como principio a aplicar a futuro si se reporta —
  no tocado esta ronda por ser diseño deliberado de una sesión anterior.
- **Aim assist AAA — causa real era un `return` temprano, no solo el diseño de `magnetism()`.**
  `CameraController.update()` cortaba toda la función (incluida la llamada al aim assist) en cuanto el
  stick derecho estaba quieto. Nuevo "sticky lock" en `AimAssistController`: un timer de 350ms que se
  arma con stick activo O con el retículo ya casi centrado en el objetivo; mientras vive, el magnetismo
  tira con una magnitud fija más suave que la de stick activo. Imita el "target sticking" de COD/BF —
  se engancha un momento, se suelta si dejas de seguir, se re-arma si sigues.
- **Íconos de rueda radial escalables**, independientes del tamaño del chip (círculo de fondo, ya
  configurable). Nuevo `RadialConfig.iconScale` (default 1.4, escala visible por defecto) aplicado con
  matriz de escala alrededor del centro del chip, sin tocar los 3 proveedores de ícono. Nuevo slider en
  Apariencia.
- **Fix de chords en el HUD de gameplay.** Confirmado que la pantalla de Botones ya mostraba los chords
  correctamente — el bug estaba solo en `GameplayHudOverlay`, que nunca consultaba el chord de un bind,
  solo su botón principal. Ahora dibuja `[chord]+[principal]` cuando corresponde. Además, cualquier bind
  con chord se promueve de nivel Completo a Normal (más difíciles de recordar, más visibilidad).
- **X (Cambiar de mano) promovido a Normal.**
- `mod_version` → 0.29.0. Build + 24/24 tests (rerun forzado con `--rerun`) → `dist/steampad-0.29.0.jar`.
- **Honestidad:** todo compila limpio, verificado paso a paso con `gradle compileJava`. Sin probar
  visualmente en el juego real todavía — el aim assist en reposo y el enganche pegajoso de jefes son los
  de mayor riesgo de sentirse distinto a lo esperado sin poder calibrar con mando real.

---

El usuario probó v0.27.0 y dio 5 puntos de feedback: el flash de Splitscreen "regresó", la sombra negra
del efecto de presión se sale del glifo (con captura de pantalla del D-pad), pedido de un slider para
escalar los glifos, Selección de Mundo debe requerir una segunda A antes de entrar al mundo cuando se
navega con D-pad, e investigar por qué REI no responde al mouse virtual ni al teclado.

**Implementado (v0.28.0):**
- **Splitscreen — no es una regresión.** `grep` confirmó cero cambios de código entre v0.26.0 y v0.27.0
  en las rutas de ventana — el lote anterior solo tocó assets y el efecto de presión. Sigue siendo la
  misma limitación conocida documentada en D059. Sin cambio de código.
- **Fix real de la sombra del efecto de presión.** El rectángulo `ctx.fill` original ignoraba la
  silueta real del PNG (por eso un D-pad no-cuadrado mostraba una sombra rectangular). Reemplazado por
  el overload de 13 argumentos de `DrawContext.drawTexture` con un `color` tint final (encontrado con
  `javap -c`, desensamblado de bytecode) que solo tiñe los píxeles que la textura realmente dibuja.
  Aplicado en `ButtonIcon` y `ControllerGlyphs`.
- **Slider de escala de glifos** (Ajustes Globales → HUD, 50%-200%, reutiliza i18n huérfano que ya
  existía en los 3 idiomas). Alcance deliberadamente acotado a HUD de gameplay + hints de inventario +
  rueda radial — NO a las pantallas de ajustes (Botones/Avanzado/teclado virtual), que tienen columnas
  de ancho fijo y arriesgarían recortes al escalar.
- **Selección de Mundo con D-pad.** `GuiFocusNavigator.activate()` ahora detecta específicamente
  `SelectWorldScreen`: si la lista de mundos tiene foco y selección, A mueve el foco al botón más
  cercano por debajo en vez de unirse directo (misma lógica espacial que ya usa `moveDir`); una segunda
  A activa ese botón normalmente. El mouse virtual no pasa por esta rama, así que un clic directo sigue
  entrando sin cambios.
- **REI investigado, sin fix de código.** Confirmado con certeza que "REI Plugin Compatibilities" NO es
  una API de mando (es un shim de compatibilidad JEI→REI, sin relación con controladores). La causa
  raíz exacta de por qué el mouse virtual/teclado no responden dentro de REI quedó sin confirmar — sin
  jar de REI disponible localmente para `javap`, y GitHub code search requiere cuenta autenticada. Sin
  cambio de código a ciegas; documentado como bloqueador abierto con 3 preguntas de diagnóstico
  concretas para cuando haya acceso al jar real o a hardware.
- `mod_version` → 0.28.0. Build + 24/24 tests (rerun forzado con `--rerun`, no solo caché up-to-date) →
  `dist/steampad-0.28.0.jar`.
- **Honestidad:** puntos de código (2-4) compilan limpio, verificado con `gradle compileJava` en cada
  paso. Sin probar visualmente en el juego real todavía (no hay forma de lanzar el cliente completo en
  este entorno de desarrollo).

---

El usuario colocó arte nuevo en `import_buttons/` (una carpeta por marca — 8bitdo, generic, ps, steam,
xbox, xbox_elite — con 35 PNG cada una, más una carpeta `icon/` con el ícono nuevo del mod) y pidió
actualizar todos los glifos, agregar un efecto de presión visual, y actualizar el ícono en todos los
lugares necesarios.

**Implementado (v0.27.0):**
- **198 glifos nuevos** (33 por marca × 6 marcas) copiados a `textures/buttons/<marca>/`, mapeando los
  nombres MAYÚSCULA del export contra el `stemFor()` fijo que ya usa `ButtonTextureManager`. Un archivo
  venía mal nombrado en el export (`RB-1.png`) — inspección visual directa de la imagen confirmó que en
  realidad es el glifo de `RT` antes de copiarlo, no fue una suposición a ciegas.
- **`controller.png` (silueta de marca) NO se tocó** — las 6 imágenes del import resultaron ser idénticas
  byte a byte a las que ya existían (verificado con `stat` antes de copiar nada), así que copiarlas
  habría sido un no-op. Queda documentado que la silueta de marca sigue con el arte anterior.
- **Ícono del mod actualizado** — única referencia real en todo el repo es `fabric.mod.json`, ya
  corregida.
- **Efecto de presión nuevo:** `GamepadInputDispatcher.isPhysicallyHeld(id)` (lectura del snapshot que
  el dispatcher ya mantiene, sin costo extra) aplicado en `ButtonIcon` (Botones/radial/pestañas/teclado
  virtual) y `ControllerGlyphs` (HUD de gameplay) — el glifo baja 1px + overlay oscuro translúcido
  mientras el botón está físicamente presionado, sin afectar el ancho devuelto por `draw()` (cero riesgo
  de que un layout salte al presionar un botón).
- `mod_version` → 0.27.0. Build + 24/24 tests → `dist/steampad-0.27.0.jar` (2.6MB, arriba de 1.6MB por
  el arte nuevo más rico). Verificado que el jar empaqueta el arte correcto (tamaños de archivo dentro
  del jar coinciden con los del import, no con los placeholders viejos).
- **Honestidad:** solo se verificó por inspección de los PNG de origen y del contenido del jar final —
  no se probó visualmente en el juego real todavía (no hay forma de lanzar el cliente completo en este
  entorno de desarrollo, ver sesiones anteriores).

---

**2026-07-11 (sesión 26 cont. 4 — v0.26.0: bug sistémico de nombres duplicados en TODOS los controles cíclicos + auditoría de i18n + investigación del flash de Splitscreen + aim assist para mods de armas). Ver D059, B058.**

El usuario dio 4 puntos de feedback sobre v0.25.0. Uno de ellos ("Detalle de glifos en juego: Detalle de
glifos en juego: Normal") resultó ser un bug SISTÉMICO — investigado con `javap -c` (desensamblado de
bytecode, no solo firmas): `CyclingButtonWidget` de vanilla YA antepone el nombre de la opción
automáticamente; el helper compartido `ColumnSettingsScreen.cycling()` (usado por Block Reach Around,
Sneak/Sprint Mode, Gyro Behaviour, marcador de zoom, y prácticamente todo control cíclico del mod) TAMBIÉN
lo anteponía manualmente — duplicando el nombre en TODO control cíclico desde que se escribió, sin que
nadie lo hubiera notado. Fix de una línea en el código compartido resuelve todos los casos a la vez.

**Implementado (v0.26.0):**
- Fix del bug sistémico de duplicación (arriba).
- Auditoría de i18n: panel de diagnóstico completo de `ControllerSelectScreen` (nunca traducido, ~35
  claves nuevas ×3 idiomas) + 2 claves ya existentes pero nunca conectadas al código
  (`steampad.controller.status.active/.connected`, notificaciones de batería baja/debug copiado).
- Flash de Splitscreen investigado a fondo — confirmado que ningún hook de Fabric API disponible puede
  eliminarlo (la ventana de Minecraft ya es visible, con la pantalla de carga, antes de que cualquier mod
  pueda intervenir) — documentado como limitación conocida, sin fix riesgoso sin hardware para probar.
- Aim assist: investigados mods populares de armas — confirmado que "Ranged Weapon API" (Fabric,
  11.8M+ descargas) ya está cubierto por el chequeo genérico de v0.25.0; TaCZ (el mod de armas de fuego
  más popular) usa un sistema propio no detectable así, sin integración a ciegas sin su jar para
  verificar firmas (misma disciplina de javap que el resto del proyecto). Descripción del ajuste
  actualizada con el alcance real y honesto.
- Boost de render distance: tope del slider bajado de 16 a 8 chunks + descripción reforzada sobre el
  costo real de rendimiento (confirmado por el usuario que el fix de la sesión anterior sí funciona).
- `mod_version` → 0.26.0. Build + 24/24 tests → `dist/steampad-0.26.0.jar` (compiló a la primera).
- **Honestidad:** nada de este lote se probó en hardware todavía.

---

**2026-07-11 (sesión 26 cont. 3 — v0.25.0: feedback de hardware sobre v0.24.0 reveló y corrigió 2 bugs reales (boost de render distance, detección de entidades), más flash de ventana, seguimiento/contorno de marcador, barras cinemáticas, rebase de sensibilidad, aim assist genérico y 3 fixes de UI). Ver D058, B057.**

El usuario probó v0.24.0 y dio 10 puntos de feedback. Dos de ellos ("no lo veo más allá de mis chunks
simulados" y "las entidades no se marcan") no eran pedidos de mejora — eran features del lote anterior que
NO FUNCIONABAN en absoluto. Investigación con javap encontró la causa real de ambos antes de tocar código:

- **Boost de render distance:** `SimpleOption.setValue()` solo cambia el valor local — nunca avisa al
  servidor. Hacía falta llamar `GameOptions.sendClientSettings()` explícitamente (algo que el código de
  zoom nunca hacía). Además, el servidor integrado de un jugador solo tiene cargados los chunks dentro de
  `simulationDistance` — subir solo `viewDistance` no sirve de nada si esos chunks ni existen del lado del
  servidor. Fix: sube ambas distancias juntas y llama `sendClientSettings()`.
- **Marcar entidades:** el primer intento usaba `ProjectileUtil.raycast` con una caja de búsqueda calculada
  con `Box.stretch` — nunca encontró nada en la prueba real del usuario (un zombie directo en la mira).
  Reescrito con un enfoque manual y directamente verificable: candidatos vía `World.getOtherEntities`
  (mismo patrón que ya usa `AimAssistController`) + `Box.raycast(eyePos, endPos)` por candidato.

**Implementado (v0.25.0):**
- Fix real de ambos bugs de arriba (puntos 2 y 3 de D058).
- Fix del flash de apertura de Splitscreen: hook adicional en `ScreenEvents.BEFORE_INIT` (Fabric API),
  corre antes del primer frame del bucle de render — antes solo se aplicaba en el primer client tick,
  que ya sucede después de al menos un frame en la posición vieja.
- Marcador de entidad con seguimiento en vivo (recalcula posición cada tick, ya no es una foto fija) +
  contorno vía `Entity.setGlowing(true)` (el sistema de brillo de vanilla, sin render propio).
- Barras cinemáticas nuevas: dos rectángulos negros que cierran hacia el centro al hacer zoom y se abren
  al soltar, velocidad fija (~0.3s) independiente del FOV configurado. Toggle + slider de altura.
- Rebase de sensibilidad: nuevo `ControllerConfig.SENSITIVITY_REBASE = 0.65f` aplicado en el punto de
  consumo (`CameraController` + `InputBindingManager`) — 1.0 en el slider ahora entrega lo que antes
  entregaba 0.65, en cualquier punto del rango, sin migrar el esquema de guardado.
- Aim assist: además de `UseAction.BOW/CROSSBOW/SPEAR` (vanilla), ahora también activa si el ítem es
  `instanceof RangedWeaponItem` — cubre arcos/ballestas moddeados que extienden esa clase vanilla para
  heredar la mecánica de carga. Limitación honesta: armas moddeadas con disparo 100% custom siguen sin
  señal detectable.
- Fix: la barra de scroll ahora se puede arrastrar con el mouse en TODAS las pantallas con scroll
  (`SteamPadBaseScreen` ganó `mouseClicked`/`mouseDragged`/`mouseReleased` — antes era puramente visual).
- Fix: en Botones, el texto/glifo de una fila parcialmente scrolleada ya no queda "flotando" sin fondo
  en los bordes de la lista — usaba un criterio de visibilidad distinto (menos estricto) que el widget
  subyacente.
- Panel de diagnóstico de Selección de Control colapsado por defecto (una línea: "Working via: ...");
  click para expandir a las 6 líneas completas.
- `mod_version` → 0.25.0. Build + 24/24 tests → `dist/steampad-0.25.0.jar` (2 errores de compilación
  reales atrapados en el primer intento — `Entity.getPos()`/`getWorld()` no existen con esos nombres,
  son `getEntityPos()`/`getEntityWorld()` — corregidos antes de continuar).
- **Honestidad:** nada de este lote se probó en hardware todavía. Dado que dos features del lote ANTERIOR
  resultaron no funcionar en absoluto pese a "build + tests OK", el peso de la prueba real recae en esta
  próxima sesión de hardware — se documentó explícitamente en el checklist qué observar para confirmar
  que los fixes esta vez sí funcionan (no solo que compilan).

---

**2026-07-11 (sesión 26 cont. 2 — v0.24.0: feedback de hardware sobre Splitscreen (v0.23.0 "funciona sorprendentemente bien") + investigación de splitscreen REAL dejada pendiente como proyecto experimental futuro). Ver D056, D057, B056, P001.**

Dos hilos en esta continuación de sesión. Primero, el usuario preguntó qué tan factible sería splitscreen
REAL (viewports simultáneos, cuentas registradas/invitado, hasta 4 jugadores) — investigación profunda con
WebSearch + javap contra el jar mapeado (ver D056): técnicamente posible (el servidor local ya soporta N
jugadores vía `ClientConnection.connectLocal`) pero el cliente es un singleton de punta a punta
(`mc.player`/`KeyBinding` estático), con alto riesgo de romper con el modpack real de ~80 mods del usuario.
Probabilidades estimadas: PoC vanilla ~65-70%, con el modpack completo ~20-25%, producto pulido ~10-15%.
**El usuario decidió dejarlo pendiente como proyecto experimental a futuro**, no como trabajo de sesión —
documentado en TODO_BLOCKERS.md → P001 (sección nueva "Proyectos Futuros", separada de bloqueadores
activos) con el plan de fases completo por si se retoma.

Segundo, el usuario probó v0.23.0 en Bazzite escritorio y dio 6 puntos de feedback (ver D057 para el
detalle completo de cada decisión):

**Implementado (v0.24.0):**
- **Persistencia del layout de Splitscreen entre sesiones** — antes se reseteaba a Ventana normal en
  cada lanzamiento a propósito (D055); el usuario pidió lo contrario. Nuevo `GlobalConfig.windowArrangeMode`
  persistido, actualizado en cada ciclo. `WindowArrangeController` gana `onFirstTick()` (reaplica el
  último layout si el toggle seguía activo de la sesión anterior) y `setEnabled(bool)` (activar captura
  un "baseline" de la ventana — fullscreen o no, y sus bounds — que desactivar restaura exactamente,
  en vez de un Ventana-normal genérico).
- **Fix del hueco reportado en la primera transición (Ventana→Izquierda):** diagnóstico por inspección
  de código — `GLFW_DECORATED` se aplicaba DESPUÉS de mover/redimensionar la ventana; reordenado para
  aplicarse ANTES. Hipótesis fuerte (coincide con el patrón exacto del reporte: solo en la primera
  transición decorada→sin-decorar), sin confirmar en hardware todavía.
- **Detalle de glifos del HUD movido de per-controller a Ajustes Globales** — el usuario lo pidió porque
  es una preferencia de cuánta información satura la pantalla, no algo que deba variar por mando.
  `ControllerConfig.ButtonGuideDetail` eliminado; el enum y el campo ahora viven en `GlobalConfig`.
- **Marcador de zoom más brillante** — escala de partícula 1.2→2.0 y spawn cada tick (antes cada 2).
- **Boost temporal de render distance mientras se hace zoom (feature nueva, opt-in, default 0/apagado)**
  — el usuario preguntó si se podía "hackear" ver más lejos durante el zoom sin subir su render distance
  normal (8 chunks) todo el tiempo. Nuevo slider en Avanzado → Zoom: sube `mc.options.getViewDistance()`
  temporalmente mientras se está zoomeando, restaura al soltar.
- **Marcador de zoom sobre entidades/mobs (feature nueva)** — `ZoomController.placeMarker` ahora
  también hace un raycast contra entidades vivas (`ProjectileUtil.raycast`, el mismo helper de las
  flechas vanilla) y marca la más cercana entre entidad y bloque. Snapshot de posición, no sigue al mob.
- `mod_version` → 0.24.0. Build + 24/24 tests → `dist/steampad-0.24.0.jar`.

**Confirmado en hardware esta sesión (no hace falta re-probar):** B050 puntos 1-2 (pad de la Ally + fix
del cursor loco al reconectar el 8BitDo) ✅; B053 (niveles de detalle del HUD, color del marcador) ✅.
Sigue abierto sin fix: marcador en escaleras — sin reproducción concreta, documentado como límite conocido
de la heurística `markerPos.up().isAir()` (a nivel de BlockPos, no de forma real/VoxelShape). Steam Input
pospuesto explícitamente por el usuario ("cuando tengamos listo esto lo retomamos") — B054/B040 sin cambios.

---

**2026-07-11 (sesión 26 — v0.23.0: integrado el mod pcal43/splitscreen como feature "Splitscreen" de acomodo de ventana, con toggle en Ajustes Globales y chord de mando Select+RB. Respondida (sin código) la pregunta del usuario sobre splitscreen REAL dentro del mod). Ver D055, B055.**

El usuario pidió integrar `https://github.com/pcal43/splitscreen` — leído el código real del repositorio
(no solo la descripción) antes de programar: el mod NO hace splitscreen dentro del juego, solo reposiciona
y redimensiona la ventana del sistema operativo vía GLFW (10 layouts: ventana normal, 4 mitades, 4
esquinas, pantalla completa) — pensado para acomodar varias instancias de Minecraft en pantalla para
multijugador local. El propio usuario ya lo había identificado así ("en realidad es un acomodo de
pantalla"). Confirmado que esto NO choca con la Restricción Inamovible 2 de CLAUDE.md ("Sin splitscreen
dentro del mod") porque no toca render/cámara/sesión de red, es pura geometría de ventana.

**Implementado (v0.23.0):**
- Algoritmo de los 10 layouts reimplementado limpio en `WindowArrangeMode` (enum, paquete
  `client/window/`) — no se vendorizó el código del mod original; se portó la lógica de posicionamiento
  con atribución MIT en el javadoc (Copyright pcal.net).
- `WindowAccessor` (mixin `@Accessor`, mismo patrón ya usado por `HandledScreenAccessor`) expone los
  campos privados de `net.minecraft.client.util.Window` (`x/y/width/height`,
  `windowedX/Y/width/height`) — nombres verificados con javap contra el jar mapeado 1.21.10 antes de
  escribir código. El flag `fullscreen` NO se toca directo: entrar/salir de pantalla completa pasa por
  el método público `Window.toggleFullscreen()` para no romper el estado interno de vanilla (que además
  rastrea un segundo flag `currentFullscreen`). Mixin puramente accessor, cero lógica — Restricción 4.
- `WindowArrangeController` (clase plana): ciclo de modos, bounds del monitor vía `getMonitor()`/
  `getCurrentVideoMode()` (públicos en 1.21.10, sin widener), reposición real vía
  `GLFW.glfwSetWindowMonitor` + `glfwSetWindowAttrib(GLFW_DECORATED,...)`. No se auto-posiciona al
  arrancar (a diferencia del mod original) — solo actúa cuando el usuario usa el chord con el feature
  activo. El modo actual vive en memoria y se resetea a Ventana normal en cada lanzamiento; solo el
  interruptor on/off y el espacio entre ventanas (gap) se persisten en `GlobalConfig`.
- **Chord Select(BACK)+RB, gesto global hardcodeado (NO un `GamepadBinds.Bind` rebindable)** — mismo
  criterio que el gesto ya existente de Select-solo (cicla el cursor virtual en menús). El sistema de
  chords existente solo suprime la acción del botón GATILLO cuando otro bind apunta ahí con un chord;
  nunca cubrió el caso inverso (un botón usado como MODIFICADOR que también tiene su propia acción
  base — BACK dispara PERSPECTIVE en juego y cicla el cursor en menús). Resuelto con hold-to-modify:
  mientras el feature está activo, BACK no dispara su acción normal en el PRESS — si RB se presiona
  durante el hold, cicla la ventana y la acción normal de BACK queda suprimida para ese hold; si BACK se
  suelta sin RB, dispara su acción normal en ESE tick (release en vez de press, demora de máximo un
  tick, imperceptible). Con el interruptor DESACTIVADO (default), cero cambio de comportamiento — la
  rama nueva de código ni se ejecuta.
- Ajustes nuevos en Ajustes Globales → sección "Splitscreen": interruptor (default OFF, mismo patrón
  conservador que `steamAttachMode=NEVER`) y slider de espacio entre ventanas (0–16px, default 1px).
  i18n ×3 (en/es-MX/es-ES).
- **Splitscreen REAL (pregunta del usuario, respondida en el chat, sin código):** viewports simultáneos
  dentro de una sola instancia (con o sin segunda cuenta) es un choque directo con la Restricción 2 y una
  tarea de escala completamente distinta — exigiría reescribir el pipeline de render (múltiples
  cámaras/viewports por frame), el input (asume un solo jugador local en todo el proyecto) y la conexión
  de red (`ClientPlayNetworkHandler` vanilla asume una sola sesión por proceso). Ningún mod Fabric
  conocido lo implementa por esta razón — la solución real de la comunidad siempre es "varias instancias
  + acomodo de ventana", que es justo lo que este lote entrega. Si el usuario quiere explorarlo en serio,
  haría falta revisar la Restricción 2 explícitamente (como se hizo con la Restricción 1 en D032) y
  tratarlo como su propio proyecto, no un incremento de sesión.
- `mod_version` → 0.23.0. Build + 24/24 tests → `dist/steampad-0.23.0.jar`.
- **Honestidad:** todo el lote es código nuevo sin probar en hardware. Se intentó `gradle runClient` en
  este entorno de desarrollo como verificación adicional; falló por una causa PREEXISTENTE y no
  relacionada con este cambio (el Fabric Loader 0.16.14 cacheado en este sandbox es incompatible con
  Fabric API 0.138.4, que pide loader ≥0.17.0 — el crash ocurre en la resolución de mods, antes de que
  Mixin intente aplicar nada, así que no verificó ni refutó el mixin nuevo). No se tocó `loader_version`
  en gradle.properties porque CLAUDE.md documenta a propósito que el build declara 0.16.x y es
  compatible con el loader real (0.19.3) del hardware del usuario. Ver D055 y B055.

---

**2026-07-10 (sesión 25 cont. 5 — v0.22.0: retomada la investigación de Steam Input "AAA" (B040) tras 6 sesiones en pausa, con research externo real antes de tocar código). Ver D054, B040, B054.**

El usuario pidió que Steam Input funcione "como debería" al lanzar desde Steam: detectar el mando con el
nombre correcto y leer las 10 acciones asignadas, "de AAA" — pidiendo investigación a fondo antes de
programar (la misma exigencia que ya existía en B040 desde la sesión 19 cont. 6, nunca cerrada). Se hizo
research real (WebSearch, no solo relectura del código propio) sobre la API real de Steam Input, la
detección de AppID, y dónde vive de verdad el archivo VDF — hallazgos completos y decisión en **D054**.

**Resumen de lo implementado (v0.22.0):**
- `SteamLaunchDetector` (nuevo): lee `SteamAppId`/`SteamGameId` (variables que Steam pone en el proceso
  hijo al lanzar un juego — confirmado por research externo, Proton depende de las mismas para aplicar
  parches por-juego) y calcula el AppID real de la sesión (para un shortcut "no-Steam", `SteamGameId` es
  un hash de 64 bits cuyos 32 bits altos SON el pseudo-AppID real — se recupera con un shift).
- `SteamBootstrap.resolveEffectiveAppId()`: usa ese AppID real en vez del 480 fijo cuando se detecta;
  480 se conserva exactamente como antes cuando no hay señal (dev/lanzamiento manual).
- `SteamControllerConfigDeployer` (nuevo): despliega el manifiesto VDF automáticamente en
  `<Steam>/controller_config/` — la ruta de auto-descubrimiento que Valve documenta para archivos IGA
  de desarrollador, sin ningún paso de "importar" en la UI de Steam. Escribe siempre la copia de AppID
  480 (el flujo manual documentado sigue igual) y, si detectó un AppID real, también esa copia — cero
  pasos manuales cuando el lanzamiento desde Steam se detecta bien.
- `steamAttachMode.AUTO` amplía su gatillo: antes solo `gamescope`, ahora `gamescope OR launchedFromSteam`
  — un lanzamiento genuino desde la biblioteca de Steam es señal más autoritativa de "el usuario quiere
  Steam Input" que gamescope solo (así deciden los juegos AAA reales). `NEVER`/`ALWAYS` sin cambio.
- **Hallazgo importante, sin acción de código:** investigado si `ISteamInput` expone el nombre real de
  marca del control — NO lo hace (solo categorías genéricas vía `GetInputTypeForHandle`). El "nombre
  incorrecto" reportado antes (B035, 8BitDo mostrado como "Xbox One Controller") probablemente NO es un
  bug del mod, es una limitación real de la API para pads en modo XInput. `resolveControllerName()` ya
  hace lo mejor posible con lo disponible — no se tocó.
- Confirmado también que `steamworks4j` (hasta su versión más reciente) NO envuelve `ISteamInput` en
  absoluto, solo el `ISteamController` deprecado — pero Valve documenta paridad de funciones entre
  ambos, así que no hay pérdida funcional real por seguir con la API vieja (migrar exigiría JNI manual,
  fuera de alcance).
- `mod_version` → 0.22.0. Build + 24/24 tests → `dist/steampad-0.22.0.jar`.
- **Honestidad:** nada de este lote se probó en hardware — ni siquiera la premisa central (que
  `SteamAppId`/`SteamGameId` realmente lleguen a la JVM a través de Prism/el script de sway en Bazzite)
  está confirmada con el diagnóstico en vivo que B040 pedía desde hace 6 sesiones. Ahora el log del
  propio mod hace ese diagnóstico automáticamente al arrancar (`SteamLaunchDetector.rawDiagnostics()`),
  así que la próxima sesión de hardware cierra la duda sin pasos manuales de SSH/Decky Terminal.
- Fuera de alcance a propósito: nombre de marca del control (limitación de API, no bug), fallback
  F13-F22 (B039, se mantiene), arquitectura SDL3-primario de D032 (los 10 slots siguen siendo lo único
  que Steam Input alimenta al gameplay — esto NO reintroduce "Steam Input como backend principal").

---

**2026-07-10 (sesión 25 cont. 3 — validación de hardware: B052 ✅ CONFIRMADO, B051 ✅ MAYORMENTE CONFIRMADO, B050 sigue abierto — glifos AÚN no en tiempo real + bug nuevo del marcador). Ver B052/B051/B050.**

**Resultado de hardware del usuario sobre v0.20.1:**
- **B052 (hotfix crash de arranque):** ✅ **CONFIRMADO** — el juego arranca sin crash. Cerrado.
- **B051 (vibración de jefes + firmas de daño):** ✅ **MAYORMENTE CONFIRMADO** — "la gran mayoría funcionando". El usuario pidió darlo por listo y notificar después si surge algún ajuste puntual (no hay checklist ítem-por-ítem confirmado, es una validación holística). Cerrado como validado en términos generales; reabrir solo si el usuario reporta algo específico.
- **B050 (glifos en tiempo real + marcador de zoom):** ❌ **SIGUE ABIERTO, 2 puntos:**
  1. **Glifos AÚN no están en tiempo real** pese al fix de v0.19.1 (`ZoomController.isButtonRepurposed` + `GameplayHudOverlay` ocultando el hint base) — el usuario reporta que el problema persiste y que además debe aplicarse **en todos lados**, no solo en el zoom. Pendiente diagnosticar: ¿el fix de zoom específicamente no funciona en runtime pese a verse correcto en el código, o el usuario se refiere a otros lugares del mod (fuera del zoom) que nunca tuvieron este tratamiento? **Necesita aclaración del usuario antes de tocar código** (ver pregunta hecha en la sesión).
  2. **Bug nuevo del marcador:** no se puede colocar el marcador si el jugador mira "a un costado de un bloque" — específicamente, un bloque que está arriba de la línea de vista (techo/saliente) no se puede marcar, y debería poder marcarse. El código actual de `ZoomController.placeMarker` usa `bhr.getBlockPos()` para cualquier `BlockHitResult` no-MISS sin importar qué cara se golpeó, así que en teoría debería funcionar para un bloque arriba — la causa real no está clara todavía (podría ser un problema de raycast en ángulos empinados, o el marcador SÍ se coloca pero el estilo visual de columna, que crece hacia ARRIBA desde el bloque, queda oculto/enterrado dentro de más bloques de techo). **Necesita aclaración del usuario** (qué pasa exactamente: ¿silencio total, o aparece en el lugar equivocado?).
- **Acción tomada esta sesión:** se revisó el código de `ZoomController`/`GameplayHudOverlay`/`GamepadInputDispatcher` para diagnosticar ambos puntos; sin reproducción clara en hardware propio, se preguntó al usuario antes de tocar código (mismo criterio que el resto del proyecto). Respuestas: (1) glifos — "Si, pero no aparecen ciertas cosas que estan asignadas a otras acciones" → el problema NO era el zoom en sí, sino que el HUD de gameplay (`GameplayHudOverlay`) solo mostraba un subconjunto curado de 10 de las 21 acciones asignables (`GamepadBinds.Bind`) — si el usuario asignaba un botón a SPRINT, DROP, DROP_STACK, PICK_BLOCK, GYRO_TOGGLE, SWAP_HANDS, PERSPECTIVE, PAUSE, PLAYER_LIST, SCREENSHOT o HUD_TOGGLE, ese hint nunca aparecía en ningún lado, sin importar el zoom. (2) marcador — "No pasa nada en absoluto" → descarta que sea un problema de raycast (el código ya cubre BLOCK hit y MISS, y en ambos casos `markerPos` queda seteado); la explicación consistente con "nada visible" es que el marcador SÍ se coloca en el bloque correcto, pero el estilo COLUMN/SHORT_COLUMN dibuja las partículas creciendo hacia ARRIBA desde el bloque — si el bloque marcado es un techo/saliente (más roca sólida arriba), toda la columna queda enterrada dentro de bloques opacos y es invisible.

**Fix v0.20.2 (ambos puntos):**
- **(Glifos)** `GameplayHudOverlay.LEFT/RIGHT` ampliados de 10 a las 21 acciones completas de `GamepadBinds.Bind` — las 11 nuevas (SWAP_HANDS, DROP, DROP_STACK, PICK_BLOCK, GYRO_TOGGLE, PLAYER_LIST en la columna izquierda; SPRINT, PERSPECTIVE, PAUSE, SCREENSHOT, HUD_TOGGLE en la derecha) reusan el `labelKey` que cada `Bind` ya trae (`steampad.bind.*`, ya traducido ×3 idiomas — sin i18n nuevo). Las acciones sin botón asignado siguen sin dibujar nada (comportamiento ya existente, sin cambios).
- **(Marcador)** `ZoomController.tickMarker()`: antes de dibujar, revisa si `markerPos.up()` es aire (`BlockState.isAir()`). Si SÍ hay espacio abierto arriba, comportamiento idéntico a antes (columna crece hacia arriba desde el bloque). Si NO (techo/saliente), la columna crece hacia ABAJO desde la cara inferior del bloque — el lado que el jugador realmente puede ver. Aplica a COLUMN, SHORT_COLUMN y BURST (velocidad de partícula también invertida); RING no cambia (ya vive a ras del bloque, un solo nivel).
- `mod_version` → 0.20.2. Build + 24/24 tests → `dist/steampad-0.20.2.jar`.
- **Honestidad:** ambos diagnósticos son consistentes con el código y con la respuesta del usuario, pero ninguno se reprodujo en hardware propio — quedan como hipótesis fuertes pendientes de confirmar. Si el marcador sigue sin verse tras este fix, el dato útil sería confirmar si el bloque marcado realmente tiene roca arriba (para descartar la hipótesis) o si de plano no hay NADA (lo que apuntaría de vuelta al raycast).

---

**2026-07-10 (sesión 25 cont. 4 — v0.21.0: 3 niveles de detalle para los glifos del HUD + distancia extendida y color configurable del marcador). Petición directa del usuario, sin bug previo.**

- **(1) Niveles de detalle del HUD de gameplay.** El usuario reportó que, tras ampliar la cobertura a 21 acciones en v0.20.2, "son muchas cosas para estar en pantalla" — pidió una opción de detalle Completo/Normal/Mínimo, dejando a mi criterio cuáles binds van en "Normal" (estilo Bedrock). Nuevo `ControllerConfig.ButtonGuideDetail` (MINIMAL/NORMAL/FULL, default NORMAL) + control cíclico "Detalle de glifos en juego" en Avanzado → HUD (sección nueva). `GameplayHudOverlay.Hint` ganó un campo `tier`: MINIMAL = las 5 acciones básicas de supervivencia (Saltar, Agachar, Atacar, Usar, Inventario); NORMAL = MINIMAL + las 5 originales de antes de hoy (Anterior/Siguiente objeto, Radial, Chat, Zoom) — el set curado de 10 que ya existía; FULL = las 21 completas (v0.20.2). El filtro es `hint.tier().ordinal() <= cfg.detail.ordinal()`, así que NORMAL siempre incluye MINIMAL y FULL siempre incluye ambos — sin huecos.
- **(2) Distancia del marcador.** El usuario reportó que a cierta distancia ya no podía marcar, y pidió una distancia "bastante lejana o infinita si no hay problemas de optimización". Análisis: el raycast del marcador corre UNA sola vez por press de A (no por frame), así que el costo de una distancia grande es despreciable incluso en hardware modesto (Steam Deck) — subido de 256 a **4096 bloques** (`ZoomController.MARKER_RAYCAST_DISTANCE`), muy por encima de cualquier distancia de renderizado real (vanilla tope 32 chunks = 512 bloques) sin acercarse a donde la precisión de punto flotante en coordenadas de bloque empezaría a importar. Aplica tanto al hit real como al fallback de MISS (cielo abierto).
- **(3) Color configurable del marcador.** El usuario pidió color configurable y que yo sugiriera ~6 colores. Verificado con javap contra el jar mapeado 1.21.10 que `DustParticleEffect(int color, float scale)` es el constructor real (color empaquetado 0xRRGGBB) — cambio de partícula de `END_ROD` (fija, sin color) a `DustParticleEffect`, la única partícula vanilla con color RGB arbitrario simple. Nuevo `ControllerConfig.ZoomMarkerColor` con 6 valores que elegí por buen contraste contra los entornos típicos de MC (evitando verdes/marrones que se pierden en pasto/tierra): **CYAN** (0x00E5FF, default — mantiene el look cian-blanco que ya tenía END_ROD), **WHITE** (0xFFFFFF), **GOLD** (0xFFD700), **MAGENTA** (0xFF3DF0), **LIME** (0x4CFF4C), **RED** (0xFF3B30). Control cíclico "Color del marcador" junto al de estilo en Avanzado → Zoom.
- `mod_version` → 0.21.0. Build + 24/24 tests → `dist/steampad-0.21.0.jar`.
- Pendiente de validar en hardware: los 3 puntos son nuevos, sin probar todavía.

---

**2026-07-10 (sesión 25 cont. 2 — v0.20.1: HOTFIX de crash de arranque, CONFIRMADO por el usuario con log real). Ver B052.**

**Crash real al iniciar (log del usuario, Bazzite/ROG Ally, primer tick):** `IndexOutOfBoundsException:
Index 15 out of bounds for length 15` en `GLFWGamepadState.buttons()`, llamado desde
`GlfwSnapshotSource.read()`. **Causa raíz (bug preexistente, NO introducido en esta sesión):**
`GamepadSnapshot.BUTTON_COUNT` es 23 (incluye los botones extra MISC1/PADDLE1-4/etc. que SOLO SDL3
puede leer — el comentario del propio código ya lo documentaba), pero el loop de la ruta GLFW mapeada
(`glfwGetGamepadState`) iteraba hasta `BUTTON_COUNT` en vez de `STANDARD_BUTTON_COUNT` (15, la
constante que ya existía exactamente para esto pero nunca se conectó a este loop). La API mapeada de
GLFW solo expone 15 botones reales (índices 0-14); al llegar a 15 revienta. **Por qué pasó justo en
esta sesión:** SDL3 no alcanzó a enumerar el mando a tiempo en este arranque y la cascada de
`ControllerManager` cayó a `GLFW_FALLBACK`, activando por primera vez esta ruta con este mando
específico — no es un bug nuevo de v0.20.0, solo la primera vez que se disparó. **Fix:** el loop ahora
se detiene en `STANDARD_BUTTON_COUNT` y los índices 15-22 (los extras) se dejan explícitamente en
`false` en la ruta GLFW (nunca se pueden leer ahí, solo por SDL3). `mod_version` → 0.20.1. Build +
24/24 tests → `dist/steampad-0.20.1.jar`. **Prioridad máxima: reemplazar el jar y confirmar que
arranca** — ver B052.

---

El usuario compartió una matriz de vibración hecha con otra IA (game events + frecuencias sculk) para
comparar contra `HapticsController`. Comparación: el núcleo que importa (daño, muerte, explosiones,
Warden, creeper, portal, geoda, tesoro, minería) ya estaba y con más matices que la matriz externa (el
filtro de 3 señales del cofre de tesoro no tenía equivalente ahí). Lo que faltaba era sobre todo
discriminación por tipo de daño/atacante y una feature nueva: jefes de mods futuros. Tras planificar
(sin código) y 4 preguntas de alcance respondidas por el usuario, se implementó:

- **(1) Exclusión de movimiento confirmada:** caminar y esprintar NUNCA vibran (decisión explícita del
  usuario) — la única excepción es pisar slime/miel EN MOVIMIENTO (silencio si el jugador está quieto
  encima), pulso elástico corto Cosmetic.
- **(2) Jefes cercanos (feature nueva, la pieza central):** detección SIN que el mod diga "boss" en su
  código — boss bar vanilla activa (100% confiable, UUID de la entidad = clave de la boss bar, cubre
  Wither/Ender Dragon gratis y cualquier mod que reuse el sistema oficial) O heurística de
  vida>100 O (ancho>1.4 Y alto>1.8 a la vez — se requieren AMBOS para no confundir un Enderman
  alto-pero-delgado con un jefe). Warden excluido a propósito (ya tiene su propio sistema de dread).
  Ping misterioso DANGER/WORLD mientras el jefe está lejos o sin línea de visión directa; se APAGA en
  cuanto está cerca (<20 bloques) Y a la vista — "ya lo viste, se acabó el misterio", exactamente como
  pidió el usuario. Sus golpes se sienten notoriamente más duros (Tier.CRITICAL, ×1.5 magnitud, ×1.4
  duración) vía el nuevo hook de `DamageSource` (ver punto 4).
- **(3) Enderman teletransportándose cerca:** sin evento público de Mojang para esto (métodos de
  teleport son privados/de servidor) — se detecta por salto de posición anómalo entre polls de la
  misma entidad (>8 bloques en ~0.2s no lo cubre el movimiento normal).
- **(4) Firmas por tipo de daño Y por atacante (ambos, confirmado por el usuario) — hook nuevo de
  `DamageSource` client-side.** Hallazgo clave: `EntityDamageS2CPacket` (paquete que ya existe en
  vanilla desde hace varias versiones) trae `createDamageSource(World)` — algo que el poll de HP por
  tick NUNCA pudo ver por sí solo. Mixin nuevo en `ClientPlayNetworkHandler.onEntityDamage` (misma
  clase que ya usábamos para `onExplosion`) reenvía la fuente real a `HapticsController.onPlayerDamaged`,
  que clasifica el golpe en un `PendingHit` de un solo uso (expira en 250ms) que la siguiente caída de
  HP en `tickDamageAndHealth` consume — así un mismo golpe obtiene UNA firma, nunca dos compitiendo.
  Fuego/explosión/caída/ahogo/congelación se excluyen a propósito de esta clasificación (ya tienen sus
  propias firmas dedicadas en otro lado, no se les toca). Nuevo: veneno/wither/pociones dañinas (tag
  MAGIC/WITHER/INDIRECT_MAGIC) con firma sucia/irregular promovida a Tier.DANGER; zombie/spider/
  skeleton/enderman como atacante con firma propia (peso/nervioso/punzante/sobrenatural); jefe (ver
  punto 2) siempre gana por encima de cualquier otra clasificación.
- **(5) Curado, complementando lo que ya se sentía bien (cofre de tesoro intacto, confirmado por el
  usuario):** splash al entrar al agua (Cosmetic, cede automáticamente ante el impacto de caída si
  ambos coinciden — arbitraje de Tier ya lo resuelve sin código extra), elytra glide (Ambient continuo
  suave mientras vuela). Descartado a propósito por bajo valor/riesgo de saturar: puertas, cofres
  normales, fluidos, notas musicales, block place.
- Verificación con javap contra el jar mapeado 1.21.10 de TODAS las firmas antes de escribir código
  (`EntityDamageS2CPacket`, `DamageSource`, `DamageTypeTags`, `DamageTypes`, `BossBarHud`,
  `EntityDimensions`/`Entity.getWidth/getHeight`, `LivingEntity.isGliding/canSee`, `Vec3d.
  horizontalLengthSquared`) — mismo patrón que el resto del proyecto. Access widener nuevo:
  `BossBarHud.bossBars` (package-private) para poder consultar boss bars activas por UUID de entidad.
- `mod_version` → 0.20.0. Build + 24/24 tests → `dist/steampad-0.20.0.jar`.
- Pendiente de validar en hardware: todo el lote es nuevo, cero validación en runtime todavía → B051.

---

**Feedback del usuario sobre B050:** puntos (1) merge SDL3+GLFW del pad de la Ally y (2) filtro de la
pantalla táctil NVTK0603 quedan **pendientes** (el usuario no recuerda si los probó — repetir en la
próxima sesión de hardware). Punto (3) glifos de zoom: **probado, funciona pero NO como se pidió** — el
usuario aclaró que "tiempo real" significa que los glifos BASE de un botón repurposed por el zoom deben
**desaparecer** en el mismo instante en que el zoom los toma (y volver a aparecer al soltar), no solo que
aparezcan los glifos NUEVOS encima. Ejemplo dado: durante el zoom se mostraba "DUP: Zoom+" pero el HUD
seguía mostrando también la acción normal de esos mismos botones físicos aunque ya no funcionara (p.ej.
A seguía marcado como "Salto" mientras zoom lo usa para "Marcador"). Pidió que esto sea la norma **en todo
el mod**, como en consola / como hace Controlify. Punto (4): el marcador de zoom funciona bien conceptualmente
pero (a) la baliza de columna de partículas es "un poco alta" y no convence — pidió más estilos + un ajuste
en Configuración para elegir; (b) apuntando muy hacia arriba, en ciertas situaciones NO detecta nada (el
raycast falla en silencio cuando no hay bloque en el rango).

**Fix v0.19.1 (los 2 puntos accionables, 3 y 4):**
- **(3) Glifos de HUD realmente en tiempo real:** `ZoomController.isButtonRepurposed(cfg, button)` nuevo
  — única fuente de verdad de "qué botón físico está tomado por el zoom ahora mismo" (D-pad ↑/↓ si
  `zoomDpadAdjust`, A si `zoomMarkerEnabled`), compartida por el dispatcher (que ya suprimía la acción,
  `zoomEatsDpad` ahora delega en este método — antes duplicaba la lógica) y por `GameplayHudOverlay`
  (que ahora SALTA de dibujar el hint base de LEFT/RIGHT cuyo botón coincide, en el mismo tick en que deja
  de funcionar). El bind ZOOM mismo queda exento (si por config vive en D-pad/A, su propio glifo no se
  oculta — igual que el dispatcher ya lo exceptuaba para no romper el release). Con esto acción y glifo
  nunca pueden desincronizarse — misma fuente, mismo tick. Nota de alcance: el resto del mod (radial,
  pantallas) ya seguía este mismo principio de "sustituir, no apilar" desde antes (B9/D-varias sesiones) —
  el hueco real estaba solo en la superposición zoom↔hints base, ahora cerrado.
- **(4a) Más estilos de marcador:** `ControllerConfig.ZoomMarkerStyle` (COLUMN=el actual, SHORT_COLUMN=
  columna de 5 bloques en vez de 15, RING=anillo de partículas a ras de suelo, BURST=un solo punto sin
  altura) + control cíclico "Estilo del marcador" en Avanzado → Zoom (mismo patrón que los temas de color
  del teclado/radial) para que el usuario pruebe y elija cuál le convence, sin tocar código otra vez.
- **(4b) Fix de detección al apuntar alto:** `ZoomController.placeMarker` — cuando el raycast da MISS
  (nada dentro de 256 bloques, típico apuntando muy hacia arriba o sobre el horizonte) ya NO se descarta
  en silencio: la baliza cae en el punto final de la línea de mira (`eyePos + look*256`) en vez de
  requerir un bloque real. Apuntar al cielo ahora sí deja marcador.
- `mod_version` → 0.19.1. Build + 24/24 tests → `dist/steampad-0.19.1.jar`.
- Pendiente de validar en hardware: puntos 1/2 de B050 (repetir), 3 y 4 corregidos (checklist nuevo).

---

**2026-07-09 (sesión 24 cont. 5 — v0.19.0: fusión SDL3+GLFW, filtro de pantalla táctil, glifos de zoom en vivo, MARCADOR de zoom con A). Ver PROGRESS.md y B050 (checklist).**

- **(1) Cámara de mouse — CONFIRMADO POR EL USUARIO: era Moonlight/Sunshine.** Con mouse directo funciona. Cerrado.
- **(2) El pad de la Ally invisible con el 8BitDo conectado (log del usuario):** la cascada de backends era todo-o-nada — con ≥1 dispositivo SDL3 (el 8BitDo), GLFW nunca se consultaba, y el pad de la Ally (visible solo vía GLFW como "Xbox One Controller" — ¡el preferido guardado del usuario!) quedaba oculto. **Fix: MERGE** — la lista ahora es SDL3 + los dispositivos GLFW cuyo nombre SDL3 no reportó (dedupe por nombre). Y el "fantasma que enloquece el cursor" al desconectar era `NVTK0603:00 0603:F200` — la PANTALLA TÁCTIL Novatek de la Ally expuesta como joystick (auto-activada, sus ejes son coordenadas de toque): el filtro de falsos ahora también veta el patrón de nombres i2c-HID (`XXXX:NN `), que ningún gamepad real usa.
- **(3) Glifos de zoom en TIEMPO REAL:** durante el zoom, el HUD muestra DUP "Zoom +", DDOWN "Zoom −" (si el ajuste por cruceta está activo) y A "Marcador" — aparecen exactamente mientras el zoom está activo y desaparecen al soltarlo.
- **(4) MARCADOR DE ZOOM (feature nueva):** A durante el zoom deja una baliza temporal de partículas (columna END_ROD de ~15 bloques) en el bloque apuntado (raycast de 256 bloques — es una feature de zoom). La acción normal de A (saltar) se suspende durante el zoom, igual que la cruceta. 3 ajustes nuevos en Avanzado → Zoom: activar/desactivar, duración 2–15s (default 6s), y **compartir en chat** (default OFF; al activarlo manda "[📍] x y z" al chat — lo único que otros jugadores pueden ver desde un mod 100% cliente; la baliza de partículas es local). Fix de paso: `addParticle` se renombró a `addParticleClient` en 1.21.10 (atrapado por el compilador).

**BUILD SUCCESSFUL + 24/24 TESTS, jar `steampad-0.19.0.jar` en `dist/`.** Checklist → B050 (en TODO_BLOCKERS junto a lo pendiente de B049).

**2026-07-09 (sesión 24 cont. 4 — v0.18.0: HALLAZGO CLAVE de la captura del usuario — juega vía MOONLIGHT/SUNSHINE; los "6 controles" eran dispositivos virtuales del stack de streaming, y la cámara de mouse muerta apunta al "Mouse passthrough (absolute)"). Ver PROGRESS.md sesión 24 cont. 4, D053, B049.**

- **(1) La captura reveló el entorno real:** título "ROGChe - Moonlight" — el juego corre en la ROG Ally (Bazzite) y el usuario lo ve/controla vía Moonlight desde otro equipo. Los 6 "controles" listados eran: el activo `NVTK0603` (¡una pantalla táctil Novatek expuesta como joystick!), "Mouse passthrough (absolute)", "Touch passthrough", "Pen passthrough" (dispositivos uinput de Sunshine), "extest fake device" (gamescope) y "Steam Virtual Gamepad" (el pad REAL de la Ally, re-expuesto por Steam Input porque Steam lo reclamó). **Fix (bug 5):** `ControllerManager` filtra dispositivos de inyección falsos (nombres con "passthrough"/"extest"/"fake device") de la detección — ya no se listan NI se auto-activan (el "mouse virtual loco" al desconectar el 8BitDo era la auto-activación agarrando el joystick-de-posición-de-mouse de Sunshine). "Steam Virtual Gamepad" NO se filtra a propósito: es un pad usable de verdad.
- **(2) Cámara de mouse — diagnóstico REVISADO (probablemente ambiental, no nuestro):** el mouse físico del usuario está en el equipo CLIENTE; todo su movimiento llega al juego a través del "Mouse passthrough (absolute)" de Sunshine. Un dispositivo puntero ABSOLUTO no genera movimiento relativo para un cursor agarrado (bajo Wayland/gamescope el movimiento relativo viene solo de eventos relativos de libinput) — la cámara moriría igual en VANILLA sin nuestro mod. Pasos de verificación para el usuario en B049: probar vanilla por Moonlight, desactivar "Optimizar mouse para escritorio remoto" en Moonlight (fuerza modo absoluto), probar Raw Input OFF en los ajustes de Mouse de MC, y probar con un mouse conectado DIRECTO a la Ally. Los fixes v0.16.0/v0.17.0 (auto-candado + anti-desync + log) se quedan — siguen siendo invariantes correctas y el log dirá si el desync ocurre.
- **(3) Sobre "no detecta el mando de la ROG Ally":** SÍ está en la lista — es el "Steam Virtual Gamepad" (Steam corriendo en la Ally reclama el pad integrado y lo re-expone con ese nombre). Opciones para el usuario: seleccionarlo tal cual, o cerrar Steam / desactivar Steam Input para ese mando y aparecerá con su nombre real.
- **(4) Teclado:** el 0.55× que el usuario declaró "punto dulce" es el nuevo 1.0× (bases rebasadas: CRUISE 20→11, MAX 82→45 px/tick) — el centro del slider ES el feel afinado.
- **(5) Vibración — 3 eventos nuevos + startup corto (auditoría):** comer/beber (pulsos suaves cada ~260ms al ritmo de masticado, COSMETIC), abrir cofre (crujido leve de 130ms acompañando la animación de la tapa, COSMETIC — el sistema de tesoro conserva el "este importa"), quemarse (pulsos DANGER cada ~420ms mientras `isOnFire`, continuo y distinto del thump de daño). Vibración de inicio 0.6f/250ms → **0.4f/90ms** (un tap discreto, no un evento).

**BUILD SUCCESSFUL + 24/24 TESTS, jar `steampad-0.18.0.jar` en `dist/`.** Checklist → **B049**.

**v0.18.1 (mismo día, antes de la prueba de hardware) — FIX: los gatillos no accionaban en el aire.** Reporte del usuario: RT golpea solo si hay algo cerca apuntado; el click del mouse golpea también en el aire. Causa: `holdOnChange` escribía solo `setKeyPressed` (el estado sostenido, que alimenta el camino CONTINUO de vanilla: minar el bloque apuntado) pero nunca `onKeyPressed` (el evento de click que incrementa `timesPressed`, que es lo que dispara el golpe/uso en el aire vía `wasPressed`). El mouse físico hace AMBOS en cada press (`Mouse.onMouseButton`) — ahora el flanco de subida del gatillo también: `hold(key,true)` + `tap(key)`. Afecta ATTACK (el caso reportado; USE ya funcionaba parcialmente en aire por el camino continuo de `doItemUse`). Revisión completa del gameplay del dispatcher hecha a petición del usuario: el resto lee correcto; única mejora encontrada (auto-golpe al mantener RT, estilo Bedrock) fue preguntada y **APROBADA por el usuario** → implementada en el mismo v0.18.1: `ControllerConfig.attackAutoRepeat` (default ON, toggle "Mantener para golpear (Bedrock)" en Básico → Movimiento): con RT sostenido y la mira NO sobre un bloque, se registra un click nuevo cada vez que el cooldown del arma se llena (auto-ritmo al arma real, sin timer propio; minado de bloques intacto por el camino continuo; sin doble golpe en el press inicial gracias al guard de flanco). i18n ×3. Jar **`steampad-0.18.1.jar`** + 24/24 tests.

**2026-07-09 (sesión 24 cont. 3 — v0.17.0: lote de 4 tras feedback de v0.16.0, con análisis del código real de Controlify vía GitHub). Ver PROGRESS.md sesión 24 cont. 3, D051–D052, B048.**

**Feedback de v0.16.0:** cámara de mouse SIGUE muerta (el auto-candado v0.16.0 no bastó), modo Apuntador no se entendió (revisado: el cableado es correcto), aim assist sigue sin sentirse, más 2 pedidos nuevos (snap a botones de mods, defaults del teclado centrados). "Todo lo demás parece que funciona bien".

- **(1) Cámara de mouse — capa 2 del fix, informada por el código REAL de Controlify (leído del repo en GitHub, sugerencia del usuario).** Confirmaciones del análisis: el `DualInput` de Controlify hace EXACTAMENTE el mismo merge de movimiento que nuestro `KeyboardInputMixin` (v0.15.0 iba bien encaminado), y su regla de cámara es `canProcessLookInput()` exige `isMouseGrabbed()` — **nunca pelean contra el agarre del mouse**. El único hueco restante en nuestro lado: un DESYNC donde vanilla cree `cursorLocked=true` pero el modo GLFW real quedó en HIDDEN/NORMAL (nuestro `VirtualCursorRenderer` es el único código que toca `GLFW_CURSOR` directo) — ahí el puntero se clava en el borde, deltas cero, cámara muerta, y el fix v0.16.0 no aplicaba porque el flag vanilla ya decía "bloqueado". **Fix de 2 capas (solo gameplay):** (a) `VirtualCursorRenderer.setOsCursorHidden` ahora hace return si `isCursorLocked()` — nunca pisa el agarre (la regla de Controlify); (b) el self-heal del dispatcher ahora también verifica el MODO GLFW real (`glfwGetInputMode != DISABLED` con `cursorLocked=true` → re-asserta DISABLED) y **loguea un warning la primera vez que repara el desync** — si la cámara estaba muerta por esto, esa línea en el log del usuario CONFIRMA el diagnóstico.
- **(2) Teclado — Apuntador verificado y defaults centrados.** El cableado del modo Apuntador es correcto (mismo convenio de ejes que Velocidad); cómo se usa: inclinar el stick apunta DIRECTO a esa zona del teclado (arriba-izq del stick = tecla superior izquierda), soltar conserva la tecla — flick→soltar→A. Queda Velocidad como default. Sliders recentrados: velocidad 0.5–1.5× (default 1.0× = CENTRO), altura 20–40% (default 30% = CENTRO, `GlobalConfig` default 0.20→0.30). De paso se corrigió una inconsistencia vieja: el slider de altura decía 12–30% pero `KeyboardGeometry` clampeaba a mínimo 22% — la mayor parte del slider no hacía nada; ahora la geometría honra 20–40%.
- **(3) Snap y D-pad sobre botones de MODS en inventarios.** `SlotSnap` generalizado: los objetivos ya no son solo slots del handler sino también **cualquier `ClickableWidget` activo/visible de la pantalla** (botón de mochila de mods, libro de recetas vanilla, botones de ordenar...). Aplica al imán del cursor Y a la navegación por D-pad (`moveToNeighbor` unificado sobre slots+widgets). `nearestSlot`/`nearestSlotUnbounded` siguen siendo slot-only a propósito (los corchetes de selección y el quick-move con Y necesitan un slot real).
- **(4) Aim assist v3 — el problema era de PERCEPCIÓN por apilamiento + caída de proyectil.** Dos hallazgos: (a) con "Reducir sensibilidad al apuntar" activo, la cámara YA iba al 45% mientras se carga el arco — una fricción extra encima era imperceptible (todo igual de lento); ahora, cuando el assist tiene objetivo, ese ×0.45 se OMITE (el assist es dueño del frenado ese frame). (b) La fricción ahora usa `sqrt(closeness)` — buena parte del frenado llega al ENTRAR al cono, no solo en el centro exacto. (c) **Compensación de caída**: el punto de assist se eleva sobre el centro del objetivo según la caída esperada de una flecha a plena carga (`drop ≈ dist² × 0.0028`, tope 2.5 bloques) — a distancia, la fricción/magnetismo ocurren donde de verdad hay que apuntar con arco/ballesta/tridente, como hacen los shooters de consola con armas con travel time. (d) Fricción a 0.30, magnetismo a 16°/s. Mobs y jugadores YA eran objetivos desde v0.15.0 — la percepción de "no incluye mobs" era el apilamiento de (a).

**BUILD SUCCESSFUL + 24/24 TESTS, jar `steampad-0.17.0.jar` en `dist/`.** Checklist → **B048**. La prueba clave sigue siendo la cámara de mouse — y ahora el log dirá explícitamente si el desync era la causa.

**2026-07-09 (sesión 24 cont. 2 — v0.16.0: lote de 7 tras el feedback de v0.15.0; el usuario reporta "todo lo demás funciona bien, aún no detecto bugs"). Ver PROGRESS.md sesión 24 cont. 2, D049–D050, B047.**

**Feedback de hardware sobre v0.15.0:** entrada mixta PARCIAL (teclado ✅, clicks de mouse ✅, cámara de mouse ❌), stick del teclado aún no convence, aim assist imperceptible con el toggle ON, y 4 pedidos nuevos (scroll+D-pad, reset de zoom, auditoría de traducciones, panel de info más pequeño). Todo lo demás de v0.15.0 sin bugs detectados.

- **(1) Cámara del mouse muerta en gameplay (entrada mixta).** Diagnóstico por descarte con bytecode: el ÚNICO estado consistente con "teclado ✅ clicks ✅ cámara ❌" es gameplay con el cursor SIN candado GLFW (el puntero se topa con el borde de la ventana → deltas cero → cámara muerta; los clicks siguen llegando). Vanilla solo re-bloquea en un click con pantalla nula o vía `setScreen(null)` — y `lockCursor()` hace `return` sin foco de ventana (`outOfFocusInput` default false), así que un menú cerrado sin foco deja el gameplay des-bloqueado. **Fix (auto-sanación):** cada tick de gameplay, si `!isCursorLocked() && isWindowFocused()` → `lockCursor()`. Honestidad: es la única explicación físicamente consistente encontrada, pero el gesto exacto no se reprodujo aquí — si persiste en hardware, el dato útil es si la cámara revive tras UN click dentro del mundo.
- **(2) Teclado: velocidad −15% + MODO APUNTADOR nuevo.** `CRUISE` 24→20, `MAX` 95→82 px/tick. Y nueva opción "Modo del stick" en Ajustes → Teclado: **Velocidad** (el actual, doble zona + freno) vs **Apuntador** (estilo Steam Big Picture: la inclinación del stick apunta DIRECTO a una zona del teclado — arriba-izquierda del stick = tecla superior izquierda; soltar conserva la última tecla; flick→soltar→A). `GlobalConfig.virtualKeyboardStickMode`, retrocompatible (default VELOCITY).
- **(3) Aim assist reforzado** (reportado imperceptible): cono base 2°→3.5° y multiplicador 2.2→2.6 (el engagement era tan raro que parecía apagado), fricción 0.45→0.35 (frenado más firme), magnetismo 8→12°/s y umbral de stick 0.1→0.02 (se siente también en tracking fino), rango 24→28 bloques. **Jugadores ya contaban como objetivos** (LivingEntity incluye PlayerEntity; `getOtherEntities` solo excluye al propio) — ahora documentado explícito en el código.
- **(4) D-pad tras scroll del stick derecho ya no regresa al inicio.** `SteamPadBaseScreen.focusMoveDir`: si el foco actual quedó FUERA del viewport (o no hay foco), la navegación arranca desde la primera fila VISIBLE del área scrolleada — la posición de scroll es la intención del usuario — en vez de desde el foco viejo/el tope.
- **(5) Zoom: nueva opción "Restablecer zoom al soltar"** (Avanzado → Zoom, default OFF = comportamiento actual): ON = los ajustes de la cruceta duran solo ese zoom y al soltar vuelve al nivel configurado; OFF = se conserva el último nivel (persistido). `ControllerConfig.zoomResetOnRelease`, captura del nivel base en el flanco de subida en `ZoomController.tick`.
- **(6) Auditoría de traducciones — LIMPIA:** 393 claves idénticas en `en_us`/`es_mx`/`es_es` (diff automatizado con comm), todas las claves referenciadas en el código existen (las `steampad_*` restantes son IDs de acciones VDF, no traducciones), y las familias dinámicas de enums (temas ×8, tipos de radial ×6, modos de stick, sneak/sprint/gyro/yaw/require_button) completas. El fallback a inglés para idiomas no soportados es comportamiento nativo del sistema lang de MC — nada que hacer.
- **(7) Panel de diagnóstico de Selección de control más pequeño:** el bloque de 7 líneas se dibuja ahora a 0.75× (traslación+escala de matriz, layout intacto), con fondo y anclaje re-calculados a la altura nueva.

**BUILD SUCCESSFUL + 24/24 TESTS, jar `steampad-0.16.0.jar` en `dist/`.** Checklist de validación → **B047**.

**2026-07-09 (sesión 24 cont. — v0.15.0: lote de 8 fixes/features tras el feedback de hardware de v0.14.0; DOS validaciones confirmadas por el usuario: B043 vibración ✅ y B044 crash ✅). Ver PROGRESS.md sesión 24 cont., D046–D048, B046.**

**Validado por el usuario en hardware (v0.13.x/v0.14.0):** B043 (vibración Tier 1+2) "funciona muy bien" ✅; B044 (crash de Ajustes) sin crash ✅. **Reprobado por el usuario:** el fix v0.14.0 del mouse-atorado NO resolvió el caso reportado (ver punto 1 abajo — esta vez la causa raíz se encontró y verificó a nivel bytecode); la velocidad del stick del teclado seguía sin convencer (punto 2).

- **(1) CAUSA RAÍZ REAL del mouse-atorado en Ajustes del gamepad (v0.14.0 no bastó — el captureMode huérfano era real pero no era ESTE bug).** Verificado con javap contra el jar 1.21.10: `MinecraftClient.openGameMenu` empieza con `if (currentScreen != null) return;` — el focus-pause espurio de vanilla SOLO puede disparar desde gameplay (sin pantalla). Pero `PauseGate.shouldSuppress()` cancelaba TODO `setScreen(GameMenuScreen)` sin foco — incluyendo el legítimo "volver al menú de pausa" cuando cierras con B una pantalla hija (Ajustes de Minecraft, ajustes del gamepad, cualquier mod). Resultado: la cadena de cierre B→B→B se ATASCABA en el salto de vuelta al menú de pausa (el `setScreen` se cancelaba, la pantalla hija se quedaba), y el jugador nunca llegaba al gameplay sin re-enfocar con el mouse físico. **Fix quirúrgico y universal (cubre todas las ventanas):** `PauseGate.shouldSuppress()` ahora devuelve false si `mc.currentScreen != null` — la supresión solo aplica a aperturas desde gameplay, que el bytecode demuestra es el ÚNICO camino espurio. El fix v0.14.0 del captureMode se queda (defensa válida, era un bug real aunque no este).
- **(2) Stick del teclado v3 — doble zona + freno.** El 85% del recorrido del stick es TODO precisión (rampa cuadrática hasta solo 24 px/tick); el turbo (95 px/tick, restaurado rápido) vive exclusivamente en el último tramo antes del tope físico. Y un **freno por desaceleración**: en cuanto la magnitud del stick empieza a caer (el usuario está soltando), la velocidad residual se corta a 30% y el imán agarra la tecla apuntada (pull 0.5, y 0.85 al aflojar del todo) — volar a turbo y soltar frena EN la letra esperada en vez de pasarse. El slider de velocidad del usuario sigue aplicando encima.
- **(3) Chat empujado por el teclado (estilo Controlify).** Verificado en bytecode: la ventana de sugerencias de comandos ancla en `owner.height - 12` HARDCODED (no al campo de texto) — mover solo el campo no servía. Fix: 2 mixins delgados nuevos (`ChatScreenMixin`, `ChatHudMixin`) que trasladan con matrices TODO el contenido del chat (campo, franja, sugerencias de comandos, historial) hacia arriba por la altura del teclado mientras está abierto. El teclado va ahora al ras del fondo (se quitó el pad de 16px) y el chat completo queda encima — los comandos ya no quedan tapados.
- **(4) Editor radial reestructurado.** Dos filas etiquetadas y separadas: "Rueda 1/3" (◀ ▶ para cambiar, "+ Rueda" / "− Rueda" con texto claro en vez de ＋/✕ ambiguos) y "Espacios: N" (− +). El tema se movió FUERA del editor a una pantalla nueva **"Apariencia"** (`RadialStyleScreen`, botón junto a "Listo"): radio de la rueda (54–130px), tamaño de espacios (12–26px, `RadialConfig.chipRadius` nuevo — chips más grandes abren la rueda para conservar separación), fondo oscuro on/off, y el tema de color AL FINAL — todo con el previo de rueda en vivo a la derecha.
- **(5) Cámara AAA (curvas de consola).** La curva anterior era casi lineal (`0.6+0.4a`). Ahora: curva de potencia sobre la MAGNITUD del stick (no por eje — no distorsiona diagonales), exponente configurable (`lookCurve`, default 2.2 = estándar de shooter de consola), velocidades base separadas yaw 260°/s / pitch 195°/s (consola gira más rápido de lo que inclina), y **aceleración de giro** (`lookTurnBoost`): stick al tope ~0.15s → rampa suave (smoothstep) a un turbo de 1.65× en yaw (~430°/s para 180s rápidos) con solo 25% del boost en pitch. 2 ajustes nuevos en Básico → Sensibilidad.
- **(6) AIM ASSIST para proyectiles (estilo COD/BF de consola).** Nuevo `input/AimAssistController`, SOLO mientras se carga arco/ballesta/tridente (`UseAction` BOW/CROSSBOW/SPEAR, verificado en `net.minecraft.item.consume.UseAction`): **fricción** (la cámara se frena hasta 0.45× sobre un objetivo vivo, cono angular escalado por distancia — cerca = más pegajoso) + **magnetismo suave** (deriva ≤8°/s hacia el centro del objetivo SOLO mientras el stick está activo — nunca mueve una cámara quieta). Línea de visión requerida (`canSee` — sin assist a través de paredes), rango 24 bloques, búsqueda espacial eficiente (`getOtherEntities` con caja a lo largo de la mirada). Sección nueva "Asistencia de apuntado" en Básico: toggle + fuerza 0–100%. Todo client-side, solo moldea la cámara — no dispara ni apunta solo.
- **(7) Glifos de gameplay en tiempo real.** El HUD ya derivaba de los binds reales; se añadieron RADIAL y CHAT (columna izq) y ZOOM (columna der) a las listas. Acciones sin botón asignado no dibujan nada (CHAT y ZOOM aparecen solo cuando el usuario les asigna botón) — se actualiza al instante al rebindear.
- **(8) ENTRADA MIXTA reparada de raíz (2 bugs concretos).** (a) `KeyboardInputMixin` SOBREESCRIBÍA el movimiento vanilla cada tick — stick en reposo escribía (0,0) sobre WASD y false sobre Space/Shift: el teclado estaba muerto con un pad conectado. Ahora MERGE: el stick solo toma el vector mientras se empuja; los booleanos hacen OR con los del teclado. (b) `hold(attackKey/useKey)` escribía `false` CADA tick con el pad en reposo — mataba el estado del KeyBinding que el click FÍSICO del mouse había puesto (minar sostenido se cortaba). Ahora escritura por flanco (`holdOnChange`): el pad solo toca el estado cuando SU PROPIO held cambia; `releaseAllMovement` solo libera lo que el pad tenía presionado.

**BUILD SUCCESSFUL + 24/24 TESTS, jar `steampad-0.15.0.jar` en `dist/`.** Checklist de validación → **B046** (todo este lote sin probar en hardware; en particular el punto 1 tiene diagnóstico verificado en bytecode pero el gesto exacto sigue pendiente de confirmarse en el Deck).

**2026-07-09 (sesión 24 — v0.14.0: lote de 4 fixes/features pedidos por el usuario tras probar v0.13.2 — velocidad del stick del teclado + slider, previo de color del teclado, fix del "mouse atorado" también en Ajustes del gamepad, y temas de color en el menú radial). Ver PROGRESS.md sesión 24, DECISIONS.md, TESTPLAN.md y TODO_BLOCKERS.md (B043/B044 siguen abiertos, más los 3 nuevos ítems de esta sesión).**

- **(1) Stick del teclado virtual — más controlable.** El usuario reportó que el cursor libre del stick (introducido sesión 21/S1) se sentía "muy rápido". Se bajó `FLOAT_MAX_SPEED` 95→62 px/tick a fondo, se subió la curva de respuesta (`FLOAT_CURVE` 2.4→2.7, más fino cerca del centro) y se hizo el imán de snap más agresivo (`PULL_SETTLE` 0.5→0.7, `SETTLE_MAG` 0.12→0.16). Además, nuevo campo `GlobalConfig.virtualKeyboardStickSpeed` (multiplicador 0.5×–2.0×, default 1.0×) con un **slider "Velocidad del stick" en Ajustes de teclado** para que el usuario ajuste fino sin tocar código.
- **(2) Previo de color del teclado.** `VirtualKeyboardRenderer.renderThemePreview(...)` — nuevo método público que dibuja una franja de 3 teclas de muestra (A, S, espaciadora) con la paleta REAL del tema elegido (reutiliza `palette()`/`drawKey()`, no hay tabla de colores duplicada). `KeyboardSettingsScreen` reserva espacio bajo el selector de tema y lo dibuja en vivo — cambia al instante al ciclar el tema.
- **(3) Fix "mouse atorado" — también reproducible dentro de Ajustes del gamepad.** El fix D037 (sesión 17, `PauseGate`) resolvía el caso general de gameplay, pero el usuario confirmó que el MISMO síntoma reaparece si el mouse sale de la ventana estando en Ajustes del gamepad: después ya no se puede salir ni siquiera del menú del juego con el mando. **Causa estructural identificada:** `GamepadInputDispatcher.captureMode` es un flag que SOLO `BindingsScreen` pone en `true`/`false`, y hace `return` inmediato al inicio de `tickGui()` — pero `MinecraftClient.setScreen()` (vanilla) NO llama al `close()` de la pantalla saliente, solo a `removed()`; si el `PauseGate` deja pasar un `setScreen(GameMenuScreen)` espurio (pérdida de foco) mientras `BindingsScreen` sigue "capturando" un rebind, `captureMode` queda huérfano en `true` para siempre — y como bloquea TODO `tickGui()`, ningún botón (incluyendo B/Start) vuelve a navegar nada con el mando, no solo la pantalla de ajustes. **Fix (auto-sanación):** al inicio de `GamepadInputDispatcher.tick()`, si `captureMode==true` y la pantalla actual NO es `BindingsScreen`, se fuerza `captureMode=false` incondicionalmente — invariante recuperado sin importar cómo se perdió. **Honestidad:** es un fix bien razonado sobre un punto de fallo estructural confirmado (single point of failure), pero no se pudo reproducir interactivamente el gesto exacto de "sacar el mouse de la ventana estando en Ajustes" para confirmar 1:1 la causa — trátese como hipótesis fuerte pendiente de validación en hardware, no como causa raíz 100% confirmada.
- **(4) Temas de color en el menú radial.** Mismo enum compartido `PixelTheme` (extraído de `GlobalConfig.KeyboardTheme`, ahora en `config/PixelTheme.java`, 8 valores: VANILLA/OAK/STONE/EMERALD/REDSTONE/LAPIS/AMETHYST/NETHER) — Gson serializa por nombre, migración retrocompatible sin tocar configs guardadas. Nuevo campo `RadialConfig.theme`. `RadialRenderer` gana un `record Palette` + `palette(PixelTheme)` (mismo patrón que el teclado) y lo aplica a los fondos de chip, backdrop, anillo de selección/acento y color del texto de las pistas — la gelatina (E11) y las siluetas de rueda fantasma se dejaron neutrales (blanco) a propósito, son decoración, no superficies principales. `RadialEditorScreen` gana un control cíclico "Tema" justo debajo de la fila de cambio de rueda; el previo YA EXISTÍA (la rueda en vivo a la derecha del editor, `RadialRenderer.render(...)`), así que cambiar el tema se ve reflejado al instante sin widget adicional.

**BUILD SUCCESSFUL + 24/24 TESTS, jar `steampad-0.14.0.jar` en `dist/`.** Nada de este lote se ha validado todavía en hardware — se suma a la lista de pendientes de checar (ver sección "Siguientes Pasos" / TODO_BLOCKERS B043/B044 + los 3 puntos nuevos de esta sesión).

**2026-07-09 (sesión 23 cont. — v0.13.2: corrección de un error propio — `TitleScreenMixin` eliminado DEL TODO, no solo desactivado).** El usuario aclaró que los glifos X/B del menú principal (que yo había "reactivado" en la sesión 20 creyendo que era un mixin dormido por accidente/bug) en realidad habían sido quitados A PROPÓSITO en una sesión anterior que no tenía completa en el contexto. Mi "fix" de la auditoría fue, sin darme cuenta, deshacer una decisión de producto ya tomada. **Corregido:** `TitleScreenMixin.java` eliminado por completo (no solo removido de `steampad.mixins.json`) — así no puede volver a "descubrirse" como dormido y reactivarse por error en una futura auditoría. Verificado con grep que no tenía otras dependencias en el código. **Lección para el futuro:** un mixin no registrado NO es automáticamente un bug — antes de "reactivar" algo que aparenta estar dormido, hay que preguntar si fue deliberado. **BUILD SUCCESSFUL + 24/24 TESTS, jar `steampad-0.13.2.jar` en `dist/`.**

**2026-07-09 (sesión 23 — v0.13.1: FIX de crash confirmado en hardware real, primera validación real de la sesión 22). Ver PROGRESS.md sesión 23 y D042.** El usuario probó v0.13.0 en su Steam Deck (Bazzite, modpack de 80 mods) y el juego **crasheó** al entrar a Ajustes de Minecraft: `IncompatibleClassChangeError` al invocar `getClass().getSimpleName()` sobre `OptionsScreenMixin$GamepadButton` (el botón de entrada a SteamPad junto a "Controls"). **Causa raíz:** esa clase estaba anidada DENTRO de la clase `@Mixin` — Mixin reescribió mal su atributo bytecode `InnerClasses` para apuntar a `ButtonWidget` (su superclase) en vez de su clase contenedora real; el error queda invisible hasta que algo llama `getClass().getSimpleName()` sobre una instancia, que es exactamente lo que hace el código nuevo de esta sesión (`VirtualKeyboard.isTextWidget`, para detectar campos de texto de mods como Xaero's). Era la ÚNICA clase anidada dentro de un `@Mixin` en todo el proyecto (verificado con grep). **Fix de raíz:** `GamepadButton` se extrajo a una clase de nivel superior (`client/ui/GamepadOptionsButton`), fuera de cualquier `@Mixin` — Mixin ya no la toca. **Fix defensivo (protege contra cualquier otro mod, no solo este caso):** `isTextWidget()` ahora envuelve la reflexión en `catch (Throwable t)` (no `Exception` — `IncompatibleClassChangeError` es un `Error`), porque con 80 mods instalados cualquier otra clase generada por mixins de OTRO mod podría tener el mismo problema. De paso, y a solicitud del usuario, se quitaron los dos puntos de color (azul/blanco) de los botones de cara en el icono del gamepad — quedó monocromo (interpretación a confirmar con el usuario). **BUILD SUCCESSFUL + 24/24 TESTS, jar `steampad-0.13.1.jar` en `dist/`.**

**2026-07-09 (sesión 22 cont. — v0.13.0: vibración Tier 2 completo, scheduler de prioridad + 8 eventos + tesoro filtrado). Ver PROGRESS.md sesión 22 cont. y D040.** Tras el diseño discutido a fondo con el usuario (comparando RDR2, God of War Ragnarök, Cyberpunk 2077, Silent Hill 2, Forza), se reconstruyó `HapticsController` con un **árbitro de prioridad de 5 niveles** (`Tier`: CRITICAL>DANGER>IMPACT>AMBIENT>COSMETIC) — necesidad técnica real dado que el hardware es UN solo canal de rumble, no una elección estética. **Nuevos eventos:** portal del Nether (ping que se acelera con la cercanía), creeper cargando explosión (pulsos cada vez más juntos), Warden cerca (rumble bajo y opresivo), geoda de amatista (ping de descubrimiento único), hambre crítica/ahogo/congelación (extienden el heartbeat de Tier 1 con timings distintos), caída+aterrizaje independiente del daño real, minería por valor (mineral=sólido, diamante/esmeralda/ancient debris=pulso limpio promovido a IMPACT). **Cofre de tesoro con filtro de 3 señales** (la pieza que se diseñó en conversación con el usuario): cerca de un spawner=dungeon real, NO cerca del punto de spawn/cama del jugador=no es su casa (usa `ClientWorld.getSpawnPoint()`, confirmado que refleja el respawn REAL del jugador vía `PlayerSpawnPositionS2CPacket`, no el spawn del mundo), no abierto antes=no repite (`UseBlockCallback` de Fabric API trackea posiciones abiertas). Deliberadamente fuera de esta ronda (decisión explícita del usuario): textura de superficie al caminar (mayor riesgo de saturación) y tesoro enterrado genérico (sin criterio confiable del lado cliente). Todas las firmas verificadas con javap antes de escribir código — compiló a la primera. **BUILD SUCCESSFUL + 24/24 TESTS, jar `steampad-0.13.0.jar` en `dist/`.**

**2026-07-09 (sesión 22 — v0.12.0: sistema de vibración AAA event-driven). Ver PROGRESS.md sesión 22 y D039.** El usuario pidió vibración "tal como Bedrock" — investigación previa reveló que **Bedrock no tiene vibración nativa** (feature nunca implementada por Mojang pese a años de solicitudes); la referencia real más cercana es Controlify (daño/bloques/rayos). Se diseñó desde cero, informado por el wishlist de la comunidad + principios de diseño AAA (Returnal: vibración ambiental continua; God of War Ragnarök: patrón "crece antes de pagar"). **Hallazgo de paso:** `TitleScreenMixin` (glifos X/B en el título) existía en el código desde hace sesiones pero NUNCA estuvo registrado en `steampad.mixins.json` — mixin completamente dormido, corregido. **Nuevo `haptics/HapticsController`:** cablea por fin las 6 categorías de vibración de `ControllerConfig` (existían en Ajustes sin hacer nada) a eventos reales: daño recibido (escala con la caída de HP), heartbeat de vida baja, muerte, golpe cuerpo a cuerpo (con heurística local de crítico), romper bloque (Fabric API `ClientPlayerBlockBreakEvents.AFTER`), explosión cercana (mixin nuevo en `ClientPlayNetworkHandler.onExplosion`, escalado por distancia real), rayo cercano (poll de `LightningEntity`, sin mixin nuevo). `ControllerManager.rumble` ganó un overload asimétrico low/high-freq (única "textura" real del hardware — sin HD haptics, B003 — pero ya alcanza para simular "boom" pesado vs. "buzz" agudo sin costo extra). Todas las firmas de mixin/API verificadas con javap contra el jar mapeado 1.21.10 ANTES de escribir código (`onExplosion`, `attackEntity`, y el descubrimiento de que `Entity.getPos()` ya no existe — ahora `getEntityPos()`). **BUILD SUCCESSFUL + 24/24 TESTS, jar `steampad-0.12.0.jar` en `dist/`.** Esto es solo la Tier 1 (eventos reactivos discretos, bajo riesgo); una Tier 2 de momentos AAA-inmersivos (portal del Nether con vibración creciente, portal del End, Warden/sculk, tormenta ambiental, elytra, montar vehículo…) se propuso al usuario para consulta — NO implementada todavía, ver B043.

**2026-07-09 (sesión 21 — v0.11.0: lote de 7 mejoras/fixes + feature ZOOM completa). Ver PROGRESS.md sesión 21 y D037/D038.** B040 sigue en pausa (usuario fuera de casa). Lo hecho: **(S1)** stick izq del teclado virtual re-curvado (`mag^2.4` normalizado por dirección, máx 46→95 px/tick solo a fondo; el imán del 45% a baja deflexión ATRAPABA el punto en la tecla actual — ahora 8% empujando / 50% al aflojar): empujar poco = tecla de al lado, a fondo = volar por el teclado. **(S2)** teclado re-tematizado a pixel-art vanilla MC (contorno negro 1px, bisel claro arriba/oscuro 2px abajo estilo botón MC, texto con sombra) + 8 presets de color (`GlobalConfig.KeyboardTheme`) ciclables en Ajustes de teclado. **(S3)** detección universal de campos de texto (`VirtualKeyboard.findTextField`): cadena de foco + duck-typing por nombre de clase + barrido recursivo buscando campos con `isFocused()` propio (el caso Xaero's que Controlify no detecta); entrega de texto con fallback directo al widget si la Screen no lo enruta. **(S4)** glifos LB/RB por marca junto a las pestañas de los 3 menús de ajustes (`SettingsTabs.renderGlyphs`). **(S5)** CAUSA RAÍZ del "ratón no puede hacer clic en algunas ventanas": `hasActivity()` contaba gatillos con umbral >0 → cualquier ruido de eje re-marcaba GAMEPAD cada tick → `markMouse()` nunca aterrizaba → cursor virtual visible DESINCRONIZADO del puntero real (los clicks caían donde no se veía). Fix 3 capas: umbral configurado para gatillos en `hasActivity`, `InputRouter.markMouseForce()` (barrido >20px o click físico ganan SIEMPRE), mixin nuevo en `Mouse.onMouseButton` (con guard INJECTING para clicks virtuales). **(S6)** carrusel radial: siluetas de rueda ANTERIOR y SIGUIENTE con su nº real de chips + glifos LB/RB, siguen la animación. **(S7 — FEATURE)** **ZOOM estilo BetterZoom nativo para mando**: diseño extraído del código real de BetterZoom (factor=zoomFov/fovOpciones, easing smoothstep, hold/toggle, bobbing off, sensibilidad auto); `input/ZoomController` nuevo (fast-path idle = 1 comparación, easing frame-rate-independent, persistencia del nivel al soltar); mixin en `GameRenderer.getFov(Camera,F,Z)F` (firma verificada con javap 1.21.10, solo FOV del mundo); bind `ZOOM` sin default en BOTONES (rebind+chord gratis); cruceta ↑/↓ ajusta nivel durante el zoom (acciones base de DUP/DDOWN suprimidas, binds y extra binds); cámara ralentizada por el factor (auto/fijo); liberación defensiva al abrir pantallas/desconectar; sección "Zoom" en Avanzado con 9 opciones; i18n ×3. **BUILD SUCCESSFUL + 24/24 TESTS, jar `steampad-0.11.0.jar` en `dist/`.** Checklist de validación en hardware → **B042**.

**2026-07-09 (sesión 20 — auditoría de código + limpieza + fixes, v0.10.6). SIN tocar B040 (usuario fuera de casa, a propósito).** El usuario pidió una pausa explícita en la investigación de Steam Input nativo (B040) y una auditoría general del código: orden/organización, limpieza y búsqueda de bugs reales, sin romper nada. Cobertura: `radial/`, `mixin/`, entry points (`SteamPadMod`/`SteamPadClient`) vía un subagente Explore que completó antes de que el resto tocara el límite de sesión de Claude (3 de 4 subagentes en paralelo murieron por "session limit"); el resto (`input/`, `steam/config/service/platform/compat/`, `screen/client-ui`) se auditó a mano leyendo los archivos de mayor riesgo directamente. **Bugs reales corregidos (4):** (1) `RadialMenuController.openSubmenu()` llamaba a `open()` mientras `open` seguía en `true` (se ejecuta en medio de `confirmSelection()`/`activateSelected()`) → `open()` hacía no-op por su propio guard → el tipo SUBMENU del radial nunca reabría la rueda salvo por el camino de `close()`; ahora fuerza `open=false` antes de reabrir. (2) `RadialRenderer.getConfig()` leía `ActiveControllerService.getActiveHandle()` (el mando globalmente activo) mientras las ranuras/selección venían de `RadialMenuController`'s propio `activeHandle` — podían divergir por un tick (mando activo cambiando con la rueda aún abierta, o editando el radial de OTRO mando) y el estilo visual usaría el config equivocado; ahora `RadialRenderer.render(...)` recibe el handle explícito de cada llamador (`RadialMenuOverlay` pasa `RadialMenuController.getActiveHandle()`, `RadialEditorScreen` pasa su propio `handle` de edición). (3) Un tipo de slot radial no reconocido (typo/config corrupta) caía a `NONE` en silencio sin loguear nada, a diferencia de los demás fallbacks de la misma clase — añadido `LogUtil.debug`. (4) `SteamPadClient.ensureFallbackBackendsInit()` marcaba `fallbackInitDone=true` ANTES del `try` — si `GamepadMappings.loadAll()`/`Sdl3GamepadProvider.init()`/`ControllerClaimService.init()`/`ActiveControllerService.restoreFromConfig()` fallaban una sola vez (ej. hiccup transitorio de GLFW/SDL en el primer tick), el mod quedaba PERMANENTEMENTE sin esos backends el resto de la sesión, sin reintento — ahora reintenta cada ~1s durante ~10s (mismo patrón que el retry de ActionSets en `SteamBootstrap`) antes de rendirse con un log claro. **Fix de robustez (no era un bug de gameplay, pero es real):** `JsonUtil.saveToFile()` escribía directo al archivo destino (no atómico) — un crash/corte de luz a mitad de escritura deja el JSON truncado e ilegible (el proyecto YA sufrió un apagón real que corrompió un cache de Loom, B016) y como el mod autoguarda en cada cambio, la ventana de riesgo es constante durante toda la sesión, no solo al cerrar; ahora escribe a un `.tmp` y hace `Files.move` atómico (con fallback no-atómico si el filesystem no soporta `ATOMIC_MOVE`). **Código muerto eliminado:** imports sin uso de `MinecraftClient` en `ItemIconProvider`/`EffectIconProvider`; imports sin uso de `ControllerSelectScreen`/`ClipboardDebugService` en `ActionExecutor` (sobrantes de una versión anterior); accessors estáticos sin ningún call site en `RadialMenuController` (`getSlotCount()`, `hasMultipleWheels()` — verificado con grep de todo el repo antes de borrar). **Hallazgo real, documentado pero NO corregido (demasiado riesgo sin poder validar en hardware):** el almacenamiento de config por-mando (`ConfigManager` — `ControllerConfig`/`BindingConfig`/`RadialConfig`) usa como clave el `handle` sintético de GLFW/SDL3, que es SOLO el índice de slot de joystick (`GlfwControllerProvider.handleForJoystick`) — NO estable entre sesiones si el orden de enumeración cambia (ej. dos mandos conectados, reconexión Bluetooth en otro orden). `ActiveControllerService` YA sabe esto y resuelve el mando ACTIVO por nombre (comentario propio: "unlike the synthetic handle"), pero eso NO migra los archivos de config del handle viejo al nuevo — el usuario podría ver sus keybinds/radial/gyro "reseteados a defaults" sin haber tocado nada, solo por una reconexión con distinto orden. Ver B041 en TODO_BLOCKERS.md para el diseño de fix propuesto (clave estable por nombre+ordinal, mismo patrón ya probado en `ControllerClaimService.keyFor()`). **BUILD SUCCESSFUL + 24/24 TESTS, jar `steampad-0.10.6.jar` en `dist/`.** B040 sigue exactamente donde estaba (ver más abajo) — esta sesión no lo tocó a propósito.

**2026-07-08 (sesión 19 cont. 6 — SIN CÓDIGO, solo análisis: el usuario rechaza F13-F22 como solución final, exige Steam Input "de verdad" en Game Mode).** El usuario probó v0.10.5 y confirmó que el flujo F13-F22 es técnicamente correcto y funciona (no es un "remedio raro" — es la forma oficial de Valve de mapear mandos para apps no integradas), PERO insiste en que eso NO es el objetivo del proyecto: SteamPad debe comportarse como un juego 100% nativo de Steam Input en Game Mode, con las 10 ranuras apareciendo NOMBRADAS directamente en el menú de configuración de mando de Steam (como cualquier AAA compatible), no requiriendo que el usuario mapee manualmente teclas F13-F22 como intermediario. Se aclaró por qué se usa AppID 480/Spacewar (mecanismo oficial de Valve para apps no publicadas, igual para todos los usuarios — no es "solo pruebas"). **Nueva hipótesis (sin confirmar, sin código escrito):** en Game Mode, Steam ya tiene "un juego actual" fijado a nivel de sesión (el acceso directo del usuario, con su propio AppID auto-generado) — cuando Minecraft intenta reclamar independientemente ser el AppID 480 (distinto), probablemente choca con ese seguimiento de "un solo juego activo". Plan propuesto: detectar el AppID real que Steam pasa al proceso (env vars `SteamAppId`/`SteamGameId`) y usarlo en vez de 480 forzado; el mod escribiría su propio VDF con el nombre correcto automáticamente — sin necesidad de "plantilla en el Taller de Steam" (idea del usuario, descartada por más simple/confiable el auto-detectar+auto-escribir). **Diagnóstico pendiente, EN PAUSA:** el usuario no está en casa — necesita correr un comando con Minecraft VIVO en Game Mode (vía SSH o Decky Terminal, confirmado que sirve) para leer las variables de entorno reales del proceso. Detalle completo → B040 (TODO_BLOCKERS.md). NO se tocó código en esta sesión — v0.10.5 sigue siendo el jar vigente.

**2026-07-08 (sesión 19 cont. 5 — v0.10.5: ranuras por tecla F13–F22 + icono del mod). Ver PROGRESS.md y D034.** Validación de v0.10.4 en hardware: **escritorio ✅ PERFECTO** (palabras del usuario: "funciona tal cual lo describes" — 8BitDo de vuelta con nombre real, todo responde). **Game Mode:** attach AUTO se intentó pero `SteamAPI.init()` devolvió **false** (el acceso directo no-Steam de sway ocupa el slot de juego con su AppID propio — no se puede suplantar a Spacewar desde ahí). Fallo BENIGNO: sin attach, Steam mantiene sus gamepads virtuales y ambos mandos funcionaron completos por SDL3; los paddles quedaron en manos de la disposición de Steam (mapeados a A/B). **Respuesta (v0.10.5, D034):** cada Ranura N ahora escucha TAMBIÉN la tecla **F(12+N)** (Ranura 1=F13…Ranura 10=F22) vía `glfwGetKey` — flujo Game Mode: disposición de Steam del acceso directo → paddle → tecla F13 → Ranura 1 → keybind elegido en BOTONES. Sin AppID, sin VDF, sin attach, sin secuestro; funciona en cualquier SO. `steamAttachMode` default → **NEVER** (ALWAYS/AUTO quedan para MC lanzado desde Steam como título real). Etiquetas de ranura muestran su tecla ("Ranura 1 (F13)"); textos ×3 idiomas. **Icono del mod:** `fabric.mod.json` declaraba `icon.png` que nunca existió — generado pixel-art ABXY 128px (pendiente reemplazar por el arte original del usuario cuando lo pase al PC). **BUILD SUCCESSFUL + 24/24 TESTS, jar `steampad-0.10.5.jar` en `dist/`.** Checklist → B039.

**2026-07-08 (sesión 19 cont. 4 — v0.10.4: CAUSA RAÍZ REAL — conectarse a Steam secuestra los mandos; política `steamAttachMode`). Ver PROGRESS.md y D033.** v0.10.3 no bastó: con SDL3 primario el 8BitDo seguía sin aparecer y el Legion mudo (solo stick derecho = era la emulación de RATÓN de Steam, no el mod). **Causa raíz:** al conectar `SteamAPI.init()` con AppID 480, Steam cree que Spacewar está corriendo y TOMA los mandos gestionados para aplicarles esa disposición — mismo claim exclusivo de Game Mode (B032), auto-infligido en escritorio. Paralelo crudo+SteamInput es imposible en un mando gestionado por Steam. **Fix v0.10.4:** `GlobalConfig.steamAttachMode` (AUTO/ALWAYS/NEVER, default **AUTO = conectar solo bajo gamescope/Game Mode**). En escritorio el mod NO se conecta → SDL3 crudo conserva todo (incl. paddles P1..P4, confirmados crudos a las 16:08 — en escritorio se asignan directo en BOTONES, sin Steam). En Game Mode sí conecta (Steam es dueño ahí de todas formas) y las ranuras son la vía de los paddles. Diagnóstico honesto: "Steam API: not attached (desktop: raw input)" en verde + aviso específico en el panel de ranuras (clave `slot_desktop` ×3). **BUILD SUCCESSFUL + 24/24 TESTS, jar `steampad-0.10.4.jar` en `dist/`.** Riesgo abierto para Game Mode y checklist completo: **B038**. Contingencia si Game Mode también muere al conectar: disposición completa de Steam (D033).

**2026-07-08 (sesión 19 cont. 3 — v0.10.3: REVERSIÓN arquitectónica, SDL3 vuelve a ser el backend principal). Ver PROGRESS.md sesión 19 cont. y D032.** Cadena completa de la sesión: (1) v0.10.1 arregló la detección de Steam (falso negativo de `isSteamRunning()`, ✅ validado) — ver más abajo. (2) Al validar en una sesión posterior, Steam Input conectó pero los **ActionSets seguían en 0** pese al VDF confirmado presente — causa: `ISteamController` tarda en aterrizar los handles tras el init incluso con el VDF bien importado (B036) → fix: reintento automático cada ~1s durante ~10s en `SteamBootstrap` (v0.10.2). (3) El retry funcionó (`Input Source: Steam Input`, `Action Sets: loaded`) — pero entonces **el juego se quedó completamente mudo**, ningún botón de ningún mando respondía (B037). **Causa raíz encontrada:** en cuanto los ActionSets se volvieron válidos, `ControllerManager` promovió Steam Input a fuente "activa" para TODO el gameplay (política original de CLAUDE.md, Restricción 1) — pero Steam Input solo reenvía acciones que el usuario mapeó EXPLÍCITAMENTE en el configurador de Steam; como solo se habían mapeado los paddles (a las ranuras), el resto del juego (movimiento, cámara, menús) quedó sin señal. **Decisión (aprobada explícitamente por el usuario vía pregunta directa):** se revierte la Restricción Inamovible 1 — **SDL3/GLFW son ahora SIEMPRE el backend principal de gameplay**; Steam Input corre en paralelo únicamente para las ranuras de paddles (`SteamSlotDispatcher`, que ya leía directo de `SteamInputManager` sin depender de `ControllerManager`, así que no necesitó cambios). Fix en `ControllerManager.refreshCache()`: SDL3 primero, GLFW después, Steam Input solo como último recurso si ninguno ve nada. CLAUDE.md actualizado (Restricción 1 tachada + nota). **BUILD SUCCESSFUL + 24/24 TESTS, jar `steampad-0.10.3.jar` en `dist/`.** Ver D032, B037.

> ### ▶️ REANUDAR AQUÍ (próxima sesión)
> **PRIORIDAD — retomar la investigación de B040 (Steam Input nativo en Game Mode), NO programar hasta
> tener el diagnóstico:**
> 1. Pedirle al usuario el resultado de (con Minecraft VIVO en Game Mode, por SSH o Decky Terminal):
>    ```bash
>    cat /proc/$(pgrep -f "PrismLauncher.AppImage")/environ | tr '\0' '\n' | grep -i steam
>    ```
> 2. Si confirma que Steam pasa `SteamAppId`/`SteamGameId` reales al proceso → implementar: detectar
>    ese AppID en gamescope, usarlo en vez de 480 forzado, y que el mod escriba su propio VDF con el
>    nombre correcto (`game_actions_<APPID>.vdf`) automáticamente. Objetivo: las 10 ranuras aparecen
>    NOMBRADAS en el menú de mando de Steam del acceso directo del usuario, sin choque de AppID.
> 3. Si NO confirma la hipótesis (env vars vacías o no relacionadas) → investigar otra causa del
>    `SteamAPI.init()=false` en Game Mode antes de tocar código.
> 4. B039 (vía F13-F22) sigue como fallback funcional y validado en concepto — no se descarta aunque
>    se logre el Steam Input nativo; documentar ambas vías si el nativo funciona.
>
> **⚠️ PUNTO CLAVE a considerar al continuar con B040 (hallazgo de la auditoría, sesión 20 — ver B041):**
> el config por-mando (`ControllerConfig`/`BindingConfig`/`RadialConfig`) hoy se guarda con clave =
> handle sintético de GLFW/SDL3 (índice de slot de joystick, INESTABLE entre sesiones). Si B040 logra
> Steam Input nativo con las 10 ranuras nombradas, el mando pasará a identificarse en Steam Input por
> su AppID/VDF real — es el momento natural para revisar si el mismo problema de clave inestable aplica
> también al lado Steam Input (¿el handle que expone `ISteamController` para el mismo mando físico es
> estable entre sesiones, o tiene el mismo problema que el synthetic handle de GLFW/SDL3?). Aunque B040
> y B041 son bugs independientes, tocar la identidad "qué es este mando" dos veces en sesiones separadas
> (una para Steam Input nativo, otra para la clave de config) es más arriesgado que resolverlas juntas
> si el diseño de "clave estable por nombre+ordinal" (ya probado en `ControllerClaimService.keyFor()`)
> termina aplicando a ambos casos. No bloquea el diagnóstico de B040, pero considerarlo antes de dar
> B040 por "terminado".
>
> **Pendiente de antes (sin bloquear lo anterior):**
> - Validar v0.10.5 en Game Mode con F13-F22 igual (si se decide usarlo mientras se resuelve B040).
> - Confirmar icono ABXY en ModMenu; reemplazar por el arte original del usuario cuando lo pase por LocalSend.
> - Re-marcar "Predeterminado" en el 8BitDo si no se ha hecho.

**2026-07-08 (sesión 19 — v0.10.0: Steam Input Slots, la vía VDF para los paddles de B032. ⚠️ NADA de esto probado en hardware todavía).** Respuesta directa al diagnóstico de B032: en Game Mode los paddles solo pueden llegar al mod como acciones de Steam Input, así que se crearon **10 ranuras genéricas** (`steampad_slot_1..10` en el VDF, ActionSet InGame). Flujo: el usuario mapea paddle→"SteamPad Slot N" en el configurador de mando de Steam, y en **BOTONES → sección "Steam Input"** (después de Ratón virtual, antes de los mods) asigna a cada ranura cualquier keybind (vanilla o mod) con el picker buscable. Dispatch con semántica **HOLD** (`KeyTap.hold/release` — zoom mantenido funciona), **solo en gameplay** (pantalla abierta = ranuras mudas), con **lectura híbrida**: las ranuras se leen de Steam Input aunque el mando activo esté servido por SDL3/GLFW (el caso real de Game Mode), porque `SteamBootstrap.runCallbacks()` mantiene los estados de Steam frescos cada tick. Asignaciones **globales** (`GlobalConfig.steamInputSlots`, D030) — los handles de Steam no son estables entre sesiones y el mapeo físico vive en Steam por-juego, no por-mando. Nueva clase `input/SteamSlotDispatcher`; UI con fila verde/gris + reset + undo + aviso amarillo si Steam Input no está activo; i18n ×3; test de round-trip añadido. **BUILD SUCCESSFUL + 24/24 TESTS, jar `steampad-0.10.0.jar` en `dist/`.** **REQUIERE re-importar el VDF en Steam** (el viejo no trae las ranuras) — pasos y checklist completo de validación en **B033**. Ver Fix 31, PROGRESS.md sesión 19 y D030.

**2026-07-08 (sesión 18 cont. 2 — diagnóstico REFINADO de paddles/vibración tras prueba en Game Mode).** El usuario probó conectando desde Steam Game Mode (gamescope): **la vibración SÍ funciona ahí** (confirma que en escritorio era 100% el sandbox de Flatpak bloqueando `/dev/hidraw*` — en Game Mode Steam ya tiene acceso privilegiado y reenvía el rumble al hardware). **Los paddles siguen SIN detectarse en Game Mode**, y esto es una causa distinta y más profunda: cuando Steam Input está activo, Steam toma el dispositivo HID físico en modo EXCLUSIVO y expone a las demás apps (incluido SteamPad vía SDL3) solo un **gamepad virtual estándar** sin los botones de paddle — SDL3 nunca puede leerlos crudos mientras Steam Input controla el mando. Los paddles solo se vuelven usables en Game Mode si se mapean dentro de la configuración de mando de Steam (VDF, ver B002) para que Steam Input los reenvíe como una acción del ActionSet — lo cual requiere que el backend activo del mod sea Steam Input (no el fallback SDL3), y eso a su vez requiere que `SteamInputManager.areActionSetsValid()` sea true (VDF importado + Steam Input realmente inicializado). Documentado en B032 (TODO_BLOCKERS.md) y en memoria persistente. **Jar vigente: `steampad-0.9.1.jar`** (imprime el fix de host para el caso de escritorio). Próximo paso si se quiere insistir en paddles dentro de Game Mode: validar el flujo VDF completo de Steam Input end-to-end (B002).

**2026-07-07 (sesión 18 — v0.9.0: feedback de hardware sobre v0.8.0). Ver PROGRESS.md sesión 18.** **(CRASH)** NPE al guardar keybind desde la rueda: el click virtual cierra la pantalla y se consultaba `currentScreen` null — corregido (captura + guard). **(Overlay de acciones)** El botón que cierra un menú ya NO se filtra al gameplay como acción mantenida (supresión de held-buttons hasta soltar, armada al cerrar pantallas y radial) — cubre "A en Volver a partida salta", inventario, ajustes, radial y su editor. **(Teclado)** Selección con esquinas estilo inventario (punto eliminado, tampoco con D-pad) + snap magnético fuerte (12% en movimiento, 45% al aflojar). **(Vibración)** Resultado de `SDL_RumbleGamepad` verificado y logueado (`SDL_GetError`+`hasRumble`); sospecha: sandbox Flatpak bloquea force-feedback. **(Paddles)** Hints `SDL_JOYSTICK_HIDAPI(+_8BITDO)=1` antes de init (el driver HIDAPI es el que expone paddles del Ultimate 2) + diagnóstico `SDL_GamepadHasButton` por botón extra + versión SDL en el log. **(Radial)** Ruedas ilimitadas 1–6 con añadir/eliminar (migración de configs legacy), fila compacta en el editor, LB/RB recorre todas con carrusel, glifos de control EN el overlay (RS/A/LT/RB), gelatina pixel-art estilo BG3 (cuadrados cuantizados 2px + cabeza diamante). **(Log)** Supresión de pausa con rate-limit (1 línea/10 s). El log del usuario CONFIRMÓ: PauseGate suprime el foco-pausa de GameRenderer vanilla y el 8BitDo entra por SDL3. **BUILD SUCCESSFUL + 24/24 TESTS, jar `steampad-0.9.0.jar` en `dist/`.** Validación → B031.

**2026-07-07 (sesión 17 cont. — v0.8.0: teclado Controlify-style + auditoría completa + bloques B/D/E/F). Ver PROGRESS.md sesión 17.** **(Corrección D8b):** con el jar COMPLETO, el camino vanilla foco→pausa SÍ existe en 1.21.10 — `GameRenderer.render` llama `openGameMenu` tras >500 ms sin foco si `pauseOnLostFocus`; el PauseGate de v0.7.3 lo neutraliza (validado en hardware por el usuario). **(Teclado)** Apertura estilo Controlify: auto-abre solo en chat/carteles/libros; en el resto A abre sobre el campo (badge "[A] Keyboard"); stick izq = cursor LIBRE flotante con snap a la tecla más cercana (`KeyboardGeometry` compartida + punto visible). **(F13)** Extra binds ahora con semántica HOLD (`KeyTap.hold/release`) → keybinds de mods con `isPressed()` (zoom) funcionan, con chord. **(F6)** "Probar vibración" enruta por `ControllerManager.rumble` (era Steam-only) y `allowVibration` se respeta. **(D14)** `swallowGuiTick`: un press = una acción al salir de captura. **(D17)** D-pad navega DENTRO de las listas vanilla (mundos/servidores) entrada por entrada; A = Enter sintético. **(B9)** HUD contextual con radial abierto. **(B16)** Radial en grises MC. **(E10)** Segunda rueda con carrusel LB/RB + selector en el editor + i18n ×3. **(E11)** Blob gelatina con easing real. **(Auditoría)** TODA la sección gyro estaba inerte (`configure` sin llamadores) — cableada; también `buttonActivationThreshold` (LT/RT, default 0.5 — el gatillo ya no dispara con rozarlo), `screenRepeatNavigationDelay` (hold-repeat D-pad), `reduceAimingSensitivity`, `autoJump`→vanilla, `showScreenButtonGuide`, `virtualKeyboardAutoShow`; eliminados 3 campos legacy muertos; pendientes-con-causa documentados (vibración por categoría, hdHaptics/B003, guide scale, opciones de servidor). Test obsoleto del radial corregido. **BUILD SUCCESSFUL + 24/24 TESTS, jar `steampad-0.8.0.jar` en `dist/`.** Validación hardware → B030.

**2026-07-07 (sesión 17 — D8b: menú de pausa espurio al perder foco). Ver PROGRESS.md.** Bug crítico: click del mouse físico fuera de la ventana → menú de pausa en gameplay, sin salida por gamepad. **Análisis bytecode 1.21.10:** no existe camino vanilla foco→pausa (`pauseOnLostFocus` muerto, `GameMenuScreen` solo vía Esc) ⇒ el open es espurio (Esc sintético del desktop-layout de Steam Input al perder foco, o edge fantasma de PAUSE en cambio de handle). Además `setScreen(null)` SÍ llama `lockCursor()` en 1.21.10 (premisa de D8 v0.7.1 incorrecta), pero su guard de foco dejaba el cursor libre tras cerrar menús sin foco. **Fix v0.7.3 (paridad Controlify):** (1) `input/PauseGate` — aperturas de pausa propias vía `openPauseMenu()`; mixin en `MinecraftClient.setScreen` suprime `GameMenuScreen` sin foco + mando fallback + no-iniciado-por-SteamPad, logueando el STACKTRACE del origen la primera vez (diagnóstico definitivo en hardware); (2) `MouseMixin` — `@Redirect` de `isWindowFocused()` en `lockCursor`: con `outOfFocusInput` + mando fallback el lock procede sin foco; (3) `GamepadInputDispatcher` — resync de `prevButtons/prevLt/prevRt` al cambiar el handle activo (sin edges fantasma). **BUILD SUCCESSFUL, jar `steampad-0.7.3.jar` en `dist/`.** Validación en hardware pendiente (B029).

**2026-06-26 (sesión 15 — pendientes + B002 + title screen). Ver PROGRESS.md.** **(J1)** ControllerSelectScreen refresca cuando el handle activo cambia (no solo cuando la lista cambia). **(I1)** BindingsScreen: `selected` se actualiza al navegar con D-pad/foco → panel de descripción funciona sin hacer clic. **(H1)** Teclado virtual en ChatScreen se desplaza 16 px hacia arriba para no tapar el campo de chat. **(F1)** SDL3: log diagnóstico al abrir gamepad y al detectar botones extra (paddles/misc). **(B002)** Steam Input dispatch completo: movimiento analógico vía `ControllerInputState`, ATTACK/USE held vía `setKeyPressed`, `ControllerInputState.clear()` al abrir pantallas, casos faltantes en `ActionExecutor` (PAUSE, DROP_STACK, GUI_NAV_*, GUI_NEXT/PREV_TAB). **(TitleScreen)** `TitleScreenMixin`: glifos X/B al lado de Opciones/Salir; `GamepadInputDispatcher`: X→focus/click Opciones, B→focus/click Salir (two-press), general B-close omitido en TitleScreen. **BUILD SUCCESSFUL, jar `steampad-0.6.0.jar` actualizado.**

**2026-06-26 (sesión 14 — fixes de hardware real). Ver PROGRESS.md.** Correcciones tras pruebas en 8BitDo Ultimate 2 Wireless / Bazzite / Prism Launcher Flatpak: **(A1)** Sprint default corregido L3 (era R3). **(B1)** Virtual mouse click: hold-based cuando cursor visible (press/release por separado para drag de ítems en inventario). **(C1)** Chord capture: Select cancela la captura (además de Esc); chord mostrado como "A+B" en la fila de la lista (modificador+trigger juntos); chord square muestra "✓" cuando hay chord. **(D1-D6)** Teclado virtual rediseñado: activación MANUAL (A abre, B cierra solo el teclado); atajos completamente aislados (no leaks a gameplay/menú); preview strip eliminado; footer con glifos de marca por acción; stick diagonal + más rápido (cooldown 1, threshold 0.3); teclado ligeramente más alto (≥80px, ≥22%); badge "[ A ] Keyboard" cuando hay campo activo. **(E1)** Ghost controllers: `ControllerManager` solo usa Steam Input cuando VDF importado (`areActionSetsValid()`), en otro caso cae a SDL3/GLFW. **BUILD OK, jar `steampad-0.6.0.jar` 1.49 MB en `dist/`.**

**2026-06-26 (sesión 13 — texturas de botones por marca + versión a 0.6.0). Ver Fix 29 y `PROGRESS.md`.** Los glifos de botón ahora son **PNG 64×64 por marca** (Xbox, PS, Steam Deck, 8BitDo, Xbox Elite, genérico) y se eligen **según el control conectado**, en TODAS las interfaces: ajustes/BindingsScreen (`ButtonIcon`), HUD de gameplay e inventario (`ControllerGlyphs`) y selector (`ControllerBrandIcon` ahora usa `controller.png`, la silueta de cada marca, en vez de los logos vector). Nuevo `ButtonTextureManager` (resuelve marca+id→textura, fallback a genérico→vector, caché de existencia). 210 PNG empaquetados. Sin cambios en lógica de input. **BUILD OK, jar `steampad-0.6.0.jar` 1.42 MB en `dist/`.**

**2026-06-26 (sesión 12 — auditoría + UI unificada en columnas + sliders finos + optimización + versión a 0.5.0). Ver Fix 28 y `PROGRESS.md`.** Todas las pantallas de ajustes (mando Básico/Avanzado, Global, Teclado) ahora usan el **layout de dos columnas estilo Botones**: lista a la izquierda + **panel de descripción a la derecha** según la opción enfocada/hover. Toggles on/off ahora son **interruptores visuales** (verde/rojo, `SteamToggle`). **Sliders** con estilo propio (`SteamSlider`) **ajustables al milímetro con el stick derecho** (suave=fino, fuerte=rápido) cuando están enfocados, y el **scroll ya no cambia valores**. **Badge** en el selector de mandos (esq. sup. der.): versión, ElDon, bandera de México, bandera de Yucatán y corazón. **Optimización**: caché de 80 ms en `ControllerManager` (evita re-enumerar SDL/GLFW por frame). **BUILD OK, jar `steampad-0.5.0.jar` 1.29 MB en `dist/`.**

**2026-06-26 (sesión 11 — Teclado virtual AAA dirigido por mando + versión a 0.4.0). Ver Fix 27 y `PROGRESS.md`.** Teclado en pantalla estilo retro-MC (~1/5 inferior, no invasivo) que escribe en cualquier campo de texto (juego + mods) vía `Screen.charTyped`/`keyPressed`. Auto-aparece al enfocar un campo. Atajos: **A**=tecla, **Y**=espacio, **X**=borrar, **RT**=enter, **LT**=shift, **LB/RB**=cursor; **cruceta**=letra a letra, **stick izq**=snap rápido entre letras (no ratón virtual); **Back** alterna teclado/widgets, **B** cierra. Capas letras/símbolos, franja de previsualización (ver qué se escribe, incl. chat). Sección "Teclado" en Ajustes globales (`KeyboardSettingsScreen`: activar, auto-mostrar, sonidos, altura). **BUILD OK, jar `steampad-0.4.0.jar` 1.29 MB en `dist/`.**

**2026-06-26 (sesión 10 — lote de 9 secciones de bugs de hardware + versión a 0.3.0). Ver Fix 26 y `PROGRESS.md`.** Resumen: (S1) ratón virtual AUTO **cede al ratón físico** al moverlo de verdad y se re-activa con el stick (donde estaba el puntero). (S2) menú radial **ejecuta todos los tipos** (KEYBIND/SCREEN/SUBMENU), no solo comando, vía nuevo `KeyTap`. (S3) editor radial: el campo "Tipo" muestra solo el nombre (no "Tipo: …") + descripción por tipo. (S4) zona de mods en BOTONES **agrupada por mod** y "Others"→"Otros"; con chord. (S5) **todas** las acciones (incl. mods) tienen chord. (S6) configurar chord ahora **captura 2 botones** (modificador + gatillo). (S7) los chords **anulan la acción base** del botón gatillo en gameplay (A=salto; DRIGHT+A=Mapa ⇒ no salta). (S8) el mando **predeterminado se auto-selecciona** al iniciar y al conectarse en caliente (por nombre, estable). (S9a) **Steam Input**: se **crea `steam_appid.txt`** automáticamente (faltaba) → puede inicializar con Steam corriendo; (S9b) marca genérica mejorada (resto, marcas estilizadas originales); (S9c) **botones extra de 8BitDo** (paddles/misc) leídos por SDL3 y asignables. **BUILD OK, jar `steampad-0.3.0.jar` 1.27 MB en `dist/`.**

**2026-06-25 (sesión 9 — lote de 5 bugs reportados en hardware + versión a 0.2.0). Ver Fix 25.** Resumen: (1) **Menú radial ahora ejecuta** — la selección se volvió *sticky* (al soltar el stick conserva el sector elegido en vez de resetear a -1, que era por qué ni ON_CLICK ni ON_RELEASE disparaban) + **A activa** el slot resaltado al instante. (2) **Sensibilidad del ratón virtual ×0.4** (`BASE_SPEED_PER_SEC` 850→340); el valor por defecto sigue siendo 1.0 pero ahora más lento/estable. (3) **Vibración al iniciar**: el mando por defecto restaurado de config (o, si no está conectado, el auto-seleccionado) ahora vibra una vez al arrancar (antes solo vibraba en hot-plug). (4) **Fabricante en la tarjeta del mando**: la sub-línea ya no dice "GENERIC" sino el fabricante (8BitDo, Sony, Microsoft, Nintendo, Valve…) vía `ControllerBrandIcon.manufacturer()`. (5) **Diagnóstico Steam más honesto**: cuando un fallback (SDL3/GLFW) está sirviendo mandos, la línea "Steam API" pasa de rojo a amarillo y el texto de ayuda nombra la fuente REAL (SDL3, antes decía siempre "GLFW") en vez de tratar el contexto no-Steam como fallo duro. Extra: arreglado el "Version: null" del log (se lee de FabricLoader). **BUILD OK, jar `steampad-0.2.0.jar` 1.32 MB en `dist/`.**

**2026-06-25 (sesión 8 — convivencia mouse físico/gamepad, rework del menú radial al estilo velolib/radial, y más arreglos) — (1) HUD de gameplay ahora deriva los glifos de los binds reales (`GameplayHudOverlay` lee `GamepadBinds`) → ya muestra Y para inventario, no X. (2) Selector del radial ya NO invertido (fórmula de ángulo de la referencia, sin negar Y). (3) Inventario: A coloca/suelta (se añadió `mouseReleased` al clic simulado → ya no queda pegado). (4) Pistas de botones del inventario ampliadas (A tomar/poner, X mitad, Y mov. rápido, B cerrar, Select cursor) + Y cablea quick-move vía `interactionManager`. (5) Ratón virtual optimizado a fondo con `InputRouter` (dispositivo activo gamepad/ratón): se cancelan los movimientos "fantasma" del ratón físico en menús, el puntero del SO se oculta cuando el mando tiene el control y reaparece al mover el ratón de verdad. (6) Puntero más pequeño (r=2). (7) Selección de casilla y radial en BLANCO. (8) La notificación de modo aparece ~2.5 s y se desvanece. (9) Modo del ratón virtual recordado POR CONTEXTO (clase de pantalla). (10) La cruceta oculta también el puntero físico. (11) Convivencia ratón físico/gamepad + entrada mixta (glifos se ocultan al mover el ratón si la entrada mixta está apagada; en gameplay el ratón sigue activo). (12) En BOTONES ahora aparecen TODOS los keybinds (vanilla + mods) agrupados por categoría → los mods se pueden mapear. (13–17) **Menú radial reescrito** inspirado en velolib/radial: editor con campos dinámicos por tipo, picker de keybinds, picker con TODOS los iconos de MC (buscable), etiquetas Nombre/valor, slots configurables 2–12 distribuidos equitativamente, y entrada al editor desde gameplay (seleccionar un slot + LT). Ver Fix 23. BUILD OK, jar 1.26 MB en `dist/`.**

**2026-06-25 (sesión 7 — pulido de ratón virtual + navegación + reestructura del menú BOTONES) — Ratón virtual: corregida la sensibilidad (doble multiplicación ×64 eliminada; ahora una sola pasada, default 1.0, slider 0.2–3.0 en Básico y en la sección Mouse virtual) y el lag (onCursorPos solo al cambiar la posición; glfwSetInputMode solo en transición, no por frame; snap solo cuando el stick movió el cursor). Cursor = punto blanco mediano con sombra (sin azul). Notificación arriba-izq con el modo del ratón (Activo/Desactivado/Auto). Doble selección corregida (al mostrar cursor se limpia el foco; al navegar con cruceta el cursor del SO se sincroniza al widget enfocado). Snap a widgets en cualquier menú (`WidgetSnap`). Navegación de cruceta ESPACIAL (arriba/abajo/izq/der por geometría, no orden lineal) en pantallas SteamPad y vanilla. Inventario: cursor por defecto; la cruceta salta casilla a casilla (`SlotSnap.moveToNeighbor`). Nombre del mando ahora visible en el encabezado de todas las pantallas. **Menú BOTONES reestructurado** (4 zonas): pestañas Básico/Botones/Avanzado (LB/RB), lista categorizada (Movimiento/Gameplay/Inventario/Misc/Interfaz/Radial/Ratón virtual + sección dinámica de keybinds de MODS) con icono del botón asignado + cuadrado Reiniciar + cuadrado Chord por fila, y panel lateral con acción seleccionada + descripción + botón ligado + Reiniciar todo/Deshacer/Aceptar. Iconos de botón monocromos propios (`ButtonIcon`: sticks con L/R, cruceta, gatillos, bumpers, caras, flechas de stick). Binds ampliados y configurables (`GamepadBinds` + `ActionCatalog`): nuevos GYRO_TOGGLE, DROP_STACK, PICK_BLOCK, PLAYER_LIST, SCREENSHOT, HUD_TOGGLE, con chords por bind y "extra binds" para disparar cualquier keybind de mod desde un botón. Ver Fix 22. BUILD OK, jar 1.25 MB.**

**2026-06-25 (sesión 6 cont. — sistema de cursor estilo Controlify + binds + 3 pestañas + blur) — Ratón virtual reescrito (modos OFF/ON/AUTO con Select; suprime el mouse físico vía MouseMixin para que no peleen; cursor de punto + brackets de esquina en inventario; render encima de ítems vía ScreenEvents). Navegación: cruceta=foco con auto-scroll a opciones ocultas, stick der=scroll, LB/RB=pestañas, B=atrás, A=confirma. 3 pestañas (Básico/Configurar botones/Avanzado) + radial movido. Capa de binds físicos configurable (GamepadBinds) → el gameplay es config-driven con defaults Bedrock + REBIND real (clic acción → presiona botón → guarda) con glyphs. Blur nativo de MC en todas las pantallas. Gyro off. Pistas de botones en inventario. Selección: tarjetas arriba (nombres) + panel al fondo. Ver Fix 21. BUILD OK, jar 1.21 MB.**

**2026-06-25 (sesión 6 — feel de gameplay + UI responsive) — ¡Arranca y detecta por SDL3, confirmado por el usuario! Fixes de feel: movimiento ANALÓGICO (stick suave = caminar lento), cámara POR-FRAME (rápida y fluida, ya no a 20 Hz), cursor virtual VISIBLE + ocultar mouse físico, rumble más corto, gyro OFF por defecto. UI: causa raíz de "no se ve nada" = anchos absolutos (±200px) se salían de la pantalla estrecha del Deck → pantallas de bindings y selección hechas RESPONSIVE; botón Default (recordar mando). Ver Fix 20. BUILD OK, jar 1.20 MB. Pendiente: 3 pestañas, blur nativo, captura de rebind, nav LB/RB.**

**2026-06-25 (sesión 5 — FIX crash de arranque) — Crash de inicio corregido: GamepadMappings/SDL3 se inicializaban en `onInitializeClient` (ANTES del glfwInit de MC) → "GLFW error before init". Diferido al primer tick. CONFIRMADO en log: el mod carga y detecta el mando por SDL3 (`source: SDL3`), solo fallaba el init de GLFW. Ver Fix 19. BUILD SUCCESSFUL, jar 1.19 MB (12:29).**

**2026-06-25 (sesión 5 — glyphs/logos/radial) — Glyphs por tipo de mando, logos de marca, radial cableado al fallback + rediseñado + editor funcional. Ver Fix 18.**

**2026-06-25 (sesión 5 — detalles Bedrock/AAA) — Rumble al conectar, modo cursor/foco con Select, snap a casillas de inventario, HUD de botones en gameplay. Ver Fix 17.**

**2026-06-25 (sesión 5 final) — B018 MIGRADO COMPLETO: TODAS las pantallas con tema fresco + traducciones + navegación por foco estilo Bedrock. Bug de bucle de Back CORREGIDO. Ver Fix 16.**

**2026-06-25 (sesión 5 cont.) — Multi-backend completo (Steam→SDL3→GLFW), 8BitDo (Ultimate 2/Pro 3/SN30) vía mapeos + joystick crudo, UI renovada base, i18n es-MX/es-ES con descripciones. Ver Fix 15.**

**2026-06-25 (sesión 5) — Crash de render RESUELTO (confirmado por usuario). Bug de detección de mandos atacado de raíz + el mando ahora CONTROLA el juego sin Steam:**
- **Detección:** fallback GLFW (`GlfwControllerProvider` + fachada `ControllerManager`). Steam Input no inicializa bajo Prism/Flatpak (`SteamAPI.init()` → "no appID found").
- **Input de gameplay:** `GlfwInputDispatcher` conduce el juego vía `KeyBinding` (movimiento, cámara, minar/usar, salto, sneak, sprint, hotbar, inventario, tirar, perspectiva, chat, pausa).
- **Auto-activación:** el primer mando detectado se activa solo al iniciar (sin pasar por la pantalla de selección).
- **Navegación de menús:** cursor virtual con stick izq., A = clic, B = atrás, bumpers = scroll.
- **i18n:** archivos `es_mx.json` y `es_es.json` creados.
- BUILD SUCCESSFUL. ⚠️ PENDIENTE validación en equipo real. Falta refactor de UI (encabezados/estilo Bedrock) + convertir literales a claves + descripciones por opción.

**2026-06-24 (sesión 4) — CAUSA RAÍZ REAL del NoSuchMethodError identificada y corregida: el build apuntaba a 1.21.4 mientras MC corre 1.21.10. Migración de build a 1.21.10 (gradle.properties), recuperación de cache Loom corrupto por apagón, access widener para Mouse.onCursorPos. BUILD SUCCESSFUL contra 1.21.10 y verificado a nivel de bytecode. CONFIRMADO sin crash por el usuario en sesión 5.**

> **Nota de continuidad (apagón sesión 3→4):** La sesión 3 quedó interrumpida a mitad de la migración a 1.21.10. `gradle.properties` ya estaba editado a 1.21.10 pero el JAR nunca se reconstruyó (el que crasheó era el de 1.21.4), y el cache de Loom (`minecraft-merged-*.jar`) quedó truncado/corrupto. La sesión 4 completó la migración. Ver Fix 12.

---

## Clasificación del Artefacto Actual

**BUILD TÉCNICO FUNCIONAL** — No es MVP jugable aún.

| Criterio | Estado |
|----------|--------|
| Compila sin errores | ✅ |
| Produce JAR | ✅ |
| Tests unitarios pasan | ✅ 24/24 |
| Carga en Minecraft real | ⚠️ NO VERIFICADO |
| Funciona con controlador físico | ⚠️ NO VERIFICADO |
| Funciona en Linux/SteamOS | ⚠️ NO VERIFICADO |
| MVP jugable | ❌ Requiere validación manual |
| Release candidate | ❌ |

---

## Fase Actual

**ENTRE FASE 5 Y FASE 6** — Todo el código de implementación está escrito y compila.
Las Fases 0–5 están **completadas a nivel de código**.
La Fase 6 está **parcialmente completada**: tests unitarios pasan, tests manuales NO ejecutados.

---

## JAR Final

```
Ruta:     C:\Users\RChe\.gradle\controlify-build\steampad\_\libs\steampad-0.18.1.jar
Ruta dist: C:\Dev\Steampad\dist\steampad-0.18.1.jar  (exportación automática vía tarea Gradle exportToDist)
Versión:  0.18.1
Target:   Minecraft 1.21.10 + Yarn 1.21.10+build.3 + Fabric API 0.138.4
Generado: 2026-07-09 (sesión 24 cont. 4 — v0.18.0 + fix de gatillos en el aire: el flanco del gatillo ahora registra el evento de click como el mouse, no solo el estado sostenido)
```

**Nota:** el `dist/` aún contiene todos los jars de sesiones anteriores. Al copiar a `mods/`, usar **`steampad-0.18.1.jar`** y **borrar** los anteriores para que no carguen dos copias.

**Nota sobre el path:** El JAR está en el directorio de Gradle global (`controlify-build`), no en `build/libs/` relativo al proyecto. Esto se debe a la configuración del wrapper de Gradle en el sistema. El JAR es válido y completo.

**ACCIÓN REQUERIDA DEL USUARIO:** Copiar este JAR nuevo a la carpeta `mods/` de la instancia de Prism, **reemplazando** el `steampad-0.1.0.jar` viejo (el de 1.21.4 que causaba el crash). El nombre del archivo es idéntico, así que hay que sobreescribirlo.

**Toolchain de build (sesión 4):**
- **Gradle 8.14** (obligatorio — Fabric Loom 1.13.6 requiere Gradle ≥8.14; con 8.12.1 el build falla en configuración). Launcher: `C:\Users\RChe\.gradle\wrapper\dists\gradle-8.14-bin\38aieal9i53h9rfe7vjup95b9\gradle-8.14\bin\gradle.bat`
- **JDK 21** vía `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot` (Java 25 rompe el build — ver B011).
- Comando: `gradle -p C:\Dev\Steampad build --no-daemon -x test`

---

## Qué Se Completó Realmente

### Infraestructura de build
- `build.gradle`, `settings.gradle`, `gradle.properties` configurados y funcionales
- Gradle 8.12.1 + Java 21 (Temurin)
- Fabric Loom 1.9.2 con access widener (`steampad.accesswidener`)
- Steamworks4j 1.9.0 bundled en el JAR final
- `fabric.mod.json` + `steampad.mixins.json` + 4 Mixins registrados

### Código implementado (65 archivos Java, todos compilan)
- **Steam layer:** SteamNativeLoader, SteamBootstrap, SteamInputManager (via ISteamController), SteamActionRegistry, SteamControllerHandleRef, SteamHapticsService, SteamGlyphService, SteamRuntimeDiagnostics
- **Platform detection:** EnvironmentReport, LinuxRuntimeInspector, GamescopeDetector, SteamDeckDetector
- **Input system:** ControllerState, InputAction (70+ acciones), InputBinding, ChordInput, ChordResolver, DeadzoneProcessor, InputDispatchContext, InputBindingManager, ActionExecutor, VirtualMouseController, GyroHandler
- **Config:** GlobalConfig, ControllerConfig, BindingConfig, RadialConfig, ConfigManager (load/save/autosave)
- **Services:** ActiveControllerService, ControllerIsolationService, BatteryMonitorService, ClipboardDebugService, UiSoundService
- **Screens:** ControllerSelectScreen, GlobalSettingsScreen, ControllerSettingsScreen, ControllerBasicSettingsScreen, ControllerAdvancedSettingsScreen, BindingsScreen, CalibrationScreen, RadialEditorScreen
- **Radial:** RadialSlot, RadialActionType, RadialConfig, RadialMenuController, RadialMenuOverlay, RadialRenderer, RadialIconResolver, ItemIconProvider, EffectIconProvider, CharacterIconProvider
- **Mixins:** MinecraftClientMixin (shutdown), GameRendererMixin (placeholder vacío — sin @Inject), MouseMixin (vmouse), ScreenMixin (button guide)
- **Compat:** MalilibCompat (soft dep runtime detection), SDLFallbackProvider (stub documentado)

### Tests unitarios (24/24 PASSED — ejecutados 2026-06-24)
| Suite | Tests | Resultado |
|-------|-------|-----------|
| ConfigSerializationTest | 6 | ✅ PASSED |
| ChordResolverTest | 7 | ✅ PASSED |
| DeadzoneProcessorTest | 8 | ✅ PASSED |
| ControllerIsolationServiceTest | 3 | ✅ PASSED |
| **Total** | **24** | **✅ 24/24** |

---

## Qué NO Se Verificó

### Funcionalidad no validada en runtime (requiere entorno real)
- Carga del mod en Minecraft 1.21.4 (¿arranca sin error?)
- SteamBootstrap.init() con Steam corriendo (¿devuelve true?)
- SteamInputManager detectando controladores físicos
- Pantallas de configuración en juego (¿se abren?, ¿son navegables con gamepad?)
- Bindings básicos en gameplay (movimiento, salto, ataque)
- Virtual mouse en GUIs
- Chords en juego (chord dispara, simple no)
- Menú radial (apertura, navegación, ejecución de slot)
- Gyro controlando cámara
- Vibración/haptics
- Persistencia real de configs entre reinicios
- Comportamiento en Linux / SteamOS / Gamescope / Steam Deck

### Features implementadas pero sin validación funcional
- GyroHandler (flick stick en particular)
- RadialEditorScreen (UI existe, ¿funciona la edición?)
- CalibrationScreen (UI existe, visualización de sticks)
- ButtonGuideWidget en ScreenMixin
- BatteryMonitorService (notificaciones de batería)
- SteamGlyphService (glifos de botones en UI)
- MaLiLib soft dependency

---

## Runtime Fixes Aplicados (2026-06-24)

### Fix 1 — NoSuchMethodError: KeyBinding constructor (MC 1.21.10)
**Archivo:** `SteamPadClient.java` — nuevo método `createOpenMenuKeyBinding()`
**Mecanismo:** Reflexión en runtime para detectar cuál constructor de `KeyBinding` existe:
- MC 1.21.10: constructor `(String, int, Category)` donde `Category` es un Record con campos estáticos propios → usa reflexión para obtener una constante y pasar como Category object
- MC 1.21.4: fallback al constructor clásico `(String, InputUtil.Type, int, String)`
**Log en 1.21.10:** `[SteamPad] Using MC 1.21.10+ KeyBinding constructor (Category object).`
**Log en 1.21.4:** `[SteamPad] Using MC 1.21.4 KeyBinding constructor (String category).`

### Fix 2 — SteamException: Native libraries not loaded
**Archivo:** `SteamNativeLoader.java` — reescrito completamente
**Causa raíz:** El código anterior hacía `Class.forName("SteamAPI")` sin llamar `SteamAPI.loadLibraries()`. Las natives del JAR bundled nunca se extraían, por lo que `SteamAPI.init()` fallaba con "Native libraries not loaded".
**Fix:** Llama `SteamAPI.loadLibraries()` explícitamente (o `SteamAPI.loadLibraries(path)` si hay `customNativesPath` configurado).
**Agregado:** Detección de entorno Flatpak (`/.flatpak-info`, `FLATPAK_ID`, `container=flatpak`) con instrucciones de workaround.
**Log éxito:** `[SteamPad] Steam natives loaded OK.`
**Log fallo Flatpak:** `[SteamPad] Flatpak container detected.` + guía de paths alternativos.
**Log fallo nativo:** `[SteamPad] SteamAPI.loadLibraries() FAILED: <mensaje>` + instrucciones.

### Fix 4 — GameRendererMixin crash de startup (descriptor @Inject incorrecto)
**Archivo:** `src/main/java/dev/steampad/mixin/GameRendererMixin.java`
**Síntoma:** Crash al arrancar MC. Mixin no encontraba `renderWorld(float, long)` y con `defaultRequire: 1` abortaba la carga del mod.
**Causa raíz:** El handler declaraba `(float tickDelta, long limitTime, CallbackInfo ci)` — firma de MC pre-1.21.x. La firma real en 1.21.4 y 1.21.10 es `method_3192(class_9779, boolean)V`. Mixin usa los tipos del handler para construir el descriptor del target y no encontró ningún match.
**Decisión:** Eliminar el `@Inject` por completo. El body era vacío — el overlay radial ya lo registra `HudRenderCallback.EVENT` en `SteamPadClient.java:87`. La clase queda como `@Mixin(GameRenderer.class)` vacío (ver D018).
**Build verificado:** `BUILD SUCCESSFUL` con `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot gradle build --no-daemon -x test`.
**Runtime:** ⚠️ NO VERIFICADO en MC real — fix es de build/Mixin, no de lógica de juego.

### Fix 5 — Crash "Can only blur once per frame" al abrir ControllerSelectScreen (INTENTO FALLIDO → ver Fix 7)
**Archivo:** `ControllerSelectScreen.java`
**Intento sesión 1 (fallido en MC real):** `shouldPause() = true` + flag `backgroundRendered` + override de `renderBackground()` con guard.
**Por qué falló:** El flag guard tenía `backgroundRendered = false` al inicio de `render()`. En MC 1.21.10, `Screen.method47413` (wrapper de render) llama `renderBackground()` ANTES de llamar `render()`. La primera blur pasa (flag → true). Luego `render()` resetea el flag a `false` y llama `renderBackground()` nuevamente → segunda blur → crash. El reset invalidaba la protección.
**Estado:** REEMPLAZADO completamente por Fix 7 (SteamPadBaseScreen). Ver D021.

### Fix 7 — Crash blur: solución definitiva via SteamPadBaseScreen (2026-06-24 sesión 2)
**Archivos afectados:**
- CREADO: `src/main/java/dev/steampad/screen/SteamPadBaseScreen.java`
- MODIFICADO: todos los 8 screens del mod extienden `SteamPadBaseScreen` en lugar de `Screen`
- `ControllerSelectScreen`: eliminado flag `backgroundRendered`, eliminado override `renderBackground()`, `render()` simplificado
**Causa raíz real:** En MC 1.21.10, el pipeline de render del juego (via `Screen.method47413`) aplica un blur pass ANTES de invocar el `render()` de la screen — incluso con `shouldPause() = true`. Cualquier llamada posterior a `super.renderBackground()` intenta un segundo blur en el mismo frame y falla con el guard de MC (`"Can only blur once per frame"`).
**Diagnóstico del stack trace:**
```
method47413 (Screen wrapper) → blur1 OK
  → nuestro render() → this.renderBackground() → super.renderBackground() → blur2 CRASH
```
**Fix:** `SteamPadBaseScreen.renderBackground()` sobreescribe con `context.fillGradient(0,0,width,height,0xC0101010,0xD0101010)`. Nunca llama `super`. Seguro de invocar N veces por frame sin efectos secundarios. Todos los screens del mod extienden esta clase.
**Por qué funciona:** Sin llamada a `super.renderBackground()`, el sistema de blur de MC nunca es tocado por el código del mod. El mundo/pipeline puede aplicar su propia blur pass sin conflicto.
**Visualmente:** Fondo de gradiente oscuro (en lugar de blur + dim). Equivalente funcional para una pantalla de ajustes. Sin cambio perceptible en Gamescope.
**Build verificado:** `BUILD SUCCESSFUL in 18s` con Java 21 (2026-06-24).
**Runtime:** ⚠️ NO VERIFICADO todavía en MC real con este fix.

### Fix 8 — IllegalFormatConversionException: origen identificado como vanilla MC (2026-06-24)
**Síntoma:** `IllegalFormatConversionException: f != java.lang.Integer` en el crash report, campo "Screen size: ERROR".
**Causa:** El crash reporter de MC 1.21.10 usa `%f` donde debería usar `%d` para las dimensiones de la ventana (bug en el generador de crash reports vanilla). No es código del mod.
**Verificación:** Revisados todos los `String.format()` del codebase del mod — usan tipos correctos. `LogUtil` usa SLF4J `{}`, no format especifiers Java.
**Estado:** No requiere acción del mod. Desaparece cuando el crash primario (blur) se elimina. Ver B013 resuelto en TODO_BLOCKERS.md.

### Fix 9 — Detección temprana de controladores en startup (2026-06-24)
**Archivo:** `src/main/java/dev/steampad/client/SteamPadClient.java`
**Cambio:** Paso 4.5 añadido en `onInitializeClient()` — llama `SteamInputManager.getConnectedControllers()` después de `ActiveControllerService.restoreFromConfig()`.
**Nota:** Best-effort. Steam requiere al menos un ciclo de `runCallbacks()` para reportar controladores. Puede retornar 0 en el primer intento incluso con controladores conectados. Los ticks subsiguientes actualizan el estado. El log indica cuántos se encontraron en el intento inicial.

### Fix 6 — Botón de entrada a SteamPad en Options: text button → icon button junto a "Controls" (2026-06-24)
**Archivo:** `src/main/java/dev/steampad/mixin/OptionsScreenMixin.java`
**Cambio:** Reemplazado botón de texto 120×20 en esquina inferior izquierda por `GamepadButton` (20×20, subclase de `ButtonWidget`) posicionado a la derecha del botón "Controls".
**Método de localización:** Iterar `this.children()` buscando `ButtonWidget` cuyo content es `TranslatableTextContent("options.controls")` — locale-independent.
**Fallback:** Posición (4, height-24) si Controls no se encuentra.
**Icono:** Silueta de gamepad con `DrawContext.fill()` — sin assets PNG externos.
**Accesibilidad:** Narración vía mensaje translatable; tooltip visible en hover.
**Build verificado:** `BUILD SUCCESSFUL in 17s` con Java 21.
**Runtime:** ⚠️ NO VERIFICADO en MC real.

**Problema de entorno descubierto durante el fix:**
Java 25.0.3 en PATH rompe `gradle test` en el paso de configuración (`DefaultTestTaskReports / Type T not present`). El build con `-x test` también falla porque Gradle configura la tarea antes de excluirla. Solución: `set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot` antes de cualquier comando Gradle. Ver B011 en TODO_BLOCKERS.md.

### Fix 10 — NoSuchMethodError: TODAS las sobrecargas de drawTextWithShadow eliminadas en MC 1.21.10 (2026-06-24 sesión 3, dos iteraciones)
**Archivos afectados:**
- `ControllerSelectScreen.java`, `BindingsScreen.java`, `CalibrationScreen.java`, `RadialEditorScreen.java`
**Causa raíz real (confirmada en MC real, dos crashes):**
- Intento 1: `drawTextWithShadow(TextRenderer, Text, ...)` = `method_27535` → ELIMINADO en 1.21.10
- Intento 2: `drawTextWithShadow(TextRenderer, OrderedText, ...)` = `method_35720` → TAMBIÉN ELIMINADO
- TODAS las sobrecargas de `drawTextWithShadow` fueron eliminadas en MC 1.21.10.
**Fix definitivo:** `ctx.drawText(textRenderer, Text.literal(str), x, y, color, true)` — el método base `drawText(TextRenderer, Text, int, int, int, boolean)` con shadow=true. Garantizado estable porque `drawCenteredTextWithShadow` lo invoca internamente y está confirmado funcionando en MC 1.21.10.
**Regla permanente:** En este mod, NUNCA usar `drawTextWithShadow` en ninguna forma. Siempre `drawText(..., true)` o `drawCenteredTextWithShadow`.
**Build verificado:** `BUILD SUCCESSFUL in 19s` con Java 21 (2026-06-24).
**Build verificado:** `BUILD SUCCESSFUL in 22s` con Java 21 (2026-06-24).
**Runtime:** ⚠️ NO VERIFICADO en MC real.

### Fix 11 — Diagnóstico Steam AppID y botón Retry Steam Init (2026-06-24 sesión 3)
**Archivos afectados:** `ControllerSelectScreen.java`, `SteamBootstrap.java`
**Cambios en ControllerSelectScreen:**
- `checkAppIdFileExists()`: verifica AMBAS variantes — `steam_appid.txt` Y `steamappid.txt` — en directorio de trabajo actual Y en `client.runDirectory`
- `init()`: cuando Steam no disponible y natives cargadas, loguea las rutas exactas donde se espera el AppID file
- Panel diagnóstico Línea 1: distingue "no AppID file" de "Steam not running" (antes ambas mostraban "Steam not running")
- `buildHelpMessage()`: cuando falta AppID, muestra la ruta real del run directory (truncada) en lugar de instrucción genérica; cuando AppID existe pero Steam no corre, pide explícitamente iniciar Steam
- `shortRunDir()`: helper para truncar rutas largas a 45 chars
- `retrySteamInit()`: nuevo método que llama `SteamBootstrap.init()` y luego `refresh()`
- Botón "Retry Steam Init": añadido en `init()` cuando Steam no disponible, natives cargadas, y 0 controllers
- Estado vacío: cuando Steam no disponible, muestra mensaje específico ("Steam not initialized") en lugar de "No controllers detected"
**Cambios en SteamBootstrap:**
- Guard `if (steamAvailable) return true` al inicio de `init()` — evita doble-init y hace el retry desde UI safe
- Log del directorio de trabajo (`System.getProperty("user.dir")`) cuando `SteamAPI.init()` devuelve false
- Hint sobre ambas variantes de filename en el log
**Build verificado:** `BUILD SUCCESSFUL in 22s` con Java 21 (2026-06-24).
**Runtime:** ⚠️ NO VERIFICADO en MC real.

### Fix 29 — Texturas de botones por marca + versión 0.6.0 (2026-06-26 sesión 13) ✅ CÓDIGO / ⚠️ runtime por validar
Detalle por check en `PROGRESS.md` (B1–B6). El usuario entregó los PNG (64×64, transparentes) organizados por marca; integrados sin tocar la lógica de input.

- **B1 — Assets:** copiados de `import_buttons/` a `assets/steampad/textures/buttons/{xbox,ps,steam,8bitdo,xbox_elite,generic}/`. 210 PNG empaquetados en el jar (verificado con `unzip -l`). Cada carpeta trae los botones base + extras propios (steam: trackpads, l4/l5/r4/r5, quickaccess; 8bitdo: m1/m2 + paddle1-4; xbox_elite: paddle1-4) + `controller.png` (silueta de marca).
- **B2 — `client/ui/ButtonTextureManager`:** `resolveButton(id)` (marca activa → genérico → null), `resolveSilhouette(type,name)`, `brandFolder(type,name)`, `stemFor(id)` (id interno → nombre de archivo). Caché de existencia (chequeo vía resource manager 1 sola vez por clave). PNG = 64×64; MC cachea por `Identifier` al dibujar.
- **B3 — `ButtonIcon`** (ajustes/`BindingsScreen`): dibuja la textura con `drawTexture(RenderPipelines.GUI_TEXTURED, id, x,y, 0,0, size,size, 64,64,64,64)`; si no hay textura, glifo vector. `width()` coherente (cuadrado con textura).
- **B4 — `ControllerGlyphs`** (HUD de gameplay/inventario, vía `GameplayHudOverlay`): mismo enrutado a textura + fallback vector; `width()` coherente.
- **B5 — `ControllerBrandIcon`** (selector de mandos): ahora dibuja `controller.png` de la marca (silueta) en lugar de los logos vector; fallback a los marcos vector si falta el PNG.
- **Detección de marca:** elite→xbox_elite · 8bitdo→8bitdo · steam deck/STEAM_DECK→steam · PS/sony/dualsense→ps · xbox→xbox · steam/STEAM_CONTROLLER→steam · resto→generic (incl. Switch, que no trae carpeta propia).

**Build:** `BUILD SUCCESSFUL` (Gradle 8.14 + JDK 21), jar **`steampad-0.6.0.jar` 1.42 MB** (+~130 KB por las texturas). **Runtime:** ⚠️ por validar en hardware (B028).
**Nota:** `import_buttons/` en la raíz es la copia de origen del usuario; ya integrada en `resources` (se puede borrar).

### Fix 28 — Auditoría + UI unificada en columnas + sliders finos + optimización + versión 0.5.0 (2026-06-26 sesión 12) ✅ CÓDIGO / ⚠️ runtime por validar
Detalle por check en `PROGRESS.md` (A1–A9). Metodología por secciones ([[work-methodology]]).

- **A8 — Widgets base:** `client/ui/SteamToggle` (on/off como interruptor visual verde/rojo) y `client/ui/SteamSlider` (estilo propio, `nudge()` para ajuste fino, ignora la rueda).
- **A2 — UI unificada estilo Botones:** nueva base `screen/ColumnSettingsScreen` (lista a la izquierda ~3/4 + **panel de descripción a la derecha** ~1/4 según la opción enfocada o bajo el cursor). Refactorizadas `ControllerBasicSettingsScreen`, `ControllerAdvancedSettingsScreen`, `GlobalSettingsScreen`, `KeyboardSettingsScreen` para usarla. Todas las opciones muestran su descripción al seleccionarlas.
- **A4 — Scroll no cambia valores:** `SteamPadBaseScreen.mouseScrolled` siempre consume (no enruta la rueda a un widget bajo el cursor) y `SteamSlider.mouseScrolled` es no-op. Antes, scrollear sobre un slider cambiaba su valor.
- **A3 — Ajuste fino de sliders con stick derecho:** en `GamepadInputDispatcher`, si el widget enfocado es `SteamSlider`, el stick derecho (X) ajusta con magnitud al cuadrado (suave=milimétrico, fuerte=rápido, máx ~3 %/tick); el eje Y sigue scrolleando. Se enfoca el slider navegando con la cruceta.
- **A7 — Badge del selector de mandos** (esq. sup. der., pequeño): versión de build (vía FabricLoader), "ElDon", bandera de México, bandera de Yucatán y corazón, dibujados como pixel-art propio (símbolos de dominio público).
- **A1 — Auditoría:** corregidos scroll-cambia-slider y polls nativos repetidos por frame; limpiado código muerto del teclado. Notas: el teclado auto-aparece en los pickers (alternar con Back); el ajuste fino del slider es por foco de cruceta. Radial/chords/rumble/predeterminado revisados sin nuevos fallos.
- **A6 — Descripciones:** panel derecho por opción (oculta la clave si falta la traducción) + etiquetas de valores enum (sneak/sprint/gyro/yaw/require/block_reach) en en/es-MX/es-ES.
- **A5 — Optimización:** caché de 80 ms en `ControllerManager.getConnectedControllers()/activeSource()` (evita re-enumerar SDL/GLFW múltiples veces por frame en el selector y cada tick); capas del teclado estáticas; scroll sin re-enrutado a widgets.

**Build:** `BUILD SUCCESSFUL` (Gradle 8.14 + JDK 21), jar **`steampad-0.5.0.jar` 1.29 MB** en `dist/`. **Runtime:** ⚠️ por validar en hardware (B027).

### Fix 27 — Teclado virtual AAA dirigido por mando + versión 0.4.0 (2026-06-26 sesión 11) ✅ CÓDIGO / ⚠️ runtime por validar
Feature nueva (no es bugfix). Detalle por check en `PROGRESS.md` (K1–K6).

**Qué hace:** teclado en pantalla manejado 100% con el mando, que escribe en cualquier campo de texto (vanilla + mods) enrutando `Screen.charTyped(CharInput)` / `Screen.keyPressed(KeyInput)` — la misma vía que el teclado físico —, así que cubre chat, yunque, búsqueda creativa, command block, carteles/libros, y campos de mods. Aparece automáticamente al enfocar un campo (modo AUTO), cubre ~1/5 inferior, estilo retro-MC biselado, con franja de previsualización del texto (para ver lo que se escribe, incl. chat).

**Atajos (investigados según convención Steam/consola):** A=tecla seleccionada, Y=espacio, X=borrar (backspace), RT=enter, LT=shift (one-shot para letras), LB/RB=mover cursor (caret), cruceta=letra a letra, **stick izq=snap rápido entre letras** (no ratón virtual mientras se escribe), Back=alternar teclado/navegación de widgets, B=cerrar pantalla. Capas LETTERS/SYMBOLS (tecla ?123 / ABC).

**Archivos nuevos:** `client/keyboard/KeyboardLayout` (capas QWERTY + símbolos), `client/keyboard/VirtualKeyboard` (estado/elegibilidad/emisión, null-guards en cada acción para evitar NPE cuando Enter cierra la pantalla), `client/ui/VirtualKeyboardRenderer` (panel + previsualización), `screen/KeyboardSettingsScreen`.
**Tocados:** `config/GlobalConfig` (`virtualKeyboardEnabled/AutoShow/HeightPct/Sounds`), `screen/GlobalSettingsScreen` (sección "Teclado"), `input/GamepadInputDispatcher` (`handleKeyboardInput` + integración en `tickGui`, retorna antes del ratón virtual), `client/SteamPadClient` (render hook), `client/ui/VirtualCursorRenderer` (oculta el cursor cuando el teclado está activo), lang ×3.

**API usada (verificada con javap):** `CharInput(int codepoint,int modifiers)`, `KeyInput(int key,int scancode,int modifiers)`, `Screen.getFocused()` recursivo, `TextFieldWidget.getText()`.

**Decisión honesta:** la visibilidad del chat se resuelve con la **franja de previsualización** sobre el teclado (robusta para todos los screens y mods) en vez de mover el cuadro de chat nativo (mixin frágil). Si se quiere levantar físicamente el cuadro, es un add-on posterior.

**Build:** `BUILD SUCCESSFUL` (Gradle 8.14 + JDK 21), jar **`steampad-0.4.0.jar` 1.29 MB** en `dist/`. **Runtime:** ⚠️ por validar en hardware (B026).

### Fix 26 — Lote de 9 secciones de bugs de hardware + versión 0.3.0 (2026-06-26 sesión 10) ✅ CÓDIGO / ⚠️ runtime por validar
Trabajado por secciones (ver `PROGRESS.md` con el detalle por check). Metodología nueva guardada en memoria ([[work-methodology]]): siempre trabajar por secciones con checklist en `PROGRESS.md`.

- **S1 — Ratón virtual AUTO vs físico:** al mover el ratón físico de verdad (`MouseMixin` realMove, pasado el guard de 400 ms del gamepad) `VirtualMouseController.onPhysicalMouseTookOver()` oculta el cursor virtual en AUTO → reaparece el puntero del SO; al volver a mover el stick `onStickUsed()` lo reactiva y lo posiciona donde estaba el físico (`syncFromOsMouse`).
- **S2 — Radial ejecuta todos los tipos:** nuevo `input/KeyTap` (press/hold/release robusto: sirve a `wasPressed()` e `isPressed()` y a keybinds sin asignar). `RadialMenuController.triggerKeybind` lo usa; `openScreen` soporta inventario/pausa/selección; `openSubmenu` reabre la rueda; `KeyTap.tick()` en `SteamPadClient`.
- **S3 — Editor radial "Tipo":** el bug eran los valores de lang con "Type: …" embebido. Quitado el prefijo (en/es-MX/es-ES) + `omitKeyText()` en el cycler → muestra solo "Comando". Tooltip por tipo (`steampad.radial.type.*.desc`) al pasar ratón o enfocar con cruceta.
- **S4 — BOTONES "Others" + agrupar por mod:** `ActionCatalog` reescrito — agrupa keybinds por el MOD propietario (namespace del id de categoría → `FabricLoader…getName()`); vanilla con su categoría localizada; bucket traducido `steampad.cat.other`. Filas de mod ahora con chord.
- **S5 — Todas las acciones con chord:** cuadro de chord en filas BIND y EXTRA; modelo `ControllerConfig.extraChords` (keybind→modificador) persistido, en undo y reset.
- **S6 — Captura de chord de 2 botones:** flujo en dos pasos (1º modificador, 2º gatillo) en `BindingsScreen` (`onCapturedButton`/`applyChord`), con overlay que indica el paso. Guarda modificador+gatillo para BIND y EXTRA.
- **S7 — Chord anula la acción base:** `GamepadBinds.buttonShadowedByHeldChord` — una acción base sin chord en el botón X se suprime si otra acción (bind o mod) usa X como gatillo y su modificador está pulsado. Aplicado en `held`/`pressed` y `dispatchExtraBinds`. Ej.: DRIGHT+A=Mapa ⇒ A no salta mientras DRIGHT está pulsado. (Caveat: el botón modificador conserva su propia acción; conviene elegir un modificador libre.)
- **S8 — Mando predeterminado:** `ActiveControllerService.restoreFromConfig` prioriza por NOMBRE (estable entre sesiones); `SteamPadClient` cambia al predeterminado cuando se conecta en caliente (edge-triggered, no pelea con la selección manual).
- **S9a — Steam Input (causa raíz):** el SDK busca `steam_appid.txt` en el working dir (o la env `SteamAppId` que pone Steam al lanzar); el mod lo comprobaba pero **nunca lo creaba**. `SteamBootstrap.ensureAppIdFile()` lo escribe (AppID `GlobalConfig.steamAppId`=480, `autoWriteAppIdFile`) en working dir + game dir ANTES de `SteamAPI.init()`. Con Steam corriendo e IPC alcanzable, Steam Input puede inicializar aunque MC no se lance desde Steam. **Honesto:** bajo sandbox Flatpak sin acceso al IPC de Steam puede seguir fallando (B015, externo) — lanzar desde Steam lo garantiza.
- **S9b — Logos:** marca genérica re-dibujada (cuerpo+grips+cruceta+4 botones). El resto se mantienen como **marcas estilizadas originales** (no se reproducen logos oficiales por ser marcas registradas).
- **S9c — Botones extra 8BitDo:** `GamepadSnapshot` extendido (MISC1, PADDLE1-4, MISC2-4; `BUTTON_COUNT` 15→23), leídos por SDL3 (`SDL_GAMEPAD_BUTTON_MISC*/PADDLE*`), ids `M1..M4`/`P1..P4` en `GamepadBinds` → capturables/asignables (incl. chord). Caveat: SDL solo los reporta si su mapeo del device/modo los expone; GLFW no tiene paddles.

**Build:** `BUILD SUCCESSFUL` (Gradle 8.14 + JDK 21), jar **`steampad-0.3.0.jar` 1.27 MB** en `dist/`. JSON en/es-MX/es-ES validados al compilar. **Runtime:** ⚠️ por validar en hardware.

### Fix 25 — Lote de 5 bugs reportados en hardware + versión 0.2.0 (2026-06-25 sesión 9) ✅ CÓDIGO / ⚠️ runtime por validar
Reportados por el usuario tras probar en equipo real (Bazzite, escritorio y gamescope, mando 8BitDo Ultimate 2 Wireless vía SDL3).

1. **Menú radial no ejecutaba nada (ni ON_CLICK ni ON_RELEASE).** Causa raíz: `RadialMenuController.updateAnalog` reseteaba `selectedSlot = -1` cuando la magnitud del stick caía por debajo de 0.35. El gesto natural es flick→soltar el botón de abrir; el stick se recentraba un frame antes de soltar → en ese frame la selección se perdía y tanto `confirmSelection()` (ON_CLICK) como `close()` (ON_RELEASE) veían -1 y no hacían nada. **Fix:** selección **sticky** — por debajo del umbral se conserva el último sector. **Además:** botón **A activa** el slot resaltado al instante (`activateSelected()` + `dismiss()` sin doble disparo), dando un "presionar para usar" claro. Archivos: `radial/RadialMenuController.java`, `input/GamepadInputDispatcher.java`.

2. **Sensibilidad del ratón virtual muy alta.** `VirtualMouseController.BASE_SPEED_PER_SEC` 850→**340** (×0.4 del feel previo, según pedido). El valor por defecto de `virtualMouseSensitivity` sigue siendo **1.0** (ahora 1.0 = más lento/estable). Slider 0.2–3.0 intacto.

3. **El mando no vibraba al iniciar.** Causa: al restaurar el mando por defecto desde config (`restoreFromConfig`), `active != 0` → el bloque de auto-activación (que tenía el rumble) se saltaba, así que el rumble solo ocurría en hot-plug. **Fix en `SteamPadClient`:** bloque de **startup-rumble** de una sola vez (`startupRumbleDone`) que vibra (0.6, 250ms) el mando activo en el primer frame en que haya uno — sea restaurado o auto-seleccionado. Si el por defecto no está conectado, el bloque de auto-activación ya elige otro y este igual vibra. El rumble de hot-plug se conserva (solo tras el de arranque).

4. **Sub-línea del mando decía "GENERIC".** `ControllerSelectScreen` mostraba `ref.type.name()`. **Fix:** nuevo `ControllerBrandIcon.manufacturer(type, name)` → "8BitDo", "Sony", "Microsoft", "Nintendo", "Valve" (o "Generic" si nada reconocible). Reusa la misma detección de marca que el logo. Aplica a todos los mandos compatibles.

5. **Diagnóstico de Steam confuso/incorrecto.** Bajo Flatpak/Prism, Steam Input no inicializa (B001/B013, bloqueo externo del sandbox) ni siquiera en gamescope, pero el panel lo pintaba como fallo rojo y el texto de ayuda decía siempre "GLFW fallback" aunque la fuente real fuera **SDL3**. **Fix en `ControllerSelectScreen`:** (a) cuando un fallback (SDL3/GLFW) está sirviendo mandos, la línea "Steam API" pasa a **amarillo** ("not in Steam context") en vez de rojo; (b) `buildHelpMessage` recibe la `Source` y nombra la fuente real (SDL3/GLFW). No cambia el comportamiento de Steam Input, solo la honestidad del diagnóstico.

**Extra:** `SteamPadMod` registraba "Version: null" porque `getImplementationVersion()` lee el manifiesto (no seteado). Ahora lee la versión vía `FabricLoader.getModContainer`. **Versión del mod 0.1.0 → 0.2.0** (`gradle.properties`).

**Build:** `BUILD SUCCESSFUL in 26s` (Gradle 8.14 + JDK 21), jar **`steampad-0.2.0.jar` 1.32 MB** exportado a `dist/`. **Runtime:** ⚠️ por validar en hardware.
**Honesto/pendiente:** el bug 5 no "arregla" Steam Input bajo Flatpak (es bloqueo externo, B001) — solo hace el diagnóstico fiel; para haptics/action-sets de Steam hay que lanzar MC desde Steam. El submenú del radial sigue como placeholder.

### Fix 24 — Crash de clic, nombre del mando invisible, y cursor suave (2026-06-25 sesión 8b) ✅ CÓDIGO / parcialmente validado por log
**Crash corregido (NPE en runtime real):** `VirtualMouseController.clickAt` llamaba `mouseReleased` después de `mouseClicked`, pero un clic puede **cerrar/cambiar la pantalla** (p. ej. "Guardar y salir", o el editor radial) → `mc.currentScreen` quedaba `null` → `NullPointerException` (`method_25406`). Ahora se captura la pantalla y solo se hace `mouseReleased` si **sigue siendo la actual** (`if (mc.currentScreen == s)`).
**Nombre del mando ahora visible (causa raíz por fin):** en `ControllerSelectScreen` las constantes de color eran `0xRRGGBB` (byte alfa = 0). En MC 1.21.10 `DrawContext.drawText` **ya no fuerza opaco** cuando el alfa es 0 → el texto se dibujaba **transparente**. Por eso "no aparecía el nombre" pese a varios intentos previos (el `displayName` siempre fue correcto; el log lo confirma: "Steam Controller (HHD)"). Corregidas todas las constantes a `0xFFRRGGBB` (+ el `0x888888` inline y un `0xAAAAAA` en `CalibrationScreen`). Regla: **todo color de texto debe llevar byte alfa.**
**Cursor suave (estilo Controlify):** el movimiento del cursor virtual pasó de **por-tick (20 Hz, daba "brinquitos")** a **por-frame con delta-time real**: el dispatcher fija una **velocidad objetivo** desde el stick (`setStick`) y `VirtualMouseController.frameUpdate()` (llamado cada frame en `VirtualCursorRenderer`) **suaviza la velocidad** (easing exponencial, `VEL_TAU=0.035s`) e integra la posición. El **snap** ahora es "settle" (solo al soltar el stick), no durante el movimiento, para no pelear.
**Parpadeo eliminado:** el mando "Steam Controller (HHD)" también emite ratón (trackpad/gyro), que hacía flip de dispositivo cada frame. Soluciones: (a) `InputRouter.markMouse` se ignora si el mando estuvo activo en los últimos 400 ms; (b) umbral de "movimiento real" del ratón subido a 3 px; (c) el dibujo del cursor y el ocultar el puntero del SO dependen del **modo del cursor (estable)**, no del dispositivo por-frame.
**Build:** `BUILD SUCCESSFUL`, jar **1.26 MB** en `dist/`. **Runtime:** crash y nombre verificables por el usuario; suavidad por validar.

### Fix 23 — Convivencia mouse/gamepad + rework del menú radial (2026-06-25 sesión 8) ✅ CÓDIGO / ⚠️ runtime
**Enrutado de dispositivo de entrada (`input/InputRouter`):** rastrea el dispositivo activo (GAMEPAD/MOUSE). Cualquier input del mando → GAMEPAD; un movimiento físico real (>1px) → MOUSE. `MouseMixin` reescrito: deja pasar siempre los movimientos inyectados (virtuales) y los movimientos físicos reales (y marca MOUSE), pero **cancela los movimientos diminutos/"fantasma"** del ratón físico cuando el mando es el dispositivo activo dentro de una pantalla — arregla el "no da A" y el cursor fantasma. En gameplay nunca se cancela (el ratón sirve de apuntado).
**Ratón virtual (items 5–11):** puntero más pequeño (r=2); selección de casilla y radial en **blanco** (unificado); la notificación de modo (`VirtualCursorRenderer.drawModeBadge`) solo aparece ~2.5 s tras un cambio y se desvanece; modo **recordado por contexto** (mapa clase-de-pantalla→modo en `VirtualMouseController`, default AUTO); el puntero del SO se oculta cuando el mando controla (también con la cruceta) y reaparece al mover el ratón; **entrada mixta**: `GameplayHudOverlay` oculta los glifos cuando la entrada mixta está apagada y el ratón es el dispositivo activo, y los restaura al usar el mando.
**HUD sincronizado (item 1):** `GameplayHudOverlay` deriva el botón de cada pista de `GamepadBinds.button(cfg, bind)` → los glifos coinciden con la asignación real (Y inventario, etc.). Pistas de inventario ampliadas (item 4): A tomar/poner, X mitad, **Y mov. rápido**, B cerrar, Select cursor.
**Inventario (items 3, 4):** el clic simulado ahora hace press+**release** (`VirtualMouseController.clickAt`) → tomar y soltar funciona; **Y = quick-move** (shift-clic) vía `interactionManager.clickSlot(QUICK_MOVE)` sobre la casilla bajo el cursor.
**Botones (item 12):** `ActionCatalog` ahora lista **todos** los keybinds (vanilla + mods) agrupados por categoría (sin heurística frágil) → los mods aparecen y se mapean a un botón (`extraBinds`).
**Menú radial reescrito (items 2, 13–17), inspirado en velolib/radial:**
- `RadialConfig`: `slotCount` 2–12 (`MAX_SLOTS=12`), `normalize()`, selección blanca, 12 slots padded.
- `RadialMenuController`: selección por ángulo de la referencia (sin invertir Y → item 2); `slotCount` dinámico; `openEditorForSelected()` (gameplay: seleccionar + **LT** abre el editor del slot).
- `RadialRenderer`: distribuye exactamente `slotCount` chips equitativamente; anillo de selección blanco; escala el radio con el nº de slots.
- `RadialEditorScreen` reescrita: control −/+ de nº de slots, selector de slot, **campos dinámicos por tipo** (KEYBIND → picker; CHAT/SCREEN/SUBMENU → campo de texto), etiquetas (Nombre, valor por tipo), botón de **icono** que abre el picker de iconos, trigger, preview en vivo.
- `screen/KeybindPickerScreen` (NUEVO): lista buscable de todos los keybinds → devuelve el id. `screen/IconPickerScreen` (NUEVO): rejilla buscable de **todos los ítems de MC** → devuelve el id del ítem.
**API note:** `mouseReleased(Click)`, `KeyBinding.getId()`, `getCategory()` devuelve `Category` (se lee por reflexión y se prettifica).
**Build:** `BUILD SUCCESSFUL`, jar **1.26 MB** exportado a `dist/`. JSON en/es-MX/es-ES validados. **Runtime:** ⚠️ por validar en hardware.
**Pendiente/honesto:** submenús del radial no implementados (solo placeholder); el picker de pantallas usa campo de texto (id) en vez de lista; el editor de iconos sólo cubre ítems (no efectos/caracteres por ahora); la lista de keybinds en BOTONES puede ser larga (scroll).

### Fix 22 — Pulido de ratón virtual + navegación + reestructura del menú BOTONES (2026-06-25 sesión 7) ✅ CÓDIGO / ⚠️ runtime
**Ratón virtual (`VirtualMouseController` reescrito):**
- **Sensibilidad arreglada (causa raíz del "super rápido"):** el despachador multiplicaba `mv * cfg.virtualMouseSensitivity` (8.0) y `update()` volvía a multiplicar por su propio `sensitivity` (8.0) → **×64**. Ahora la sensibilidad se aplica **una sola vez** (`update(mv)` con `sensitivity * BASE_SPEED=18 px/tick`). Default `virtualMouseSensitivity` 8.0→**1.0**; **slider 0.2–3.0** en Básico (`ControllerBasicSettingsScreen`) y en la sección Ratón virtual del menú Botones.
- **Lag arreglado:** `onCursorPos` solo se invoca cuando la posición cambia ≥0.6 px (`syncOsCursor` con `MOVE_EPSILON`); `glfwSetInputMode` solo en **transición** (estado rastreado, re-aseverado al abrir pantalla vía `ScreenMixin` → `VirtualCursorRenderer.invalidateOsCursorState`), no por frame; el snap solo se aplica el tick en que el stick movió el cursor (`consumeMoved()`).
- **Cursor = punto blanco mediano** con sombra (sin el azul anterior).
- **Notificación arriba-izq** del modo (Activo/Desactivado/Auto) con punto de color (`VirtualCursorRenderer.drawModeBadge`).
- **Doble selección corregida:** al mostrarse el cursor se limpia el foco de la pantalla (`clearScreenFocus`); al navegar con cruceta el cursor del SO se sincroniza al centro del widget enfocado (`syncCursorToFocused`) → un solo resaltado.
**Navegación:**
- **Espacial por geometría** (arriba=arriba, abajo=abajo, izq/der reales) en pantallas SteamPad (`SteamPadBaseScreen.focusMoveDir`) y vanilla (`GuiFocusNavigator.moveDir`), reemplazando el recorrido lineal.
- **Inventario:** abre con cursor; la cruceta salta **casilla a casilla** (`SlotSnap.moveToNeighbor`), el cursor se mantiene visible. Snap a casilla (contenedores) y a **widgets** (menús normales, `WidgetSnap`).
**Nombre del mando:** visible en el encabezado de toda pantalla SteamPad (`SteamPadBaseScreen.renderChrome` dibuja `activeControllerName()` arriba-izq).
**Menú BOTONES reestructurado (`BindingsScreen` reescrita, 4 zonas):**
- Pestañas Básico/Botones/Avanzado (LB/RB). Lista categorizada (`ActionCatalog`): Movimiento, Gameplay, Inventario, Misc, Interfaz, Radial, Ratón virtual + **sección dinámica por cada mod** (lee `mc.options.allKeys`, filtra categorías no-minecraft, muestra la tecla de teclado y permite asignar un botón → `extraBinds`).
- Cada fila: nombre de la acción (izq) + **icono del botón asignado** (der) + cuadrado **Reiniciar** + cuadrado **Chord** (en binds). Filas FIXED (movimiento/look/GUI/clics del ratón virtual) muestran su icono pero no se reasignan (van ligadas al stick/botón por diseño). Fila SLIDER para la sensibilidad. Fila ACTION abre el editor radial.
- **Panel lateral (1/4):** acción seleccionada + descripción + botón ligado; botones Reiniciar todo / Deshacer (undo de un nivel sobre los mapas) / Aceptar.
- **Iconos propios monocromos** (`client/ui/ButtonIcon`): caras (disco + letra), sticks (anillo + L/R), gatillos (LT/RT), bumpers (LB/RB píldora), cruceta (cruz con brazo iluminado), flechas de stick (LS_*/RS_* con flecha), vacío (cuadro punteado). Negro con blanco, acorde a la UI.
**Modelo de binds (`GamepadBinds` + `ControllerConfig`):** binds con `Category`; nuevos GYRO_TOGGLE, DROP_STACK, PICK_BLOCK, PLAYER_LIST, SCREENSHOT, HUD_TOGGLE cableados en `GamepadInputDispatcher.tickInGame`. Soporte de **chord** por bind (`chordBindings`) y **extra binds** (`extraBinds`: botón→keybind id) despachados como tap, que reflejan/disparan keybinds de cualquier mod. Default remapeado a la visión del usuario (Inventario=Y, Cambiar mano=X, Esprintar=R3, Radial=cruceta derecha, Perspectiva=Select).
**API note (1.21.10):** el id de un `KeyBinding` es `getId()` (no `getTranslationKey()`, eliminado) y `getCategory()` devuelve un objeto `Category` (no String).
**Build:** `BUILD SUCCESSFUL`, jar **1.25 MB**. JSON de idioma (en/es-MX/es-ES) validados. **Runtime:** ⚠️ por validar en hardware.
**Pendiente/honesto:** acciones Creativo avanzadas (selección con NBT, guardar/cargar barra creativa) y F3/selector de gamemode no tienen keybind vanilla estable → no se incluyeron como binds cableados (se pueden añadir como extra binds si un mod las expone); el remapeo de defaults cambia el layout confirmado de sesiones previas (es lo pedido) y debe re-probarse en equipo.

### Fix 21 — Cursor estilo Controlify + binds configurables + 3 pestañas + blur nativo (2026-06-25 sesión 6 cont.) ✅ CÓDIGO / ⚠️ runtime
**Ratón virtual (reescrito, modelo Controlify):**
- `VirtualMouseController` con modos **OFF / ON / AUTO** (Select cicla). OFF = solo cruceta; ON = cursor siempre; AUTO = el stick izq. muestra el cursor, la cruceta lo oculta (conviven).
- **Suprime el mouse físico** mientras el cursor del mando está activo: `MouseMixin` cancela `onCursorPos` físico; el cursor virtual mueve el puntero del SO con flag `INJECTING` (y escalado correcto raw↔scaled, que también arregla la posición). Esto corrige el desincronizado/pelea físico-virtual.
- Cursor = **punto** en ventanas normales; **brackets de esquina** alrededor de la casilla en contenedores (selección clara). Render vía `ScreenEvents.AFTER_RENDER` → **encima** de ítems/tooltips (corrige "cursor detrás"). `glfwSetInputMode` oculta el puntero del SO.
**Navegación tipo consola:** cruceta = foco con **auto-scroll** que revela opciones ocultas (`SteamPadBaseScreen.focusMove`), stick der. = scroll, **LB/RB = pestañas** (`TabbedScreen`), A = confirma, B = atrás, X = clic derecho.
**Binds físicos configurables:** `input/GamepadBinds` (acción→botón con defaults Bedrock). `GamepadInputDispatcher.tickInGame` ahora es **config-driven** (mismo comportamiento por defecto). `ControllerConfig.buttonBindings` persiste overrides. **Rebind real:** `BindingsScreen` reescrita — lista navegable de acciones con **glyph** del botón asignado; clic → "presiona un botón" (`captureMode` suprime la nav) → guarda. Esc cancela.
**3 pestañas:** Básico / **Configurar botones** / Avanzado (`SettingsTabs`), con LB/RB. El **editor radial** se movió a Configurar botones.
**Blur nativo:** quitado el override de `renderBackground` (gradiente) y las llamadas manuales → el pipeline aplica el blur nativo de MC una sola vez en todas las pantallas. (Riesgo: si el pipeline no pre-blurea, el peor caso es fondo transparente, no crash.)
**Otros:** gyro off por defecto (`gyroEnabled`); pistas de botones en inventario (`GameplayHudOverlay.renderContainerHints`); selección de mando con tarjetas arriba (nombres visibles) + panel de diagnóstico compacto al fondo.
**Build:** `BUILD SUCCESSFUL`, jar 1.21 MB (16:05). **Runtime:** ⚠️ por validar.
**Pendiente/honesto:** glyphs por botón aún básicos para no-face (chips L3/R3/D↑…); el blur nativo no pude probarlo (verificar que no crashee). Mapeo Bedrock por defecto = el actual (A salto, RT atacar, LT usar, X inventario, Y mano, LB/RB hotbar, L3 sprint, R3 radial, cruceta arriba=vista/abajo=tirar, Back=chat, Start=pausa).

### Fix 20 — Feel de gameplay (movimiento analógico, cámara por-frame, cursor) + UI responsive (2026-06-25 sesión 6) ✅ CÓDIGO / ⚠️ runtime
**Gameplay (lo que arregla el "feel"):**
- **Movimiento analógico:** `mixin/KeyboardInputMixin` escribe `Input.movementVector` (Vec2f) + el record `PlayerInput` desde el stick izq. → stick suave = caminar lento, a tope = correr (como consola). `input/ControllerInputState` comparte la intención (el despachador es el único que lee el mando; el mixin solo aplica). Se reemplazó el `setKeyPressed` binario de movimiento.
- **Cámara por-frame:** `input/CameraController.frame()` aplicado en el HUD callback (cada frame, no a 20 Hz) con escalado por delta-time real y curva de respuesta. `BASE_DEG_PER_SEC=360`. Corrige #1 (lentitud aunque suba sensibilidad — el multiplicador viejo `*5` daba ~15°/s) y #3 (lag — antes a 20 Hz).
- **Cursor virtual visible + ocultar mouse físico:** `client/ui/VirtualCursorRenderer` dibuja una retícula de acento en la posición virtual y pone el cursor del SO en `GLFW_CURSOR_HIDDEN` mientras el mando controla la pantalla (restaura NORMAL si no). Cableado en `ScreenMixin.render` (TAIL). Corrige #5 (cursor invisible) y #otros4 (mouse físico presente).
- **Rumble más corto:** conexión 220ms→80ms, intensidad 0.6→0.45.
- **Gyro OFF por defecto:** `ControllerConfig.gyroEnabled=false`; el gyro del path Steam se gatea con esto.
**UI — causa raíz de "no se ve nada":** muchas pantallas usaban anchos absolutos (`width/2 - 200`, columnas a ±200px) pensados para ventana 960px. En la **resolución lógica estrecha del Steam Deck** eso dibuja el contenido FUERA de pantalla.
- **`BindingsScreen` reescrita responsive + scrollable:** columna centrada (`min(360, width-24)`), pestañas que se envuelven, nombres legibles ("Walk Forward"), binding a la derecha, scroll (rueda/bumpers), tinte alterno. Corrige #interfaz3.3 (bindings vacíos).
- **`ControllerSelectScreen` responsive + botón Default:** cajas con `boxW=min(420,width-16)`, botones [Select][Settings]/[Default] en columna derecha. Nombre robusto (fallback si vacío) + ✓ activo + ★ predeterminado. `GlobalConfig.preferredControllerName` (por nombre, estable entre reconexiones); auto-activación prefiere el recordado. Corrige #interfaz1 (nombres) y #interfaz2 (default).
**Build:** `BUILD SUCCESSFUL`, jar 1.20 MB (13:33). **Runtime:** ⚠️ por validar.
**Pendiente (siguiente pasada):** 3 pestañas (Básico/Configurar botones/Avanzado) + mover radial; blur nativo de MC en todas las pantallas; captura de rebind (configurar botones/chords de verdad); nav LB/RB pestañas + stick der. scroll + auto-scroll al foco; verificar mapeo Bedrock por defecto.

### Fix 19 — Crash de arranque: GLFW/SDL inicializados antes del glfwInit de MC (2026-06-25 sesión 5) ✅ CORREGIDO / parcialmente confirmado en log
**Síntoma (log del usuario):** el mod carga bien, **detecta el mando por SDL3** (`Early controller scan: 1 controller(s) detected (source: SDL3)`), pero el juego crashea al inicializar:
```
IllegalStateException: GLFW error before init: [0x10001] The GLFW library is not initialized
   at GLX._initGlfw → RenderSystem.initBackendSystem → MinecraftClient.<init>:509
```
**Causa raíz:** `SteamPadClient.onInitializeClient()` llamaba `GamepadMappings.loadAll()` (`glfwUpdateGamepadMappings`) y `Sdl3GamepadProvider.init()` (`SDL_Init`) en el paso 3.5. En esta versión de Fabric/MC, `onInitializeClient` corre **ANTES** de que MC inicialice GLFW. Tocar GLFW/SDL antes del `glfwInit()` de MC dejó GLFW en mal estado → el `glfwInit()` posterior falló con NOT_INITIALIZED y el callback de error de MC abortó el arranque.
**Fix:** TODA la init que toca GLFW/SDL (mappings, SDL3, `restoreFromConfig`, escaneo temprano) se difiere al **primer tick del cliente** (`ensureFallbackBackendsInit()`, guard `fallbackInitDone`). Para el primer tick, GLFW ya está inicializado y estamos en el render thread — el lugar correcto. Es el patrón que usan Controlify y similares (inicializan su capa de input post-arranque, no en onInitializeClient).
**Lo bueno confirmado por el log:** SDL3 funciona y detecta el mando; el cableado de detección es correcto. Solo era el timing del init.
**Build:** `BUILD SUCCESSFUL`, jar 1.19 MB (12:29). **Runtime:** ⚠️ falta confirmar que ya arranca con el jar nuevo.

### Fix 18 — Glyphs, logos de marca, radial funcional + rediseñado, editor radial (2026-06-25 sesión 5) ✅ CÓDIGO / ⚠️ runtime
**BUG CRÍTICO corregido:** el menú radial **no se abría desde el path de fallback** (GLFW/SDL3) — solo desde Steam. O sea, en los mandos que de hecho funcionan en Bazzite, el radial era inalcanzable. Cableado en `GamepadInputDispatcher.tickInGame`: **R3 (clic stick der.) abre**, stick derecho navega, **soltar ejecuta** (confirmSelection + close); suspende el resto del input mientras está abierto; libera teclas al abrir/cerrar.
**Radial rediseñado (sin bug visual):** `RadialRenderer` usaba `ctx.fill` (rects) para aproximar arcos → se veía en bloques. Reescrito a chips redondos limpios alrededor del centro, seleccionado con anillo de acento, etiqueta del slot en el centro, slots vacíos con punto. Usa `client/ui/Draw` (primitivas de círculo/anillo).
**Editor radial funcional:** `RadialEditorScreen` reescrito — antes solo cambiaba el tipo del slot 0 y no reflejaba el slot seleccionado (bug). Ahora: selector de 8 slots (re-init al cambiar), y por slot: Tipo (cycling), Disparo (on-click/on-release), Acción (campo de texto), Etiqueta (campo), Icono (campo, autodetecta ITEM/CHARACTER). Preview en vivo. Guarda al instante.
**Glyphs de botón por tipo (`client/ui/ControllerGlyphs`):** Xbox = discos de color ABXY + letra; PlayStation = formas ×/○/□/△; Switch = discos oscuros con letra; bumpers/gatillos = chips con etiqueta. Cableado en el HUD de gameplay (usa el tipo del mando activo).
**Logos de marca en la selección (`client/ui/ControllerBrandIcon`):** marca dibujada por código a la izquierda de cada mando — Steam Deck (silueta handheld), 8BitDo (rejilla retro), Xbox (esfera-X verde), PlayStation (4 formas), Switch (dos Joy-Con rojo/azul), Steam (círculo). Detecta marca por nombre + tipo. Marcas originales simplificadas (no reproducen logos con copyright pixel a pixel).
**Primitivas de dibujo (`client/ui/Draw`):** fillCircle/fillRing/outlineCircle/fillRoundRect/triangle/cross/square/line por scanlines — sin assets PNG.
**Steam Input (repaso):** la compatibilidad con TODOS los mandos que soporta Steam Input es automática vía ISteamController/VDF (Valve mapea cada mando soportado al ActionSet; sin código por modelo, conforme a la restricción #5 de CLAUDE.md). Funciona lanzado desde Steam (escritorio, gamescope, Game Mode); bajo Flatpak/Prism no inicializa → fallback SDL3/GLFW.
**Build:** `BUILD SUCCESSFUL`, jar 1.19 MB (11:42). **Runtime:** ⚠️ NO VERIFICADO en hardware.
**i18n:** claves `steampad.radial.*` (editor) añadidas en en/es-MX/es-ES.

### Fix 17 — Detalles de jugabilidad/usabilidad estilo Bedrock/AAA (2026-06-25 sesión 5) ✅ CÓDIGO / ⚠️ runtime
- **Rumble al conectar:** `ControllerManager.rumble(handle, intensity, durationMs)` enruta el rumble — SDL3 (nativo), Steam (ISteamController), GLFW (no-op, sin API). `SteamPadClient` lanza un pulso corto (0.6, 220ms) al auto-activar un mando, para que se sienta la conexión.
- **Modo cursor/foco con Select (estilo Controlify):** `GamepadInputDispatcher.tickGui` — el botón **Select (Back)** alterna entre cursor libre y navegación por foco en cualquier pantalla/submenú. Las pantallas de contenedor (inventario, cofres) abren en modo cursor por defecto; los menús, en modo foco. A = confirmar (clic en cursor o activar enfocado), X = clic derecho (partir pilas), B = atrás, bumpers = scroll, stick izq = cursor.
- **Snap suave a casillas (inventario Bedrock):** `input/SlotSnap.java` + `mixin/HandledScreenAccessor.java` (accessor de `HandledScreen.x/y`, verificado que existen en 1.21.10). Estando en modo cursor sobre un contenedor, el cursor es atraído suavemente (35% por tick, radio 22px) al centro de la casilla más cercana — sensación magnética por celda. `VirtualMouseController.setPosition()` añadido.
- **HUD de botones en gameplay (Bedrock):** `client/hud/GameplayHudOverlay.java` registrado en `HudRenderCallback`. Durante gameplay (sin pantalla, mando fallback activo, HUD visible, `cfg.showIngameButtonGuide`), muestra pistas de botones en las dos esquinas inferiores: izq (X Inventario, LB Anterior, LT Usar), der (A Saltar, B Agacharse, RB Siguiente, RT Atacar). Badges con borde de acento + etiqueta traducible. Respeta `ingameButtonGuidePosition` (TOP/BOTTOM) y F1 (HUD oculto).
- **i18n:** claves `steampad.hud.*` en en/es-MX/es-ES.
**Build:** `BUILD SUCCESSFUL`, jar 1.18 MB (10:57). **Runtime:** ⚠️ NO VERIFICADO en equipo real.
**Notas:** GLFW no tiene rumble (solo SDL3/Steam vibran al conectar). El HUD solo se muestra para mandos fallback (su layout coincide con el mapeo GLFW/SDL3 fijo).

### Fix 16 — B018 completo: todas las pantallas migradas + navegación por foco Bedrock + fix bucle Back (2026-06-25 sesión 5 final) ✅ CÓDIGO / ⚠️ runtime
**Migración de UI (todas las pantallas):**
- **`SteamPadBaseScreen`**: añadido helper de **scroll reutilizable** (`addScroll`/`finishScroll`/`renderScrollbar`/`isInViewport`) y `mouseScrolled` (rueda + bumpers).
- **`ControllerBasicSettingsScreen` / `ControllerAdvancedSettingsScreen`**: reescritas con chrome, **secciones** (Sensibilidad/Movimiento/Zonas muertas/Configurar; Vibración/Gyro/Avanzado), **tooltip de descripción por opción**, claves traducibles, **scroll**, y **fila de pestañas Básico/Avanzado** dentro de cada una.
- **`BindingsScreen` / `CalibrationScreen` / `RadialEditorScreen`**: chrome + títulos/labels traducibles, layout ajustado bajo el header. `CalibrationScreen` ahora lee los sticks del **fallback GLFW/SDL3** (no solo Steam).
- **i18n:** ~70 claves nuevas (`steampad.cset.*`, secciones, pestañas, bindings, calibración) en `en_us`/`es_mx`/`es_es`, con `.desc` por opción. JSON validado.
**Navegación por foco estilo Bedrock:**
- **`input/GuiFocusNavigator.java`** (NUEVO): D-pad mueve el foco entre widgets, A activa el enfocado (vía `mouseClicked` sintético en su centro — evita la API de `keyPressed` que cambió a record en 1.21.10). Funciona en cualquier pantalla (vanilla incluida).
- **`GamepadInputDispatcher.tickGui`**: D-pad → foco, A → activar (o clic de cursor si no hay foco), X → clic libre de cursor, B → atrás, bumpers → scroll, stick izq → cursor.
**Bug pre-existente corregido (crítico de navegación):**
- `ControllerSettingsScreen` rebotaba a la pestaña básica en `init()`, y como las pestañas tenían de parent a ese mismo screen, **Back quedaba en bucle infinito** (imposible volver a la selección). ELIMINADO el intermediario; `ControllerSelectScreen` abre `ControllerBasicSettingsScreen` directamente con el parent real; las pestañas Básico/Avanzado se cambian entre sí preservando el parent. Ahora Back navega correctamente toda la jerarquía.
**Limitaciones conocidas (polish futuro):** la navegación por foco solo alcanza widgets visibles; en pantallas con scroll hay que bajar con bumpers para revelar los de abajo (no auto-scrollea al foco). Sliders se ajustan con cursor+X (no con D-pad izq/der).
**Build:** `BUILD SUCCESSFUL`, jar **1.18 MB** (07:29). **Runtime:** ⚠️ NO VERIFICADO en equipo real.

### Fix 15 — Multi-backend (SDL3 + GLFW + 8BitDo), refactor de UI fresca, i18n con descripciones, scroll (2026-06-25 sesión 5 cont.) ✅ CÓDIGO / ⚠️ runtime
**Backends de entrada en cascada (Steam Input → SDL3 → GLFW), todos con degradación elegante:**
- **`input/GamepadSnapshot.java`** (NUEVO): estado físico normalizado de gamepad (15 botones + 6 ejes, orden estilo GLFW) para que cualquier backend alimente el mismo despachador.
- **`input/GamepadInputDispatcher.java`** (NUEVO, reemplaza a GlfwInputDispatcher): despachador agnóstico que lee un `GamepadSnapshot` vía `ControllerManager.readSnapshot`. Misma lógica de gameplay/GUI de la sesión anterior. **Fix de bug:** si el mando se desconecta a mitad, libera todas las teclas (no quedan pegadas).
- **`input/sdl/Sdl3Native.java` + `Sdl3GamepadProvider.java`** (NUEVOS): binding JNA mínimo a libSDL3 (init, enumerar, leer botones/ejes, rumble). Si libSDL3 no está o JNA falla → se desactiva solo y cae a GLFW (cero crash). JNA es `compileOnly` (MC ya lo trae en runtime). Handles etiquetados ASCII "SDL3".
- **`input/GlfwSnapshotSource.java`** (NUEVO): lee snapshot de GLFW; **fallback a joystick crudo** (`glfwGetJoystickButtons/Axes/Hats`) para devices sin mapping SDL — clave para 8BitDo en modos no-XInput.
- **`input/GamepadMappings.java` + `assets/steampad/gamecontrollerdb.txt`** (NUEVOS): carga mapeos SDL en GLFW vía `glfwUpdateGamepadMappings` (DB curada de 8BitDo SN30/Pro2/Ultimate + archivo de usuario en `config/steampad/gamecontrollerdb.txt`). Solo aplica a GUIDs que coincidan → inofensivo para mandos que ya funcionan.
- **`service/ControllerManager.java`**: cascada Steam→SDL3→GLFW; `activeSource()` reporta cuál; `readSnapshot`/`isFallbackHandle` enrutan por etiqueta de handle.
- **`config/GlobalConfig.java`**: flags `useSdl3Fallback` / `useGlfwFallback` (ambos default true).
- **`SteamPadClient`**: inicializa mapeos + SDL3 al arranque; auto-activación mejorada (limpia el handle si el mando se desconecta y re-activa otro presente).

**Refactor de UI (fresca + funcional):**
- **`screen/SteamPadBaseScreen.java`**: tema renovado — paleta con acento azul SteamPad, barra de título (`renderChrome`), encabezados de sección (`drawSectionHeader`), helpers de layout.
- **`screen/GlobalSettingsScreen.java`**: reescrito — secciones con encabezado, **tooltip de descripción por opción**, claves traducibles, y **scroll vertical** (rueda + bumpers del mando) para no desbordar en pantallas pequeñas (Steam Deck/Ally). Barra de scroll con acento.
- **`screen/ControllerSelectScreen.java`**: barra de título con acento; línea "Input Source" ahora incluye SDL3; layout ajustado bajo el header.

**i18n con descripciones:** `en_us`, `es_mx`, `es_es` ampliados con secciones + `*.desc` por opción (es-MX "control", es-ES "mando"). JSON validado.

**Build:** `BUILD SUCCESSFUL` (Gradle 8.14 + JDK 21), jar 1.17 MB con todas las clases/recursos. **Runtime:** ⚠️ NO VERIFICADO en equipo real.
**Pendiente (B018):** convertir literales de las demás pantallas (bindings, calibración, radial, controller settings) a claves; navegación por foco D-pad estilo Bedrock (hoy: cursor virtual + A/B + scroll).

### Fix 14 — El mando controla el juego sin Steam: input GLFW + auto-activación + nav + i18n (2026-06-25 sesión 5) ✅ CÓDIGO / ⚠️ runtime
**Contexto:** Tras el Fix 13 el mando se DETECTA, pero el pipeline de input (orientado a Steam ActionSets) no producía acción — de hecho el movimiento ni estaba cableado (caía en no-op). Esto lo resuelve la ruta de gameplay GLFW.
**Archivos:**
- **`input/GlfwInputDispatcher.java`** (NUEVO): cuando el mando activo es un handle GLFW, conduce el juego directamente vía `KeyBinding` en vez de ActionSets:
  - Acciones mantenidas (`KeyBinding.setKeyPressed`): movimiento (stick izq.), minar (RT), usar/colocar (LT), salto (A), sneak (B, hold/toggle según `sneakMode`), sprint (L3, hold/toggle según `sprintMode`).
  - Cámara: stick der. → `player.changeLookDirection` con sensibilidad/inversión de `ControllerConfig`.
  - Acciones discretas (edge → `KeyBinding.onKeyPressed`): inventario (X), cambiar mano (Y), hotbar ±1 (LB/RB), tirar (D-pad abajo), perspectiva (D-pad arriba), chat (Back), menú de pausa (Start).
  - GUI: al abrir cualquier pantalla activa el cursor virtual; stick izq. mueve, A = clic, B = cerrar, bumpers = scroll. Libera todas las teclas al volver al mundo (no quedan teclas pegadas).
  - Layout por defecto estilo Xbox (mapeo estándar de GLFW). Un `GLFWGamepadState` reutilizable (sin asignación por tick). Detección de flanco con `prevButtons`.
- **`input/InputBindingManager.java`**: en `tick()`, si el handle activo es GLFW → delega a `GlfwInputDispatcher` y retorna (no toca la ruta Steam).
- **`client/SteamPadClient.java`**: auto-activación — si no hay mando activo y hay alguno detectado, activa el primero (una sola vez; el mod "funciona al conectar" sin selección manual).
- **i18n:** `assets/steampad/lang/es_mx.json` y `es_es.json` (es-MX usa "control", es-ES usa "mando").
**Limitaciones honestas (siguiente pasada):**
- El mapeo GLFW es fijo por ahora; respetar `BindingConfig`/chords/radial en la ruta GLFW es pendiente.
- Devices sin mapping SDL de gamepad (`glfwGetGamepadState` false) se omiten — fallback a joystick crudo pendiente. Steam Deck/ROG Ally sí tienen mapping.
- Varias pantallas usan `Text.literal` en duro → las traducciones aún no se ven en esas pantallas hasta convertirlas a claves (parte del refactor de UI).
**Build:** `BUILD SUCCESSFUL` (Gradle 8.14 + JDK 21). **Runtime:** ⚠️ NO VERIFICADO en equipo real.

### Fix 13 — Detección de mandos vía GLFW fallback (2026-06-25 sesión 5) ✅ CÓDIGO / ⚠️ runtime pendiente
**Síntoma (confirmado por el usuario en equipo real):** SteamPad no detecta ningún mando — ni en Bazzite escritorio (lanzado desde Prism) ni en gamescope con Steam Input activo. `ControllerSelectScreen` siempre muestra "Detected 0 controller(s)".
**Causa raíz (clara en el log):**
```
[SteamPad] Environment: OS=linux, Linux=true, Gamescope=false, SteamDeck=false
[SteamPad] SteamAPI.init() returned false. Steam may not be running or AppID is missing.
SteamAPIInit failed; no appID found.
```
Steam Input (Steamworks4j / ISteamController) **solo inicializa dentro de un contexto de app de Steam real**. Bajo Prism Launcher (Flatpak), el sandbox aísla el proceso Java del cliente de Steam → `SteamAPI.init()` falla con "no appID found" → `SteamInputManager` nunca tiene controladores que enumerar → 0 detectados. Esto ocurre en escritorio Y en gamescope porque el problema es el sandbox de Flatpak, no la presencia de Steam.
**Solución implementada — fallback de detección por GLFW:**
- **`input/GlfwControllerProvider.java`** (NUEVO): enumera joysticks/gamepads vía la API de GLFW (`glfwJoystickPresent`, `glfwJoystickIsGamepad`, `glfwGetGamepadName`/`glfwGetJoystickName`). GLFW siempre está disponible (LWJGL lo incluye y MC ya lo inicializa) — **cero dependencias nuevas**. Cuando Steam Input está activo en Game Mode, Steam expone un gamepad virtual (xpad uinput) que GLFW detecta; en escritorio ve el dispositivo crudo. Funciona en ambos casos donde Steam Input falla.
  - Representa cada gamepad como `SteamControllerHandleRef` con handle sintético etiquetado (`0x474C465700000000L | (jid+1)`, ASCII "GLFW") → reutiliza todo el modelo de datos y UI sin cambios. `isGlfwHandle()`/`joystickId()` permiten recuperar el origen para el futuro despacho de input.
  - Tipo de mando inferido del nombre (Xbox/PlayStation/Switch/Steam/Genérico).
- **`service/ControllerManager.java`** (NUEVO): fachada única. `getConnectedControllers()` devuelve la lista de Steam Input si está disponible, si no la de GLFW. `activeSource()` reporta STEAM_INPUT / GLFW_FALLBACK / NONE. No toca la ruta de Steam (sigue siendo el backend primario).
- **Consumidores migrados a la fachada:** `ActiveControllerService` (restore + getActiveRef), `ControllerSelectScreen` (init/refresh/tick/panel), `SteamRuntimeDiagnostics` (dump), `SteamPadClient` (early scan). 
- **UI:** panel de diagnóstico ahora muestra "Input Source: Steam Input / GLFW fallback / none"; el estado vacío y el mensaje de ayuda distinguen "detectado vía GLFW fallback" de los fallos de Steam (ya no insiste con steam_appid.txt cuando el fallback sí encontró el mando).
**Alcance honesto:** Esto resuelve la **detección** (el mando aparece en la pantalla y se puede seleccionar). El **despacho de input en gameplay vía GLFW todavía NO está cableado** — el pipeline de input (`InputBindingManager`/`ActionExecutor`/`ControllerState`) lee datos de Steam ActionSets, que siguen caídos en este entorno. Próximo hito: construir `ControllerState` desde `glfwGetGamepadState` para que los botones muevan el juego sin Steam. Ver TODO_BLOCKERS B017.
**Build:** `BUILD SUCCESSFUL in 15s` (Gradle 8.14 + JDK 21).
**Runtime:** ⚠️ NO VERIFICADO en equipo real — pendiente que el usuario pruebe el JAR nuevo.

### Fix 12 — CAUSA RAÍZ REAL del NoSuchMethodError: build target ≠ runtime version (2026-06-24 sesión 4) ✅ DEFINITIVO
**Síntoma (MC real, crash al abrir ControllerSelectScreen):**
```
NoSuchMethodError: 'int net.minecraft.class_332.method_51439(
    net.minecraft.class_327, net.minecraft.class_2561, int, int, int, boolean)'
  at dev.steampad.screen.ControllerSelectScreen.drawStatusLine(...:285)
```
**Causa raíz real (por fin identificada):** Los Fixes 10 anteriores cambiaban el *nombre* del método en el source (`drawTextWithShadow` → `drawText`), pero el problema nunca fue el nombre. El JAR se compilaba contra **mappings de MC 1.21.4** mientras el juego corre **MC 1.21.10**. El método `DrawContext.drawText(TextRenderer, Text, int, int, int, boolean)` (`method_51439`) **devuelve `int` en 1.21.4 y `void` en 1.21.10**. El descriptor JVM incluye el tipo de retorno:
- Compilado contra 1.21.4 → call site busca `(…Z)I`
- Runtime 1.21.10 solo tiene `(…Z)V`
- → `NoSuchMethodError` (el descriptor no coincide aunque el nombre sí).

Ningún cambio de nombre de método en el source podía arreglar esto. **La única solución es compilar contra la versión de runtime real.**

**Fix aplicado (3 partes):**
1. **`gradle.properties` migrado a 1.21.10** (ya estaba hecho en sesión 3, pre-apagón): `minecraft_version=1.21.10`, `yarn_mappings=1.21.10+build.3`, `fabric_version=0.138.4+1.21.10`, `cloth_config_version=20.0.149`, `modmenu_version=16.0.0`. Backups en `*.bak1214`. **El JAR nunca se reconstruyó tras este cambio** — por eso el usuario seguía con el JAR de 1.21.4.
2. **Cache de Loom corrupto recuperado:** El apagón truncó `C:\Dev\Steampad\.gradle\loom-cache\...\minecraft-merged-*.jar` (`ZipException: zip END header not found`). Se eliminó `C:\Dev\Steampad\.gradle\loom-cache` y Loom lo regeneró limpio.
3. **Access widener para `Mouse.onCursorPos`:** En 1.21.10 `onCursorPos(long,double,double)` es **privado** (el comentario previo del AW decía erróneamente "public en 1.21.10"). Añadida línea: `accessible method net/minecraft/client/Mouse onCursorPos (JDD)V`.

**Verificación a nivel de bytecode (no solo "compila"):** `javap -c` sobre `ControllerSelectScreen.class` del JAR nuevo confirma que ahora referencia `method_51439:(…Z)V` (retorno **void**) y `method_27534:(…III)V` (drawCenteredTextWithShadow) — descriptores que SÍ existen en 1.21.10.
**Build:** `BUILD SUCCESSFUL in 30s` (Gradle 8.14 + JDK 21).
**Runtime:** ⚠️ PENDIENTE — el usuario debe reemplazar el JAR viejo en `mods/` y reabrir el botón SteamPad en Ajustes.

**Lección permanente (sobreescribe la "regla" del Fix 10):** El problema NO era `drawTextWithShadow` ni la elección de método. Era la disparidad build-target/runtime. Mantener `minecraft_version` y `yarn_mappings` sincronizados con la versión de MC donde se ejecuta. Cuando el usuario diga "estoy en MC X.Y.Z", verificar `gradle.properties` ANTES de tocar el código de las pantallas.

### Fix 3 — VDF de Steam Input creado
**Archivo:** `steampad_steam_input/game_actions_480.vdf`
**Contenido:** ActionSets completos con nombres exactos que usa `SteamActionRegistry`:
- `SteamPad_InGame`: 2 analógicos (left_stick, right_stick) + 22 digitales
- `SteamPad_GUI`: 1 analógico (vmouse) + 15 digitales
- Localización: English + Spanish
**Acción requerida:** El usuario debe copiar el VDF a la carpeta `controller_config/` de Steam para que los action handles sean válidos.

---

## Decisión Técnica Crítica de Esta Sesión

**Steamworks4j 1.9.0 NO expone ISteamInput — expone ISteamController.**

La API usada es `SteamController` (ISteamController), no `SteamInput` (ISteamInput). Ambas exponen la misma arquitectura de ActionSets/ActionHandles pero con diferencias en:
- Tipos de handles (objetos tipados vs raw long)
- Sin callbacks de conexión/desconexión en ISteamController
- Gyro via `getMotionData(SteamControllerMotionData)`, no como analog action
- Sin trigger-motor separation en ISteamController (solo motores L/R)
- Sin `SteamDeckController` como InputType (mapeado a SteamController)

Ver DECISIONS.md D013, D014, D015.

---

## Bloqueos Activos

Ver TODO_BLOCKERS.md para lista completa. Los más críticos:
1. **B001** — Steam debe estar corriendo; sin Steam el mod no detecta controladores
2. **B002** — ActionSets VDF creado pero importación manual requerida (handles = 0 sin ella)
3. **B004** — Gamescope: comportamiento desconocido en runtime real
4. **B011** — Java 25 en PATH rompe `gradle test`; usar Java 21 explícitamente para builds

---

## Siguientes Pasos Concretos para Validación Manual

**En esta prioridad exacta:**

### Paso 1 — Prueba de arranque (Linux o Windows con Steam)
```
1. Instalar Fabric Loader 0.16.14 para MC 1.21.4
2. Copiar steampad-0.1.0.jar a .minecraft/mods/
3. Asegurar que Steam está corriendo
4. Iniciar Minecraft desde Steam (o con steam_appid.txt=480 en el dir run)
5. Verificar en logs: "Steam API initialized" o "SteamAPI.init() returned false"
6. Si Steam falla: anotar error exacto → actualizar B001
```

### Paso 2 — Detección de controlador
```
1. Con controlador conectado y Steam corriendo
2. Abrir ControllerSelectScreen (asignar keybind en Controls de MC)
3. Verificar que la lista muestra el controlador conectado
4. Seleccionarlo y confirmar que `isActive()` cambia a true en el dump
```

### Paso 3 — Input básico en juego
```
1. Con controlador seleccionado, cargar un mundo
2. Mover stick izquierdo → verificar movimiento de personaje
3. Mover stick derecho → verificar movimiento de cámara
4. Presionar botón de salto configurado → verificar salto
5. Si nada funciona: verificar si ActionSet handles son 0 (VDF no importado)
   → intentar raw binding sin VDF como workaround
```

### Paso 4 — Prueba de chord
```
1. En BindingsScreen, configurar un chord (e.g. LB+A → OPEN_RADIAL)
2. Mantener LB, presionar A → verificar que radial abre
3. Soltar LB, presionar A solo → verificar que acción simple de A dispara (no chord)
```

### Paso 5 — Persistencia
```
1. Cambiar cualquier setting (e.g. sensibilidad de stick)
2. Cerrar MC
3. Reabrir MC → verificar que el valor persiste
4. Verificar que .minecraft/config/steampad/global.json existe y tiene el valor
```

---

## Historial de Fases

| Fase | Estado | Fecha |
|------|--------|-------|
| 0    | ✅ Completada | 2026-06-24 |
| 1    | ✅ Código completo / ⚠️ Sin validar runtime | 2026-06-24 |
| 2    | ✅ Código completo / ⚠️ Sin validar runtime | 2026-06-24 |
| 3    | ✅ Código completo / ⚠️ Parcialmente verificado (unit tests) | 2026-06-24 |
| 4    | ✅ Código completo / ⚠️ Sin validar runtime | 2026-06-24 |
| 5    | ⚠️ Parcial (haptics básico; SDL3 stub) | 2026-06-24 |
| 6    | ⚠️ En progreso (unit tests ✅, manuales ❌) | 2026-06-24 |
