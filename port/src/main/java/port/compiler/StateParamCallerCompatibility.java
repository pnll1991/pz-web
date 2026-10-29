package port.compiler;

import java.util.ArrayList;
import java.util.List;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.instructions.ClassConstantInstruction;
import org.teavm.model.instructions.InvokeInstruction;

/**
 * Fixes the {@code zombie.ai.State$Param} caller lookup for Wasm GC (docs/notes-from-code.md, Ronda 47).
 *
 * {@code State$Param.<init>} discovers its caller with
 * {@code StackWalker.getInstance(RETAIN_CLASS_REFERENCE).walk(s -> s.map(StackFrame::getDeclaringClass)
 * .filter(not(this.getClass())).findFirst().orElse(null))}, then calls
 * {@code State.class.isAssignableFrom(caller)}. TeaVM's {@code TThread.getStackTrace()} returns an empty
 * array, so the walk yields {@code null} and {@code isAssignableFrom(null)} is an uncatchable
 * "dereferencing a null pointer" trap that kills the MainThread (measured in {@code diag135.log} through
 * {@code AttackState::<clinit>} via {@code LuaJavaClassExposer::exposeStatics}).
 *
 * Every factory ({@code of}, {@code ofBool}, {@code ofSupplier}, ...) lives in {@code Param} itself, so on
 * the JVM the walk always returns the class of the call site -- which is known at compile time. This
 * transformer wraps each static factory call site in class {@code C} with
 * {@code StateParamCaller.enter(C)} / {@code exit()}, and replaces the {@code StackWalker.walk} call in
 * {@code Param.<init>} with {@code StateParamCaller.callerClass()}. The build fails if the bytecode does
 * not have that exact shape, instead of rewriting a newer game build blindly.
 */
public final class StateParamCallerCompatibility implements ClassHolderTransformer {
    private static final String PARAM = "zombie.ai.State$Param";
    private static final String RUNTIME = "port.runtime.StateParamCaller";
    private static final ValueType CLASS = ValueType.object("java.lang.Class");

    @Override
    public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (cls.getName().equals(PARAM)) {
            redirectCallerLookup(cls);
            return;
        }
        int wrapped = wrapFactoryCallSites(cls);
        if (wrapped > 0 && !cls.getName().startsWith("zombie.ai.states.")) throw new IllegalStateException(
            "State$Param factory call site outside zombie.ai.states: " + cls.getName() + " (" + wrapped
                + " sites) - verify the bytecode shape before instrumenting it");
    }

    /** Param.<init>(String,Class,Supplier): the StackWalker.walk becomes the compile-time caller. */
    private static void redirectCallerLookup(ClassHolder cls) {
        var constructor = cls.getMethod(new MethodDescriptor("<init>", ValueType.object("java.lang.String"),
            CLASS, ValueType.object("java.util.function.Supplier"), ValueType.VOID));
        if (constructor == null) throw new IllegalStateException(PARAM + ".<init>(String,Class,Supplier)V is missing");
        var program = constructor.getProgram();
        if (program == null) throw new IllegalStateException(PARAM + ".<init> has no code");
        List<InvokeInstruction> walks = new ArrayList<>();
        for (int block = 0; block < program.basicBlockCount(); block++)
            for (Instruction instruction = program.basicBlockAt(block).getFirstInstruction();
                    instruction != null; instruction = instruction.getNext())
                // Match by name+descriptor: the owner is TeaVM's StackWalker or this repo's compat one,
                // depending on which substitution reached this holder, and both have the same contract.
                if (instruction instanceof InvokeInstruction invoke
                        && invoke.getMethod().getName().equals("walk")
                        && invoke.getMethod().signatureToString().equals(
                            "(Ljava/util/function/Function;)Ljava/lang/Object;")) walks.add(invoke);
        if (walks.size() != 1) throw new IllegalStateException(
            "Expected exactly one StackWalker.walk(Function)Object call in " + PARAM + ".<init>, found "
                + walks.size());
        var walk = walks.get(0);
        walk.setMethod(new MethodReference(RUNTIME, "callerClass", ValueType.object("java.lang.Object")));
        walk.setInstance(null);
        walk.setArguments();
    }

    /** Wraps every static call to a Param factory with enter(C)/exit(); returns how many were wrapped. */
    private static int wrapFactoryCallSites(ClassHolder cls) {
        int wrapped = 0;
        for (var method : cls.getMethods()) {
            var program = method.getProgram();
            if (program == null) continue;
            for (var found : collectFactories(program)) {
                wrapped += wrap(found.call, found.program, cls.getName());
            }
        }
        return wrapped;
    }

    private record FactorySite(InvokeInstruction call, Program program) { }

    private static List<FactorySite> collectFactories(Program program) {
        List<FactorySite> sites = new ArrayList<>();
        for (int block = 0; block < program.basicBlockCount(); block++)
            for (Instruction instruction = program.basicBlockAt(block).getFirstInstruction();
                    instruction != null; instruction = instruction.getNext())
                if (instruction instanceof InvokeInstruction invoke
                        && invoke.getMethod().getClassName().equals(PARAM)
                        && invoke.getMethod().getName().startsWith("of")
                        && invoke.getInstance() == null) sites.add(new FactorySite(invoke, program));
        return sites;
    }

    private static int wrap(InvokeInstruction call, Program program, String callerClass) {
        var enter = new InvokeInstruction();
        enter.setType(org.teavm.model.instructions.InvocationType.VIRTUAL);
        enter.setMethod(new MethodReference(RUNTIME, "enter", CLASS, ValueType.VOID));
        var owner = program.createVariable();
        var constant = new ClassConstantInstruction();
        constant.setConstant(ValueType.object(callerClass));
        constant.setReceiver(owner);
        enter.setArguments(owner);
        call.insertPrevious(constant);
        call.insertPrevious(enter);

        var exit = new InvokeInstruction();
        exit.setType(org.teavm.model.instructions.InvocationType.VIRTUAL);
        exit.setMethod(new MethodReference(RUNTIME, "exit", ValueType.VOID));
        call.insertNext(exit);
        return 1;
    }
}
