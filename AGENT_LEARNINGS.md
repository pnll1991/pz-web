# Aprendizajes del agente

Leer al comenzar cada chat. Registrar errores propios, patrones a evitar y enfoques mejores.

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
