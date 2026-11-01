package port;
import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.teavm.tooling.*;
import org.teavm.vm.TeaVMOptimizationLevel;

class CompilerRegressionTest {
    public static final class CapturedConsole extends org.teavm.classlib.impl.console.JsConsolePrintStream {
        final StringBuilder text=new StringBuilder();
        @Override public void print(String value){text.append(value);}
        public void slice(char[] text,int begin,int end){super.print(text,begin,end);}
    }
    public static final class ConsoleUtf8Probe {
        public static void main(String[] args) {
            try {
                var captured=new CapturedConsole();
                captured.print(1.5d);captured.slice("startpieceend".toCharArray(),5,10);
                if(!captured.text.toString().equals("1.5piece"))throw new AssertionError("Console double/char slice");
                String payload="ROUND5:"+"abcdefghijklmnopqrstuvwxyz".repeat(4)+":ñ😀:end\n";
                byte[] padded=("SKIP!"+payload+"IGNORE").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                System.out.write(padded,5,padded.length-11);
                System.out.flush();System.out.flush();
                byte[] utf8="ROUND5:ñ😀:split\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                for(byte b:utf8){System.out.write(b);System.out.flush();}
                System.out.write("ROUND5:after-flush\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                System.out.flush();
                System.err.write("ROUND5:stderr-complete\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                System.err.flush();
                done("passed");
            } catch(Throwable error) {done(error.toString());}
        }
        @org.teavm.jso.JSBody(params="result",script="globalThis.pzConsoleUtf8Probe=result;")
        private static native void done(String result);
    }
    public interface Marker {}
    public interface Nontrivial {void action();}
    public interface Orphan extends Nontrivial,Marker {}
    public static final class Implementor implements Marker {}
    public interface AsyncCloseable extends AutoCloseable {void close()throws InterruptedException;}
    public static final class AsyncResource implements AsyncCloseable {
        static int closed;
        public void close()throws InterruptedException{Thread.sleep(1);closed++;}
    }
    private static void withResource(AsyncCloseable resource)throws InterruptedException{
        try(resource){Thread.sleep(1);}
    }
    public static final class RootMetadataProbe {
        public static void main(String[] args){complete(new Implementor().getClass().getSuperclass().getName()+":"+Orphan.class.getName());}
        @org.teavm.jso.JSBody(params="name",script="globalThis.pzCompilerProbe=name;")
        private static native void complete(String name);
    }
    public static final class ThreadBootstrapProbe {
        public static void main(String[] args)throws Exception{
            Thread main=Thread.currentThread();ThreadGroup root=main.getThreadGroup();
            if(root==null||!main.isAlive())throw new AssertionError("main thread registration");
            Thread[] members=new Thread[4];int count=root.enumerate(members,false);boolean found=false;
            for(int i=0;i<count;i++)if(members[i]==main)found=true;
            if(!found)throw new AssertionError("main thread absent from root group");
            // Exercise membership and a suspended class-monitor section.
            // The large game's asynchronous bootstrap is verified separately.
            synchronized(ThreadGroup.class){Thread.sleep(1);}
            ThreadGroup[] inherited=new ThreadGroup[1];
            var child=new Thread(()->inherited[0]=Thread.currentThread().getThreadGroup());
            child.start();child.join();
            if(inherited[0]!=root)throw new AssertionError("inherited group");
            if(child.isAlive()||child.getThreadGroup()!=null)throw new AssertionError("terminated child");
            complete();
        }
        @org.teavm.jso.JSBody(script="globalThis.pzThreadBootstrapProbe='passed';")private static native void complete();
    }
    public static final class IntrinsicMetadataProbe {
        public static void main(String[] args)throws Exception{
            for(Class<?> type:List.of(Integer.class,Long.class)){
                boolean wide=type==Long.class;Class<?> primitive=wide?long.class:int.class;
                Object all=wide?(Object)Long.valueOf(-1):Integer.valueOf(-1);
                Object two=wide?(Object)Long.valueOf(2):Integer.valueOf(2);
                for(String name:List.of("numberOfLeadingZeros","numberOfTrailingZeros","bitCount")){
                    Object actual=type.getMethod(name,primitive).invoke(null,all);
                    int expected=name.equals("bitCount")?(wide?64:32):0;
                    if(!actual.equals(expected))throw new AssertionError(type+"."+name+"="+actual);
                }
                Object quotient=type.getMethod("divideUnsigned",primitive,primitive).invoke(null,all,two);
                if(((Number)quotient).longValue()!=(wide?Long.MAX_VALUE:Integer.MAX_VALUE))throw new AssertionError("unsigned divide");
                if(((Number)type.getMethod("remainderUnsigned",primitive,primitive).invoke(null,all,two)).longValue()!=1)throw new AssertionError("unsigned remainder");
                if(!type.getMethod("compareUnsigned",primitive,primitive).invoke(null,all,two).equals(1))throw new AssertionError("unsigned compare");
                Object zero=wide?(Object)Long.valueOf(0):Integer.valueOf(0);
                for(String name:List.of("numberOfLeadingZeros","numberOfTrailingZeros")){
                    if(!type.getMethod(name,primitive).invoke(null,zero).equals(wide?64:32))throw new AssertionError("zero bit count");
                }
            }
            if(new Implementor().getClass()!=Implementor.class)throw new AssertionError("Object metadata");
            if(java.lang.reflect.Field.class.getConstructors().length!=0||java.lang.reflect.Method.class.getConstructors().length!=0||java.lang.reflect.Constructor.class.getConstructors().length!=0)throw new AssertionError("TeaVM internal constructors leaked into JDK reflection");
            withResource(new AsyncResource());withResource(null);
            if(AsyncResource.closed!=1)throw new AssertionError("asynchronous resource cleanup");
            complete();
        }
        @org.teavm.jso.JSBody(script="globalThis.pzIntrinsicProbe='passed';")private static native void complete();
        @org.teavm.jso.JSExport public static void divideByZero(boolean wide)throws Exception{
            Class<?> type=wide?Long.class:Integer.class,primitive=wide?long.class:int.class;
            Object all=wide?(Object)Long.valueOf(-1):Integer.valueOf(-1),zero=wide?(Object)Long.valueOf(0):Integer.valueOf(0);
            type.getMethod("divideUnsigned",primitive,primitive).invoke(null,all,zero);
        }
    }
    @Test void markerInterfacesCannotReplaceTheObjectTable(){
        var classes=new LinkedHashMap<String,org.teavm.model.ClassHolder>();
        for(String name:List.of("java.lang.Object","own.Impl","own.Marker","own.Nontrivial","own.Orphan")){var cls=new org.teavm.model.ClassHolder(name);cls.setParent(name.equals("java.lang.Object")?null:"java.lang.Object");classes.put(name,cls);}
        for(String name:List.of("own.Marker","own.Nontrivial","own.Orphan")){classes.get(name).getModifiers().add(org.teavm.model.ElementModifier.INTERFACE);classes.get(name).getModifiers().add(org.teavm.model.ElementModifier.ABSTRACT);}
        classes.get("own.Impl").getInterfaces().add("own.Marker");classes.get("own.Orphan").getInterfaces().addAll(List.of("own.Nontrivial","own.Marker"));
        var method=new org.teavm.model.MethodHolder(new org.teavm.model.MethodDescriptor("action",org.teavm.model.ValueType.VOID));method.getModifiers().add(org.teavm.model.ElementModifier.ABSTRACT);classes.get("own.Nontrivial").addMethod(method);
        var source=new org.teavm.model.ListableClassReaderSource(){public Set<String> getClassNames(){return classes.keySet();}public org.teavm.model.ClassReader get(String name){return classes.get(name);}};
        var tables=new org.teavm.backend.wasm.vtable.WasmGCVirtualTableProvider(source,List.of(),ref->false);
        assertEquals("java.lang.Object",tables.lookup("java.lang.Object").getClassName());assertTrue(tables.lookup("java.lang.Object").isConcrete());
    }
    @Test void coroutineInferenceKeepsBranchReferencesNullable(){
        var type=new org.teavm.backend.wasm.model.WasmStructure("own.Receiver");
        for(boolean forceNullable:List.of(false,true)){
            var inference=new org.teavm.backend.wasm.model.instruction.WasmTypeInference(forceNullable);
            inference.typeStack.add(type.getReference());
            inference.visit(new org.teavm.backend.wasm.model.instruction.WasmNullBranch(org.teavm.backend.wasm.model.instruction.WasmNullCondition.NULL,new org.teavm.backend.wasm.model.instruction.WasmInstructionList()));
            assertEquals(forceNullable,((org.teavm.backend.wasm.model.WasmType.Reference)inference.typeStack.get(0)).isNullable());
            inference.visit(new org.teavm.backend.wasm.model.instruction.WasmCast(type.getNonNullReference()));
            assertEquals(forceNullable,((org.teavm.backend.wasm.model.WasmType.Reference)inference.typeStack.get(0)).isNullable(),"cast result respects coroutine mode");
            var block=new org.teavm.backend.wasm.model.instruction.WasmBlock(false);block.setType(type.getNonNullReference().asBlock());inference.visit(block);
            assertEquals(forceNullable,((org.teavm.backend.wasm.model.WasmType.Reference)inference.typeStack.get(1)).isNullable(),"block result respects coroutine mode");
        }
    }
    // Ronda 80 (frente H): un Class que sólo aparece como valor de una anotación anidada (como
    // @XmlElements({@XmlElement(type=LerpSequence.class)}) en InterpolatorSlot) debe llegar al resultado del
    // accesor en el análisis de dependencias; si no, TeaVM no enlaza su constructor ni su nombre para reflexión.
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ChoiceProbe{Class<?> type();}
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ChoicesProbe{ChoiceProbe[] value();}
    public static final class AnnotationOnlyBean{public int touched;public AnnotationOnlyBean(){touched=7;}}
    public static final class AnnotationClassProbe {
        @ChoicesProbe({@ChoiceProbe(type=AnnotationOnlyBean.class)}) public Object slot;
        public static void main(String[] args)throws Exception{
            var choice=AnnotationClassProbe.class.getField("slot").getAnnotation(ChoicesProbe.class).value()[0];
            Object bean=choice.type().getDeclaredConstructor().newInstance();
            complete(choice.type().getName()+":"+((AnnotationOnlyBean)bean).touched);
        }
        @org.teavm.jso.JSBody(params="name",script="globalThis.pzAnnotationClassProbe=name;")private static native void complete(String name);
    }
    @Test void annotationClassValuesReachReflection()throws Exception{
        compile(AnnotationClassProbe.class,"annotation-class",List.of(AnnotationClassProbe.class.getName(),AnnotationOnlyBean.class.getName()));
        String names=new String(Files.readAllBytes(Path.of(System.getProperty("basedir",".")).toAbsolutePath().resolve("target/web/regressions/annotation-class.wasm")),java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(names.contains(AnnotationOnlyBean.class.getName()+"::<init>"),"constructor of an annotation-only class is linked");
    }
    @Test void markerHierarchyCompilesToExecutableMetadata()throws Exception{
        compile(RootMetadataProbe.class,"object-metadata",List.of(RootMetadataProbe.class.getName()));
    }
    @Test void consoleByteWritesCompile()throws Exception{
        compile(ConsoleUtf8Probe.class,"console-utf8",List.of());
    }
    @Test void nativeIntrinsicsCompileForReflection()throws Exception{
        compile(IntrinsicMetadataProbe.class,"intrinsic-metadata",List.of(IntrinsicMetadataProbe.class.getName(),"java.lang.Object","java.lang.Integer","java.lang.Long","java.lang.Class","java.lang.reflect.Field","java.lang.reflect.Method","java.lang.reflect.Constructor"));
    }
    @Test void threadBootstrapAndGroupMembershipCompile()throws Exception{
        compile(ThreadBootstrapProbe.class,"thread-bootstrap",List.of(ThreadBootstrapProbe.class.getName(),"java.lang.Thread","java.lang.ThreadGroup","java.lang.Object"));
    }
    // Ronda 80 (frente Q): tras Esc->QUIT, TileGeometryFile.parseTileProperties -> String.intern murió con
    // "TypeError: type incompatibility when transforming from/to JS" en StringInternPool::query: el pool guarda cada
    // String en un WeakRef y su import teavm.stringDeref devolvía undefined para un String ya recolectado cuyo
    // FinalizationRegistry aún no había corrido. La sonda interna cadenas en un job, fuerza gc() en otro e interna de
    // nuevo cadenas iguales en ese mismo job (antes de los callbacks de finalización).
    public static final class InternPoolProbe {
        static String kept;
        public static void main(String[] args){}
        private static String fresh(int i){return new StringBuilder("pz-intern-").append(i).toString();}
        @org.teavm.jso.JSExport public static int internFresh(int count){
            for(int i=0;i<count;i++)fresh(i).intern();
            kept=fresh(-1).intern();
            return count;
        }
        @org.teavm.jso.JSExport public static int internAgain(int count){
            int own=0;
            for(int i=0;i<count;i++){String s=fresh(i);String interned=s.intern();if(!interned.equals(s))return -2;if(interned==s)own++;if(s.intern()!=interned)return -3;}
            String k=fresh(-1);if(k.intern()!=kept||k==kept)return -1;
            return own;
        }
    }
    private static final String INTERN_HARNESS="""
        import { pathToFileURL } from 'node:url';
        const [runtimePath, wasmPath, mode] = process.argv.slice(2);
        await import(pathToFileURL(runtimePath).href);
        // Control negativo: el deref original del pool (teavm.stringDeref = e => e.deref()).
        const options = mode === 'unpatched' ? { installImports(imports) { imports.teavm.deref = ref => ref.deref(); } } : {};
        const runtime = await TeaVM.wasmGC.load(wasmPath, options);
        runtime.exports.main([]);
        const count = runtime.exports.internFresh(4000);
        await new Promise(resolve => setTimeout(resolve, 0));
        globalThis.gc();
        let result;
        try { result = runtime.exports.internAgain(count); } catch (error) { result = `${error.name}: ${error.message}`; }
        console.log(JSON.stringify({ result }));
        """;
    private static String runInternHarness(Path root,String mode)throws Exception{
        Path script=root.resolve("target/web/regressions/intern-pool-harness.mjs");Files.writeString(script,INTERN_HARNESS);
        var process=new ProcessBuilder(root.resolve("../.toolchain/node/bin/node").normalize().toString(),"--expose-gc",script.toString(),
            root.resolve("target/web/boot.wasm-runtime.js").toString(),root.resolve("target/web/regressions/intern-pool.wasm").toString(),mode).redirectErrorStream(true).start();
        String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0,process.waitFor(),output);
        return output.strip().lines().reduce((a,b)->b).orElse("");
    }
    @Test void internPoolSurvivesCollectedEntries()throws Exception{
        compile(InternPoolProbe.class,"intern-pool",List.of());
        Path root=Path.of(System.getProperty("basedir",".")).toAbsolutePath();
        String wasm=new String(Files.readAllBytes(root.resolve("target/web/regressions/intern-pool.wasm")),java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(wasm.contains("\u0005teavm\u000bstringDeref"),"the pool no longer imports teavm.stringDeref");assertTrue(wasm.contains("\u0005teavm\u0005deref"),"it imports teavm.deref");
        String patched=runInternHarness(root,"patched");
        var number=java.util.regex.Pattern.compile("\\{\"result\":(-?\\d+)\\}").matcher(patched);
        assertTrue(number.matches(),patched);
        int own=Integer.parseInt(number.group(1));
        assertTrue(own>0,"gc() collected interned strings and intern() re-registered them: "+patched);
        assertTrue(own<=4000,patched);
        String unpatched=runInternHarness(root,"unpatched");
        assertTrue(unpatched.contains("type incompatibility"),"negative control reproduces the browser TypeError: "+unpatched);
    }
    // Ronda 81 (frente 3): StringBuilder delete/deleteCharAt/insert/replace mueven la cola con System.arraycopy
    // (array.copy) en vez del bucle char a char de TeaVM (70 s del boot en ScriptParser.stripComments). La misma
    // secuencia pseudoaleatoria corre en la JVM (referencia OpenJDK) y en Wasm GC; el resultado debe ser idéntico.
    public static final class StringBuilderCopyProbe {
        public static void main(String[] args){}
        public static String script(){
            var sb=new StringBuilder("pz-web/ronda81: /* comentario */ texto // linea\n");long seed=81;
            for(int step=0;step<3000;step++){
                seed=seed*6364136223846793005L+1442695040888963407L;int r=(int)(seed>>>33);int op=Math.floorMod(r,6);
                int len=sb.length();int a=len==0?0:Math.floorMod(r>>>3,len+1);int b=a+Math.floorMod(r>>>11,Math.max(1,len-a+1));if(b>len)b=len;
                switch(op){
                    case 0->sb.insert(a,"ins"+step);
                    case 1->{if(len>0&&a<len)sb.deleteCharAt(a);}
                    case 2->sb.delete(a,b);
                    case 3->sb.replace(a,b,step%2==0?"":"r"+step+"-larga");
                    case 4->sb.insert(a,(char)('a'+step%26));
                    default->{if(sb.length()<40)sb.append("relleno-").append(step).append(" /* x */\n");}
                }
            }
            try{sb.delete(-1,2);sb.append("sin-excepcion");}catch(StringIndexOutOfBoundsException e){sb.append("|SIOOBE");}
            try{sb.deleteCharAt(sb.length());sb.append("sin-excepcion");}catch(StringIndexOutOfBoundsException e){sb.append("|SIOOBE2");}
            return sb.length()+":"+sb;
        }
        @org.teavm.jso.JSExport public static String run(){return script();}
    }
    // Ronda 81 (frente 3): el diagnóstico de monitores (port.shim.BrowserMonitor) es opt-in; por defecto ningún
    // notifyAll/monitorExit cruza a JS. Con PZ_MONITOR_TRACE=true vuelve a contar (pzMonitorCounts).
    public static final class MonitorTraceProbe {
        static final Object lock=new Object();static int n;
        public static void main(String[] args){}
        @org.teavm.jso.JSExport public static int run(){for(int i=0;i<200;i++){synchronized(lock){n++;lock.notifyAll();}}return n;}
    }
    private static String runExportHarness(Path root,String wasm,String trace)throws Exception{
        Path script=root.resolve("target/web/regressions/export-harness.mjs");Files.writeString(script,"""
            import { pathToFileURL } from 'node:url';
            const [runtimePath, wasmPath, trace] = process.argv.slice(2);
            if (trace === '1') globalThis.PZ_MONITOR_TRACE = true;
            await import(pathToFileURL(runtimePath).href);
            const runtime = await TeaVM.wasmGC.load(wasmPath);
            runtime.exports.main([]);
            const result = runtime.exports.run();
            const counts = globalThis.pzMonitorCounts ? Object.values(globalThis.pzMonitorCounts).reduce((a, b) => a + b, 0) : 0;
            console.log(JSON.stringify({ result, counts }));
            """);
        var process=new ProcessBuilder(root.resolve("../.toolchain/node/bin/node").normalize().toString(),script.toString(),
            root.resolve("target/web/boot.wasm-runtime.js").toString(),root.resolve("target/web/regressions/"+wasm).toString(),trace).redirectErrorStream(true).start();
        String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0,process.waitFor(),output);
        return output.strip().lines().reduce((a,b)->b).orElse("");
    }
    private static String jsonString(String s){var b=new StringBuilder("\"");for(char c:s.toCharArray()){if(c=='"'||c=='\\')b.append('\\').append(c);else if(c=='\n')b.append("\\n");else if(c<0x20||c>0x7e)b.append(String.format("\\u%04x",(int)c));else b.append(c);}return b.append('"').toString();}
    @Test void stringBuilderTailMovesMatchOpenJdk()throws Exception{
        compile(StringBuilderCopyProbe.class,"string-builder-copy",List.of());
        String expected=StringBuilderCopyProbe.script();
        assertTrue(expected.endsWith("|SIOOBE|SIOOBE2"),expected);
        String out=runExportHarness(Path.of(System.getProperty("basedir",".")).toAbsolutePath(),"string-builder-copy.wasm","0");
        assertEquals("{\"result\":"+jsonString(expected)+",\"counts\":0}",out);
    }
    @Test void monitorTraceIsOptIn()throws Exception{
        compile(MonitorTraceProbe.class,"monitor-trace",List.of());
        Path root=Path.of(System.getProperty("basedir",".")).toAbsolutePath();
        assertEquals("{\"result\":200,\"counts\":0}",runExportHarness(root,"monitor-trace.wasm","0"));
        var traced=java.util.regex.Pattern.compile("\\{\"result\":200,\"counts\":(\\d+)\\}").matcher(runExportHarness(root,"monitor-trace.wasm","1"));
        assertTrue(traced.matches());assertTrue(Integer.parseInt(traced.group(1))>=200,"PZ_MONITOR_TRACE=true counts every notifyAll/monitorExit");
    }
    private void compile(Class<?> main,String name,List<String> reflectionClasses)throws Exception{
        Path root=Path.of(System.getProperty("basedir", ".")).toAbsolutePath();
        var classpath=new ArrayList<File>();classpath.add(root.resolve("target/test-classes").toFile());classpath.add(root.resolve("target/classes").toFile());
        for(String entry:Files.readString(root.resolve("target/dependency-classpath.txt")).strip().split(File.pathSeparator))classpath.add(new File(entry));
        Path reflection=root.resolve("target/compiler-regression-reflection.txt");Files.writeString(reflection,String.join("\n",reflectionClasses)+"\n");String previous=System.getProperty("pz.reflection");System.setProperty("pz.reflection",reflection.toString());
        try(var loader=new URLClassLoader(classpath.stream().map(file->{try{return file.toURI().toURL();}catch(Exception e){throw new IllegalArgumentException(e);}}).toArray(java.net.URL[]::new),getClass().getClassLoader())){
            var tool=new TeaVMTool();tool.setClassLoader(loader);tool.setClassPath(classpath);tool.setMainClass(main.getName());tool.setTargetType(TeaVMTargetType.WEBASSEMBLY_GC);tool.setOptimizationLevel(TeaVMOptimizationLevel.SIMPLE);tool.setObfuscated(false);
            if(main==ThreadBootstrapProbe.class){tool.getTransformers().add(port.compiler.CompatReferences.class.getName());tool.getTransformers().add(port.compiler.JdkCompatibility.class.getName());}
            Path output=root.resolve("target/web/regressions");Files.createDirectories(output);tool.setTargetDirectory(output.toFile());tool.setTargetFileName(name+".wasm");tool.generate();
            var diagnostics=new StringBuilder();for(var problem:tool.getProblemProvider().getProblems()){var text=new org.teavm.diagnostics.DefaultProblemTextConsumer();problem.render(text);diagnostics.append(problem.getLocation()==null?"compiler":problem.getLocation().getMethod()).append(": ").append(text.getText()).append('\n');}
            assertTrue(tool.getProblemProvider().getProblems().isEmpty(),diagnostics.toString());assertTrue(Files.size(output.resolve(name+".wasm"))>0);
        }finally{if(previous==null)System.clearProperty("pz.reflection");else System.setProperty("pz.reflection",previous);}
    }
}
