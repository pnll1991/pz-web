package org.teavm.model.instructions;

import org.teavm.model.Instruction;
import org.teavm.model.Variable;

public class BinaryInstruction extends Instruction {
   private BinaryOperation operation;
   private Variable receiver;
   private Variable firstOperand;
   private Variable secondOperand;
   private NumericOperandType operandType;

   public BinaryInstruction(BinaryOperation operation, NumericOperandType operandType) {
      this.operation = operation;
      this.operandType = operandType;
   }

   public Variable getReceiver() {
      return this.receiver;
   }

   public void setReceiver(Variable receiver) {
      this.receiver = receiver;
   }

   public Variable getFirstOperand() {
      return this.firstOperand;
   }

   public void setFirstOperand(Variable firstOperand) {
      this.firstOperand = firstOperand;
   }

   public Variable getSecondOperand() {
      return this.secondOperand;
   }

   public void setSecondOperand(Variable secondOperand) {
      this.secondOperand = secondOperand;
   }

   public NumericOperandType getOperandType() {
      return this.operandType;
   }

   public BinaryOperation getOperation() {
      return this.operation;
   }

   @Override
   public void acceptVisitor(InstructionVisitor visitor) {
      visitor.visit(this);
   }
}
