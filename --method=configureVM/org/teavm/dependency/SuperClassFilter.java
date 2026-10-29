package org.teavm.dependency;

import java.util.BitSet;
import org.teavm.common.OptionalPredicate;
import org.teavm.hppc.IntIntHashMap;
import org.teavm.hppc.cursors.IntIntCursor;
import org.teavm.model.ValueType;

class SuperClassFilter implements DependencyTypeFilter {
   private static final int SMALL_CACHE_THRESHOLD = 16;
   private OptionalPredicate<ValueType> predicate;
   private IntIntHashMap smallCache;
   private BitSet knownTypes;
   private BitSet cache;

   SuperClassFilter(DependencyAnalyzer dependencyAnalyzer, String superClass) {
      this.predicate = dependencyAnalyzer.getClassHierarchy().getSuperclassPredicate(superClass);
   }

   @Override
   public boolean match(DependencyType type) {
      if (this.knownTypes != null) {
         if (this.knownTypes.get(type.index)) {
            return this.cache.get(type.index);
         }

         boolean result = this.predicate.test(type.getValueType(), false);
         this.knownTypes.set(type.index);
         this.cache.set(type.index, result);
         return result;
      } else {
         if (this.smallCache == null) {
            this.smallCache = new IntIntHashMap();
         }

         int result = this.smallCache.getOrDefault(type.index, -1);
         if (result != -1) {
            return result != 0;
         }

         boolean value = this.predicate.test(type.getValueType(), false);
         this.smallCache.put(type.index, value ? 1 : 0);
         if (this.smallCache.size() > 16) {
            this.knownTypes = new BitSet();
            this.cache = new BitSet();

            for (IntIntCursor entry : this.smallCache) {
               this.knownTypes.set(entry.key);
               if (entry.value != 0) {
                  this.cache.set(entry.key);
               }
            }

            this.smallCache = null;
         }

         return value;
      }
   }
}
