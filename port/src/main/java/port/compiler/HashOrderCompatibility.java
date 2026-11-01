package port.compiler;

import java.util.ArrayList;
import java.util.List;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.Instruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.InvokeInstruction;

/**
 * Round 82: SpriteOverlayConfigScript.getVersion feeds a SHA-256 (checked by the server) by iterating its HashMap
 * allStyles; TeaVM's HashMap has another iteration order than the JDK's, so the hash differed. In this one class every
 * {@code new HashMap} becomes a LinkedHashMap (insertion order is the information the JDK order depends on) and
 * {@code HashMap.values()} becomes port.runtime.text.JdkHashOrder.values(map). The other 12 getVersion bodies
 * (checked with javap) do not iterate a map or set.
 */
public final class HashOrderCompatibility implements ClassHolderTransformer {
    static final String TARGET = "zombie.scripting.entity.components.spriteconfig.SpriteOverlayConfigScript";
    static final String BRIDGE = "port.runtime.text.JdkHashOrder";
    private static final String HASH_MAP = "java.util.HashMap", LINKED = "java.util.LinkedHashMap";

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (!cls.getName().equals(TARGET)) return;
        int constructs = 0, iterations = 0;
        for (MethodHolder method : cls.getMethods()) {
            var program = method.getProgram();
            if (program == null) continue;
            List<Instruction> found = new ArrayList<>();
            for (int b = 0; b < program.basicBlockCount(); b++)
                for (Instruction i = program.basicBlockAt(b).getFirstInstruction(); i != null; i = i.getNext()) found.add(i);
            for (Instruction i : found) {
                if (i instanceof ConstructInstruction construct && construct.getType().equals(HASH_MAP)) {
                    construct.setType(LINKED); constructs++;
                } else if (i instanceof InvokeInstruction call && call.getMethod().getClassName().equals(HASH_MAP)) {
                    MethodReference m = call.getMethod();
                    if (m.getName().equals("<init>")) call.setMethod(new MethodReference(LINKED, m.getDescriptor()));
                    else if (m.getName().equals("values") && m.parameterCount() == 0 && method.getName().equals("getVersion")) {
                        var replacement = new InvokeInstruction();
                        replacement.setType(InvocationType.SPECIAL);
                        replacement.setMethod(new MethodReference(BRIDGE, "values", ValueType.object("java.util.Map"),
                            ValueType.object("java.util.Collection")));
                        replacement.setArguments(call.getInstance());
                        replacement.setReceiver(call.getReceiver());
                        replacement.setLocation(call.getLocation());
                        call.replace(replacement); iterations++;
                    }
                }
            }
        }
        if (constructs == 0 || iterations != 1) throw new IllegalStateException(
            TARGET + ": expected new HashMap + one HashMap.values() in getVersion, found " + constructs + "/" + iterations
                + " - the script class changed, refusing to guess");
    }
}
