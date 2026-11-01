// Round 82 smoke test: Node WebSocket client -> dev server /ws -> bridge JVM -> real PZ server (127.0.0.1:16261).
// Prints the RakNet packet ids received; expects 16 (ID_CONNECTION_REQUEST_ACCEPTED).
const url = process.argv[2] || 'ws://127.0.0.1:8098/ws';
const ws = new WebSocket(url); ws.binaryType = 'arraybuffer';
const t0 = Date.now(), seen = [];
ws.onopen = () => {
  const host = Buffer.from('127.0.0.1'), pw = Buffer.from(''); // empty-password hash is computed by the game; here only the wire contract is tested
  ws.send(Buffer.concat([Buffer.from([1]), Buffer.from([16261 >> 8, 16261 & 255, host.length]), host, pw]));
};
ws.onmessage = ev => {
  const b = Buffer.from(ev.data), type = b[0];
  if (type === 0x82) console.log('connect result', b.readInt32BE(1));
  else if (type === 0x81) { seen.push(b[9]); console.log(`packet id=${b[9]} len=${b.length - 9} guid=${b.readBigInt64BE(1)} +${Date.now() - t0}ms`); }
  else if (type === 0x83) { /* stats */ }
};
setTimeout(() => { console.log('ids', seen.join(',')); ws.close(); process.exit(seen.includes(16) ? 0 : 1); }, 9000);
