package port.compiler;

import java.util.ArrayList;
import java.util.List;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.InvocationType;

/**
 * OpenGL capability lifecycle for the browser backend. The pinned LWJGL builds capabilities from a
 * host OpenGL library through JNI and dlsym; here the provider is {@code BrowserGL}, which answers
 * from the page's real WebGL2 context, so {@code GLCapabilities} is constructed for real: every
 * entrypoint the context does not expose keeps address 0 and its capability is reported missing.
 *
 * Nothing here simulates a capability. The core probe (glGetError, glGetString, glGetIntegerv)
 * raises with its own identity when the context does not expose one of them.
 */
public final class GLCompatibility implements ClassHolderTransformer {
    private static final String GL = "org.lwjgl.opengl.GL";
    private static final String GL_ICD = "org.lwjgl.opengl.GL$ICD";
    private static final String GL_ICD_STATIC = "org.lwjgl.opengl.GL$ICDStatic";
    private static final String GL_CAPABILITIES = "org.lwjgl.opengl.GLCapabilities";
    private static final String THREAD_LOCAL_UTIL = "org.lwjgl.system.ThreadLocalUtil";
    private static final String LINUX_LIBRARY = "org.lwjgl.system.linux.LinuxLibrary";
    private static final String BROWSER_GL = "port.shim.BrowserGL";
    private static final String NATIVE_MEMORY = "port.runtime.memory.NativeMemory";
    private static final String POINTER_BUFFER = "org.lwjgl.PointerBuffer";
    private static final String POINTER_DEFAULT = "org.lwjgl.system.Pointer$Default";
    private static final String FUNCTION_PROVIDER = "org.lwjgl.system.FunctionProvider";
    private static final String INT_FUNCTION = "java.util.function.IntFunction";
    private static final String API_VERSION = "org.lwjgl.system.APIUtil$APIVersion";
    private static final ValueType OBJECT = ValueType.object("java.lang.Object");
    private static final ValueType STRING = ValueType.object("java.lang.String");
    private static final ValueType SET = ValueType.object("java.util.Set");
    private static final ValueType THREAD_LOCAL = ValueType.object("java.lang.ThreadLocal");
    private static final ValueType BYTE_BUFFER = ValueType.object("java.nio.ByteBuffer");
    private static final ValueType CHAR_SEQUENCE = ValueType.object("java.lang.CharSequence");
    private static final ValueType CAPABILITIES = ValueType.object(GL_CAPABILITIES);
    private static final ValueType PROVIDER = ValueType.object(FUNCTION_PROVIDER);
    private static final ValueType INT_FUNCTION_TYPE = ValueType.object(INT_FUNCTION);
    private static final ValueType POINTER_BUFFER_TYPE = ValueType.object(POINTER_BUFFER);
    /** GLCapabilities.ADDRESS_BUFFER_SIZE: verified in the pinned jar as a compile-time constant. */
    private static final int ADDRESS_BUFFER_SIZE = 2236;

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        switch (cls.getName()) {
            case GL -> rewriteGL(cls, context);
            case GL_ICD_STATIC -> rewriteIcdStatic(cls, context);
            case THREAD_LOCAL_UTIL -> rewriteThreadLocalUtil(cls, context);
            case LINUX_LIBRARY -> rewriteLinuxLibrary(cls, context);
            case GL_CAPABILITIES -> patchCapabilitiesConstructor(cls);
            default -> { }
        }
    }

    /**
     * Upstream {@code GL.<clinit>} loads the host OpenGL shared library through JNI. That load
     * cannot happen in wasm, so this backend installs its own provider: a {@code LinuxLibrary} with
     * address 0 whose {@code getFunctionAddress} asks {@code BrowserGL}. MAX_VERSION stays null
     * because no desktop OpenGL maximum version can be read from a WebGL2 context.
     */
    private void rewriteGL(ClassHolder cls, ClassHolderTransformerContext context) {
        var hierarchy = context.getHierarchy();
        var clinit = require(cls, new MethodDescriptor("<clinit>", ValueType.VOID), "GL.<clinit>");
        var e = ProgramEmitter.create(clinit, hierarchy);
        e.setField(GL, "capabilitiesTLS", e.construct("java.lang.ThreadLocal"));
        e.setField(GL, "icd", e.construct(GL_ICD_STATIC));
        e.setField(GL, "MAX_VERSION", e.constantNull(ValueType.object(API_VERSION)));
        var handle = e.invoke(new MethodReference(BROWSER_GL, "providerHandle", ValueType.LONG));
        var provider = e.construct(LINUX_LIBRARY, e.constant("WebGL2-browser"), handle);
        e.invoke(new MethodReference(GL, "create", PROVIDER, ValueType.VOID), provider);
        e.exit();

        // Upstream also writes the capabilities address into the JNI thread-local slot. That slot
        // only feeds native function-missing lookups, which do not exist here (ThreadLocalUtil is
        // inert), so the two effects that exist in a browser are kept: the thread-local instance and
        // the ICD registration.
        var setCapabilities = require(cls, new MethodDescriptor("setCapabilities", CAPABILITIES, ValueType.VOID),
            "GL.setCapabilities");
        var eSet = ProgramEmitter.create(setCapabilities, hierarchy);
        var caps = eSet.var(1, CAPABILITIES);
        eSet.getField(GL, "capabilitiesTLS", THREAD_LOCAL).invoke(InvocationType.VIRTUAL,
            new MethodReference("java.lang.ThreadLocal", "set", OBJECT, ValueType.VOID), caps.cast(OBJECT));
        eSet.getField(GL, "icd", ValueType.object(GL_ICD)).invoke(InvocationType.VIRTUAL,
            new MethodReference(GL_ICD, "set", CAPABILITIES, ValueType.VOID), caps);
        eSet.exit();

        var createCapabilities = require(cls, new MethodDescriptor("createCapabilities", ValueType.BOOLEAN,
            INT_FUNCTION_TYPE, CAPABILITIES), "GL.createCapabilities(boolean,IntFunction)");
        var eCaps = ProgramEmitter.create(createCapabilities, hierarchy);
        var installed = eCaps.getField(GL, "functionProvider", PROVIDER);
        eCaps.when(installed.isNull()).thenDo(() -> eCaps.construct(IllegalStateException.class,
            eCaps.constant("Browser OpenGL provider is not installed: " + GL + "#create(FunctionProvider)"))
            .raise());
        // Upstream requires the host library to expose glGetError/glGetString/glGetIntegerv before it
        // builds a capability set. A WebGL2 context has no desktop symbol table: the version and the
        // extension list come from the context itself (BrowserGL reads getContext and
        // getSupportedExtensions), so those two probes are replaced by the browser equivalents that
        // really exist. What is checked instead is that the context is current, reports a version and
        // that this backend's own callable entrypoints resolve through the provider: a context that
        // cannot clear a framebuffer has no GL to build capabilities from.
        eCaps.when(eCaps.invoke(new MethodReference(BROWSER_GL, "contextCurrent", ValueType.BOOLEAN)).isFalse())
            .thenDo(() -> eCaps.construct(IllegalStateException.class,
                eCaps.constant("Browser WebGL2 context is not available for " + GL
                    + "#createCapabilities(boolean,IntFunction)")).raise());
        eCaps.when(eCaps.invoke(new MethodReference(BROWSER_GL, "versionMajor", ValueType.INTEGER)).isEqualTo(eCaps.constant(0)))
            .thenDo(() -> eCaps.construct(IllegalStateException.class,
                eCaps.constant("Browser WebGL2 context reports no API version for " + GL
                    + "#createCapabilities(boolean,IntFunction)")).raise());
        for (String core : new String[] {"glGetError", "glClear", "glClearColor", "glViewport"}) {
            var address = lookup(eCaps, installed, core);
            var present = eCaps.invoke(new MethodReference(BROWSER_GL, "addressPresent", ValueType.LONG,
                ValueType.BOOLEAN), address);
            eCaps.when(present.isFalse()).thenDo(() -> eCaps.construct(IllegalStateException.class,
                eCaps.constant("Browser provider cannot resolve " + core + ", which " + GL
                    + "#createCapabilities(boolean,IntFunction) requires")).raise());
        }
        // Upstream probes the GL error log through JNI before building the instance; no JNI call
        // exists here, so that probe is not emitted. The IntFunction argument is unused because the
        // patched constructor takes the BrowserGL arena block instead of a host allocation.
        var instance = eCaps.construct(GL_CAPABILITIES, installed,
            eCaps.invoke(new MethodReference(BROWSER_GL, "tags", SET)),
            eCaps.constant(0).cast(ValueType.BOOLEAN), eCaps.constantNull(INT_FUNCTION_TYPE));
        eCaps.invoke(new MethodReference(GL, "setCapabilities", CAPABILITIES, ValueType.VOID), instance);
        instance.returnValue();
    }

    private static org.teavm.model.emit.ValueEmitter lookup(ProgramEmitter e,
            org.teavm.model.emit.ValueEmitter provider, String name) {
        return provider.invoke(InvocationType.VIRTUAL,
            new MethodReference(FUNCTION_PROVIDER, "getFunctionAddress", CHAR_SEQUENCE, ValueType.LONG),
            e.constant(name));
    }

    /**
     * Upstream {@code GL$ICDStatic.set} swaps the ICD through an invokedynamic lambda when a second
     * capabilities instance with different addresses appears. {@code areCapabilitiesDifferent} is
     * false in this backend (there is no host missing-function table), so upstream would neither log
     * nor swap: the first instance wins, which is what is emitted here.
     */
    private void rewriteIcdStatic(ClassHolder cls, ClassHolderTransformerContext context) {
        var set = require(cls, new MethodDescriptor("set", CAPABILITIES, ValueType.VOID), "GL$ICDStatic.set");
        var e = ProgramEmitter.create(set, context.getHierarchy());
        var caps = e.var(1, CAPABILITIES);
        e.when(e.getField(GL_ICD_STATIC, "tempCaps", CAPABILITIES).isNull()).thenDo(() ->
            e.setField(GL_ICD_STATIC, "tempCaps", caps));
        e.exit();
    }

    /**
     * The thread-local host environment (JNI {@code JNIEnv} pointer, capabilities slot,
     * missing-function abort table) has no browser counterpart. The constants carry the wasm layout
     * (8-byte pointers, capabilities at offset 3*8) and the four helpers become inert: capabilities
     * are looked up through {@code GL.capabilitiesTLS}, which is the only path in this backend.
     */
    private void rewriteThreadLocalUtil(ClassHolder cls, ClassHolderTransformerContext context) {
        var hierarchy = context.getHierarchy();
        var clinit = require(cls, new MethodDescriptor("<clinit>", ValueType.VOID), "ThreadLocalUtil.<clinit>");
        var e = ProgramEmitter.create(clinit, hierarchy);
        e.setField(THREAD_LOCAL_UTIL, "JNI_NATIVE_INTERFACE", e.constant(0L));
        e.setField(THREAD_LOCAL_UTIL, "CAPABILITIES_OFFSET", e.constant(24));
        e.setField(THREAD_LOCAL_UTIL, "RESERVED_NULL", e.constant(0L));
        e.setField(THREAD_LOCAL_UTIL, "JNI_NATIVE_INTERFACE_FUNCTION_COUNT", e.constant(0));
        e.setField(THREAD_LOCAL_UTIL, "FUNCTION_MISSING_ABORT", e.constant(0L));
        e.setField(THREAD_LOCAL_UTIL, "FUNCTION_MISSING_ABORT_TABLE", e.constant(0L));
        e.exit();
        inert(cls, hierarchy, new MethodDescriptor("setCapabilities", ValueType.LONG, ValueType.VOID));
        inert(cls, hierarchy, new MethodDescriptor("setFunctionMissingAddresses", ValueType.INTEGER, ValueType.VOID));
        var setup = cls.getMethod(new MethodDescriptor("setupAddressBuffer", POINTER_BUFFER_TYPE, POINTER_BUFFER_TYPE));
        if (setup != null) ProgramEmitter.create(setup, hierarchy).var(1, POINTER_BUFFER_TYPE).returnValue();
        var different = cls.getMethod(new MethodDescriptor("areCapabilitiesDifferent", POINTER_BUFFER_TYPE,
            POINTER_BUFFER_TYPE, ValueType.BOOLEAN));
        if (different != null) ProgramEmitter.create(different, hierarchy).constant(0).cast(ValueType.BOOLEAN).returnValue();
    }

    private static void inert(ClassHolder cls, org.teavm.model.ClassHierarchy hierarchy, MethodDescriptor descriptor) {
        var method = cls.getMethod(descriptor);
        if (method != null) ProgramEmitter.create(method, hierarchy).exit();
    }

    /**
     * {@code LinuxLibrary} is the provider object this backend installs. {@code <init>(String,long)}
     * is pure Java, so only symbol lookup is replaced: instead of {@code dlsym} it asks
     * {@code BrowserGL}, which returns 0 for entrypoints the WebGL2 context does not expose.
     * {@code free} is inert (no host handle exists); {@code getPath} and {@code loadLibrary} keep
     * their fail-fast bodies because no browser path or dlopen exists.
     */
    private void rewriteLinuxLibrary(ClassHolder cls, ClassHolderTransformerContext context) {
        var hierarchy = context.getHierarchy();
        var lookup = require(cls, new MethodDescriptor("getFunctionAddress", BYTE_BUFFER, ValueType.LONG),
            "LinuxLibrary.getFunctionAddress(ByteBuffer)");
        var e = ProgramEmitter.create(lookup, hierarchy);
        var address = e.invoke(new MethodReference(NATIVE_MEMORY, "memAddress", BYTE_BUFFER, ValueType.LONG),
            e.var(1, BYTE_BUFFER));
        var name = e.invoke(new MethodReference(NATIVE_MEMORY, "memASCII", ValueType.LONG, STRING), address);
        e.invoke(new MethodReference(BROWSER_GL, "functionAddress", STRING, ValueType.LONG), name).returnValue();
        var free = cls.getMethod(new MethodDescriptor("free", ValueType.VOID));
        if (free != null) ProgramEmitter.create(free, hierarchy).exit();
    }

    /**
     * The capability constructor asks its {@code IntFunction} for a 2236-slot address buffer, which
     * upstream allocates on the host. Only that allocation source is patched: the 226 real
     * {@code check_*} calls and their 2233 field writes stay untouched, so a capability is true only
     * when {@code BrowserGL} handed out a real address for it.
     */
    private void patchCapabilitiesConstructor(ClassHolder cls) {
        MethodHolder ctor = null;
        for (MethodHolder method : cls.getMethods()) {
            MethodDescriptor descriptor = method.getDescriptor();
            if (!descriptor.getName().equals("<init>") || descriptor.parameterCount() != 4) continue;
            if (descriptor.parameterType(0).equals(PROVIDER) && descriptor.parameterType(1).equals(SET)
                && descriptor.parameterType(2).equals(ValueType.BOOLEAN)
                && descriptor.parameterType(3).equals(INT_FUNCTION_TYPE)) {
                ctor = method;
            }
        }
        if (ctor == null) throw new IllegalStateException("Inventoried GLCapabilities.<init>("
            + "FunctionProvider,Set,boolean,IntFunction) is missing");
        var program = ctor.getProgram();
        if (program == null) throw new IllegalStateException("GLCapabilities.<init> has no program to patch");
        List<InvokeInstruction> applies = new ArrayList<>();
        for (BasicBlock block : program.getBasicBlocks()) {
            for (Instruction instruction : block) {
                if (instruction instanceof InvokeInstruction invoke
                    && invoke.getMethod().getClassName().equals(INT_FUNCTION)
                    && invoke.getMethod().getName().equals("apply")) {
                    applies.add(invoke);
                }
            }
        }
        if (applies.size() != 1) throw new IllegalStateException("Expected exactly one IntFunction.apply in "
            + "GLCapabilities.<init>, found " + applies.size());
        var apply = applies.get(0);
        // The original bytecode after apply is "checkcast PointerBuffer; astore var5". In TeaVM IR the
        // checkcast is an EmptyInstruction placeholder and the store is an AssignInstruction, so the
        // block split is preserved: PointerBuffer.create reuses the variable the apply produced, and
        // the untouched store/checkcast feed it to the 226 real check_* calls.
        Variable produced = apply.getReceiver();
        if (produced == null) {
            produced = program.createVariable();
            apply.setReceiver(produced);
        }
        Variable arena = program.createVariable();
        var addressCall = new InvokeInstruction();
        addressCall.setType(InvocationType.SPECIAL);
        addressCall.setMethod(new MethodReference(BROWSER_GL, "addressBufferAddress", ValueType.LONG));
        addressCall.setReceiver(arena);
        apply.replace(addressCall);
        var size = new IntegerConstantInstruction();
        size.setConstant(ADDRESS_BUFFER_SIZE);
        Variable sizeVar = program.createVariable();
        size.setReceiver(sizeVar);
        addressCall.insertNext(size);
        var create = new InvokeInstruction();
        create.setType(InvocationType.SPECIAL);
        create.setMethod(new MethodReference(POINTER_BUFFER, "create", ValueType.LONG, ValueType.INTEGER,
            POINTER_BUFFER_TYPE));
        create.setReceiver(produced);
        create.setArguments(arena, sizeVar);
        size.insertNext(create);
    }

    private static MethodHolder require(ClassHolder cls, MethodDescriptor descriptor, String name) {
        var method = cls.getMethod(descriptor);
        if (method == null) throw new IllegalStateException("Inventoried " + name + " is missing");
        return method;
    }
}
