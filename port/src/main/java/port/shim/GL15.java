package port.shim;

import java.nio.ByteBuffer;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Int32Array;
import org.teavm.jso.typedarrays.Uint8Array;
import port.runtime.memory.NativeMemory;

/**
 * Real WebGL2 wrappers for the inventoried GL15 natives (org.lwjgl.opengl.GL15C). WebGL2 owns
 * buffer/query storage; port/runtime/gl.mjs keeps handle -> WebGLBuffer/WebGLQuery, and this
 * class moves bytes between the native arena and that storage the way the desktop ABI does.
 * Descriptors match the natives exactly so tools/glgen/generate.py can map them.
 */
public final class GL15 {
    private GL15() { }

    /** Client bytes of an arena range as a live view of the block (same mechanism as BrowserStb). */
    private static Uint8Array arena(long address, int bytes) {
        ByteBuffer view = NativeMemory.memByteBuffer(address, bytes);
        return Uint8Array.fromJavaBuffer(view);
    }
    private static int checked(long bytes) {
        if (bytes < 0 || bytes > Integer.MAX_VALUE) throw new IllegalArgumentException("Buffer size: " + bytes);
        return (int) bytes;
    }

    public static void nglGenBuffers(int count, long buffers) {
        if (count <= 0) return;
        Int32Array handles = genManyNative(count);
        for (int i = 0; i < count; i++) NativeMemory.memPutInt(buffers + 4L * i, handles.get(i));
    }
    public static void nglDeleteBuffers(int count, long buffers) {
        if (count <= 0) return;
        Int32Array handles = Int32Array.create(count);
        for (int i = 0; i < count; i++) handles.set(i, NativeMemory.memGetInt(buffers + 4L * i));
        deleteBuffersNative(handles);
    }
    public static void glBindBuffer(int target, int buffer) { bindNative(target, buffer); }
    public static boolean glIsBuffer(int buffer) { return isBufferNative(buffer) != 0; }
    public static void nglGetBufferParameteriv(int target, int pname, long params) {
        Int32Array values = bufferParameterNative(target, pname);
        for (int i = 0; i < values.getLength(); i++) NativeMemory.memPutInt(params + 4L * i, values.get(i));
    }
    public static void nglBufferData(int target, long address, long size, int usage) {
        // The desktop usage hint has no WebGL2 equivalent; WebGL2 owns the storage.
        int bytes = checked(size);
        bufferDataNative(target, bytes, address == 0L ? null : arena(address, bytes));
    }
    public static void nglBufferSubData(int target, long offset, long address, long size) {
        bufferSubNative(target, offset, arena(address, checked(size)));
    }
    public static void nglGetBufferSubData(int target, long offset, long address, long size) {
        int bytes = checked(size);
        ByteBuffer view = NativeMemory.memByteBuffer(address, bytes);
        getSubNative(target, offset, Uint8Array.fromJavaBuffer(view));
    }
    public static long nglMapBuffer(int target, int access) {
        // WebGL2 has no client pointer for a buffer range: mapping cannot be honoured.
        throw new UnsupportedOperationException(
            "WebGL2 cannot map a buffer range: org.lwjgl.opengl.GL15C#nglMapBuffer(II)J");
    }
    public static boolean glUnmapBuffer(int target) {
        throw new UnsupportedOperationException(
            "WebGL2 has no mapped buffer range: org.lwjgl.opengl.GL15C#glUnmapBuffer(I)Z");
    }

    // --- Occlusion queries (same handle model as desktop GL: 0 means "none") ---------------
    public static void nglGenQueries(int count, long queries) {
        if (count <= 0) return;
        Int32Array handles = genQueriesNative(count);
        for (int i = 0; i < count; i++) NativeMemory.memPutInt(queries + 4L * i, handles.get(i));
    }
    public static void nglDeleteQueries(int count, long queries) {
        if (count <= 0) return;
        Int32Array handles = Int32Array.create(count);
        for (int i = 0; i < count; i++) handles.set(i, NativeMemory.memGetInt(queries + 4L * i));
        deleteQueriesNative(handles);
    }
    public static boolean glIsQuery(int query) { return isQueryNative(query) != 0; }
    public static void glBeginQuery(int target, int query) { beginQueryNative(target, query); }
    public static void glEndQuery(int target) { endQueryNative(target); }
    public static void nglGetQueryiv(int target, int pname, long params) {
        Int32Array values = queryActiveNative(target, pname);
        for (int i = 0; i < values.getLength(); i++) NativeMemory.memPutInt(params + 4L * i, values.get(i));
    }
    public static void nglGetQueryObjectiv(int query, int pname, long params) {
        Int32Array values = queryResultNative(query, pname);
        for (int i = 0; i < values.getLength(); i++) NativeMemory.memPutInt(params + 4L * i, values.get(i));
    }
    public static void nglGetQueryObjectuiv(int query, int pname, long params) {
        nglGetQueryObjectiv(query, pname, params); // WebGL2's QUERY_RESULT is a boolean, so unsigned is identical
    }

    // --- JS bodies: each maps to a port/runtime/gl.mjs bridge method -----------------------
    @JSBody(params="count", script="return globalThis.pzRuntime.gl.genBuffers(count);")
    private static native Int32Array genManyNative(int count);
    @JSBody(params="handles", script="globalThis.pzRuntime.gl.deleteBuffers(handles);")
    private static native void deleteBuffersNative(Int32Array handles);
    @JSBody(params={"target","buffer"}, script="globalThis.pzRuntime.gl.bindBuffer(target,buffer);")
    private static native void bindNative(int target, int buffer);
    @JSBody(params="buffer", script="return globalThis.pzRuntime.gl.isBuffer(buffer) ? 1 : 0;")
    private static native int isBufferNative(int buffer);
    @JSBody(params={"target","size","data"}, script="globalThis.pzRuntime.gl.bufferData(target,size,data);")
    private static native void bufferDataNative(int target, int size, Uint8Array data);
    @JSBody(params={"target","offset","data"}, script="globalThis.pzRuntime.gl.bufferSubData(target,offset,data);")
    private static native void bufferSubNative(int target, long offset, Uint8Array data);
    @JSBody(params={"target","offset","data"}, script="globalThis.pzRuntime.gl.getBufferSubData(target,offset,data);")
    private static native void getSubNative(int target, long offset, Uint8Array data);
    @JSBody(params={"target","pname"}, script="return new Int32Array(globalThis.pzRuntime.gl.bufferParameter(target,pname));")
    private static native Int32Array bufferParameterNative(int target, int pname);
    @JSBody(params="count", script="return globalThis.pzRuntime.gl.genQueries(count);")
    private static native Int32Array genQueriesNative(int count);
    @JSBody(params="handles", script="globalThis.pzRuntime.gl.deleteQueries(handles);")
    private static native void deleteQueriesNative(Int32Array handles);
    @JSBody(params="query", script="return globalThis.pzRuntime.gl.isQuery(query) ? 1 : 0;")
    private static native int isQueryNative(int query);
    @JSBody(params={"target","query"}, script="globalThis.pzRuntime.gl.beginQuery(target,query);")
    private static native void beginQueryNative(int target, int query);
    @JSBody(params="target", script="globalThis.pzRuntime.gl.endQuery(target);")
    private static native void endQueryNative(int target);
    @JSBody(params={"target","pname"}, script="return new Int32Array(globalThis.pzRuntime.gl.queryActive(target,pname));")
    private static native Int32Array queryActiveNative(int target, int pname);
    @JSBody(params={"query","pname"}, script="return new Int32Array(globalThis.pzRuntime.gl.queryResult(query,pname));")
    private static native Int32Array queryResultNative(int query, int pname);
}
