package port.compiler;

import java.util.Map;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;

/**
 * Forward only the Bullet natives whose semantics are known from the game's own calls and
 * which the locally built wasm32 module really implements. Every other native in the class
 * keeps its fail-fast guard with the JNI name from the inventory.
 */
public final class BulletNativeCompatibility implements ClassHolderTransformer {
    private static final String CLASS = "zombie.core.physics.Bullet";
    private static final String BRIDGE = "port.shim.BulletLibrary";
    // Signature of each inventoried native of this class, read from the same index the
    // fail-fast transformer uses, so only real natives of the game class are forwarded.
    private final java.util.Set<String> inventoriedNatives = new java.util.HashSet<>();

    public BulletNativeCompatibility() {
        try {
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of(System.getProperty("pz.nativeIndex")))) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\t", -1);
                if (fields.length != 4) throw new IllegalArgumentException("Invalid native index line");
                if (fields[0].equals(CLASS)) inventoriedNatives.add(fields[1] + fields[2]);
            }
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Missing native index: run scripts/build.sh", error);
        }
    }
    // Key: native name plus descriptor; value: descriptor of the port bridge that answers it.
    // Keying by name too matters: clearPhysicsMeshes and destroyWorld also have ()V.
    private static final Map<String, MethodDescriptor> FORWARDED = Map.of(
        "initPZBullet()V", new MethodDescriptor("initLogging", ValueType.VOID),
        "getPZBulletVersion()Ljava/lang/String;", new MethodDescriptor("version", ValueType.object("java.lang.String")),
        "isWorldInit()Z", new MethodDescriptor("worldInitialised", ValueType.BOOLEAN));

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (!cls.getName().equals(CLASS)) return;
        for (var method : cls.getMethods()) {
            // Runs after FailFastNatives, so the modifier may already be gone; match the
            // inventoried name plus signature read from the same index.
            MethodDescriptor descriptor = MethodDescriptor.get(method);
            String key = method.getName() + descriptor.signatureToString();
            if (!inventoriedNatives.contains(key)) continue;
            MethodDescriptor target = FORWARDED.get(key);
            if (target == null) continue;
            method.getModifiers().remove(ElementModifier.NATIVE);
            var emitter = ProgramEmitter.create(method, context.getHierarchy());
            var result = emitter.invoke(new MethodReference(BRIDGE, target));
            if (method.getResultType().equals(ValueType.VOID)) emitter.exit(); else result.returnValue();
        }
    }
}
