package org.teavm.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class AnnotationHolder implements AnnotationReader {
   private String type;
   private Map<String, AnnotationValue> values = new LinkedHashMap<>();

   public AnnotationHolder(String type) {
      this.type = type;
   }

   @Override
   public String getType() {
      return this.type;
   }

   public Map<String, AnnotationValue> getValues() {
      return this.values;
   }

   @Override
   public AnnotationValue getValue(String fieldName) {
      return this.values.get(fieldName);
   }

   @Override
   public Iterable<String> getAvailableFields() {
      return this.values.keySet();
   }
}
