package port.compiler;

import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;

/**
 * Platform adaptation for the LWJGL 3.4.1 copy inside the pinned fat jar. TeaVM's classlib
 * reports os.name="TeaVM" and no os.arch, so LWJGL's own detection raises
 * LinkageError: Unknown platform: TeaVM in Platform.&lt;clinit&gt;, which stops
 * GLFWImage/Struct and therefore MainScreenState.loadIcons. Changing os.name globally is
 * forbidden by docs/decisions.md, so this transformer gives the browser's real platform facts
 * to the original LWJGL classes:
 *
 *  - Platform: the four constants are built exactly as upstream does; current is LINUX because
 *    this port's own semantics are little-endian, 64-bit addresses and "lib*.so" names; the
 *    bundled-library mappers become identity because a browser module has no native-path tree.
 *  - Platform$Architecture: the same six constants, current=X64, which is the host ISA this
 *    wasm module is compiled for; os.arch is absent in TeaVM and is never read here.
 *  - Pointer: 8-byte pointer and long sizes (LP64), matching port.runtime.memory.NativeArena,
 *    where addresses are 64-bit longs over little-endian direct buffers. The host native
 *    MemoryAccessJNI.getPointerSize()I is not consulted and keeps its fail-fast JNI guard.
 *  - MemoryAccessJNI: there is no host allocator, so the six function-pointer fields stay 0 and
 *    every allocator/pointer native still raises with its JNI name.
 *  - Library: the host-layout fields are computed from the adapted platform, and the upstream
 *    loadSystem("org.lwjgl", ...) of &lt;clinit&gt; is dropped. Every loadSystem/loadNative method
 *    raises: a browser tab really cannot load an ELF library, so nothing may look initialised.
 *
 * Game rules are untouched; this is only the platform layer.
 */
public final class LwjglPlatformCompatibility implements ClassHolderTransformer {
    private static final String PLATFORM = "org.lwjgl.system.Platform";
    private static final String ARCHITECTURE = PLATFORM + "$Architecture";
    private static final String POINTER = "org.lwjgl.system.Pointer";
    private static final String MEMORY_ACCESS_JNI = "org.lwjgl.system.MemoryAccessJNI";
    private static final String LIBRARY = "org.lwjgl.system.Library";
    private static final ValueType STRING = ValueType.object("java.lang.String");
    private static final ValueType OBJECT = ValueType.object("java.lang.Object");
    private static final ValueType FUNCTION = ValueType.object("java.util.function.Function");
    private static final ValueType PATTERN = ValueType.object("java.util.regex.Pattern");
    private static final String BUILDER = "java.lang.StringBuilder";
    private static final ValueType BUILDER_TYPE = ValueType.object(BUILDER);

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        switch (cls.getName()) {
            case PLATFORM -> rewritePlatform(cls, context);
            case ARCHITECTURE -> rewriteArchitecture(cls, context);
            case POINTER -> rewritePointer(cls, context);
            case MEMORY_ACCESS_JNI -> rewriteMemoryAccess(cls, context);
            case LIBRARY -> rewriteLibrary(cls, context);
            default -> { }
        }
    }

    private void rewritePlatform(ClassHolder cls, ClassHolderTransformerContext context) {
        var clinit = requireClinit(cls, PLATFORM);
        var emitter = ProgramEmitter.create(clinit, context.getHierarchy());
        // Same constants, order, display names and native paths as upstream Platform.<clinit>.
        ValueEmitter freebsd = emitter.construct(PLATFORM + "$1",
            emitter.constant("FREEBSD"), emitter.constant(0), emitter.constant("FreeBSD"), emitter.constant("freebsd"));
        ValueEmitter linux = emitter.construct(PLATFORM + "$2",
            emitter.constant("LINUX"), emitter.constant(1), emitter.constant("Linux"), emitter.constant("linux"));
        ValueEmitter macosx = emitter.construct(PLATFORM + "$3",
            emitter.constant("MACOSX"), emitter.constant(2), emitter.constant("macOS"), emitter.constant("macos"));
        ValueEmitter windows = emitter.construct(PLATFORM + "$4",
            emitter.constant("WINDOWS"), emitter.constant(3), emitter.constant("Windows"), emitter.constant("windows"));
        ValueEmitter values = emitter.constructArray(ValueType.object(PLATFORM), 4);
        values.setElement(0, freebsd); values.setElement(1, linux); values.setElement(2, macosx); values.setElement(3, windows);
        emitter.setField(PLATFORM, "$VALUES", values);
        emitter.setField(PLATFORM, "current", linux);
        // No bundled native-path hierarchy exists in a browser module, so names stay as given.
        var identity = emitter.invoke(new MethodReference("java.util.function.Function", "identity", FUNCTION));
        emitter.setField(PLATFORM, "bundledLibraryNameMapper", identity);
        emitter.setField(PLATFORM, "bundledLibraryPathMapper", identity);
        emitter.exit();
    }

    private void rewriteArchitecture(ClassHolder cls, ClassHolderTransformerContext context) {
        var clinit = requireClinit(cls, ARCHITECTURE);
        var emitter = ProgramEmitter.create(clinit, context.getHierarchy());
        var type = ValueType.object(ARCHITECTURE);
        ValueEmitter x64 = constant(emitter, type, "X64", 0, true);
        ValueEmitter x86 = constant(emitter, type, "X86", 1, false);
        ValueEmitter arm64 = constant(emitter, type, "ARM64", 2, true);
        ValueEmitter arm32 = constant(emitter, type, "ARM32", 3, false);
        ValueEmitter ppc64le = constant(emitter, type, "PPC64LE", 4, true);
        ValueEmitter riscv64 = constant(emitter, type, "RISCV64", 5, true);
        ValueEmitter values = emitter.constructArray(type, 6);
        values.setElement(0, x64); values.setElement(1, x86); values.setElement(2, arm64);
        values.setElement(3, arm32); values.setElement(4, ppc64le); values.setElement(5, riscv64);
        emitter.setField(ARCHITECTURE, "$VALUES", values);
        emitter.setField(ARCHITECTURE, "current", x64);
        emitter.exit();
    }

    private static ValueEmitter constant(ProgramEmitter emitter, ValueType type, String name, int ordinal, boolean is64Bit) {
        return emitter.construct(ARCHITECTURE, emitter.constant(name), emitter.constant(ordinal),
            emitter.constant(is64Bit ? 1 : 0).cast(ValueType.BOOLEAN));
    }

    private void rewritePointer(ClassHolder cls, ClassHolderTransformerContext context) {
        var clinit = requireClinit(cls, POINTER);
        var emitter = ProgramEmitter.create(clinit, context.getHierarchy());
        // LP64 sizes of this port's own memory model: 64-bit addresses, little-endian buffers.
        emitter.setField(POINTER, "POINTER_SIZE", emitter.constant(8));
        emitter.setField(POINTER, "POINTER_SHIFT", emitter.constant(3));
        emitter.setField(POINTER, "CLONG_SIZE", emitter.constant(8));
        emitter.setField(POINTER, "CLONG_SHIFT", emitter.constant(3));
        emitter.setField(POINTER, "BITS32", emitter.constant(0).cast(ValueType.BOOLEAN));
        emitter.setField(POINTER, "BITS64", emitter.constant(1).cast(ValueType.BOOLEAN));
        emitter.exit();
    }

    private void rewriteMemoryAccess(ClassHolder cls, ClassHolderTransformerContext context) {
        var clinit = requireClinit(cls, MEMORY_ACCESS_JNI);
        var emitter = ProgramEmitter.create(clinit, context.getHierarchy());
        // No host allocator exists, so no allocator function pointer can be looked up. The
        // allocator and nget/nput natives keep the fail-fast guards of FailFastNatives.
        for (String field : new String[] {"malloc", "calloc", "realloc", "free", "aligned_alloc", "aligned_free"})
            emitter.setField(MEMORY_ACCESS_JNI, field, emitter.constant(0L));
        emitter.exit();
    }

    private void rewriteLibrary(ClassHolder cls, ClassHolderTransformerContext context) {
        var clinit = requireClinit(cls, LIBRARY);
        var emitter = ProgramEmitter.create(clinit, context.getHierarchy());
        // Upstream: Configuration.LIBRARY_NAME.get(Platform.mapLibraryNameBundled("lwjgl")).
        var defaultName = emitter.invoke(new MethodReference(PLATFORM, "mapLibraryNameBundled", STRING, STRING), emitter.constant("lwjgl"));
        var configured = emitter.getField("org.lwjgl.system.Configuration", "LIBRARY_NAME",
            ValueType.object("org.lwjgl.system.Configuration"));
        var name = configured.invokeVirtual(new MethodReference("org.lwjgl.system.Configuration", "get", OBJECT, OBJECT), defaultName)
            .cast(STRING);
        emitter.setField(LIBRARY, "JNI_LIBRARY_NAME", name);
        var pathSeparator = emitter.getField("java.io.File", "pathSeparator", STRING);
        emitter.setField(LIBRARY, "PATH_SEPARATOR",
            emitter.invoke(new MethodReference("java.util.regex.Pattern", "compile", STRING, PATTERN), pathSeparator));
        emitter.setField(LIBRARY, "NATIVES_JAR",
            emitter.invoke(new MethodReference("java.util.regex.Pattern", "compile", STRING, PATTERN), emitter.constant("/[\\w-]+?-natives-\\w+.jar!/")));
        // Upstream: platform.name().toLowerCase() + "/" + architecture.name().toLowerCase().
        var builder = emitter.construct(BUILDER);
        var platformName = emitter.invoke(new MethodReference(PLATFORM, "get", ValueType.object(PLATFORM)))
            .invokeVirtual(new MethodReference(PLATFORM, "name", STRING))
            .invokeVirtual(new MethodReference("java.lang.String", "toLowerCase", STRING));
        var architectureName = emitter.invoke(new MethodReference(PLATFORM, "getArchitecture", ValueType.object(ARCHITECTURE)))
            .invokeVirtual(new MethodReference(ARCHITECTURE, "name", STRING))
            .invokeVirtual(new MethodReference("java.lang.String", "toLowerCase", STRING));
        var manifest = builder
            .invokeVirtual(new MethodReference(BUILDER, "append", STRING, BUILDER_TYPE), platformName)
            .invokeVirtual(new MethodReference(BUILDER, "append", STRING, BUILDER_TYPE), emitter.constant("/"))
            .invokeVirtual(new MethodReference(BUILDER, "append", STRING, BUILDER_TYPE), architectureName)
            .invokeVirtual(new MethodReference(BUILDER, "toString", STRING));
        emitter.setField(LIBRARY, "EXPECTED_MANIFEST_PLATFORM", manifest);
        // The upstream body ends with loadSystem("org.lwjgl", JNI_LIBRARY_NAME); that host load
        // cannot happen here, so it is not emitted and loadSystem below raises on any call.
        emitter.exit();
        for (var method : cls.getMethods()) {
            String methodName = method.getName();
            if (!methodName.startsWith("loadSystem") && !methodName.startsWith("loadNative")) continue;
            var failure = ProgramEmitter.create(method, context.getHierarchy());
            var message = failure.construct(BUILDER)
                .invokeVirtual(new MethodReference(BUILDER, "append", STRING, BUILDER_TYPE),
                    failure.constant("Browser cannot load host LWJGL libraries: " + LIBRARY + "." + method.getDescriptor() + " name="));
            for (int index = 1; index <= method.parameterCount(); index++) {
                if (!method.parameterType(index - 1).equals(STRING)) continue;
                message = message.invokeVirtual(new MethodReference(BUILDER, "append", STRING, BUILDER_TYPE),
                    failure.invoke(new MethodReference("java.lang.String", "valueOf", OBJECT, STRING), failure.var(index, STRING)));
            }
            failure.construct(UnsupportedOperationException.class,
                message.invokeVirtual(new MethodReference(BUILDER, "toString", STRING))).raise();
        }
    }

    private static MethodHolder requireClinit(ClassHolder cls, String owner) {
        var clinit = cls.getMethod(new MethodDescriptor("<clinit>", ValueType.VOID));
        if (clinit == null) throw new IllegalStateException("Inventoried " + owner + ".<clinit> is missing");
        return clinit;
    }
}
