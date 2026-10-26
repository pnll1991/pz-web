# Estado del port personal

Actualizado: **2026-10-25**, Linux x86_64, repositorio en `/datos/Projects/pz-web` (`~/Projects/pz-web` es el enlace de compatibilidad).

**Fase actual: F0 pendiente de la copia del juego; base comprobada de F1–F3.** La demo de plataforma compila a WebAssembly GC y se ejecuta en Chrome. **Project Zomboid todavía no está integrado ni se ha compilado su jar.** No hay hitos completos del juego, menú, mundo, física o audio.

## Qué funciona

| Área | Implementado y comprobado |
|---|---|
| Entorno F0 | JDK 25.0.5, Maven 3.9.16, Node 24.22.1 y TeaVM 0.16.0. Extras: Emscripten 6.0.13, CMake 4.4.4, Ninja 1.13.2, Ghidra 12.2, Vineflower 1.12.0, apitrace 14.0, RenderDoc 1.46. Todo lo descargado está en `.toolchain/`, sin sudo. Chrome 156 y herramientas base ya estaban instalados. |
| Inventario F0 | Script offline e idempotente: jars/manifests/major versions, métodos native y descriptores, mangling JNI, cruce con exports ELF, debug, LWJGL, Kahlua y metadatos de versión. Probado sobre clases y `.so` propios, incluidos overloads y nombres con guiones bajos/Unicode. |
| Compilador F1 | Maven → TeaVM → Wasm GC; classpath externo a partir del launcher/inventario, verificación de hashes, guardas nativas que arrojan `UnsupportedOperationException`, configuración explícita de reflexión y diagnósticos. El modo `--with-game` aún ejecuta únicamente nuestra prueba de plataforma. |
| Sustituciones F1/F3 | `SubstitutionPolicy` y transformación de métodos probadas en Chrome con API sintética. Generador de cobertura GL para firmas reales; cuatro mapeos escalares exactos, sin fingir que todo LWJGL está implementado. |
| Servidor F2 | Solo `127.0.0.1:8090`, COOP/COEP, MIME Wasm, GET/HEAD, rangos, ETag, lectura de `/media` y `/packs`, control de rutas y symlinks. |
| Archivos F2 | VFS con lecturas precargadas sincrónicas, puente Java/JS probado, escritura en `/Zomboid`, rename y snapshots atómicos en IndexedDB. Persiste tras recarga; aborto conserva snapshot previo. |
| Paquetes F2 | Selección explícita de recursos, índice y blobs SHA-256, validación de integridad, precarga y Cache Storage. Cache corrupta produce error explícito y se elimina. |
| Loop/input F2/F3 | `requestAnimationFrame` llama a Java exportado, pausa/reanuda, contador y tiempos acotados; teclado GLFW, mouse, scroll, liberación al perder foco y caracteres Unicode. Pointer lock y gamepads estándar tienen adaptadores iniciales, todavía sin validación con dispositivos reales. |
| Gráficos F3 | Shader propio GLSL ES 3.00, dibujo y lectura de píxeles reales; puente de buffers directos Java/JS, `glGetError` real, fallo de shader inválido, matrices y triangulación de quads. No hay pipeline fijo completo. |
| Herramientas F5 | Emscripten compiló C propio a Wasm y devolvió `add(20,22)=42`; Ghidra analizó un ELF propio y Vineflower decompiló una clase propia. No se implementó ninguna nativa de PZ. |

## Verificación ejecutada

- `bash scripts/test.sh`: **25 pruebas aprobadas**: 3 JVM, 7 Python, 8 Node y 7 Chrome/Playwright.
- Build: **171 clases alcanzadas, 647 métodos alcanzados, Wasm de 126.096 bytes**, sin diagnósticos TeaVM. Son cifras de la demo, no del juego.
- Chrome confirmó una instancia real de WebAssembly, ejecución Java, error JNI esperado, checksum 170 del buffer directo y píxel `[64,217,166,255]` del shader.
- Se comprobó persistencia tras recargar, rollback de una transacción abortada, cache de paquetes, recursos solo lectura, rangos y ETags.
- `python3 scripts/doctor.py`: todas las herramientas requeridas detectadas. `bash scripts/check-toolchains.sh`: pruebas funcionales de Emscripten, Ghidra y Vineflower aprobadas.
- Captura inspeccionada: `test-results/platform.png`. Detalle reproducible: [docs/verification.md](docs/verification.md).
- Los tests gráficos usan **SwiftShader**, no miden la RTX 3090 ni predicen fps del juego. No se usó una sesión de navegador personal.

## Bloqueos y faltantes

1. **No hay instalación local encontrada del juego.** `tools/inventory/run.sh --find` volvió a devolver `[]` al finalizar. Se consultaron las rutas habituales, las bibliotecas declaradas por Steam, Flatpak y `~/pz-pinned`. Por eso no existe un inventario real ni evidencia de versión/LWJGL/APIs del juego. No se rellenó con datos inventados.
2. **F1 real sigue pendiente:** configurar el arranque del juego, alcanzar su código, resolver las APIs JDK faltantes y generar el manifiesto de reflexión Lua. El reporte actual `jdk-api-report.txt` solo cubre código alcanzado en nuestra prueba.
3. **F2/F3 reales siguen pendientes:** sustituir `java.io`/`java.nio`, analizar hilos y busy-waits, integrar callbacks GLFW/MemoryUtil, pipeline fijo, texturas/formatos y traductor GLSL con parser. La lista real de recursos y las capturas del menú requieren el juego.
4. **F4–F8:** faltan exposición Kahlua/Lua, grabaciones JNI, implementaciones nativas, Bullet, sqlite-wasm, guardado de partidas reales, FMOD y benchmarks del juego. Sin firmas y entradas/salidas locales no hay base para implementarlos fielmente.
5. **Git es de solo lectura en esta sesión.** `git add` falló al crear `.git/index.lock`; no se pudo hacer ningún commit. Los cambios sí están en el sistema de archivos. Los grupos y comandos de commits locales están en [docs/local-commits.md](docs/local-commits.md). No se configuró ni publicó un remoto.
6. RenderDoc CLI está instalado; su capa de Vulkan no está registrada globalmente. No se modificó esa configuración: la captura OpenGL de PZ aún requiere instalar y abrir el juego. Apitrace está disponible como alternativa.

## Qué necesita hacer Paolozky

**Ahora:** instalar Project Zomboid **Linux nativo (Steam 108600)**, abrirlo una vez y comprobar que funciona. Después fijar una copia fuera del repositorio en `~/pz-pinned/<build>` y guardar el build id. La copia debe contener `ProjectZomboid64.json`.

Para habilitar la siguiente etapa:

```bash
export PZ_HOME="$HOME/pz-pinned/REEMPLAZAR-POR-BUILD"
source scripts/env.sh
tools/inventory/run.sh "$PZ_HOME"
bash scripts/build.sh --with-game
```

Ese build prepara y prueba la plataforma con el classpath externo; **no habilita todavía el juego**. El siguiente trabajo es revisar el inventario y conectar su inicialización mínima.

**Cuando llegue F7:** cuenta y descarga manual del SDK HTML5 de FMOD que corresponda a los bancos instalados. No se necesita esa cuenta para continuar F1–F3, y no se solicitó ninguna credencial.

**Para registrar el trabajo:** ejecutar los commits locales documentados desde una terminal con escritura en `.git`, o iniciar una sesión que permita esa escritura. No hace falta sudo para los pasos preparados.

## Abrir la demo y retomar

Al finalizar esta sesión quedó el servidor local activo en **http://127.0.0.1:8090**, sin juego montado; se verificaron `/health`, el Wasm servido y ambos headers de aislamiento. Si el proceso ya terminó al retomar, arrancarlo con:

```bash
bash scripts/dev.sh
# http://127.0.0.1:8090
```

Instrucciones completas en [README.md](README.md). No se publicaron archivos ni se descargó el juego por otra vía. `AGENT_LEARNINGS.md` contiene los errores corregidos durante esta implementación.
