package org.teavm.model.optimization;

import org.teavm.model.MethodReference;
import org.teavm.model.ProgramReader;

public interface InliningStrategy {
   InliningStep start(MethodReference var1, ProgramReader var2);

   default void methodChanged(MethodReference method) {
   }
}
