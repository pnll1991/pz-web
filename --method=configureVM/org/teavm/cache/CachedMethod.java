package org.teavm.cache;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.AnnotationValue;
import org.teavm.model.GenericTypeParameter;
import org.teavm.model.GenericValueType;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ProgramReader;
import org.teavm.model.ValueType;

class CachedMethod extends CachedMember implements MethodReader {
   MethodReference reference;
   GenericTypeParameter[] typeParameters;
   GenericValueType genericReturnType;
   GenericValueType[] genericParameterTypes;
   CachedAnnotations[] parameterAnnotations;
   AnnotationValue annotationDefault;
   WeakReference<ProgramReader> program;
   Supplier<ProgramReader> programSupplier;
   List<? extends String> thrownTypes = Collections.emptyList();

   @Override
   public GenericTypeParameter[] getTypeParameters() {
      return this.typeParameters != null ? (GenericTypeParameter[])this.typeParameters.clone() : new GenericTypeParameter[0];
   }

   @Override
   public ValueType getResultType() {
      return this.reference.getReturnType();
   }

   @Override
   public GenericValueType getGenericResultType() {
      return this.genericReturnType;
   }

   @Override
   public int parameterCount() {
      return this.reference.parameterCount();
   }

   @Override
   public ValueType[] getSignature() {
      return this.reference.getSignature();
   }

   @Override
   public ValueType parameterType(int index) {
      return this.reference.parameterType(index);
   }

   @Override
   public GenericValueType[] getGenericParameterTypes() {
      return this.genericParameterTypes != null ? (GenericValueType[])this.genericParameterTypes.clone() : null;
   }

   @Override
   public GenericValueType genericParameterType(int index) {
      return this.genericParameterTypes != null ? this.genericParameterTypes[index] : null;
   }

   @Override
   public ValueType[] getParameterTypes() {
      return this.reference.getParameterTypes();
   }

   @Override
   public AnnotationContainerReader parameterAnnotation(int index) {
      return this.parameterAnnotations[index];
   }

   @Override
   public AnnotationContainerReader[] getParameterAnnotations() {
      return (AnnotationContainerReader[])this.parameterAnnotations.clone();
   }

   @Override
   public MethodDescriptor getDescriptor() {
      return this.reference.getDescriptor();
   }

   @Override
   public MethodReference getReference() {
      return this.reference;
   }

   @Override
   public ProgramReader getProgram() {
      if (this.programSupplier == null) {
         return null;
      }

      ProgramReader program = this.program != null ? this.program.get() : null;
      if (program == null) {
         program = this.programSupplier.get();
         this.program = new WeakReference<>(program);
      }

      return program;
   }

   @Override
   public AnnotationValue getAnnotationDefault() {
      return this.annotationDefault;
   }

   @Override
   public List<? extends String> getThrownTypes() {
      return this.thrownTypes;
   }
}
