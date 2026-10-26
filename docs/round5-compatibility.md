# Ronda 5: ventana de navegador y arranque observable

La inicialización de `org.lwjglx.opengl.Display` supera el bloqueo `Unknown platform: TeaVM`. El main real llega a cargar `PZBullet64` y se detiene porque falta su implementación de navegador. No hay evidencia del menú ni de `LuaManager.init` completo. Los resultados finales y métricas están en `STATUS.md` y `docs/verification.md`.

## Ventana

`WindowCompatibility` sustituye solamente `Display.init()` en esa clase. La inicialización valida el canvas conectado, su contexto WebGL2 y su entrada DOM; consulta `Screen.width`, `height` y `colorDepth`; construye el `DisplayMode` real con esos datos e invoca `Keyboard.create()` original. Conserva la guarda `isInitialized` y solo la marca al terminar. La frecuencia cero significa desconocida, no una medición de Hz. El identificador de monitor 1 pertenece al backend del navegador.

La carga estática de GLFW inicializa el backend del navegador. `glfwGetKeyScancode` usa un dominio virtual estable ligado a los códigos físicos soportados por `CanvasInput`; no son scancodes del SO. `glfwGetKeyName` consulta el layout obtenido del navegador y devuelve null para teclas no imprimibles o etiquetas no disponibles. No infiere la distribución del teclado del dueño. El layout se obtiene al arrancar; su actualización dinámica queda pendiente.

Esta ronda no implementa `Display.create`, pantalla completa, cambios de modo, callbacks de GLFW ni presentación de frames. Las consultas de estado de teclado/mouse requieren una ventana creada y rechazan el identificador mientras ese lifecycle no esté implementado. Los demás entrypoints de GLFW fallan explícitamente; si el inventario identifica una declaración nativa se conserva su nombre JNI completo. El renderer, `Display.create` y `LWJGLUtil` siguen originales: todavía necesitan integración antes del menú.

Referencias: [GLFW 3.4 input](https://www.glfw.org/docs/3.4/group__input.html), [Screen](https://developer.mozilla.org/en-US/docs/Web/API/Screen), [Keyboard.getLayoutMap](https://developer.mozilla.org/en-US/docs/Web/API/Keyboard/getLayoutMap). Las firmas y campos concretos de LWJGLx proceden de la copia fijada, sin desensamblado ni extracción de cuerpos. Metadatos locales ignorados: `build/round5/lwjgl-usage.tsv`.

## Consola y trazas

La ejecución real reveló corrupción de los logs. En `JsConsolePrintStream` de TeaVM 0.16.0, las escrituras de bytes mayores al buffer interno no avanzaban `off`. Además, `flush` borraba el callback antes de llamarlo y trataba el flush como final del stream UTF-8. La corrección avanza el offset, guarda el callback en una variable local y conserva la decodificación incremental. También valida rangos, entrega escrituras de un byte sin esperar a llenar el buffer, corrige el límite final de slices de caracteres y la ruta de `print(double)`.

El parche se genera desde el [source jar oficial](https://repo.maven.apache.org/maven2/org/teavm/teavm-classlib/0.16.0/teavm-classlib-0.16.0-sources.jar), SHA-256 `da6f83c1436c87a9ad683b6143aaa9c172b08c3974c9a7d5741a479d0d60092e`. El archivo generado conserva su licencia Apache-2.0 bajo target ignorado. No se altera el cache Maven. La fixture anterior al parche falla en Chrome dentro de `JsConsolePrintStream.flush`; el resultado posterior comprueba mensajes completos, offsets no nulos, UTF-8 fragmentado y escrituras después de flush repetidos. No se afirma cobertura de todo PrintStream.

`StartupProgress` agrega registros en entrada y retorno normal de inicializadores seleccionados. No sustituye sus cuerpos ni convierte excepciones en éxito; conserva solo la primera aparición de cada límite para evitar crecimiento por frame. `GameBoot` conserva además los frames de la excepción por un canal separado de `System.err`, que el juego puede reemplazar. Los símbolos se resuelven por nombres y límites del Wasm, sin desensamblar.

La prueba real guarda automáticamente `chrome-startup.json` y la captura de esta ronda. Cada build invalida los reportes derivados anteriores para no confundir evidencia de módulos distintos. Ninguno de estos registros marca el menú como renderizado.

## Bloqueo nativo

`PZBullet64` no está disponible como módulo wasm32 ni como puente JNI compatible en este proyecto. La clase `zombie.core.physics.Bullet` declara 93 métodos native en la copia fijada; eso no equivale al número de exports de todas las variantes ELF. Las firmas están en el inventario y en `build/round5/bullet-signatures.json`. No se cargó el juego nativo ni se ejecutaron sus funciones para obtener esta lista.

No hay especificación del wrapper de PZ ni grabaciones de referencia bajo `docs/re` en este checkout. El siguiente trabajo concreto es definir y verificar el contrato de inicialización/versionado de Bullet, identificar la versión correspondiente y construir el módulo separado con Emscripten según el roadmap. Los métodos de física que se incorporen necesitan resultados de referencia; los restantes deben seguir fallando. Después de resolver la carga se repite el main real para determinar la siguiente dependencia. Registrar una biblioteca vacía o modificar `System.loadLibrary` para devolver éxito no resolvería este bloqueo.
