# Tarea continua (ronda automática, modelo local Gyro-M vía Codex + OpenCodex)

Leé las últimas 80 líneas de STATUS.md, AGENTS.md, docs/ROADMAP-source.txt, la última tarea en docs/ (CODEX_TASK_4.md) y los reportes en port/target/reports/. PZ_HOME=/home/agente47/pz-pinned/25485521/projectzomboid (42.21). El trabajo previo ya está commiteado por el dueño.

Continuá exactamente desde donde quedó la ronda anterior según STATUS.md, siempre hacia el objetivo final: el menú principal real de Project Zomboid corriendo de forma nativa en Chrome (TeaVM/Wasm + WebGL2, NO streaming). Orden: 0 APIs ausentes y boot.wasm del main real -> arranque en Chrome (LuaManager.init, etc.) -> render GL/WebGL2, GLFW, loop, shaders -> menú visible.

RAM: ahora el servidor local Strata (el modelo que te está ejecutando) usa ~19 GB; quedan ~10 GB. Usá MAVEN_OPTS=-Xmx8g (el build Lua completo con caché compacta pico ~6 GiB). NUNCA detengas, reinicies ni toques Strata ni el puerto 8083: sos vos. No corras el juego nativo ni pruebas pesadas de navegador en paralelo con el build. No toques aplicaciones del dueño (Strata).
Mismas reglas de AGENTS.md: nada de archivos del juego ni código decompilado en git, nunca stubs que simulen éxito.

Cuando, y solo cuando, el menú principal real se vea renderizado en Chrome y esté verificado con una captura en test-results/ y una prueba Playwright que pase, creá el archivo MENU_REACHED en la raíz del repo con la ruta de la captura y la prueba. No lo crees en ningún otro caso.
Al terminar la ronda actualizá STATUS.md con el avance, los bloqueos y el siguiente paso concreto. No hagas commits ni toques .git: el vigilante commitea al final de cada ronda. Trabajá en pasos chicos y verificables; si el contexto se llena, dejá STATUS.md actualizado para que la siguiente ronda continúe.

## Bloqueo actual, ya diagnosticado, NO volver a analizar
TextureFBO.funcs == null porque GLCapabilities.OpenGL30 queda en false en WebGL2 (check_GL30 exige entrypoints de OpenGL desktop). Eso rompe el texImage2D/FBO en UI_Loading_ModelsAnimations.
Tu PRIMERA acción de la ronda tiene que ser un apply_patch que agregue:
  (a) un sondeo FBO real en el bridge (port/runtime/gl.mjs: crear framebuffer + texture, checkFramebufferStatus == FRAMEBUFFER_COMPLETE, exponerlo a Java);
  (b) un transformer que haga que el gate de TextureFBO (selección de funcs) use ese sondeo en vez de GLCapabilities.OpenGL30.
Nada de stubs que simulen éxito: el sondeo tiene que ser real. No corras javap sobre GLCapabilities.

## REGLA CRÍTICA: cada mensaje con acción lleva su llamada a herramienta
Nunca termines un mensaje anunciando una acción ("Empiezo a editar…", "Let me…") sin hacer la llamada a herramienta en ese mismo mensaje. Un mensaje sin llamada a herramienta TERMINA la ronda y se pierde todo.

## STATUS.md: leé solo el final
Leé solo las últimas 80 líneas: `tail -n 80 STATUS.md`. Las rondas viejas están en docs/status-archive.md; no lo leas salvo necesidad puntual.

## OBLIGATORIO al terminar cada ronda: actualizar STATUS.md

Antes de dar la ronda por terminada, reescribí `STATUS.md` para que refleje el estado REAL actual (no el de rondas anteriores):
- Número de ronda y fecha/hora.
- Qué se cambió en esta ronda (archivos y funcionalidad).
- Hasta dónde llega hoy el arranque en Chrome (última etapa alcanzada y el error exacto donde se detiene), verificado con la prueba/captura de esta ronda.
- El bloqueo actual y los siguientes pasos concretos, en orden.
- Resultado de las pruebas de esta ronda.
La siguiente ronda arranca leyendo este archivo: si queda viejo, se pierde el trabajo. No termines la ronda sin actualizarlo.

## OBLIGATORIO: menos análisis, más código
- Límite de análisis: como máximo 10 comandos de lectura (javap, grep, sed, cat, ls) antes de la primera edición real de código. No repitas análisis que ya figura en STATUS.md o en commits anteriores.
- Trabajá en pasos chicos: editá, compilá, probá en Chrome, y repetí. Cada 30-40 minutos tiene que haber cambios en archivos del repo.
- Si un detalle no está claro, elegí la implementación más simple que avance el arranque y anotá la duda en STATUS.md, en vez de seguir investigando.

## OBLIGATORIO: cierre antes del límite de 4 horas
Cada ronda se corta sola a las 4 horas exactas y, hasta ahora, ninguna llegó a actualizar STATUS.md.
- La hora de inicio de tu ronda es la de la última línea "lanzando ronda N (local)" en supervisor.log. Revisala al empezar (`grep 'lanzando ronda' supervisor.log | tail -1`) y mirá `date` cada tanto (por lo menos cada 20-30 comandos).
- Cuando lleves 3 horas y 30 minutos (o antes, si terminaste), dejá de programar: corré los tests rápidos que puedas, actualizá STATUS.md según la sección "actualizar STATUS.md" de arriba, y terminá la ronda.
- Un STATUS.md actualizado vale más que 20 minutos extra de código sin documentar.

## OBLIGATORIO: ediciones chicas y STATUS.md incremental (desde la ronda 26)

- Nunca escribas ni reescribas un archivo entero en una sola llamada de herramienta. Las llamadas muy largas se rompen (salen como texto `<tool_call>` y la ronda se corta). Editá con parches chicos: como máximo unas 80 líneas por llamada; si un archivo nuevo es más largo, crealo en varias partes (por ejemplo, un primer bloque y después agregados con `cat >> archivo <<'EOF'`).
- Después de cada cambio que compila o de cada prueba, agregá enseguida una o dos líneas a STATUS.md (sección de la ronda actual) con lo que cambió y el resultado. No esperes al final de la ronda para actualizar STATUS.md.

## Regla: decompilaciones siempre en /tmp
Toda salida de vineflower u otro decompilador va SIEMPRE a /tmp/..., nunca dentro del repo. Pasarle los argumentos bien (no dejar que un flag como --method=... termine usándose como carpeta de salida). Antes de terminar la ronda, verificar con `git status` que no haya carpetas nuevas raras en la raíz.

## Notas viejas de Code
Fixes de notas de Code ya aplicados; no rehacer.


## Regla apply_patch (ronda 57 terminó rc=1 por un hunk inválido)
- En apply_patch cada hunk empieza con una línea `@@` (opcionalmente `@@ contexto`), y TODA línea siguiente lleva prefijo ' ', '+' o '-'. Nunca pongas código como encabezado de hunk.
- Para cambios grandes en un archivo Java nuevo usá `*** Add File`; para archivos existentes, hunks chicos (<=80 líneas).
- Si apply_patch falla, leé el error y reintentá el parche corregido en el mismo turno; no termines la ronda.

## Ronda 52b de Code (parche listo, aplicar si no está)
- `git apply --check /datos/Projects/pz-web-code/patches/ronda52b-png-grayalpha.patch && git apply` ese archivo (solo BrowserStb.java: PNG gris, gris+alpha y paleta). Si --check falla, ya está aplicado o hay conflicto: anotarlo en STATUS.md y seguir.
- Verificación en el siguiente build: desaparecen las 2 líneas "failed to read image /media/ui/cursor_*.png".
- Los 7 FileNotFound restantes son esperables (packs 2x excluidos a propósito); no perseguirlos.
