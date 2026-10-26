"""Read class metadata only. Never decompile methods or execute game code."""
import struct


class ClassReader:
    def __init__(self, data):
        self.data, self.offset = data, 0

    def take(self, size):
        if size < 0 or self.offset + size > len(self.data):
            raise ValueError('Truncated class file')
        result = self.data[self.offset:self.offset + size]
        self.offset += size
        return result

    def u1(self): return self.take(1)[0]
    def u2(self): return struct.unpack('>H', self.take(2))[0]
    def u4(self): return struct.unpack('>I', self.take(4))[0]

    def attributes(self):
        for _ in range(self.u2()):
            self.u2()
            self.take(self.u4())

    def parse(self):
        if self.u4() != 0xcafebabe:
            raise ValueError('Invalid class magic')
        minor, major = self.u2(), self.u2()
        pool = [None] * self.u2()
        index = 1
        while index < len(pool):
            tag = self.u1()
            if tag == 1:
                # Java's modified UTF-8 encodes NUL and surrogate pairs specially.
                value = self.take(self.u2()).replace(b'\xc0\x80', b'\0').decode('utf-8', 'surrogatepass')
                pool[index] = value.encode('utf-16', 'surrogatepass').decode('utf-16', 'surrogatepass')
            elif tag == 3: pool[index] = struct.unpack('>i', self.take(4))[0]
            elif tag == 4: self.take(4)
            elif tag in (5, 6): self.take(8); index += 1
            elif tag in (7, 8, 16, 19, 20): pool[index] = (tag, self.u2())
            elif tag in (9, 10, 11, 12, 17, 18): self.take(4)
            elif tag == 15: self.take(3)
            else: raise ValueError(f'Unsupported constant pool tag {tag}')
            index += 1
        flags, this_class, parent = self.u2(), self.u2(), self.u2()
        name = pool[pool[this_class][1]]
        interfaces = [pool[pool[self.u2()][1]].replace('/', '.') for _ in range(self.u2())]
        fields = []
        for _ in range(self.u2()):
            access, field, descriptor = self.u2(), self.u2(), self.u2()
            item = {'name': pool[field], 'descriptor': pool[descriptor], 'access': access}
            for _ in range(self.u2()):
                attribute, size = pool[self.u2()], self.u4()
                if attribute == 'ConstantValue' and size == 2:
                    value = pool[self.u2()]
                    if isinstance(value, tuple) and value[0] == 8: value = pool[value[1]]
                    if isinstance(value, (int, str)): item['constant'] = value
                else: self.take(size)
            fields.append(item)
        methods = []
        for _ in range(self.u2()):
            access, method, descriptor = self.u2(), self.u2(), self.u2()
            methods.append({'name': pool[method], 'descriptor': pool[descriptor],
                            'access': access, 'native': bool(access & 0x100), 'static': bool(access & 8)})
            self.attributes()
        self.attributes()
        references = sorted({pool[item[1]].replace('/', '.') for item in pool
                             if isinstance(item, tuple) and item[0] == 7})
        return {'name': name.replace('/', '.'), 'major': major, 'minor': minor,
                'access': flags, 'methods': methods, 'fields': fields,
                'super': pool[pool[parent][1]].replace('/', '.') if parent else None,
                'interfaces': interfaces, 'class_references': references}


def jni_escape(value):
    result = []
    raw = value.encode('utf-16-be')
    for (code,) in struct.iter_unpack('>H', raw):
        char = chr(code)
        if char.isascii() and char.isalnum(): result.append(char)
        elif char == '/': result.append('_')
        elif char == '_': result.append('_1')
        elif char == ';': result.append('_2')
        elif char == '[': result.append('_3')
        else: result.append(f'_0{code:04x}')
    return ''.join(result)


def jni_names(class_name, method, descriptor):
    short = 'Java_' + jni_escape(class_name.replace('.', '/')) + '_' + jni_escape(method)
    args = descriptor[1:descriptor.index(')')]
    return short, short + '__' + jni_escape(args)
