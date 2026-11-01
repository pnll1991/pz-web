# Exponer el modo directo en la LAN (documentado, NO aplicado)

Estado: hoy todo escucha sólo en `127.0.0.1` (regla del proyecto). Esto describe qué habría que cambiar para que otros equipos de la red
local abran `/play.html`, qué lo bloquea y cómo apagarlo. Nada de esto está activo ni cambia el repo.

## Qué escucha hoy
| Pieza | Puerto | Quién la usa |
|---|---|---|
| dev server (`dev-server/server.mjs`, `listen(port, '127.0.0.1')`) | 8098/TCP | los navegadores (página, assets, `/ws`) |
| servidor PZ (`scripts/mp-server.sh`, `-ip 127.0.0.1`) | 16261/UDP | sólo el puente (misma máquina) |
| puente RakNet (un proceso por navegador, stdio) | ninguno | lo lanza el dev server |

Para la LAN **sólo hace falta abrir el dev server**: el servidor PZ y los puentes siguen en la máquina anfitriona hablando por loopback
(el puente rechaza destinos no loopback a propósito). No abrir el 16261 a la red ni activar UPnP (`UPnP=false` ya está forzado).

## Cambios necesarios (cuando Paolozky lo apruebe)
1. **Escuchar en la IP de la LAN.** En `dev-server/server.mjs` el `listen` fija `'127.0.0.1'`. Habría que leer una variable (p. ej. `PZ_BIND=192.168.1.10`)
   y arrancar con ella; nunca `0.0.0.0` (expondría también la VPN/otras interfaces).
2. **`/ws` y el origen.** `dev-server/ws-bridge.mjs` sólo acepta pares loopback y `Origin` 127.0.0.1/localhost. Habría que permitir la IP LAN
   del anfitrión como `Origin` y rangos privados (10/8, 172.16/12, 192.168/16) como par remoto, y seguir rechazando el resto.
3. **Contexto seguro (el bloqueo real).** El juego exige `crossOriginIsolated` (COOP/COEP) y eso sólo existe en un *contexto seguro*: `http://127.0.0.1`
   lo es, `http://192.168.x.x` **no**. Sin eso el cliente muestra "Faltan COOP/COEP". Opciones:
   - HTTPS con certificado propio (por ejemplo una CA local con `mkcert`, instalada en cada dispositivo; o un certificado autofirmado aceptado a mano)
     delante del dev server, p. ej. un proxy TLS en 8443 que reenvíe a 127.0.0.1:8098 (incluido el upgrade de `/ws`).
   - Chrome de cada cliente con `--unsafely-treat-insecure-origin-as-secure=http://IP:8098` (sólo para pruebas; hay que lanzar Chrome así en cada equipo).
4. **Cortafuegos.** Permitir 8098/TCP (o 8443) sólo desde la subred (`ufw allow from 192.168.1.0/24 to any port 8098 proto tcp`); no abrir 16261.
5. **Recursos por cliente.** Cada navegador abre un puente Java (~150 MB) y el servidor PZ usa ~3 GB; con 2–4 clientes en la misma máquina hay
   que vigilar los ~30 GB de RAM (el render y el wasm van en el navegador de cada cliente, no en el anfitrión).

## Lo que conviene decidir antes
- **Archivos del juego.** El dev server sirve `/media` (texturas, mapas, sonidos) de la copia fija de Paolozky. Con LAN, esos archivos se descargan
  en otros dispositivos. Es uso personal sólo si son equipos propios; si son de otras personas es compartir los archivos del juego. Es una decisión
  de Paolozky (la regla del proyecto es uso personal y local).
- **Sin autenticación real.** Cualquiera en la LAN que abra la URL obtiene una cuenta y entra. El servidor usa `Open=true`. Para limitarlo: lista blanca
  en el servidor (`Open=false` y `/adduser` desde la consola) o una contraseña de servidor (`Password=` en el ini; el modo directo tendría que enviarla).
- **Internet (túnel).** No se plantea sin OK explícito: exponer assets del juego y un servidor sin autenticación fuerte.

## Cómo apagarlo / volver a solo-local
- Parar el dev server (`bash scripts/dev_server_8098.sh stop`) y volver a arrancarlo sin `PZ_BIND`; quitar la regla del cortafuegos
  (`ufw delete allow from 192.168.1.0/24 to any port 8098 proto tcp`); `bash scripts/mp-server.sh stop` si el servidor PZ no debe seguir.
- Comprobación: `ss -ltnH | grep ':8098'` debe mostrar sólo `127.0.0.1:8098`; `ss -lunH | grep ':16261'` sólo `127.0.0.1:16261`.
