package port.shim;

import java.nio.IntBuffer;
import org.teavm.jso.JSBody;
import port.runtime.memory.NativeMemory;

/** Wrappers for the browser window system, independent of native OS detection. */
public final class BrowserWindow {
    // Handles belong to this backend, never to the host OS. The page sees exactly one display.
    public static final long MONITOR_HANDLE = 2L;
    /** GLFWVidMode layout: width, height, redBits, greenBits, blueBits, refreshRate (6 ints). */
    private static final int VID_MODE_BYTES = 24;
    private static long videoModeAddress;
    private BrowserWindow() { }
    public static void init() { initNative(); }
    public static int desktopWidth() { return metric(0); }
    public static int desktopHeight() { return metric(1); }
    public static int colorBits() { return metric(2); }
    public static int glfwGetKeyScancode(int key) { return scancode(key); }
    public static String glfwGetKeyName(int key,int code) { return keyName(key,code); }
    public static int glfwGetKey(long window,int key) { return key(window,key); }
    public static int glfwGetMouseButton(long window,int button) { return button(window,button); }
    // The single display's position and work area: a tab has no OS panels, so the
    // work area is the whole screen and the monitor origin is (0,0).
    public static int monitorX() { return monitorMetric(0); }
    public static int monitorY() { return monitorMetric(1); }
    public static int monitorWorkareaWidth() { return monitorMetric(2); }
    public static int monitorWorkareaHeight() { return monitorMetric(3); }
    /** glfwGetPrimaryMonitor returns this backend's own monitor handle. */
    public static long glfwGetPrimaryMonitor() { monitorX(); return MONITOR_HANDLE; }
    public static void glfwGetMonitorWorkarea(long monitor,int[] x,int[] y,int[] width,int[] height) {
        checkMonitor(monitor);
        x[0] = monitorX(); y[0] = monitorY(); width[0] = monitorWorkareaWidth(); height[0] = monitorWorkareaHeight();
    }
    public static void glfwGetMonitorWorkarea(long monitor,IntBuffer x,IntBuffer y,IntBuffer width,IntBuffer height) {
        checkMonitor(monitor);
        x.put(0, monitorX()); y.put(0, monitorY()); width.put(0, monitorWorkareaWidth()); height.put(0, monitorWorkareaHeight());
    }
    /**
     * One address holding the single mode the page can actually present: the canvas drawing
     * buffer size and the screen's colour depth; the browser exposes no refresh rate (0).
     * Allocated once on the arena, which never reuses an address, so earlier struct views stay valid.
     */
    public static long videoModeAddress() {
        if (videoModeAddress == 0) {
            int bits = colorBits();
            int perChannel = bits >= 3 ? bits / 3 : 0;
            long address = NativeMemory.nmemAllocChecked(VID_MODE_BYTES);
            NativeMemory.memPutInt(address, bufferWidth());
            NativeMemory.memPutInt(address + 4, bufferHeight());
            NativeMemory.memPutInt(address + 8, perChannel);
            NativeMemory.memPutInt(address + 12, perChannel);
            NativeMemory.memPutInt(address + 16, perChannel);
            NativeMemory.memPutInt(address + 20, 0);
            videoModeAddress = address;
        }
        return videoModeAddress;
    }
    /** Raises for any handle that is not this backend's monitor; never pretends success. */
    public static void checkMonitor(long monitor) {
        if (monitor != MONITOR_HANDLE) throw new IllegalArgumentException("Invalid browser monitor handle: " + monitor);
    }
    @JSBody(script="globalThis.pzRuntime.window.init();") private static native void initNative();
    @JSBody(params="kind",script="var w=globalThis.pzRuntime.window;return kind===0?w.desktopWidth():kind===1?w.desktopHeight():w.colorBits();") private static native int metric(int kind);
    @JSBody(params="kind",script="var w=globalThis.pzRuntime.window;return kind===0?w.monitorX():kind===1?w.monitorY():kind===2?w.monitorWorkareaWidth():w.monitorWorkareaHeight();") private static native int monitorMetric(int kind);
    @JSBody(params="kind",script="var w=globalThis.pzRuntime.window;return kind===0?w.bufferWidth():w.bufferHeight();") private static native int bufferMetric(int kind);
    public static int bufferWidth() { return bufferMetric(0); }
    public static int bufferHeight() { return bufferMetric(1); }
    @JSBody(params="key",script="return globalThis.pzRuntime.window.keyScancode(key);") private static native int scancode(int key);
    @JSBody(params={"key","code"},script="return globalThis.pzRuntime.window.keyName(key,code);") private static native String keyName(int key,int code);
    // JSBody cannot receive Java long directly: validate the backend handle in Java.
    private static int key(long window,int key) { return keyNative(checkedHandle(window),key); }
    private static int button(long window,int button) { return buttonNative(checkedHandle(window),button); }
    private static int checkedHandle(long window) {
        if (window != 1) throw new IllegalArgumentException("Invalid browser window handle: " + window);
        return (int)window;
    }
    @JSBody(params={"window","key"},script="return globalThis.pzRuntime.window.key(window,key);") private static native int keyNative(int window,int key);
    @JSBody(params={"window","button"},script="return globalThis.pzRuntime.window.button(window,button);") private static native int buttonNative(int window,int button);
}
