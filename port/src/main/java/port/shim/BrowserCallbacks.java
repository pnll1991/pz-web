package port.shim;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import org.teavm.jso.JSBody;

/**
 * Browser replacement for LWJGL's libffi upcalls. A wasm module cannot build a native closure,
 * so a callback instance is registered here and identified by an integer handle owned by the
 * runtime (window.mjs keeps the instance). glfwPollEvents drains queued DOM events and dispatches
 * them through the original {@code callback(long,long)} body, which reads its arguments from an
 * arena block exactly as the native ABI passes them (one pointer-sized slot per argument).
 *
 * A missing callback or an unknown event never dispatches: dispatchNext returns false and the
 * event stays queued rather than pretending it was delivered.
 */
public final class BrowserCallbacks {
    private static final Map<Long, org.lwjgl.system.CallbackI> byAddress = new HashMap<>();
    private static final Map<org.lwjgl.system.CallbackI, Long> byInstance = new IdentityHashMap<>();
    private static int dispatched;
    private BrowserCallbacks() { }

    /** Register a callback instance under the handle the runtime returned for it. */
    public static long register(long address, org.lwjgl.system.CallbackI callback) {
        if (address == 0) throw new IllegalStateException("Browser callback handle must not be 0");
        byAddress.put(address, callback);
        byInstance.put(callback, address);
        return address;
    }

    /** The handle of a previously registered instance, or 0 when it was never registered. */
    public static long addressOf(org.lwjgl.system.CallbackI callback) {
        Long address = callback == null ? null : byInstance.get(callback);
        return address == null ? 0L : address;
    }

    public static void forget(long address) {
        org.lwjgl.system.CallbackI callback = byAddress.remove(address);
        if (callback != null) byInstance.remove(callback);
    }

    public static org.lwjgl.system.CallbackI get(long address) { return byAddress.get(address); }

    /** Ask the runtime for one queued event and dispatch it. Returns false when nothing is queued. */
    public static boolean dispatchNext() {
        String event = pollNative();
        if (event == null) return false;
        String[] fields = event.split("\t");
        int kind = Integer.parseInt(fields[0]);
        long handle = Long.parseLong(fields[1]);
        long address = BrowserWindow.callbackId(kind);
        org.lwjgl.system.CallbackI callback = byAddress.get(address);
        if (callback == null) return false;
        int slots = switch (kind) {
            case 0 -> 4; // key: window, key, scancode, mods
            case 1 -> 3; // mouse button: window, button, action, mods
            case 2 -> 2; // cursor position: xox, yoy
            case 3 -> 2; // scroll: xox, yoy
            case 4 -> 1; // character codepoint
            default -> throw new IllegalStateException("Unknown browser input event kind: " + kind);
        };
        long arguments = port.runtime.memory.NativeMemory.nmemAllocChecked((long) slots * 8);
        port.runtime.memory.NativeMemory.memPutAddress(arguments, handle);
        switch (kind) {
            case 0 -> {
                port.runtime.memory.NativeMemory.memPutAddress(arguments + 8, Long.parseLong(fields[2]));
                port.runtime.memory.NativeMemory.memPutAddress(arguments + 16, Long.parseLong(fields[3]));
                port.runtime.memory.NativeMemory.memPutAddress(arguments + 24, Long.parseLong(fields[4]));
            }
            case 1 -> {
                port.runtime.memory.NativeMemory.memPutAddress(arguments + 8, Long.parseLong(fields[2]));
                port.runtime.memory.NativeMemory.memPutAddress(arguments + 16, Long.parseLong(fields[3]));
            }
            case 2 -> {
                port.runtime.memory.NativeMemory.memPutDouble(arguments + 8, Double.parseDouble(fields[2]));
                port.runtime.memory.NativeMemory.memPutDouble(arguments + 16, Double.parseDouble(fields[3]));
            }
            case 3 -> {
                port.runtime.memory.NativeMemory.memPutDouble(arguments + 8, Double.parseDouble(fields[2]));
                port.runtime.memory.NativeMemory.memPutDouble(arguments + 16, Double.parseDouble(fields[3]));
            }
            default -> port.runtime.memory.NativeMemory.memPutAddress(arguments + 8, fields[2].codePointAt(0));
        }
        // The original interface body reads the slots and calls invoke(...) on the instance.
        callback.callback(arguments, 0);
        dispatched++;
        return true;
    }

    public static int dispatchedCount() { return dispatched; }

    @JSBody(script="return globalThis.pzRuntime.window.poll();") private static native String pollNative();
}
