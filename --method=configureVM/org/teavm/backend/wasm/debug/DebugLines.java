package org.teavm.backend.wasm.debug;

import org.teavm.model.MethodReference;

public interface DebugLines {
   void advance(int var1);

   void location(String var1, int var2);

   void emptyLocation();

   void start(MethodReference var1);

   void end();
}
