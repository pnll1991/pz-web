package org.teavm.model.optimization;

import org.teavm.model.MethodReference;
import org.teavm.model.ProgramReader;

public interface InliningContext {
   boolean isUsedOnce(MethodReference var1);

   ProgramReader getProgram(MethodReference var1);

   int getDepth();
}
