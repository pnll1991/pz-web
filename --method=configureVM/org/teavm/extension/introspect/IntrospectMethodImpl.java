package org.teavm.extension.introspect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.GenericTypeParameter;
import org.teavm.model.GenericValueType;
import org.teavm.model.MethodReader;
import org.teavm.model.ValueType;

public class IntrospectMethodImpl extends IntrospectAnnotatedElementImpl implements IntrospectMethod {
   IntrospectClassImpl<?> declaringClass;
   public final MethodReader method;
   private IntrospectClassImpl<?> returnType;
   private List<? extends IntrospectParameter> parameters;
   private List<? extends IntrospectTypeVariableImpl> typeParameters;
   private List<? extends IntrospectClass<? extends Throwable>> exceptionTypes;

   IntrospectMethodImpl(IntrospectClassImpl<?> declaringClass, MethodReader method) {
      super(declaringClass.introspection);
      this.declaringClass = declaringClass;
      this.method = method;
   }

   public IntrospectClass<?> declaringClass() {
      return this.declaringClass;
   }

   public String name() {
      return this.method.getName();
   }

   public int modifiers() {
      return Introspection.getModifiers(this.method);
   }

   public boolean isConstructor() {
      return this.method.getName().equals("<init>");
   }

   public IntrospectClass<?> returnType() {
      if (this.returnType == null) {
         this.returnType = this.introspection.getClass(this.method.getResultType());
      }

      return this.returnType;
   }

   public List<? extends IntrospectParameter> parameters() {
      if (this.parameters == null) {
         ArrayList<IntrospectParameterImpl> params = new ArrayList<>(this.method.parameterCount());

         for (int i = 0; i < this.method.parameterCount(); i++) {
            params.add(new IntrospectParameterImpl(this, i));
         }

         this.parameters = Collections.unmodifiableList(params);
      }

      return this.parameters;
   }

   public List<? extends IntrospectTypeVariable> typeParameters() {
      if (this.typeParameters == null) {
         GenericTypeParameter[] params = this.method.getTypeParameters();
         if (params != null && params.length != 0) {
            ArrayList<IntrospectTypeVariableImpl> result = new ArrayList<>(params.length);

            for (GenericTypeParameter param : params) {
               result.add(new IntrospectTypeVariableImpl(param, this.declaringClass, this, this.introspection));
            }

            this.typeParameters = Collections.unmodifiableList(result);
         } else {
            this.typeParameters = Collections.emptyList();
         }
      }

      return this.typeParameters;
   }

   public List<? extends IntrospectClass<? extends Throwable>> exceptionTypes() {
      if (this.exceptionTypes == null) {
         List<? extends String> thrownTypes = this.method.getThrownTypes();
         if (thrownTypes != null && !thrownTypes.isEmpty()) {
            ArrayList<IntrospectClass<? extends Throwable>> result = new ArrayList<>(thrownTypes.size());

            for (String name : thrownTypes) {
               result.add((IntrospectClass<? extends Throwable>)this.introspection.getClass(ValueType.object(name)));
            }

            this.exceptionTypes = Collections.unmodifiableList(result);
         } else {
            this.exceptionTypes = Collections.emptyList();
         }
      }

      return this.exceptionTypes;
   }

   IntrospectTypeVariableImpl typeParameter(String name) {
      for (IntrospectTypeVariable tp : this.typeParameters()) {
         if (tp.name().equals(name)) {
            return (IntrospectTypeVariableImpl)tp;
         }
      }

      return null;
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      this.typeParameters();
      if (!this.typeParameters.isEmpty()) {
         sb.append('<');
         sb.append(this.typeParameters.stream().map(IntrospectTypeVariableImpl::toTypeParameterString).collect(Collectors.joining(", ")));
         sb.append("> ");
      }

      GenericValueType genericResultType = this.method.getGenericResultType();
      if (genericResultType != null) {
         sb.append(this.introspection.convertGenericType(genericResultType, this.declaringClass, this));
      } else {
         sb.append(this.returnType());
      }

      sb.append(' ').append(this.name()).append('(');
      sb.append(this.parameters().stream().map(Object::toString).collect(Collectors.joining(", ")));
      sb.append(')');
      return sb.toString();
   }

   @Override
   protected AnnotationContainerReader annotationContainer() {
      return this.method.getAnnotations();
   }
}
