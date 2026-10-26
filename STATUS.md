# Estado del port personal

Actualizado: **2026-10-26**, Linux x86_64. Repositorio: `/datos/Projects/pz-web` (`~/Projects/pz-web` es el enlace de compatibilidad).

**F0 real inventariado; F1 conectado al main real, todavía sin compilar; integración de archivos F2 y prueba de textura real F3 verificadas. El menú de Project Zomboid no se ejecutó ni se renderizó.** La demo de plataforma sigue siendo una prueba independiente.

## Copia e inventario reales

- `PZ_HOME` canónico: `/home/agente47/pz-pinned/25485521/projectzomboid`. Los scripts aceptan también el padre `25485521`. El log inicial fallaba porque buscaba el launcher un nivel más arriba.
- Steam appid **108600** y build id **25485521** confirmados en `/datos/SteamLibrary/steamapps/appmanifest_108600.acf`. El SHA-256 del jar instalado coincide con el de la copia fijada. Esta verificación adicional cubre el jar principal; el inventario determinista de la copia conserva como procedencia del build id el nombre de su directorio.
- Versión **42.21**, obtenida de los argumentos literales de `GameVersion` en `Core.<clinit>` sin ejecutar clases del juego; revisión `4a0e9546ec` en `GitVersion.REVISION`.
- Launcher real: `zombie.gameStates.MainScreenState`, classpath `.` y `projectzomboid.jar`. La clave del JSON es `classpath`, en minúsculas.
- Fat jar: **65.682.033 bytes**, **23.829 entradas de clase**, de ellas 6.462 con major 69 (Java 25). SHA-256: `e1a69eb743ede60b213a0fe7f8b83d4fcab773036d256cc4543a336f3b058a33`. El runtime incluido es Zulu 25.0.1; el compilador del proyecto usa el JDK 25 local de `.toolchain`.
- **LWJGL 3.4.1** integrado en el fat jar: core, GLFW, OpenGL, STB, system y util. La versión se obtiene de `org.lwjgl.Version`, ya que no hay jars LWJGL separados con manifests propios.
- **88 clases Kahlua** integradas. Su clase `Version` contiene placeholders; no se inventó una versión de release.
- **8.284 declaraciones native / 7.799 firmas distintas** en los tres jars inventariados: 8.281 declaraciones en el fat jar, 2 en pzexe y 1 en jrt-fs. Se distinguen variantes multi-release, duplicados, JRE y plataformas. Quedan 88 declaraciones sin export identificado; `nm` no resuelve registro JNI dinámico.
- Se inspeccionan también ELF empaquetados dentro del jar mediante archivos anónimos en memoria, sin extraer binarios al repositorio. Dos inventarios consecutivos produjeron JSON idéntico. Salidas locales: `inventory/report.md`, `inventory/inventory.json`, `inventory-run.log`.

Exports JNI Linux relevantes: Lighting 50; Bullet 98 (variante NoOpenGL 93); Clipper 19; PathFind 13; PopMan 59; ZNetJNI 139; Bink 11; FMOD integration 437; jassimp 10; ImGui 2.129; LWJGL core 2.150; OpenGL 2.236; STB 224; tinyfd 11; SQLite 61. Son exports, no implementaciones del port ni conteos sumables entre variantes.

## Qué se implementó

| Área | Resultado comprobado |
|---|---|
| Arranque F1 | `--with-game` conecta `GameBoot` al main real del launcher mediante transformación, sin cargar el juego en la JVM del compilador. Ya no ejecuta la demo. Hashes y launcher se validan antes del build. |
| Reflexión Lua | Analizador JDK 25 de bytecode sin decompilar: 1.043 registros literales, **1.040 clases distintas**, 1.123 clases del jar incluyendo jerarquías y 35 tipos externos. Sigue 9 helpers y no quedan registros directos sin resolver en ese alcance. Produce `port/target/config/exposure.json` y `reflection-lua.txt`; no demuestra inicialización Lua ni cobertura de toda exposición dinámica recursiva. |
| APIs JDK | Implementadas `File.toPath`, `Reader.transferTo`, `Path.resolve(String, String...)` y `Thread.onSpinWait` con yield. Corregida la lectura de un byte de `FileInputStream` de TeaVM para distinguir 128/255 de EOF. Validación JVM y Wasm/Chrome. |
| Archivos F2 | SPI `BrowserFileSystem`: File, streams, RandomAccessFile y Files/Paths comparten el VFS JS. Directorios, rename, offsets, ceros al extender, permisos, cuota y metadata persistida. El flush explícito usa una transacción IndexedDB para contenido y metadata. |
| Copia real F2 | `media/WorkshopTags.txt` se empaquetó localmente, precargó y leyó mediante Java NIO; hash idéntico al original. Una escritura Java en `/Zomboid` persistió tras recargar Chrome. |
| Texturas F3 | `media/ui/Title.png` de 1024×1024 se decodificó, subió a WebGL2 y leyó con readPixels. **Los 4.194.304 bytes RGBA coinciden** con la decodificación local de Pillow; glGetError = 0. Esto no es el menú ni valida sus shaders. |
| Diagnóstico | Wasm y reportes separados por modo. `/game.html` informa un build bloqueado y no carga la demo como si fuera el juego. Los fallos no conservan un Wasm anterior de ese modo. |

## Evidencia y bloqueos actuales

- **37 pruebas aprobadas:** 6 JVM, 10 Python, 10 Node, 8 Chrome sintéticas y 3 Chrome con la copia real. Reproducción y límites en [docs/verification.md](docs/verification.md).
- Plataforma: **251 clases / 1.084 métodos alcanzados, 232.283 bytes Wasm**, cero diagnósticos TeaVM. Estas cifras pertenecen a la demo.
- Main real con reflexión **mínima de diagnóstico**: **3.173 clases / 13.867 métodos**, **232 diagnósticos y 52 APIs ausentes distintas**, cero bytes Wasm. Se resolvieron cuatro APIs de la primera pasada y apareció otra al ampliar el código alcanzado. El reporte incluye además advertencias ajenas a esas 52 APIs.
- Main real con el manifiesto **Lua**: el análisis agotó el heap tanto con **2 GiB como con 3 GiB**. No se obtuvo un módulo ni un conteo final. El sistema tenía aproximadamente 3–5 GB de RAM disponible; no se intentó llenar el heap predeterminado de 20 GiB con esa memoria libre ni se cerraron aplicaciones del dueño.
- Pendientes F1: memoria para el análisis completo y compatibilidad de Unsafe/MemoryUtil, concurrencia, FileChannel/watchers, management, XML/JAXB, AWT y carga de librerías. Los errores se conservan; no se agregaron stubs que simulen éxito.
- Pendientes F2/F3: log real de LoggerManager, lista completa de recursos, recursos del classpath, hilos/busy-waits, loop real, GLFW/callbacks, arena nativa, pipeline fijo y shaders GLSL del juego. El flush durable de modos RandomAccessFile `rws/rwd` no está integrado; persistir requiere `await vfs.flush()`.
- No se implementaron nativas de PZ, física, mundo, SQLite ni FMOD. El menú, LuaManager.init y una partida real siguen sin ejecutar. Las capturas y grabaciones del juego original serán necesarias para validar equivalencia.

Reportes conservados:
- `port/target/reports/platform-smoke-minimal/`: build y diagnósticos de plataforma.
- `port/target/reports/game-main-minimal/`: build, diagnósticos y `missing-apis.json`/`.md` con call sites.
- `port/target/reports/game-main-lua/`: falla de memoria de la reflexión Lua.
- `verification-task2.log`, `build-game-minimal.log`, `build-game-lua-3g.log`, `real-browser-tests.log` y `test-results/`: evidencia local ignorada.

## Qué necesita hacer Paolozky

**No hace falta reinstalar el juego.** Para intentar el build completo, liberar memoria de otras aplicaciones; el roadmap prevé hasta 20 GiB de heap de compilación más el sistema. No ejecutar a la vez una partida o pruebas pesadas de navegador. Dar más RAM no resuelve por sí solo las APIs faltantes: queda trabajo de implementación.

```bash
source scripts/env.sh
export PZ_HOME="$HOME/pz-pinned/25485521"
tools/inventory/run.sh "$PZ_HOME"
bash scripts/build.sh --with-game
```

Para un diagnóstico parcial con poca RAM: `MAVEN_OPTS=-Xmx2g bash scripts/build.sh --with-game --reflection minimal`, seguido de `python3 scripts/report_game.py`. Ese comando falla mientras existan APIs ausentes, deliberadamente.

Después hará falta una captura local del menú original y logs de arranque/Lua para comparar el port. FMOD HTML5 queda para F7; no se requieren cuenta ni credenciales para las pruebas actuales.

## Git y ejecución local

La base ya tiene el commit **ba29078**. `.git` sigue en solo lectura en esta sesión: los cambios están en disco y los nuevos commits sugeridos figuran en [docs/local-commits.md](docs/local-commits.md). No se crearon remotos ni se publicaron archivos. La auditoría de archivos rastreados y candidatos no encontró binarios ni recursos del juego.

Al finalizar quedó activo **http://127.0.0.1:8093**, con la copia real montada. Se verificaron `/health`, media real, MIME Wasm, COOP/COEP y ambas pantallas en Chrome. Si ese proceso ya terminó al retomar, para abrir la plataforma ejecutar `bash scripts/build.sh` y `PZ_HOME="$HOME/pz-pinned/25485521" bash scripts/dev.sh`, siempre en **127.0.0.1:8090**. `/` es la demo; `/game.html` muestra el estado del build real. Si 8090 ya está ocupado, usar otro puerto local con `PORT=8093`; no se detuvo el servidor existente. Los tests usan puertos separados 8091 y 8092.
