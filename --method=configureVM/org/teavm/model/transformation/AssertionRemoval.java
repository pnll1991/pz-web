package org.teavm.model.transformation;

import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.Instruction;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.InvokeInstruction;

public class AssertionRemoval implements ClassHolderTransformer {
   private static final MethodReference ASSERTION_METHOD = new MethodReference(Class.class, "desiredAssertionStatus", boolean.class);

   @Override
   public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
      for (MethodHolder method : cls.getMethods()) {
         if (method.getProgram() != null) {
            this.removeAssertions(method.getProgram());
         }
      }
   }

   private void removeAssertions(Program program) {
      for (BasicBlock block : program.getBasicBlocks()) {
         for (Instruction instruction : block) {
            if (instruction instanceof InvokeInstruction invoke && invoke.getInstance() != null && invoke.getMethod().equals(ASSERTION_METHOD)) {
               if (invoke.getReceiver() == null) {
                  invoke.delete();
               } else {
                  IntegerConstantInstruction replacement = new IntegerConstantInstruction();
                  replacement.setConstant(0);
                  replacement.setReceiver(invoke.getReceiver());
                  replacement.setLocation(invoke.getLocation());
                  invoke.replace(replacement);
               }
            }
         }
      }
   }
}
