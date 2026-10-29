package port.shim;

import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Int32Array;
import port.runtime.memory.NativeMemory;

/**
 * Real WebGL2 wrappers for the inventoried GL30 natives (org.lwjgl.opengl.GL30C): vertex array
 * objects, framebuffers and renderbuffers. Handles live in port/runtime/gl.mjs; bytes for the
 * gen/delete natives move through the native arena exactly as the desktop ABI does.
 * Descriptors match the natives so tools/glgen/generate.py can map them.
 */
public final class GL30 {
    private GL30() { }

    private static void writeHandles(long address, Int32Array handles) {
        for (int i = 0; i < handles.getLength(); i++) NativeMemory.memPutInt(address + 4L * i, handles.get(i));
    }
    private static Int32Array readHandles(int count, long address) {
        Int32Array handles = Int32Array.create(count);
        for (int i = 0; i < count; i++) handles.set(i, NativeMemory.memGetInt(address + 4L * i));
        return handles;
    }

    public static void nglGenVertexArrays(int count, long arrays) {
        if (count <= 0) return;
        writeHandles(arrays, genVertexArraysNative(count));
    }
    public static void nglDeleteVertexArrays(int count, long arrays) {
        if (count <= 0) return;
        deleteVertexArraysNative(readHandles(count, arrays));
    }
    public static boolean glIsVertexArray(int array) { return isVertexArrayNative(array) != 0; }
    public static void glBindVertexArray(int array) { bindVertexArrayNative(array); }

    public static void nglGenFramebuffers(int count, long framebuffers) {
        if (count <= 0) return;
        writeHandles(framebuffers, genFramebuffersNative(count));
    }
    public static void nglDeleteFramebuffers(int count, long framebuffers) {
        if (count <= 0) return;
        deleteFramebuffersNative(readHandles(count, framebuffers));
    }
    public static boolean glIsFramebuffer(int framebuffer) { return isFramebufferNative(framebuffer) != 0; }
    public static void glBindFramebuffer(int target, int framebuffer) { bindFramebufferNative(target, framebuffer); }
    public static int glCheckFramebufferStatus(int target) { return checkFramebufferStatusNative(target); }
    public static void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        framebufferTexture2DNative(target, attachment, textarget, texture, level);
    }
    public static void glFramebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer) {
        framebufferRenderbufferNative(target, attachment, renderbuffertarget, renderbuffer);
    }

    public static void nglGenRenderbuffers(int count, long renderbuffers) {
        if (count <= 0) return;
        writeHandles(renderbuffers, genRenderbuffersNative(count));
    }
    public static void nglDeleteRenderbuffers(int count, long renderbuffers) {
        if (count <= 0) return;
        deleteRenderbuffersNative(readHandles(count, renderbuffers));
    }
    public static boolean glIsRenderbuffer(int renderbuffer) { return isRenderbufferNative(renderbuffer) != 0; }
    public static void glBindRenderbuffer(int target, int renderbuffer) { bindRenderbufferNative(target, renderbuffer); }
    public static void glRenderbufferStorage(int target, int internalformat, int width, int height) {
        renderbufferStorageNative(target, internalformat, width, height);
    }

    // --- JS bodies: each maps to a port/runtime/gl.mjs bridge method -----------------------
    @JSBody(params="count", script="return globalThis.pzRuntime.gl.genVertexArrays(count);")
    private static native Int32Array genVertexArraysNative(int count);
    @JSBody(params="handles", script="globalThis.pzRuntime.gl.deleteVertexArrays(handles);")
    private static native void deleteVertexArraysNative(Int32Array handles);
    @JSBody(params="array", script="return globalThis.pzRuntime.gl.isVertexArray(array) ? 1 : 0;")
    private static native int isVertexArrayNative(int array);
    @JSBody(params="array", script="globalThis.pzRuntime.gl.bindVertexArray(array);")
    private static native void bindVertexArrayNative(int array);

    @JSBody(params="count", script="return globalThis.pzRuntime.gl.genFramebuffers(count);")
    private static native Int32Array genFramebuffersNative(int count);
    @JSBody(params="handles", script="globalThis.pzRuntime.gl.deleteFramebuffers(handles);")
    private static native void deleteFramebuffersNative(Int32Array handles);
    @JSBody(params="framebuffer", script="return globalThis.pzRuntime.gl.isFramebuffer(framebuffer) ? 1 : 0;")
    private static native int isFramebufferNative(int framebuffer);
    @JSBody(params={"target","framebuffer"}, script="globalThis.pzRuntime.gl.bindFramebuffer(target,framebuffer);")
    private static native void bindFramebufferNative(int target, int framebuffer);
    @JSBody(params="target", script="return globalThis.pzRuntime.gl.checkFramebufferStatus(target);")
    private static native int checkFramebufferStatusNative(int target);
    @JSBody(params={"target","attachment","textarget","texture","level"},
        script="globalThis.pzRuntime.gl.framebufferTexture2D(target,attachment,textarget,texture,level);")
    private static native void framebufferTexture2DNative(int target, int attachment, int textarget, int texture, int level);
    @JSBody(params={"target","attachment","renderbuffertarget","renderbuffer"},
        script="globalThis.pzRuntime.gl.framebufferRenderbuffer(target,attachment,renderbuffertarget,renderbuffer);")
    private static native void framebufferRenderbufferNative(int target, int attachment, int renderbuffertarget, int renderbuffer);

    @JSBody(params="count", script="return globalThis.pzRuntime.gl.genRenderbuffers(count);")
    private static native Int32Array genRenderbuffersNative(int count);
    @JSBody(params="handles", script="globalThis.pzRuntime.gl.deleteRenderbuffers(handles);")
    private static native void deleteRenderbuffersNative(Int32Array handles);
    @JSBody(params="renderbuffer", script="return globalThis.pzRuntime.gl.isRenderbuffer(renderbuffer) ? 1 : 0;")
    private static native int isRenderbufferNative(int renderbuffer);
    @JSBody(params={"target","renderbuffer"}, script="globalThis.pzRuntime.gl.bindRenderbuffer(target,renderbuffer);")
    private static native void bindRenderbufferNative(int target, int renderbuffer);
    @JSBody(params={"target","internalformat","width","height"},
        script="globalThis.pzRuntime.gl.renderbufferStorage(target,internalformat,width,height);")
    private static native void renderbufferStorageNative(int target, int internalformat, int width, int height);
}
