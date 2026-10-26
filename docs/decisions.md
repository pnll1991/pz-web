# Decisiones

- 2026-10-25: conservar el roadmap como referencia; sus cifras y firmas no sustituyen un inventario de la instalación real.
- 2026-10-25: todas las herramientas descargadas y caches viven en `.toolchain/`, sin sudo ni cambios en el shell global.
- 2026-10-25: la ruta de trabajo resuelve a `/datos/Projects/pz-web`; los scripts calculan su raíz y no dependen del enlace de compatibilidad en `~/Projects`.
- 2026-10-25: distinguir explícitamente pruebas sintéticas de hitos que requieren PZ; no inicializar ni compilar un juego inexistente.
- 2026-10-25: TeaVM 0.16.0 y `WEBASSEMBLY_GC`, conforme a https://teavm.org/docs/tooling/maven.html y https://teavm.org/docs/release-notes/0.16.0.html; comprobar las extensiones en los artefactos de esa versión.
- 2026-10-25: usar la API `TeaVMTool` invocada por Maven para controlar el classpath externo y obtener contadores reales; Maven también copia el loader oficial y resuelve dependencias a `.toolchain/m2`.
- 2026-10-25: usar `SubstitutionPolicy` para clases completas conocidas y `ClassHolderTransformer` para métodos inventariados, preservando los demás miembros; fuentes: https://teavm.org/docs/runtime/compiler-extensions.html y source jars 0.16.0.
- 2026-10-25: las redirecciones de GL generadas llaman wrappers Java normales; esos wrappers contienen el puente JSO, evitando depender del orden de transformaciones JSO para instrucciones inyectadas.
- 2026-10-25: el loader es el oficial de TeaVM (https://teavm.org/docs/wasm-gc-backend/loader.html); se verifica `WebAssembly.Instance`, no solo HTTP 200 o existencia del archivo.
- 2026-10-25: la reflexión inicial usa una lista explícita independiente; no habilitar la reflexión global sin el manifiesto Lua real.
- 2026-10-25: generar guardas por firma y símbolo JNI largo en vez de clases de reemplazo vacías que perderían métodos no nativos del juego.
- 2026-10-25: montar únicamente el directorio web compilado, `/media` y `/packs`; bloquear escritura HTTP, orígenes ajenos, Host ajenos y symlinks que salgan del montaje.
- 2026-10-25: ETag de metadatos débil; un `If-Range` débil no autoriza respuesta parcial, conforme a la semántica HTTP.
- 2026-10-25: guardar `/Zomboid` mediante snapshots en una transacción IndexedDB con durabilidad estricta y flush explícito; sin guardado implícito en unload.
- 2026-10-25: paquetes locales con SHA-256 y selección explícita de recursos; no empaquetar todo el juego sin saber qué se lee al arrancar.
- 2026-10-25: Chrome separado con SwiftShader para pruebas reproducibles de WebGL2; no extrapolar fps ni memoria de la demo al juego o a la RTX 3090.
- 2026-10-25: Ghidra, Vineflower y Emscripten se verifican con código propio; no leer decompilaciones o chats privados ni descargar activos del juego.
- 2026-10-25: `.git` es de solo lectura en este sandbox; conservar cambios en archivos y documentar commits pendientes, sin eludir esa protección.
