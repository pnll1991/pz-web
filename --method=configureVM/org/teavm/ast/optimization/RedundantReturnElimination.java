package org.teavm.ast.optimization;

import java.util.List;
import org.teavm.ast.AbstractStatementVisitor;
import org.teavm.ast.BlockStatement;
import org.teavm.ast.ConditionalStatement;
import org.teavm.ast.ReturnStatement;
import org.teavm.ast.SequentialStatement;
import org.teavm.ast.Statement;
import org.teavm.ast.SwitchClause;
import org.teavm.ast.SwitchStatement;
import org.teavm.ast.TryCatchStatement;

class RedundantReturnElimination extends AbstractStatementVisitor {
   @Override
   public void visit(SequentialStatement statement) {
      this.handleList(statement.getSequence());
   }

   @Override
   public void visit(ConditionalStatement statement) {
      this.handleList(statement.getConsequent());
      this.handleList(statement.getAlternative());
   }

   @Override
   public void visit(SwitchStatement statement) {
      this.handleList(statement.getDefaultClause());

      for (SwitchClause clause : statement.getClauses()) {
         this.handleList(clause.getBody());
      }
   }

   @Override
   public void visit(BlockStatement statement) {
      this.handleList(statement.getBody());
   }

   @Override
   public void visit(TryCatchStatement statement) {
      this.handleList(statement.getProtectedBody());
      this.handleList(statement.getHandler());
   }

   private void handleList(List<Statement> statements) {
      if (!statements.isEmpty()) {
         Statement last = statements.get(statements.size() - 1);
         if (last instanceof ReturnStatement && ((ReturnStatement)last).getResult() == null) {
            statements.remove(statements.size() - 1);
         } else {
            last.acceptVisitor(this);
         }
      }
   }
}
