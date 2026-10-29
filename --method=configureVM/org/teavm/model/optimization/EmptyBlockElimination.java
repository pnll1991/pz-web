package org.teavm.model.optimization;

import org.teavm.model.BasicBlock;
import org.teavm.model.Program;
import org.teavm.model.instructions.JumpInstruction;
import org.teavm.model.util.BasicBlockMapper;

public class EmptyBlockElimination implements MethodOptimization {
   @Override
   public boolean optimize(MethodOptimizationContext context, Program program) {
      boolean affected = true;
      int[] blockMapping = new int[program.basicBlockCount()];
      int i = 0;

      while (i < blockMapping.length) {
         blockMapping[i] = i++;
      }

      i = program.basicBlockCount() - 1;

      for (int ix = program.basicBlockCount() - 2; ix > 0; ix--) {
         BasicBlock block = program.basicBlockAt(ix);
         if (block.getPhis().isEmpty() && block.instructionCount() == 1 && block.getLastInstruction() instanceof JumpInstruction) {
            JumpInstruction insn = (JumpInstruction)block.getLastInstruction();
            if (insn.getTarget().getIndex() == ix + 1) {
               blockMapping[ix] = i;
            }
         }

         i = blockMapping[ix];
      }

      new BasicBlockMapper(blockx -> blockMapping[blockx]).transform(program);

      for (int ix = 0; ix < program.basicBlockCount(); ix++) {
         if (blockMapping[ix] != ix) {
            program.deleteBasicBlock(ix);
            affected = true;
         }
      }

      program.pack();
      return affected;
   }
}
