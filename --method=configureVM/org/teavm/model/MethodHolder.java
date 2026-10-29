package org.teavm.model;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class MethodHolder extends MemberHolder implements MethodReader {
   private MethodDescriptor descriptor;
   private GenericTypeParameter[] typeParameters;
   private GenericValueType genericReturnType;
   private GenericValueType[] genericParameterTypes;
   private ClassHolder owner;
   private Program program;
   private Function<MethodHolder, Program> programSupplier;
   private AnnotationValue annotationDefault;
   private AnnotationContainer[] parameterAnnotations;
   private MethodReference reference;
   private List<String> thrownTypes;

   public MethodHolder(MethodDescriptor descriptor) {
      super(descriptor.getName());
      this.descriptor = descriptor;
      this.parameterAnnotations = new AnnotationContainer[descriptor.parameterCount()];

      for (int i = 0; i < this.parameterAnnotations.length; i++) {
         this.parameterAnnotations[i] = new AnnotationContainer();
      }
   }

   public MethodHolder(String name, ValueType... signature) {
      this(new MethodDescriptor(name, signature));
   }

   @Override
   public ValueType getResultType() {
      return this.descriptor.getResultType();
   }

   @Override
   public GenericValueType getGenericResultType() {
      return this.genericReturnType;
   }

   @Override
   public GenericValueType genericParameterType(int index) {
      return this.genericParameterTypes != null ? this.genericParameterTypes[index] : null;
   }

   @Override
   public GenericValueType[] getGenericParameterTypes() {
      return this.genericParameterTypes != null ? (GenericValueType[])this.genericParameterTypes.clone() : null;
   }

   public void setGenericSignature(GenericValueType returnType, GenericValueType[] parameterTypes) {
      this.genericReturnType = returnType;
      this.genericParameterTypes = parameterTypes != null ? (GenericValueType[])parameterTypes.clone() : null;
   }

   public void removeGenericSignature() {
      this.genericReturnType = null;
      this.genericParameterTypes = null;
   }

   @Override
   public GenericTypeParameter[] getTypeParameters() {
      return this.typeParameters != null ? (GenericTypeParameter[])this.typeParameters.clone() : new GenericTypeParameter[0];
   }

   public void setTypeParameters(GenericTypeParameter[] typeParameters) {
      this.typeParameters = typeParameters != null ? (GenericTypeParameter[])typeParameters.clone() : null;
   }

   @Override
   public int parameterCount() {
      return this.descriptor.parameterCount();
   }

   @Override
   public ValueType[] getSignature() {
      return this.descriptor.getSignature();
   }

   @Override
   public ValueType parameterType(int index) {
      return this.descriptor.parameterType(index);
   }

   @Override
   public ValueType[] getParameterTypes() {
      return this.descriptor.getParameterTypes();
   }

   public AnnotationContainer parameterAnnotation(int index) {
      return this.parameterAnnotations[index];
   }

   public AnnotationContainer[] getParameterAnnotations() {
      return (AnnotationContainer[])this.parameterAnnotations.clone();
   }

   @Override
   public String getOwnerName() {
      return this.owner != null ? this.owner.getName() : null;
   }

   ClassHolder getOwner() {
      return this.owner;
   }

   void setOwner(ClassHolder owner) {
      this.reference = null;
      this.owner = owner;
   }

   @Override
   public MethodDescriptor getDescriptor() {
      return this.descriptor;
   }

   @Override
   public MethodReference getReference() {
      if (this.owner == null) {
         return null;
      }

      if (this.reference == null) {
         this.reference = new MethodReference(this.owner.getName(), this.descriptor);
      }

      return this.reference;
   }

   public void updateReference(ReferenceCache cache) {
      MethodReference reference = this.getReference();
      if (reference != null) {
         this.reference = cache.getCached(reference);
      }
   }

   public Program getProgram() {
      if (this.program == null && this.programSupplier != null) {
         this.program = this.programSupplier.apply(this);
         this.programSupplier = null;
      }

      return this.program;
   }

   public void setProgram(Program program) {
      this.program = program;
      this.programSupplier = null;
   }

   public boolean hasProgram() {
      return this.program != null || this.programSupplier != null;
   }

   public void setProgramSupplier(Function<MethodHolder, Program> programSupplier) {
      this.program = null;
      this.programSupplier = programSupplier;
   }

   @Override
   public AnnotationValue getAnnotationDefault() {
      return this.annotationDefault;
   }

   public void setAnnotationDefault(AnnotationValue annotationDefault) {
      this.annotationDefault = annotationDefault;
   }

   @Override
   public List<? extends String> getThrownTypes() {
      return this.thrownTypes != null ? this.thrownTypes : Collections.emptyList();
   }

   public void setThrownTypes(List<? extends String> thrownTypes) {
      if (thrownTypes.isEmpty()) {
         this.thrownTypes = null;
      } else {
         this.thrownTypes = List.copyOf(thrownTypes);
      }
   }
}
