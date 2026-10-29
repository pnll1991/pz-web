package org.teavm.extension.introspect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.teavm.model.GenericValueType;
import org.teavm.model.ValueType;

public class IntrospectParameterizedTypeImpl implements IntrospectParameterizedType {
   private final GenericValueType.Object type;
   private final IntrospectClassImpl<?> declaringClass;
   private final IntrospectMethodImpl declaringMethod;
   private final Introspection introspection;
   private IntrospectClass<?> rawType;
   private IntrospectType ownerType;
   private boolean ownerTypeResolved;
   private List<IntrospectTypeArgument> typeArguments;

   IntrospectParameterizedTypeImpl(
      GenericValueType.Object type, IntrospectClassImpl<?> declaringClass, IntrospectMethodImpl declaringMethod, Introspection introspection
   ) {
      this.type = type;
      this.declaringClass = declaringClass;
      this.declaringMethod = declaringMethod;
      this.introspection = introspection;
   }

   public IntrospectClass<?> rawType() {
      if (this.rawType == null) {
         this.rawType = this.introspection.getClass(ValueType.object(this.type.getFullClassName()));
      }

      return this.rawType;
   }

   public IntrospectType ownerType() {
      if (!this.ownerTypeResolved) {
         this.ownerTypeResolved = true;
         GenericValueType.Object parent = this.type.getParent();
         if (parent != null) {
            this.ownerType = this.introspection.convertGenericType(parent, this.declaringClass, this.declaringMethod);
         }
      }

      return this.ownerType;
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      IntrospectType owner = this.ownerType();
      if (owner != null) {
         sb.append(owner).append('.');
         String rawName = this.rawType().name();
         int dollar = rawName.lastIndexOf(36);
         sb.append(dollar >= 0 ? rawName.substring(dollar + 1) : rawName);
      } else {
         sb.append(this.rawType().name());
      }

      List<? extends IntrospectTypeArgument> args = this.typeArguments();
      if (!args.isEmpty()) {
         sb.append('<');

         for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
               sb.append(", ");
            }

            sb.append(args.get(i));
         }

         sb.append('>');
      }

      return sb.toString();
   }

   public List<? extends IntrospectTypeArgument> typeArguments() {
      if (this.typeArguments == null) {
         GenericValueType.Argument[] args = this.type.getArguments();
         ArrayList<IntrospectTypeArgument> result = new ArrayList<>(args.length);

         for (GenericValueType.Argument arg : args) {
            result.add(new IntrospectTypeArgumentImpl(arg, this.declaringClass, this.declaringMethod, this.introspection));
         }

         this.typeArguments = Collections.unmodifiableList(result);
      }

      return this.typeArguments;
   }
}
