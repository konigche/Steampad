# tools/

Herramientas del port multi-versión. No forman parte del mod: son andamiaje de desarrollo.

## `audit_mixin_descriptors.py`

Comprueba que cada selector de mixin (`@Inject(method=…)`, `@Redirect`, `@Invoker`) coincida con el
**descriptor JVM completo** del método real en el jar de Minecraft de esa versión.

**Por qué existe.** Estos selectores son *strings*: el compilador no los verifica. Un objetivo que no
exista **aborta el arranque del juego**, no el build. Y comprobar solo el *nombre* no basta —
así se colaron los primeros minutos de vida del variant de 1.21.1:

```
@Inject on steampad$applyZoom could not find any targets matching
'getFov(Lnet/minecraft/client/Camera;FZ)F' in net/minecraft/class_757
```

`GameRenderer.getFov` **sí existe** en 1.21.1, con los mismos parámetros. Lo que cambia es el tipo de
**retorno**: `double` en 1.21.1, `float` desde 1.21.9. Un grep por nombre lo aprueba; el juego crashea.

**Cómo usarla.** Audita las fuentes que de verdad se compilan para esa versión, no `src/` a secas:
para el variant **activo** eso es `src/main/java/dev/steampad/mixin`; para los demás, el árbol que
genera Stonecutter (`…/generated/stonecutter/main/java/dev/steampad/mixin`), porque en `src/` conviven
las dos ramas de cada condicional y auditar ahí da falsos positivos.

```bash
# variant activo (el que marca versions/current)
python tools/audit_mixin_descriptors.py <jar-de-MC> src/main/java/dev/steampad/mixin

# cualquier otro variant, tras un build
python tools/audit_mixin_descriptors.py <jar-de-MC> \
  <build>/generated/stonecutter/main/java/dev/steampad/mixin
```

Los jars remapeados de Minecraft están en el caché de Loom:
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/<version>-loom.mappings.*/`

**Cuándo es obligatoria.** Al añadir un variant de versión, y después de tocar cualquier mixin.
Salida esperada: `todos los descriptores coinciden exactamente`. Ver D142 en `DECISIONS.md`.

Nota: ignora los bloques `/* … */`, así que la rama inactiva de un condicional no cuenta — y un
archivo cuya clase entera está gateada fuera de esa versión se reporta como
`(sin clase activa en esta version)`, que es correcto, no un fallo.
