// Regresión (R86): un merge dejó dos `await preloadPacks(vfs)` seguidos en game.mjs. La segunda pasada vuelve a bajar y a montar los
// ~550 MB de packs sobre los ya montados (las vistas nuevas no comparten blob con las viejas), pasa el presupuesto del VFS y la carga fría
// con los packs reales muere con "VFS memory budget exceeded". Con el juego sintético de CI no se nota: este guardia lo cubre.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

test('game.mjs monta los packs una sola vez', () => {
  const source = readFileSync(new URL('../../port/web/game.mjs', import.meta.url), 'utf8');
  const calls = source.split('\n').filter(line => /\bpreloadPacks\(/.test(line) && !/^\s*(import|\/\/)/.test(line));
  assert.equal(calls.length, 1, `preloadPacks se llama ${calls.length} veces:\n${calls.join('\n')}`);
});
