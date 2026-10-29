#!/usr/bin/env python3
"""Locate the exact Wasm GC instruction at a trap byte offset inside a function body.

Usage: wasmloc.py boot.wasm <function-index> <byte-offset> [context=40]

The offset comes straight from a Chrome stack line ("wasm-function[N]:0xOFF").
Prints the decoded instruction stream of that body and marks the instruction that
contains the offset. struct.get/array.get/ref.cast/ref.test operands are resolved
to Java names via the `name` section (subkind 4 = type names, subkind 5 = field names).
"""
import sys
from pathlib import Path

data = Path(sys.argv[1]).read_bytes()


def leb(p, signed=False):
    r = s = 0
    while True:
        b = data[p]; p += 1; r |= (b & 0x7F) << s; s += 7
        if b < 0x80:
            if signed and (b & 0x40): r -= (1 << s)
            return r, p


def string(p):
    n, p = leb(p)
    return data[p:p + n].decode('utf8', 'replace'), p + n


secs = []
p = 8
while p < len(data):
    sid = data[p]; p += 1
    size, p = leb(p)
    secs.append((sid, p, size)); p += size

fn_imports = 0
names, type_names, field_names = {}, {}, {}
for sid, start, size in secs:
    end = start + size
    if sid == 2:
        q = start; n, q = leb(q)
        for _ in range(n):
            _, q = string(q); _, q = string(q)
            kind = data[q]; q += 1
            if kind == 0:
                _, q = leb(q); fn_imports += 1
            elif kind == 1:
                t = data[q]; q += 1
                if t in (0x63, 0x64): _, q = leb(q)
                fl = data[q]; q += 1; _, q = leb(q)
                if fl & 1: _, q = leb(q)
            elif kind == 2:
                fl = data[q]; q += 1; _, q = leb(q)
                if fl & 1: _, q = leb(q)
            elif kind == 3:
                t = data[q]; q += 1
                if t in (0x63, 0x64): _, q = leb(q)
                q += 1
            elif kind == 4:
                q += 1; _, q = leb(q)
    elif sid == 0:
        q = start
        label, q = string(q)
        if label != 'name': continue
        while q < end:
            sub = data[q]; q += 1
            sz, q = leb(q); se = q + sz
            if sub == 1:
                n, q = leb(q)
                for _ in range(n):
                    i, q = leb(q); nm, q = string(q); names[i] = nm
            elif sub == 4:
                n, q = leb(q)
                for _ in range(n):
                    i, q = leb(q); nm, q = string(q); type_names[i] = nm
            elif sub == 5:
                n, q = leb(q)
                for _ in range(n):
                    ti, q = leb(q)
                    m, q = leb(q)
                    for j in range(m):
                        fi, q = leb(q); nm, q = string(q)
                        field_names[(ti, fi)] = nm
            q = se

bodies = []
for sid, start, size in secs:
    if sid == 10:
        q = start; n, q = leb(q)
        for _ in range(n):
            sz, q = leb(q); bodies.append((q, sz)); q += sz

NOOPERAND = {
    0x00: 'unreachable', 0x01: 'nop', 0x05: 'return', 0x0b: 'end', 0x1a: 'drop', 0x1b: 'select',
    0x45: 'i32.eqz', 0x46: 'i32.eq', 0x47: 'i32.ne', 0x48: 'i32.lt_s', 0x49: 'i32.lt_u',
    0x4a: 'i32.gt_s', 0x4b: 'i32.gt_u', 0x4c: 'i32.le_s', 0x4d: 'i32.le_u', 0x4e: 'i32.ge_s',
    0x4f: 'i32.ge_u', 0x50: 'i64.eqz', 0x51: 'i64.eq', 0x52: 'i64.ne', 0x5b: 'f32.eq',
    0x5c: 'f32.ne', 0x61: 'f64.eq', 0x62: 'f64.ne', 0x67: 'i32.wrap_i64',
    0x68: 'i32.trunc_f32_s', 0x6a: 'i32.trunc_f64_s', 0x6b: 'i64.extend_i32_s',
    0x6c: 'i64.extend_i32_u', 0x6d: 'i64.trunc_f32_s', 0x6f: 'i64.trunc_f64_s',
    0x71: 'f32.convert_i32_s', 0x73: 'f32.convert_i64_s', 0x74: 'f64.convert_i32_s',
    0x76: 'f64.convert_i64_s', 0x77: 'f32.demote_f64', 0x78: 'f64.promote_f32',
    0x81: 'f32.add', 0x82: 'f32.sub', 0x83: 'f32.mul', 0x84: 'f32.div', 0x85: 'f32.max',
    0x86: 'f32.min', 0x88: 'f64.add', 0x89: 'f64.sub', 0x8a: 'f64.mul', 0x8b: 'f64.div',
    0x90: 'f32.abs', 0x91: 'f32.neg', 0xa0: 'i32.extend8', 0xa1: 'i32.extend16',
}
LOADS = {0x28: 'i32.load', 0x29: 'i64.load', 0x2a: 'f32.load', 0x2b: 'f64.load',
         0x2c: 'i32.load8_s', 0x2d: 'i32.load8_u', 0x2e: 'i32.load16_s', 0x2f: 'i32.load16_u',
         0x30: 'i64.load8_s', 0x31: 'i64.load8_u', 0x32: 'i64.load16_s', 0x33: 'i64.load32_s'}
STORES = {0x36: 'i32.store', 0x37: 'i64.store', 0x38: 'f32.store', 0x39: 'f64.store',
          0x3a: 'i32.store8', 0x3b: 'i32.store16', 0x3c: 'i64.store8', 0x3d: 'i64.store16',
          0x3e: 'i64.store32'}
# TeaVM 0.16 emits the FINAL Wasm GC encoding: 0xFB prefix for struct/array/ref ops.
GC_SUB = {
    0: ('struct.new', 1), 1: ('struct.new_default', 1), 2: ('struct.get', 2),
    3: ('struct.get_s', 2), 4: ('struct.get_u', 2), 5: ('struct.set', 2),
    6: ('array.new_default', 1), 7: ('array.new', 1), 8: ('array.new_fixed', 0),
    9: ('array.new_data', 1), 10: ('array.new_data_non_ref', 1), 11: ('array.get', 1),
    12: ('array.get_s', 1), 13: ('array.get_u', 1), 14: ('array.set', 1), 15: ('array.len', 0),
    16: ('array.grow', 1), 17: ('array.copy', 2), 18: ('array.init_data', 1),
    19: ('array.init_data_non_ref', 1), 20: ('ref.test non-null', 1), 21: ('ref.test nullable', 1),
    22: ('ref.cast non-null', 1), 23: ('ref.cast nullable', 1), 24: ('extern.convert_any', 0),
    25: ('any.convert_extern', 0), 26: ('ref.eq', 0), 27: ('i31.new', 0), 28: ('i31.get_id', 0),
    29: ('i31.get_rtt', 0), 30: ('i31.add', 0), 31: ('i31.sub', 0), 32: ('i31.mul', 0),
    33: ('i31.div_s', 0), 34: ('i31.div_u', 0), 35: ('i31.rem_s', 0), 36: ('i31.rem_u', 0),
    37: ('i31.and', 0), 38: ('i31.or', 0), 39: ('i31.xor', 0), 40: ('i31.shl', 0),
    41: ('i31.shr_s', 0), 42: ('i31.shr_u', 0), 43: ('i31.wrap', 0),
    44: ('i31.sext', 1), 45: ('i31.trunc', 1), 46: ('i31.sub_sat', 0), 47: ('i31.neg_sat', 0),
}
# Old GC proposal numbering (kept for reference -- NOT what Chrome runs).
GC_SUB_OLD = {
    0: ('ref.is_null', 0), 1: ('ref.func', 1), 2: ('ref.as_func', 1), 3: ('any.convert_extern', 0),
    4: ('extern.convert_any', 0), 5: ('struct.new', 1), 6: ('struct.new_default', 1),
    7: ('struct.get', 2), 8: ('struct.get_s', 2), 9: ('struct.get_u', 2), 10: ('struct.set', 2),
    11: ('array.new_default', 1), 12: ('array.new', 1), 13: ('array.new_data', 1),
    14: ('array.new_data_non_ref', 1), 15: ('array.get', 1), 16: ('array.get_s', 1),
    17: ('array.get_u', 1), 18: ('array.set', 1), 19: ('array.len', 0), 20: ('array.grow', 1),
    21: ('ref.test non-null', 1), 22: ('ref.test nullable', 1), 23: ('ref.cast non-null', 1),
    24: ('ref.cast nullable', 1),
}
NUM_PREFIX = {0: 'i32.trunc_sat_f32_s', 1: 'i32.trunc_sat_f32_u', 2: 'i32.trunc_sat_f64_s',
              3: 'i32.trunc_sat_f64_u', 4: 'i64.trunc_sat_f32_s', 5: 'i64.trunc_sat_f32_u',
              6: 'i64.trunc_sat_f64_s', 7: 'i64.trunc_sat_f64_u', 8: 'memory.init',
              9: 'data.drop', 10: 'memory.copy', 11: 'memory.fill', 14: 'memory.size',
              15: 'memory.grow'}
REF_OPS = {0xd0: ('ref.null', 1), 0xd1: ('ref.is_null', 0), 0xd2: ('ref.func', 1),
           0xd3: ('ref.as_non_null', 0), 0xd4: ('ref.eq', 0), 0xd5: ('br_on_null', 2),
           0xd6: ('br_on_non_null', 2), 0xd7: ('br_on_cast', 3), 0xd8: ('br_on_cast_fail', 3)}

def skip_locals(p):
    n, p = leb(p)
    for _ in range(n):
        count, p = leb(p)
        t = data[p]; p += 1
        if t in (0x63, 0x64):  # ref / ref-null declarations carry a type index
            _, p = leb(p)
        p += 1  # packed-storage flag (final GC encoding)
    return p


def decode(start, end):
    out = []
    p = skip_locals(start)
    while p < end:
        off = p
        op = data[p]; p += 1
        if op in NOOPERAND: out.append((off, NOOPERAND[op])); continue
        if op == 0x41:
            v, p = leb(p, True); out.append((off, 'i32.const %d' % v)); continue
        if op == 0x42:
            v, p = leb(p, True); out.append((off, 'i64.const %d' % v)); continue
        if op == 0x43: p += 4; out.append((off, 'f32.const')); continue
        if op == 0x44: p += 8; out.append((off, 'f64.const')); continue
        if op in (0x02, 0x03, 0x04, 0x06):
            t, p = leb(p, True)
            out.append((off, {0x02: 'block', 0x03: 'loop', 0x04: 'if', 0x06: 'try'}[op] + ' %d' % t))
            continue
        if op == 0x07: out.append((off, 'catch')); continue
        if op == 0x08:
            t, p = leb(p); out.append((off, 'catch %d' % t)); continue
        if op == 0x09: out.append((off, 'throw_ref')); continue
        if op == 0x14:
            t, p = leb(p); out.append((off, 'throw tag %d' % t)); continue
        if op in (0x0c, 0x0d):
            d, p = leb(p); out.append((off, ('br' if op == 0x0c else 'br_if') + ' %d' % d)); continue
        if op == 0x0e:
            n, p = leb(p)
            for _ in range(n + 1): _, p = leb(p)
            out.append((off, 'br_table %d' % n)); continue
        if op == 0x10:
            f, p = leb(p); out.append((off, 'call %d %s' % (f, names.get(f, '?')))); continue
        if op == 0x12:
            f, p = leb(p); out.append((off, 'return_call %d %s' % (f, names.get(f, '?')))); continue
        if op == 0x11:
            t, p = leb(p); _, p = leb(p); out.append((off, 'call_indirect %d' % t)); continue
        if op == 0x20:
            v, p = leb(p); out.append((off, 'local.get %d' % v)); continue
        if op == 0x21:
            v, p = leb(p); out.append((off, 'local.set %d' % v)); continue
        if op == 0x22:
            v, p = leb(p); out.append((off, 'local.tee %d' % v)); continue
        if op == 0x23:
            v, p = leb(p); out.append((off, 'global.get %d' % v)); continue
        if op == 0x24:
            v, p = leb(p); out.append((off, 'global.set %d' % v)); continue
        if op in LOADS or op in STORES:
            a, p = leb(p); o, p = leb(p)
            base = LOADS.get(op) or STORES.get(op)
            out.append((off, '%s offset=%d align=%d' % (base, o, a))); continue
        if op == 0xfb:
            sub, p = leb(p)
            name, nops = GC_SUB.get(sub, ('gc-sub-%d' % sub, 0))
            vals = []
            for _ in range(nops):
                v, p = leb(p); vals.append(v)
            text = '%s %s' % (name, ' '.join(str(v) for v in vals))
            if (name.startswith('struct.get') or name.startswith('struct.set')) and len(vals) == 2:
                text += ' -> %s.%s' % (type_names.get(vals[0], '?'),
                                       field_names.get((vals[0], vals[1]), '?'))
            elif vals:
                text += ' -> %s' % type_names.get(vals[0], '?')
            out.append((off, text)); continue
        if op in REF_OPS:
            nm, nops = REF_OPS[op]
            vals = []
            for _ in range(nops):
                v, p = leb(p); vals.append(v)
            text = '%s %s' % (nm, ' '.join(str(v) for v in vals))
            if vals:
                text += ' -> %s' % type_names.get(vals[-1], 'heap')
            out.append((off, text)); continue
        if op == 0xfc:
            sub, p = leb(p)
            out.append((off, 'num-prefix %d %s' % (sub, NUM_PREFIX.get(sub, '?')))); continue
        out.append((off, 'UNKNOWN opcode 0x%x (stop at %s)' % (op, hex(off)))); break
    return out


if __name__ == '__main__':
    idx = int(sys.argv[2])
    target = int(sys.argv[3], 0)
    window = int(sys.argv[4]) if len(sys.argv) > 4 else 40
    print('functions=%d fn_imports=%d type_names=%d' % (len(bodies), fn_imports, len(type_names)))
    if idx < fn_imports:
        print('function', idx, 'is an import'); raise SystemExit
    start, size = bodies[idx - fn_imports]
    print('function', idx, names.get(idx, '?'), 'body', hex(start), 'size', size,
          'code', hex(skip_locals(start)), 'target', hex(target))
    instr = decode(start, start + size)
    hits = [i for i, (off, _) in enumerate(instr) if off <= target < (instr[i + 1][0] if i + 1 < len(instr) else start + size)]
    lo = max(0, (hits[0] if hits else 0) - window)
    hi = min(len(instr), (hits[0] if hits else 0) + 4)
    for i in range(lo, hi):
        off, text = instr[i]
        print('>>>' if i == (hits[0] if hits else -1) else '   ', hex(off), text)
    if not hits:
        print('!! offset not inside a decoded instruction boundary; last decoded',
              hex(instr[-1][0]), instr[-1][1])
