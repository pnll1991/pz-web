package org.teavm.debugging.information;

import org.teavm.model.MethodReference;

public class DebuggerStaticCallSite extends DebuggerCallSite {
   private MethodReference method;

   DebuggerStaticCallSite(MethodReference method) {
      this.method = method;
   }

   public MethodReference getMethod() {
      return this.method;
   }

   @Override
   public void acceptVisitor(DebuggerCallSiteVisitor visitor) {
      visitor.visit(this);
   }
}
