package port;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.teavm.model.ClassHolder;
import org.teavm.model.ElementModifier;
import org.teavm.model.instructions.InvokeInstruction;
import port.natives.JassimpCacheReader;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Round 80 (front L): the jassimp64 browser native rebuilds an AiScene from the preconverted cache.
 * The scene below is synthetic (our own numbers); the jassimp classes come from the pinned jar,
 * loaded through its own class loader so their private ctors/allocators run as with the JNI.
 */
class JassimpCacheTest {
    private ClassLoader previous;
    private URLClassLoader jar;

    private static File gameJar() throws IOException {
        Path config = Path.of(System.getProperty("user.dir"), "target/config/classpath.txt");
        for (String line : Files.readAllLines(config)) if (line.endsWith(".jar")) return new File(line.strip());
        throw new IllegalStateException("No jar entry in " + config);
    }

    @BeforeEach void loadJar() throws Exception {
        jar = new URLClassLoader(new URL[]{gameJar().toURI().toURL()}, getClass().getClassLoader());
        previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(jar);
        // What Jassimp.loadLibrary() stores from the native size queries (Jassimp64 answers the same).
        Class<?> jassimp = Class.forName("jassimp.Jassimp", true, jar);
        String[] names = {"NATIVE_AIVEKTORKEY_SIZE", "NATIVE_AIQUATKEY_SIZE", "NATIVE_AIVEKTOR3D_SIZE", "NATIVE_FLOAT_SIZE",
            "NATIVE_INT_SIZE", "NATIVE_UINT_SIZE", "NATIVE_DOUBLE_SIZE", "NATIVE_LONG_SIZE"};
        for (int i = 0; i < names.length; i++) jassimp.getField(names[i]).setInt(null, JassimpCacheReader.NATIVE_SIZES[i]);
    }

    @AfterEach void restore() throws IOException {
        Thread.currentThread().setContextClassLoader(previous);
        jar.close();
    }

    static Object call(Object target, String name, Object... args) throws Exception {
        for (Method m : target.getClass().getMethods())
            if (m.getName().equals(name) && m.getParameterCount() == args.length) return m.invoke(target, args);
        throw new NoSuchMethodException(name);
    }

    static void buf(DataOutputStream w, float... values) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : values) b.putFloat(v);
        w.writeInt(b.capacity());
        w.write(b.array());
    }

    static final long FLAGS = 0x4000000L | 0x4L | 0x200L | 0x8L | 0x200000L | 0x100000L | 0x2L;

    /** One triangle, normals + 2D uv, one bone with 3 weights, a material, a 2-key channel, root + child node. */
    static byte[] syntheticCache(long flags) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream w = new DataOutputStream(bytes);
        w.writeInt(JassimpCacheReader.MAGIC);
        w.writeInt(JassimpCacheReader.VERSION);
        w.writeLong(flags);
        for (int s : JassimpCacheReader.NATIVE_SIZES) w.writeInt(s);
        w.writeUTF("models_X/Synthetic.x");
        w.writeLong(123);
        w.writeInt(1);                 // meshes
        w.writeUTF("Tri");
        w.writeInt(0);                 // material
        w.writeInt(0x4);               // aiPrimitiveType_TRIANGLE
        w.writeInt(3);                 // vertices
        w.writeInt(1);                 // faces
        w.writeBoolean(true);
        w.writeInt(12);
        buf(w, 0, 0, 0, 1, 0, 0, 0, 1, 0);
        ByteBuffer faces = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putInt(0).putInt(1).putInt(2);
        w.writeInt(12); w.write(faces.array());
        w.writeInt(-1);                // faceOffsets
        buf(w, 0, 0, 1, 0, 0, 1, 0, 0, 1);
        w.writeInt(-1); w.writeInt(-1); // tangents, bitangents
        for (int c = 0; c < 8; c++) w.writeInt(-1);
        w.writeInt(2); buf(w, 0, 0, 1, 0, 0, 1);
        for (int t = 1; t < 8; t++) w.writeInt(0);
        w.writeInt(1);                 // bones
        w.writeUTF("Bip01");
        for (int i = 0; i < 16; i++) w.writeFloat(i % 5 == 0 ? 1 : 0);
        w.writeInt(3);
        for (int v = 0; v < 3; v++) { w.writeInt(v); w.writeFloat(1f); }
        w.writeInt(1);                 // materials
        w.writeInt(2);
        w.writeUTF("?mat.name"); w.writeInt(0); w.writeInt(0); w.writeInt(3); w.writeByte('S'); w.writeUTF("Skin");
        w.writeUTF("$raw"); w.writeInt(0); w.writeInt(0); w.writeInt(5); w.writeByte('B'); w.writeInt(2); w.write(new byte[]{7, 9});
        w.writeInt(1);                 // animations
        w.writeUTF("Clip"); w.writeDouble(10); w.writeDouble(30);
        w.writeInt(1);
        w.writeUTF("Bip01"); w.writeInt(2); w.writeInt(0); w.writeInt(0); w.writeInt(0); w.writeInt(0);
        ByteBuffer keys = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN);
        keys.putDouble(0).putFloat(1).putFloat(2).putFloat(3).putFloat(0);
        keys.putDouble(10).putFloat(4).putFloat(5).putFloat(6).putFloat(0);
        w.writeInt(48); w.write(keys.array());
        w.writeInt(0); w.writeInt(0);  // rot, scale (empty buffers)
        w.writeBoolean(true);          // root
        w.writeUTF("Scene"); for (int i = 0; i < 16; i++) w.writeFloat(i % 5 == 0 ? 1 : 0);
        w.writeInt(0); w.writeInt(1);
        w.writeUTF("Body"); for (int i = 0; i < 16; i++) w.writeFloat(i % 5 == 0 ? 2 : 0);
        w.writeInt(1); w.writeInt(0); w.writeInt(0);
        w.flush();
        return bytes.toByteArray();
    }

    @Test void rebuildsSceneWithTheLibraryClasses() throws Exception {
        Object scene = JassimpCacheReader.read(new ByteArrayInputStream(syntheticCache(FLAGS)), FLAGS, "/m/Synthetic.x");
        assertEquals("jassimp.AiScene", scene.getClass().getName());
        assertSame(jar, scene.getClass().getClassLoader());
        List<?> meshes = (List<?>) call(scene, "getMeshes");
        assertEquals(1, meshes.size());
        Object mesh = meshes.get(0);
        assertEquals("Tri", call(mesh, "getName"));
        assertEquals(3, call(mesh, "getNumVertices"));
        assertEquals(1, call(mesh, "getNumFaces"));
        assertEquals(true, call(mesh, "isPureTriangle"));
        assertEquals(2, call(mesh, "getFaceVertex", 0, 2));
        assertEquals(1f, call(mesh, "getPositionY", 2));
        assertEquals(true, call(mesh, "hasNormals"));
        assertEquals(1f, call(mesh, "getNormalZ", 1));
        assertEquals(true, call(mesh, "hasTexCoords", 0));
        assertEquals(false, call(mesh, "hasTexCoords", 1));
        assertEquals(2, call(mesh, "getNumUVComponents", 0));
        assertEquals(1f, call(mesh, "getTexCoordU", 1, 0));
        assertEquals(1f, call(mesh, "getTexCoordV", 2, 0));
        List<?> bones = (List<?>) call(mesh, "getBones");
        assertEquals("Bip01", call(bones.get(0), "getName"));
        assertEquals(3, call(bones.get(0), "getNumWeights"));
        Object builtin = Class.forName("jassimp.Jassimp", true, jar).getField("BUILTIN").get(null);
        Object offset = call(bones.get(0), "getOffsetMatrix", builtin);
        assertEquals(1f, call(offset, "get", 3, 3));
        Object weight = ((List<?>) call(bones.get(0), "getBoneWeights")).get(2);
        assertEquals(2, call(weight, "getVertexId"));

        List<?> materials = (List<?>) call(scene, "getMaterials");
        List<?> props = (List<?>) call(materials.get(0), "getProperties");
        assertEquals("Skin", call(props.get(0), "getData"));
        ByteBuffer raw = (ByteBuffer) call(props.get(1), "getData");
        assertEquals(9, raw.get(1));

        Object anim = ((List<?>) call(scene, "getAnimations")).get(0);
        assertEquals(30.0, call(anim, "getTicksPerSecond"));
        Object channel = ((List<?>) call(anim, "getChannels")).get(0);
        assertEquals(2, call(channel, "getNumPosKeys"));
        assertEquals(10.0, call(channel, "getPosKeyTime", 1));
        assertEquals(5f, call(channel, "getPosKeyY", 1));

        Object root = call(scene, "getSceneRoot", builtin);
        assertEquals("Scene", call(root, "getName"));
        Object child = ((List<?>) call(root, "getChildren")).get(0);
        assertSame(root, call(child, "getParent"));
        assertArrayEquals(new int[]{0}, (int[]) call(child, "getMeshes"));
        assertEquals(2f, call(call(child, "getTransform", builtin), "get", 0, 0));
    }

    @Test void refusesOtherFlagsRawFilesAndTruncation() throws Exception {
        byte[] cache = syntheticCache(FLAGS);
        IOException flags = assertThrows(IOException.class,
            () -> JassimpCacheReader.read(new ByteArrayInputStream(cache), FLAGS | 1, "/m/S.x"));
        assertTrue(flags.getMessage().contains("post-process flags"), flags.getMessage());
        IOException raw = assertThrows(IOException.class, () -> JassimpCacheReader.read(
            new ByteArrayInputStream("xof 0303txt 0032\n".getBytes()), FLAGS, "/m/Raw.x"));
        assertTrue(raw.getMessage().contains("not a preconverted jassimp cache"), raw.getMessage());
        byte[] cut = java.util.Arrays.copyOf(cache, cache.length - 10);
        assertThrows(IOException.class, () -> JassimpCacheReader.read(new ByteArrayInputStream(cut), FLAGS, "/m/S.x"));
    }

    // ------------------------------------------------------------- TeaVM rewrite (compiled model)

    private static org.teavm.model.ClassHolderSource source() throws IOException {
        List<File> cp = List.of(Path.of(System.getProperty("user.dir"), "target/classes").toFile(), gameJar());
        return new org.teavm.parsing.ClasspathClassHolderSource(org.teavm.parsing.resource.ResourceProvider.ofClassPath(cp),
            new org.teavm.model.ReferenceCache(), JassimpCacheTest.class.getClassLoader());
    }

    @Test void teavmRewriteUsesNoReflectionAndForwardsTheNatives() throws Exception {
        if (System.getProperty("pz.nativeIndex") == null)
            System.setProperty("pz.nativeIndex", Path.of(System.getProperty("user.dir"), "target/config/natives.tsv").toString());
        var source = source();
        var context = new TransformerPatchTest.TestContext(new org.teavm.model.ClassHierarchy(source));
        var transformer = new port.compiler.JassimpNativeCompatibility();

        ClassHolder objects = source.get("port.natives.JassimpObjects");
        transformer.transformClass(objects, context);
        int rewritten = 0;
        for (var method : objects.getMethods()) {
            if (!method.hasModifier(ElementModifier.STATIC) || method.getLevel() != org.teavm.model.AccessLevel.PUBLIC) continue;
            rewritten++;
            for (var block : method.getProgram().getBasicBlocks())
                for (var insn : block)
                    if (insn instanceof InvokeInstruction invoke) {
                        String owner = invoke.getMethod().getClassName();
                        assertFalse(owner.startsWith("java.lang.reflect") || owner.equals("java.lang.Class")
                            || owner.equals("port.natives.JassimpObjects"), method.getName() + " still calls " + invoke.getMethod());
                    }
        }
        assertTrue(rewritten >= 30, "rewrote " + rewritten);

        ClassHolder jassimp = source.get("jassimp.Jassimp");
        transformer.transformClass(jassimp, context);
        var importFile = jassimp.getMethod(org.teavm.model.MethodDescriptor.parse(
            "aiImportFile(Ljava/lang/String;JLjassimp/AiIOSystem;Ljassimp/AiProgressHandler;)Ljassimp/AiScene;"));
        assertFalse(importFile.hasModifier(ElementModifier.NATIVE));
        boolean forwards = false;
        for (var block : importFile.getProgram().getBasicBlocks())
            for (var insn : block)
                if (insn instanceof InvokeInstruction invoke && invoke.getMethod().getClassName().equals("port.natives.Jassimp64"))
                    forwards = invoke.getMethod().getName().equals("aiImportFile");
        assertTrue(forwards, "aiImportFile must call port.natives.Jassimp64.aiImportFile");
    }
}
