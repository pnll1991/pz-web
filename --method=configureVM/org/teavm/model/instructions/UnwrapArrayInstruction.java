package org.teavm.model.instructions;

import org.teavm.model.Instruction;
import org.teavm.model.Variable;

public class UnwrapArrayInstruction extends Instruction {
   private Variable array;
   private Variable receiver;
   private ArrayElementType elementType;

   public UnwrapArrayInstruction(ArrayElementType elementType) {
      this.elementType = elementType;
   }

   public Variable getArray() {
      return this.array;
   }

   public void setArray(Variable array) {
      this.array = array;
   }

   public Variable getReceiver() {
      return this.receiver;
   }

   public void setReceiver(Variable receiver) {
      this.receiver = receiver;
   }

   public ArrayElementType getElementType() {
      return this.elementType;
   }

   @Override
   public void acceptVisitor(InstructionVisitor visitor) {
      visitor.visit(this);
   }
}
