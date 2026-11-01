// Round 82: /ws on the local dev server <-> one RakNet bridge JVM per browser client (scripts/mp-bridge.sh run).
// Minimal RFC 6455 server (binary messages only, no extensions). Each WebSocket message is one bridge frame
// (u32-BE length + body on the child's stdin/stdout); the server never looks inside the frames.
import { createHash } from 'node:crypto';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

export function encodeFrame(payload, opcode = 0x2) {
  const n = payload.length;
  const head = n < 126 ? Buffer.from([0x80 | opcode, n])
    : n < 65536 ? Buffer.from([0x80 | opcode, 126, n >> 8, n & 255])
    : Buffer.concat([Buffer.from([0x80 | opcode, 127]), (() => { const b = Buffer.alloc(8); b.writeBigUInt64BE(BigInt(n)); return b; })()]);
  return Buffer.concat([head, payload]);
}

// Incremental decoder for client->server frames (always masked). Calls onMessage(opcode, Buffer).
export function createDecoder(onMessage, maxBytes = 8 << 20) {
  let buf = Buffer.alloc(0), parts = [], partsOp = 0;
  return chunk => {
    buf = buf.length ? Buffer.concat([buf, chunk]) : chunk;
    for (;;) {
      if (buf.length < 2) return;
      const fin = (buf[0] & 0x80) !== 0, op = buf[0] & 15, masked = (buf[1] & 0x80) !== 0;
      let len = buf[1] & 127, off = 2;
      if (len === 126) { if (buf.length < 4) return; len = buf.readUInt16BE(2); off = 4; }
      else if (len === 127) { if (buf.length < 10) return; len = Number(buf.readBigUInt64BE(2)); off = 10; }
      if (!masked) throw new Error('unmasked client frame');
      if (len > maxBytes) throw new Error('frame too large');
      if (buf.length < off + 4 + len) return;
      const mask = buf.subarray(off, off + 4), data = Buffer.from(buf.subarray(off + 4, off + 4 + len));
      for (let i = 0; i < len; i++) data[i] ^= mask[i & 3];
      buf = buf.subarray(off + 4 + len);
      if (op >= 0x8) { onMessage(op, data); continue; }
      if (op !== 0) { parts = []; partsOp = op; }
      parts.push(data);
      if (fin) { const whole = parts.length === 1 ? parts[0] : Buffer.concat(parts); parts = []; onMessage(partsOp, whole); }
    }
  };
}

// Attach to an http.Server. Only loopback peers and loopback Origins are accepted.
export function attachBridge(server, { command = path.join(ROOT, 'scripts/mp-bridge.sh'), args = ['run'], log = () => {}, gate = null } = {}) {
  const sessions = new Set();
  server.on('upgrade', (req, socket) => {
    const url = new URL(req.url, 'http://x');
    let origin = '';
    try { origin = req.headers.origin ? new URL(req.headers.origin).hostname : '127.0.0.1'; } catch { /* junk Origin: refused below */ }
    const fail = (code, msg) => { log(`ws refused ${code} host=${req.headers.host} origin=${req.headers.origin || '-'} cookie=${/pzweb_k=/.test(req.headers.cookie || '') ? 'y' : 'n'} k=${url.searchParams.has('k') ? 'y' : 'n'}`); socket.end(`HTTP/1.1 ${code} ${msg}\r\nConnection: close\r\n\r\n`); };
    if (url.pathname !== '/ws') return fail(404, 'Not Found');
    if (!['127.0.0.1', '::1', '::ffff:127.0.0.1'].includes(socket.remoteAddress)) return fail(403, 'Forbidden'); // the tunnel connects from loopback too
    // Round 84: a public (tunnel) upgrade needs the key cookie and its own https Origin; everything else keeps the loopback rules.
    const publicVerdict = gate ? gate.wsAllowed(req) : null;
    if (publicVerdict === false) return fail(403, 'Forbidden');
    if (publicVerdict === null && (!['127.0.0.1', 'localhost'].includes(origin) || req.headers['cf-connecting-ip'])) return fail(403, 'Forbidden');
    const key = req.headers['sec-websocket-key'];
    if (!key || req.headers.upgrade?.toLowerCase() !== 'websocket') return fail(400, 'Bad Request');
    socket.write('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n' +
      `Sec-WebSocket-Accept: ${createHash('sha1').update(key + GUID).digest('base64')}\r\n\r\n`);
    socket.setNoDelay(true);
    const child = spawn(command, args, { stdio: ['pipe', 'pipe', 'pipe'] });
    const id = sessions.size + 1; sessions.add(child);
    log(`ws session start pid=${child.pid}`);
    let closed = false;
    const close = why => {
      if (closed) return; closed = true; sessions.delete(child);
      try { child.stdin.end(); } catch {}
      setTimeout(() => { try { child.kill('SIGKILL'); } catch {} }, 1500).unref();
      try { socket.end(encodeFrame(Buffer.alloc(0), 0x8)); } catch {}
      log(`ws session end pid=${child.pid} (${why})`);
    };
    // child -> websocket (reassemble u32-length frames)
    let acc = Buffer.alloc(0);
    child.stdout.on('data', d => {
      acc = acc.length ? Buffer.concat([acc, d]) : d;
      while (acc.length >= 4) {
        const n = acc.readUInt32BE(0);
        if (acc.length < 4 + n) break;
        if (!socket.destroyed) socket.write(encodeFrame(Buffer.from(acc.subarray(4, 4 + n))));
        acc = acc.subarray(4 + n);
      }
    });
    child.stderr.on('data', d => log(`bridge[${child.pid}] ${d.toString().trimEnd()}`));
    child.on('exit', code => close(`bridge exit ${code}`));
    // websocket -> child
    const decode = createDecoder((op, data) => {
      if (op === 0x8) return close('client close');
      if (op === 0x9) { socket.write(encodeFrame(data, 0xA)); return; }
      if (op === 0x2) { const h = Buffer.alloc(4); h.writeUInt32BE(data.length); child.stdin.write(Buffer.concat([h, data])); }
    });
    socket.on('data', d => { try { decode(d); } catch (e) { close(e.message); } });
    socket.on('close', () => close('socket close'));
    socket.on('error', () => close('socket error'));
  });
  return { sessions };
}
