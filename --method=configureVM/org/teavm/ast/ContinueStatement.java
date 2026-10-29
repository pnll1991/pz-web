package org.teavm.ast;

import org.teavm.model.TextLocation;

public class ContinueStatement extends Statement {
   private IdentifiedStatement target;
   private TextLocation location;

   public IdentifiedStatement getTarget() {
      return this.target;
   }

   public void setTarget(IdentifiedStatement target) {
      this.target = target;
   }

   public TextLocation getLocation() {
      return this.location;
   }

   public void setLocation(TextLocation location) {
      this.location = location;
   }

   @Override
   public void acceptVisitor(StatementVisitor visitor) {
      visitor.visit(this);
   }
}
