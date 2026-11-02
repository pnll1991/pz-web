// Memo persistente de la traducción GLSL (port/runtime/glslcache.mjs): misma salida que translateShader, sin volver a
// traducir en la carga siguiente, invalidado por la versión del traductor y nunca guardando errores.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ShaderCache, translatorVersion, TRANSLATOR_FILES } from '../../port/runtime/glslcache.mjs';
import { translateShader } from '../../port/runtime/glsl.mjs';
import { GLBridge } from '../../port/runtime/gl.mjs';
import { FakeIndexedDB } from './fake-storage.mjs';

// Shader propio de escritorio (GLSL 1.20) con un uniform inicializado y una promoción int -> float.
const vertex = '#version 120\nuniform float scale = 2.0;\nattribute vec3 position;\nvoid main() { gl_Position = vec4(position * scale * 1, 1.0); }\n';
const counting = () => { const calls = []; return { calls, translate: (source, stage) => { calls.push(stage); return translateShader(source, stage); } }; };

test('la segunda carga devuelve lo mismo sin volver a traducir, y cada acierto es una copia', async () => {
  const idb = new FakeIndexedDB(), first = counting();
  const a = await new ShaderCache({ indexedDBImpl: idb, translate: first.translate }).load('glsl:v1');
  const translated = a.translate(vertex, 'vertex');
  assert.deepEqual(translated, translateShader(vertex, 'vertex'));
  await a.flush();
  assert.deepEqual([a.stats.miss, a.stats.stored], [1, 1]);

  const second = counting();
  const b = await new ShaderCache({ indexedDBImpl: idb, translate: second.translate }).load('glsl:v1');
  assert.equal(b.stats.loaded, 1);
  const hit = b.translate(vertex, 'vertex');
  assert.deepEqual(hit, translated);
  assert.deepEqual(second.calls, [], 'no se tradujo de nuevo');
  hit.uniformDefaults.push({ name: 'x' });
  assert.equal(b.translate(vertex, 'vertex').uniformDefaults.length, translated.uniformDefaults.length, 'el acierto es una copia');
});

test('otra versión vacía el almacén; los errores no se guardan; verify compara', async () => {
  const idb = new FakeIndexedDB();
  const a = await new ShaderCache({ indexedDBImpl: idb }).load('glsl:v1');
  a.translate(vertex, 'vertex'); await a.flush();
  assert.throws(() => a.translate('void main( {', 'fragment'));
  await a.flush();
  assert.equal(a.stats.stored, 1);
  const b = await new ShaderCache({ indexedDBImpl: idb }).load('glsl:v2');
  assert.deepEqual([b.stats.loaded, b.stats.cleared], [0, 1]);
  const verify = counting();
  const c = await new ShaderCache({ indexedDBImpl: idb, translate: verify.translate, mode: 2 }).load('glsl:v2');
  c.translate(vertex, 'vertex'); await c.flush();
  const d = await new ShaderCache({ indexedDBImpl: idb, translate: verify.translate, mode: 2 }).load('glsl:v2');
  assert.equal(d.stats.loaded, 0, 'verify no guarda');
});

test('modos por URL, sin IndexedDB queda apagada y GLBridge usa la caché instalada', () => {
  assert.equal(ShaderCache.fromLocation('?glslcache=0', { indexedDBImpl: {} }).mode, 0);
  assert.equal(ShaderCache.fromLocation('?nocache=1', { indexedDBImpl: {} }).mode, 0);
  assert.equal(ShaderCache.fromLocation('?glslcache=verify', { indexedDBImpl: {} }).mode, 2);
  assert.equal(ShaderCache.fromLocation('', { indexedDBImpl: {} }).mode, 1);
  const off = new ShaderCache({ indexedDBImpl: undefined });
  assert.equal(off.mode, 0);
  assert.deepEqual(off.translate(vertex, 'vertex'), translateShader(vertex, 'vertex'));
  // GLBridge envuelve sus métodos con guardas de estado: se prueba el método sobre un this mínimo.
  const translate = GLBridge.prototype.translateGlsl;
  assert.notEqual(GLBridge.prototype.translateGlsl, GLBridge.prototype.translate, 'no pisa glTranslate');
  assert.deepEqual(translate.call({ shaderCache: null }, vertex, 'vertex'), translateShader(vertex, 'vertex'), 'sin caché: translateShader');
  const seen = [];
  const bridge = { shaderCache: { translate: (source, stage) => { seen.push(stage); return { source: 'memo' }; } } };
  assert.equal(translate.call(bridge, vertex, 'vertex').source, 'memo');
  assert.deepEqual(seen, ['vertex']);
});

test('versión del traductor: cambia si cambia cualquier archivo y falla si falta uno', async () => {
  const files = { '/runtime/glsl.mjs': 'a', '/vendor/glsl-parser/index.js': 'b' };
  const fetchImpl = async url => url in files ? new Response(files[url]) : new Response('', { status: 404 });
  const list = Object.keys(files);
  const v1 = await translatorVersion(fetchImpl, list);
  assert.match(v1, /^glsl:[a-f0-9]{64}$/);
  files['/vendor/glsl-parser/index.js'] = 'c';
  assert.notEqual(await translatorVersion(fetchImpl, list), v1);
  await assert.rejects(translatorVersion(fetchImpl, [...list, '/falta.js']), /HTTP 404/);
  assert.ok(TRANSLATOR_FILES.includes('/runtime/glsl.mjs') && TRANSLATOR_FILES.some(f => f.endsWith('parser/parser.js')));
});
