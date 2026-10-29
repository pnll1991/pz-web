package org.teavm.ast;

import java.util.Map;

public class BoundCheckExpr extends Expr {
   private Expr index;
   private Expr array;
   private boolean lower;

   public Expr getIndex() {
      return this.index;
   }

   public void setIndex(Expr index) {
      this.index = index;
   }

   public Expr getArray() {
      return this.array;
   }

   public void setArray(Expr array) {
      this.array = array;
   }

   public boolean isLower() {
      return this.lower;
   }

   public void setLower(boolean lower) {
      this.lower = lower;
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

      BoundCheckExpr copy = new BoundCheckExpr();
      cache.put(this, copy);
      copy.setIndex(this.index.clone(cache));
      copy.setArray(this.array != null ? this.array.clone(cache) : null);
      copy.setLower(this.lower);
      return copy;
   }
}
