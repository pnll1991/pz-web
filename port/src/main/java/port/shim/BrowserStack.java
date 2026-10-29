package port.shim;

import org.teavm.jso.JSBody;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSString;

/**
 * Real Java frames of the TeaVM fiber that is running right now.
 *
 * TeaVM's Wasm GC {@code TThread.getStackTrace()} returns an empty array, so a {@code StackWalker}
 * built on it walks nothing and callers such as {@code zombie.ai.State$Param.<init>} receive a null
 * declaring class and trap inside {@code Class.isAssignableFrom(null)}. Chrome unwinds Wasm frames
 * into the V8 stack and {@code boot.wasm} carries a complete {@code name} custom section, so each
 * stack line already reads {@code at zombie.ai.State$Param::<init> (…:wasm-function[..]:0x..)}:
 * {@code port/runtime/frames.mjs} reads that stack synchronously. No Java exception is constructed,
 * so no fiber can suspend here, and an index without a name yields no frame instead of an invented
 * one. When the page exposes no bridge the result is empty and {@code StackWalker} says so.
 */
public final class BrowserStack {
    private BrowserStack() { }

    /** {@code com.Example::method}, innermost first; empty when the host provides no frames. */
    public static String[] frames() {
        JSArray<JSString> array = take();
        if (array == null) return new String[0];
        String[] frames = new String[array.getLength()];
        for (int i = 0; i < frames.length; i++) {
            JSString frame = array.get(i);
            frames[i] = frame == null ? "" : frame.stringValue();
        }
        return frames;
    }

    @JSBody(script = "return typeof globalThis.pzFrames === 'function' ? globalThis.pzFrames(64) : [];")
    private static native JSArray<JSString> take();
}
