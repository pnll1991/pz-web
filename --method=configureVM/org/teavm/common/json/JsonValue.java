package org.teavm.common.json;

public abstract class JsonValue {
   public JsonObjectValue asObject() {
      throw new IllegalStateException();
   }

   public String asString() {
      throw new IllegalStateException();
   }

   public long asIntNumber() {
      throw new IllegalStateException();
   }

   public double asNumber() {
      throw new IllegalStateException();
   }
}
