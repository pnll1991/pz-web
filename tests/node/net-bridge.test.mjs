// Round 82: browser RakNet peer (port/runtime/net.mjs) against a fake WebSocket. Synthetic frames, no game bytes.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { NetBridge } from '../../port/runtime/net.mjs';

class FakeWS {
  static last = null;
  constructor(url) { this.url = url; this.sent = []; FakeWS.last = this; }
  send(d) { this.sent.push(Buffer.from(d)); }
  close() { this.onclose?.(); }
}
const packetFrame = (hi, lo, ...bytes) => { const b = Buffer.alloc(9 + bytes.length); b[0] = 0x81; b.writeInt32BE(hi, 1); b.writeInt32BE(lo, 5); Buffer.from(bytes).copy(b, 9); return new Uint8Array(b); };
const mk = () => new NetBridge({ WebSocketImpl: FakeWS, url: 'ws://127.0.0.1:1/ws' });

test('connect sends one CONNECT frame after the socket opens', () => {
  const net = mk();
  assert.equal(net.connect('127.0.0.1', 16261, 'pw'), 0);
  assert.equal(FakeWS.last.sent.length, 0);
  FakeWS.last.onopen();
  const f = FakeWS.last.sent[0];
  assert.equal(f[0], 1); assert.equal(f.readUInt16BE(1), 16261); assert.equal(f[3], 9);
  assert.equal(f.subarray(4, 13).toString(), '127.0.0.1'); assert.equal(f.subarray(13).toString(), 'pw');
});

test('packets are delivered in order with their 64-bit guid halves', () => {
  const net = mk(); net.connect('127.0.0.1', 1, ''); FakeWS.last.onopen();
  FakeWS.last.onmessage({ data: packetFrame(-5, 7, 16, 3).buffer });
  FakeWS.last.onmessage({ data: packetFrame(-5, 7, 134, 1, 2).buffer });
  assert.equal(net.tryReceive(), true); assert.equal(net.currentLength(), 2); assert.equal(net.currentHi(), -5); assert.equal(net.currentLo(), 7);
  const dst = new Uint8Array(8); assert.equal(net.copyCurrentTo(dst), 2); assert.deepEqual([...dst.subarray(0, 2)], [16, 3]);
  assert.equal(net.tryReceive(), true); assert.equal(net.currentLength(), 3);
  assert.equal(net.tryReceive(), false);
});

test('send frames carry reliability, channel, guid and payload; sends before open are held', () => {
  const net = mk(); net.connect('127.0.0.1', 1, '');
  net.send(Uint8Array.of(9, 8), 1, 3, 0, 4, 5, false);
  FakeWS.last.onopen();
  const f = FakeWS.last.sent[1];
  assert.equal(f[0], 2); assert.deepEqual([...f.subarray(1, 5)], [1, 3, 0, 0]);
  assert.equal(f.readInt32BE(5), 4); assert.equal(f.readInt32BE(9), 5); assert.deepEqual([...f.subarray(13)], [9, 8]);
});

test('closing before the game was accepted is ID_CONNECTION_ATTEMPT_FAILED; after, ID_CONNECTION_LOST with the guid', () => {
  const a = mk(); a.connect('127.0.0.1', 1, ''); FakeWS.last.onclose();
  assert.equal(a.tryReceive(), true); assert.equal(a.current.bytes[0], 17);
  const b = mk(); b.connect('127.0.0.1', 1, ''); FakeWS.last.onopen();
  FakeWS.last.onmessage({ data: packetFrame(1, 2, 16, 0).buffer }); b.tryReceive();
  FakeWS.last.onclose();
  assert.equal(b.tryReceive(), true); assert.equal(b.current.bytes[0], 22); assert.equal(b.currentHi(), 1); assert.equal(b.currentLo(), 2);
});

test('stats frames answer ping, MTU, type and ip per guid', () => {
  const net = mk(); net.connect('127.0.0.1', 1, ''); FakeWS.last.onopen();
  const ip = Buffer.from('127.0.0.1'), f = Buffer.alloc(27 + ip.length);
  f[0] = 0x83; f.writeInt32BE(1, 1); f.writeInt32BE(2, 5); f.writeInt32BE(30, 9); f.writeInt32BE(31, 13); f.writeInt32BE(29, 17); f.writeInt32BE(1400, 21);
  f[25] = 1; f[26] = ip.length; ip.copy(f, 27);
  FakeWS.last.onmessage({ data: new Uint8Array(f).buffer });
  assert.equal(net.statOf(1, 2, 'avg'), 30); assert.equal(net.statOf(1, 2, 'mtu'), 1400); assert.equal(net.statOf(1, 2, 'type'), 1); assert.equal(net.statOf(1, 2, 'ip', ''), '127.0.0.1');
  assert.equal(net.statOf(9, 9, 'avg', -1), -1);
});

test('the WebSocket URL follows the page scheme (wss on https, ws on http)', () => {
  const saved = globalThis.location;
  try {
    globalThis.location = { protocol: 'https:', host: 'x.trycloudflare.com' };
    assert.equal(new NetBridge({ WebSocketImpl: FakeWS }).wsUrl(), 'wss://x.trycloudflare.com/ws');
    globalThis.location = { protocol: 'http:', host: '127.0.0.1:8098' };
    assert.equal(new NetBridge({ WebSocketImpl: FakeWS }).wsUrl(), 'ws://127.0.0.1:8098/ws');
  } finally { globalThis.location = saved; }
});
