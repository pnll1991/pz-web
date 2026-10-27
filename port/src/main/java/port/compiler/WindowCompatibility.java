package port.compiler;

import org.teavm.model.*;
import org.teavm.model.emit.*;
import org.teavm.model.instructions.InvocationType;

/** Replace platform initialization, preserving the game's renderer and state machine. */
public final class WindowCompatibility implements ClassHolderTransformer {
    private static final String BRIDGE = "port.shim.BrowserWindow";
    private static final String CALLBACKS = "port.shim.BrowserCallbacks";
    private static final String LIBFFI_BRIDGE = "port.shim.BrowserLibFFI";
    private static final String STB_BRIDGE = "port.shim.BrowserStb";
    private static final String MEMORY_STACK = "org.lwjgl.system.MemoryStack";
    private static final String POINTER_DEFAULT = "org.lwjgl.system.Pointer$Default";
    private static final String CALLBACK_I = "org.lwjgl.system.CallbackI";
    private static final String GL = "org.lwjgl.opengl.GL";
    private static final ValueType OBJECT = ValueType.object("java.lang.Object");
    private static final ValueType STRING = ValueType.object("java.lang.String");
    private static final ValueType THREAD_LOCAL = ValueType.object("java.lang.ThreadLocal");
    private static final ValueType MEMORY_STACK_TYPE = ValueType.object(MEMORY_STACK);
    private static final ValueType FFI_TYPE = ValueType.object("org.lwjgl.system.libffi.FFIType");
    private static final ValueType FFI_CIF = ValueType.object("org.lwjgl.system.libffi.FFICIF");
    private static final ValueType FFI_TYPE_ARRAY = ValueType.arrayOf(FFI_TYPE);
    private static final ValueType BYTE_BUFFER = ValueType.object("java.nio.ByteBuffer");
    /**
     * libffi CIF preparation cannot exist in wasm, so every CIF the game builds is a real
     * zeroed arena record that nothing reads. The message names the identity that is missing
     * rather than pretending the capability query succeeded.
     */
    private static final String CIF_FAILURE =
        "Browser libffi closure is unavailable: org.lwjgl.system.libffi.LibFFI#ffi_prep_cif "
        + "(wasm has no native callback trampoline)";
    /** Event kinds shared with port/runtime/window.mjs poll() and BrowserCallbacks. */
    private static final int KIND_KEY = 0, KIND_BUTTON = 1, KIND_CURSOR = 2, KIND_SCROLL = 3,
        KIND_CHAR = 4, KIND_WINDOW_POS = 5, KIND_WINDOW_SIZE = 6, KIND_REFRESH = 7,
        KIND_FRAMEBUFFER = 8, KIND_ICONIFY = 9, KIND_ERROR = 10;
    private final java.util.Map<String,String> nativeNames=new java.util.HashMap<>();
    public WindowCompatibility() {
        try {
            for(String line:java.nio.file.Files.readAllLines(java.nio.file.Path.of(System.getProperty("pz.nativeIndex")))) {
                if(line.isBlank()||line.startsWith("#"))continue;
                String[] fields=line.split("\t",-1);
                if(fields.length!=4)throw new IllegalArgumentException("Invalid native index");
                nativeNames.put(fields[0]+"#"+fields[1]+fields[2],fields[3]);
            }
        } catch(java.io.IOException error) {throw new IllegalStateException("Missing native index",error);}
    }
    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        switch (cls.getName()) {
            case "org.lwjglx.LWJGLUtil" -> rewriteLwjglUtil(cls, context);
            case "org.lwjgl.system.Checks" -> rewriteChecks(cls, context);
            case "org.lwjgl.system.libffi.LibFFI" -> rewriteLibFFI(cls, context);
            case "org.lwjgl.system.libffi.FFICIF" -> rewriteFFICIF(cls, context);
            case "org.lwjgl.system.APIUtil" -> rewriteAPIUtil(cls, context);
            case "org.lwjgl.system.Upcalls" -> rewriteUpcalls(cls, context);
            case MEMORY_STACK -> rewriteMemoryStack(cls, context);
            case GL -> rewriteGL(cls, context);
            default -> { }
        }
        if (cls.getName().equals("org.lwjglx.opengl.Display")) {
            var method = cls.getMethod(new MethodDescriptor("init",ValueType.VOID));
            if (method == null) throw new IllegalStateException("Inventoried Display.init is missing");
            var e = ProgramEmitter.create(method,context.getHierarchy());
            e.when(e.getField(cls.getName(),"isInitialized",ValueType.BOOLEAN).isTrue()).thenDo(e::exit);
            e.invoke(new MethodReference(BRIDGE,"init",ValueType.VOID));
            var width = e.invoke(new MethodReference(BRIDGE,"desktopWidth",ValueType.INTEGER));
            var height = e.invoke(new MethodReference(BRIDGE,"desktopHeight",ValueType.INTEGER));
            var bits = e.invoke(new MethodReference(BRIDGE,"colorBits",ValueType.INTEGER));
            // The browser exposes no monitor refresh rate; preserve an unknown frequency.
            e.setField(cls.getName(),"desktopDisplayMode",e.construct("org.lwjglx.opengl.DisplayMode",width,height,bits,e.constant(0)));
            // Display.monitor holds a monitor handle, not the window handle: this backend's
            // single display is BrowserWindow.MONITOR_HANDLE. The window handle stays 0 until created.
            e.setField(cls.getName(),"monitor",e.constant(2L));
            e.invoke(new MethodReference("org.lwjglx.input.Keyboard","create",ValueType.VOID));
            e.setField(cls.getName(),"isInitialized",e.constant(1).cast(ValueType.BOOLEAN));
            e.exit();
        }
        if (!cls.getName().equals("org.lwjgl.glfw.GLFW")) return;
        var bridge = context.getHierarchy().getClassSource().get(BRIDGE);
        for (var method : cls.getMethods()) {
            // The monitor structs are built in IR: the bridge cannot import org.lwjgl types,
            // so a real GLFWVidMode/Buffer is constructed over one arena address holding the
            // single mode the page can present (the canvas drawing buffer).
            if (method.getDescriptor().equals(new MethodDescriptor("glfwGetVideoModes",
                    ValueType.LONG, ValueType.object("org.lwjgl.glfw.GLFWVidMode$Buffer")))) {
                var e = ProgramEmitter.create(method,context.getHierarchy());
                e.invoke(new MethodReference(BRIDGE,"checkMonitor",ValueType.LONG,ValueType.VOID),e.var(1,ValueType.LONG));
                var address = e.invoke(new MethodReference(BRIDGE,"videoModeAddress",ValueType.LONG));
                e.construct("org.lwjgl.glfw.GLFWVidMode$Buffer",address,e.constant(1)).returnValue();
                continue;
            }
            if (method.getDescriptor().equals(new MethodDescriptor("glfwGetVideoMode",
                    ValueType.LONG, ValueType.object("org.lwjgl.glfw.GLFWVidMode")))) {
                var e = ProgramEmitter.create(method,context.getHierarchy());
                e.invoke(new MethodReference(BRIDGE,"checkMonitor",ValueType.LONG,ValueType.VOID),e.var(1,ValueType.LONG));
                var address = e.invoke(new MethodReference(BRIDGE,"videoModeAddress",ValueType.LONG));
                e.construct("org.lwjgl.glfw.GLFWVidMode",address,
                    e.constantNull(java.nio.ByteBuffer.class)).returnValue();
                continue;
            }
            // The browser owns the whole backend, so init never probes a host dynamic library.
            if (method.getDescriptor().equals(new MethodDescriptor("glfwInit",ValueType.BOOLEAN))) {
                method.getModifiers().remove(ElementModifier.NATIVE);
                var e = ProgramEmitter.create(method,context.getHierarchy());
                e.constant(1).cast(ValueType.BOOLEAN).returnValue();
                continue;
            }
            if (method.getDescriptor().equals(new MethodDescriptor("glfwInitHint",ValueType.INTEGER,ValueType.INTEGER,ValueType.VOID))
                || method.getDescriptor().equals(new MethodDescriptor("glfwPlatformSupported",ValueType.INTEGER,ValueType.BOOLEAN))) {
                method.getModifiers().remove(ElementModifier.NATIVE);
                var e = ProgramEmitter.create(method,context.getHierarchy());
                if (method.getResultType()==ValueType.VOID) e.exit();
                else e.constant(0).cast(ValueType.BOOLEAN).returnValue(); // no host platform to select
                continue;
            }
            int kind = callbackSetterKind(method.getDescriptor());
            if (kind >= 0) {
                method.getModifiers().remove(ElementModifier.NATIVE);
                var e = ProgramEmitter.create(method,context.getHierarchy());
                ValueType previous = ValueType.object(((ValueType.Object)method.getResultType()).getClassName());
                if (method.parameterCount()==2) {
                    e.invoke(new MethodReference(BRIDGE,"checkWindow",ValueType.LONG,ValueType.VOID),e.var(1,ValueType.LONG));
                    e.invoke(new MethodReference(CALLBACKS,"setHandler",ValueType.INTEGER,OBJECT,ValueType.VOID),
                        e.constant(kind), e.var(2,method.parameterType(1)).cast(OBJECT));
                } else {
                    e.invoke(new MethodReference(CALLBACKS,"setHandler",ValueType.INTEGER,OBJECT,ValueType.VOID),
                        e.constant(kind), e.var(1,method.parameterType(0)).cast(OBJECT));
                }
                // GLFW returns the previously installed callback. This backend has never installed
                // one through native code, and it does not keep the previous instance alive for a
                // wasm caller, so it reports null rather than a handle the caller would free.
                e.constantNull(previous).returnValue();
                continue;
            }
            if (method.getDescriptor().equals(
                    new MethodDescriptor("glfwSetWindowIcon",ValueType.LONG,
                        ValueType.object("org.lwjgl.glfw.GLFWImage$Buffer"),ValueType.VOID))) {
                method.getModifiers().remove(ElementModifier.NATIVE);
                var e = ProgramEmitter.create(method,context.getHierarchy());
                var icons = e.var(2,ValueType.object("org.lwjgl.glfw.GLFWImage$Buffer"));
                e.when(icons.isNull()).thenDo(e::exit);
                e.invoke(new MethodReference(BRIDGE,"checkWindow",ValueType.LONG,ValueType.VOID),e.var(1,ValueType.LONG));
                // The pixel bridge is port.shim.BrowserStb: each GLFWImage entry is
                // __struct(member(4),member(4),POINTER_SIZE) = 16 bytes (width, height, pixels),
                // and the buffer's element count is its remaining() (limit - position, both ints).
                var count = icons.invoke(InvocationType.VIRTUAL,
                    new MethodReference("org.lwjgl.system.CustomBuffer","remaining",ValueType.INTEGER));
                e.when(count.isLessOrEqualTo(e.constant(0))).thenDo(e::exit);
                var entries = icons.invoke(InvocationType.VIRTUAL,
                    new MethodReference("org.lwjgl.system.CustomBuffer","address",ValueType.LONG));
                e.invoke(new MethodReference(STB_BRIDGE,"uploadIcon",ValueType.LONG,ValueType.INTEGER,
                    ValueType.INTEGER,ValueType.VOID),entries,count,e.constant(16));
                // A void body must end with an explicit terminator: the join block would
                // otherwise have no last instruction and TeaVM's unreachable-block eliminator
                // fails with a NullPointerException while optimizing it.
                e.exit();
                continue;
            }
            if (method.getDescriptor().equals(new MethodDescriptor("glfwPollEvents",ValueType.VOID))) {
                method.getModifiers().remove(ElementModifier.NATIVE);
                rewritePollEvents(method, context);
                continue;
            }
            if (method.getName().equals("<clinit>")) {
                // The browser owns the backend; there is no host dynamic library.
                var e = ProgramEmitter.create(method,context.getHierarchy());
                e.invoke(new MethodReference(BRIDGE,"init",ValueType.VOID));e.exit();
            } else if (bridge.getMethod(method.getDescriptor()) != null && method.hasModifier(ElementModifier.STATIC)) {
                method.getModifiers().remove(ElementModifier.NATIVE);
                var e = ProgramEmitter.create(method,context.getHierarchy());
                var arguments = new ValueEmitter[method.parameterCount()];
                for(int i=0;i<arguments.length;i++) arguments[i]=e.var(i+1,method.parameterType(i));
                var result=e.invoke(new MethodReference(BRIDGE,method.getDescriptor()),arguments);
                if(method.getResultType()==ValueType.VOID)e.exit();else result.returnValue();
            } else if (!method.getName().equals("<init>")) {
                String key=cls.getName()+"#"+method.getDescriptor();
                String failure=nativeNames.getOrDefault(key,"Browser GLFW operation unavailable: "+key);
                method.getModifiers().remove(ElementModifier.NATIVE);
                var e=ProgramEmitter.create(method,context.getHierarchy());
                e.construct(UnsupportedOperationException.class,e.constant(failure)).raise();
            }
        }
    }

    /**
     * Event kind for the setters {@code Display$Callbacks.initCallbacks} and the error callback
     * actually use, or -1 for every other setter (those keep the honest fail-fast raise).
     */
    private static int callbackSetterKind(MethodDescriptor descriptor) {
        return switch (descriptor.getName()) {
            case "glfwSetKeyCallback" -> KIND_KEY;
            case "glfwSetMouseButtonCallback" -> KIND_BUTTON;
            case "glfwSetCursorPosCallback" -> KIND_CURSOR;
            case "glfwSetScrollCallback" -> KIND_SCROLL;
            case "glfwSetCharCallback" -> KIND_CHAR;
            case "glfwSetWindowPosCallback" -> KIND_WINDOW_POS;
            case "glfwSetWindowSizeCallback" -> KIND_WINDOW_SIZE;
            case "glfwSetWindowRefreshCallback" -> KIND_REFRESH;
            case "glfwSetFramebufferSizeCallback" -> KIND_FRAMEBUFFER;
            case "glfwSetWindowIconifyCallback" -> KIND_ICONIFY;
            case "glfwSetErrorCallback" -> KIND_ERROR;
            default -> -1;
        };
    }

    /**
     * Drain the DOM queue the way GLFW drains its event queue: one event per iteration, calling
     * the interface default {@code CallbackI.callback(0, args)} so the untouched LWJGL body reads
     * the argument cells and then dispatches to the game's own lambda.
     */
    private void rewritePollEvents(MethodHolder method, ClassHolderTransformerContext context) {
        var e = ProgramEmitter.create(method,context.getHierarchy());
        var loop = e.prepareBlock();
        e.jump(loop);
        e.enter(loop);
        var event = e.invoke(new MethodReference(CALLBACKS,"pollEvent",STRING));
        e.when(event.isNull()).thenDo(e::exit);
        var callback = e.invoke(new MethodReference(CALLBACKS,"eventCallback",STRING,OBJECT),event);
        e.when(callback.isNull()).thenDo(() -> e.jump(loop));
        var arguments = e.invoke(new MethodReference(CALLBACKS,"eventArguments",STRING,ValueType.LONG),event);
        callback.cast(ValueType.object(CALLBACK_I))
            .invoke(InvocationType.VIRTUAL,new MethodReference(CALLBACK_I,"callback",ValueType.LONG,ValueType.LONG,ValueType.VOID),
                e.constant(0L), arguments);
        e.jump(loop);
    }

    /**
     * {@code LWJGLUtil.<clinit>} reads {@code os.name} and raises LinkageError for "TeaVM". The
     * three platform fields are the only thing Display.create needs, and the values are the same
     * ones the adapted {@code Platform} reports (Linux, checks enabled, debug off).
     */
    private void rewriteLwjglUtil(ClassHolder cls, ClassHolderTransformerContext context) {
        var clinit = cls.getMethod(new MethodDescriptor("<clinit>",ValueType.VOID));
        if (clinit == null) throw new IllegalStateException("Inventoried LWJGLUtil.<clinit> is missing");
        var e = ProgramEmitter.create(clinit,context.getHierarchy());
        e.setField(cls.getName(),"DEBUG",e.constant(0).cast(ValueType.BOOLEAN));
        e.setField(cls.getName(),"CHECKS",e.constant(1).cast(ValueType.BOOLEAN));
        e.setField(cls.getName(),"PLATFORM",e.constant(1)); // PLATFORM_LINUX
        e.exit();
    }

    /** Same flags {@code Checks.<clinit>} computes when no Configuration override is present. */
    private void rewriteChecks(ClassHolder cls, ClassHolderTransformerContext context) {
        var clinit = cls.getMethod(new MethodDescriptor("<clinit>",ValueType.VOID));
        if (clinit == null) throw new IllegalStateException("Inventoried Checks.<clinit> is missing");
        var e = ProgramEmitter.create(clinit,context.getHierarchy());
        e.setField(cls.getName(),"CHECKS",e.constant(1).cast(ValueType.BOOLEAN));
        e.setField(cls.getName(),"DEBUG",e.constant(0).cast(ValueType.BOOLEAN));
        e.setField(cls.getName(),"DEBUG_FUNCTIONS",e.constant(0).cast(ValueType.BOOLEAN));
        e.exit();
    }

    /**
     * libffi's own type records live in the host library. Here they are arena records written by
     * {@code BrowserLibFFI}, which is enough for the game's struct construction: no closure is
     * ever prepared, so nothing reads them. The ABI constants are set to 0 because the getters are
     * native; STATUS.md records that the values cannot be known for a browser backend.
     */
    private void rewriteLibFFI(ClassHolder cls, ClassHolderTransformerContext context) {
        var clinit = cls.getMethod(new MethodDescriptor("<clinit>",ValueType.VOID));
        if (clinit == null) throw new IllegalStateException("Inventoried LibFFI.<clinit> is missing");
        var e = ProgramEmitter.create(clinit,context.getHierarchy());
        // FFI_TYPE_LONGDOUBLE normally equals the host's FFI_TYPE_DOUBLE; unused in this backend.
        e.setField(cls.getName(),"FFI_TYPE_LONGDOUBLE",e.constant(3).cast(ValueType.SHORT));
        for (String abi : new String[] {"FFI_FIRST_ABI","FFI_WIN64","FFI_GNUW64","FFI_UNIX64","FFI_EFI64",
            "FFI_SYSV","FFI_STDCALL","FFI_THISCALL","FFI_FASTCALL","FFI_MS_CDECL","FFI_PASCAL",
            "FFI_REGISTER","FFI_VFP","FFI_LAST_ABI","FFI_DEFAULT_ABI"}) {
            e.setField(cls.getName(),abi,e.constant(0));
        }
        String[] types = {"void","uint8","sint8","uint16","sint16","uint32","sint32","uint64","sint64",
            "uchar","schar","ushort","sshort","uint","sint","ulong","slong","float","double","longdouble","pointer"};
        for (int kind = 0; kind < types.length; kind++) {
            var address = e.invoke(new MethodReference(LIBFFI_BRIDGE,"typeAddress",ValueType.INTEGER,ValueType.LONG),e.constant(kind));
            e.setField(cls.getName(),"ffi_type_"+types[kind],
                e.construct("org.lwjgl.system.libffi.FFIType",address,e.constantNull(BYTE_BUFFER)));
        }
        e.exit();
    }

    /**
     * {@code FFICIF.<clinit>} reads its offsets from the host through a native call. The constants
     * are the x86_64 ffi_cif layout, kept only so {@code sizeof()} and the accessors can compute
     * addresses inside the arena record {@code APIUtil.apiCreateCIF} now allocates. Nothing in
     * this backend reads those fields, and STATUS.md records that doubt.
     */
    private void rewriteFFICIF(ClassHolder cls, ClassHolderTransformerContext context) {
        var clinit = cls.getMethod(new MethodDescriptor("<clinit>",ValueType.VOID));
        if (clinit == null) throw new IllegalStateException("Inventoried FFICIF.<clinit> is missing");
        var e = ProgramEmitter.create(clinit,context.getHierarchy());
        int[] values = {40,8,0,4,16,8,24,28}; // SIZEOF, ALIGNOF, ABI, NARGS, ARG_TYPES, RTYPE, BYTES, FLAGS
        String[] names = {"SIZEOF","ALIGNOF","ABI","NARGS","ARG_TYPES","RTYPE","BYTES","FLAGS"};
        for (int i = 0; i < names.length; i++) e.setField(cls.getName(),names[i],e.constant(values[i]));
        e.exit();
    }

    /**
     * A CIF cannot be prepared without libffi, so {@code apiCreateCIF} returns a real zeroed record
     * instead of calling ffi_prep_cif; the game only stores it inside {@code Callback$Descriptor},
     * and {@code Upcalls.upcallCreate} (rewritten below) never reads it. The variable-argument
     * variants have no browser equivalent and raise with their identity.
     */
    private void rewriteAPIUtil(ClassHolder cls, ClassHolderTransformerContext context) {
        var hierarchy = context.getHierarchy();
        MethodDescriptor[] cifBuilders = {
            new MethodDescriptor("apiCreateCIF",FFI_TYPE,FFI_TYPE_ARRAY,FFI_CIF),
            new MethodDescriptor("apiCreateCIF",ValueType.INTEGER,FFI_TYPE,FFI_TYPE_ARRAY,FFI_CIF)};
        for (MethodDescriptor descriptor : cifBuilders) {
            var method = cls.getMethod(descriptor);
            if (method == null) continue;
            method.getModifiers().remove(ElementModifier.NATIVE);
            var e = ProgramEmitter.create(method,hierarchy);
            e.construct("org.lwjgl.system.libffi.FFICIF",
                e.invoke(new MethodReference(LIBFFI_BRIDGE,"newCIFAddress",ValueType.LONG)),
                e.constantNull(BYTE_BUFFER)).returnValue();
        }
        MethodDescriptor[] varBuilders = {
            new MethodDescriptor("apiCreateCIFVar",ValueType.INTEGER,FFI_TYPE,FFI_TYPE_ARRAY,FFI_CIF),
            new MethodDescriptor("apiCreateCIFVar",ValueType.INTEGER,ValueType.INTEGER,FFI_TYPE,FFI_TYPE_ARRAY,FFI_CIF)};
        for (MethodDescriptor descriptor : varBuilders) {
            var method = cls.getMethod(descriptor);
            if (method == null) continue;
            method.getModifiers().remove(ElementModifier.NATIVE);
            var e = ProgramEmitter.create(method,hierarchy);
            e.construct(UnsupportedOperationException.class,e.constant(
                "Browser libffi operation unavailable: org.lwjgl.system.APIUtil#"+descriptor)).raise();
        }
    }

    /**
     * libffi closures cannot exist in wasm. {@code upcallCreate} therefore hands the instance to
     * {@code BrowserCallbacks} and returns this backend's own handle; {@code upcallGet} resolves
     * the handle back to the instance, and {@code upcallFree} drops the registration. The host
     * closure fields are not created, so {@code getCallbackHandler} keeps its fail-fast raise and
     * nothing pretends a native trampoline exists.
     */
    private void rewriteUpcalls(ClassHolder cls, ClassHolderTransformerContext context) {
        var hierarchy = context.getHierarchy();
        var clinit = cls.getMethod(new MethodDescriptor("<clinit>",ValueType.VOID));
        if (clinit == null) throw new IllegalStateException("Inventoried Upcalls.<clinit> is missing");
        var e = ProgramEmitter.create(clinit,hierarchy);
        e.setField(cls.getName(),"DEBUG_ALLOCATOR",e.constant(0).cast(ValueType.BOOLEAN));
        e.setField(cls.getName(),"CLOSURE_SIZE",e.constant(0));
        e.setField(cls.getName(),"CLOSURE_REGISTRY",e.constantNull(ValueType.object("org.lwjgl.system.Upcalls$ClosureRegistry")));
        e.setField(cls.getName(),"CALLBACK_HANDLER",e.constant(0L));
        e.exit();
        var create = cls.getMethod(new MethodDescriptor("upcallCreate",
            ValueType.object("org.lwjgl.system.Callback$Descriptor"),OBJECT,ValueType.LONG));
        if (create == null) throw new IllegalStateException("Inventoried Upcalls.upcallCreate is missing");
        var eCreate = ProgramEmitter.create(create,hierarchy);
        eCreate.invoke(new MethodReference(CALLBACKS,"newHandle",OBJECT,ValueType.LONG),eCreate.var(2,OBJECT)).returnValue();
        var get = cls.getMethod(new MethodDescriptor("upcallGet",ValueType.LONG,ValueType.object(CALLBACK_I)));
        if (get == null) throw new IllegalStateException("Inventoried Upcalls.upcallGet is missing");
        var eGet = ProgramEmitter.create(get,hierarchy);
        eGet.invoke(new MethodReference(CALLBACKS,"instanceOf",ValueType.LONG,OBJECT),eGet.var(1,ValueType.LONG))
            .cast(ValueType.object(CALLBACK_I)).returnValue();
        var free = cls.getMethod(new MethodDescriptor("upcallFree",ValueType.LONG,ValueType.VOID));
        if (free == null) throw new IllegalStateException("Inventoried Upcalls.upcallFree is missing");
        var eFree = ProgramEmitter.create(free,hierarchy);
        eFree.invoke(new MethodReference(CALLBACKS,"forget",ValueType.LONG,ValueType.VOID),eFree.var(1,ValueType.LONG));
        eFree.exit();
    }

    /**
     * The stack itself stays LWJGL's: push/pop/frames/pointer arithmetic are untouched. Only the
     * host layout constants, the lazy thread-local creation (upstream uses a lambda the IR cannot
     * express) and the two allocator natives, which only need pointer arithmetic on the arena,
     * are replaced. {@code malloc}/{@code calloc}/{@code mallocInt} keep their original bodies.
     */
    private void rewriteMemoryStack(ClassHolder cls, ClassHolderTransformerContext context) {
        var hierarchy = context.getHierarchy();
        var clinit = cls.getMethod(new MethodDescriptor("<clinit>",ValueType.VOID));
        if (clinit == null) throw new IllegalStateException("Inventoried MemoryStack.<clinit> is missing");
        var e = ProgramEmitter.create(clinit,hierarchy);
        e.setField(cls.getName(),"DEFAULT_STACK_SIZE",e.constant(65536));
        e.setField(cls.getName(),"DEFAULT_STACK_FRAMES",e.constant(8));
        e.setField(cls.getName(),"TLS",e.construct("java.lang.ThreadLocal"));
        e.exit();
        // TLS is created without upstream's supplier lambda, so stackGet fills it on first use,
        // which is the same lazy per-thread behaviour ThreadLocal.withInitial gives.
        var stackGet = cls.getMethod(new MethodDescriptor("stackGet",MEMORY_STACK_TYPE));
        if (stackGet == null) throw new IllegalStateException("Inventoried MemoryStack.stackGet is missing");
        var eGet = ProgramEmitter.create(stackGet,hierarchy);
        var tls = eGet.getField(cls.getName(),"TLS",THREAD_LOCAL);
        eGet.when(tls.invoke(InvocationType.VIRTUAL,"get",OBJECT).isNull()).thenDo(() -> {
            var fresh = eGet.invoke(new MethodReference(MEMORY_STACK,"create",ValueType.INTEGER,MEMORY_STACK_TYPE),eGet.constant(65536));
            tls.invoke(InvocationType.VIRTUAL,"set",ValueType.VOID,fresh.cast(OBJECT));
        });
        eGet.getField(cls.getName(),"TLS",THREAD_LOCAL)
            .invoke(InvocationType.VIRTUAL,"get",OBJECT).cast(MEMORY_STACK_TYPE).returnValue();
        var init = cls.getMethod(new MethodDescriptor("<init>",BYTE_BUFFER,ValueType.LONG,ValueType.INTEGER,ValueType.VOID));
        if (init == null) throw new IllegalStateException("Inventoried MemoryStack.<init> is missing");
        var eInit = ProgramEmitter.create(init,hierarchy);
        var self = eInit.var(0,MEMORY_STACK_TYPE); // var 0 is this, the parameters follow
        self.invokeSpecial(new MethodReference(POINTER_DEFAULT,"<init>",ValueType.LONG,ValueType.VOID),eInit.var(2,ValueType.LONG));
        self.setField("container",eInit.var(1,BYTE_BUFFER));
        self.setField("size",eInit.var(3,ValueType.INTEGER));
        self.setField("pointer",eInit.var(3,ValueType.INTEGER));
        self.setField("frames",eInit.constructArray(ValueType.INTEGER,8));
        eInit.exit();
        var malloc = cls.getMethod(new MethodDescriptor("nmalloc",ValueType.INTEGER,ValueType.INTEGER,ValueType.LONG));
        if (malloc == null) throw new IllegalStateException("Inventoried MemoryStack.nmalloc is missing");
        var eMalloc = ProgramEmitter.create(malloc,hierarchy);
        var stack = eMalloc.var(0,MEMORY_STACK_TYPE);
        var address = stack.getField("address",ValueType.LONG);
        var pointer = stack.getField("pointer",ValueType.INTEGER);
        // Same arithmetic as the pinned bytecode: (address + pointer - bytes) & ~((align - 1)).
        var mask = eMalloc.constant(-1L)
            .sub(eMalloc.invoke(new MethodReference("java.lang.Integer","toUnsignedLong",ValueType.INTEGER,ValueType.LONG),
                eMalloc.var(1,ValueType.INTEGER).sub(eMalloc.constant(1))));
        var result = address.add(pointer.cast(ValueType.LONG)).sub(eMalloc.var(2,ValueType.INTEGER).cast(ValueType.LONG))
            .bitAnd(mask);
        stack.setField("pointer",result.sub(address).cast(ValueType.INTEGER));
        eMalloc.when(eMalloc.getField("org.lwjgl.system.Checks","CHECKS",ValueType.BOOLEAN).isTrue()).thenDo(() ->
            eMalloc.when(stack.getField("pointer",ValueType.INTEGER).isLessThan(eMalloc.constant(0))).thenDo(() ->
                eMalloc.construct(OutOfMemoryError.class,eMalloc.constant("Out of stack space.")).raise()));
        result.returnValue();
        // nmalloc(int), ncalloc(int,int,int), malloc/calloc/mallocInt/callocInt keep their original
        // bodies: they only combine the nmalloc above with MemoryUtil.wrapBuffer*, already bridged.
    }

    /**
     * Capability discovery belongs to the host GL library. The browser has a real WebGL2 context,
     * but no GLCapabilities object has been built from it yet, so every createCapabilities variant
     * raises with its own identity: Display.create stops here until the WebGL2 capability bridge
     * exists. setCapabilities/getCapabilities keep their thread-local bodies.
     */
    private void rewriteGL(ClassHolder cls, ClassHolderTransformerContext context) {
        var hierarchy = context.getHierarchy();
        var clinit = cls.getMethod(new MethodDescriptor("<clinit>",ValueType.VOID));
        if (clinit == null) throw new IllegalStateException("Inventoried GL.<clinit> is missing");
        var e = ProgramEmitter.create(clinit,hierarchy);
        e.setField(cls.getName(),"capabilitiesTLS",e.construct("java.lang.ThreadLocal"));
        e.exit();
        ValueType intFunction = ValueType.object("java.util.function.IntFunction");
        MethodDescriptor[] variants = {
            new MethodDescriptor("createCapabilities",ValueType.object("org.lwjgl.opengl.GLCapabilities")),
            new MethodDescriptor("createCapabilities",intFunction,ValueType.object("org.lwjgl.opengl.GLCapabilities")),
            new MethodDescriptor("createCapabilities",ValueType.BOOLEAN,ValueType.object("org.lwjgl.opengl.GLCapabilities")),
            new MethodDescriptor("createCapabilities",ValueType.BOOLEAN,intFunction,ValueType.object("org.lwjgl.opengl.GLCapabilities"))};
        for (MethodDescriptor descriptor : variants) {
            var method = cls.getMethod(descriptor);
            if (method == null) continue;
            method.getModifiers().remove(ElementModifier.NATIVE);
            var emitter = ProgramEmitter.create(method,hierarchy);
            emitter.construct(IllegalStateException.class,emitter.constant(
                "Browser WebGL2 GL capability bridge is not implemented: org.lwjgl.opengl.GL#"+descriptor)).raise();
        }
    }
}
