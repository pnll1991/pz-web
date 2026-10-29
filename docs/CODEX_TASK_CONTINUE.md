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

## OBLIGATORIO: menos análisis, más código
Las rondas 15 y 18 gastaron más de una hora leyendo bytecode (javap) de GLFW/LWJGL/OpenGL sin editar un solo archivo. Eso no sirve: una ronda sin cambios no deja nada.
- Límite de análisis: como máximo 20 comandos de lectura (javap, grep, sed, cat, ls) antes de la primera edición real de código. No repitas análisis que ya figura en STATUS.md o en commits anteriores.
- Objetivo inmediato (lo que dejó planteado la ronda 15): implementar el ciclo de vida de ventana GLFW sobre el canvas (glfwCreateWindow, glfwSetXxxCallback, glfwPollEvents, glfwSwapBuffers) y el registro de callbacks de teclado/mouse en port/src/main/java/port/shim/BrowserWindow.java y port/runtime/window.mjs, corrigiendo los descriptores de método invertidos en WindowCompatibility.
- Trabajá en pasos chicos: editá, compilá, probá en Chrome, y repetí. Cada 30-40 minutos tiene que haber cambios en archivos del repo.
- Si un detalle no está claro, elegí la implementación más simple que avance el arranque y anotá la duda en STATUS.md, en vez de seguir investigando.
- Antes de terminar, actualizá STATUS.md como indica la sección anterior.

## OBLIGATORIO: cierre antes del límite de 4 horas
Cada ronda se corta sola a las 4 horas exactas y, hasta ahora, ninguna llegó a actualizar STATUS.md.
- La hora de inicio de tu ronda es la de la última línea "lanzando ronda N (local)" en supervisor.log. Revisala al empezar (`grep 'lanzando ronda' supervisor.log | tail -1`) y mirá `date` cada tanto (por lo menos cada 20-30 comandos).
- Cuando lleves 3 horas y 30 minutos (o antes, si terminaste), dejá de programar: corré los tests rápidos que puedas, actualizá STATUS.md según la sección "actualizar STATUS.md" de arriba, y terminá la ronda.
- Un STATUS.md actualizado vale más que 20 minutos extra de código sin documentar.

## OBLIGATORIO: ediciones chicas y STATUS.md incremental (desde la ronda 26)

- Nunca escribas ni reescribas un archivo entero en una sola llamada de herramienta. Las llamadas muy largas se rompen (salen como texto `<tool_call>` y la ronda se corta). Editá con parches chicos: como máximo unas 80 líneas por llamada; si un archivo nuevo es más largo, crealo en varias partes (por ejemplo, un primer bloque y después agregados con `cat >> archivo <<'EOF'`).
- Después de cada cambio que compila o de cada prueba, agregá enseguida una o dos líneas a STATUS.md (sección de la ronda actual) con lo que cambió y el resultado. No esperes al final de la ronda para actualizar STATUS.md.

## OBLIGATORIO: notas del agente Code (desde la ronda 34)
- Antes de tocar el handoff de hilos, leé `docs/notes-from-code.md`. Ahí está la causa raíz verificada del crash `Fiber::popInt` (Thread.currentThread/setCurrentThread vueltos corrutinas por el registro synchronized + WeakHashMap del ThreadGroup compat en JdkCompatibility.threads()), el fix recomendado y un repro mínimo. El script de verificación está en `tools/code-agent/wasmfn.py`.
- Aplicá ese fix primero, sin stubs, y agregá el chequeo de build que propone. Anotá el resultado en STATUS.md.

## OBLIGATORIO: segundo diagnóstico de Code (GATE FAIL del build con juego)
- El fix de ThreadGroup de la ronda 34 NO alcanza: con el juego, Thread::<clinit> -> <init> -> ThreadGroup::registerDefault sigue siendo async (excepciones -> fillInStackTrace -> ... -> KahluaTableImpl::toString -> UIManager$Sync::endFrame -> Thread.sleep). Leé la sección "Ronda 35…" de `docs/notes-from-code.md`.
- Aplicá ese fix tal cual: en JdkCompatibility.threads() dejá de inyectar registerDefault(this); inyectá el campo $pzCreator en Thread (solo asignación al final del constructor) y resolvé el grupo tarde con los accesores nativos que muestran las notas. Sin stubs.
- Después hacé UN solo build --with-game (nunca dos en paralelo). Esperado: gate con fiberops False y `python3 tools/code-agent/probe8.py port/target/web/game/boot.wasm 'java.lang.Thread::<clinit>'` imprimiendo `no seed`. Anotá el resultado en STATUS.md.

## OBLIGATORIO: tercer diagnóstico de Code (ronda 40, wait/initFonts)
- Leé la sección "Ronda 40: monitor no se suelta en wait()" de `docs/notes-from-code.md`.
- El wait/notify ya funciona con releaseForWait (round40c: 4 waits, 4 listeners, 4 re-adquisiciones). No lo rehagas.
- Bloqueo nuevo: excepción no capturada en el MainThread dentro de GameWindow.initFonts. Primero capturá clase, mensaje y stack del Throwable en uncaughtExceptionMainThread/logException y mandalos a BrowserMonitor, SIN usar StringBuilder dentro de Fiber. Con eso identificá y arreglá la causa real (sin stubs).

## OBLIGATORIO: cuarto diagnóstico de Code (AttackState, ronda 47)
- Leé la sección "Ronda 47: AttackState" de `docs/notes-from-code.md` y aplicá ese fix tal cual: transformer StateParamCaller que fija la clase llamadora al compilar (50 clases de zombie.ai.states; el build falla si el bytecode no tiene la forma esperada). Causa: en TeaVM Thread.getStackTrace() es vacío ⇒ StackWalker da null ⇒ isAssignableFrom(null) trap.
- Verificalo como dice la nota, con UN solo build --with-game, y anotá el resultado en STATUS.md. Sin stubs.
- Regla: ante cualquier 'dereferencing a null pointer' en Wasm GC, revisá primero si el null viene de algo de la classlib que devuelve vacío.

## OBLIGATORIO: quinto diagnóstico de Code (StateRun render, ronda 49)
- Leé la sección "Ronda 49: StateRun render" de `docs/notes-from-code.md` y aplicala en este orden:
  1. El null pointer es porque defaultShader es null: faltan media/shaders/default.vert y default.frag (con sus #include) en el pack de arranque (build/real-browser/startup.txt no tiene media/shaders). Agregalos y reempaquetá. NO pongas una guarda contra null.
  2. En gl.mjs (~líneas 1126-1156): shaderParameter usa 0x8B81 como SHADER_TYPE, pero 0x8B81 es COMPILE_STATUS (SHADER_TYPE es 0x8B4F). A programParameter agregale 0x8B82 LINK_STATUS y 0x8B83 VALIDATE_STATUS.
  3. Agregá al pack mainfont_0.png, mainfont2_0.png y zomboidDialogue.bmfc_0.png (AngelCodeFont no los encuentra).
- Después, UN solo build --with-game, probá en Chrome y anotá en STATUS.md. Pendientes para después: los 8 FileNotFoundException, los UnsatisfiedLinkError de oshi/RakNet64 y un invokeOnRenderContext que nunca vuelve.

## Sexto diagnóstico de Code (ronda 49b, camino al menú) — aplicar junto con el quinto
Detalle en docs/notes-from-code.md, sección 'Ronda 49b: próximos frenos'.
1. startup.txt: agregar TODO media/shaders/** (196 archivos con includes) y los texturepacks UI, UI2, IconsMoveables, RadioIcons, ApComUI, Mechanics, WeatherFx. UI2 pesa 54,6 MB: subir --max-bytes de pack.py (hoy 32 MB) o ponerla en un blob propio.
2. gl.mjs: shaderParameter debe manejar 0x8B81, 0x8B84, 0x8B88, 0x8B4F, 0x8B80. programParameter: 0x8B80, 0x8B82–0x8B87, 0x8B89. INFO_LOG_LENGTH = largo del log + 1, o 0 si está vacío.
3. oshi (printSpecs), RakNet64 (SteamUtils → netEnabled=false) y páginas de fuente: el juego los tolera, NO simular éxito ni tocarlos.
4. invokeOnRenderContext que no vuelve es consecuencia del trap de StateRun; no es un bug aparte.
5. Queda 1 FileNotFoundException sin identificar (probablemente LuaManager.init): hookear readFile para loguear la ruta.
Hacer UN solo build después de 1+2 y probar en Chrome.
