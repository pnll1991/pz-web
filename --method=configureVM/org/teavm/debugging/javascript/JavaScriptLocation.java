package org.teavm.debugging.javascript;

import java.util.Objects;

public class JavaScriptLocation {
   private JavaScriptScript script;
   private int line;
   private int column;

   public JavaScriptLocation(JavaScriptScript script, int line, int column) {
      this.script = script;
      this.line = line;
      this.column = column;
   }

   public JavaScriptScript getScript() {
      return this.script;
   }

   public int getLine() {
      return this.line;
   }

   public int getColumn() {
      return this.column;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else {
         return !(obj instanceof JavaScriptLocation other)
            ? false
            : Objects.equals(other.script, this.script) && other.line == this.line && other.column == this.column;
      }
   }

   @Override
   public int hashCode() {
      return (31 + this.column) * ((31 + this.line) * 31 + Objects.hashCode(this.script));
   }

   @Override
   public String toString() {
      return this.script + ":(" + this.line + ";" + this.column + ")";
   }
}
