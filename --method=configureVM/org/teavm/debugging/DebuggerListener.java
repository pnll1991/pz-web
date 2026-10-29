package org.teavm.debugging;

public interface DebuggerListener {
   void resumed();

   void paused(Breakpoint var1);

   void breakpointStatusChanged(Breakpoint var1);

   void attached();

   void detached();
}
