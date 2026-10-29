package org.teavm.model.instructions;

import org.teavm.model.Instruction;
import org.teavm.model.Variable;

public class StringConstantInstruction extends Instruction {
   private String constant;
   private Variable receiver;

   public String getConstant() {
      return this.constant;
   }

   public void setConstant(String constant) {
      this.constant = constant;
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
