package org.teavm.ast;

import org.teavm.model.TextLocation;

public class ReturnStatement extends Statement {
   private Expr result;
   private TextLocation location;

   public Expr getResult() {
      return this.result;
   }

   public void setResult(Expr result) {
      this.result = result;
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
