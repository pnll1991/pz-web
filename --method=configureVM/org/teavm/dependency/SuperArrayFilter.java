package org.teavm.dependency;

import java.util.BitSet;
import org.teavm.model.ValueType;

class SuperArrayFilter implements DependencyTypeFilter {
   private DependencyAnalyzer analyzer;
   private DependencyTypeFilter itemTypeFilter;
   private BitSet knownTypes = new BitSet();
   private BitSet cache = new BitSet();

   SuperArrayFilter(DependencyAnalyzer analyzer, DependencyTypeFilter itemTypeFilter) {
      this.analyzer = analyzer;
      this.itemTypeFilter = itemTypeFilter;
   }

   @Override
   public boolean match(DependencyType type) {
      if (this.knownTypes.get(type.index)) {
         return this.cache.get(type.index);
      }

      boolean result = this.matchCacheMiss(type);
      this.knownTypes.set(type.index);
      this.cache.set(type.index, result);
      return result;
   }

   private boolean matchCacheMiss(DependencyType type) {
      if (!(type.getValueType() instanceof ValueType.Array)) {
         return false;
      }

      ValueType itemValueType = ((ValueType.Array)type.getValueType()).getItemType();
      return this.itemTypeFilter.match(this.analyzer.getType(itemValueType));
   }

   @Override
   public int[] tryExtract(BitSet types) {
      int[] result = this.itemTypeFilter.tryExtract(types);
      if (result == null) {
         return null;
      }

      for (int i = 0; i < result.length; i++) {
         ValueType valueType = this.analyzer.types.get(i).getValueType();
         result[i] = this.analyzer.getType(ValueType.arrayOf(valueType)).index;
      }

      return result;
   }
}
