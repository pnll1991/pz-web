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
import port.shim.BrowserPlatform;

public final class Boot {
    private static int frames;
    public static void main(String[] args) throws Exception {
        BrowserFileSystem.install();
        BrowserPlatform.installLocale();
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
    @JSExport public static String windowSystemProbe() {
        port.shim.BrowserWindow.init();
        if (port.shim.BrowserWindow.glfwGetKeyScancode(65) != 65 ||
                port.shim.BrowserWindow.glfwGetKeyScancode(-1) != -1)
            throw new AssertionError("Browser scancode domain");
        // The page's single display: its own handle, origin and work area, never a host monitor.
        long monitor = port.shim.BrowserWindow.glfwGetPrimaryMonitor();
        if (monitor == 1L || monitor == 0L) throw new AssertionError("Monitor handle collides with the window handle");
        int[] x = new int[1]; int[] y = new int[1]; int[] width = new int[1]; int[] height = new int[1];
        port.shim.BrowserWindow.glfwGetMonitorWorkarea(monitor, x, y, width, height);
        java.nio.IntBuffer bx = java.nio.IntBuffer.allocate(1);
        java.nio.IntBuffer by = java.nio.IntBuffer.allocate(1);
        java.nio.IntBuffer bw = java.nio.IntBuffer.allocate(1);
        java.nio.IntBuffer bh = java.nio.IntBuffer.allocate(1);
        port.shim.BrowserWindow.glfwGetMonitorWorkarea(monitor, bx, by, bw, bh);
        if (x[0] != bx.get(0) || y[0] != by.get(0) || width[0] != bw.get(0) || height[0] != bh.get(0))
            throw new AssertionError("Monitor work area differs between the array and buffer overloads");
        // One arena address with the single mode the page can present; stable across calls.
        long mode = port.shim.BrowserWindow.videoModeAddress();
        if (mode == 0 || mode != port.shim.BrowserWindow.videoModeAddress())
            throw new AssertionError("Video mode address is unstable");
        int modeWidth = port.runtime.memory.NativeMemory.memGetInt(mode);
        int modeHeight = port.runtime.memory.NativeMemory.memGetInt(mode + 4);
        int modeBits = port.runtime.memory.NativeMemory.memGetInt(mode + 8)
                + port.runtime.memory.NativeMemory.memGetInt(mode + 12)
                + port.runtime.memory.NativeMemory.memGetInt(mode + 16);
        int modeRefresh = port.runtime.memory.NativeMemory.memGetInt(mode + 20);
        if (modeWidth != port.shim.BrowserWindow.bufferWidth() || modeHeight != port.shim.BrowserWindow.bufferHeight())
            throw new AssertionError("Video mode does not match the canvas drawing buffer");
        String invalidMonitor;
        try { port.shim.BrowserWindow.checkMonitor(monitor + 1); invalidMonitor = "unexpected success"; }
        catch (IllegalArgumentException error) { invalidMonitor = error.getMessage(); }
        return port.shim.BrowserWindow.desktopWidth() + ":" + port.shim.BrowserWindow.desktopHeight()
                + ":" + port.shim.BrowserWindow.colorBits()
                + "|" + monitor + ":" + x[0] + "," + y[0] + "," + width[0] + "," + height[0]
                + "|" + modeWidth + "x" + modeHeight + ":" + modeBits + ":" + modeRefresh
                + "|" + invalidMonitor;
    }
    /** Properties as Java actually sees them after the browser locale was installed. */
    @JSExport public static String localeProperties() {
        return String.valueOf(System.getProperty("user.language")) + ":" + String.valueOf(System.getProperty("user.country"));
    }
    @JSExport public static void checkTask4() {
        task4Result("running", "starting");
        new Thread(() -> {try {port.runtime.Task4Checks.run();task4Result("passed", "memory,handles,concurrency,resources,xml,watchers,jdbc,digest");}catch(Throwable error){task4Result("failed",error.toString());error.printStackTrace();}},"task4-checks").start();
    }
    @JSExport public static void checkSavedSql() {
        task4Result("running", "sql-reload");
        new Thread(() -> {try {task4Result("passed",port.runtime.Task4Checks.readSavedSql());}catch(Throwable error){task4Result("failed",error.toString());error.printStackTrace();}},"sql-reload").start();
    }
    @JSBody(params={"status","detail"},script="globalThis.pzTask4={status:status,detail:detail};")private static native void task4Result(String status,String detail);
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
