package port.compiler;

import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;

/** Links our wrapper without loading or initializing game classes in the JVM. */
public final class GameEntryTransformer implements ClassHolderTransformer {
    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (!cls.getName().equals("port.GameBoot")) return;
        String main = System.getProperty("pz.gameMain");
        if (main == null || main.isBlank()) throw new IllegalStateException("Missing inventoried main class");
        ValueType args = ValueType.arrayOf(ValueType.object("java.lang.String"));
        var method = cls.getMethod(new MethodDescriptor("launch", args, ValueType.VOID));
        var emitter = ProgramEmitter.create(method, context.getHierarchy());
        emitter.invoke(new MethodReference(main, new MethodDescriptor("main", args, ValueType.VOID)), emitter.var(1, args));
        emitter.exit();
    }
}
