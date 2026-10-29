package org.teavm.extension.introspect;

import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.GenericValueType;

public class IntrospectFieldImpl extends IntrospectAnnotatedElementImpl implements IntrospectField {
   private IntrospectClassImpl<?> declaringClass;
   public final FieldReader field;
   private IntrospectClassImpl<?> type;
   private IntrospectType genericType;

   IntrospectFieldImpl(IntrospectClassImpl<?> declaringClass, FieldReader field) {
      super(declaringClass.introspection());
      this.declaringClass = declaringClass;
      this.field = field;
   }

   public IntrospectClass<?> declaringClass() {
      return this.declaringClass;
   }

   public String name() {
      return this.field.getName();
   }

   public int modifiers() {
      return Introspection.getModifiers(this.field);
   }

   public boolean isEnumConstant() {
      return this.field.readModifiers().contains(ElementModifier.ENUM);
   }

   public IntrospectClass<?> type() {
      if (this.type == null) {
         this.type = this.introspection.getClass(this.field.getType());
      }

      return this.type;
   }

   public IntrospectType genericType() {
      if (this.genericType == null) {
         GenericValueType gvt = this.field.getGenericType();
         this.genericType = (IntrospectType)(gvt != null ? this.introspection.convertGenericType(gvt, this.declaringClass, null) : this.type());
      }

      return this.genericType;
   }

   public FieldReader getBackingField() {
      return this.field;
   }

   @Override
   public String toString() {
      return this.genericType() + " " + this.name();
   }

   @Override
   protected AnnotationContainerReader annotationContainer() {
      return this.field.getAnnotations();
   }
}
