package org.teavm.backend.javascript.rendering;

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

class TryCatchFinder implements StatementVisitor {
   public boolean tryCatchFound;

   @Override
   public void visit(AssignmentStatement statement) {
   }

   private void visitSequence(List<Statement> statements) {
      if (!this.tryCatchFound) {
         for (Statement statement : statements) {
            statement.acceptVisitor(this);
            if (this.tryCatchFound) {
               return;
            }
         }
      }
   }

   @Override
   public void visit(SequentialStatement statement) {
      if (!this.tryCatchFound) {
         this.visitSequence(statement.getSequence());
      }
   }

   @Override
   public void visit(ConditionalStatement statement) {
      if (!this.tryCatchFound) {
         this.visitSequence(statement.getConsequent());
         this.visitSequence(statement.getAlternative());
      }
   }

   @Override
   public void visit(SwitchStatement statement) {
      if (!this.tryCatchFound) {
         for (SwitchClause clause : statement.getClauses()) {
            this.visitSequence(clause.getBody());
            if (this.tryCatchFound) {
               return;
            }
         }

         this.visitSequence(statement.getDefaultClause());
      }
   }

   @Override
   public void visit(WhileStatement statement) {
      if (!this.tryCatchFound) {
         this.visitSequence(statement.getBody());
      }
   }

   @Override
   public void visit(BlockStatement statement) {
      if (!this.tryCatchFound) {
         this.visitSequence(statement.getBody());
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
   }

   @Override
   public void visit(TryCatchStatement statement) {
      this.tryCatchFound = true;
   }

   @Override
   public void visit(GotoPartStatement statement) {
   }

   @Override
   public void visit(MonitorEnterStatement statement) {
   }

   @Override
   public void visit(MonitorExitStatement statement) {
   }
}
