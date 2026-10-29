package org.teavm.common.json;

public class JsonSyntaxException extends RuntimeException {
   private final int lineNumber;
   private final int columnNumber;
   private final String error;

   public JsonSyntaxException(int lineNumber, int columnNumber, String error) {
      super("JSON syntax error at " + (lineNumber + 1) + ":" + (columnNumber + 1) + ": " + error);
      this.lineNumber = lineNumber;
      this.columnNumber = columnNumber;
      this.error = error;
   }

   public int getLineNumber() {
      return this.lineNumber;
   }

   public int getColumnNumber() {
      return this.columnNumber;
   }

   public String getError() {
      return this.error;
   }
}
