import http from 'node:http';
import { classpathResources } from './classpath.mjs';
import { attachBridge } from './ws-bridge.mjs';
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
const headers = {
  'Cross-Origin-Opener-Policy': 'same-origin',
  'Cross-Origin-Embedder-Policy': 'require-corp',
  'Cross-Origin-Resource-Policy': 'same-origin',
  'X-Content-Type-Options': 'nosniff',
  'Cache-Control': 'no-cache',
};

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

/** Round 79: [[path relative to media/, size], ...] for every regular file (symlinks followed only inside media). */
async function buildManifest(mediaRoot) {
  const out = [];
  async function walk(dir, rel) {
    for (const entry of await readdir(dir, { withFileTypes:true })) {
      const full = path.join(dir, entry.name), name = rel ? `${rel}/${entry.name}` : entry.name;
      if (entry.isDirectory()) await walk(full, name);
      else if (entry.isFile()) out.push([name, (await stat(full)).size]);
    }
  }
  await walk(mediaRoot, '');
  return JSON.stringify({ schema_version:1, files:out });
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

export async function createDevServer({ webRoot = path.join(root, 'port/target/web'),
  resourceRoots = [], pzHome = process.env.PZ_HOME, packsRoot = process.env.PZ_PACKS || path.join(root, 'packs') } = {}) {
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
    try {
      const authority = `127.0.0.1:${server.address().port}`;
      if (req.headers.host !== authority || req.headers['sec-fetch-site'] === 'cross-site' ||
          (req.headers.origin && req.headers.origin !== `http://${authority}`)) {
        respond(403, 'Local same-origin requests only'); return;
      }
      if (!['GET', 'HEAD'].includes(req.method)) { respond(405, 'Read-only server', { Allow: 'GET, HEAD' }); return; }
      let pathname;
      try { pathname = decodeURIComponent(new URL(req.url, `http://${authority}`).pathname); }
      catch { respond(400, 'Invalid path'); return; }
      if (pathname.includes('\0') || pathname.includes('\\') || pathname.split('/').includes('..')) {
        respond(400, 'Invalid path'); return;
      }
      if (pathname === '/health') {
        res.writeHead(200, { ...headers, 'Content-Type': 'application/json' });
        res.end(req.method === 'HEAD' ? undefined : JSON.stringify({ mode: 'platform-smoke', gameMounted: !!mediaRoot }));
        return;
      }
      if (pathname === '/media-manifest.json') { // Round 79: every PZ_HOME/media file with its size, for on-demand reads
        if (!mediaRoot) { respond(404, 'PZ_HOME not mounted'); return; }
        manifest ??= buildManifest(mediaRoot);
        const body = await manifest;
        res.writeHead(200, { ...headers, 'Content-Type':'application/json', 'Content-Length':Buffer.byteLength(body) });
        res.end(req.method === 'HEAD' ? undefined : body); return;
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
      let base = webRoot, relative = pathname === '/' ? 'index.html' : pathname.slice(1);
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
        const etag = `W/"${info.size.toString(16)}-${info.mtimeMs.toString(16)}"`;
        const modified = info.mtime.toUTCString();
        const fileHeaders = { ...headers, 'Content-Type': types.get(path.extname(resolved).toLowerCase()) || 'application/octet-stream',
          'Accept-Ranges': 'bytes', ETag: etag, 'Last-Modified': modified };
        const candidates = req.headers['if-none-match']?.split(',').map(s => s.trim().replace(/^W\//, ''));
        if (candidates?.includes('*') || candidates?.includes(etag.replace(/^W\//, ''))) {
          res.writeHead(304, fileHeaders); res.end(); return;
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
  attachBridge(server, { log: message => console.log('mp-bridge:', message) });
  return server;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const port = Number(process.env.PORT || 8090);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid PORT');
  const server = await createDevServer();
  server.listen(port, '127.0.0.1', () => console.log(`PZ platform development: http://127.0.0.1:${port}`));
  server.on('error', error => { console.error(error.message); process.exitCode = 1; });
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close());
}
