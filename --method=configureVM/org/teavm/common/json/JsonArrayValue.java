package org.teavm.common.json;

import java.util.ArrayList;
import java.util.List;

public class JsonArrayValue extends JsonValue {
   private List<JsonValue> elements = new ArrayList<>();

   public int size() {
      return this.elements.size();
   }

   public JsonValue get(int index) {
      return this.elements.get(index);
   }

   public void add(JsonValue element) {
      this.elements.add(element);
   }
}
