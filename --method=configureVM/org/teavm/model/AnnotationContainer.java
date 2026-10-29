package org.teavm.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnnotationContainer implements AnnotationContainerReader {
   private Map<String, AnnotationHolder> annotations;
   private List<AnnotationHolder> annotationsList;

   public void add(AnnotationHolder annotation) {
      if (this.annotations == null) {
         this.annotations = new LinkedHashMap<>();
      }

      if (this.annotationsList == null) {
         this.annotationsList = new ArrayList<>();
      }

      if (this.annotations.containsKey(annotation.getType())) {
         throw new IllegalArgumentException("Annotation of type " + annotation.getType() + " is already there");
      }

      this.annotations.put(annotation.getType(), annotation);
      this.annotationsList.add(annotation);
   }

   public AnnotationHolder get(String type) {
      return this.annotations != null ? this.annotations.get(type) : null;
   }

   public void remove(AnnotationHolder annotation) {
      AnnotationHolder existingAnnot = this.get(annotation.getType());
      if (existingAnnot != annotation) {
         throw new IllegalArgumentException("There is no such annotation");
      }

      this.annotations.remove(annotation.getType(), annotation);
      this.annotationsList.remove(annotation);
   }

   public void remove(String type) {
      if (this.annotations != null) {
         this.annotations.remove(type);
      }

      this.annotationsList.removeIf(a -> a.getType().equals(type));
   }

   public void removeAll() {
      this.annotations = null;
      this.annotationsList = null;
   }

   @Override
   public Iterable<AnnotationHolder> all() {
      return this.annotationsList != null ? this.annotationsList : Collections.emptyList();
   }
}
