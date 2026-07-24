# CLAUDE.md — SteamPad Mod: Resumen Operativo

## Identidad del Proyecto

- **Nombre:** SteamPad
- **Plataforma:** Minecraft Java Edition **1.21.10** (migrado desde 1.21.4 en sesión 4 — el build DEBE coincidir con la versión de runtime; ver STATE.md Fix 12). Backups 1.21.4 en `*.bak1214`.
- **Loader:** Fabric (runtime loader 0.19.3; build declara 0.16.x, compatible)
- **Java:** 21 (Java 25 rompe el build — ver TODO_BLOCKERS B011)
- **Build:** Gradle **8.14** (Loom 1.13.6 lo exige) con Fabric Loom
- **Package raíz:** `dev.steampad`
- **Backends de input:** SDL3 (JNA, principal para gameplay) → GLFW (baseline). Steam Input corre EN PARALELO, solo para las ranuras genéricas (paddles → keybind, ver D030/D032) — nunca toma el control del gameplay normal. Ver STATE.md Fix 13–15 y D032 (sesión 19 cont.).

## Objetivo Central

Mod Fabric para Minecraft que integra Steam Input API como backend principal de compatibilidad de controladores, con foco en:
- SteamOS / Steam Deck
- Bazzite / Linux escritorio
- Gamescope / Steam Game Mode

## Restricciones Inamovibles

1. ~~Steam Input es el backend principal. SDL3 solo como fallback documentado.~~ **REVISADO (sesión 19 cont., D032):** SDL3/GLFW manejan SIEMPRE el gameplay normal (movimiento, cámara, menús, BOTONES); Steam Input corre en paralelo solo para las ranuras genéricas de paddles. Razón: promover Steam Input a principal exige mapear CADA acción del juego en el configurador de Steam (no solo las ranuras) — rompe la experiencia "funciona solo" que SDL3 ya daba. Ver D032 en DECISIONS.md.
2. Sin splitscreen dentro del mod.
3. UI 100% navegable con mando.
4. Mixins: hooks delgados únicamente, cero lógica pesada.
5. No implementar soporte manual por modelo de dispositivo si Steam Input ya lo abstrae.
6. Solo un controlador activo por instancia de Minecraft.
7. Las múltiples instancias de Minecraft NO son responsabilidad del mod: solo aislamiento lógico interno.

## Definition of Done

- [ ] 1. Proyecto compila sin errores
- [ ] 2. Se genera .jar del mod
- [ ] 3. Configs funcionan y persisten en .minecraft/config/steampad/
- [ ] 4. Pantalla de selección de controladores funciona
- [ ] 5. Aislamiento por instancia implementado
- [ ] 6. Pantallas de configuración navegables con gamepad
- [ ] 7. Bindings base funcionan
- [ ] 8. Chords funcionan sin doble acción
- [ ] 9. Radial funciona con configuración visual y funcional
- [ ] 10. Debug dump existe
- [ ] 11. Documentación mínima de instalación/uso/pruebas
- [ ] 12. TASKS.md y STATE.md reflejan estado real
- [ ] 13. Bugs críticos: cero abiertos o documentados con causa externa
- [ ] 14. Jar final entregado con lista de pruebas realizadas

## Stack

| Componente        | Biblioteca                          | Rol                                 |
|-------------------|-------------------------------------|-------------------------------------|
| Loader            | Fabric Loader 0.16.x                | Base del mod                        |
| APIs del juego    | Fabric API 0.111.x                  | Hooks de ciclo de vida, render      |
| Steam             | Steamworks4j 1.9.0                  | Steam Input API, native loader      |
| Config UI         | Cloth Config API 15.x               | Pantallas de configuración          |
| Serialización     | Gson 2.10.x (bundled en MC)         | Persistencia JSON                   |
| Mixins            | Mixin (via Fabric)                  | Hooks mínimos                       |
| SDL fallback      | SDL3 via JNA (opcional)             | Detección robusta en Linux/Gamescope|
| Compat opcional   | MaLiLib (soft dep)                  | Keybinds avanzados en radial        |

## Regla Mandatoria: Graphify y Navegación del Repositorio

- **SIEMPRE** antes de realizar búsquedas masivas (`grep`) o explorar código a ciegas, ejecuta o consulta `/graphify .` o la carpeta `graphify-out/graph.json`.
- Usa las consultas de Graphify para entender relaciones entre clases y métodos sin gastar tokens leyendo archivos completos.
- Después de modificar archivos de código en una sesión, ejecuta `graphify extract . --code-only` para mantener el grafo al día sin costo de API.

## Archivos de Control

| Archivo            | Propósito                                      |
|--------------------|------------------------------------------------|
| CLAUDE.md          | Este archivo — referencia operativa fija       |
| SPEC.md            | Especificación funcional completa              |
| ARCHITECTURE.md    | Paquetes, clases, flujo de datos               |
| TASKS.md           | Backlog por fases con checklist                |
| STATE.md           | Estado actual del proyecto                     |
| TESTPLAN.md        | Estrategia de pruebas                          |
| DECISIONS.md       | Decisiones técnicas y justificación            |
| TODO_BLOCKERS.md   | Bloqueos reales, riesgos, desvíos              |

## Regla de Reanudación y Actualización

Frase de activación rápida: Si el usuario dice **"Lee los archivos para entrar en contexto"**, debes automáticamente:
1. Leer `STATE.md` → punto exacto de reanudación
2. Leer `TODO_BLOCKERS.md` → bloqueos activos
3. Leer `TASKS.md` → próxima tarea pendiente
4. Consultar `graphify-out/graph.json` o ejecutar `/graphify .` para cargar la estructura en memoria de forma económica.

Al finalizar cualquier modificación de código:
1. Actualizar `STATE.md`, `PROGRESS.md` y `TASKS.md` como de costumbre.
2. Ejecutar `graphify extract . --code-only` en la terminal para actualizar automáticamente el grafo de código (sin costo de API) antes de cerrar la sesión.
