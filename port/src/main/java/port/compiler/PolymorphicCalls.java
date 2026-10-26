package port.compiler;
import java.io.File;
import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.teavm.model.*;
import org.teavm.model.emit.*;
import org.teavm.model.instructions.*;

/** Preserves call-site types for Java signature-polymorphic APIs in an AOT build. */
public final class PolymorphicCalls implements ClassHolderTransformer {
    private static final Map<String,Set<MethodDescriptor>> signatures=new HashMap<>();
    public static void scan(List<File> paths)throws Exception{
        signatures.clear();signatures.put("java.lang.invoke.MethodHandle",new HashSet<>());signatures.put("java.lang.invoke.VarHandle",new HashSet<>());
        for(File path:paths){
            if(path.isDirectory()){try(var files=Files.walk(path.toPath())){for(Path file:files.filter(p->p.toString().endsWith(".class")).toList())read(Files.readAllBytes(file));}}
            else if(path.getName().endsWith(".jar")){try(var jar=new ZipFile(path)){for(var entry:jar.stream().filter(e->e.getName().endsWith(".class")&&!e.getName().startsWith("META-INF/")).toList())read(jar.getInputStream(entry).readAllBytes());}}
        }
    }
    private static void read(byte[] bytes){
        var cls=ClassFile.of().parse(bytes);
        for(var method:cls.methods())if(method.code().isPresent())for(var instruction:method.code().get())if(instruction instanceof InvokeInstruction call){
            var set=signatures.get(call.owner().asInternalName().replace('/','.'));if(set==null)continue;
            String name=call.name().stringValue();if(Set.of("invokeExact","invoke","get","set","getVolatile","setVolatile","compareAndExchange","compareAndSet").contains(name))set.add(MethodDescriptor.parse(name+call.type().stringValue()));
        }
    }
    @Override public void transformClass(ClassHolder cls,ClassHolderTransformerContext context){
        for(var method:cls.getMethods()){
            var program=method.getProgram();if(program==null)continue;
            for(int b=0;b<program.basicBlockCount();b++)for(var instruction:program.basicBlockAt(b))if(instruction instanceof org.teavm.model.instructions.InvokeInstruction call&&call.getMethod().toString().equals("java.lang.invoke.MethodHandles.lookup()Ljava/lang/invoke/MethodHandles$Lookup;")){
                var value=program.createVariable();var constant=new ClassConstantInstruction();constant.setConstant(ValueType.object(cls.getName()));constant.setReceiver(value);call.insertPrevious(constant);
                call.setMethod(new MethodReference("java.lang.invoke.MethodHandles","lookup",ValueType.object("java.lang.Class"),ValueType.object("java.lang.invoke.MethodHandles$Lookup")));call.setArguments(value);
            }
        }
        for(var descriptor:signatures.getOrDefault(cls.getName(),Set.of())){
            if(cls.getMethod(descriptor)!=null)continue;var method=new MethodHolder(descriptor);method.setLevel(AccessLevel.PUBLIC);cls.addMethod(method);
            var emitter=ProgramEmitter.create(method,context.getHierarchy());var arguments=emitter.constructArray(Object.class,method.parameterCount());
            for(int i=0;i<method.parameterCount();i++){var value=emitter.var(i+1,method.parameterType(i));arguments.setElement(i,method.parameterType(i) instanceof ValueType.Primitive?value.box():value.cast(Object.class));}
            var self=emitter.var(0,ValueType.object(cls.getName()));ValueEmitter result;
            if(cls.getName().endsWith("VarHandle"))result=self.invokeVirtual("access",ValueType.object("java.lang.Object"),emitter.constant(method.getName()),arguments);
            else if(method.getName().equals("invokeExact")){
                var types=emitter.constructArray(Class.class,method.parameterCount());for(int i=0;i<method.parameterCount();i++)types.setElement(i,emitter.constant(method.parameterType(i)));
                result=self.invokeVirtual("exact",ValueType.object("java.lang.Object"),arguments,types,emitter.constant(method.getResultType()));
            }else result=self.invokeVirtual("invokeWithArguments",ValueType.object("java.lang.Object"),arguments);
            ValueType type=method.getResultType();if(type==ValueType.VOID){emitter.exit();continue;}
            if(type instanceof ValueType.Primitive){String box=box(type),unbox=type.toString();String name=switch(unbox){case "Z"->"booleanValue";case "B"->"byteValue";case "S"->"shortValue";case "C"->"charValue";case "I"->"intValue";case "J"->"longValue";case "F"->"floatValue";case "D"->"doubleValue";default->throw new IllegalStateException();};result.cast(ValueType.object(box)).invokeVirtual(name,type).returnValue();}
            else result.cast(type).returnValue();
        }
    }
    private static String box(ValueType type){return "java.lang."+switch(type.toString()){case "Z"->"Boolean";case "B"->"Byte";case "S"->"Short";case "C"->"Character";case "I"->"Integer";case "J"->"Long";case "F"->"Float";case "D"->"Double";default->throw new IllegalArgumentException();};}
}
