# Ronda 82: multijugador en el navegador (diseño)

Estado: diseño + prototipo por pasos. Solo uso personal y local: todo escucha en `127.0.0.1`.

## Cómo habla el cliente con el servidor (verificado sobre el jar fijado, solo nombres/firmas)

- Cliente y servidor comparten `zombie.core.raknet.RakNetPeerInterface`, un envoltorio Java de 30 nativas JNI
  (`libRakNet64.so`, más `libZNetNoSteam64.so` sin Steam). Todo el tráfico del juego pasa por tres puntos:
  `Connect(ip, puerto, contraseña, ...)`, `Send(buffer, prioridad, fiabilidad, canal, guid, broadcast)` y
  `Receive(buffer)` (`TryReceive` + `nativeGetData`). Los eventos de conexión (`ID_CONNECTION_REQUEST_ACCEPTED`,
  `ID_CONNECTION_LOST`, `ID_INVALID_PASSWORD`, ...) llegan como paquetes de un byte por el mismo `Receive`.
- Por encima están `UdpEngine`/`UdpConnection` y `GameClient`/`GameServer` (Java puro, ya compilado a Wasm por el port).
  `Startup(maxConnections)` pasa `Core.getVersionNumber()` a la nativa: el handshake exige la misma versión.
- Hoy en el port `SteamUtils.init()` falla al cargar `RakNet64` dentro de un `try/catch UnsatisfiedLinkError` y el juego deja
  `netEnabled=false`. Camino vanilla; es la razón por la que JOIN no existe en el menú web.

## Arquitectura elegida

```
Chrome (Wasm GC)                    puente (proceso local)                 servidor PZ 42.21
 GameClient -> UdpEngine             Java, 127.0.0.1:8099 (WebSocket)       Java real, -nosteam
  -> RakNetPeerInterface (natives     un RakNetPeerInterface REAL por        127.0.0.1:16261 (UDP RakNet)
     JNI = WebSocket)  <== ws ==>     cliente web (misma libRakNet64)  <==>  UDP 16262
```

- **Servidor real** (paso 1): `zombie.network.GameServer` de la misma instalación, `-nosteam`, `UPnP=false` (el servidor
  intenta abrir puertos del router por defecto: se desactiva), `SteamVAC=false`, `-ip 127.0.0.1`, datos fuera del repo
  (`/tmp/pz82/zomboid`). Script: `scripts/mp-server.sh`.
- **Puente** (paso 2): cada WebSocket crea un par de `RakNetPeerInterface` reales en una JVM (la del juego, `-nosteam`), por lo
  que el handshake RakNet, la versión y la fiabilidad/fragmentación son las del juego, sin reimplementar RakNet. El puente solo
  reenvía: `connect`, `send(bytes, prioridad, fiabilidad, canal)`, `recv(bytes)`, `disconnect`. TCP/WebSocket ya es fiable y
  ordenado; se conservan los parámetros de fiabilidad para que el puente los aplique hacia UDP.
- **Nativas del navegador** (paso 3): `port.natives.RakNetPeer` implementa las 30 firmas sobre un WebSocket (JS). Las no
  soportables (`ConnectToSteamServer`, `GetClientSteamID`, ...) lanzan una excepción con su nombre JNI; nunca simulan éxito.
- **Menú** (paso 4): habilitar `netEnabled` solo cuando las nativas existen (no con una bandera mentirosa) y usar la pantalla JOIN
  vanilla con `127.0.0.1:16261`.

## Riesgos

1. Autenticación: sin Steam, el servidor usa cuentas propias (usuario/clave en su base). `SteamVAC=false`.
2. Versión exacta: el cliente web y el servidor salen del mismo jar fijado; no hay desajuste posible salvo cambiar el jar.
3. Hilos: el juego recibe en un hilo de red; el port es de un solo hilo, así que `Receive` debe ser no bloqueante sobre
   una cola alimentada por el WebSocket.
4. Rendimiento: el cliente web hace ~58 fps; la sincronización de mundo del servidor (cargar chunks) añade carga en el hilo único.
5. Fuga de red: no abrir puertos fuera de 127.0.0.1 ni dejar UPnP activo.

## Verificación prevista

Dos clientes (Chrome de prueba, en secuencia o dos pestañas en un perfil) conectan al servidor, entran con cuentas distintas y se
ven moverse. Se guarda capturas locales (no se versionan).
