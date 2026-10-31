package port.shim;
import org.teavm.jso.JSBody;
public final class BrowserProgress {
    // Round 32: boundaries are counted with an INT id instead of a String label. On WasmGC a fiber
    // that resumes re-enters the instrumented body mid-way, and the String constant the transformer
    // injected in that frame is still unmaterialized, so passing it to the JS conversion crashed the
    // module (diag56: record <- org.teavm.runtime.Fiber#suspend, i.e. the :returned injection inside
    // the instrumented suspend method; the previous "label != null" guard did not help because the
    // value is only null in that replayed frame and the crash happens inside String::length).
    // An int argument is never null and needs no materialization. The label is resolved on the JS
    // side from globalThis.pzPulseLabels, which game.mjs loads from the build's pulse-map.json, so
    // pzGameProgress/pzGameTrace/pzGameCounts keep exactly the same label-based shape.
    // Storage: counts per label plus the last time, a deduplicated progress list, and a bounded ring
    // of the most recent boundaries (duplicates included) so the park point stays visible while a
    // render loop cannot grow the browser heap.
    // The transformer-injected call must target a plain Java method: calls added to a @JSBody
    // method after the JSO transformation phase fail with "Method is not annotated with
    // org.teavm.interop.Import" (build-round32-game.log). The JS body stays inside this wrapper.
    public static void pulse(int bucket) { pulseNative(bucket); }
    @JSBody(params="bucket",script="""
      var labels=globalThis.pzPulseLabels; if(!labels)return;
      var label=labels[bucket]; if(label===undefined)return;
      var t=performance.now();
      var c=globalThis.pzGameCounts||(globalThis.pzGameCounts={});
      var n=(c[label]|0)+1; c[label]=n; c[label+'#last']=t;
      var p=globalThis.pzGameProgress||(globalThis.pzGameProgress=[]);
      if(!p.some(function(x){return x.label===label;}))p.push({label:label,time:t});
      var r=globalThis.pzGameTrace||(globalThis.pzGameTrace=[]);
      r.push({label:label,time:t}); if(r.length>400)r.splice(0,r.length-400);
    """)
    private static native void pulseNative(int bucket);
    // Ronda 75: ultimos argumentos de UIManager#updateMouseButtons / UIElement#onMouseDown.
    public static void traceInts(int a,int b) { traceNative("ints",a,b); }
    public static void traceDoubles(double a,double b) { traceNative("dbl",a,b); }
    @JSBody(params={"kind","a","b"},script="""
      var r=globalThis.pzUiTrace||(globalThis.pzUiTrace=[]);
      if(kind==="ints"){var l=r.lastInts; if(l&&l[0]===a&&l[1]===b)return; r.lastInts=[a,b];}
      r.push([kind,a,b,performance.now()|0]); if(r.length>60)r.splice(0,r.length-60);
    """)
    private static native void traceNative(String kind,double a,double b);
}
