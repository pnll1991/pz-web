package org.teavm.ast;

import java.util.Map;

public class UnaryExpr extends Expr {
   private UnaryOperation operation;
   private Expr operand;
   private OperationType type;

   public UnaryOperation getOperation() {
      return this.operation;
   }

   public void setOperation(UnaryOperation operation) {
      this.operation = operation;
   }

   public Expr getOperand() {
      return this.operand;
   }

   public void setOperand(Expr operand) {
      this.operand = operand;
   }

   public OperationType getType() {
      return this.type;
   }

   public void setType(OperationType type) {
      this.type = type;
   }

   @Override
   public void acceptVisitor(ExprVisitor visitor) {
      visitor.visit(this);
   }

   @Override
   protected Expr clone(Map<Expr, Expr> cache) {
      Expr known = cache.get(this);
      if (known != null) {
         return known;
      }

      UnaryExpr copy = new UnaryExpr();
      copy.setOperation(this.operation);
      copy.setOperand(this.operand != null ? this.operand.clone(cache) : null);
      copy.setType(this.type);
      return copy;
   }
}
