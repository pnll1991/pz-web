package org.teavm.model.optimization;

import org.teavm.model.BasicBlock;
import org.teavm.model.Incoming;
import org.teavm.model.Program;
import org.teavm.model.Variable;
import org.teavm.model.util.InstructionVariableMapper;

public class RedundantPhiElimination implements MethodOptimization {
   @Override
   public boolean optimize(MethodOptimizationContext context, Program program) {
      boolean changed = false;
      int[] map = new int[program.variableCount()];
      int i = 0;

      while (i < map.length) {
         map[i] = i++;
      }

      for (BasicBlock block : program.getBasicBlocks()) {
         changed |= block.getPhis().removeIf(phi -> {
            if (phi.getIncomings().isEmpty()) {
               return true;
            }

            Variable singleInput = null;

            for (Incoming incoming : phi.getIncomings()) {
               if (incoming.getValue() != phi.getReceiver()) {
                  if (singleInput == null) {
                     singleInput = incoming.getValue();
                  } else if (singleInput != incoming.getValue()) {
                     return false;
                  }
               }
            }

            if (singleInput != null) {
               map[phi.getReceiver().getIndex()] = map[singleInput.getIndex()];
            }

            return true;
         });
      }

      if (changed) {
         InstructionVariableMapper mapper = new InstructionVariableMapper(v -> program.variableAt(map(map, v.getIndex())));

         for (BasicBlock block : program.getBasicBlocks()) {
            mapper.apply(block);
         }
      }

      return changed;
   }

   private static int map(int[] array, int index) {
      int result = array[index];
      if (result != index) {
         int newResult = map(array, result);
         if (newResult != result) {
            array[index] = newResult;
         }

         result = newResult;
      }

      return result;
   }
}
