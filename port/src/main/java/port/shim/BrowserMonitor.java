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
    public static void record(int phase, int objId, int ownerId, int threadId, int count,
            int entering, int notify) {
        recordNative(phase, objId, ownerId, threadId, count, entering, notify);
    }

    @JSBody(params = {"phase", "objId", "ownerId", "threadId", "count", "entering", "notify"},
            script = "var contended = phase === 10 || phase === 11"
                    + "     ? ownerId !== 0 && ownerId !== threadId : true;"
                    + " if (!contended) { return; }"
                    + " var log = globalThis.pzMonitorLog || (globalThis.pzMonitorLog = []);"
                    // The page sets Error.stackTraceLimit to Infinity, so capturing a stack here
                    // without a temporary limit stalls the event loop (measured in round 37: the
                    // whole page stopped answering). Only contention events get a short stack.
                    + " var limit = Error.stackTraceLimit;"
                    + " Error.stackTraceLimit = 14;"
                    + " var frames = String((new Error()).stack || '').split('\\n')"
                    + "     .map(function (s) { return s.trim(); })"
                    + "     .filter(function (s) { return s.indexOf('::') >= 0; }).slice(0, 6);"
                    + " Error.stackTraceLimit = limit;"
                    + " log.push({phase: phase, obj: objId, owner: ownerId, thread: threadId,"
                    + " count: count, entering: entering, notify: notify,"
                    + " t: Math.round(performance.now()), frames: frames});"
                    + " if (log.length > 160) { log.shift(); }")
    private static native void recordNative(int phase, int objId, int ownerId, int threadId,
            int count, int entering, int notify);
}
