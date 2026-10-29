package org.teavm.model.instructions;

import org.teavm.model.Instruction;
import org.teavm.model.Variable;

public class CastNumberInstruction extends Instruction {
   private Variable value;
   private Variable receiver;
   private NumericOperandType sourceType;
   private NumericOperandType targetType;

   public CastNumberInstruction(NumericOperandType sourceType, NumericOperandType targetType) {
      this.sourceType = sourceType;
      this.targetType = targetType;
   }

   public Variable getValue() {
      return this.value;
   }

   public void setValue(Variable value) {
      this.value = value;
   }

   public Variable getReceiver() {
      return this.receiver;
   }

   public void setReceiver(Variable receiver) {
      this.receiver = receiver;
   }

   public NumericOperandType getSourceType() {
      return this.sourceType;
   }

   public NumericOperandType getTargetType() {
      return this.targetType;
   }

   @Override
   public void acceptVisitor(InstructionVisitor visitor) {
      visitor.visit(this);
   }
}
