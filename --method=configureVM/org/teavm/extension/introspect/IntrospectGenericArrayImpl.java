package org.teavm.extension.introspect;

import org.teavm.model.GenericValueType;

public class IntrospectGenericArrayImpl implements IntrospectGenericArray {
   private final GenericValueType.Array type;
   private final IntrospectClassImpl<?> declaringClass;
   private final IntrospectMethodImpl declaringMethod;
   private final Introspection introspection;
   private IntrospectType componentType;

   IntrospectGenericArrayImpl(
      GenericValueType.Array type, IntrospectClassImpl<?> declaringClass, IntrospectMethodImpl declaringMethod, Introspection introspection
   ) {
      this.type = type;
      this.declaringClass = declaringClass;
      this.declaringMethod = declaringMethod;
      this.introspection = introspection;
   }

   @Override
   public String toString() {
      return this.componentType().toString() + "[]";
   }

   public IntrospectType componentType() {
      if (this.componentType == null) {
         this.componentType = this.introspection.convertGenericType(this.type.getItemType(), this.declaringClass, this.declaringMethod);
      }

      return this.componentType;
   }
}
