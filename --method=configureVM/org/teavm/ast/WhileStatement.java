package org.teavm.ast;

import java.util.ArrayList;
import java.util.List;

public class WhileStatement extends IdentifiedStatement {
   private Expr condition;
   private List<Statement> body = new ArrayList<>();

   public Expr getCondition() {
      return this.condition;
   }

   public void setCondition(Expr condition) {
      this.condition = condition;
   }

   public List<Statement> getBody() {
      return this.body;
   }

   @Override
   public void acceptVisitor(StatementVisitor visitor) {
      visitor.visit(this);
   }
}
