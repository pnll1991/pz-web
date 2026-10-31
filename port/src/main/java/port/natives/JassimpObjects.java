package port.natives;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * The object surgery the jassimp JNI library does on the jar's jassimp classes (private ctors,
 * private fields, the library's own allocators), one small step per method.
 *
 * Our sources do not compile against the game jar, so every jassimp type is {@code Object} here.
 * Two implementations of the same contract:
 *  - TeaVM: port.compiler.JassimpNativeCompatibility replaces every body below with direct IR
 *    (new / getfield / putfield / invoke on the jassimp classes), exactly like the native's
 *    NewObject / SetObjectField / CallVoidMethod. The reflection code is never compiled to wasm.
 *  - JVM (tests and the cache verifier): the bodies use reflection with setAccessible, against
 *    the jassimp classes of {@link #loader()}.
 * Keep both in sync; JassimpNativeCompatibility fails the build if a method here has no rewrite.
 */
public final class JassimpObjects {
    private JassimpObjects() { }

    // ---- JVM implementation helpers (unused in the wasm build) ----
    private static ClassLoader loader() {
        ClassLoader l = Thread.currentThread().getContextClassLoader();
        return l != null ? l : JassimpObjects.class.getClassLoader();
    }
    private static Class<?> type(String name) {
        try { return Class.forName("jassimp." + name, true, loader()); }
        catch (ClassNotFoundException e) { throw new IllegalStateException("jassimp class missing: " + name, e); }
    }
    private static Object make(String cls, Class<?>[] params, Object... args) {
        try {
            Constructor<?> c = type(cls).getDeclaredConstructor(params);
            c.setAccessible(true);
            return c.newInstance(args);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(cls, e); }
    }
    private static Field field(Object target, String name) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) { throw new IllegalStateException(name, e); }
    }
    private static Object get(Object target, String name) {
        try { return field(target, name).get(target); } catch (IllegalAccessException e) { throw new IllegalStateException(name, e); }
    }
    private static void set(Object target, String name, Object value) {
        try { field(target, name).set(target, value); } catch (IllegalAccessException e) { throw new IllegalStateException(name, e); }
    }
    private static Object call(Class<?> cls, Object target, String name, Class<?>[] params, Object... args) {
        try {
            Method m = cls.getDeclaredMethod(name, params);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw new IllegalStateException(name, e.getCause());
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(name, e); }
    }
    @SuppressWarnings("unchecked")
    private static void add(Object target, String listField, Object value) { ((List<Object>) get(target, listField)).add(value); }

    // ---- the contract (one JNI step each) ----
    /** new AiScene() */
    public static Object newScene() { return make("AiScene", new Class<?>[0]); }
    /** scene.m_meshes.add(mesh) */
    public static void sceneAddMesh(Object scene, Object mesh) { add(scene, "m_meshes", mesh); }
    /** scene.m_materials.add(material) */
    public static void sceneAddMaterial(Object scene, Object material) { add(scene, "m_materials", material); }
    /** scene.m_animations.add(animation) */
    public static void sceneAddAnimation(Object scene, Object animation) { add(scene, "m_animations", animation); }
    /** scene.m_sceneRoot = root */
    public static void sceneSetRoot(Object scene, Object root) { set(scene, "m_sceneRoot", root); }

    /** new AiMesh() (private ctor) */
    public static Object newMesh() { return make("AiMesh", new Class<?>[0]); }
    /** m_name, m_materialIndex, setPrimitiveTypes(raw), allocateBuffers(nv, nf, pure, faceBufferSize) */
    public static void meshInit(Object mesh, String name, int materialIndex, int primitiveTypes,
                                int numVertices, int numFaces, boolean pure, int faceBufferSize) {
        set(mesh, "m_name", name);
        try { field(mesh, "m_materialIndex").setInt(mesh, materialIndex); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        Class<?> c = type("AiMesh");
        call(c, mesh, "setPrimitiveTypes", new Class<?>[]{int.class}, primitiveTypes);
        call(c, mesh, "allocateBuffers", new Class<?>[]{int.class, int.class, boolean.class, int.class},
            numVertices, numFaces, pure, faceBufferSize);
    }
    /** allocateDataChannel(type, index) */
    public static void meshChannel(Object mesh, int channelType, int index) {
        call(type("AiMesh"), mesh, "allocateDataChannel", new Class<?>[]{int.class, int.class}, channelType, index);
    }
    public static ByteBuffer meshVertices(Object mesh) { return (ByteBuffer) get(mesh, "m_vertices"); }
    public static ByteBuffer meshFaces(Object mesh) { return (ByteBuffer) get(mesh, "m_faces"); }
    public static ByteBuffer meshFaceOffsets(Object mesh) { return (ByteBuffer) get(mesh, "m_faceOffsets"); }
    public static ByteBuffer meshNormals(Object mesh) { return (ByteBuffer) get(mesh, "m_normals"); }
    public static ByteBuffer meshTangents(Object mesh) { return (ByteBuffer) get(mesh, "m_tangents"); }
    public static ByteBuffer meshBitangents(Object mesh) { return (ByteBuffer) get(mesh, "m_bitangents"); }
    public static ByteBuffer meshColors(Object mesh, int index) { return ((ByteBuffer[]) get(mesh, "m_colorsets"))[index]; }
    public static ByteBuffer meshTexCoords(Object mesh, int index) { return ((ByteBuffer[]) get(mesh, "m_texcoords"))[index]; }
    /** mesh.m_bones.add(bone) */
    public static void meshAddBone(Object mesh, Object bone) { add(mesh, "m_bones", bone); }

    /** new AiBone(); m_name = name; m_offsetMatrix = Jassimp.wrapMatrix(offset) */
    public static Object newBone(String name, float[] offset) {
        Object bone = make("AiBone", new Class<?>[0]);
        set(bone, "m_name", name);
        set(bone, "m_offsetMatrix", wrapMatrix(offset));
        return bone;
    }
    /** new AiBoneWeight(); m_vertexId; m_weight; bone.m_boneWeights.add(weight) */
    public static void boneAddWeight(Object bone, int vertexId, float weight) {
        Object w = make("AiBoneWeight", new Class<?>[0]);
        try {
            field(w, "m_vertexId").setInt(w, vertexId);
            field(w, "m_weight").setFloat(w, weight);
        } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        add(bone, "m_boneWeights", w);
    }

    /** new AiMaterial() */
    public static Object newMaterial() { return make("AiMaterial", new Class<?>[0]); }
    /** material.m_properties.add(property) */
    public static void materialAddProperty(Object material, Object property) { add(material, "m_properties", property); }
    /** new AiMaterial.Property(key, semantic, index, type, Object data) */
    public static Object newPropertyObject(String key, int semantic, int index, int type, Object data) {
        return make("AiMaterial$Property", new Class<?>[]{String.class, int.class, int.class, int.class, Object.class},
            key, semantic, index, type, data);
    }
    /** new AiMaterial.Property(key, semantic, index, type, dataLength): allocates its direct buffer */
    public static Object newPropertyBuffer(String key, int semantic, int index, int type, int length) {
        return make("AiMaterial$Property", new Class<?>[]{String.class, int.class, int.class, int.class, int.class},
            key, semantic, index, type, length);
    }
    /** (ByteBuffer) property.getData() */
    public static ByteBuffer propertyBuffer(Object property) { return (ByteBuffer) get(property, "m_data"); }
    /** Jassimp.wrapColor4(r, g, b, a) */
    public static Object wrapColor4(float r, float g, float b, float a) {
        return call(type("Jassimp"), null, "wrapColor4", new Class<?>[]{float.class, float.class, float.class, float.class}, r, g, b, a);
    }

    /** new AiAnimation(name, duration, ticksPerSecond) */
    public static Object newAnimation(String name, double duration, double ticksPerSecond) {
        return make("AiAnimation", new Class<?>[]{String.class, double.class, double.class}, name, duration, ticksPerSecond);
    }
    /** animation.m_nodeAnims.add(channel) */
    public static void animationAddChannel(Object animation, Object channel) { add(animation, "m_nodeAnims", channel); }
    /** new AiNodeAnim(name, numPos, numRot, numScale, preState, postState): allocates its key buffers */
    public static Object newNodeAnim(String name, int numPos, int numRot, int numScale, int preState, int postState) {
        return make("AiNodeAnim", new Class<?>[]{String.class, int.class, int.class, int.class, int.class, int.class},
            name, numPos, numRot, numScale, preState, postState);
    }
    public static ByteBuffer nodeAnimPosKeys(Object channel) { return (ByteBuffer) get(channel, "m_posKeys"); }
    public static ByteBuffer nodeAnimRotKeys(Object channel) { return (ByteBuffer) get(channel, "m_rotKeys"); }
    public static ByteBuffer nodeAnimScaleKeys(Object channel) { return (ByteBuffer) get(channel, "m_scaleKeys"); }

    /** Jassimp.wrapMatrix(m) */
    public static Object wrapMatrix(float[] m) {
        return call(type("Jassimp"), null, "wrapMatrix", new Class<?>[]{float[].class}, (Object) m);
    }
    /** Jassimp.wrapSceneNode(parent, Jassimp.wrapMatrix(matrix), meshes, name); the AiNode ctor links the parent */
    public static Object wrapNode(Object parent, float[] matrix, int[] meshes, String name) {
        return call(type("Jassimp"), null, "wrapSceneNode",
            new Class<?>[]{Object.class, Object.class, int[].class, String.class}, parent, wrapMatrix(matrix), meshes, name);
    }
}
