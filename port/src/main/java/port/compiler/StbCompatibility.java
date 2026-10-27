package port.compiler;

import org.teavm.model.ClassHolder;
import org.teavm.model.MethodHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;

/**
 * Routes the two STB entry points the boot path really uses (PNG load and the uint8 sRGB
 * resize) to {@code port.shim.BrowserStb}, and makes {@code LibSTB.<clinit>} a no-op: the host
 * {@code lwjgl_stb} dynamic library cannot exist in a tab, and the game's {@code loadIcons}
 * catches the resulting class-initialization failure, which left the icon buffer empty.
 *
 * Every other STB native keeps the fail-fast guard from {@code FailFastNatives}; nothing here
 * simulates success for a function whose implementation is missing.
 */
public final class StbCompatibility implements ClassHolderTransformer {
    private static final String BRIDGE = "port.shim.BrowserStb";
    private static final String IMAGE = "org.lwjgl.stb.STBImage";
    private static final String RESIZE = "org.lwjgl.stb.STBImageResize";
    private static final String BYTE_BUFFER = "java.nio.ByteBuffer";
    private static final String CHAR_SEQUENCE = "java.lang.CharSequence";
    private static final ValueType INT_ARRAY = ValueType.arrayOf(ValueType.INTEGER);
    private static final ValueType BUFFER = ValueType.object(BYTE_BUFFER);
    private static final ValueType SEQUENCE = ValueType.object(CHAR_SEQUENCE);
    private static final ValueType STRING = ValueType.object("java.lang.String");

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        switch (cls.getName()) {
            case "org.lwjgl.stb.LibSTB" -> noOpClinit(cls, context);
            case IMAGE -> rewriteImage(cls, context);
            case RESIZE -> rewriteResize(cls, context);
            default -> { }
        }
    }

    /** No host library to load and no malloc hooks to install in wasm. */
    private void noOpClinit(ClassHolder cls, ClassHolderTransformerContext context) {
        var clinit = cls.getMethod(new MethodDescriptor("<clinit>", ValueType.VOID));
        if (clinit == null) throw new IllegalStateException("Inventoried LibSTB.<clinit> is missing");
        var e = ProgramEmitter.create(clinit, context.getHierarchy());
        e.exit();
    }

    private void rewriteImage(ClassHolder cls, ClassHolderTransformerContext context) {
        // The game's NativeImage.read path: stbi_load(CharSequence,int[],int[],int[],int).
        route(cls, context, new MethodDescriptor("stbi_load", SEQUENCE, INT_ARRAY, INT_ARRAY,
            INT_ARRAY, ValueType.INTEGER, BUFFER), "stbiLoad",
            SEQUENCE, INT_ARRAY, INT_ARRAY, INT_ARRAY, ValueType.INTEGER);
        // NativeImage.read sets the flip flag through this wrapper before loading.
        route(cls, context, new MethodDescriptor("stbi_set_flip_vertically_on_load",
            ValueType.BOOLEAN, ValueType.VOID), "setFlip", ValueType.INTEGER);
        route(cls, context, new MethodDescriptor("nstbi_set_flip_vertically_on_load",
            ValueType.INTEGER, ValueType.VOID), "setFlip", ValueType.INTEGER);
        // Rewrite the String wrapper (nstbi_failure_reason itself stays fail-fast) so the reason
        // reported by NativeImage.read is this decoder's own reason.
        route(cls, context, new MethodDescriptor("stbi_failure_reason", STRING), "failureReason");
        route(cls, context, new MethodDescriptor("stbi_image_free", BUFFER, ValueType.VOID),
            "stbiImageFree", BUFFER);
    }

    private void rewriteResize(ClassHolder cls, ClassHolderTransformerContext context) {
        // NativeImage.resize: stbir_resize_uint8_srgb(src,w,h,stride,dst,dw,dh,dstride,layout).
        var descriptor = new MethodDescriptor("stbir_resize_uint8_srgb", BUFFER, ValueType.INTEGER,
            ValueType.INTEGER, ValueType.INTEGER, BUFFER, ValueType.INTEGER, ValueType.INTEGER,
            ValueType.INTEGER, ValueType.INTEGER, BUFFER);
        var method = cls.getMethod(descriptor);
        if (method == null) throw new IllegalStateException("Inventoried stbir_resize_uint8_srgb is missing");
        method.getModifiers().remove(ElementModifier.NATIVE);
        var e = ProgramEmitter.create(method, context.getHierarchy());
        var arguments = new ValueEmitter[method.parameterCount()];
        for (int i = 0; i < arguments.length; i++) arguments[i] = e.var(i + 1, method.parameterType(i));
        // Varargs order: class, name, parameter types..., result type.
        e.invoke(new MethodReference(BRIDGE, "resize", BUFFER, ValueType.INTEGER, ValueType.INTEGER,
            ValueType.INTEGER, BUFFER, ValueType.INTEGER, ValueType.INTEGER, ValueType.INTEGER,
            ValueType.INTEGER, BUFFER), arguments).returnValue();
    }

    /**
     * boolean -> int for the flip flag, exactly like LWJGL's own wrapper. Everything else is
     * passed through; an argument that needs a different conversion is rejected here rather than
     * producing an EmitException later in the dependency analyzer.
     */
    private ValueEmitter argument(MethodHolder method, ProgramEmitter e, int index, ValueType wanted) {
        var value = e.var(index + 1, method.parameterType(index));
        ValueType given = method.parameterType(index);
        if (given.equals(wanted)) return value;
        if (given == ValueType.BOOLEAN && wanted == ValueType.INTEGER) return value.cast(ValueType.INTEGER);
        throw new IllegalStateException("No bridge conversion for " + given + " to " + wanted);
    }

    /** Replace one static wrapper's body with a call to the Java bridge (never a @JSBody). */
    private void route(ClassHolder cls, ClassHolderTransformerContext context, MethodDescriptor descriptor,
        String bridgeName, ValueType... parameterTypes) {
        var method = cls.getMethod(descriptor);
        if (method == null) throw new IllegalStateException("Inventoried " + cls.getName() + "#" + descriptor
            + " is missing");
        method.getModifiers().remove(ElementModifier.NATIVE);
        var e = ProgramEmitter.create(method, context.getHierarchy());
        if (method.parameterCount() != parameterTypes.length) {
            throw new IllegalStateException("Bridge arity mismatch for " + cls.getName() + "#" + descriptor);
        }
        var arguments = new ValueEmitter[method.parameterCount()];
        for (int i = 0; i < arguments.length; i++) arguments[i] = argument(method, e, i, parameterTypes[i]);
        // MethodReference varargs: class, name, parameter types..., result type.
        var signature = new ValueType[parameterTypes.length + 1];
        System.arraycopy(parameterTypes, 0, signature, 0, parameterTypes.length);
        signature[parameterTypes.length] = method.getResultType();
        var target = new MethodReference(BRIDGE, bridgeName, signature);
        var result = e.invoke(target, arguments);
        if (method.getResultType() == ValueType.VOID) e.exit(); else result.returnValue();
    }
}
