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
 * Forward the Lighting64 natives our own module really implements. Everything else in
 * zombie.iso.LightingJNI keeps the fail-fast guard with its JNI name, so an in-world session
 * raises instead of silently producing wrong light.
 *
 * Why this exists: LightingJNI.init() does System.loadLibrary("Lighting64") and, on
 * UnsatisfiedLinkError, logs, sleeps 3 s and calls System.exit(1). That killed the boot thread
 * right after initFonts, before any frame was drawn (STATUS.md round 42).
 */
public final class LightingNativeCompatibility implements ClassHolderTransformer {
    private static final String CLASS = "zombie.iso.LightingJNI";
    private static final String BRIDGE = "port.natives.Lighting64";
    private final Set<String> inventoriedNatives = new java.util.HashSet<>();

    public LightingNativeCompatibility() {
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
        if (inventoriedNatives.isEmpty()) throw new IllegalStateException("Inventory has no LightingJNI natives");
    }

    // Key: inventoried native name plus descriptor; value: our module's method for it.
    private static final Map<String, MethodDescriptor> FORWARDED = Map.of(
        "configure(F)V", new MethodDescriptor("configure", ValueType.FLOAT, ValueType.VOID),
        "WaitingForMain()Z", new MethodDescriptor("waitingForMain", ValueType.BOOLEAN),
        "DoLightingUpdateNew(JZ)V", new MethodDescriptor("doLightingUpdate", ValueType.LONG, ValueType.BOOLEAN, ValueType.VOID),
        "destroy()V", new MethodDescriptor("destroy", ValueType.VOID));

    // Round 79: natives forwarded to a Lighting64 method with the identical name and descriptor.
    private static final Set<String> SAME_NAME = Set.of(
        "scrollLeft(I)V", "scrollRight(I)V", "scrollUp(I)V", "scrollDown(I)V",
        "stateBeginUpdate(IIIII)V", "stateEndFrame(FFFFFFFZFI)V", "stateEndUpdate()V", "stateUpdateCounter(I)I",
        "teleport(III)V", "playerSet(FFFFFZZZZFFF)V", "chunkLightingDone(II)Z", "getChunkDirty(IIII)Z",
        "chunkBeginUpdate(IIII)V", "chunkEndUpdate()V", "chunkLevelBeginUpdate(I)V", "chunkLevelEndUpdate()V",
        "squareSetNull(III)V", "squareBeginUpdate(III)V", "squareSet(IZZZIJJIZ)V",
        "squareSetLightTransmission(FFFFFFFFFFFFFFFFFFFF)V", "squareAddCurtain(IZ)V", "squareAddDoor(ZZZ)V",
        "squareAddThumpable(ZZZZ)V", "squareAddWindow(ZZZ)V", "squareEndUpdate()V",
        "getVertLight(IIIII)I", "getLightInfo(IIIII)F", "getDarkMulti(IIII)F", "getTargetDarkMulti(IIII)F",
        "getSeen(IIII)Z", "getCanSee(IIII)Z", "getCouldSee(IIII)Z", "getSquareLighting(IIII[I)Z", "getSquareDirty(IIII)Z",
        "addLight(IIIIIFFFIZ)V", "addTempLight(IIIIIFFFI)V", "removeLight(I)V", "setLightActive(IZ)V",
        "setLightColor(IFFF)V", "addRoomLight(IJJIIIIIZ)V", "removeRoomLight(I)V", "setRoomLightActive(IZ)V",
        "updateTorch(IFFFFFFFFFFZFI)V", "removeTorch(I)V", "getVisibleRoomCount(I)I", "getVisibleRooms(I[J)I");

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (!cls.getName().equals(CLASS)) return;
        for (var method : cls.getMethods()) {
            MethodDescriptor descriptor = MethodDescriptor.get(method);
            String key = method.getName() + descriptor.signatureToString();
            if (!inventoriedNatives.contains(key)) continue;
            MethodDescriptor target = FORWARDED.get(key);
            // Round 79: every other native our module implements under its own JNI name and descriptor.
            if (target == null && SAME_NAME.contains(key)) target = MethodDescriptor.parse(key);
            if (target == null) continue;
            if (!method.hasModifier(ElementModifier.STATIC)) throw new IllegalStateException("LightingJNI native must be static: " + key);
            method.getModifiers().remove(ElementModifier.NATIVE);
            var emitter = ProgramEmitter.create(method, context.getHierarchy());
            var result = emitter.invoke(new MethodReference(BRIDGE, target), arguments(method, emitter));
            if (method.getResultType().equals(ValueType.VOID)) emitter.exit(); else result.returnValue();
        }
    }

    private static ValueEmitter[] arguments(MethodHolder method, ProgramEmitter emitter) {
        var values = new org.teavm.model.emit.ValueEmitter[method.parameterCount()];
        for (int i = 0; i < values.length; i++) values[i] = emitter.var(i + 1, method.parameterType(i));
        return values;
    }
}
