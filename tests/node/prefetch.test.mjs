// Round 84: warm list (server side), parallel prefetch (client side) and the VFS hook that consumes prefetched ranges.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createWarmList } from '../../dev-server/warm-list.mjs';
import { prefetchMedia } from '../../port/runtime/prefetch.mjs';
import { VirtualFileSystem } from '../../port/runtime/vfs.mjs';

test('warm list dedupes, persists and reloads', async () => {
  const dir = await mkdtemp(path.join(tmpdir(), 'pz-warm-')), file = path.join(dir, 'w.json');
  try {
    const a = createWarmList({ file });
    assert.equal(a.record('/media/x.bin?r=0-4', 'bytes=0-3'), true);
    assert.equal(a.record('/media/x.bin?r=0-4', 'bytes=0-3'), false);
    assert.equal(a.record('/media-bundle?dir=maps&ext=lotheader', ''), true);
    a.flush();
    assert.deepEqual(JSON.parse(await readFile(file, 'utf8')).length, 2);
    assert.equal(createWarmList({ file }).size(), 2);
    assert.equal(createWarmList({ file: path.join(dir, 'x.json'), max: 1 }).record('/a', ''), true);
  } finally { await rm(dir, { recursive: true }); }
});

test('prefetch fills vfs.prefetched and counts failures; the VFS reads them instead of the network', async () => {
  const data = Uint8Array.from({ length: 8 }, (_, i) => i + 1);
  const url = '/media/lua/a.bin?r=0-8';
  const fetchImpl = async (u, opts = {}) => {
    if (u === '/warm-media.json') return { ok: true, json: async () => [[url, 'bytes=0-7'], ['/media/missing?r=0-4', 'bytes=0-3']] };
    if (u === url && opts.headers.Range === 'bytes=0-7') return { status: 206, arrayBuffer: async () => data.buffer.slice(0) };
    return { status: 404, arrayBuffer: async () => new ArrayBuffer(0) };
  };
  const vfs = new VirtualFileSystem(); vfs.attachRemote([['lua/a.bin', 8]]);
  const stats = await prefetchMedia(vfs, { fetchImpl, concurrency: 4 });
  assert.deepEqual([stats.requested, stats.stored, stats.failed, stats.bytes], [2, 1, 1, 8]);
  globalThis.XMLHttpRequest = class { open() { throw new Error('network must not be used'); } };
  try {
    assert.deepEqual([...vfs.readFile('/media/lua/a.bin')], [...data]);
    assert.equal(vfs.prefetchHits, 1); assert.equal(vfs.prefetched.size, 0, 'a prefetched response is used once');
  } finally { delete globalThis.XMLHttpRequest; }
});
