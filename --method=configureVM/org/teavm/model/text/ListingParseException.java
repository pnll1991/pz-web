package org.teavm.model.text;

public class ListingParseException extends Exception {
   private final int index;

   public ListingParseException(String message, int index) {
      super(message);
      this.index = index;
   }

   public int getIndex() {
      return this.index;
   }
}
