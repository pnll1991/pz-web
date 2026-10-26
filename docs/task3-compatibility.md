# Compatibilidad agregada en la tarea 3

El objetivo del menú sigue pendiente. Este documento distingue implementaciones comprobadas, contratos de tipos y operaciones que fallan explícitamente. El reporte de TeaVM cuenta diagnósticos alcanzados; que desaparezca un diagnóstico no demuestra que toda una clase del JDK sea compatible.

## Implementaciones y verificación

| Área | Implementación | Evidencia y límites |
|---|---|---|
| Colas y conjuntos | ConcurrentLinkedQueue/Deque con snapshots e identidad de nodos; CopyOnWriteArraySet; ConcurrentHashMap.newKeySet | Orden, rechazo de null en colas, unicidad, snapshot y eliminación del nodo correcto en JVM; interfaces estándar sustituidas en Chrome. No es una implementación de todas las colecciones concurrentes del JDK. |
| Tareas | FutureTask; ExecutorService con pools fijos y de un worker; submit, invokeAll/invokeAny, shutdown | Resultado, excepción, timeout, cancelación, orden y terminación. Los workers son Threads cooperativos de TeaVM, sin paralelismo de CPU. ScheduledExecutorService, ForkJoinPool y CompletableFuture siguen pendientes. |
| Locks | ReentrantLock y Condition | Propiedad/reentrancia, espera y señalización, rechazo de unlock/signal sin propiedad, equidad e interrupción. ReentrantReadWriteLock sigue pendiente. |
| Hilos | ThreadGroup con membresía débil y herencia; constructor con grupo; estado creado/iniciado/terminado; join con monitor y plazo | Chrome verifica join antes de start, ejecución en el grupo, finalización y rechazo del segundo start. No audita ni reescribe los busy-waits del juego. |
| Archivos | FileChannel sobre el mismo VirtualFileAccessor que FileInputStream/FileOutputStream/RandomAccessFile | IO posicional, lectura/escritura, transferencias parciales, posición, EOF, truncate y cierre; pruebas JVM y Chrome. Offsets limitados a int32 por el VFS actual. Mapeo de archivos y watchers pendientes. |
| Persistencia | FileChannel.force espera el flush de IndexedDB usando @Async | Los bytes sobreviven a la recarga. El rechazo inyectado de la transacción llega a Java como error. Los modos rws/rwd todavía no fuerzan cada escritura. |
| Aritmética | Math.fma(float/double) con enteros exactos y un redondeo | Comparación de bits con JDK 25 para 5.000 ternas aleatorias de cada tipo y casos especiales; overflow cancelado y cero con signo en Wasm. No es un benchmark de rendimiento. |
| APIs concretas | UUID(long,long) y acceso a bits; PrintStream(File); String(bytes,high,offset,count); Matcher.replaceAll(Function); CRC32.update(ByteBuffer); GregorianCalendar.toInstant; carga AOT sin inicialización | Pruebas JVM/Chrome según operación; UUID y constructor de String verificados con bits altos. La detección de mutación de Matcher es best effort. Recursos del classpath y carga dinámica de clases siguen pendientes. |
| Entorno | maxMemory consulta performance.memory de Chrome; capacidad/espacio del VFS; getenv vacío | Describe el navegador y el VFS, no el disco ni las variables del proceso anfitrión. No se inventa un límite de heap si la API de Chrome no está disponible. |
| Contratos de tipos | ObjectInput/ObjectOutput y tipos de excepciones | Son interfaces y errores estándar. No se agregó un serializador del juego ni se validaron partidas guardadas. |

## Operaciones que no simulan disponibilidad

- System.load/loadLibrary lanzan UnsatisfiedLinkError con la biblioteca concreta. Los native del inventario siguen conservando la guarda con su nombre JNI completo.
- ProcessBuilder.start y Runtime.exec fallan con IOException: una página no puede iniciar procesos del SO.
- System.exit y Runtime.addShutdownHook fallan explícitamente; no se inventa un ciclo de vida de proceso ni una garantía de ejecución durante unload.
- FileChannel.lock/tryLock fallan explícitamente; no se afirma que una transacción IndexedDB equivalga a un lock de bytes del SO.

Estas fallas pueden detener el juego si alcanza esas rutas. Su presencia no convierte el port en ejecutable.

## Shaders

El parser fijado es `@shaderfrog/glsl-parser` 7.0.1, servido localmente. El traductor usa AST y scopes para qualifiers, builtins conocidos, defaults de uniforms y promociones escalares int/float. Las features sin soporte se rechazan y WebGL conserva la autoridad sobre compilación/enlace.

Se comprobaron los pares originales `default`, `blit` y `vboRenderer_PositionColorUV` mediante fetch local, transformación, compilación y enlace. Los reportes guardan hashes y resultados; no contienen fuentes del juego versionadas. Una fixture propia verifica uniforms y el píxel [64,128,191,255].

`screen.frag` usa un include propio (`util/math`) cuya resolución y orden no se inventaron. No se implementaron todos los builtins, expresiones de inicialización, promociones, extensiones o preprocesamiento de PZ. Los entrypoints LWJGL GL20, el pipeline fijo completo, la arena MemoryUtil y los callbacks GLFW aún deben conectarse al renderer. No se demostró el uso de estos tres pares por el menú ni se renderizó el menú.

## Memoria del compilador

La tercera compilación completa agotó 20 GiB en `ReflectionDependencyListener$InstanceCallConsumer`, al insertar tipos en un HashSet por consumidor. `scripts/prepare_teavm.py` aplica un cambio local a TeaVM 0.16.0: tres conjuntos de tipos vistos usan un BitSet y el índice único que DependencyAnalyzer asigna al internar cada tipo. Cada conjunto pertenece a un único análisis; no se comparte entre compilaciones. No cambia la selección de reflexión ni la propagación de métodos/campos.

La fuente pública Apache-2.0 se obtiene del source jar oficial y se fija al SHA-256 `687cabf23e0d17a15be30fbc070809c2931714340aca224120fe31579f95519b`. El archivo modificado se genera bajo `port/target/generated-sources/teavm/`, conservando la licencia; no se altera el jar de Maven ni ninguna herramienta global. El compilador verifica que las tres cachés activas tengan el tipo esperado y registra `compiler_patch` en sus reportes. El test compara con HashSet para 20.000 tipos y 100.000 inserciones repetidas, más consumidores independientes. La fixture de reflexión de plataforma verifica dispatch virtual y lectura/escritura de campos de referencia y primitivos.
