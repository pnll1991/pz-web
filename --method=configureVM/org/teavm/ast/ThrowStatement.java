package org.teavm.ast;

import org.teavm.model.TextLocation;

public class ThrowStatement extends Statement {
   private Expr exception;
   private TextLocation location;

   public Expr getException() {
      return this.exception;
   }

   public void setException(Expr exception) {
      this.exception = exception;
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
