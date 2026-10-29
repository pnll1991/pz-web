package org.teavm.ast;

import java.util.List;

public class RecursiveVisitor implements ExprVisitor, StatementVisitor {
   protected boolean canceled;

   protected void beforeVisit(Expr expr) {
   }

   protected void afterVisit(Expr expr) {
   }

   protected final void cancel() {
      this.canceled = true;
   }

   @Override
   public void visit(BinaryExpr expr) {
      this.beforeVisit(expr);
      if (!this.canceled) {
         expr.getFirstOperand().acceptVisitor(this);
         if (!this.canceled) {
            expr.getSecondOperand().acceptVisitor(this);
         }
      }

      this.afterVisit(expr);
   }

   @Override
   public void visit(UnaryExpr expr) {
      this.beforeVisit(expr);
      if (!this.canceled) {
         expr.getOperand().acceptVisitor(this);
      }

      this.afterVisit(expr);
   }

   @Override
   public void visit(AssignmentStatement statement) {
      if (statement.getLeftValue() != null) {
         statement.getLeftValue().acceptVisitor(this);
         if (this.canceled) {
            return;
         }
      }

      statement.getRightValue().acceptVisitor(this);
   }

   @Override
   public void visit(ConditionalExpr expr) {
      this.beforeVisit(expr);
      if (!this.canceled) {
         expr.getCondition().acceptVisitor(this);
         if (!this.canceled) {
            expr.getConsequent().acceptVisitor(this);
            if (!this.canceled) {
               expr.getAlternative().acceptVisitor(this);
            }
         }
      }

      this.afterVisit(expr);
   }

   public void visit(List<Statement> statements) {
      for (Statement part : statements) {
         part.acceptVisitor(this);
         if (this.canceled) {
            break;
         }
      }
   }

   @Override
   public void visit(SequentialStatement statement) {
      this.visit(statement.getSequence());
   }

   @Override
   public void visit(ConstantExpr expr) {
      this.beforeVisit(expr);
      this.afterVisit(expr);
   }

   @Override
   public void visit(ConditionalStatement statement) {
      statement.getCondition().acceptVisitor(this);
      this.visit(statement.getConsequent());
      if (!this.canceled) {
         this.visit(statement.getAlternative());
      }
   }

   @Override
   public void visit(VariableExpr expr) {
      this.beforeVisit(expr);
      this.afterVisit(expr);
   }

   @Override
   public void visit(SubscriptExpr expr) {
      this.beforeVisit(expr);
      expr.getArray().acceptVisitor(this);
      if (!this.canceled) {
         expr.getIndex().acceptVisitor(this);
      }

      this.afterVisit(expr);
   }

   @Override
   public void visit(SwitchStatement statement) {
      statement.getValue().acceptVisitor(this);
      if (!this.canceled) {
         for (SwitchClause clause : statement.getClauses()) {
            this.visit(clause.getBody());
            if (this.canceled) {
               return;
            }
         }

         if (!this.canceled) {
            this.visit(statement.getDefaultClause());
         }
      }
   }

   @Override
   public void visit(UnwrapArrayExpr expr) {
      this.beforeVisit(expr);
      if (!this.canceled) {
         expr.getArray().acceptVisitor(this);
      }

      this.afterVisit(expr);
   }

   @Override
   public void visit(WhileStatement statement) {
      if (statement.getCondition() != null) {
         statement.getCondition().acceptVisitor(this);
         if (this.canceled) {
            return;
         }
      }

      this.visit(statement.getBody());
   }

   @Override
   public void visit(InvocationExpr expr) {
      this.beforeVisit(expr);
      if (!this.canceled) {
         for (Expr argument : expr.getArguments()) {
            argument.acceptVisitor(this);
            if (this.canceled) {
               break;
            }
         }
      }

      this.afterVisit(expr);
   }

   @Override
   public void visit(BlockStatement statement) {
      this.visit(statement.getBody());
   }

   @Override
   public void visit(QualificationExpr expr) {
      this.beforeVisit(expr);
      if (!this.canceled && expr.getQualified() != null) {
         expr.getQualified().acceptVisitor(this);
      }

      this.afterVisit(expr);
   }

   @Override
   public void visit(BreakStatement statement) {
   }

   @Override
   public void visit(NewExpr expr) {
      this.beforeVisit(expr);
      this.afterVisit(expr);
   }

   @Override
   public void visit(ContinueStatement statement) {
   }

   @Override
   public void visit(NewArrayExpr expr) {
      this.beforeVisit(expr);
      if (!this.canceled) {
         expr.getLength().acceptVisitor(this);
      }

      this.afterVisit(expr);
   }

   @Override
   public void visit(NewMultiArrayExpr expr) {
      this.beforeVisit(expr);
      if (!this.canceled) {
         for (Expr dimension : expr.getDimensions()) {
            dimension.acceptVisitor(this);
            if (this.canceled) {
               break;
            }
         }
      }

      this.afterVisit(expr);
   }

   @Override
   public void visit(ArrayFromDataExpr expr) {
      this.beforeVisit(expr);
      if (!this.canceled) {
         for (Expr element : expr.getData()) {
            element.acceptVisitor(this);
            if (this.canceled) {
               break;
            }
         }
      }

      this.afterVisit(expr);
   }

   @Override
   public void visit(ReturnStatement statement) {
      if (statement.getResult() != null) {
         statement.getResult().acceptVisitor(this);
      }
   }

   @Override
   public void visit(InstanceOfExpr expr) {
      this.beforeVisit(expr);
      if (!this.canceled) {
         expr.getExpr().acceptVisitor(this);
      }

      this.afterVisit(expr);
   }

   @Override
   public void visit(ThrowStatement statement) {
      statement.getException().acceptVisitor(this);
   }

   @Override
   public void visit(CastExpr expr) {
      this.beforeVisit(expr);
      if (!this.canceled) {
         expr.getValue().acceptVisitor(this);
      }

      this.afterVisit(expr);
   }

   @Override
   public void visit(InitClassStatement statement) {
   }

   @Override
   public void visit(PrimitiveCastExpr expr) {
      this.beforeVisit(expr);
      if (!this.canceled) {
         expr.getValue().acceptVisitor(this);
      }

      this.afterVisit(expr);
   }

   @Override
   public void visit(TryCatchStatement statement) {
      this.visit(statement.getProtectedBody());
      this.visit(statement.getHandler());
   }

   @Override
   public void visit(GotoPartStatement statement) {
   }

   @Override
   public void visit(MonitorEnterStatement statement) {
      statement.getObjectRef().acceptVisitor(this);
   }

   @Override
   public void visit(MonitorExitStatement statement) {
      statement.getObjectRef().acceptVisitor(this);
   }

   @Override
   public void visit(BoundCheckExpr expr) {
      this.beforeVisit(expr);
      if (!this.canceled) {
         expr.getIndex().acceptVisitor(this);
         if (!this.canceled && expr.getArray() != null) {
            expr.getArray().acceptVisitor(this);
         }
      }

      this.afterVisit(expr);
   }
}
