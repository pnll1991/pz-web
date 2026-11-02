---
name: trabajo-con-agentes
description: Cómo repartir y entregar trabajo entre agentes en pz-web sin pisarse - brief con la plantilla, una rama y un worktree por tarea, recursos compartidos (build Maven, Chrome, chrome.lock, servidor en vivo), niveles de evidencia y registro de aprendizajes. Cargar al empezar una ronda, al escribir un brief, al lanzar un subagente o al integrar lo que devolvió.
---

# Trabajo con agentes

Detalle completo: `docs/procesos/trabajo-con-agentes.md`. Checklist:

1. Leer lo que pide `AGENTS.md` (por rangos los archivos largos).
2. Tarea delegada = brief en `docs/briefs/` copiado de `docs/briefs/PLANTILLA.md`, con "Cómo se prueba" y "Terminado cuando".
3. Una rama desde `dev` y un worktree propio por tarea; subagentes en archivos distintos, sin commitear ni tocar lockfiles.
4. Recursos compartidos: un build Maven y un Chrome de prueba a la vez (`chrome.lock`); nada en vivo (8098, servidor PZ, túnel) sin OK del dueño. Si están ocupados, anotarlo; no forzar.
5. Decir hasta qué nivel de evidencia se llegó: Node → JVM → Chrome plataforma → build real → Chrome con el juego. Solo el último demuestra un hito del juego.
6. Errores de la tarea en `AGENT_LEARNINGS.md` (buscar antes si ya existe), decisiones en `docs/decisions.md`, estado en `STATUS.md`, todo en el mismo PR.
