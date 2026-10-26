# Tarea para Codex

Sos el ingeniero principal de un port PERSONAL de Project Zomboid al navegador (el juego corriendo dentro del navegador, NO streaming). El dueño (Paolozky) compró el juego en Steam; el port carga SU copia local y es solo para uso personal.

El plan completo está en `docs/ROADMAP-source.txt` (es el código fuente de una página con el roadmap: fases F0–F8, arquitectura TeaVM -> WebAssembly GC, tabla de reemplazos, AGENTS.md sugerido y prompts por fase). Leelo entero primero y seguilo.

Reglas:
- Nunca commitees archivos del juego ni código derivado/decompilado del juego. Los archivos del juego van en una carpeta ignorada por git (fuera del repo o en `.gitignore`).
- Nada de sudo: instalá toolchains (JDK, TeaVM, Emscripten, etc.) a nivel usuario dentro de `~/Projects/pz-web/.toolchain` o similar.
- Natives faltantes tiran error, nunca fingen éxito.
- Commits chicos y descriptivos en git local. No publiques nada en internet ni crees repos remotos.
- Escribí y mantené `STATUS.md` en la raíz: fase actual, qué funciona, qué falta, bloqueos y qué necesita hacer Paolozky a mano.

Ahora mismo el juego TODAVÍA NO está instalado (Steam appid 108600, build Linux nativa; lo buscás en `~/.local/share/Steam/steamapps/common/ProjectZomboid` o en otra biblioteca de Steam). Hacé:
1. Crear `AGENTS.md` según el roadmap y la estructura del repo.
2. F0: preparar el entorno de desarrollo completo a nivel usuario y el script de inventario de archivos del juego. Si el juego ya está instalado cuando llegues a ese punto, corré el inventario; si no, dejalo listo y anotalo en STATUS.md.
3. Avanzar todo lo que se pueda de F1 en adelante sin los archivos del juego (scaffolding TeaVM, shims de LWJGL->WebGL2, servidor de dev con COOP/COEP, harness de tests).
4. Parar cuando estés bloqueado por algo que solo puede hacer Paolozky (instalar el juego, cuenta de fmod.com, sudo) y dejarlo claro en STATUS.md.
