package org.teavm.extension.introspect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.teavm.model.GenericTypeParameter;
import org.teavm.model.GenericValueType;

public class IntrospectTypeVariableImpl implements IntrospectTypeVariable {
   private final String name;
   private final GenericTypeParameter parameter;
   private final IntrospectClassImpl<?> declaringClass;
   private final IntrospectMethodImpl declaringMethod;
   private final Introspection introspection;
   private List<IntrospectType> superTypes;

   IntrospectTypeVariableImpl(
      GenericTypeParameter parameter, IntrospectClassImpl<?> declaringClass, IntrospectMethodImpl declaringMethod, Introspection introspection
   ) {
      this.name = parameter.getName();
      this.parameter = parameter;
      this.declaringClass = declaringClass;
      this.declaringMethod = declaringMethod;
      this.introspection = introspection;
   }

   IntrospectTypeVariableImpl(String name) {
      this.name = name;
      this.parameter = null;
      this.declaringClass = null;
      this.declaringMethod = null;
      this.introspection = null;
   }

   public String name() {
      return this.name;
   }

   public List<? extends IntrospectType> superTypes() {
      if (this.superTypes == null) {
         if (this.parameter == null) {
            this.superTypes = Collections.emptyList();
         } else {
            List<GenericValueType.Reference> bounds = this.parameter.extractAllBounds();
            ArrayList<IntrospectType> result = new ArrayList<>(bounds.size());

            for (GenericValueType.Reference bound : bounds) {
               result.add(this.introspection.convertGenericType(bound, this.declaringClass, this.declaringMethod));
            }

            this.superTypes = Collections.unmodifiableList(result);
         }
      }

      return this.superTypes;
   }

   public IntrospectGenericDeclaration genericDeclaration() {
      return this.declaringMethod != null ? this.declaringMethod : this.declaringClass;
   }

   @Override
   public String toString() {
      return this.name;
   }

   String toTypeParameterString() {
      List<? extends IntrospectType> supers = this.superTypes();
      if (supers.isEmpty()) {
         return this.name;
      }

      StringBuilder sb = new StringBuilder(this.name);
      sb.append(" extends ");

      for (int i = 0; i < supers.size(); i++) {
         if (i > 0) {
            sb.append(" & ");
         }

         sb.append(supers.get(i));
      }

      return sb.toString();
   }
}
