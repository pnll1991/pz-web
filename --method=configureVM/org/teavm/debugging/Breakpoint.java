package org.teavm.debugging;

import java.util.ArrayList;
import java.util.List;
import org.teavm.debugging.information.SourceLocation;
import org.teavm.debugging.javascript.JavaScriptBreakpoint;

public class Breakpoint {
   private Debugger debugger;
   volatile List<JavaScriptBreakpoint> jsBreakpoints = new ArrayList<>();
   private SourceLocation location;
   boolean valid;

   Breakpoint(Debugger debugger, SourceLocation location) {
      this.debugger = debugger;
      this.location = location;
   }

   public SourceLocation getLocation() {
      return this.location;
   }

   public void destroy() {
      this.debugger.destroyBreakpoint(this);
      this.debugger = null;
   }

   public boolean isValid() {
      return this.valid;
   }

   public boolean isDestroyed() {
      return this.debugger == null;
   }

   public Debugger getDebugger() {
      return this.debugger;
   }
}
