package org.teavm.model;

import java.io.Serializable;
import java.util.Arrays;

public class MethodDescriptor implements Serializable {
   private String name;
   private ValueType[] signature;
   private transient int hash;

   public MethodDescriptor(String name, ValueType... signature) {
      if (signature.length < 1) {
         throw new IllegalArgumentException("Signature must be at least 1 element length");
      }

      this.name = name;
      this.signature = Arrays.copyOf(signature, signature.length);
   }

   public MethodDescriptor(String name, Class<?>... signature) {
      if (signature.length < 1) {
         throw new IllegalArgumentException("Signature must be at least 1 element length");
      }

      this.name = name;
      this.signature = new ValueType[signature.length];

      for (int i = 0; i < signature.length; i++) {
         this.signature[i] = ValueType.parse(signature[i]);
      }
   }

   public String getName() {
      return this.name;
   }

   public ValueType[] getSignature() {
      return Arrays.copyOf(this.signature, this.signature.length);
   }

   public ValueType[] getParameterTypes() {
      return Arrays.copyOf(this.signature, this.signature.length - 1);
   }

   public ValueType getResultType() {
      return this.signature[this.signature.length - 1];
   }

   public int parameterCount() {
      return this.signature.length - 1;
   }

   public ValueType parameterType(int index) {
      if (index >= this.signature.length - 1) {
         throw new IndexOutOfBoundsException(index + "/" + (this.signature.length - 1));
      } else {
         return this.signature[index];
      }
   }

   @Override
   public String toString() {
      return this.name + this.signatureToString();
   }

   public String signatureToString() {
      StringBuilder sb = new StringBuilder();
      sb.append('(');

      for (int i = 0; i < this.signature.length - 1; i++) {
         sb.append(this.signature[i].toString());
      }

      sb.append(')');
      sb.append(this.signature[this.signature.length - 1]);
      return sb.toString();
   }

   public static MethodDescriptor get(MethodHolder method) {
      return new MethodDescriptor(method.getName(), method.getSignature());
   }

   public static MethodDescriptor parse(String text) {
      MethodDescriptor desc = parseIfPossible(text);
      if (desc == null) {
         throw new IllegalArgumentException("Wrong method descriptor: " + text);
      } else {
         return desc;
      }
   }

   public static MethodDescriptor parseIfPossible(String text) {
      int parenIndex = text.indexOf(40);
      return parenIndex < 1 ? null : new MethodDescriptor(text.substring(0, parenIndex), parseSignature(text.substring(parenIndex)));
   }

   public static ValueType[] parseSignature(String text) {
      ValueType[] signature = parseSignatureIfPossible(text);
      if (signature == null) {
         throw new IllegalArgumentException("Illegal method signature: " + text);
      } else {
         return signature;
      }
   }

   public static ValueType[] parseSignatureIfPossible(String text) {
      if (text.charAt(0) != '(') {
         return null;
      }

      int index = text.indexOf(41, 1);
      if (index <= 0) {
         return null;
      }

      ValueType[] params = ValueType.parseManyIfPossible(text.substring(1, index));
      if (params == null) {
         return null;
      }

      ValueType result = ValueType.parseIfPossible(text.substring(index + 1));
      if (result == null) {
         return null;
      }

      ValueType[] signature = new ValueType[params.length + 1];
      System.arraycopy(params, 0, signature, 0, params.length);
      signature[params.length] = result;
      return signature;
   }

   @Override
   public int hashCode() {
      if (this.hash == 0) {
         this.hash = this.name.hashCode();

         for (ValueType param : this.signature) {
            this.hash = 31 * this.hash + param.hashCode();
         }

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
      } else if (!(obj instanceof MethodDescriptor that)) {
         return false;
      } else {
         if (this.signature.length != that.signature.length) {
            return false;
         }

         if (!this.name.equals(that.name)) {
            return false;
         }

         for (int i = 0; i < this.signature.length; i++) {
            if (!this.signature[i].equals(that.signature[i])) {
               return false;
            }
         }

         return true;
      }
   }
}
