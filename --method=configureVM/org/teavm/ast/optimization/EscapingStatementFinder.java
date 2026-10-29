package org.teavm.ast.optimization;

import java.util.List;
import org.teavm.ast.AssignmentStatement;
import org.teavm.ast.BlockStatement;
import org.teavm.ast.BreakStatement;
import org.teavm.ast.ConditionalStatement;
import org.teavm.ast.ContinueStatement;
import org.teavm.ast.GotoPartStatement;
import org.teavm.ast.InitClassStatement;
import org.teavm.ast.MonitorEnterStatement;
import org.teavm.ast.MonitorExitStatement;
import org.teavm.ast.ReturnStatement;
import org.teavm.ast.SequentialStatement;
import org.teavm.ast.Statement;
import org.teavm.ast.StatementVisitor;
import org.teavm.ast.SwitchClause;
import org.teavm.ast.SwitchStatement;
import org.teavm.ast.ThrowStatement;
import org.teavm.ast.TryCatchStatement;
import org.teavm.ast.WhileStatement;

class EscapingStatementFinder implements StatementVisitor {
   AllBlocksCountVisitor blockCountVisitor;
   public boolean escaping;

   public EscapingStatementFinder(AllBlocksCountVisitor blockCountVisitor) {
      this.blockCountVisitor = blockCountVisitor;
   }

   private boolean isEmpty(Statement statement) {
      if (!(statement instanceof SequentialStatement seq)) {
         return false;
      } else {
         for (int i = seq.getSequence().size() - 1; i >= 0; i--) {
            if (!this.isEmpty(seq.getSequence().get(i))) {
               return false;
            }
         }

         return true;
      }
   }

   public boolean check(List<Statement> statements) {
      if (this.escaping) {
         return true;
      }

      if (statements.isEmpty()) {
         this.escaping = true;
         return true;
      }

      for (int i = statements.size() - 1; i >= 0; i--) {
         Statement stmt = statements.get(i);
         if (!this.isEmpty(stmt)) {
            stmt.acceptVisitor(this);
            return this.escaping;
         }
      }

      this.escaping = true;
      return true;
   }

   @Override
   public void visit(AssignmentStatement statement) {
      this.escaping |= true;
   }

   @Override
   public void visit(SequentialStatement statement) {
      this.check(statement.getSequence());
   }

   @Override
   public void visit(ConditionalStatement statement) {
      if (!this.check(statement.getConsequent())) {
         this.check(statement.getAlternative());
      }
   }

   @Override
   public void visit(SwitchStatement statement) {
      if (this.blockCountVisitor.getCount(statement) > 0) {
         this.escaping = true;
      } else {
         for (SwitchClause clause : statement.getClauses()) {
            if (this.check(clause.getBody())) {
               break;
            }
         }

         if (!this.escaping) {
            this.check(statement.getDefaultClause());
         }
      }
   }

   @Override
   public void visit(WhileStatement statement) {
      if (this.blockCountVisitor.getCount(statement) > 0) {
         this.escaping = true;
      } else {
         if (statement.getCondition() != null && this.check(statement.getBody())) {
            this.escaping = true;
         }
      }
   }

   @Override
   public void visit(BlockStatement statement) {
      if (this.blockCountVisitor.getCount(statement) > 0) {
         this.escaping = true;
      } else {
         this.check(statement.getBody());
      }
   }

   @Override
   public void visit(BreakStatement statement) {
   }

   @Override
   public void visit(ContinueStatement statement) {
   }

   @Override
   public void visit(ReturnStatement statement) {
   }

   @Override
   public void visit(ThrowStatement statement) {
   }

   @Override
   public void visit(InitClassStatement statement) {
      this.escaping = true;
   }

   @Override
   public void visit(TryCatchStatement statement) {
      if (!this.check(statement.getProtectedBody())) {
         this.check(statement.getHandler());
      }
   }

   @Override
   public void visit(GotoPartStatement statement) {
   }

   @Override
   public void visit(MonitorEnterStatement statement) {
      this.escaping = true;
   }

   @Override
   public void visit(MonitorExitStatement statement) {
      this.escaping = true;
   }
}
