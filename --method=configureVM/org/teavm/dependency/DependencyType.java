package org.teavm.dependency;

import org.teavm.model.ValueType;

public class DependencyType {
   private ValueType valueType;
   int index;

   DependencyType(ValueType valueType, int index) {
      this.valueType = valueType;
      this.index = index;
   }

   public ValueType getValueType() {
      return this.valueType;
   }
}
