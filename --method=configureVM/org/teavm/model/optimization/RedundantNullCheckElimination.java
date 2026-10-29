package org.teavm.model.optimization;

import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.Program;
import org.teavm.model.analysis.NullnessInformation;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.util.ProgramUtils;

public class RedundantNullCheckElimination implements MethodOptimization {
   @Override
   public boolean optimize(MethodOptimizationContext context, Program program) {
      NullnessInformation nullness = NullnessInformation.build(program, context.getMethod().getDescriptor());
      boolean hasChanges = false;

      for (BasicBlock block : program.getBasicBlocks()) {
         for (Instruction instruction : block) {
            if (instruction instanceof NullCheckInstruction nullCheck && !nullness.isSynthesized(nullCheck.getReceiver())) {
               if (nullness.isNotNull(nullCheck.getValue())) {
                  AssignInstruction assign = new AssignInstruction();
                  assign.setAssignee(nullCheck.getValue());
                  assign.setReceiver(nullCheck.getReceiver());
                  assign.setLocation(nullCheck.getLocation());
                  nullCheck.replace(assign);
                  hasChanges = true;
               } else if (nullness.isNull(nullCheck.getValue())) {
                  block.detachSuccessors();

                  while (nullCheck.getNext() != null) {
                     nullCheck.getNext().delete();
                  }

                  nullCheck.insertPreviousAll(ProgramUtils.createThrowNPEInstructions(program, nullCheck.getLocation()));
                  nullCheck.delete();
                  hasChanges = true;
               }
            }
         }
      }

      if (hasChanges) {
         new UnreachableBasicBlockEliminator().optimize(program);
      }

      nullness.dispose();
      return hasChanges;
   }
}
