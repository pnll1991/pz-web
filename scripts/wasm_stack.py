#!/usr/bin/env python3
"""Resolve TeaVM raw stack offsets using only Wasm function names and body bounds."""
import sys
from pathlib import Path

def symbols(filename):
    data = Path(filename).read_bytes()
    def unsigned(p):
        value = shift = 0
        while True:
            byte = data[p]; p += 1; value |= (byte & 127) << shift
            if byte < 128: return value, p
            shift += 7
            if shift > 63: raise ValueError('Invalid LEB128')
    def string(p):
        size, p = unsigned(p)
        return data[p:p+size].decode('utf8'), p+size
    p = 8; names = {}; bodies = []; imports = 0
    while p < len(data):
        kind = data[p]; size, p = unsigned(p+1); end = p+size
        if kind == 2:
            count,q = unsigned(p)
            for _ in range(count):
                _,q = string(q); _,q = string(q); import_kind = data[q]; q += 1
                if import_kind == 0:
                    _,q = unsigned(q); imports += 1
                elif import_kind == 4:
                    q += 1; _,q = unsigned(q)  # exception tag attribute and type
                elif import_kind == 3:
                    value_type = data[q]; q += 1
                    if value_type in (0x63,0x64): _,q = unsigned(q)
                    q += 1  # mutability
                elif import_kind in (1,2):
                    if import_kind == 1:
                        value_type = data[q]; q += 1
                        if value_type in (0x63,0x64): _,q = unsigned(q)
                    flags,q = unsigned(q); _,q = unsigned(q)
                    if flags & 1: _,q = unsigned(q)
                else:
                    raise ValueError(f'Unsupported Wasm import kind {import_kind}')
        if kind == 10:
            count, q = unsigned(p)
            for index in range(count):
                size, q = unsigned(q); bodies.append((q,q+size,index)); q += size
        if kind == 0:
            label, q = string(p)
            if label == 'name':
                while q < end:
                    subkind = data[q]; size,q = unsigned(q+1); subend = q+size
                    if subkind == 1:
                        count,q = unsigned(q)
                        for _ in range(count):
                            index,q = unsigned(q); name,q = string(q); names[index] = name
                    q = subend
        p = end
    return [(a,b,names.get(i+imports,f'function {i+imports}')) for a,b,i in bodies]

if __name__ == '__main__':
    table = symbols(sys.argv[1])
    for value in sys.argv[2:]:
        offset = int(value,0)
        print(offset, next((name for lo,hi,name in table if lo <= offset < hi),'outside function bodies'))
