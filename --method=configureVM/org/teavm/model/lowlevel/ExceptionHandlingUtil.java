package org.teavm.model.lowlevel;

import org.teavm.model.Instruction;
import org.teavm.model.MethodReference;
import org.teavm.model.instructions.BoundCheckInstruction;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ConstructMultiArrayInstruction;
import org.teavm.model.instructions.InitClassInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.MonitorEnterInstruction;
import org.teavm.model.instructions.MonitorExitInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.runtime.ExceptionHandling;

public final class ExceptionHandlingUtil {
   private static final MethodReference FILL_STACK_TRACE = new MethodReference(ExceptionHandling.class, "fillStackTrace", StackTraceElement[].class);

   private ExceptionHandlingUtil() {
   }

   public static boolean isCallInstruction(Characteristics characteristics, Instruction insn) {
      if (insn instanceof InitClassInstruction
         || insn instanceof ConstructInstruction
         || insn instanceof ConstructArrayInstruction
         || insn instanceof ConstructMultiArrayInstruction
         || insn instanceof CloneArrayInstruction
         || insn instanceof RaiseInstruction
         || insn instanceof MonitorEnterInstruction
         || insn instanceof MonitorExitInstruction
         || insn instanceof NullCheckInstruction
         || insn instanceof BoundCheckInstruction) {
         return true;
      } else if (insn instanceof CastInstruction) {
         return !((CastInstruction)insn).isWeak();
      } else {
         return insn instanceof InvokeInstruction ? isManagedMethodCall(characteristics, ((InvokeInstruction)insn).getMethod()) : false;
      }
   }

   public static boolean isManagedMethodCall(Characteristics characteristics, MethodReference method) {
      return !characteristics.isManaged(method) && !method.equals(FILL_STACK_TRACE)
         ? method.getClassName().equals(ExceptionHandling.class.getName()) && method.getName().startsWith("throw")
         : true;
   }
}
