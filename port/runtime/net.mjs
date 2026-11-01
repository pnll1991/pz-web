// Round 82: browser side of zombie.core.raknet.RakNetPeerInterface. A WebSocket to the local dev server (/ws) reaches a bridge
// process that owns the game's real RakNet peer (tools/mp-bridge); see docs/multiplayer-design.md for the frame format.
// Java (port.natives.RakNetPeer) only calls the synchronous methods below; everything arrives through a FIFO of packets.
const C_CONNECT = 1, C_SEND = 2, C_DISCONNECT = 3, C_SHUTDOWN = 4;
const B_PACKET = 0x81, B_CONNECT_RESULT = 0x82, B_STATS = 0x83;
const ID_CONNECTION_ATTEMPT_FAILED = 17, ID_CONNECTION_LOST = 22;
const guidKey = (hi, lo) => `${hi >>> 0}:${lo >>> 0}`;

export class NetBridge {
  constructor({ WebSocketImpl = globalThis.WebSocket, url = null } = {}) {
    this.WebSocketImpl = WebSocketImpl; this.url = url;
    this.ws = null; this.open = false; this.pending = []; this.queue = []; this.current = null;
    this.stats = new Map(); this.lastGuid = null; this.everConnected = false;
    this.counters = { packetsIn: 0, packetsOut: 0, bytesIn: 0, bytesOut: 0 };
  }
  // wss:// when the page itself is https (the public tunnel): a plain ws:// from an https page is blocked as mixed content.
  wsUrl() { return this.url || `${globalThis.location.protocol === 'https:' ? 'wss' : 'ws'}://${globalThis.location.host}/ws`; }

  connect(host, port, password) {
    if (this.ws) return 0;
    const h = new TextEncoder().encode(host), p = new TextEncoder().encode(password);
    const frame = new Uint8Array(1 + 2 + 1 + h.length + p.length);
    frame[0] = C_CONNECT; frame[1] = port >> 8; frame[2] = port & 255; frame[3] = h.length; frame.set(h, 4); frame.set(p, 4 + h.length);
    let ws;
    try { ws = new this.WebSocketImpl(this.wsUrl()); } catch { this.fail(); return 0; }
    ws.binaryType = 'arraybuffer'; this.ws = ws;
    ws.onopen = () => { this.open = true; ws.send(frame); for (const f of this.pending.splice(0)) ws.send(f); };
    ws.onmessage = event => this.onFrame(new Uint8Array(event.data));
    ws.onclose = () => this.closed();
    ws.onerror = () => {};
    return 0; // RakNet ConnectionAttemptResult::CONNECTION_ATTEMPT_STARTED; failures arrive as ID_CONNECTION_ATTEMPT_FAILED
  }

  // A closed socket is a lost link the game must see in its own vocabulary.
  closed() {
    const wasOpen = this.open; this.open = false; this.ws = null;
    if (this.everConnected && this.lastGuid) this.push(Uint8Array.of(ID_CONNECTION_LOST), this.lastGuid.hi, this.lastGuid.lo);
    else if (wasOpen || !this.everConnected) this.fail();
    this.everConnected = false;
  }
  fail() { this.push(Uint8Array.of(ID_CONNECTION_ATTEMPT_FAILED), 0, 0); }
  push(bytes, hi, lo) { this.queue.push({ bytes, hi, lo }); this.counters.packetsIn++; this.counters.bytesIn += bytes.length; }

  onFrame(f) {
    const dv = new DataView(f.buffer, f.byteOffset, f.byteLength);
    if (f[0] === B_PACKET) {
      const hi = dv.getInt32(1), lo = dv.getInt32(5), bytes = f.slice(9);
      this.lastGuid = { hi, lo };
      if (bytes[0] === 16) this.everConnected = true; // ID_CONNECTION_REQUEST_ACCEPTED
      this.push(bytes, hi, lo);
    } else if (f[0] === B_CONNECT_RESULT) {
      if (dv.getInt32(1) !== 0) this.fail();
    } else if (f[0] === B_STATS) {
      const hi = dv.getInt32(1), lo = dv.getInt32(5), n = f[26];
      this.stats.set(guidKey(hi, lo), { avg: dv.getInt32(9), last: dv.getInt32(13), lowest: dv.getInt32(17), mtu: dv.getInt32(21),
        type: f[25], ip: new TextDecoder().decode(f.subarray(27, 27 + n)) });
    }
  }

  tryReceive() { this.current = this.queue.shift() || null; return this.current !== null; }
  currentLength() { return this.current ? this.current.bytes.length : 0; }
  currentHi() { return this.current ? this.current.hi : 0; }
  currentLo() { return this.current ? this.current.lo : 0; }
  copyCurrentTo(dst) { dst.set(this.current.bytes); return this.current.bytes.length; }

  send(src, prio, reliability, channel, hi, lo, broadcast) {
    const frame = new Uint8Array(1 + 4 + 8 + src.length);
    const dv = new DataView(frame.buffer);
    frame[0] = C_SEND; frame[1] = prio; frame[2] = reliability; frame[3] = channel; frame[4] = broadcast ? 1 : 0;
    dv.setInt32(5, hi); dv.setInt32(9, lo); frame.set(src, 13);
    this.counters.packetsOut++; this.counters.bytesOut += src.length;
    if (this.open) this.ws.send(frame); else this.pending.push(frame);
    return src.length;
  }
  disconnect(hi, lo, reason) {
    const r = new TextEncoder().encode(reason || ''), frame = new Uint8Array(9 + r.length), dv = new DataView(frame.buffer);
    frame[0] = C_DISCONNECT; dv.setInt32(1, hi); dv.setInt32(5, lo); frame.set(r, 9);
    if (this.open) this.ws.send(frame);
  }
  shutdown() {
    if (this.open) this.ws.send(Uint8Array.of(C_SHUTDOWN));
    try { this.ws?.close(); } catch {}
    this.ws = null; this.open = false; this.queue = []; this.pending = []; this.stats.clear(); this.lastGuid = null; this.everConnected = false;
  }
  statOf(hi, lo, field, fallback = 0) { return this.stats.get(guidKey(hi, lo))?.[field] ?? fallback; }
  connectionCount() { return this.everConnected ? 1 : 0; }
}
