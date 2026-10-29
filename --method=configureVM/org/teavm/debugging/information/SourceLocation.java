package org.teavm.debugging.information;

public class SourceLocation {
   private String fileName;
   private int line;

   public SourceLocation(String fileName, int line) {
      this.fileName = fileName;
      this.line = line;
   }

   public String getFileName() {
      return this.fileName;
   }

   public int getLine() {
      return this.line;
   }

   @Override
   public String toString() {
      return this.fileName + ":" + this.line;
   }
}
