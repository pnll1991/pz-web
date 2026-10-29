package port.compiler;

import java.util.ArrayList;
import java.util.List;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.Instruction;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.InvokeInstruction;

/**
 * Disables Java {@code assert} in the Wasm build, exactly as the real game runs.
 *
 * Verified cause (round 50, diag148.log): the MainThread died with
 * {@code java.lang.AssertionError} inside {@code XuiSkin$EntityUiStyle::Load} (chain
 * XuiManager::ParseScripts <- ScriptManager::PostLoadScripts <- GameWindow::initShared), right after
 * the game logged "Could not find icon: Build_AnvilStone". javap of
 * {@code zombie.scripting/ui/XuiSkin$EntityUiStyle.class} shows the usual javac shape:
 * {@code $assertionsDisabled = !XuiSkin.class.desiredAssertionStatus()} in {@code <clinit>} and
 * {@code if ($assertionsDisabled) ... else throw AssertionError} at the assert site.
 * TeaVM 0.16.0's classlib answers {@code TClass.desiredAssertionStatus()} with a hardcoded
 * {@code return true} (teavm-classlib-0.16.0-sources, TClass.java:518), so {@code $assertionsDisabled}
 * becomes {@code false} and every game assert executes -- while the real game never enables
 * assertions: {@code ProjectZomboid64.json} vmArgs have no {@code -ea}/{@code --enableAssertions}.
 * TeaVM ships {@code org.teavm.model.transformation.AssertionRemoval} (teavm-core sources) but never
 * registers it, so it is dead code.
 *
 * This transformer reproduces that transformation for every class: each
 * {@code java.lang.Class::desiredAssertionStatus()} invoke becomes the constant {@code 0}, which the
 * javac-shaped {@code <clinit>} (ifne -> iconst_0) stores as {@code $assertionsDisabled = true}. Only
 * the {@code java.lang.Class} receiver form is rewritten; nothing else in the program is touched, and
 * a receiver is required (an invoke without one cannot feed the putstatic), which is verified.
 */
public final class AssertionDisabledCompatibility implements ClassHolderTransformer {
    /** Same reference TeaVM's own (unregistered) AssertionRemoval uses. */
    static final MethodReference DESIRED = new MethodReference(Class.class, "desiredAssertionStatus", boolean.class);

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        for (MethodHolder method : cls.getMethods()) {
            var program = method.getProgram();
            if (program == null) continue;
            List<InvokeInstruction> hits = new ArrayList<>();
            for (int block = 0; block < program.basicBlockCount(); block++)
                for (Instruction instruction = program.basicBlockAt(block).getFirstInstruction();
                     instruction != null; instruction = instruction.getNext())
                    if (instruction instanceof InvokeInstruction invoke && DESIRED.equals(invoke.getMethod()))
                        hits.add(invoke);
            for (InvokeInstruction invoke : hits) {
                if (invoke.getInstance() == null) throw new IllegalStateException(
                    cls.getName() + "." + method.getName()
                        + " calls desiredAssertionStatus without a receiver - javac always uses a Class receiver");
                if (invoke.getReceiver() == null) { invoke.delete(); continue; } // result unused
                var constant = new IntegerConstantInstruction();
                constant.setConstant(0);                    // desiredAssertionStatus() == false
                constant.setReceiver(invoke.getReceiver()); // javac's <clinit> stores !that == true
                constant.setLocation(invoke.getLocation());
                invoke.replace(constant);
            }
        }
    }
}
