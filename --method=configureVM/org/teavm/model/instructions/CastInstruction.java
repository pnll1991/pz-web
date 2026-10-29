package org.teavm.model.instructions;

import org.teavm.model.Instruction;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;

public class CastInstruction extends Instruction {
   private Variable value;
   private Variable receiver;
   private ValueType targetType;
   private boolean weak;

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

   public ValueType getTargetType() {
      return this.targetType;
   }

   public void setTargetType(ValueType targetType) {
      this.targetType = targetType;
   }

   public boolean isWeak() {
      return this.weak;
   }

   public void setWeak(boolean weak) {
      this.weak = weak;
   }

   @Override
   public void acceptVisitor(InstructionVisitor visitor) {
      visitor.visit(this);
   }
}
