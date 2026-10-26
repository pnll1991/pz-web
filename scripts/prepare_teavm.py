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


if __name__ == "__main__":
    main()
