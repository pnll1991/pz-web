package port.compiler;
import org.teavm.model.*;
import org.teavm.model.emit.*;

/**
 * Rewrites {@code org.lwjglx.BufferUtils} so its buffers live in the browser arena.
 * The desktop class allocates {@code ByteBuffer.allocateDirect(...).asFloatBuffer()}, and TeaVM
 * returns a heap view for that: {@code NativeArena.address} then raises "Arena address requires a
 * registered/direct ByteBuffer view" the moment the game hands such a buffer to a native GL call
 * (verified: ShaderProgram.&lt;clinit&gt; -> createFloatBuffer(16) -> setTransformMatrix ->
 * GL20C.glUniformMatrix4fv -> MemoryUtil.memAddress). Every method with an equivalent in
 * {@code port.runtime.memory.BufferUtilsBridge} is delegated by exact descriptor; the remaining
 * private natives keep an honest raise naming the operation.
 */
public final class BufferUtilsCompatibility implements ClassHolderTransformer {
    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (!cls.getName().equals("org.lwjglx.BufferUtils")) return;
        var own = context.getHierarchy().getClassSource().get("port.runtime.memory.BufferUtilsBridge");
        if (own == null) throw new IllegalStateException("BufferUtils bridge missing");
        for (var method : cls.getMethods()) {
            var emitter = ProgramEmitter.create(method, context.getHierarchy());
            if (method.getName().equals("<init>") || method.getName().equals("<clinit>")) {
                emitter.exit();
                continue;
            }
            if (own.getMethod(method.getDescriptor()) != null && method.hasModifier(ElementModifier.STATIC)) {
                ValueEmitter[] args = new ValueEmitter[method.parameterCount()];
                for (int i = 0; i < args.length; i++) args[i] = emitter.var(i + 1, method.parameterType(i));
                var result = emitter.invoke(new MethodReference(own.getName(), method.getDescriptor()), args);
                if (method.getResultType() == ValueType.VOID) emitter.exit(); else result.returnValue();
            } else {
                emitter.construct(UnsupportedOperationException.class,
                    emitter.constant("Browser BufferUtils operation unavailable: " + cls.getName() + "." + method.getDescriptor()))
                    .raise();
            }
        }
    }
}
