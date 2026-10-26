package port.compiler;

import java.util.ArrayList;
import org.teavm.model.AccessLevel;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;

/** Adds narrowly implemented JDK APIs; never replaces missing APIs with success. */
public final class JdkCompatibility implements ClassHolderTransformer {
    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        switch (cls.getName()) {
            case "java.io.File" -> forward(cls, context, "toPath()Ljava/nio/file/Path;", false);
            case "java.io.Reader" -> forward(cls, context, "transferTo(Ljava/io/Writer;)J", false);
            case "java.nio.file.Path" -> forward(cls, context, "resolve(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;", false);
            case "java.lang.Thread" -> forward(cls, context, "onSpinWait()V", true);
            case "java.io.FileInputStream" -> {
                // The pinned classlib returns a signed byte from read(). Keep
                // EOF distinct from 0xff, as required by InputStream's contract.
                var method = cls.getMethod(new MethodDescriptor("read", ValueType.INTEGER));
                var emitter = ProgramEmitter.create(method, context.getHierarchy());
                emitter.invoke(new MethodReference("port.runtime.JdkSupport", "read", ValueType.object("java.io.InputStream"), ValueType.INTEGER),
                        emitter.var(0, ValueType.object("java.io.InputStream"))).returnValue();
            }
            default -> { }
        }
    }

    private static void forward(ClassHolder cls, ClassHolderTransformerContext context, String signature, boolean isStatic) {
        MethodDescriptor descriptor = MethodDescriptor.parse(signature);
        if (cls.getMethod(descriptor) != null) return;
        var method = new MethodHolder(descriptor);
        method.setLevel(AccessLevel.PUBLIC);
        if (isStatic) method.getModifiers().add(ElementModifier.STATIC);
        cls.addMethod(method);
        var emitter = ProgramEmitter.create(method, context.getHierarchy());
        var values = new ArrayList<ValueEmitter>();
        var types = new ArrayList<ValueType>();
        if (!isStatic) {
            ValueType owner = ValueType.object(cls.getName());
            types.add(owner); values.add(emitter.var(0, owner));
        }
        for (int i = 0; i < method.parameterCount(); i++) {
            types.add(method.parameterType(i)); values.add(emitter.var(i + 1, method.parameterType(i)));
        }
        types.add(method.getResultType());
        var result = emitter.invoke(new MethodReference("port.runtime.JdkSupport",
                new MethodDescriptor(descriptor.getName(), types.toArray(ValueType[]::new))), values.toArray(ValueEmitter[]::new));
        if (method.getResultType().equals(ValueType.VOID)) emitter.exit(); else result.returnValue();
    }
}
