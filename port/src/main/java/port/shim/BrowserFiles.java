package port.shim;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Uint8Array;

/** Explicit Java bridge to the preloaded VFS; java.io/java.nio substitution is pending. */
public final class BrowserFiles {
    public static String readUtf8(String path) {
        Uint8Array data = read(path);
        byte[] bytes = new byte[data.getLength()];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) data.get(i);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    public static void writeUtf8(String path, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        ByteBuffer direct = ByteBuffer.allocateDirect(bytes.length);
        direct.put(bytes).flip();
        write(path, Uint8Array.fromJavaBuffer(direct));
    }
    @JSBody(params="path", script="return globalThis.pzRuntime.vfs.readFile(path);")
    private static native Uint8Array read(String path);
    @JSBody(params={"path","data"}, script="globalThis.pzRuntime.vfs.writeFile(path,data);")
    private static native void write(String path, Uint8Array data);
}
