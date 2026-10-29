package org.teavm.model;

import java.io.Serializable;
import java.util.Objects;

public class CallLocation implements Serializable {
   private MethodReference method;
   private TextLocation sourceLocation;

   public CallLocation(MethodReference method) {
      this(method, null);
   }

   public CallLocation(MethodReference method, TextLocation sourceLocation) {
      if (method == null) {
         throw new IllegalArgumentException("Method must not be null");
      }

      this.method = method;
      this.sourceLocation = sourceLocation;
   }

   public MethodReference getMethod() {
      return this.method;
   }

   public TextLocation getSourceLocation() {
      return this.sourceLocation;
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.method, this.sourceLocation);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else {
         return !(obj instanceof CallLocation other)
            ? false
            : Objects.equals(this.method, other.method) && Objects.equals(this.sourceLocation, other.sourceLocation);
      }
   }
}
