package org.teavm.model;

import java.util.EnumSet;

public interface ElementReader {
   AccessLevel getLevel();

   EnumSet<ElementModifier> readModifiers();

   boolean hasModifier(ElementModifier var1);

   String getName();

   AnnotationContainerReader getAnnotations();
}
