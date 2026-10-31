package port.compiler;

import java.util.Map;
import java.util.Set;
import org.teavm.model.AccessLevel;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldHolder;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;

/**
 * Round 80: the instance natives of zombie.vehicles.Clipper and zombie.vehicles.ClipperOffset
 * (library PZClipper64) answered by our own Java engines, port.natives.ClipperEngine and
 * port.natives.ClipperOffsetEngine (Clipper2-java + earcut4j).
 *
 * The native library keeps its C++ object behind the game's `address` field. Here each game object
 * gets one extra field, {@value #FIELD}, holding its engine; newInstance() creates it and every other
 * forwarded native calls the engine method with the same name and descriptor. Natives not listed in
 * {@link #FORWARDED} keep the FailFastNatives guard with their JNI name.
 */
public final class ClipperNativeCompatibility implements ClassHolderTransformer {
    static final String FIELD = "port$engine";
    private record Spec(String engine, Set<String> forwarded) { }
    private static final Map<String, Spec> FORWARDED = Map.of(
        "zombie.vehicles.Clipper", new Spec("port.natives.ClipperEngine", Set.of(
            "newInstance()V", "clear()V", "addPath(ILjava/nio/ByteBuffer;ZZ)V", "addLine(FFFF)V",
            "addAABB(FFFF)V", "addPolygon(FFFFFFFF)V", "clipAABB(FFFF)V", "generatePolygons(IDI)I",
            "getPolygon(ILjava/nio/ByteBuffer;)I", "generateTriangulatePolygons(II)I",
            "triangulate(ILjava/nio/ByteBuffer;)I", "triangulate2(ILjava/nio/ByteBuffer;)I")),
        "zombie.vehicles.ClipperOffset", new Spec("port.natives.ClipperOffsetEngine", Set.of(
            "newInstance()J", "clear()V", "addPath(ILjava/nio/ByteBuffer;II)V", "execute(D)V",
            "getPolygonCount()I", "getPolygon(ILjava/nio/ByteBuffer;)I")));

    private final Map<String, Set<String>> inventoried = new java.util.HashMap<>();

    public ClipperNativeCompatibility() {
        try {
            for (String line : java.nio.file.Files.readAllLines(
                    java.nio.file.Path.of(System.getProperty("pz.nativeIndex")))) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\t", -1);
                if (fields.length != 4) throw new IllegalArgumentException("Invalid native index line");
                if (!FORWARDED.containsKey(fields[0])) continue;
                inventoried.computeIfAbsent(fields[0], k -> new java.util.HashSet<>()).add(fields[1] + fields[2]);
            }
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Missing native index: run scripts/build.sh", error);
        }
        for (var entry : FORWARDED.entrySet()) {
            Set<String> natives = inventoried.get(entry.getKey());
            if (natives == null || !natives.containsAll(entry.getValue().forwarded()))
                throw new IllegalStateException("Inventory does not list the Clipper natives we forward: " + entry.getKey());
        }
    }

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        Spec spec = FORWARDED.get(cls.getName());
        if (spec == null) return;
        ValueType engineType = ValueType.object(spec.engine());
        FieldHolder field = new FieldHolder(FIELD);
        field.setType(engineType);
        field.setLevel(AccessLevel.PRIVATE);
        cls.addField(field);
        for (MethodHolder method : cls.getMethods()) {
            String key = method.getName() + MethodDescriptor.get(method).signatureToString();
            if (!spec.forwarded().contains(key) || !inventoried.get(cls.getName()).contains(key)) continue;
            if (method.hasModifier(ElementModifier.STATIC))
                throw new IllegalStateException("Clipper native expected to be an instance method: " + key);
            method.getModifiers().remove(ElementModifier.NATIVE);
            var emitter = ProgramEmitter.create(method, context.getHierarchy());
            ValueEmitter self = emitter.var(0, ValueType.object(cls.getName()));
            if (method.getName().equals("newInstance")) {
                ValueEmitter engine = emitter.construct(spec.engine());
                self.setField(FIELD, engine);
                if (method.getResultType().equals(ValueType.VOID)) emitter.exit();
                else engine.invokeVirtual("handle", ValueType.LONG).returnValue();
                continue;
            }
            ValueEmitter[] arguments = new ValueEmitter[method.parameterCount()];
            for (int i = 0; i < arguments.length; i++) arguments[i] = emitter.var(i + 1, method.parameterType(i));
            ValueEmitter engine = self.getField(FIELD, engineType);
            var result = engine.invokeVirtual(new MethodReference(spec.engine(), MethodDescriptor.get(method)), arguments);
            if (method.getResultType().equals(ValueType.VOID)) emitter.exit(); else result.returnValue();
        }
    }
}
