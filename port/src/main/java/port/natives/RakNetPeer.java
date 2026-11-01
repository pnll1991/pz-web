package port.natives;

import java.nio.ByteBuffer;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Uint8Array;

/**
 * Round 82: browser implementation of the client-side natives of zombie.core.raknet.RakNetPeerInterface and
 * zombie.core.znet.ZNet (libraries RakNet64 / ZNetNoSteam64). The peer is a WebSocket to the local dev server,
 * which spawns a bridge process that owns the game's real RakNet peer (tools/mp-bridge, docs/multiplayer-design.md).
 *
 * Forwarded by port.compiler.RakNetNativeCompatibility: same name and descriptor as the JNI method, without `this`
 * (the game keeps one peer). Natives that only make sense for a server or for Steam are NOT forwarded and keep the
 * fail-fast guard with their JNI name. 64-bit RakNet GUIDs cross the JS boundary as two ints.
 */
public final class RakNetPeer {
    private RakNetPeer() { }
    private static final String LIBRARY = "RakNet64";

    public static void install() {
        NativeRegistry.register("RakNet64");
        NativeRegistry.register("ZNetNoSteam64");
        NativeRegistry.publish(LIBRARY, "installed", null);
    }

    // -- ZNet --------------------------------------------------------------------------------------------------------
    /** ZNet.init(): the native only prepares its own logging; we hold no native state. */
    public static void znetInit() { NativeRegistry.publish(LIBRARY, "znet-init", "znetInit"); }
    public static void znetSetLogLevel(int level) { NativeRegistry.publish(LIBRARY, "znet-log-level", "znetSetLogLevel"); }

    // -- zombie.core.raknet.RakVoice: there is no voice engine in the browser (no microphone/codec path), so the queries the
    //    multiplayer client makes anyway answer what is true without one. Every other RakVoice native keeps its fail-fast guard.
    /** No voice channel exists for any connection, hence no channel statistics (the caller keeps its -1 defaults). */
    public static boolean voiceGetChannelStatistics(long guid, long[] out) { return false; }
    /** The server's VOIP service is not available to this client. */
    public static boolean voiceGetServerVOIPEnable() { return false; }
    /** Voice carries nowhere without an engine. */
    public static float voiceGetMaxDistance() { return 0f; }

    // -- peer configuration the client path calls (the bridge process picks its own ports/limits) ----------------------
    public static void Init(boolean steam) { NativeRegistry.publish(LIBRARY, "init", "Init"); }
    public static int Startup(int maxConnections, String version, boolean server) {
        if (server) throw new UnsatisfiedLinkError("zombie.core.raknet.RakNetPeerInterface.Startup: server mode is not available in the browser");
        NativeRegistry.publish(LIBRARY, "startup", "Startup");
        return 0; // 0 = RAKNET_STARTED; the bridge process starts its real peer when the socket opens
    }
    public static void Shutdown() { shutdown0(); NativeRegistry.publish(LIBRARY, "shutdown", "Shutdown"); }
    public static void SetClientPort(int port) { }
    public static void SetMaximumIncomingConnections(int n) { }
    public static void SetOccasionalPing(boolean on) { }
    public static void SetTimeoutTime(int ms) { }
    public static void SetUnreliableTimeout(int ms) { }

    // -- traffic -----------------------------------------------------------------------------------------------------
    public static int Connect(String host, int port, String password, boolean steamRelay) {
        if (steamRelay) throw new UnsatisfiedLinkError("zombie.core.raknet.RakNetPeerInterface.Connect: Steam relay is not available in the browser");
        NativeRegistry.publish(LIBRARY, "connect", "Connect");
        return connect0(host, port, password);
    }
    public static boolean TryReceive() { return tryReceive0(); }
    public static int nativeGetData(ByteBuffer buffer) {
        int n = currentLength0();
        copyCurrent0(Uint8Array.fromJavaBuffer(buffer));
        buffer.position(0); buffer.limit(n);
        return n;
    }
    public static int sendNative(ByteBuffer data, int length, int priority, int reliability, byte channel, long guid, boolean broadcast) {
        Uint8Array view = Uint8Array.fromJavaBuffer(data);
        return send0(view, length, priority, reliability, channel, (int) (guid >>> 32), (int) guid, broadcast);
    }
    public static long getGuidOfPacket() { return ((long) currentHi0() << 32) | (currentLo0() & 0xffffffffL); }
    public static void disconnect(long guid, String reason) { disconnect0((int) (guid >>> 32), (int) guid, reason == null ? "" : reason); }

    // -- per-connection values answered from the bridge's periodic stats frame ---------------------------------------------
    public static String getIPFromGUID(long guid) { return stat0S((int) (guid >>> 32), (int) guid, "ip"); }
    public static int GetAveragePing(long guid) { return stat0((int) (guid >>> 32), (int) guid, "avg"); }
    public static int GetLastPing(long guid) { return stat0((int) (guid >>> 32), (int) guid, "last"); }
    public static int GetLowestPing(long guid) { return stat0((int) (guid >>> 32), (int) guid, "lowest"); }
    public static int GetMTUSize(long guid) { return stat0((int) (guid >>> 32), (int) guid, "mtu"); }
    public static byte GetConnectionType(long guid) { return (byte) stat0((int) (guid >>> 32), (int) guid, "type"); }
    public static int GetConnectionsNumber() { return connectionCount0(); }

    @JSBody(params={"h","p","w"}, script="return globalThis.pzRuntime.net.connect(h,p,w);")
    private static native int connect0(String host, int port, String password);
    @JSBody(script="globalThis.pzRuntime.net.shutdown();")
    private static native void shutdown0();
    @JSBody(script="return globalThis.pzRuntime.net.tryReceive();")
    private static native boolean tryReceive0();
    @JSBody(script="return globalThis.pzRuntime.net.currentLength();")
    private static native int currentLength0();
    @JSBody(script="return globalThis.pzRuntime.net.currentHi();")
    private static native int currentHi0();
    @JSBody(script="return globalThis.pzRuntime.net.currentLo();")
    private static native int currentLo0();
    @JSBody(params="dst", script="return globalThis.pzRuntime.net.copyCurrentTo(dst);")
    private static native int copyCurrent0(Uint8Array dst);
    @JSBody(params={"src","len","prio","rel","ch","hi","lo","bc"},
        script="return globalThis.pzRuntime.net.send(src.subarray(0,len),prio,rel,ch,hi,lo,bc);")
    private static native int send0(Uint8Array src, int len, int prio, int rel, int ch, int hi, int lo, boolean bc);
    @JSBody(params={"hi","lo","reason"}, script="globalThis.pzRuntime.net.disconnect(hi,lo,reason);")
    private static native void disconnect0(int hi, int lo, String reason);
    @JSBody(params={"hi","lo","f"}, script="return globalThis.pzRuntime.net.statOf(hi,lo,f,0);")
    private static native int stat0(int hi, int lo, String field);
    @JSBody(params={"hi","lo","f"}, script="return globalThis.pzRuntime.net.statOf(hi,lo,f,'');")
    private static native String stat0S(int hi, int lo, String field);
    @JSBody(script="return globalThis.pzRuntime.net.connectionCount();")
    private static native int connectionCount0();
}
