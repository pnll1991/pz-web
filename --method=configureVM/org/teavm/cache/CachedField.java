package org.teavm.cache;

import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;
import org.teavm.model.GenericValueType;
import org.teavm.model.ValueType;

class CachedField extends CachedMember implements FieldReader {
   ValueType type;
   GenericValueType genericType;
   Object initialValue;
   FieldReference reference;

   @Override
   public ValueType getType() {
      return this.type;
   }

   @Override
   public GenericValueType getGenericType() {
      return this.genericType;
   }

   @Override
   public Object getInitialValue() {
      return this.initialValue;
   }

   @Override
   public FieldReference getReference() {
      return this.reference;
   }
}
