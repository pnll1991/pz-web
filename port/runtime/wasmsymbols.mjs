/**
 * Round 46: real Java frames inside the browser.
 *
 * TeaVM's Wasm GC runtime derives `Throwable.getStackTrace()` from the V8 stack and, when no
 * stack deobfuscator is available, reports every frame as
 * `java.lang.Throwable$FakeClass.fakeMethod(Throwable.java, <wasm address>)`. This module is the
 * deobfuscator this port actually owns: the compiled module carries a wasm `name` custom section
 * whose function-names subsection maps every function index to TeaVM's Java symbol
 * (`zombie.ai.State$Param::<init>`), and Chrome prints that same index in each stack line
 * (`wasm-function[459582]:0x44d86fb`). Parsing it here needs no extra download and no rebuild, and
 * it never fabricates a frame: an index without a name yields no frame.
 */
function reader(bytes) {
  return { bytes, ptr: 0 };
}
function unsigned(r) {
  let value = 0, shift = 0;
  for (;;) {
    const byte = r.bytes[r.ptr++];
    value |= (byte & 127) << shift;
    if (byte < 128) return value >>> 0;
    shift += 7;
    if (shift > 49) throw new Error('Invalid LEB128 in the wasm name section');
  }
}
function string(r) {
  const size = unsigned(r);
  const text = new TextDecoder('utf-8').decode(r.bytes.subarray(r.ptr, r.ptr + size));
  r.ptr += size;
  return text;
}
/** Parse the `name` custom section and return index -> TeaVM Java symbol. */
export function parseNameSection(section) {
  const bytes = section instanceof Uint8Array ? section : new Uint8Array(section);
  const names = [];
  if (bytes.length < 2 || bytes[0] !== 0) return names; // not a name section
  const r = reader(bytes);
  const size = unsigned(r);
  const end = Math.min(r.ptr + size, bytes.length);
  while (r.ptr < end) {
    const subkind = bytes[r.ptr++];
    const subSize = unsigned(r);
    const subEnd = Math.min(r.ptr + subSize, bytes.length);
    if (subkind === 1) { // function names
      const count = unsigned(r);
      for (let i = 0; i < count && r.ptr < subEnd; ++i) {
        const index = unsigned(r);
        const name = string(r);
        names[index] = name;
      }
    }
    r.ptr = subEnd;
  }
  return names;
}
