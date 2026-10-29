package org.teavm.model.instructions;

import org.teavm.model.Instruction;
import org.teavm.model.Variable;

public class RaiseInstruction extends Instruction {
   private Variable exception;

   public Variable getException() {
      return this.exception;
   }

   public void setException(Variable exception) {
      this.exception = exception;
   }

   @Override
   public void acceptVisitor(InstructionVisitor visitor) {
      visitor.visit(this);
   }
}
