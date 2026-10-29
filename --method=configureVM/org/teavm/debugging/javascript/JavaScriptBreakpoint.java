package org.teavm.debugging.javascript;

import org.teavm.common.Promise;

public interface JavaScriptBreakpoint {
   JavaScriptLocation getLocation();

   boolean isValid();

   Promise<Void> destroy();
}
