package org.teavm.model.instructions;

import java.util.ArrayList;
import java.util.List;
import org.teavm.model.Instruction;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;

public class ConstructMultiArrayInstruction extends Instruction {
   private List<Variable> dimensions = new ArrayList<>();
   private ValueType itemType;
   private Variable receiver;

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

   public List<Variable> getDimensions() {
      return this.dimensions;
   }

   @Override
   public void acceptVisitor(InstructionVisitor visitor) {
      visitor.visit(this);
   }
}
