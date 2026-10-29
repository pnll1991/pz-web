package org.teavm.ast;

import java.util.ArrayList;
import java.util.List;

public class SequentialStatement extends Statement {
   private List<Statement> sequence = new ArrayList<>();

   public List<Statement> getSequence() {
      return this.sequence;
   }

   @Override
   public void acceptVisitor(StatementVisitor visitor) {
      visitor.visit(this);
   }
}
