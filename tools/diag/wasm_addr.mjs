// Round 79: map the numbers TeaVM prints as `Throwable$FakeClass.fakeMethod(Throwable.java:N)` (module
// byte offsets of the call sites) to the Java symbols in the wasm `name` section.
//   node tools/diag/wasm_addr.mjs boot.wasm < console.txt   -> annotates every fakeMethod line
//   node tools/diag/wasm_addr.mjs boot.wasm 73006252 ...   -> one symbol per address
import { readFileSync } from 'node:fs';

const bytes = readFileSync(process.argv[2]);
let p = 8;
const leb = () => { let v = 0, s = 0; for (;;) { const b = bytes[p++]; v += (b & 127) * 2 ** s; if (b < 128) return v; s += 7; } };
// GC reference value types carry a heap type after the 0x63/0x64 prefix.
const valtype = () => { const b = bytes[p++]; if (b === 0x63 || b === 0x64) leb(); };
let imported = 0, starts = [], names = [];
while (p < bytes.length) {
  const id = bytes[p++], size = leb(), end = p + size;
  if (id === 2) { // imports: count function imports, they shift the function index space
    const n = leb();
    for (let i = 0; i < n; i++) {
      for (let k = 0; k < 2; k++) { const l = leb(); p += l; }
      const kind = bytes[p++];
      if (kind === 0) { leb(); imported++; }
      else if (kind === 1) { valtype(); const f = leb(); leb(); if (f & 1) leb(); }
      else if (kind === 2) { const f = leb(); leb(); if (f & 1) leb(); }
      else if (kind === 3) { valtype(); p++; }
      else if (kind === 4) { p++; leb(); }
    }
  } else if (id === 10) {
    const n = leb();
    for (let i = 0; i < n; i++) { const s = leb(); starts.push(p); p += s; }
  } else if (id === 0) {
    const save = p, nl = leb(), name = bytes.subarray(p, p + nl).toString(); p += nl;
    if (name === 'name') {
      while (p < end) {
        const sub = bytes[p++], subSize = leb(), subEnd = p + subSize;
        if (sub === 1) { const n = leb(); for (let i = 0; i < n; i++) { const idx = leb(), l = leb(); names[idx] = bytes.subarray(p, p + l).toString(); p += l; } }
        p = subEnd;
      }
    }
    p = save;
  }
  p = end;
}
function symbol(addr) {
  let lo = 0, hi = starts.length - 1;
  if (addr < starts[0]) return '?';
  while (lo < hi) { const mid = (lo + hi + 1) >> 1; if (starts[mid] <= addr) lo = mid; else hi = mid - 1; }
  return names[lo + imported] ?? `func${lo + imported}`;
}
const args = process.argv.slice(3);
if (args.length) for (const a of args) console.log(a, symbol(+a));
else {
  const text = readFileSync(0, 'utf8');
  for (const line of text.split('\n')) {
    const m = /fakeMethod\(Throwable\.java:(\d+)\)/.exec(line);
    console.log(m ? line.replace(m[0], symbol(+m[1])) : line);
  }
}
