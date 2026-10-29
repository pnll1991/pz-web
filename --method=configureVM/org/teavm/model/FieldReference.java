package org.teavm.model;

import java.io.Serializable;

public class FieldReference implements Serializable {
   private String className;
   private String fieldName;

   public FieldReference(String className, String fieldName) {
      this.className = className;
      this.fieldName = fieldName;
   }

   public String getClassName() {
      return this.className;
   }

   public String getFieldName() {
      return this.fieldName;
   }

   @Override
   public int hashCode() {
      return this.className.hashCode() ^ this.fieldName.hashCode();
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else {
         return !(obj instanceof FieldReference other) ? false : this.className.equals(other.className) && this.fieldName.equals(other.fieldName);
      }
   }

   @Override
   public String toString() {
      return this.className + "." + this.fieldName;
   }
}
