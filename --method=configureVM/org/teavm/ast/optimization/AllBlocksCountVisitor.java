package org.teavm.ast.optimization;

import java.util.List;
import org.teavm.ast.AssignmentStatement;
import org.teavm.ast.BlockStatement;
import org.teavm.ast.BreakStatement;
import org.teavm.ast.ConditionalStatement;
import org.teavm.ast.ContinueStatement;
import org.teavm.ast.GotoPartStatement;
import org.teavm.ast.IdentifiedStatement;
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
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;

class AllBlocksCountVisitor implements StatementVisitor {
   private ObjectIntMap<IdentifiedStatement> blocksCount = new ObjectIntHashMap();
   private boolean exits;
   private IdentifiedStatement currentBlock;

   public void visit(List<Statement> statements) {
      if (statements != null) {
         for (Statement statement : statements) {
            statement.acceptVisitor(this);
         }
      }
   }

   public int getCount(IdentifiedStatement statement) {
      return this.blocksCount.get(statement);
   }

   @Override
   public void visit(AssignmentStatement statement) {
      this.exits = true;
   }

   @Override
   public void visit(SequentialStatement statement) {
      this.visit(statement.getSequence());
   }

   @Override
   public void visit(ConditionalStatement statement) {
      this.visit(statement.getConsequent());
      boolean consequentExits = this.exits;
      this.visit(statement.getAlternative());
      this.exits |= consequentExits;
   }

   @Override
   public void visit(SwitchStatement statement) {
      IdentifiedStatement outerBlock = this.currentBlock;
      this.currentBlock = statement;
      boolean hasExitClause = false;

      for (SwitchClause clause : statement.getClauses()) {
         this.visit(clause.getBody());
         hasExitClause |= this.exits;
      }

      this.visit(statement.getDefaultClause());
      this.exits |= hasExitClause;
      this.incrementCurrentBlockIfExits();
      this.currentBlock = outerBlock;
   }

   @Override
   public void visit(WhileStatement statement) {
      IdentifiedStatement outerBlock = this.currentBlock;
      this.currentBlock = statement;
      this.visit(statement.getBody());
      this.exits = this.getCount(statement) > 0;
      this.currentBlock = outerBlock;
   }

   @Override
   public void visit(BlockStatement statement) {
      IdentifiedStatement outerBlock = this.currentBlock;
      this.currentBlock = statement;
      this.visit(statement.getBody());
      this.incrementCurrentBlockIfExits();
      this.currentBlock = outerBlock;
   }

   @Override
   public void visit(BreakStatement statement) {
      IdentifiedStatement target = statement.getTarget();
      this.incrementBlock(target != null ? target : this.currentBlock);
   }

   @Override
   public void visit(ContinueStatement statement) {
      IdentifiedStatement target = statement.getTarget();
      this.incrementBlock(target != null ? target : this.currentBlock);
   }

   private void incrementBlock(IdentifiedStatement statement) {
      this.blocksCount.put(statement, this.getCount(statement) + 1);
   }

   private void incrementCurrentBlockIfExits() {
      if (this.exits) {
         this.incrementBlock(this.currentBlock);
      } else {
         this.exits = this.exits | this.getCount(this.currentBlock) > 0;
      }
   }

   @Override
   public void visit(ReturnStatement statement) {
      this.exits = false;
   }

   @Override
   public void visit(ThrowStatement statement) {
      this.exits = false;
   }

   @Override
   public void visit(InitClassStatement statement) {
      this.exits = true;
   }

   @Override
   public void visit(TryCatchStatement statement) {
      this.visit(statement.getProtectedBody());
      boolean tryExits = this.exits;
      this.visit(statement.getHandler());
      this.exits |= tryExits;
   }

   @Override
   public void visit(GotoPartStatement statement) {
      this.exits = false;
   }

   @Override
   public void visit(MonitorEnterStatement statement) {
      this.exits = true;
   }

   @Override
   public void visit(MonitorExitStatement statement) {
      this.exits = true;
   }
}
