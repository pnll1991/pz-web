package port;

import java.nio.ByteBuffer;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSExport;
import org.teavm.jso.typedarrays.Uint8Array;
import port.demo.NativeProbe;
import port.demo.GLMappingProbe;
import port.runtime.CoreChecks;
import port.demo.lwjgl.GL11;
import port.shim.BrowserFiles;

public final class Boot {
    private static int frames;
    public static void main(String[] args) throws Exception {
        CoreChecks.run();
        String missing;
        try {
            NativeProbe.missing(42);
            throw new AssertionError("Missing native returned success");
        } catch (UnsupportedOperationException error) {
            missing = error.getMessage();
        }
        ByteBuffer direct = ByteBuffer.allocateDirect(4);
        direct.put(new byte[]{17, 34, 51, 68});
        direct.flip();
        int checksum = bufferSum(Uint8Array.fromJavaBuffer(direct));
        if (checksum != 170) throw new AssertionError("Direct ByteBuffer transfer");
        GLMappingProbe.glClearColor(0.04f, 0.06f, 0.08f, 1);
        GLMappingProbe.glClear(GL11.GL_COLOR_BUFFER_BIT);
        if (GLMappingProbe.glGetError() != 0) throw new AssertionError("Forwarded GL method");
        GL11.glClearColor(0.04f, 0.06f, 0.08f, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        if (GL11.glGetError() != 0) throw new AssertionError("WebGL error");
        if (!BrowserFiles.readUtf8("/media/platform-probe.txt").contains("sintético"))
            throw new AssertionError("Java VFS read");
        BrowserFiles.writeUtf8("/Zomboid/java-probe.txt", "Java → VFS: ñ");
        if (!"Java → VFS: ñ".equals(BrowserFiles.readUtf8("/Zomboid/java-probe.txt")))
            throw new AssertionError("Java VFS round trip");
        // TODO F1/F2: initialize the real game only after inventory and API analysis.
        System.out.println("PZ platform smoke: Wasm GC, reflection, JNI failures and direct buffers OK; game not initialized.");
        ready(missing, checksum);
    }
    @JSExport public static int frame(double timestamp) { return ++frames; }
    @JSExport public static int frameCount() { return frames; }
    @JSExport public static int glError() { return GL11.glGetError(); }
    @JSBody(params="data", script="return data[0]+data[1]+data[2]+data[3];")
    private static native int bufferSum(Uint8Array data);
    @JSBody(params={"missing","checksum"}, script="globalThis.pzBoot={ready:true,missingNative:missing,bufferChecksum:checksum,javaVfs:true,gameInitialized:false};")
    private static native void ready(String missing, int checksum);
}
