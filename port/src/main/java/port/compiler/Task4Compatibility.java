package port.compiler;
import org.teavm.model.*;
import org.teavm.model.emit.*;
public final class Task4Compatibility {
    static void apply(ClassHolder cls,ClassHolderTransformerContext context){
        switch(cls.getName()){
            case "java.util.concurrent.TimeUnit" -> {
                forward(cls,context,"timedWait(Ljava/lang/Object;J)V",false);
                forward(cls,context,"timedJoin(Ljava/lang/Thread;J)V",false);
                forward(cls,context,"sleep(J)V",false);
            }
            case "java.lang.Class" -> {for(String s:new String[]{"isAnonymousClass()Z","getPackageName()Ljava/lang/String;","getResource(Ljava/lang/String;)Ljava/net/URL;","getResourceAsStream(Ljava/lang/String;)Ljava/io/InputStream;","getGenericSuperclass()Ljava/lang/reflect/Type;"})forward(cls,context,s,false);}
            case "java.lang.ClassLoader" -> {
                for(String s:new String[]{"getResource(Ljava/lang/String;)Ljava/net/URL;","getResources(Ljava/lang/String;)Ljava/util/Enumeration;","getResourceAsStream(Ljava/lang/String;)Ljava/io/InputStream;"})forward(cls,context,s,false);
                for(String s:new String[]{"getSystemResource(Ljava/lang/String;)Ljava/net/URL;","getSystemResources(Ljava/lang/String;)Ljava/util/Enumeration;","getSystemResourceAsStream(Ljava/lang/String;)Ljava/io/InputStream;"})forward(cls,context,s,true);
            }
            case "java.lang.Package" -> {forward(cls,context,"getSpecificationVersion()Ljava/lang/String;",false);forward(cls,context,"getImplementationVersion()Ljava/lang/String;",false);}
            case "java.nio.file.FileSystem" -> {
                var method=new MethodHolder(MethodDescriptor.parse("newWatchService()Ljava/nio/file/WatchService;"));method.setLevel(AccessLevel.PUBLIC);cls.addMethod(method);
                var emitter=ProgramEmitter.create(method,context.getHierarchy());emitter.construct("java.nio.file.WatchService").returnValue();
            }
            case "java.nio.file.Path" -> {
                cls.getInterfaces().add("java.nio.file.Watchable");
                for(String signature:new String[]{"register(Ljava/nio/file/WatchService;[Ljava/nio/file/WatchEvent$Kind;)Ljava/nio/file/WatchKey;","register(Ljava/nio/file/WatchService;[Ljava/nio/file/WatchEvent$Kind;[Ljava/nio/file/WatchEvent$Modifier;)Ljava/nio/file/WatchKey;"}){
                    var method=new MethodHolder(MethodDescriptor.parse(signature));method.setLevel(AccessLevel.PUBLIC);cls.addMethod(method);
                    var emitter=ProgramEmitter.create(method,context.getHierarchy());var modifiers=method.parameterCount()==3?emitter.var(3,method.parameterType(2)):emitter.constructArray(ValueType.object("java.nio.file.WatchEvent$Modifier"),0);
                    emitter.var(1,ValueType.object("java.nio.file.WatchService")).invokeVirtual("register",ValueType.object("java.nio.file.WatchKey"),emitter.var(0,ValueType.object("java.nio.file.Path")),emitter.var(2,method.parameterType(1)),modifiers).returnValue();
                }
            }
            case "java.io.File" -> {forward(cls,context,"setExecutable(ZZ)Z",false);forward(cls,context,"setWritable(ZZ)Z",false);}
            case "java.lang.reflect.Field" -> {forward(cls,context,"canAccess(Ljava/lang/Object;)Z",false);forward(cls,context,"trySetAccessible()Z",false);}
            case "java.lang.reflect.Method" -> forward(cls,context,"getGenericExceptionTypes()[Ljava/lang/reflect/Type;",false);
            case "java.nio.file.FileSystems" -> {forward(cls,context,"newFileSystem(Ljava/nio/file/Path;)Ljava/nio/file/FileSystem;",true);forward(cls,context,"newFileSystem(Ljava/nio/file/Path;Ljava/util/Map;)Ljava/nio/file/FileSystem;",true);}
            case "java.awt.Color" -> {forward(cls,context,"RGBtoHSB(III[F)[F",true);forward(cls,context,"HSBtoRGB(FFF)I",true);}
            case "java.util.logging.Formatter" -> forward(cls,context,"formatMessage(Ljava/util/logging/LogRecord;)Ljava/lang/String;",false);
            case "java.util.logging.Logger" -> {forward(cls,context,"addHandler(Ljava/util/logging/Handler;)V",false);forward(cls,context,"removeHandler(Ljava/util/logging/Handler;)V",false);forward(cls,context,"getHandlers()[Ljava/util/logging/Handler;",false);forward(cls,context,"setUseParentHandlers(Z)V",false);forward(cls,context,"getUseParentHandlers()Z",false);forward(cls,context,"setLevel(Ljava/util/logging/Level;)V",false);forward(cls,context,"getLevel()Ljava/util/logging/Level;",false);forward(cls,context,"isLoggable(Ljava/util/logging/Level;)Z",false);forward(cls,context,"log(Ljava/util/logging/LogRecord;)V",false);}
            case "java.nio.ByteBuffer" -> forward(cls,context,"slice(II)Ljava/nio/ByteBuffer;",false);
            case "java.util.concurrent.ConcurrentHashMap" -> forward(cls,context,"keySet()Ljava/util/concurrent/ConcurrentHashMap$KeySetView;",false);
            case "java.io.FileReader" -> {
                var method=new MethodHolder(MethodDescriptor.parse("<init>(Ljava/io/File;Ljava/nio/charset/Charset;)V"));method.setLevel(AccessLevel.PUBLIC);cls.addMethod(method);
                var emitter=ProgramEmitter.create(method,context.getHierarchy());var stream=emitter.construct("java.io.FileInputStream",emitter.var(1,ValueType.object("java.io.File")));
                emitter.var(0,ValueType.object(cls.getName())).cast(ValueType.object("java.io.InputStreamReader")).invokeSpecial("<init>",stream.cast(ValueType.object("java.io.InputStream")),emitter.var(2,ValueType.object("java.nio.charset.Charset")));emitter.exit();
            }
            case "java.lang.Thread" -> {
                forward(cls,context,"getAllStackTraces()Ljava/util/Map;",true);
                var method=new MethodHolder(MethodDescriptor.parse("<init>(Ljava/lang/ThreadGroup;Ljava/lang/Runnable;)V"));method.setLevel(AccessLevel.PUBLIC);cls.addMethod(method);
                var emitter=ProgramEmitter.create(method,context.getHierarchy());emitter.var(0,ValueType.object(cls.getName())).invokeSpecial("<init>",emitter.var(1,ValueType.object("java.lang.ThreadGroup")),emitter.var(2,ValueType.object("java.lang.Runnable")),emitter.constant("browser-worker"));emitter.exit();
            }
        }
    }
    private static void forward(ClassHolder cls,ClassHolderTransformerContext context,String signature,boolean isStatic){JdkCompatibility.forwardTo(cls,context,signature,isStatic,"port.runtime.JdkTask4Support",true);}
}
