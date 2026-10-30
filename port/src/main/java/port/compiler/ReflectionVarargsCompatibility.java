package port.compiler;

import java.util.ArrayList;
import java.util.List;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.Instruction;
import org.teavm.model.MethodHolder;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.NullConstantInstruction;

/**
 * Makes the game's {@code (Class[])null} varargs reflection work in Wasm.
 *
 * Verified cause (round 52, console-round51.txt: 215x UiConfigScript, 134x FluidContainerScript,
 * 3x ContextMenuConfigScript, 1x SpriteConfigScript = "Class cannot be created (missing no-arg
 * constructor)"): {@code ComponentScriptFactory$ScriptConstructor.findConstructor} calls
 * {@code ClassReflection.getConstructor(type, (Class[])null)} and then
 * {@code getDeclaredConstructor(type, (Class[])null)} -- javap of the pinned class shows
 * {@code aconst_null; checkcast [Ljava/lang/Class;} at both sites, and a scan of every class in the
 * pinned jar found exactly one such site, this one. On the desktop a null varargs array IS the
 * no-arg lookup (verified on JDK 25: {@code T.class.getDeclaredConstructor((Class[])null)} returns
 * the private no-arg constructor). TeaVM 0.16.0's {@code TClass.getConstructor/getDeclaredConstructor}
 * instead do {@code Arrays.equals(constructor.getParameterTypes(), parameterTypes)} and
 * {@code TArrays.equals(Object[],Object[])} returns false whenever either side is null, so the
 * private constructor that round 51 made reflectable is never found and the game drops the script.
 *
 * The fix is at the call site and keeps desktop semantics: a {@code null} handed to a
 * {@code Class[]} varargs parameter becomes an empty {@code Class[]} array, which is what the JVM's
 * varargs call would have produced if the source had written {@code new Class[0]} (and it is what
 * {@code ComponentFactory$ComponentPool.findConstructor} already writes). Nothing else is touched.
 */
public final class ReflectionVarargsCompatibility implements ClassHolderTransformer {
    /** The only game class that hands {@code null} to a {@code Class[]} varargs parameter. */
    static final String TARGET = "zombie.entity.ComponentScriptFactory$ScriptConstructor";
    static final ValueType CLASS_ARRAY = ValueType.arrayOf(ValueType.object("java.lang.Class"));

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (!cls.getName().equals(TARGET)) return;
        int fixed = 0;
        for (MethodHolder method : cls.getMethods()) {
            Program program = method.getProgram();
            if (program == null) continue;
            for (Instruction instruction : all(program)) {
                if (!(instruction instanceof CastInstruction cast)) continue;
                if (!CLASS_ARRAY.equals(cast.getTargetType())) continue;
                Variable nullValue = cast.getValue();
                if (nullValue == null) throw new IllegalStateException(TARGET + " has a cast with no value");
                Instruction producer = producerOf(nullValue, program);
                if (!(producer instanceof NullConstantInstruction)) continue;
                // new Class[0] in place of the null: same receiver, then drop the dead null/cast.
                Variable size = program.createVariable();
                IntegerConstantInstruction zero = new IntegerConstantInstruction();
                zero.setConstant(0);
                zero.setReceiver(size);
                zero.setLocation(cast.getLocation());
                ConstructArrayInstruction array = new ConstructArrayInstruction();
                array.setItemType(ValueType.object("java.lang.Class"));
                array.setSize(size);
                array.setReceiver(cast.getReceiver());
                array.setLocation(cast.getLocation());
                // insertPrevious puts each instruction directly before `cast`, so the size constant
                // goes first: the block order must be ICONST(size) -> NEWARRAY -> INVOKE.
                cast.insertPrevious(zero);
                cast.insertPrevious(array);
                if (!cast.delete()) throw new IllegalStateException("could not delete the null cast in " + TARGET);
                if (producer instanceof NullConstantInstruction dead && dead.getReceiver() == nullValue
                        && dead.getBasicBlock() != null) dead.delete();
                fixed++;
            }
        }
        if (fixed != 2) throw new IllegalStateException(
            TARGET + ": expected exactly 2 null-varargs sites (getConstructor + getDeclaredConstructor), fixed "
                + fixed + " - verify the bytecode shape before rewriting");
        verify(cls);
    }

    private static List<Instruction> all(Program program) {
        List<Instruction> instructions = new ArrayList<>();
        for (int block = 0; block < program.basicBlockCount(); block++)
            for (Instruction instruction = program.basicBlockAt(block).getFirstInstruction();
                    instruction != null; instruction = instruction.getNext())
                instructions.add(instruction);
        return instructions;
    }

    /** The instruction that defines {@code variable} inside {@code program} (null when it is a parameter). */
    private static Instruction producerOf(Variable variable, Program program) {
        for (Instruction instruction : all(program)) {
            if (instruction instanceof NullConstantInstruction nullConstant && nullConstant.getReceiver() == variable)
                return instruction;
            if (instruction instanceof CastInstruction cast && cast.getReceiver() == variable) return instruction;
            if (instruction instanceof ConstructArrayInstruction array && array.getReceiver() == variable) return instruction;
            if (instruction instanceof IntegerConstantInstruction integer && integer.getReceiver() == variable) return instruction;
            if (instruction instanceof InvokeInstruction invoke && invoke.getReceiver() == variable) return instruction;
        }
        return null;
    }

    /** Both ClassReflection lookups must now receive a constructed array, never a null. */
    private static void verify(ClassHolder cls) {
        int sites = 0;
        for (MethodHolder method : cls.getMethods()) {
            Program program = method.getProgram();
            if (program == null) continue;
            for (Instruction instruction : all(program)) {
                if (!(instruction instanceof InvokeInstruction invoke)) continue;
                String owner = invoke.getMethod().getClassName();
                if (!owner.equals("zombie.entity.util.reflect.ClassReflection")) continue;
                List<? extends Variable> arguments = invoke.getArguments();
                if (arguments.size() != 2) throw new IllegalStateException("unexpected ClassReflection arity: " + invoke.getMethod());
                Instruction producer = producerOf(arguments.get(1), program);
                if (!(producer instanceof ConstructArrayInstruction)) throw new IllegalStateException(
                    TARGET + ": " + invoke.getMethod() + " still receives a null varargs array");
                sites++;
            }
        }
        if (sites != 2) throw new IllegalStateException(TARGET + ": expected 2 ClassReflection sites, found " + sites);
    }
}
