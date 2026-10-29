package org.teavm.common.json;

public class JsonStringValue extends JsonValue {
   private String text;

   public JsonStringValue(String text) {
      this.text = text;
   }

   @Override
   public String asString() {
      return this.text;
   }
}
