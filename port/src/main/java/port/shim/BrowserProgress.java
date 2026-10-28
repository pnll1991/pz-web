package port.shim;
import org.teavm.jso.JSBody;
public final class BrowserProgress {
    // Round 31: on WasmGC a resumed fiber re-enters the instrumented body mid-way, so the String
    // constant the transformer injected can still be unmaterialized (null) there. Verified stack:
    // java.lang.String::length <- org.teavm.jso.impl.wasmgc.WasmGCJSRuntime::stringToJs <-
    // port.shim.BrowserProgress::record <- port.shim.BrowserYield::pause <- JdkSupport::yield <-
    // Thread::yield <- RenderThread::renderLoop, during Fiber::resume. A null String passed to a JS
    // conversion crashes the module (RuntimeError: dereferencing a null pointer), so a boundary whose
    // label is not materialized is simply skipped. Do NOT substitute a constant here: a new String
    // constant is deferred exactly the same way and crashed again (build round31f).
    public static void record(String label) { if (label != null) recordNative(label); }
    // Only the first occurrence of each boundary is needed; render loops stay bounded.
    // Round 31: the deduplicated list cannot answer "does this boundary repeat?", which is what
    // decides whether a fiber is iterating or parked. Counts are per label (and the last time it
    // happened), so one sample 25 s apart is enough; the storage stays bounded by the label set.
    @JSBody(params="label",script="var p=globalThis.pzGameProgress||(globalThis.pzGameProgress=[]);var c=globalThis.pzGameCounts||(globalThis.pzGameCounts={});var t=performance.now();var n=(c[label]|0)+1;c[label]=n;c[label+'#last']=t;if(!p.some(function(x){return x.label===label;}))p.push({label:label,time:t});")
    private static native void recordNative(String label);

    // Round 29: when a fiber parks, the deduplicated list cannot show which call was last. This
    // keeps a bounded ring of the most recent boundary events (duplicates included) so the actual
    // park point is visible. Bounded so a render loop cannot grow the browser heap.
    public static void trace(String label) { if (label != null) traceNative(label); }
    @JSBody(params="label",script="var t=globalThis.pzGameTrace||(globalThis.pzGameTrace=[]);t.push({label:label,time:performance.now()});if(t.length>400)t.splice(0,t.length-400);")
    private static native void traceNative(String label);
}
