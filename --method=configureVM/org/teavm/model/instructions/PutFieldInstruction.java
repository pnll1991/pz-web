package org.teavm.model.instructions;

import org.teavm.model.FieldReference;
import org.teavm.model.Instruction;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;

public class PutFieldInstruction extends Instruction {
   private Variable instance;
   private FieldReference field;
   private Variable value;
   private ValueType fieldType;

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

   public Variable getValue() {
      return this.value;
   }

   public void setValue(Variable value) {
      this.value = value;
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
