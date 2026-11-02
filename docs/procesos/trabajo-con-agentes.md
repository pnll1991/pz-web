# Trabajo con agentes

Cómo se reparte el trabajo entre agentes (Codex, Claude Code, T3 Code) y personas sin pisarse. Las reglas del port (`AGENTS.md`) valen siempre; esto dice cómo organizarse.

## Una tarea = un brief, una rama, un worktree

- **Brief.** Toda tarea delegada arranca con un brief en `docs/briefs/` que sigue [`PLANTILLA.md`](../briefs/PLANTILLA.md): objetivo, alcance, archivos, recursos compartidos, cómo se prueba y cuándo está terminada. Un brief que no dice cómo se prueba no está listo para delegar.
- **Rama.** Una rama `<tipo>/<tema>` desde `dev` por tarea ([ramas-y-prs.md](ramas-y-prs.md)). Dos agentes nunca comparten rama.
- **Worktree.** Si dos agentes trabajan a la vez en la misma máquina, cada uno en su propio `git worktree`. No compartir el árbol de trabajo ni el stash.

## Recursos compartidos de la máquina

Un build del juego usa ~20 GB de RAM y Chrome con el juego, varios más. Antes de usar uno de estos recursos, el agente comprueba que esté libre y, si no lo está, lo anota en el PR o en `STATUS.md` en vez de esperar o forzar:

| Recurso | Regla |
|---|---|
| Build de Maven/TeaVM | Uno a la vez en la máquina |
| Chrome de prueba | Uno a la vez; tomar `chrome.lock`; perfil propio en `/tmp`, borrado al terminar |
| Dev server 8098, servidor PZ, túnel | En vivo: no se tocan sin OK del dueño (`docs/HANDOFF-T3.md`). Para probar, puerto temporal (8199) e instancia de prueba (`PZ_MP_DATA`/`PZ_MP_PORT`) |
| `~/pz-pinned`, Steam, Chrome de escritorio | No se tocan |

## Qué cuenta como evidencia

De menos a más. Un PR dice hasta qué nivel llegó y por qué no siguió:

1. **Sintaxis y Node**: `node --check`, `npm test`. Corre en la CI.
2. **JVM**: pruebas del Java propio (`port/`, `tools/`).
3. **Chrome de plataforma**: `npm run test:browser`, con SwiftShader. No mide rendimiento.
4. **Build real**: `bash scripts/build.sh --with-game` contra la copia fijada.
5. **Chrome con el juego**: sonda, `scripts/bench.sh` o capturas locales. Es el único nivel que demuestra un hito del juego.

Una demo sintética no demuestra que el juego funcione. Las capturas, logs y grabaciones JNI se guardan fuera del repo (`~/pzshots/`) y en el PR se describen, no se adjuntan si muestran la URL del túnel o datos de jugadores.

## Cuidar el contexto

- Leer primero lo que pide `AGENTS.md`. De los documentos largos (`STATUS.md`, `AGENT_LEARNINGS.md`, `docs/status-archive.md`) leer por rangos: el principio, la ronda actual y lo que se busca con `grep`.
- Los logs de build y de Chrome no se vuelcan enteros: `tail`, `grep` o el resumen de la sonda.
- Los relevamientos largos de un subagente van a un archivo ignorado (`build/agentes/<tarea>.md`) y el subagente devuelve un resumen de una pantalla.

## Subagentes y entrega

- La sesión principal reparte, revisa y commitea. Los subagentes trabajan sobre archivos distintos y no commitean ni tocan `package-lock.json`, `port/pom.xml` o `scripts/*-lock.json`; si necesitan una dependencia, lo proponen.
- Al terminar, la sesión principal abre el PR a `dev` sin esperar un pedido aparte, y avisa con el link, el nivel de evidencia alcanzado y lo que no se pudo probar.
- El reporte de una ronda (`~/pzshots/CLAUDE_REPORT_R<N>.md`) sigue existiendo para el dueño; lo esencial (evidencia, faltantes, bloqueos) también va en `STATUS.md` y en el PR, que es lo que leen los demás.

## Aprendizajes

`AGENT_LEARNINGS.md` se actualiza después de cada error con causa, patrón a evitar y mejor enfoque, **en el mismo PR** donde ocurrió. Antes de agregar una entrada, buscar si ya existe una igual (`grep -n`) y ampliarla en vez de repetirla: varias de las entradas de octubre repiten el mismo error (usar `rg` ausente, truncar lecturas agrupadas). Cuando una lección se repite tres veces, se sube como regla a `AGENTS.md`.
