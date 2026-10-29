package org.teavm.model.instructions;

import org.teavm.model.Instruction;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;

public class IsInstanceInstruction extends Instruction {
   private Variable value;
   private ValueType type;
   private Variable receiver;

   public Variable getValue() {
      return this.value;
   }

   public void setValue(Variable value) {
      this.value = value;
   }

   public ValueType getType() {
      return this.type;
   }

   public void setType(ValueType type) {
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
