package org.teavm.model.optimization;

import org.teavm.model.Program;

public interface MethodOptimization {
   boolean optimize(MethodOptimizationContext var1, Program var2);
}
