package org.teavm.ast.optimization;

import java.util.List;
import org.teavm.ast.BinaryExpr;
import org.teavm.ast.BinaryOperation;
import org.teavm.ast.ConditionalExpr;
import org.teavm.ast.ConditionalStatement;
import org.teavm.ast.InvocationExpr;
import org.teavm.ast.NewExpr;
import org.teavm.ast.RecursiveVisitor;
import org.teavm.ast.Statement;

public class ExpressionSideEffectDecomposer extends RecursiveVisitor {
   private List<Statement> target;

   public ExpressionSideEffectDecomposer(List<Statement> target) {
      this.target = target;
   }

   @Override
   public void visit(ConditionalExpr expr) {
      ConditionalStatement statement = new ConditionalStatement();
      statement.setCondition(expr.getCondition());
      expr.getConsequent().acceptVisitor(new ExpressionSideEffectDecomposer(statement.getConsequent()));
      expr.getAlternative().acceptVisitor(new ExpressionSideEffectDecomposer(statement.getAlternative()));
      this.target.add(statement);
   }

   @Override
   public void visit(InvocationExpr expr) {
      this.target.add(Statement.assign(null, expr));
   }

   @Override
   public void visit(NewExpr expr) {
      this.target.add(Statement.assign(null, expr));
   }

   @Override
   public void visit(BinaryExpr expr) {
      if (expr.getOperation() != BinaryOperation.AND && expr.getOperation() != BinaryOperation.OR) {
         super.visit(expr);
      } else {
         this.target.add(Statement.assign(null, expr));
      }
   }
}
