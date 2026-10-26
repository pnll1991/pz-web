package port.compiler;

import java.util.ArrayList;
import org.teavm.model.AccessLevel;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldHolder;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;

/** Adds narrowly implemented JDK APIs; never replaces missing APIs with success. */
public final class JdkCompatibility implements ClassHolderTransformer {
    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        switch (cls.getName()) {
            case "java.lang.Math" -> {
                forward(cls, context, "fma(FFF)F", true);
                forward(cls, context, "fma(DDD)D", true);
            }
            case "java.util.regex.Matcher" -> forward(cls, context, "replaceAll(Ljava/util/function/Function;)Ljava/lang/String;", false);
            case "java.util.zip.CRC32" -> forward(cls, context, "update(Ljava/nio/ByteBuffer;)V", false);
            case "java.util.GregorianCalendar" -> forward(cls, context, "toInstant()Ljava/time/Instant;", false);
            case "java.util.Collection" -> forward(cls, context, "parallelStream()Ljava/util/stream/Stream;", false);
            case "java.lang.System" -> {
                forward(cls, context, "getenv()Ljava/util/Map;", true);
                forward(cls, context, "mapLibraryName(Ljava/lang/String;)Ljava/lang/String;", true);
                forward(cls, context, "load(Ljava/lang/String;)V", true);
                forward(cls, context, "loadLibrary(Ljava/lang/String;)V", true);
                forward(cls, context, "exit(I)V", true);
            }
            case "java.lang.Runtime" -> {
                forward(cls, context, "addShutdownHook(Ljava/lang/Thread;)V", false);
                forward(cls, context, "maxMemory()J", false);
                for (String signature : new String[]{
                        "exec(Ljava/lang/String;)Ljava/lang/Process;", "exec([Ljava/lang/String;)Ljava/lang/Process;",
                        "exec(Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/Process;", "exec([Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/Process;",
                        "exec(Ljava/lang/String;[Ljava/lang/String;Ljava/io/File;)Ljava/lang/Process;", "exec([Ljava/lang/String;[Ljava/lang/String;Ljava/io/File;)Ljava/lang/Process;"})
                    forward(cls, context, signature, false);
            }
            case "java.io.File" -> {
                forward(cls, context, "toPath()Ljava/nio/file/Path;", false);
                forward(cls, context, "getTotalSpace()J", false);
                forward(cls, context, "getFreeSpace()J", false);
                forward(cls, context, "getUsableSpace()J", false);
                forward(cls, context, "setReadable(ZZ)Z", false);
            }
            case "java.lang.ClassLoader" -> forward(cls, context, "loadClass(Ljava/lang/String;)Ljava/lang/Class;", false);
            case "java.util.UUID" -> {
                var method = new MethodHolder(MethodDescriptor.parse("<init>(JJ)V"));
                method.setLevel(AccessLevel.PUBLIC); cls.addMethod(method);
                var emitter = ProgramEmitter.create(method, context.getHierarchy());
                var value = emitter.invoke(new MethodReference("port.runtime.JdkSupport", MethodDescriptor.parse("uuidValue(JJ)Ljava/lang/String;")),
                        emitter.var(1, ValueType.LONG), emitter.var(2, ValueType.LONG));
                emitter.var(0, ValueType.object(cls.getName())).setField("value", value); emitter.exit();
                forward(cls, context, "getMostSignificantBits()J", false);
                forward(cls, context, "getLeastSignificantBits()J", false);
            }
            case "java.util.concurrent.ConcurrentHashMap" -> {
                for (String signature : new String[]{"newKeySet()Ljava/util/concurrent/ConcurrentHashMap$KeySetView;", "newKeySet(I)Ljava/util/concurrent/ConcurrentHashMap$KeySetView;"}) {
                    var method = new MethodHolder(MethodDescriptor.parse(signature)); method.setLevel(AccessLevel.PUBLIC);
                    method.getModifiers().add(ElementModifier.STATIC); cls.addMethod(method);
                    var emitter = ProgramEmitter.create(method, context.getHierarchy());
                    ValueEmitter[] args = method.parameterCount() == 0 ? new ValueEmitter[0] : new ValueEmitter[]{emitter.var(1, ValueType.INTEGER)};
                    emitter.construct("java.util.concurrent.ConcurrentHashMap$KeySetView", args).returnValue();
                }
            }
            case "java.io.PrintStream" -> {
                var method = new MethodHolder(MethodDescriptor.parse("<init>(Ljava/io/File;)V"));
                method.setLevel(AccessLevel.PUBLIC); cls.addMethod(method);
                var emitter = ProgramEmitter.create(method, context.getHierarchy());
                var out = emitter.construct("java.io.FileOutputStream", emitter.var(1, ValueType.object("java.io.File")));
                emitter.var(0, ValueType.object(cls.getName())).invokeSpecial("<init>", out.cast(ValueType.object("java.io.OutputStream")));
                emitter.exit();
            }
            case "java.lang.String" -> {
                var method = new MethodHolder(MethodDescriptor.parse("<init>([BIII)V"));
                method.setLevel(AccessLevel.PUBLIC); cls.addMethod(method);
                var emitter = ProgramEmitter.create(method, context.getHierarchy());
                var chars = emitter.invoke(new MethodReference("port.runtime.JdkSupport", MethodDescriptor.parse("highBytes([BIII)[C")),
                        emitter.var(1, ValueType.arrayOf(ValueType.BYTE)), emitter.var(2, ValueType.INTEGER),
                        emitter.var(3, ValueType.INTEGER), emitter.var(4, ValueType.INTEGER));
                emitter.var(0, ValueType.object(cls.getName())).invokeSpecial("<init>", chars); emitter.exit();
            }
            case "java.io.Reader" -> forward(cls, context, "transferTo(Ljava/io/Writer;)J", false);
            case "java.nio.file.Path" -> forward(cls, context, "resolve(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;", false);
            case "java.lang.Thread" -> { forward(cls, context, "onSpinWait()V", true); threads(cls,context); }
            case "java.io.FileInputStream" -> {
                channel(cls, context, true);
                // The pinned classlib returns a signed byte from read(). Keep
                // EOF distinct from 0xff, as required by InputStream's contract.
                var method = cls.getMethod(new MethodDescriptor("read", ValueType.INTEGER));
                var emitter = ProgramEmitter.create(method, context.getHierarchy());
                emitter.invoke(new MethodReference("port.runtime.JdkSupport", "read", ValueType.object("java.io.InputStream"), ValueType.INTEGER),
                        emitter.var(0, ValueType.object("java.io.InputStream"))).returnValue();
            }
            case "java.io.FileOutputStream" -> channel(cls, context, false);
            case "java.io.RandomAccessFile" -> channel(cls, context, true);
            default -> { }
        }
    }

    private static void threads(ClassHolder cls, ClassHolderTransformerContext context) {
        var thread = ValueType.object("java.lang.Thread"); var group = ValueType.object("java.lang.ThreadGroup");
        var runnable = ValueType.object("java.lang.Runnable"); var string = ValueType.object("java.lang.String");
        var constructor = cls.getMethod(new MethodDescriptor("<init>",runnable,string,ValueType.VOID));
        var program = constructor.getProgram();
        for (int i = 0; i < program.basicBlockCount(); i++) {
            var block = program.basicBlockAt(i);
            if (block.getLastInstruction() instanceof ExitInstruction exit) {
                var register = new InvokeInstruction(); register.setType(InvocationType.SPECIAL);
                register.setMethod(new MethodReference("java.lang.ThreadGroup","registerDefault",thread,ValueType.VOID));
                register.setArguments(program.variableAt(0)); exit.insertPrevious(register);
            }
        }
        var start = cls.getMethod(new MethodDescriptor("start",ValueType.VOID)).getProgram();
        var hook = new InvokeInstruction(); hook.setType(InvocationType.SPECIAL);
        hook.setMethod(new MethodReference("java.lang.ThreadGroup","beforeStart",thread,ValueType.VOID));
        hook.setArguments(start.variableAt(0)); start.basicBlockAt(0).getFirstInstruction().insertPrevious(hook);
        var grouped = new MethodHolder(new MethodDescriptor("<init>",group,runnable,string,ValueType.VOID));
        grouped.setLevel(AccessLevel.PUBLIC); cls.addMethod(grouped);
        var emitter = ProgramEmitter.create(grouped,context.getHierarchy());
        var self = emitter.var(0,thread);
        self.invokeSpecial("<init>",emitter.var(2,runnable),emitter.var(3,string));
        emitter.invoke(new MethodReference("java.lang.ThreadGroup","assign",thread,group,ValueType.VOID),self,emitter.var(1,group)); emitter.exit();
        var getter = new MethodHolder(new MethodDescriptor("getThreadGroup",group)); getter.setLevel(AccessLevel.PUBLIC); cls.addMethod(getter);
        emitter = ProgramEmitter.create(getter,context.getHierarchy());
        emitter.invoke(new MethodReference("java.lang.ThreadGroup","groupOf",thread,group),emitter.var(0,thread)).returnValue();
        var handlerType = ValueType.object("java.lang.Thread$UncaughtExceptionHandler");
        var handler = cls.getMethod(new MethodDescriptor("getUncaughtExceptionHandler",handlerType));
        emitter = ProgramEmitter.create(handler,context.getHierarchy()); self = emitter.var(0,thread);
        emitter.invoke(new MethodReference("java.lang.ThreadGroup","handler",thread,handlerType,handlerType),self,self.getField("uncaughtExceptionHandler",handlerType)).returnValue();
        var alive = cls.getMethod(new MethodDescriptor("isAlive",ValueType.BOOLEAN));
        emitter = ProgramEmitter.create(alive,context.getHierarchy()); self = emitter.var(0,thread);
        emitter.invoke(new MethodReference("java.lang.ThreadGroup","alive",thread,ValueType.BOOLEAN,ValueType.BOOLEAN),self,self.getField("alive",ValueType.BOOLEAN)).returnValue();
        var join = cls.getMethod(new MethodDescriptor("join",ValueType.LONG,ValueType.INTEGER,ValueType.VOID));
        emitter = ProgramEmitter.create(join,context.getHierarchy()); self = emitter.var(0,thread);
        emitter.invoke(new MethodReference("port.runtime.JdkSupport","join",thread,ValueType.LONG,ValueType.INTEGER,ValueType.object("java.lang.Object"),ValueType.VOID),
                self,emitter.var(1,ValueType.LONG),emitter.var(2,ValueType.INTEGER),self.getField("finishedLock",ValueType.object("java.lang.Object")));
        emitter.exit();
    }

    private static void channel(ClassHolder cls, ClassHolderTransformerContext context, boolean read) {
        var type = ValueType.object("java.nio.channels.FileChannel");
        var field = new FieldHolder("browserChannel"); field.setType(type); field.setLevel(AccessLevel.PRIVATE); cls.addField(field);
        var method = new MethodHolder(new MethodDescriptor("getChannel", type)); method.setLevel(AccessLevel.PUBLIC); cls.addMethod(method);
        var emitter = ProgramEmitter.create(method, context.getHierarchy());
        var self = emitter.var(0, ValueType.object(cls.getName()));
        var existing = self.getField("browserChannel", type);
        emitter.when(existing.isNotNull()).thenDo(() -> existing.returnValue());
        var accessor = self.getField("accessor", ValueType.object("org.teavm.runtime.fs.VirtualFileAccessor"));
        var writable = cls.getName().equals("java.io.RandomAccessFile")
                ? emitter.invoke(new MethodReference("port.runtime.JdkSupport", MethodDescriptor.parse("not(Z)Z")), self.getField("readOnly", ValueType.BOOLEAN))
                : emitter.constant(read ? 0 : 1).cast(ValueType.BOOLEAN);
        var result = emitter.construct("java.nio.channels.FileChannel", accessor,
                emitter.constant(read ? 1 : 0).cast(ValueType.BOOLEAN), writable,
                self.cast(ValueType.object("java.io.Closeable")));
        self.setField("browserChannel", result); result.returnValue();
    }

    private static void forward(ClassHolder cls, ClassHolderTransformerContext context, String signature, boolean isStatic) {
        MethodDescriptor descriptor = MethodDescriptor.parse(signature);
        if (cls.getMethod(descriptor) != null) return;
        var method = new MethodHolder(descriptor);
        method.setLevel(AccessLevel.PUBLIC);
        if (isStatic) method.getModifiers().add(ElementModifier.STATIC);
        cls.addMethod(method);
        var emitter = ProgramEmitter.create(method, context.getHierarchy());
        var values = new ArrayList<ValueEmitter>();
        var types = new ArrayList<ValueType>();
        if (!isStatic) {
            ValueType owner = ValueType.object(cls.getName());
            types.add(owner); values.add(emitter.var(0, owner));
        }
        for (int i = 0; i < method.parameterCount(); i++) {
            types.add(method.parameterType(i)); values.add(emitter.var(i + 1, method.parameterType(i)));
        }
        types.add(method.getResultType());
        var result = emitter.invoke(new MethodReference("port.runtime.JdkSupport",
                new MethodDescriptor(descriptor.getName(), types.toArray(ValueType[]::new))), values.toArray(ValueEmitter[]::new));
        if (method.getResultType().equals(ValueType.VOID)) emitter.exit(); else result.returnValue();
    }
}
