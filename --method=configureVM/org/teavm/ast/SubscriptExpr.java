package org.teavm.ast;

import java.util.Map;

public class SubscriptExpr extends Expr {
   private Expr array;
   private Expr index;
   private ArrayType type;

   public Expr getArray() {
      return this.array;
   }

   public void setArray(Expr array) {
      this.array = array;
   }

   public Expr getIndex() {
      return this.index;
   }

   public void setIndex(Expr index) {
      this.index = index;
   }

   public ArrayType getType() {
      return this.type;
   }

   public void setType(ArrayType type) {
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

      SubscriptExpr copy = new SubscriptExpr();
      copy.setArray(this.array != null ? this.array.clone(cache) : null);
      copy.setIndex(this.index != null ? this.index.clone(cache) : null);
      copy.setType(this.type);
      return copy;
   }
}
