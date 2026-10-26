package port.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;

/** Replace only inventoried native methods, preserving every other member. */
public final class FailFastNatives implements ClassHolderTransformer {
    private final Map<String, String> names = new HashMap<>();
    private final Map<String, String> graphics = new HashMap<>();

    public FailFastNatives() {
        try {
            for (String line : Files.readAllLines(Path.of(System.getProperty("pz.nativeIndex")))) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\t", -1);
                if (fields.length != 4) throw new IllegalArgumentException("Invalid native index line");
                names.put(fields[0] + "#" + fields[1] + fields[2], fields[3]);
            }
            Path mappings = Path.of(System.getProperty("pz.glIndex"));
            for (String line : Files.readAllLines(mappings)) {
                if (line.isBlank()) continue;
                String[] fields = line.split("\t", -1);
                if (fields.length != 4) throw new IllegalArgumentException("Invalid GL mapping");
                graphics.put(fields[0] + "#" + fields[1] + fields[2], fields[3]);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Missing native index: run scripts/build.sh", error);
        }
    }

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        for (var method : cls.getMethods()) {
            String key = cls.getName() + "#" + method.getDescriptor();
            String target = graphics.get(key);
            if (target != null) {
                if (!method.hasModifier(ElementModifier.STATIC)) throw new IllegalStateException("GL mapping must be static: " + key);
                method.getModifiers().remove(ElementModifier.NATIVE);
                var emitter = ProgramEmitter.create(method, context.getHierarchy());
                ValueEmitter[] arguments = new ValueEmitter[method.parameterCount()];
                for (int i = 0; i < arguments.length; i++) arguments[i] = emitter.var(i + 1, method.parameterType(i));
                ValueEmitter result = emitter.invoke(new MethodReference(target, method.getDescriptor()), arguments);
                if (method.getResultType().equals(ValueType.VOID)) emitter.exit(); else result.returnValue();
                continue;
            }
            String name = names.get(key);
            if (name == null || !method.hasModifier(ElementModifier.NATIVE)) continue;
            method.getModifiers().remove(ElementModifier.NATIVE);
            var emitter = ProgramEmitter.create(method, context.getHierarchy());
            emitter.construct(UnsupportedOperationException.class, emitter.constant(name)).raise();
        }
    }
}
