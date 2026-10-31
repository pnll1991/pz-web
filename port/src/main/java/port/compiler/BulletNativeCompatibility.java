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
        "isWorldInit()Z", new MethodDescriptor("worldInitialised", ValueType.BOOLEAN),
        // Round 52: called 241 times from VehicleScript.toBullet during script load. The bridge
        // keeps the same signature (String, float[]) and copies the block into the wasm module.
        "defineVehicleScript(Ljava/lang/String;[F)V",
        new MethodDescriptor("defineVehicleScript", ValueType.object("java.lang.String"),
            ValueType.arrayOf(ValueType.FLOAT), ValueType.VOID));

    private static final java.util.Set<String> WORLD = java.util.Set.of(
        "ToBullet(Ljava/nio/ByteBuffer;)V", "isWorldInit()Z", "initWorld(IIIIIIZ)V", "destroyWorld()V",
        "activateChunkMap(IIII)V", "deactivateChunkMap(I)V", "scrollChunkMap(II)V", "setChunkMinMaxLevel(IIII)V",
        "stepSimulation(FIF)V", "getVehicleCount()I", "getVehiclePhysics(I[F)I", "getObjectPhysics([F)I",
        "defineRagdollConstraints([FZ)Z", "defineRagdollAnchors([FZ)Z", "defineRagdollBodyPartInfo([FZ)Z",
        "defineRagdollBodyDynamics([FZ)Z", "definePhysicsMesh(IZ[F)V", "defineVehiclePhysicsMesh(Ljava/lang/String;I[F)V",
        "clearPhysicsMeshes()V");

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (!cls.getName().equals(CLASS)) return;
        for (var method : cls.getMethods()) {
            // Runs after FailFastNatives, so the modifier may already be gone; match the
            // inventoried name plus signature read from the same index.
            MethodDescriptor descriptor = MethodDescriptor.get(method);
            String key = method.getName() + descriptor.signatureToString();
            if (!inventoriedNatives.contains(key)) continue;
            MethodDescriptor target = FORWARDED.get(key);
            String bridge = BRIDGE;
            // Round 79: world lifecycle natives answered by port.natives.BulletWorld (same name/descriptor).
            if (WORLD.contains(key)) { target = MethodDescriptor.parse(key); bridge = "port.natives.BulletWorld"; }
            if (target == null) continue;
            method.getModifiers().remove(ElementModifier.NATIVE);
            var emitter = ProgramEmitter.create(method, context.getHierarchy());
            // ProgramEmitter.invoke does not pass the method's own locals, so a bridge that keeps
            // the game's signature (defineVehicleScript) receives each parameter explicitly.
            var arguments = new org.teavm.model.emit.ValueEmitter[method.parameterCount()];
            for (int i = 0; i < arguments.length; i++) arguments[i] = emitter.var(i + 1, method.parameterType(i));
            var result = emitter.invoke(new MethodReference(bridge, target), arguments);
            if (method.getResultType().equals(ValueType.VOID)) emitter.exit(); else result.returnValue();
        }
    }
}
