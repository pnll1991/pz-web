package org.teavm.debugging.information;

public class GeneratedLocation implements Comparable<GeneratedLocation> {
   private int line;
   private int column;

   public GeneratedLocation(int line, int column) {
      this.line = line;
      this.column = column;
   }

   public int getLine() {
      return this.line;
   }

   public int getColumn() {
      return this.column;
   }

   public int compareTo(GeneratedLocation o) {
      int r = Integer.compare(this.line, o.line);
      if (r == 0) {
         r = Integer.compare(this.column, o.column);
      }

      return r;
   }

   @Override
   public int hashCode() {
      int prime = 31;
      int result = 1;
      result = 31 * result + this.column;
      return 31 * result + this.line;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else {
         return !(obj instanceof GeneratedLocation other) ? false : this.line == other.line && this.column == other.column;
      }
   }

   @Override
   public String toString() {
      return "line: " + this.line + ", column: " + this.column;
   }
}
