package org.teavm.model.optimization;

import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.Program;
import org.teavm.model.Variable;
import org.teavm.model.instructions.UnwrapArrayInstruction;
import org.teavm.model.util.DefinitionExtractor;

public class ArrayUnwrapMotion implements MethodOptimization {
   @Override
   public boolean optimize(MethodOptimizationContext context, Program program) {
      for (int i = 0; i < program.basicBlockCount(); i++) {
         this.optimize(program.basicBlockAt(i));
      }

      return false;
   }

   private void optimize(BasicBlock block) {
      for (Instruction insn : block) {
         if (insn instanceof UnwrapArrayInstruction unwrap) {
            Instruction def = this.whereDefined(insn, unwrap.getArray());
            insn.delete();
            if (def == null) {
               block.addFirst(unwrap);
            } else {
               def.insertNext(unwrap);
               unwrap.setLocation(def.getLocation());
            }
         }
      }
   }

   private Instruction whereDefined(Instruction instruction, Variable var) {
      DefinitionExtractor def = new DefinitionExtractor();

      while (instruction != null) {
         instruction.acceptVisitor(def);

         for (Variable defVar : def.getDefinedVariables()) {
            if (defVar == var) {
               return instruction;
            }
         }

         instruction = instruction.getPrevious();
      }

      return instruction;
   }
}
