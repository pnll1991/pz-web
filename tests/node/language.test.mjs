// Idioma del cliente (port/runtime/language.mjs), del dev server (PZWEB_LANGUAGE en /media-manifest.json) y del servidor PZ
// (scripts/mp-server.sh apply-config). Todo con archivos sintéticos propios: ningún texto ni traducción del juego.
import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { once } from 'node:events';
import { VirtualFileSystem } from '../../port/runtime/vfs.mjs';
import { DEFAULT_LANGUAGE, LANGUAGE_STORAGE_KEY, normalizeLanguage, resolveLanguage, withLanguage, languageOf, applyLanguage,
  translatePath, OPTIONS_PATH } from '../../port/runtime/language.mjs';
import { createDevServer, languageSetting } from '../../dev-server/server.mjs';

const memoryStorage = (initial = {}) => {
  const map = new Map(Object.entries(initial));
  return { getItem: k => map.has(k) ? map.get(k) : null, setItem: (k, v) => map.set(k, String(v)), removeItem: k => map.delete(k), map };
};
// Mismo formato que tools/pack/boot_options.py (comentarios + version=8 + language=EN).
const PACK_INI = '# comentario\nversion=8\nwidth=1280\nheight=720\nlanguage=EN\ntexture2x=false\ndoVideoEffects=false\n';

test('ES por defecto; el servidor puede cambiarlo; la URL manda y se recuerda', () => {
  assert.equal(DEFAULT_LANGUAGE, 'ES');
  assert.deepEqual(resolveLanguage({}), { code: 'ES', source: 'default' });
  assert.deepEqual(resolveLanguage({ serverDefault: 'en' }), { code: 'EN', source: 'server' });
  assert.deepEqual(resolveLanguage({ serverDefault: 'mal=valor' }), { code: 'ES', source: 'default' });
  const storage = memoryStorage();
  assert.deepEqual(resolveLanguage({ search: '?autojoin=1&lang=en', storage, serverDefault: 'ES' }), { code: 'EN', source: 'url' });
  assert.equal(storage.map.get(LANGUAGE_STORAGE_KEY), 'EN');
  // Próxima carga sin ?lang: sigue la elección explícita, aunque el servidor diga ES.
  assert.deepEqual(resolveLanguage({ search: '?autojoin=1', storage, serverDefault: 'ES' }), { code: 'EN', source: 'saved' });
  // ?lang=auto borra la elección y vuelve al del servidor.
  assert.deepEqual(resolveLanguage({ search: '?lang=auto', storage, serverDefault: 'ES' }), { code: 'ES', source: 'server' });
  assert.equal(storage.map.has(LANGUAGE_STORAGE_KEY), false);
  // Un ?lang inválido no se guarda ni pisa nada.
  const kept = memoryStorage({ [LANGUAGE_STORAGE_KEY]: 'FR' });
  assert.deepEqual(resolveLanguage({ search: '?lang=x%0Ay', storage: kept }), { code: 'FR', source: 'saved' });
  assert.equal(kept.map.get(LANGUAGE_STORAGE_KEY), 'FR');
  // localStorage que tira (modo privado / bloqueado) no rompe el arranque.
  const throwing = { getItem() { throw new Error('bloqueado'); }, setItem() { throw new Error('bloqueado'); }, removeItem() { throw new Error('bloqueado'); } };
  assert.deepEqual(resolveLanguage({ search: '?lang=es_mx', storage: throwing }), { code: 'ES_MX', source: 'url' });
  assert.deepEqual(resolveLanguage({ storage: throwing }), { code: 'ES', source: 'default' });
});

test('normalizeLanguage solo acepta códigos de carpeta', () => {
  assert.equal(normalizeLanguage(' es '), 'ES');
  assert.equal(normalizeLanguage('PTBR'), 'PTBR');
  for (const bad of ['', 'E', 'ES=EN', 'ES\nversion=1', '../EN', 'es-mx', null, 3]) assert.equal(normalizeLanguage(bad), null, String(bad));
});

test('withLanguage cambia solo la línea language= (o la agrega)', () => {
  const out = withLanguage(PACK_INI, 'ES');
  assert.equal(out, PACK_INI.replace('language=EN', 'language=ES'));
  assert.equal(languageOf(out), 'ES');
  assert.equal(withLanguage('version=8\n', 'ES'), 'version=8\nlanguage=ES\n');
  assert.equal(withLanguage('version=8', 'ES'), 'version=8\nlanguage=ES\n');
  assert.equal(withLanguage('version=8\r\nlanguage=EN\r\nlanguage=FR\r\n', 'ES'), 'version=8\r\nlanguage=ES\r\n');
  assert.throws(() => withLanguage(PACK_INI, 'ES=1'), /inválido/);
});

test('applyLanguage reescribe el options.ini del pack y lo deja de solo lectura', () => {
  const vfs = new VirtualFileSystem();
  vfs.mountReadOnly(OPTIONS_PATH, new TextEncoder().encode(PACK_INI));
  vfs.mount(translatePath('EN'), new TextEncoder().encode('{"version":"1"}'));
  // Sin la carpeta ES no se toca nada y se dice por qué.
  const missing = applyLanguage(vfs, { code: 'ES', source: 'default' });
  assert.equal(missing.applied, false);
  assert.match(missing.reason, /Translate\/ES\/language\.json/);
  assert.equal(languageOf(new TextDecoder().decode(vfs.readFile(OPTIONS_PATH))), 'EN');
  // ES llega bajo demanda desde /media (lista remota del manifest), como en el juego real.
  vfs.attachRemote([['lua/shared/Translate/ES/language.json', 40]]);
  const done = applyLanguage(vfs, { code: 'ES', source: 'default' });
  assert.deepEqual(done, { applied: true, code: 'ES', source: 'default', previous: 'EN', changed: true });
  const text = new TextDecoder().decode(vfs.readFile(OPTIONS_PATH));
  assert.equal(text, PACK_INI.replace('language=EN', 'language=ES'));
  assert.equal(vfs.isReadOnlyMounted(OPTIONS_PATH), true);
  assert.throws(() => vfs.writeFile(OPTIONS_PATH, new Uint8Array([1])), /EROFS/);
  assert.equal(applyLanguage(vfs, { code: 'ES', source: 'default' }).changed, false);
  assert.equal(applyLanguage(new VirtualFileSystem(), { code: 'ES', source: 'default' }).applied, false);
});

test('game.mjs aplica el idioma antes de cargar boot.wasm', () => {
  const source = readFileSync(new URL('../../port/web/game.mjs', import.meta.url), 'utf8');
  const apply = source.indexOf('applyLanguage(vfs');
  assert.ok(apply > 0, 'game.mjs llama applyLanguage');
  assert.ok(apply > source.indexOf('await preloadPacks('), 'después de montar los packs');
  assert.ok(apply > source.indexOf('vfs.attachRemote('), 'después de la lista remota de /media');
  assert.ok(apply < source.indexOf('globalThis.pzRuntime ='), 'antes de entregar la VFS al juego');
});

test('PZWEB_LANGUAGE del dev server: ES por defecto, en el manifest, inválido corta', async () => {
  assert.equal(languageSetting(undefined), 'ES');
  assert.equal(languageSetting(''), 'ES');
  assert.equal(languageSetting('en'), 'EN');
  assert.throws(() => languageSetting('E=S'), /PZWEB_LANGUAGE/);
  const temporary = await mkdtemp(join(tmpdir(), 'pz-language-'));
  try {
    await mkdir(join(temporary, 'web')); await mkdir(join(temporary, 'game/media'), { recursive: true });
    await writeFile(join(temporary, 'web/index.html'), 'x');
    await writeFile(join(temporary, 'game/ProjectZomboid64.json'), '{}');
    await writeFile(join(temporary, 'game/media/a.txt'), 'a');
    for (const [language, expected] of [[undefined, 'ES'], ['en', 'EN']]) {
      const server = await createDevServer({ webRoot: join(temporary, 'web'), pzHome: join(temporary, 'game'), language,
        warmFile: join(temporary, 'warm.json') });
      server.listen(0, '127.0.0.1'); await once(server, 'listening');
      try {
        const listed = await (await fetch(`http://127.0.0.1:${server.address().port}/media-manifest.json`)).json();
        assert.equal(listed.language, expected);
        assert.equal(listed.slices, 1);
        assert.match(listed.version, /^media:[0-9a-f]{64}$/);
      } finally { await new Promise(r => server.close(r)); }
    }
    await assert.rejects(createDevServer({ webRoot: join(temporary, 'web'), language: 'x y' }), /PZWEB_LANGUAGE/);
  } finally { await rm(temporary, { recursive: true, force: true }); }
});

const SCRIPT = resolve(import.meta.dirname, '../../scripts/mp-server.sh');
function serverSandbox({ options, extraEnv = {} } = {}) {
  const root = mkdtempSync(join(tmpdir(), 'pz-mp-lang-'));
  mkdirSync(join(root, 'data/Server'), { recursive: true });
  for (const code of ['EN', 'ES']) {
    mkdirSync(join(root, `game/media/lua/shared/Translate/${code}`), { recursive: true });
    writeFileSync(join(root, `game/media/lua/shared/Translate/${code}/language.json`), '{"version":"1"}');
  }
  const ini = join(root, 'data/options.ini');
  if (options !== undefined) writeFileSync(ini, options);
  const env = { PATH: process.env.PATH, HOME: root, PZ_HOME: join(root, 'game'), PZ_MP_DATA: join(root, 'data'),
    PZ_MP_LOG: join(root, 'server.log'), ...extraEnv };
  return { ini, run: () => spawnSync('bash', [SCRIPT, 'apply-config'], { env, encoding: 'utf8' }),
    read: () => readFileSync(ini, 'utf8'), done: () => rmSync(root, { recursive: true, force: true }) };
}

test('mp-server.sh apply-config deja el servidor en ES y respeta el resto del options.ini', () => {
  const s = serverSandbox({ options: 'version=8\nambientVolume=5\nlanguage=EN\nclock24Hour=true\n' });
  try {
    const r = s.run(); assert.equal(r.status, 0, r.stderr);
    assert.equal(s.read(), 'version=8\nambientVolume=5\nlanguage=ES\nclock24Hour=true\n');
    assert.equal(s.run().status, 0); assert.equal(s.read(), 'version=8\nambientVolume=5\nlanguage=ES\nclock24Hour=true\n', 'idempotente');
  } finally { s.done(); }
});

test('mp-server.sh: mundo nuevo, PZWEB_LANGUAGE y códigos inválidos o ausentes', () => {
  const fresh = serverSandbox();
  try { assert.equal(fresh.run().status, 0); assert.equal(fresh.read(), 'version=8\nlanguage=ES\n'); } finally { fresh.done(); }
  const noLine = serverSandbox({ options: 'version=8\n', extraEnv: { PZWEB_LANGUAGE: 'en' } });
  try { assert.equal(noLine.run().status, 0); assert.equal(noLine.read(), 'version=8\nlanguage=EN\n'); } finally { noLine.done(); }
  for (const bad of ['FR', 'E/S', 'ES;rm']) {
    const s = serverSandbox({ options: 'version=8\nlanguage=EN\n', extraEnv: { PZWEB_LANGUAGE: bad } });
    try {
      const r = s.run();
      assert.notEqual(r.status, 0, bad);
      assert.match(r.stderr, /PZWEB_LANGUAGE/);
      assert.equal(s.read(), 'version=8\nlanguage=EN\n', `${bad}: no se toca el archivo`);
    } finally { s.done(); }
  }
});

// Línea sintética con el mismo comienzo que el mensaje por defecto (solo se compara ese comienzo).
const DEFAULT_WELCOME = 'ServerWelcomeMessage=Welcome to Project Zomboid Multiplayer! <LINE> texto sintético';
function welcomeSandbox(ini, extraEnv = {}) {
  const s = serverSandbox({ options: 'version=8\nlanguage=EN\n', extraEnv });
  const file = join(s.ini, '../Server/pzweb.ini');
  writeFileSync(file, ini);
  return { ...s, file, welcome: () => readFileSync(file, 'utf8') };
}

test('mp-server.sh: bienvenida en español solo si sigue la del juego', () => {
  const s = welcomeSandbox(`PublicName=x\n${DEFAULT_WELCOME}\nPVP=true\n`);
  try {
    assert.equal(s.run().status, 0);
    const out = s.welcome();
    assert.match(out, /^ServerWelcomeMessage=¡Bienvenido al multijugador de Project Zomboid! <LINE>/m);
    assert.match(out, /^PublicName=x\n/); assert.match(out, /\nPVP=true\n$/);
    assert.equal(out.split('\n').filter(l => l.startsWith('ServerWelcomeMessage=')).length, 1);
    assert.equal(s.run().status, 0); assert.equal(s.welcome(), out, 'idempotente');
  } finally { s.done(); }
  const custom = welcomeSandbox('ServerWelcomeMessage=Hola amigos\n');
  try { assert.equal(custom.run().status, 0); assert.equal(custom.welcome(), 'ServerWelcomeMessage=Hola amigos\n', 'uno propio no se toca'); } finally { custom.done(); }
  const en = welcomeSandbox(`${DEFAULT_WELCOME}\n`, { PZWEB_LANGUAGE: 'EN' });
  try { assert.equal(en.run().status, 0); assert.equal(en.welcome(), `${DEFAULT_WELCOME}\n`, 'en inglés queda el del juego'); } finally { en.done(); }
  const forced = welcomeSandbox('ServerWelcomeMessage=viejo\n', { PZWEB_WELCOME_MESSAGE: 'a \\n & / <LINE> b' });
  try { assert.equal(forced.run().status, 0); assert.equal(forced.welcome(), 'ServerWelcomeMessage=a \\n & / <LINE> b\n', 'tal cual, sin interpretar'); } finally { forced.done(); }
  const bad = welcomeSandbox('ServerWelcomeMessage=viejo\n', { PZWEB_WELCOME_MESSAGE: 'a\nb' });
  try { assert.notEqual(bad.run().status, 0); assert.equal(bad.welcome(), 'ServerWelcomeMessage=viejo\n'); } finally { bad.done(); }
});
