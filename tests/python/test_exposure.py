import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('exposure', ROOT / 'tools/exposure/generate.py')
exposure = importlib.util.module_from_spec(spec)
spec.loader.exec_module(exposure)
from inventory import inspect_game


class ExposureTest(unittest.TestCase):
    def test_literal_registrations_helpers_hierarchy_and_dynamic_limits(self):
        with tempfile.TemporaryDirectory(prefix='pz-exposure-') as temporary:
            root = Path(temporary)
            source = root / 'src'; classes = root / 'classes'; game = root / 'game'
            source.mkdir(); game.mkdir()
            sources = {
                'LuaManager.java': '''package zombie.Lua;
                    public class LuaManager {
                        public static class Exposer {
                            public void setExposed(Class<?> c) {}
                            public void exposeAll() { setExposed(Surface.class); Helper.setExposed(this); }
                            public void dynamic(Class<?> type) { setExposed(type); }
                        }
                        public static class Parent { public int inherited() { return 42; } }
                        public static class Surface extends Parent { public String field; private int hidden; public Surface() {} }
                        public static class Helper { public static void setExposed(Exposer e) { e.setExposed(Parent.class); } }
                    }''',
            }
            for name, text in sources.items(): (source / name).write_text(text)
            subprocess.run(['javac', '-d', str(classes), *map(str, source.glob('*.java'))], check=True, capture_output=True)
            with zipfile.ZipFile(game / 'projectzomboid.jar', 'w') as jar:
                for path in sorted(classes.rglob('*.class')): jar.write(path, path.relative_to(classes))
            (game / 'ProjectZomboid64.json').write_text(json.dumps({'classpath':['projectzomboid.jar'], 'mainClass':'zombie/Lua/LuaManager'}))
            inventory = root / 'inventory.json'; inventory.write_text(json.dumps(inspect_game(game)))
            output = root / 'output'
            manifest = exposure.generate(inventory, output)
            roots = {r['class'] for r in manifest['registrations']}
            self.assertEqual(roots, {'zombie.Lua.LuaManager$Surface', 'zombie.Lua.LuaManager$Parent'})
            self.assertEqual(len(manifest['unresolved']), 1)
            self.assertEqual(len(manifest['helpers']), 1)
            self.assertIn('java.lang.Object', manifest['external_classes'])
            surface = next(c for c in manifest['classes'] if c['name'].endswith('$Surface'))
            self.assertEqual([f['name'] for f in surface['public_fields']], ['field'])
            self.assertIn('<init>', [m['name'] for m in surface['public_methods']])
            first = (output / 'exposure.json').read_bytes()
            exposure.generate(inventory, output)
            self.assertEqual(first, (output / 'exposure.json').read_bytes())
            self.assertEqual({p.name for p in output.iterdir()}, {'exposure.json', 'reflection-lua.txt'})
            with (game / 'projectzomboid.jar').open('ab') as stream: stream.write(b'changed')
            with self.assertRaisesRegex(ValueError, 'changed'): exposure.generate(inventory, output)


if __name__ == '__main__': unittest.main()
