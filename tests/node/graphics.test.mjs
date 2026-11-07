// Calidad gráfica del cliente (port/runtime/graphics.mjs). Archivos sintéticos propios: nada del juego.
import test from 'node:test';
import assert from 'node:assert/strict';
import { VirtualFileSystem } from '../../port/runtime/vfs.mjs';
import { DEFAULT_QUALITY, QUALITY_STORAGE_KEY, MINIMUM_OPTIONS, OPTIONS_PATH, normalizeQuality, resolveQuality, withOptions,
  applyQuality } from '../../port/runtime/graphics.mjs';

const memoryStorage = (initial = {}) => {
  const map = new Map(Object.entries(initial));
  return { getItem: k => map.has(k) ? map.get(k) : null, setItem: (k, v) => map.set(k, String(v)), removeItem: k => map.delete(k), map };
};
const PACK_INI = '# comentario\nversion=8\nwidth=1280\nheight=720\nlanguage=ES\ntexture2x=false\ndoVideoEffects=false\n';
const parse = text => new Map(text.split('\n').filter(l => l && !l.startsWith('#')).map(l => [l.split('=')[0], l.split('=')[1]]));

test('mínima por defecto; la URL elige y se recuerda; auto la borra', () => {
  assert.equal(DEFAULT_QUALITY, 'minima');
  assert.deepEqual(resolveQuality({}), { quality: 'minima', source: 'default' });
  const storage = memoryStorage();
  assert.deepEqual(resolveQuality({ search: '?autojoin=1&calidad=juego', storage }), { quality: 'juego', source: 'url' });
  assert.equal(storage.map.get(QUALITY_STORAGE_KEY), 'juego');
  assert.deepEqual(resolveQuality({ search: '?autojoin=1', storage }), { quality: 'juego', source: 'saved' });
  assert.deepEqual(resolveQuality({ search: '?calidad=auto', storage }), { quality: 'minima', source: 'default' });
  assert.equal(storage.map.has(QUALITY_STORAGE_KEY), false);
  assert.deepEqual(resolveQuality({ search: '?calidad=ultra', storage: memoryStorage({ [QUALITY_STORAGE_KEY]: 'juego' }) }),
    { quality: 'juego', source: 'saved' });
  assert.equal(normalizeQuality('Mínima'), 'minima');
  const throwing = { getItem() { throw new Error('bloqueado'); }, setItem() { throw new Error('bloqueado'); }, removeItem() { throw new Error('x'); } };
  assert.deepEqual(resolveQuality({ search: '?calidad=juego', storage: throwing }), { quality: 'juego', source: 'url' });
});

test('withOptions reemplaza, quita repetidas, agrega las que faltan y conserva el resto', () => {
  const text = withOptions(PACK_INI + 'water=0\r\nwater=1\n', [['water', '2'], ['3DGroundItem', 'false']]);
  const lines = text.split('\n');
  assert.equal(lines.filter(l => l.startsWith('water=')).length, 1);
  assert.ok(lines.includes('water=2\r'));
  assert.ok(lines.includes('3DGroundItem=false'));
  assert.ok(text.startsWith('# comentario\nversion=8\n'));
  assert.ok(text.endsWith('\n'));
  assert.equal(parse(text).get('language'), 'ES');
  assert.throws(() => withOptions(PACK_INI, [['a', 'b=c']]));
});

test('applyQuality reescribe el options.ini de solo lectura con todas las opciones mínimas', () => {
  const vfs = new VirtualFileSystem();
  vfs.mountReadOnly(OPTIONS_PATH, new TextEncoder().encode(PACK_INI));
  const r = applyQuality(vfs, { quality: 'minima', source: 'default' });
  assert.equal(r.applied, true); assert.equal(r.changed, true);
  const ini = parse(new TextDecoder().decode(vfs.readFile(OPTIONS_PATH)));
  for (const [k, v] of MINIMUM_OPTIONS) assert.equal(ini.get(k), v, k);
  assert.equal(ini.get('language'), 'ES');
  if (vfs.isReadOnlyMounted) assert.equal(vfs.isReadOnlyMounted(OPTIONS_PATH), true);
  assert.equal(applyQuality(vfs, { quality: 'minima', source: 'default' }).changed, false);
});

test('juego o sin options.ini: no toca nada', () => {
  const vfs = new VirtualFileSystem();
  assert.equal(applyQuality(vfs, { quality: 'minima', source: 'default' }).applied, false);
  vfs.mountReadOnly(OPTIONS_PATH, new TextEncoder().encode(PACK_INI));
  assert.deepEqual(applyQuality(vfs, { quality: 'juego', source: 'url' }), { applied: false, quality: 'juego', source: 'url', reason: 'valores del juego' });
  assert.equal(new TextDecoder().decode(vfs.readFile(OPTIONS_PATH)), PACK_INI);
});
