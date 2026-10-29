package org.teavm.ast;

import java.util.Map;

public class ConstantExpr extends Expr {
   private Object value;

   public Object getValue() {
      return this.value;
   }

   public void setValue(Object value) {
      this.value = value;
   }

   @Override
   public void acceptVisitor(ExprVisitor visitor) {
      visitor.visit(this);
   }

   @Override
   protected Expr clone(Map<Expr, Expr> cache) {
      ConstantExpr copy = new ConstantExpr();
      copy.setValue(this.value);
      return copy;
   }
}
