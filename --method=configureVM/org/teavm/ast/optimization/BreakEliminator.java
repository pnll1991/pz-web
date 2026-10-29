package org.teavm.ast.optimization;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.ast.AbstractStatementVisitor;
import org.teavm.ast.BlockStatement;
import org.teavm.ast.BreakStatement;
import org.teavm.ast.ConditionalStatement;
import org.teavm.ast.ContinueStatement;
import org.teavm.ast.IdentifiedStatement;
import org.teavm.ast.ReturnStatement;
import org.teavm.ast.SequentialStatement;
import org.teavm.ast.Statement;
import org.teavm.ast.SwitchClause;
import org.teavm.ast.SwitchStatement;
import org.teavm.ast.ThrowStatement;
import org.teavm.ast.TryCatchStatement;
import org.teavm.ast.WhileStatement;

class BreakEliminator extends AbstractStatementVisitor {
   private Map<BlockStatement, List<Statement>> blockSuccessors = new LinkedHashMap<>();
   private Set<IdentifiedStatement> outerStatements = new LinkedHashSet<>();
   private List<Statement> currentSequence;
   private int currentIndex;
   private AllBlocksCountVisitor usageCounter;

   public void eliminate(Statement statement) {
      this.usageCounter = new AllBlocksCountVisitor();
      statement.acceptVisitor(this.usageCounter);
      statement.acceptVisitor(this);
   }

   private void processSequence(List<Statement> statements) {
      List<Statement> oldSequence = this.currentSequence;
      int oldIndex = this.currentIndex;
      this.currentSequence = statements;

      for (this.currentIndex = 0; this.currentIndex < this.currentSequence.size(); this.currentIndex++) {
         statements.get(this.currentIndex).acceptVisitor(this);
      }

      this.currentIndex = oldIndex;
      this.currentSequence = oldSequence;
   }

   @Override
   public void visit(SequentialStatement statement) {
      if (this.currentSequence == null) {
         this.processSequence(statement.getSequence());
      } else {
         this.currentIndex--;
         this.currentSequence.remove(this.currentIndex);
         this.currentSequence.addAll(this.currentIndex, statement.getSequence());
      }
   }

   @Override
   public void visit(ConditionalStatement statement) {
      this.processSequence(statement.getConsequent());
      this.processSequence(statement.getAlternative());
   }

   @Override
   public void visit(SwitchStatement statement) {
      this.outerStatements.add(statement);

      for (SwitchClause clause : statement.getClauses()) {
         this.processSequence(clause.getBody());
      }

      this.processSequence(statement.getDefaultClause());
      this.outerStatements.remove(statement);
   }

   @Override
   public void visit(WhileStatement statement) {
      this.outerStatements.add(statement);
      this.processSequence(statement.getBody());
      this.outerStatements.remove(statement);
   }

   @Override
   public void visit(BlockStatement statement) {
      this.outerStatements.add(statement);
      if (!this.escapes(this.currentSequence.subList(this.currentIndex + 1, this.currentSequence.size()))) {
         this.blockSuccessors.put(statement, this.currentSequence.subList(this.currentIndex + 1, this.currentSequence.size()));
      }

      this.processSequence(statement.getBody());
      this.blockSuccessors.remove(statement);
      this.outerStatements.remove(statement);
   }

   @Override
   public void visit(BreakStatement statement) {
      if (this.blockSuccessors.containsKey(statement.getTarget()) && this.usageCounter.getCount(statement.getTarget()) == 1) {
         this.currentSequence.subList(this.currentIndex, this.currentSequence.size()).clear();
         List<Statement> successors = this.blockSuccessors.remove(statement.getTarget());
         this.currentSequence.addAll(successors);
         successors.clear();
         this.currentIndex--;
      } else {
         this.currentSequence.subList(this.currentIndex + 1, this.currentSequence.size()).clear();
      }
   }

   @Override
   public void visit(ContinueStatement statement) {
      this.currentSequence.subList(this.currentIndex + 1, this.currentSequence.size()).clear();
   }

   @Override
   public void visit(ReturnStatement statement) {
      this.currentSequence.subList(this.currentIndex + 1, this.currentSequence.size()).clear();
   }

   @Override
   public void visit(ThrowStatement statement) {
      this.currentSequence.subList(this.currentIndex + 1, this.currentSequence.size()).clear();
   }

   @Override
   public void visit(TryCatchStatement statement) {
      Map<BlockStatement, List<Statement>> oldBlockSuccessors = this.blockSuccessors;
      Set<IdentifiedStatement> oldOuterStatements = this.outerStatements;
      this.outerStatements = new LinkedHashSet<>();
      this.blockSuccessors = new LinkedHashMap<>();
      this.processSequence(statement.getProtectedBody());
      this.outerStatements = oldOuterStatements;
      this.blockSuccessors = oldBlockSuccessors;
      this.processSequence(statement.getHandler());
   }

   private boolean escapes(List<Statement> statements) {
      return new EscapingStatementFinder(this.usageCounter).check(statements);
   }
}
