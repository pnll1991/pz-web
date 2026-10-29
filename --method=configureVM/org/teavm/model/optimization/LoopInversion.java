package org.teavm.model.optimization;

import org.teavm.model.MethodReader;
import org.teavm.model.Program;

public class LoopInversion implements MethodOptimization {
   @Override
   public boolean optimize(MethodOptimizationContext context, Program program) {
      MethodReader method = context.getMethod();
      return new LoopInversionImpl(program, method.getReference(), method.parameterCount() + 1).apply();
   }
}
