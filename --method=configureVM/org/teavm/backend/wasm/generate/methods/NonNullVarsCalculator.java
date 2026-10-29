package org.teavm.backend.wasm.generate.methods;

import java.util.Arrays;
import org.teavm.ast.AssignmentStatement;
import org.teavm.ast.BinaryExpr;
import org.teavm.ast.BlockStatement;
import org.teavm.ast.ConditionalExpr;
import org.teavm.ast.ConditionalStatement;
import org.teavm.ast.RecursiveVisitor;
import org.teavm.ast.SwitchClause;
import org.teavm.ast.SwitchStatement;
import org.teavm.ast.TryCatchStatement;
import org.teavm.ast.VariableExpr;
import org.teavm.ast.WhileStatement;

class NonNullVarsCalculator extends RecursiveVisitor {
   private boolean[] nonNullVars;
   private boolean[] definedVars;

   public NonNullVarsCalculator(boolean[] nonNullVars) {
      this.nonNullVars = nonNullVars;
      this.definedVars = new boolean[nonNullVars.length];
   }

   @Override
   public void visit(VariableExpr expr) {
      this.use(expr.getIndex());
   }

   @Override
   public void visit(AssignmentStatement statement) {
      statement.getRightValue().acceptVisitor(this);
      if (statement.getLeftValue() instanceof VariableExpr) {
         int index = ((VariableExpr)statement.getLeftValue()).getIndex();
         this.define(index);
      } else if (statement.getLeftValue() != null) {
         statement.getLeftValue().acceptVisitor(this);
      }
   }

   @Override
   public void visit(BlockStatement statement) {
      this.runInBlock(() -> super.visit(statement));
   }

   @Override
   public void visit(WhileStatement statement) {
      this.runInBlock(() -> super.visit(statement));
   }

   @Override
   public void visit(SwitchStatement statement) {
      statement.getValue().acceptVisitor(this);

      for (SwitchClause clause : statement.getClauses()) {
         this.runInBlock(() -> this.visit(clause.getBody()));
      }

      this.runInBlock(() -> this.visit(statement.getDefaultClause()));
   }

   @Override
   public void visit(TryCatchStatement statement) {
      this.runInBlock(() -> this.visit(statement.getProtectedBody()));
      this.runInBlock(() -> this.visit(statement.getHandler()));
   }

   @Override
   public void visit(ConditionalStatement statement) {
      statement.getCondition().acceptVisitor(this);
      this.runInBlock(() -> this.visit(statement.getConsequent()));
      this.runInBlock(() -> this.visit(statement.getAlternative()));
   }

   @Override
   public void visit(ConditionalExpr expr) {
      expr.getCondition().acceptVisitor(this);
      this.runInBlock(() -> expr.getConsequent().acceptVisitor(this));
      this.runInBlock(() -> expr.getAlternative().acceptVisitor(this));
   }

   @Override
   public void visit(BinaryExpr expr) {
      switch (expr.getOperation()) {
         case AND:
         case OR:
            expr.getFirstOperand().acceptVisitor(this);
            this.runInBlock(() -> expr.getSecondOperand().acceptVisitor(this));
            break;
         default:
            super.visit(expr);
      }
   }

   private void runInBlock(Runnable action) {
      boolean[] oldDefinedVars = this.definedVars;
      this.definedVars = Arrays.copyOf(this.definedVars, this.definedVars.length);
      action.run();
      this.definedVars = oldDefinedVars;
   }

   private void use(int index) {
      if (!this.definedVars[index]) {
         this.nonNullVars[index] = false;
      }
   }

   private void define(int index) {
      this.definedVars[index] = true;
   }
}
