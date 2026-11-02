// Ronda 89 (frente B): /media-ranges sirve muchas entradas del warm-list en UNA respuesta, para que la precarga haga un puñado de
// pedidos en vez de ~4700 (por el túnel cada uno es un viaje de ida y vuelta). El cliente manda solo ÍNDICES del warm-list; la ruta
// y el rango salen de la lista del servidor, nunca del cliente, y cada una pasa las mismas comprobaciones que /media y /media-bundle
// (sin '..', sin salir de PZ_HOME/media tras realpath). Los bytes de cada entrada son los de GET /media/<ruta> con ese Range, o los
// de /media-bundle?dir&ext, así que la clave `url|Range` de vfs.prefetched / mediacache queda exacta.
//
// Cuerpo: [u32le largoCabecera][JSON {served, entries:[[url, range, size], ...]}][bytes de las entradas con size >= 0, en orden].
// size -1 = no disponible (el cliente la cuenta como fallida y el juego la lee como siempre). `served` = cuántos de los índices pedidos
// se resolvieron antes de llegar al tope de bytes de la respuesta (el cliente pide el resto en otro lote).
// Ronda 91: server.mjs comprime el cuerpo entero (br/gzip según Accept-Encoding); el formato es el de los bytes ya descomprimidos.
// `compressibleBytes` cuenta los bytes de entradas cuya extensión no está en `incompressible`, para no gastar CPU en lotes de png/pack.
import { realpath, open } from 'node:fs/promises';
import path from 'node:path';

export const MAX_INDICES = 512;
const MAX_ENTRY_BYTES = 64 << 20;

const inside = (parent, child) => {
  const relative = path.relative(parent, child);
  return !relative.startsWith(`..${path.sep}`) && relative !== '..' && !path.isAbsolute(relative);
};

/** Extensión en minúsculas ('.png') de una entrada del warm-list; los bundles cuentan con su `ext`. */
export function extensionOf(url) {
  try {
    const parsed = new URL(url, 'http://x');
    return parsed.pathname === '/media-bundle' ? `.${(parsed.searchParams.get('ext') ?? '').toLowerCase()}` : path.extname(parsed.pathname).toLowerCase();
  } catch { return ''; }
}

/** "3,4,9" -> [3,4,9]; null si no es una lista de enteros >= 0 (o pasa de MAX_INDICES). */
export function parseIndices(text) {
  if (typeof text !== 'string' || !/^\d{1,6}(,\d{1,6})*$/.test(text)) return null;
  const list = text.split(',').map(Number);
  return list.length <= MAX_INDICES ? list : null;
}

// Mismo criterio que parseRange() de server.mjs, pero con el tamaño ya conocido.
function rangeOf(header, size) {
  const m = /^bytes=(\d*)-(\d*)$/.exec(header);
  if (!m || (!m[1] && !m[2]) || size === 0) return null;
  let start, end;
  if (!m[1]) { const n = Number(m[2]); if (!Number.isSafeInteger(n) || n <= 0) return null; start = Math.max(0, size - n); end = size - 1; }
  else {
    start = Number(m[1]); end = m[2] ? Number(m[2]) : size - 1;
    if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start >= size || end < start) return null;
    end = Math.min(end, size - 1);
  }
  return { start, end };
}

async function readEntry({ mediaRoot, bundle }, url, range) {
  let parsed;
  try { parsed = new URL(url, 'http://x'); } catch { return null; }
  if (parsed.pathname === '/media-bundle') {
    const dir = parsed.searchParams.get('dir') ?? '', ext = parsed.searchParams.get('ext') ?? '';
    if (!/^[A-Za-z0-9]{1,16}$/.test(ext) || dir === '' || dir.startsWith('/') || dir.includes('\0') || dir.includes('\\') || dir.split('/').includes('..')) return null;
    const folder = await realpath(path.join(mediaRoot, dir)).catch(() => null);
    return folder && inside(mediaRoot, folder) ? bundle(folder, ext) : null;
  }
  if (!parsed.pathname.startsWith('/media/')) return null;
  let relative;
  try { relative = decodeURIComponent(parsed.pathname.slice('/media/'.length)); } catch { return null; }
  if (relative === '' || relative.includes('\0') || relative.includes('\\') || relative.split('/').includes('..')) return null;
  const resolved = await realpath(path.resolve(mediaRoot, relative)).catch(() => null);
  if (!resolved || !inside(mediaRoot, resolved)) return null;
  const file = await open(resolved, 'r').catch(() => null);
  if (!file) return null;
  try {
    const info = await file.stat();
    if (!info.isFile()) return null;
    if (range === '' && info.size === 0) return Buffer.alloc(0);
    const span = range ? rangeOf(range, info.size) : { start: 0, end: info.size - 1 };
    if (!span) return null;
    const length = span.end - span.start + 1;
    if (length > MAX_ENTRY_BYTES) return null;
    const out = Buffer.alloc(length);
    const { bytesRead } = await file.read(out, 0, length, span.start);
    return bytesRead === length ? out : null;
  } finally { await file.close(); }
}

/**
 * entries: warm.list() ([[url, range], ...]); indices: enteros pedidos por el cliente.
 * Devuelve { body: Buffer, served, bytes }. maxBytes acota la respuesta (siempre se resuelve al menos una entrada).
 */
export async function buildRangesResponse({ mediaRoot, bundle, entries, indices, maxBytes = 24 << 20, incompressible = new Set() }) {
  const meta = [], parts = [];
  let bytes = 0, served = 0, compressibleBytes = 0;
  for (const index of indices) {
    if (served > 0 && bytes >= maxBytes) break;
    served++;
    const entry = entries[index];
    if (!entry) { meta.push(['', '', -1]); continue; }
    const [url, range] = entry;
    let data = null;
    try { data = await readEntry({ mediaRoot, bundle }, url, range); } catch { data = null; }
    if (!data) { meta.push([url, range, -1]); continue; }
    meta.push([url, range, data.length]); parts.push(data); bytes += data.length;
    if (!incompressible.has(extensionOf(url))) compressibleBytes += data.length;
  }
  const header = Buffer.from(JSON.stringify({ served, entries: meta })), length = Buffer.alloc(4);
  length.writeUInt32LE(header.length);
  return { body: Buffer.concat([length, header, ...parts]), served, bytes, compressibleBytes };
}
