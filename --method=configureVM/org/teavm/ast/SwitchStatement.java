package org.teavm.ast;

import java.util.ArrayList;
import java.util.List;

public class SwitchStatement extends IdentifiedStatement {
   private Expr value;
   private List<SwitchClause> clauses = new ArrayList<>();
   private List<Statement> defaultClause = new ArrayList<>();

   public List<SwitchClause> getClauses() {
      return this.clauses;
   }

   public List<Statement> getDefaultClause() {
      return this.defaultClause;
   }

   public Expr getValue() {
      return this.value;
   }

   public void setValue(Expr value) {
      this.value = value;
   }

   @Override
   public void acceptVisitor(StatementVisitor visitor) {
      visitor.visit(this);
   }
}
