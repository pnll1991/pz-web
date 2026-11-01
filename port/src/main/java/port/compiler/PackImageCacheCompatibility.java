package port.compiler;

import java.util.HashMap;
import java.util.Map;
import org.teavm.model.AccessLevel;
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

/**
 * Ronda 81 (frente 2): caché de páginas de texturas ({@code port.runtime.PackImageCache}).
 *
 * 1. {@code zombie.asset.FileTask_LoadPackImage}: el {@code call()} original se mueve intacto a un método
 *    nuevo {@code port$uncachedCall()} y {@code call()} pasa a ser {@code return PackImageCache.call(this)}.
 *    El build falla si call() no existe o ya no tiene la forma esperada (otra versión del jar).
 * 2. {@code port.runtime.PackImageObjects}: cada cuerpo (reflexión JVM) se reemplaza por IR directo sobre las
 *    clases del jar (getfield/putfield/new/invoke). Campos y métodos se resuelven contra el jar recorriendo
 *    {@code getParent()} a mano (ronda 80: ClassHierarchy.resolve devolvía null para campos que existen);
 *    falla el build si falta uno o si PackImageObjects tiene un método público sin reescritura.
 */
public final class PackImageCacheCompatibility implements ClassHolderTransformer {
    static final String TASK = "zombie.asset.FileTask_LoadPackImage";
    static final String OBJECTS = "port.runtime.PackImageObjects";
    static final String UNCACHED = "port$uncachedCall";
    private static final ValueType OBJ = ValueType.object("java.lang.Object");
    private static final ValueType STR = ValueType.object("java.lang.String");
    private static final ValueType INT = ValueType.INTEGER;
    private static final ValueType BOOL = ValueType.BOOLEAN;
    private static final String IMAGE = "zombie.core.textures.ImageData";
    private static final String MIP = "zombie.core.textures.MipMapLevel";
    private static final String WRAPPED = "zombie.core.utils.WrappedBuffer";
    private static final String GRID = "zombie.core.utils.BooleanGrid";
    private static final String FS = "zombie.fileSystem.FileSystem";
    private static final String DEVICES = "zombie.fileSystem.DeviceList";
    private static final MethodReference CACHE_CALL = new MethodReference("port.runtime.PackImageCache", "call", OBJ, OBJ);
    private static final MethodDescriptor CALL = new MethodDescriptor("call", OBJ);

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (cls.getName().equals(TASK)) wrapCall(cls, context);
        else if (cls.getName().equals(OBJECTS)) rewriteObjects(cls, context);
    }

    private static void wrapCall(ClassHolder cls, ClassHolderTransformerContext context) {
        MethodHolder call = cls.getMethod(CALL);
        if (call == null || call.getProgram() == null || call.hasModifier(ElementModifier.STATIC))
            throw new IllegalStateException("PackImageCacheCompatibility: falta " + TASK + ".call()");
        if (cls.getMethod(new MethodDescriptor(UNCACHED, OBJ)) != null) throw new IllegalStateException("PackImageCacheCompatibility: " + UNCACHED + " ya existe");
        for (String field : new String[]{"packName", "imageName", "mask", "flags"})
            if (cls.getField(field) == null) throw new IllegalStateException("PackImageCacheCompatibility: falta " + TASK + "." + field);
        MethodHolder uncached = new MethodHolder(UNCACHED, OBJ);
        uncached.setLevel(AccessLevel.PUBLIC);
        uncached.setThrownTypes(call.getThrownTypes());
        uncached.setProgram(call.getProgram());
        cls.addMethod(uncached);
        ProgramEmitter pe = ProgramEmitter.create(call, context.getHierarchy());
        pe.invoke(CACHE_CALL, pe.var(0, ValueType.object(TASK)).cast(OBJ)).returnValue();
    }

    // ------------------------------------------------------------ PackImageObjects

    private interface Body { void emit(Emit e); }

    private static final class Emit {
        final ProgramEmitter pe; final MethodHolder method; final ClassHolderTransformerContext context;
        Emit(ProgramEmitter pe, MethodHolder method, ClassHolderTransformerContext context) { this.pe = pe; this.method = method; this.context = context; }
        ValueEmitter arg(int i) { return pe.var(i + 1, method.parameterType(i)); }
        ValueEmitter as(int i, String cls) { return arg(i).cast(ValueType.object(cls)); }
        ClassReader cls(String name) {
            ClassReader c = context.getHierarchy().getClassSource().get(name);
            if (c == null) throw new IllegalStateException("PackImageCacheCompatibility: clase ausente del jar: " + name);
            return c;
        }
        /** Dueño real y tipo de un campo, subiendo por getParent(). */
        FieldReader field(String owner, String name) {
            for (String c = owner; c != null; c = cls(c).getParent()) {
                FieldReader f = cls(c).getField(name);
                if (f != null) return f;
                if (c.equals("java.lang.Object")) break;
            }
            throw new IllegalStateException("PackImageCacheCompatibility: campo ausente: " + owner + "." + name);
        }
        ValueEmitter get(ValueEmitter target, String owner, String name) {
            FieldReader f = field(owner, name);
            return target.cast(ValueType.object(f.getOwnerName())).getField(name, f.getType());
        }
        void put(ValueEmitter target, String owner, String name, ValueEmitter value) {
            FieldReader f = field(owner, name);
            if (!value.getType().equals(f.getType())) value = value.cast(f.getType());
            target.cast(ValueType.object(f.getOwnerName())).setField(name, value);
        }
        MethodReference ref(String owner, String name, ValueType... signature) {
            MethodReference ref = new MethodReference(owner, name, signature);
            for (String c = owner; c != null; c = cls(c).getParent()) {
                if (cls(c).getMethod(ref.getDescriptor()) != null) return ref;
                if (c.equals("java.lang.Object")) break;
            }
            throw new IllegalStateException("PackImageCacheCompatibility: método ausente: " + ref);
        }
        ValueEmitter construct(String owner, ValueType[] params, ValueEmitter... args) {
            ValueType[] signature = java.util.Arrays.copyOf(params, params.length + 1);
            signature[params.length] = ValueType.VOID;
            if (cls(owner).getMethod(new MethodDescriptor("<init>", signature)) == null)
                throw new IllegalStateException("PackImageCacheCompatibility: constructor ausente: " + owner + java.util.Arrays.toString(params));
            for (int i = 0; i < args.length; i++) if (!args[i].getType().equals(params[i])) args[i] = args[i].cast(params[i]);
            return pe.construct(owner, args);
        }
        void ret(ValueEmitter value) {
            ValueType type = method.getResultType();
            if (!value.getType().equals(type)) value = value.cast(type);
            value.returnValue();
        }
        void exit() { pe.exit(); }
    }

    private static final Map<String, Body> BODIES = new HashMap<>();
    private static void body(String descriptor, Body body) {
        if (BODIES.put(descriptor, body) != null) throw new IllegalStateException("duplicado: " + descriptor);
    }
    private static void getter(String name, String owner, String field, String desc) {
        body(name + "(Ljava/lang/Object;)" + desc, e -> e.ret(e.get(e.as(0, owner), owner, field)));
    }
    private static void setter(String name, String owner, String field, String desc) {
        body(name + "(Ljava/lang/Object;" + desc + ")V", e -> { e.put(e.as(0, owner), owner, field, e.arg(1)); e.exit(); });
    }
    static {
        ValueType fs = ValueType.object(FS), devices = ValueType.object(DEVICES);
        body("waitFileTask()V", e -> {
            String manager = "zombie.core.textures.TextureIDAssetManager";
            FieldReader f = e.field(manager, "instance");
            e.pe.getField(f.getOwnerName(), "instance", f.getType())
                .invokeVirtual(e.ref(manager, "waitFileTask", ValueType.VOID));
            e.exit();
        });
        body("openPage(Ljava/lang/Object;)Ljava/io/InputStream;", e -> {
            ValueEmitter task = e.as(0, TASK);
            ValueEmitter system = e.get(task, TASK, "fileSystem").cast(fs);
            ValueEmitter device = system.invokeVirtual(e.ref(FS, "getTexturePackDevice", STR, devices), e.get(task, TASK, "packName"));
            e.ret(system.invokeVirtual(e.ref(FS, "openStream", devices, STR, ValueType.object("java.io.InputStream")), device, e.get(task, TASK, "imageName")));
        });
        getter("taskMask", TASK, "mask", "Z");
        getter("taskFlags", TASK, "flags", "I");
        getter("taskPack", TASK, "packName", "Ljava/lang/String;");
        getter("taskImage", TASK, "imageName", "Ljava/lang/String;");
        // Llama al call() original que wrapCall dejó como port$uncachedCall (mismo cuerpo, sin cambios).
        body("uncached(Ljava/lang/Object;)Ljava/lang/Object;", e -> e.ret(e.as(0, TASK).invokeVirtual(new MethodReference(TASK, UNCACHED, OBJ))));

        for (String[] f : new String[][]{{"Width", "width"}, {"Height", "height"}, {"WidthHw", "widthHw"}, {"HeightHw", "heightHw"},
                {"MipMapCount", "mipMapCount"}, {"Id", "id"}}) {
            getter("image" + f[0], IMAGE, f[1], "I"); setter("setImage" + f[0], IMAGE, f[1], "I");
        }
        for (String[] f : new String[][]{{"Solid", "solid"}, {"AlphaPaddingDone", "alphaPaddingDone"},
                {"PreMultipliedAlphaDone", "preMultipliedAlphaDone"}, {"PreserveTransparentColor", "preserveTransparentColor"}}) {
            getter("image" + f[0], IMAGE, f[1], "Z"); setter("setImage" + f[0], IMAGE, f[1], "Z");
        }
        getter("imageData", IMAGE, "data", "Ljava/lang/Object;");
        setter("setImageData", IMAGE, "data", "Ljava/lang/Object;");
        getter("imageMipMaps", IMAGE, "mipMaps", "[Ljava/lang/Object;");
        setter("setImageMipMaps", IMAGE, "mipMaps", "[Ljava/lang/Object;");
        getter("imageMask", IMAGE, "mask", "Ljava/lang/Object;");
        setter("setImageMask", IMAGE, "mask", "Ljava/lang/Object;");
        body("imageFrameCount(Ljava/lang/Object;)I", e -> e.ret(e.get(e.as(0, IMAGE), IMAGE, "frames")
            .cast(ValueType.object("java.util.ArrayList")).invokeVirtual(new MethodReference("java.util.ArrayList", "size", INT))));
        body("newImage(IILjava/lang/Object;)Ljava/lang/Object;", e -> e.ret(e.construct(IMAGE,
            new ValueType[]{INT, INT, ValueType.object(WRAPPED)}, e.arg(0), e.arg(1), e.arg(2))));

        body("newMip(II)Ljava/lang/Object;", e -> e.ret(e.construct(MIP, new ValueType[]{INT, INT}, e.arg(0), e.arg(1))));
        body("newMipArray(I)[Ljava/lang/Object;", e -> { e.cls(MIP); e.ret(e.pe.constructArray(ValueType.object(MIP), e.arg(0))); });
        getter("mipWidth", MIP, "width", "I");
        getter("mipHeight", MIP, "height", "I");
        getter("mipWrapped", MIP, "data", "Ljava/lang/Object;");
        getter("wrappedBuffer", WRAPPED, "buf", "Ljava/nio/ByteBuffer;");
        getter("wrappedCapacity", WRAPPED, "capacity", "I");
        getter("wrappedDisposed", WRAPPED, "disposed", "Z");

        body("newGrid(II)Ljava/lang/Object;", e -> e.ret(e.construct(GRID, new ValueType[]{INT, INT}, e.arg(0), e.arg(1))));
        getter("gridWidth", GRID, "width", "I");
        getter("gridHeight", GRID, "height", "I");
        getter("gridBitWidth", GRID, "bitWidth", "I");
        getter("gridValue", GRID, "value", "[I");
    }

    static java.util.Set<String> rewrittenDescriptors() { return BODIES.keySet(); }

    private static void rewriteObjects(ClassHolder cls, ClassHolderTransformerContext context) {
        int done = 0;
        for (MethodHolder method : cls.getMethods()) {
            if (!method.hasModifier(ElementModifier.STATIC) || method.getLevel() != AccessLevel.PUBLIC) continue;
            String key = method.getName() + MethodDescriptor.get(method).signatureToString();
            Body body = BODIES.get(key);
            if (body == null) throw new IllegalStateException("PackImageObjects." + key + " no tiene reescritura TeaVM");
            body.emit(new Emit(ProgramEmitter.create(method, context.getHierarchy()), method, context));
            done++;
        }
        if (done != BODIES.size()) throw new IllegalStateException("PackImageObjects: reescritos " + done + " de " + BODIES.size());
    }
}
