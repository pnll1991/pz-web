# Port personal de Project Zomboid al navegador

## Reglas
- Leer `AGENT_LEARNINGS.md` al comenzar cada chat; crearlo si falta y actualizarlo después de cada error con causa, patrón a evitar y mejor enfoque.
- Leer `docs/CODEX_TASK.md`, `docs/ROADMAP-source.txt` y `STATUS.md` antes de trabajar.
- Nunca versionar archivos del juego, `.jar`, `.class`, `.so`, `media/`, `.bank`, mapas, código decompilado/desensamblado ni código derivado del juego.
- Leer la copia fija desde `PZ_HOME`; no copiarla al repositorio. Inventarios: solo nombres, tamaños, firmas y metadatos.
- Uso personal y local. No publicar, no crear remotos. Servidor exclusivamente en `127.0.0.1`.
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
- `docs/decisions.md`: una decisión por línea, con motivo y fuente cuando corresponda.
- `STATUS.md`: fase actual, evidencia, faltantes, bloqueos y pasos manuales.

## Verificación
- Builds mediante `scripts/build.sh`, por defecto `MAVEN_OPTS=-Xmx20g`.
- Cambios pequeños con commits locales descriptivos cuando los permisos lo permitan.
- Probar Java propio en JVM y el resultado Wasm GC en Chrome (Playwright).
- Una demo sintética no demuestra que el juego compile o funcione; no marcar hitos del juego cumplidos sin ejecutarlos.
- No inventar firmas, versiones ni comportamientos de PZ. El inventario real y las capturas del dueño son la fuente de verdad.
- Comparar futuras implementaciones con logs, grabaciones JNI y capturas locales del juego; no subir esos datos.
