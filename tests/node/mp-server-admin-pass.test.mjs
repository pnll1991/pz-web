// Contraseña admin del servidor PZ (scripts/mp-server.sh): de dónde se lee y que nunca se imprima.
// Sólo prueba la selección/permisos del script con un HOME y un PZ_MP_DATA temporales; no arranca el juego.
import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, statSync, chmodSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const SCRIPT = resolve(import.meta.dirname, '../../scripts/mp-server.sh');
function sandbox() {
  const root = mkdtempSync(join(tmpdir(), 'pz-adminpass-'));
  mkdirSync(join(root, 'home'));
  const env = { PATH: process.env.PATH, HOME: join(root, 'home'), XDG_CONFIG_HOME: join(root, 'home/.config'), PZ_MP_DATA: join(root, 'data'), PZ_MP_LOG: join(root, 'server.log') };
  const run = (...args) => spawnSync('bash', [SCRIPT, ...args], { env, encoding: 'utf8' });
  return { root, env, run, dflt: join(root, 'home/.config/pz-web/admin-pass'), done: () => rmSync(root, { recursive: true, force: true }) };
}
const mode = f => (statSync(f).mode & 0o777).toString(8);

test('gen-admin-pass crea el archivo 600 en ~/.config/pz-web, no imprime la contraseña y no pisa sin --force', () => {
  const s = sandbox();
  try {
    const r = s.run('gen-admin-pass');
    assert.equal(r.status, 0, r.stderr);
    const pass = readFileSync(s.dflt, 'utf8');
    assert.match(pass, /^[A-Za-z0-9]{20,}$/);
    assert.equal(mode(s.dflt), '600');
    assert.ok(!r.stdout.includes(pass) && !r.stderr.includes(pass), 'la contraseña se imprimió');
    assert.equal(s.run('admin-pass-file').stdout.trim(), s.dflt, 'start debe leer el archivo nuevo');

    const again = s.run('gen-admin-pass');
    assert.notEqual(again.status, 0);
    assert.equal(readFileSync(s.dflt, 'utf8'), pass, 'sin --force no se reemplaza');

    const forced = s.run('gen-admin-pass', '--force');
    assert.equal(forced.status, 0, forced.stderr);
    const rotated = readFileSync(s.dflt, 'utf8');
    assert.notEqual(rotated, pass);
    assert.equal(mode(s.dflt), '600');
    assert.ok(!forced.stdout.includes(rotated));
  } finally { s.done(); }
});

test('PZ_MP_ADMIN_PASSWORD_FILE manda sobre el archivo por defecto', () => {
  const s = sandbox();
  try {
    assert.equal(s.run('gen-admin-pass').status, 0);
    const otro = join(s.root, 'otro');
    s.env.PZ_MP_ADMIN_PASSWORD_FILE = otro;
    assert.equal(s.run('admin-pass-file').stdout.trim(), otro);
    assert.equal(s.run('gen-admin-pass').status, 0);
    assert.equal(mode(otro), '600');
  } finally { s.done(); }
});

test('start corta antes de tocar nada si el archivo nombrado falta, está vacío o tiene permisos flojos (sin caer a otra contraseña)', () => {
  const s = sandbox();
  try {
    const f = join(s.root, 'pass');
    s.env.PZ_MP_ADMIN_PASSWORD_FILE = f;
    let r = s.run('start');
    assert.notEqual(r.status, 0); assert.match(r.stderr, /no existe o está vacío/);
    writeFileSync(f, ''); chmodSync(f, 0o600);
    r = s.run('start');
    assert.notEqual(r.status, 0); assert.match(r.stderr, /no existe o está vacío/);
    writeFileSync(f, 'SecretoDePrueba123'); chmodSync(f, 0o644);
    r = s.run('start');
    assert.notEqual(r.status, 0); assert.match(r.stderr, /permisos 644/);
    assert.ok(!r.stdout.includes('SecretoDePrueba123') && !r.stderr.includes('SecretoDePrueba123'));
    assert.throws(() => statSync(join(s.root, 'data')), 'no debe crear el directorio de datos');
  } finally { s.done(); }
});
