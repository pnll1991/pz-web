package port.compiler;

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
 * Forwards the JNI entry points of the game's native library classes to our own browser modules
 * (port.natives.*), and only the ones those modules really implement.
 *
 * Every other inventoried native of the same class keeps the fail-fast guard with its JNI name
 * (FailFastNatives runs first, but we match on name+signature from the same inventory). That is
 * deliberate: a library whose init() calls System.exit(1) on UnsatisfiedLinkError kills the main
 * thread and the render loop with it, so the boot needs a module; an in-world session must still
 * raise instead of silently doing the wrong thing.
 *
 * Verified against the pinned copy (42.21) with javap, per class:
 *  - zombie.popman.ZombiePopulationManager.init(): loadLibrary("PZPopMan64") + a Trigger_Zombie.xml
 *    watcher. The population natives are only reached with an IsoWorld (IngameState), so they stay.
 *  - zombie.vehicles.Clipper.init(): loadLibrary("PZClipper64") + static n_init()V.
 *  - zombie.pathfind.nativeCode.PathfindNative.init(): loadLibrary("PZPathFind64") only; its
 *    freeMemoryAtExit()V runs on GameWindow.exit(), where our module holds no native memory.
 */
public final class NativeModuleCompatibility implements ClassHolderTransformer {
    private record Spec(String bridge, Map<String, MethodDescriptor> forwarded) { }
    private static final String POP_MAN = "zombie.popman.ZombiePopulationManager";
    private static final String CLIPPER = "zombie.vehicles.Clipper";
    private static final String PATHFIND = "zombie.pathfind.nativeCode.PathfindNative";
    private static final Map<String, Spec> SPECS = Map.of(
        POP_MAN, new Spec("port.natives.PopMan64", Map.ofEntries(
            Map.entry("n_init(ZZIIII)V", new MethodDescriptor("init",
                ValueType.BOOLEAN, ValueType.BOOLEAN, ValueType.INTEGER, ValueType.INTEGER,
                ValueType.INTEGER, ValueType.INTEGER, ValueType.VOID)),
            Map.entry("n_config(FFFIFFFFI)V", new MethodDescriptor("config",
                ValueType.FLOAT, ValueType.FLOAT, ValueType.FLOAT, ValueType.INTEGER,
                ValueType.FLOAT, ValueType.FLOAT, ValueType.FLOAT, ValueType.FLOAT,
                ValueType.INTEGER, ValueType.VOID)),
            Map.entry("n_configFloat(Ljava/lang/String;F)V", new MethodDescriptor("configFloat",
                ValueType.object("java.lang.String"), ValueType.FLOAT, ValueType.VOID)),
            Map.entry("n_configInt(Ljava/lang/String;I)V", new MethodDescriptor("configInt",
                ValueType.object("java.lang.String"), ValueType.INTEGER, ValueType.VOID)),
            Map.entry("n_setSpawnOrigins([I)V", new MethodDescriptor("setSpawnOrigins",
                ValueType.arrayOf(ValueType.INTEGER), ValueType.VOID)),
            Map.entry("n_setOutfitNames([Ljava/lang/String;)V", new MethodDescriptor("setOutfitNames",
                ValueType.arrayOf(ValueType.object("java.lang.String")), ValueType.VOID)),
            Map.entry("n_hasDataForThread()Z", new MethodDescriptor("hasDataForThread", ValueType.BOOLEAN)),
            Map.entry("n_readyToPause()Z", new MethodDescriptor("readyToPause", ValueType.BOOLEAN)),
            Map.entry("n_shouldWait()Z", new MethodDescriptor("shouldWait", ValueType.BOOLEAN)),
            Map.entry("n_save()V", new MethodDescriptor("save", ValueType.VOID)),
            Map.entry("n_saveCell(II)V", new MethodDescriptor("saveCell",
                ValueType.INTEGER, ValueType.INTEGER, ValueType.VOID)),
            Map.entry("n_stop()V", new MethodDescriptor("stop", ValueType.VOID)),
            // Round 79: in-world entry points (n_<name> -> <name>, same descriptor).
            same("n_updateMain(FD)V"), same("n_updateThread()V"), same("n_addZombie(FFFBIIIII)V"),
            same("n_registerZombie(FFFBIII)I"), same("n_releaseZombie(I)V"), same("n_aggroTarget(III)V"),
            same("n_loadChunk(IIZ)V"), same("n_loadedAreas(I[IZ)V"), same("n_updateRealZombies(I[I[F)V"),
            same("n_spawnHorde(IIIIFFI)V"), same("n_worldSound(IIII)V"), same("n_getAddZombieCount()I"),
            same("n_getAddZombieData(ILjava/nio/ByteBuffer;)I"), same("n_hasRadarData()Z"),
            same("n_requestRadarData()V"), same("n_getRadarZombieData([F)I"))),
        CLIPPER, new Spec("port.natives.Clipper2", Map.of(
            "n_init()V", new MethodDescriptor("init", ValueType.VOID))),
        PATHFIND, new Spec("port.natives.PathFind64", Map.of(
            "freeMemoryAtExit()V", new MethodDescriptor("freeMemoryAtExit", ValueType.VOID))));

    private static Map.Entry<String, MethodDescriptor> same(String key) {
        MethodDescriptor parsed = MethodDescriptor.parse(key);
        return Map.entry(key, MethodDescriptor.parse(parsed.getName().substring(2) + key.substring(key.indexOf('('))));
    }

    // name+descriptor -> the inventory entries of the classes we forward
    private final Map<String, Set<String>> inventoried = new java.util.HashMap<>();

    public NativeModuleCompatibility() {
        try {
            for (String line : java.nio.file.Files.readAllLines(
                    java.nio.file.Path.of(System.getProperty("pz.nativeIndex")))) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\t", -1);
                if (fields.length != 4) throw new IllegalArgumentException("Invalid native index line");
                if (!SPECS.containsKey(fields[0])) continue;
                inventoried.computeIfAbsent(fields[0], k -> new java.util.HashSet<>())
                    .add(fields[1] + fields[2]);
            }
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Missing native index: run scripts/build.sh", error);
        }
        for (String cls : SPECS.keySet())
            if (inventoried.get(cls) == null || inventoried.get(cls).isEmpty())
                throw new IllegalStateException("Inventory has no natives for " + cls);
    }

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        Spec spec = SPECS.get(cls.getName());
        if (spec == null) return;
        for (var method : cls.getMethods()) {
            MethodDescriptor descriptor = MethodDescriptor.get(method);
            String key = method.getName() + descriptor.signatureToString();
            if (!inventoried.get(cls.getName()).contains(key)) continue;
            MethodDescriptor target = spec.forwarded().get(key);
            if (target == null) continue;
            if (!method.hasModifier(ElementModifier.STATIC))
                throw new IllegalStateException("Forwarded native must be static: " + cls.getName() + "." + key);
            method.getModifiers().remove(ElementModifier.NATIVE);
            var emitter = ProgramEmitter.create(method, context.getHierarchy());
            ValueEmitter[] arguments = new ValueEmitter[method.parameterCount()];
            for (int i = 0; i < arguments.length; i++)
                arguments[i] = emitter.var(i + 1, method.parameterType(i));
            var result = emitter.invoke(new MethodReference(spec.bridge(), target), arguments);
            if (method.getResultType().equals(ValueType.VOID)) emitter.exit(); else result.returnValue();
        }
    }
}
