package org.teavm.model.optimization;

import org.teavm.model.Program;

public class UnreachableBasicBlockElimination implements MethodOptimization {
   @Override
   public boolean optimize(MethodOptimizationContext context, Program program) {
      new UnreachableBasicBlockEliminator().optimize(program);
      return false;
   }
}
