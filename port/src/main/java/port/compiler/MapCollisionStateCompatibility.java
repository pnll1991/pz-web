package port.compiler;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

/**
 * Round 64: forward only zombie.MapCollisionData.n_setGameState(String,X) to port.natives.MapCollision64.
 *
 * Evidence: the first exception after UI_Loading_OnGameBoot in
 * build/.../DebugLog 2026-10-30_15-02 was UnsupportedOperationException
 * Java_zombie_MapCollisionData_n_1setGameState__Ljava_lang_String_2Z, raised by FailFastNatives
 * from MapCollisionData.updateGameState() (called from GameWindow#frameStep, see
 * /tmp/r63/gw.txt line 503). Our module records the key/value verbatim, which is what the desktop
 * native does with its settings block; the collision/pathing natives (n_init, n_initMetaGrid,
 * n_initMetaCell, n_initMetaChunk, n_chunkUpdateTask, n_squareUpdateTask, n_pathTask,
 * n_hasDataForThread, n_shouldWait, n_update, n_save, n stop) stay behind the fail-fast guard.
 */
public final class MapCollisionStateCompatibility implements ClassHolderTransformer {
    private static final String CLASS = "zombie.MapCollisionData";
    private static final String BRIDGE = "port.natives.MapCollision64";
    private final Set<String> inventoriedNatives = new HashSet<>();

    public MapCollisionStateCompatibility() {
        try {
            for (String line : java.nio.file.Files.readAllLines(
                    java.nio.file.Path.of(System.getProperty("pz.nativeIndex")))) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\t", -1);
                if (fields.length != 4) throw new IllegalArgumentException("Invalid native index line");
                if (fields[0].equals(CLASS)) inventoriedNatives.add(fields[1] + fields[2]);
            }
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Missing native index: run scripts/build.sh", error);
        }
        if (inventoriedNatives.isEmpty()) throw new IllegalStateException("Inventory has no MapCollisionData natives");
    }

    private static final Map<String, MethodDescriptor> FORWARDED = Map.of(
        "n_setGameState(Ljava/lang/String;Z)V", new MethodDescriptor("setGameState",
            ValueType.object("java.lang.String"), ValueType.BOOLEAN, ValueType.VOID),
        "n_setGameState(Ljava/lang/String;I)V", new MethodDescriptor("setGameState",
            ValueType.object("java.lang.String"), ValueType.INTEGER, ValueType.VOID),
        "n_setGameState(Ljava/lang/String;F)V", new MethodDescriptor("setGameState",
            ValueType.object("java.lang.String"), ValueType.FLOAT, ValueType.VOID),
        "n_setGameState(Ljava/lang/String;D)V", new MethodDescriptor("setGameState",
            ValueType.object("java.lang.String"), ValueType.DOUBLE, ValueType.VOID),
        "n_setGameState(Ljava/lang/String;Ljava/lang/String;)V", new MethodDescriptor("setGameState",
            ValueType.object("java.lang.String"), ValueType.object("java.lang.String"), ValueType.VOID));

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (!cls.getName().equals(CLASS)) return;
        for (MethodHolder method : cls.getMethods()) {
            MethodDescriptor descriptor = MethodDescriptor.get(method);
            String key = method.getName() + descriptor.signatureToString();
            if (!inventoriedNatives.contains(key)) continue;
            MethodDescriptor target = FORWARDED.get(key);
            if (target == null) continue;
            if (!method.hasModifier(ElementModifier.STATIC))
                throw new IllegalStateException("MapCollisionData native must be static: " + key);
            method.getModifiers().remove(ElementModifier.NATIVE);
            var emitter = ProgramEmitter.create(method, context.getHierarchy());
            ValueEmitter[] values = new ValueEmitter[method.parameterCount()];
            for (int i = 0; i < values.length; i++) values[i] = emitter.var(i + 1, method.parameterType(i));
            var result = emitter.invoke(new MethodReference(BRIDGE, target), values);
            if (method.getResultType().equals(ValueType.VOID)) emitter.exit(); else result.returnValue();
        }
    }
}
