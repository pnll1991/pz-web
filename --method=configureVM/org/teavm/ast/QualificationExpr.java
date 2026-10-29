package org.teavm.ast;

import java.util.Map;
import org.teavm.model.FieldReference;

public class QualificationExpr extends Expr {
   private Expr qualified;
   private FieldReference field;

   public Expr getQualified() {
      return this.qualified;
   }

   public void setQualified(Expr qualified) {
      this.qualified = qualified;
   }

   public FieldReference getField() {
      return this.field;
   }

   public void setField(FieldReference field) {
      this.field = field;
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

      QualificationExpr copy = new QualificationExpr();
      cache.put(this, copy);
      copy.setField(this.field);
      copy.setQualified(this.qualified != null ? this.qualified.clone(cache) : null);
      return copy;
   }
}
