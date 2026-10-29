package org.teavm.ast;

import java.util.Map;

public class UnwrapArrayExpr extends Expr {
   private ArrayType elementType;
   private Expr array;

   public UnwrapArrayExpr(ArrayType elementType) {
      this.elementType = elementType;
   }

   public ArrayType getElementType() {
      return this.elementType;
   }

   public Expr getArray() {
      return this.array;
   }

   public void setArray(Expr array) {
      this.array = array;
   }

   @Override
   public void acceptVisitor(ExprVisitor visitor) {
      visitor.visit(this);
   }

   protected UnwrapArrayExpr clone(Map<Expr, Expr> cache) {
      UnwrapArrayExpr copy = (UnwrapArrayExpr)cache.get(this);
      if (copy == null) {
         copy = new UnwrapArrayExpr(this.elementType);
         copy.array = this.array != null ? this.array.clone(cache) : null;
         cache.put(copy, copy);
      }

      return copy;
   }
}
