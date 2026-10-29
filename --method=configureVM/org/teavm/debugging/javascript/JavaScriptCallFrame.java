package org.teavm.debugging.javascript;

import java.util.Map;
import org.teavm.common.Promise;

public interface JavaScriptCallFrame {
   JavaScriptDebugger getDebugger();

   JavaScriptLocation getLocation();

   Promise<Map<String, ? extends JavaScriptVariable>> getVariables();

   JavaScriptValue getThisVariable();

   JavaScriptValue getClosureVariable();

   Promise<byte[]> getMemory(int var1, int var2);
}
