package org.teavm.model;

import java.io.Serializable;
import java.util.Objects;

public class InliningInfo implements Serializable {
   private MethodReference method;
   private String fileName;
   private int line;
   private InliningInfo parent;
   private transient int hash;

   public InliningInfo(MethodReference method, String fileName, int line, InliningInfo parent) {
      this.method = method;
      this.fileName = fileName;
      this.line = line;
      this.parent = parent;
   }

   public MethodReference getMethod() {
      return this.method;
   }

   public String getFileName() {
      return this.fileName;
   }

   public int getLine() {
      return this.line;
   }

   public InliningInfo getParent() {
      return this.parent;
   }

   public boolean isEmpty() {
      return this.fileName == null && this.line < 0;
   }

   @Override
   public int hashCode() {
      int result = this.hash;
      if (result == 0) {
         int prime = 31;
         int var3 = 1;
         int var4 = 31 * var3 + this.method.hashCode();
         int var5 = 31 * var4 + (this.fileName == null ? 0 : this.fileName.hashCode());
         int var6 = 31 * var5 + this.line;
         result = 31 * var6 + (this.parent != null ? this.parent.hashCode() : 0);
         this.hash = result;
      }

      return result;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else {
         return !(obj instanceof InliningInfo that)
            ? false
            : Objects.equals(this.method, that.method)
               && Objects.equals(this.fileName, that.fileName)
               && this.line == that.line
               && Objects.equals(this.parent, that.parent);
      }
   }
}
