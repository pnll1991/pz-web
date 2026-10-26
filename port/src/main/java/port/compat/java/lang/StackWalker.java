package port.compat.java.lang;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;
public final class StackWalker {
    public enum Option {RETAIN_CLASS_REFERENCE,SHOW_REFLECT_FRAMES,SHOW_HIDDEN_FRAMES}
    public interface StackFrame {String getClassName();String getMethodName();Class<?> getDeclaringClass();StackTraceElement toStackTraceElement();int getLineNumber();boolean isNativeMethod();String getFileName();}
    private final boolean retain;
    private StackWalker(Set<Option> options){retain=options.contains(Option.RETAIN_CLASS_REFERENCE);if(options.contains(Option.SHOW_HIDDEN_FRAMES))throw new UnsupportedOperationException("StackWalker: hidden VM frames do not exist in this AOT runtime");}
    public static StackWalker getInstance(){return new StackWalker(Set.of());}public static StackWalker getInstance(Option option){return new StackWalker(Set.of(option));}public static StackWalker getInstance(Set<Option> options){return new StackWalker(Set.copyOf(options));}
    public <T>T walk(Function<? super Stream<StackFrame>,? extends T> visitor){List<StackFrame> frames=new ArrayList<>();for(var element:Thread.currentThread().getStackTrace()){
        String name=element.getClassName();if(name.equals("java.lang.Throwable$FakeClass"))throw new UnsupportedOperationException("StackWalker: this TeaVM build exposes Wasm offsets, not symbolized Java frame metadata");if(name.equals("java.lang.Thread")||name.equals("java.lang.Throwable")||name.equals(StackWalker.class.getName())||name.equals("java.lang.StackWalker"))continue;
        frames.add(new Frame(element,retain));}return visitor.apply(frames.stream());}
    public Class<?> getCallerClass(){if(!retain)throw new UnsupportedOperationException("RETAIN_CLASS_REFERENCE required");return walk(stream->stream.skip(1).findFirst().orElseThrow(()->new IllegalCallerException("No caller frame available")).getDeclaringClass());}
    private record Frame(StackTraceElement element,boolean retain)implements StackFrame{
        public String getClassName(){return element.getClassName();}public String getMethodName(){return element.getMethodName();}public Class<?> getDeclaringClass(){if(!retain)throw new UnsupportedOperationException("RETAIN_CLASS_REFERENCE required");try{return Class.forName(element.getClassName(),false,ClassLoader.getSystemClassLoader());}catch(ClassNotFoundException e){throw new UnsupportedOperationException("Stack frame class was not retained in AOT metadata: "+element.getClassName(),e);}}
        public StackTraceElement toStackTraceElement(){return element;}public int getLineNumber(){return element.getLineNumber();}public boolean isNativeMethod(){return element.isNativeMethod();}public String getFileName(){return element.getFileName();}
    }
}
