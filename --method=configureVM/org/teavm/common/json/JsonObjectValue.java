package org.teavm.common.json;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public class JsonObjectValue extends JsonValue {
   private Map<String, JsonValue> properties = new HashMap<>();

   public void put(String name, JsonValue value) {
      this.properties.put(name, value);
   }

   public JsonValue get(String name) {
      return this.properties.get(name);
   }

   public boolean has(String name) {
      return this.properties.containsKey(name);
   }

   public Set<Entry<String, JsonValue>> entrySet() {
      return this.properties.entrySet();
   }

   @Override
   public JsonObjectValue asObject() {
      return this;
   }
}
