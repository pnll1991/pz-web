# Mecánicas de movimiento: saltar, trepar y lo que falla en el puerto

Rama `fix/mecanicas-saltar-trepar`. Qué mecánicas se revisaron, cuáles funcionan, cuáles se arreglaron y cuáles no se pueden arreglar todavía, con el motivo. Las capturas y logs de las pruebas quedan fuera del repo (`/tmp/pzmec`, `~/pzshots/`).

## Causa raíz

El juego decide qué clip reproducir con los `AnimSets` (XML en `media/AnimSets/player/<estado>/`). Cada nodo nombra un clip de `media/anims_X/Bob/*.x`. En el navegador no hay `libjassimp64.so`, así que los clips se preconvierten al formato de caché (`tools/jassimp/JassimpCache.java`) y se empaquetan; **un clip que no está en el paquete no existe para el motor**.

El paquete traía los 240 clips de locomoción y los 205 de combate del jugador (`stage_jassimp.py --bob-anims locomotion|combat`), pero **ninguno** de los de trepar. Los estados `ClimbOverFenceState`, `ClimbThroughWindowState`, `ClimbOverWallState`, `ClimbSheetRopeState` y `ClimbDownSheetRopeState` salen de su animación con un evento del clip: sin clip, el personaje queda en el estado y no vuelve a moverse (mismo patrón que `PlayerHitReactionState` antes de los clips de combate, R86).

Inventario estático (`AnimSets/player/*` contra el índice de `/packs`, 2/11/2026): los `AnimSets` de trepar están todos en el paquete; sus clips no.

| AnimSet | Mecánica | Clips que faltaban |
|---|---|---:|
| `climbfence` | saltar/trepar valla, vault corriendo y esprintando (con arma de una/dos manos, pesada, arma de fuego…) | 29 |
| `climbwindow` | trepar por una ventana, marco de ventana, obstáculo | 9 |
| `climbwall` | pared o valla alta | 5 |
| `climbrope`, `climbdownrope` | subir y bajar por una soga | 1 + 1 |
| `openwindow`, `closewindow`, `smashwindow` | abrir, cerrar y romper ventanas | 4 + 3 + 1 |
| `falling` | caída desde altura (techo, valla) | 26 |
| `throwBodyOverFence-*`, `throwBodyOutWindow-*` | tirar un cuerpo por encima de una valla o por una ventana | 8 |

Total: 87 clips únicos, 15,1 MB ya convertidos.

## Arreglo

`tools/pack/stage_txt.py` define `MOBILITY_ANIMSETS` y `tools/pack/stage_jassimp.py --bob-anims mobility` los preconvierte con la `libjassimp64.so` fijada, igual que los de combate:

```bash
python3 tools/pack/stage_jassimp.py --pz-home "$PZ_HOME" --stage build/real-browser/stage-mobility --clean --bob-anims mobility
python3 tools/pack/overlay.py --packs build/real-browser/packs --stage build/real-browser/stage-mobility \
    --prefix /media/anims_X/ --prefix /media/anims_x/ --add     # respaldo del index.json en index.json.prev
```

Los clips **no se versionan**: salen de la copia local del juego (`PZ_HOME`) hacia `build/real-browser/` (ignorado por git) en el momento de armar los paquetes. Lo único que entra al repo son el script, el modo nuevo y su test (`tests/python/test_stage_txt.py`, con un árbol sintético propio).

Presupuesto del VFS del navegador (`vfs.mjs`, 640 MiB): paquetes 576,0 → 591,3 MB (quedan ~80 MB). Para aplicarlo en vivo hay que rehacer/ampliar los paquetes en la máquina en vivo (ver `docs/operacion/actualizar.md`); no corta la partida y la primera carga siguiente baja el paquete nuevo.

## Resultados

RESULTADOS_PLACEHOLDER

## Mecánicas con clips ausentes que no se arreglaron (inventario)

Siguen sin clip 854 de los 1615 de `anims_X/Bob` (208 MB sin convertir). **No se probaron en el juego**; la lista dice qué `AnimSet` los usa y es una inferencia por el mismo patrón, no una comprobación. No caben en el presupuesto actual del VFS junto con los demás (~80 MB libres):

| AnimSet | Qué es (por el nombre) | Clips | MB |
|---|---|---:|---:|
| `actions` | acciones temporizadas: comer, beber, craftear, leer, curar… | 206 | 55,7 |
| `ext`, `sitext` | animaciones de exterior/sentado | 133 + 83 | 39,8 + 23,0 |
| `strafe` | desplazamiento lateral apuntando | 127 | 24,1 |
| `emote`, `fishing`, `fitness` | gestos, pesca, ejercicio | 70 + 66 + 24 | 15,7 + 12,7 + 6,6 |
| `maskingleft/right` | máscaras de manos (objetos en mano) | 50 + 41 | 9,5 + 7,9 |
| `sitonfurniture`, `sitonground*`, `onbed` | sentarse y dormir | 24 + 25 + 7 | 4,9 + 5,1 + 1,5 |
| `ranged` | armas de fuego | 13 | 2,3 |
| `draggingBody*`, `pickUpBody*`, `layDownBody*`, `grabCorpse*` | arrastrar y mover cuerpos | ~20 | ~5 |
| `petanimal`, `milkanimal`, `shearanimal` | animales | 38 | 12,4 |

Por qué no se arreglan ahora: (1) el presupuesto de memoria del VFS; subirlo o cargar clips bajo demanda (el motor los enumera al arrancar, `ModelManager.loadAnimsFromDir`) es un cambio de arquitectura; (2) cada grupo necesita su propia prueba en el juego y no hay evidencia de cuáles frenan el juego. Siguiente paso razonable: `onbed`, `sitonground*`, `sitonfurniture` y `ranged` (~14 MB) con su prueba.

## Cómo reproducir

```bash
# instancia de prueba propia (nunca 8098 ni el servidor PZ en vivo)
PZ_MP_DATA=/tmp/pzmec/zomboid PZ_MP_PORT=16281 PZ_MP_UDPPORT=16282 PZ_MP_LOG=/tmp/pzmec/server.log bash scripts/mp-server.sh start
# web con los paquetes originales (antes) y con los de mobility (después)
PORT=8201 PZWEB_WEB_ROOT=/tmp/pzmec/web PZ_PACKS=/tmp/pzmec/packs-orig node dev-server/server.mjs &
PORT=8202 PZWEB_WEB_ROOT=/tmp/pzmec/web PZ_PACKS=/tmp/pzmec/packs     node dev-server/server.mjs &
flock /tmp/pz80-locks/chrome.lock node tools/diag/play-repl.mjs --url http://127.0.0.1:8202/play.html --port 16281 --admin --only 1 --out /tmp/pzmec/after --profiles /tmp/pzmec --console &
cp tools/diag/mobility-scenario.js /tmp/pzmec/after/cmd.js   # cuando el cliente está en el mundo; MOB_ONLY=fence,window,... filtra
```

El escenario encola las mismas acciones temporizadas que el menú contextual (`ISClimbOverFence`, `ISClimbThroughWindow`, `ISClimbSheetRopeAction`) con el jugador a un paso del objeto, mira el estado del motor cada ~1 s y escribe `MOB-RESULT <mecánica> ok|FAIL ...` en `out.txt`.
