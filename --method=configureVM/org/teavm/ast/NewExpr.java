package org.teavm.ast;

import java.util.Map;

public class NewExpr extends Expr {
   private String constructedClass;

   public String getConstructedClass() {
      return this.constructedClass;
   }

   public void setConstructedClass(String constructedClass) {
      this.constructedClass = constructedClass;
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

      NewExpr copy = new NewExpr();
      copy.setConstructedClass(this.constructedClass);
      cache.put(this, copy);
      return copy;
   }
}
