package org.teavm.model.instructions;

import org.teavm.model.Instruction;
import org.teavm.model.Variable;

public class ConstructInstruction extends Instruction {
   private String type;
   private Variable receiver;

   public String getType() {
      return this.type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public Variable getReceiver() {
      return this.receiver;
   }

   public void setReceiver(Variable receiver) {
      this.receiver = receiver;
   }

   @Override
   public void acceptVisitor(InstructionVisitor visitor) {
      visitor.visit(this);
   }
}
