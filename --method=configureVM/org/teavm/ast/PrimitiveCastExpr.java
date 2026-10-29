package org.teavm.ast;

import java.util.Map;

public class PrimitiveCastExpr extends Expr {
   private Expr value;
   private OperationType source;
   private OperationType target;

   @Override
   public void acceptVisitor(ExprVisitor visitor) {
      visitor.visit(this);
   }

   public Expr getValue() {
      return this.value;
   }

   public void setValue(Expr value) {
      this.value = value;
   }

   public OperationType getSource() {
      return this.source;
   }

   public void setSource(OperationType source) {
      this.source = source;
   }

   public OperationType getTarget() {
      return this.target;
   }

   public void setTarget(OperationType target) {
      this.target = target;
   }

   @Override
   protected Expr clone(Map<Expr, Expr> cache) {
      PrimitiveCastExpr copy = new PrimitiveCastExpr();
      copy.value = this.value.clone(cache);
      copy.source = this.source;
      copy.target = this.target;
      return copy;
   }
}
