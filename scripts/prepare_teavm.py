#!/usr/bin/env python3
"""Apply pinned compiler and runtime fixes to Apache-2.0 TeaVM sources.

Only generated, ignored output contains the upstream source. No game inputs.
"""
from pathlib import Path
import hashlib
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = "0.16.0"
SHA256 = "687cabf23e0d17a15be30fbc070809c2931714340aca224120fe31579f95519b"
SOURCE = "org/teavm/reflection/ReflectionDependencyListener.java"


def patch_monitor():
    """Interrupted Object.wait must reacquire the monitor before throwing."""
    digest = "da6f83c1436c87a9ad683b6143aaa9c172b08c3974c9a7d5741a479d0d60092e"
    archive = ROOT / f".toolchain/downloads/teavm-classlib-{VERSION}-sources.jar"
    if not archive.exists():
        url = f"https://repo.maven.apache.org/maven2/org/teavm/teavm-classlib/{VERSION}/{archive.name}"
        with urllib.request.urlopen(url, timeout=60) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != digest:
            raise RuntimeError("Unexpected TeaVM classlib digest")
        archive.write_bytes(data)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != digest:
        raise RuntimeError("TeaVM classlib source digest mismatch")
    path = "org/teavm/classlib/java/lang/TObject.java"
    with zipfile.ZipFile(archive) as package:
        source = package.read(path).decode("utf-8")
    old = "        waitImpl(timeout, nanos);"
    new = """        if (timeout < 0 || nanos < 0 || nanos > 999999) {
            throw new IllegalArgumentException("Invalid wait timeout");
        }
        if (TThread.interrupted()) {
            throw new TInterruptedException();
        }
        waitImpl(timeout, nanos);"""
    assert source.count(old) == 1
    source = source.replace(old, new)
    old = "int timeoutToSchedule = timeout >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) timeout;"
    new = "int timeoutToSchedule = timeout >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) timeout + (nanos > 0 ? 1 : 0);"
    assert source.count(old) == 1
    source = source.replace(old, new)
    old = "            monitorEnterWait(obj, lockCount, callback);"
    new = """            if (currentThread.interruptHandler == this) {
                currentThread.interruptHandler = null;
            }
            monitorEnterWait(obj, lockCount, callback);"""
    assert source.count(old) == 1
    source = source.replace(old, new)
    old = "() -> callback.error(new TInterruptedException())"
    assert source.count(old) == 2
    source = source.replace(old, "this::resumeInterrupted")
    marker = "        public void interrupted() {"
    method = """        private void resumeInterrupted() {
            expired = true;
            TThread.setCurrentThread(currentThread);
            monitorEnterWait(obj, lockCount, new AsyncCallback<Void>() {
                public void complete(Void value) {
                    TThread.interrupted();
                    callback.error(new TInterruptedException());
                }
                public void error(Throwable error) { callback.error(error); }
            });
        }

        @Override
        public void interrupted() {"""
    assert source.count("        @Override\n" + marker) == 1
    source = source.replace("        @Override\n" + marker, method)
    # Round 40 (platform only, no game rules touched): the monitor's two queues must NOT be
    # classlib collections. Measured on boot.wasm (build round38d) with tools/code-agent:
    # java.util.ArrayDeque::add/addFirst/addLast/clear/offer/offerLast/peek/remove/removeFirst/
    # removeLast/pop/push are all compiled as TeaVM COROUTINES (Fiber ops in their bodies), because
    # grow/Objects.requireNonNull -> Throwable::fillInStackTrace -> Class::getName -> StringBuilder
    # -> KahluaTableImpl::toString -> UIManager$Sync::endFrame -> Thread::sleep reaches a suspension
    # seed (probe8 cost 16 from ArrayDeque::addLast). TeaVM inserts a suspension point at EVERY async
    # call, so inside Object.waitImpl's own @Async body `monitor.notifyListeners.add(listener)` parked
    # the game fiber before the listener existed, and inside monitorEnterWait's body
    # `monitor.enteringThreads.add(...)` parked the render fiber before the waiter existed. Neither
    # fiber ever registered itself, so no release could grant either one -- exactly what diag84d.log
    # and diag85d.log show: phase 16 = 1 but phase 3 = 0, pzAsync.offers = 2 for the whole run
    # (Thread::start and BrowserYield's resume only), invoke entered 1 / returned 0, swap stuck at 2.
    # The queues below are plain field writes on concrete classes: no method call, therefore no
    # suspension point.
    struct_edits = [
        ("""    static class Monitor {
        static final int MASK = 0x80000000;

        Queue<PlatformRunnable> enteringThreads;
        Queue<NotifyListener> notifyListeners;""",
         """    static class Monitor {
        static final int MASK = 0x80000000;

        EnteringQueue enteringThreads;
        NotifyQueue notifyListeners;"""),
        ("""    interface NotifyListener extends PlatformRunnable, EventQueue.Event {""",
         """    // Intrusive FIFO of monitorenter waiters. Every operation is a field write on a concrete
    // class: an async call anywhere here would park the fiber that is only trying to register.
    static final class EnteringQueue {
        EnteringRequest head;
        EnteringRequest tail;
        int size;

        boolean isEmpty() {
            return head == null;
        }

        int size() {
            return size;
        }

        void add(EnteringRequest request) {
            request.next = null;
            if (tail == null) {
                head = request;
                tail = request;
            } else {
                tail.next = request;
                tail = request;
            }
            ++size;
        }

        EnteringRequest remove() {
            EnteringRequest request = head;
            if (request == null) {
                return null;
            }
            head = request.next;
            if (head == null) {
                tail = null;
            }
            request.next = null;
            --size;
            return request;
        }
    }

    static final class EnteringRequest implements PlatformRunnable {
        EnteringRequest next;
        final TObject object;
        final TThread thread;
        final int count;
        final AsyncCallback<Void> callback;

        EnteringRequest(TObject object, TThread thread, int count, AsyncCallback<Void> callback) {
            this.object = object;
            this.thread = thread;
            this.count = count;
            this.callback = callback;
        }

        @Override
        public void run() {
            TThread.setCurrentThread(thread);
            Monitor monitor = monitorOf(object);
            if (monitor == null) {
                callback.complete(null);
                return;
            }
            monitor.owner = thread;
            monitor.count += count;
            callback.complete(null);
        }
    }

    // Intrusive FIFO of Object.wait listeners, same reason as EnteringQueue.
    static final class NotifyQueue {
        NotifyListenerImpl head;
        NotifyListenerImpl tail;
        int size;

        boolean isEmpty() {
            return head == null;
        }

        int size() {
            return size;
        }

        void add(NotifyListenerImpl listener) {
            listener.next = null;
            if (tail == null) {
                head = listener;
                tail = listener;
            } else {
                tail.next = listener;
                tail = listener;
            }
            ++size;
        }

        NotifyListenerImpl remove() {
            NotifyListenerImpl listener = head;
            if (listener == null) {
                return null;
            }
            head = listener.next;
            if (head == null) {
                tail = null;
            }
            listener.next = null;
            --size;
            return listener;
        }
    }

    interface NotifyListener extends PlatformRunnable, EventQueue.Event {"""),
        ("""        Monitor monitor = o.monitor;
        if (monitor.enteringThreads == null) {
            monitor.enteringThreads = new ArrayDeque<>();
        }
        monitor.enteringThreads.add(() -> {
            TThread.setCurrentThread(thread);
            o.monitor.owner = thread;
            o.monitor.count += count;
            callback.complete(null);
        });""",
         """        Monitor monitor = o.monitor;
        if (monitor.enteringThreads == null) {
            monitor.enteringThreads = new EnteringQueue();
        }
        monitor.enteringThreads.add(new EnteringRequest(o, thread, count, callback));"""),
        ("""        var listener = new NotifyListenerImpl(this, callback, monitor.count);
        if (monitor.notifyListeners == null) {
            monitor.notifyListeners = new ArrayDeque<>();
        }""",
         """        var listener = new NotifyListenerImpl(this, callback, monitor.count);
        if (monitor.notifyListeners == null) {
            monitor.notifyListeners = new NotifyQueue();
        }"""),
        ("""        final TObject obj;
        final AsyncCallback<Void> callback;""",
         """        NotifyListenerImpl next;
        final TObject obj;
        final AsyncCallback<Void> callback;"""),
    ]
    for old, new in struct_edits:
        if source.count(old) != 1:
            raise RuntimeError("Unexpected TeaVM monitor shape (struct): " + old[:60])
        source = source.replace(old, new)
    # Round 37 diagnostics: the render fiber is parked in RenderContextQueueItem.invoke()'s
    # monitorenter (Object.monitorEnterWait) while the game fiber is parked in the same object's
    # Object.wait. Record the monitor state at every grant, wait, release, re-acquire and notify so
    # the deadlock is identified instead of guessed. Ints only: a String constant in a replayed
    # coroutine frame is not materialized yet (round 32), and StringBuilder.append is a coroutine.
    # Round 40 (second blocker, measured in diag87.log on the round40 build): the render-thread
    # fiber now runs the whole render loop (flushInvokeQueue 376768/376768, invoke returned 1, the
    # contended monitorenter phases 15/16 are gone), but the MainThread fiber is parked at
    # Fiber::suspend <- Object::waitImpl <- Object::wait <- RenderContextQueueItem::waitUntilFinished
    # with suspends 1 / resumes 0, and the monitor is NEVER released (owner stays thread 2, which is
    # why the only notify on that lock is the re-entrant 9:2 and phases 3/7/13 stay 0).
    # Cause, same class of bug as the queues above: Object.monitorExit(o,count) IS a coroutine here,
    # because its error path calls `new TIllegalMonitorStateException()`, and Throwable::<init> ->
    # fillInStackTrace -> Class::getName -> StringBuilder -> KahluaTableImpl::toString ->
    # UIManager$Sync::endFrame -> Thread::sleep makes the whole constructor async (probe5 on the
    # round40 wasm: monitorExit_0 fiber True, the only Fiber suspension seed sitting after the
    # release lines). TeaVM suspends at that call, so waitImpl registers the listener (phase 4 = 1)
    # and parks before `monitor.count -= count; monitor.owner = null` ever runs.
    # Fix: release the monitor through a helper with NO method call that can suspend -- plain field
    # writes, isEmptyMonitor() (fiber False, measured) and EventQueue.offer (fiber False). Semantics
    # are the same as monitorExit(o, monitor.count): count drops to 0, owner clears, waiters get the
    # deferred drain. No game rules touched; the exception path is not needed here because wait()
    # already checked holdsLock() in wait0 before calling waitImpl.
    wait_release_edits = [
        ("""        monitorExit(this, monitor.count);
    }""",
         """        releaseForWait(monitor);
    }

    private static void releaseForWait(Monitor monitor) {
        monitor.count = 0;
        monitor.owner = null;
        reportMonitor(5, monitor.id, 0, (int) TThread.currentThread().getId(), monitor.count,
                monitor.enteringThreads == null ? 0 : monitor.enteringThreads.size(),
                monitor.notifyListeners == null ? 0 : monitor.notifyListeners.size());
        if (monitor.enteringThreads != null && !monitor.enteringThreads.isEmpty()) {
            EventQueue.offer(() -> waitForOtherThreads(monitor.object));
            return;
        }
        monitor.object.isEmptyMonitor();
    }"""),
        ("""        EnteringQueue enteringThreads;
        NotifyQueue notifyListeners;""",
         """        EnteringQueue enteringThreads;
        NotifyQueue notifyListeners;
        // Round 40: releaseForWait needs the owning object to drain waiters, and the upstream
        // Monitor has no back reference. Assigned once in createMonitor (plain field write).
        TObject object;"""),
    ]
    for old, new in wait_release_edits:
        if source.count(old) != 1:
            raise RuntimeError("Unexpected TeaVM monitor shape (wait release): " + old[:60])
        source = source.replace(old, new)
    # createMonitor must set the back reference on every branch.
    create_marker = """    private static void createMonitor(TObject o) {
        if (PlatformDetector.isWebAssemblyGC()) {
            int hashCode = o.wasmGCIdentity();
            o.monitor = new Monitor();
            o.monitor.id = hashCode;
        } else if (PlatformDetector.isLowLevel()) {
            int hashCode = hashCodeLowLevel(o);
            o.monitor = new Monitor();
            o.monitor.id = hashCode;
        } else {
            o.monitor = new Monitor();
        }
    }"""
    assert source.count(create_marker) == 1
    assert create_marker.count("""        } else {
            o.monitor = new Monitor();
        }
    }""") == 1
    source = source.replace(create_marker, create_marker.replace(
        """        } else {
            o.monitor = new Monitor();
        }
    }""", """        } else {
            o.monitor = new Monitor();
        }
        o.monitor.object = o;
    }"""))
    diag_edits = [
        ("""        Monitor monitor = o.monitor;
        if (monitor.enteringThreads == null) {""",
         """        Monitor monitor = o.monitor;
        reportMonitor(3, monitor.id, monitor.owner == null ? 0 : (int) monitor.owner.getId(),
                (int) thread.getId(), monitor.count,
                monitor.enteringThreads == null ? 0 : monitor.enteringThreads.size(),
                monitor.notifyListeners == null ? 0 : monitor.notifyListeners.size());
        if (monitor.enteringThreads == null) {"""),
        ("""            o.monitor.count += count;
            callback.complete(null);
            return;
        } else if (o.monitor.owner == null) {""",
         """            o.monitor.count += count;
            reportMonitor(1, o.monitor.id, 0, (int) thread.getId(), o.monitor.count, 0, 0);
            callback.complete(null);
            return;
        } else if (o.monitor.owner == null) {"""),
        ("""            o.monitor.count += count;
            callback.complete(null);
            return;
        }

        Monitor monitor = o.monitor;""",
         """            o.monitor.count += count;
            reportMonitor(2, o.monitor.id, 0, (int) thread.getId(), o.monitor.count, 0, 0);
            callback.complete(null);
            return;
        }

        Monitor monitor = o.monitor;"""),
        # Round 38: the phase 10/11/12 reports that used to sit on monitorEnter/monitorExit/
        # createMonitor are REMOVED. Those three are the hottest path of the whole program (every
        # synchronized method of the boot calls them thousands of times per frame), and from the
        # build that added them (round37d, 16:42) on, the page stopped answering JavaScript at all:
        # diag72.log/diag72b.log (round37e/f) and this round's diag75.log/diag76.log time out on
        # every page.evaluate, including a minimal probe 15 s after load. Round 37's last complete
        # measurement (diag71.log, build round37c, phases 1-9 only) still returned everything, so
        # the hot-path instrumentation is what destroyed measurability. The wait/notify/grant
        # phases below stay: they are the ones that actually explain the parked fibers.
        ("""    final void waitImpl(long timeout, int nanos, AsyncCallback<Void> callback) {
        Monitor monitor = this.monitor;""",
         """    final void waitImpl(long timeout, int nanos, AsyncCallback<Void> callback) {
        Monitor monitor = this.monitor;
        reportMonitor(4, monitor.id, monitor.owner == null ? 0 : (int) monitor.owner.getId(),
                (int) TThread.currentThread().getId(), monitor.count,
                monitor.enteringThreads == null ? 0 : monitor.enteringThreads.size(),
                monitor.notifyListeners == null ? 0 : monitor.notifyListeners.size());"""),
        ("""        monitor.owner = null;
        if (monitor.enteringThreads != null && !monitor.enteringThreads.isEmpty()) {""",
         """        monitor.owner = null;
        reportMonitor(5, monitor.id, 0, (int) TThread.currentThread().getId(), monitor.count,
                monitor.enteringThreads == null ? 0 : monitor.enteringThreads.size(),
                monitor.notifyListeners == null ? 0 : monitor.notifyListeners.size());
        if (monitor.enteringThreads != null && !monitor.enteringThreads.isEmpty()) {"""),
        ("""            var enteringThreads = monitor.enteringThreads;
            PlatformRunnable r = enteringThreads.remove();""",
         """            var enteringThreads = monitor.enteringThreads;
            reportMonitor(6, monitor.id, 0, (int) TThread.currentThread().getId(), monitor.count,
                    enteringThreads.size(),
                    monitor.notifyListeners == null ? 0 : monitor.notifyListeners.size());
            PlatformRunnable r = enteringThreads.remove();"""),
        ("""            if (currentThread.interruptHandler == this) {
                currentThread.interruptHandler = null;
            }
            monitorEnterWait(obj, lockCount, callback);""",
         """            if (currentThread.interruptHandler == this) {
                currentThread.interruptHandler = null;
            }
            reportMonitor(7, monitorIdOr0(obj), 0, (int) currentThread.getId(),
                    monitorCountOr0(obj), 0, 0);
            monitorEnterWait(obj, lockCount, callback);"""),
        ("""    @Rename("notify")
    public final void notify0() {
        if (!holdsLock(this)) {
            throw new TIllegalMonitorStateException();
        }
        var listeners = monitor.notifyListeners;""",
         """    @Rename("notify")
    public final void notify0() {
        if (!holdsLock(this)) {
            throw new TIllegalMonitorStateException();
        }
        reportMonitor(8, monitor.id, (int) monitor.owner.getId(),
                (int) TThread.currentThread().getId(), monitor.count,
                monitor.enteringThreads == null ? 0 : monitor.enteringThreads.size(),
                monitor.notifyListeners == null ? 0 : monitor.notifyListeners.size());
        var listeners = monitor.notifyListeners;"""),
        ("""    @Rename("notifyAll")
    public final void notifyAll0() {
        if (!holdsLock(this)) {
            throw new TIllegalMonitorStateException();
        }""",
         """    @Rename("notifyAll")
    public final void notifyAll0() {
        if (!holdsLock(this)) {
            throw new TIllegalMonitorStateException();
        }
        reportMonitor(9, monitor.id, (int) monitor.owner.getId(),
                (int) TThread.currentThread().getId(), monitor.count,
                monitor.enteringThreads == null ? 0 : monitor.enteringThreads.size(),
                monitor.notifyListeners == null ? 0 : monitor.notifyListeners.size());"""),
        # Round 38 (second half): diag83/diag84 measured that phase 3 (the contended branch of the
        # monitorEnterWait BODY) is NEVER counted, while the render fiber's stack says it is parked in
        # Fiber::suspend <- Object::monitorEnterWait <- Object::monitorEnter <-
        # RenderContextQueueItem::invoke. Either the @Async body runs only when the fiber is resumed
        # (so the waiter never registers itself in enteringThreads and nobody can grant it), or the
        # park happens somewhere else. Phase 15 is at the contended branch of monitorEnter (BEFORE
        # calling the @Async wait) and phase 16 at the very first line of the wait body: they decide
        # which of the two is true. Both sit on the contended path only, so unlike phases 10/11/12
        # they are not on the hot path and do not destroy measurability.
        ("""        if (o.monitor.owner != TThread.currentThread()) {
            monitorEnterWait(o, count);
        } else {
            o.monitor.count += count;
        }""",
         """        if (o.monitor.owner != TThread.currentThread()) {
            reportMonitor(15, o.monitor.id, ownerIdOr0(o.monitor), threadIdOr0(), o.monitor.count,
                    enteringSize(o.monitor), notifySize(o.monitor));
            monitorEnterWait(o, count);
        } else {
            o.monitor.count += count;
        }"""),
        ("""    static void monitorEnterWait(TObject o, int count, AsyncCallback<Void> callback) {
        TThread thread = TThread.currentThread();""",
         """    static void monitorEnterWait(TObject o, int count, AsyncCallback<Void> callback) {
        reportMonitor(16, o.monitor == null ? 0 : o.monitor.id, ownerIdOr0(o.monitor), threadIdOr0(),
                o.monitor == null ? 0 : o.monitor.count, enteringSize(o.monitor), notifySize(o.monitor));
        TThread thread = TThread.currentThread();"""),
    ]
    for old, new in diag_edits:
        if source.count(old) != 1:
            raise RuntimeError("Unexpected TeaVM monitor shape: " + old[:60])
        source = source.replace(old, new)
    # Round 38 fix (platform only, no game rules touched): two real bugs in TeaVM 0.16.0's monitor
    # that strand both fibers of the RenderContextQueueItem handshake (diag71.log: game fiber parked
    # in Object.wait on waitLock, render fiber parked in the same waitLock's monitorenter,
    # RenderContextQueueItem#invoke entered 1 / returned 0, menu:false).
    #  a) waitForOtherThreads grants the head of enteringThreads and then clears the WHOLE queue
    #     (monitor.enteringThreads = null). With two waiters -- the render fiber in invoke()'s
    #     monitorenter and the game fiber re-acquiring the lock after Object.wait -- the second one
    #     is dropped, isEmptyMonitor() then deletes the monitor, and nobody ever grants it.
    #  b) A waiter enqueued while nobody is about to release the monitor is never drained: the only
    #     drain sources were monitorExit (needs an owner to release) and the release-triggered
    #     waitForOtherThreads. Offering a deferred drain at enqueue time covers that, and it is not
    #     a fake grant -- waitForOtherThreads still refuses to hand out a monitor someone owns.
    #  c) NotifyListenerImpl.run re-entered the lock through monitorEnterWait even when the monitor
    #     was already free, i.e. it became an enteringThreads waiter that no release would drain.
    #     Grant it directly, exactly as monitorEnterWait's own free-monitor branch does.
    fix_edits = [
        ("""            PlatformRunnable r = enteringThreads.remove();
            monitor.enteringThreads = null;
            r.run();""",
         """            PlatformRunnable r = enteringThreads.remove();
            if (enteringThreads.isEmpty()) {
                monitor.enteringThreads = null;
            }
            reportMonitor(13, monitor.id, 0, (int) TThread.currentThread().getId(), monitor.count,
                    monitor.enteringThreads == null ? 0 : monitor.enteringThreads.size(),
                    monitor.notifyListeners == null ? 0 : monitor.notifyListeners.size());
            r.run();
            if (monitor.enteringThreads != null && !monitor.enteringThreads.isEmpty()) {
                EventQueue.offer(() -> waitForOtherThreads(o));
            }"""),
        # Round 40: the enqueue itself is now the plain `new EnteringRequest(...)` above (no
        # suspension point), so the deferred drain is offered right after the waiter is registered.
        ("""        monitor.enteringThreads.add(new EnteringRequest(o, thread, count, callback));""",
         """        monitor.enteringThreads.add(new EnteringRequest(o, thread, count, callback));
        if (PlatformDetector.isLowLevel() || PlatformDetector.isWebAssemblyGC()) {
            EventQueue.offer(() -> waitForOtherThreads(o));
        } else {
            Platform.postpone(() -> waitForOtherThreads(o));
        }"""),
        ("""            reportMonitor(7, monitorIdOr0(obj), 0, (int) currentThread.getId(),
                    monitorCountOr0(obj), 0, 0);
            monitorEnterWait(obj, lockCount, callback);""",
         """            reportMonitor(7, monitorIdOr0(obj), 0, (int) currentThread.getId(),
                    monitorCountOr0(obj), 0, 0);
            Monitor free = monitorOf(obj);
            if (free != null && free.owner == null) {
                free.owner = currentThread;
                free.count += lockCount;
                reportMonitor(14, free.id, 0, (int) currentThread.getId(), free.count,
                        free.enteringThreads == null ? 0 : free.enteringThreads.size(), 0);
                callback.complete(null);
                return;
            }
            monitorEnterWait(obj, lockCount, callback);"""),
    ]
    for old, new in fix_edits:
        if source.count(old) != 1:
            raise RuntimeError("Unexpected TeaVM monitor shape (fix): " + old[:60])
        source = source.replace(old, new)
    js_diag = """    private static void reportMonitor(int phase, int objId, int ownerId, int threadId,
            int count, int entering, int notify) {
        port.shim.BrowserMonitor.record(phase, objId, ownerId, threadId, count, entering, notify);
    }

    private static int threadIdOr0() {
        TThread thread = TThread.currentThread();
        return thread == null ? 0 : (int) thread.getId();
    }

    private static int ownerIdOr0(Monitor monitor) {
        if (monitor == null || monitor.owner == null) {
            return 0;
        }
        return (int) monitor.owner.getId();
    }

    private static int enteringSize(Monitor monitor) {
        return monitor == null || monitor.enteringThreads == null ? 0 : monitor.enteringThreads.size();
    }

    private static int notifySize(Monitor monitor) {
        return monitor == null || monitor.notifyListeners == null ? 0 : monitor.notifyListeners.size();
    }

    // Round 40 (root cause of the `RuntimeError: unreachable` in NotifyListenerImpl::run): TeaVM's
    // BaseClassesTransformation rewrites getfield/putfield of java.lang.Object::monitor into the
    // getMonitor/setMonitor intrinsics ONLY inside methods of java.lang.Object, and
    // WasmGCClassGenerator.fillSimpleClassFields deliberately skips that field, so getFieldIndex()
    // is -1 for every other class. WasmGCInstructionGenerationVisitor.loadNormalField/storeNormalField
    // then emit `unreachable`, truncating the body: the verified wasm of java.lang.Object$
    // NotifyListenerImpl::run traps right after `local.get 0; struct.get field=3 obj` (rel 219) and
    // java.lang.Object$EnteringRequest::run right after `struct.get field=3 object` (rel 86). Neither
    // nested access is in Object's own methods, so both were dead code paths (phases 3/6/7/13/14 = 0
    // and the game fiber never resumed). Reading/writing the monitor through these helpers keeps the
    // access inside java.lang.Object, where the intrinsic applies.
    static Monitor monitorOf(TObject o) {
        return o.monitor;
    }

    static int monitorIdOr0(TObject o) {
        Monitor monitor = o.monitor;
        return monitor == null ? 0 : monitor.id;
    }

    static int monitorCountOr0(TObject o) {
        Monitor monitor = o.monitor;
        return monitor == null ? 0 : monitor.count;
    }

"""
    marker = "    static boolean holdsLock(TObject o) {"
    assert source.count(marker) == 1
    source = source.replace(marker, js_diag + marker)


    destination = ROOT / "port/target/generated-sources/teavm" / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("// Local change: restore monitor ownership on interrupted wait.\n" + source)
    print(f"TeaVM {VERSION}: interrupted wait monitor restoration; source SHA-256 {digest}")


def patch_console():
    """Preserve byte slices and nonterminal UTF-8 decoding across console flushes."""


def patch_inflater():
    """java.util.zip.Inflater.inflate must report "needs input", not a data error.

    Measured on this machine (JDK 25 vs the pinned TeaVM classlib + jzlib 1.1.3):
    PZ's zombie.core.textures.PNGDecoder.readChunkUnzip calls inflate() *before*
    the first setInput(); jzlib's Inflate.inflate returns Z_STREAM_ERROR (-2)
    because z.next_in is still null, and upstream TInflater maps every code
    outside {Z_OK, Z_NEED_DICT, Z_STREAM_END} to
    DataFormatException("Error occurred: -2"). The JDK contract instead returns 0
    with needsInput() == true, which is what the decoder's refill loop expects.
    Result on boot.wasm: 48x "inflate error" and every font page .png failed.
    Genuine corrupt data still yields Z_DATA_ERROR (-3) and throws.
    """
    archive = ROOT / f".toolchain/downloads/teavm-classlib-{VERSION}-sources.jar"
    digest = "da6f83c1436c87a9ad683b6143aaa9c172b08c3974c9a7d5741a479d0d60092e"
    if hashlib.sha256(archive.read_bytes()).hexdigest() != digest:
        raise RuntimeError("TeaVM inflater source digest mismatch")
    path = "org/teavm/classlib/java/util/zip/TInflater.java"
    with zipfile.ZipFile(archive) as package:
        source = package.read(path).decode("utf-8")
    old = """            default:
                throw new TDataFormatException("Error occurred: " + errCode);"""
    new = """            default:
                // jzlib reports Z_STREAM_ERROR/Z_BUF_ERROR when the stream has no
                // unconsumed input (next_in == null before the first setInput, or
                // avail_in == 0). java.util.zip.Inflater returns 0 and reports
                // needsInput() there; PNGDecoder.readChunkUnzip feeds the next
                // IDAT chunk on exactly that answer. Corrupt data is Z_DATA_ERROR.
                if ((errCode == JZlib.Z_STREAM_ERROR || errCode == JZlib.Z_BUF_ERROR)
                        && (impl.next_in == null || impl.avail_in == 0)) {
                    return 0;
                }
                throw new TDataFormatException("Error occurred: " + errCode);"""
    if source.count(old) != 1:
        raise RuntimeError("Unexpected TeaVM inflater source shape")
    source = source.replace(old, new)
    destination = ROOT / "port/target/generated-sources/teavm" / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("// Local change: JDK inflate semantics when no input is available.\n" + source)
    print(f"TeaVM {VERSION}: inflate returns 0 (needsInput) instead of DataFormatException -2; source SHA-256 {digest}")
    archive = ROOT / f".toolchain/downloads/teavm-classlib-{VERSION}-sources.jar"
    digest = "da6f83c1436c87a9ad683b6143aaa9c172b08c3974c9a7d5741a479d0d60092e"
    if hashlib.sha256(archive.read_bytes()).hexdigest() != digest:
        raise RuntimeError("TeaVM console source digest mismatch")
    path = "org/teavm/classlib/impl/console/JsConsolePrintStream.java"
    with zipfile.ZipFile(archive) as package:
        source = package.read(path).decode("utf-8")
    edits = [
        ("""            flushAction = null;
            flushAction.run();""", """            Runnable action = flushAction;
            flushAction = null;
            action.run();"""),
        ("""                buffer.put(b, off, count);
                len -= count;""", """                buffer.put(b, off, count);
                off += count;
                len -= count;"""),
        ("""        void write(byte[] b, int off, int len) {
            postponeFlush();""", """        void write(byte[] b, int off, int len) {
            Objects.checkFromIndexSize(off, len, b.length);
            postponeFlush();"""),
        ("""            buffer.put((byte) b);
        }""", """            buffer.put((byte) b);
            output();
        }"""),
        ("                output(true);", "                output(false);"),
        ("print(new String(s, begin, end));", "print(new String(s, begin, end - begin));"),
        ("        super.print(d);", "        print(Double.toString(d));"),
    ]
    for old,new in edits:
        if source.count(old) != 1:
            raise RuntimeError("Unexpected TeaVM console source shape")
        source = source.replace(old,new)
    destination = ROOT / "port/target/generated-sources/teavm" / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("// Local changes: byte slice advancement, flush action and streaming UTF-8.\n" + source)
    print(f"TeaVM {VERSION}: console byte slices and streaming UTF-8; source SHA-256 {digest}")


def patch_backend(package):
    path = "org/teavm/backend/wasm/intrinsics/reflection/ReflectionMetadataGenerator.java"
    source = package.read(path).decode("utf-8")
    old = "            generateGenericType(builder, cls, null, genericType);"
    new = """            if (genericType != null) {
                generateGenericType(builder, cls, null, genericType);
            } else {
                builder.nullConst(WasmType.STRUCT);
            }"""
    assert source.count(old) == 1
    source = source.replace(old, new)
    old = "                var builder = initFunction.getBody().builder();"
    new = """                // Keep each class's complete metadata together, but avoid
                // exceeding browser limits with one monolithic initializer.
                var classInit = new WasmFunction(functionTypes.of(null));
                classInit.setName(names.topLevel("teavm@initReflection@" + className));
                module.functions.add(classInit);
                initFunction.getBody().builder().call(classInit);
                var builder = classInit.getBody().builder();"""
    assert source.count(old) == 1
    source = source.replace(old, new)
    destination = ROOT / "port/target/generated-sources/teavm" / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("// Local changes: annotated raw fields and per-class reflection initialization.\n" + source)
    print("TeaVM: preserve raw field metadata and split reflection initialization by class")
    path = "org/teavm/backend/wasm/generate/classes/WasmGCClassGenerator.java"
    source = package.read(path).decode("utf-8")
    # Maven's TeaVM binaries relocate HPPC, while the source archive retains
    # original import names. Match the published binary method signatures.
    source = source.replace("com.carrotsearch.hppc.", "org.teavm.hppc.")
    marker = "            var vtStructure = classInfo.virtualTableStructure;"
    diagnostic = """            if (type.equals(OBJECT_TYPE)) {
                System.out.println("TeaVM root vtable: representative="
                        + (virtualTable != null ? virtualTable.getClassName() : "missing")
                        + ", concrete=" + (virtualTable != null && virtualTable.isConcrete())
                        + ", structure=" + (classInfo.virtualTableStructure != null));
            }
"""
    assert source.count(marker) == 1
    source = source.replace(marker, diagnostic + marker)
    destination = ROOT / "port/target/generated-sources/teavm" / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("// Local diagnostic: report root Object virtual table identity.\n" + source)
    path = "org/teavm/backend/wasm/vtable/WasmGCVirtualTableBuilder.java"
    source = package.read(path).decode("utf-8").replace("com.carrotsearch.hppc.", "org.teavm.hppc.")
    old = """                itf = itf.resolve();
                if (itf.commonImplementor == parent && !itf.interfaceMergedIntoClass) {"""
    new = """                itf = itf.resolve();
                // A marker interface can already resolve to its implementing class.
                // Do not merge that class into another interface at this level.
                if (!itf.cls.hasModifier(ElementModifier.INTERFACE)) {
                    continue;
                }
                if (itf.commonImplementor == parent && !itf.interfaceMergedIntoClass) {"""
    assert source.count(old) == 2
    source = source.replace(old, new)
    destination = ROOT / "port/target/generated-sources/teavm" / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("// Local change: resolved marker interfaces must not merge their class into interfaces.\n" + source)
    print("TeaVM: preserve class identity while merging marker interfaces")
    path = "org/teavm/backend/wasm/generate/methods/WasmGCMethodGenerator.java"
    source = package.read(path).decode("utf-8").replace("com.carrotsearch.hppc.", "org.teavm.hppc.")
    old = """            } else if (!method.hasModifier(ElementModifier.NATIVE)) {
                generateRegularMethodBody(method, function);"""
    new = """            } else if (method.hasModifier(ElementModifier.NATIVE)
                    && method.hasModifier(ElementModifier.STATIC)
                    && inlineIntrinsics.getIntrinsic(method.getReference()) != null) {
                // Reflection takes function references, so an inline-only native
                // intrinsic also needs a callable body. The self invocation is
                // expanded by the existing inline intrinsic during generation.
                var emitter = org.teavm.model.emit.ProgramEmitter.create(method, hierarchy);
                var arguments = new org.teavm.model.emit.ValueEmitter[method.parameterCount()];
                for (var i = 0; i < arguments.length; i++) {
                    arguments[i] = emitter.var(i + 1, method.parameterType(i));
                }
                var result = emitter.invoke(method.getReference(), arguments);
                if (method.getResultType() == ValueType.VOID) {
                    emitter.exit();
                } else {
                    result.returnValue();
                }
                generateRegularMethodBody(method, function);
            } else if (!method.hasModifier(ElementModifier.NATIVE)) {
                generateRegularMethodBody(method, function);"""
    assert source.count(old) == 1
    destination = ROOT / "port/target/generated-sources/teavm" / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("// Local change: callable bodies for reflected static inline intrinsics.\n" + source.replace(old, new))
    print("TeaVM: emit callable bodies for reflected static native inline intrinsics")
    path = "org/teavm/backend/wasm/generate/methods/WasmGCInstructionGenerationVisitor.java"
    source = package.read(path).decode("utf-8").replace("com.carrotsearch.hppc.", "org.teavm.hppc.")
    old = "            var exceptionType = (WasmType.Reference) context.classInfoProvider().getClassInfo(exType).getType();"
    new = """            if (context.classes().get(exType) == null) {
                context.diagnostics().error(new org.teavm.model.CallLocation(currentMethod),
                        "Class {{c0}} was not found", exType);
            }
            var exceptionType = (WasmType.Reference) context.classInfoProvider().getClassInfo(exType).getType();"""
    assert source.count(old) == 1
    destination = ROOT / "port/target/generated-sources/teavm" / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("// Local change: report missing catch types before claiming a valid module.\n" + source.replace(old, new))
    print("TeaVM: diagnose exception types missing from catch metadata")
    path = "org/teavm/backend/wasm/model/instruction/WasmTypeInference.java"
    source = package.read(path).decode("utf-8")
    old = "            if (ref.isNullable()) {"
    new = "            if (ref.isNullable() && !forceNullable) {"
    assert source.count(old) == 1
    source = source.replace(old, new)
    assert source.count("typeStack.add(") > 20
    source = source.replace("typeStack.add(", "push(").replace("typeStack.addAll(", "pushAll(")
    marker = "    private void pop() {"
    helpers = """    private void push(WasmType type) {
        typeStack.add(mapToNullable(type));
    }

    private void pushAll(List<? extends WasmType> types) {
        for (var type : types) {
            push(type);
        }
    }

"""
    assert source.count(marker) == 1
    source = source.replace(marker, helpers + marker)
    destination = ROOT / "port/target/generated-sources/teavm" / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("// Local change: preserve forced nullable coroutine types for every stack result.\n" + source)
    print("TeaVM: honor forced nullable inference for all coroutine stack results")


def patch_fiber():
    """The fiber's own stack growth must never be a coroutine."""
    path = "org/teavm/runtime/Fiber.java"
    with zipfile.ZipFile(ROOT / f".toolchain/downloads/teavm-core-{VERSION}-sources.jar") as package:
        source = package.read(path).decode("utf-8")
    old = """        } else if (objectTop + 1 == objectValues.length) {
            objectValues = Arrays.copyOf(objectValues, objectValues.length * 3 / 2);
        }"""
    new = """        } else if (objectTop + 1 == objectValues.length) {
            objectValues = growObjectStack(objectValues);
        }"""
    assert source.count(old) == 1
    source = source.replace(old, new)
    marker = "    public static void reversePush(int value, Fiber fiber) {"
    helper = """    // Local change: growing with Arrays.copyOf(T[], int) is fatal here. That classlib method
    // allocates through Array.newInstance(componentType, n), whose getClass() is asynchronous, so
    // TeaVM turns copyOf into a coroutine whose prologue calls Fiber.reversePush -> Fiber.push,
    // which grows the very same full stack again: unbounded mutual recursion until V8 throws
    // "RangeError: Maximum call stack size exceeded" (measured in round 29: 3369 cycles of
    // java.util.Arrays::copyOf / Fiber::push / Fiber::reversePush inside
    // zombie.core.opengl.RenderThread::renderLoop). A concrete Object[] allocation is an
    // allocation intrinsic that never suspends, so the growth stays synchronous.
    private static Object[] growObjectStack(Object[] values) {
        Object[] result = new Object[values.length * 3 / 2];
        for (int i = 0; i < values.length; ++i) {
            result[i] = values[i];
        }
        return result;
    }

"""
    assert source.count(marker) == 1
    source = source.replace(marker, helper + marker)
    import_marker = "import org.teavm.interop.AsyncCallback;"
    assert source.count(import_marker) == 1
    source = source.replace(import_marker, import_marker + "\nimport org.teavm.jso.JSBody;")
    # Local change: never resume a fiber from inside another fiber's live start(). In WasmGC,
    # AsyncCallbackImpl.complete/error calls fiber.resume() whenever Fiber.current is not the
    # awaited fiber, so the JS callback runs resume() -> start() nested inside the other parked
    # fiber's start(). The async prologue (CoroutineTransformation.generatePrologue:
    # isResuming ? popInt(...) : state = 0) then reads that other fiber's saved-int stack.
    # Deferring the resume through EventQueue (time 0 -> setTimeout 0) serializes every fiber
    # transition in a top-level macrotask, exactly like TThread.sleep already does. Semantics are
    # unchanged: resume still happens outside any Fiber.current, and nothing is simulated -- if the
    # timer never fires the fiber stays parked.
    old = """                setCurrentThread(javaThread);
                javaThread = null;
                Fiber fiber = this.fiber;
                this.fiber = null;
                fiber.result = result;
                removePendingCall();
                fiber.resume();"""
    new = """                Fiber fiber = this.fiber;
                this.fiber = null;
                Thread thread = javaThread;
                javaThread = null;
                fiber.result = result;
                removePendingCall();
                EventQueue.offer(new ResumeEvent(thread, fiber));"""
    assert source.count(old) == 1, "Fiber.complete handoff changed"
    source = source.replace(old, new)
    old = """                setCurrentThread(javaThread);
                javaThread = null;
                Fiber fiber = this.fiber;
                this.fiber = null;
                fiber.exception = e;
                removePendingCall();
                fiber.resume();"""
    new = """                Fiber fiber = this.fiber;
                this.fiber = null;
                Thread thread = javaThread;
                javaThread = null;
                fiber.exception = e;
                removePendingCall();
                EventQueue.offer(new ResumeEvent(thread, fiber));"""
    assert source.count(old) == 1, "Fiber.error handoff changed"
    source = source.replace(old, new)
    resume_event = """    private static final class ResumeEvent implements EventQueue.Event {
        private Thread thread;
        private Fiber fiber;

        ResumeEvent(Thread thread, Fiber fiber) {
            this.thread = thread;
            this.fiber = fiber;
        }

        @Override
        public void run() {
            Thread thread = this.thread;
            this.thread = null;
            Fiber fiber = this.fiber;
            this.fiber = null;
            setCurrentThread(thread);
            fiber.resume();
        }
    }

"""
    end_marker = """        private void removePendingCall() {"""
    assert source.count(end_marker) == 1
    source = source.replace(end_marker, resume_event + end_marker)
    # Diagnostics (round 33): the deferred resume above did not remove the crash, so the
    # out-of-bounds pop must report the counters that identify the corruptor. No string building:
    # StringBuilder.append is itself a coroutine in the pinned classlib, so composing a message
    # inside popInt re-enters popInt and overflows the JS stack (measured: RangeError inside
    # Fiber::describe). The counters go straight to the console through a plain JS body.
    fields_marker = """    private static Fiber current;
    private static PendingCall lastPendingCall;"""
    fields = """    private static Fiber current;
    private static PendingCall lastPendingCall;
    static int fiberIdCounter;
    static int lastSuspendFiber;
    static int lastResumeFiber;
    static int lastResumeFrom;
    static int startDepth;
    static int maxStartDepth;
    static int nestedStarts;
    int fiberId;
    int suspendCount;
    int resumeCount;
    int intPushes;
    int intPops;
    int doubleResumes;"""
    assert source.count(fields_marker) == 1
    source = source.replace(fields_marker, fields)
    ctor_marker = """    private Fiber(FiberRunner runner, boolean daemon) {
        this.runner = runner;
        this.daemon = daemon;
    }"""
    assert source.count(ctor_marker) == 1
    source = source.replace(ctor_marker, """    private Fiber(FiberRunner runner, boolean daemon) {
        this.runner = runner;
        this.daemon = daemon;
        this.fiberId = ++fiberIdCounter;
    }""")
    push_marker = "        intValues[intTop++] = value;"
    assert source.count(push_marker) == 1
    source = source.replace(push_marker, "        ++intPushes;\n        intValues[intTop++] = value;")
    popint_marker = """    @Unmanaged
    public int popInt() {
        return intValues[--intTop];
    }"""
    assert source.count(popint_marker) == 1
    source = source.replace(popint_marker, """    public int popInt() {
        if (intTop <= 0) {
            Fiber other = current;
            reportUnderflow(fiberId, state, intTop, intPushes, intPops, suspendCount, resumeCount,
                    doubleResumes,
                    other == null ? -1 : other.fiberId,
                    other == null ? -1 : other.state,
                    other == null ? -1 : other.intTop,
                    other == null ? -1 : other.intPushes,
                    other == null ? -1 : other.intPops,
                    other == null ? -1 : other.suspendCount,
                    other == null ? -1 : other.resumeCount,
                    lastSuspendFiber, lastResumeFiber, lastResumeFrom, startDepth, maxStartDepth,
                    nestedStarts, lastPendingCall == null ? 0 : 1);
            throw new IllegalStateException("Fiber int-stack underflow (see console pzFiberDiag)");
        }
        ++intPops;
        return intValues[--intTop];
    }

    @JSBody(params = {"selfId", "selfState", "selfIntTop", "selfPush", "selfPop", "selfSuspend",
            "selfResume", "selfDoubleResume", "curId", "curState", "curIntTop", "curPush", "curPop",
            "curSuspend", "curResume", "lastSuspendFiber", "lastResumeFiber", "lastResumeFrom",
            "startDepth", "maxStartDepth", "nestedStarts", "pendingCall"},
            script = "globalThis.pzFiberDiag = { selfId: selfId, selfState: selfState,"
                    + " selfIntTop: selfIntTop, selfPush: selfPush, selfPop: selfPop,"
                    + " selfSuspend: selfSuspend, selfResume: selfResume,"
                    + " selfDoubleResume: selfDoubleResume, curId: curId, curState: curState,"
                    + " curIntTop: curIntTop, curPush: curPush, curPop: curPop,"
                    + " curSuspend: curSuspend, curResume: curResume,"
                    + " lastSuspendFiber: lastSuspendFiber, lastResumeFiber: lastResumeFiber,"
                    + " lastResumeFrom: lastResumeFrom, startDepth: startDepth,"
                    + " maxStartDepth: maxStartDepth, nestedStarts: nestedStarts,"
                    + " pendingCall: pendingCall };"
                    + " console.log('pzFiberDiag ' + JSON.stringify(globalThis.pzFiberDiag));")
    private static native void reportUnderflow(int selfId, int selfState, int selfIntTop, int selfPush,
            int selfPop, int selfSuspend, int selfResume, int selfDoubleResume, int curId, int curState,
            int curIntTop, int curPush, int curPop, int curSuspend, int curResume, int lastSuspendFiber,
            int lastResumeFiber, int lastResumeFrom, int startDepth, int maxStartDepth, int nestedStarts,
            int pendingCall);""")
    suspend_marker = """        Thread javaThread = Thread.currentThread();
        if (fiber.isResuming()) {"""
    assert source.count(suspend_marker) == 1
    source = source.replace(suspend_marker, """        Thread javaThread = Thread.currentThread();
        ++fiber.suspendCount;
        lastSuspendFiber = fiber.fiberId;
        if (fiber.isResuming()) {""")
    resume_marker = """    void resume() {
        state = STATE_RESUMING;
        start();
    }"""
    park_marker = "        PendingCall pendingCall = new PendingCall(call, lastPendingCall);"
    assert source.count(park_marker) == 1
    source = source.replace(park_marker, """        reportSuspend(fiber.fiberId, fiber.state, fiber.suspendCount, pendingDepth());
""" + park_marker)
    assert source.count(resume_marker) == 1
    source = source.replace(resume_marker, """    void resume() {
        ++resumeCount;
        lastResumeFiber = fiberId;
        lastResumeFrom = current == null ? -1 : current.fiberId;
        if (state == STATE_RESUMING) {
            ++doubleResumes;
        }
        state = STATE_RESUMING;
        reportResume(fiberId, resumeCount, state, lastResumeFrom);
        start();
    }

    private static int pendingDepth() {
        int depth = 0;
        PendingCall call = lastPendingCall;
        while (call != null) {
            ++depth;
            call = call.next;
        }
        return depth;
    }

    @JSBody(params = {"fiberId", "state", "suspendCount", "pendingDepth"},
            script = "var rec = globalThis.pzFiberSuspend || (globalThis.pzFiberSuspend = {});"
                    + " var entry = rec[fiberId] || (rec[fiberId] = {id: fiberId, suspends: 0,"
                    + " resumes: 0, stacks: []});"
                    + " entry.suspends = suspendCount; entry.state = state;"
                    + " entry.pendingDepth = pendingDepth; entry.parked = true;"
                    + " entry.t = performance.now();"
                    + " var lines = String((new Error()).stack || '').split('\\\\n').slice(1, 26)"
                    + "     .map(function (s) { return s.trim(); });"
                    + " entry.stacks.push(lines);"
                    + " if (entry.stacks.length > 6) { entry.stacks.shift(); }")
    private static native void reportSuspend(int fiberId, int state, int suspendCount, int pendingDepth);

    @JSBody(params = {"fiberId", "resumeCount", "state", "resumeFrom"},
            script = "var rec = globalThis.pzFiberSuspend || (globalThis.pzFiberSuspend = {});"
                    + " var entry = rec[fiberId] || (rec[fiberId] = {id: fiberId, suspends: 0,"
                    + " resumes: 0, stacks: []});"
                    + " entry.resumes = resumeCount; entry.state = state;"
                    + " entry.resumeFrom = resumeFrom; entry.parked = false;")
    private static native void reportResume(int fiberId, int resumeCount, int state, int resumeFrom);""")
    start_marker = """    private void start() {
        Fiber former = current;
        current = this;"""
    assert source.count(start_marker) == 1
    source = source.replace(start_marker, """    private void start() {
        Fiber former = current;
        ++startDepth;
        if (startDepth > maxStartDepth) {
            maxStartDepth = startDepth;
        }
        if (former != null) {
            ++nestedStarts;
        }
        try {
            current = this;""")
    start_end_marker = """        current = former;
        if (!isSuspending() && !daemon && --userThreadCount == 0) {
            EventQueue.stop();
        }
    }"""
    assert source.count(start_end_marker) == 1
    source = source.replace(start_end_marker, """        current = former;
        } finally {
            --startDepth;
        }
        if (!isSuspending() && !daemon && --userThreadCount == 0) {
            EventQueue.stop();
        }
    }""")
    # The concrete Object[] allocation makes Array<java.lang.Object>@new reachable even when
    # Array.newInstance is not, and then the upstream generator adds a null supertype.
    generator_path = "org/teavm/backend/wasm/generate/classes/WasmGCNewArrayFunctionGenerator.java"
    with zipfile.ZipFile(ROOT / f".toolchain/downloads/teavm-core-{VERSION}-sources.jar") as generator_package:
        generator = generator_package.read(generator_path).decode("utf-8")
    old = "functionType.getSupertypes().add(classInfoType.newArrayFunctionType());"
    new = """var declared = classInfoType.newArrayFunctionType();
            if (declared != null) {
                functionType.getSupertypes().add(declared);
            }"""
    assert generator.count(old) == 2
    generator = generator.replace(old, new)
    generator_destination = ROOT / "port/target/generated-sources/teavm" / generator_path
    generator_destination.parent.mkdir(parents=True, exist_ok=True)
    generator_destination.write_text(
        "// Local change: the declared newArray supertype only exists when Array.newInstance is reachable.\n"
        + generator)
    print("TeaVM: array allocation function tolerates an undeclared newArray supertype")
    destination = ROOT / "port/target/generated-sources/teavm" / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(
        "// Local change: synchronous fiber object-stack growth; deferred cross-fiber resume;\n"
        "// per-fiber park diagnostics (pzFiberSuspend) added in round 37.\n"
        + source)
    print("TeaVM: fiber object-stack growth no longer goes through the async Arrays.copyOf(T[],int)")
    print("TeaVM: AsyncCallbackImpl resumes through EventQueue instead of nesting start() in another fiber")

def patch_default_path():
    """java.nio.file.Path.normalize() must accept relative paths.

    Round 79, measured in Chrome (symbolized with tools/diag/wasm_addr.mjs): the game's
    LuaManager$GlobalObject.reloadLuaFile -> ZomboidFileSystem.validatePrefix -> normalizeToPath
    calls Paths.get("media/maps/Muldraugh, KY/spawnpoints.lua").normalize(). Upstream
    TDefaultPath.normalize copies every kept segment together with its leading separator from
    start - 1; for the first segment of a relative path start == 0, so String.getChars(-1, ...)
    throws IndexOutOfBoundsException and the spawn-point list never loads. The JDK returns the
    relative path unchanged. Absolute paths and ".." handling are unchanged.
    """
    archive = ROOT / f".toolchain/downloads/teavm-classlib-{VERSION}-sources.jar"
    digest = "da6f83c1436c87a9ad683b6143aaa9c172b08c3974c9a7d5741a479d0d60092e"
    if hashlib.sha256(archive.read_bytes()).hexdigest() != digest:
        raise RuntimeError("TeaVM path source digest mismatch")
    path = "org/teavm/classlib/java/nio/file/impl/TDefaultPath.java"
    with zipfile.ZipFile(archive) as package:
        source = package.read(path).decode("utf-8")
    old = """                resultSegments[resultSegmentIndex++] = resultIndex;
                pathString.getChars(start - 1, end, result, resultIndex);
                resultIndex += end - start + 1;"""
    new = """                resultSegments[resultSegmentIndex++] = resultIndex;
                if (resultIndex == 0 && !abs) {
                    // First kept segment of a relative path: there is no separator before it.
                    pathString.getChars(start, end, result, 0);
                    resultIndex = end - start;
                } else {
                    result[resultIndex++] = fs.getSeparatorChar();
                    pathString.getChars(start, end, result, resultIndex);
                    resultIndex += end - start;
                }"""
    if source.count(old) != 1:
        raise RuntimeError("Unexpected TeaVM path source shape")
    source = source.replace(old, new)
    destination = ROOT / "port/target/generated-sources/teavm" / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("// Local change: normalize() of relative paths (no separator before the first segment).\n" + source)
    print(f"TeaVM {VERSION}: Path.normalize accepts relative paths; source SHA-256 {digest}")


def patch_zip_inflater_stream():
    """ZipFile entry streams must give the raw inflater one dummy byte at end of input.

    Round 79, measured in Chrome (symbolized): ImagePyramid.readInfoFile reads a DEFLATED entry of
    media/maps/Muldraugh, KY/spawnSelectImagePyramid.zip through TZipFile.getInputStream ->
    ZipInflaterInputStream and gets EOFException once the entry's compressed bytes are consumed:
    the nowrap inflater has not reported finished() yet. The JDK's ZipFile does exactly this in
    ZipFileInflaterInputStream.fill(): when the entry is exhausted it feeds a single 0 byte
    ("Inflater likes a bit of slack") before declaring EOF.
    """
    archive = ROOT / f".toolchain/downloads/teavm-classlib-{VERSION}-sources.jar"
    digest = "da6f83c1436c87a9ad683b6143aaa9c172b08c3974c9a7d5741a479d0d60092e"
    if hashlib.sha256(archive.read_bytes()).hexdigest() != digest:
        raise RuntimeError("TeaVM zip source digest mismatch")
    path = "org/teavm/classlib/java/util/zip/TZipFile.java"
    with zipfile.ZipFile(archive) as package:
        source = package.read(path).decode("utf-8")
    old = """        public ZipInflaterInputStream(InputStream is, TInflater inf, int bsize, TZipEntry entry) {
            super(is, inf, bsize);
            this.entry = entry;
        }
"""
    new = """        public ZipInflaterInputStream(InputStream is, TInflater inf, int bsize, TZipEntry entry) {
            super(is, inf, bsize);
            this.entry = entry;
        }

        private boolean dummyByteSent;

        @Override
        protected void fill() throws IOException {
            super.fill();
            if (len == -1 && !dummyByteSent) {
                // As java.util.zip.ZipFile: the raw inflater needs one extra byte after the entry.
                dummyByteSent = true;
                buf[0] = 0;
                len = 1;
                inf.setInput(buf, 0, 1);
            }
        }
"""
    if source.count(old) != 1:
        raise RuntimeError("Unexpected TeaVM zip source shape")
    source = source.replace(old, new)
    destination = ROOT / "port/target/generated-sources/teavm" / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("// Local change: dummy trailing byte for zip entry inflation (JDK ZipFile behaviour).\n" + source)
    print(f"TeaVM {VERSION}: zip entry streams feed the JDK dummy trailing byte; source SHA-256 {digest}")


def patch_dependency_filter(package):
    """Round 80 (build speed only): memoize DependencyAnalyzer.filterType per DependencyType.

    JFR of a --with-game build (build-round80g-dev1): dependency analysis is 364 s of 403 s and
    DependencyAnalyzer.filterType -- classFilters.stream().allMatch(...) on EVERY type propagation --
    is ~38% of the samples (stream machinery + JSOPlugin's filter -> JSTypeHelper.isJavaScriptClass).
    The only ClassFilter in 0.16.0 is JSOPlugin$1: a pure function of the class name over the
    unprocessed class source, so its answer for a DependencyType (interned, unique index) never
    changes. Opt-in with -Dpz.teavm.filterCache=true (scripts/build.sh --dev); without it the
    original stream path runs unchanged. addClassFilter clears the cache.
    """
    analyzer_path = "org/teavm/dependency/DependencyAnalyzer.java"
    node_path = "org/teavm/dependency/DependencyNode.java"
    # Maven's TeaVM binaries relocate HPPC and ASM; the source archive keeps the upstream packages.
    relocate = lambda text: text.replace("com.carrotsearch.hppc.", "org.teavm.hppc.").replace("org.objectweb.asm.", "org.teavm.asm.")
    analyzer = relocate(package.read(analyzer_path).decode("utf-8"))
    node = relocate(package.read(node_path).decode("utf-8"))
    old = """    boolean filterType(ValueType type) {
        return classFilters.stream().allMatch(filter -> filter.accept(filterContext, type));
    }"""
    new = """    boolean filterType(ValueType type) {
        return classFilters.stream().allMatch(filter -> filter.accept(filterContext, type));
    }

    private static final boolean FILTER_CACHE = Boolean.getBoolean("pz.teavm.filterCache");
    private byte[] filterCache = new byte[0];

    boolean filterType(DependencyType type) {
        if (!FILTER_CACHE) {
            return filterType(type.getValueType());
        }
        int index = type.index;
        if (index < filterCache.length && filterCache[index] != 0) {
            return filterCache[index] > 0;
        }
        boolean result = true;
        for (ClassFilter filter : classFilters) {
            if (!filter.accept(filterContext, type.getValueType())) {
                result = false;
                break;
            }
        }
        if (index >= filterCache.length) {
            filterCache = Arrays.copyOf(filterCache, Math.max(index + 1, filterCache.length * 2 + 64));
        }
        filterCache[index] = result ? (byte) 1 : (byte) -1;
        return result;
    }"""
    if analyzer.count(old) != 1 or analyzer.count("filterType(type.getValueType())") != 4:
        raise RuntimeError("Unexpected TeaVM DependencyAnalyzer.filterType shape")
    analyzer = analyzer.replace(old, new).replace("filterType(type.getValueType())", "filterType(type)")
    # The overload delegating with FILTER_CACHE off must still call the ValueType version.
    analyzer = analyzer.replace("        if (!FILTER_CACHE) {\n            return filterType(type);", "        if (!FILTER_CACHE) {\n            return filterType(type.getValueType());")
    old = "        classFilters.add(filter);"
    if analyzer.count(old) != 1:
        raise RuntimeError("Unexpected TeaVM addClassFilter shape")
    analyzer = analyzer.replace(old, old + "\n        filterCache = new byte[0];")
    if node.count("dependencyAnalyzer.filterType(type.getValueType())") != 2:
        raise RuntimeError("Unexpected TeaVM DependencyNode.filterType callers")
    node = node.replace("dependencyAnalyzer.filterType(type.getValueType())", "dependencyAnalyzer.filterType(type)")
    for path, source in [(analyzer_path, analyzer), (node_path, node)]:
        destination = ROOT / "port/target/generated-sources/teavm" / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text("// Local change: opt-in memoized class filter (pz.teavm.filterCache); semantics unchanged.\n" + source, encoding="utf-8")
    print("TeaVM: opt-in memoized DependencyAnalyzer.filterType (pz.teavm.filterCache)")


def main():
    archive = ROOT / f".toolchain/downloads/teavm-core-{VERSION}-sources.jar"
    if not archive.exists():
        archive.parent.mkdir(parents=True, exist_ok=True)
        url = f"https://repo.maven.apache.org/maven2/org/teavm/teavm-core/{VERSION}/{archive.name}"
        with urllib.request.urlopen(url, timeout=60) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != SHA256:
            raise RuntimeError("Unexpected TeaVM source archive digest")
        archive.write_bytes(data)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != SHA256:
        raise RuntimeError("TeaVM source archive digest mismatch")
    with zipfile.ZipFile(archive) as package:
        source = package.read(SOURCE).decode("utf-8")
        # DependencyAnalyzer interns types using a unique, monotonically assigned
        # index per analysis. Each consumer/set belongs to that same analysis.
        analyzer = package.read("org/teavm/dependency/DependencyAnalyzer.java").decode("utf-8")
        patch_backend(package)
        patch_dependency_filter(package)
    if "new DependencyType(valueType, types.size())" not in analyzer:
        raise RuntimeError("TeaVM type-index invariant changed")
    old = "private Set<DependencyType> knownTypes = new HashSet<>();"
    new = "private org.teavm.dependency.CompactTypeSet knownTypes = new org.teavm.dependency.CompactTypeSet();"
    if source.count(old) != 3:
        raise RuntimeError("Unexpected TeaVM reflection cache declarations")
    patched = "// Local change: compact seen-type caches; reflection semantics unchanged.\n" + source.replace(old, new)
    destination = ROOT / "port/target/generated-sources/teavm" / SOURCE
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(patched, encoding="utf-8")
    print(f"TeaVM {VERSION}: compact reflection caches (3); source SHA-256 {SHA256}")
    patch_monitor()
    patch_console()
    patch_inflater()
    patch_default_path()
    patch_zip_inflater_stream()
    patch_fiber()


if __name__ == "__main__":
    main()
