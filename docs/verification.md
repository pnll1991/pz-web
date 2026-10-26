# Evidencia de verificación — 2026-10-26, ronda 5

**94 pruebas aprobadas:** 45 JVM, 10 Python, 17 Node, 17 Chrome de plataforma y 5 Chrome con la copia real. Los reportes finales tienen cero fallos, omitidos y flaky. **El main real se ejecuta, termina Display.init y LoggerManager.init, y se detiene al cargar PZBullet64. No hay menú ni MENU_REACHED.**

## Builds y entorno

| Módulo | Clases / métodos | APIs / diagnósticos | Wasm | TeaVM | RSS pico Maven |
|---|---:|---:|---:|---:|---:|
| Main real, Lua completo | 10.279 / 73.003 | 0 / 0 | 144.676.333 bytes | 345.614 ms | 10.245.228 KiB |
| Plataforma | 937 / 4.341 | 0 / 0 | 1.735.621 bytes | 5.353 ms | 1.174.168 KiB |

Builds mediante `scripts/build.sh`, heap máximo 20 GiB y pila 16 MiB. No hubo navegador pesado ni juego nativo en paralelo con el build completo. Chrome usa perfil de prueba independiente y SwiftShader; no es una medición de la RTX 3090 ni de fps del juego.

La exposición conserva 1.043 registros, 1.040 clases distintas, 1.123 clases del jar con jerarquías y 35 externas; cero registros no resueltos. El build vuelve a validar los hashes de los jars externos. No se redujo la exposición Lua.

El SHA-256 del módulo ejecutado es `ca09a2d13ff22283736a0ac837b533df5c6a78bbdd841641ee1fcfeefab96ce2`. Las correcciones públicas de TeaVM se generan en target ignorado con hashes fijados; el campo compiler_patch identifica la caché de reflexión conservada, no enumera todos los parches. El alcance nuevo está en [round5-compatibility.md](round5-compatibility.md).

## Pruebas de la ronda

| Área | Evidencia |
|---|---|
| Inicialización de ventana | La JVM compila el transformador y el main real lo ejecuta en Chrome. El registro confirma Display.init retornado. La fixture Java/JS compara las métricas con Screen, llama dos veces para verificar idempotencia y comprueba los scancodes virtuales. |
| Errores de plataforma | Chrome rechaza canvas desconectado y contexto perdido, y no marca initialized. El estado de ventana creada sigue false; consultas a un identificador inválido fallan. |
| Etiquetas de teclado | Layout distinto de US, uso del scancode cuando key=-1, null si falta el layout y null para Enter aun si existe una etiqueta. La distribución real solo se consulta mediante KeyboardLayoutMap. |
| Consola de TeaVM | La fixture previa falla en JsConsolePrintStream.flush. La final ejecuta bytes con offset no nulo y longitud mayor que 32, texto ñ/emoji partido byte a byte con flush, flush repetido, escritura posterior y stderr; exige contenido exacto. Una subclase propia verifica print(double) y slice char[] con inicio no nulo. |
| Diagnóstico del juego | La excepción conserva frames fuera de System.err; Chrome amplía el límite de stack antes de cargar Wasm. La prueba resuelve offsets por metadata y exige MainScreenState.main, Bullet.init y Bullet.loadLibrary. |
| Etapas reales | Display.init y LoggerManager.init entran y retornan. Bullet.init entra y no retorna. No hay entrada de LuaManager.init, Display.create ni render del menú en el registro observado. |

La observación de etapas no cambia lógica ni atrapa errores del juego. El campo menuRendered permanece false y nunca se toma el dibujo de la demo como prueba del menú.

## Cobertura previa conservada

- JVM: memoria/arenas, handles, colecciones y concurrencia, archivos, FMA, digest, geometría, contratos JDK y regresiones del compilador.
- Python: inventario determinista, modified UTF-8, firmas, exposición y jerarquías, fixtures y rechazo de hashes cambiados.
- Node: servidor 127.0.0.1, Range/ETag, confinamiento, VFS, watchers, recursos ZIP/CRC y traducción de GLSL por AST.
- Chrome: IO y persistencia IndexedDB, SQL real y recarga, XML, memoria, reflexión, concurrencia/corrutinas, monitores, primitivas WebGL, uniforms, input y paquetes cacheados.
- Copia real: lectura Java de WorkshopTags, escritura Java persistente, comparación de los 4.194.304 bytes RGBA de Title.png y enlace de los pares default, blit y vboRenderer_PositionColorUV. No equivalen al renderer del juego.

Los límites anteriores de compatibilidad siguen documentados en [task4-compatibility.md](task4-compatibility.md) y [task3-compatibility.md](task3-compatibility.md).

## Arranque real y captura

La corrida final, registrada en `chrome-startup.json` el **2026-10-26T16:03:58.994Z**, solicitó `/game/boot.wasm` y terminó con:

```text
java.lang.UnsatisfiedLinkError: System.loadLibrary: browser implementation is missing for PZBullet64
```

La traza resuelta es `MainScreenState.main → Bullet.init → Bullet.loadLibrary → System.loadLibrary → JdkSupport.loadLibrary`. Antes aparece una excepción de consulta de hardware OSHI (`Unsupported platform: UNSUPPORTED`) que el juego captura y tras la cual continúa. No hubo pageerror sin capturar. El reporte conserva consola, etapas, estado, símbolos y hashes de módulo/captura.

La captura [game-main-round5.png](../test-results/game-main-round5.png) fue inspeccionada: canvas negro y mensaje de PZBullet64, sin menú. La prueba de arranque aprobada verifica esta detención y las etapas anteriores; no afirma éxito del juego. MENU_REACHED no se creó.

## Artefactos y reproducción

- `build-round5-pass3.log`: build final del main; `port/target/reports/game-main-lua/`: build, cero APIs, diagnósticos vacíos y chrome-startup.json.
- `build-round5-platform-final.log`, `port/target/reports/platform-smoke-minimal/` y XML en `port/target/surefire-reports/` (45 JVM).
- `verification-round5-python.log` (10), `verification-round5-node.log` (17), `verification-round5-browser.log` (17), `verification-round5-real-final.log` (5).
- `test-results/browser-results.json` y `test-results/real-game-results.json`, con salidas separadas en platform/ y real/.
- `verification-round5-console-before.log` conserva la regresión previa; `verification-round5-console-fixed.log` y la suite final conservan la verificación posterior. Los fallos intermedios no se cuentan como aprobados.
- `build/round5/lwjgl-usage.tsv` y `bullet-signatures.json`: nombres, firmas y sitios de acceso, sin cuerpos del juego.

```bash
source scripts/env.sh
export PZ_HOME="$HOME/pz-pinned/25485521/projectzomboid"
bash scripts/test.sh
bash scripts/build.sh --with-game
python3 scripts/report_game.py --reports port/target/reports/game-main-lua
npx playwright test --config playwright.game.config.mjs
```

La prueba real vuelve a guardar el reporte con screenshot_visually_checked=false; solo una inspección posterior de esa captura autoriza marcarlo true. Al iniciar otra compilación se invalida la evidencia de ejecución anterior.

## Auditoría y límites

277 archivos rastreados/candidatos auditados, sin binarios ni carpetas de recursos prohibidos. `git diff --check` pasó. HEAD permanece 054512f y .git es de solo lectura; no hubo commits, remotos, publicaciones, sudo ni cambios en aplicaciones del dueño.

Los servidores de prueba se gestionaron en 127.0.0.1:8091/8092. No se lanzó el juego nativo ni se usaron datos privados del dueño. Faltan el módulo y contrato del wrapper Bullet, creación de ventana/GLFW, GL20/pipeline/loop del renderer, Lua ejecutado, otras nativas y FMOD. El siguiente paso concreto está en STATUS.md.
