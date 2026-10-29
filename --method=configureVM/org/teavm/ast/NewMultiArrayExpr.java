package org.teavm.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.teavm.model.ValueType;

public class NewMultiArrayExpr extends Expr {
   private ValueType type;
   private List<Expr> dimensions = new ArrayList<>();

   public ValueType getType() {
      return this.type;
   }

   public void setType(ValueType type) {
      this.type = type;
   }

   public List<Expr> getDimensions() {
      return this.dimensions;
   }

   @Override
   public void acceptVisitor(ExprVisitor visitor) {
      visitor.visit(this);
   }

   @Override
   protected Expr clone(Map<Expr, Expr> cache) {
      NewMultiArrayExpr copy = new NewMultiArrayExpr();
      cache.put(this, copy);

      for (Expr dimension : this.dimensions) {
         copy.dimensions.add(dimension.clone(cache));
      }

      copy.type = this.type;
      return copy;
   }
}
