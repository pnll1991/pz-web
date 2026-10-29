package org.teavm.common.json;

import java.util.HashMap;
import java.util.Map;

public class JsonPropertyVisitor extends JsonAllErrorVisitor {
   private Map<String, JsonVisitor> properties = new HashMap<>();
   private boolean skipNonExistentProperties;

   public JsonPropertyVisitor(boolean skipNonExistentProperties) {
      this.skipNonExistentProperties = skipNonExistentProperties;
   }

   public JsonPropertyVisitor addProperty(String propertyName, JsonVisitor visitor) {
      this.properties.put(propertyName, visitor);
      return this;
   }

   @Override
   public JsonVisitor property(JsonErrorReporter reporter, String name) {
      if (!this.skipNonExistentProperties && !this.properties.containsKey(name)) {
         reporter.error("Unexpected property name: " + name);
      }

      return this.properties.get(name);
   }
}
