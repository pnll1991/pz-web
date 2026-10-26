package port.compat.java.lang.foreign;
public final class Linker {
    public static Linker nativeLinker(){throw new UnsupportedOperationException("Linker.nativeLinker: the browser cannot call host ABI functions; use a registered Wasm bridge");}
    public port.compat.java.lang.invoke.MethodHandle downcallHandle(MemorySegment symbol,FunctionDescriptor signature,Option... options){throw new UnsupportedOperationException("Linker.downcallHandle: no host ABI in Wasm GC");}
    public static final class Option {
        public static Option captureCallState(String... names){throw new UnsupportedOperationException("Linker.Option.captureCallState: no host errno in browser");}
        public static StructLayout captureStateLayout(){throw new UnsupportedOperationException("Linker.Option.captureStateLayout: no host ABI");}
    }
}
