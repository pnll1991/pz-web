Ronda 87 pz-web. Objetivo "todo el juego en el navegador", en este orden:
1) Achicar la descarga: service worker que cachee wasm/js/packs para bajar una sola vez (con versión/invalidación), y carga del mapa/packs por partes bajo demanda. Medí bytes de primera y segunda carga antes/después.
2) Evaluar (informe; prototipo solo si es barato) correr el server PZ dentro del navegador del host compilado a wasm con TeaVM: qué bloquea (hilos, sockets, nativas, RAM).
3) Evaluar WebRTC entre jugadores con un server mínimo de señalización (viabilidad, NAT/TURN, qué cambia).
REGLA CLAVE: nada se aplica a lo que está en vivo (server PZ en uso, web en 8098, túnel Cloudflare/key/URL) sin OK de Paolozky. Desarrollá y probá en un puerto temporal (8199) o build aparte. El reporte debe decir explícitamente qué cambios afectarían la partida en vivo al aplicarlos.
Commits: preferí rama code/sw (creada desde code/menu) con git -c user.name=Paolozky -c user.email=paolozky@local; en code/menu solo si no rompe el modo directo. Nada de archivos del juego ni decompilado en git. Sin stubs que finjan éxito.
No tocar: 8097, 8092, pids 446769/12149/12150, Steam, ~/pz-pinned, Strata, Chrome de escritorio. Sin sudo. Un build Maven y un Chrome de prueba a la vez. Benchmarks cortos.
Reporte en ~/pzshots/CLAUDE_REPORT_R87.md: qué se hizo, bytes antes/después (1ra y 2da carga), qué es viable y qué no, qué afectaría la partida en vivo.
