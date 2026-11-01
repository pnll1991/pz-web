package port.shim;
import org.teavm.jso.JSBody;

/**
 * Round 37: observation of TeaVM's monitor implementation (org.teavm.classlib.java.lang.TObject,
 * which is renamed to java.lang.Object). The render fiber is parked in the monitorenter of
 * zombie.core.opengl.RenderContextQueueItem.invoke() while the game fiber is parked in
 * Object.wait() on the same waitLock, so the monitor state decides who is at fault.
 *
 * Only ints cross the boundary: a String constant injected in a replayed coroutine frame is not
 * materialized yet (round 32) and StringBuilder.append is itself a coroutine (round 33). The JS
 * body lives here, not inside the substituted java.lang.Object, because TeaVM does not emit the
 * JSO imports for a @JSBody declared in a renamed java.* class (round 25 learning).
 */
public final class BrowserMonitor {
    // Round 81 (front 3): opt-in. The boot CPU profile spent 172 s of 395 s (44 %) here: every
    // Object.notifyAll (phase 9, ReentrantLock.acquire/unlock, CellularAutomatonRNG.next) crossed into
    // JS and built `new Error().stack`, and every monitorExit (phase 5) crossed to bump a counter.
    // Nothing reads pzMonitorCounts/pzMonitorLast/pzMonitorLog unless someone is debugging monitors, so
    // the trace is read once (globalThis.PZ_MONITOR_TRACE === true or ?monitor=1) and otherwise the
    // call returns before the boundary. Plain static fields without initializers: no <clinit>, and the
    // monitor paths that call this must not gain a suspension point (round 40).
    private static boolean checked;
    private static boolean trace;

    public static void record(int phase, int objId, int ownerId, int threadId, int count,
            int entering, int notify) {
        if (!checked) {
            trace = traceEnabled();
            checked = true;
        }
        if (!trace) {
            return;
        }
        recordNative(phase, objId, ownerId, threadId, count, entering, notify);
    }

    @JSBody(script = "return globalThis.PZ_MONITOR_TRACE === true || (typeof location !== 'undefined'"
            + " && new URLSearchParams(location.search).get('monitor') === '1');")
    private static native boolean traceEnabled();

    @JSBody(params = {"phase", "objId", "ownerId", "threadId", "count", "entering", "notify"},
            script = "var contended = phase === 10 || phase === 11"
                    + "     ? ownerId !== 0 && ownerId !== threadId : true;"
                    + " if (!contended) { return; }"
                    // Round 38: counts accumulate over the WHOLE run. The 160-entry ring below was
                    // flooded by phase 5 (every monitorExit of the boot), so the phases that decide
                    // the deadlock (3 enqueued, 6/13 granted, 7 listener, 8/9 notify, 14 direct grant)
                    // were shifted out of the ring and looked like they never happened. objId is also
                    // useless as an object discriminator: wasmGCIdentity() saturates, so every monitor
                    // logs either -1 or 2147483647. The caller frames are the real discriminator.
                    + " var cnt = globalThis.pzMonitorCounts || (globalThis.pzMonitorCounts = {});"
                    + " var ck = phase + ':' + threadId;"
                    + " cnt[ck] = (cnt[ck] || 0) + 1;"
                    + " var last = globalThis.pzMonitorLast || (globalThis.pzMonitorLast = {});"
                    // Phase 5 is monitorExit: the hottest path of the boot. Count it, never capture a
                    // stack for it (that is what made the page unmeasurable in round 37d).
                    + " if (phase === 5) { return; }"
                    + " var limit = Error.stackTraceLimit;"
                    + " Error.stackTraceLimit = 12;"
                    + " var frames = String((new Error()).stack || '').split('\\n')"
                    + "     .map(function (s) { return s.trim(); })"
                    + "     .filter(function (s) { return s.indexOf('::') >= 0; }).slice(1, 5);"
                    + " Error.stackTraceLimit = limit;"
                    + " last[phase] = {phase: phase, obj: objId, owner: ownerId, thread: threadId,"
                    + " count: count, entering: entering, notify: notify,"
                    + " t: Math.round(performance.now()), frames: frames};"
                    + " var log = globalThis.pzMonitorLog || (globalThis.pzMonitorLog = []);"
                    + " log.push({phase: phase, obj: objId, owner: ownerId, thread: threadId,"
                    + " count: count, entering: entering, notify: notify,"
                    + " t: Math.round(performance.now()), frames: frames});"
                    + " if (log.length > 60) { log.shift(); }")
    private static native void recordNative(int phase, int objId, int ownerId, int threadId,
            int count, int entering, int notify);
}
