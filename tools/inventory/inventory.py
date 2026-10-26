#!/usr/bin/env python3
"""Offline, deterministic inventory of a local Project Zomboid Linux copy."""
import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

from classfile import ClassReader, jni_names


def resolve_game_home(path):
    """Accept the Steam install directory or its single Linux launcher child."""
    root = Path(path).expanduser().resolve()
    for candidate in (root, root / 'projectzomboid'):
        if (candidate / 'ProjectZomboid64.json').is_file():
            resolved = candidate.resolve()
            if not resolved.is_relative_to(root):
                raise ValueError('Nested game directory escapes PZ_HOME')
            return resolved
    raise ValueError(f'PZ_HOME must contain ProjectZomboid64.json (directly or in projectzomboid/): {root}')


def launcher_metadata(root):
    raw = json.loads((root / 'ProjectZomboid64.json').read_text())
    # The real Linux launcher uses lowercase "classpath". Retain support for
    # the alternative spelling used by earlier fixtures and launchers.
    entries = raw.get('classpath', raw.get('classPath'))
    if not isinstance(entries, list) or not entries or not all(isinstance(x, str) and x for x in entries):
        raise ValueError('Launcher classpath must be a nonempty list of strings')
    if 'classpath' in raw and 'classPath' in raw and raw['classpath'] != raw['classPath']:
        raise ValueError('Conflicting launcher classpath keys')
    main = raw.get('mainClass')
    if not isinstance(main, str) or not main:
        raise ValueError('Launcher mainClass is missing')
    return {'classPath': entries, 'mainClass': main.replace('/', '.'), 'vmArgs': raw.get('vmArgs', []),
            'classpath_key': 'classpath' if 'classpath' in raw else 'classPath'}


def command(*args, pass_fds=()):
    result = subprocess.run(args, capture_output=True, text=True, pass_fds=pass_fds, env={**os.environ, 'LC_ALL': 'C'})
    if result.returncode:
        raise RuntimeError(f'{args[0]} failed for {args[-1]}: {result.stderr.strip()}')
    return result.stdout.strip()


def inspect_elf(path, label, size, pass_fds=()):
    symbols = sorted({line.split()[-1].split('@')[0] for line in
                      command('nm', '-D', '--defined-only', str(path), pass_fds=pass_fds).splitlines()
                      if line and line.split()[-1].startswith('Java_')})
    sections = command('readelf', '-S', '-W', str(path), pass_fds=pass_fds)
    description = command('file', '-L', '-b', str(path), pass_fds=pass_fds)
    lower = label.lower()
    other_os = any(x in lower for x in ('android', 'freebsd', 'openbsd', 'dragonfly', 'sunos', 'linux-musl'))
    return {'path': label, 'size': size, 'file': description,
            'scope': 'bundled-jre' if label.startswith('jre64/') else
                     ('linux-x86_64' if 'x86-64' in description and not other_os else 'other-platform'),
            'has_debug_sections': bool(re.search(r'\.(?:z?debug_)', sections)),
            'has_symtab': '.symtab' in sections, 'jni_exports': symbols,
            'matched_methods': [], 'unmatched_exports': []}


def discover():
    home = Path.home()
    roots = {home / '.local/share/Steam', home / '.steam/steam',
             home / '.var/app/com.valvesoftware.Steam/.local/share/Steam'}
    for root in list(roots):
        libraries = root / 'steamapps/libraryfolders.vdf'
        if libraries.is_file():
            roots.update(Path(p.replace('\\\\', '\\')) for p in re.findall(r'"path"\s+"([^"]+)"', libraries.read_text()))
    candidates = {p / 'steamapps/common/ProjectZomboid' for p in roots}
    pinned = home / 'pz-pinned'
    if pinned.is_dir(): candidates.update(pinned.iterdir())
    if os.environ.get('PZ_HOME'): candidates.add(Path(os.environ['PZ_HOME']))
    found = set()
    for candidate in candidates:
        try: found.add(str(resolve_game_home(candidate)))
        except ValueError: pass
    return sorted(found)


def manifest(data):
    # Continuation lines belong to the preceding attribute; only main section.
    text = data.decode('utf-8', 'replace').replace('\r\n', '\n').split('\n\n')[0]
    result, key = {}, None
    for line in text.splitlines():
        if line.startswith(' ') and key: result[key] += line[1:]
        elif ': ' in line:
            key, value = line.split(': ', 1)
            result[key] = value
    return result


def inspect_game(root):
    for tool in ('du', 'nm', 'file', 'readelf'):
        if not shutil.which(tool): raise RuntimeError(f'Missing required tool: {tool}')
    root = resolve_game_home(root)
    launcher = launcher_metadata(root)
    tree = [{'path': p.name, 'disk_usage': command('du', '-sh', '--', str(p)).split('\t')[0]}
            for p in sorted(root.iterdir()) if not p.is_symlink()]
    natives, jars, libraries, kahlua, lwjgl, skipped, loose = [], [], [], [], [], [], []
    jdk_refs = set()
    embedded_lwjgl = {}
    game_jar = None

    def inspect_class(data, source, entry):
        cls = ClassReader(data).parse()
        if cls['name'].startswith('org.lwjgl.'):
            info = embedded_lwjgl.setdefault(source, {'modules': Counter(), 'version': {}})
            parts = cls['name'].split('.')
            info['modules'][parts[2] if len(parts) > 3 else 'core'] += 1
            if cls['name'] == 'org.lwjgl.Version':
                info['version'] = {f['name']: f['constant'] for f in cls['fields']
                                   if f['name'] in ('VERSION_MAJOR', 'VERSION_MINOR', 'VERSION_REVISION') and 'constant' in f}
        if cls['name'].startswith('se.krka.kahlua.') or '.kahlua.' in cls['name']:
            kahlua.append({'source': source, 'class': cls['name']})
        jdk_refs.update(n for n in cls['class_references'] if n.startswith(('java.', 'javax.', 'jdk.', 'sun.')))
        for method in cls['methods']:
            if not method['native']: continue
            short, long_name = jni_names(cls['name'], method['name'], method['descriptor'])
            natives.append({'source': source, 'entry': entry, 'class_major': cls['major'], 'class': cls['name'], 'method': method['name'],
                            'descriptor': method['descriptor'], 'static': method['static'],
                            'jni_short': short, 'jni_long': long_name, 'libraries': []})
        return cls

    for directory, dirs, files in os.walk(root, followlinks=False):
        dirs.sort()
        for filename in sorted(files):
            path = Path(directory) / filename
            relative = path.relative_to(root).as_posix()
            if not path.resolve().is_relative_to(root):
                skipped.append({'path': relative, 'reason': 'symlink outside PZ_HOME'})
                continue
            if filename.endswith('.jar'):
                with zipfile.ZipFile(path) as jar:
                    names = sorted(jar.namelist())
                    if 'zombie/core/Core.class' in names: game_jar = path
                    mf = manifest(jar.read('META-INF/MANIFEST.MF')) if 'META-INF/MANIFEST.MF' in names else {}
                    classes = [n for n in names if n.endswith('.class')]
                    majors = Counter()
                    for entry in classes:
                        cls = inspect_class(jar.read(entry), relative, entry)
                        majors[cls['major']] += 1
                    for entry in names:
                        if not re.search(r'\.so(?:\.[0-9]+)*$', entry): continue
                        payload = jar.read(entry)
                        if not payload.startswith(b'\x7fELF'): continue
                        # ELF tools inspect an anonymous memory file; no game binary
                        # is extracted into the repository or onto disk.
                        fd = os.memfd_create('pz-inventory-elf')
                        try:
                            with os.fdopen(os.dup(fd), 'wb') as stream: stream.write(payload)
                            libraries.append(inspect_elf(f'/proc/self/fd/{fd}', relative + '!/' + entry, len(payload), (fd,)))
                        finally: os.close(fd)
                    with path.open('rb') as stream: sha = hashlib.file_digest(stream, 'sha256').hexdigest()
                    item = {'path': relative, 'size': path.stat().st_size, 'sha256': sha,
                            'implementation_version': mf.get('Implementation-Version'),
                            'implementation_title': mf.get('Implementation-Title'),
                            'automatic_module_name': mf.get('Automatic-Module-Name'),
                            'class_count': len(classes), 'class_majors': dict(sorted(majors.items()))}
                    jars.append(item)
                    if 'lwjgl' in filename.lower() or 'lwjgl' in (mf.get('Implementation-Title') or '').lower():
                        lwjgl.append(item)
            elif filename.endswith('.class'):
                cls = inspect_class(path.read_bytes(), relative, filename)
                loose.append({'path': relative, 'class': cls['name'], 'major': cls['major']})
            elif '.so' in filename and re.search(r'\.so(?:\.[0-9]+)*$', filename):
                libraries.append(inspect_elf(path, relative, path.stat().st_size))
    natives.sort(key=lambda n: (n['class'], n['method'], n['descriptor'], n['source'], n['entry']))
    symbol_map = {}
    for index, native in enumerate(natives):
        for key in ('jni_short', 'jni_long'): symbol_map.setdefault(native[key], []).append(index)
    for lib in libraries:
        for symbol in lib['jni_exports']:
            matches = symbol_map.get(symbol, [])
            if not matches: lib['unmatched_exports'].append(symbol)
            for index in matches:
                native = natives[index]
                if lib['path'] not in native['libraries']: native['libraries'].append(lib['path'])
                lib['matched_methods'].append({'native_index': index, 'symbol': symbol,
                                               'ambiguous': len(matches) > 1})
    build_id, build_source = None, None
    for parent in (root.parent.parent, root.parent.parent.parent):
        steam_manifest = parent / 'appmanifest_108600.acf'
        if steam_manifest.is_file():
            match = re.search(r'"buildid"\s+"([^"]+)"', steam_manifest.read_text())
            if match:
                build_id, build_source = match[1], str(steam_manifest)
                break
    pin = root.parent if root.name == 'projectzomboid' else root
    if not build_id and pin.parent == Path.home() / 'pz-pinned' and pin.name.isdigit():
        build_id, build_source = pin.name, 'owner pin directory; not independently verified against Steam manifest'
    version, version_source = None, None
    for jar in jars:
        if Path(jar['path']).name.lower() == 'projectzomboid.jar' and jar['implementation_version']:
            version, version_source = jar['implementation_version'], jar['path'] + ':Implementation-Version'
    for name in ('version.txt', 'media/version.txt'):
        candidate = root / name
        if candidate.is_file() and candidate.resolve().is_relative_to(root):
            value = candidate.read_text()[:256].strip()
            if re.fullmatch(r'\d+(?:\.\d+)+(?:[-+a-zA-Z0-9._ ]*)?', value):
                version, version_source = value, name
                break
    if not version and game_jar:
        project = Path(__file__).resolve().parents[2]
        java = project / '.toolchain/jdk/bin/java'
        if java.is_file():
            metadata = command(str(java), '-Xmx512m', str(project / 'tools/inventory/BytecodeMetadata.java'), str(game_jar), 'zombie/Lua/LuaManager')
            for line in metadata.splitlines():
                values = line.split('\t')
                if len(values) in (5, 6) and values[:3] == ['V', 'zombie.core.Core#<clinit>()V', '(IILjava/lang/String;)V']:
                    version = '.'.join(values[3:5]) + (values[5] if len(values) == 6 else '')
                    version_source = 'zombie.core.Core.<clinit>: literal GameVersion constructor arguments; no game execution'
    for source, info in sorted(embedded_lwjgl.items()):
        values = info['version']
        version_parts = [values.get(n) for n in ('VERSION_MAJOR', 'VERSION_MINOR', 'VERSION_REVISION')]
        embedded_lwjgl[source] = {'modules': dict(sorted(info['modules'].items())),
                                 'version': '.'.join(map(str, version_parts)) if all(x is not None for x in version_parts) else None,
                                 'version_source': 'org.lwjgl.Version ConstantValue fields'}
    return {'schema_version': 2, 'pz_home': str(root), 'steam_build_id': build_id, 'steam_build_id_source': build_source,
            'game_version': version or 'unknown; needs local game version evidence', 'game_version_source': version_source,
            'launcher': launcher,
            'top_level': tree, 'jars': sorted(jars, key=lambda j: j['path']),
            'loose_classes': sorted(loose, key=lambda c: c['path']),
            'lwjgl': sorted(lwjgl, key=lambda j: j['path']), 'native_methods': natives,
            'embedded_lwjgl': embedded_lwjgl,
            'libraries': sorted(libraries, key=lambda l: l['path']), 'kahlua': sorted(kahlua, key=lambda c:(c['source'],c['class'])),
            'jdk_class_references': sorted(jdk_refs), 'skipped': skipped,
            'notes': ['JNI dynamic registration and non-exported functions cannot be mapped by nm.',
                      'Every jar is scanned; duplicate and multi-release definitions retain their source.',
                      'Class references are candidates, not a TeaVM compatibility verdict.']}


def report(data):
    lines = ['# Inventario local de Project Zomboid', '',
             '| Biblioteca | Plataforma | Exports JNI | Declaraciones vinculadas | Exports sin match |',
             '|---|---|---:|---:|---:|']
    for lib in data['libraries']:
        lines.append(f"| {lib['path']} | {lib['scope']} | {len(lib['jni_exports'])} | {len({m['native_index'] for m in lib['matched_methods']})} | {len(lib['unmatched_exports'])} |")
    lines += ['', f"Métodos nativos: {len(data['native_methods'])}. Sin biblioteca exportada identificada: "
              f"{sum(not n['libraries'] for n in data['native_methods'])}.",
              f"Steam build id: {data['steam_build_id'] or 'desconocido (copia fijada o sin manifest)'}. Versión: {data['game_version']}.",
              f"Firmas nativas distintas: {len({(n['class'], n['method'], n['descriptor']) for n in data['native_methods']})}; las declaraciones incluyen variantes multi-release y duplicados entre jars.",
              f"Fuente de build id: {data['steam_build_id_source']}. Fuente de versión: {data['game_version_source']}.",
              '', '## Launcher', '', '```json', json.dumps(data['launcher'], indent=2, ensure_ascii=False), '```',
              '', '## Primer nivel', '']
    lines += [f"- `{x['path']}`: {x['disk_usage']}" for x in data['top_level']]
    lines += ['', '## Jars', '', '| Ruta | Bytes | Implementation-Version | Clases | Majors |', '|---|---:|---|---:|---|']
    lines += [f"| {x['path']} | {x['size']} | {x['implementation_version'] or 'no declarada'} | {x['class_count']} | {x['class_majors']} |" for x in data['jars']]
    lines += ['', '## LWJGL', '']
    lines += [f"- `{x['path']}`: {x['implementation_version'] or 'versión no declarada'}, módulo {x['automatic_module_name'] or 'no declarado'}" for x in data['lwjgl']]
    lines += [f"- Integrado en `{source}`: {info['version'] or 'versión no declarada'} ({info['version_source']}); módulos/clases: {info['modules']}"
              for source, info in data['embedded_lwjgl'].items()]
    lines += ['', '## Nativas (firmas, sin cuerpos)', '', '| Clase | Método | Descriptor JNI | Biblioteca |', '|---|---|---|---|']
    lines += [f"| {n['class']} | {n['method']} | `{n['descriptor']}` | {', '.join(n['libraries']) or 'sin match exportado'} |" for n in data['native_methods']]
    lines += ['', '## Símbolos y debug', '']
    lines += [f"- `{l['path']}`: debug={l['has_debug_sections']}, symtab={l['has_symtab']}; {l['file']}" for l in data['libraries']]
    lines += ['', f"## Kahlua: {len(data['kahlua'])} clases", '']
    counts = Counter(x['source'] for x in data['kahlua'])
    lines += [f'- `{source}`: {count}' for source, count in sorted(counts.items())]
    lines += ['', '## Límites', ''] + ['- ' + note for note in data['notes']]
    if data['skipped']: lines += ['- Symlinks externos omitidos: ' + json.dumps(data['skipped'])]
    return '\n'.join(lines) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('pz_home', nargs='?', default=os.environ.get('PZ_HOME'))
    parser.add_argument('--out', type=Path, default=Path(__file__).resolve().parents[2] / 'inventory')
    parser.add_argument('--find', action='store_true')
    args = parser.parse_args()
    if args.find:
        candidates = discover()
        print(json.dumps(candidates, indent=2))
        return 0 if candidates else 2
    if not args.pz_home:
        parser.error('Pass a pinned Linux copy path or set PZ_HOME. No game files are downloaded.')
    requested_root = Path(args.pz_home).expanduser().resolve()
    root = resolve_game_home(requested_root)
    if args.out.resolve().is_relative_to(requested_root):
        parser.error('--out must be outside PZ_HOME; the game copy is read-only')
    data = inspect_game(root)
    args.out.mkdir(parents=True, exist_ok=True)
    for name, content in [('inventory.json', json.dumps(data, indent=2, ensure_ascii=False) + '\n'), ('report.md', report(data))]:
        temporary = args.out / (name + '.tmp')
        temporary.write_text(content)
        temporary.replace(args.out / name)
    print(f"PZ_HOME: {root}")
    print(f"Inventory: {args.out / 'report.md'} ({len(data['jars'])} jars, {len(data['native_methods'])} native methods)")
    return 0


if __name__ == '__main__':
    try: sys.exit(main())
    except (OSError, ValueError, RuntimeError, zipfile.BadZipFile) as error:
        sys.exit(f'inventory: {error}')
