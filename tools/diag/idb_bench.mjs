// Ronda 81 (frente 2): rendimiento de IndexedDB para registros grandes (decidir el presupuesto de texcache).
// Uso: flock /tmp/pz80-locks/chrome.lock node tools/diag/idb_bench.mjs [MB=700] [REG_MB=5.3]
import { chromium } from '@playwright/test';
import { launchOptions } from './chrome-flags.mjs';
const [mb = '700', reg = '5.3'] = process.argv.slice(2);
const ctx = await chromium.launchPersistentContext('/tmp/pz81/f2/idbprof', { ...launchOptions(), viewport: { width: 800, height: 600 } });
try {
  const page = ctx.pages()[0] || await ctx.newPage();
  await page.goto(`http://127.0.0.1:${process.env.PZ_DIAG_PORT || 8098}/health`);
  const r = await page.evaluate(async ([mb, reg]) => {
    const n = Math.round(mb / reg), size = Math.round(reg * 1048576);
    const req = indexedDB.open('idbbench', 1); req.onupgradeneeded = () => { try { req.result.deleteObjectStore('s'); } catch {} req.result.createObjectStore('s'); };
    const db = await new Promise(r => { req.onsuccess = () => r(req.result); });
    const P = q => new Promise((res, rej) => { q.onsuccess = () => res(q.result); q.onerror = () => rej(q.error); });
    const done = tx => new Promise((res, rej) => { tx.oncomplete = res; tx.onabort = () => rej(tx.error); });
    await P(db.transaction('s', 'readwrite').objectStore('s').clear());
    const out = { n, size };
    let t = performance.now();
    for (let i = 0; i < n; i++) { const b = new Uint8Array(size); for (let k = 0; k < size; k += 4096) b[k] = (i * 31 + k) & 255; const tx = db.transaction('s', 'readwrite'); tx.objectStore('s').put({ meta: new Uint8Array(64).buffer, levels: [b.buffer] }, 'k' + i); await done(tx); }
    out.writeMs = Math.round(performance.now() - t);
    t = performance.now(); let bytes = 0;
    for (let i = 0; i < n; i++) { const v = await P(db.transaction('s').objectStore('s').get('k' + i)); bytes += v.levels[0].byteLength; }
    out.readSeqMs = Math.round(performance.now() - t); out.readMB = Math.round(bytes / 1048576);
    t = performance.now();
    await Promise.all(Array.from({ length: n }, (_, i) => P(db.transaction('s').objectStore('s').get('k' + i))));
    out.readParMs = Math.round(performance.now() - t);
    // lectura + copia a un Uint8Array (lo que hace el juego)
    const target = new Uint8Array(size); t = performance.now();
    for (let i = 0; i < n; i++) { const v = await P(db.transaction('s').objectStore('s').get('k' + i)); target.set(new Uint8Array(v.levels[0])); }
    out.readCopyMs = Math.round(performance.now() - t);
    const e = await navigator.storage.estimate(); out.usageMB = Math.round(e.usage / 1048576); out.quotaMB = Math.round(e.quota / 1048576);
    db.close(); indexedDB.deleteDatabase('idbbench');
    return out;
  }, [+mb, +reg]);
  console.log(JSON.stringify(r));
} finally { await ctx.close(); }
