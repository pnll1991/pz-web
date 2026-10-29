package org.teavm.dependency;

import java.util.BitSet;
import org.teavm.model.ValueType;

class ExactTypeFilter implements DependencyTypeFilter {
   private static final int[] EMPTY = new int[0];
   ValueType valueType;
   int cache = -1;
   int index;

   ExactTypeFilter(DependencyType dependencyType) {
      this.valueType = dependencyType.getValueType();
      this.index = dependencyType.index;
   }

   @Override
   public boolean match(DependencyType type) {
      if (this.cache >= 0) {
         return type.index == this.cache;
      }

      boolean result = this.valueType.equals(type.getValueType());
      if (result) {
         this.cache = type.index;
      }

      return result;
   }

   @Override
   public int[] tryExtract(BitSet types) {
      return types.get(this.index) ? new int[]{this.index} : EMPTY;
   }
}
