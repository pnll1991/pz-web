package port.compiler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;
import org.teavm.model.instructions.InvocationType;

/**
 * Round 80 (front L): the jassimp64 JNI library answered by port.natives.Jassimp64 over a
 * preconverted cache.
 *
 * 1. jassimp.Jassimp: the inventoried natives (aiImportFile + the eight struct-size queries +
 *    getErrorString) forward to Jassimp64; aiImportFile's jassimp-typed parameters are passed as
 *    Object and the result is cast back to AiScene.
 * 2. port.natives.JassimpObjects: every public static method body (JVM reflection) is replaced by
 *    the direct IR the JNI library performs: new on the (private) constructors, getfield/putfield
 *    on the private fields, invokespecial on the private allocators, Jassimp.wrapMatrix /
 *    wrapColor4 / wrapSceneNode. Field types and methods are resolved against the jar and the
 *    build fails if one is missing or if JassimpObjects grows a method without a rewrite here.
 */
public final class JassimpNativeCompatibility implements ClassHolderTransformer {
    static final String JASSIMP = "jassimp.Jassimp";
    static final String MODULE = "port.natives.Jassimp64";
    static final String OBJECTS = "port.natives.JassimpObjects";
    private static final ValueType OBJ = ValueType.object("java.lang.Object");
    private static final ValueType STR = ValueType.object("java.lang.String");
    private static final ValueType BUF = ValueType.object("java.nio.ByteBuffer");
    private static final ValueType LIST = ValueType.object("java.util.List");
    private static final ValueType FLOATS = ValueType.arrayOf(ValueType.FLOAT);
    private static final ValueType INTS = ValueType.arrayOf(ValueType.INTEGER);
    static final Set<String> FORWARDED = Set.of(
        "aiImportFile(Ljava/lang/String;JLjassimp/AiIOSystem;Ljassimp/AiProgressHandler;)Ljassimp/AiScene;",
        "getErrorString()Ljava/lang/String;", "getVKeysize()I", "getQKeysize()I", "getV3Dsize()I",
        "getfloatsize()I", "getintsize()I", "getuintsize()I", "getdoublesize()I", "getlongsize()I");

    private final Set<String> inventoried = new java.util.HashSet<>();

    public JassimpNativeCompatibility() {
        try {
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of(System.getProperty("pz.nativeIndex")))) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\t", -1);
                if (fields.length != 4) throw new IllegalArgumentException("Invalid native index line");
                if (fields[0].equals(JASSIMP)) inventoried.add(fields[1] + fields[2]);
            }
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Missing native index: run scripts/build.sh", error);
        }
        if (!inventoried.containsAll(FORWARDED))
            throw new IllegalStateException("Inventory does not list the jassimp natives we forward: " + inventoried);
    }

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (cls.getName().equals(JASSIMP)) forwardNatives(cls, context);
        else if (cls.getName().equals(OBJECTS)) rewriteObjects(cls, context);
    }

    private void forwardNatives(ClassHolder cls, ClassHolderTransformerContext context) {
        int done = 0;
        for (MethodHolder method : cls.getMethods()) {
            String key = method.getName() + MethodDescriptor.get(method).signatureToString();
            if (!FORWARDED.contains(key) || !inventoried.contains(key)) continue;
            if (!method.hasModifier(ElementModifier.STATIC)) throw new IllegalStateException("jassimp native must be static: " + key);
            method.getModifiers().remove(ElementModifier.NATIVE);
            ProgramEmitter pe = ProgramEmitter.create(method, context.getHierarchy());
            ValueType[] params = new ValueType[method.parameterCount()];
            ValueEmitter[] args = new ValueEmitter[params.length];
            for (int i = 0; i < params.length; i++) {
                ValueType p = method.parameterType(i);
                boolean jassimpType = p instanceof ValueType.Object o && o.getClassName().startsWith("jassimp.");
                params[i] = jassimpType ? OBJ : p;
                args[i] = pe.var(i + 1, p);
                if (jassimpType) args[i] = args[i].cast(OBJ);
            }
            ValueType result = method.getResultType();
            boolean jassimpResult = result instanceof ValueType.Object o && o.getClassName().startsWith("jassimp.");
            ValueType[] signature = new ValueType[params.length + 1];
            System.arraycopy(params, 0, signature, 0, params.length);
            signature[params.length] = jassimpResult ? OBJ : result;
            ValueEmitter value = pe.invoke(new MethodReference(MODULE, method.getName(), signature), args);
            if (jassimpResult) value = value.cast(result);
            value.returnValue();
            done++;
        }
        if (done != FORWARDED.size()) throw new IllegalStateException("jassimp.Jassimp: forwarded " + done + " of " + FORWARDED.size());
    }

    // ---------------------------------------------------------------- JassimpObjects rewrite

    private interface Body { void emit(Emit e); }

    /** Small helper over ProgramEmitter that resolves fields/methods against the jar. */
    private static final class Emit {
        final ProgramEmitter pe;
        final MethodHolder method;
        final ClassHolderTransformerContext context;
        Emit(ProgramEmitter pe, MethodHolder method, ClassHolderTransformerContext context) {
            this.pe = pe; this.method = method; this.context = context;
        }
        ValueEmitter arg(int i) { return pe.var(i + 1, method.parameterType(i)); }
        ValueEmitter obj(int i, String jassimpClass) { return arg(i).cast(ValueType.object("jassimp." + jassimpClass)); }
        ClassReader cls(String name) {
            ClassReader c = context.getHierarchy().getClassSource().get("jassimp." + name);
            if (c == null) throw new IllegalStateException("jassimp class missing from the jar: " + name);
            return c;
        }
        ValueType fieldType(String owner, String field) {
            FieldReader f = cls(owner).getField(field);
            if (f == null) throw new IllegalStateException("jassimp field missing: " + owner + "." + field);
            return f.getType();
        }
        ValueEmitter get(ValueEmitter target, String owner, String field) {
            return target.getField(field, fieldType(owner, field));
        }
        void put(ValueEmitter target, String owner, String field, ValueEmitter value) {
            ValueType type = fieldType(owner, field);
            if (!value.getType().equals(type)) value = value.cast(type);
            target.setField(field, value);
        }
        MethodReference ref(String owner, String name, ValueType... signature) {
            MethodReference ref = new MethodReference("jassimp." + owner, name, signature);
            if (cls(owner).getMethod(ref.getDescriptor()) == null) throw new IllegalStateException("jassimp method missing: " + ref);
            return ref;
        }
        ValueEmitter construct(String owner, ValueType[] params, ValueEmitter... args) {
            ValueType[] signature = java.util.Arrays.copyOf(params, params.length + 1);
            signature[params.length] = ValueType.VOID;
            ref(owner, "<init>", signature);
            return pe.construct("jassimp." + owner, args);
        }
        void listAdd(ValueEmitter target, String owner, String field, ValueEmitter value) {
            get(target, owner, field).invokeVirtual(new MethodReference("java.util.List", "add", OBJ, ValueType.BOOLEAN), value.cast(OBJ));
        }
        ValueEmitter wrapMatrix(ValueEmitter floats) { return pe.invoke(ref("Jassimp", "wrapMatrix", FLOATS, OBJ), floats); }
        void ret(ValueEmitter value) {
            ValueType type = method.getResultType();
            if (!value.getType().equals(type)) value = value.cast(type);
            value.returnValue();
        }
        void exit() { pe.exit(); }
    }

    private static final Map<String, Body> BODIES = new HashMap<>();
    private static void body(String descriptor, Body body) { BODIES.put(descriptor, body); }
    static {
        ValueType[] none = new ValueType[0];
        body("newScene()Ljava/lang/Object;", e -> e.ret(e.construct("AiScene", none)));
        body("sceneAddMesh(Ljava/lang/Object;Ljava/lang/Object;)V", e -> { e.listAdd(e.obj(0, "AiScene"), "AiScene", "m_meshes", e.arg(1)); e.exit(); });
        body("sceneAddMaterial(Ljava/lang/Object;Ljava/lang/Object;)V", e -> { e.listAdd(e.obj(0, "AiScene"), "AiScene", "m_materials", e.arg(1)); e.exit(); });
        body("sceneAddAnimation(Ljava/lang/Object;Ljava/lang/Object;)V", e -> { e.listAdd(e.obj(0, "AiScene"), "AiScene", "m_animations", e.arg(1)); e.exit(); });
        body("sceneSetRoot(Ljava/lang/Object;Ljava/lang/Object;)V", e -> { e.put(e.obj(0, "AiScene"), "AiScene", "m_sceneRoot", e.arg(1)); e.exit(); });

        body("newMesh()Ljava/lang/Object;", e -> e.ret(e.construct("AiMesh", none)));
        body("meshInit(Ljava/lang/Object;Ljava/lang/String;IIIIZI)V", e -> {
            ValueEmitter mesh = e.obj(0, "AiMesh");
            e.put(mesh, "AiMesh", "m_name", e.arg(1));
            e.put(mesh, "AiMesh", "m_materialIndex", e.arg(2));
            mesh.invoke(InvocationType.SPECIAL, e.ref("AiMesh", "setPrimitiveTypes", ValueType.INTEGER, ValueType.VOID), e.arg(3));
            mesh.invoke(InvocationType.SPECIAL, e.ref("AiMesh", "allocateBuffers", ValueType.INTEGER, ValueType.INTEGER,
                ValueType.BOOLEAN, ValueType.INTEGER, ValueType.VOID), e.arg(4), e.arg(5), e.arg(6), e.arg(7));
            e.exit();
        });
        body("meshChannel(Ljava/lang/Object;II)V", e -> {
            e.obj(0, "AiMesh").invoke(InvocationType.SPECIAL, e.ref("AiMesh", "allocateDataChannel",
                ValueType.INTEGER, ValueType.INTEGER, ValueType.VOID), e.arg(1), e.arg(2));
            e.exit();
        });
        for (String[] f : new String[][]{{"meshVertices", "m_vertices"}, {"meshFaces", "m_faces"},
                {"meshFaceOffsets", "m_faceOffsets"}, {"meshNormals", "m_normals"}, {"meshTangents", "m_tangents"},
                {"meshBitangents", "m_bitangents"}})
            body(f[0] + "(Ljava/lang/Object;)Ljava/nio/ByteBuffer;", e -> e.ret(e.get(e.obj(0, "AiMesh"), "AiMesh", f[1])));
        body("meshColors(Ljava/lang/Object;I)Ljava/nio/ByteBuffer;", e -> e.ret(e.get(e.obj(0, "AiMesh"), "AiMesh", "m_colorsets").getElement(e.arg(1))));
        body("meshTexCoords(Ljava/lang/Object;I)Ljava/nio/ByteBuffer;", e -> e.ret(e.get(e.obj(0, "AiMesh"), "AiMesh", "m_texcoords").getElement(e.arg(1))));
        body("meshAddBone(Ljava/lang/Object;Ljava/lang/Object;)V", e -> { e.listAdd(e.obj(0, "AiMesh"), "AiMesh", "m_bones", e.arg(1)); e.exit(); });

        body("newBone(Ljava/lang/String;[F)Ljava/lang/Object;", e -> {
            ValueEmitter bone = e.construct("AiBone", none);
            e.put(bone, "AiBone", "m_name", e.arg(0));
            e.put(bone, "AiBone", "m_offsetMatrix", e.wrapMatrix(e.arg(1)));
            e.ret(bone);
        });
        body("boneAddWeight(Ljava/lang/Object;IF)V", e -> {
            ValueEmitter weight = e.construct("AiBoneWeight", none);
            e.put(weight, "AiBoneWeight", "m_vertexId", e.arg(1));
            e.put(weight, "AiBoneWeight", "m_weight", e.arg(2));
            e.listAdd(e.obj(0, "AiBone"), "AiBone", "m_boneWeights", weight);
            e.exit();
        });

        body("newMaterial()Ljava/lang/Object;", e -> e.ret(e.construct("AiMaterial", none)));
        body("materialAddProperty(Ljava/lang/Object;Ljava/lang/Object;)V", e -> { e.listAdd(e.obj(0, "AiMaterial"), "AiMaterial", "m_properties", e.arg(1)); e.exit(); });
        body("newPropertyObject(Ljava/lang/String;IIILjava/lang/Object;)Ljava/lang/Object;", e -> e.ret(e.construct("AiMaterial$Property",
            new ValueType[]{STR, ValueType.INTEGER, ValueType.INTEGER, ValueType.INTEGER, OBJ}, e.arg(0), e.arg(1), e.arg(2), e.arg(3), e.arg(4))));
        body("newPropertyBuffer(Ljava/lang/String;IIII)Ljava/lang/Object;", e -> e.ret(e.construct("AiMaterial$Property",
            new ValueType[]{STR, ValueType.INTEGER, ValueType.INTEGER, ValueType.INTEGER, ValueType.INTEGER}, e.arg(0), e.arg(1), e.arg(2), e.arg(3), e.arg(4))));
        body("propertyBuffer(Ljava/lang/Object;)Ljava/nio/ByteBuffer;", e -> e.ret(e.get(e.obj(0, "AiMaterial$Property"), "AiMaterial$Property", "m_data")));
        body("wrapColor4(FFFF)Ljava/lang/Object;", e -> e.ret(e.pe.invoke(e.ref("Jassimp", "wrapColor4",
            ValueType.FLOAT, ValueType.FLOAT, ValueType.FLOAT, ValueType.FLOAT, OBJ), e.arg(0), e.arg(1), e.arg(2), e.arg(3))));

        body("newAnimation(Ljava/lang/String;DD)Ljava/lang/Object;", e -> e.ret(e.construct("AiAnimation",
            new ValueType[]{STR, ValueType.DOUBLE, ValueType.DOUBLE}, e.arg(0), e.arg(1), e.arg(2))));
        body("animationAddChannel(Ljava/lang/Object;Ljava/lang/Object;)V", e -> { e.listAdd(e.obj(0, "AiAnimation"), "AiAnimation", "m_nodeAnims", e.arg(1)); e.exit(); });
        body("newNodeAnim(Ljava/lang/String;IIIII)Ljava/lang/Object;", e -> e.ret(e.construct("AiNodeAnim",
            new ValueType[]{STR, ValueType.INTEGER, ValueType.INTEGER, ValueType.INTEGER, ValueType.INTEGER, ValueType.INTEGER},
            e.arg(0), e.arg(1), e.arg(2), e.arg(3), e.arg(4), e.arg(5))));
        for (String[] f : new String[][]{{"nodeAnimPosKeys", "m_posKeys"}, {"nodeAnimRotKeys", "m_rotKeys"}, {"nodeAnimScaleKeys", "m_scaleKeys"}})
            body(f[0] + "(Ljava/lang/Object;)Ljava/nio/ByteBuffer;", e -> e.ret(e.get(e.obj(0, "AiNodeAnim"), "AiNodeAnim", f[1])));

        body("wrapMatrix([F)Ljava/lang/Object;", e -> e.ret(e.wrapMatrix(e.arg(0))));
        body("wrapNode(Ljava/lang/Object;[F[ILjava/lang/String;)Ljava/lang/Object;", e -> e.ret(e.pe.invoke(
            e.ref("Jassimp", "wrapSceneNode", OBJ, OBJ, INTS, STR, OBJ), e.arg(0), e.wrapMatrix(e.arg(1)), e.arg(2), e.arg(3))));
    }

    static Set<String> rewrittenDescriptors() { return BODIES.keySet(); }

    private void rewriteObjects(ClassHolder cls, ClassHolderTransformerContext context) {
        int done = 0;
        for (MethodHolder method : cls.getMethods()) {
            if (!method.hasModifier(ElementModifier.STATIC) || method.getLevel() != org.teavm.model.AccessLevel.PUBLIC) continue;
            String key = method.getName() + MethodDescriptor.get(method).signatureToString();
            Body body = BODIES.get(key);
            if (body == null) throw new IllegalStateException("JassimpObjects." + key + " has no TeaVM rewrite");
            body.emit(new Emit(ProgramEmitter.create(method, context.getHierarchy()), method, context));
            done++;
        }
        if (done != BODIES.size()) throw new IllegalStateException("JassimpObjects: rewrote " + done + " of " + BODIES.size());
    }
}
