package org.teavm.ast;

import org.teavm.model.TextLocation;

public class MonitorEnterStatement extends Statement {
   private TextLocation location;
   private Expr objectRef;

   @Override
   public void acceptVisitor(StatementVisitor visitor) {
      visitor.visit(this);
   }

   public TextLocation getLocation() {
      return this.location;
   }

   public void setLocation(TextLocation location) {
      this.location = location;
   }

   public Expr getObjectRef() {
      return this.objectRef;
   }

   public void setObjectRef(Expr objectRef) {
      this.objectRef = objectRef;
   }
}
