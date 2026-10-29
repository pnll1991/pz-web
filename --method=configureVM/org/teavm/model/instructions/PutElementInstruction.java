package org.teavm.model.instructions;

import org.teavm.model.Instruction;
import org.teavm.model.Variable;

public class PutElementInstruction extends Instruction {
   private ArrayElementType type;
   private Variable array;
   private Variable index;
   private Variable value;

   public PutElementInstruction(ArrayElementType type) {
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

   public Variable getValue() {
      return this.value;
   }

   public void setValue(Variable value) {
      this.value = value;
   }

   @Override
   public void acceptVisitor(InstructionVisitor visitor) {
      visitor.visit(this);
   }
}
