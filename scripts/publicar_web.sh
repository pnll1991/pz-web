#!/usr/bin/env bash
# Publica en vivo un update sin build (docs/procesos/updates-sin-build.md): trae origin/main al checkout que sirve
# el dev server 8098 y copia port/web/* y port/runtime/*.mjs a port/target/web con sync_web.sh. Un reload de la
# página lo toma (el server lee cada archivo por petición, Cache-Control: no-cache). No reinicia nada.
# Se corre en la máquina en vivo y solo con OK del dueño. Se niega si lo que entra necesita un build o un reinicio.
#   bash scripts/publicar_web.sh --dry-run   clasifica lo que entraría y no toca nada
#   bash scripts/publicar_web.sh             fast-forward de main + sync_web.sh + comprobación de lo servido
# Salida: 0 publicado o al día; 2 uso o checkout no apto; 3 necesita build; 4 necesita reiniciar algo en vivo.
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
cd "$PZ_WEB_ROOT"

dry=0
case "${1:-}" in
  "") ;;
  --dry-run) dry=1 ;;
  *) echo "uso: $0 [--dry-run]" >&2; exit 2 ;;
esac

branch=$(git rev-parse --abbrev-ref HEAD)
[[ "$branch" == main ]] || { echo "el checkout en vivo tiene que estar en main (está en $branch)" >&2; exit 2; }
[[ -z "$(git status --porcelain --untracked-files=no)" ]] || { echo "hay cambios sin commitear en el checkout en vivo; no se publica" >&2; git status --short --untracked-files=no >&2; exit 2; }

git fetch -q origin main
old=$(git rev-parse HEAD) new=$(git rev-parse origin/main)
[[ "$old" != "$new" ]] || { echo "al día: $(git log -1 --format='%h %s' HEAD)"; exit 0; }
git merge-base --is-ancestor "$old" "$new" || { echo "main local no es ancestro de origin/main; no se puede hacer fast-forward" >&2; exit 2; }

# Clasificación por ruta. Lo que no está en ninguna lista cuenta como build: ante la duda, no se publica en caliente.
recarga=() reinicio=() build=() inerte=()
while IFS= read -r f; do
  case "$f" in
    port/web/*/*) build+=("$f") ;;                      # sync_web.sh solo copia el primer nivel de port/web
    port/web/*|port/runtime/*.mjs) recarga+=("$f") ;;
    dev-server/*|scripts/mp-server.sh|scripts/mp-bridge.sh|scripts/public-tunnel.sh|scripts/dev_server_8098.sh) reinicio+=("$f") ;;
    *.md|docs/*|.claude/*|.github/*|.githooks/*|tests/*|tools/diag/*|scripts/check-*.sh|scripts/install-hooks.sh|scripts/publicar_web.sh|AGENT_LEARNINGS.md) inerte+=("$f") ;;
    *) build+=("$f") ;;
  esac
done < <(git diff --name-only "$old" "$new")

echo "entran $(git rev-list --count "$old..$new") commit(s): ${old:0:7}..${new:0:7}"
git log --format='  %h %s' "$old..$new"
show() { local title=$1; shift; (( $# )) || return 0; echo "$title"; printf '  %s\n' "$@"; }
show "se ven al recargar la página:" "${recarga[@]+"${recarga[@]}"}"
show "sin efecto en vivo (docs, pruebas, CI):" "${inerte[@]+"${inerte[@]}"}"
show "necesitan reiniciar algo en vivo (dev server, servidor PZ o túnel):" "${reinicio[@]+"${reinicio[@]}"}"
show "necesitan build (scripts/build.sh) o reempaquetar:" "${build[@]+"${build[@]}"}"

if (( ${#build[@]} )); then echo "no es un update sin build: no se publica en caliente" >&2; exit 3; fi
if (( ${#reinicio[@]} )); then echo "hay que reiniciar algo en vivo: hacerlo a mano con OK del dueño (docs/HANDOFF-T3.md); no se publica" >&2; exit 4; fi
if (( dry )); then echo "--dry-run: no se tocó nada"; exit 0; fi

git merge -q --ff-only "$new"
bash scripts/sync_web.sh
# Comprobar que lo servido es lo que entró (AGENT_LEARNINGS R85/R86: probar sin sync_web.sh fue un error repetido).
bad=0
for f in "${recarga[@]+"${recarga[@]}"}"; do
  [[ -f "$f" ]] || continue                             # borrado en el repo: sync_web.sh no borra lo servido
  case "$f" in port/web/*) served="port/target/web/${f#port/web/}" ;; *) served="port/target/web/runtime/${f#port/runtime/}" ;; esac
  cmp -s "$f" "$served" || { echo "no coincide lo servido: $served" >&2; bad=1; }
done
(( bad == 0 )) || exit 1
echo "publicado ${new:0:7}. Los jugadores lo ven al recargar la página; la URL no cambia."
