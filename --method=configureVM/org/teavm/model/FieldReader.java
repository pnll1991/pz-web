package org.teavm.model;

public interface FieldReader extends MemberReader {
   ValueType getType();

   GenericValueType getGenericType();

   Object getInitialValue();

   FieldReference getReference();
}
