package org.teavm.model.instructions;

import org.teavm.model.FieldReference;
import org.teavm.model.Instruction;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;

public class GetFieldInstruction extends Instruction {
   private Variable instance;
   private FieldReference field;
   private ValueType fieldType;
   private Variable receiver;

   public Variable getInstance() {
      return this.instance;
   }

   public void setInstance(Variable instance) {
      this.instance = instance;
   }

   public FieldReference getField() {
      return this.field;
   }

   public void setField(FieldReference field) {
      this.field = field;
   }

   public Variable getReceiver() {
      return this.receiver;
   }

   public void setReceiver(Variable receiver) {
      this.receiver = receiver;
   }

   public ValueType getFieldType() {
      return this.fieldType;
   }

   public void setFieldType(ValueType fieldType) {
      this.fieldType = fieldType;
   }

   @Override
   public void acceptVisitor(InstructionVisitor visitor) {
      visitor.visit(this);
   }
}
