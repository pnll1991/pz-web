package org.teavm.debugging.javascript;

import java.util.Map;
import org.teavm.common.Promise;

public interface JavaScriptDebugger {
   void addListener(JavaScriptDebuggerListener var1);

   void removeListener(JavaScriptDebuggerListener var1);

   Promise<Void> suspend();

   Promise<Void> resume();

   Promise<Void> stepInto();

   Promise<Void> stepOut();

   Promise<Void> stepOver();

   boolean isSuspended();

   boolean isAttached();

   void detach();

   JavaScriptCallFrame[] getCallStack();

   Promise<JavaScriptBreakpoint> createBreakpoint(JavaScriptLocation var1);

   Map<? extends String, ? extends JavaScriptScript> getScripts();
}
