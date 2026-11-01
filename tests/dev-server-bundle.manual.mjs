// Ronda 81 (frente 2): prueba manual (necesita PZ_HOME fijado): /media-bundle devuelve los mismos bytes que GET /media/... y rechaza rutas inválidas.
import { createDevServer } from "../dev-server/server.mjs";
import { readFileSync } from 'node:fs';
const home = '/home/agente47/pz-pinned/25485521/projectzomboid';
const server = await createDevServer({ pzHome: home, packsRoot: '/datos/Projects/02-Productos-propios/pz-web/build/real-browser/packs' });
await new Promise(r => server.listen(0, '127.0.0.1', r)); const port = server.address().port;
const get = async (u) => { const r = await fetch(`http://127.0.0.1:${port}${u}`); return [r.status, Buffer.from(await r.arrayBuffer())]; };
let t = performance.now();
const [st, b] = await get('/media-bundle?dir=' + encodeURIComponent('maps/Muldraugh, KY') + '&ext=lotheader');
console.log('status', st, 'bytes', b.length, 'ms', Math.round(performance.now() - t));
const hl = b.readUInt32LE(0), list = JSON.parse(b.subarray(4, 4 + hl).toString()); let off = 4 + hl, bad = 0;
for (const [name, size] of list) { const f = readFileSync(`${home}/media/maps/Muldraugh, KY/${name}`); if (!f.equals(b.subarray(off, off + size))) bad++; off += size; }
console.log('files', list.length, 'mismatches', bad, 'end', off === b.length);
for (const u of ['/media-bundle?dir=..%2F..&ext=lotheader', '/media-bundle?dir=maps&ext=a/b', '/media-bundle?dir=%2Fetc&ext=txt', '/media-bundle?dir=maps%2Fnope&ext=lotheader']) console.log(u, (await get(u))[0]);
server.close();
