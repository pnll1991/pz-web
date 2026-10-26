package port.shim;
import org.teavm.jso.JSBody;
public final class BrowserProgress {
    public static void record(String label) { recordNative(label); }
    // Only the first occurrence of each boundary is needed; render loops stay bounded.
    @JSBody(params="label",script="var p=globalThis.pzGameProgress||(globalThis.pzGameProgress=[]);if(!p.some(function(x){return x.label===label;}))p.push({label:label,time:performance.now()});")
    private static native void recordNative(String label);
}
