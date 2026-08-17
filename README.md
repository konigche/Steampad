# 🎮 SteamPad

*Read this in [English](#-english) below.*

Este mod lo hice para mis hijos. No soy programador — soy Arquitecto — pero quería que pudieran jugar Minecraft Java con un control de forma cómoda, y que pudieran aprovechar la enorme cantidad de mods que ya teníamos armados. Se me fue un poco de las manos, y desde mi punto de vista quedó increíble. No es perfecto, pero creo que le puede servir a más de uno, así que quiero compartirlo.

---

### 🕹️ Pensado para jugar en familia
Puedes elegir libremente qué control usar, y está pensado especialmente para cuando hay más de una instancia de Minecraft abierta al mismo tiempo (como cuando juegan varios en la misma PC): los controles nunca se pelean entre sí. Puedes predeterminar cuál control le corresponde a cuál instancia, y el mod siempre le va a dar prioridad a ese control cuando se conecte.

### 🖥️ Splitscreen
Cuando juegan dos o más instancias a la vez, esta función acomoda las ventanas de varias formas para que se ajusten mejor a la pantalla y la experiencia de compartir un mismo equipo sea más cómoda. Se activa desde Ajustes Globales.

### 🎥 Cámara en tercera persona
Inspirada en Leawind/Third-Person, le agregué bastante encima:
- **Al montar** — una balsa, un caballo, o la montura de algún mod — la cámara cambia automáticamente de primera a tercera persona, para que veas bien lo que estás manejando. Ajustable desde Ajustes Globales.
- **Conducir en tercera persona ahora se siente bien de verdad** — antes era incómodo, y le puse trabajo específico para que fuera disfrutable.
- **Al apuntar con una flecha**, la cámara se acerca al hombro, como en un shooter, para tener mejor precisión.
- Hay un montón de opciones configurables en Ajustes Globales para que la ajustes a tu gusto.

### 🎯 Aim Assist
Esta es de las que más me gustan. Al apuntar con arco o ballesta, sientes esa ayuda de puntería que ya conoces de otros juegos de disparos — la fuerza de la asistencia es calibrable, y está implementada en todas las armas de proyectil.

### 🔍 Zoom estilo BetterZoom
- Marcador de zoom.
- Barras cinematográficas (ajustable desde Ajustes del Control → Avanzado).
- Puedes acercar o alejar el zoom mientras lo tienes activo, con los botones que tú configures.
- Y bastante más — te invito a explorar la sección Ajustes del Control → Avanzado.

### 🎮 Sistema de control híbrido
Empecé este mod queriendo dar soporte total a Steam Input, y honestamente fue la parte que más se me complicó. Si el mod tiene apoyo de la comunidad, voy a retomar esa integración para que se sienta 100% nativo en SteamOS.

Mientras tanto, tienes SDL3/GLFW como base sólida:
- **Chords** (combos de dos botones) para poder mapear acciones de otros mods sin quedarte sin botones.
- **Sistema de tocar y mantener** — muchos botones pueden tener más de una acción, según si los tocas rápido o los mantienes presionados.
- Y en general, una arquitectura de control moderna pensada para que se sienta natural y cómodo jugar.

### 🎡 Menú Radial
No es solo un radial de hotbar — es un menú pensado para abrir menús de otros mods, lanzar sus acciones, y hasta lanzar comandos de chat rápido. ¿Cuántas veces quisiste hacer `/tp` con un amigo y con el control fue un dolor de cabeza? Con este radial puedes configurar todo tipo de comandos.
- Hasta 6 ruedas con 12 slots cada una.
- Varios estilos visuales (skins).
- Cambias entre ruedas con RB y LB.
- Te invito a armarlo a tu manera — cada quien juega distinto.

*(También me inspiré en velolib/radial para este sistema — gracias a ese proyecto por la base de la idea.)*

### 🧭 Radial de Ítems
RB y LB normalmente sirven para pasar de hotbar en hotbar. Pensé que podíamos aprovechar mucho mejor esos dos botones, así que armé esta otra forma de jugar (configurable en ajustes):
- **LB** abre una rueda de categorías: enfocas, por ejemplo, "bloques", y se abre todo lo que tienes en el inventario relacionado a bloques, para elegir rápido sin abrir el inventario. Lo mismo pasa con comida, armas, etc.
- **RB** abre el hotbar normal del juego.

### 💃 Sistema de emotes
Integré un sistema de emotes que otros jugadores con SteamPad pueden ver en tiempo real. Es compatible con los emotes de Emotecraft, así que además de los 12 que vienen incluidos, tienes acceso a una cantidad enorme ya hechos por la comunidad.

### 📳 Vibración háptica — como en ningún otro mod
A esto le dediqué muchísimo tiempo. Vas a sentir la vibración cuando peleas, cuando rompes un bloque, cuando minas, cuando hay un jefe cerca... Cerca de un portal del Nether, o cerca de un jefe, la vibración se va sintiendo más fuerte mientras más te acercas — lo mismo pasa con las armas. El sistema es tan grande que prefiero que lo descubras tú mismo — hasta cuando te estás ahogando empiezas a sentir esos latidos que te avisan que te estás por morir.

---

Hay mucho cariño puesto en este mod, y muchas cosas técnicas que fui aprendiendo en el camino que sería larguísimo explicar aquí. Mejor descúbranlas jugando — por eso creo que este es el mejor mod de control que existe hoy.

Ahora mismo tengo poco tiempo libre porque trabajo. Si veo que el mod tiene apoyo de la comunidad, voy a hacer el esfuerzo de seguir mejorándolo y tomar en cuenta los comentarios de todos.

## 🔧 Compilar desde el código fuente

Requisitos: **Java 25** disponible como `JAVA_HOME` (lo exige el daemon de Gradle; el propio mod compila en toolchain 21), y una conexión a internet para las dependencias.

```bash
# Variant 1.21.10 (Fabric)
./gradlew :1.21.10-fabric:build

# Variant 1.21.1 (Fabric)
./gradlew :1.21.1-fabric:build
```

Los `.jar` resultantes quedan en `dist/`.

## 📜 Licencia

SteamPad se distribuye bajo **LGPL-3.0-or-later** — ver [`LICENSE`](LICENSE) y [`LICENSE.LESSER`](LICENSE.LESSER).

Este mod usa código e ideas de otros proyectos, con atribución completa y honesta en [`CREDITS.md`](CREDITS.md).

---
---

## 🇬🇧 English

I made this mod for my kids. I'm not a developer — I'm an Architect — but I wanted them to be able to play Minecraft Java comfortably with a controller, and take advantage of the huge amount of mods we already had set up. It got a bit out of hand, and from where I stand, it turned out amazing. It's not perfect, but I think it can be useful to more than a few people, so I want to share it.

---

### 🕹️ Built for playing together
You can freely choose which controller to use, and it's specifically designed for when you have more than one Minecraft instance running at once (like when several people are playing on the same PC): controllers never fight each other. You can set a default controller for each instance, and the mod will always give that instance priority when that specific controller connects.

### 🖥️ Splitscreen
When you're running two or more instances at once, this feature arranges the windows in several ways so they fit the screen better and sharing one machine feels more comfortable. You can turn it on from Global Settings.

### 🎥 Third-Person Camera
Inspired by Leawind/Third-Person, I built quite a bit on top of it:
- **Better mounting** — a raft, a horse, or a mount from some other mod — the camera automatically switches from first to third person so you can actually see what you're riding. Adjustable in Global Settings.
- **Driving in third person actually feels good now** — it used to be awkward, and I put specific work into making it enjoyable.
- **When you aim with a bow**, the camera pulls in over the shoulder, like in a shooter, for better precision.
- There's a ton of configurable options in Global Settings to tune it to your taste.

### 🎯 Aim Assist
This is one of my favorites. When you aim with a bow or crossbow, you feel that same aim assist you already know from other shooter-style games — the strength is adjustable, and it's implemented across every projectile weapon.

### 🔍 BetterZoom-style Zoom
- Zoom marker/beacon.
- Cinematic bars (adjustable under Controller Settings → Advanced).
- You can zoom in or out while it's active, using whatever buttons you set up.
- And plenty more — I invite you to explore the Controller Settings → Advanced section.

### 🎮 Hybrid Input System
I started this mod wanting to give full support to Steam Input, and honestly that turned out to be the most complicated part. If the mod gets enough community support, I'll pick that integration back up so it can feel 100% native on SteamOS.

In the meantime, you've got SDL3/GLFW as a solid foundation:
- **Chords** (two-button combos) so you can map other mods' actions without running out of buttons.
- **Tap-and-hold system** — many buttons can carry more than one action, depending on whether you tap or hold them.
- And overall, a modern controller architecture built so playing feels natural and comfortable.

### 🎡 Radial Menu
This isn't just a hotbar radial — it's a menu built to open other mods' menus, trigger their actions, and even fire off quick chat commands. How many times have you wanted to `/tp` to a friend and doing it with a controller was a headache? With this radial, you can set up any kind of command you want.
- Up to 6 wheels with 12 slots each.
- Multiple visual styles (skins).
- Switch between wheels with RB and LB.
- I invite you to build it your own way — everyone plays differently.

*(I also drew inspiration from velolib/radial for this system — credit to that project for the original idea.)*

### 🧭 Item Radial
RB and LB normally just cycle through your hotbar. I figured we could do a lot more with those two buttons, so I built this alternative way to play (configurable in settings):
- **LB** opens a wheel of categories: focus on, say, "blocks," and everything block-related in your inventory pops open for you to pick quickly, no need to open your inventory. Same goes for food, weapons, and so on.
- **RB** opens the normal in-game hotbar.

### 💃 Emote System
I built in an emote system that other players running SteamPad can see in real time. It's compatible with Emotecraft's emotes, so on top of the 12 that come bundled in, you get access to a huge library the community has already made.

### 📳 Haptic Feedback — unlike anything else out there
I spent a huge amount of time on this one. You'll feel the vibration when you fight, when you break a block, when you mine, when there's a boss nearby... Near a Nether portal, or near a boss, the vibration builds the closer you get — same goes for weapons. The system is big enough that I'd rather you discover it yourself — even when you're drowning, you start feeling those heartbeat-like pulses warning you you're about to die.

---

There's a lot of love in this mod, and a lot of technical stuff I learned along the way that would take forever to explain here. Better to discover it by playing — that's why I think this is the best controller mod out there right now.

Right now my free time is limited because of work. If I see the mod gets community support, I'll make the effort to keep improving it and take everyone's feedback into account.

## 🔧 Building from source

Requirements: **Java 25** available as `JAVA_HOME` (required by the Gradle daemon; the mod itself compiles against toolchain 21), and an internet connection for dependencies.

```bash
# 1.21.10 variant (Fabric)
./gradlew :1.21.10-fabric:build

# 1.21.1 variant (Fabric)
./gradlew :1.21.1-fabric:build
```

Resulting `.jar` files land in `dist/`.

## 📜 License

SteamPad is licensed under **LGPL-3.0-or-later** — see [`LICENSE`](LICENSE) and [`LICENSE.LESSER`](LICENSE.LESSER).

This mod uses code and ideas from other projects, with full, honest attribution in [`CREDITS.md`](CREDITS.md).
