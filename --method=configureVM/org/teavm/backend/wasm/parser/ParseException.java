package org.teavm.backend.wasm.parser;

public class ParseException extends RuntimeException {
   public final String message;
   public final int pos;

   public ParseException(String message, int pos) {
      super("Error parsing at " + pos + ": " + message);
      this.message = message;
      this.pos = pos;
   }
}
