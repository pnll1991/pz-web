package org.teavm.model.instructions;

import org.teavm.model.Instruction;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;

public class ConstructArrayInstruction extends Instruction {
   private Variable size;
   private ValueType itemType;
   private Variable receiver;

   public Variable getSize() {
      return this.size;
   }

   public void setSize(Variable size) {
      this.size = size;
   }

   public ValueType getItemType() {
      return this.itemType;
   }

   public void setItemType(ValueType itemType) {
      this.itemType = itemType;
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
