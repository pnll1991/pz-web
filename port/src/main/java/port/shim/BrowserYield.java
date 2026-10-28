package port.shim;

import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Suspends the calling fiber until a real browser timer fires.
 *
 * Round 31 measurement: `Thread.sleep(0L)` enters (java.lang.Thread#sleep(J)V:entered = 1) and
 * suspends the render fiber (org.teavm.runtime.Fiber#suspend = 2 with zero returns), but the page
 * creates exactly ONE setTimeout in its whole life -- the EventQueue.offer of TThread.start() for
 * the renderThread -- so nothing ever resumes that fiber. The classlib path that should offer the
 * SleepHandler (TThread.sleep(long,AsyncCallback) -> EventQueue.offer) produced zero boundaries,
 * i.e. TeaVM 0.16.0 never reaches the timer for this sleep on WasmGC.
 *
 * This shim is the port's own cooperative handoff: it suspends on a genuine macrotask, so the
 * browser event loop (rAF, input, timers of other fibers) runs and TeaVM resumes this fiber from
 * the callback. Nothing is simulated -- if the timer never fires, the fiber stays parked, exactly
 * as before, and the diagnostics say so.
 */
public final class BrowserYield {
    @Async public static native void pause(int millis) throws InterruptedException;
    private static void pause(int millis, AsyncCallback<Void> callback) {
        schedule(Math.max(0, millis), () -> callback.complete(null));
    }
    @JSFunctor private interface Resume extends JSObject { void complete(); }
    @JSBody(params={"millis","resume"}, script="var y=globalThis.pzYield||(globalThis.pzYield={pauses:0,resumes:0});y.pauses++;setTimeout(function(){y.resumes++;resume();}, Math.max(0, millis|0));")
    private static native void schedule(int millis, Resume resume);
}
