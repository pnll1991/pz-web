# Tarea 4 para Codex

Leé STATUS.md, AGENTS.md, docs/ROADMAP-source.txt, docs/task3-compatibility.md y port/target/reports/game-main-lua/missing-apis.md. PZ_HOME=/home/agente47/pz-pinned/25485521/projectzomboid (42.21). El estado de la tarea 3 ya está commiteado (a1d4963).

RAM: el servidor de IA local sigue apagado y hay ~25 GB libres. El build Lua completo con la caché compacta usa ~6 GiB pico; podés usar MAVEN_OPTS=-Xmx20g. No corras el juego nativo ni pruebas de navegador pesadas en paralelo con el build. No prendas ni apagues aplicaciones del dueño (Strata).

Objetivo: build del main real SIN errores (0 APIs ausentes) y obtener port/target/web/game/boot.wasm; después avanzar F3 hacia el menú principal renderizado en Chrome.
1. Implementar las 82 APIs/dependencias ausentes por familias, con pruebas: Unsafe/MemoryUtil y Foreign Memory (arena de memoria nativa), MethodHandles/VarHandle, recursos del classpath, watchers, JMX/management, XML/DOM, AWT geométrico, digest/normalización, SQL/JDBC, concurrencia (BlockingQueue, ReadWriteLock, ScheduledExecutor, CompletableFuture), tipos opcionales de OpenTelemetry (excluir/sustituir de forma segura si son opcionales). Implementaciones reales para navegador; si algo no aplica, error explícito, nunca stubs que simulen éxito.
2. Recompilar el main real con reflexión Lua completa hasta obtener el Wasm y arrancarlo en Chrome; documentar hasta dónde llega el arranque (LuaManager.init, etc.).
3. Si sobra margen: GL20 + pipeline fijo -> WebGL2, GLFW/callbacks, loop del renderer, resolver el include util/math de screen.frag, hasta ver el menú. Captura en test-results y pruebas.
Mismas reglas de AGENTS.md (nada de archivos del juego ni código decompilado en git). Actualizá STATUS.md al final. .git es de solo lectura para vos; el dueño commitea.
