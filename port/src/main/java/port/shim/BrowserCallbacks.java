package port.shim;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import org.teavm.jso.JSBody;

/**
 * Browser replacement for LWJGL's libffi upcalls. A wasm module cannot build a native closure, so
 * {@code Upcalls.upcallCreate} (rewritten in IR by WindowCompatibility) hands the callback instance
 * here and receives a backend-owned integer handle instead of a real function pointer.
 *
 * Dispatch happens in the IR body of {@code GLFW#glfwPollEvents()V}: it asks window.mjs for one
 * queued DOM event at a time, looks up the instance registered for that event kind, fills an arena
 * block with the same slot layout the native upcall uses (libffi hands the closure a {@code void**}
 * array: every slot k holds the address of a separate cell, and slot 0's cell holds the window
 * handle, which the interface body dereferences twice), and calls the untouched interface default
 * {@code CallbackI.callback(long,long)}, which then invokes the game's own lambda. This class never
 * imports {@code org.lwjgl} types: the module compile classpath has no LWJGL, and only IR inside a
 * transformer can reference those identities.
 *
 * Kinds match the serialization of window.mjs poll(): 0 key, 1 mouse button, 2 cursor, 3 scroll,
 * 4 character, 5 window position, 6 window size, 7 refresh, 8 framebuffer size, 9 iconify,
 * 10 error, 11 joystick. An event whose kind has no registered callback is dropped, exactly as
 * GLFW drops an event when no callback is set; nothing is simulated.
 */
public final class BrowserCallbacks {
    /** Same kind numbering as WindowCompatibility and window.mjs poll(). */
    public static final int KIND_JOYSTICK = 11;
    private static final Map<Long, Object> instances = new HashMap<>();
    private static final Map<Object, Long> handles = new IdentityHashMap<>();
    private static final Map<Integer, Long> handlers = new HashMap<>(); // event kind -> handle
    private static long nextHandle = 1L;
    private static int dispatched;
    private static int dropped;
    private BrowserCallbacks() { }

    /** A fresh backend handle for the instance libffi would have closed over. Never 0, never reused. */
    public static long newHandle(Object instance) {
        if (instance == null) throw new IllegalStateException("Browser callback instance must not be null");
        Long existing = handles.get(instance);
        if (existing != null) return existing;
        long handle = nextHandle++;
        instances.put(handle, instance);
        handles.put(instance, handle);
        return handle;
    }

    /** The instance registered under a handle, or null when the handle is unknown or freed. */
    public static Object instanceOf(long handle) {
        return handle == 0 ? null : instances.get(handle);
    }

    public static void forget(long handle) {
        Object instance = instances.remove(handle);
        if (instance != null) {
            handles.remove(instance);
            handlers.entrySet().removeIf(entry -> entry.getValue() == handle);
        }
    }

    /** Record which instance receives each DOM event kind, and mirror it in window.mjs for evidence. */
    public static void setHandler(int kind, Object instance) {
        long handle = instance == null ? 0L : newHandle(instance);
        BrowserWindow.registerCallback(kind, (int) handle);
        if (instance == null) handlers.remove(kind);
        else handlers.put(kind, handle);
    }

    public static long handlerOf(int kind) {
        Long handle = handlers.get(kind);
        return handle == null ? 0L : handle;
    }

    /** One queued DOM event from window.mjs, or null when the queue is empty. */
    public static String pollEvent() { return pollNative(); }

    public static int eventKind(String event) { return Integer.parseInt(event.split("\t", -1)[0]); }

    /** The instance registered for this kind, or null when GLFW would drop the event. */
    public static Object eventCallback(String event) {
        Object instance = instanceOf(handlerOf(eventKind(event)));
        if (instance == null) dropped++;
        return instance;
    }

    public static int eventInt(String event, int index) {
        String[] fields = event.split("\t", -1);
        if (index >= fields.length) throw new IllegalStateException("Browser input event is missing field " + index);
        return Integer.parseInt(fields[index]);
    }

    public static long eventLong(String event, int index) {
        String[] fields = event.split("\t", -1);
        if (index >= fields.length) throw new IllegalStateException("Browser input event is missing field " + index);
        return Long.parseLong(fields[index]);
    }

    public static double eventDouble(String event, int index) {
        String[] fields = event.split("\t", -1);
        if (index >= fields.length) throw new IllegalStateException("Browser input event is missing field " + index);
        return Double.parseDouble(fields[index]);
    }

    public static int eventCodePoint(String event, int index) {
        String[] fields = event.split("\t", -1);
        if (index >= fields.length || fields[index].isEmpty())
            throw new IllegalStateException("Browser input event is missing field " + index);
        return fields[index].codePointAt(0);
    }

    /**
     * Arena block laid out like the native upcall argument array: slot 0 points at a cell holding
     * the window handle (the interface body reads it through two memGetAddress calls), and every
     * argument k&gt;0 points at its own cell holding the value as int or double, exactly as the
     * {@code GLFWXxxCallbackI.callback} bytecode does: {@code memGetAddress(args + k*POINTER_SIZE)}
     * and then {@code memGetInt}/{@code memGetDouble} of that address. The arena never reuses
     * addresses, so the cells stay readable while the game body runs.
     */
    public static long eventArguments(String event) {
        int kind = eventKind(event);
        int slots = switch (kind) {
            case 0 -> 4; // key: key, scancode, action, mods
            case 1 -> 3; // mouse button: button, action, mods
            case 2 -> 2; // cursor position: xoffset, yoffset as double
            case 3 -> 2; // scroll: xoffset, yoffset as double
            case 4 -> 1; // character codepoint
            case 6, 8 -> 2; // window size / framebuffer size: width, height
            case KIND_JOYSTICK -> 2; // joystick: joystick index and connected/disconnected flag
            default -> throw new IllegalStateException("Unknown browser input event kind: " + kind);
        };
        long args = port.runtime.memory.NativeMemory.nmemAllocChecked((long) (slots + 1) * 8);
        if (kind == KIND_JOYSTICK) {
            // GLFWJoystickCallbackI.callback reads args[0] with a single memGetAddress and then
            // memGetInt, so slot 0 is the joystick index itself (not a window handle) and slot 1
            // is the connect/disconnect flag. Both are plain ints in their own cells. window.mjs
            // serializes kind 11 as: kind, slot, connected, so argument k is field k+1.
            for (int k = 0; k < slots; k++) {
                long cell = port.runtime.memory.NativeMemory.nmemAllocChecked(8);
                port.runtime.memory.NativeMemory.memPutInt(cell, eventInt(event, k + 1));
                port.runtime.memory.NativeMemory.memPutAddress(args + (long) k * 8, cell);
            }
            dispatched++;
            return args;
        }
        // Slot 0 is a pointer to the cell holding the window handle, which the interface body
        // reads through two memGetAddress calls.
        long handleCell = port.runtime.memory.NativeMemory.nmemAllocChecked(8);
        port.runtime.memory.NativeMemory.memPutAddress(handleCell, eventLong(event, 1));
        port.runtime.memory.NativeMemory.memPutAddress(args, handleCell);
        // Argument k lives at field k+1 of the serialized event and is read from its own cell.
        for (int k = 1; k <= slots; k++) {
            long cell = port.runtime.memory.NativeMemory.nmemAllocChecked(8);
            switch (kind) {
                case 0, 1, 6, 8 -> port.runtime.memory.NativeMemory.memPutInt(cell, eventInt(event, k + 1));
                case 2, 3 -> port.runtime.memory.NativeMemory.memPutDouble(cell, eventDouble(event, k + 1));
                default -> port.runtime.memory.NativeMemory.memPutInt(cell, eventCodePoint(event, 2));
            }
            port.runtime.memory.NativeMemory.memPutAddress(args + (long) k * 8, cell);
        }
        dispatched++;
        return args;
    }

    public static int dispatchedCount() { return dispatched; }

    public static int droppedCount() { return dropped; }

    @JSBody(script="return globalThis.pzRuntime.window.poll();") private static native String pollNative();
}
