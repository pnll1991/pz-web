package port.compat.java.lang.foreign;
public final class FunctionDescriptor {
    private final MemoryLayout result;private final MemoryLayout[] arguments;
    private FunctionDescriptor(MemoryLayout result,MemoryLayout[] arguments){this.result=result;this.arguments=arguments.clone();for(var arg:arguments)java.util.Objects.requireNonNull(arg);}
    public static FunctionDescriptor of(MemoryLayout result,MemoryLayout... arguments){return new FunctionDescriptor(java.util.Objects.requireNonNull(result),arguments);}
    public static FunctionDescriptor ofVoid(MemoryLayout... arguments){return new FunctionDescriptor(null,arguments);}
}
