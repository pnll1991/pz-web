package port.shim;

import java.nio.ByteBuffer;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Float32Array;
import org.teavm.jso.typedarrays.Int32Array;
import org.teavm.jso.typedarrays.Uint8Array;
import port.runtime.memory.NativeMemory;

/**
 * Real WebGL2 wrappers for the inventoried GL20C natives (shaders, programs, uniforms, vertex
 * attributes). port/runtime/gl.mjs owns the WebGL2 objects and the GL handle table; this class
 * moves bytes between the native arena and that storage the way the desktop ABI does, and
 * translates nothing silently: desktop GLSL goes through port/runtime/glsl.mjs in the bridge.
 * Descriptors match org.lwjgl.opengl.GL20C exactly so tools/glgen/generate.py can map them.
 */
public final class GL20 {
    private GL20() { }

    private static int checked(long bytes) {
        if (bytes < 0 || bytes > Integer.MAX_VALUE) throw new IllegalArgumentException("Size: " + bytes);
        return (int) bytes;
    }

    /* --- Shaders --------------------------------------------------------------------- */
    public static int glCreateShader(int type) { return createShaderNative(type); }
    public static void glDeleteShader(int shader) { deleteShaderNative(shader); }
    public static boolean glIsShader(int shader) { return isShaderNative(shader) != 0; }
    public static void glCompileShader(int shader) { compileShaderNative(shader); }

    /**
     * {@code nglShaderSource(shader,count,pointers,lengths)}: the desktop call reads `count`
     * (pointer,length) pairs from the arena and the driver concatenates them. The blocks live in
     * different arena allocations, so this copies them into one fresh contiguous buffer (byte-wise,
     * through the checked arena accessors) and hands that view plus the lengths to the bridge,
     * which decodes UTF-8 and translates the GLSL (glsl.mjs).
     */
    public static void nglShaderSource(int shader, int count, long pointers, long lengths) {
        if (count < 0) throw new IllegalArgumentException("Shader source count: " + count);
        int[] parts = new int[count];
        long total = 0L;
        for (int i = 0; i < count; i++) {
            int length = NativeMemory.memGetInt(lengths + 4L * i);
            parts[i] = length;
            total = Math.addExact(total, length);
        }
        ByteBuffer joined = NativeMemory.memAlloc(checked(total));
        for (int i = 0; i < count; i++) {
            long address = NativeMemory.memGetLong(pointers + 8L * i);
            for (int k = 0; k < parts[i]; k++) joined.put((byte) NativeMemory.memGetByte(address + k));
        }
        joined.clear();
        Int32Array sizes = Int32Array.create(count);
        for (int i = 0; i < count; i++) sizes.set(i, parts[i]);
        shaderSourceNative(shader, sizes, Uint8Array.fromJavaBuffer(joined));
        NativeMemory.memFree(joined);
    }
    /* --- Programs -------------------------------------------------------------------- */
    public static int glCreateProgram() { return createProgramNative(); }
    public static void glDeleteProgram(int program) { deleteProgramNative(program); }
    public static boolean glIsProgram(int program) { return isProgramNative(program) != 0; }
    public static void glAttachShader(int program, int shader) { attachShaderNative(program, shader); }
    public static void glDetachShader(int program, int shader) { detachShaderNative(program, shader); }
    public static void glLinkProgram(int program) { linkProgramNative(program); }
    public static void glUseProgram(int program) { useProgramNative(program); }
    public static void glValidateProgram(int program) { validateProgramNative(program); }

    /* --- Parameters read back into the arena ---------------------------------------- */
    public static void nglGetShaderiv(int shader, int pname, long params) {
        Int32Array values = shaderParameterNative(shader, pname);
        for (int i = 0; i < values.getLength(); i++) NativeMemory.memPutInt(params + 4L * i, values.get(i));
    }
    public static void nglGetProgramiv(int program, int pname, long params) {
        Int32Array values = programParameterNative(program, pname);
        for (int i = 0; i < values.getLength(); i++) NativeMemory.memPutInt(params + 4L * i, values.get(i));
    }
    /**
     * {@code nglGetShaderInfoLog(shader,maxLength,sourceLength,source)}: writes at most maxLength
     * bytes of the real log into the caller's arena block and stores the written length (including
     * the terminating NUL, as the desktop driver does) at sourceLength when that pointer is given.
     */
    public static void nglGetShaderInfoLog(int shader, int maxLength, long sourceLength, long source) {
        writeLog(shaderInfoLogNative(shader), maxLength, sourceLength, source);
    }
    public static void nglGetProgramInfoLog(int program, int maxLength, long sourceLength, long source) {
        writeLog(programInfoLogNative(program), maxLength, sourceLength, source);
    }
    private static void writeLog(String log, int maxLength, long sourceLength, long source) {
        int bytes = Math.min(maxLength, log.length() + 1);
        for (int i = 0; i < bytes; i++) {
            char c = i < log.length() ? log.charAt(i) : '\0';
            if (c > 0x7f) throw new IllegalArgumentException("Non-ASCII character in GL info log");
            NativeMemory.memPutByte(source + i, (byte) c);
        }
        if (sourceLength != 0L) NativeMemory.memPutInt(sourceLength, bytes);
    }
    /* --- Uniform setters --------------------------------------------------------------
     * Scalar setters take values directly; the "v" natives take an arena pointer. WebGL2 needs
     * arrays, so the views come from the arena block (same mechanism as port.shim.GL15).
     */
    public static void glUniform1f(int location, float x) { call1fNative("uniform1f", location, x); }
    public static void glUniform2f(int location, float x, float y) { call2fNative("uniform2f", location, x, y); }
    public static void glUniform3f(int location, float x, float y, float z) {
        call3fNative("uniform3f", location, x, y, z);
    }
    public static void glUniform4f(int location, float x, float y, float z, float w) {
        call4fNative("uniform4f", location, x, y, z, w);
    }
    public static void glUniform1i(int location, int x) { call1iNative("uniform1i", location, x); }
    public static void glUniform2i(int location, int x, int y) { call2iNative("uniform2i", location, x, y); }
    public static void glUniform3i(int location, int x, int y, int z) {
        call3iNative("uniform3i", location, x, y, z);
    }
    public static void glUniform4i(int location, int x, int y, int z, int w) {
        call4iNative("uniform4i", location, x, y, z, w);
    }
    public static void nglUniform1fv(int location, int count, long values) {
        uniformFvNative("uniform1fv", location, count, floatView(values, count));
    }
    public static void nglUniform2fv(int location, int count, long values) {
        uniformFvNative("uniform2fv", location, count, floatView(values, count * 2));
    }
    public static void nglUniform3fv(int location, int count, long values) {
        uniformFvNative("uniform3fv", location, count, floatView(values, count * 3));
    }
    public static void nglUniform4fv(int location, int count, long values) {
        uniformFvNative("uniform4fv", location, count, floatView(values, count * 4));
    }
    public static void nglUniform1iv(int location, int count, long values) {
        uniformIvNative("uniform1iv", location, count, intView(values, count));
    }
    public static void nglUniform2iv(int location, int count, long values) {
        uniformIvNative("uniform2iv", location, count, intView(values, count * 2));
    }
    public static void nglUniform3iv(int location, int count, long values) {
        uniformIvNative("uniform3iv", location, count, intView(values, count * 3));
    }
    public static void nglUniform4iv(int location, int count, long values) {
        uniformIvNative("uniform4iv", location, count, intView(values, count * 4));
    }
    public static void nglUniformMatrix2fv(int location, int count, boolean transpose, long values) {
        uniformMatrixNative(2, location, count, transpose, floatView(values, count * 4));
    }
    public static void nglUniformMatrix3fv(int location, int count, boolean transpose, long values) {
        uniformMatrixNative(3, location, count, transpose, floatView(values, count * 9));
    }
    public static void nglUniformMatrix4fv(int location, int count, boolean transpose, long values) {
        uniformMatrixNative(4, location, count, transpose, floatView(values, count * 16));
    }
    /* --- Locations -------------------------------------------------------------------- */
    public static int nglGetUniformLocation(int program, long name) {
        return uniformLocationNative(program, NativeMemory.memASCII(name));
    }
    public static int nglGetAttribLocation(int program, long name) {
        return attribLocationNative(program, NativeMemory.memASCII(name));
    }

    /* --- Vertex attributes -------------------------------------------------------------
     * The desktop native's last argument is an offset into the bound ARRAY_BUFFER, which is
     * exactly what WebGL2 expects; gl.mjs rejects an offset that is not stride-aligned.
     */
    public static void nglVertexAttribPointer(int index, int size, int type, boolean normalized,
                                              int stride, long pointer) {
        // The native's last argument is an offset into the bound ARRAY_BUFFER. TeaVM hands a Java long
        // to a JSBody as a BigInt, which WebGL2 rejects with "Cannot convert a BigInt value to a
        // number", so the offset crosses the bridge as an int and is checked rather than truncated.
        attribPointerNative(index, size, type, normalized, stride, checked(pointer));
    }
    public static void glEnableVertexAttribArray(int index) { enableAttribNative(index); }
    public static void glDisableVertexAttribArray(int index) { disableAttribNative(index); }
    public static void glVertexAttrib1f(int index, float x) { attrib1fNative(index, x); }
    public static void glVertexAttrib2f(int index, float x, float y) { attrib2fNative(index, x, y); }
    public static void glVertexAttrib3f(int index, float x, float y, float z) {
        attrib3fNative(index, x, y, z);
    }
    public static void glVertexAttrib4f(int index, float x, float y, float z, float w) {
        attrib4fNative(index, x, y, z, w);
    }

    /* --- Unsupported desktop-only natives (honest raise, no simulation) --------------
     * WebGL2 has no getter for uniform values, no query of an attribute's value/pointer and no
     * way to bind an attribute name before linking. Those raise with their own identity.
     */
    public static void nglGetUniformfv(int program, int location, long params) {
        throw new UnsupportedOperationException(
            "WebGL2 cannot read a program uniform value: org.lwjgl.opengl.GL20C#nglGetUniformfv(IIJ)V");
    }
    public static void nglGetUniformiv(int program, int location, long params) {
        throw new UnsupportedOperationException(
            "WebGL2 cannot read a program uniform value: org.lwjgl.opengl.GL20C#nglGetUniformiv(IIJ)V");
    }
    public static void nglGetVertexAttribiv(int index, int pname, long params) {
        throw new UnsupportedOperationException(
            "WebGL2 cannot query a vertex attribute: org.lwjgl.opengl.GL20C#nglGetVertexAttribiv(IIJ)V");
    }
    public static void nglGetVertexAttribfv(int index, int pname, long params) {
        throw new UnsupportedOperationException(
            "WebGL2 cannot query a vertex attribute: org.lwjgl.opengl.GL20C#nglGetVertexAttribfv(IIJ)V");
    }
    public static void nglGetVertexAttribdv(int index, int pname, long params) {
        throw new UnsupportedOperationException(
            "WebGL2 cannot query a vertex attribute: org.lwjgl.opengl.GL20C#nglGetVertexAttribdv(IIJ)V");
    }
    public static void nglGetVertexAttribPointerv(int index, int pname, long params) {
        throw new UnsupportedOperationException(
            "WebGL2 has no vertex attribute pointer query: org.lwjgl.opengl.GL20C#nglGetVertexAttribPointerv(IIJ)V");
    }
    /* --- Introspection WebGL2 does expose ---------------------------------------------
     * glBindAttribLocation, glGetAttachedShaders, glGetActiveUniform/Attrib and glGetShaderSource
     * exist in WebGL2 (verified in Chrome: all are functions on the WebGL2RenderingContext). They
     * are implemented over the real context through the bridge, so nothing is invented.
     */
    public static void nglBindAttribLocation(int program, int index, long name) {
        bindAttribLocationNative(program, index, NativeMemory.memASCII(name));
    }
    public static void nglGetAttachedShaders(int program, int maxCount, long count, long shaders) {
        Int32Array handles = attachedShadersNative(program, Math.max(0, maxCount));
        for (int i = 0; i < handles.getLength(); i++) NativeMemory.memPutInt(shaders + 4L * i, handles.get(i));
        if (count != 0L) NativeMemory.memPutInt(count, handles.getLength());
    }
    public static void nglGetActiveUniform(int program, int index, int maxLength, long length,
                                           long size, long type, long name) {
        writeActiveInfo(program, index, 1, maxLength, length, size, type, name);
    }
    public static void nglGetActiveAttrib(int program, int index, int maxLength, long length,
                                          long size, long type, long name) {
        writeActiveInfo(program, index, 0, maxLength, length, size, type, name);
    }
    /**
     * Desktop layout: at most maxLength characters plus a NUL go into `name`, and `length` (when
     * given) receives the character count written without the terminator. size/type come from
     * WebGL2's own getActiveUniform/getActiveAttrib, whose type tokens are the GL ones.
     */
    private static void writeActiveInfo(int program, int index, int kind, int maxLength,
                                        long length, long size, long type, long name) {
        String text = activeNameNative(program, index, kind);
        Int32Array meta = activeSizeTypeNative(program, index, kind);
        int chars = Math.min(text.length(), Math.max(0, maxLength - 1));
        for (int i = 0; i < chars; i++) NativeMemory.memPutByte(name + i, (byte) text.charAt(i));
        if (maxLength > 0) NativeMemory.memPutByte(name + chars, (byte) 0);
        if (length != 0L) NativeMemory.memPutInt(length, chars);
        if (size != 0L) NativeMemory.memPutInt(size, meta.get(0));
        if (type != 0L) NativeMemory.memPutInt(type, meta.get(1));
    }
    /** {@code nglGetShaderSource}: the source this bridge actually handed WebGL2 (desktop GLSL after
     * the glsl.mjs translation), written like the desktop call with a terminating NUL. */
    public static void nglGetShaderSource(int shader, int maxLength, long sourceLength, long source) {
        String text = shaderSourceTextNative(shader);
        int chars = Math.min(text.length(), Math.max(0, maxLength - 1));
        for (int i = 0; i < chars; i++) NativeMemory.memPutByte(source + i, (byte) text.charAt(i));
        if (maxLength > 0) NativeMemory.memPutByte(source + chars, (byte) 0);
        if (sourceLength != 0L) NativeMemory.memPutInt(sourceLength, chars);
    }

    /* --- Arena views ------------------------------------------------------------------
     * A view over the caller's block. The block must already be a live allocation, so the
     * bounds are checked through NativeMemory.memByteBuffer (checked addresses, no GC objects).
     */
    private static Float32Array floatView(long address, int floats) {
        return Float32Array.fromJavaBuffer(NativeMemory.memByteBuffer(address, checked(Math.multiplyExact((long) floats, 4L))));
    }
    private static Int32Array intView(long address, int ints) {
        return Int32Array.fromJavaBuffer(NativeMemory.memByteBuffer(address, checked(Math.multiplyExact((long) ints, 4L))));
    }

    @JSBody(params="type", script="return globalThis.pzRuntime.gl.createShader(type);")
    private static native int createShaderNative(int type);
    @JSBody(params="shader", script="globalThis.pzRuntime.gl.deleteShader(shader);")
    private static native void deleteShaderNative(int shader);
    @JSBody(params="shader", script="return globalThis.pzRuntime.gl.isShader(shader) ? 1 : 0;")
    private static native int isShaderNative(int shader);
    @JSBody(params="shader", script="globalThis.pzRuntime.gl.compileShader(shader);")
    private static native void compileShaderNative(int shader);
    @JSBody(params={"shader","lengths","bytes"}, script="globalThis.pzRuntime.gl.shaderSourceJoined(shader,lengths,bytes);")
    private static native void shaderSourceNative(int shader, Int32Array lengths, Uint8Array bytes);

    @JSBody(script="return globalThis.pzRuntime.gl.createProgram();")
    private static native int createProgramNative();
    @JSBody(params="program", script="globalThis.pzRuntime.gl.deleteProgram(program);")
    private static native void deleteProgramNative(int program);
    @JSBody(params="program", script="return globalThis.pzRuntime.gl.isProgram(program) ? 1 : 0;")
    private static native int isProgramNative(int program);
    @JSBody(params={"program","shader"}, script="globalThis.pzRuntime.gl.attachShader(program,shader);")
    private static native void attachShaderNative(int program, int shader);
    @JSBody(params={"program","shader"}, script="globalThis.pzRuntime.gl.detachShader(program,shader);")
    private static native void detachShaderNative(int program, int shader);
    @JSBody(params="program", script="globalThis.pzRuntime.gl.linkProgram(program);")
    private static native void linkProgramNative(int program);
    @JSBody(params="program", script="globalThis.pzRuntime.gl.useProgram(program);")
    private static native void useProgramNative(int program);
    @JSBody(params="program", script="globalThis.pzRuntime.gl.validateProgram(program);")
    private static native void validateProgramNative(int program);

    @JSBody(params={"shader","pname"}, script="return new Int32Array(globalThis.pzRuntime.gl.shaderParameter(shader,pname));")
    private static native Int32Array shaderParameterNative(int shader, int pname);
    @JSBody(params={"program","pname"}, script="return new Int32Array(globalThis.pzRuntime.gl.programParameter(program,pname));")
    private static native Int32Array programParameterNative(int program, int pname);
    @JSBody(params="shader", script="return globalThis.pzRuntime.gl.shaderInfoLog(shader);")
    private static native String shaderInfoLogNative(int shader);
    @JSBody(params="program", script="return globalThis.pzRuntime.gl.programInfoLog(program);")
    private static native String programInfoLogNative(int program);

    @JSBody(params={"name","location","x"}, script="globalThis.pzRuntime.gl.call(name,globalThis.pzRuntime.gl.locationOf(location,name),x);")
    private static native void call1fNative(String name, int location, float x);
    @JSBody(params={"name","location","x","y"}, script="globalThis.pzRuntime.gl.call(name,globalThis.pzRuntime.gl.locationOf(location,name),x,y);")
    private static native void call2fNative(String name, int location, float x, float y);
    @JSBody(params={"name","location","x","y","z"}, script="globalThis.pzRuntime.gl.call(name,globalThis.pzRuntime.gl.locationOf(location,name),x,y,z);")
    private static native void call3fNative(String name, int location, float x, float y, float z);
    @JSBody(params={"name","location","x","y","z","w"}, script="globalThis.pzRuntime.gl.call(name,globalThis.pzRuntime.gl.locationOf(location,name),x,y,z,w);")
    private static native void call4fNative(String name, int location, float x, float y, float z, float w);
    @JSBody(params={"name","location","x"}, script="globalThis.pzRuntime.gl.call(name,globalThis.pzRuntime.gl.locationOf(location,name),x);")
    private static native void call1iNative(String name, int location, int x);
    @JSBody(params={"name","location","x","y"}, script="globalThis.pzRuntime.gl.call(name,globalThis.pzRuntime.gl.locationOf(location,name),x,y);")
    private static native void call2iNative(String name, int location, int x, int y);
    @JSBody(params={"name","location","x","y","z"}, script="globalThis.pzRuntime.gl.call(name,globalThis.pzRuntime.gl.locationOf(location,name),x,y,z);")
    private static native void call3iNative(String name, int location, int x, int y, int z);
    @JSBody(params={"name","location","x","y","z","w"}, script="globalThis.pzRuntime.gl.call(name,globalThis.pzRuntime.gl.locationOf(location,name),x,y,z,w);")
    private static native void call4iNative(String name, int location, int x, int y, int z, int w);
    @JSBody(params={"name","location","count","values"}, script="globalThis.pzRuntime.gl.uniformfv(name,location,count,values);")
    private static native void uniformFvNative(String name, int location, int count, Float32Array values);
    @JSBody(params={"name","location","count","values"}, script="globalThis.pzRuntime.gl.uniform1iv(name,location,count,values);")
    private static native void uniformIvNative(String name, int location, int count, Int32Array values);
    @JSBody(params={"size","location","count","transpose","values"}, script="globalThis.pzRuntime.gl.uniformMatrix(size,location,count,transpose?1:0,values);")
    private static native void uniformMatrixNative(int size, int location, int count, boolean transpose, Float32Array values);
    @JSBody(params={"program","name"}, script="return globalThis.pzRuntime.gl.uniformLocation(program,name);")
    private static native int uniformLocationNative(int program, String name);
    @JSBody(params={"program","name"}, script="return globalThis.pzRuntime.gl.attribLocation(program,name);")
    private static native int attribLocationNative(int program, String name);
    @JSBody(params={"program","index","name"}, script="globalThis.pzRuntime.gl.bindAttribLocation(program,index,name);")
    private static native void bindAttribLocationNative(int program, int index, String name);
    @JSBody(params={"program","maxCount"}, script="return new Int32Array(globalThis.pzRuntime.gl.attachedShaders(program,maxCount));")
    private static native Int32Array attachedShadersNative(int program, int maxCount);
    @JSBody(params={"program","index","kind"}, script="return globalThis.pzRuntime.gl.activeName(program,index,kind);")
    private static native String activeNameNative(int program, int index, int kind);
    @JSBody(params={"program","index","kind"}, script="return new Int32Array(globalThis.pzRuntime.gl.activeSizeType(program,index,kind));")
    private static native Int32Array activeSizeTypeNative(int program, int index, int kind);
    @JSBody(params="shader", script="return globalThis.pzRuntime.gl.shaderSourceText(shader);")
    private static native String shaderSourceTextNative(int shader);
    @JSBody(params={"index","size","type","normalized","stride","offset"}, script="globalThis.pzRuntime.gl.vertexAttribPointer(index,size,type,normalized?1:0,stride,offset);")
    private static native void attribPointerNative(int index, int size, int type, boolean normalized, int stride, int offset);
    @JSBody(params="index", script="globalThis.pzRuntime.gl.enableAttrib(index);")
    private static native void enableAttribNative(int index);
    @JSBody(params="index", script="globalThis.pzRuntime.gl.disableAttrib(index);")
    private static native void disableAttribNative(int index);
    @JSBody(params={"index","x"}, script="globalThis.pzRuntime.gl.call('vertexAttrib1f',index,x);")
    private static native void attrib1fNative(int index, float x);
    @JSBody(params={"index","x","y"}, script="globalThis.pzRuntime.gl.call('vertexAttrib2f',index,x,y);")
    private static native void attrib2fNative(int index, float x, float y);
    @JSBody(params={"index","x","y","z"}, script="globalThis.pzRuntime.gl.call('vertexAttrib3f',index,x,y,z);")
    private static native void attrib3fNative(int index, float x, float y, float z);
    @JSBody(params={"index","x","y","z","w"}, script="globalThis.pzRuntime.gl.call('vertexAttrib4f',index,x,y,z,w);")
    private static native void attrib4fNative(int index, float x, float y, float z, float w);
}
