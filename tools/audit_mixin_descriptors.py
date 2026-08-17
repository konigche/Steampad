"""Audita los selectores de mixin comparando DESCRIPTORES JVM COMPLETOS, no nombres.

El fallo que motivó esto: `GameRenderer.getFov` existe en 1.21.1 con los mismos parámetros pero
devolviendo `double` en vez de `float`. Un grep por nombre lo aprueba; el `@Inject` lleva el
descriptor completo y revienta al arrancar el juego. Esto usa `javap -s`, que imprime el descriptor
real del bytecode, así que la comparación es exacta.

Uso: python audit_descriptors.py <ruta-al-jar-de-MC>
"""
import re
import subprocess
import sys
import zipfile
from pathlib import Path

JAVAP = r"C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot\bin\javap.exe"

# IMPORTANTE: se auditan las fuentes GENERADAS por Stonecutter, no `src/`. En `src/` conviven las dos
# ramas de cada condicional, así que auditarlo ahí marca falsos positivos de la rama inactiva. El árbol
# generado contiene solo la rama que de verdad se compila para esa versión — es lo que hay que
# comparar contra el jar.
SKIP_ON_LEGACY = set()
MODERN_ONLY = set()


def mixin_target(text):
    m = re.search(r"@Mixin\((?:targets\s*=\s*)?\"?([A-Za-z0-9_.$]+)\"?\)", text)
    if not m:
        return None
    name = m.group(1)
    return name[:-6] if name.endswith(".class") else name


def fqcn_for(simple, text):
    """Resuelve el nombre simple del @Mixin a su FQCN usando los imports del archivo."""
    root = simple.split(".")[0]
    m = re.search(r"^import\s+([a-z0-9_.]+\." + re.escape(root) + r");", text, re.M)
    if m:
        return m.group(1) + simple[len(root):]
    return None


def descriptors_of(jar, fqcn):
    """Todos los (nombre, descriptor) declarados por la clase, según javap -s."""
    # Una clase anidada llega como "…ModelPart.Cube": el segmento capitalizado tras otro capitalizado
    # es un '$' en el nombre interno del bytecode.
    parts = fqcn.split(".")
    internal = ""
    for p in parts:
        if internal and p[:1].isupper() and internal.rsplit("/", 1)[-1][:1].isupper():
            internal += "$" + p
        else:
            internal += ("/" if internal else "") + p
    entry = internal + ".class"
    with zipfile.ZipFile(jar) as z:
        if entry not in z.namelist():
            return None
        out = Path("_probe.class")
        out.write_bytes(z.read(entry))
    txt = subprocess.run([JAVAP, "-p", "-s", str(out)], capture_output=True, text=True).stdout
    out.unlink(missing_ok=True)
    pairs = set()
    lines = txt.splitlines()
    for i, line in enumerate(lines):
        if "descriptor:" not in line:
            continue
        desc = line.split("descriptor:")[1].strip()
        # el nombre está en la línea de declaración justo encima
        decl = lines[i - 1] if i else ""
        nm = re.search(r"([A-Za-z0-9_$<>]+)\s*\(", decl)
        if nm:
            pairs.add((nm.group(1), desc))
        else:  # campo
            fld = re.search(r"([A-Za-z0-9_$]+)\s*;\s*$", decl)
            if fld:
                pairs.add((fld.group(1), desc))
    return pairs


def strip_block_comments(text):
    """Quita los bloques /* ... */.

    Imprescindible: en el árbol generado la rama INACTIVA del condicional sigue presente como
    comentario, así que sin esto se auditan selectores que no se compilan y salen falsos positivos.
    El javadoc también desaparece, lo cual da igual — no contiene selectores de mixin reales.
    """
    return re.sub(r"/\*.*?\*/", "", text, flags=re.S)


def main(jar, src):
    SRC = Path(src)
    problems, checked = [], 0
    for f in sorted(SRC.glob("*.java")):
        text = strip_block_comments(f.read_text(encoding="utf-8", errors="replace"))
        if "@Mixin" not in text:
            print(f"  (sin clase activa en esta version) {f.stem}")
            continue
        if f.stem in SKIP_ON_LEGACY:
            print(f"  (saltado, gateado fuera de esta version) {f.stem}")
            continue
        simple = mixin_target(text)
        if not simple:
            continue
        fqcn = fqcn_for(simple, text)
        if not fqcn:
            problems.append(f"{f.stem}: no pude resolver el FQCN de @Mixin({simple})")
            continue
        pairs = descriptors_of(jar, fqcn)
        if pairs is None:
            problems.append(f"{f.stem}: CLASE AUSENTE en el jar -> {fqcn}")
            continue
        # Selectores activos = los que NO estan dentro de un bloque comentado por Stonecutter.
        # Dos formas: descriptor completo ("render(...)V") o nombre desnudo ("render", valido en
        # Mixin solo si el nombre es UNICO en la clase objetivo — Mixin lo resuelve el mismo, pero
        # revienta en runtime si hay mas de un metodo con ese nombre).
        for m in re.finditer(r'method\s*=\s*"([A-Za-z_$][A-Za-z0-9_$]*)"(?!\s*\()', text):
            nm = m.group(1)
            checked += 1
            matches = sorted(d for n, d in pairs if n == nm)
            if len(matches) == 0:
                problems.append(f"{f.stem}: selector desnudo \"{nm}\" — NINGUN metodo con ese nombre")
            elif len(matches) > 1:
                problems.append(
                    f"{f.stem}: selector desnudo \"{nm}\" es AMBIGUO — {len(matches)} sobrecargas: {matches}")
        for m in re.finditer(r'(?:method|value)\s*=\s*"([^"]*\([^"]*\)[^"]*)"', text):
            sel = m.group(1)
            if sel.startswith("L"):          # call site de @Redirect, se audita aparte
                continue
            if sel in MODERN_ONLY:
                continue
            nm, desc = sel.split("(", 1)
            desc = "(" + desc
            checked += 1
            if (nm, desc) not in pairs:
                same_name = sorted(d for n, d in pairs if n == nm)
                problems.append(
                    f"{f.stem}: NO COINCIDE  {nm}{desc}\n"
                    f"      la clase declara: {same_name if same_name else '(ningun metodo con ese nombre)'}")
        # @Accessor/@Invoker: el string es el nombre del CAMPO (Accessor) o METODO (Invoker) real de
        # la clase objetivo, casi siempre en forma posicional ("@Accessor(\"campo\")", sin "value=" ni
        # "method=" literal) — por eso las dos pasadas de arriba nunca los tocaban: ambas exigen ese
        # "=" en el texto. Se colaron sin auditar desde que existe el tipo de mixin (verificado: cero
        # menciones de @Accessor/@Invoker en este script antes de este parche). Incluye ademas la forma
        # explicita @Accessor(value="campo") por si alguna vez se escribe asi.
        for m in re.finditer(r'@(?:Accessor|Invoker)\s*\(\s*(?:value\s*=\s*)?"([A-Za-z_$][A-Za-z0-9_$]*)"', text):
            nm = m.group(1)
            checked += 1
            if not any(n == nm for n, _ in pairs):
                problems.append(f"{f.stem}: @Accessor/@Invoker \"{nm}\" — NINGUN campo/metodo con ese nombre")
    print(f"\nselectores con descriptor comprobados: {checked}")
    if problems:
        print(f"\n*** {len(problems)} PROBLEMA(S) ***")
        for p in problems:
            print("  " + p)
        sys.exit(1)
    print("todos los descriptores coinciden exactamente")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
