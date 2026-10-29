package org.teavm.ast.optimization;

import java.util.function.IntPredicate;
import org.teavm.ast.RecursiveVisitor;
import org.teavm.ast.VariableExpr;

public class VariableAccessFinder extends RecursiveVisitor {
   private IntPredicate predicate;
   private boolean found;

   public VariableAccessFinder(IntPredicate predicate) {
      this.predicate = predicate;
   }

   public boolean isFound() {
      return this.found;
   }

   public void reset() {
      this.found = false;
   }

   @Override
   public void visit(VariableExpr expr) {
      if (this.predicate.test(expr.getIndex())) {
         this.found = true;
         this.cancel();
      }
   }
}
