# Aprendizajes del agente

Leer al comenzar cada chat. Registrar errores propios, patrones a evitar y enfoques mejores.

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
