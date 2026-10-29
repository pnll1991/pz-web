package org.teavm.model.instructions;

import org.teavm.model.Instruction;
import org.teavm.model.Variable;

public class GetElementInstruction extends Instruction {
   private ArrayElementType type;
   private Variable array;
   private Variable index;
   private Variable receiver;

   public GetElementInstruction(ArrayElementType type) {
      this.type = type;
   }

   public ArrayElementType getType() {
      return this.type;
   }

   public Variable getArray() {
      return this.array;
   }

   public void setArray(Variable array) {
      this.array = array;
   }

   public Variable getIndex() {
      return this.index;
   }

   public void setIndex(Variable index) {
      this.index = index;
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
