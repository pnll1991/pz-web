// Pantalla de carga: el lector que cuenta bytes de los packs devuelve exactamente lo que daría arrayBuffer().
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readWithProgress, formatMB } from '../../port/runtime/loading.mjs';

const chunked = parts => new Response(new ReadableStream({
  start(controller) { for (const part of parts) controller.enqueue(Uint8Array.from(part)); controller.close(); }
}));

test('readWithProgress counts every chunk and returns the full body', async () => {
  const seen = [];
  const bytes = await readWithProgress(chunked([[1, 2, 3], [4, 5], [6]]), 6, n => seen.push(n));
  assert.deepEqual([...new Uint8Array(bytes)], [1, 2, 3, 4, 5, 6]);
  assert.deepEqual(seen, [3, 2, 1]);
});

test('readWithProgress keeps the real length when the body differs from the promised size', async () => {
  assert.equal((await readWithProgress(chunked([[1, 2], [3]]), 5)).byteLength, 3);
  const longer = await readWithProgress(chunked([[1, 2], [3, 4, 5]]), 2);
  assert.deepEqual([...new Uint8Array(longer)], [1, 2, 3, 4, 5]);
});

test('readWithProgress falls back to arrayBuffer() without a body or a size', async () => {
  let total = 0;
  const bytes = await readWithProgress(new Response(Uint8Array.from([7, 8])), undefined, n => { total += n; });
  assert.deepEqual([...new Uint8Array(bytes)], [7, 8]);
  assert.equal(total, 2);
});

test('formatMB rounds to whole megabytes', () => {
  assert.match(formatMB(321_456_789), /^321 MB$/);
  assert.equal(formatMB(0), '0 MB');
});
