package org.teavm.model;

import java.util.EnumSet;

public abstract class ElementHolder implements ElementReader {
   private EnumSet<ElementModifier> modifiers = EnumSet.noneOf(ElementModifier.class);
   private AnnotationContainer annotations = new AnnotationContainer();
   private AccessLevel level = AccessLevel.PACKAGE_PRIVATE;
   private String name;

   public ElementHolder(String name) {
      this.name = name;
   }

   @Override
   public EnumSet<ElementModifier> readModifiers() {
      return this.modifiers.clone();
   }

   @Override
   public boolean hasModifier(ElementModifier modifier) {
      return this.modifiers.contains(modifier);
   }

   public EnumSet<ElementModifier> getModifiers() {
      return this.modifiers;
   }

   @Override
   public AccessLevel getLevel() {
      return this.level;
   }

   public void setLevel(AccessLevel level) {
      this.level = level;
   }

   @Override
   public String getName() {
      return this.name;
   }

   public AnnotationContainer getAnnotations() {
      return this.annotations;
   }
}
