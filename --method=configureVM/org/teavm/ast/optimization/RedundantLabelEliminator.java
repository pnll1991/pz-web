package org.teavm.ast.optimization;

import java.util.HashSet;
import java.util.Set;
import org.teavm.ast.BlockStatement;
import org.teavm.ast.BreakStatement;
import org.teavm.ast.ContinueStatement;
import org.teavm.ast.IdentifiedStatement;
import org.teavm.ast.RecursiveVisitor;
import org.teavm.ast.SwitchClause;
import org.teavm.ast.SwitchStatement;
import org.teavm.ast.WhileStatement;

class RedundantLabelEliminator extends RecursiveVisitor {
   private IdentifiedStatement currentBlock;
   private Set<IdentifiedStatement> hasRefs = new HashSet<>();

   @Override
   public void visit(SwitchStatement statement) {
      IdentifiedStatement currentBlockBackup = this.currentBlock;
      this.currentBlock = statement;

      for (SwitchClause clause : statement.getClauses()) {
         this.visit(clause.getBody());
      }

      this.visit(statement.getDefaultClause());
      if (!this.hasRefs.contains(this.currentBlock)) {
         this.currentBlock.setId(null);
      }

      this.currentBlock = currentBlockBackup;
   }

   @Override
   public void visit(WhileStatement statement) {
      IdentifiedStatement currentBlockBackup = this.currentBlock;
      this.currentBlock = statement;
      this.visit(statement.getBody());
      if (!this.hasRefs.contains(this.currentBlock)) {
         this.currentBlock.setId(null);
      }

      this.currentBlock = currentBlockBackup;
   }

   @Override
   public void visit(BlockStatement statement) {
      IdentifiedStatement currentBlockBackup = this.currentBlock;
      this.currentBlock = null;
      this.visit(statement.getBody());
      this.currentBlock = currentBlockBackup;
   }

   @Override
   public void visit(BreakStatement statement) {
      if (statement.getTarget() == this.currentBlock) {
         statement.setTarget(null);
      } else {
         this.hasRefs.add(statement.getTarget());
      }
   }

   @Override
   public void visit(ContinueStatement statement) {
      if (statement.getTarget() == this.currentBlock) {
         statement.setTarget(null);
      } else {
         this.hasRefs.add(statement.getTarget());
      }
   }
}
