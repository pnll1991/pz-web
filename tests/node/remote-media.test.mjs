import { test } from 'node:test';
import assert from 'node:assert/strict';
import { VirtualFileSystem } from '../../port/runtime/vfs.mjs';

// Round 79: on-demand media reads through synchronous Range requests (fake XHR over a byte array).
test('remote media files are listed, sized and read by range without shadowing packs', () => {
  const data = new Uint8Array(3 * (1 << 20) + 5).map((_, i) => (i * 7) & 0xff);
  const seen = [];
  globalThis.XMLHttpRequest = class {
    open(method, url) { this.url = url; } overrideMimeType() {} setRequestHeader(k, v) { this.range = v; }
    send() {
      seen.push([this.url, this.range]);
      const [, a, b] = /bytes=(\d+)-(\d+)/.exec(this.range).map(Number);
      this.status = 206; this.responseText = String.fromCharCode(...data.subarray(a, b + 1).slice(0, 65536)) +
        (b + 1 - a > 65536 ? Array.from(data.subarray(a + 65536, b + 1), c => String.fromCharCode(c)).join('') : '');
    }
  };
  const vfs = new VirtualFileSystem();
  vfs.mount('/media/lua/a.lua', new Uint8Array([9]));
  vfs.attachRemote([['lua/a.lua', 99], ['maps/Muldraugh, KY/0_0.lotpack', data.length]]);
  assert.equal(vfs.size('/media/lua/a.lua'), 1);
  assert.equal(vfs.kind('/media/maps'), 2);
  assert.equal(vfs.kind('/media/maps/Muldraugh, KY/0_0.lotpack'), 1);
  assert.deepEqual(vfs.children('/media'), ['lua', 'maps']);
  assert.deepEqual(vfs.children('/media/maps'), ['Muldraugh, KY']);
  const target = new Uint8Array(10);
  assert.equal(vfs.readInto('/media/maps/Muldraugh, KY/0_0.lotpack', (1 << 20) - 4, target), 10);
  assert.deepEqual([...target], [...data.subarray((1 << 20) - 4, (1 << 20) + 6)]);
  assert.match(seen[0][0], /Muldraugh%2C%20KY/);
  assert.deepEqual(vfs.readFile('/media/maps/Muldraugh, KY/0_0.lotpack'), data);
  assert.throws(() => vfs.writeFile('/media/maps/Muldraugh, KY/0_0.lotpack', new Uint8Array(1)), /EROFS/);
  delete globalThis.XMLHttpRequest;
});
