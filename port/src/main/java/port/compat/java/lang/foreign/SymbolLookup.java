package port.compat.java.lang.foreign;
public final class SymbolLookup {
    public static SymbolLookup libraryLookup(String path,Arena arena){throw new UnsupportedOperationException("SymbolLookup.libraryLookup: cannot load host library "+path);}
    public static SymbolLookup loaderLookup(){throw new UnsupportedOperationException("SymbolLookup.loaderLookup: host libraries are not loaded into Wasm GC");}
    public MemorySegment findOrThrow(String name){throw new IllegalArgumentException("Native symbol unavailable: "+name);}
}
