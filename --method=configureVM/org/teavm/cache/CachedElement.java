package org.teavm.cache;

import java.util.EnumSet;
import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.ElementReader;

class CachedElement implements ElementReader {
   EnumSet<ElementModifier> modifiers;
   CachedAnnotations annotations;
   AccessLevel level;
   String name;

   @Override
   public AccessLevel getLevel() {
      return this.level;
   }

   @Override
   public EnumSet<ElementModifier> readModifiers() {
      return this.modifiers.clone();
   }

   @Override
   public boolean hasModifier(ElementModifier modifier) {
      return this.modifiers.contains(modifier);
   }

   @Override
   public String getName() {
      return this.name;
   }

   @Override
   public AnnotationContainerReader getAnnotations() {
      return this.annotations;
   }
}
