package org.teavm.model;

public interface AnnotationReader {
   String getType();

   AnnotationValue getValue(String var1);

   Iterable<String> getAvailableFields();
}
