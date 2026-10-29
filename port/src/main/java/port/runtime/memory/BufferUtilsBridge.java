package port.runtime.memory;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

/**
 * Browser equivalents of {@code org.lwjglx.BufferUtils} (the LWJGL2 helper the game and its mods
 * use, e.g. {@code ShaderProgram.<clinit>} builds its static {@code floatBuffer} with
 * {@code BufferUtils.createFloatBuffer(16)}). Every allocation here goes through {@link NativeMemory},
 * so the returned buffer is a registered arena view and {@code memAddress} works on it; the desktop
 * helper's {@code ByteBuffer.allocateDirect(...).asFloatBuffer()} produced an unregistered view and
 * made {@code GL20.glUniformMatrix4fv} raise "Arena address requires a registered/direct ByteBuffer view".
 * Sizes keep the desktop semantics: createXBuffer(count) yields a buffer of `count` elements.
 */
public final class BufferUtilsBridge {
    private BufferUtilsBridge() { }

    public static ByteBuffer createByteBuffer(int bytes) { return NativeMemory.memAlloc(bytes); }
    public static ShortBuffer createShortBuffer(int count) { return NativeMemory.memAllocShort(count); }
    public static CharBuffer createCharBuffer(int count) { return NativeMemory.memAllocChar(count); }
    public static IntBuffer createIntBuffer(int count) { return NativeMemory.memAllocInt(count); }
    public static LongBuffer createLongBuffer(int count) { return NativeMemory.memAllocLong(count); }
    public static FloatBuffer createFloatBuffer(int count) { return NativeMemory.memAllocFloat(count); }
    public static DoubleBuffer createDoubleBuffer(int count) { return NativeMemory.memAllocDouble(count); }

    /** Desktop semantics: address of the element at {@code position} inside the arena block. */
    public static long getBufferAddress(Buffer buffer) { return NativeMemory.memAddress(buffer); }

    /** Same exponent table as {@code org.lwjglx.BufferUtils.getElementSizeExponent}. */
    public static int getElementSizeExponent(Buffer buffer) {
        if (buffer instanceof ByteBuffer) return 0;
        if (buffer instanceof ShortBuffer || buffer instanceof CharBuffer) return 1;
        if (buffer instanceof FloatBuffer || buffer instanceof IntBuffer) return 2;
        if (buffer instanceof LongBuffer || buffer instanceof DoubleBuffer) return 3;
        throw new IllegalStateException("Unknown buffer type: " + buffer);
    }

    public static int getOffset(Buffer buffer) { return buffer.position() << getElementSizeExponent(buffer); }

    public static void zeroBuffer(ByteBuffer buffer) { NativeMemory.memSet(buffer, 0); }
    public static void zeroBuffer(ShortBuffer buffer) { NativeMemory.memSet(buffer, 0); }
    public static void zeroBuffer(CharBuffer buffer) { NativeMemory.memSet(buffer, 0); }
    public static void zeroBuffer(IntBuffer buffer) { NativeMemory.memSet(buffer, 0); }
    public static void zeroBuffer(FloatBuffer buffer) { NativeMemory.memSet(buffer, 0); }
    public static void zeroBuffer(LongBuffer buffer) { NativeMemory.memSet(buffer, 0); }
    public static void zeroBuffer(DoubleBuffer buffer) { NativeMemory.memSet(buffer, 0); }
}
