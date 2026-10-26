package port.shim;

import org.teavm.jso.JSBody;

/** Wrappers for the browser window system, independent of native OS detection. */
public final class BrowserWindow {
    private BrowserWindow() { }
    public static void init() { initNative(); }
    public static int desktopWidth() { return metric(0); }
    public static int desktopHeight() { return metric(1); }
    public static int colorBits() { return metric(2); }
    public static int glfwGetKeyScancode(int key) { return scancode(key); }
    public static String glfwGetKeyName(int key,int code) { return keyName(key,code); }
    public static int glfwGetKey(long window,int key) { return key(window,key); }
    public static int glfwGetMouseButton(long window,int button) { return button(window,button); }
    @JSBody(script="globalThis.pzRuntime.window.init();") private static native void initNative();
    @JSBody(params="kind",script="var w=globalThis.pzRuntime.window;return kind===0?w.desktopWidth():kind===1?w.desktopHeight():w.colorBits();") private static native int metric(int kind);
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
