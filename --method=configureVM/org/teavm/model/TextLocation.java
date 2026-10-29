package org.teavm.model;

import java.io.Serializable;
import java.util.Objects;

public class TextLocation implements Serializable {
   public static final TextLocation EMPTY = new TextLocation(null, -1);
   private static final InliningInfo[] EMPTY_ARRAY = new InliningInfo[0];
   private String fileName;
   private int line;
   private InliningInfo inlining;
   private transient int hash;

   public TextLocation(String fileName, int line) {
      this(fileName, line, null);
   }

   public TextLocation(String fileName, int line, InliningInfo inlining) {
      this.fileName = fileName;
      this.line = line;
      this.inlining = inlining;
   }

   public String getFileName() {
      return this.fileName;
   }

   public int getLine() {
      return this.line;
   }

   public InliningInfo getInlining() {
      return this.inlining;
   }

   public InliningInfo[] getInliningPath() {
      if (this.inlining == null) {
         return EMPTY_ARRAY;
      }

      InliningInfo inlining = this.inlining;
      int sz = 0;

      while (inlining != null) {
         sz++;
         inlining = inlining.getParent();
      }

      InliningInfo[] result = new InliningInfo[sz];

      for (InliningInfo var4 = this.inlining; var4 != null; var4 = var4.getParent()) {
         result[--sz] = var4;
      }

      return result;
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
         int var4 = 31 * var3 + (this.fileName == null ? 0 : this.fileName.hashCode());
         int var5 = 31 * var4 + this.line;
         result = 31 * var5 + (this.inlining != null ? this.inlining.hashCode() : 0);
         this.hash = result;
      }

      return result;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else {
         return !(obj instanceof TextLocation other)
            ? false
            : Objects.equals(this.fileName, other.fileName) && this.line == other.line && Objects.equals(this.inlining, other.inlining);
      }
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(this.fileName).append(':').append(this.line);
      InliningInfo inlining = this.inlining;
      if (inlining != null) {
         sb.append('[');
         boolean first = true;

         while (inlining != null) {
            if (!first) {
               sb.append("->");
            }

            first = false;
            sb.append(inlining.getMethod()).append("@").append(inlining.getFileName()).append(':').append(inlining.getLine());
            inlining = inlining.getParent();
         }

         sb.append(']');
      }

      return sb.toString();
   }
}
