package org.teavm.ast.optimization;

import org.teavm.ast.BinaryExpr;
import org.teavm.ast.BinaryOperation;
import org.teavm.ast.Expr;
import org.teavm.ast.OperationType;
import org.teavm.ast.UnaryExpr;
import org.teavm.ast.UnaryOperation;

final class ExprOptimizer {
   private ExprOptimizer() {
   }

   public static Expr invert(Expr expr) {
      if (expr instanceof UnaryExpr unary) {
         if (unary.getOperation() == UnaryOperation.NOT) {
            return unary.getOperand();
         }
      } else if (expr instanceof BinaryExpr binary) {
         Expr a = binary.getFirstOperand();
         Expr b = binary.getSecondOperand();
         switch (binary.getOperation()) {
            case EQUALS:
               return Expr.binary(BinaryOperation.NOT_EQUALS, binary.getType(), a, b, expr.getLocation());
            case NOT_EQUALS:
               return Expr.binary(BinaryOperation.EQUALS, binary.getType(), a, b, expr.getLocation());
            case LESS:
               if (binary.getType() == OperationType.INT || binary.getType() == OperationType.LONG) {
                  return Expr.binary(BinaryOperation.GREATER_OR_EQUALS, binary.getType(), a, b, expr.getLocation());
               }
               break;
            case LESS_OR_EQUALS:
               if (binary.getType() == OperationType.INT || binary.getType() == OperationType.LONG) {
                  return Expr.binary(BinaryOperation.GREATER, binary.getType(), a, b, expr.getLocation());
               }
               break;
            case GREATER:
               if (binary.getType() == OperationType.INT || binary.getType() == OperationType.LONG) {
                  return Expr.binary(BinaryOperation.LESS_OR_EQUALS, binary.getType(), a, b, expr.getLocation());
               }
               break;
            case GREATER_OR_EQUALS:
               if (binary.getType() == OperationType.INT || binary.getType() == OperationType.LONG) {
                  return Expr.binary(BinaryOperation.LESS, binary.getType(), a, b, expr.getLocation());
               }
         }
      }

      return Expr.invert(expr);
   }
}
