package org.teavm.model.instructions;

import org.teavm.model.Instruction;
import org.teavm.model.Variable;

public class BoundCheckInstruction extends Instruction {
   private Variable receiver;
   private Variable index;
   private Variable array;
   private boolean lower;

   public Variable getReceiver() {
      return this.receiver;
   }

   public void setReceiver(Variable receiver) {
      this.receiver = receiver;
   }

   public Variable getIndex() {
      return this.index;
   }

   public void setIndex(Variable index) {
      this.index = index;
   }

   public Variable getArray() {
      return this.array;
   }

   public void setArray(Variable array) {
      this.array = array;
   }

   public boolean isLower() {
      return this.lower;
   }

   public void setLower(boolean lower) {
      this.lower = lower;
   }

   @Override
   public void acceptVisitor(InstructionVisitor visitor) {
      visitor.visit(this);
   }
}
