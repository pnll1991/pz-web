package org.teavm.model.instructions;

import org.teavm.model.Instruction;
import org.teavm.model.Variable;

public class LongConstantInstruction extends Instruction {
   private long constant;
   private Variable receiver;

   public long getConstant() {
      return this.constant;
   }

   public void setConstant(long constant) {
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
