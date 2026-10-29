package org.teavm.cache;

import java.util.Map;
import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.AnnotationReader;

class CachedAnnotations implements AnnotationContainerReader {
   private Map<String, CachedAnnotation> data;

   public CachedAnnotations(Map<String, CachedAnnotation> data) {
      this.data = data;
   }

   @Override
   public AnnotationReader get(String type) {
      return this.data.get(type);
   }

   @Override
   public Iterable<? extends AnnotationReader> all() {
      return this.data.values();
   }
}
