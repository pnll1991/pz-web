# Tarea 3 para Codex

Leé STATUS.md, AGENTS.md y docs/ROADMAP-source.txt. PZ_HOME=/home/agente47/pz-pinned/25485521/projectzomboid (42.21).

Novedad: durante esta sesión Paolozky liberó la RAM (se apagó el servidor de IA local que ocupaba ~19 GB). Tenés ~25 GB libres: podés usar hasta MAVEN_OPTS=-Xmx20g para el build completo con reflexión Lua. No corras el juego nativo ni pruebas de navegador pesadas en paralelo con el build.

Objetivo: llegar al hito del menú principal de Project Zomboid renderizado en Chrome.
1. Implementar las 52 APIs JDK pendientes (Unsafe/MemoryUtil, concurrencia, FileChannel/watchers, management, XML/JAXB, AWT, carga de librerías, etc.) con implementaciones reales para el navegador; si algo no aplica en el navegador, error explícito, nunca stubs que simulen éxito.
2. Compilar el main real con reflexión Lua completa (-Xmx20g) y obtener el módulo Wasm.
3. Avanzar F3: pipeline GL (LWJGL->WebGL2), shaders del menú, GLFW, hasta que el menú se vea. Si llegás, captura en test-results y pruebas.
Mismas reglas de AGENTS.md. Actualizá STATUS.md al final.
