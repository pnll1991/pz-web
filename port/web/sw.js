// Ronda 87: service worker de pz-web (opt-in con ?sw=1, ver port/runtime/sw-register.mjs). Sólo cachea lo inmutable que el HTTP cache no guarda:
//  - /packs/<sha256>.bin: nombre = contenido, nunca cambia; sin tope de tamaño (el Tiles2x pesa 321 MB). Se podan con el mensaje 'keep-packs'.
//  - /media/* con cabecera Range (las lecturas síncronas del juego, respuestas 206): cada rango se guarda como una respuesta 200 bajo una clave sintética
//    con el rango y se devuelve como 206. La caché lleva la versión del manifest de media (?m=): si cambia la copia fijada, se descarta sola.
// El código (js/html/wasm) NO pasa por acá: lo valida el HTTP cache con ETag, así un deploy nuevo siempre se ve al recargar.
// ?sw=0 (o borrar el registro) lo desactiva y vacía las cachés. Cualquier error cae a la red: el worker nunca debe romper la carga.
const P = new URL(self.location.href).searchParams;
const MEDIA = `pz-media-${P.get('m') || '0'}`, PACKS = 'pz-sw-packs-v1';

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', event => event.waitUntil((async () => {
  for (const key of await caches.keys()) if (key.startsWith('pz-media-') && key !== MEDIA) await caches.delete(key);
  await self.clients.claim();
})()));

self.addEventListener('message', event => {
  const d = event.data || {};
  if (d.type === 'keep-packs' && Array.isArray(d.names)) event.waitUntil((async () => {
    const cache = await caches.open(PACKS), keep = new Set(d.names);
    for (const req of await cache.keys()) if (!keep.has(new URL(req.url).pathname.split('/').pop())) await cache.delete(req);
  })());
  if (d.type === 'clear') event.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k.startsWith('pz-')).map(k => caches.delete(k)))));
});

const rangeKey = (url, range) => new Request(`${url.origin}${url.pathname}${url.search}${url.search ? '&' : '?'}__range=${encodeURIComponent(range)}`);

async function media(req, url, range) {
  const cache = await caches.open(MEDIA), key = rangeKey(url, range);
  const hit = await cache.match(key);
  if (hit) {
    return new Response(hit.body, { status: 206, headers: { 'Content-Type': hit.headers.get('Content-Type') || 'application/octet-stream',
      'Content-Range': hit.headers.get('X-Content-Range'), 'Content-Length': hit.headers.get('Content-Length'), 'Accept-Ranges': 'bytes' } });
  }
  const res = await fetch(req);
  if (res.status !== 206) return res;
  const buf = await res.arrayBuffer();
  const store = new Response(buf, { status: 200, headers: { 'Content-Type': res.headers.get('Content-Type') || 'application/octet-stream',
    'X-Content-Range': res.headers.get('Content-Range') || '', 'Content-Length': String(buf.byteLength) } });
  cache.put(key, store).catch(() => {}); // cuota llena: se sirve igual, sólo no se guarda
  return new Response(buf, { status: 206, headers: res.headers });
}

async function pack(req) {
  const cache = await caches.open(PACKS);
  const hit = await cache.match(req);
  if (hit) return hit;
  const res = await fetch(req);
  if (res.ok && res.status === 200) { try { await cache.put(req, res.clone()); } catch { /* cuota: se sirve sin guardar */ } }
  return res;
}

self.addEventListener('fetch', event => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;
  const range = req.headers.get('range');
  if (/^\/packs\/[0-9a-f]{64}\.bin$/.test(url.pathname) && !range) event.respondWith(pack(req).catch(() => fetch(req)));
  else if (url.pathname.startsWith('/media/') && range && /^bytes=\d+-\d+$/.test(range)) event.respondWith(media(req, url, range).catch(() => fetch(req)));
});
