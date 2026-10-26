import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/inventory'))
from classfile import ClassReader, jni_names
from inventory import inspect_game, report


class InventoryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix='pz-inventory-test-')
        cls.base = Path(cls.temporary.name)
        cls.game = cls.base / 'game'
        (cls.game / 'java').mkdir(parents=True)
        (cls.game / 'linux64').mkdir()
        (cls.game / 'media').mkdir()
        (cls.game / 'media/fixture.txt').write_text('fixture propia, no es un archivo del juego\n')
        (cls.game / 'ProjectZomboid64.json').write_text(json.dumps({
            'classPath':['java/projectzomboid.jar','java/lwjgl.jar'], 'mainClass':'fixture.Native_Fixture','vmArgs':['-Xmx512m']}))
        source = cls.base / 'Native_Fixture.java'
        source.write_text('''package fixture;
          public class Native_Fixture {
            public static native int add(int a,int b);
            public native long overload(int x);
            public native long overload(String x);
            public native byte[] unresolved();
            public static String ordinary(){return "synthetic";}
          }''')
        cls.classes = cls.base / 'classes'
        subprocess.run(['javac','-d',str(cls.classes),str(source)],check=True,capture_output=True)
        cls.bytecode = (cls.classes / 'fixture/Native_Fixture.class').read_bytes()
        gl_source = cls.base / 'GL11.java'
        gl_source.write_text('''package org.lwjgl.opengl;
          public class GL11 {
            public static native void glClearColor(float r,float g,float b,float a);
            public static native void glClear(int mask);
            public static native void glViewport(int x,int y,int w,int h);
            public static native int glGetError();
            public static native void glUnsupported();
          }''')
        subprocess.run(['javac','-d',str(cls.classes),str(gl_source)],check=True,capture_output=True)
        with zipfile.ZipFile(cls.game / 'java/projectzomboid.jar','w') as jar:
            jar.writestr('fixture/Native_Fixture.class',cls.bytecode)
            jar.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\r\nImplementation-Version: fixture-1\r\n\r\n')
        with zipfile.ZipFile(cls.game / 'java/lwjgl.jar','w') as jar:
            jar.writestr('org/lwjgl/opengl/GL11.class',(cls.classes/'org/lwjgl/opengl/GL11.class').read_bytes())
            jar.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\r\nImplementation-Version: synthetic-3.\r\n 0\r\nAutomatic-Module-Name: org.lwjgl\r\n\r\n')
        native = cls.base / 'fixture.c'
        native.write_text('int Java_fixture_Native_1Fixture_add__II(void){return 42;}\nint Java_fixture_Native_1Fixture_overload__I(void){return 42;}\nint Java_unmatched_export(void){return 42;}\n')
        subprocess.run(['gcc','-shared','-fPIC','-g',str(native),'-o',str(cls.game/'linux64/libfixture.so')],check=True,capture_output=True)

    @classmethod
    def tearDownClass(cls): cls.temporary.cleanup()

    def test_metadata_and_jni_cross_reference(self):
        data = inspect_game(self.game)
        self.assertEqual(len(data['native_methods']),9)
        add = next(n for n in data['native_methods'] if n['method']=='add')
        self.assertEqual(add['jni_long'],'Java_fixture_Native_1Fixture_add__II')
        self.assertEqual(add['libraries'],['linux64/libfixture.so'])
        self.assertTrue(data['libraries'][0]['has_debug_sections'])
        self.assertEqual(data['libraries'][0]['unmatched_exports'],['Java_unmatched_export'])
        self.assertEqual(data['lwjgl'][0]['implementation_version'],'synthetic-3.0')
        self.assertEqual(data['jars'][1]['class_majors'],{69:1})
        self.assertNotIn('synthetic"',report(data))

    def test_idempotent_and_no_files_copied(self):
        out = self.base / 'inventory'
        command = [sys.executable,str(ROOT/'tools/inventory/inventory.py'),str(self.game),'--out',str(out)]
        subprocess.run(command,check=True,capture_output=True)
        first = {p.name:p.read_bytes() for p in out.iterdir()}
        subprocess.run(command,check=True,capture_output=True)
        self.assertEqual(first,{p.name:p.read_bytes() for p in out.iterdir()})
        self.assertEqual(set(first),{'inventory.json','report.md'})

    def test_jni_escaping_and_overloads(self):
        short,long_name = jni_names('example.Some_Class','méthod_','([Ljava/lang/String;I)V')
        self.assertEqual(short,'Java_example_Some_1Class_m_000e9thod_1')
        self.assertEqual(long_name,short+'___3Ljava_lang_String_2I')
        self.assertNotEqual(jni_names('X','f','(I)V')[1],jni_names('X','f','(J)V')[1])

    def test_bad_class_is_not_silently_ignored(self):
        with self.assertRaises(ValueError): ClassReader(b'\xca\xfe\xba\xbe').parse()

    def test_missing_tool_and_copy_are_clear(self):
        command = [sys.executable,str(ROOT/'tools/inventory/inventory.py'),str(self.game)]
        result = subprocess.run(command,env={**os.environ,'PATH':''},capture_output=True,text=True)
        self.assertNotEqual(result.returncode,0)
        self.assertIn('Missing required tool: du',result.stderr)
        result = subprocess.run(command+['--out',str(self.game/'bad-output')],capture_output=True,text=True)
        self.assertNotEqual(result.returncode,0)
        self.assertFalse((self.game/'bad-output').exists())

    def test_pack_integrity_and_determinism(self):
        import hashlib
        startup = self.base/'startup.txt'; startup.write_text('media/fixture.txt\n')
        out = self.base/'packs'
        command = [sys.executable,str(ROOT/'tools/pack/pack.py'),'--pz-home',str(self.game),'--files',str(startup),'--out',str(out)]
        subprocess.run(command,check=True,capture_output=True)
        first = (out/'index.json').read_bytes()
        index = json.loads(first)
        blob = out/index['packs'][0]['name']
        self.assertEqual(hashlib.sha256(blob.read_bytes()).hexdigest(),index['packs'][0]['sha256'])
        self.assertEqual(blob.read_bytes(),(self.game/'media/fixture.txt').read_bytes())
        subprocess.run(command,check=True,capture_output=True)
        self.assertEqual(first,(out/'index.json').read_bytes())

    def test_glgen_only_maps_exact_supported_signatures(self):
        sys.path.insert(0,str(ROOT/'tools/glgen'))
        from generate import generate
        source = self.base/'fixture-inventory.json'
        source.write_text(json.dumps(inspect_game(self.game)))
        output = self.base/'gl-config'
        generate(source,output)
        coverage = json.loads((output/'coverage.json').read_text())['signatures']
        self.assertEqual(len(coverage),5)
        self.assertEqual(sum(row['status']=='direct' for row in coverage),4)
        unsupported = next(row for row in coverage if row['method']=='glUnsupported')
        self.assertEqual(unsupported['status'],'throws-native')
        self.assertNotIn('glUnsupported',(output/'gl-overrides.tsv').read_text())


if __name__ == '__main__': unittest.main()
