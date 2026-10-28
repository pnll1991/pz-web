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
    // Round 32: pause takes NO argument. TeaVM's WasmGC @Async transformation saves the arguments
    // and int locals of a suspending method in the fiber's int stack (Fiber::pushInt/popInt) and
    // restores them on resume. The A/B build (PZ_PULSE_OFF=1, zero injected pulses) still crashed
    // with "array element access out of bounds" at Fiber::popInt <- Thread::currentThread <-
    // Fiber::suspend <- BrowserYield::pause, so the corruptor is the int-handoff of this async
    // native itself, not the boundary instrumentation. BrowserPersistence.flush() -- the only
    // other @Async native that works here -- also takes no int, so the handoff stays symmetric.
    @Async public static native void pause() throws InterruptedException;
    private static void pause(AsyncCallback<Void> callback) {
        schedule(() -> callback.complete(null));
    }
    /**
     * Round 32: TeaVM 0.16.0 keeps ONE static `Fiber.lastPendingCall` (verified by reading
     * org.teavm.runtime.Fiber from teavm-core 0.16.0). With two fibers parked at once -- the render
     * fiber in this yield and the game fiber in RenderContextQueueItem#waitUntilFinished ->
     * Object#wait -- the async continuation of one fiber under-flows the other fiber's saved-int
     * stack, which is exactly the `RuntimeError: array element access out of bounds` at
     * Fiber::popInt <- Thread::currentThread <- Fiber::suspend that rounds 31/32 measured, and it
     * reproduced with the PZ_PULSE_OFF build (zero injected pulses), so it is not our instrumentation.
     * Suspending here is opt-in through globalThis.pzYieldSuspend. Round 32 measured the other
     * branch: with suspension OFF (and a gate that skips the suspend while a render item is in
     * flight) the page never returns to the JavaScript event loop -- Playwright evaluate() and
     * screenshot() both time out and the log stops advancing (diag64) -- so the render fiber
     * starves the browser. Suspension stays the default and the corrupt pending-call chain is the
     * blocker to attack next round (see STATUS.md).
     */
    @JSBody(params={"fallback"}, script="return globalThis.pzYieldSuspend === true ? fallback : 0;")
    private static native int suspendDecision(int suspendRequested);

    public static boolean suspendRequested() {
        return suspendDecision(1) != 0;
    }

    /** Observer-only flag: 1 while a render-context item is waiting (game fiber parked on its lock). */
    public static void setRenderPending(int pending) {
        renderPendingNative(pending != 0);
    }

    @JSBody(params={"pending"}, script="globalThis.pzRenderItemPending = (pending === true);")
    private static native void renderPendingNative(boolean pending);

    @JSFunctor private interface Resume extends JSObject { void complete(); }
    @JSBody(params={"resume"}, script="var y=globalThis.pzYield||(globalThis.pzYield={pauses:0,resumes:0});y.pauses++;setTimeout(function(){y.resumes++;resume();}, 0);")
    private static native void schedule(Resume resume);
}
