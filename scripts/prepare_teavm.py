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
    # Round 37 diagnostics: the render fiber is parked in RenderContextQueueItem.invoke()'s
    # monitorenter (Object.monitorEnterWait) while the game fiber is parked in the same object's
    # Object.wait. Record the monitor state at every grant, wait, release, re-acquire and notify so
    # the deadlock is identified instead of guessed. Ints only: a String constant in a replayed
    # coroutine frame is not materialized yet (round 32), and StringBuilder.append is a coroutine.
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
        ("""    static void monitorEnter(TObject o, int count) {
        if (o.monitor == null) {""",
         """    static void monitorEnter(TObject o, int count) {
        reportMonitor(10, o.monitor == null ? 0 : o.monitor.id,
                o.monitor == null || o.monitor.owner == null ? 0 : (int) o.monitor.owner.getId(),
                (int) TThread.currentThread().getId(), o.monitor == null ? 0 : o.monitor.count, 0,
                o.monitor == null || o.monitor.notifyListeners == null ? 0
                        : o.monitor.notifyListeners.size());
        if (o.monitor == null) {"""),
        ("""    static void monitorExit(TObject o, int count) {
        if (o.isEmptyMonitor() || o.monitor.owner != TThread.currentThread()) {""",
         """    static void monitorExit(TObject o, int count) {
        reportMonitor(11, o.monitor == null ? 0 : o.monitor.id,
                o.monitor == null || o.monitor.owner == null ? 0 : (int) o.monitor.owner.getId(),
                (int) TThread.currentThread().getId(), o.monitor == null ? 0 : o.monitor.count, 0,
                o.monitor == null || o.monitor.notifyListeners == null ? 0
                        : o.monitor.notifyListeners.size());
        if (o.isEmptyMonitor() || o.monitor.owner != TThread.currentThread()) {"""),
        ("""    private static void createMonitor(TObject o) {""",
         """    private static void createMonitor(TObject o) {
        reportMonitor(12, 0, 0, (int) TThread.currentThread().getId(), 0, 0, 0);"""),
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
            reportMonitor(7, obj.monitor == null ? 0 : obj.monitor.id, 0,
                    (int) currentThread.getId(), obj.monitor == null ? 0 : obj.monitor.count, 0, 0);
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
    ]
    for old, new in diag_edits:
        if source.count(old) != 1:
            raise RuntimeError("Unexpected TeaVM monitor shape: " + old[:60])
        source = source.replace(old, new)
    js_diag = """    private static void reportMonitor(int phase, int objId, int ownerId, int threadId,
            int count, int entering, int notify) {
        port.shim.BrowserMonitor.record(phase, objId, ownerId, threadId, count, entering, notify);
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
    patch_fiber()


if __name__ == "__main__":
    main()
