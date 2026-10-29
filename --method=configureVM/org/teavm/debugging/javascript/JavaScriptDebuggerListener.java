package org.teavm.debugging.javascript;

public interface JavaScriptDebuggerListener {
   void paused(JavaScriptBreakpoint var1);

   void resumed();

   void attached();

   void detached();

   void breakpointChanged(JavaScriptBreakpoint var1);

   void scriptAdded(JavaScriptScript var1);
}
