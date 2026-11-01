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
 * Round 82: forwards the client-side natives of zombie.core.raknet.RakNetPeerInterface and zombie.core.znet.ZNet
 * to port.natives.RakNetPeer (WebSocket to the local RakNet bridge). The bridge method has the same name and
 * descriptor as the JNI method and takes no `this`. Server-only and Steam natives are deliberately not listed:
 * FailFastNatives keeps throwing with their JNI name instead of pretending to work.
 *
 * Names and signatures verified with javap against the pinned copy (42.21); each forwarded entry must also be in
 * the native inventory of the build, so a changed jar breaks the build instead of silently mis-binding.
 */
public final class RakNetNativeCompatibility implements ClassHolderTransformer {
    private static final String BRIDGE = "port.natives.RakNetPeer";
    private static final String PEER = "zombie.core.raknet.RakNetPeerInterface";
    private static final String ZNET = "zombie.core.znet.ZNet";
    private static final String VOICE = "zombie.core.raknet.RakVoice";
    // class -> (JNI name+descriptor -> bridge method name)
    private static final Map<String, Map<String, String>> FORWARDED = Map.of(
        PEER, Map.ofEntries(
            Map.entry("Init(Z)V", "Init"), Map.entry("Startup(ILjava/lang/String;Z)I", "Startup"),
            Map.entry("Shutdown()V", "Shutdown"), Map.entry("SetClientPort(I)V", "SetClientPort"),
            Map.entry("SetMaximumIncomingConnections(I)V", "SetMaximumIncomingConnections"),
            Map.entry("SetOccasionalPing(Z)V", "SetOccasionalPing"), Map.entry("SetTimeoutTime(I)V", "SetTimeoutTime"),
            Map.entry("SetUnreliableTimeout(I)V", "SetUnreliableTimeout"),
            Map.entry("Connect(Ljava/lang/String;ILjava/lang/String;Z)I", "Connect"),
            Map.entry("TryReceive()Z", "TryReceive"), Map.entry("nativeGetData(Ljava/nio/ByteBuffer;)I", "nativeGetData"),
            Map.entry("sendNative(Ljava/nio/ByteBuffer;IIIBJZ)I", "sendNative"),
            Map.entry("getGuidOfPacket()J", "getGuidOfPacket"), Map.entry("getIPFromGUID(J)Ljava/lang/String;", "getIPFromGUID"),
            Map.entry("disconnect(JLjava/lang/String;)V", "disconnect"), Map.entry("GetAveragePing(J)I", "GetAveragePing"),
            Map.entry("GetLastPing(J)I", "GetLastPing"), Map.entry("GetLowestPing(J)I", "GetLowestPing"),
            Map.entry("GetMTUSize(J)I", "GetMTUSize"), Map.entry("GetConnectionsNumber()I", "GetConnectionsNumber"),
            Map.entry("GetConnectionType(J)B", "GetConnectionType")),
        ZNET, Map.of("init()V", "znetInit", "setLogLevel(I)V", "znetSetLogLevel"),
        VOICE, Map.of("GetChannelStatistics(J[J)Z", "voiceGetChannelStatistics", "GetServerVOIPEnable()Z", "voiceGetServerVOIPEnable",
            "GetMaxDistance()F", "voiceGetMaxDistance"));

    private final Map<String, Set<String>> inventoried = new java.util.HashMap<>();

    public RakNetNativeCompatibility() {
        try {
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of(System.getProperty("pz.nativeIndex")))) {
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
            if (natives == null || !natives.containsAll(entry.getValue().keySet()))
                throw new IllegalStateException("Inventory does not list the RakNet natives we forward: " + entry.getKey());
        }
    }

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        Map<String, String> forwarded = FORWARDED.get(cls.getName());
        if (forwarded == null) return;
        for (MethodHolder method : cls.getMethods()) {
            String key = method.getName() + MethodDescriptor.get(method).signatureToString();
            String target = forwarded.get(key);
            if (target == null || !inventoried.get(cls.getName()).contains(key)) continue;
            method.getModifiers().remove(ElementModifier.NATIVE);
            var emitter = ProgramEmitter.create(method, context.getHierarchy());
            ValueEmitter[] arguments = new ValueEmitter[method.parameterCount()];
            // program variable 0 is `this` (or unused for static); parameters start at 1
            for (int i = 0; i < arguments.length; i++) arguments[i] = emitter.var(i + 1, method.parameterType(i));
            MethodDescriptor descriptor = new MethodDescriptor(target, MethodDescriptor.get(method).getSignature());
            var result = emitter.invoke(new MethodReference(BRIDGE, descriptor), arguments);
            if (method.getResultType().equals(ValueType.VOID)) emitter.exit(); else result.returnValue();
        }
    }
}
