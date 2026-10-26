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


def command(*args):
    result = subprocess.run(args, capture_output=True, text=True, env={**os.environ, 'LC_ALL': 'C'})
    if result.returncode:
        raise RuntimeError(f'{args[0]} failed for {args[-1]}: {result.stderr.strip()}')
    return result.stdout.strip()


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
    return sorted({str(p.resolve()) for p in candidates if (p / 'ProjectZomboid64.json').is_file()})


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
    launcher_file = root / 'ProjectZomboid64.json'
    if not launcher_file.is_file(): raise ValueError(f'PZ_HOME must contain ProjectZomboid64.json: {root}')
    launcher = json.loads(launcher_file.read_text())
    tree = [{'path': p.name, 'disk_usage': command('du', '-sh', '--', str(p)).split('\t')[0]}
            for p in sorted(root.iterdir()) if not p.is_symlink()]
    natives, jars, libraries, kahlua, lwjgl, skipped, loose = [], [], [], [], [], [], []
    jdk_refs = set()

    def inspect_class(data, source, entry):
        cls = ClassReader(data).parse()
        if cls['name'].startswith('se.krka.kahlua.') or '.kahlua.' in cls['name']:
            kahlua.append({'source': source, 'class': cls['name']})
        jdk_refs.update(n for n in cls['class_references'] if n.startswith(('java.', 'javax.', 'jdk.', 'sun.')))
        for method in cls['methods']:
            if not method['native']: continue
            short, long_name = jni_names(cls['name'], method['name'], method['descriptor'])
            natives.append({'source': source, 'class': cls['name'], 'method': method['name'],
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
                    mf = manifest(jar.read('META-INF/MANIFEST.MF')) if 'META-INF/MANIFEST.MF' in names else {}
                    classes = [n for n in names if n.endswith('.class')]
                    majors = Counter()
                    for entry in classes:
                        cls = inspect_class(jar.read(entry), relative, entry)
                        majors[cls['major']] += 1
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
                symbols = sorted({line.split()[-1].split('@')[0] for line in
                                  command('nm', '-D', '--defined-only', str(path)).splitlines()
                                  if line and line.split()[-1].startswith('Java_')})
                sections = command('readelf', '-S', '-W', str(path))
                libraries.append({'path': relative, 'size': path.stat().st_size,
                                  'file': command('file', '-b', str(path)),
                                  'has_debug_sections': bool(re.search(r'\.(?:z?debug_)', sections)),
                                  'has_symtab': '.symtab' in sections, 'jni_exports': symbols,
                                  'matched_methods': [], 'unmatched_exports': []})
    natives.sort(key=lambda n: (n['class'], n['method'], n['descriptor'], n['source']))
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
    build_id = None
    steam_manifest = root.parent.parent / 'appmanifest_108600.acf'
    if steam_manifest.is_file():
        match = re.search(r'"buildid"\s+"([^"]+)"', steam_manifest.read_text())
        if match: build_id = match[1]
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
    return {'schema_version': 1, 'pz_home': str(root), 'steam_build_id': build_id,
            'game_version': version or 'unknown; needs local game version evidence', 'game_version_source': version_source,
            'launcher': {key: launcher.get(key) for key in ('classPath', 'mainClass', 'vmArgs')},
            'top_level': tree, 'jars': sorted(jars, key=lambda j: j['path']),
            'loose_classes': sorted(loose, key=lambda c: c['path']),
            'lwjgl': sorted(lwjgl, key=lambda j: j['path']), 'native_methods': natives,
            'libraries': sorted(libraries, key=lambda l: l['path']), 'kahlua': sorted(kahlua, key=lambda c:(c['source'],c['class'])),
            'jdk_class_references': sorted(jdk_refs), 'skipped': skipped,
            'notes': ['JNI dynamic registration and non-exported functions cannot be mapped by nm.',
                      'Every jar is scanned; duplicate and multi-release definitions retain their source.',
                      'Class references are candidates, not a TeaVM compatibility verdict.']}


def report(data):
    lines = ['# Inventario local de Project Zomboid', '',
             '| Biblioteca | Exports JNI | Métodos vinculados | Exports sin match |',
             '|---|---:|---:|---:|']
    for lib in data['libraries']:
        lines.append(f"| {lib['path']} | {len(lib['jni_exports'])} | {len({m['native_index'] for m in lib['matched_methods']})} | {len(lib['unmatched_exports'])} |")
    lines += ['', f"Métodos nativos: {len(data['native_methods'])}. Sin biblioteca exportada identificada: "
              f"{sum(not n['libraries'] for n in data['native_methods'])}.",
              f"Steam build id: {data['steam_build_id'] or 'desconocido (copia fijada o sin manifest)'}. Versión: {data['game_version']}.",
              '', '## Launcher', '', '```json', json.dumps(data['launcher'], indent=2, ensure_ascii=False), '```',
              '', '## Primer nivel', '']
    lines += [f"- `{x['path']}`: {x['disk_usage']}" for x in data['top_level']]
    lines += ['', '## Jars', '', '| Ruta | Bytes | Implementation-Version | Clases | Majors |', '|---|---:|---|---:|---|']
    lines += [f"| {x['path']} | {x['size']} | {x['implementation_version'] or 'no declarada'} | {x['class_count']} | {x['class_majors']} |" for x in data['jars']]
    lines += ['', '## LWJGL', '']
    lines += [f"- `{x['path']}`: {x['implementation_version'] or 'versión no declarada'}, módulo {x['automatic_module_name'] or 'no declarado'}" for x in data['lwjgl']]
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
    root = Path(args.pz_home).expanduser().resolve()
    if args.out.resolve().is_relative_to(root):
        parser.error('--out must be outside PZ_HOME; the game copy is read-only')
    data = inspect_game(root)
    args.out.mkdir(parents=True, exist_ok=True)
    for name, content in [('inventory.json', json.dumps(data, indent=2, ensure_ascii=False) + '\n'), ('report.md', report(data))]:
        temporary = args.out / (name + '.tmp')
        temporary.write_text(content)
        temporary.replace(args.out / name)
    print(f"Inventory: {args.out / 'report.md'} ({len(data['jars'])} jars, {len(data['native_methods'])} native methods)")
    return 0


if __name__ == '__main__':
    try: sys.exit(main())
    except (OSError, ValueError, RuntimeError, zipfile.BadZipFile) as error:
        sys.exit(f'inventory: {error}')
