package org.teavm.common.json;

import java.util.function.Consumer;

public class JsonValueProvider implements Consumer<JsonValue> {
   private JsonValue value;

   public JsonValue getValue() {
      return this.value;
   }

   public void accept(JsonValue jsonValue) {
      this.value = jsonValue;
   }
}
