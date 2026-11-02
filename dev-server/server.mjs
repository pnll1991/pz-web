import http from 'node:http';
import { classpathResources } from './classpath.mjs';
import { attachBridge } from './ws-bridge.mjs';
import { createPublicGate } from './public-mode.mjs';
import { createWarmList } from './warm-list.mjs';
import { createPlayerBoard } from './players.mjs';
import { acceptedCodings, createCompressor } from './compress.mjs';
import { buildRangesResponse, parseIndices } from './media-ranges.mjs';
import { appendFile } from 'node:fs';
import { createHash } from 'node:crypto';
import { realpath, stat, open, readFile, readdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { pipeline } from 'node:stream/promises';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const types = new Map(Object.entries({
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.wasm': 'application/wasm', '.json': 'application/json', '.txt': 'text/plain; charset=utf-8',
  '.lua': 'text/plain; charset=utf-8', '.png': 'image/png', '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml', '.ico': 'image/x-icon', '.ogg': 'audio/ogg',
}));
const TEXTUAL = new Set(['.html', '.js', '.mjs', '.css', '.json', '.txt', '.svg']);
// Ronda 90: /media ya comprimido de por sí. Medido con br q5 / gzip 6 sobre bloques de 1 MB de PZ_HOME/media (docs/analisis-carga-inicial.md):
// png 0,99, pack 0,94, ogg 0,99, bank 0,98, bik 0,98, zip 0,98, glb 0,96, dat/bk2/backup 1,00, jpg 1,00. No se intenta: es CPU sin ganancia.
// El resto se comprime si baja al menos MEDIA_MIN_SAVING (txt 0,21, lua 0,17, xml 0,28, lotpack 0,10, lotheader 0,11, bin 0,08, tiles 0,03,
// x 0,22, wav 0,76…); lo que no baja queda anotado en la caché como "no vale la pena" y no se vuelve a probar.
export const MEDIA_INCOMPRESSIBLE = new Set(['.png', '.jpg', '.jpeg', '.ogg', '.ogv', '.mp3', '.bank', '.bik', '.bk2', '.pack',
  '.zip', '.gz', '.br', '.7z', '.glb', '.dat', '.backup', '.webm', '.webp', '.woff2']);
export const MEDIA_MIN_SAVING = 0.1;
// Un tramo más grande que esto se manda sin comprimir (stream directo del disco, sin armar el tramo en memoria).
export const MEDIA_SLICE_COMPRESS_MAX = 32 << 20;
const headers = {
  'Cross-Origin-Opener-Policy': 'same-origin',
  'Cross-Origin-Embedder-Policy': 'require-corp',
  'Cross-Origin-Resource-Policy': 'same-origin',
  'X-Content-Type-Options': 'nosniff',
  'Cache-Control': 'no-cache',
};

export { acceptedCodings };

/** Ronda 90: tramo de /media pedido como `?r=<inicio>-<fin>` (fin exclusivo, el formato que ya usa vfs.fetchRange) y SIN cabecera Range.
 *  undefined si no hay `r`; null si es inválido o se sale del archivo. */
export function parseSlice(value, size) {
  if (value == null) return undefined;
  const match = /^(\d+)-(\d+)$/.exec(value);
  if (!match) return null;
  const start = Number(match[1]), end = Number(match[2]);
  if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start >= end || end > size) return null;
  return { start, end };
}

export function parseRange(value, size) {
  const match = /^bytes=(\d*)-(\d*)$/.exec(value);
  if (!match || (!match[1] && !match[2]) || size === 0) return null;
  let start, end;
  if (!match[1]) {
    const length = Number(match[2]);
    if (!Number.isSafeInteger(length) || length <= 0) return null;
    start = Math.max(0, size - length); end = size - 1;
  } else {
    start = Number(match[1]); end = match[2] ? Number(match[2]) : size - 1;
    if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start >= size || end < start) return null;
    end = Math.min(end, size - 1);
  }
  return { start, end };
}

/** Round 79: [[path relative to media/, size], ...] for every regular file (symlinks followed only inside media).
 *  `version` (sha256 of names, sizes and mtimes) keys the browser's persistent media cache (port/runtime/mediacache.mjs):
 *  any change in PZ_HOME/media invalidates it. Returned with a strong ETag so a reload revalidates with a 304. */
async function buildManifest(mediaRoot, extra = {}) {
  const out = [], hash = createHash('sha256');
  async function walk(dir, rel) {
    for (const entry of (await readdir(dir, { withFileTypes:true })).sort((a, b) => (a.name < b.name ? -1 : 1))) {
      const full = path.join(dir, entry.name), name = rel ? `${rel}/${entry.name}` : entry.name;
      if (entry.isDirectory()) await walk(full, name);
      else if (entry.isFile()) {
        const info = await stat(full);
        out.push([name, info.size]); hash.update(`${name}\0${info.size}\0${info.mtimeMs}\n`);
      }
    }
  }
  await walk(mediaRoot, '');
  // `slices: 1` (ronda 90): este servidor entiende /media/<archivo>?r=a-b sin Range (y lo comprime); el cliente solo lo usa si lo ve.
  const body = JSON.stringify({ schema_version:1, version:`media:${hash.digest('hex')}`, ...extra, files:out });
  return { body, etag:`"${createHash('sha256').update(body).digest('hex')}"` };
}
/** Round 81: [u32le headerLength][JSON [[name,size],...]][file bytes in header order]; same bytes as GET /media/<dir>/<name>. */
async function buildBundle(mediaRoot, folder, ext) {
  const names = [], parts = [];
  for (const entry of (await readdir(folder, { withFileTypes:true })).sort((a, b) => (a.name < b.name ? -1 : 1))) {
    if (!entry.isFile() || !entry.name.endsWith('.' + ext)) continue;
    const full = await realpath(path.join(folder, entry.name));
    if (!inside(mediaRoot, full)) continue;
    const bytes = await readFile(full); names.push([entry.name, bytes.length]); parts.push(bytes);
  }
  const header = Buffer.from(JSON.stringify(names)), length = Buffer.alloc(4); length.writeUInt32LE(header.length);
  return Buffer.concat([length, header, ...parts]);
}
function inside(parent, child) {
  const relative = path.relative(parent, child);
  return !relative.startsWith(`..${path.sep}`) && relative !== '..' && !path.isAbsolute(relative);
}

// Round 85: one line per public request (never the key, never an IP): enough to see which request a remote browser failed on.
const ACCESS_LOG = process.env.PZWEB_ACCESS_LOG || '/tmp/pzweb-access.log';
function accessLog(req, status) {
  const url = req.url.replace(/([?&])k=[^&]*/, '$1k=*'), cookie = /(?:^|;\s*)pzweb_k=/.test(req.headers.cookie || '') ? 'cookie' : 'nocookie';
  const line = `${new Date().toISOString()} ${status} ${req.method} ${url.slice(0, 160)} ${cookie} ${req.headers['sec-fetch-dest'] || '-'} "${String(req.headers['user-agent'] || '').slice(0, 90)}"\n`;
  appendFile(ACCESS_LOG, line, () => {});
}

/** Ronda 90: respuesta de un tramo de /media (ver parseSlice). ETag débil con tamaño, mtime, tramo y codificación. */
async function sendSlice(req, res, file, resolved, info, { start, end }, mediaCompressor) {
  const ext = path.extname(resolved).toLowerCase(), length = end - start;
  const coding = !MEDIA_INCOMPRESSIBLE.has(ext) && length <= MEDIA_SLICE_COMPRESS_MAX ? acceptedCodings(req.headers['accept-encoding'])[0] : undefined;
  const read = async () => { const buf = Buffer.alloc(length); const { bytesRead } = await file.read(buf, 0, length, start); return buf.subarray(0, bytesRead); };
  const packed = coding ? await mediaCompressor.get(`${resolved}|${info.size}|${info.mtimeMs}|${start}-${end}`, coding, read, length) : null;
  const etag = `W/"${info.size.toString(16)}-${info.mtimeMs.toString(16)}-${start.toString(16)}-${end.toString(16)}${packed ? '-' + coding : ''}"`;
  const out = { ...headers, 'Content-Type': types.get(ext) || 'application/octet-stream', ETag: etag, 'Last-Modified': info.mtime.toUTCString(),
    'X-PZ-Slice': `${start}-${end}/${info.size}`, ...(MEDIA_INCOMPRESSIBLE.has(ext) ? {} : { Vary: 'Accept-Encoding' }),
    ...(packed ? { 'Content-Encoding': coding } : {}) };
  const candidates = req.headers['if-none-match']?.split(',').map(s => s.trim().replace(/^W\//, ''));
  if (candidates?.includes('*') || candidates?.includes(etag.replace(/^W\//, ''))) { res.writeHead(304, out); res.end(); return; }
  res.writeHead(200, { ...out, 'Content-Length': packed ? packed.length : length });
  if (req.method === 'HEAD') { res.end(); return; }
  if (packed) { res.end(packed); return; }
  await pipeline(file.createReadStream({ start, end: end - 1, autoClose: false }), res);
}

/** El <archivo>.br / .gz más conveniente (más nuevo que el original y más chico) para este Accept-Encoding, o null. Devuelve el handle abierto. */
async function pickPrecompressed(resolved, info, acceptEncoding) {
  for (const coding of acceptedCodings(acceptEncoding)) {
    const file = await open(`${resolved}.${coding === 'br' ? 'br' : 'gz'}`, 'r').catch(() => null);
    if (!file) continue;
    const stat = await file.stat();
    if (stat.isFile() && stat.mtimeMs >= info.mtimeMs && stat.size < info.size) return { file, size: stat.size, coding };
    await file.close();
  }
  return null;
}

export async function createDevServer({ webRoot = path.join(root, 'port/target/web'),
  resourceRoots = [], pzHome = process.env.PZ_HOME, packsRoot = process.env.PZ_PACKS || path.join(root, 'packs'),
  publicKey = process.env.PZWEB_PUBLIC_KEY || '', warmFile = process.env.PZWEB_WARM_FILE || '/tmp/pzweb-warm.json' } = {}) {
  const gate = createPublicGate({ key: publicKey });
  const warm = createWarmList({ file: warmFile });
  const compressor = createCompressor();
  // Ronda 90: tramos de /media y bundles de lotheader. Clave = ruta + tamaño + mtime + tramo: un archivo que cambia en PZ_HOME no reusa nada.
  const mediaCompressor = createCompressor({ maxBytes: Number(process.env.PZWEB_MEDIA_CACHE_MB || 256) << 20, minSaving: MEDIA_MIN_SAVING });
  let bridge = null;
  const board = createPlayerBoard({ bridges: () => bridge?.sessions.size ?? null });
  webRoot = await realpath(webRoot).catch(() => { throw new Error('Build missing. Run scripts/build.sh first.'); });
  let mediaRoot;
  if (pzHome) {
    const requestedHome = await realpath(pzHome);
    let home = requestedHome;
    try { await stat(path.join(home, 'ProjectZomboid64.json')); }
    catch (error) {
      if (error.code !== 'ENOENT') throw error;
      home = await realpath(path.join(home, 'projectzomboid'));
      if (!inside(requestedHome, home)) throw new Error('Nested game directory escapes PZ_HOME');
    }
    await stat(path.join(home, 'ProjectZomboid64.json'));
    const launcher = JSON.parse(await readFile(path.join(home, 'ProjectZomboid64.json'), 'utf8'));
    for (const entry of launcher.classpath || []) {
      const candidate = await realpath(path.resolve(home, entry));
      if (!inside(home, candidate)) throw new Error('Classpath escapes pinned copy');
      resourceRoots.push(candidate);
    }
    mediaRoot = await realpath(path.join(home, 'media'));
    if (!inside(home, mediaRoot)) throw new Error('PZ_HOME/media must stay inside the pinned copy');
  }
  const resources = await classpathResources(resourceRoots);
  let manifest = null;
  const bundles = new Map();
  const server = http.createServer(async (req, res) => {
    const respond = (status, message = '', extra = {}) => {
      res.writeHead(status, { ...headers, 'Content-Type': 'text/plain; charset=utf-8', ...extra });
      res.end(req.method === 'HEAD' ? undefined : message);
    };
    // Ronda 89: respuesta de texto en memoria con br/gzip si el navegador lo acepta (cacheada por `key`). `etag` (opcional) lleva el coding.
    const sendText = async (key, body, extra, etag, using = compressor) => {
      const coding = req.method === 'GET' || req.method === 'HEAD' ? acceptedCodings(req.headers['accept-encoding'])[0] : undefined;
      const packed = coding ? await using.get(key, coding, Buffer.isBuffer(body) ? body : Buffer.from(body)) : null;
      const out = { ...headers, ...extra, Vary: 'Accept-Encoding', ...(packed ? { 'Content-Encoding': coding } : {}) };
      const tag = etag && packed ? etag.replace(/"$/, `-${coding}"`) : etag;
      if (tag) out.ETag = tag;
      if (tag && req.headers['if-none-match'] === tag) { res.writeHead(304, out); res.end(); return; }
      const payload = packed ?? Buffer.from(body);
      res.writeHead(200, { ...out, 'Content-Length': payload.length });
      res.end(req.method === 'HEAD' ? undefined : payload);
    };
    try {
      // Round 84: public (tunnel) requests are authenticated by the key gate first; their same-origin rules use the tunnel host and https.
      const publicRequest = gate.isPublic(req);
      // /perf/report llega cada 5 s por jugador: no se anota para que el log siga mostrando quién juega y qué falló.
      if (publicRequest && !req.url.startsWith('/perf/report')) res.on('finish', () => accessLog(req, res.statusCode));
      if (gate.handle(req, res, headers)) return;
      if (new URL(req.url, 'http://x').pathname === '/favicon.ico') { res.writeHead(204, { ...headers }); res.end(); return; }
      const authority = publicRequest ? req.headers.host : `127.0.0.1:${server.address().port}`;
      const scheme = publicRequest ? 'https' : 'http';
      // a cross-site *navigation* (a link opened from a chat app) is fine; cross-site subresources are not
      const crossSite = req.headers['sec-fetch-site'] === 'cross-site' && !(publicRequest && req.headers['sec-fetch-mode'] === 'navigate');
      if (req.headers.host !== authority || crossSite ||
          (req.headers.origin && req.headers.origin !== `${scheme}://${authority}`)) {
        respond(403, 'Local same-origin requests only'); return;
      }
      if (!['GET', 'HEAD'].includes(req.method)) { respond(405, 'Read-only server', { Allow: 'GET, HEAD' }); return; }
      let pathname;
      try { pathname = decodeURIComponent(new URL(req.url, `http://${authority}`).pathname); }
      catch { respond(400, 'Invalid path'); return; }
      if (pathname.includes('\0') || pathname.includes('\\') || pathname.split('/').includes('..')) {
        respond(400, 'Invalid path'); return;
      }
      // La raíz es el juego: / y /index.html redirigen a /play.html con la query tal cual llegó (?k=… incluido). Va después del
      // gate (sin clave → 403, nunca un redirect) y del chequeo same-origin; el hash no llega al servidor y lo conserva index.html.
      if (pathname === '/' || pathname === '/index.html') {
        const query = req.url.indexOf('?');
        res.writeHead(302, { ...headers, Location: '/play.html' + (query < 0 ? '' : req.url.slice(query)), 'Cache-Control': 'no-store', 'Content-Length': 0 });
        res.end(); return;
      }
      if (pathname === '/warm-media.json') { // Round 84: ranges real sessions asked for (see dev-server/warm-list.mjs)
        const body = JSON.stringify(warm.list());
        await sendText(`warm|${warm.size()}`, body, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); return;
      }
      // Ronda 90: un tramo pedido sin Range (?r=a-b) se anota con el Range equivalente, igual que lo anotaba un cliente viejo:
      // la misma entrada sirve a los dos y la clave `${url}|${range}` de la caché de media no cambia.
      const sliceQuery = pathname.startsWith('/media/') && !req.headers.range ? /^(\d+)-(\d+)$/.exec(new URL(req.url, 'http://x').searchParams.get('r') ?? '') : null;
      const warmRange = req.headers.range || (sliceQuery && Number(sliceQuery[2]) > Number(sliceQuery[1]) ? `bytes=${sliceQuery[1]}-${Number(sliceQuery[2]) - 1}` : '');
      if (req.method === 'GET' && (pathname === '/media-bundle' || ((pathname.startsWith('/media/') || /^\/packs\/[a-f0-9]{64}\.bin$/.test(pathname)) && warmRange) && !/[?&]fill=1\b/.test(req.url))) warm.record(req.url, warmRange);
      if (pathname === '/perf/report') { // reporte de rendimiento de una pestaña (dev-server/players.mjs)
        const ok = board.report(new URL(req.url, 'http://x').searchParams);
        res.writeHead(ok ? 204 : 400, { ...headers, 'Cache-Control': 'no-store' }); res.end(); return;
      }
      if (pathname === '/perf') {
        const body = JSON.stringify(board.snapshot());
        res.writeHead(200, { ...headers, 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body), 'Cache-Control': 'no-store' });
        res.end(req.method === 'HEAD' ? undefined : body); return;
      }
      if (pathname === '/health') {
        res.writeHead(200, { ...headers, 'Content-Type': 'application/json' });
        res.end(req.method === 'HEAD' ? undefined : JSON.stringify({ mode: 'platform-smoke', gameMounted: !!mediaRoot }));
        return;
      }
      if (pathname === '/media-manifest.json') { // Round 79: every PZ_HOME/media file with its size, for on-demand reads
        if (!mediaRoot) { respond(404, 'PZ_HOME not mounted'); return; }
        manifest ??= buildManifest(mediaRoot, { slices: 1 });
        const { body, etag } = await manifest;
        await sendText(`manifest|${etag}`, body, { 'Content-Type': 'application/json' }, etag); return;
      }
      if (pathname === '/media-bundle') { // Round 81 (front 2): every direct child of media/<dir> with one extension, in one response
        if (!mediaRoot) { respond(404, 'PZ_HOME not mounted'); return; }
        const q = new URL(req.url, `http://${authority}`).searchParams;
        const dir = q.get('dir') ?? '', ext = q.get('ext') ?? '';
        if (!/^[A-Za-z0-9]{1,16}$/.test(ext) || dir.split('/').includes('..') || dir.startsWith('/') || dir === '') { respond(400, 'Invalid bundle request'); return; }
        const folder = await realpath(path.join(mediaRoot, dir)).catch(() => null);
        if (!folder || !inside(mediaRoot, folder)) { respond(404, 'No such directory'); return; }
        const key = `${folder}\0${ext}`;
        bundles.has(key) || bundles.set(key, buildBundle(mediaRoot, folder, ext));
        const body = await bundles.get(key);
        // Ronda 90: los lotheader bajan a ~0,11 con br; el bundle se arma una vez por proceso, así que la clave es la del bundle.
        await sendText(`bundle|${key}`, body, { 'Content-Type':'application/octet-stream', 'Cache-Control':'no-store' }, undefined, mediaCompressor); return;
      }
      if (pathname === '/media-ranges') { // Ronda 89: varias entradas del warm-list en una respuesta (ver dev-server/media-ranges.mjs)
        if (!mediaRoot) { respond(404, 'PZ_HOME not mounted'); return; }
        const indices = parseIndices(new URL(req.url, `http://${authority}`).searchParams.get('i'));
        if (!indices) { respond(400, 'Invalid ranges request'); return; }
        const { body } = await buildRangesResponse({ mediaRoot, entries: warm.list(), indices,
          bundle: (folder, ext) => { const key = `${folder}\0${ext}`; bundles.has(key) || bundles.set(key, buildBundle(mediaRoot, folder, ext)); return bundles.get(key); } });
        res.writeHead(200, { ...headers, 'Content-Type':'application/octet-stream', 'Content-Length':body.length, 'Cache-Control':'no-store' });
        res.end(req.method === 'HEAD' ? undefined : body); return;
      }
      if (pathname === '/classpath/lookup') {
        const name = new URL(req.url, `http://${authority}`).searchParams.get('name');
        const found = await resources.lookup(name);
        res.writeHead(200, { ...headers, 'Content-Type':'application/json' });
        res.end(req.method === 'HEAD' ? undefined : JSON.stringify(found.map(url => `http://${authority}${url}`))); return;
      }
      if (pathname.startsWith('/classpath/resource/')) {
        const match = /^\/classpath\/resource\/(\d+)\/(.+)$/.exec(pathname);
        const bytes = match ? await resources.read(Number(match[1]), match[2]) : null;
        if (!bytes) { respond(404, 'Classpath resource not found'); return; }
        const etag = '"' + createHash('sha256').update(bytes).digest('hex') + '"';
        if (req.headers['if-none-match'] === etag) { res.writeHead(304, { ...headers, ETag:etag }); res.end(); return; }
        const range = req.headers.range && (!req.headers['if-range'] || req.headers['if-range'] === etag) ? parseRange(req.headers.range, bytes.length) : undefined;
        if (range === null) { respond(416, 'Unsatisfiable byte range', { 'Content-Range':`bytes */${bytes.length}` }); return; }
        const content = range ? bytes.subarray(range.start, range.end+1) : bytes;
        res.writeHead(range ? 206 : 200, { ...headers, 'Content-Type':'application/octet-stream', 'Content-Length':content.length, 'Accept-Ranges':'bytes', ETag:etag, ...(range ? {'Content-Range':`bytes ${range.start}-${range.end}/${bytes.length}`} : {}) });
        res.end(req.method === 'HEAD' ? undefined : content); return;
      }
      let base = webRoot, relative = pathname.slice(1);
      if (pathname === '/media' || pathname.startsWith('/media/')) {
        if (!mediaRoot) { respond(404, 'PZ_HOME not mounted'); return; }
        base = mediaRoot; relative = pathname.slice('/media/'.length);
      } else if (pathname.startsWith('/packs/')) {
        base = await realpath(packsRoot); relative = pathname.slice('/packs/'.length);
      }
      const resolved = await realpath(path.resolve(base, relative));
      if (!inside(base, resolved)) { respond(403, 'Path outside served root'); return; }
      const file = await open(resolved, 'r');
      try {
        const info = await file.stat();
        if (!info.isFile()) { respond(404, 'No directory listing'); return; }
        // Metadata validator is deliberately weak; never accept it as a strong If-Range validator.
        // Ronda 87/89: boot.wasm / *.wasm y packs con nombre por hash pueden tener un <archivo>.br y/o <archivo>.gz al lado
        // (scripts/precompress.mjs, fuera de git). Se sirve el primero que el navegador acepte (br antes que gzip), siempre que sea
        // más nuevo que el original y más chico. Con Range se sirve el original (un rango sobre un cuerpo codificado no significa lo
        // mismo: los chunks de un pack perezoso se piden con Range). El ETag lleva el codec para no mezclar los cuerpos.
        const isPack = pathname.startsWith('/packs/') && /^[a-f0-9]{64}\.bin$/.test(relative);
        // Ronda 90: tramo de /media sin Range (?r=a-b, fin exclusivo): 200 con exactamente esos bytes, comprimido con br/gzip si el
        // navegador lo acepta y el tipo lo vale. Con Range (clientes viejos, packs perezosos) todo sigue como antes y sin comprimir:
        // Chrome manda `Accept-Encoding: identity` en todo pedido con Range, así que comprimir ahí no es posible.
        const slice = base === mediaRoot && !req.headers.range ? parseSlice(new URL(req.url, 'http://x').searchParams.get('r'), info.size) : undefined;
        if (slice === null) { respond(416, 'Unsatisfiable slice', { 'Content-Range': `bytes */${info.size}` }); return; }
        if (slice) { await sendSlice(req, res, file, resolved, info, slice, mediaCompressor); return; }
        const compressible = path.extname(resolved).toLowerCase() === '.wasm' || isPack;
        const encoded = compressible && !req.headers.range ? await pickPrecompressed(resolved, info, req.headers['accept-encoding']) : null;
        const etag = `W/"${info.size.toString(16)}-${info.mtimeMs.toString(16)}${encoded ? '-' + encoded.coding : ''}"`;
        const modified = info.mtime.toUTCString();
        const fileHeaders = { ...headers, 'Content-Type': types.get(path.extname(resolved).toLowerCase()) || 'application/octet-stream',
          'Accept-Ranges': 'bytes', ETag: etag, 'Last-Modified': modified, ...(compressible ? { Vary: 'Accept-Encoding' } : {}),
          ...(encoded ? { 'Content-Encoding': encoded.coding } : {}) };
        // Packs are content-addressed (<sha256>.bin, checked again by port/runtime/packs.mjs): a name never changes bytes,
        // so the browser may keep them without revalidating. index.json and everything else keep no-cache.
        if (isPack) fileHeaders['Cache-Control'] = 'public, max-age=31536000, immutable';
        // Texto chico (JS, JSON, HTML…) servido desde disco: se comprime una vez en memoria (compress.mjs). Los packs/wasm usan lo precomprimido.
        let text = null;
        if (!encoded && !req.headers.range && !compressible && TEXTUAL.has(path.extname(resolved).toLowerCase()) && base !== mediaRoot
            && info.size >= 1024 && info.size <= 32 << 20) {
          const coding = acceptedCodings(req.headers['accept-encoding'])[0];
          if (coding) {
            const packed = await compressor.get(`${resolved}|${info.size}|${info.mtimeMs}`, coding, await file.readFile());
            if (packed) { text = { body: packed, coding }; fileHeaders['Content-Encoding'] = coding; fileHeaders.Vary = 'Accept-Encoding'; fileHeaders.ETag = etag.replace(/"$/, `-${coding}"`); }
          }
        }
        const sentEtag = fileHeaders.ETag;
        const candidates = req.headers['if-none-match']?.split(',').map(s => s.trim().replace(/^W\//, ''));
        if (candidates?.includes('*') || candidates?.includes(sentEtag.replace(/^W\//, ''))) {
          await encoded?.file.close(); res.writeHead(304, fileHeaders); res.end(); return;
        }
        if (text) {
          res.writeHead(200, { ...fileHeaders, 'Content-Length': text.body.length });
          res.end(req.method === 'HEAD' ? undefined : text.body); return;
        }
        if (encoded) {
          try {
            res.writeHead(200, { ...fileHeaders, 'Content-Length': encoded.size });
            if (req.method === 'HEAD') { res.end(); return; }
            await pipeline(encoded.file.createReadStream({ autoClose: false }), res); return;
          } finally { await encoded.file.close(); }
        }
        let range;
        const ifRange = req.headers['if-range'];
        const useRange = req.headers.range && (!ifRange ||
          (!ifRange.includes('"') && Date.parse(ifRange) >= Math.floor(info.mtimeMs / 1000) * 1000));
        if (useRange) {
          range = parseRange(req.headers.range, info.size);
          if (!range) { respond(416, 'Unsatisfiable byte range', { 'Content-Range': `bytes */${info.size}` }); return; }
        }
        res.writeHead(range ? 206 : 200, { ...fileHeaders,
          'Content-Length': range ? range.end - range.start + 1 : info.size,
          ...(range ? { 'Content-Range': `bytes ${range.start}-${range.end}/${info.size}` } : {}),
        });
        if (req.method === 'HEAD' || info.size === 0) { res.end(); return; }
        await pipeline(file.createReadStream({ ...(range || {}), autoClose: false }), res);
      } finally { await file.close(); }
    } catch (error) {
      if (res.headersSent) { res.destroy(); return; }
      if (['ENOENT', 'ENOTDIR'].includes(error.code)) respond(404, 'Not found');
      else if (error.code === 'EACCES') respond(403, 'Not readable');
      else { console.error('dev-server:', error.message); respond(500, 'Local server error'); }
    }
  });
  bridge = attachBridge(server, { log: message => console.log('mp-bridge:', message), gate });
  return server;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const port = Number(process.env.PORT || 8090);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid PORT');
  // PZWEB_WEB_ROOT: raiz web alternativa (pruebas en un puerto temporal sin tocar port/target/web, que sirve el 8098 en vivo)
  const server = await createDevServer(process.env.PZWEB_WEB_ROOT ? { webRoot: process.env.PZWEB_WEB_ROOT } : {});
  server.listen(port, '127.0.0.1', () => console.log(`PZ platform development: http://127.0.0.1:${port}`));
  server.on('error', error => { console.error(error.message); process.exitCode = 1; });
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close());
}
