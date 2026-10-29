package org.teavm.model;

public interface AnnotationContainerReader {
   AnnotationReader get(String var1);

   Iterable<? extends AnnotationReader> all();
}
