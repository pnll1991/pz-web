# Tarea continua (ronda automática, modelo local Gyro-M vía Codex + OpenCodex)

Leé STATUS.md, AGENTS.md, docs/ROADMAP-source.txt, la última tarea en docs/ (CODEX_TASK_4.md) y los reportes en port/target/reports/. PZ_HOME=/home/agente47/pz-pinned/25485521/projectzomboid (42.21). El trabajo previo ya está commiteado por el dueño.

Continuá exactamente desde donde quedó la ronda anterior según STATUS.md, siempre hacia el objetivo final: el menú principal real de Project Zomboid corriendo de forma nativa en Chrome (TeaVM/Wasm + WebGL2, NO streaming). Orden: 0 APIs ausentes y boot.wasm del main real -> arranque en Chrome (LuaManager.init, etc.) -> render GL/WebGL2, GLFW, loop, shaders -> menú visible.

RAM: ahora el servidor local Strata (el modelo que te está ejecutando) usa ~19 GB; quedan ~10 GB. Usá MAVEN_OPTS=-Xmx8g (el build Lua completo con caché compacta pico ~6 GiB). NUNCA detengas, reinicies ni toques Strata ni el puerto 8083: sos vos. No corras el juego nativo ni pruebas pesadas de navegador en paralelo con el build. No toques aplicaciones del dueño (Strata).
Mismas reglas de AGENTS.md: nada de archivos del juego ni código decompilado en git, nunca stubs que simulen éxito.

Cuando, y solo cuando, el menú principal real se vea renderizado en Chrome y esté verificado con una captura en test-results/ y una prueba Playwright que pase, creá el archivo MENU_REACHED en la raíz del repo con la ruta de la captura y la prueba. No lo crees en ningún otro caso.
Al terminar la ronda actualizá STATUS.md con el avance, los bloqueos y el siguiente paso concreto. No hagas commits ni toques .git: el vigilante commitea al final de cada ronda. Trabajá en pasos chicos y verificables; si el contexto se llena, dejá STATUS.md actualizado para que la siguiente ronda continúe.

## OBLIGATORIO al terminar cada ronda: actualizar STATUS.md

Antes de dar la ronda por terminada, reescribí `STATUS.md` para que refleje el estado REAL actual (no el de rondas anteriores):
- Número de ronda y fecha/hora.
- Qué se cambió en esta ronda (archivos y funcionalidad).
- Hasta dónde llega hoy el arranque en Chrome (última etapa alcanzada y el error exacto donde se detiene), verificado con la prueba/captura de esta ronda.
- El bloqueo actual y los siguientes pasos concretos, en orden.
- Resultado de las pruebas de esta ronda.
La siguiente ronda arranca leyendo este archivo: si queda viejo, se pierde el trabajo. No termines la ronda sin actualizarlo.
