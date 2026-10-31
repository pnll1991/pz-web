import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Offline jassimp cache ("Preconvertir en tu PC y leer el caché", ROADMAP).
 *
 * Runs the pinned copy's own native library (PZ_HOME/natives/libjassimp64.so) on this PC through the
 * pinned jar's jassimp classes, with the post-processing flags the game passes, and serializes the
 * AiScene object graph the native built (fields, direct ByteBuffers, raw enum values) to a compact
 * binary file. The browser's Jassimp.aiImportFile (port.natives.Jassimp64) reads that file back and
 * rebuilds the same objects with the library's own allocators, so the game code that consumes the
 * scene (ProcessedAiScene, ImportedSkinnedMesh, ImportedSkeleton) runs unchanged.
 *
 * Nothing here is game code; outputs are derived data and go to git-ignored build paths only.
 *
 * Usage (JDK 25 source launch):
 *   java -Djava.library.path=$PZ_HOME/natives -cp $PZ_HOME/projectzomboid.jar tools/jassimp/JassimpCache.java \
 *        --natives $PZ_HOME/natives [--profile x-mesh] [--verify] <out-root> <media-root> <rel.x | @list>...
 * --verify needs port/target/classes on -cp as well.
 * Each <media-root>/<rel.x> becomes <out-root>/<rel.x> (same relative path, cache bytes).
 *
 * Format (big-endian scalars, buffer payloads copied verbatim in the native byte order of the
 * dumping machine, which must be little-endian like wasm): see port.natives.JassimpCacheReader.
 */
public final class JassimpCache {
    static final int MAGIC = 0x505a4a41; // "PZJA"
    static final int VERSION = 1;

    public static void main(String[] args) throws Exception {
        Path natives = null;
        String profile = "x-mesh";
        boolean verify = false;
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--natives" -> natives = Path.of(args[++i]);
                case "--profile" -> profile = args[++i];
                // Round trip through the browser reader (port/target/classes on -cp): rebuild the scene
                // with port.natives.JassimpCacheReader and serialize it again; must be byte-identical.
                case "--verify" -> verify = true;
                default -> {
                    if (args[i].startsWith("@")) {
                        for (String line : Files.readAllLines(Path.of(args[i].substring(1))))
                            if (!line.isBlank() && !line.startsWith("#")) rest.add(line.strip());
                    } else rest.add(args[i]);
                }
            }
        }
        if (natives == null || rest.size() < 3) throw new IllegalArgumentException("usage: --natives DIR [--profile x-mesh] OUT MEDIA REL...");
        if (ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN) throw new IllegalStateException("dump on a little-endian machine");
        // The game's own loader (System.loadLibrary("jassimp64") on Linux), so the library binds to the
        // class loader of the jar's jassimp classes; System.load from this source-launched class would
        // bind it to the launcher's loader and every native would stay unlinked.
        String libPath = System.getProperty("java.library.path", "");
        if (!java.util.Arrays.asList(libPath.split(java.io.File.pathSeparator)).contains(natives.toAbsolutePath().toString()))
            throw new IllegalArgumentException("run with -Djava.library.path=" + natives.toAbsolutePath());
        var loaderClass = Class.forName("zombie.core.skinnedmodel.model.jassimp.JAssImpImporter$LibraryLoader");
        var ctor = loaderClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        jassimp.Jassimp.setLibraryLoader((jassimp.JassimpLibraryLoader) ctor.newInstance());
        Set<jassimp.AiPostProcessSteps> steps = profile(profile);
        Path out = Path.of(rest.get(0));
        Path media = Path.of(rest.get(1));
        for (String rel : rest.subList(2, rest.size())) {
            Path src = media.resolve(rel);
            jassimp.AiScene scene = jassimp.Jassimp.importFile(src.toAbsolutePath().toString(), steps);
            Path dst = out.resolve(rel);
            Files.createDirectories(dst.getParent());
            var bytes = new java.io.ByteArrayOutputStream();
            try (DataOutputStream w = new DataOutputStream(bytes)) {
                writeScene(w, scene, rawFlags(steps), rel, Files.size(src));
            }
            Files.write(dst, bytes.toByteArray());
            if (verify) {
                var reader = Class.forName("port.natives.JassimpCacheReader")
                    .getMethod("read", java.io.InputStream.class, long.class, String.class);
                Object again = reader.invoke(null, new java.io.ByteArrayInputStream(bytes.toByteArray()), rawFlags(steps), rel);
                var copy = new java.io.ByteArrayOutputStream();
                try (DataOutputStream w = new DataOutputStream(copy)) {
                    writeScene(w, (jassimp.AiScene) again, rawFlags(steps), rel, Files.size(src));
                }
                if (!java.util.Arrays.equals(bytes.toByteArray(), copy.toByteArray()))
                    throw new IllegalStateException(rel + ": browser reader round trip differs");
            }
            System.out.println(rel + "\t" + bytes.size() + "\tmeshes=" + scene.getNumMeshes()
                + "\tanims=" + scene.getNumAnimations() + "\t" + summary(scene) + (verify ? "\tverified" : ""));
        }
    }

    /** The exact step sets of the game's call sites (javap of the pinned jar). */
    static Set<jassimp.AiPostProcessSteps> profile(String name) {
        return switch (name) {
            // FileTask_LoadMesh.loadX / FileTask_LoadPhysicsShape.loadX (no script postProcess for .x)
            case "x-mesh" -> EnumSet.of(jassimp.AiPostProcessSteps.FIND_INSTANCES,
                jassimp.AiPostProcessSteps.MAKE_LEFT_HANDED, jassimp.AiPostProcessSteps.LIMIT_BONE_WEIGHTS,
                jassimp.AiPostProcessSteps.TRIANGULATE, jassimp.AiPostProcessSteps.OPTIMIZE_MESHES,
                jassimp.AiPostProcessSteps.REMOVE_REDUNDANT_MATERIALS, jassimp.AiPostProcessSteps.JOIN_IDENTICAL_VERTICES);
            // FileTask_LoadAnimation.loadX
            case "x-anim" -> EnumSet.of(jassimp.AiPostProcessSteps.MAKE_LEFT_HANDED,
                jassimp.AiPostProcessSteps.REMOVE_REDUNDANT_MATERIALS);
            default -> throw new IllegalArgumentException("unknown profile " + name);
        };
    }

    static String summary(jassimp.AiScene scene) {
        StringBuilder s = new StringBuilder();
        for (jassimp.AiMesh m : scene.getMeshes())
            s.append(m.getName()).append(":v").append(m.getNumVertices()).append(",f").append(m.getNumFaces())
             .append(",b").append(m.getBones().size()).append(' ');
        return s.toString().trim();
    }

    static Object get(Object target, String field) throws ReflectiveOperationException {
        Class<?> c = target.getClass();
        Field f = c.getDeclaredField(field);
        f.setAccessible(true);
        return f.get(target);
    }

    static long rawFlags(Set<jassimp.AiPostProcessSteps> steps) throws ReflectiveOperationException {
        var m = jassimp.AiPostProcessSteps.class.getDeclaredMethod("toRawValue", Set.class);
        m.setAccessible(true);
        return (Long) m.invoke(null, steps);
    }

    static int raw(Enum<?> value) throws ReflectiveOperationException { return (Integer) get(value, "m_rawValue"); }

    static void bytes(DataOutputStream w, ByteBuffer b) throws IOException {
        if (b == null) { w.writeInt(-1); return; }
        if (b.order() != ByteOrder.LITTLE_ENDIAN) throw new IllegalStateException("buffer not in native LE order");
        byte[] data = new byte[b.capacity()];
        b.duplicate().clear().get(data);
        w.writeInt(data.length);
        w.write(data);
    }

    static void matrix(DataOutputStream w, Object wrapped) throws Exception {
        float[] m = (float[]) get(wrapped, "m_data");
        if (m.length != 16) throw new IllegalStateException("matrix length " + m.length);
        for (float v : m) w.writeFloat(v);
    }

    static void writeScene(DataOutputStream w, jassimp.AiScene scene, long flags, String rel, long srcSize) throws Exception {
        w.writeInt(MAGIC);
        w.writeInt(VERSION);
        w.writeLong(flags);
        int[] sizes = {jassimp.Jassimp.NATIVE_AIVEKTORKEY_SIZE, jassimp.Jassimp.NATIVE_AIQUATKEY_SIZE,
            jassimp.Jassimp.NATIVE_AIVEKTOR3D_SIZE, jassimp.Jassimp.NATIVE_FLOAT_SIZE, jassimp.Jassimp.NATIVE_INT_SIZE,
            jassimp.Jassimp.NATIVE_UINT_SIZE, jassimp.Jassimp.NATIVE_DOUBLE_SIZE, jassimp.Jassimp.NATIVE_LONG_SIZE};
        for (int s : sizes) w.writeInt(s);
        w.writeUTF(rel);
        w.writeLong(srcSize);

        w.writeInt(scene.getNumMeshes());
        for (jassimp.AiMesh m : scene.getMeshes()) {
            w.writeUTF(m.getName());
            w.writeInt(m.getMaterialIndex());
            int prim = 0;
            for (jassimp.AiPrimitiveType t : m.getPrimitiveTypes()) prim |= raw(t);
            w.writeInt(prim);
            w.writeInt(m.getNumVertices());
            w.writeInt(m.getNumFaces());
            ByteBuffer faces = (ByteBuffer) get(m, "m_faces");
            ByteBuffer offsets = (ByteBuffer) get(m, "m_faceOffsets");
            boolean pure = m.getNumFaces() > 0 && offsets == null;
            w.writeBoolean(pure);
            w.writeInt(faces == null ? 0 : faces.capacity());
            bytes(w, (ByteBuffer) get(m, "m_vertices"));
            bytes(w, faces);
            bytes(w, offsets);
            bytes(w, (ByteBuffer) get(m, "m_normals"));
            bytes(w, (ByteBuffer) get(m, "m_tangents"));
            bytes(w, (ByteBuffer) get(m, "m_bitangents"));
            ByteBuffer[] colors = (ByteBuffer[]) get(m, "m_colorsets");
            for (ByteBuffer c : colors) bytes(w, c);
            ByteBuffer[] uv = (ByteBuffer[]) get(m, "m_texcoords");
            int[] comps = (int[]) get(m, "m_numUVComponents");
            for (int i = 0; i < uv.length; i++) {
                w.writeInt(uv[i] == null ? 0 : comps[i]);
                if (uv[i] != null) bytes(w, uv[i]);
            }
            w.writeInt(m.getBones().size());
            for (jassimp.AiBone b : m.getBones()) {
                w.writeUTF(b.getName());
                matrix(w, get(b, "m_offsetMatrix"));
                w.writeInt(b.getNumWeights());
                for (jassimp.AiBoneWeight bw : b.getBoneWeights()) {
                    w.writeInt(bw.getVertexId());
                    w.writeFloat(bw.getWeight());
                }
            }
        }

        w.writeInt(scene.getNumMaterials());
        for (jassimp.AiMaterial mat : scene.getMaterials()) {
            List<jassimp.AiMaterial.Property> props = mat.getProperties();
            w.writeInt(props.size());
            for (jassimp.AiMaterial.Property p : props) {
                w.writeUTF(p.getKey());
                w.writeInt(p.getSemantic());
                w.writeInt(p.getIndex());
                w.writeInt(raw(p.getType()));
                Object d = p.getData();
                if (d instanceof String s) { w.writeByte('S'); w.writeUTF(s); }
                else if (d instanceof Float f) { w.writeByte('F'); w.writeFloat(f); }
                else if (d instanceof Integer i) { w.writeByte('I'); w.writeInt(i); }
                else if (d instanceof jassimp.AiColor c) {
                    w.writeByte('C');
                    w.writeFloat(c.getRed()); w.writeFloat(c.getGreen()); w.writeFloat(c.getBlue()); w.writeFloat(c.getAlpha());
                } else if (d instanceof ByteBuffer b) { w.writeByte('B'); bytes(w, b); }
                else throw new IllegalStateException("material property data " + (d == null ? "null" : d.getClass()));
            }
        }

        w.writeInt(scene.getNumAnimations());
        for (jassimp.AiAnimation a : scene.getAnimations()) {
            w.writeUTF(a.getName());
            w.writeDouble(a.getDuration());
            w.writeDouble(a.getTicksPerSecond());
            w.writeInt(a.getNumChannels());
            for (jassimp.AiNodeAnim c : a.getChannels()) {
                w.writeUTF(c.getNodeName());
                w.writeInt(c.getNumPosKeys());
                w.writeInt(c.getNumRotKeys());
                w.writeInt(c.getNumScaleKeys());
                w.writeInt(raw(c.getPreState()));
                w.writeInt(raw(c.getPostState()));
                bytes(w, c.getPosKeyBuffer());
                bytes(w, c.getRotKeyBuffer());
                bytes(w, c.getScaleKeyBuffer());
            }
        }

        // The game only clears these lists (JAssImpImporter.takeOutTheTrash); a .x with lights or
        // cameras would need their own encoding, so refuse instead of dropping them.
        if (scene.getNumLights() != 0 || scene.getNumCameras() != 0)
            throw new IllegalStateException(rel + ": lights/cameras not supported by the cache");

        Object root = get(scene, "m_sceneRoot");
        w.writeBoolean(root != null);
        if (root != null) node(w, (jassimp.AiNode) root);
    }

    static void node(DataOutputStream w, jassimp.AiNode n) throws Exception {
        w.writeUTF(n.getName());
        matrix(w, get(n, "m_transformationMatrix"));
        int[] meshes = n.getMeshes();
        w.writeInt(meshes == null ? -1 : meshes.length);
        if (meshes != null) for (int i : meshes) w.writeInt(i);
        Map<?, ?> meta = n.getMetadata();
        if (meta != null && !meta.isEmpty())
            throw new IllegalStateException("node metadata not supported by the cache: " + n.getName());
        w.writeInt(n.getNumChildren());
        for (jassimp.AiNode c : n.getChildren()) node(w, c);
    }
}
