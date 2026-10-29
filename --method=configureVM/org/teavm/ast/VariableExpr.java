package org.teavm.ast;

import java.util.Map;

public class VariableExpr extends Expr {
   private int index;

   public int getIndex() {
      return this.index;
   }

   public void setIndex(int index) {
      this.index = index;
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

      VariableExpr copy = new VariableExpr();
      copy.setIndex(this.index);
      cache.put(this, copy);
      return copy;
   }
}
