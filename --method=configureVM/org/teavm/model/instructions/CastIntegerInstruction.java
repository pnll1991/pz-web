package org.teavm.model.instructions;

import org.teavm.model.Instruction;
import org.teavm.model.Variable;

public class CastIntegerInstruction extends Instruction {
   private Variable value;
   private Variable receiver;
   private IntegerSubtype targetType;
   private CastIntegerDirection direction;

   public CastIntegerInstruction(IntegerSubtype targetType, CastIntegerDirection direction) {
      this.targetType = targetType;
      this.direction = direction;
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

   public IntegerSubtype getTargetType() {
      return this.targetType;
   }

   public CastIntegerDirection getDirection() {
      return this.direction;
   }

   @Override
   public void acceptVisitor(InstructionVisitor visitor) {
      visitor.visit(this);
   }
}
