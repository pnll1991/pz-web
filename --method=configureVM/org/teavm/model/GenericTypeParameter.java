package org.teavm.model;

import java.util.ArrayList;
import java.util.List;

public class GenericTypeParameter {
   private String name;
   private GenericValueType.Reference classBound;
   private GenericValueType.Reference[] interfaceBounds;

   public GenericTypeParameter(String name, GenericValueType.Reference classBound, GenericValueType.Reference[] interfaceBounds) {
      this.name = name;
      this.classBound = classBound;
      this.interfaceBounds = (GenericValueType.Reference[])interfaceBounds.clone();
   }

   public String getName() {
      return this.name;
   }

   public GenericValueType.Reference getClassBound() {
      return this.classBound;
   }

   public GenericValueType.Reference[] getInterfaceBounds() {
      return (GenericValueType.Reference[])this.interfaceBounds.clone();
   }

   public List<GenericValueType.Reference> extractAllBounds() {
      ArrayList<GenericValueType.Reference> bounds = new ArrayList<>();
      if (this.classBound != null && !isTrivialBound(this.classBound)) {
         bounds.add(this.classBound);
      }

      bounds.addAll(List.of(this.interfaceBounds));
      return bounds;
   }

   public static boolean isTrivialBound(GenericValueType.Reference bound) {
      return bound instanceof GenericValueType.Object && ((GenericValueType.Object)bound).getFullClassName().equals("java.lang.Object");
   }
}
