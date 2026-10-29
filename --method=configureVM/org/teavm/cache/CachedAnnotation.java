package org.teavm.cache;

import java.util.Map;
import org.teavm.model.AnnotationReader;
import org.teavm.model.AnnotationValue;

class CachedAnnotation implements AnnotationReader {
   String type;
   Map<String, AnnotationValue> fields;

   @Override
   public String getType() {
      return this.type;
   }

   @Override
   public AnnotationValue getValue(String fieldName) {
      return this.fields.get(fieldName);
   }

   @Override
   public Iterable<String> getAvailableFields() {
      return this.fields.keySet();
   }
}
