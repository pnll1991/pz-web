// Round 82: WebSocket <-> RakNet bridge process (one JVM per browser client: the native peer is a per-process singleton).
// Frames on stdin/stdout are u32-BE length + body; dev-server/ws-bridge.mjs relays them 1:1 to a WebSocket.
// The real game RakNetPeerInterface (libRakNet64 / libZNetNoSteam64 from PZ_HOME) talks UDP to the dedicated server, so
// handshake, version check, reliability and fragmentation are the game's own. Loopback targets only.
// Compiled against the pinned game jar on the classpath; nothing from the game is copied into the repository.
import zombie.core.raknet.RakNetPeerInterface;
import java.io.*;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public final class Bridge {
    static final int C_CONNECT = 1, C_SEND = 2, C_DISCONNECT = 3, C_SHUTDOWN = 4;
    static final int B_PACKET = 0x81, B_CONNECT_RESULT = 0x82, B_STATS = 0x83;
    static final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(System.out, 1 << 16));
    static RakNetPeerInterface peer;
    static volatile boolean running = true;
    static final ConcurrentHashMap<Long, Boolean> guids = new ConcurrentHashMap<>();

    static synchronized void emit(int type, byte[] body, int off, int len) throws IOException {
        out.writeInt(1 + len); out.writeByte(type); out.write(body, off, len); out.flush();
    }

    public static void main(String[] args) throws Exception {
        System.setOut(System.err); // keep stdout exclusively for frames (native/Java logging goes to stderr)
        System.loadLibrary("RakNet64");
        System.loadLibrary("ZNetNoSteam64");
        RakNetPeerInterface.init();
        peer = new RakNetPeerInterface();
        peer.Init(false);
        peer.SetMaximumIncomingConnections(0);
        peer.SetClientPort(20000 + (int) (Math.random() * 20000));
        peer.SetOccasionalPing(true);
        int rc = peer.Startup(1);
        if (rc != 0) { System.err.println("bridge: Startup failed " + rc); System.exit(2); }
        Thread rx = new Thread(Bridge::receiveLoop, "bridge-rx"); rx.setDaemon(true); rx.start();
        DataInputStream in = new DataInputStream(new BufferedInputStream(System.in, 1 << 16));
        try {
            while (true) {
                int len = in.readInt();
                byte[] f = new byte[len]; in.readFully(f);
                if (!handle(f)) break;
            }
        } catch (EOFException ignored) { }
        running = false;
        try { peer.Shutdown(); } catch (Throwable ignored) { }
        Runtime.getRuntime().halt(0);
    }

    static boolean handle(byte[] f) throws Exception {
        ByteBuffer b = ByteBuffer.wrap(f); b.position(1);
        switch (f[0] & 0xff) {
            case C_CONNECT -> {
                int port = b.getShort() & 0xffff;
                byte[] h = new byte[b.get() & 0xff]; b.get(h);
                byte[] pw = new byte[b.remaining()]; b.get(pw);
                String host = new String(h, StandardCharsets.UTF_8);
                InetAddress a = InetAddress.getByName(host);
                if (!a.isLoopbackAddress()) throw new IOException("bridge: only loopback targets are allowed, got " + host);
                int r = peer.Connect(a.getHostAddress(), port, new String(pw, StandardCharsets.UTF_8), false);
                byte[] o = ByteBuffer.allocate(4).putInt(r).array();
                emit(B_CONNECT_RESULT, o, 0, 4);
            }
            case C_SEND -> {
                int prio = b.get() & 0xff, rel = b.get() & 0xff, ch = b.get(), bc = b.get();
                long guid = b.getLong();
                ByteBuffer data = ByteBuffer.allocateDirect(b.remaining()); data.put(b); data.flip();
                peer.SendRaw(data, prio, rel, (byte) ch, guid, bc != 0);
            }
            case C_DISCONNECT -> {
                long guid = b.getLong();
                byte[] r = new byte[b.remaining()]; b.get(r);
                peer.disconnect(guid, new String(r, StandardCharsets.UTF_8));
            }
            case C_SHUTDOWN -> { return false; }
            default -> throw new IOException("bridge: unknown frame type " + (f[0] & 0xff));
        }
        return true;
    }

    static void receiveLoop() {
        ByteBuffer rb = ByteBuffer.allocateDirect(1_000_000);
        long nextStats = 0;
        try {
            while (running) {
                boolean any = false;
                while (peer.Receive(rb)) {
                    any = true;
                    long guid = peer.getGuidOfPacket();
                    guids.put(guid, Boolean.TRUE);
                    byte[] o = new byte[8 + rb.remaining()];
                    ByteBuffer.wrap(o).putLong(guid).put(rb);
                    emit(B_PACKET, o, 0, o.length);
                }
                long now = System.currentTimeMillis();
                if (now >= nextStats) { nextStats = now + 1000; stats(); }
                if (!any) Thread.sleep(2);
            }
        } catch (Throwable t) { t.printStackTrace(); }
    }

    // per-guid values the browser natives answer from a cache (they must be synchronous there)
    static void stats() throws IOException {
        for (Long g : guids.keySet()) {
            String ip = peer.getIPFromGUID(g);
            byte[] ipb = (ip == null ? "" : ip).getBytes(StandardCharsets.UTF_8);
            ByteBuffer o = ByteBuffer.allocate(8 + 4 * 4 + 1 + 1 + ipb.length);
            o.putLong(g).putInt(peer.GetAveragePing(g)).putInt(peer.GetLastPing(g)).putInt(peer.GetLowestPing(g))
                .putInt(peer.GetMTUSize(g)).put(peer.GetConnectionType(g)).put((byte) ipb.length).put(ipb);
            emit(B_STATS, o.array(), 0, o.position());
        }
    }
}
