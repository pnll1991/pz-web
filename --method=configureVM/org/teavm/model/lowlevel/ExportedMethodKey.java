package org.teavm.model.lowlevel;

import java.util.Objects;

public class ExportedMethodKey {
   public final String functionClassName;
   public final String className;
   public final String methodName;

   public ExportedMethodKey(String functionClassName, String className, String methodName) {
      this.functionClassName = functionClassName;
      this.className = className;
      this.methodName = methodName;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         ExportedMethodKey that = (ExportedMethodKey)o;
         return Objects.equals(this.functionClassName, that.functionClassName)
            && Objects.equals(this.className, that.className)
            && Objects.equals(this.methodName, that.methodName);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.functionClassName, this.className, this.methodName);
   }
}
