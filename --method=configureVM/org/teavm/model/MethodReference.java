package org.teavm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

public class MethodReference implements Serializable {
   private String className;
   private MethodDescriptor descriptor;
   private transient int hash;

   public MethodReference(String className, MethodDescriptor descriptor) {
      this.className = className;
      this.descriptor = descriptor;
   }

   public MethodReference(String className, String name, ValueType... signature) {
      this(className, new MethodDescriptor(name, signature));
   }

   public MethodReference(Class<?> cls, String name, Class<?>... signature) {
      this(cls.getName(), name, convertSignature(signature));
   }

   private static ValueType[] convertSignature(Class<?>... signature) {
      ValueType[] types = new ValueType[signature.length];

      for (int i = 0; i < types.length; i++) {
         types[i] = ValueType.parse(signature[i]);
      }

      return types;
   }

   public String getClassName() {
      return this.className;
   }

   public MethodDescriptor getDescriptor() {
      return this.descriptor;
   }

   public int parameterCount() {
      return this.descriptor.parameterCount();
   }

   public ValueType parameterType(int index) {
      return this.descriptor.parameterType(index);
   }

   public ValueType[] getParameterTypes() {
      return this.descriptor.getParameterTypes();
   }

   public ValueType[] getSignature() {
      return this.descriptor.getSignature();
   }

   public ValueType getReturnType() {
      return this.descriptor.getResultType();
   }

   public String getName() {
      return this.descriptor.getName();
   }

   @Override
   public int hashCode() {
      if (this.hash == 0) {
         this.hash = (this.className.hashCode() * 31 + this.descriptor.hashCode()) * 17;
         if (this.hash == 0) {
            this.hash++;
         }
      }

      return this.hash;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else {
         return !(obj instanceof MethodReference other) ? false : this.className.equals(other.className) && this.descriptor.equals(other.descriptor);
      }
   }

   @JsonValue
   @Override
   public String toString() {
      return this.className + "." + this.getDescriptor().toString();
   }

   @JsonCreator
   public static MethodReference parse(String string) {
      MethodReference reference = parseIfPossible(string);
      if (reference == null) {
         throw new IllegalArgumentException("Illegal method reference: " + string);
      } else {
         return reference;
      }
   }

   public static MethodReference parseIfPossible(String string) {
      int index = string.lastIndexOf(46);
      if (index < 1) {
         return null;
      }

      String className = string.substring(0, index);
      MethodDescriptor desc = MethodDescriptor.parseIfPossible(string.substring(index + 1));
      return desc != null ? new MethodReference(className, desc) : null;
   }

   public static MethodReference parse(Method method) {
      ValueType[] signature = Stream.concat(Arrays.stream(method.getParameterTypes()).map(ValueType::parse), Stream.of(ValueType.parse(method.getReturnType())))
         .toArray(ValueType[]::new);
      return new MethodReference(method.getDeclaringClass().getName(), method.getName(), signature);
   }

   public String signatureToString() {
      return this.getDescriptor().signatureToString();
   }
}
