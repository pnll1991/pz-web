import { test } from 'node:test';
import assert from 'node:assert/strict';
import { VirtualFileSystem, normalizePath } from '../../port/runtime/vfs.mjs';
import { glfwKey } from '../../port/runtime/input.mjs';

test('read only assets and independent byte ownership', () => {
  const vfs = new VirtualFileSystem(); const bytes = new Uint8Array([1,2,3]);
  vfs.mount('/media/a.bin',bytes); bytes[0] = 99;
  const read = vfs.readFile('/media/a.bin'); read[0] = 88;
  assert.equal(vfs.readFile('/media/a.bin')[0],1);
  assert.throws(()=>vfs.writeFile('/media/a.bin',bytes),/EROFS/);
  assert.throws(()=>vfs.readFile('/media/missing'),/ENOENT/);
});
test('atomic in-memory rename, deletion and memory budget', async () => {
  const vfs = new VirtualFileSystem({maxBytes:4});
  vfs.writeFile('/Zomboid/a.tmp',new Uint8Array([1,2]));
  vfs.rename('/Zomboid/a.tmp','/Zomboid/a');
  assert.equal(vfs.exists('/Zomboid/a.tmp'),false);
  assert.deepEqual([...vfs.readFile('/Zomboid/a')],[1,2]);
  assert.throws(()=>vfs.writeFile('/Zomboid/b',new Uint8Array(3)),/budget/);
  assert.throws(()=>vfs.rename('/Zomboid/a','/media/a'),/EROFS/);
  await assert.rejects(vfs.flush(),/opened/);
  vfs.remove('/Zomboid/a'); assert.equal(vfs.byteLength,0);
});
test('POSIX paths and GLFW physical key mapping', () => {
  assert.equal(normalizePath('/Zomboid/a/../b'),'/Zomboid/b');
  assert.throws(()=>normalizePath('/../../x'),/escapes/);
  assert.throws(()=>normalizePath('relative'),/absolute/);
  assert.equal(glfwKey('KeyW'),87); assert.equal(glfwKey('ArrowLeft'),263);
  assert.equal(glfwKey('F12'),301); assert.equal(glfwKey('Numpad9'),329);
  assert.equal(glfwKey('Unsupported'),-1);
});

test('directories move with their contents and nonempty deletion fails', () => {
  const vfs = new VirtualFileSystem();
  assert.equal(vfs.mkdir('/Zomboid/a'), true);
  assert.equal(vfs.mkdir('/Zomboid/a/empty'), true);
  assert.equal(vfs.createFile('/Zomboid/a/data'), true);
  assert.throws(() => vfs.remove('/Zomboid/a'), /ENOTEMPTY/);
  vfs.rename('/Zomboid/a', '/Zomboid/b');
  assert.deepEqual(vfs.children('/Zomboid/b'), ['data', 'empty']);
  assert.equal(vfs.exists('/Zomboid/a'), false);
  assert.throws(() => vfs.rename('/Zomboid/b', '/Zomboid/b/inside'), /Invalid/);
  assert.equal(vfs.mkdir('/media/no'), false);
});

test('random writes zero fill holes, preserve bytes and enforce read-only and quota', () => {
  const vfs = new VirtualFileSystem({maxBytes:8});
  vfs.writeFile('/Zomboid/file', new Uint8Array([0, 255]));
  vfs.writeAt('/Zomboid/file', 4, new Uint8Array([128]));
  assert.deepEqual([...vfs.readFile('/Zomboid/file')], [0, 255, 0, 0, 128]);
  const target = new Uint8Array(8);
  assert.equal(vfs.readInto('/Zomboid/file', 1, target), 4);
  assert.deepEqual([...target.subarray(0, 4)], [255, 0, 0, 128]);
  assert.throws(() => vfs.resize('/Zomboid/file', 9), /budget/);
  vfs.resize('/Zomboid/file', 2);
  assert.deepEqual([...vfs.readFile('/Zomboid/file')], [0, 255]);
  vfs.setAttribute('/Zomboid/file', 'readOnly', true);
  assert.throws(() => vfs.writeAt('/Zomboid/file', 0, new Uint8Array([1])), /EROFS/);
  assert.throws(() => vfs.writeFile('/Zomboid/file', new Uint8Array([1])), /EROFS/);
});

test('a read-only /Zomboid pack entry cannot be rewritten or persisted', async () => {
  const vfs = new VirtualFileSystem();
  vfs.mountReadOnly('/Zomboid/options.ini', new TextEncoder().encode('version=8\ntexture2x=false\n'));
  assert.equal(vfs.exists('/Zomboid/options.ini'), true);
  assert.equal(vfs.canWrite('/Zomboid/options.ini'), false, 'the game must not overwrite the pack option file');
  assert.throws(() => vfs.writeFile('/Zomboid/options.ini', new Uint8Array([1])), /EROFS/);
  assert.throws(() => vfs.writeAt('/Zomboid/options.ini', 0, new Uint8Array([1])), /EROFS/);
  assert.throws(() => vfs.resize('/Zomboid/options.ini', 0), /EROFS/);
  assert.equal(vfs.isReadOnlyMounted('/Zomboid/options.ini'), true);
  assert.equal(vfs.isReadOnlyMounted('/Zomboid/other.ini'), false);
  assert.throws(() => vfs.mountReadOnly('/media/x.bin', new Uint8Array([1])), /Use mount\(\)/);
  assert.throws(() => vfs.mountReadOnly('/elsewhere/x.bin', new Uint8Array([1])), /Only \/Zomboid/);
  // Ordinary user data under /Zomboid stays writable.
  vfs.writeFile('/Zomboid/other.ini', new Uint8Array([7]));
  assert.equal(vfs.canWrite('/Zomboid/other.ini'), true);
  // flush() builds the save snapshot: a game asset must never be copied into it.
  const writes = [];
  const fakeTransaction = () => {
    const listeners = {};
    const transaction = {
      objectStore: name => ({ clear: () => writes.push(['clear', name]), put: (value, key) => writes.push([name, key]) }),
      addEventListener: undefined,
      set oncomplete(handler) { listeners.complete = handler; },
      set onabort(handler) { listeners.abort = handler; },
      set onerror(handler) { listeners.error = handler; },
    };
    // IndexedDB completes asynchronously; the VFS resolves on that event.
    queueMicrotask(() => listeners.complete?.({ target: transaction }));
    return transaction;
  };
  vfs.database = { transaction: fakeTransaction };
  vfs.pending = Promise.resolve();
  await vfs.flush();
  const fileKeys = writes.filter(([name]) => name === 'files').map(([, key]) => key);
  assert.deepEqual(fileKeys, ['/Zomboid/other.ini'], 'only real user data is persisted');
  assert.ok(writes.some(([name, key]) => name === 'metadata' && key === '/Zomboid/other.ini'));
  assert.ok(!writes.some(([, key]) => key === '/Zomboid/options.ini'));
});
test('a pack view is read-only, shares the blob bytes and counts once', () => {
  const vfs = new VirtualFileSystem({maxBytes:64});
  const blob = new Uint8Array(48);
  for (let i = 0; i < blob.length; i++) blob[i] = i;
  vfs.mountView('/media/big.bin', blob, 8, 16);
  vfs.mountView('/Zomboid/options.ini', blob, 32, 8);
  const view = vfs.readFile('/media/big.bin');
  assert.equal(view.byteLength, 16);
  assert.equal(view[0], 8, 'the view starts at the pack offset');
  assert.equal(vfs.byteLength, 24, 'the mounted view costs its own length, not the blob size');
  assert.equal(vfs.canWrite('/media/big.bin'), false);
  assert.equal(vfs.isReadOnlyMounted('/Zomboid/options.ini'), true);
  assert.throws(() => vfs.writeFile('/media/big.bin', new Uint8Array([1])), /EROFS/);
  // readFile() copies the bytes, so rewriting the returned buffer cannot corrupt the pack view.
  view[0] = 255;
  assert.equal(vfs.readFile('/media/big.bin')[0], 8);
  assert.throws(() => vfs.mountView('/media/x.bin', blob, 40, 16), /Invalid view range/);
  assert.throws(() => vfs.mountView('/elsewhere/x.bin', blob, 0, 4), /Pack views are only allowed/);
  assert.throws(() => vfs.mountView('/media/y.bin', [1, 2], 0, 2), /must be Uint8Array/);
  const small = new VirtualFileSystem({maxBytes:4});
  assert.throws(() => small.mountView('/media/big.bin', blob, 0, 8), /budget/);
});

test('autoFlush persists /Zomboid changes incrementally once the VFS is quiet', async () => {
  const vfs = new VirtualFileSystem();
  vfs.mountReadOnly('/Zomboid/options.ini', new TextEncoder().encode('version=8\n'));
  const ops = [];
  vfs.database = { transaction: () => {
    const listeners = {};
    const store = name => ({ put: (value, key) => ops.push(['put', name, key, value]), delete: key => ops.push(['delete', name, key]) });
    const transaction = { objectStore: store,
      set oncomplete(h) { listeners.complete = h; }, set onabort(h) { listeners.abort = h; }, set onerror(h) {} };
    setTimeout(() => listeners.complete?.({ target: transaction }), 0);
    return transaction;
  } };
  const stop = vfs.autoFlush({ quietMs: 20, maxDelayMs: 1000 });
  vfs.writeFile('/Zomboid/Saves/Sandbox/w1/map_ver.bin', new Uint8Array([1, 2]));
  vfs.writeFile('/Zomboid/latestSave.ini', new TextEncoder().encode('w1\nSandbox\n'));
  vfs.writeFile('/Zomboid/tmp.bin', new Uint8Array([9]));
  vfs.remove('/Zomboid/tmp.bin');
  assert.equal(ops.length, 0, 'nothing is written while the game keeps changing files');
  await new Promise(resolve => setTimeout(resolve, 80));
  await vfs.pending;
  const puts = ops.filter(([op, name]) => op === 'put' && name === 'files').map(([, , key]) => key).sort();
  assert.deepEqual(puts, ['/Zomboid/Saves/Sandbox/w1/map_ver.bin', '/Zomboid/latestSave.ini']);
  assert.ok(ops.some(([op, name, key]) => op === 'put' && name === 'metadata' && key === '/Zomboid/Saves/Sandbox/w1'), 'new directories are persisted');
  assert.ok(ops.some(([op, name, key]) => op === 'delete' && name === 'files' && key === '/Zomboid/tmp.bin'), 'removed files are deleted');
  assert.ok(!ops.some(([, , key]) => key === '/Zomboid/options.ini'), 'the read-only pack file is never persisted');
  assert.equal(vfs.autoFlushStats.batches, 1);
  // Stopping flushes what is still pending (pagehide).
  ops.length = 0;
  vfs.writeFile('/Zomboid/latestSave.ini', new TextEncoder().encode('w2\nSandbox\n'));
  stop();
  await new Promise(resolve => setTimeout(resolve, 10)); await vfs.pending;
  assert.deepEqual(ops.filter(([op, name]) => op === 'put' && name === 'files').map(([, , key]) => key), ['/Zomboid/latestSave.ini']);
});
