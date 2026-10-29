package org.teavm.diagnostics;

import org.teavm.model.CallLocation;

public interface Diagnostics {
   void error(CallLocation var1, String var2, Object... var3);

   void warning(CallLocation var1, String var2, Object... var3);
}
