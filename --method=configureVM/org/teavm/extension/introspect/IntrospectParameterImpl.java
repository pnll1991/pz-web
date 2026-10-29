package org.teavm.extension.introspect;

import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.GenericValueType;

public class IntrospectParameterImpl extends IntrospectAnnotatedElementImpl implements IntrospectParameter {
   private final IntrospectMethodImpl declaringMethod;
   private final int index;
   private IntrospectClass<?> type;
   private IntrospectType genericType;

   IntrospectParameterImpl(IntrospectMethodImpl declaringMethod, int index) {
      super(declaringMethod.introspection);
      this.declaringMethod = declaringMethod;
      this.index = index;
   }

   public IntrospectClass<?> type() {
      if (this.type == null) {
         this.type = this.introspection.getClass(this.declaringMethod.method.parameterType(this.index));
      }

      return this.type;
   }

   public IntrospectType genericType() {
      if (this.genericType == null) {
         GenericValueType gvt = this.declaringMethod.method.genericParameterType(this.index);
         this.genericType = (IntrospectType)(gvt != null
            ? this.introspection.convertGenericType(gvt, this.declaringMethod.declaringClass, this.declaringMethod)
            : this.type());
      }

      return this.genericType;
   }

   @Override
   public String toString() {
      return this.genericType().toString();
   }

   @Override
   protected AnnotationContainerReader annotationContainer() {
      return this.declaringMethod.method.parameterAnnotation(this.index);
   }
}
