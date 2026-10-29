package org.teavm.common.json;

public class JsonBooleanValue extends JsonValue {
   private boolean innerValue;

   public JsonBooleanValue(boolean value) {
      this.innerValue = value;
   }

   public boolean asBoolean() {
      return this.innerValue;
   }
}
