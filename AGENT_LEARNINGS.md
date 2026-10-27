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
