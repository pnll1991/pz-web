package org.teavm.backend.wasm.debug;

import org.teavm.backend.wasm.debug.info.VariableType;

public interface DebugVariables {
   void startSequence(int var1);

   void type(String var1, VariableType var2);

   void range(String var1, int var2, int var3, int var4);

   void endSequence();
}
