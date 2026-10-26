# Commits locales sugeridos — tarea 3

HEAD al comenzar: **649b950**, tarea 2. `.git` está montado en solo lectura en esta sesión; no se intentó eludir esa restricción ni se crearon remotos. Las modificaciones quedan en el árbol de trabajo.

Desde una terminal con escritura en `.git`, revisar los cambios y separar estos grupos:

1. `port/src/`: sustituciones JDK, concurrencia, ciclo de vida de hilos, FileChannel, persistencia y pruebas JVM; ajustes de sus probes en `tests/browser/platform.spec.mjs`.
2. `port/runtime/`, `port/web/`, dependencia fijada en `package*.json`, `scripts/copy_web_vendor.py`, `scripts/build.sh`, tests GLSL Node/Chrome y separación de outputDir de Playwright.
3. Parche de memoria del compilador: `scripts/prepare_teavm.py`, `port/pom.xml`, `CompactTypeSet` y su prueba; fixture de reflexión y BuildCompiler.
4. Reporte de APIs, límites del generador GL, README, STATUS, decisiones, verificación y aprendizajes.

No usar `git add -f`: inventarios, builds, binarios, recursos, capturas, paquetes y resultados de pruebas continúan ignorados. `docs/CODEX_TASK_3.md` y `run-task3.sh` ya estaban sin seguimiento al comenzar y no fueron modificados; decidir su inclusión al revisar el commit. El script del dueño puede realizar acciones adicionales y no fue ejecutado por este agente.
