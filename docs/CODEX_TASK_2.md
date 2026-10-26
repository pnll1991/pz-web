# Tarea 2 para Codex

Leé STATUS.md, AGENTS.md y docs/ROADMAP-source.txt. El juego ya está instalado (Steam buildid 25485521) y fijado en `$HOME/pz-pinned/25485521` (PZ_HOME). El inventario ya se corrió con `tools/inventory/run.sh "$PZ_HOME"`; su salida está en `inventory-run.log`.

Seguí el roadmap a partir de F0 real: revisá el inventario, confirmá versión/LWJGL/natives/Kahlua, actualizá los docs con datos reales, y avanzá F1 (conectar el arranque real del juego en TeaVM, resolver APIs JDK faltantes, manifiesto de reflexión Lua) y luego F2/F3 hacia el hito del menú principal renderizado en Chrome. Mismas reglas de AGENTS.md: sin sudo, nunca commitear archivos del juego ni código decompilado, natives faltantes tiran error. Git: si no podés escribir `.git`, dejá los cambios en disco y anotá los commits sugeridos. Actualizá STATUS.md al final con qué funciona, qué falta y qué necesita hacer Paolozky.
