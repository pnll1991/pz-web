package org.teavm.debugging.information;

import org.teavm.model.MethodReference;

public class DebuggerVirtualCallSite extends DebuggerCallSite {
   private MethodReference method;

   DebuggerVirtualCallSite(MethodReference method) {
      this.method = method;
   }

   public MethodReference getMethod() {
      return this.method;
   }

   public void setMethod(MethodReference method) {
      this.method = method;
   }

   @Override
   public void acceptVisitor(DebuggerCallSiteVisitor visitor) {
      visitor.visit(this);
   }
}
