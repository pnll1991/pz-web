package org.teavm.extension.introspect;

import org.teavm.model.GenericValueType;
import org.teavm.model.ValueType;

public class IntrospectTypeArgumentImpl implements IntrospectTypeArgument {
   private final GenericValueType.Argument argument;
   private final IntrospectClassImpl<?> declaringClass;
   private final IntrospectMethodImpl declaringMethod;
   private final Introspection introspection;
   private IntrospectType type;

   IntrospectTypeArgumentImpl(
      GenericValueType.Argument argument, IntrospectClassImpl<?> declaringClass, IntrospectMethodImpl declaringMethod, Introspection introspection
   ) {
      this.argument = argument;
      this.declaringClass = declaringClass;
      this.declaringMethod = declaringMethod;
      this.introspection = introspection;
   }

   public IntrospectProjection projection() {
      switch (this.argument.getKind()) {
         case INVARIANT:
            return IntrospectProjection.EXACT;
         case COVARIANT:
         case ANY:
            return IntrospectProjection.EXTENDS;
         case CONTRAVARIANT:
            return IntrospectProjection.SUPER;
         default:
            throw new AssertionError("Unknown kind: " + this.argument.getKind());
      }
   }

   @Override
   public String toString() {
      if (this.argument.getValue() == null) {
         return "?";
      }

      switch (this.projection()) {
         case EXACT:
            return this.type().toString();
         case EXTENDS:
            return "? extends " + this.type();
         case SUPER:
            return "? super " + this.type();
         default:
            throw new AssertionError("Unknown projection: " + this.projection());
      }
   }

   public IntrospectType type() {
      if (this.type == null) {
         if (this.argument.getValue() == null) {
            this.type = this.introspection.getClass(ValueType.object("java.lang.Object"));
         } else {
            this.type = this.introspection.convertGenericType(this.argument.getValue(), this.declaringClass, this.declaringMethod);
         }
      }

      return this.type;
   }
}
