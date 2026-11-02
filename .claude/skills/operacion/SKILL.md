---
name: operacion
description: Cómo se levanta pz-web en una máquina nueva, cómo se manejan los servicios en marcha (dev server 8098, web de pruebas 8199, servidor PZ, túnel público, Chrome de prueba), dónde están los logs y qué hay que hacer en la máquina en vivo según lo que cambió (web, Java, dev server, servidor, paquetes, toolchain). Cargar antes de instalar, compilar, arrancar, parar o reiniciar algo, publicar en la máquina en vivo o diagnosticar por qué algo no anda en vivo.
---

# Operación

Detalle completo en `docs/operacion/`: `README.md` (puertos, rutas, dónde corre cada cosa), `levantar.md`, `manejar.md`, `actualizar.md`. Checklist:

1. Todo corre en la máquina Linux x86_64 en vivo; en macOS no se compila ni se levanta nada (solo editar, `npm test` y PRs).
2. Lo que está en vivo (8098, servidor PZ en 16261, túnel) no se reinicia ni se toca sin OK del dueño. Antes, `tail /tmp/pzweb-access.log` para ver quién juega.
3. Para probar: web en 8199 (`scripts/dev_web_8199.sh`) e instancia de prueba del servidor (`PZ_MP_DATA`/`PZ_MP_PORT`/`PZ_MP_UDPPORT`/`PZ_MP_LOG`). Un build y un Chrome de prueba a la vez.
4. Después de editar `port/web` o `port/runtime`: `bash scripts/sync_web.sh && grep -c '<cambio>' port/target/web/<archivo>` antes de cualquier prueba.
5. Cada merge a `main` se publica enseguida en la máquina en vivo (`scripts/publicar_web.sh`, ver `docs/procesos/ramas-y-prs.md#publicar-de-dev-a-main`). Al traer `main` a la máquina en vivo, mirar `git diff --stat` y seguir la tabla de `docs/operacion/actualizar.md` según lo que cambió.
6. Nunca compartir en PRs, issues ni commits la URL del túnel, `/tmp/pzweb-public.key` ni `/tmp/pz82/admin.pw`.
