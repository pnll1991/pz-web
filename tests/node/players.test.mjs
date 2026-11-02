// Panel de jugadores del dev server: valida, acota y vence los reportes de rendimiento.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createPlayerBoard } from '../../dev-server/players.mjs';

const q = s => new URLSearchParams(s);

test('report keeps clamped numbers, the gap between reports and drops stale players', () => {
  let t = 1000;
  const board = createPlayerBoard({ now: () => t, ttlMs: 30000, bridges: () => 2 });
  assert.equal(board.report(q('u=pabc&fps=58.8&worst=22&busy=150&ping=-3&mem=x')), true);
  t = 6000;
  assert.equal(board.report(q('u=pabc&fps=30&ping=45')), true);
  const snap = board.snapshot();
  assert.deepEqual(snap.players, [{ name: 'pabc', gapS: 5, fps: 30, worst: null, busy: null, ping: 45, mem: null, ageS: 0, at: undefined }]);
  assert.equal(snap.server.bridges, 2);
  assert.equal(typeof snap.server.load1, 'number');
  t = 40000;
  assert.deepEqual(board.snapshot().players, []);
});

test('report rejects bad names and caps the number of players', () => {
  const board = createPlayerBoard({ max: 2 });
  for (const bad of ['', 'u=', 'u=Bad%20Name', 'u=' + 'a'.repeat(21), 'u=<script>']) assert.equal(board.report(q(bad)), false, bad);
  assert.equal(board.report(q('u=a1')), true);
  assert.equal(board.report(q('u=a2')), true);
  assert.equal(board.report(q('u=a3')), false);
  assert.equal(board.report(q('u=a1&fps=60')), true); // an existing player can still update
});
