#!/usr/bin/env bash
# Guarda de archivos prohibidos (AGENTS.md, "Reglas"): nada del juego, binarios, media, packs ni decompilado.
# Uso:
#   bash scripts/check-forbidden.sh            # revisa lo que está en el índice (pre-commit)
#   bash scripts/check-forbidden.sh <base>     # revisa lo agregado o cambiado desde <base> (CI: origin/dev)
#   bash scripts/check-forbidden.sh --all      # revisa todo lo versionado
# Sale con 1 y lista los archivos si encuentra alguno. No lee el contenido salvo para medir el tamaño.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

# Tamaño máximo de un archivo versionado. Los recursos grandes van a build/ o packs/, que están ignorados.
max_bytes=$((2 * 1024 * 1024))

case "${1:-}" in
  "") files=$(git diff --cached --name-only --diff-filter=ACMR) ;;
  --all) files=$(git ls-files) ;;
  *) files=$(git diff --name-only --diff-filter=ACMR "$1"...HEAD) ;;
esac

# Extensiones y rutas que nunca se versionan. Mantener en línea con .gitignore.
pattern='\.(jar|class|so|so\.[0-9.]+|bank|wasm|zip|tar|tar\.[a-z0-9]+|deb|pack|lotheader|lotpack|bin)$'
pattern+='|^(media|packs|inventory|pz-decomp|game|sdk|\.toolchain|build)/|(^|/)target/|(^|/)media/(lua|maps|textures|sound|models)'
pattern+='|(^|/)\.env$|\.key$|(^|/)supervisor\.log$'

bad=()
while IFS= read -r f; do
  [ -z "$f" ] && continue
  if printf '%s\n' "$f" | grep -Eqi "$pattern"; then
    bad+=("$f  (ruta o extensión prohibida)")
  elif [ -f "$f" ] && [ "$(wc -c <"$f")" -gt "$max_bytes" ]; then
    bad+=("$f  (más de $((max_bytes / 1024 / 1024)) MiB)")
  fi
done <<<"$files"

if [ "${#bad[@]}" -gt 0 ]; then
  echo "Archivos que no se pueden versionar (AGENTS.md, Reglas):" >&2
  printf '  %s\n' "${bad[@]}" >&2
  echo "Si es un archivo propio legítimo, discutirlo en el PR antes de agregar una excepción acá." >&2
  exit 1
fi
echo "check-forbidden: ok ($(printf '%s\n' "$files" | grep -c . || true) archivos revisados)"
