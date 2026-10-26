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
import port.shim.BrowserFileSystem;

public final class Boot {
    private static int frames;
    public static void main(String[] args) throws Exception {
        BrowserFileSystem.install();
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
        // GameBoot is compiled separately from this platform fixture.
        System.out.println("PZ platform smoke: Wasm GC, reflection, JNI failures and direct buffers OK; game not initialized.");
        ready(missing, checksum);
    }
    @JSExport public static int frame(double timestamp) { return ++frames; }
    @JSExport public static int frameCount() { return frames; }
    @JSExport public static int glError() { return GL11.glGetError(); }
    @JSExport public static void checkCompatibility() {
        compatibilityResult("running", "");
        new Thread(() -> {
            try { compatibilityResult("passed", port.runtime.CompatibilityChecks.concurrency() + ";" + port.runtime.CompatibilityChecks.filesAndMath()); }
            catch (Throwable error) { compatibilityResult("failed", error.toString()); error.printStackTrace(); }
        }, "compatibility-checks").start();
    }
    @JSBody(params={"status","detail"}, script="globalThis.pzCompatibility={status:status,detail:detail};")
    private static native void compatibilityResult(String status, String detail);
    @JSExport public static String readJavaFile(String path) throws Exception {
        return java.nio.file.Files.readString(new java.io.File(path).toPath());
    }
    @JSExport public static String exerciseJavaFiles(String base) throws Exception {
        var directory = new java.io.File(base);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new AssertionError("mkdirs");
        var file = new java.io.File(directory, "nio.txt");
        java.nio.file.Files.writeString(file.toPath(), "Java NIO: ñ😀");
        try (var random = new java.io.RandomAccessFile(file, "rw")) {
            random.seek(0); random.writeByte('j');
            random.seek(0); if (random.readUnsignedByte() != 'j') throw new AssertionError("seek/write");
        }
        var target = directory.toPath().resolve("sub", "file");
        if (!target.toString().endsWith("/sub/file")) throw new AssertionError("resolve varargs");
        Thread.onSpinWait();
        return java.nio.file.Files.readString(file.toPath());
    }
    @JSExport public static boolean readUnsignedBytes(String path) throws Exception {
        try (var stream = new java.io.FileInputStream(path)) {
            return stream.read() == 0 && stream.read() == 128 && stream.read() == 255 && stream.read() == -1;
        }
    }
    @JSBody(params="data", script="return data[0]+data[1]+data[2]+data[3];")
    private static native int bufferSum(Uint8Array data);
    @JSBody(params={"missing","checksum"}, script="globalThis.pzBoot={ready:true,missingNative:missing,bufferChecksum:checksum,javaVfs:true,gameInitialized:false};")
    private static native void ready(String missing, int checksum);
}
