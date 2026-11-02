---
name: updates-sin-build
description: Cómo se manda a la partida en vivo de pz-web un cambio que no necesita compilar Java/TeaVM ni reempaquetar (port/web/*, port/runtime/*.mjs) - qué califica, cómo se prueba sin build, qué se dice en el PR y cómo se publica con scripts/publicar_web.sh para que los jugadores lo vean al recargar. Cargar al proponer, hacer o publicar una mejora chica que se tenga que notar online.
---

# Updates sin build

Detalle completo: `docs/procesos/updates-sin-build.md`. Checklist:

1. El diff toca solo `port/web/*` (primer nivel), `port/runtime/*.mjs` y, si acompañan, docs o pruebas. Nada de `port/src`, `port/config`, `pom.xml`, `tools/pack`, `dev-server/` ni scripts del servidor o el túnel.
2. El cambio se nota en vivo: el PR dice qué ve el jugador y dónde, y lleva la etiqueta `sin build` en "Riesgos y pendientes".
3. Probar: `npm test`; `bash scripts/sync_web.sh && grep -c '<cambio>' port/target/web/<archivo>`; Chrome contra el `boot.wasm` existente en 8199, nunca en 8098. `play.lua` en la instancia de prueba del servidor.
4. PR a `dev` según la skill `ramas-y-prs`. No mergear sin pedido explícito.
5. Publicar solo con OK del dueño, en la máquina en vivo: mirar `/tmp/pzweb-access.log`, `bash scripts/publicar_web.sh --dry-run` y después sin `--dry-run`. Si sale con 3 (build) o 4 (reinicio), no es un update sin build.
6. Avisar a los jugadores que recarguen. La URL no cambia.
