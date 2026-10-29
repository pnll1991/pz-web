// Offline check of StateParamCallerCompatibility against the pinned game jar (never committed content
// of the jar: this prints only counts and instruction shapes of OUR transformer).
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import org.teavm.model.*;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.parsing.ClasspathClassHolderSource;
import org.teavm.parsing.resource.ResourceProvider;
import port.compiler.StateParamCallerCompatibility;

public class Check {
    public static void main(String[] args) throws Exception {
        List<URL> urls = new ArrayList<>();
        for (String entry : args[0].split(File.pathSeparator)) urls.add(new File(entry).toURI().toURL());
        var loader = new URLClassLoader(urls.toArray(URL[]::new), Check.class.getClassLoader());
        var files = new ArrayList<File>();
        for (String entry : args[0].split(File.pathSeparator)) files.add(new File(entry));
        var source = new ClasspathClassHolderSource(ResourceProvider.ofClassPath(files), new ReferenceCache(), loader);
        var transformer = new StateParamCallerCompatibility();

        var param = source.get("zombie.ai.State$Param");
        if (param == null) throw new IllegalStateException("zombie.ai.State$Param not found on classpath");
        transformer.transformClass(param, null);
        var ctor = param.getMethod(new MethodDescriptor("<init>", ValueType.object("java.lang.String"),
            ValueType.object("java.lang.Class"), ValueType.object("java.util.function.Supplier"), ValueType.VOID));
        if (ctor == null) throw new IllegalStateException("Param.<init>(String,Class,Supplier)V not found");
        boolean callsWalk = false, callsCallerClass = false;
        for (int b = 0; b < ctor.getProgram().basicBlockCount(); b++)
            for (var ins : ctor.getProgram().basicBlockAt(b))
                if (ins instanceof InvokeInstruction call) {
                    String name = call.getMethod().getName();
                    if (name.equals("walk")) callsWalk = true;
                    if (name.equals("callerClass")) callsCallerClass = true;
                }
        System.out.println("param.init: walk=" + callsWalk + " callerClass=" + callsCallerClass);
        if (callsWalk || !callsCallerClass) throw new IllegalStateException("Param.<init> was not redirected");

        int classes = 0, sites = 0, enter = 0, exit = 0;
        for (String name : List.of("zombie.ai.states.AttackState", "zombie.ai.states.ClimbOverFenceState",
                "zombie.ai.states.PlayerDraggingCorpse", "zombie.ai.states.animals.AnimalClimbOverFenceState")) {
            var cls = source.get(name);
            if (cls == null) { System.out.println("MISSING " + name); continue; }
            transformer.transformClass(cls, null);
            classes++;
            for (var m : cls.getMethods()) {
                var p = m.getProgram(); if (p == null) continue;
                int local = 0;
                for (int b = 0; b < p.basicBlockCount(); b++)
                    for (var ins : p.basicBlockAt(b))
                        if (ins instanceof InvokeInstruction call) {
                            String n = call.getMethod().getName();
                            if (call.getMethod().getClassName().equals("port.runtime.StateParamCaller")) {
                                if (n.equals("enter")) enter++; else if (n.equals("exit")) exit++;
                            }
                            if (call.getMethod().getClassName().equals("zombie.ai.State$Param") && n.startsWith("of")) local++;
                        }
                sites += local;
            }
            System.out.println("  " + name + " enter/exit so far: " + enter + "/" + exit);
        }
        System.out.println("classes=" + classes + " factorySites=" + sites + " enter=" + enter + " exit=" + exit);
        if (enter != exit) throw new IllegalStateException("unbalanced enter/exit");
        if (enter != sites) throw new IllegalStateException("sites not wrapped: sites=" + sites + " enter=" + enter);
        System.out.println("OK");
    }
}
