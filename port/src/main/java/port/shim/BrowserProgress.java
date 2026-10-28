package port.shim;
import org.teavm.jso.JSBody;
public final class BrowserProgress {
    public static void record(String label) { recordNative(label); }
    // Only the first occurrence of each boundary is needed; render loops stay bounded.
    @JSBody(params="label",script="var p=globalThis.pzGameProgress||(globalThis.pzGameProgress=[]);if(!p.some(function(x){return x.label===label;}))p.push({label:label,time:performance.now()});")
    private static native void recordNative(String label);

    // Round 29: when a fiber parks, the deduplicated list cannot show which call was last. This
    // keeps a bounded ring of the most recent boundary events (duplicates included) so the actual
    // park point is visible. Bounded so a render loop cannot grow the browser heap.
    public static void trace(String label) { traceNative(label); }
    @JSBody(params="label",script="var t=globalThis.pzGameTrace||(globalThis.pzGameTrace=[]);t.push({label:label,time:performance.now()});if(t.length>400)t.splice(0,t.length-400);")
    private static native void traceNative(String label);
}
