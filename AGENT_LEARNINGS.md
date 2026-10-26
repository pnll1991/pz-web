# Aprendizajes del agente

Leer al comenzar cada chat. Registrar errores propios, patrones a evitar y enfoques mejores.

## 2026-10-26 — Tarea 3: exploración
- Error detectado en revisión: remove(Object) en la cola devolvía true después de iterator.remove aunque otro hilo ya hubiera extraído ese nodo durante equals.
- Mejor enfoque: comparar fuera del monitor y comprobar el resultado de retirar el nodo por identidad dentro del monitor; probar ese interleaving de forma determinista.
- Error: un patch intentó eliminar y agregar el mismo documento en una sola operación; la herramienta lo rechazó antes de modificar archivos.
- Mejor enfoque: usar Update File o una escritura completa única para reemplazar un documento, y verificar el resultado.
- Error: las dos suites Playwright compartían el outputDir por defecto; ejecutar la suite real borró capturas y el JSON anterior de plataforma.
- Mejor enfoque: separar `test-results/platform` y `test-results/real`, conservar los resúmenes/capturas comunes fuera de esos subdirectorios y verificar que ambos reportes sigan presentes al terminar.
- Error: corregir solo `Thread.isAlive()` no alcanzaba para join antes de start: el join original lee directamente el campo privado `alive`.
- Mejor enfoque: revisar todos los consumidores del estado; hacer join con el estado público corregido, el monitor de finalización real, validación de tiempos y un bucle que tolere despertares espurios.
- Error: la guarda de reinicio de Thread alcanzó `IllegalThreadStateException`, que tampoco existe en la classlib fijada.
- Mejor enfoque: incluir el tipo de excepción estándar en la misma sustitución y verificar que el rechazo conserve su tipo, además del mensaje.
- Error detectado en revisión: `ClassLoader.loadClass` usaba el overload de `Class.forName` que inicializa en JVM.
- Mejor enfoque: usar el overload con `initialize=false` y conservar la carga separada de la inicialización aunque TeaVM tenga un comportamiento más limitado.
- Error: el primer acceso a los bits de UUID usaba `Long.parseUnsignedLong`, también ausente de TeaVM 0.16.0.
- Mejor enfoque: para un formato fijo de 16 dígitos implementar la acumulación hexadecimal exacta y probar los bits altos en Wasm, sin asumir cobertura por existir la API en JDK 25.
- Error detectado en revisión: la primera espera no interrumpible del lock reingresaba al final de la cola tras una interrupción, alterando la equidad solicitada.
- Mejor enfoque: conservar el mismo lugar en la cola, restaurar el flag al adquirir y reenviar una señal de condición si gana la interrupción.
- Error: la segunda pasada de shaders dejó promociones implícitas de int a float permitidas en escritorio y rechazadas por GLSL ES.
- Mejor enfoque: resolver tipos mediante los scopes y referencias del AST e insertar conversiones solo donde corresponden; conservar índices y variables enteras, y exigir compilación/enlace reales en WebGL2.
- Error: el primer shader real traducido conservaba inicializadores de uniforms de GLSL de escritorio, que WebGL rechaza.
- Mejor enfoque: extraer esos valores del AST, inicializarlos con `uniform*` después del enlace y validar valores y píxeles; no descartar silenciosamente los defaults.
- Error: al eliminar el nodo AST de `#version` también eliminé comentarios adjuntos a ese nodo; el test del traductor lo detectó.
- Mejor enfoque: conservar el whitespace/comentarios del AST al sustituir directivas, y comprobar identificadores, macros y comentarios con fixtures propias.
- Error: invoqué una exportación JSO que esperaba un Future directamente desde JavaScript; TeaVM rechazó la suspensión fuera de una corrutina.
- Mejor enfoque: la exportación inicia un Thread de TeaVM y comunica estado al host; el test espera ese estado, como requiere la documentación de interacción JS/corrutinas.
- Error: la primera policy de sustitución seleccionaba solo clases superiores; las clases anidadas conservaron tipos del paquete propio y TeaVM no encontró sus constructores.
- Mejor enfoque: incluir explícitamente las clases anidadas (`Nombre$...`) en la misma regla de renombrado y verificar el camino Java estándar en Wasm, además de la implementación propia en JVM.
- Error: una aserción JUnit con `invokeAny` y lambdas dejó ambiguos los overloads numéricos de `assertEquals`.
- Mejor enfoque: fijar el parámetro genérico y asignar el resultado antes de comparar; compilar los tests junto con la primera implementación.
- Error: repetí la búsqueda con `rg` ausente antes de leer los aprendizajes y agrupé lecturas que excedieron el límite global de salida.
- Evitar: repetir comandos fallidos de sesiones anteriores y asumir que los límites individuales de los comandos amplían el de la herramienta contenedora.
- Mejor enfoque: leer primero este archivo con `cat`, usar Python para búsquedas en este entorno y acotar cada lectura; recuperar todo tramo truncado antes de usarlo.

## 2026-10-26 — Lectura inicial y estructura real
- Error: volví a intentar `rg` antes de leer que no está instalado; además una lectura agrupada excedió el límite total de salida.
- Evitar: iniciar exploraciones antes de leer este archivo o confundir el límite por comando con el límite de la llamada agrupada.
- Mejor enfoque: leer primero los aprendizajes con herramientas básicas, comprobar disponibilidad y limitar la suma de las lecturas; recuperar explícitamente los rangos truncados.
- Error: el parser inicial rechazaba strings Java válidos con surrogates aislados; el primer inventario real falló al leer el fat jar.
- Evitar: suponer que todo `CONSTANT_Utf8` representa Unicode escalar; Java también admite unidades UTF-16 aisladas.
- Mejor enfoque: conservar surrogates con `surrogatepass`, probar NUL, pares e individuos, y limitar la salida a metadatos.
- Error: busqué el SPI de archivos en el jar de classlib en lugar de localizar primero la clase; está en teavm-core. Además `var` conservó un wildcard de `ZipFile.stream()` y bloqueó agregar helpers al analizador.
- Evitar: inferir el jar por el paquete o dejar tipos capturados en colecciones que se amplían.
- Mejor enfoque: consultar entradas de los jars fijados y declarar `List<ZipEntry>` al construir la cola de análisis.
- Error: la primera clasificación ELF confundía x86-64 de BSD/Solaris/Android con Linux.
- Mejor enfoque: combinar arquitectura y ruta de plataforma; separar JRE, Linux glibc y otras plataformas antes de contar funciones.
- Error: el primer test de navegador intentó usar 8090, ocupado por el servidor existente; la primera compilación con reflexión agotó el heap reducido y conservó el reporte anterior porque solo se capturaban `Exception`.
- Evitar: compartir el puerto de desarrollo con tests o conservar evidencia vieja tras un `Error` del compilador.
- Mejor enfoque: usar un puerto de pruebas independiente; escribir estado `running` al inicio, capturar fallas del compilador y separar reportes y Wasm por modo/reflexión. El heap de 2 GB se usa solo para diagnóstico con poca RAM disponible.
- Error: el predicado propio de reflexión asumía que TeaVM siempre entrega una clase no nula; con la exposición real alcanzó tipos ausentes y abortó antes de emitir los diagnósticos.
- Mejor enfoque: rechazar explícitamente el valor nulo en la selección; conservar los errores de tipos ausentes del compilador en el reporte en lugar de ocultarlos con una excepción del plugin.
- Error: mezclé `Class<?>` con el overload de `MethodReference` cuyo propietario es un `String`, y javac rechazó la corrección de lectura binaria.
- Mejor enfoque: usar `ValueType` para toda la firma cuando se identifica al propietario por nombre; comprobar en JVM y Chrome los bytes 0, 128, 255 y EOF.
- Error: la adaptación inicial de PZ_HOME anidado cubría inventario/build/servidor, pero no el empaquetador usado por el README.
- Mejor enfoque: reutilizar el mismo resolver en todos los scripts Python y verificar el comando documentado con una fixture anidada antes de entregar.
- Error: el estilo global `canvas { display: block }` anuló el atributo `hidden` de la pantalla bloqueada; además había anticipado esa verificación en el borrador de documentación.
- Mejor enfoque: respetar `[hidden]` explícitamente en CSS y registrar la comprobación visual solo después de que las aserciones y la inspección de la captura hayan pasado.

## 2026-10-25 — Herramientas y estado inicial
- Error: intenté usar `rg` sin que estuviera instalado y consultar el historial de un repositorio aún sin commits.
- Evitar: asumir que las herramientas habituales o un HEAD inicial ya existen.
- Mejor enfoque: detectar herramientas con `command -v`; usar Python para búsquedas si falta `rg`, y `git rev-parse --verify HEAD` antes de consultar commits.
- Error: la primera lectura combinada del roadmap excedió el límite de salida y quedó truncada.
- Evitar: considerar leído un archivo cuando la herramienta informa truncamiento.
- Mejor enfoque: comprobar tamaño, dividir lecturas por rangos y recuperar cualquier tramo omitido antes de implementar.
- Error: una búsqueda de herramientas por palabras demasiado generales devolvió cientos de conectores irrelevantes.
- Evitar: buscar `exec` en todas las descripciones, ya que aparece en las declaraciones de cada herramienta.
- Mejor enfoque: filtrar nombres concretos y limitar la salida antes de consultar documentación.

## 2026-10-25 — Extensiones de TeaVM
- Error: importé `ProgramEmitter` desde `org.teavm.model.util`; en 0.16.0 está en `org.teavm.model.emit` y el primer build Java falló.
- Evitar: inferir el paquete de una API a partir del nombre o de recuerdos de otras versiones.
- Mejor enfoque: consultar tanto el paquete como las firmas en el source jar de la versión fijada y compilar una prueba pequeña antes de integrar.
- Error: pasé solo las clases propias a `TeaVMTool.setClassPath`; el ClassLoader tenía dependencias pero el proveedor de recursos de TeaVM no. El compilador falló al buscar Fiber.
- Evitar: confundir el classpath del proceso JVM con el classpath explícito del compilador AOT.
- Mejor enfoque: obtener las dependencias de ejecución desde Maven y entregar a TeaVM tanto esos archivos como las clases propias y las referencias externas.
- Error: probé `apitrace --version`; esa herramienta usa el subcomando `apitrace version`.
- Mejor enfoque: confirmar los subcomandos en la ayuda y usar el chequeo correcto en el doctor del proyecto.

## 2026-10-25 — Tests asíncronos
- Error: incluí una aserción sincrónica `assert.throws` alrededor de `flush()`, que devuelve una promesa rechazada.
- Evitar: tratar un rechazo asíncrono como una excepción sincrónica o dejar promesas rechazadas sin esperar.
- Mejor enfoque: `await assert.rejects(...)`; la prueba de falta de apertura queda en el test asíncrono del VFS.

## 2026-10-25 — Orden de transformaciones y diagnóstico
- Error: el transformador de GL inyectaba llamadas directas a métodos `@JSBody`. Esas llamadas llegaban después de la transformación JSO y TeaVM reclamaba `@Import`.
- Evitar: asumir que una llamada agregada tarde al IR vuelve a pasar por todos los transformadores anteriores.
- Mejor enfoque: dirigir el IR nuevo a wrappers Java normales; dejar la llamada JSO dentro del cuerpo original del wrapper y comprobarlo compilando y ejecutando una fixture propia en Chrome.
- Error: el primer reporte imprimía `CallLocation.toString()` y mostraba un hash de objeto, sin identificar el método; además consulté su paquete por inferencia.
- Mejor enfoque: usar el tipo real `org.teavm.model.CallLocation` y escribir `getMethod()` en el diagnóstico.

## 2026-10-26 — Caché de reflexión y reportes de fallos
- Error: el build completo agotó 20 GiB en los HashSet de tipos vistos por cada consumidor de reflexión; además el reporte de APIs anterior podía quedar junto al nuevo estado fallido.
- Evitar: reducir silenciosamente la exposición Lua para ocultar el consumo o presentar diagnósticos viejos como actuales.
- Mejor enfoque: verificar una caché compacta por índice internado con la misma semántica; invalidar reportes derivados al iniciar y conservar métricas también al fallar.
- Error: una lectura usó `port/compiler/BuildCompiler.java` en lugar de la ruta real bajo `src/main/java`.
- Mejor enfoque: usar rutas verificadas del árbol de fuentes; no inferir ubicaciones desde abreviaturas.
- Error: al retomar, también supuse los nombres `verify.sh` y `ReflectionPolicy.java`; no existían.
- Mejor enfoque: listar el directorio antes de recuperar rutas que no quedaron explícitas en el contexto.
- Error detectado en revisión: la validación del parche del compilador estaba fuera del bloque que registra fallos y podía dejar el estado running.
- Mejor enfoque: ejecutar esa validación dentro de la misma captura de fallos que TeaVM, antes de generar el módulo.
- Error: jstat no pudo abrir el perfdata identificado desde otra llamada de herramienta; repetir esa vía no aportó métricas fiables.
- Mejor enfoque: usar las métricas que escribe el propio proceso compilador; no interpretar ese fallo de observación como terminación ni como falta de memoria.
- Error: ejecuté la suite Python sin cargar env.sh y sus fixtures no encontraron javac.
- Mejor enfoque: cargar siempre el entorno local también para tests Python que compilan fixtures Java, como hace scripts/test.sh.
- Error detectado en revisión: la prueba nueva del reporte real asumía que siempre existía build-state.json del juego, aunque el README permite probar recursos después de compilar solo la plataforma.
- Mejor enfoque: aceptar explícitamente el 404 de un workspace sin build del juego, validar el reporte real si existe y ejecutar siempre el caso aislado de fallo.
