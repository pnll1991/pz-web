package org.teavm.ast;

import java.util.Map;
import org.teavm.model.ValueType;

public class NewArrayExpr extends Expr {
   private ValueType type;
   private Expr length;

   public ValueType getType() {
      return this.type;
   }

   public void setType(ValueType type) {
      this.type = type;
   }

   public Expr getLength() {
      return this.length;
   }

   public void setLength(Expr length) {
      this.length = length;
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

      NewArrayExpr copy = new NewArrayExpr();
      cache.put(this, copy);
      copy.setType(this.type);
      copy.setLength(this.length != null ? this.length.clone(cache) : null);
      return copy;
   }
}
