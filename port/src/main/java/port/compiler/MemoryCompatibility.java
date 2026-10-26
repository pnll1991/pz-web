package port.compiler;
import org.teavm.model.*;
import org.teavm.model.emit.*;

/** Replaces LWJGL's JVM layout assumptions with checked virtual memory operations. */
public final class MemoryCompatibility implements ClassHolderTransformer {
    @Override public void transformClass(ClassHolder cls,ClassHolderTransformerContext context){
        if(!java.util.Set.of("org.lwjgl.system.MemoryUtil","port.demo.MemoryMappingProbe").contains(cls.getName()))return;
        var own=context.getHierarchy().getClassSource().get("port.runtime.memory.NativeMemory");
        if(own==null)throw new IllegalStateException("Native memory bridge missing");
        for(var method:cls.getMethods()){
            // Inventoried native guards retain their complete JNI names.
            if(method.getName().equals("memGlobalRefToObject"))continue;
            var emitter=ProgramEmitter.create(method,context.getHierarchy());
            if(method.getName().equals("<clinit>")){
                emitter.setField(cls.getName(),"PAGE_SIZE",emitter.constant(65536));
                emitter.setField(cls.getName(),"CACHE_LINE_SIZE",emitter.constant(64));
                emitter.setField(cls.getName(),"UNSAFE",emitter.invoke(new MethodReference("sun.misc.Unsafe","getUnsafe",ValueType.object("sun.misc.Unsafe"))));
                emitter.setField(cls.getName(),"NATIVE_ORDER",emitter.getField("java.nio.ByteOrder","LITTLE_ENDIAN",ValueType.object("java.nio.ByteOrder")));
                emitter.exit();continue;
            }
            if(own.getMethod(method.getDescriptor())!=null&&method.hasModifier(ElementModifier.STATIC)){
                ValueEmitter[] args=new ValueEmitter[method.parameterCount()];for(int i=0;i<args.length;i++)args[i]=emitter.var(i+1,method.parameterType(i));
                var result=emitter.invoke(new MethodReference(own.getName(),method.getDescriptor()),args);
                if(method.getResultType()==ValueType.VOID)emitter.exit();else result.returnValue();
            }else emitter.construct(UnsupportedOperationException.class,emitter.constant("Browser LWJGL memory operation unavailable: "+cls.getName()+"."+method.getDescriptor())).raise();
        }
    }
}
