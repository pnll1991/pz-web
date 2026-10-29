package org.teavm.model.instructions;

import org.teavm.model.Instruction;
import org.teavm.model.Variable;

public class NegateInstruction extends Instruction {
   private NumericOperandType operandType;
   private Variable operand;
   private Variable receiver;

   public NegateInstruction(NumericOperandType operandType) {
      this.operandType = operandType;
   }

   public Variable getOperand() {
      return this.operand;
   }

   public void setOperand(Variable operand) {
      this.operand = operand;
   }

   public Variable getReceiver() {
      return this.receiver;
   }

   public void setReceiver(Variable receiver) {
      this.receiver = receiver;
   }

   public NumericOperandType getOperandType() {
      return this.operandType;
   }

   @Override
   public void acceptVisitor(InstructionVisitor visitor) {
      visitor.visit(this);
   }
}
