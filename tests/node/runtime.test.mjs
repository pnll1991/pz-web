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
