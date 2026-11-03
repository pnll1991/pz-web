// Población de zombis del servidor PZ (scripts/mp-server.sh apply-config) sobre un pzweb_SandboxVars.lua sintético propio
// (mismas claves y formato "clave = valor," que genera el servidor; sin texto del juego). No arranca el juego.
import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const SCRIPT = resolve(import.meta.dirname, '../../scripts/mp-server.sh');
// Valores del mundo en vivo antes del cambio (R86) y una clave parecida que no se tiene que tocar.
const FIXTURE = `SandboxVars = {
    VERSION = 6,
    Zombies = 3,
    Distribution = 2,
    ZombieVoronoiNoise = true,
    ZombieRespawn = 2,
    HoursForLootRespawn = 0,
    EnableVehicles = true,
    ZombieLore = {
        PlayerSpawnZombieRemoval = 4,
    },
    ZombieConfig = {
        PopulationMultiplier = 0.65,
        PopulationStartMultiplier = 1.0,
        PopulationPeakMultiplier = 1.5,
        PopulationPeakDay = 28,
        RespawnHours = 72.0,
        RespawnUnseenHours = 16.0,
        RespawnMultiplier = 0.1,
        RedistributeHours = 12.0,
    },
}
`;
function sandbox(extraEnv = {}) {
  const root = mkdtempSync(join(tmpdir(), 'pz-zombies-'));
  const lua = join(root, 'data/Server/pzweb_SandboxVars.lua');
  mkdirSync(join(root, 'data/Server'), { recursive: true });
  writeFileSync(lua, FIXTURE);
  const env = { PATH: process.env.PATH, HOME: root, PZ_MP_DATA: join(root, 'data'), PZ_MP_LOG: join(root, 'server.log'), ...extraEnv };
  const run = () => spawnSync('bash', [SCRIPT, 'apply-config'], { env, encoding: 'utf8' });
  const vals = () => Object.fromEntries([...readFileSync(lua, 'utf8').matchAll(/^\s*(\w+) = ([^,\n]+),/gm)].map(m => [m[1], m[2]]));
  return { lua, run, vals, done: () => rmSync(root, { recursive: true, force: true }) };
}

test('apply-config deja pocos zombis por defecto, sin reaparición y sin zombis donde aparecen los jugadores', () => {
  const s = sandbox();
  try {
    const r = s.run();
    assert.equal(r.status, 0, r.stderr);
    const v = s.vals();
    assert.deepEqual(
      [v.Zombies, v.Distribution, v.ZombieRespawn, v.PlayerSpawnZombieRemoval],
      ['5', '2', '4', '1']);
    assert.deepEqual(
      [v.PopulationMultiplier, v.PopulationStartMultiplier, v.PopulationPeakMultiplier,
        v.RespawnHours, v.RespawnUnseenHours, v.RespawnMultiplier, v.RedistributeHours],
      ['0.15', '0.5', '1.0', '0.0', '0.0', '0.0', '12.0']);
    assert.equal(v.PopulationPeakDay, '28', 'claves no configuradas quedan igual');
    assert.equal(v.HoursForLootRespawn, '0', 'una clave que contiene "Respawn" no se toca');
    assert.equal(v.EnableVehicles, 'false', 'el resto de apply_config sigue funcionando');

    const once = readFileSync(s.lua, 'utf8');
    assert.equal(s.run().status, 0);
    assert.equal(readFileSync(s.lua, 'utf8'), once, 'aplicar dos veces da lo mismo');
  } finally { s.done(); }
});

test('las variables PZWEB_* cambian cada valor (enteros y decimales) y siguen siendo idempotentes', () => {
  const s = sandbox({ PZWEB_ZOMBIES: '4', PZWEB_ZOMBIE_DISTRIBUTION: '1', PZWEB_ZOMBIE_RESPAWN: '3', PZWEB_SPAWN_ZOMBIE_REMOVAL: '2',
    PZWEB_POP_MULT: '0.3', PZWEB_POP_START_MULT: '1', PZWEB_POP_PEAK_MULT: '0.75', PZWEB_RESPAWN_HOURS: '720',
    PZWEB_RESPAWN_UNSEEN_HOURS: '48.5', PZWEB_RESPAWN_MULT: '0.05', PZWEB_REDISTRIBUTE_HOURS: '0' });
  try {
    assert.equal(s.run().status, 0);
    assert.equal(s.run().status, 0);
    const v = s.vals();
    assert.deepEqual(
      [v.Zombies, v.Distribution, v.ZombieRespawn, v.PlayerSpawnZombieRemoval, v.PopulationMultiplier, v.PopulationStartMultiplier,
        v.PopulationPeakMultiplier, v.RespawnHours, v.RespawnUnseenHours, v.RespawnMultiplier, v.RedistributeHours],
      ['4', '1', '3', '2', '0.3', '1.0', '0.75', '720.0', '48.5', '0.05', '0.0']);
  } finally { s.done(); }
});

for (const [name, value] of [['PZWEB_ZOMBIES', '7'], ['PZWEB_ZOMBIES', '5;rm'], ['PZWEB_POP_MULT', '0,3'], ['PZWEB_POP_MULT', '4.5'],
  ['PZWEB_RESPAWN_MULT', '1.2'], ['PZWEB_POP_MULT', '/x/'], ['PZWEB_POP_START_MULT', '-1']]) {
  test(`${name}=${value} se rechaza sin tocar el archivo`, () => {
    const s = sandbox({ [name]: value });
    try {
      const r = s.run();
      assert.notEqual(r.status, 0);
      assert.match(r.stderr, new RegExp(name));
      assert.equal(s.vals().Zombies, '3', 'no debe quedar a medio escribir: Zombies va primero');
    } finally { s.done(); }
  });
}
