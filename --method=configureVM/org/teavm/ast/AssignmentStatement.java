package org.teavm.ast;

import org.teavm.model.TextLocation;

public class AssignmentStatement extends Statement {
   private Expr leftValue;
   private Expr rightValue;
   private TextLocation location;
   private boolean async;

   public Expr getLeftValue() {
      return this.leftValue;
   }

   public void setLeftValue(Expr leftValue) {
      this.leftValue = leftValue;
   }

   public Expr getRightValue() {
      return this.rightValue;
   }

   public void setRightValue(Expr rightValue) {
      this.rightValue = rightValue;
   }

   public TextLocation getLocation() {
      return this.location;
   }

   public void setLocation(TextLocation location) {
      this.location = location;
   }

   public boolean isAsync() {
      return this.async;
   }

   public void setAsync(boolean async) {
      this.async = async;
   }

   @Override
   public void acceptVisitor(StatementVisitor visitor) {
      visitor.visit(this);
   }
}
