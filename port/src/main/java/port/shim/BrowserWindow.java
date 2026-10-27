package port.shim;

import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;
import java.nio.ByteBuffer;
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
    // --- Window lifecycle and metrics, with descriptors identical to org.lwjgl.glfw.GLFW ---
    // WindowCompatibility forwards any static GLFW method whose exact descriptor exists here, so
    // these names/signatures are the mapping. Only JDK types appear: the module classpath has no
    // LWJGL. Long handles are validated in Java because @JSBody cannot receive a long.
    /** The UTF-8 title buffer GLFW passes; decoded through the arena, never copied into a fake. */
    public static long glfwCreateWindow(int width,int height,ByteBuffer title,long monitor,long share) {
        if (share != 0) throw new IllegalArgumentException("Browser windows cannot share OpenGL contexts: share=" + share);
        String text = title == null ? null : NativeMemory.memUTF8(NativeMemory.memAddress0(title));
        return createWindow(width,height,text,monitor);
    }
    public static long createWindow(int width,int height,String title,long monitor) {
        int handle = createNative(width,height,(int)monitor,title == null ? "" : title);
        return handle;
    }
    /**
     * The CharSequence variant org.lwjglx.opengl.Display.create passes. Kept as a bridge method so
     * WindowCompatibility can forward it without IR: the title is converted to plain text here and
     * window.mjs records it in the 'created' event.
     */
    public static long glfwCreateWindow(int width,int height,java.lang.CharSequence title,long monitor,long share) {
        if (share != 0) throw new IllegalArgumentException("Browser windows cannot share OpenGL contexts: share=" + share);
        return createWindow(width,height,title == null ? "" : String.valueOf(title),monitor);
    }
    /** Validate a window handle for the callback setters, which receive no other window argument. */
    public static void checkWindow(long window) { checkedHandle(window); }
    public static void glfwDestroyWindow(long window) { destroyNative(checkedHandle(window)); }
    public static boolean glfwWindowShouldClose(long window) { return shouldCloseNative(checkedHandle(window)) != 0; }
    public static void glfwSetWindowShouldClose(long window,boolean value) { setShouldCloseNative(checkedHandle(window),value?1:0); }
    public static void glfwShowWindow(long window) { showNative(checkedHandle(window)); }
    public static void glfwHideWindow(long window) { hideNative(checkedHandle(window)); }
    public static void glfwFocusWindow(long window) { focusNative(checkedHandle(window)); }
    public static void glfwMakeContextCurrent(long window) { makeContextCurrentNative(checkedHandle(window)); }
    public static long glfwGetCurrentContext() { return currentContextNative(); }
    public static void glfwSwapBuffers(long window) { swapBuffersNative(checkedHandle(window)); }
    public static void glfwSwapInterval(int interval) { swapIntervalNative(interval); }
    public static void glfwSetWindowTitle(long window,java.lang.CharSequence title) { titleNative(checkedHandle(window),String.valueOf(title)); }
    public static void glfwSetWindowTitle(long window,ByteBuffer title) { titleNative(checkedHandle(window),NativeMemory.memUTF8(NativeMemory.memAddress0(title))); }
    public static void glfwSetWindowPos(long window,int x,int y) { setPosNative(checkedHandle(window),x,y); }
    public static void glfwGetWindowPos(long window,int[] x,int[] y) { x[0]=windowMetric(4,checkedHandle(window)); y[0]=windowMetric(5,checkedHandle(window)); }
    public static void glfwGetWindowPos(long window,IntBuffer x,IntBuffer y) { x.put(0,windowMetric(4,checkedHandle(window))); y.put(0,windowMetric(5,checkedHandle(window))); }
    public static void glfwSetWindowSize(long window,int width,int height) { setSizeNative(checkedHandle(window),width,height); }
    public static void glfwGetWindowSize(long window,int[] width,int[] height) { width[0]=windowMetric(0,checkedHandle(window)); height[0]=windowMetric(1,checkedHandle(window)); }
    public static void glfwGetWindowSize(long window,IntBuffer width,IntBuffer height) { width.put(0,windowMetric(0,checkedHandle(window))); height.put(0,windowMetric(1,checkedHandle(window))); }
    public static void glfwGetFramebufferSize(long window,int[] width,int[] height) { width[0]=windowMetric(2,checkedHandle(window)); height[0]=windowMetric(3,checkedHandle(window)); }
    public static void glfwGetFramebufferSize(long window,IntBuffer width,IntBuffer height) { width.put(0,windowMetric(2,checkedHandle(window))); height.put(0,windowMetric(3,checkedHandle(window))); }
    public static void glfwGetWindowFrameSize(long window,int[] left,int[] top,int[] right,int[] bottom) { frame(checkedHandle(window),left,top,right,bottom); }
    public static void glfwGetWindowFrameSize(long window,IntBuffer left,IntBuffer top,IntBuffer right,IntBuffer bottom) {
        int handle=checkedHandle(window);
        // LWJGL's own wrapper accepts null out-parameters (Checks.checkSafe + memAddressSafe) and
        // Display.calcWindowPos really passes null for right and bottom, so skip those cells
        // instead of dereferencing them.
        if (left != null) left.put(0,frameMetric(0,handle));
        if (top != null) top.put(0,frameMetric(1,handle));
        if (right != null) right.put(0,frameMetric(2,handle));
        if (bottom != null) bottom.put(0,frameMetric(3,handle));
    }
    public static void glfwGetWindowContentScale(long window,float[] x,float[] y) { float scale=contentScaleNative(checkedHandle(window)); x[0]=scale; y[0]=scale; }
    public static void glfwGetWindowContentScale(long window,FloatBuffer x,FloatBuffer y) { float scale=contentScaleNative(checkedHandle(window)); x.put(0,scale); y.put(0,scale); }
    public static void glfwGetWindowContentScale(long window,DoubleBuffer x,DoubleBuffer y) { double scale=contentScaleNative(checkedHandle(window)); x.put(0,scale); y.put(0,scale); }
    public static int glfwGetWindowAttrib(long window,int attrib) { return attribNative(checkedHandle(window),attrib); }
    public static void glfwSetWindowAttrib(long window,int attrib,int value) { setAttribNative(checkedHandle(window),attrib,value); }
    public static int glfwGetInputMode(long window,int mode) { return inputModeNative(checkedHandle(window),mode); }
    public static void glfwSetInputMode(long window,int mode,int value) { setInputModeNative(checkedHandle(window),mode,value); }
    public static void glfwSetCursorPos(long window,double x,double y) { setCursorPosNative(checkedHandle(window),x,y); }
    public static void glfwGetCursorPos(long window,double[] x,double[] y) { x[0]=cursorMetric(0,checkedHandle(window)); y[0]=cursorMetric(1,checkedHandle(window)); }
    public static void glfwGetCursorPos(long window,DoubleBuffer x,DoubleBuffer y) { int handle=checkedHandle(window); x.put(0,cursorMetric(0,handle)); y.put(0,cursorMetric(1,handle)); }
    public static double glfwGetTime() { return timeNative(); }
    public static int glfwGetPlatform() { return platformNative(); }
    public static void glfwDefaultWindowHints() { defaultHintsNative(); }
    public static void glfwWindowHint(int name,int value) { hintNative(name,value); }
    public static long glfwGetWindowMonitor(long window) { checkedHandle(window); return 0L; } // windowed: this backend has no fullscreen
    /** Record which callback instance receives each event kind; kind matches BrowserCallbacks. */
    public static void registerCallback(int kind,long handle) { registerCallbackNative(kind,(int)handle); }
    private static void frame(int handle,int[] left,int[] top,int[] right,int[] bottom) {
        left[0]=frameMetric(0,handle); top[0]=frameMetric(1,handle); right[0]=frameMetric(2,handle); bottom[0]=frameMetric(3,handle);
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
    @JSBody(params={"width","height","monitor","title"},script="return globalThis.pzRuntime.window.create(width,height,monitor,title);") private static native int createNative(int width,int height,int monitor,String title);
    @JSBody(params="window",script="globalThis.pzRuntime.window.destroy(window);") private static native void destroyNative(int window);
    @JSBody(params="window",script="return globalThis.pzRuntime.window.shouldClose(window);") private static native int shouldCloseNative(int window);
    @JSBody(params={"window","value"},script="globalThis.pzRuntime.window.setShouldClose(window,value===1);") private static native void setShouldCloseNative(int window,int value);
    @JSBody(params="window",script="globalThis.pzRuntime.window.show(window);") private static native void showNative(int window);
    @JSBody(params="window",script="globalThis.pzRuntime.window.hide(window);") private static native void hideNative(int window);
    @JSBody(params="window",script="globalThis.pzRuntime.window.focus(window);") private static native void focusNative(int window);
    @JSBody(params="window",script="globalThis.pzRuntime.window.makeContextCurrent(window);") private static native void makeContextCurrentNative(int window);
    @JSBody(script="return globalThis.pzRuntime.window.getCurrentContext();") private static native int currentContextNative();
    @JSBody(params="window",script="globalThis.pzRuntime.window.swapBuffers(window);") private static native void swapBuffersNative(int window);
    @JSBody(params="interval",script="globalThis.pzRuntime.window.swapInterval(interval);") private static native void swapIntervalNative(int interval);
    @JSBody(params={"window","title"},script="globalThis.pzRuntime.window.title(window,title);") private static native void titleNative(int window,String title);
    @JSBody(params={"window","x","y"},script="globalThis.pzRuntime.window.setWindowPos(window,x,y);") private static native void setPosNative(int window,int x,int y);
    @JSBody(params={"window","width","height"},script="globalThis.pzRuntime.window.setWindowSize(window,width,height);") private static native void setSizeNative(int window,int width,int height);
    @JSBody(params={"kind","window"},script="var w=globalThis.pzRuntime.window;switch(kind){case 0:return w.windowWidth(window);case 1:return w.windowHeight(window);case 2:return w.framebufferWidth(window);case 3:return w.framebufferHeight(window);case 4:return w.posX(window);default:return w.posY(window);}") private static native int windowMetric(int kind,int window);
    @JSBody(params={"kind","window"},script="return globalThis.pzRuntime.window.frameMetric(window,kind);") private static native int frameMetric(int kind,int window);
    @JSBody(params={"kind","window"},script="return globalThis.pzRuntime.window.cursorMetric(window,kind);") private static native double cursorMetric(int kind,int window);
    @JSBody(params="window",script="return globalThis.pzRuntime.window.contentScale(window);") private static native float contentScaleNative(int window);
    @JSBody(params={"window","attrib"},script="return globalThis.pzRuntime.window.attrib(window,attrib);") private static native int attribNative(int window,int attrib);
    @JSBody(params={"window","attrib","value"},script="globalThis.pzRuntime.window.setAttrib(window,attrib,value);") private static native void setAttribNative(int window,int attrib,int value);
    @JSBody(params={"window","mode"},script="return globalThis.pzRuntime.window.inputModeValue(window,mode);") private static native int inputModeNative(int window,int mode);
    @JSBody(params={"window","mode","value"},script="globalThis.pzRuntime.window.setInputMode(window,mode,value);") private static native void setInputModeNative(int window,int mode,int value);
    @JSBody(params={"window","x","y"},script="globalThis.pzRuntime.window.setCursorPos(window,x,y);") private static native void setCursorPosNative(int window,double x,double y);
    @JSBody(script="return globalThis.pzRuntime.window.getTime();") private static native double timeNative();
    @JSBody(script="return globalThis.pzRuntime.window.getPlatform();") private static native int platformNative();
    @JSBody(script="globalThis.pzRuntime.window.defaultHints();") private static native void defaultHintsNative();
    @JSBody(params={"name","value"},script="globalThis.pzRuntime.window.hint(name,value);") private static native void hintNative(int name,int value);
    @JSBody(params={"kind","handle"},script="globalThis.pzRuntime.window.registerCallback(kind,handle);") private static native void registerCallbackNative(int kind,int handle);
}
