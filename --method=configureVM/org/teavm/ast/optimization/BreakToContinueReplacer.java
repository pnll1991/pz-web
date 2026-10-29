package org.teavm.ast.optimization;

import java.util.List;
import org.teavm.ast.BreakStatement;
import org.teavm.ast.ContinueStatement;
import org.teavm.ast.IdentifiedStatement;
import org.teavm.ast.RecursiveVisitor;
import org.teavm.ast.Statement;

class BreakToContinueReplacer extends RecursiveVisitor {
   private IdentifiedStatement replacedBreak;
   private IdentifiedStatement replacement;
   private ContinueStatement replaceBy;

   public BreakToContinueReplacer(IdentifiedStatement replacedBreak, IdentifiedStatement replacement) {
      this.replacedBreak = replacedBreak;
      this.replacement = replacement;
   }

   @Override
   public void visit(List<Statement> statements) {
      if (statements != null) {
         for (int i = 0; i < statements.size(); i++) {
            Statement stmt = statements.get(i);
            stmt.acceptVisitor(this);
            if (this.replaceBy != null) {
               statements.set(i, this.replaceBy);
               this.replaceBy = null;
            }
         }
      }
   }

   @Override
   public void visit(BreakStatement statement) {
      if (statement.getTarget() == this.replacedBreak) {
         this.replaceBy = new ContinueStatement();
         this.replaceBy.setTarget(this.replacement);
         this.replaceBy.setLocation(statement.getLocation());
      }
   }

   @Override
   public void visit(ContinueStatement statement) {
      if (statement.getTarget() == this.replacedBreak) {
         statement.setTarget(this.replacement);
      }
   }
}
