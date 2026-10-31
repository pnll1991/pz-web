# Aprendizajes del agente

Leer al comenzar cada chat. Registrar errores propios, patrones a evitar y enfoques mejores.

## 2026-10-26 — Ronda 5
- Error: la prueba real esperaba ver MainScreenState en una traza más profunda, pero Chrome recortaba los frames con el límite predeterminado. Mejor enfoque: conservar los frames fuera del logger y ampliar el límite de diagnóstico de V8 antes de cargar Wasm; verificar símbolos y registros de entrada/retorno juntos.
- Error detectado en revisión: el nombre de tecla podía devolver una etiqueta para Enter si el layout la ofrecía. Mejor enfoque: aplicar el dominio de teclas imprimibles de GLFW antes de consultar KeyboardLayoutMap y probar una tecla no imprimible con etiqueta presente.
- Error descubierto al ejecutar el juego: los logs no eran una traza fiable; JsConsolePrintStream repetía segmentos por no avanzar off, anulaba flushAction antes de invocarlo y finalizaba el decoder en cada flush. Mejor enfoque: reproducir con una fixture propia de bytes y UTF-8, corregir la fuente pública fijada y conservar además la traza Java por un canal independiente de System.err.
- Error: asumí `build-state.json` en reports, una clase Drawable ausente y el source jar dentro de Maven; las rutas reales eran web/game y .toolchain/downloads. Mejor enfoque: listar y reutilizar las rutas y tipos confirmados antes de consultarlos.
- Error: usé ProgramEmitter.constant(boolean), overload inexistente. Mejor enfoque: consultar la API fijada; emitir un entero convertido a BOOLEAN y preservar la guarda idempotente de inicialización.
- Error: la lectura inicial volvió a usar `rg` ausente y agrupó documentos hasta truncar la salida, pese a los aprendizajes existentes.
- Causa y patrón a evitar: iniciar exploración antes de recuperar las restricciones locales y sumar límites de salida individuales como si fueran independientes.
- Mejor enfoque: usar Python para búsquedas en este entorno, leer por rangos acotados y recuperar todos los tramos omitidos antes de implementar.

## 2026-10-26 — Tarea 4
- Error: coloqué @JSBody y @JSFunctor dentro de clases sustituidas con nombres java.*; TeaVM no generó sus imports.
- Mejor enfoque: dejar los puentes JSO en port.shim y llamarlos desde wrappers Java normales de las sustituciones.
- Error: asumí que ClassRefsRenamer.rename devolvía una copia. Devuelve el mismo ClassHolder si el nombre no cambia; limpiar sus interfaces borró las interfaces reales y Chrome falló con illegal cast.
- Mejor enfoque: leer el contrato/fuente fijada del helper y aplicar el renombrado in-place sin limpiar colecciones compartidas; comparar alcance y ejecutar el resultado, no aceptar solo cero diagnósticos.
- Error: los helpers fuera de port.compat conservaban referencias a tipos propios mientras las sustituciones esperaban java/javax/org originales; el primer build de plataforma encontró una firma que no coincidía.
- Mejor enfoque: normalizar las referencias de los puentes con ClassRefsRenamer y verificar las APIs estándar en Wasm; no confundir la prueba JVM del tipo propio con el enlace de la sustitución.
- Error: inferí de nuevo el paquete de ClassRefsRenamer en una consulta; está en org.teavm.parsing.
- Mejor enfoque: consumir la ruta detectada antes de consultar su API.
- Error: ScheduledFuture heredaba Delayed del JDK mientras su implementación usaba el contrato propio; javac detectó firmas incompatibles.
- Mejor enfoque: mantener las familias sustituidas en un mismo sistema de tipos en JVM antes del renombrado AOT.
- Error detectado en revisión: quitar un buffer liberado del registro permitía volver a registrarlo; y el read/write lock justo solo ordenaba el monitor, no los solicitantes.
- Mejor enfoque: conservar marcas débiles de buffers liberados y una cola explícita de solicitudes, incluida la readquisición de condiciones; probar ambos casos.
- Error: usé int para Uint8Array.set(index, value); la API JSO fijada recibe short.
- Mejor enfoque: verificar las firmas JSO y convertir explícitamente bytes sin signo antes de compilar.
- Error: el generador de llamadas polimórficas llamó box() también sobre referencias; TeaVM lo rechazó.
- Mejor enfoque: boxear solo ValueType.Primitive y conservar referencias como Object; comprobar el adaptador en Wasm antes de atribuir progreso al build real.
- Error: consulté ClassSelection por inferencia; la API fijada usa SimpleReflectionPolicy$ClassPolicy.
- Mejor enfoque: listar/consultar las clases reales del SPI antes de usarlas.
- Error: repetí el intento de usar rg antes de leer este archivo, y dos lecturas agrupadas excedieron el límite global de salida.
- Evitar: inferir disponibilidad de herramientas o confiar solo en el límite por comando.
- Mejor enfoque: leer primero aprendizajes, usar Python en este entorno y limitar cada lectura recuperando tramos truncados.

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

## 2026-10-26 — Tarea 4, compatibilidad y navegador
- Error: el adaptador polimórfico intentaba boxear referencias y falló en el compilador. Mejor enfoque: distinguir primitivas y referencias, y validar tipos exactos de parámetros y retorno.
- Error: inferí paquetes de extensiones TeaVM y el tipo de Uint8Array.set; las firmas no coincidían. Mejor enfoque: consultar las APIs públicas de la versión fijada antes de compilar.
- Error: ClassRefsRenamer modifica in situ las clases cuyo nombre no cambia; copiar interfaces de vuelta vació la misma colección y rompió los casts. Mejor enfoque: respetar su identidad y verificar el enlace con una ejecución Wasm.
- Error: JSO dentro de clases renombradas java.* no produjo imports válidos. Mejor enfoque: colocar los métodos JSO en puentes propios y llamar wrappers Java desde las sustituciones.
- Error: el primer control de XML malformado solo revisaba la raíz; Chrome puede insertar parsererror como descendiente. Mejor enfoque: buscar el elemento de error en el namespace del parser y probar entradas malformadas.
- Error: asumí que existía el source jar de classlib sin listar el cache. Mejor enfoque: resolver primero archivos disponibles y obtener fuentes oficiales de la versión exacta cuando falten.
- Error: el lector ZIP pasaba maxOutputLength=0 para recursos vacíos. Mejor enfoque: permitir un límite mínimo de un byte y validar longitud real y CRC después de descomprimir.
- Error: supuse nombres contiguos e imports exclusivamente de funciones en Wasm. Mejor enfoque: contar imports por su tipo real, incluidos tags de excepciones; usar solo nombres y límites para resolver trazas.
- Error: una búsqueda de procesos incluyó la línea extensa del sandbox. Mejor enfoque: limitar columnas y nombres de proceso; los namespaces por ejecución tampoco permiten observar otros comandos así.
- Error detectado en Chrome: confiar en wait de TeaVM ocultó dos fallos del runtime, interrupción sin recuperar el monitor y espera submilisegundo redondeada a cero. Mejor enfoque: parche público fijado, recuperar profundidad del monitor, limpiar interrupción y redondear nanos hacia arriba; probar todo en Wasm, no solo JVM.
- Error de diagnóstico: el timeout submilisegundo también perdía precisión en TimeUnit.timedWait, antes de llegar a Object.wait. Mejor enfoque: seguir toda la ruta de conversión y preservar el resto de nanosegundos en wait/join; los timeouts no positivos no esperan.
- Error: reutilicé el namespace de error XML de Firefox para Chrome. Mejor enfoque: observar DOMParser en el Chrome instalado; sus errores usan XHTML. Rechazar explícitamente esos nodos reservados y documentar la limitación.
- Error: llamé getColumnNames de SQLite para sentencias sin columnas; el wrapper oficial rechaza el índice cero. Mejor enfoque: consultar columnCount y distinguir DDL/DML de resultados tabulares.
- Error: el JSBody de detección XML usó spread syntax que el parser de TeaVM advertía aunque Chrome lo ejecutara. Mejor enfoque: usar sintaxis compatible con el parser fijado y revisar warnings también en el main real.
- Error detectado en revisión: el logger descartaba incluso handlers locales cuando useParentHandlers=false. Mejor enfoque: publicar en handlers locales primero y cortar solo la propagación; probar el filtro en Chrome.
- Error: el listener de progreso llamó peakRss, que declara una excepción checked, desde un método que no la permite. Mejor enfoque: mantener el callback de progreso sin IO; registrar recursos en el reporte final ya protegido.
- Error: asumí Formatter.formatMessage disponible porque Formatter ya existía en TeaVM. Mejor enfoque: verificar métodos concretos y agregar la operación faltante con una prueba de parámetros.
- Error detectado en revisión: memRealloc de capacidad cero podía producir dirección nula y luego intentar una vista sobre ella. Mejor enfoque: usar la variante checked no nula en APIs que devuelven buffers y comprobar el buffer vacío/liberación.
- Error de alcance detectado en el main: el backend Wasm de TeaVM desreferencia el tipo genérico nulo de campos con anotaciones y sin Signature. Mejor enfoque: preservar anotaciones, emitir genericType nulo como prevé TField.getGenericType y verificar campos anotados raw junto con campos parametrizados; no recortar reflexión Lua para evitarlo.
- Error detectado en revisión: aplicar MessageFormat a mensajes sin placeholders altera apóstrofos literales si hay parámetros. Mejor enfoque: formatear solo cuando el mensaje contiene un índice de formato y comparar ambos casos en JVM.
- Error detectado en revisión inicial de la tarea: registrar de nuevo un ByteBuffer liberado podía revivir una dirección y el lock read/write justo no tenía una cola explícita de solicitudes. Mejor enfoque: recordar vistas retiradas sin retenerlas fuertemente y arbitrar solicitudes con una cola, incluidos timeout/interrupción.
- Error detectado en comparación de tareas diferidas: asumir que todo Delayed pertenece al mismo pool produce casts inválidos. Mejor enfoque: comparar deadlines propios cuando el tipo coincide y usar getDelay para otras implementaciones.
- Error de backend detectado después: class metadata usa incondicionalmente la vtable raíz, pero el generador la omitía si el modelo Object era abstracto. La fixture mínima normal no reproducía esa condición; la variante de IR abstracta sí reproduce la misma traza. Mejor enfoque: mantener la vtable requerida por la metadata independientemente de instanciabilidad, registrar el estado real y ejecutar el módulo de regresión en Chrome.
- Error: compilar WasmGCClassGenerator desde el source jar requería HPPC relocalizado, pero la fuente usa el paquete original y Maven lo declaraba solo en runtime. Mejor enfoque: adaptar los imports al namespace del binario publicado y añadir la dependencia relocalizada al classpath de compilación; no mezclar ambos tipos de mapas.
- Error: el primer parche de vtable contempló Object pero no sus arrays, que reutilizan la misma tabla aunque siempre sean tipos concretos. Mejor enfoque: cubrir la raíz y los arrays juntos; la regresión mínima detecta ambos sitios antes de repetir el main completo.
- Error de diagnóstico: forzar la emisión de vtables trataba el síntoma; el main reveló que la tabla de Object había sido absorbida por una interfaz JNA. Mejor enfoque: reproducir la fusión con una jerarquía Java válida de interfaces marker y evitar fusionar como interfaz una tabla que ya resolvió a una clase; retirar el parche del emisor.
- Error: la pila JVM predeterminada se agotó al serializar los bloques Wasm anidados del main completo. Mejor enfoque: configurar la pila local de Maven explícitamente junto al límite de heap y comprobar la emisión completa sin recortar reflexión.
- Error: lecturas de revisión supusieron subdirectorios para GameBoot, un nombre JdkRuntime y una interfaz generada MBeanServer. Mejor enfoque: listar primero archivos propios y esperar ese resultado antes de leerlos; distinguir fuentes mantenidas de contratos generados bajo target.
- Error detectado al emitir el main: TeaVM solo generaba las intrinsics nativas inline en llamadas directas; al tomar sus referencias por reflexión intentaba importarlas sin anotación. Mejor enfoque: generar cuerpos invocables a partir de las mismas intrinsics, con una fixture de reflexión que reproduce el diagnóstico y luego se ejecuta en Chrome; no eliminar Integer, Long u Object del manifiesto.
- Error detectado en artefactos: TeaVM llegó a escribir un Wasm pese a reportar errores severos del backend, y BuildCompiler solo eliminaba salidas al inicio o ante excepciones internas. Mejor enfoque: eliminar también el módulo cuando el análisis final contiene errores severos; un estado fallido nunca deja un Wasm utilizable aparente.
- Error detectado en Chrome: exponer todos los miembros públicos de Object también exponía getObjectClassInfo, un helper exclusivo de TeaVM que retorna un struct interno no convertible a Object Java. Mejor enfoque: excluir solo ese helper de la invocación reflectiva; conservar todas las clases Lua y las APIs reales del JDK, incluida getClass.
- Error de lectura: busqué ReflectionMetadataGenerator en generate/classes, pero el source jar lo ubica en intrinsics/reflection. Mejor enfoque: usar el nombre exacto ya fijado en prepare_teavm.py antes de buscar el archivo upstream.
- Error en la expectativa de la fixture numérica: supuse que TeaVM convertía la trampa Wasm de división por cero a ArithmeticException Java. Chrome demuestra RuntimeError. Mejor enfoque: comprobar resultados y límites numéricos válidos, registrar y probar por separado la trampa explícita, y documentar que esa traducción de excepciones sigue pendiente.
- Error al filtrar el helper interno de Object: asumí metadata de clase no nula, pero el análisis del main consulta tipos opcionales ausentes. Mejor enfoque: conservar la respuesta de la policy base cuando cls es null y aplicar el filtro solo a Object presente.
- Error detectado al cargar el Wasm completo: el build sin diagnósticos emitió initReflection de 9.245.402 bytes, mayor al máximo de Chrome (7.654.321 bytes por función). Mejor enfoque: generar una función de inicialización por clase y llamarlas en el mismo orden; conservar toda la metadata y verificar el módulo en el navegador además del éxito del compilador.
- Error de diagnóstico del cast: Chrome detectó una excepción usada solo en catch sin jerarquía; TeaVM no la había informado como API ausente. Mejor enfoque: inventariar nombres/supertipos de la sección de tipos Wasm, completar las excepciones JDK faltantes con sus padres reales y comprobar capturas dinámicas en Chrome. La inspección encontró también ObjectOutputStream solo como tipo de firma; su serialización falla explícitamente.
- Error al añadir ObjectOutputStream: su interfaz de constantes no estaba en TeaVM. Mejor enfoque: generar el contrato y sus constantes primitivas desde el JDK fijado; probar la sustitución en el módulo pequeño antes de volver al main completo.
- Error de alcance de la policy: excluir solo getObjectClassInfo dejaba otros miembros públicos internos de TeaVM, como el constructor de Field con FieldInfo, que generaban retornos/argumentos Wasm incompatibles. Mejor enfoque: contrastar firmas públicas de java/javax con el JDK 25 del compilador mediante su PlatformClassLoader, sin cargar el juego; conservar miembros reales, tipos sin resolver y toda la exposición del juego. Probar también la reflexión sobre Field, Method, Constructor y Class.
- Error de backend en corrutinas: WasmTypeInference(forceNullable=true) refinaba br_on_null a non-null, pero la reanudación restauraba un valor nullable y Chrome rechazaba el bloque. Mejor enfoque: respetar forceNullable en esa rama; la prueba mínima falla antes del parche y pasa después, con verificación Chrome de recursos cuyo cierre suspende y de recurso nulo.
- Error de diagnóstico parcial: corregir solo br_on_null no resolvió el main; los casts de dispatch virtual y las salidas de bloques también introducen referencias non-null. Mejor enfoque: normalizar todos los valores de referencia que se agregan a la pila cuando forceNullable está activo, conservando el modo normal; ampliar la regresión a casts/bloques y al cierre suspendible mediante una interfaz.
- Error de arranque: registerDefault adquiría el monitor de ThreadGroup durante la construcción estática del hilo main, cuando currentThread aún es null; el monitor suspendible falla al salir. Mejor enfoque: registrar ese único hilo de bootstrap antes de tomar cualquier monitor, y mantener sincronizado el registro normal; reproducir con una fixture que hace suspendible el monitor de ThreadGroup.
- Error de lectura al retomar: supuse que Task4Checks estaba directamente en port; está en port/runtime. Mejor enfoque: recuperar primero la ruta existente con find, sin repetir ubicaciones abreviadas.
- Error de expectativa: la fixture pequeña de ThreadGroup no reprodujo el monitor asíncrono del bootstrap del main completo, incluso con reflexión Thread/ThreadGroup. Mejor enfoque: usarla solo para verificar membresía y ciclo de vida; comprobar el fallo de arranque con el módulo real, sin afirmar que la fixture fallaba antes del parche.
- Error de edición: intenté borrar y agregar el mismo archivo en un único apply_patch; la herramienta lo rechaza. Mejor enfoque: reemplazar su contenido mediante una operación Update File.
- Error detectado en revisión de la fixture: una aserción dentro del hilo hijo podía acabar en su handler y no rechazar la prueba del padre. Mejor enfoque: recoger el resultado y comprobarlo después de join en el hilo que determina el resultado del test.

## Ronda 19 (2026-10-27)
- Error grave: interpreto mal los varargs de `MethodDescriptor`/`MethodReference` de TeaVM, que son `nombre, tipos de parámetros…, tipo de resultado`. Escribí `new MethodDescriptor("checkWindow",ValueType.VOID,ValueType.LONG)` y produce `checkWindow(V)J` (argumento VOID, resultado LONG). El `EmitException` lo confirma: "Argument 0 of type I is not compatible with method port.shim.BrowserCallbacks.setHandler(VI)Ljava/lang/Object;". Mejor enfoque: verificar el contrato con un programa mínimo (`javac/java` contra `teavm-core.jar`, imprimir `toString()`) antes de escribir decenas de referencias; imprimir el descriptor esperado para cada shim.
- Error: ese mismo orden invertido ya existía en el código de rondas previas para `setHandler` y en otros 25 sitios nuevos; un solo build largo reveló el primero porque el analizador de dependencias aborta en el primer método. Mejor enfoque: auditar todas las construcciones de referencias del archivo de una vez (grep `new MethodReference|new MethodDescriptor`) y corregirlas por patrón, no de a una por build.
- Error: `InvocationType` de TeaVM solo tiene `VIRTUAL` y `SPECIAL`; intenté usar `INTERFACE`. Mejor enfoque: dispatch de `CallbackI` con `InvocationType.VIRTUAL`.
- Error: asumí que el argumento de un método de instancia empieza en `var(1)` sin recordar que `var(0)` es `this`; para los estáticos los parámetros sí empiezan en `var(1)`. Mejor enfoque: distinguir explícitamente estático/instancia al indexar.
- Error: usé `bitwiseAnd`; el método real es `bitAnd`. Mejor enfoque: `javap` sobre la clase concreta del emisor antes de escribir aritmética.
- Error: declaré un campo usado por un método anterior (`addresses` después de su uso relativo a `KIND_COUNT`) y javac rechazó la referencia ilegal hacia adelante. Mejor enfoque: ordenar constantes derivadas antes de su primer uso.
- Error operativo: lancé el build completo con `nohup` dentro de un `exec_command`; al cerrar esa sesión el proceso hijo murió y el log quedó congelado en `DEPENDENCY_ANALYSIS`, lo que me hizo esperar ~20 minutos sin progreso. Mejor enfoque: correr builds largos en la sesión en primer plano (o `setsid` + verificación de `ps`), y comprobar que el proceso sigue vivo y el archivo de log avanza (`stat -c '%y'`) antes de confiar en un build en segundo plano.
- Patrón a evitar: esperar el build completo para validar supuestos de API. Un test de una línea contra `teavm-core-0.16.0.jar` cuesta segundos.
- Patrón a evitar: dejar `STATUS.md` viejo durante muchas rondas. Estaba en ronda 5 cuando HEAD era ronda 18; la siguiente ronda arranca leyéndolo y pierde el contexto.

## 2026-10-27 — Ronda 21
- Error: treaté el raise de `glfwSetWindowIcon` como la raíz del stop de `Display.create`. La raíz real era `org.lwjgl.system.libstb.LibSTB.<clinit>` → `Library.loadSystem`, cuya excepción de clase quedaba capturada por `loadIcons` y sólo producía el mensaje "failed to read image". Mejor enfoque: antes de sustituir el síntoma, rastrear el `catch` del llamador y qué `<clinit>`/`loadSystem` lo dispara; sustituir el `<clinit>` y puentear la operación, no ensanchar el raise.
- Error: volví a escribir los varargs de `MethodDescriptor`/`MethodReference` al revés (dos veces en `StbCompatibility.java`) → "no suitable constructor". La convención de TeaVM 0.16.0 es `nombre, parámetros…, resultado`. Mejor enfoque: copiar la forma ya verificada de otro transformador o confirmar con `javap` del jar fijado antes de editar; no re-derivarla de memoria.
- Error: un cuerpo void cuyo `if` termina sus ramas con `e.exit()` dejó el bloque de.join sin terminator → NPE en `UnreachableBasicBlockEliminator.optimize` durante el build Wasm (build 21c falló). Mejor enfoque: cerrar todo cuerpo transformado con un `e.exit()` explícito, sin confiar en el jump implícito del `IfEmitter` cuando las ramas escapan.
- Error: asumí que los wrappers LWJGL pasan buffers de salida no nulos. `Display.calcWindowPos` pasa `null` para right/bottom a `glfwGetWindowFrameSize(J,IntBuffer,IntBuffer,IntBuffer,IntBuffer)` → "dereferencing a null pointer" en Wasm (loader-failed). Mejor enfoque: leer el llamador real y validar `null` en cada out-buffer; el contrato nativo de GLFW permite punteros nulos.
- Error: asumí que el `Uint8Array` del filesystem del navegador era asignable a `byte[]`. No lo es. Mejor enfoque: copiar con `getLength()`/`get(i)` al buffer del arena y verificar la conversión con una prueba de decode, no solo con `mvn compile`.
- Error repetido: usé `rg` (ausente) y luego intenté inspeccionar la captura con `view_image` (deshabilitado). Mejor enfoque: `grep`/Python para buscar; para capturas, confirmar con histograma/región de píxeles y dejar `screenshot_visually_checked:false` cuando no hay inspección visual real, en vez de afirmarla.

## 2026-10-27 — Ronda 22
- Error: otra ronda (>3 h) leyendo bytecode de GL/GLCapabilities/ThreadLocalUtil sin terminar el puente; el límite del task (20 comandos de lectura antes de la primera edición) se superó ~25 y el corte duro de 4 h dejó la ronda sin build ni prueba. Mejor enfoque: fijar un sub-hito de 40-60 min con build+Chrome antes de abrir análisis nuevos; si el diseño requiere más de ~10 transformadores, particionar la ronda y dejar el diseño escrito en STATUS.md en lugar de ejecutarlo a medias.
- Patrón verificado (útil, reutilizar): `GLCapabilities.<init>` llena sus 2233 campos `long glXxx` con `PointerBuffer.get(i)` al final del constructor, y cada `check_*` empieza con `set.contains(tag)`; por lo tanto basta un `FunctionProvider` que devuelva address ≠ 0 solo para entrypoints realmente disponibles para obtener capacidades honestas (ausente ⇒ 0 ⇒ capability false).
- Hecho: los wrappers públicos `GL11.glXxx` reenvían a `GL11C.glXxx` **native**; mapear `GL11` en `gl-overrides.tsv` no sirve para el juego. Las mappings deben apuntar a `org.lwjgl.opengl.GL11C` (y `GL12C/GL13C/GL15C/GL20C/GL21C/GL30C`, todas presentes en el jar).
- Hecho: en el jar pinneado **no existe** ninguna implementación concreta de `org.lwjgl.system.FunctionProvider` (`SharedLibraryImpl` no está); el provider debe construirse en IR sobre una clase sustituida (`GL$1 extends SharedLibrary$Delegate`, `linux.LinuxLibrary extends SharedLibrary$Default`).
- Hecho: `ThreadLocalUtil.<clinit>` y `GL.setCapabilities` dependen de JNI/`MemoryUtil.memAddress(CustomBuffer)`; hay que sustituir ambos (no-op / `address()J`) o el camino de capacidades lanza aunque el provider sea correcto.

## 2026-10-28 — Ronda 29
- Error: la Ronda 28 dejó dos líneas (`this.capabilities` / `this.fixedFunctionCaps`) escritas
  **después** de la llave de cierre de `class GLBridge` en `port/runtime/gl.mjs`, lo que producía
  `TypeError: Cannot set properties of undefined (setting 'capabilities')` al importar el módulo y
  hacía fallar las 5 pruebas Chrome en cascada (`loader-failed`). `node --check` NO lo detecta
  (es error de runtime, no de sintaxis). Mejor enfoque: validar todo módulo editado con
  `node -e "import('./port/runtime/gl.mjs')"`, no solo con `node --check`.
- Error: asumí que un build previo estaba usable. El corte duro de 4 h de la Ronda 28 ocurrió
  *durante* el build: `build-round28f-game.log` truncado en `TeaVM progress=3990` y
  `port/target/web/game/build-state.json = {"success":false,"status":"running"}` sin `boot.wasm`.
  Mejor enfoque: al empezar cada ronda, leer `build-state.json` y verificar que exista
  `port/target/web/game/boot.wasm`; si no, reconstruir antes de cualquier prueba.
- Hecho operativo: `scripts/build.sh` copia `port/runtime/*.mjs → port/target/web/runtime/`
  **después** de `mvn`, así que editar `port/runtime` exige `cp port/runtime/*.mjs
  port/target/web/runtime/` o un build nuevo; probar contra `port/target` sin ese cp usa código viejo.
- Error de herramienta: `scripts/wasm_stack.py` exige offsets con prefijo `0x`; sin prefijo
  resuelve 0 o falla silenciosamente. Mejor enfoque: pasar siempre `0x...`.
- Corrección (Ronda 29): la nota anterior sobre `scripts/wasm_stack.py` era falsa. Su CLI hace
  `int(value,0)`, por lo que acepta decimales **y** `0x...`; los offsets decimales resuelven bien
  (verificado con 6227728 fillInStackTrace, 27792179 printSpecs, 9936031 main). Mejor enfoque:
  verificar el comportamiento real de un script antes de documentar un requisito de formato.
- Error: interpreté la recursión del `RangeError` como "replay por iteración" del prólogo de
  corutina. Era recursión mutua real `Arrays.copyOf(T[],int) → Array.newInstance(@Async) →
  Fiber::reversePush → Fiber::push` (~3369 ciclos). Mejor enfoque: resolver la pila con
  `wasm_stack.py` y contar frames repetidos antes de nombrar la causa.
- Error: un `str.replace()` en `scripts/prepare_teavm.py` consumió `def main():` al insertar código
  (el patrón ancla coincidía con esa línea). Mejor enfoque: tras insertar en .py, correr siempre
  `python3 -c "import ast;ast.parse(open(path).read())"` antes de ejecutar el script.
- Error en `patch_fiber()`: (a) se leía otro entry del source jar después de cerrar su `with`
  (`package.read` fuera del bloque) → KeyError/ZipFile cerrado; hay que reabrir un segundo `ZipFile`
  o mover la lectura dentro; (b) el patrón buscado en `WasmGCNewArrayFunctionGenerator.java` estaba
  indentado, pero los 2 call sites tienen 4 y 8 espacios → el reemplazo no aplicaba y el guard
  `declared != null` perdía el prefijo. Mejor enfoque: buscar el patrón sin indentación y verificar
  el número de sustituciones.
- Hecho: parchar `Fiber.java` hace alcanzable `Array<Object>@new` sin pasar por `Array.newInstance`,
  lo que exige parchar también `WasmGCNewArrayFunctionGenerator.java` (devolvía `null` tipo de
  newArrayFunctionType → 2 NPE en `CompilerRegressionTest`). Ahora 6/6 OK.
- Hecho: la lista `pzGameProgress` deduplica por label y **no** muestra el punto de park. Para
  cazar fibras parked hace falta un anillo sin dedup (`pzGameTrace`, cap 400) y, si el loop de
  render rota el anillo, las fronteras se leen de la lista deduplicada.
- Error operativo: el navegador empaquetado de Playwright no está instalado. Mejor enfoque: lanzar
  con `executablePath: process.env.CHROME_PATH || '/usr/bin/google-chrome'` más los args swiftshader
  en los scripts de diagnóstico propios (`port/target/diag*.mjs`).
- Error operativo: queda un dev-server colgado en 8092 dentro del sandbox y Playwright dice
  "port already used". Mejor enfoque: `PZ_REAL_TEST_PORT=8093` para la suite real y 8091 para la de
  plataforma, manteniendo `test-results/platform` y `test-results/real` separados.
- Error de cuenta: el handoff decía "3 h 30 min transcurridos"; `grep 'lanzando ronda' supervisor.log`
  + `date` dieron 2 h 33 min. Mejor enfoque: calcular el tiempo restante con esas dos lecturas
  reales antes de decidir "solo cierre documentado".

## 2026-10-28 — Ronda 30
- Error: el handoff de la Ronda 29 afirmaba que `DebugOptionsXml` no tenía anotaciones JAXB. Sí las tiene
  (scan del jar: 58 clases bean con `javax.xml.bind.annotation.*`). Mejor enfoque: antes de asumir “este bean
  no usa JAXB”, correr el scan de anotaciones sobre el jar fijado y citar el conteo.
- Error: `PortUnmarshaller` llamaba `readAllBytes()` dos veces sobre el mismo stream (la segunda lectura ya
  devolvía 0 bytes) y el parseador quedaba con XML vacío. Mejor enfoque: leer una vez en un `byte[]` propio y
  derivar todas las vistas (BOM, charset, snippet) de ese buffer.
- Error: busqué las anotaciones reflectables en `boot.wasm` con el patrón `$$$_impl` y concluí “no se emitieron”.
  TeaVM 0.16 emite `$$_impl`. Mejor enfoque: confirmar el sufijo real con `grep -o 'Xml[A-Za-z]*\$_*impl'` sobre
  el wasm antes de concluir ausencia.
- Error: métodos declarados package-private en `port.runtime` y llamados desde `port.compat` (distinto paquete)
  → `mvn compile` falló dos veces. Mejor enfoque: todo helper cruzado entre `port.compat` y `port.runtime`
  se declara `public` desde el inicio.
- Error: `javap` sin `source scripts/env.sh` (el JDK está en `.toolchain/`) y sin `cd /tmp/tm30` donde están las
  clases extraídas del jar. Mejor enfoque: `source scripts/env.sh` y `cd /tmp/tm30` como prefijo fijo de todo
  `javap` de la ronda.
- Error de observación: `pzGameProgress` **deduplica por label**, así que interpreté “no hay un segundo
  `acquireContextReentrant():entered`” como “el loop no itera”. Eso no se puede deducir de una lista dedup.
  Mejor enfoque: para preguntas de iteración usar un contador por label, no una lista dedup ni un anillo que
  satura (el anillo de 400 quedó lleno de escrituras de `FileOutputStream`).
- Error de atribución: atribuí el `Thread#sleep(J)V:entered` al `renderLoop()` del juego. `javap -c` muestra que
  `renderLoop()` tiene **0** `Thread.sleep` (los 2 están en `lambda$uncaughtException$0()`). Mejor enfoque: antes
  de nombrar la causa de un park, contar las llamadas al método sospechoso en el bytecode del llamador.
- Patrón a evitar: `pgrep -af` inunda tokens por los argumentos largos de los procesos. Usar `ss -ltnp` para
  puertos y `ls`/`stat` para procesos de build.
- Patrón útil (reutilizar): para decidir “¿boot lento o interbloqueo?”, muestrear dos veces el mismo estado
  con 25 s de diferencia y comparar contadores (`progress.length`, `swapCount`, GL total). Es más concluyente
  que leer logs.

## Ronda 31 — error: `MAVEN_OPTS=-Xmx8g` sin `-Xss16m` reventó el compilador TeaVM
- **Error**: `bash scripts/build.sh --with-game` con `MAVEN_OPTS=-Xmx8g` terminó en
  `BUILD FAILURE ... compile-wasm-gc ... StackOverflowError` (341 frames de
  `WasmBinaryRenderingVisitor.render`) después de 6 min de trabajo.
- **Causa**: `scripts/env.sh` define `MAVEN_OPTS="${MAVEN_OPTS:--Xmx20g -Xss16m}"`. Al asignar
  `MAVEN_OPTS=-Xmx8g` se reemplaza el valor por completo y se pierde `-Xss16m`; el goal
  `exec-maven-plugin` renderiza el módulo Wasm con recursion profunda y con el stack por defecto
  (1 MB) del JVM de Maven revienta con SOE.
- **Patrón a evitar**: no reasignar `MAVEN_OPTS` con solo `-Xmx`; sobrescribe la línea completa de
  flags que el entorno necesita.
- **Mejor enfoque**: siempre `MAVEN_OPTS=-Xmx8g -Xss16m` para builds del juego (el límite de RAM
  sigue siendo 8 GiB). Un fallo del compilador por stack no es un error del port: revisar primero
  los flags del entorno antes de tocar código.

## Ronda 31 — error: duplicado en `Set.of` de StartupProgress reventó el goal `compile-wasm-gc`
- **Error**: al agregar observabilidad, escribí `"org.teavm.runtime.EventQueue#offer(Lorg/teavm/runtime/EventQueue$Event;)I"`
  en el bloque nuevo de `EXTRA_METHODS` cuando ya estaba declarado en el bloque del paso b.
  `bash scripts/build.sh --with-game` falló en 16 s con
  `ExceptionInInitializerError ... Caused by: java.lang.IllegalArgumentException: duplicate element: ...`.
- **Causa**: `java.util.Set.of(E,E,...)` lanza `IllegalArgumentException` por elementos duplicados, y el
  conjunto se construye en el inicializador estático del transformer, antes de cualquier análisis.
- **Patrón a evitar**: no duplicar keys cuando se amplían los `Set.of` de `StartupProgress`
  (`EXTRA_CLASSES` / `EXTRA_METHODS` / `METHODS` comparten el mismo espacio de keys `clase#descriptor`).
- **Mejor enfoque**: antes de agregar una key, buscarla en el archivo
  (`grep -n 'key' port/src/main/java/port/compiler/StartupProgress.java`) o usar un `Set` acumulado.
  Este fallo es barato (16 s) y temprano: vale la pena comprobarlo con `mvn -f port/pom.xml compile`
  + un build corto antes de asumir que el instrumentation set es válido.

## Ronda 31 — error: `resume.complete is not a function` (JSFunctor usado como objeto)
- **Error**: en `port/shim/BrowserYield.java` el `@JSBody` llamaba `resume.complete()` sobre el
  `@JSFunctor` pasado como argumento. El timer disparó y tiró `TypeError: resume.complete is not a
  function` (`pageerror` en Chrome), por lo que el `AsyncCallback` nunca se completó y el fiber
  quedó igual de parkado que con `Thread.sleep(0L)`.
- **Causa**: un `@JSFunctor` de TeaVM **es la función JS en sí**, no un objeto con métodos; el
  callback se invoca llamando al objeto (`resume()`), como ya hace `BrowserPersistence`
  (`.then(success, e=>failure(...))`).
- **Patrón a evitar**: inventar la forma del functor JS. Si ya hay un shim propio que pasa un
  `@JSFunctor` a un `@JSBody`, copiar su convención de llamada en vez de adivinar.
- **Mejor enfoque**: `setTimeout(function(){resume();}, ...)`; verificar el tipo de functor leyendo
  un shim existente del repo antes de escribir el `@JSBody`.

## 2026-10-28 — Ronda 32
- Error: `pkill -f 'scripts/build.sh'` mató mi propio build en primer plano porque el patrón coincidió también con la línea de comando del wrapper que lo ejecutaba (y con la llamada que hizo el pkill: rc=143).
- Evitar: usar `pkill -f` con un patrón que aparece en el propio comando/shell de la sesión.
- Mejor enfoque: matizar procesos por PID verificado con `ps -eo pid,etime,args --sort=-rss | grep ...` y matar solo el PID del java/maven, nunca por substring del script.
- Error: lancé el build largo con `setsid ... &` dentro de `exec_command`; al cerrar la sesión murió y el log quedó congelado en `DEPENDENCY_ANALYSIS` (recaída del aprendizaje de la ronda 19).
- Evitar: builds largos en segundo plano sin verificación de vida.
- Mejor enfoque: correrlos en la sesión en primer plano y monitorear con `stat -c '%y' log` en llamadas separadas.
- Error: `--with-game` falló por faltar `PZ_HOME` en el entorno de esa llamada; y reescribí `BrowserProgress.java` con un `cat >>` que duplicó el bloque (dos `}`).
- Evitar: asumir variables de entorno y appending sin releer el archivo.
- Mejor enfoque: exportar `PZ_HOME` en cada comando de build y verificar el archivo final (`tail`) antes de compilar.
- Error: el `pulse(int)` nuevo fue declarado `@JSBody native` y el transformer le inyectó llamadas; TeaVM abortó con `port.shim.BrowserProgress.pulse(I)V: Method is not annotated with org.teavm.interop.Import` porque las llamadas agregadas después de la fase JSO no vuelven a transformarse.
- Evitar: inyectar `InvokeInstruction` directo a un método `@JSBody`.
- Mejor enfoque: envoltorio Java normal (`pulse` → `pulseNative` con `@JSBody`), como ya exige este repositorio para todos los puentes JSO.

## 2026-10-28 — Ronda 32 (segunda mitad)
- Error: `InvocationType` de TeaVM 0.16.0 sólo tiene `VIRTUAL` y `SPECIAL` (verificado con `javap`),
  y no existe `ConstantInstruction` ni `ValueType.booleanConstant(...)`; mi inyección de un flag usaba
  `InvocationType.STATIC` y compilar falló.
  Evitar: inventar enums/clases del modelo de TeaVM.
  Mejor enfoque: para un flag booleano inyectado, usar `IntegerConstantInstruction` + `InvocationType.SPECIAL`
  hacia un método estático propio del shim (misma forma que `BrowserProgress.pulse`), y comprobar el enum
  con `javap -p -classpath .toolchain/m2/org/teavm/teavm-core/0.16.0/teavm-core-0.16.0.jar org.teavm.model.instructions.InvocationType`.
- Error: quité el suspend del yield y la página quedó en busy-loop: Playwright `evaluate` y `screenshot`
  colgaban (`timeout` rc=124) y el log no avanzaba.
  Evitar: asumir que "no suspender" deja correr el event loop de JS. Con fibras TeaVM el fiber activo
  acapara la pila y el loop de JS nunca vuelve.
  Mejor enfoque: medir con un diag que envuelva `page.evaluate`/`screenshot` en `Promise.race` con timeout
  (`port/target/diag64.mjs`) y volcar `console`+`pageerror` antes de evaluar; así se distingue
  "crasheó" de "nunca devuelve el control".
- Error: corrí `python3 -m unittest discover -s tests/python` sin `source scripts/env.sh` → 2 errores por
  herramientas ausentes.
  Evitar: suites del repo sin el toolchain local en PATH.
  Mejor enfoque: `source scripts/env.sh` primero (10 tests OK).

## 2026-10-28 — Ronda 33
- Error: `apply_patch` con un bloque duplicado por error dejó líneas de texto Java fuera de Python al final de `scripts/prepare_teavm.py`; un fix posterior con `python3` recortó mal el ancla (`"    main()"` aparecía dentro de un string) y luego un script de inserción rompió otro parche (`patch_monitor`). La archivo quedó con `IndentationError`.
- Causa y patrón a evitar: parches sucesuales sobre un archivo ya dañado, y buscar anclas que no son únicas (líneas que también aparecen dentro de cadenas triple-quoted).
- Mejor enfoque: cuando un archivo de generación queda sintácticamente roto, restaurar desde `git show HEAD:<path>` y volver a aplicar TODO el cambio en una sola pasada con anclas verificadas por `count(...)==1` antes de insertar; después `ast.parse` como puerta antes de correr el script.
- Error: el primer diagnóstico dentro de `Fiber.popInt` construyó el mensaje con `StringBuilder`; `StringBuilder.append` es él mismo una corrutina en la classlib fijada, así que el diagnóstico recursó `popInt <- describe <- append` y saturó la pila de JS (`RangeError: Maximum call stack size exceeded`) en lugar de reportar los contadores.
- Mejor enfoque: dentro de machinery de corrutinas (`popInt`/`push`/prologue) no usar APIs suspendibles: emitir números con `@JSBody` directo a `console.log` y lanzar un mensaje fijo.

## 2026-10-28 — Ronda 38 (primera mitad)
- Error repetido: `MAVEN_OPTS="-Xmx8g"` **sin `-Xss16m`** → `build-round38-game.log` terminó con
  `java.lang.StackOverflowError` en `WasmBinaryRenderingVisitor.render` durante `compile-wasm-gc`,
  después de ~6 min. `scripts/env.sh` sólo aplica su default `-Xmx20g -Xss16m` si `MAVEN_OPTS` viene vacío.
  Evitar: reasignar `MAVEN_OPTS` con sólo `-Xmx` (borra el `-Xss` que el render del módulo necesita).
  Mejor enfoque: `MAVEN_OPTS="-Xmx8g -Xss16m"` siempre (ya estaba documentado en Ronda 31; se volvió a
  cometer porque el comando se escribió de memoria).
- Error de observación: el anillo de 160 eventos de `port/shim/BrowserMonitor` se llenó de `phase 5`
  (`monitorExit` es el camino más caliente del boot), así que las fases 3/6/7/8/13/14 fueron desplazadas
  y conclusiones como "nunca se ofreció el drenaje diferido" eran infundadas: un anillo desplazado no
  demuestra que un evento no ocurrió. Mejor enfoque: contadores acumulados por fase (`globalThis.pzMonitorCounts`)
  + el último evento por fase (`pzMonitorLast`), y excluir del anillo las fases masivas (5).
- Error de medición: `wasmGCIdentity()` satura (todos los monitors muestran `-1` o `2147483647`), así que
  `obj` no sirve para identificar el objeto monitoriado. Mejor enfoque: discriminar por las frames de Java
  del llamador (el stack corto ya capturado), no por el id del objeto.
- Error de entorno: un `node port/target/diagNN.mjs > log &` dentro de `exec_command` **no sobrevive** al
  cierre de la sesión (el log queda en 0 bytes y no hay proceso). Evitar: creer que el diag quedó corriendo.
  Mejor enfoque: correr el diag en primer plano con `yield_time_ms` y luego `write_stdin` en la misma sesión.

## 2026-10-28 — Ronda 38 (segunda mitad)
- Error de inferencia: concluí que el solicitante contendido “se encola en `enteringThreads` y nadie lo
  drena” leyendo sólo el código fuente de `TObject.monitorEnterWait(...)`. La medición (fases 15/16,
  `diag85d.log`) mostró `15:0:1` y `16:0:1` con `entering:0`: el body `monitorEnterWait_0` corre **sólo
  su primera línea** porque `Fiber.suspend()` parka la fibra antes de continuar; el `add()` a la cola y
  el `EventQueue.offer` diferido de la fix (b) nunca se ejecutan (y `pzAsync` contó 2 offers en todo el
  run, ningún drenaje). Evitar: razonar sobre el body de un `@Async` como si fuera código secuencial.
  Mejor enfoque: instrumentar antes y después de cada posible punto de suspensión (dos fases contiguas)
  y leer los contadores acumulados; si la fase posterior cuenta 0, la suspensión ocurrió en el medio.
- Observación útil: el anillo `pzMonitorLog` + `pzMonitorCounts` por fase permitió ver la secuencia
  `9 → 4(waitImpl) → 9(owner=0) → 15 → 16` sin saturar el anillo (fase 5 excluida), y los frames de
  Java del discriminaron (`RenderContextQueueItem::invoke`, `Object$monitorEnterWait$0::run`).

## 2026-10-29 — Ronda 40 (recursos de arranque / initFonts)
- Error: derivé la lista del pack de fuentes con `re.findall(r'fnt\s*=\s*(\S+)')` sobre `media/fonts/EN/fonts.txt`.
  El archivo termina las entradas con coma (`fnt = zomboidCode.fnt,`), así que los nombres capturados llevaban `,`
  y ninguna ruta resolvía: `startup.txt` quedó en 2 archivos y el `FileNotFoundException` persistió.
  Mejor enfoque: capturar `[^,\s]+` y **verificar el conteo de archivos y bytes** después de escribir la lista,
  no asumir que el regex produjo rutas válidas.
- Error: escribí la selección con `Path('build/real-browser/startup.txt')` habiendo hecho `cd` al PZ_HOME de la
  copia pinneada; la ruta relativa apuntó fuera del repo y el `write_text` falló (y hubiera corromper otra ruta
  si existiera). Mejor enfoque: toda escritura de artefactos con `ROOT = Path(cwd del repo)` absoluto.
- Error de atribución: tras montar `media/fonts/EN/1x/` + aliases, el `FileNotFoundException` seguía con el mismo
  stack (`FileInputStream::<init> ← AngelCodeFont::<init>_0 ← TextManager::Init`) y lo leí como "los aliases no
  funcionaban". La causa real eran otras rutas: `getFontFilePath` **devuelve sin verificar** la última cadena
  `media/<file>`, y `TextManager.Init` también pide `media/fonts/zomboidNormal<N>.fnt` (N=11..25) que no estaban
  en el pack. Mejor enfoque: instrumentar el VFS (proxy de `files.has`/journal con `op`+`path`+`value`) y leer las
  cadenas `makeConcatWithConstants` reales de la constante del bytecode, en lugar de Deducir por el stack.
- Hecho útil (reutilizar): `TextManager.getFontFilePath(lang,sizeDir,fileName)` usa las plantillas del constant
  pool `media/fonts/{1}/{1}/{1}`, `media/fonts/{1}/{1}`, `media/fonts/EN/{1}/{1}`, `media/fonts/EN/{1}`,
  `media/fonts/{1}`, `media/{1}`; sólo las primeras prueban contra el file map, la última se devuelve ciega.
  Por eso el pack debe montar el nombre exacto también en `media/` (alias) y no basta `media/fonts/EN/1x/`.
- Enseñanza sobre "nunca simular éxito": `mainfont_0.png`, `mainfont2_0.png` y
  `zomboidDialogue.bmfc_0.png` **no existen en la copia pinneada** (verificado con `find`). `AngelCodeFont` sólo
  loguea `failed to load page 0 texture` y continúa; no hay que inventar esos PNG ni considerarlos bug del port.

## 2026-10-29 — Ronda 42 (texturas GL11 y contexto GLFW)
- Error de build: los wrappers nuevos de `port/src/main/java/port/shim/GL11.java` usaban `ByteBuffer`
  (para el arena de `glGetTexImage`/`glTexSubImage2D`) sin `import java.nio.ByteBuffer;`. El build con
  juego falló tras 6 min de TeaVM por un error de compilación que se veía en 5 segundos de `javac`.
  Mejor enfoque: al agregar un tipo al shim, escribir el import en el mismo parche y correr
  `mvn -q -pl port compile` (o `javac` del archivo) antes del build completo.
- Error de fuente: escribí tokens OpenGL de memoria (valores hex) y varios estaban mal. Verificar siempre
  con `javap -constants -classpath $PZ_HOME/projectzomboid.jar org.lwjgl.opengl.GL11` (decimales) y cruzar
  con los constantes WebGL2 del navegador; en WebGL2 los constantes están en el **prototipo**
  (`Object.entries(gl)` devuelve 0 claves, el prototipo tiene 553), así que una tabla valor→nombre debe
  recorrer la cadena de prototipos o `webglConstantName()` devuelve `undefined`.
- Error de edición: un `apply_patch` en el medio de `port/runtime/gl.mjs` duplicó un método; hubo que
  excindir el duplicado. Mejor enfoque: un parche por región coherente y después `node --check` +
  `grep -c 'nombreDelMétodo('` para detectar duplicados.
- Error de entorno de pruebas: para testear `GLBridge` en Node el canvas falso necesita
  `getContext: () => fake` porque el constructor de `GLBridge` lo llama; asignar `instance.context`
  después del constructor prueba otra cosa.
- Error de inferencia sobre el bloqueo: el canvas negro 95 % no era "el renderer no corre". Los
  contadores de `StartupProgress` mostraban `initFonts` sin `returned` y 48 raises de
  `GL11C.nglGenTextures`; la cadena real era `generateHwId` → raise → nunca `ImageData.dispose()` →
  `DirectBufferAllocator.getBytesAllocated() > 50 MB` → `waitFileTask` dormía para siempre.
  Mejor enfoque: leer `X:entered` vs `X:returned` en los contadores y recién después abrir bytecode.
- Error de diseño del shim GLFW: `checkedHandle` rechazaba handle `0`, pero
  `org.lwjglx.opengl.Display.releaseContext()` llama `glfwMakeContextCurrent(0)` (bytecode: `lconst_0`)
  y `RenderThread.releaseContextReentrant()` lo ejecuta cada frame. Rechazar 0 abortaba el render loop
  con `IllegalArgumentException: Invalid browser window handle: 0`. GLFW define `NULL` como *detach*:
  el backend ahora tiene `contextCurrent`/`detachContext()` y `getCurrentContext()` devuelve 0.
  Mejor enfoque: antes de validar un handle, leer quién lo llama en el bytecode del juego/LWJGLx.
- Observación útil: tras el puente de texturas, `glTop` muestra `createTexture/texImage2D/bindTexture` 66 y
  `texParameteri` 264 con `glThrown: []`; la textura deja de ser el cuello. Los bloqueos siguientes son
  `System.loadLibrary("Lighting64")` → `LightingJNI.init` → `Thread.sleep(3000)` → `System.exit(1)`
  (bytecode verificado) y luego `RenderThread.renderLoop` → `System.exit(0)` cuando
  `GameWindow.gameThreadExited` es verdad. Por eso `JdkSupport.exit/loadLibrary` ahora registran
  `pzRuntime.exits` y `pzRuntime.missingLibraries` sin cambiar el fallo honesto.
- Error de API del transformer: en el transformer nuevo usé `org.teavm.model.MethodNode` como tipo del
  método iterado en `transformClass`. Ese tipo no existe en teavm-model 0.16.0 (`cannot find symbol`);
  el que devuelve `cls.getMethods()` es `org.teavm.model.MethodHolder`. Mejor enfoque: copiar el patrón
  de `BulletNativeCompatibility` (usa `var method` y `MethodDescriptor.get(method)`) y recién escribir
  tipos explícitos cuando el compilador lo pida.
- Patrón a evitar: tratar una librería nativa ausente como "se puede ignorar". `LightingJNI.init`
  llama `System.exit(1)` cuando `loadLibrary` falla; nuestro `JdkSupport.exit` lanza (honesto) y eso
  mata el MainThread → el RenderThread cierra el loop y hace `System.exit(0)`. Cualquier librería cuyo
  `init` tiene `exit` en el catch (verificado con un regex sobre los dumps: LightingJNI y PZSQLUtils)
  necesita un módulo real, aunque sea parcial, registrado en `pzRuntime.loadedLibraries`.
- Error de transformer (ronda 42): declaré el método destino con `new MethodDescriptor("configure",
  ValueType.VOID)` y TeaVM respondió `ERROR: zombie.iso.LightingJNI.configure(F)V: Method
  port.natives.Lighting64.configure()V was not found`. `MethodDescriptor(name, ValueType...)` toma los
  N-1 primeros como **parámetros** y el último como **retorno**, así que omitir los parámetros genera
  un descriptor `()V`. Mejor enfoque: al reenviar una native, reconstruir el descriptor destino con la
  misma aridad que la native (`configure(F)V → (FLOAT, VOID)`, `DoLightingUpdateNew(JZ)V → (LONG,
  BOOLEAN, VOID)`) y verificarlo con el primer build; los forwards sin argumentos (Bullet) no revelan
  este detalle.

## 2026-10-29 — Ronda 43 (módulos PopMan/Clipper/Pathfind + SQLite)
- Error de edición: dentro de un `Map.of(...)` anidado cerré `Map.ofEntries(...)` sin el paréntesis final y el
  build con juego falló **después de 6 minutos de TeaVM** por algo que `javac` detecta en 3 segundos.
  Mejor enfoque: `mvn -q -f port/pom.xml compile` inmediatamente después de cada edición de un transformer,
  antes de lanzar `scripts/build.sh --with-game`.
- Error de diseño: escribí un `port/runtime/jdbc/SqliteDriver.java` propio que `implements java.sql.Driver`.
  En el build JVM compiló mal y en Wasm no tiene sentido: `JdkCompatibility` sustituye el contrato
  `java.sql.*` por `port.compat.java.sql.*`, así que un `Driver` propio no se registra donde mira el
  `DriverManager` compat, que ya resuelve `jdbc:sqlite:` contra `BrowserConnection` (SQLite Wasm 3.54.1).
  `org.sqlite.JDBC` es inalcanzable en game-main (sólo `PZSQLUtils` usa `DriverManager`).
  Mejor enfoque: borrar el driver y reemplazar el cuerpo de `PZSQLUtils.init()` por un bootstrap propio que
  valide `pzRuntime.sqlite.version` y falle honesto si falta — nunca simular un driver.
- Error de edición: un `cat >>` para agregar un método al final de `NativeModuleCompatibility.java` dejó una
  `}` extra (el archivo ya cerraba la clase). Mejor enfoque: para editar el interior de un archivo existente
  usar una cirugía con ancla única (`python3` + `content.count(anchor) == 1`) y recompilar, no append al EOF.
- Error de estado compartido en `NativeRegistry`: `register(library)` guardaba `r.natives[library] = true` y
  `publish(library,stage,counter)` hacía `s.calls[counter]` sobre ese booleano. En JVM el código es inocuo, pero
  en WasmGC el JS se ejecuta de verdad y `Clipper.init → n_init → Clipper2.init → publish` abortó el MainThread
  con `TypeError: Cannot read properties of undefined (reading 'init')` (visto sólo en el DebugLog del juego, que
  lo reenvuelve en `RuntimeException`). Mejor enfoque: que el registro cree siempre `{stage:'registered',calls:{}}`
  y que todo publish verifique `typeof s === 'object'`; un diagnóstico JS no puede asumir la forma que escribió
  otra JSBody.
- Técnica que funcionó: el DebugLog del juego tiene la cadena de `Throwable$FakeClass.fakeMethod(<offset>)`;
  `python3 scripts/wasm_stack.py port/target/web/game/boot.wasm <offsets>` traduce los offsets a nombres reales
  de función (`zombie.vehicles.Clipper::n_init ← port.natives.Clipper2::init`). Con eso el bloqueo se resuelve en
  un comando, sin speculation.

## Ronda 49 — errores y patrones
- Error: `(globalThis.pzRuntime ??= {}).missingPaths = []` → `SyntaxError` en Node. `??=` no puede ser
  objetivo de una propiedad en la misma expresión de esa forma. Patrón a evitar:Assignment encadenada sobre
  un logical-assignment. Mejor enfoque: `const runtime = globalThis.pzRuntime ?? (globalThis.pzRuntime = {});`
  y luego asignar propiedades.
- Error: `Path(home/'media').rglob('*')` ya devuelve rutas **con** prefijo `media/`, pero el tamaño se medía
  en `home/'media'/name` (duplicando el prefijo) → todos los shaders daban tamaño 0 y `pack.py` fallaba.
  Patrón: no re-preponer el prefijo cuando el glob ya lo incluye. Mejor enfoque: medir `(home / name)` y
  verificar con `assert (home/name).exists()` antes de escribir el listado.
- Error (arreglado): tokens de `pname` deducidos de memoria — `0x8B81` como SHADER_TYPE, y `0x8B85/0x8B86/
  0x8B87/0x8B8D` como DELETE/INFO_LOG/COMPILE de shader. Los reales son `0x8B80 DELETE_STATUS, 0x8B81
  COMPILE_STATUS, 0x8B84 INFO_LOG_LENGTH, 0x8B4F SHADER_TYPE, 0x8B88 SHADER_SOURCE_LENGTH`.
  Mejor enfoque: `javap -p -c` de la clase del juego y leer los enteros reales del constant pool
  (`ShaderProgram` usa 35716; `ShaderBufferData$BufferUtility` 35718/35719). Nunca deducir tokens GL de memoria.
- Error: `pack.py --max-bytes` default 32 MB rechazó `UI2.pack` (54,6 MB) → el re-empacado se cortaba.
  Mejor enfoque: subir el límite explícitamente (80 MB) y verificar el total montado contra el presupuesto VFS
  (256 MB) antes de publicar `index.json` (que se escribe último para que el index nunca apunte a blobs incompletos).
- Entorno: `PZ_HOME` en `scripts/env.sh` sigue apuntando a `/datos/Projects/pz-web` (stale). Patrón a evitar:
  usar `PZ_HOME` del env tal cual. Mejor enfoque: `export PZ_HOME=/home/agente47/pz-pinned/25485521/projectzomboid`
  en cada comando que la use (pack, inventarios, alias_index).
- RAM: con Strata ~19 GB y swap casi lleno, `free -g` **antes** de cada build/navegador y nunca build + test
  de navegador en paralelo (build ~7 min foreground, test ~2 min).

## Ronda 49 — errores y patrones (continuación 14:30)
- Error: `MAVEN_OPTS=-Xmx8g -Xss16m bash scripts/build.sh` → `/bin/bash: -Xss16m: command not found`,
  exit 127 y el build nunca empezó (la variable debía quedar dentro del comando). Patrón: prefijo de
  variable con dos tokens separados por espacio no es una asignación válida. Mejor enfoque:
  `export MAVEN_OPTS="-Xmx8g -Xss16m"` y recién después lanzar `bash scripts/build.sh`.
- Error: el build `--with-game` (14:22→14:30) murió en `port.CompilerRegressionTest` sin escribir
  `EXIT=` en el log: con Strata ~19 GB y swap 14/15 usados, el JVM de tests/TeaVM fue matado por OOM
  (sin registro en dmesg/journalctl accesible). Patrón: fiarse de que un log existente implica un build
  vivo. Mejor enfoque: verificar `ps -C java` y el tamaño/mtime del log antes de asumir progreso; ante
  OOM reintentar con `-Xmx6g` y registrar el intento.
- Error de diagnóstico: asumir que el trap `ScriptManager::createZedDmgMap` se arregla montando
  `media/scripts` era una hipótesis, no una causa. Verificado en bytecode: `searchFolders(URI,File,List)`
  sólo acumula archivos terminados en `.txt` y `getModule("Base")` devuelve `moduleMap.get("Base")`
  (null si ningún `.txt` con `module Base` fue parseado). La copia pinned tiene 1004 `.txt` de scripts
  (7 MB) con `module Base`, y `media/lua/common` (items.lua) NO existe en esa instalación.
  Mejor enfoque: montar el prefijo, y medir en el diag el resultado real (`mediaDirs`, `missingPaths`)
  en vez de dar por hecho que el null desaparece.

## Ronda 52 — errores y patrones
- Error mío grande: di por hecho que `texture2x=false` en `options.ini` haría que el juego montara los
  packs 1x. Verificado en el bytecode pinned (`javap -p -c zombie.core.Core`):
  `getOptionTexture2x()` es `iconst_1; ireturn` (hardcodeado), así que `Core.tileScale` es siempre 2 y
  los `Tiles1x/Overlays1x/JumboTrees1x/Tiles1x.floor` que agregué al pack nunca se leen.
  Patrón a evitar: inferir el comportamiento de un getter por su nombre o por el campo que parece usar.
  Mejor enfoque: antes de diseñar un fix sobre "lo que el juego pide", desensamblar el getter y leer el
  bytecode; si devuelve una constante, la config es irrelevante.
- Error: `MethodDescriptor("defineVehicleScript", ValueType.VOID)` en `BulletNativeCompatibility.FORWARDED`
  declaró 0 parámetros y TeaVM falló 5 minutos después con
  `Method port.shim.BulletLibrary.defineVehicleScript()V was not found`.
  `MethodDescriptor(String, ValueType...)` toma `ValueType...` = **parámetros + resultado**, no sólo el resultado.
  Patrón a evitar: escribir el descriptor del bridge "de memoria". Mejor enfoque: replicar la firma completa
  (`object("java.lang.String"), arrayOf(FLOAT), VOID`) y comprobar con un probe JVM mínimo
  (`ClassHolder.getMethod(...)` != null) antes de lanzar el build de 6 minutos.
- Error: `std::string`/`std::vector` en `port/pzbullet/pzbullet_wrapper.cpp` -> wasm-ld con símbolos libc++
  sin definir (`__init_copy_ctor_external`, `__libcpp_verbose_abort`) porque el módulo se compila
  `-fno-exceptions` y linkea sólo `-lc++abi`. Patrón: STL en un módulo Emscripten mínimo.
  Mejor enfoque: arrays C de tamaño fijo y buffers propios del módulo (nunca punteros foráneos retenidos).
- Error: cwrap de Emscripten con tipo `'array'` marshala **bytes** por HEAP8 -> ancho de elemento incorrecto
  para `float[]`. Mejor enfoque: scratch exportado por el módulo + `Float32Array` vista sobre
  `HEAPU8.buffer`, recreada en cada llamada porque `ALLOW_MEMORY_GROWTH` desdetach las vistas.
- Error: lanzar el build con `&` (background) -> el proceso fue matado, el log quedó sin `EXIT=` y sin
  proceso java. Mejor enfoque: correr el build foreground dentro de la sesión de herramienta viva y
  pollear con `write_stdin`; el log debe terminar con `EXIT=<code>`.
- Error: en `startup_extra.py` el filtro de `UI_FILES` comparaba `name[len('media/ui/'):]` contra rutas
  relativas `ui/cursor_*.png` -> 0 archivos agregados silenciosamente. Patrón: agregadores que imprimen
  "Appended N" sin verificar N por categoría. Mejor enfoque: imprimir conteos por categoría y verificarlos.
- Error: `ProgramEmitter.invoke` **no** pasa los locales del método compilado; un bridge que conserva la
  firma del juego recibía 0 argumentos. Mejor enfoque: construir el array de `ValueEmitter` con
  `emitter.var(i+1, method.parameterType(i))`.
- Error: el test Node de bullet usaba `fetch(url)` con `file:` sin `fileURLToPath`, y un IndexedDB fake debe
  disparar `oncomplete` **asincrónicamente** (`queueMicrotask`) porque el VFS asigna `transaction.oncomplete`.
- Error de presupuesto: `alias_index.py` y `boot_options.py` tenían cada uno su `256*1024*1024` hardcodeado;
  al reempaquetar con Tiles2x el alias reventó con "Alias index exceeds the VFS memory budget" y el index
  quedó a medio actualizar. Patrón: límites duplicados en varios tools. Mejor enfoque: un único
  `tools/pack/budget.py` compartido y sincronizado con el default de `port/runtime/vfs.mjs`.
- Error de despliegue: cambié `port/runtime/vfs.mjs` (presupuesto 512 MB) y probé en Chrome sin copiar a
  `port/target/web/runtime/`; el servidor sirve esa copia, así que el diag dio
  `loader-failed: Pack preload exceeds VFS memory budget`. Patrón: editar el fuente y asumir que el
  servidor ya ve el cambio. Mejor enfoque: `cp port/runtime/*.mjs port/target/web/runtime/` (o rebuild)
  antes de cada prueba de navegador, y verificar con `grep` en el archivo servido.
- `mount()` del VFS siempre hace `data.slice()`: montar un pack de 321 MB duplicaba el blob y nunca entraba
  en el presupuesto. Mejor enfoque: `mountView()` con `blob.subarray()` (el blob queda vivo por la vista) y
  `readFile()` sigue copiando, así el juego no puede corromper el pack.
- Error de la ronda 52: un `page.evaluate` grande (DebugLog de 73 KB + mapa de shaders + contadores) con un
  `Promise.race` de 30 s -> el diag murió con `{timeout:true}` y `EXIT=1` **sin ninguna evidencia** del
  avance real. Patrón: observar todo el estado de una pestaña con 422 MB montados en un solo evaluate.
  Mejor enfoque: divides la observación (phase/counts primero, GL y log después), races de 120 s, y recortar
  el texto del log dentro de la página antes de serializarlo.
- Error de instrumentación: `glTexImage2D failed for texture 336 level 0 1024x1024` no decía el código de
  WebGL2 ni si `data` era `null`, así que la primera hipótesis fue OUT_OF_MEMORY (falso). Patrón: validar con
  `getError()` y lanzar un mensaje sin el error ni los tokens. Mejor enfoque: guardar
  `gl.lastUploadFailure = {error, tokens, sized, data}` y exponerlo en el diag; así se probó que era
  `INVALID_VALUE` con `data=null` (allocación desktop sin datos que WebGL2 rechaza).
- Error propio parcheando con `python3 str.replace()` sobre `diag155.mjs`: reemplacé un string que contenía
  el cierre de `Promise.race([...])` y rompí la aridad (dos `SyntaxError`). Mejor enfoque: parchar archivos
  JS con `apply_patch` y validar siempre con `node --check` antes de correr el navegador.

## Ronda 60 — errores y patrones
- Error del conversor `.x`: `nFaces` se trató como un array plano de `nFaceVertexIndices` y las caras
  salían mal contadas. Patrón a evitar: asumir el layout de un formato sin leer su plantilla.
  Mejor enfoque: leer la estructura del template (`Material`, `Mesh`, `Faces`, `MeshHelper`) y
  verificar vértices/caras contra valores conocidos del `.x` (MaleBody 617/916, FemaleBody 660/984).
- Error de regeneración: `python3 tests/prepare_real.py` reescribió `startup.txt` y **perdió las
  entradas de fuentes** que la ronda 40 había agregado a mano (`build/` está git-ignorado, así que
  no son reconstruibles desde el repo). El arranque murió en `GameWindow#initFonts()`.
  Patrón: datos requeridos escritos a mano en rutas ignoradas. Mejor enfoque: derivar cada lista con
  una herramienta commiteable (`tools/pack/font_files.py`) y regenerar siempre desde `PZ_HOME`.
- Error de montaje: el **listing** `media/fonts/EN/fonts.txt` también debe montarse. Su ausencia es
  silenciosa: `FontsFile.read()` captura `FileNotFoundException` y devuelve `false`, el mapa de
  fuentes queda vacío, cada `UIFont` conserva `AngelCodeFont == null` y el primer `getFontHeight()`
  produce un NPE en `AngelCodeFont.getLineHeight` **lejos** de `initFonts` (aparecía en
  `Core.EndFrameUI` via `ExceptionLogger.render`). Patrón: interpretar un error tardío como causa
  tardía. Mejor enfoque: cuando una carga opcional captura su propia excepción, montar también el
  archivo de índice que la habilita y comprobar su presencia por HTTP.
- Error del shim GL15: `unmap()` liberaba el bloque del arena. `GLVertexBufferObject` cachea su
  `ByteBuffer` y LWJGL llama `APIUtil.apiGetMappedBuffer(old_buffer, ...)` -> `memAddress(old)`, que
  lanza para una view liberada (116 `IllegalStateException: Buffer allocation was freed` por frame).
  Además el arena es bump allocator: liberar/reasignar 2 bloques por frame crecía ~131 KB/frame.
  Mejor enfoque: mantener el bloque del último map por target en `reusable` y reutilizarlo cuando
  `size >= size` pedido (semántica de escritorio: la dirección sigue estable entre maps).
- Error de proceso: lanzar Playwright con `&`/`setsid` dentro del sandbox mata el proceso y deja el
  log en 0 B. Mejor enfoque: correr el diag en foreground con `tty=true` y seguirlo con `write_stdin`
  en trozos de 120-250 s.
- Error de invocado: `scripts/build.sh --with-game` aborta con "requires PZ_HOME and a matching
  inventory" si `PZ_HOME` no está exportado (no figura en `scripts/env.sh` con el valor real).
  Mejor enfoque: exportar `PZ_HOME` junto con `JAVA_HOME` antes de cada build.
- Error de observación (ronda 60): se asumió que el arranque colgaba en el bucle
  `while(isLoadingAnimationMeshes())` de `ModelManager#initAnimationMeshes()` sin evidencia. Con
  `ANIM_CLASSES` instrumentado, el anillo muestra `ModelManager#create()V:entered` como último límite
  y `initAnimationMeshes` **nunca entra**: el bloqueo estaba antes. Patrón: inferir el punto de bloqueo
  leyendo el fuente del juego en vez de observarlo. Mejor enfoque: instrumentar la clase justo antes de
  la hipótesis y dejar que el PROGRESS diga qué llamada no retorna.
- Error de instrumentación: `StartupProgress.FONT_CLASSES` estaba declarado pero el gate de
  `transformClass` sólo lee `METHODS`/`EXTRA_METHODS`/`CLASSES`/`EXTRA_CLASSES`/`own()`, así que las
  clases de fuentes nunca se instrumentaron (rondas 42+ de análisis sobre una sonda muda).
  Mejor enfoque: agregar el set al gate y verificar el efecto en `port/target/web/game/pulse-map.json`
  (1929 -> 2173 labels; 0 -> 244 de `skinnedmodel`).
- Verificación de labels: `grep -c` sobre `boot.wasm` devuelve 0 para labels que sí existen (la data
  section no es texto continuo). Fuente de verdad: `pulse-map.json`.
- Error de invocado: `mvn -f port/pom.xml compile` sin `-Dmaven.repo.local="$PZ_WEB_ROOT/.toolchain/m2"`
  falla con "Could not create local repository at /home/agente47/.m2/repository" (y `-o` agrava el
  silencio). Mejor enfoque: usar `scripts/build.sh` (fuente de `scripts/env.sh`) o repetir el flag.
- Señal nuevo a perseguir, no ignorar: `WebGL: INVALID_VALUE: clear: invalid mask` aparece en el
  console **justo** después de `UI_Loading_ModelsAnimations`; una línea de console WebGL después de la
  etapa de carga es evidencia directa del shim, no ruido.

## Ronda 63 (2026-10-30) — dos causas reales bajo un mismo síntoma
- Error de cobertura de transformer: `BufferUtilsCompatibility` sólo reescribía
  `org.lwjglx.BufferUtils` (LWJGL2) y el juego llama **`org.lwjgl.BufferUtils`** (LWJGL3) desde
  `VertexBufferObject$VertexArray.<init>`. El `ByteBuffer` quedaba una vista heap de TeaVM y
  `MemoryUtil.memAddress` lanzaba `UnsupportedOperationException: Arena address requires a
  registered/direct ByteBuffer view` dentro del render item de `ModelLoader#applyToMesh`, dejando
  `ModelMesh#isReady()` false para siempre (bucle infinito en `ModelManager#initAnimationMeshes`).
  Patrón a evitar: asumir que una sustitución de una librería cubre las dos familias de paquetes.
  Mejor enfoque: en los transformers de sustitución usar `Set.of("org.lwjglx.X","org.lwjgl.X")` y
  comprobar contra el constant pool del juego (`javap -c` o vineflower sobre la clase concreta), no
  contra el nombre que usa nuestro shim.
- Error de verificación: `grep -c "Arena address"` sobre el DebugLog **no** sirve para comprobar que
  un fix funcionó (las líneas tienen prefijo de timestamp y tabuladores). Fuente de verdad: el
  *tally* del DebugLog que la sonda ya calcula (`DEBUGLOG_FINAL.tally`) + los contadores de
  `entered/returned` de la función implicada.
- Error de observación: la sonda `models_poll.mjs` marcaba `RESULT stalled` en falso porque el "key"
  incluía contadores del loop por frame (`Display#update`, `isLoadingAnimationMeshes`,
  `ModelMesh#isReady`, `DoLoadingText`) que suben ~33/s. Patrón: un detector de estancamiento debe
  ignorar el ruido por frame (lista `LOOP_NOISE`) y mirar sólo contadores que avanzan cuando el
  arranque avanza.
- Error de proceso: `java` y `javap` no están en `PATH`; sólo `.toolchain/jdk/bin`. Un `java -jar`
  fallido en medio de una cadena deja la carpeta de salida vacía y parece un fallo del decompilador.
  Mejor enfoque: exportar `JAVA_HOME` y usar `$JAVA_HOME/bin/...` en cada comando.
- Error de invocación: `unzip` de `projectzomboid.jar` con un patrón inexistente (`zombie/core/utilities/*.class`)
  abortaba la cadena con exit 11 y el `&&` cortaba el resto. Mejor enfoque: separar el `unzip` del
  resto con `;` y verificar `ls` después.

## Ronda 79 (Code, 31/10) — media bajo demanda y camino a la partida
- Error de proceso: las tareas en segundo plano del harness mueren a los 10 min por defecto; el dev server
  de 8098 murió a mitad del arranque y el juego vio `XMLHttpRequest ... Failed to load` (parecía un bug
  del VFS). Mejor enfoque: lanzar server y driver con timeout explícito largo (2 h).
- Error de proceso: `pkill -f "node tools/diag/drive.mjs"` dentro de un `bash -c` cuyo texto contiene el
  patrón mata al propio shell (exit 144). Mejor enfoque: `pgrep` + `kill PID` o el comando `quit` del driver.
- Error de proceso: dos drivers compartiendo `/tmp/pzdrive` se pisan `cmd.txt`/`out.txt` (el viejo seguía
  en un `until` largo y no leyó `quit`). Mejor enfoque: un directorio por corrida (`/tmp/pzdN`).
- Error de proceso: `scripts/build.sh` borra `port/target/web/game/boot.wasm` al empezar; las direcciones
  `fakeMethod(Throwable.java:N)` de una corrida solo se simbolizan con SU wasm. Mejor enfoque: copiar
  `boot.wasm` a `/tmp/boot-<ronda>.wasm` antes de compilar y usar `tools/diag/wasm_addr.mjs`.
- Patrón: exponer todo `media/` cambia caminos del juego: con `models_X/*.x|.fbx|.glb` visibles el
  juego elige jassimp64 (nativa ausente) en vez del `.txt` montado. El manifest remoto excluye
  `models_X/` y `anims_X/`.
- Patrón: `children()` lineal sobre todo el VFS es O(N) por llamada; con 60k rutas es inviable.
  Índice padre->hijos mantenido por subclases de Map/Set (`ChildIndex`).
- Patrón: en Wasm GC sin `setStrict(true)` un NPE es una trampa que mata la fibra; el juego cuenta con capturar NPE
  (Kahlua pcall). Con strict, la traza muestra `WasmGCSupport::npe` y el juego sigue como en la JVM.
- Patrón: firmas de nativas asumidas por nombre de parámetro (PopMan64 `n_init`) estaban mal; verificar SIEMPRE el
  caller con javap (`ZombiePopulationManager.init` pasa minX,minY,width,height).
- Patrón: una nativa ausente durante la carga de scripts (RagdollScript.toBullet) no se ve hasta mucho después:
  `IsoWorld.init` lanza "script load errors". Buscar las excepciones de `PostLoadScripts` en el log de arranque.
- Proceso: con el límite de 1–2 h de las tareas del harness, reiniciar el dev server ANTES de una corrida larga
  (~17 min hasta IngameState) y no a mitad.

## Ronda 80 (Code, 31/10) — Clipper y el siguiente freno
- Patrón: para una nativa con formatos de `ByteBuffer`, el bytecode de los consumidores da el layout, pero NO la
  semántica fina (regla de relleno, escala, qué borra `clear()`, orientación). Los símbolos de la `.so` (aquí
  Clipper2 C++ + earcut sin strip de nombres internos) y `objdump` local la resuelven en minutos: `FillRule=2`
  (Positive) estaba en un `mov $0x2,%edx` antes de `ExecuteInternal`. No versionar nada de eso.
- Error evitado: suponer que `clear()` también borra el resultado. La librería sólo limpia las entradas;
  `generateTriangulatePolygons` depende de leer el árbol anterior.
- Patrón: Clipper2-java y Clipper2 C++ no comparten el orden del enum `JoinType` (Java: Square, Round, Miter,
  Bevel; C++/juego: Square, Bevel, Round, Miter). Mapear por nombre, nunca por ordinal.
- Patrón: nativas de instancia (`private native void newInstance()` que escribe `address` por JNI): el transformer
  agrega un campo propio a la clase del juego con el motor y reenvía con `invokeVirtual`; sin mapas globales.
- Proceso: el orden del driver importa; `until X` sólo sirve si X está instrumentado (IngameState/GameLoadingState
  no lo están). Usar `wait` fijo o `counts GameStateMachine#update`.
- Proceso: un `shot` durante la carga del mundo puede expirar (hilo principal ocupado); no es un fallo del juego.
- Freno siguiente (ronda 80): vehículos. `setVehicleParams` ya responde como la librería para un id sin cuerpo
  (aviso + -1), pero `CarController.<init>` llama `Bullet.addVehicle` y `getVehiclePhysics` devuelve un registro
  completo por vehículo cada frame (transform Bullet, velocidad, ruedas). No inventar un modelo de "autos quietos"
  sin derivar antes las conversiones de coordenadas de `addVehicle` y el consumo en `WorldSimulation.update`.

## Ronda 80 (b) (Code, 31/10) — conversiones implícitas y nombres reservados en GLSL
- Error: suponer que `binding.declaration` del parser shaderfrog siempre es el identificador. Para el segundo
  declarador de `vec3 r,u;` es el nodo `declaration` (y para parámetros, `parameter_declaration`); el tipo de `u`
  quedaba sin inferir. Mejor enfoque: registrar el tipo bajo los tres nodos y renombrar vía `.identifier`.
- Patrón: las llamadas a funciones de usuario llegan como `type_specifier`/`type_name` (no `identifier`); los
  built-ins y los `pz_user_*` renombrados como `identifier`. Distinguir constructores de struct por la tabla de structs.
- Patrón: los prototipos no crean bindings de parámetros; renombrarlos aparte con la misma regla determinista.
- Proceso: un barrido completo de los shaders del juego con el arnés tarda >2 min; lanzarlo en segundo plano.

## Ronda 80 (frente A, 31/10) — GPU real en el Chrome de pruebas
- Patrón: Chrome 156 headless (`--ozone-platform=headless` que agrega Playwright) usa la NVIDIA sin ventana con
  `--enable-gpu --ignore-gpu-blocklist --use-angle=gl` (o `vulkan`); no hace falta DISPLAY/Wayland. Verificar con
  `tools/diag/gpu_check.mjs` (UNMASKED_RENDERER debe decir NVIDIA) antes de atribuir tiempos a la GPU.
- Error evitado: suponer que la GPU acelera el arranque. Con NVIDIA el menú tarda lo mismo que con SwiftShader
  (~404 s hasta `exit TISLogoState`): el cuello es CPU/wasm del hilo principal (heartbeats `evaluate-timeout`).
  Mejor enfoque: perfilar el hilo principal (JFR en JVM / perfil de Chrome) en vez de cambiar flags de gráficos.

## Ronda 80 (frente F, 31/10) — auditoría estática de GL en partida
- Error de herramienta: en Python `p[0] += u4()` evalúa `p[0]` ANTES de que `u4()` avance el cursor; el lector
  de class files propio saltaba mal los atributos de campos y ~30 % de las clases fallaban. Mejor enfoque:
  `ln = u4(); p[0] += ln`.
- Error de shell: en este entorno `grep` es una función que llama a ugrep con `-I` (salta binarios);
  `grep -rl X zombie/` sobre `.class` no encuentra nada. Mejor enfoque: `command grep -rla`.
- Patrón: la alcanzabilidad estática de GL debe respetar las compuertas de `GLCapabilities`. `OpenGL30/31/33/43`
  son false aquí (LWJGL exige TODAS las direcciones de esa versión y `hasEntrypoint` no da, p. ej.,
  `glMapBufferRange`), así que RenderList/RenderTexture/ShaderBuffer (GL43+), `glMapBufferRange` y los caminos
  ARB/EXT no se ejecutan; pero `iso.Particles` sólo *avisa* sin OpenGL33 y usa instanciado igual.
- Error encontrado: tokens de consulta mal escritos sin test (`GL_SCISSOR_BOX` 0x0c36 en vez de 0x0C10,
  `GL_MAJOR/MINOR_VERSION` 0x820b/0x820d en vez de 0x821B/0x821C). Mejor enfoque: cada token nuevo con
  `javap -constants` sobre GL11/GL30C del jar fijado y un test que lo consulte.
- Error evitado: `glClearDepthf` del shim llamaba `clearDepthf` de WebGL2, que no existe (WebGL2 sólo tiene
  `clearDepth`); se veía recién al mapear GL41C. Mejor enfoque: pasar por el método del puente, no por `call(nombre)`.
