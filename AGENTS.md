# Port personal de Project Zomboid al navegador

## Reglas
- Leer `AGENT_LEARNINGS.md` al comenzar cada chat; crearlo si falta y actualizarlo después de cada error con causa, patrón a evitar y mejor enfoque.
- Leer `docs/CODEX_TASK.md`, `docs/ROADMAP-source.txt` y `STATUS.md` antes de trabajar.
- Nunca versionar archivos del juego, `.jar`, `.class`, `.so`, `media/`, `.bank`, mapas, código decompilado/desensamblado ni código derivado del juego.
- Leer la copia fija desde `PZ_HOME`; no copiarla al repositorio. Inventarios: solo nombres, tamaños, firmas y metadatos.
- Uso personal. El único remoto es el repositorio privado `pnll1991/pz-web`: no hacerlo público ni crear otros remotos. Servidor exclusivamente en `127.0.0.1`.
- Las nativas ausentes lanzan una excepción con su nombre JNI; nunca simular éxito.
- No cambiar reglas del juego: solo plataforma, archivos, gráficos, input, audio y nativas.
- No usar sudo ni modificar herramientas globales. Instalar en `.toolchain/`; caches y temporales en rutas ignoradas.

## Stack y estructura
- JDK 25, Maven, TeaVM 0.16.0 con WebAssembly GC, Node 24, Chrome estable.
- Emscripten solo para Bullet, como módulo wasm32 separado.
- `scripts/`: instalación local, builds, verificación y arranque.
- `tools/inventory/`: inventario determinista y offline de la copia Linux.
- `tools/pack/`: empaquetado local de recursos; salida ignorada.
- `port/`: arranque Java, integración TeaVM y shims propios.
- `dev-server/`: servidor local COOP/COEP con ETag y Range.
- `tests/`: fixtures sintéticas propias y pruebas JVM/Node/Chrome.
- `docs/procesos/`: cómo se trabaja (ramas, PRs, agentes). `docs/briefs/`: un brief por tarea delegada.
- `.claude/skills/`: puerta de entrada a cada proceso para agentes; el detalle vive en `docs/procesos/`.
- `docs/decisions.md`: una decisión por línea, con motivo y fuente cuando corresponda.
- `STATUS.md`: fase actual, evidencia, faltantes, bloqueos y pasos manuales.

## Verificación
- Builds mediante `scripts/build.sh`, por defecto `MAVEN_OPTS=-Xmx20g`.
- Cambios pequeños, en una rama propia y entregados como PR a `dev` (ver abajo).
- Probar Java propio en JVM y el resultado Wasm GC en Chrome (Playwright).
- Una demo sintética no demuestra que el juego compile o funcione; no marcar hitos del juego cumplidos sin ejecutarlos.
- No inventar firmas, versiones ni comportamientos de PZ. El inventario real y las capturas del dueño son la fuente de verdad.
- Comparar futuras implementaciones con logs, grabaciones JNI y capturas locales del juego; no subir esos datos.

## Ramas, PRs y agentes
- Nada se commitea directo en `main` ni en `dev`: cada tarea va en una rama `<tipo>/<tema>` que sale de `dev` y vuelve por PR a `dev` con `.github/pull_request_template.md`.
- **Ningún agente mergea un PR salvo pedido explícito de una persona para ese PR.** La CI en verde o la tarea terminada no son permiso.
- Títulos de commit y PR en Conventional Commits, con la descripción en español. `bash scripts/install-hooks.sh` activa los hooks locales.
- La CI corre sin la copia del juego (archivos prohibidos, título, `npm test`); el build real y Chrome con el juego se verifican en local y se informan en el PR.
- Un build Maven y un Chrome de prueba a la vez; nada en vivo (8098, servidor PZ, túnel) sin OK del dueño.

| Si vas a… | Leer / skill |
|---|---|
| Crear una rama, commitear, pushear, abrir o mergear un PR, publicar `dev → main` | `docs/procesos/ramas-y-prs.md` · skill `ramas-y-prs` |
| Empezar una ronda, escribir un brief, lanzar un subagente o integrar lo que devolvió | `docs/procesos/trabajo-con-agentes.md` · skill `trabajo-con-agentes` |
| Tocar algo que está en vivo (dev server, servidor PZ, túnel) | `docs/HANDOFF-T3.md` |

Proceso nuevo = documento en `docs/procesos/` + skill (o sección en una existente) + fila en esta tabla, en el mismo PR.
